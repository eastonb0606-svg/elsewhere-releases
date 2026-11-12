#!/usr/bin/env python3
"""Assemble the Elsewhere beta source tree from snapshots of the core and the content modules.

Usage: python3 tools/merge.py            (uses snap/ as is)
       python3 tools/merge.py --sync     (rsync fresh snapshots first)

Regenerates ElsewhereBeta/src/{main,client} from scratch every run. Order: core, then modules in
MODULES order, then overlay/ (our own glue, patches and tests; overlay files replace anything).
Resource collisions: lang, sounds.json, tags and atlases are merged (earlier source wins per key);
any other duplicate keeps the earlier file and is logged to merge-report.txt.
"""
import json, os, re, shutil, subprocess, sys, filecmp

BETA = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.dirname(BETA)
SNAP = os.environ.get("BETA_SNAP") or os.path.join(BETA, "snap")
OUT = os.path.join(BETA, "src")
OVERLAY = os.path.join(BETA, "overlay")

# beta 2: arsenal (Glimmer, Calling, caster gear) and summoner (minions) first, so their attributes/items exist
# before the dimension modules look them up by id.
# 2.0: boss_ui, the kingdom modules (base first), boss_music, and gear2 LAST (its placeholder loot tables must lose).
ALL_MODULES = ["arsenal", "summoner", "petalwind_glasswaste", "verdigris_ironpeak", "greenhaze_hush", "overworld_bosses", "nether_end_bosses",
               "jei_compat", "boss_ui", "kingdom", "kingdom_duelist", "kingdom_court", "rival_hrafnholt", "rival_azmar", "rival_mirewick",
               "kingdom_villages", "kingdom_lore", "boss_music", "gear2", "gear3"]  # 2.1: duelist and the three rivals after the court
cfg_path = os.path.join(BETA, "tools", "modules.json")
CFG = json.load(open(cfg_path)) if os.path.exists(cfg_path) else {}
MODULES = [m for m in ALL_MODULES if m not in CFG.get("disabled", [])]
if os.environ.get("BETA_MODULES") is not None:
    MODULES = [m for m in os.environ["BETA_MODULES"].split(",") if m]

report = []
def log(msg):
    report.append(msg)

def module_src(m):
    """Snapshot source of a module (dir holding main/client/...): modules.json "snapshot_from" (path under ROOT, a dir
    that contains src/) pins a module to a frozen state (e.g. the alpha.3 snapshot); otherwise the live module."""
    pin = CFG.get("snapshot_from", {}).get(m)
    if pin:
        return os.path.join(ROOT, pin, "src")
    if m == "core":
        return os.path.join(ROOT, "Elsewhere", "src")
    src = os.path.join(ROOT, "ElsewhereContent", m, "src")
    frozen = os.path.join(ROOT, "ElsewhereContent", m, os.environ.get("BETA_FREEZE", "freeze-beta2"), "src")
    return frozen if os.path.isdir(frozen) else src

def sync():
    ex = ["--exclude", "build/", "--exclude", ".gradle/", "--exclude", "run/", "--exclude", ".DS_Store"]
    os.makedirs(SNAP, exist_ok=True)
    art = os.path.join(ROOT, CFG["art2_overlay"]) if CFG.get("art2_overlay") else None
    for m in ["core"] + ALL_MODULES:
        src = module_src(m)
        if not os.path.isdir(src):
            continue
        dst = os.path.join(SNAP, m, "src")
        subprocess.check_call(["rsync", "-a", "--delete", *ex, src + "/", dst + "/"])
        log(f"snapshot {m} <- {os.path.relpath(src, ROOT)}")
        # art staging (art2/<module>/src mirrors the module; core is art2/Elsewhere): copied into the snapshot only
        a = os.path.join(art, "Elsewhere" if m == "core" else m, "src") if art else None
        if a and os.path.isdir(a):
            n = sum(len(fn) for _, _, fn in os.walk(a))
            subprocess.check_call(["rsync", "-a", "--exclude", ".DS_Store", a + "/", dst + "/"])
            log(f"art2 {m}: {n} file(s) mirrored over the snapshot")
        for frm, to in CFG.get("snapshot_extra", {}).get(m, []):
            p = os.path.join(ROOT, frm)
            if os.path.exists(p):
                os.makedirs(os.path.dirname(os.path.join(SNAP, m, to)), exist_ok=True)
                shutil.copy2(p, os.path.join(SNAP, m, to))
                log(f"extra {m}: {frm} -> {to}")
            else:
                log(f"EXTRA-MISSING {m}: {frm}")
        for f in ("NOTES.md",):
            p = os.path.join(os.path.dirname(src), f)
            if os.path.exists(p):
                shutil.copy2(p, os.path.join(SNAP, m, f))

def shared_owner(rel):
    """modules.json "shared": {regex: owner}: files that several modules carry as identical copies (boss kit, HUD)."""
    for pat, own in CFG.get("shared", {}).items():
        if re.search(pat, rel):
            return own
    return None

def is_placeholder_loot(path):
    """An entity/chest loot table with no entries at all (only there so Fabric's MODIFY event fires)."""
    try:
        d = json.load(open(path, encoding="utf-8"))
    except Exception:
        return False
    return all(not p.get("entries") for p in d.get("pools", []))

def deep_merge(a, b, path):
    """Merge b into a; a wins on scalar conflicts."""
    for k, v in b.items():
        if k not in a:
            a[k] = v
        elif isinstance(a[k], dict) and isinstance(v, dict):
            deep_merge(a[k], v, path + "/" + k)
        elif a[k] != v:
            log(f"  key conflict {path}:{k} kept earlier value")
    return a

def merge_json(dest, src, rel):
    try:
        a = json.load(open(dest, encoding="utf-8"))
        b = json.load(open(src, encoding="utf-8"))
    except Exception as e:
        log(f"JSON-PARSE-FAIL {rel}: {e}")
        return False
    if re.search(r"(^|/)tags/", rel) and isinstance(a, dict) and isinstance(b, dict):
        vals = list(a.get("values", []))
        for v in b.get("values", []):
            if v not in vals:
                vals.append(v)
        a["values"] = vals
        a["replace"] = bool(a.get("replace", False) and b.get("replace", False))
    elif re.search(r"/lang/[^/]+\.json$", rel) or rel.endswith("sounds.json") or rel in CFG.get("json_merge", []):
        deep_merge(a, b, rel)
    elif "/atlases/" in rel:
        srcs = a.get("sources", [])
        for s in b.get("sources", []):
            if s not in srcs:
                srcs.append(s)
        a["sources"] = srcs
    else:
        return False
    json.dump(a, open(dest, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    return True

def module_excludes(name):
    return CFG.get("exclude", {}).get(name, [])

def excluded(name, rel):
    for pat in module_excludes(name):
        if re.search(pat, rel):
            return True
    return False

mixin_configs = []  # (filename, client?)
origin = {}

def copy_tree(name, base):
    for sset in ("main", "client", "gametest"):
        if sset == "gametest":
            continue  # our own gametest lives in overlay
        jroot = os.path.join(base, "src", sset, "java")
        for dp, dn, fn in os.walk(jroot):
            for f in fn:
                if not f.endswith(".java"):
                    continue
                full = os.path.join(dp, f)
                rel = os.path.relpath(full, jroot)
                if excluded(name, rel):
                    log(f"excluded {name}: java/{rel}")
                    continue
                own = shared_owner(rel)
                if own and own != name and name not in ("overlay", "overlay_late"):
                    of = os.path.join(SNAP, own, "src", sset, "java", rel)
                    if os.path.exists(of):
                        if filecmp.cmp(of, full, shallow=False):
                            log(f"shared {rel}: {name} copy identical to {own}'s, dropped")
                        else:
                            log(f"SHARED-DIFF {rel}: {name}'s copy differs from {own}'s ({own} kept)")
                        continue
                    log(f"shared {rel}: owner {own} has no copy, {name}'s used if first")
                dest = os.path.join(OUT, sset, "java", rel)
                if os.path.exists(dest):
                    if origin.get(dest) == "overlay":
                        log(f"overlay-replaces {name}: java/{rel}")
                    else:
                        log(f"JAVA-DUP {rel}: {origin.get(dest)} kept, {name} dropped")
                    continue
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                shutil.copy2(full, dest)
                origin[dest] = name
        rroot = os.path.join(base, "src", sset, "resources")
        for dp, dn, fn in os.walk(rroot):
            for f in fn:
                if f == ".DS_Store":
                    continue
                full = os.path.join(dp, f)
                rel = os.path.relpath(full, rroot)
                if rel == "fabric.mod.json" or rel == "pack.mcmeta":
                    continue
                if excluded(name, rel):
                    log(f"excluded {name}: res/{rel}")
                    continue
                if rel.endswith(".mixins.json") and "/" not in rel:
                    try:
                        mj = json.load(open(full))
                        is_client = bool(mj.get("client")) and not mj.get("mixins") or sset == "client"
                    except Exception:
                        is_client = sset == "client"
                    mixin_configs.append((rel, is_client))
                dest = os.path.join(OUT, "main", "resources", rel)  # all resources in main
                if os.path.exists(dest):
                    if filecmp.cmp(dest, full, shallow=False):
                        continue
                    if re.match(r"data/[^/]+/loot_table/.+\.json$", rel):
                        # placeholder tables (no entries) never win over a real table, whatever the module order:
                        # the real drops stay and every module's MODIFY pools are still added to that table at load
                        pd, pn = is_placeholder_loot(dest), is_placeholder_loot(full)
                        if pd and not pn:
                            shutil.copy2(full, dest)
                            log(f"loot {rel}: {origin.get(dest)}'s placeholder replaced by {name}'s real table")
                            origin[dest] = name
                            continue
                        if pn:
                            log(f"loot {rel}: {name}'s placeholder dropped ({origin.get(dest)}'s table kept)")
                            continue
                    if rel.endswith(".json") and merge_json(dest, full, rel):
                        log(f"merged {rel} ({origin.get(dest)} + {name})")
                        origin[dest] = origin.get(dest, "?") + "+" + name
                        continue
                    log(f"RES-DUP {rel}: {origin.get(dest)} kept, {name} dropped")
                    continue
                os.makedirs(os.path.dirname(dest), exist_ok=True)
                shutil.copy2(full, dest)
                origin[dest] = name

NO_RIFT_DIMS = {"the_hush"}

def inject_return_rifts():
    """CORE-API: every biome of a Return-Rift dimension needs elsewhere:return_rift first in step 9."""
    data = os.path.join(OUT, "main", "resources", "data", "elsewhere")
    ddir = os.path.join(data, "dimension")
    if not os.path.isdir(ddir):
        return
    for f in sorted(os.listdir(ddir)):
        dim = f[:-5]
        if dim in NO_RIFT_DIMS:
            continue
        try:
            d = json.load(open(os.path.join(ddir, f)))
            bs = d["generator"]["biome_source"]
            biomes = [b["biome"] if isinstance(b, dict) else b for b in bs.get("biomes", [])] if isinstance(bs.get("biomes"), list) else [bs.get("biome")]
        except Exception as e:
            log(f"RIFT-SKIP {dim}: {e}")
            continue
        n = 0
        for b in biomes:
            if not b or not b.startswith("elsewhere:"):
                continue
            bp = os.path.join(data, "worldgen", "biome", b.split(":")[1] + ".json")
            if not os.path.exists(bp):
                continue
            bj = json.load(open(bp))
            feats = bj.setdefault("features", [])
            flat = [x for step in feats for x in (step if isinstance(step, list) else [step])]
            if "elsewhere:return_rift" in flat:
                continue
            while len(feats) < 10:
                feats.append([])
            if not isinstance(feats[9], list):
                feats[9] = [feats[9]]
            feats[9].insert(0, "elsewhere:return_rift")
            json.dump(bj, open(bp, "w"), indent=2)
            n += 1
        log(f"return_rift added to {n} biome(s) of {dim}")

def apply_patches(m):
    for pt in CFG.get("patches", {}).get(m, []):
        f = os.path.join(OUT, pt["file"])
        if not os.path.exists(f):
            log(f"PATCH-MISSING {m} {pt['file']}")
            continue
        txt = open(f, encoding="utf-8").read()
        sk = pt.get("skip_if")
        if sk and sk in txt:
            log(f"patch {m} {pt['file']}: skipped, module already has {sk!r}")
            continue
        new, n = re.subn(pt["find"], pt["replace"], txt)
        if n:
            open(f, "w", encoding="utf-8").write(new)
        log(f"patch {m} {pt['file']}: {n} replacement(s) for {pt['find'][:50]}")
        if n == 0 and pt.get("required"):
            log(f"PATCH-FAILED {m} {pt['file']}: {pt['find'][:80]}")

def find_bossart(base, m):
    """Every dev.elsewhere.bossart.<group>.BossArt*Client with a public static initClient(), unless excluded/disabled."""
    out = []
    for x in sorted(find_entry(base, "client", r"BossArt\w*Client\.java$", "initClient")):
        if not x.startswith("dev.elsewhere.bossart."):
            continue
        if x in CFG.get("bossart_disabled", []):
            log(f"bossart {x} ({m}) DISABLED by modules.json")
            continue
        if excluded(m, x.replace(".", "/") + ".java"):
            continue
        log(f"bossart {m}: {x}")
        out.append(x + ".initClient()")
    return out

def find_entry(base, sset, pattern, method):
    jroot = os.path.join(base, "src", sset, "java")
    hits = []
    for dp, dn, fn in os.walk(jroot):
        for f in fn:
            if re.match(pattern, f):
                full = os.path.join(dp, f)
                txt = open(full, encoding="utf-8").read()
                if re.search(r"public\s+static\s+void\s+" + method + r"\s*\(\s*\)", txt):
                    rel = os.path.relpath(full, jroot)[:-5].replace(os.sep, ".")
                    hits.append(rel)
    return hits

def main():
    if "--sync" in sys.argv:
        sync()
    for sset in ("main", "client"):
        shutil.rmtree(os.path.join(OUT, sset), ignore_errors=True)
    os.makedirs(os.path.join(OUT, "main", "resources"), exist_ok=True)

    # Overlay first for java (so patched files replace module files), but resources merge after.
    ov = os.path.join(OVERLAY)
    copy_tree("overlay", ov) if os.path.isdir(ov) else None
    copy_tree("core", os.path.join(SNAP, "core"))
    apply_patches("core")
    entries, client_entries, bossart_entries = [], [], []
    bossart_entries += find_bossart(os.path.join(SNAP, "core"), "core")
    for m in MODULES:
        base = os.path.join(SNAP, m)
        if not os.path.isdir(base):
            log(f"MISSING module snapshot {m}")
            continue
        copy_tree(m, base)
        apply_patches(m)
        e = [x for x in find_entry(base, "main", r".*Content\.java$", "init") if x.startswith("dev.elsewhere." + m + ".")]
        c = [x for x in find_entry(base, "client", r".*Client\.java$", "initClient") if not x.startswith("dev.elsewhere.bossart.")]
        c.sort(key=lambda x: (not x.startswith("dev.elsewhere." + m + "."), x))
        bossart_entries += find_bossart(base, m)
        log(f"module {m}: init={e} client={c}")
        entries += CFG.get("pre_init", {}).get(m, []) + [x + ".init()" for x in e[:1]] + CFG.get("post_init", {}).get(m, [])
        for x in [x + ".initClient()" for x in c[:1]] + CFG.get("post_client", {}).get(m, []):
            if x in client_entries:
                log(f"client entry {x} ({m}) already listed, not called twice")
            else:
                client_entries.append(x)

    # Boss art (artists' BossArt<Group>Client) after every module client init, so it overrides fallback renderers.
    client_entries += bossart_entries

    late = os.path.join(BETA, "overlay_late")
    if os.path.isdir(late):
        copy_tree("overlay_late", late)

    inject_return_rifts()

    # glue classes
    gdir = os.path.join(OUT, "main", "java", "dev", "elsewhere", "beta")
    os.makedirs(gdir, exist_ok=True)
    cmdf = os.path.join(SNAP, "core", "src", "main", "java", "dev", "elsewhere", "command", "ElsewhereCommands.java")
    core_toggle = os.path.exists(cmdf) and 'literal("off")' in open(cmdf, encoding="utf-8").read()
    log(f"core has its own /elsewhere trips on|off: {core_toggle}")
    open(os.path.join(gdir, "BetaFlags.java"), "w").write(f"""package dev.elsewhere.beta;

/** Generated by tools/merge.py. */
public final class BetaFlags {{
	private BetaFlags() {{}}

	/** The core registers /elsewhere trips on|off itself (then the overlay's command is not registered). */
	public static final boolean CORE_TRIPS_TOGGLE = {str(core_toggle).lower()};
	public static final String MODULES = "{",".join(MODULES)}";
}}
""")
    body = "\n".join(f"\t\t{x};" for x in entries)
    open(os.path.join(gdir, "BetaInit.java"), "w").write(f"""package dev.elsewhere.beta;

import net.fabricmc.api.ModInitializer;

/** Generated by tools/merge.py: initialises every content module after the core. */
public final class BetaInit implements ModInitializer {{
	@Override
	public void onInitialize() {{
{body}
		BetaHooks.afterModules();
	}}
}}
""")
    cdir = os.path.join(OUT, "client", "java", "dev", "elsewhere", "beta")
    os.makedirs(cdir, exist_ok=True)
    cbody = "\n".join(f"\t\t{x};" for x in client_entries)
    open(os.path.join(cdir, "BetaClientInit.java"), "w").write(f"""package dev.elsewhere.beta;

import net.fabricmc.api.ClientModInitializer;

/** Generated by tools/merge.py: client init of every content module after the core. */
public final class BetaClientInit implements ClientModInitializer {{
	@Override
	public void onInitializeClient() {{
{cbody}
		BetaClientSafety.fillMissingRenderers();
	}}
}}
""")

    # fabric.mod.json
    core_fmj = json.load(open(os.path.join(SNAP, "core", "src", "main", "resources", "fabric.mod.json")))
    fmj = dict(core_fmj)
    fmj["version"] = "${version}"
    fmj["name"] = "Elsewhere"
    fmj["description"] = "New dimensions reached with Wayfinders, and the bosses that guard them. Beta: " + ", ".join(["core"] + MODULES) + "."
    ep = fmj.setdefault("entrypoints", {})
    main_eps = [x for x in ep.get("main", [])]
    client_eps = []
    for x in ep.get("client", []):
        path = os.path.join(OUT, "client", "java", *x.split(".")) + ".java"
        if os.path.exists(path):
            client_eps.append(x)
        else:
            log(f"core client entrypoint {x} missing, skipped")
    # extra entrypoint kinds from the modules' own fabric.mod.json (e.g. jei_mod_plugin), never main/client/dev ones
    for m in MODULES:
        mp = os.path.join(SNAP, m, "src", "main", "resources", "fabric.mod.json")
        if not os.path.exists(mp):
            continue
        try:
            mj = json.load(open(mp))
        except Exception as e:
            log(f"FMJ-PARSE-FAIL {m}: {e}")
            continue
        for kind, lst in mj.get("entrypoints", {}).items():
            if kind in ("main", "client", "server", "fabric-gametest", "fabric-client-gametest", "fabric-datagen"):
                continue
            for x in lst:
                cls = x if isinstance(x, str) else x.get("value", "")
                found = any(os.path.exists(os.path.join(OUT, ss, "java", *cls.split("::")[0].split(".")) + ".java") for ss in ("main", "client"))
                if not found:
                    log(f"ENTRYPOINT-MISSING {m} {kind}: {cls}")
                    continue
                ep.setdefault(kind, [])
                if x not in ep[kind]:
                    ep[kind].append(x)
                    log(f"entrypoint {m} {kind}: {cls}")
    ep["main"] = main_eps + ["dev.elsewhere.beta.BetaInit"]
    ep["client"] = client_eps + ["dev.elsewhere.beta.BetaClientInit"]
    mix = []
    for x in core_fmj.get("mixins", []):
        mix.append(x)
    names = {m if isinstance(m, str) else m["config"] for m in mix}
    for f, is_client in mixin_configs:
        if f in names:
            continue
        names.add(f)
        mix.append({"config": f, "environment": "client"} if is_client else f)
    if mix:
        fmj["mixins"] = mix
    fmj["suggests"] = {"greenleaf": "*", "testmeter": "*", "jei": "*"}
    if not os.path.exists(os.path.join(OUT, "main", "resources", "assets", "elsewhere", "icon.png")):
        fmj.pop("icon", None)
    json.dump(fmj, open(os.path.join(OUT, "main", "resources", "fabric.mod.json"), "w"), indent=2)

    json.dump({os.path.relpath(k, OUT): v for k, v in origin.items()}, open(os.path.join(BETA, "merge-origin.json"), "w"), indent=0)
    open(os.path.join(BETA, "merge-report.txt"), "w").write("\n".join(report) + "\n")
    dups = [r for r in report if r.startswith(("JAVA-DUP", "RES-DUP", "JSON-PARSE", "MISSING", "PATCH-FAILED", "PATCH-MISSING", "ENTRYPOINT-MISSING", "FMJ-PARSE", "SHARED-DIFF", "EXTRA-MISSING"))]
    print(f"merged core + {MODULES}; {len(dups)} unresolved duplicates (see merge-report.txt)")
    for d in dups[:80]:
        print(" ", d)

if __name__ == "__main__":
    lock = os.path.join(BETA, ".merging")
    open(lock, "w").close()
    try:
        main()
    finally:
        os.remove(lock)
