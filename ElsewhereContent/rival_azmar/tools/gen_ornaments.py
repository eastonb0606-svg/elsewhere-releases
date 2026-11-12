"""Azmar 3D head ornaments: cube geometry + one painted atlas.

Writes
  src/main/resources/assets/elsewhere/azmar_ornaments.json            (cubes, read by the client layer)
  src/main/resources/assets/elsewhere/textures/entity/azmar_ornaments.png
Coordinates are model-space pixels of the player HEAD part (x right, y DOWN, z back; head cube is x -4..4, y -8..0, z -4..4,
the armor layer is 1 px bigger). Authoring helper `box` takes UP = height above the neck line, so the head top is up 8
and the helmet top up 9. Cube sizes are whole pixels. Palette: RIVALS-DECISIONS section 9 (no sun discs, no glass).
"""
import json, math, os, random, sys, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngio import Image

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "elsewhere")
GOLD = (212, 165, 55); LAPIS = (31, 63, 143); TURQ = (43, 179, 163); OBS = (22, 19, 26); LINEN = (237, 227, 204); CARN = (176, 54, 43)
GOLD_D = (160, 116, 34); GOLD_L = (240, 205, 100); LAPIS_L = (62, 104, 190); TURQ_D = (28, 124, 114)

FACE_SHADE = {"top": 1.12, "front": 1.0, "right": 0.88, "left": 0.88, "back": 0.78, "bottom": 0.62}
ORN = {}   # name -> list of cubes
YOFF = 0   # 0 for head ornaments (neck line); 24 for whole-entity models (ground line)
SHEET = []  # all cubes, for packing


def sc(c, f): return tuple(max(0, min(255, int(round(v * f)))) for v in c[:3])


def box(orn, name, x, up, z, w, h, d, style, faces=None, pivot=None, rot=(0, 0, 0)):
    """Cube with min corner x/z and bottom at `up`; w/h/d whole pixels. rot in degrees (rx forward-lean, ry, rz sideways)."""
    assert w == int(w) and h == int(h) and d == int(d), (orn, name, w, h, d)
    assert all(k['name'] != name for k in ORN.get(orn, [])), (orn, name)
    c = dict(orn=orn, name=name, x=x, y=YOFF - (up + h), z=z, w=int(w), h=int(h), d=int(d), style=style, faces=faces or {},
             pivot=(pivot if pivot else (x + w / 2.0, YOFF - up, z + d / 2.0)), rot=rot)
    ORN.setdefault(orn, []).append(c); SHEET.append(c)
    return c


def piv(x, up, z): return (x, YOFF - up, z)


def S(base): return ("solid", base)
def ST(a, b, per=2, vert=False): return ("stripes", a, b, per, vert)
def FE(base, edge): return ("feather", base, edge)
def GL(base, acc): return ("glyph", base, acc)


def band(orn, base, acc, up=5.2):
    """A raised ring round the helmet: front, back, left, right."""
    box(orn, "band_f", -5.5, up, -5.9, 11, 2, 1, GL(base, acc))
    box(orn, "band_b", -5.5, up, 4.9, 11, 2, 1, GL(base, acc))
    box(orn, "band_l", 4.9, up, -5.0, 1, 2, 10, GL(base, acc))
    box(orn, "band_r", -5.9, up, -5.0, 1, 2, 10, GL(base, acc))


# ---------------------------------------------------------------------------------------------------------------------
# NEMES OF THE URAEUS (Sultana): striped royal headcloth, flared wings, lappets, queue, gold cobra on the brow.
o = "nemes"
box(o, "cloth_top", -5.5, 8.8, -5.5, 11, 1, 11, ST(GOLD, LAPIS, 2))
box(o, "flare_r", 5.0, 0.0, -3.5, 2, 8, 8, ST(GOLD, LAPIS, 2), pivot=piv(5.0, 8.0, 0), rot=(0, 0, -13))
box(o, "flare_l", -7.0, 0.0, -3.5, 2, 8, 8, ST(GOLD, LAPIS, 2), pivot=piv(-5.0, 8.0, 0), rot=(0, 0, 13))
box(o, "lappet_r", 4.8, -6.0, -4.3, 3, 14, 2, ST(GOLD, LAPIS, 2))
box(o, "lappet_l", -7.8, -6.0, -4.3, 3, 14, 2, ST(GOLD, LAPIS, 2))
box(o, "queue_u", -3.0, 0.0, 5.0, 6, 9, 1, ST(GOLD, LAPIS, 2))
box(o, "queue_l", -2.5, -8.0, 3.2, 5, 8, 1, ST(GOLD, LAPIS, 2))
band(o, GOLD_L, LAPIS)
box(o, "gem", -1.0, 5.1, -6.3, 2, 2, 1, S(LAPIS_L))
# the uraeus: coil on the brow, rising hood with flared sides, head with carnelian eyes leaning to strike
box(o, "ur_coil", -1.5, 6.9, -6.9, 3, 2, 2, GL(GOLD, LAPIS))
box(o, "ur_neck", -1, 8.9, -6.4, 2, 2, 1, S(GOLD))
box(o, "ur_hood", -2, 9.9, -6.9, 4, 3, 1, S(GOLD), faces={"front": ("hood", GOLD, TURQ)})
box(o, "ur_hood_r", 2.0, 9.6, -6.6, 1, 3, 1, S(GOLD_D), pivot=piv(2.0, 9.6, -6.1), rot=(0, 0, -14))
box(o, "ur_hood_l", -3.0, 9.6, -6.6, 1, 3, 1, S(GOLD_D), pivot=piv(-2.0, 9.6, -6.1), rot=(0, 0, 14))
box(o, "ur_head", -1.5, 12.9, -7.6, 3, 2, 2, S(GOLD), faces={"front": ("eyes", GOLD, CARN)}, pivot=piv(0, 12.9, -6.4), rot=(24, 0, 0))
box(o, "ur_tongue", -0.5, 12.9, -9.2, 1, 1, 2, S(CARN), pivot=piv(0, 12.9, -6.4), rot=(24, 0, 0))

# ---------------------------------------------------------------------------------------------------------------------
# IBIS CREST (Wazir): the head of Thoth's ibis on the brow with a long curved beak, a fan of plumes behind, lapis band.
o = "ibis"
band(o, LAPIS, GOLD_L)
box(o, "gem", -1.0, 5.1, -6.3, 2, 2, 1, S(GOLD_L))
box(o, "neck", -1.0, 9.0, -4.9, 2, 5, 2, ST(LINEN, LINEN_D := sc(LINEN, 0.82), 1), pivot=piv(0, 9.0, -3.9), rot=(10, 0, 0))
box(o, "head", -2.0, 13.5, -7.2, 4, 3, 4, S(OBS), faces={"front": ("eyes_gold", OBS, GOLD_L), "left": ("eyes_side", OBS, GOLD_L), "right": ("eyes_side", OBS, GOLD_L)})
box(o, "beak1", -1.0, 13.7, -10.4, 2, 2, 4, ST(GOLD_D, OBS, 2, True), faces={"top": S(OBS)})
box(o, "beak2", -1.0, 12.3, -12.4, 2, 2, 3, S(GOLD_D))
box(o, "beak3", -1.0, 10.7, -13.1, 2, 2, 2, S(OBS))
box(o, "wattle", -1.0, 12.2, -7.2, 2, 2, 1, S(LAPIS))
for i, (ang, col) in enumerate([(-42, LAPIS), (-21, LINEN), (0, TURQ), (21, LINEN), (42, LAPIS)]):
    box(o, f"plume{i}", -1.0, 8.2, 5.0, 2, 9 - abs(i - 2), 1, FE(col, GOLD), pivot=piv(0, 8.2, 5.5), rot=(-26, 0, ang * -1))

# ---------------------------------------------------------------------------------------------------------------------
# FALCON HELM (Falconer): hooked beak, heavy brow, Eye-of-Horus markings, three swept feather layers each side, crest.
o = "falcon"
band(o, GOLD, LAPIS)
box(o, "brow", -5.5, 6.9, -6.0, 11, 1, 1, S(OBS))
box(o, "beak_top", -1.0, 3.4, -8.6, 2, 3, 3, S(GOLD))
box(o, "beak_hook", -1.0, 1.4, -8.4, 2, 2, 2, S(GOLD_D))
box(o, "beak_tip", -1.0, 0.2, -7.8, 2, 1, 1, S(OBS))
for sx in (1, -1):
    xe = 2.4 if sx > 0 else -4.4
    box(o, "ring" + str(sx), xe, 3.7, -5.9, 2, 2, 1, S(LAPIS), faces={"front": ("eyes_gold", LAPIS, GOLD_L)})
    box(o, "tear" + str(sx), (3.0 if sx > 0 else -4.0), 0.9, -5.9, 1, 3, 1, S(OBS))
    for k in range(3):
        xr = (5.0 + 0.4 * k) if sx > 0 else (-6.0 - 0.4 * k)
        col = [GOLD, LAPIS, TURQ][k]
        box(o, f"wing{sx}_{k}", xr, 6.6 - 2.2 * k, -1.0, 1, 2, 5 + 2 * k, FE(col, GOLD_L if k != 0 else LAPIS),
            pivot=piv(xr, 8.0 - 2.2 * k, 0), rot=(0, 0, -9 * k * sx))
box(o, "crest", -1.0, 9.0, -1.0, 2, 4, 5, FE(OBS, GOLD), pivot=piv(0, 9.0, 1.5), rot=(-20, 0, 0))

# ---------------------------------------------------------------------------------------------------------------------
# JACKAL HELM (Jackal Guard): tall pointed ears with gold insides, a long obsidian muzzle with a gold nose, gold brow and ridge.
o = "jackal"
band(o, GOLD, GOLD_L, up=5.6)
box(o, "ridge", -1.0, 9.0, -3.0, 2, 1, 7, GL(GOLD, OBS))
for sx in (1, -1):
    cx = 3.0 * sx
    for k, (up, w, h, dz) in enumerate([(8.8, 3, 3, 1.8), (11.8, 2, 3, 1.8), (14.8, 1, 2, 1.8)]):
        x0 = cx - w / 2.0 + (0.45 * k) * (-sx)
        box(o, f"ear{sx}_{k}", x0, up, -0.8, w, h, 2, S(OBS), faces={"front": ("ear_in", OBS, GOLD)}, pivot=piv(cx, 8.8, 0.2), rot=(-4, 0, -7 * sx))
box(o, "bridge", -1.0, 4.4, -8.6, 2, 2, 4, S(OBS))
box(o, "snout", -1.5, 1.8, -9.9, 3, 3, 5, S(OBS), faces={"front": ("nose", OBS, GOLD)})
box(o, "jaw", -1.0, 0.4, -9.3, 2, 2, 4, S(OBS))
box(o, "nose", -1.5, 3.0, -10.9, 3, 2, 1, S(GOLD))
box(o, "brow", -5.5, 7.0, -6.0, 11, 1, 1, GL(GOLD, OBS))

# ---------------------------------------------------------------------------------------------------------------------
# HORNED SCARAB CROWN (Amir): a turquoise scarab with a forward horn on the brow, a lyre of two gold horns, elytra cheek-plates.
o = "scarab"
band(o, GOLD, TURQ)
box(o, "shell", -3.0, 9.0, -4.0, 6, 2, 7, ("shell", TURQ, OBS, GOLD))
box(o, "thorax", -2.0, 9.0, -6.0, 4, 2, 2, GL(GOLD_D, OBS))
box(o, "clypeus", -1.5, 9.0, -7.6, 3, 1, 2, S(GOLD))
box(o, "horn_f1", -1.0, 10.0, -8.6, 2, 2, 2, S(GOLD), pivot=piv(0, 10.0, -7.0), rot=(18, 0, 0))
box(o, "horn_f2", -1.0, 11.8, -9.7, 2, 1, 2, S(CARN), pivot=piv(0, 10.0, -7.0), rot=(18, 0, 0))
for sx in (1, -1):
    def hx(cx, w): return cx * sx - w / 2.0
    box(o, f"horn{sx}_1", hx(3.4, 2), 9.4, -2.6, 2, 3, 2, GL(GOLD, CARN), pivot=piv(3.4 * sx, 9.4, -1.6), rot=(0, 0, -12 * sx))
    box(o, f"horn{sx}_2", hx(4.2, 2), 12.2, -2.6, 2, 3, 2, GL(GOLD, CARN))
    box(o, f"horn{sx}_3", hx(3.6, 2), 14.9, -2.6, 2, 2, 2, S(GOLD_L))
    box(o, f"horn{sx}_4", hx(2.4, 1), 16.2, -2.6, 1, 2, 2, S(CARN))
    xe = 5.0 if sx > 0 else -7.0
    box(o, f"elytra{sx}", xe, 0.5, -3.5, 2, 8, 8, ("shell", TURQ, OBS, GOLD), pivot=piv(5.0 * sx, 8.5, 0), rot=(0, 0, -11 * sx))
box(o, "mane", -1.0, 8.9, 3.4, 2, 5, 2, GL(GOLD, OBS), pivot=piv(0, 8.9, 4.4), rot=(-22, 0, 0))

# ---------------------------------------------------------------------------------------------------------------------
# FALCON OF THE FALCONER: a small hooded falcon (model space: y down, ground at 24, front -z). Parts named for the animation
# (wing_r, wing_l are modelled spread out to the side; the model folds them by rotating about z).
YOFF = 24
o = "falcon_bird"
for sx in (1, -1):
    box(o, "leg" + ("r" if sx > 0 else "l"), 1.0 * sx - 0.5, 0.8, -0.5, 1, 3, 1, S(GOLD_D))
    box(o, "talon" + ("r" if sx > 0 else "l"), 1.0 * sx - 0.5, 0.0, -1.6, 1, 1, 2, S(OBS))
box(o, "body", -2, 3.6, -3, 4, 4, 7, S(LAPIS), faces={"front": S(GOLD), "bottom": S(LINEN), "top": ("glyph", LAPIS, GOLD_L), "back": ST(LAPIS, GOLD, 1)})
box(o, "breast", -1.5, 3.8, -3.6, 3, 3, 1, ST(GOLD, GOLD_L, 1))
box(o, "head", -1.5, 7.3, -5.4, 3, 3, 3, S(LAPIS), faces={"front": S(LAPIS_L)})
box(o, "hood_band", -2, 8.4, -5.9, 4, 1, 4, GL(GOLD, LAPIS))
box(o, "plume", -0.5, 10.4, -4.6, 1, 3, 2, FE(TURQ, GOLD), pivot=piv(0, 10.4, -3.6), rot=(-24, 0, 0))
box(o, "beak", -0.5, 6.7, -7.5, 1, 2, 2, S(GOLD))
box(o, "beak_hook", -0.5, 5.8, -7.2, 1, 1, 1, S(OBS))
box(o, "tail", -1.5, 3.9, 3.8, 3, 1, 5, ST(LAPIS, GOLD, 1, True), faces={"top": ("glyph", LAPIS, GOLD_L)})
box(o, "wing_r", 2.0, 7.0, -1.5, 7, 1, 7, ("wing", False), pivot=piv(2.0, 7.4, -1.5))
box(o, "wing_l", -9.0, 7.0, -1.5, 7, 1, 7, ("wing", True), pivot=piv(-2.0, 7.4, -1.5))
YOFF = 0


# ---------------------------------------------------------------------------------------------------------------------
# painting
def noise(rng, c, amt=0.045):
    f = 1.0 + (rng.random() * 2 - 1) * amt
    return sc(c, f)


def paint_face(img, c, face, x0, y0, W, H, rng):
    sty = c["faces"].get(face, c["style"])
    kind = sty[0]
    for j in range(H):
        for i in range(W):
            col = None
            if kind == "solid":
                col = sty[1]
            elif kind == "stripes":
                _, a, b, per, vert = sty
                idx = (i if vert else j) // per
                if face in ("top", "bottom") and not vert: idx = j // per
                col = a if idx % 2 == 0 else b
            elif kind == "feather":
                _, base, edge = sty
                mid = W // 2
                col = sc(base, 1.18) if i == mid or (W % 2 == 0 and i == mid - 1) else base
                if j >= H - 2 or i == 0 or i == W - 1: col = sc(edge, 0.92) if j >= H - 2 else sc(base, 0.82)
                if (j + abs(i - mid)) % 3 == 0 and W > 2: col = sc(col, 0.9)
            elif kind == "glyph":
                _, base, acc = sty
                col = base
            elif kind == "hood":
                _, base, acc = sty
                col = base
                if abs(i - (W - 1) / 2.0) < 0.7: col = acc
                if j == H - 1: col = sc(base, 0.8)
            elif kind == "eyes":
                _, base, acc = sty
                col = base
                if j == 0 and i in (0, W - 1): col = acc
                if j == 0 and W >= 3 and i in (0, W - 1): col = acc
            elif kind == "eyes_gold":
                _, base, acc = sty
                col = base
                if W >= 2 and H >= 2 and i in (W // 2 - 1, W // 2) and j == H // 2: col = acc
            elif kind == "eyes_side":
                _, base, acc = sty
                col = base
                if i == W // 2 and j == H // 2: col = acc
            elif kind == "ear_in":
                _, base, acc = sty
                col = acc if (0 < i < W - 1 and j > 0) or W <= 1 else base
                if W <= 2: col = acc if j > 0 else base
            elif kind == "nose":
                _, base, acc = sty
                col = base
                if face == "front" and j == 0 and i in (0, W - 1): col = acc
            elif kind == "wing":
                _, mirror = sty
                k = (W - 1 - i) if mirror else i            # k = distance from the shoulder along the wing
                if face in ("top", "bottom"):
                    if k < 2: col = GOLD if (j % 2 == 0) else GOLD_D
                    elif k >= W - 1: col = OBS
                    else: col = LAPIS if (j % 2 == 0) else LAPIS_L
                    if k >= W - 3 and k < W - 1 and j % 2 == 0: col = TURQ
                else:
                    col = LAPIS
            elif kind == "shell":
                _, base, line, edge = sty
                col = base
                if i == W // 2 or (W % 2 == 0 and i == W // 2 - 1): col = line
                if j % 4 == 3: col = sc(base, 0.78)
                if i == 0 or i == W - 1 or j == H - 1: col = edge if face in ("top", "front", "left", "right") else sc(base, 0.8)
            else:
                col = (255, 0, 255)
            if kind == "glyph" and face in ("front", "left", "right", "back") and H >= 2 and W >= 2:
                # invented glyph marks: sparse accent strokes in a repeating cartouche rhythm (not real text)
                if (i * 7 + j * 13 + rng.randrange(6)) % 9 == 0 and 0 < j < H - 1: col = sty[2]
            # bevel: light edge top-left, dark bottom-right, then face shading and a little grain
            f = FACE_SHADE[face]
            if W > 2 and H > 2:
                if i == 0 or j == 0: f *= 1.10
                if i == W - 1 or j == H - 1: f *= 0.86
            img.put(x0 + i, y0 + j, noise(rng, sc(col, f)))


def main():
    # pack unwrap rectangles on shelves, 128 wide
    W = 128
    items = sorted(SHEET, key=lambda c: -(c["d"] + c["h"]))
    x = y = rowh = 0
    for c in items:
        cw, ch = 2 * c["d"] + 2 * c["w"], c["d"] + c["h"]
        if x + cw > W: x, y, rowh = 0, y + rowh, 0
        c["u"], c["v"] = x, y
        x += cw; rowh = max(rowh, ch)
    H = ((y + rowh + 7) // 8) * 8
    img = Image(W, H)
    for c in SHEET:
        rng = random.Random(zlib.crc32((c["orn"] + c["name"]).encode()))
        w, h, d, u, v = c["w"], c["h"], c["d"], c["u"], c["v"]
        paint_face(img, c, "top", u + d, v, w, d, rng)
        paint_face(img, c, "bottom", u + d + w, v, w, d, rng)
        paint_face(img, c, "right", u, v + d, d, h, rng)
        paint_face(img, c, "front", u + d, v + d, w, h, rng)
        paint_face(img, c, "left", u + d + w, v + d, d, h, rng)
        paint_face(img, c, "back", u + 2 * d + w, v + d, w, h, rng)
    out_png = os.path.join(BASE, "textures", "entity", "azmar_ornaments.png")
    os.makedirs(os.path.dirname(out_png), exist_ok=True)
    img.save(out_png)
    data = {"texture": "elsewhere:textures/entity/azmar_ornaments.png", "width": W, "height": H, "ornaments": {}}
    for name, cubes in ORN.items():
        lst = []
        for c in cubes:
            px, py, pz = c["pivot"]
            lst.append({"n": c["name"], "uv": [c["u"], c["v"]], "o": [round(c["x"] - px, 3), round(c["y"] - py, 3), round(c["z"] - pz, 3)], "s": [c["w"], c["h"], c["d"]],
                        "p": [round(px, 3), round(py, 3), round(pz, 3)], "r": [round(math.radians(a), 5) for a in c["rot"]]})
        data["ornaments"][name] = lst
    with open(os.path.join(BASE, "azmar_ornaments.json"), "w") as f:
        json.dump(data, f, indent=0)
    print("atlas", W, "x", H, "cubes", len(SHEET), {k: len(v) for k, v in ORN.items()})


main()
