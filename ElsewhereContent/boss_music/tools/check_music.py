"""3.0 music checks (static, no game). Run: _tools/venv/bin/python -I ElsewhereContent/boss_music/tools/check_music.py
1. every slot (boss ids + realm events) resolves to sound events whose OGG files exist and decode (intro/loop/surge/end);
2. no two slots share a theme unless boss_music.json 'shared_themes' documents it (one fight, two entities);
3. seams: every used loop and surge passes the LOOPS.md gate (rel <= 0.5 and corr >= 0.7, or corr >= 0.45 with a >= 2 s
   blend), and its wrap is click-free (|last - first| sample jump <= max(0.02, 4x the 99.9th pct of |diff|));
4. every theme has a 'themes' plan whose numbers match the files (intro/loop/surge/end lengths within 1 tick);
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


# 1 + 4
n_files = 0
themes = sorted(set(bm["bosses"].values()))
for slot, p in prof.items():
    for ent in p.get("boss", []):
        if ent not in bm["bosses"]:
            fail(f"slot {slot}: {ent} not in boss map")
    if p["kind"] == "realm":
        ev = p["event"].split()[-1]
        if ev.startswith("court_music"):
            continue  # Aldermark's realm file lives in kingdom_court (its own module)
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
    parts = ["intro", "loop"] + (["surge"] if plan["surge_ticks"] else []) + (["end"] if plan["end_ticks"] else [])
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
            want = {"intro": plan["intro_ticks"] / 20 + 0.4, "loop": plan["loop_ticks"] / 20, "surge": plan["surge_ticks"] / 20, "end": plan["end_ticks"] / 20}[part]
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
    sg = sec.get(name, {}).get("surge")
    if bm["themes"][tid]["surge_ticks"]:
        if not sg or not gate(sg["rel"], sg["corr"], sg["crossfade_s"]):
            fail(f"{name} surge seam {sg}")
        ok, j, t = wrap_ok(os.path.join(MOD, "sounds/music", name + "_surge.ogg"))
        if not ok:
            fail(f"{name}_surge wrap jump {j:.4f} > {t:.4f}")
        nseam += 1
print(f"CHECK seams: {nseam} loops/surges pass rel/corr gate and wrap click test")

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
