"""Herdcaller's Standard entity texture (64x64) for HerdcallerStandardModel (src/client/java/.../s3/pennant/client/).
Owner: s-pennant (3.0 swarm). Run: ROOT/_tools/venv/bin/python -I gear3/tools/pennant_art.py [--preview out.png]
Palette = the herdcaller_pennant item sprite (Ideas/overhaul/sprites.json): moss-green field with a dark hem and light trim, an
amber resin medallion with a white heart, two dark chevrons, a swallow tail; a dark-oak pole and crossbar with resin knobs, a
resin cord under the bar and an iron-dark ferrule. Symmetric left/right, front = back, so the mirrored box faces all read the same.
Box UV layout (vanilla): top (u+d, v) w x d, bottom (u+d+w, v); strip at v+d: -x side d x h, front w x h, +x side d x h, back w x h.
"""
import os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "herdcaller_standard.png")
W = H = 64
rng = random.Random(2630)


def hx(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


O, D, M, L = hx("#1c2a12"), hx("#3d5e22"), hx("#6a9a36"), hx("#a8d468")
M2 = hx("#5e8a30")  # fold shade of the field
A, A_HI, A_LO = hx("#e0a92a"), hx("#f4d27a"), hx("#a8781a")
B, B2 = hx("#ffffff"), hx("#e6eedc")
WOOD, WOOD_HI, GRAIN = hx("#6b4a2a"), hx("#8a6238"), hx("#3b2414")
IRON, IRON_HI = hx("#2e2e30"), hx("#55575a")
CLEAR = (0, 0, 0, 0)

img = Image.new("RGBA", (W, H), CLEAR)
px = img.load()


def put(x, y, c):
    px[x, y] = c


def rect(x, y, w, h, c):
    for i in range(w):
        for j in range(h):
            put(x + i, y + j, c)


def box_faces(u, v, w, h, d):
    """Rectangles (x, y, w, h) of a box's faces at texOffs(u, v)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "west": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "east": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }


# ---------------------------------------------------------------- flag: 16 x 28 x 1 at (0, 0)
def flag_face(ox, oy):
    for y in range(28):
        for x in range(16):
            c = M2 if (x in (5, 10) and 3 <= y <= 25) else M
            if x in (2, 13) or y == 2:
                c = L
            if x in (1, 14) or y == 1:
                c = D
            if x in (0, 15) or y == 0:
                c = O
            put(ox + x, oy + y, c)
    # medallion: 8 x 8 amber, beveled, white 4 x 4 heart
    mx, my = ox + 4, oy + 5
    for j in range(8):
        for i in range(8):
            if (i in (0, 7)) and (j in (0, 7)):
                continue
            edge = i in (0, 7) or j in (0, 7)
            c = A
            if edge:
                c = A_HI if j == 0 or (i == 0 and j < 4) or (i == 7 and j < 4) else A_LO
            put(mx + i, my + j, c)
    for j in range(4):
        for i in range(4):
            put(mx + 2 + i, my + 2 + j, B2 if (i in (0, 3) and j in (0, 3)) else B)
    # two downward chevrons (2 px thick) below the medallion
    for top in (15, 20):
        for k in range(5):
            for x in (3 + k, 4 + k, 12 - k, 11 - k):
                if 3 <= x <= 12:
                    put(ox + x, oy + top + k, D)
    # bottom hem
    for x in range(1, 15):
        put(ox + x, oy + 27, D)


f = box_faces(0, 0, 16, 28, 1)
flag_face(*f["front"][:2])
flag_face(*f["back"][:2])
for k in ("west", "east"):
    x, y, w, h = f[k]
    for j in range(h):
        put(x, y + j, O if j in (0, h - 1) else D)
for k in ("top", "bottom"):
    x, y, w, h = f[k]
    rect(x, y, w, h, D)
    put(x, y, O)
    put(x + w - 1, y, O)


# ---------------------------------------------------------------- tails: two 6 x 5 x 1 boxes, (0, 30) at x -8..-2 and (14, 30) at x 2..8
# A swallow tail: each tail's outer edge runs straight down, its inner edge slopes out, so the notch between them is a V.
# ModelPart UVs: the north (front) face's texture column 0 is the box's min x, the south (back) face's column 0 its max x.
def tail_face(ox, oy, outer_at_left):
    for y in range(5):
        n = 6 - y  # opaque width: 6, 5, 4, 3, 2
        cols = range(0, n) if outer_at_left else range(6 - n, 6)
        for x in cols:
            inner = (x == n - 1) if outer_at_left else (x == 6 - n)
            outer = (x == 0) if outer_at_left else (x == 5)
            c = M
            if outer:
                c = O
            elif inner:
                c = D if y == 0 else O
            elif y == 4:
                c = O
            elif x == (2 if outer_at_left else 3):
                c = M2
            put(ox + x, oy + y, c)


for u, left in ((0, True), (14, False)):  # left = the tail at negative x: its outer edge is min x
    t = box_faces(u, 30, 6, 5, 1)
    tail_face(*t["front"][:2], left)       # north: column 0 = min x
    tail_face(*t["back"][:2], not left)    # south: column 0 = max x
    for k in ("west", "east"):
        x, y, w, h = t[k]
        outer = (k == "west") == left
        for j in range(5 if outer else 1):  # the straight outer edge is a full side; the sloped inner one only its top
            put(x, y + j, O if outer else D)
    for k in ("top", "bottom"):
        x, y, w, h = t[k]
        rect(x, y, w, h, D)


# ---------------------------------------------------------------- pole: 2 x 44 x 2 at (56, 0)
p = box_faces(56, 0, 2, 44, 2)
for k in ("west", "front", "east", "back"):
    x, y, w, h = p[k]
    for j in range(h):
        for i in range(w):
            c = WOOD_HI if i == 0 else WOOD
            if rng.random() < 0.10:
                c = GRAIN
            if j in (3, 6):
                c = O  # cord edges
            elif j in (4, 5):
                c = A if i == 0 else A_LO  # resin cord under the crossbar
            elif j >= h - 3:
                c = IRON_HI if (i == 0 and j == h - 3) else IRON  # ferrule
            put(x + i, y + j, c)
rect(*p["top"], WOOD_HI)
put(p["top"][0], p["top"][1], GRAIN)
rect(*p["bottom"], IRON)

# ---------------------------------------------------------------- bar: 18 x 2 x 2 at (0, 46)
b = box_faces(0, 46, 18, 2, 2)
for k in ("top", "bottom", "front", "back"):
    x, y, w, h = b[k]
    for j in range(h):
        for i in range(w):
            c = WOOD_HI if j == 0 else WOOD
            if 2 < i < w - 3 and rng.random() < 0.12:
                c = GRAIN
            if i in (0, 1, w - 2, w - 1):
                c = A_HI if j == 0 else A  # resin knobs on both ends
            put(x + i, y + j, c)
for k in ("west", "east"):
    x, y, w, h = b[k]
    rect(x, y, w, h, A)
    put(x, y, A_HI)
    put(x + w - 1, y + h - 1, A_LO)

# ---------------------------------------------------------------- finial: 3 x 3 x 3 at (0, 52), a resin knob
fn = box_faces(0, 52, 3, 3, 3)
for k, (x, y, w, h) in fn.items():
    for j in range(h):
        for i in range(w):
            c = A
            if k == "top" or (j == 0 and i == 0):
                c = A_HI
            if k == "bottom" or (j == h - 1 and i == w - 1):
                c = A_LO
            put(x + i, y + j, c)
put(fn["top"][0] + 1, fn["top"][1] + 1, B)


def preview(path):
    """Front view of the assembled standard (orthographic, 8x), next to the raw texture (4x)."""
    S = 8
    canvas = Image.new("RGBA", (24 * S + 64 * 4 + 16, 48 * S), (120, 150, 190, 255))
    def blit(face, dx, dy):
        x, y, w, h = face
        crop = img.crop((x, y, x + w, y + h)).resize((w * S, h * S), Image.NEAREST)
        canvas.alpha_composite(crop, (dx * S, dy * S))
    # model coords: ground y = 24 -> row 47; x -12..12 -> col 0..24
    gx, gy = 12, 47 - 24
    blit(p["front"], gx - 1, gy - 20)
    blit(b["front"], gx - 9, gy - 19)
    blit(fn["front"], gx - 2, gy - 23)
    blit(f["front"], gx - 8, gy - 17)
    blit(box_faces(0, 30, 6, 5, 1)["front"], gx - 8, gy + 11)
    blit(box_faces(14, 30, 6, 5, 1)["front"], gx + 2, gy + 11)
    canvas.alpha_composite(img.resize((256, 256), Image.NEAREST), (24 * S + 16, 0))
    canvas.save(path)


if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("pennant_art: wrote", OUT)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
