"""Wolf-Call Horn assets (s-wolfcall, 3.0 swarm): the Warg skin and the Warg howl.

  ROOT/_tools/venv/bin/python -I gear3/tools/wolfcall_assets.py [--wolf PATH/wolf.png]

1. textures/entity/hrafnholt_warg.png (64x32, the vanilla wolf UV layout, drawn on the vanilla AdultWolfModel rig):
   the vanilla wolf skin is used only as a stencil (which pixels the rig samples, where the eyes/nose/muzzle sit, and its
   light/dark relief); the colours are our own Hrafnholt war-wolf: charcoal back, ash belly and throat, amber eyes, a scar
   over the right eye, a tarred-leather harness ring round the shoulders and a girth strap, both with hacksilver rings.
   The wolf.png path defaults to the loom cache's 26.3 client jar (read in place, nothing downloaded).
2. sounds/gear3/wolfcall/warg_howl.ogg: an original synthesized howl (gliding fundamental with vibrato, a few harmonics,
   breath noise, a short outdoor echo), mono Vorbis so Minecraft attenuates it by distance.
Needs numpy, soundfile and Pillow (all in _tools/venv).
"""
import io, os, sys, zipfile

import numpy as np
import soundfile as sf
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "elsewhere")
TEX = os.path.join(RES, "textures", "entity", "hrafnholt_warg.png")
OGG = os.path.join(RES, "sounds", "gear3", "wolfcall", "warg_howl.ogg")
CLIENT_JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")


def hexc(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


FUR_DARK, FUR_LIGHT = hexc("#1f1c1a"), hexc("#625b53")          # charcoal back and flanks
ASH_DARK, ASH_LIGHT = hexc("#5d5750"), hexc("#a39a8e")          # belly, throat, chest
MUZ_DARK, MUZ_LIGHT = hexc("#4a4038"), hexc("#8d7f70")          # muzzle (vanilla tan)
EYE, BROW, NOSE, MOUTH, FANG = hexc("#f0a830"), hexc("#141110"), hexc("#121010"), hexc("#2a201c"), hexc("#e6dccb")
SCAR = hexc("#9a7d72")
LEATHER, LEATHER_HI = hexc("#3b2618"), hexc("#5e3d25")
SILVER, SILVER_HI = hexc("#8e939a"), hexc("#d4d7db")


def load_wolf(path):
    if path:
        return Image.open(path).convert("RGBA")
    with zipfile.ZipFile(CLIENT_JAR) as z:
        return Image.open(io.BytesIO(z.read("assets/minecraft/textures/entity/wolf/wolf.png"))).convert("RGBA")


def lerp(a, b, t):
    return a + (b - a) * max(0.0, min(1.0, t))


def in_box(x, y, x0, y0, x1, y1):
    return x0 <= x < x1 and y0 <= y < y1


def warg_skin(src):
    w, h = src.size
    assert (w, h) == (64, 32), "vanilla wolf skin must be 64x32"
    rng = np.random.default_rng(1112)
    noise = rng.normal(0.0, 1.0, (h, w))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = src.load()
    o = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (r + g + b) / 3.0
            sat = max(r, g, b) - min(r, g, b)
            t = (lum - 115.0) / 120.0 + noise[y, x] * 0.07
            # undersides: head bottom (throat), mane bottom (chest), body bottom (belly)
            under = in_box(x, y, 10, 0, 16, 4) or in_box(x, y, 36, 0, 44, 7) or in_box(x, y, 30, 14, 36, 20)
            if lum < 60:
                c = NOSE
            elif sat > 25:
                c = lerp(MUZ_DARK, MUZ_LIGHT, t)
            elif under:
                c = lerp(ASH_DARK, ASH_LIGHT, t)
            else:
                # darker along the spine: the top faces of the head, mane and body
                top = in_box(x, y, 4, 0, 10, 4) or in_box(x, y, 28, 0, 36, 7) or in_box(x, y, 24, 14, 30, 20)
                c = lerp(FUR_DARK, FUR_LIGHT, t - (0.25 if top else 0.0))
            o[x, y] = tuple(int(round(v)) for v in c) + (255,)

    def put(x, y, c):
        if px[x, y][3] != 0:
            o[x, y] = tuple(int(v) for v in c) + (255,)

    # head front (4..9, 4..9): heavy brow, amber eyes, a scar through the right eye
    for x in range(4, 10):
        put(x, 5, lerp(BROW, FUR_DARK, 0.4))
    put(5, 6, EYE)
    put(8, 6, EYE)
    put(9, 4, SCAR)
    put(8, 5, SCAR)
    put(7, 7, SCAR)
    # snout front (4..6, 14..16): black nose, dark lips and two fangs
    put(5, 14, NOSE)
    for x in range(1, 10):
        put(x, 16, MOUTH)
    put(4, 16, FANG)
    put(6, 16, FANG)
    # ears (16..21, 14..16): dark tips
    for x in range(16, 22):
        put(x, 14, BROW)
    # harness ring round the shoulders: rows 9-10 of the mane's side faces, hacksilver rings on it
    for x in range(21, 51):
        put(x, 9, LEATHER_HI)
        put(x, 10, LEATHER)
    for x in (24, 31, 39, 46):
        put(x, 9, SILVER_HI)
        put(x, 10, SILVER)
    # girth strap round the body: rows 22-23 of the body's side faces
    for x in range(18, 42):
        put(x, 22, LEATHER_HI)
        put(x, 23, LEATHER)
    for x in (21, 33):
        put(x, 22, SILVER_HI)
        put(x, 23, SILVER)
    # paws: dark pads stay, claws pale
    for x in (0, 2, 4, 6):
        put(x, 27, lerp(FANG, FUR_DARK, 0.45))
    return out


def howl(sr=44100, seconds=2.5):
    n = int(sr * seconds)
    t = np.arange(n) / sr
    # pitch contour (Hz): scoop up, a long held cry rising a little, then the fall
    kt = np.array([0.0, 0.10, 0.45, 1.10, 1.55, 2.05, 2.5])
    kf = np.array([250, 330, 500, 530, 545, 380, 300])
    f0 = np.interp(t, kt, kf)
    vib_depth = np.interp(t, [0, 0.6, 1.6, 2.5], [0.0, 0.006, 0.014, 0.01])
    f0 = f0 * (1.0 + vib_depth * np.sin(2 * np.pi * 5.3 * t))
    phase = 2 * np.pi * np.cumsum(f0) / sr
    sig = np.zeros(n)
    for k, amp in ((1, 1.0), (2, 0.38), (3, 0.16), (4, 0.07), (5, 0.03)):
        sig += amp * np.sin(k * phase + 0.3 * k)
    # breath: noise shaped by a one-pole low-pass, following the voice
    rng = np.random.default_rng(26)
    nz = rng.normal(0, 1, n)
    lp = np.zeros(n)
    acc = 0.0
    for i in range(n):
        acc += 0.08 * (nz[i] - acc)
        lp[i] = acc
    sig += 0.35 * lp / (np.max(np.abs(lp)) + 1e-9)
    env = np.interp(t, [0, 0.03, 0.18, 1.7, 2.15, 2.5], [0, 0.35, 1.0, 0.95, 0.35, 0.0])
    env = env * env * (3 - 2 * env)
    sig *= env
    # outdoor echo: a few quiet late taps
    out = sig.copy()
    for delay, gain in ((0.09, 0.22), (0.17, 0.13), (0.29, 0.07)):
        d = int(sr * delay)
        out[d:] += gain * sig[:-d]
    out /= np.max(np.abs(out)) + 1e-9
    return (out * 0.7).astype(np.float32), sr


def main():
    wolf = None
    if "--wolf" in sys.argv:
        wolf = sys.argv[sys.argv.index("--wolf") + 1]
    os.makedirs(os.path.dirname(TEX), exist_ok=True)
    warg_skin(load_wolf(wolf)).save(TEX)
    os.makedirs(os.path.dirname(OGG), exist_ok=True)
    data, sr = howl()
    sf.write(OGG, data, sr, format="OGG", subtype="VORBIS")
    info = sf.info(OGG)
    print(f"wolfcall_assets: {TEX} 64x32; {OGG} {info.channels} ch {info.samplerate} Hz {info.duration:.2f} s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
