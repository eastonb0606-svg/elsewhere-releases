"""3.0 music checks (static, no game). Run: _tools/venv/bin/python -I ElsewhereContent/boss_music/tools/check_music.py
1. every slot (boss ids + realm events) resolves to sound events whose OGG files exist and decode (intro/loop/phase layers/end);
2. no two slots share a theme unless boss_music.json 'shared_themes' documents it (one fight, two entities);
3. seams: every used loop and surge passes the LOOPS.md gate (rel <= 0.5 and corr >= 0.7, or corr >= 0.45 with a >= 2 s
   blend), and its wrap is click-free (|last - first| sample jump <= max(0.02, 4x the 99.9th pct of |diff|));
4. every theme has a 'themes' plan whose numbers match the files (intro/loop/layer/end lengths within 1 tick);
6. P12 phase layers ('layers', the surge being the threshold-0 one): valid names and thresholds, each louder than the section
   before it; 7. court titles by id: each Aldermark court OGG matches its own title's catalog length (and no other's).
5. size budget: boss_music sounds/music <= BUDGET_MB; credits list every installed track title.
Exit 1 on any failure; prints one CHECK line per group and a final RESULT line."""
import glob, json, os, sys
import numpy as np, soundfile as sf

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.join(HERE, "../src/main/resources/assets/elsewhere")
CONTENT = os.path.normpath(os.path.join(HERE, "../.."))
BUDGET_MB = 135.0
fails = []


def fail(msg):
    fails.append(msg)
    print("  FAIL", msg)


bm = json.load(open(os.path.join(MOD, "boss_music.json")))
sj = json.load(open(os.path.join(MOD, "sounds.json")))
loops = json.load(open(os.path.join(CONTENT, "audio/loops/loops.json")))
sec = json.load(open(os.path.join(CONTENT, "audio/loops/sections.json")))
prof = json.load(open(os.path.join(CONTENT, "audio/fit/profiles.json")))["slots"]


def files_of(event):
    if event not in sj:
        return None
    out = []
    for s in sj[event]["sounds"]:
        n = s["name"] if isinstance(s, dict) else s
        ns, path = n.split(":")
        out.append(os.path.join(MOD, "sounds", path + ".ogg"))
    return out


def seconds(p):
    i = sf.info(p)
    return i.frames / i.samplerate


def layers_of(plan):
    """P12 phase layers of a theme plan: the explicit 'layers' list, else the surge as the threshold-0 layer."""
    if "layers" in plan:
        return plan["layers"]
    return [dict(phase=0, name="surge", loop_ticks=plan["surge_ticks"], phase_ticks=plan.get("surge_phase_ticks", 0))] if plan["surge_ticks"] > 20 else []


def layer_rec(name, lname):
    e = sec.get(name, {})
    return e.get("surge") if lname == "surge" else e.get("layers", {}).get(lname)


# 1 + 4
n_files = 0
themes = sorted(set(bm["bosses"].values()))
for slot, p in prof.items():
    for ent in p.get("boss", []):
        if ent not in bm["bosses"]:
            fail(f"slot {slot}: {ent} not in boss map")
    if p["kind"] == "realm":
        ev = p["event"].split()[-1]  # 3.0 fit pass: court_music.realm now lives here too (the merged jar drops kingdom_court's sounds.json)
        fs = files_of(ev)
        if not fs or not all(os.path.exists(f) for f in fs):
            fail(f"realm {slot}: event {ev} has no file")
        else:
            n_files += len(fs)
for tid in themes:
    slug = tid.split("boss_music.theme.")[1]
    plan = bm.get("themes", {}).get(tid)
    if plan is None:
        fail(f"{tid}: no plan")
        continue
    lay = layers_of(plan)
    parts = ["intro", "loop"] + [l["name"] for l in lay] + (["end"] if plan["end_ticks"] else [])
    for part in parts:
        fs = files_of(f"boss_music.theme.{slug}.{part}")
        if not fs:
            fail(f"{tid}: no sounds.json event .{part}")
            continue
        for f in fs:
            if not os.path.exists(f):
                fail(f"{tid}: missing {os.path.basename(f)}")
                continue
            with open(f, "rb") as fh:
                if fh.read(4) != b"OggS":
                    fail(f"{tid}: not an OGG {f}")
            n_files += 1
            s = seconds(f)
            want = {"intro": plan["intro_ticks"] / 20 + 0.4, "loop": plan["loop_ticks"] / 20, "end": plan["end_ticks"] / 20,
                    **{l["name"]: l["loop_ticks"] / 20 for l in lay}}[part]
            tol = 0.06 if part != "end" else 0.06
            if part == "end":
                ok = want - 0.05 - tol <= s <= want + tol
            else:
                ok = abs(s - want) <= tol
            if not ok:
                fail(f"{tid}.{part}: file {s:.3f}s vs plan {want:.3f}s")
print(f"CHECK files: {len(themes)} themes, {n_files} files resolve and match their plans")

# 2
users = {}
for ent, tid in bm["bosses"].items():
    users.setdefault(tid, []).append(ent)
shared = {k: v for k, v in bm.get("shared_themes", {}).items() if not k.startswith("_")}
for tid, ents in users.items():
    if len(ents) > 1 and sorted(shared.get(tid, [])) != sorted(ents):
        fail(f"undocumented shared theme {tid}: {ents}")
print(f"CHECK shares: {sum(1 for e in users.values() if len(e) > 1)} shared themes, all documented ({', '.join(sorted(shared))})")


# 3
def wrap_ok(path):
    x, sr = sf.read(path, dtype="float32", always_2d=True)
    d = np.abs(np.diff(x, axis=0))
    thr = max(0.02, 4 * float(np.percentile(d, 99.9)))
    jump = float(np.abs(x[0] - x[-1]).max())
    return jump <= thr, jump, thr


def gate(rel, corr, cf):
    return rel <= 0.5 and (corr >= 0.7 or (corr >= 0.45 and cf >= 2.0))


nseam = 0
for tid in themes:
    name = "boss_" + tid.split("boss_music.theme.")[1]
    m = loops[name]
    if not gate(m["rel"], m["join_corr"], m["crossfade_s"]):
        fail(f"{name} loop seam rel {m['rel']} corr {m['join_corr']} cf {m['crossfade_s']}")
    ok, j, t = wrap_ok(os.path.join(MOD, "sounds/music", name + "_loop.ogg"))
    if not ok:
        fail(f"{name}_loop wrap jump {j:.4f} > {t:.4f}")
    nseam += 1
    for l in layers_of(bm["themes"][tid]):
        sg = layer_rec(name, l["name"])
        if not sg or not gate(sg["rel"], sg["corr"], sg["crossfade_s"]):
            fail(f"{name} {l['name']} seam {sg}")
        ok, j, t = wrap_ok(os.path.join(MOD, "sounds/music", name + "_" + l["name"] + ".ogg"))
        if not ok:
            fail(f"{name}_{l['name']} wrap jump {j:.4f} > {t:.4f}")
        nseam += 1
print(f"CHECK seams: {nseam} loops and phase layers pass rel/corr gate and wrap click test")

# P12 phase layers: valid data, and each layer is the step up in intensity its threshold promises
import re
nlay = nth = 0
nthr = len(bm.get("phase_thresholds", [0.66, 0.33]))
for tid in themes:
    name = "boss_" + tid.split("boss_music.theme.")[1]
    lay = layers_of(bm["themes"][tid])
    seen = set()
    prev_db = 0.0  # loudness over the loop, dB
    for l in sorted(lay, key=lambda l: l["phase"]):
        nlay += 1
        if not re.fullmatch(r"[a-z0-9_]+", l["name"]) or l["name"] in ("intro", "loop", "end"):
            fail(f"{tid}: bad layer name {l['name']}")
        if not (0 <= l["phase"] < nthr) or l["phase"] in seen:
            fail(f"{tid}: layer {l['name']} threshold {l['phase']} out of range or taken")
        seen.add(l["phase"])
        if l["loop_ticks"] <= 20:
            fail(f"{tid}: layer {l['name']} too short")
        r = layer_rec(name, l["name"])
        if r and r.get("gain_db", 0) < prev_db + 0.5 - 1e-6:
            fail(f"{tid}: layer {l['name']} is not louder than the section before it ({r.get('gain_db')} dB vs {prev_db} dB)")
        prev_db = r.get("gain_db", prev_db) if r else prev_db
    nth += len(seen)
print(f"CHECK layers: {nlay} phase layers on {sum(1 for t in themes if layers_of(bm['themes'][t]))} themes ({nth} of {nthr * len(themes)} thresholds swap, the rest sting); "
      "names, thresholds and rising intensity valid")

# court titles by id (not by list order): each kingdom_court boss_court_<id>.ogg matches its own title's catalog length and no other's
ct = json.load(open(os.path.join(CONTENT, "audio/fit/court_titles.json")))["tracks"]
tr_all = json.load(open(os.path.join(CONTENT, "audio/fit/tracks.json")))["tracks"]
for cid, c in ct.items():
    f = os.path.join(CONTENT, c["file"])
    if not os.path.exists(f):
        fail(f"court {cid}: no file {c['file']}")
        continue
    s = seconds(f)
    hits = [k for k, o in ct.items() if abs(o["catalog_s"] - s) <= 1.5]
    if hits != [cid]:
        fail(f"court {cid}: {s:.1f} s matches {hits}, want only itself ({c['title']}, {c['catalog_s']} s)")
    t = tr_all.get(cid, {})
    if t.get("title") != c["title"] or t.get("title_by") != "id" or t.get("title_assumed"):
        fail(f"court {cid}: tracks.json title {t.get('title')} ({'by id' if t.get('title_by') == 'id' else 'assumed'}) vs {c['title']}")
print(f"CHECK court titles: {len(ct)} Aldermark court tracks matched by id (audio length vs catalog, unique), tracks.json agrees")

# fit (s-music-fit): no orphan theme or file in the jar, the boss map is the fit scorer's assignment, no boss slot is weak
orph = sorted({k.rsplit(".", 1)[0] for k in sj if k.startswith("boss_music.theme.")
               and "elsewhere:boss_music.theme." + k[len("boss_music.theme."):].split(".")[0] not in themes})
if orph:
    fail(f"orphan theme events (no boss uses them): {orph}")
refd = {os.path.normpath(f) for ev in sj for f in (files_of(ev) or [])}
stray = sorted(os.path.basename(f) for f in glob.glob(os.path.join(MOD, "sounds/music/*.ogg")) if os.path.normpath(f) not in refd)
if stray:
    fail(f"OGGs no sounds.json event plays: {stray}")
fitd = json.load(open(os.path.join(CONTENT, "audio/fit/scores.json")))
for slot, a in fitd["assign"].items():
    for ent in prof[slot].get("boss", []):
        if bm["bosses"].get(ent) != "elsewhere:boss_music.theme." + a["track"]:
            fail(f"fit: {ent} plays {bm['bosses'].get(ent)}, scorer assigned {a['track']}")
weak = [f"{s} {a['fit']:.3f}" for s, a in fitd["assign"].items() if a["band"] == "weak"]
if weak:
    fail(f"fit: weak slots (< 0.55): {weak}")
print(f"CHECK fit: no orphan themes or files, boss map = scorer assignment for {len(fitd['assign'])} slots, weak slots: {len(weak)}")

# 5
size = sum(os.path.getsize(f) for f in glob.glob(os.path.join(MOD, "sounds/music/*.ogg"))) / 1e6
if size > BUDGET_MB:
    fail(f"music {size:.1f} MB > budget {BUDGET_MB} MB")
cred = open(os.path.join(CONTENT, "audio/CREDITS.md")).read()
tracks = json.load(open(os.path.join(CONTENT, "audio/fit/tracks.json")))["tracks"]
missing = [t["title"] for s, t in tracks.items() if t["title"] not in cred]
if missing:
    fail(f"credits miss {missing}")
print(f"CHECK budget: boss_music music {size:.1f} MB <= {BUDGET_MB} MB; credits list all {len(tracks)} tracks")
print("RESULT", "OK" if not fails else f"{len(fails)} FAILED")
sys.exit(1 if fails else 0)
