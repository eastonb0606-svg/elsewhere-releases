"""Azmar headpiece equipment textures (64x32 humanoid layer, head box only): the painted base under the 3D ornaments (gen_ornaments.py).
Each helmet is a real equipment piece: gold nemes, linen ibis cowl, turquoise falcon hood, obsidian jackal mask, gold scarab war-crown."""
import os, sys, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngio import Image
EQ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "equipment", "humanoid")
GOLD = (212, 165, 55); GOLD_L = (245, 215, 110); GOLD_D = (150, 110, 30); LAPIS = (31, 63, 143); LAPIS_L = (62, 104, 190)
TURQ = (43, 179, 163); TURQ_D = (28, 120, 112); OBS = (22, 19, 26); OBS_L = (48, 42, 58); LINEN = (237, 227, 204); LINEN_D = (200, 188, 160); CARN = (176, 54, 43)

def sh(c, f): return tuple(max(0, min(255, int(v * f))) for v in c)

# head layout in the 64x32 layer: right (0,8) front (8,8) left (16,8) back (24,8) top (8,0) bottom (16,0); each face 8x8
FACES = {"right": (0, 8), "front": (8, 8), "left": (16, 8), "back": (24, 8), "top": (8, 0), "bottom": (16, 0)}

def face(im, name, rows, pal):
    x0, y0 = FACES[name]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch in pal: im.put(x0 + i, y0 + j, pal[ch])

def fill(im, name, fn):
    x0, y0 = FACES[name]
    for j in range(8):
        for i in range(8): im.put(x0 + i, y0 + j, fn(i, j))

def grain(im, seed):
    r = random.Random(seed)
    for y in range(16):
        for x in range(32):
            p = im.get(x, y)
            if p[3]: im.put(x, y, sh(p[:3], 1 + (r.random() - 0.5) * 0.07))

def helm(name, base, side_fn, top_fn, front, pal, back_fn=None):
    im = Image(64, 32)
    for f in ("right", "left"): fill(im, f, side_fn)
    fill(im, "back", back_fn or side_fn)
    fill(im, "top", top_fn)
    fill(im, "bottom", lambda i, j: sh(base, 0.6))
    face(im, "front", front, pal)
    grain(im, __import__('zlib').crc32(name.encode()))
    im.save(os.path.join(EQ, name + ".png"))

# NEMES: gold and lapis striped cloth all round; a gold mask with kohl-lined eyes, lapis brow, carnelian jewel
stripe = lambda i, j: LAPIS if (j // 1) % 2 == 0 and j < 4 else (LAPIS if j % 2 == 0 else GOLD)
helm("azmar_nemes", GOLD, lambda i, j: LAPIS if j % 2 == 0 else GOLD, lambda i, j: LAPIS if (i + 0) % 2 == 0 else GOLD, [
    "LGLGLGLG",
    "GLGLGLGL",
    "hhhhhhhh",
    "gggCCggg",
    "gOWLLWOg",
    "gOOggOOg",
    "gggggggg",
    "ghgOOghg",
], {"L": LAPIS, "G": GOLD, "g": GOLD, "h": GOLD_L, "C": CARN, "O": OBS, "W": LINEN})

# IBIS: linen cowl with a lapis crown band, gold rim; a pale mask with kohl eyes and a gold cheek line
helm("azmar_ibis", LINEN, lambda i, j: LAPIS if j in (2, 3) else (GOLD if j == 4 else LINEN), lambda i, j: LINEN if (i + j) % 4 else LINEN_D, [
    "LLLLLLLL",
    "LGLGLGLG",
    "gggggggg",
    "wwwwwwww",
    "wOWOOWOw",
    "wOOwwOOw",
    "wwwddwww",
    "wdwwwwdw",
], {"L": LAPIS, "G": GOLD, "g": GOLD, "w": LINEN, "d": LINEN_D, "O": OBS, "W": GOLD_L})

# FALCON: turquoise hood with gold eye-rims, lapis cheek feathers and obsidian brow
helm("azmar_falcon", TURQ, lambda i, j: LAPIS if (j >= 4 and (i + j) % 3 == 0) else (GOLD if j == 3 else (TURQ if (i + j) % 4 else TURQ_D)), lambda i, j: TURQ if (i + j) % 3 else TURQ_D, [
    "OOOOOOOO",
    "TTTTTTTT",
    "tTTTTTTt",
    "TGGTTGGT",
    "TGEGGEGT",
    "TTLTTLTT",
    "TTOTTOTT",
    "tTTTTTTt",
], {"O": OBS, "T": TURQ, "t": TURQ_D, "G": GOLD, "E": OBS, "L": LAPIS})

# JACKAL: obsidian mask with gold eyes (carnelian slit), gold brow and cheek lines, turquoise ear-ties
helm("azmar_jackal", OBS, lambda i, j: GOLD if j == 3 else (OBS_L if (i + j) % 5 == 0 else OBS), lambda i, j: GOLD if i in (3, 4) else (OBS if (i + j) % 3 else OBS_L), [
    "OOOOOOOO",
    "OGGGGGGO",
    "OOOOOOOO",
    "OOOOOOOO",
    "OGYOOYGO",
    "OOGOOGOO",
    "OOOOOOOO",
    "OTOOOOTO",
], {"O": OBS, "G": GOLD, "Y": GOLD_L, "T": TURQ, "C": CARN})

# SCARAB war-crown: gold with turquoise and carnelian inlay; a stern mask with turquoise-lined eyes
helm("azmar_scarab", GOLD, lambda i, j: TURQ if j == 3 else (GOLD_L if j == 2 else (GOLD if (i + j) % 3 else GOLD_D)), lambda i, j: GOLD if (i + j) % 3 else GOLD_D, [
    "ggggggggg"[:8],
    "GGGGGGGG",
    "hhhhhhhh",
    "ggTCCTgg",
    "gTWgGWTg",
    "gggggggg",
    "gGGggGGg",
    "gdgddgdg",
], {"g": GOLD, "G": GOLD_L, "h": GOLD_L, "T": TURQ, "C": CARN, "W": LINEN, "d": GOLD_D})
print("helms done")
