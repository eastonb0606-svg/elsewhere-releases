"""Generates the Elsewhere Arsenal ("Glimmer") textures: original pixel art in the vanilla item style.

16x16 item icons, 64x32 humanoid / humanoid_leggings equipment layers, the amethyst bolt spell sprite and the
Clarity effect icon, plus a contact sheet (tools/texture_sheet.png) for eyeballing everything at x8.

Run from anywhere:  python3 arsenal/tools/gen_textures.py      (needs Pillow)

Conventions: hard pixels only (no anti-aliasing, no smooth gradients), 3-4 step shade ramps lit from the
top-left, 1-px dark outline around item silhouettes, fully transparent background.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.normpath(os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures"))
SHEET = os.path.join(HERE, "texture_sheet.png")


# ------------------------------------------------------------------------------------------------ colours

def C(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


CLEAR = (0, 0, 0, 0)

# Glimmer amethyst
AM_OUT, AM_DEEP, AM_DARK, AM_MAIN, AM_LIGHT, WHITE = (C("2A1F3D"), C("4E2F94"), C("6B45B8"), C("A77BF3"),
                                                      C("D9C2FF"), C("FFFFFF"))
# Moonsilk cloth (pale silver-lavender) + the faint moon motif colour
SILK = {"o": C("3B3352"), "d": C("8C80AE"), "m": C("B3A9D1"), "l": C("D6CFEA"), "h": C("F1EEFA"),
        "n": C("9A8FD0"), "t": AM_MAIN, "T": AM_DARK, "x": C("2B2440")}
# Netherite-grey lining
LINE = {"o": C("1C1819"), "d": C("2C2729"), "m": C("3B3537"), "l": C("4D4446"), "h": C("625759")}
# Storm cloth
STORM = {"o": C("232B3A"), "d": C("3E4C63"), "m": C("5A6E8C"), "l": C("8FA7C9"), "h": C("C2D3EA")}
BOLT_W, BOLT_Y, BOLT_O = C("FFFFFF"), C("FFE45C"), C("E8A22E")
FEATHER = {"F": C("EEF1F6"), "f": C("B9C2D0"), "q": C("6F7A8C")}
# Silver + choir-quartz gems
SILVER = {"o": C("34344A"), "d": C("767891"), "m": C("AEB1C6"), "l": C("DADDEA"), "h": C("FFFFFF")}
GEM = {"o": C("23436A"), "d": C("4F8FC0"), "m": C("9FD8F2"), "l": C("D8F4FF"), "h": C("FFFFFF")}
# Gold / sun-glass
GOLD = {"o": C("5A3E12"), "d": C("A9792A"), "m": C("E0B54A"), "l": C("F8E39A"), "h": C("FFF8DC")}
SUN = {"o": C("7A2E0A"), "d": C("D9621E"), "m": C("F49B2C"), "l": C("FFD54A"), "h": C("FFF7C2")}
# Sculk
SCULK = {"o": C("061A1E"), "d": C("0F3A40"), "m": C("1E6E73"), "l": C("2C9096"), "g": C("3FD4D9"),
         "G": C("A8F6F4")}
# Wood / copper
WOOD = {"o": C("3A2512"), "d": C("6A4626"), "m": C("8E6236"), "l": C("B88B50")}
COPPER = {"d": C("7A3E26"), "m": C("C46A3E"), "l": C("EFA172")}
# Skyiron (bow)
SKY = {"o": C("1F2836"), "d": C("4F6178"), "m": C("86A0BE"), "l": C("C3D6EA")}
ROC = {"B": C("3E2716"), "b": C("8A5A32"), "f": C("C9A27A"), "F": C("F6F3EC")}


def lerp(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


# ------------------------------------------------------------------------------------------------ canvas

class Canvas:
    def __init__(self, w=16, h=16):
        self.w, self.h = w, h
        self.p = [[None] * w for _ in range(h)]

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def put(self, x, y, c):
        if self.inside(x, y):
            self.p[y][x] = c

    def get(self, x, y):
        return self.p[y][x] if self.inside(x, y) else None

    def grid(self, rows, pal, ox=0, oy=0):
        """Stamp ASCII art: every char except '.' and ' ' is looked up in pal."""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch not in ". ":
                    self.put(ox + x, oy + y, pal[ch])

    def rect(self, x, y, w, h, c):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                self.put(xx, yy, c)

    def outline(self, color, only=None, diagonal=False):
        """1-px outline on empty pixels touching a filled pixel (optionally only pixels for which only(x,y))."""
        nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
        if diagonal:
            nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
        add = []
        for y in range(self.h):
            for x in range(self.w):
                if self.p[y][x] is not None:
                    continue
                for dx, dy in nb:
                    if self.get(x + dx, y + dy) is not None and (only is None or only(x + dx, y + dy)):
                        add.append((x, y))
                        break
        for x, y in add:
            self.p[y][x] = color

    def mirror_x(self):
        self.p = [row[::-1] for row in self.p]

    def image(self):
        im = Image.new("RGBA", (self.w, self.h))
        im.putdata([c or CLEAR for row in self.p for c in row])
        return im


def save(cv, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cv.image().save(path)
    WRITTEN.append(rel)
    return path


WRITTEN = []


def diag(cv, a_lo, a_hi, b_lo, b_hi, colour, axis=15):
    """Fill the band of a bottom-left -> top-right diagonal object.

    a = x - y runs along the object (grows towards the top-right), b = x + y across it (grows towards the
    bottom-right).  b_lo/b_hi may be ints or callables of a; colour(a, b) returns a colour or None."""
    for y in range(cv.h):
        for x in range(cv.w):
            a, b = x - y, x + y
            if not (a_lo <= a <= a_hi):
                continue
            lo = b_lo(a) if callable(b_lo) else b_lo
            hi = b_hi(a) if callable(b_hi) else b_hi
            if lo <= b <= hi:
                c = colour(a, b)
                if c is not None:
                    cv.put(x, y, c)


def tagged(cv):
    """Snapshot of currently filled coordinates (used to restrict outlines to one part)."""
    return {(x, y) for y in range(cv.h) for x in range(cv.w) if cv.p[y][x] is not None}


# ================================================================================================ ITEMS
# ------------------------------------------------------------------------------------------- casters

def stick(cv, a_lo, a_hi, ramp, axis=15):
    """Vanilla-stick style rod: lighter core on the axis, mid on the upper-left, dark edge on the lower-right."""
    def col(a, b):
        if a <= a_lo + 1:  # rounded bottom cap
            return ramp["o"] if b >= axis else ramp["d"]
        return {axis - 1: ramp["m"], axis: ramp["l"], axis + 1: ramp["o"]}.get(b)
    diag(cv, a_lo, a_hi, axis - 1, axis + 1, col)


def amethyst_wand():
    cv = Canvas()
    stick(cv, -13, 2, WOOD)
    # copper band
    band = lambda a, b: {13: COPPER["d"], 14: COPPER["l"], 15: COPPER["l"], 16: COPPER["m"], 17: COPPER["d"]}[b]
    diag(cv, 3, 4, 13, 17, band)
    # amethyst crystal tip: widest in the middle, pointed at the top-right
    width = {5: 1, 6: 2, 7: 2, 8: 2, 9: 2, 10: 1, 11: 1, 12: 0, 13: 0}

    def crystal(a, b):
        d = b - 15
        if a >= 12:
            return AM_LIGHT
        if d <= -1:
            return AM_LIGHT if d == -1 or a < 9 else WHITE
        if d == 0:
            return AM_MAIN
        return AM_DARK if d == 1 else AM_DEEP
    diag(cv, 5, 13, lambda a: 15 - width[a], lambda a: 15 + width[a], crystal)
    cv.put(11, 3, WHITE)
    body = tagged(cv)
    stick_px = {(x, y) for (x, y) in body if (x - y) <= 2}
    cv.outline(AM_OUT, only=lambda x, y: (x, y) not in stick_px)
    return cv


def sunlance_staff():
    cv = Canvas()
    # long pale-gold shaft from the very corner
    stick(cv, -15, 4, GOLD)
    # sun-glass rings along the shaft
    diag(cv, -6, -6, 14, 16, lambda a, b: SUN["d"] if b == 16 else SUN["m"])
    diag(cv, 0, 0, 14, 16, lambda a, b: SUN["d"] if b == 16 else SUN["m"])
    # golden collar under the head
    diag(cv, 3, 4, 13, 17, lambda a, b: {13: GOLD["m"], 14: GOLD["l"], 15: GOLD["l"], 16: GOLD["m"], 17: GOLD["d"]}[b])
    shaft = tagged(cv)
    # glowing sun-glass orb
    cx, cy = 11, 4
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - cx, y - cy)
            if d <= 2.3:
                # light from the top-left inside the glass, hot white core
                s = (x - cx) + (y - cy)
                if d < 0.8:
                    c = SUN["h"]
                elif s <= -1:
                    c = SUN["l"] if d > 1.5 else SUN["h"]
                elif s <= 1:
                    c = SUN["l"]
                else:
                    c = SUN["m"] if s <= 2 else SUN["d"]
                cv.put(x, y, c)
    cv.put(10, 3, WHITE)
    head = tagged(cv) - shaft
    cv.outline(SUN["o"], only=lambda x, y: (x, y) in head or (x - y) >= 3)
    # rays: short spikes off the outline (not towards the shaft)
    for dx, dy, c in ((0, -4, SUN["l"]), (4, 0, SUN["l"]), (3, -3, SUN["m"]), (-3, -3, SUN["m"]),
                      (-4, 0, SUN["m"]), (0, 4, SUN["m"]), (3, 3, SUN["m"])):
        cv.put(cx + dx, cy + dy, c)
    return cv


def sculk_resonator():
    """Tuning fork of sculk: dark grip with glowing nodes, an echo-shard gem in the yoke, two open prongs
    with cyan tips and a resonance glint hanging between them."""
    cv = Canvas()
    stick(cv, -12, 0, {"o": SCULK["o"], "d": SCULK["d"], "m": SCULK["d"], "l": SCULK["m"]})
    for a in (-8, -4):  # glowing sculk nodes on the grip
        diag(cv, a, a, 15, 15, lambda a, b: SCULK["g"])

    def yoke(a, b):
        if 14 <= b <= 16:
            return SCULK["G"] if (a == 1 and b == 15) else SCULK["g"]
        return SCULK["l"] if b <= 12 else (SCULK["m"] if b <= 17 else SCULK["d"])
    diag(cv, 1, 2, 11, 19, yoke)

    def prong(a, b):
        lit = b in (11, 18)
        if a >= 9:
            return SCULK["G"] if lit else SCULK["g"]
        return SCULK["l"] if lit else SCULK["d"]
    diag(cv, 3, 10, 11, 12, prong)
    diag(cv, 3, 10, 18, 19, prong)
    body = tagged(cv)
    grip = {(x, y) for (x, y) in body if (x - y) <= 0}
    cv.outline(SCULK["o"], only=lambda x, y: (x, y) not in grip)
    # echo glint resonating between the prong tips
    cv.put(12, 3, SCULK["g"])
    cv.put(13, 2, SCULK["G"])
    return cv


# ------------------------------------------------------------------------------------------- armour icons

HOOD = [
    "................",
    "................",
    "................",
    ".....oooooo.....",
    "....ohhllmmo....",
    "...ohllnllmdo...",
    "...ohlnlllmdo...",
    "...oltttttTdo...",
    "...olTxxxxTdo...",
    "...olTxxxxTmo...",
    "...omTxxxxTdo...",
    "....oo....oo....",
    "................",
    "................",
    "................",
    "................",
]
ROBE = [
    "................",
    "................",
    ".ooooo....ooooo.",
    ".ohlto....otlmo.",
    ".ohllto..otlmmo.",
    ".olllltoottlmmo.",
    ".odlllltTllmmdo.",
    ".oodlllltTlmdoo.",
    "...ollnltTlmmo..",
    "...olnlltTlmmo..",
    "...ollnltTmmdo..",
    "...olllltTmmdo..",
    "...odmmmtTmddo..",
    "...ottttttTTTo..",
    "....oooooooooo..",
    "................",
]
LEGS = [
    "................",
    "................",
    "....oooooooo....",
    "...ottttttTTo...",
    "...ohllllmmdo...",
    "...ohllmmlmdo...",
    "...ohldoodmdo...",
    "...ohlo..olmo...",
    "...olmo..olmo...",
    "...olno..omdo...",
    "...olmo..omdo...",
    "...ommo..omdo...",
    "...otTo..otTo...",
    "...oooo..oooo...",
    "................",
    "................",
]
BOOTS = [
    "................",
    "................",
    "................",
    "....ooo..ooo....",
    "...ottTo.otTo...",
    "...ohlmo.olmo...",
    "...ohlmo.olmo...",
    "...olmdo.olmo...",
    "...olmdo.olmdo..",
    "..ohlmdo.ohlmdo.",
    ".ohlnmdo.olnmmdo",
    ".odmmddo.oddmmdo",
    ".oooooo...oooooo",
    "................",
    "................",
    "................",
]


def fix_rows(rows):
    return [(r + "................")[:16] for r in rows]


BOOTS = fix_rows(BOOTS)
ROBE = fix_rows(ROBE)


def armour_icon(rows, lined=False):
    cv = Canvas()
    cv.grid(rows, SILK)
    if lined:
        # dark netherite plates/lining on every cloth pixel that touches the outline or the outside;
        # the pale cloth stays on top in the middle, the purple trim is kept
        base = [row[:] for row in cv.p]
        out = {SILK["o"], SILK["x"]}
        trim = {SILK["t"], SILK["T"]}
        for y in range(16):
            for x in range(16):
                c = base[y][x]
                if c is None or c in out or c in trim:
                    continue
                edge = False
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    n = base[y + dy][x + dx] if 0 <= x + dx < 16 and 0 <= y + dy < 16 else None
                    if n is None or n in out:
                        edge = True
                if edge:
                    lit = (x + y) < 15 and c in (SILK["h"], SILK["l"])
                    cv.p[y][x] = LINE["h"] if lit else (LINE["l"] if c in (SILK["l"], SILK["h"], SILK["n"]) else LINE["m"])
                elif c == SILK["h"]:
                    cv.p[y][x] = SILK["l"]
                elif c == SILK["d"]:
                    cv.p[y][x] = LINE["l"]
        for y in range(16):
            for x in range(16):
                if cv.p[y][x] == SILK["o"]:
                    cv.p[y][x] = LINE["o"]
                elif cv.p[y][x] == SILK["x"]:
                    cv.p[y][x] = LINE["d"]
    return cv


MANTLE = [
    "................",
    "................",
    ".ooooo....ooooo.",
    ".oFFfo....oFffo.",
    ".oFfFfo..oFfFfo.",
    ".ofFfFfooFfFfFo.",
    ".omFmFmFFmFmFmo.",
    ".oodllmmyWlmdoo.",
    "...ollmyWmmdo...",
    "...olmyWWWWdo...",
    "...olmmmyWmdo...",
    "...olmmyWmmdo...",
    "...odmmWmmddo...",
    "....odmmmmdo....",
    ".....oooooo.....",
    "................",
]


def stormsage_mantle():
    cv = Canvas()
    pal = dict(STORM)
    pal.update(FEATHER)
    pal.update({"W": BOLT_W, "y": BOLT_Y})
    cv.grid(MANTLE, pal)
    return cv


# Left half only (cols 0-7); mirrored to the right with every shade one step darker (light from the left).
# h/l/m/d = silver ramp, H/G/g/b = choir-quartz gem ramp.  A curved silver band (ends sweep up and back),
# a tall quartz crystal in the middle and a smaller one on each side, a gem set in the front of the band.
DIADEM_LEFT = [
    "........",
    "........",
    ".......H",
    "......GH",
    "......GH",
    "......Gg",
    "..H...Gg",
    ".GH....l",
    ".Gg...ll",
    "..l..lhl",
    "hhllllll",
    ".mllllgH",
    "..mmmmmb",
    "........",
    "........",
    "........",
]
DARKER = {"h": "l", "l": "m", "m": "d", "d": "d", "H": "G", "G": "g", "g": "b", "b": "b", ".": "."}


def cantors_diadem():
    cv = Canvas()
    rows = [left + "".join(DARKER[ch] for ch in reversed(left)) for left in DIADEM_LEFT]
    pal = {"h": SILVER["h"], "l": SILVER["l"], "m": SILVER["m"], "d": SILVER["d"],
           "H": GEM["h"], "G": GEM["l"], "g": GEM["m"], "b": GEM["d"]}
    cv.grid(rows, pal)
    cv.outline(SILVER["o"])
    return cv


# ------------------------------------------------------------------------------------------- trinkets

FOCUS = [
    "................",
    "..w..........w..",
    ".wWw...ooo..wWw.",
    "..w...oLhLo..w..",
    ".....oLhLLPo....",
    "....oLLhLPPpo...",
    "....oLLLPPPpo...",
    "...gGLPPPPppGg..",
    "...gGPPPPpppGg..",
    "...kgGppppdGgk..",
    "....kgGGYGGgk...",
    ".....kggYggk....",
    "......kgggk.....",
    ".....kgGGGgk....",
    ".....kkkkkkk....",
    "................",
]


def glimmerstone_focus():
    cv = Canvas()
    pal = {"o": AM_OUT, "L": AM_LIGHT, "h": WHITE, "P": AM_MAIN, "p": AM_DARK, "d": AM_DEEP,
           "g": GOLD["d"], "G": GOLD["m"], "k": GOLD["o"], "Y": SUN["l"], "w": AM_LIGHT, "W": WHITE}
    cv.grid(FOCUS, pal)
    return cv


TONIC = [
    "................",
    "................",
    "......jKKj......",
    "......kKKk......",
    ".....gjkkjG.....",
    "......gLLG......",
    "......gPLG......",
    ".....gLPPPG.....",
    "....gLsPPPpG....",
    "...gLPPPPsppG...",
    "...gLPsPPPppG...",
    "...gPPPPPpppG...",
    "...gpPPPPppdG...",
    "....GppppddG....",
    ".....GGGGGG.....",
    "................",
]


def tonic(strong=False):
    cv = Canvas()
    glass = {"g": C("DDEBF7"), "G": C("7F93B0")}
    cork = {"j": C("4E2E18"), "k": C("8A5634"), "K": C("C08A5C")}
    if strong:
        liquid = {"L": C("F4ECFF"), "P": C("D2B6FF"), "p": C("A77BF3"), "d": C("7B55C8"), "s": WHITE}
    else:
        liquid = {"L": AM_LIGHT, "P": AM_MAIN, "p": AM_DARK, "d": AM_DEEP, "s": C("F2E8FF")}
    pal = {}
    pal.update(glass)
    pal.update(cork)
    pal.update(liquid)
    cv.grid(TONIC, pal)
    if strong:
        for x, y, c in ((5, 11, WHITE), (8, 12, C("FFF1A0")), (9, 8, C("FFF1A0")), (7, 10, WHITE),
                        (10, 10, WHITE), (6, 13, C("FFF1A0"))):
            cv.put(x, y, c)
        # a couple of motes escaping the cork
        cv.put(10, 1, C("FFF1A0"))
        cv.put(4, 3, WHITE)
    return cv


THREAD = [
    "................",
    "................",
    "....oooooooo....",
    "...oPLLPPPPpo...",
    "...oppppppppo...",
    "....ohllllmo....",
    "....olmmmmdo....",
    "....ohllllmo....",
    "....olnmmmdo....",
    "....ohllllmo....",
    "....olmmmmdo....",
    "....ohllllmo....",
    "...oPLLPPPPpo...",
    "...oppppppppo...",
    "....oooooooo....",
    "................",
]


def moonsilk_thread():
    cv = Canvas()
    pal = dict(SILK)
    pal.update({"P": AM_MAIN, "L": AM_LIGHT, "p": AM_DARK})
    cv.grid(THREAD, pal)
    # a single loose strand trailing off the spool
    for x, y, c in ((12, 7, SILK["h"]), (13, 7, SILK["l"]), (13, 8, SILK["m"]), (13, 9, SILK["l"]),
                    (14, 10, SILK["l"]), (14, 11, SILK["m"]), (14, 12, SILK["l"]), (13, 13, SILK["m"])):
        cv.put(x, y, c)
    return cv


# ------------------------------------------------------------------------------------------- longbow
# Drawn on the upper-right half (x >= y) and mirrored across the x = y diagonal, so both limbs match.
# Tips at (12,2) / (2,12) leave the corners free for the roc feathers.

BOW_HALF = [
    "................",
    "........DDDD....",
    "......DDLMMMo...",
    "...DDDMMoooo....",
    "....GG..........",
    ".....o..........",
    "................",
]
FEATHER_TOP = [  # placed at (12, 0): hangs from the upper tip down the right edge
    ".BB.",
    "BFFB",
    "oFfB",
    ".BFb",
    ".BFb",
    ".Bbf",
    "..Bb",
    "..BB",
]


def bow_base(pull):
    cv = Canvas()
    pal = {"D": SKY["d"], "M": SKY["m"], "L": SKY["l"], "o": SKY["o"], "G": C("7A4E30"), "g": C("4A2C18")}
    half = {}
    for y, row in enumerate(BOW_HALF):
        for x, ch in enumerate(row):
            if ch != "." and x >= y:
                half[(x, y)] = pal[ch]
    # grip on the bend
    half[(4, 4)] = pal["G"]
    half[(5, 4)] = pal["G"]
    half[(3, 3)] = pal["g"]
    half[(4, 3)] = pal["g"]
    for (x, y), c in half.items():
        cv.put(x, y, c)
        cv.put(y, x, c)
    # roc feathers
    fpal = {"B": ROC["B"], "b": ROC["b"], "f": ROC["f"], "F": ROC["F"], "o": SKY["o"]}
    feather = {}
    for y, row in enumerate(FEATHER_TOP):
        for x, ch in enumerate(row):
            if ch != ".":
                feather[(12 + x, y)] = fpal[ch]
    for (x, y), c in feather.items():
        cv.put(x, y, c)
        cv.put(y, x, c)
    return cv


STRING = C("E2DCF2")
STRING_D = C("9C94B8")


def line_px(x0, y0, x1, y1):
    n = max(abs(x1 - x0), abs(y1 - y0))
    return [(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n)) for i in range(n + 1)] if n else [(x0, y0)]


def roc_bow(stage):
    """stage None = idle, 0..2 = pulling frames (string and arrow drawn back towards the bottom-right)."""
    cv = bow_base(stage)
    top, bot = (11, 3), (3, 11)
    if stage is None:
        for x, y in line_px(*top, *bot):
            cv.put(x, y, STRING)
        return cv
    nock = [(9, 9), (10, 10), (11, 11)][stage]
    for x, y in line_px(*top, *nock) + line_px(*nock, *bot):
        if cv.get(x, y) is None:
            cv.put(x, y, STRING)
    # arrow along x = y: flint head at the top-left, shaft to the nock, white fletching at the string
    tip = nock[0] - 8
    shaft_dark, shaft_light = C("3A2512"), C("A27B4E")
    for i in range(tip + 2, nock[0]):
        cv.put(i, i, shaft_dark)
        cv.put(i + 1, i, shaft_light)
    cv.put(tip, tip, C("FFFFFF"))
    cv.put(tip + 1, tip, C("C9C9D2"))
    cv.put(tip, tip + 1, C("C9C9D2"))
    cv.put(tip + 1, tip + 1, C("7E7E8C"))
    fx = nock[0] - 1
    cv.put(fx - 1, fx, ROC["F"])
    cv.put(fx, fx + 1, ROC["F"])
    cv.put(fx - 2, fx - 1, AM_LIGHT)
    return cv


# ================================================================================================ SPELL / EFFECT

def amethyst_bolt():
    cv = Canvas()
    cx = cy = 7.5
    for y in range(16):
        for x in range(16):
            dx, dy = x - cx, y - cy
            d = math.hypot(dx, dy)
            star = min(abs(dx), abs(dy))  # distance to the cross arms
            c = None
            if d <= 1.6:
                c = WHITE
            elif d <= 3.0:
                c = AM_LIGHT
            elif d <= 4.4:
                c = AM_MAIN
            elif d <= 5.4:
                c = AM_DARK
            elif star <= 0.6 and d <= 7.6:
                c = AM_MAIN if d <= 6.6 else AM_LIGHT
            if c is not None:
                cv.put(x, y, c)
    # lilac flecks on the rim so the orb reads as a star
    for (x, y) in ((7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)):
        cv.put(x, y, AM_LIGHT)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        cv.put(x, y, AM_LIGHT)
    return cv


def clarity():
    cv = Canvas(18, 18)
    cx, cy = 8.5, 9.5
    OR_D, OR_M, OR_L = C("B3541A"), C("F08A24"), C("FFC46A")
    for y in range(18):
        for x in range(18):
            dx = (x - cx) / 7.6
            half = 3.6 * (1 - dx * dx)
            if half <= 0.35:
                continue
            if abs(y - cy) <= half:
                cv.put(x, y, C("EDE4FF"))
    eye = tagged(cv)
    # iris (orange eyeblossom glow) + amethyst pupil
    for y in range(18):
        for x in range(18):
            d = math.hypot(x - cx, y - cy)
            if (x, y) in eye and d <= 3.2:
                if d <= 1.3:
                    c = AM_DEEP
                elif d <= 2.2:
                    c = OR_M if (x + y) < cx + cy else OR_D
                else:
                    c = OR_L if (y < cy) else OR_M
                cv.put(x, y, c)
    cv.put(7, 8, WHITE)
    cv.put(8, 8, WHITE)
    # lower lid shade
    for x in range(18):
        for y in range(17, -1, -1):
            if (x, y) in eye:
                if cv.p[y][x] == C("EDE4FF"):
                    cv.p[y][x] = AM_LIGHT
                break
    cv.outline(AM_OUT)
    # amethyst lashes / eyeblossom petals fanning off the lid: dark at the root, bright at the tip
    lashes = [[(8, 4), (8, 3), (8, 2)], [(9, 4), (9, 3), (9, 2)],
              [(5, 5), (4, 4), (3, 3)], [(12, 5), (13, 4), (14, 3)],
              [(2, 7), (1, 6)], [(15, 7), (16, 6)]]
    for lash in lashes:
        for i, (x, y) in enumerate(lash):
            cv.put(x, y, AM_DARK if i == 0 else (AM_LIGHT if i == len(lash) - 1 else AM_MAIN))
    for (x, y) in ((8, 15), (9, 15), (5, 14), (12, 14)):  # short lower lashes
        cv.put(x, y, AM_DARK)
    return cv


# ================================================================================================ EQUIPMENT LAYERS
# Box UV (vanilla ModelPart.addBox texOffs): for a box w x h x d at (u, v):
#   top (u+d, v, w, d)  bottom (u+d+w, v, w, d)
#   right (u, v+d, d, h)  front (u+d, v+d, w, h)  left (u+d+w, v+d, d, h)  back (u+2d+w, v+d, w, h)
# The four side faces form one contiguous strip, so horizontal trims wrap all the way round.

def faces(u, v, w, h, d):
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)
SIDES = ("right", "front", "left", "back")


def cloth_rect(cv, rect, pal, seed, rows=None, seam=None, light_top=True):
    """Fill a face with a soft woven cloth: base tone with a sparse twill of highlight/shade pixels, the
    lower part of tall faces one step darker, and (on faces 8+ px wide only, i.e. the box corners) a 1-px
    seam colour down the left/right edge."""
    x0, y0, w, h = rect
    rng = random.Random(seed)
    ylo, yhi = (y0, y0 + h) if rows is None else (max(y0, rows[0]), min(y0 + h, rows[1]))
    for y in range(ylo, yhi):
        for x in range(x0, x0 + w):
            t = (y - y0) / max(1, h - 1)
            base = pal["l"] if (t < 0.55 or not light_top) else pal["m"]
            r = rng.random()
            if (x + 2 * y) % 5 == 0 and r < 0.7:
                c = pal["h"] if base == pal["l"] else pal["l"]
            elif (x - y) % 7 == 0 and r < 0.5:
                c = pal["m"] if base == pal["l"] else pal["d"]
            else:
                c = base
            if seam is not None and w >= 8 and x in (x0, x0 + w - 1):
                c = seam
            cv.put(x, y, c)


def strip(face_map, sides=SIDES):
    """Bounding rect of the contiguous side strip of a box."""
    xs = [face_map[s][0] for s in sides]
    xe = [face_map[s][0] + face_map[s][2] for s in sides]
    y0 = face_map[sides[0]][1]
    return min(xs), y0, max(xe) - min(xs), face_map[sides[0]][3]


def hline(cv, x0, x1, y, c):
    for x in range(x0, x1):
        cv.put(x, y, c)


def crescent(cv, x, y, c):
    """Tiny 3x3 moon: ( shape."""
    for dx, dy in ((1, 0), (0, 1), (1, 2)):
        cv.put(x + dx, y + dy, c)


def moonsilk_layer(lined=False):
    """Hood (head), robe (body + sleeves) and boots (lower legs) - humanoid layer."""
    cv = Canvas(64, 32)
    seam = LINE["m"] if lined else SILK["m"]
    pal = SILK
    trim, trim_d = AM_MAIN, AM_DARK
    # ---- hood: every face but the bottom, face opening on the front
    cloth_rect(cv, HEAD["top"], pal, 1, seam=seam, light_top=False)
    for i, s in enumerate(SIDES):
        cloth_rect(cv, HEAD[s], pal, 10 + i, seam=seam)
    fx, fy, fw, fh = HEAD["front"]
    for y in range(fy + 2, fy + fh):
        for x in range(fx + 1, fx + fw - 1):
            cv.put(x, y, None)  # face opening
    hline(cv, fx + 1, fx + fw - 1, fy + 1, trim)  # brow trim
    sx, sy, sw, sh = strip(HEAD)
    for x in range(sx, sx + sw):  # hem trim round the sides and back
        if not (fx < x < fx + fw - 1):
            cv.put(x, sy + sh - 1, trim_d if lined else trim)
    bx, by, bw, bh = HEAD["back"]
    crescent(cv, bx + 3, by + 2, pal["n"])
    tx, ty, tw, th = HEAD["top"]
    crescent(cv, tx + 3, ty + 2, pal["n"])
    if lined:
        # dark lining inside the opening edges and a dark crown plate on top
        for y in range(fy + 2, fy + fh):
            cv.put(fx, y, LINE["l"])
            cv.put(fx + fw - 1, y, LINE["m"])
        for x in range(tx, tx + tw):
            cv.put(x, ty, LINE["l"])
            cv.put(x, ty + th - 1, LINE["m"])
        for y in range(ty, ty + th):
            cv.put(tx, y, LINE["l"])
            cv.put(tx + tw - 1, y, LINE["m"])
        hline(cv, sx, sx + sw, sy, LINE["l"])
        for x in range(sx, sx + sw):
            if not (fx < x < fx + fw - 1):
                cv.put(x, sy + sh - 2, LINE["m"])
    # ---- robe: body box
    cloth_rect(cv, BODY["top"], pal, 2, seam=seam, light_top=False)
    cloth_rect(cv, BODY["bottom"], {"l": pal["m"], "m": pal["d"], "h": pal["l"], "d": pal["d"]}, 3,
               seam=seam, light_top=False)
    for i, s in enumerate(SIDES):
        # lined: the narrow flanks under the arms show the netherite lining
        cloth_rect(cv, BODY[s], LINE if (lined and s in ("right", "left")) else pal, 20 + i, seam=seam)
    sx, sy, sw, sh = strip(BODY)
    fx, fy, fw, fh = BODY["front"]
    # front closure + V collar
    for y in range(fy + 2, fy + fh):
        cv.put(fx + 3, y, trim)
        cv.put(fx + 4, y, trim_d)
    for i in range(3):
        cv.put(fx + i, fy + i, trim)
        cv.put(fx + fw - 1 - i, fy + i, trim_d)
    # sash / belt and hem
    hline(cv, sx, sx + sw, sy + 7, trim_d)
    hline(cv, sx, sx + sw, sy + sh - 1, trim)
    if lined:
        hline(cv, sx, sx + sw, sy + sh - 2, LINE["m"])
        hline(cv, sx, sx + sw, sy + sh - 1, trim_d)
        hline(cv, sx, sx + sw, sy, LINE["l"])
        tx, ty, tw, th = BODY["top"]
        cv.rect(tx, ty, 2, th, LINE["l"])           # shoulder plates
        cv.rect(tx + tw - 2, ty, 2, th, LINE["m"])
        cv.rect(*BODY["bottom"], LINE["d"])
    bx, by, bw, bh = BODY["back"]
    crescent(cv, bx + 3, by + 2, pal["n"])
    crescent(cv, fx + 1, fy + 4, pal["n"])
    # ---- sleeves (arms): rows 0..9 of the side strip, cuff trim, hands left bare
    cloth_rect(cv, ARM["top"], pal, 4, seam=seam, light_top=False)
    for i, s in enumerate(SIDES):
        r = ARM[s]
        cloth_rect(cv, r, LINE if (lined and s == "left") else pal, 30 + i, rows=(r[1], r[1] + 10), seam=seam)
    sx, sy, sw, sh = strip(ARM)
    hline(cv, sx, sx + sw, sy + 9, trim)
    if lined:
        hline(cv, sx, sx + sw, sy + 8, LINE["m"])
        hline(cv, sx, sx + sw, sy, LINE["l"])
        cv.rect(*ARM["top"], LINE["l"])
    # ---- boots: lower 6 rows of the leg strip + sole
    for i, s in enumerate(SIDES):
        r = LEG[s]
        cloth_rect(cv, r, LINE if (lined and s == "left") else pal, 40 + i, rows=(r[1] + 6, r[1] + 12), seam=seam,
                   light_top=False)
    sx, sy, sw, sh = strip(LEG)
    hline(cv, sx, sx + sw, sy + 6, trim)
    hline(cv, sx, sx + sw, sy + sh - 1, LINE["l"] if lined else pal["d"])
    cv.rect(*LEG["bottom"], LINE["m"] if lined else pal["d"])
    if lined:
        hline(cv, sx, sx + sw, sy + sh - 2, LINE["m"])
        hline(cv, sx, sx + sw, sy + 7, LINE["l"])
    return cv


def moonsilk_leggings_layer(lined=False):
    cv = Canvas(64, 32)
    seam = LINE["m"] if lined else SILK["m"]
    pal = SILK
    trim, trim_d = AM_MAIN, AM_DARK
    # legs: top + upper 9 rows of the sides
    cloth_rect(cv, LEG["top"], pal, 5, seam=seam, light_top=False)
    for i, s in enumerate(SIDES):
        r = LEG[s]
        cloth_rect(cv, r, LINE if (lined and s == "left") else pal, 50 + i, rows=(r[1], r[1] + 9), seam=seam)
    sx, sy, sw, sh = strip(LEG)
    hline(cv, sx, sx + sw, sy + 8, trim_d if lined else trim)
    rx, ry, rw, rh = LEG["right"]
    crescent(cv, rx + 1, ry + 3, pal["n"])
    if lined:
        hline(cv, sx, sx + sw, sy + 7, LINE["m"])
        # knee plates on the front face
        fx, fy, fw, fh = LEG["front"]
        cv.rect(fx, fy + 4, fw, 2, LINE["l"])
        cv.put(fx + 1, fy + 4, LINE["h"])
        cv.rect(*LEG["top"], LINE["l"])
    # waist: lower 5 rows of the body sides, waistband trim
    for i, s in enumerate(SIDES):
        r = BODY[s]
        cloth_rect(cv, r, LINE if (lined and s in ("right", "left")) else pal, 60 + i, rows=(r[1] + 7, r[1] + 12),
                   seam=seam, light_top=False)
    sx, sy, sw, sh = strip(BODY)
    hline(cv, sx, sx + sw, sy + 7, trim)
    hline(cv, sx, sx + sw, sy + 8, trim_d)
    if lined:
        hline(cv, sx, sx + sw, sy + sh - 1, LINE["m"])
        fx, fy, fw, fh = BODY["front"]
        cv.rect(fx + 3, fy + 7, 2, 2, LINE["l"])  # dark buckle on the waistband
    return cv


def stormsage_layer():
    cv = Canvas(64, 32)
    pal = STORM

    def feathers(rect, seed, rows=None):
        x0, y0, w, h = rect
        ylo, yhi = (y0, y0 + h) if rows is None else rows
        for y in range(ylo, yhi):
            for x in range(x0, x0 + w):
                k = (x + (y - ylo) * 2) % 4
                c = FEATHER["F"] if k in (0, 1) else (FEATHER["f"] if k == 2 else FEATHER["q"])
                cv.put(x, y, c)

    # body: feather collar on top, storm cloth all round, lightning down front and back
    feathers(BODY["top"], 1)
    cv.rect(*BODY["bottom"], pal["d"])
    for i, s in enumerate(SIDES):
        cloth_rect(cv, BODY[s], pal, 70 + i)
    sx, sy, sw, sh = strip(BODY)
    feathers((sx, sy, sw, 2), 2)
    for x in range(sx, sx + sw):  # ragged feather tips
        if x % 2 == 0:
            cv.put(x, sy + 2, FEATHER["f"])
    hline(cv, sx, sx + sw, sy + sh - 1, pal["o"])
    hline(cv, sx, sx + sw, sy + sh - 2, pal["d"])
    bolt = [(4, 3), (3, 4), (3, 5), (2, 6), (3, 6), (4, 6), (3, 7), (2, 8), (2, 9), (1, 10)]
    for face in ("front", "back"):
        fx, fy, fw, fh = BODY[face]
        for (bx, by) in bolt:
            cv.put(fx + bx, fy + by, BOLT_W)
            cv.put(fx + bx + 1, fy + by, BOLT_Y)
        cv.put(fx + 5, fy + 3, BOLT_O)
    # arms: feather pauldron + storm sleeve with a yellow stripe down the outside
    feathers(ARM["top"], 3)
    for i, s in enumerate(SIDES):
        r = ARM[s]
        cloth_rect(cv, r, pal, 80 + i, rows=(r[1], r[1] + 10))
    sx, sy, sw, sh = strip(ARM)
    feathers((sx, sy, sw, 3), 4)
    for x in range(sx, sx + sw):
        if x % 2 == 1:
            cv.put(x, sy + 3, FEATHER["f"])
    hline(cv, sx, sx + sw, sy + 9, pal["o"])
    rx, ry, rw, rh = ARM["right"]
    for y in range(ry + 4, ry + 9):
        cv.put(rx + 1 + (y % 2), y, BOLT_Y)
    return cv


def cantors_layer():
    cv = Canvas(64, 32)
    sx, sy, sw, sh = strip(HEAD)
    hline(cv, sx, sx + sw, sy + 2, SILVER["l"])
    hline(cv, sx, sx + sw, sy + 3, SILVER["m"])
    for x in range(sx, sx + sw, 4):
        cv.put(x, sy + 3, SILVER["d"])
    fx, fy, fw, fh = HEAD["front"]
    # central choir-quartz gem rising above the band
    cv.put(fx + 3, fy + 1, GEM["l"])
    cv.put(fx + 4, fy + 1, GEM["m"])
    cv.put(fx + 3, fy + 2, GEM["h"])
    cv.put(fx + 4, fy + 2, GEM["m"])
    cv.put(fx + 3, fy + 3, GEM["m"])
    cv.put(fx + 4, fy + 3, GEM["d"])
    cv.put(fx + 3, fy + 0, SILVER["l"])
    cv.put(fx + 4, fy + 0, SILVER["m"])
    for x in (fx + 1, fx + 6):
        cv.put(x, fy + 2, GEM["m"])
    for s in ("right", "left"):
        x, y, w, h = HEAD[s]
        cv.put(x + 3, y + 2, GEM["l"])
        cv.put(x + 4, y + 2, GEM["m"])
    return cv


# ================================================================================================ CONTACT SHEET

def _font(size):
    try:
        return ImageFont.load_default(size=size)
    except TypeError:  # Pillow < 10.1: bitmap font only
        return ImageFont.load_default()


def _wrap(d, text, font, width):
    """Split a snake_case label onto several lines at underscores so it fits the cell."""
    lines, cur = [], ""
    for part in text.split("_"):
        cand = part if not cur else cur + "_" + part
        if cur and d.textlength(cand, font=font) > width:
            lines.append(cur + "_")
            cur = part
        else:
            cur = cand
    lines.append(cur)
    return lines


def contact_sheet(icons, layers, path, scale=8, layer_scale=6):
    """Icons (16x16 / 18x18) at x`scale` in a 6-column grid on inventory grey, then the 64x32 equipment
    layers at x`layer_scale` with an 8-px UV grid behind them."""
    bg, slot, ink = (139, 139, 139, 255), (118, 118, 118, 255), (20, 20, 20, 255)
    cols, pad = 6, 12
    cell_w, cell_h = 18 * scale + pad, 18 * scale + 44
    lw, lh = 64 * layer_scale, 32 * layer_scale
    icon_rows = (len(icons) + cols - 1) // cols
    layer_rows = (len(layers) + 1) // 2
    W = max(cols * cell_w + pad, 2 * (lw + pad) + pad)
    H = pad + icon_rows * cell_h + layer_rows * (lh + 34) + pad
    sheet = Image.new("RGBA", (W, H), bg)
    d = ImageDraw.Draw(sheet)
    font = _font(12)
    for i, (name, im) in enumerate(icons):
        cx, cy = pad + (i % cols) * cell_w, pad + (i // cols) * cell_h
        img = im.resize((im.size[0] * scale, im.size[1] * scale), Image.NEAREST)
        box = 18 * scale
        d.rectangle([cx, cy, cx + box - 1, cy + box - 1], fill=slot)
        sheet.alpha_composite(img, (cx + (box - img.size[0]) // 2, cy + (box - img.size[1]) // 2))
        for j, line in enumerate(_wrap(d, name, font, cell_w - 6)):
            d.text((cx, cy + box + 4 + j * 14), line, fill=ink, font=font)
    y0 = pad + icon_rows * cell_h
    for i, (name, im) in enumerate(layers):
        x, y = pad + (i % 2) * (lw + pad), y0 + (i // 2) * (lh + 34)
        d.rectangle([x, y, x + lw - 1, y + lh - 1], fill=slot)
        for gx in range(0, 65, 8):
            d.line([x + gx * layer_scale, y, x + gx * layer_scale, y + lh - 1], fill=(104, 104, 104, 255))
        for gy in range(0, 33, 8):
            d.line([x, y + gy * layer_scale, x + lw - 1, y + gy * layer_scale], fill=(104, 104, 104, 255))
        sheet.alpha_composite(im.resize((lw, lh), Image.NEAREST), (x, y))
        d.text((x, y + lh + 4), name, fill=ink, font=font)
    sheet.save(path)


# ================================================================================================ MAIN

def main():
    WRITTEN.clear()
    items = {
        "amethyst_wand": amethyst_wand(),
        "sunlance_staff": sunlance_staff(),
        "sculk_resonator": sculk_resonator(),
        "moonsilk_hood": armour_icon(HOOD),
        "moonsilk_robe": armour_icon(ROBE),
        "moonsilk_leggings": armour_icon(LEGS),
        "moonsilk_boots": armour_icon(BOOTS),
        "lined_moonsilk_hood": armour_icon(HOOD, lined=True),
        "lined_moonsilk_robe": armour_icon(ROBE, lined=True),
        "lined_moonsilk_leggings": armour_icon(LEGS, lined=True),
        "lined_moonsilk_boots": armour_icon(BOOTS, lined=True),
        "stormsage_mantle": stormsage_mantle(),
        "cantors_diadem": cantors_diadem(),
        "glimmerstone_focus": glimmerstone_focus(),
        "glimmer_tonic": tonic(False),
        "strong_glimmer_tonic": tonic(True),
        "moonsilk_thread": moonsilk_thread(),
        "roc_feather_longbow": roc_bow(None),
        "roc_feather_longbow_pulling_0": roc_bow(0),
        "roc_feather_longbow_pulling_1": roc_bow(1),
        "roc_feather_longbow_pulling_2": roc_bow(2),
    }
    icons, layers = [], []
    for name, cv in items.items():
        save(cv, f"item/{name}.png")
        icons.append((name, cv.image()))
    for rel, cv in (("entity/spell/amethyst_bolt.png", amethyst_bolt()),
                    ("mob_effect/clarity.png", clarity())):
        save(cv, rel)
        icons.append((rel[:-4].replace("/", ": "), cv.image()))
    for rel, cv in (("entity/equipment/humanoid/moonsilk.png", moonsilk_layer(False)),
                    ("entity/equipment/humanoid/lined_moonsilk.png", moonsilk_layer(True)),
                    ("entity/equipment/humanoid/stormsage_mantle.png", stormsage_layer()),
                    ("entity/equipment/humanoid/cantors_diadem.png", cantors_layer()),
                    ("entity/equipment/humanoid_leggings/moonsilk.png", moonsilk_leggings_layer(False)),
                    ("entity/equipment/humanoid_leggings/lined_moonsilk.png", moonsilk_leggings_layer(True))):
        save(cv, rel)
        layers.append((rel.replace("entity/equipment/", ""), cv.image()))
    contact_sheet(icons, layers, SHEET)
    print(f"wrote {len(WRITTEN)} textures under {TEX}")
    for rel in WRITTEN:
        print("  " + rel)
    print(f"contact sheet: {SHEET}")


if __name__ == "__main__":
    main()
