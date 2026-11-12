"""skinkit: paint 64x64 player-layout skins (vanilla PlayerModel, wide arms) part by part.

Parts are boxes (u, v, w, h, d) in the classic 64x64 layout. Faces of a box: top, bottom, right, front, left, back.
The side faces form one ring (right, front, left, back) in the texture, so a "ring row" is a horizontal band all the way
round a limb. Local face coords: front x=0 is the character's right side, back x=0 the character's left side,
right-face x=0 is the back edge, left-face x=0 the front edge.
"""
import random
from PIL import Image

PARTS = {
    "head": (0, 0, 8, 8, 8), "hat": (32, 0, 8, 8, 8),
    "body": (16, 16, 8, 12, 4), "jacket": (16, 32, 8, 12, 4),
    "rarm": (40, 16, 4, 12, 4), "rsleeve": (40, 32, 4, 12, 4),
    "larm": (32, 48, 4, 12, 4), "lsleeve": (48, 48, 4, 12, 4),
    "rleg": (0, 16, 4, 12, 4), "rpants": (0, 32, 4, 12, 4),
    "lleg": (16, 48, 4, 12, 4), "lpants": (0, 48, 4, 12, 4),
}
ARMS = ("rarm", "larm"); LEGS = ("rleg", "lleg"); SLEEVES = ("rsleeve", "lsleeve"); PANTS = ("rpants", "lpants")
OVER = {"head": "hat", "body": "jacket", "rarm": "rsleeve", "larm": "lsleeve", "rleg": "rpants", "lleg": "lpants"}
SIDES = ("right", "front", "left", "back")


def clamp(v):
    return max(0, min(255, int(round(v))))


def dk(c, n=24):
    return tuple(clamp(v - n) for v in c[:3])


def lt(c, n=24):
    return tuple(clamp(v + n) for v in c[:3])


def mix(a, b, t):
    return tuple(clamp(a[i] * (1 - t) + b[i] * t) for i in range(3))


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def face_box(part, face):
    u, v, w, h, d = PARTS[part]
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }[face]


class Skin:
    def __init__(self, seed):
        self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.im.load()
        self.rng = random.Random(seed)

    # ---- low level -------------------------------------------------------------------------------------------------
    def put(self, x, y, c, noise=0, a=255):
        if not (0 <= x < 64 and 0 <= y < 64):
            return
        n = self.rng.randint(-noise, noise) if noise else 0
        self.px[x, y] = (clamp(c[0] + n), clamp(c[1] + n), clamp(c[2] + n), a)

    def get(self, x, y):
        return self.px[x, y]

    def fpx(self, part, face, x, y, c, noise=0):
        fx, fy, w, h = face_box(part, face)
        if 0 <= x < w and 0 <= y < h:
            self.put(fx + x, fy + y, c, noise)

    def frect(self, part, face, x0, y0, x1, y1, c, noise=0):
        """Inclusive-exclusive rect in local face coords."""
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.fpx(part, face, x, y, c, noise)

    def fclear(self, part, face, x0=0, y0=0, x1=99, y1=99):
        fx, fy, w, h = face_box(part, face)
        for y in range(max(0, y0), min(h, y1)):
            for x in range(max(0, x0), min(w, x1)):
                self.px[fx + x, fy + y] = (0, 0, 0, 0)

    def fill(self, part, c, noise=5, faces=("top", "bottom") + SIDES):
        for f in faces:
            self.frect(part, f, 0, 0, 99, 99, c, noise)

    def ring_w(self, part):
        u, v, w, h, d = PARTS[part]
        return 2 * d + 2 * w

    def ring(self, part, row, c, noise=3, step=1, off=0):
        """Paint one row all round the side faces. step/off let you dot it (stitching, beads)."""
        u, v, w, h, d = PARTS[part]
        for i in range(off, 2 * d + 2 * w, step):
            self.put(u + i, v + d + row, c, noise)

    def rows(self, part, r0, r1, c, noise=4):
        for r in range(r0, r1):
            self.ring(part, r, c, noise)

    def ringpx(self, part, i, row, c, noise=0):
        u, v, w, h, d = PARTS[part]
        self.put(u + (i % (2 * d + 2 * w)), v + d + row, c, noise)

    def ringget(self, part, i, row):
        u, v, w, h, d = PARTS[part]
        return self.px[u + (i % (2 * d + 2 * w)), v + d + row]

    def tex(self, part, faces, fn):
        """fn(face, x, y, current_rgba) -> rgb or None, over every pixel of the given faces."""
        for f in faces:
            fx, fy, w, h = face_box(part, f)
            for y in range(h):
                for x in range(w):
                    r = fn(f, x, y, self.px[fx + x, fy + y])
                    if r is not None:
                        self.put(fx + x, fy + y, r[:3], 0)

    def save(self, path):
        self.im.save(path)


# ---- cloth textures ----------------------------------------------------------------------------------------------
def weave(s, part, base, amt=7, faces=("top", "bottom") + SIDES):
    """Coarse wool: a soft checker with a little noise."""
    def fn(f, x, y, cur):
        k = amt if (x + y) % 2 == 0 else -amt // 2
        n = s.rng.randint(-3, 3)
        return tuple(clamp(c + k + n) for c in base)
    s.tex(part, faces, fn)


def linen(s, part, base, faces=("top", "bottom") + SIDES, fold=10, every=3):
    """Fine linen: vertical fold lines every few pixels."""
    def fn(f, x, y, cur):
        k = -fold if x % every == every - 1 else 0
        return tuple(clamp(c + k + s.rng.randint(-2, 2)) for c in base)
    s.tex(part, faces, fn)


def flat(s, part, base, noise=4, faces=("top", "bottom") + SIDES):
    s.fill(part, base, noise, faces)


def leather(s, part, base, faces=("top", "bottom") + SIDES):
    def fn(f, x, y, cur):
        r = s.rng.random()
        k = -14 if r < 0.12 else (10 if r > 0.93 else 0)
        return tuple(clamp(c + k + s.rng.randint(-3, 3)) for c in base)
    s.tex(part, faces, fn)


# ---- heads ------------------------------------------------------------------------------------------------------
WHITE = (236, 235, 230)


def head_base(s, tone):
    s.fill("head", tone, 3)
    s.frect("head", "bottom", 0, 0, 8, 8, dk(tone, 18), 2)       # under the chin


def eyes(s, iris, white=WHITE, row=4, lash=None, kohl=None, look=0):
    """Eyes on the front face: outer white, inner iris (look=1 shifts both irises one px to the character's left)."""
    if look == 0:
        pts = ((1, white), (2, iris), (5, iris), (6, white))
    else:
        pts = ((1, iris), (2, white), (5, iris), (6, white))
    for x, c in pts:
        s.fpx("head", "front", x, row, c)
    if lash:
        for x in (1, 2, 5, 6):
            s.fpx("head", "front", x, row - 1, lash)
    if kohl:
        for x in (1, 2, 5, 6):
            s.fpx("head", "front", x, row - 1, kohl)
        s.fpx("head", "front", 0, row, kohl); s.fpx("head", "front", 7, row, kohl)
        s.fpx("head", "front", 0, row + 1, kohl); s.fpx("head", "front", 7, row + 1, kohl)


def brows(s, c, row=3, style="flat"):
    if style == "flat":
        for x in (1, 2, 5, 6):
            s.fpx("head", "front", x, row, c)
    elif style == "stern":        # inner ends low
        for x in (1, 6):
            s.fpx("head", "front", x, row, c)
        s.fpx("head", "front", 2, row, c); s.fpx("head", "front", 5, row, c)
        s.fpx("head", "front", 2, row + 0, dk(c, 10)); s.fpx("head", "front", 5, row + 0, dk(c, 10))
    elif style == "thin":
        for x in (1, 2, 5, 6):
            s.fpx("head", "front", x, row, mix(c, s.get(*face_box("head", "front")[:2]), 0.35))


def nose_mouth(s, tone, mouth=None, nose_row=5, mouth_row=6, wide=False, smile=False):
    s.fpx("head", "front", 3, nose_row, dk(tone, 22)); s.fpx("head", "front", 4, nose_row, dk(tone, 22))
    m = mouth or mix(dk(tone, 60), (120, 40, 40), 0.35)
    xs = (2, 3, 4, 5) if wide else (3, 4)
    for x in xs:
        s.fpx("head", "front", x, mouth_row, m)
    if smile:
        s.fpx("head", "front", 2, mouth_row - 1, dk(tone, 30)); s.fpx("head", "front", 5, mouth_row - 1, dk(tone, 30))


def ears(s, tone):
    for f, x in (("right", 4), ("left", 3)):
        s.fpx("head", f, x, 4, dk(tone, 20)); s.fpx("head", f, x, 5, dk(tone, 26))


def hair(s, c, style="short", part_x=None):
    """Hair on the head base layer. c is the mid colour; texture adds strands."""
    hi, lo = lt(c, 16), dk(c, 18)

    def strand(x, y):
        return hi if (x * 3 + y) % 5 == 0 else (lo if (x + y * 2) % 7 == 0 else c)
    for x in range(8):
        for y in range(8):
            s.fpx("head", "top", x, y, strand(x, y), 3)
    if style == "bald":
        return
    if style == "short":
        for x in range(8):
            s.fpx("head", "front", x, 0, strand(x, 0), 2)
            if x not in (3, 4) and x != part_x:
                s.fpx("head", "front", x, 1, strand(x, 1), 2)
        for f in ("right", "left"):
            for x in range(8):
                back = (x <= 3) if f == "right" else (x >= 4)
                for y in range(0, 5 if back else 2):
                    s.fpx("head", f, x, y, strand(x, y), 2)
            s.fpx("head", f, 6 if f == "right" else 1, 2, strand(1, 2))   # sideburn
            s.fpx("head", f, 6 if f == "right" else 1, 3, strand(1, 3))
        for x in range(8):
            for y in range(6):
                s.fpx("head", "back", x, y, strand(x, y), 2)
            if x in (1, 3, 4, 6):
                s.fpx("head", "back", x, 6, strand(x, 6), 2)
    elif style == "cropped":                 # shorn: stubble mixed with skin
        base = s.get(*face_box("head", "front")[:2])
        for f in ("front", "right", "left", "back"):
            depth = {"front": 1, "right": 3, "left": 3, "back": 4}[f]
            for x in range(8):
                for y in range(depth):
                    s.fpx("head", f, x, y, mix(c, base, 0.25 + 0.25 * ((x + y) % 2)), 2)
        for x in range(8):
            for y in range(8):
                s.fpx("head", "top", x, y, mix(c, base, 0.2 + 0.2 * ((x * y + x) % 3 == 0)), 2)
    elif style in ("long", "braid", "bob"):
        for x in range(8):
            s.fpx("head", "front", x, 0, strand(x, 0), 2)
            if x not in (3, 4):
                s.fpx("head", "front", x, 1, strand(x, 1), 2)
        for y in range(2, 6 if style != "bob" else 5):
            s.fpx("head", "front", 0, y, strand(0, y), 2); s.fpx("head", "front", 7, y, strand(7, y), 2)
        for f in ("right", "left"):
            for x in range(8):
                for y in range(8 if style != "bob" else 6):
                    s.fpx("head", f, x, y, strand(x, y), 2)
        for x in range(8):
            for y in range(8):
                s.fpx("head", "back", x, y, strand(x, y), 2)
        if style == "braid":                  # a plaited tail down the middle of the back
            for y in range(1, 8):
                s.fpx("head", "back", 3, y, lo if y % 2 else hi); s.fpx("head", "back", 4, y, hi if y % 2 else lo)
    elif style == "receding":
        base = s.get(*face_box("head", "front")[:2])
        for f in ("right", "left"):
            for x in range(8):
                back = (x <= 4) if f == "right" else (x >= 3)
                for y in range(1, 5 if back else 3):
                    s.fpx("head", f, x, y, strand(x, y), 2)
        for x in range(8):
            for y in range(1, 6):
                s.fpx("head", "back", x, y, strand(x, y), 2)
        for x in range(2, 6):
            for y in range(2, 6):
                s.fpx("head", "top", x, y, mix(base, c, 0.2), 2)


def beard(s, c, style="full", tone=None):
    hi, lo = lt(c, 14), dk(c, 16)
    if style == "full":
        for x in range(8):
            for y in range(5, 8):
                if y == 5 and x in (2, 3, 4, 5):
                    continue
                s.fpx("head", "front", x, y, hi if (x + y) % 3 == 0 else c, 2)
        s.fpx("head", "front", 3, 6, dk(c, 45)); s.fpx("head", "front", 4, 6, dk(c, 45))   # mouth in the beard
        for f, xs in (("right", range(5, 8)), ("left", range(0, 3))):
            for x in xs:
                for y in range(4, 8):
                    s.fpx("head", f, x, y, c if (x + y) % 2 else lo, 2)
        for x in range(8):
            for y in range(8):
                if (x + y) % 2 == 0 or y < 4:
                    s.fpx("head", "bottom", x, y, lo, 2)
    elif style == "short":
        for x in range(1, 7):
            s.fpx("head", "front", x, 7, c, 2)
        for x in (1, 6):
            s.fpx("head", "front", x, 6, c, 2)
        s.fpx("head", "front", 2, 6, c, 2); s.fpx("head", "front", 5, 6, c, 2)
        for f, xs in (("right", range(6, 8)), ("left", range(0, 2))):
            for x in xs:
                for y in range(4, 8):
                    s.fpx("head", f, x, y, c, 2)
    elif style == "mustache":
        for x in (2, 3, 4, 5):
            s.fpx("head", "front", x, 6, c, 1)
        s.fpx("head", "front", 2, 7, c, 1); s.fpx("head", "front", 5, 7, c, 1)
        s.fpx("head", "front", 3, 7, dk(c, 50)); s.fpx("head", "front", 4, 7, dk(c, 50))
    elif style == "stubble":
        base = tone or s.get(*face_box("head", "front")[:2])
        for x in range(8):
            for y in (6, 7):
                if (x + y) % 2 == 0:
                    s.fpx("head", "front", x, y, mix(base, c, 0.45))
    elif style == "forked":                  # viking: long beard, two plaits at the chin
        beard(s, c, "full")
        for x in (2, 5):
            s.fpx("head", "front", x, 7, hi)
        for x in (1, 6):
            s.fpx("head", "front", x, 7, lo)


# ---- clothes helpers --------------------------------------------------------------------------------------------
def skin_limbs(s, tone):
    for p in ARMS + LEGS:
        s.fill(p, tone, 3)
    s.fill("body", tone, 3)


def sleeves(s, c, to_row=12, texfn=None, cuff=None, rolled=None):
    """Sleeves on both arms from the shoulder to to_row (exclusive); the rest stays skin. rolled: a turned-back cuff row."""
    for p in ARMS:
        if texfn:
            texfn(p, c)
        else:
            s.fill(p, c, 4)
    return to_row


def hands(s, tone, from_row=10, arms=ARMS):
    for p in arms:
        s.rows(p, from_row, 12, tone, 3)
        s.frect(p, "bottom", 0, 0, 4, 4, dk(tone, 10), 2)


def forearms(s, tone, from_row, arms=ARMS):
    for p in arms:
        s.rows(p, from_row, 12, tone, 3)
        s.frect(p, "bottom", 0, 0, 4, 4, dk(tone, 10), 2)


def shoes(s, c, from_row=10, sole=None, legs=LEGS):
    for p in legs:
        s.rows(p, from_row, 12, c, 3)
        s.frect(p, "bottom", 0, 0, 4, 4, sole or dk(c, 25), 2)
        s.frect(p, "front", 0, 11, 4, 12, dk(c, 12), 1)


def legwraps(s, cloth, strap, r0=4, r1=10, legs=LEGS):
    """Viking winingas: wrapped bands with crossing straps."""
    for p in legs:
        for r in range(r0, r1):
            s.ring(p, r, cloth if r % 2 else dk(cloth, 14), 3)
        n = s.ring_w(p)
        for r in range(r0, r1):
            for i in range(n):
                if (i + r) % 4 == 0:
                    s.ringpx(p, i, r, strap)


def belt(s, row, c, buckle=None, h=1, part="body", buckle_w=2):
    for r in range(row, row + h):
        s.ring(part, r, c, 3)
    if buckle:
        x0 = 4 - buckle_w // 2
        for x in range(x0, x0 + buckle_w):
            for r in range(row, row + h):
                s.fpx(part, "front", x, r, buckle)


def collar_ring(s, c, part="body", row=0, faces=SIDES):
    for f in faces:
        fx, fy, w, h = face_box(part, f)
        for x in range(w):
            s.fpx(part, f, x, row, c, 2)
    s.frect(part, "top", 0, 0, 8, 4, c, 2)


def vneck(s, tone, depth=2, part="body", width=2):
    for y in range(depth):
        for x in range(4 - width // 2 + y // 2 * 0, 4 + width // 2):
            if abs(x - 3.5) <= width / 2 - y * 0.5 + 0.01:
                s.fpx(part, "front", x, y, tone, 2)


def apron(s, c, top=4, legs_to=8, x0=1, x1=7, strap=None, bib=False, texfn=None):
    """Apron on the body front (rows top..12, x0..x1) and down the leg fronts to legs_to; waist strings round the body."""
    for y in range(top, 12):
        for x in range(x0, x1):
            s.fpx("body", "front", x, y, c, 3)
    if bib:
        for y in range(1, top):
            for x in range(2, 6):
                s.fpx("body", "front", x, y, c, 3)
        if strap:
            for y in range(0, 2):
                s.fpx("body", "front", 1, y, strap); s.fpx("body", "front", 6, y, strap)
                s.fpx("body", "back", 1, y, strap); s.fpx("body", "back", 6, y, strap)
            for x in range(8):
                if x in (1, 6):
                    s.fpx("body", "top", x, 1, strap); s.fpx("body", "top", x, 2, strap)
    for y in range(0, legs_to):
        for x in range(1 if True else 0, 4):
            s.fpx("rleg", "front", x, y, c, 3)
        for x in range(0, 3):
            s.fpx("lleg", "front", x, y, c, 3)
    for y in range(0, legs_to):
        s.fpx("rleg", "front", 1, legs_to - 1, dk(c, 16)) if y == legs_to - 1 else None
    if strap:
        for f in ("right", "left", "back"):
            fx, fy, w, h = face_box("body", f)
            for x in range(w):
                s.fpx("body", f, x, top + 3 if top + 3 < 12 else 11, strap)
    # hem
    for x in range(1, 4):
        s.fpx("rleg", "front", x, legs_to - 1, dk(c, 18), 2)
    for x in range(0, 3):
        s.fpx("lleg", "front", x, legs_to - 1, dk(c, 18), 2)


def skirt(s, c, to_row=12, hem=None, overlay=True, texfn=None, hem_row=None):
    """A long kirtle: legs become one cloth column; the pants overlay adds the flare of the hem (lower rows only)."""
    for p in LEGS:
        if texfn:
            texfn(p, c)
        else:
            s.fill(p, c, 4)
    # close the gap between the legs visually: inner faces a shade darker (the skirt's fold)
    s.frect("rleg", "left", 0, 0, 4, 12, dk(c, 12), 3)
    s.frect("lleg", "right", 0, 0, 4, 12, dk(c, 12), 3)
    if hem:
        hr = hem_row if hem_row is not None else to_row - 1
        for p in LEGS:
            s.ring(p, hr, hem, 2)
    if overlay:
        for pp, base in (("rpants", "rleg"), ("lpants", "lleg")):
            for f in ("right", "front", "left", "back"):
                for y in range(6, to_row):
                    if (pp == "rpants" and f == "left") or (pp == "lpants" and f == "right"):
                        continue
                    fx, fy, w, h = face_box(base, f)
                    for x in range(w):
                        cc = s.px[fx + x, fy + y]
                        s.fpx(pp, f, x, y, cc[:3])


# ---- more clothes and props ---------------------------------------------------------------------------------------
def stripes(s, part, base, stripe, every=3, off=0, faces=("top", "bottom") + SIDES, vertical=True):
    def fn(f, x, y, cur):
        k = (x if vertical else y) + off
        c = stripe if k % every == 0 else base
        return tuple(clamp(v + s.rng.randint(-2, 2)) for v in c)
    s.tex(part, faces, fn)


def spots(s, part, faces, base, spot, region=None, density=0.22):
    """Leopard pelt: tan with dark rosettes. region(face, x, y) -> bool limits where it goes."""
    def fn(f, x, y, cur):
        if region and not region(f, x, y):
            return None
        r = s.rng.random()
        return spot if r < density else tuple(clamp(v + s.rng.randint(-6, 6)) for v in base)
    s.tex(part, faces, fn)


def diag(s, c, part="body", x0=1, x1=6, rows=(0, 12), width=2, back=True, shoulder="right"):
    """A sash or strap from one shoulder (character's right by default) to the opposite hip, front and back."""
    r0, r1 = rows
    n = r1 - r0
    for i in range(n):
        y = r0 + i
        t = i / max(1, n - 1)
        fx = round(x0 + (x1 - x0) * t) if shoulder == "right" else round(x1 - (x1 - x0) * t)
        bx = 7 - fx
        for k in range(width):
            s.fpx(part, "front", fx + k, y, c, 2)
            if back:
                s.fpx(part, "back", bx - k, y, c, 2)
    s.frect(part, "top", 0 if shoulder == "right" else 6, 0, 2 if shoulder == "right" else 8, 4, c, 2)


def iron_collar(s, c=(104, 108, 114), ring=True):
    for f in SIDES:
        fx, fy, w, h = face_box("body", f)
        for x in range(w):
            s.fpx("body", f, x, 0, c, 3)
    s.frect("body", "top", 0, 0, 8, 4, c, 3)
    if ring:
        s.fpx("body", "front", 3, 1, lt(c, 30)); s.fpx("body", "front", 4, 1, dk(c, 10))


def flare(s, r0, r1):
    """Copy leg rows r0..r1 onto the pants layer (outer faces only) so a skirt or coat hem stands out a little."""
    for pp, base in (("rpants", "rleg"), ("lpants", "lleg")):
        for f in SIDES:
            if (pp == "rpants" and f == "left") or (pp == "lpants" and f == "right"):
                continue
            fx, fy, w, h = face_box(base, f)
            for y in range(max(0, r0), r1):
                for x in range(w):
                    cc = s.px[fx + x, fy + y]
                    s.fpx(pp, f, x, y, cc[:3])


def kilt(s, c, to_row=6, flap=None, hem=None, pleats=True):
    """Egyptian shendyt: wrapped kilt over the thighs, optional front flap."""
    for p in LEGS:
        for r in range(0, to_row):
            s.ring(p, r, c, 3)
            if pleats:
                for i in range(s.ring_w(p)):
                    if i % 3 == 2:
                        s.ringpx(p, i, r, dk(c, 12))
    if hem:
        for p in LEGS:
            s.ring(p, to_row - 1, hem, 1)
    if flap:
        for y in range(0, to_row + 1):
            for x in range(2, 4):
                s.fpx("rleg", "front", x, y, flap, 2)
            for x in range(0, 2):
                s.fpx("lleg", "front", x, y, flap, 2)
    flare(s, max(0, to_row - 3), to_row)


def coat_skirts(s, c, to_row=8, opening=None, hem=None, texfn=None):
    """Coat skirts down the legs to to_row, flared on the pants layer; opening colours the gap between the fronts."""
    for p in LEGS:
        if texfn:
            for r in range(0, to_row):
                for i in range(s.ring_w(p)):
                    s.ringpx(p, i, r, texfn(i, r))
        else:
            for r in range(0, to_row):
                s.ring(p, r, c, 3)
    if opening:
        s.frect("rleg", "front", 3, 0, 4, to_row, opening, 1)
        s.frect("lleg", "front", 0, 0, 1, to_row, opening, 1)
    if hem:
        for p in LEGS:
            s.ring(p, to_row - 1, hem, 1)
    flare(s, max(0, to_row - 4), to_row)


def wide_hat(s, c, band=None, crown_rows=2, brim=None):
    """Felt hat on the hat layer: crown over the top and upper rows; the last row is the brim edge."""
    s.fill("hat", c, 3, faces=("top",))
    for f in SIDES:
        s.frect("hat", f, 0, 0, 8, crown_rows + 1, c, 3)
        if band:
            s.frect("hat", f, 0, crown_rows - 1, 8, crown_rows, band, 1)
        s.frect("hat", f, 0, crown_rows, 8, crown_rows + 1, brim or dk(c, 22), 2)


def knit_cap(s, c, rows=3, fold=True):
    s.fill("hat", c, 3, faces=("top",))
    for f in SIDES:
        for x in range(8):
            for y in range(rows):
                s.fpx("hat", f, x, y, c if x % 2 else dk(c, 14), 2)
        if fold:
            s.frect("hat", f, 0, rows - 1, 8, rows, lt(c, 14), 2)


def buttons(s, c, x=4, rows=range(2, 11, 2), part="body"):
    for y in rows:
        s.fpx(part, "front", x, y, c)


def mud(s, parts, r0, r1, c, density=0.35, faces=SIDES):
    for p in parts:
        for f in faces:
            fx, fy, w, h = face_box(p, f)
            for y in range(r0, min(r1, h)):
                d = density * (0.5 + 0.5 * (y - r0) / max(1, r1 - r0))
                for x in range(w):
                    if s.rng.random() < d:
                        s.fpx(p, f, x, y, c, 6)


def clogs(s, wood=(200, 166, 104), legs=LEGS):
    for p in legs:
        s.rows(p, 10, 12, wood, 4)
        s.ring(p, 10, lt(wood, 12), 2)
        s.frect(p, "front", 0, 11, 4, 12, dk(wood, 18), 2)
        s.frect(p, "bottom", 0, 0, 4, 4, dk(wood, 30), 2)


def sandals(s, tone, strap, legs=LEGS):
    for p in legs:
        s.rows(p, 9, 12, tone, 3)
        s.ring(p, 11, strap, 1)
        s.fpx(p, "front", 1, 10, strap); s.fpx(p, "front", 2, 10, strap)
        s.frect(p, "bottom", 0, 0, 4, 4, dk(strap, 20), 2)


def barefoot(s, tone, dirt=None, legs=LEGS):
    for p in legs:
        s.rows(p, 9, 12, tone, 3)
        if dirt:
            s.ring(p, 11, mix(tone, dirt, 0.45), 4)
        s.frect(p, "bottom", 0, 0, 4, 4, mix(tone, dirt or dk(tone, 40), 0.5), 3)


def wig(s, c, rows_side=7, fringe=2, fillet=None, band_row=None):
    """Azmar bob wig on the base head: top, blunt fringe, long sides and back, locks framing the face."""
    hi = lt(c, 22)
    for x in range(8):
        for y in range(8):
            s.fpx("head", "top", x, y, hi if (x + y) % 4 == 0 else c, 2)
    for y in range(fringe):
        for x in range(8):
            s.fpx("head", "front", x, y, c, 2)
    for y in range(fringe, fringe + 4):
        s.fpx("head", "front", 0, y, c, 2); s.fpx("head", "front", 7, y, c, 2)
    for f in ("right", "left"):
        for x in range(8):
            for y in range(rows_side):
                s.fpx("head", f, x, y, hi if (x * 2 + y) % 5 == 0 else c, 2)
    for x in range(8):
        for y in range(rows_side):
            s.fpx("head", "back", x, y, hi if (x * 2 + y) % 5 == 0 else c, 2)
    if fillet:
        r = band_row if band_row is not None else fringe - 1
        for f in SIDES:
            s.frect("head", f, 0, r, 8, r + 1, fillet, 1)
        for x in (3, 4):
            s.fpx("head", "front", x, r, lt(fillet, 20))


def usekh(s, gold, bead, bead2=None, rows=4, part="body", back=True):
    """Broad collar: arcs of beads round the neck, front and back."""
    for f in (("front", "back") if back else ("front",)):
        for dy in range(rows):
            a = dy
            for x in range(a, 8 - a):
                c = gold if (x + dy) % 3 else bead
                if bead2 and dy == rows - 1:
                    c = bead2
                s.fpx(part, f, x, dy, c)
    for f in ("right", "left"):
        s.frect(part, f, 0, 0, 4, 2, gold, 2)
    s.frect(part, "top", 0, 0, 8, 4, gold, 3)


def armbands(s, c, row, arms=ARMS):
    for p in arms:
        s.ring(p, row, c, 1)


def shoulder_cape(s, c, rows=3, dag=None):
    for f in SIDES:
        fx, fy, w, h = face_box("jacket", f)
        for y in range(rows):
            for x in range(w):
                if y == rows - 1 and dag and x % dag == 1:
                    continue
                s.fpx("jacket", f, x, y, c if y < rows - 1 else dk(c, 12), 3)
    s.fill("jacket", c, 3, faces=("top",))
    for p in SLEEVES:
        s.fill(p, c, 3, faces=("top",))
        for f in SIDES:
            fx, fy, w, h = face_box(p, f)
            for y in range(rows - 1):
                for x in range(w):
                    s.fpx(p, f, x, y, c if y < rows - 2 else dk(c, 12), 3)
