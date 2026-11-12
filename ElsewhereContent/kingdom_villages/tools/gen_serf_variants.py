#!/usr/bin/env python3
"""Serf-tier skins for the rival kingdoms (3.0.0 repaint, s-townsfolk-art). QA found the 2.1 recolours of serf.png
"look identical", so each variant is now its own outfit and silhouette in its kingdom's palette (plain clothing, no
painted armor). serf.png (Aldermark: brown tunic, straw hair, grey hose) is the 2.0 skin and stays as it is.
- Thrall (Hrafnholt levy): bark-brown hood up, undyed wadmal, iron thrall collar, leg wraps, bare feet.
- Hired Porter (Azmar): bare sun-dark chest, white headcloth with a lapis band, lapis sash, linen kilt, rope sandals.
- Peat-Cutter (Mirewick): faded purple smock, crimson cap, sackcloth apron, peat mud to the knee, wooden clogs.
Run: python3 tools/gen_serf_variants.py   (Pillow; uses kingdom/tools/skinkit.py)"""
import os, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "kingdom", "tools"))
from skinkit import *

D = os.path.join(HERE, "..", "src/main/resources/assets/elsewhere/textures/entity/village")
IRON = (108, 112, 118); ROPE = (186, 158, 98); DLEATHER = (74, 52, 34)


def face(s, tone, hair_c, style, beard_s=None, iris=(80, 70, 50)):
    head_base(s, tone); ears(s, tone)
    hair(s, hair_c, style)
    eyes(s, iris); brows(s, dk(hair_c, 18)); nose_mouth(s, tone)
    if beard_s:
        beard(s, hair_c, beard_s, tone)


def thrall(s):
    """Hrafnholt: green-and-brown realm; thralls wear undyed wadmal and the iron collar. Hood up, so the shape differs."""
    tone = (210, 168, 132); hood = (107, 74, 43); wad = (126, 114, 94)
    face(s, tone, (74, 56, 40), "cropped", "short", (70, 80, 70))
    s.fill("hat", hood, 4, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, hood, 4)
    s.frect("hat", "front", 0, 2, 1, 8, hood, 4); s.frect("hat", "front", 7, 2, 8, 8, hood, 4)
    s.frect("hat", "front", 1, 1, 7, 2, dk(hood, 22), 1)
    for f in ("right", "left", "back"):
        s.frect("hat", f, 0, 0, 8, 8, hood, 4)
    s.frect("hat", "back", 3, 0, 5, 8, dk(hood, 18), 2)      # seam
    shoulder_cape(s, hood, rows=3, dag=None)
    s.fclear("jacket", "front", 2, 0, 6, 3)                 # hood opens at the throat: the iron collar shows
    weave(s, "body", wad, 9)
    for p in ARMS:
        weave(s, p, wad, 9)
    forearms(s, tone, 7)
    for p in ARMS:
        s.ring(p, 6, dk(wad, 26), 2)
        s.ring(p, 11, mix(tone, (90, 70, 50), 0.35), 3)
    s.frect("rarm", "front", 0, 2, 4, 5, (70, 96, 60), 3)    # a faded green patch on the sleeve
    iron_collar(s, IRON)
    for p in LEGS:
        weave(s, p, dk(wad, 28), 6)
        for r in range(4):
            s.ring(p, r, wad, 5)
        for i in range(s.ring_w(p)):
            if i % 3 == 1:
                s.ringpx(p, i, 4, dk(wad, 10))               # ragged tunic hem
    belt(s, 8, ROPE)
    s.fpx("body", "front", 5, 9, ROPE); s.fpx("body", "front", 5, 10, dk(ROPE, 26))
    legwraps(s, (150, 140, 120), (96, 80, 60), 5, 9)
    barefoot(s, tone, (70, 56, 40))


def porter(s):
    """Azmar: sun-dark labourer, white headcloth with a lapis band and a lapis sash (no gold: a hired hand)."""
    tone = (124, 82, 54); linen = (237, 227, 204); lapis = (31, 63, 143); obs = (22, 19, 26)
    head_base(s, tone); ears(s, tone)
    hair(s, (26, 22, 24), "cropped")
    eyes(s, (40, 30, 25), kohl=obs)
    s.fpx("head", "front", 3, 5, dk(tone, 24)); s.fpx("head", "front", 4, 5, dk(tone, 24))
    s.fpx("head", "front", 3, 6, dk(tone, 50)); s.fpx("head", "front", 4, 6, dk(tone, 50))
    s.fill("hat", linen, 3, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, linen, 3)
    for f in ("right", "left", "back"):
        s.frect("hat", f, 0, 0, 8, 6, linen, 3)
    for f in SIDES:
        s.frect("hat", f, 0, 1, 8, 2, lapis, 1)
    s.frect("hat", "back", 2, 6, 6, 8, dk(linen, 18), 2)     # cloth tail at the nape
    s.fill("body", tone, 3)
    for x in (2, 5):
        s.fpx("body", "front", x, 4, dk(tone, 16))
    for p in ARMS + LEGS:
        s.fill(p, tone, 3)
    diag(s, lapis, x0=0, x1=5, rows=(0, 10), width=2)
    s.frect("body", "front", 5, 9, 7, 11, lapis, 2)          # sash knot at the hip
    for p in LEGS:
        for r in range(0, 7):
            s.ring(p, r, linen, 3)
        s.ring(p, 6, dk(linen, 30), 1)
    for x in range(2, 4):
        for y in range(0, 7):
            s.fpx("rleg", "front", x, y, dk(linen, 10), 2)
    for x in range(0, 2):
        for y in range(0, 7):
            s.fpx("lleg", "front", x, y, dk(linen, 10), 2)
    flare(s, 4, 7)
    belt(s, 10, ROPE); belt(s, 11, linen)
    for p in ("rarm",):                                      # rope coil carried on the shoulder
        s.frect(p, "top", 0, 0, 4, 4, ROPE, 3)
        for f in SIDES:
            s.frect(p, f, 0, 0, 4, 2, ROPE, 3)
            s.frect(p, f, 0, 1, 4, 2, dk(ROPE, 28), 1)
    sandals(s, tone, ROPE)


def peat_cutter(s):
    """Mirewick: faded royal-purple smock, crimson cap, sackcloth apron, mud to the knee, clogs."""
    tone = (222, 176, 142); smock = (104, 76, 122); crimson = (158, 27, 50); peat = (58, 47, 38)
    face(s, tone, (120, 80, 50), "short", "stubble", (80, 90, 70))
    s.fill("hat", crimson, 3, faces=("top",))
    for f in SIDES:
        for x in range(8):
            for y in range(2):
                s.fpx("hat", f, x, y, crimson if x % 2 else dk(crimson, 14), 2)
    s.fpx("hat", "back", 3, 2, dk(crimson, 10)); s.fpx("hat", "back", 4, 2, dk(crimson, 10))
    weave(s, "body", smock, 5)
    for p in ARMS:
        weave(s, p, smock, 5)
    forearms(s, tone, 6)
    for p in ARMS:
        s.ring(p, 5, dk(smock, 26), 2)
        s.ring(p, 11, mix(tone, peat, 0.45), 3)
    s.frect("body", "front", 3, 0, 5, 2, dk(smock, 30), 1)   # neck slit
    apron(s, (168, 150, 112), top=5, legs_to=6, strap=(120, 100, 70))
    for y in range(6, 12, 2):
        s.fpx("body", "front", 2 + y % 3, y, (140, 122, 90))
    for p in LEGS:
        weave(s, p, (88, 76, 66), 4)
        s.rows(p, 6, 10, tone, 3)                             # breeches rolled to the knee
        s.ring(p, 5, (70, 60, 52), 2)
    mud(s, LEGS, 5, 10, peat, 0.55)
    clogs(s, (190, 156, 100))
    mud(s, LEGS, 10, 12, (78, 62, 48), 0.4)


for name, fn in (("serf_thrall", thrall), ("serf_porter", porter), ("serf_peat_cutter", peat_cutter)):
    s = Skin(sum(ord(c) * (i + 1) for i, c in enumerate(name)))
    fn(s)
    s.save(os.path.join(D, name + ".png"))
print("ok")
