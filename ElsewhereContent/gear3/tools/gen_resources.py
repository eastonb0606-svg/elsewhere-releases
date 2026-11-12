"""gear3 resources from the catalogue (src/main/resources/elsewhere_gear3/catalogue.json): item definitions + models, lang,
recipes, tags (item tags, repair tags), JEI info lines, empty placeholder loot tables, armor equipment assets.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/gen_resources.py [--check]
Every generated file is listed in tools/.resources_generated.json so a removed catalogue item takes its files with it.
Schema: see tools/README-gear3.txt.  Pure stdlib.
"""
import json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
RES = os.path.join(MOD, "src", "main", "resources")
CATALOGUE = os.path.join(RES, "elsewhere_gear3", "catalogue.json")
MANIFEST = os.path.join(HERE, ".resources_generated.json")
HANDHELD = {"sword", "axe", "pickaxe", "shovel", "hoe"}
sys.path.insert(0, HERE)  # -I drops the script dir from sys.path; shield3d.py sits next to this file
import shield3d  # noqa: E402
import calling_tags  # noqa: E402  (s-callingtags: tools/calling_tags.py)
_SPRITES_PATH = os.path.join(os.path.dirname(os.path.dirname(MOD)), "Ideas", "overhaul", "sprites.json")
SPRITES = json.load(open(_SPRITES_PATH, encoding="utf-8")) if os.path.exists(_SPRITES_PATH) else {}
OUT = {}  # relative path under RES -> text


def foreign_ids(obj, own):
    """elsewhere: item ids in a recipe that gear3 does not own (another module's items: they may be absent when that module is off)."""
    out = []
    if isinstance(obj, str):
        if obj.startswith("elsewhere:") and obj[len("elsewhere:"):] not in own and obj not in out:
            out.append(obj)
    elif isinstance(obj, dict):
        for k, v in obj.items():
            if k != "fabric:load_conditions":
                out += [x for x in foreign_ids(v, own) if x not in out]
    elif isinstance(obj, list):
        for v in obj:
            out += [x for x in foreign_ids(v, own) if x not in out]
    return out


def put(rel, obj):
    OUT[rel] = json.dumps(obj, indent=2, ensure_ascii=False) + "\n"


def extra_lang_jei(lang, jei, own):
    """s-setbonus-a: (1) keeps lang keys other lanes added to en_us.json by hand, except the catalogue items' own
    item.elsewhere.<id>[.loreN] keys, which this script owns; (2) merges tools/extra/*.json fragments
    {"lang": {key: text}, "jei": {"elsewhere:<id>": [lines appended after the item's own lines]}}."""
    import glob
    import re
    p = os.path.join(RES, "assets", "elsewhere", "lang", "en_us.json")
    if os.path.exists(p):
        for k, v in json.load(open(p, encoding="utf-8")).items():
            m = re.fullmatch(r"item\.elsewhere\.([a-z0-9_]+?)(\.lore\d+)?", k)
            if k not in lang and not (m and m.group(1) in own):
                lang[k] = v
    for f in sorted(glob.glob(os.path.join(HERE, "extra", "*.json"))):
        frag = json.load(open(f, encoding="utf-8"))
        lang.update(frag.get("lang", {}))
        for k, lines in frag.get("jei", {}).items():
            cur = jei.setdefault(k, [])
            for line in lines:
                if line not in cur:
                    cur.append(line)


def extra_files_sounds():
    """s-galesling: tools/extra/*.json fragments may also carry {"files": {"assets/... or data/... path": json}} (written and
    tracked like any generated file; a path the catalogue also generates, e.g. an item definition, is replaced) and
    {"sounds": {event path: sounds.json entry}} merged into assets/elsewhere/sounds.json (keys already in that file are kept)."""
    import glob
    sounds = {}
    p = os.path.join(RES, "assets", "elsewhere", "sounds.json")
    if os.path.exists(p):
        sounds.update(json.load(open(p, encoding="utf-8")))
    frag_sounds = False
    for f in sorted(glob.glob(os.path.join(HERE, "extra", "*.json"))):
        frag = json.load(open(f, encoding="utf-8"))
        for rel, obj in frag.get("files", {}).items():
            put(rel, obj)
        sounds.update(frag.get("sounds", {}))
        frag_sounds = frag_sounds or bool(frag.get("sounds"))
    if frag_sounds:
        put("assets/elsewhere/sounds.json", sounds)


def pretty(table):
    return table.split("/")[-1].split(":")[-1].replace("_", " ").title()


def pct(x):
    return f"{round(x * 100):d}%" if x * 100 == round(x * 100) else f"{x * 100:.1f}%"


def main():
    cat = json.load(open(CATALOGUE, encoding="utf-8"))
    items = cat["items"]
    sets = set()
    lang = {"itemGroup.elsewhere.gear3": cat.get("tab_name", "Elsewhere 3.0 Gear")}
    jei, tags = {}, {}  # tags: "ns:path" -> [values]
    ids = set()
    own = {i["id"] for i in items}
    errs = []
    for it in items:
        i = it["id"]
        if i in ids:
            errs.append(f"duplicate id {i}")
        ids.add(i)
        kind = it.get("kind", "plain")
        parent = "minecraft:item/handheld" if it.get("model", "handheld" if kind in HANDHELD else "generated") == "handheld" else "minecraft:item/generated"
        if kind == "shield":
            # 3D shield (element model in vanilla ShieldModel space + vanilla display, blocking model on using_item): tools/shield3d.py
            for rel, obj in shield3d.models(i, SPRITES.get(i, {}).get("template", "shield")).items():
                put(rel, obj)
        else:
            put(f"assets/elsewhere/models/item/{i}.json", {"parent": parent, "textures": {"layer0": f"elsewhere:item/{i}"}})
            put(f"assets/elsewhere/items/{i}.json", {"model": {"type": "minecraft:model", "model": f"elsewhere:item/{i}"}})
        lang[f"item.elsewhere.{i}"] = it["name"]
        for n, line in enumerate(it.get("lore", []), 1):
            lang[f"item.elsewhere.{i}.lore{n}"] = line[1:] if line.startswith("!") else line
        # tags
        tg = list(it.get("tags", []))
        for c in it.get("calling", []):
            tg.append(("elsewhere:calling/" if kind == "armor" else "elsewhere:calling_weapon/") + c)
        if kind == "trinket" and "elsewhere:trinket" not in tg:
            tg.append("elsewhere:trinket")
        for t in tg:
            tags.setdefault(t if ":" in t else "elsewhere:" + t, []).append("elsewhere:" + i)
        rep = it.get("stats", {}).get("repair")
        if rep:
            tags.setdefault(f"elsewhere:repairs_{i}", []).extend([r.strip() for r in rep.split(",")])
        # recipes
        rs = it.get("recipe")
        if rs is not None:
            rs = rs if isinstance(rs, list) else [rs]
            for n, r in enumerate(rs):
                r = dict(r)
                fname = r.pop("file", i if n == 0 else f"{i}_{n + 1}")
                r.setdefault("result", {"id": "elsewhere:" + i})
                if isinstance(r["result"], dict) and "id" not in r["result"]:
                    r["result"]["id"] = "elsewhere:" + i
                fo = foreign_ids(r, own)
                if fo:
                    r["fabric:load_conditions"] = [{"condition": "fabric:registry_contains", "registry": "minecraft:item", "values": fo}]
                put(f"data/elsewhere/recipe/{fname}.json", r)
        # JEI
        j = it.get("jei")
        if j is None:
            parts = [f"{pretty(d['table'])} drops it ({pct(d['chance'])})" for d in it.get("loot", [])]
            if rs is not None:
                parts.append("craftable")
            j = "How to get: " + "; ".join(parts) + "." if parts else "How to get: see the Elsewhere wiki."
        jei["elsewhere:" + i] = [j] if isinstance(j, str) else j
        if kind == "armor":
            if not it.get("set") or it.get("slot") not in ("helmet", "chestplate", "leggings", "boots"):
                errs.append(f"{i}: armor needs set and slot")
            sets.add(it.get("set"))
        if kind not in HANDHELD | {"plain", "material", "trinket", "armor", "shield"}:
            errs.append(f"{i}: unknown kind {kind}")
    if any(it.get("kind") == "shield" and it.get("stats", {}).get("melee_only") for it in items):
        put("data/elsewhere/tags/damage_type/gear3_melee.json", {"replace": False, "values": [
            "minecraft:mob_attack", "minecraft:mob_attack_no_aggro", "minecraft:player_attack", "minecraft:sting", "minecraft:mace_smash", "minecraft:thorns"]})
    for name in sorted(sets):
        put(f"assets/elsewhere/equipment/{name}.json", {"layers": {"humanoid": [{"texture": f"elsewhere:{name}"}], "humanoid_leggings": [{"texture": f"elsewhere:{name}"}]}})
    calling_tags.extra(tags, own)  # s-callingtags: calling tags of GEAR.md items not in the catalogue yet (written as required: false)
    for t, vals in tags.items():
        ns, path = t.split(":")
        vv = sorted(set(vals), key=vals.index)
        vv = [v if (v.startswith("#") or (v.startswith("elsewhere:") and v[len("elsewhere:"):] in own) or v.startswith("minecraft:")) and not v.startswith("#") or v.startswith("#") else v for v in vv]
        vv = [{"id": v, "required": False} if (v.startswith("elsewhere:") and v[len("elsewhere:"):] not in own) else v for v in vv]
        put(f"data/{ns}/tags/item/{path}.json", {"replace": False, "values": vv})
    for tb in cat.get("placeholder_tables", []):
        put(f"data/elsewhere/loot_table/{tb}.json", {"type": "minecraft:chest" if tb.startswith("chests/") else "minecraft:entity", "pools": []})
    extra_lang_jei(lang, jei, own)  # s-setbonus-a: tools/extra/*.json fragments, and hand-added lang keys survive a regen
    put("assets/elsewhere/lang/en_us.json", lang)
    put("elsewhere_gear3/jei_info.json", jei)
    if errs:
        print("gen_resources: ERRORS\n  " + "\n  ".join(errs))
        return 1
    if "--check" in sys.argv:
        bad = [r for r, t in OUT.items() if not os.path.exists(os.path.join(RES, r)) or open(os.path.join(RES, r), encoding="utf-8").read() != t]
        print(f"gen_resources: {len(OUT)} file(s), {len(bad)} stale" + ("\n  " + "\n  ".join(bad) if bad else ""))
        return 1 if bad else 0
    old = json.load(open(MANIFEST)) if os.path.exists(MANIFEST) else []
    for rel, text in OUT.items():
        p = os.path.join(RES, rel)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "w", encoding="utf-8").write(text)
    for rel in old:
        if rel not in OUT and os.path.exists(os.path.join(RES, rel)):
            os.remove(os.path.join(RES, rel))
    json.dump(sorted(OUT), open(MANIFEST, "w"), indent=0)
    print(f"gen_resources: wrote {len(OUT)} file(s) for {len(items)} item(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
