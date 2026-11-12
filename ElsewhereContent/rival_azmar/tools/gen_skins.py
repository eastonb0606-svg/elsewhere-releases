"""Azmar skins (64x64 player layout, plain linen-and-gold clothing, NO painted armor), trims, banner, shield pattern.
Run: python3 tools/gen_skins.py   (pure python, no Pillow)"""
import os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngio import Image, load

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "elsewhere", "textures")
ENT = os.path.join(ROOT, "entity", "kingdom", "azmar")
os.makedirs(ENT, exist_ok=True)

GOLD = (212, 165, 55); LAPIS = (31, 63, 143); TURQ = (43, 179, 163); OBS = (22, 19, 26); LINEN = (237, 227, 204); CARN = (176, 54, 43)
def dk(c, n=30): return tuple(max(0, v - n) for v in c)
def lt(c, n=25): return tuple(min(255, v + n) for v in c)

def rect(img, x0, y0, x1, y1, col, noise=7, seed=0):
    r = random.Random(seed)
    for x in range(x0, x1):
        for y in range(y0, y1):
            n = r.randint(-noise, noise)
            img.put(x, y, tuple(max(0, min(255, c + n)) for c in col[:3]))

def cube(img, u, v, w, h, d, col, seed):
    rect(img, u + d, v, u + d + w, v + d, col, seed=seed)
    rect(img, u + d + w, v, u + d + 2 * w, v + d, col, seed=seed + 1)
    rect(img, u, v + d, u + 2 * d + 2 * w, v + d + h, col, seed=seed + 2)

def skin(name, tone, tunic, kilt, collar, bead, band, hair=OBS, sash=None, bare=False, wrap=False, seed=1, hood=None, belt=GOLD):
    img = Image(64, 64)
    cube(img, 0, 0, 8, 8, 8, tone, seed)
    # wig: top, blunt fringe, long sides and back (Egyptian bob)
    rect(img, 8, 0, 16, 8, hair, seed=seed + 3)
    rect(img, 8, 8, 16, 10, hair, seed=seed + 4)                 # fringe
    rect(img, 0, 8, 8, 15, hair, seed=seed + 5); rect(img, 16, 8, 24, 15, hair, seed=seed + 6)   # sides
    rect(img, 24, 8, 32, 15, hair, seed=seed + 7)                # back
    rect(img, 8, 10, 9, 14, hair, seed=seed + 8); rect(img, 15, 10, 16, 14, hair, seed=seed + 8)  # side locks framing the face
    if hood:                                                    # headcloth stripe band (nemes style) over the wig
        for yy in (8, 10, 12):
            rect(img, 8, yy, 16, yy + 1, hood, 0, seed)
        for xx in range(0, 8, 2): rect(img, xx, 8, xx + 1, 15, hood, 0, seed)
    gold_fillet = lambda: [img.put(x, 10, GOLD) for x in range(8, 16)]
    gold_fillet()
    # kohl-lined eyes with winged tails
    for x in (9, 10, 13, 14): img.put(x, 12, (245, 245, 245))
    img.put(10, 12, (40, 30, 25)); img.put(13, 12, (40, 30, 25))
    for x in (9, 10, 13, 14): img.put(x, 11, OBS)
    img.put(8, 12, OBS); img.put(15, 12, OBS); img.put(9, 13, dk(OBS, -20)); img.put(14, 13, dk(OBS, -20))
    img.put(11, 13, dk(tone, 25)); img.put(12, 13, dk(tone, 25))
    img.put(11, 14, dk(tone, 55)); img.put(12, 14, dk(tone, 55))
    # body
    cube(img, 16, 16, 8, 12, 4, tunic, seed + 11)
    if not bare:
        for yy in range(23, 30, 2):                              # linen pleats
            for xx in list(range(20, 28)) + list(range(32, 40)): img.put(xx, yy, dk(tunic, 14))
    if sash:                                                     # diagonal sash
        for i in range(10):
            for xx in (21 + i * 6 // 10, 22 + i * 6 // 10): img.put(xx, 20 + i, sash)
    # usekh collar: gold arcs with lapis/turquoise beads, front and back
    for fx in (20, 32):
        for dy, (a, b) in enumerate(((0, 8), (0, 8), (1, 7), (2, 6), (3, 5))):
            for xx in range(fx + a, fx + b):
                img.put(xx, 20 + dy, GOLD if (xx + dy) % 3 else bead)
        img.put(fx + 3, 24, GOLD); img.put(fx + 4, 24, GOLD)
    rect(img, 20, 16, 28, 20, GOLD, 5, seed)                      # shoulder top = collar seen from above
    rect(img, 16, 20, 20, 22, GOLD, 0); rect(img, 28, 20, 32, 22, GOLD, 0)
    # belt + kilt flap
    rect(img, 20, 30, 28, 32, belt, 4, seed); img.put(23, 30, CARN); img.put(24, 30, CARN); img.put(23, 31, CARN); img.put(24, 31, CARN)
    rect(img, 32, 30, 40, 32, belt, 4, seed)
    for (u, v) in ((40, 16), (32, 48)):                           # arms: sleeves optional, bands gold, cuffs lapis
        cube(img, u, v, 4, 12, 4, tunic if not bare else tone, seed + 13)
        rect(img, u, v + 13, u + 16, v + 16, tone, seed=seed + 14)
        rect(img, u, v + 7, u + 16, v + 9, band, 0, seed)          # upper-arm band
        rect(img, u, v + 12, u + 16, v + 13, bead, 0, seed)        # wrist cuff
        rect(img, u + 4, v, u + 8, v + 4, collar, 5, seed)
        if wrap:
            for yy in range(v + 4, v + 12, 2): rect(img, u, yy, u + 16, yy + 1, dk(LINEN, 20), 0, seed)
    for (u, v) in ((0, 16), (16, 48)):                            # legs: kilt on the thigh, bare shin, gold-strap sandal
        cube(img, u, v, 4, 12, 4, kilt, seed + 16)
        rect(img, u, v + 10, u + 16, v + 14, tone, seed=seed + 17)
        rect(img, u, v + 14, u + 16, v + 16, (150, 105, 60), seed=seed + 18)
        rect(img, u, v + 13, u + 16, v + 14, GOLD, 0)
        rect(img, u, v + 10, u + 16, v + 11, band, 0)              # kilt hem
    img.save(os.path.join(ENT, name + ".png"))

TAN = (176, 124, 88); DARK = (132, 88, 60); PALE = (196, 148, 108)
skin("spearguard", TAN, LINEN, LINEN, GOLD, LAPIS, GOLD, seed=1)
skin("sunguard", PALE, LINEN, dk(LINEN, 10), GOLD, TURQ, GOLD, sash=LAPIS, seed=2)
skin("bowman", DARK, lt(LINEN, 5), (205, 190, 150), GOLD, TURQ, TURQ, seed=3)
skin("jemadar", TAN, LINEN, LINEN, GOLD, LAPIS, GOLD, sash=CARN, seed=4, hood=LAPIS)
skin("qaid", PALE, LINEN, LAPIS, GOLD, TURQ, GOLD, sash=CARN, seed=5, hood=GOLD)
skin("sand_dancer", TAN, TAN, LINEN, TURQ, TURQ, TURQ, bare=True, wrap=True, seed=6)
skin("naffatun", DARK, (140, 100, 70), (120, 85, 60), (184, 115, 51), (184, 115, 51), (184, 115, 51), seed=7, belt=(184, 115, 51))
skin("porter", DARK, (170, 140, 100), (150, 125, 90), (120, 100, 70), (120, 100, 70), (120, 100, 70), seed=8, belt=(110, 80, 50))
skin("herald", TAN, LINEN, LINEN, GOLD, TURQ, GOLD, sash=GOLD, seed=9, hood=TURQ)
skin("physician", PALE, LINEN, LINEN, TURQ, TURQ, GOLD, seed=10, hood=LINEN)
skin("scarab_bearer", TAN, LAPIS, LINEN, GOLD, TURQ, GOLD, seed=11, hood=GOLD)
skin("envoy", PALE, LINEN, LINEN, GOLD, TURQ, GOLD, sash=LAPIS, seed=12, hood=TURQ)
skin("sultana", TAN, LINEN, LINEN, GOLD, TURQ, GOLD, sash=CARN, seed=20, hood=LAPIS)
skin("wazir", PALE, LAPIS, LINEN, GOLD, TURQ, GOLD, sash=LINEN, seed=21, hood=None)
skin("falconer", TAN, TURQ, LINEN, GOLD, LAPIS, GOLD, seed=22)
skin("amir", DARK, CARN, LINEN, GOLD, LAPIS, GOLD, sash=GOLD, seed=23)

# ---- trim: scarab wings (grayscale, palette-mapped by the game) ---------------------------------------------------
def trim_layer(leggings):
    im = Image(64, 32)
    def g(v): return (v, v, v, 255)
    # helmet band (head box front/sides/back rows 10..11 + a scarab on the brow)
    if not leggings:
        for (x0, x1) in ((8, 16), (0, 8), (16, 24), (24, 32)):
            for x in range(x0, x1): im.put(x, 8, g(224)); im.put(x, 9, g(160))
        for x in (11, 12): im.put(x, 10, g(255))
        # chest front (20..27, 20..31): outspread wings over the collar + scarab
        for fx in (20, 32):
            for i in range(4):
                im.put(fx + i, 21 + (i // 2), g(224)); im.put(fx + 7 - i, 21 + (i // 2), g(224))
                im.put(fx + i, 22 + (i // 2), g(160)); im.put(fx + 7 - i, 22 + (i // 2), g(160))
            for y in range(24, 29): im.put(fx + 3, y, g(255)); im.put(fx + 4, y, g(255))
            im.put(fx + 2, 25, g(192)); im.put(fx + 5, 25, g(192)); im.put(fx + 2, 27, g(192)); im.put(fx + 5, 27, g(192))
        for ax in (44, 36 + 12):                                  # arm: a wing feather stripe
            for y in range(24, 28): im.put(ax, y, g(192)); im.put(ax + 1, y, g(224))
        for bx in (0, 8):                                         # boots: a small wing at the ankle (feet region of the humanoid layer)
            for y in (26, 27): im.put(bx + 2, y, g(224)); im.put(bx + 3, y, g(160))
    else:
        for lx in (0, 8):
            for y in (20, 21): 
                for x in range(lx, lx + 4): im.put(x, y, g(224))
            for y in (24, 25):
                for x in range(lx, lx + 4): im.put(x, y, g(160))
        for fx in (20, 32):
            for x in range(fx, fx + 8): im.put(x, 30, g(224)); im.put(x, 31, g(160))
            im.put(fx + 3, 29, g(255)); im.put(fx + 4, 29, g(255))
    return im

for sub, lg in (("humanoid", False), ("humanoid_leggings", True)):
    d = os.path.join(ROOT, "trims", "entity", sub); os.makedirs(d, exist_ok=True)
    trim_layer(lg).save(os.path.join(d, "azmar.png"))
    open(os.path.join(d, "azmar.png.mcmeta"), "w").write('{\n  "palette" : {\n    "base_palette" : "trim_base"\n  }\n}\n')

# ---- banner pattern (white mask, 64x64): a scarab holding a sun disc between outspread wings -----------------------
def scarab_banner(img, ox, w, top):
    """w x ~38 area starting at ox, centred horizontally."""
    c = ox + w // 2
    W = (240, 240, 240, 255)
    # wings: feathered fans left and right of the body, rows top+8 .. top+20
    for side in (-1, 1):
        for row in range(7):
            length = 8 - row // 2
            y = top + 9 + row * 2
            for k in range(length):
                for yy in (y, y + 1):
                    if (k + row) % 3: img.put(c + side * (2 + k), yy, W)
        for k in range(8): img.put(c + side * (2 + k), top + 8, W)
    # sun disc above (ring with a dot), held up by the forelegs
    for dx, dy in ((-2, 0), (-1, -1), (0, -1), (1, -1), (2, 0), (2, 1), (-2, 1), (-1, 2), (0, 2), (1, 2)):
        img.put(c + dx, top + 3 + dy + 1, W)
    img.put(c, top + 4, W); img.put(c, top + 5, W)
    # beetle body: head, thorax, wing-case with a centre line, legs
    for dx in (-1, 0, 1): img.put(c + dx, top + 7, W)
    for y in range(top + 8, top + 22):
        half = 3 if y < top + 18 else 2
        for dx in range(-half, half + 1):
            if dx != 0 or y % 2: img.put(c + dx, y, W)
    for y, ln in ((top + 10, 2), (top + 14, 3), (top + 18, 2)):
        for k in range(1, ln + 1): img.put(c - 3 - k, y, W); img.put(c + 3 + k, y, W)
    # ground line of small glyph bars under the scarab
    for k in range(-6, 7, 2): img.put(c + k, top + 27, W); img.put(c + k, top + 28, W)
    for k in range(-5, 6, 2): img.put(c + k, top + 31, W)
b = Image(64, 64)
scarab_banner(b, 1, 20, 1); scarab_banner(b, 22, 20, 1)
for x in range(1, 21): pass
d = os.path.join(ROOT, "entity", "banner"); os.makedirs(d, exist_ok=True); b.save(os.path.join(d, "azmar.png"))
s = Image(64, 64)
scarab_banner(s, 2, 12, 2)
d = os.path.join(ROOT, "entity", "shield"); os.makedirs(d, exist_ok=True); s.save(os.path.join(d, "azmar.png"))
print("skins/trims/banner done")
