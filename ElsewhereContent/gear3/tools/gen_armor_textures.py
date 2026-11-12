"""Original worn-armour art for the 13 gear3 sets (64x32, standard humanoid layout). Pure PIL; all pixels drawn here.

Output: assets/elsewhere/textures/entity/equipment/humanoid/<set>.png (layer 1: helmet, body+arms, boots)
        assets/elsewhere/textures/entity/equipment/humanoid_leggings/<set>.png (layer 2: belt + legs)
Layout (64x32): head box (0,0) 32x16 | legs (0,16) 16x16 | body (16,16) 24x16 | arms (40,16) 16x16.
Run:  python3 tools/gen_armor_textures.py [--sheet out.png]     (re-runnable, deterministic)
"""
import os
import sys
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "equipment")


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


class Mat:
    """A material: base colour plus a texture kind used by Canvas.fill."""

    def __init__(self, hexcol, kind="metal"):
        self.c = rgb(hexcol)
        self.kind = kind


class Canvas:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
        self.px = self.im.load()
        self.rng = random.Random(seed)

    def put(self, x, y, c, a=255):
        if 0 <= x < 64 and 0 <= y < 32:
            self.px[x, y] = (*c, a)

    def fill(self, x, y, w, h, m, light=True):
        """Shaded fill of a face rectangle: top/left highlight, bottom/right shadow, kind-specific grain."""
        c = m.c
        for j in range(h):
            for i in range(w):
                f = 1.0
                if light:
                    if j == 0:
                        f += 0.20
                    if i == 0:
                        f += 0.10
                    if j == h - 1 and h > 3:
                        f -= 0.22
                    if i == w - 1 and w > 3:
                        f -= 0.12
                k = m.kind
                r = self.rng.random()
                if k == "metal":
                    f += (r - 0.5) * 0.10 + (0.07 if (j % 4 == 1) else 0) - (0.06 if j % 4 == 3 else 0)
                elif k == "mail":
                    f += (0.14 if (i + j) % 2 == 0 else -0.16) + (r - 0.5) * 0.06
                elif k == "cloth":
                    f += (0.06 if i % 2 == 0 else -0.05) + (r - 0.5) * 0.06
                elif k == "leather":
                    f += (r - 0.5) * 0.22 - (0.08 if r > 0.9 else 0)
                elif k == "fur":
                    f += (r - 0.5) * 0.45 + (0.10 if (i * 3 + j) % 5 == 0 else 0)
                elif k == "glass":
                    f += 0.10 * ((i + j) % 6 < 2) - 0.07 * ((i - j) % 7 == 0) + (r - 0.5) * 0.04
                elif k == "wood":
                    f += (0.08 if i % 3 == 0 else -0.06) + (r - 0.5) * 0.08
                self.put(x + i, y + j, shade(c, f))

    def rect_outline(self, x, y, w, h, c):
        for i in range(w):
            self.put(x + i, y, c)
            self.put(x + i, y + h - 1, c)
        for j in range(h):
            self.put(x, y + j, c)
            self.put(x + w - 1, y + j, c)

    def hline(self, x, y, w, m, hi=True):
        for i in range(w):
            self.put(x + i, y, shade(m.c, 1.0 + (0.12 if (hi and i % 2 == 0) else 0)))

    def band(self, x, y, w, h, m):
        """Trim band with a bright top row and dark bottom row."""
        for j in range(h):
            f = 1.25 if j == 0 else (0.72 if j == h - 1 and h > 1 else 1.0)
            for i in range(w):
                self.put(x + i, y + j, shade(m.c, f + (0.06 if (i + j) % 4 == 0 else 0)))

    def rivets(self, pts, m):
        for (x, y) in pts:
            self.put(x, y, shade(m.c, 1.35))
            self.put(x + 1, y + 1, shade(m.c, 0.6))

    def vstripe(self, x, y, w, h, m):
        for j in range(h):
            for i in range(w):
                f = 1.15 if i == 0 else (0.8 if i == w - 1 and w > 2 else 1.0)
                self.put(x + i, y + j, shade(m.c, f + (0.05 if j % 3 == 0 else 0)))

    def dither_edge(self, x, y, w, m, down=True, rng_p=0.5):
        """Ragged edge (fur/fringe/hem) along a row."""
        for i in range(w):
            if self.rng.random() < rng_p:
                self.put(x + i, y, shade(m.c, 0.9 + self.rng.random() * 0.25))


# ---------------------------------------------------------------- parts
HEAD = {"top": (8, 0), "bottom": (16, 0), "right": (0, 8), "front": (8, 8), "left": (16, 8), "back": (24, 8)}


def head_faces():
    return [(8, 0, 8, 8), (16, 0, 8, 8), (0, 8, 8, 8), (8, 8, 8, 8), (16, 8, 8, 8), (24, 8, 8, 8)]


def paint_helm(cv, s):
    h = s["helm"]
    base, trim, acc = h["base"], h["trim"], h["acc"]
    for f in head_faces():
        cv.fill(*f, base)
    style = h["style"]
    # trim band round the brow (rows 1..2 of every side face), plus rim around crown top
    for (x, y) in ((0, 8), (8, 8), (16, 8), (24, 8)):
        cv.band(x, y, 8, 2, trim)
    cv.rect_outline(8, 0, 8, 8, shade(trim.c, 0.8))
    cv.hline(9, 1, 6, trim)
    # crown top: seam cross
    for k in range(8):
        cv.put(11 + (k % 2), k, shade(base.c, 0.7))
    cv.rivets([(1, 12), (6, 12), (17, 12), (22, 12), (25, 12), (30, 12)], trim)
    if style == "closed":  # visor with eye slit and breath holes
        cv.fill(9, 11, 6, 3, Mat("#14161a"), light=False)
        cv.hline(9, 11, 6, Mat("#06070a"), hi=False)
        for x in (9, 11, 13):
            cv.put(x, 14, shade(acc.c, 1.0))
        cv.put(11, 12, shade(acc.c, 1.2))
        cv.put(12, 12, shade(acc.c, 1.2))
        cv.vstripe(11, 8, 2, 3, trim)  # nasal crest above slit
    elif style == "open":  # nasal helm: face open
        for j in range(11, 16):
            for i in range(9, 15):
                cv.put(i, j, (0, 0, 0), 0)
        cv.vstripe(11, 10, 2, 4, trim)
        for x in (9, 14):
            cv.put(x, 10, shade(trim.c, 1.2))
    elif style == "hood":  # hood with face opening and a pointed cowl
        for j in range(10, 16):
            for i in range(9, 15):
                cv.put(i, j, (0, 0, 0), 0)
        for i in range(8, 16):
            cv.put(i, 10, shade(base.c, 0.55))
        for j in range(10, 16):
            cv.put(8, j, shade(base.c, 0.65))
            cv.put(15, j, shade(base.c, 0.65))
        for x in (0, 8, 16, 24):  # cowl folds
            for j in range(3, 16, 3):
                cv.put(x + 3, j, shade(base.c, 0.78))
        cv.band(9, 9, 6, 1, acc)
    elif style == "mask":  # beast mask: ears on top, snout, eye holes
        cv.fill(8, 0, 8, 8, base)
        for (x, y) in ((8, 0), (14, 0)):
            cv.fill(x, y, 2, 2, acc)
        for j in range(10, 12):
            for i in (9, 13):
                cv.put(i, j, (0, 0, 0), 0)
                cv.put(i + 1, j, (0, 0, 0), 0)
        cv.fill(10, 12, 4, 3, Mat("#d9cbb0", "fur"))
        cv.put(11, 12, (20, 14, 12))
        cv.put(12, 12, (20, 14, 12))
        for i in range(10, 14):
            cv.put(i, 15, shade(acc.c, 1.1))
        for x in (9, 12):
            cv.put(x, 14, (240, 235, 220))
        for i in range(8, 16):  # war-paint streaks
            if i % 3 == 0:
                cv.put(i, 9, shade(acc.c, 1.2))
    elif style == "crown":  # circlet: only a band and gems, rest open
        keep = [(8, 0, 8, 8)]
        cv.im.paste((0, 0, 0, 0), (0, 0, 32, 16))
        for (x, y) in ((0, 8), (8, 8), (16, 8), (24, 8)):
            cv.band(x, y, 8, 2, base)
            cv.fill(x, y + 2, 8, 1, trim)
            for i in range(1, 8, 2):
                cv.put(x + i, y + 3, shade(acc.c, 1.2 if i % 4 == 1 else 0.9))
        cv.fill(8, 0, 8, 8, base)
        cv.rect_outline(8, 0, 8, 8, shade(trim.c, 1.1))
        cv.put(11, 9, shade(acc.c, 1.5))
        cv.put(12, 9, shade(acc.c, 1.2))
    if h.get("crest"):  # coloured plume on the crown (top face centre line) and back
        cv.vstripe(11, 0, 2, 8, h["crest"])
        cv.vstripe(27, 8, 2, 5, h["crest"])


def paint_body(cv, s, ox=16, oy=16):
    b = s["body"]
    base, trim, acc, belt = b["base"], b["trim"], b["acc"], b["belt"]
    # faces: top(20,16 8x4) bottom(28,16) right(16,20 4x12) front(20,20 8x12) left(28,20) back(32,20 8x12)
    cv.fill(20, 16, 8, 4, trim)
    cv.fill(28, 16, 8, 4, shade_mat(base, 0.7))
    for (x, w) in ((16, 4), (20, 8), (28, 4), (32, 8)):
        cv.fill(x, 20, w, 12, base)
    # collar / neckline
    for (x, w) in ((20, 8), (32, 8), (16, 4), (28, 4)):
        cv.band(x, 20, w, 2, trim)
    # hem band
    for (x, w) in ((20, 8), (32, 8), (16, 4), (28, 4)):
        cv.band(x, 30, w, 2, trim)
    # belt
    for (x, w) in ((20, 8), (32, 8), (16, 4), (28, 4)):
        cv.band(x, 26, w, 2, belt)
    cv.put(23, 26, shade(acc.c, 1.4))
    cv.put(24, 26, shade(acc.c, 1.4))
    cv.put(23, 27, shade(acc.c, 0.8))
    cv.put(24, 27, shade(acc.c, 0.8))
    kind = b.get("emblem", "stripe")
    if kind == "stripe":  # centre livery stripe
        cv.vstripe(23, 22, 2, 4, acc)
        cv.vstripe(35, 22, 2, 4, acc)
    elif kind == "chevron":
        for k in range(4):
            cv.put(22 + k, 22 + k, shade(acc.c, 1.2))
            cv.put(25 - k, 22 + k, shade(acc.c, 1.2))
            cv.put(36 + k, 22 + k, shade(acc.c, 1.2))
            cv.put(39 - k, 22 + k, shade(acc.c, 1.2))
    elif kind == "glyph":  # small rune-like glyph
        for (dx, dy) in ((0, 0), (1, 0), (1, 1), (1, 2), (0, 3), (2, 3), (2, 1)):
            cv.put(22 + dx, 22 + dy, shade(acc.c, 1.3))
            cv.put(36 + dx, 22 + dy, shade(acc.c, 1.3))
    elif kind == "diamond":
        for (dx, dy) in ((1, 0), (0, 1), (2, 1), (1, 2), (1, 1)):
            cv.put(22 + dx, 22 + dy, shade(acc.c, 1.3 if (dx, dy) != (1, 1) else 1.6))
            cv.put(36 + dx, 22 + dy, shade(acc.c, 1.3 if (dx, dy) != (1, 1) else 1.6))
    cv.rivets([(21, 22), (26, 22), (21, 28), (26, 28)], trim)
    # arms: top(44,16 4x4) bottom(48,16) right(40,20) front(44,20) left(48,20) back(52,20), 4x12 each
    a = s["arm"]
    cv.fill(44, 16, 4, 4, a["cap"])
    cv.fill(48, 16, 4, 4, shade_mat(a["base"], 0.7))
    for x in (40, 44, 48, 52):
        cv.fill(x, 20, 4, 12, a["base"])
        cv.band(x, 20, 4, 3, a["cap"])          # pauldron / shoulder cap
        cv.band(x, 29, 4, 3, a["cuff"])         # vambrace / cuff
        cv.put(x + 1, 21, shade(a["cap"].c, 1.45))
    cv.rivets([(44, 25), (50, 25)], a["cuff"])


def shade_mat(m, f):
    n = Mat("#000000", m.kind)
    n.c = shade(m.c, f)
    return n


def paint_boots(cv, s):
    b = s["boots"]
    base, trim, sole = b["base"], b["trim"], b["sole"]
    # leg region at (0,16); boots occupy lower rows (y 26..31 of the leg faces)
    cv.fill(4, 16, 4, 4, shade_mat(base, 0.6), light=False)
    cv.fill(8, 16, 4, 4, shade_mat(sole, 0.9))
    for x in (0, 4, 8, 12):
        cv.fill(x, 26, 4, 6, base)
        cv.band(x, 26, 4, 2, trim)          # cuff
        cv.band(x, 31, 4, 1, sole)          # sole
    cv.rivets([(4, 28), (6, 28)], trim)
    if b.get("buckle"):
        cv.put(5, 29, shade(b["buckle"].c, 1.4))
        cv.put(6, 29, shade(b["buckle"].c, 1.1))
    if b.get("fur"):
        cv.dither_edge(0, 25, 16, b["fur"], rng_p=0.8)
        for x in (0, 4, 8, 12):
            cv.fill(x, 26, 4, 2, b["fur"])


def paint_leggings(cv, s):
    lg = s["legs"]
    base, trim, acc, belt = lg["base"], lg["trim"], lg["acc"], lg["belt"]
    # body region: the lower torso/waist the leggings cover (front/back/sides rows 20..27)
    for (x, w) in ((16, 4), (20, 8), (28, 4), (32, 8)):
        cv.fill(x, 20, w, 8, base)
        cv.band(x, 20, w, 3, belt)
    cv.fill(20, 16, 8, 4, belt)
    cv.fill(28, 16, 8, 4, shade_mat(base, 0.7))
    cv.put(23, 21, shade(acc.c, 1.5))
    cv.put(24, 21, shade(acc.c, 1.5))
    cv.rivets([(21, 24), (26, 24), (35, 24), (38, 24)], trim)
    # legs: top(4,16) bottom(8,16) right(0,20) front(4,20) left(8,20) back(12,20)
    cv.fill(4, 16, 4, 4, belt)
    cv.fill(8, 16, 4, 4, shade_mat(base, 0.7))
    for x in (0, 4, 8, 12):
        cv.fill(x, 20, 4, 12, base)
        cv.band(x, 20, 4, 1, belt)
        cv.band(x, 24, 4, 1, trim)            # thigh strap
        cv.band(x, 29, 4, 3, trim) if lg.get("hem", True) else None
    cv.vstripe(5, 21, 2, 8, acc) if lg.get("stripe") else None
    cv.rivets([(1, 26), (9, 26), (5, 26), (13, 26)], trim)
    if lg.get("tassets"):  # hanging plates over the thigh
        for x in (0, 4, 8, 12):
            cv.band(x, 21, 4, 3, lg["tassets"])
            cv.put(x + 1, 22, shade(lg["tassets"].c, 1.5))


# ---------------------------------------------------------------- sets
M = Mat

SETS = {}

# Aldermark: polished steel, blue livery, gold trim
steel, steel_d = M("#a9b3bf"), M("#6e7885")
blue, gold, lea = M("#2c52ae", "cloth"), M("#e0b83e"), M("#6a452a", "leather")
SETS["aldermark_outrider"] = dict(
    helm=dict(base=steel, trim=gold, acc=gold, style="open", crest=blue),
    body=dict(base=M("#2c52ae", "cloth"), trim=gold, acc=gold, belt=lea, emblem="chevron"),
    arm=dict(base=steel_d, cap=steel, cuff=gold),
    boots=dict(base=lea, trim=gold, sole=M("#2b1d12", "leather"), buckle=gold),
    legs=dict(base=M("#6a452a", "leather"), trim=gold, acc=blue, belt=M("#4a2f1c", "leather"), stripe=True, tassets=M("#2c52ae", "cloth")),
)

# Azmar: desert sand, white linen, lapis, gold
sand, linen, lapis = M("#d6bb83", "cloth"), M("#efe8d6", "cloth"), M("#2a4fa6", "cloth")
SETS["azmar_falconer"] = dict(
    helm=dict(base=linen, trim=lapis, acc=gold, style="hood"),
    body=dict(base=sand, trim=lapis, acc=gold, belt=lapis, emblem="diamond"),
    arm=dict(base=linen, cap=sand, cuff=gold),
    boots=dict(base=M("#a98657", "leather"), trim=lapis, sole=M("#5a4128", "leather"), buckle=gold),
    legs=dict(base=linen, trim=lapis, acc=gold, belt=lapis, stripe=True, tassets=sand),
)
SETS["azmar_glyphmail"] = dict(
    helm=dict(base=M("#d3b46f", "mail"), trim=gold, acc=lapis, style="hood"),
    body=dict(base=M("#d3b46f", "mail"), trim=lapis, acc=M("#5fb4ff"), belt=lapis, emblem="glyph"),
    arm=dict(base=M("#d3b46f", "mail"), cap=gold, cuff=lapis),
    boots=dict(base=M("#a98657", "leather"), trim=gold, sole=M("#4b3722", "leather"), buckle=gold),
    legs=dict(base=M("#d3b46f", "mail"), trim=lapis, acc=M("#5fb4ff"), belt=gold, stripe=True),
)

# Hrafnholt: dark iron, fur, brown leather, red/green wool
iron, iron_l, fur, wool_r, wool_g = M("#3a3e47"), M("#565c68"), M("#8d7d66", "fur"), M("#9b2b24", "cloth"), M("#40692f", "cloth")
lea2 = M("#6b4a2e", "leather")
SETS["hrafnholt_hearthguard"] = dict(
    helm=dict(base=iron, trim=iron_l, acc=M("#d8c9a8"), style="closed", crest=wool_r),
    body=dict(base=iron, trim=fur, acc=wool_r, belt=lea2, emblem="stripe"),
    arm=dict(base=iron, cap=fur, cuff=lea2),
    boots=dict(base=lea2, trim=iron_l, sole=M("#2a1c10", "leather"), fur=fur),
    legs=dict(base=iron, trim=iron_l, acc=wool_r, belt=lea2, tassets=wool_r),
)
SETS["hrafnholt_hird"] = dict(
    helm=dict(base=lea2, trim=iron_l, acc=wool_g, style="open", crest=wool_g),
    body=dict(base=wool_g, trim=lea2, acc=wool_r, belt=lea2, emblem="chevron"),
    arm=dict(base=wool_g, cap=lea2, cuff=iron_l),
    boots=dict(base=lea2, trim=wool_g, sole=M("#2a1c10", "leather"), fur=fur),
    legs=dict(base=wool_g, trim=lea2, acc=wool_r, belt=lea2, stripe=True),
)
wolf = M("#6f6f72", "fur")
SETS["hrafnholt_ulfhednar"] = dict(
    helm=dict(base=wolf, trim=M("#3b3b3f", "fur"), acc=M("#a82a22"), style="mask"),
    body=dict(base=wolf, trim=M("#4a4a4e", "fur"), acc=M("#a82a22"), belt=lea2, emblem="chevron"),
    arm=dict(base=lea2, cap=wolf, cuff=M("#3a2a1a", "leather")),
    boots=dict(base=M("#3a2a1a", "leather"), trim=wolf, sole=M("#1d140c", "leather"), fur=wolf),
    legs=dict(base=M("#4f3a26", "leather"), trim=wolf, acc=M("#a82a22"), belt=M("#2f2216", "leather"), stripe=True),
)

# Mirewick: crimson / purple, lantern brass
crim, purp, brass = M("#9a1c3a", "cloth"), M("#5a2a7c", "cloth"), M("#c9a04a")
SETS["mirewick_lamplighter"] = dict(
    helm=dict(base=crim, trim=brass, acc=M("#ffd870"), style="hood"),
    body=dict(base=crim, trim=brass, acc=M("#ffd870"), belt=purp, emblem="diamond"),
    arm=dict(base=purp, cap=crim, cuff=brass),
    boots=dict(base=M("#3a1a30", "leather"), trim=brass, sole=M("#1a0c16", "leather"), buckle=brass),
    legs=dict(base=purp, trim=brass, acc=M("#ffd870"), belt=crim, stripe=True),
)

# Glasswaste: glass, pale amber
glass, amber = M("#bfe0e6", "glass"), M("#f0c878")
SETS["glasswaste_starsight"] = dict(
    helm=dict(base=glass, trim=amber, acc=M("#ffe9a8"), style="closed"),
    body=dict(base=M("#a9d2dc", "glass"), trim=amber, acc=M("#ffe9a8"), belt=amber, emblem="diamond"),
    arm=dict(base=glass, cap=amber, cuff=amber),
    boots=dict(base=M("#a9d2dc", "glass"), trim=amber, sole=M("#7a6a48"), buckle=M("#ffe9a8")),
    legs=dict(base=glass, trim=amber, acc=M("#ffe9a8"), belt=amber, stripe=True),
)

# Galeplate: pale gale-steel, teal, white (kept to its existing palette)
gale, teal, white = M("#c8d4dc"), M("#2a7a82"), M("#f2f0ff")
SETS["galeplate"] = dict(
    helm=dict(base=gale, trim=teal, acc=white, style="closed", crest=white),
    body=dict(base=gale, trim=teal, acc=white, belt=teal, emblem="chevron"),
    arm=dict(base=gale, cap=white, cuff=teal),
    boots=dict(base=teal, trim=white, sole=M("#1d4045"), buckle=white),
    legs=dict(base=gale, trim=teal, acc=white, belt=teal, tassets=white),
)

# Ridgewatch: sniper, slate cloak and olive leather
slate, olive = M("#566270", "cloth"), M("#5b6b3a", "leather")
SETS["ridgewatch"] = dict(
    helm=dict(base=slate, trim=olive, acc=M("#c7d1d9"), style="hood"),
    body=dict(base=slate, trim=olive, acc=M("#c7d1d9"), belt=M("#3d2e1e", "leather"), emblem="stripe"),
    arm=dict(base=slate, cap=olive, cuff=M("#3d2e1e", "leather")),
    boots=dict(base=olive, trim=M("#3d2e1e", "leather"), sole=M("#221a10", "leather"), buckle=M("#c7d1d9")),
    legs=dict(base=M("#4b5560", "cloth"), trim=olive, acc=M("#c7d1d9"), belt=M("#3d2e1e", "leather"), stripe=False),
)

# Sunscribe: gold and ivory vestments, orange sunburst
ivory, sungold, ember = M("#f1e6c4", "cloth"), M("#e8b63a"), M("#d9692a", "cloth")
SETS["sunscribe"] = dict(
    helm=dict(base=ivory, trim=sungold, acc=ember, style="hood"),
    body=dict(base=ivory, trim=sungold, acc=ember, belt=ember, emblem="diamond"),
    arm=dict(base=ivory, cap=sungold, cuff=ember),
    boots=dict(base=M("#a8773a", "leather"), trim=sungold, sole=M("#4a331b", "leather"), buckle=sungold),
    legs=dict(base=ivory, trim=sungold, acc=ember, belt=ember, stripe=True),
)

# Elderbark: living bark, moss green, amber sap
bark, moss, sap = M("#5e4630", "wood"), M("#4f7a36", "cloth"), M("#d9a640")
SETS["elderbark"] = dict(
    helm=dict(base=bark, trim=moss, acc=sap, style="crown"),
    body=dict(base=bark, trim=moss, acc=sap, belt=moss, emblem="glyph"),
    arm=dict(base=bark, cap=moss, cuff=moss),
    boots=dict(base=M("#4a3624", "wood"), trim=moss, sole=M("#2a1d12", "wood"), buckle=sap),
    legs=dict(base=bark, trim=moss, acc=sap, belt=moss, stripe=True),
)

# Court Marshal: crimson tabard, gold, steel
ms, mred, mgold = M("#9aa4b0"), M("#a1202e", "cloth"), M("#e2bc45")
SETS["court_marshals"] = dict(
    helm=dict(base=ms, trim=mgold, acc=mgold, style="closed", crest=mred),
    body=dict(base=mred, trim=mgold, acc=mgold, belt=M("#4a2f1c", "leather"), emblem="chevron"),
    arm=dict(base=ms, cap=mgold, cuff=mgold),
    boots=dict(base=M("#4a2f1c", "leather"), trim=mgold, sole=M("#241810", "leather"), buckle=mgold),
    legs=dict(base=ms, trim=mgold, acc=mred, belt=M("#4a2f1c", "leather"), tassets=mred),
)

ORDER = ["hrafnholt_hearthguard", "aldermark_outrider", "azmar_falconer", "azmar_glyphmail", "glasswaste_starsight",
         "aldermark_outrider", "galeplate", "hrafnholt_hird", "hrafnholt_ulfhednar", "mirewick_lamplighter",
         "ridgewatch", "sunscribe", "elderbark", "court_marshals"]


def render(name, s):
    seed = sum(map(ord, name))
    l1 = Canvas(seed)
    paint_helm(l1, s)
    paint_body(l1, s)
    paint_boots(l1, s)
    l2 = Canvas(seed + 1)
    paint_leggings(l2, s)
    return l1.im, l2.im


def main():
    sheet = None
    if "--sheet" in sys.argv:
        sheet = sys.argv[sys.argv.index("--sheet") + 1]
    os.makedirs(os.path.join(OUT, "humanoid"), exist_ok=True)
    os.makedirs(os.path.join(OUT, "humanoid_leggings"), exist_ok=True)
    done = []
    for name in dict.fromkeys(ORDER):
        l1, l2 = render(name, SETS[name])
        l1.save(os.path.join(OUT, "humanoid", name + ".png"))
        l2.save(os.path.join(OUT, "humanoid_leggings", name + ".png"))
        done.append((name, l1, l2))
    print("wrote", len(done) * 2, "textures")
    if sheet:
        S = 4
        cw, ch = 64 * S + 8, 32 * S + 8
        sh = Image.new("RGBA", (cw * 2, ch * len(done)), (70, 74, 82, 255))
        for r, (name, l1, l2) in enumerate(done):
            for c, im in enumerate((l1, l2)):
                bg = Image.new("RGBA", im.size, (70, 74, 82, 255))
                # checker so transparency reads
                for yy in range(0, 32, 2):
                    for xx in range(0, 64, 2):
                        if (xx + yy) // 2 % 2 == 0:
                            bg.paste((84, 88, 96, 255), (xx, yy, xx + 2, yy + 2))
                bg.alpha_composite(im)
                sh.paste(bg.resize((64 * S, 32 * S), Image.NEAREST), (c * cw + 4, r * ch + 4))
        sh.save(sheet)
        print("sheet", sheet)


if __name__ == "__main__":
    main()
