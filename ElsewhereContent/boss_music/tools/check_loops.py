"""Click check for every *_loop.ogg: the jump from the last sample to the first (the wrap) must not exceed the local
sample-to-sample motion, and intro->loop must be continuous at the hand-off. Run: _tools/venv/bin/python -I tools/check_loops.py [dir]
Pass = wrap jump <= max(0.02, 4x the 99.9th percentile of |diff| inside the file) on every channel."""
import glob, json, os, sys
import numpy as np, soundfile as sf
here = os.path.dirname(os.path.abspath(__file__))
d = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "../src/main/resources/assets/elsewhere/sounds/music")
meta = json.load(open(os.path.join(here, "../../audio/loops/loops.json")))
bad = 0
for p in sorted(glob.glob(d + "/*_loop.ogg")):
    name = os.path.basename(p)[:-9]
    x, sr = sf.read(p, dtype="float32", always_2d=True)
    ip = p.replace("_loop.ogg", "_intro.ogg")
    i, _ = sf.read(ip, dtype="float32", always_2d=True)
    m = meta[name]
    diff = np.abs(np.diff(x, axis=0))
    thr = max(0.02, 4 * float(np.percentile(diff, 99.9)))
    wrap = float(np.abs(x[0] - x[-1]).max())
    A = m["loop_start"]
    # 3.0 lead-in intros (_tools/music_leadin.py): the intro is opening + bars before the loop, so the hand-off sample is its length minus the tail
    H = m["intro_len"] if m.get("leadin") else A
    hand = float(np.abs(i[H - 1] - x[0]).max())
    ok = wrap <= thr and hand <= thr and abs(len(x) - (m["loop_end"] - m["loop_start"])) <= 2 and abs(len(i) - (H + int(m["tail_s"] * sr))) <= 2
    bad += not ok
    print(f"{'ok  ' if ok else 'FAIL'} {name:28s} wrap {wrap:.4f} hand-off {hand:.4f} thr {thr:.4f} loop {len(x)/sr:.1f}s intro {len(i)/sr:.1f}s")
print("OK" if not bad else f"{bad} FAILED"); sys.exit(1 if bad else 0)
