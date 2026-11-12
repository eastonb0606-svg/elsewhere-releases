"""Azmar item icons (16x16) + headpiece equipment textures (64x32 humanoid layer). Pure python."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngio import Image
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "elsewhere", "textures")
ITEM = os.path.join(ROOT, "item"); EQ = os.path.join(ROOT, "entity", "equipment", "humanoid")
os.makedirs(ITEM, exist_ok=True); os.makedirs(EQ, exist_ok=True)

GOLD = (212, 165, 55); GOLD_L = (245, 215, 110); GOLD_D = (150, 110, 30)
LAPIS = (31, 63, 143); LAPIS_L = (70, 110, 200); TURQ = (43, 179, 163); TURQ_D = (28, 120, 112); OBS = (22, 19, 26)
LINEN = (237, 227, 204); LINEN_D = (200, 188, 160); CARN = (176, 54, 43); BROWN = (110, 74, 40); BROWN_D = (70, 46, 26)
COP = (196, 118, 66); COP_L = (232, 160, 100); COP_D = (130, 72, 40); STEEL_L = (235, 235, 240)

def draw(rows, pal):
    im = Image(16, 16)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row[:16]):
            if ch != "." and ch in pal: im.put(x, y, pal[ch])
    return im

def blade_pal(m, l, d):
    return {"o": OBS, "b": m, "l": l, "d": d, "g": GOLD, "G": GOLD_L, "p": LAPIS, "q": LAPIS_L, "t": TURQ, "h": BROWN, "H": BROWN_D, "r": CARN}

# Sickle-curved khopesh: handle bottom-left, scarab-shell guard (lapis inlay), blade rises then hooks forward/down at the tip.
KHOP = [
    "...........oooo.",
    "..........obllbo",
    ".........oblbbdo",
    ".........obb.obo",
    "........obbo.obo",
    "........obbo..oo",
    "........obbo....",
    ".......obbdo....",
    ".......obbdo....",
    "....oogGpGgoo...",
    "....ogqpptqgo...",
    "...ohoogggoo....",
    "..ohHo..........",
    ".ohHo...........",
    ".orHo...........",
    "..oo............",
]
blade_pal_sets = {"copper": (COP, COP_L, COP_D), "gilded": (GOLD, GOLD_L, GOLD_D)}
for nm, (m, l, d) in blade_pal_sets.items():
    draw(KHOP, blade_pal(m, l, d)).save(os.path.join(ITEM, f"azmar_{nm}_khopesh.png"))

# twin khopeshes: two blades crossed (one bright, one dark), shared turquoise scarab at the centre
tw = Image(16, 16)
a = draw(KHOP, blade_pal(GOLD, GOLD_L, GOLD_D))
b = draw(KHOP, blade_pal(COP, COP_L, COP_D))
for y in range(16):                            # mirror b horizontally so the pair crosses
    for x in range(16):
        p = b.get(15 - x, y)
        if p[3]: tw.put(x, y, p)
for y in range(16):
    for x in range(16):
        p = a.get(x, y)
        if p[3]: tw.put(x, y, p)
tw.put(7, 9, TURQ); tw.put(8, 9, TURQ); tw.put(7, 10, TURQ_D); tw.put(8, 10, TURQ_D)
tw.save(os.path.join(ITEM, "azmar_twin_khopesh.png"))

# scarab-head spear
SPEAR = [
    "..........oo....",
    ".........obbo...",
    "........obllbo..",
    "........oblbbo..",
    ".......oGobbo...",
    "......oGgGo.....",
    ".....otgtgo.....",
    ".....ogGo.......",
    "....oho.........",
    "...ohHo.........",
    "..ohHo..........",
    ".ohHo...........",
    "ohHo............",
    "orHo............",
    ".oo.............",
    "................",
]
draw(SPEAR, {"o": OBS, "b": GOLD, "l": GOLD_L, "g": GOLD, "G": GOLD_L, "t": TURQ, "h": BROWN, "H": BROWN_D, "r": CARN}).save(os.path.join(ITEM, "azmar_scarab_spear.png"))

# composite bow (recurved) + 3 draw states
def bow(draw_level):
    im = Image(16, 16)
    # limb arc: gold-wrapped wood, recurved tips
    pts = [(12, 0), (13, 1), (13, 2), (12, 3), (11, 4), (10, 5), (9, 6), (9, 7), (9, 8), (9, 9), (10, 10), (11, 11), (12, 12), (13, 13), (13, 14), (12, 15)]
    for i, (x, y) in enumerate(pts):
        im.put(x, y, BROWN if i % 4 else GOLD); im.put(x - 1, y, BROWN_D)
    for k in (3, 7, 12): im.put(pts[k][0], pts[k][1], TURQ)
    sx = 9 - draw_level * 2
    for y in range(1, 15):
        t = abs(y - 7.5) / 7.5
        im.put(int(round(sx + (9 - sx) * 0 + 3 * t * (1 if draw_level == 0 else 0))), y, LINEN_D) if False else None
    for y in range(1, 15):
        x = 12 - draw_level * 2 if False else 12
    # string: straight at rest, pulled back in the middle when drawn
    top, bot = (12, 0), (12, 15)
    mid = (12 - 2 - draw_level * 2, 7)
    im.line(top[0], top[1], mid[0], mid[1], LINEN); im.line(mid[0], mid[1], bot[0], bot[1], LINEN)
    if draw_level:                              # nocked arrow
        im.line(mid[0], mid[1], 2, 7, BROWN); im.put(1, 6, LINEN); im.put(1, 8, LINEN)
        im.put(3, 7, LINEN_D)
    return im
for lv, nm in enumerate(("", "_pulling_0", "_pulling_1", "_pulling_2")):
    bow(lv if lv == 0 else lv - 1 if False else lv).save(os.path.join(ITEM, f"azmar_composite_bow{nm}.png"))

# faience fire pot: turquoise jar, gold bands, carnelian-glaze flame fuse
POT = [
    "......oo........",
    ".....oRRo.......",
    ".....oyRo.......",
    "......oo........",
    "....oogggoo.....",
    "...otTtTttto....",
    "..otTTtTTTTto...",
    "..otTpTTTpTto...",
    "..ogGgGgGgGgo...",
    "..otTTTtTTTto...",
    "..otTqTTqTTto...",
    "...otTTTTTto....",
    "....ootttoo.....",
    ".....oooooo.....",
    "................",
    "................",
]
draw(POT, {"o": OBS, "g": GOLD, "G": GOLD_L, "t": TURQ, "T": (80, 205, 190), "p": LAPIS, "q": LAPIS_L, "R": CARN, "y": GOLD_L}).save(os.path.join(ITEM, "azmar_fire_pot.png"))

# dinar: gold coin stamped with a small scarab and wings
DIN = [
    "................",
    ".....oooooo.....",
    "....oGGGGGGo....",
    "...oGgGGGGgGo...",
    "..oGgGggGgGgGo..",
    "..oGGggGGggGGo..",
    "..oGgggggggGgo..",
    "..oGgGgggGggGo..",
    "..oGGggGGggGGo..",
    "..oGgGggGggGGo..",
    "..oGGGGGGGGGgo..",
    "...oGgGGGGgGo...",
    "....oggggggo....",
    ".....oooooo.....",
    "................",
    "................",
]
draw(DIN, {"o": GOLD_D, "G": GOLD_L, "g": GOLD}).save(os.path.join(ITEM, "azmar_dinar.png"))

# Lord seals: sandstone disc with a lapis rim and the boss's animal glyph in a distinct colour
def seal(glyph_col, shape):
    im = Image(16, 16)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 6.6: im.put(x, y, (214, 190, 140) if d < 5.2 else LAPIS)
            if 6.0 < d < 6.9: im.put(x, y, OBS if d > 6.5 else LAPIS)
    for (x, y) in shape: im.put(x, y, glyph_col)
    return im
SH = {
    "sultana": [(7, 4), (8, 4), (6, 5), (7, 5), (8, 5), (9, 5), (5, 6), (10, 6), (7, 7), (8, 7), (7, 8), (8, 8), (6, 9), (9, 9), (7, 10), (8, 10)],   # scarab + wings
    "wazir": [(7, 4), (8, 4), (9, 4), (6, 5), (9, 5), (9, 6), (8, 7), (7, 8), (7, 9), (6, 10), (7, 10), (8, 10)],                                     # ibis head and neck
    "falconer": [(5, 5), (6, 5), (7, 6), (8, 6), (9, 7), (10, 8), (6, 7), (7, 8), (8, 9), (7, 9), (6, 8)],                                           # falcon in dive
    "amir": [(7, 4), (8, 4), (6, 5), (9, 5), (7, 6), (8, 6), (7, 7), (8, 7), (6, 8), (9, 8), (7, 9), (8, 9), (7, 10), (8, 10)],                      # horned scarab
}
for k, col in (("sultana", GOLD_L), ("wazir", TURQ), ("falconer", CARN), ("amir", (60, 40, 20))):
    seal(col, SH[k]).save(os.path.join(ITEM, f"azmar_seal_{k}.png"))

# trim template + banner pattern icons
tt = Image(16, 16)
for y in range(1, 15):
    for x in range(2, 14): tt.put(x, y, (46, 50, 86) if (x + y) % 5 else (60, 66, 108))
for x in range(2, 14): tt.put(x, 1, GOLD); tt.put(x, 14, GOLD)
for y in range(1, 15): tt.put(2, y, GOLD); tt.put(13, y, GOLD)
for (x, y) in [(7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (4, 7), (5, 7), (10, 7), (11, 7), (7, 8), (8, 8), (7, 9), (8, 9), (5, 10), (6, 10), (9, 10), (10, 10), (7, 11), (8, 11)]: tt.put(x, y, GOLD_L)
tt.save(os.path.join(ITEM, "azmar_armor_trim_smithing_template.png"))
bp = Image(16, 16)
for y in range(1, 15):
    for x in range(3, 13): bp.put(x, y, LINEN if (x + y) % 7 else LINEN_D)
for x in range(3, 13): bp.put(x, 1, LAPIS); bp.put(x, 14, LAPIS)
for y in range(1, 15): bp.put(3, y, TURQ); bp.put(12, y, TURQ)
for (x, y) in [(7, 4), (8, 4), (5, 6), (6, 6), (9, 6), (10, 6), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9), (6, 11), (9, 11), (7, 11), (8, 11)]: bp.put(x, y, GOLD)
bp.save(os.path.join(ITEM, "azmar_banner_pattern.png"))

# headpiece icons: 16x16 front views
def icon(rows, pal, name): draw(rows, pal).save(os.path.join(ITEM, name + ".png"))
NEMES = [
    "......oooo......",
    ".....oGggGo.....",
    "....oGyGGyGo....",
    "...oGgGggGgGo...",
    "..olGlGGGGlGlo..",
    "..oplGlGGlGlpo..",
    "..olplGGGGlplo..",
    "..oplpGGGGplpo..",
    "..olplo..olplo..",
    "..oplpo..opplo..",
    "..olplo..olplo..",
    "..oplo....oplo..",
    "..olo......olo..",
    "...oo......oo...",
    "................",
    "................",
]
icon(NEMES, {"o": OBS, "G": GOLD_L, "g": GOLD, "y": CARN, "l": LINEN, "p": LAPIS}, "azmar_nemes_of_the_uraeus")
IBIS = [
    "........ooo.....",
    ".......oLLLo....",
    "......oLLooLo...",
    ".....oLLo.oRo...",
    "....oLLo..oRRRo.",
    "...oLLLo...oRo..",
    "..oLLLLo....o...",
    "..oLpLLo........",
    "..oLLLLLo.......",
    "..oLpLpLLo......",
    "...oLLLLLo......",
    "....ooooo.......",
    "................",
    "................",
    "................",
    "................",
]
icon(IBIS, {"o": OBS, "L": LINEN, "p": LAPIS, "R": OBS}, "azmar_ibis_crest")
FALCON = [
    "......oooo......",
    ".....oTTTTo.....",
    "....oTTttTTo....",
    "...oTtGTTGtTo...",
    "...oTTtooTtTo...",
    "...oTtoYYotTo...",
    "...ooTtYYtToo...",
    "..oGoTTooTToGo..",
    "..oGoTt..tToGo..",
    "..ooo.o..o.ooo..",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]
icon(FALCON, {"o": OBS, "T": TURQ, "t": TURQ_D, "G": GOLD, "Y": GOLD_L}, "azmar_falcon_helm")
JACKAL = [
    "..oo........oo..",
    ".oOOo......oOOo.",
    ".oOOOo....oOOOo.",
    ".oOOOooooooOOOo.",
    "..oOOOOOOOOOOo..",
    "..oOgOOOOOOgOo..",
    "..oOOOOOOOOOOo..",
    "...oOGOOOOGOo...",
    "...oOOOooOOOo...",
    "....oOOOOOOo....",
    ".....ooGGoo.....",
    ".......oo.......",
    "................",
    "................",
    "................",
    "................",
]
icon(JACKAL, {"o": (10, 8, 12), "O": (44, 40, 52), "g": GOLD, "G": GOLD_L}, "azmar_jackal_helm")
SCARAB = [
    "..o..........o..",
    ".oRo........oRo.",
    ".oRRo......oRRo.",
    "..oRRo.oo.oRRo..",
    "...oRoGggGoRo...",
    "....ooGGGGoo....",
    "...oGgGTTGgGo...",
    "..oGgGGTTGGgGo..",
    "..oGgGGGGGGgGo..",
    "..oGGgggggGGGo..",
    "...oGGGGGGGGo...",
    "....ooooooooo...",
    "................",
    "................",
    "................",
    "................",
]
icon(SCARAB, {"o": OBS, "R": CARN, "G": GOLD_L, "g": GOLD, "T": TURQ}, "azmar_horned_scarab_crown")
CUIRASS = [
    "................",
    "..ooo......ooo..",
    ".oGGGo....oGGGo.",
    ".oGgGoooooooGgGo",
    ".oGGgGGGGGGgGGo.",
    "..oGGlGggGlGGo..",
    "..oooLlGGlLooo..",
    "...oLlpGGplLo...",
    "...oLllGGllLo...",
    "...oLlllllLLo...",
    "...oLLLLLLLLo...",
    "...oGGGGGGGGo...",
    "....oooooooo....",
    "................",
    "................",
    "................",
]
icon(CUIRASS, {"o": OBS, "G": GOLD_L, "g": GOLD, "L": LINEN, "l": LINEN_D, "p": LAPIS}, "azmar_sunplate_cuirass")
SANDAL = [
    "................",
    "................",
    "................",
    "...oo...........",
    "...oBo..........",
    "...oBBo.........",
    "...oBGo.........",
    "..ooBBoooo......",
    "..oBBBBBBBo.....",
    ".oBGGBBGGBBo....",
    ".oBBBBBBBBBBo...",
    ".oHHHHHHHHHHo...",
    "..oooooooooo....",
    "................",
    "................",
    "................",
]
icon(SANDAL, {"o": OBS, "B": (170, 120, 70), "G": GOLD, "H": BROWN_D}, "azmar_sandrunner_sandals")
GAUNT = [
    "................",
    "....oo..........",
    "...oLLo....oo...",
    "..oLLLo...oTTo..",
    "..oLLLLo.oTTTo..",
    "..oGGGGooTTTo...",
    "..oLLLLLLTTo....",
    "...oLLLLLTo.....",
    "...oLLLLLo......",
    "....oLLLLo......",
    "....oGGGGo......",
    ".....oooo.......",
    "................",
    "................",
    "................",
    "................",
]
icon(GAUNT, {"o": OBS, "L": (150, 105, 60), "G": GOLD, "T": TURQ}, "azmar_falconers_gauntlet")
STANDARD = [
    ".....o..........",
    "....oGo.........",
    "...oGgGoooooo...",
    "...oooooPPPPPo..",
    "....oHoPGGGGPo..",
    "....oHoPGggGPo..",
    "....oHoPPGGPPo..",
    "....oHoPGggGPo..",
    "....oHoPPGGPPo..",
    "....oHooPPPPoo..",
    "....oHo.oooo....",
    "....oHo.........",
    "....oHo.........",
    "....oHo.........",
    "....ooo.........",
    "................",
]
icon(STANDARD, {"o": OBS, "G": GOLD_L, "g": GOLD, "P": LAPIS, "H": BROWN}, "azmar_winged_scarab_standard")

# ---- headpiece equipment textures: humanoid layer 64x32 (helmet uses the head box; the model is slightly inflated, so
# this is real equipment rendered ON the head, never painted on the skin). Ears, beak, horns and the uraeus are added by
# the client head-ornament layer (AzmarHeadLayer). ------------------------------------------------------------------
def helm_tex(name, base, accent, trim, stripes=None, front_extra=None):
    im = Image(64, 32)
    for x0, x1 in ((0, 32),):
        for y in range(0, 16):
            for x in range(x0, x1): im.put(x, y, base if (x + y) % 11 else tuple(max(0, c - 14) for c in base))
    # brow band (gold) at row 11 across all four sides, and a lower trim at the temple
    for x in range(0, 32):
        if 8 <= x < 32 or x < 8:
            im.put(x, 10, accent); im.put(x, 11, trim)
    if stripes:
        for x in range(0, 32, 2):
            for y in range(12, 16): im.put(x, y, stripes)
        for x in range(8, 16): im.put(x, 12, stripes)
    for x in range(8, 16): im.put(x, 8, accent)             # front lower edge
    for x in (11, 12): im.put(x, 9, trim)                    # brow scarab / jewel
    if front_extra: front_extra(im)
    im.save(os.path.join(EQ, name + ".png"))
# helm_tex("azmar_nemes", (232, 200, 80), GOLD_L, LAPIS, stripes=LAPIS, front_extra=lambda im: [im.put(11, 8, CARN), im.put(12, 8, CARN)])   # replaced by gen_helms.py (richer face masks)
# helm_tex("azmar_ibis", LINEN, GOLD, LAPIS, front_extra=lambda im: [im.put(x, 12, LAPIS) for x in (10, 13)])   # replaced by gen_helms.py (richer face masks)
# helm_tex("azmar_falcon", TURQ, GOLD, LINEN, front_extra=lambda im: [im.put(x, 12, OBS) for x in (9, 14)])   # replaced by gen_helms.py (richer face masks)
# helm_tex("azmar_jackal", (44, 40, 52), GOLD, TURQ, front_extra=lambda im: [im.put(x, 12, GOLD_L) for x in (10, 13)])   # replaced by gen_helms.py (richer face masks)
# helm_tex("azmar_scarab", GOLD, GOLD_L, CARN, front_extra=lambda im: [im.put(11, 8, TURQ), im.put(12, 8, TURQ)])   # replaced by gen_helms.py (richer face masks)
# cuirass (humanoid layer: body 16..40 x 16..32) and sandals (humanoid layer boots on legs rows 24..32)
cu = Image(64, 32)
for y in range(16, 32):
    for x in range(16, 56): cu.put(x, y, LINEN if (x + y) % 9 else LINEN_D)
for fx in (20, 32):
    for dy, (a, b) in enumerate(((0, 8), (0, 8), (1, 7), (2, 6), (3, 5))):
        for x in range(fx + a, fx + b): cu.put(x, 20 + dy, GOLD if (x + dy) % 3 else LAPIS)
    for x in range(fx, fx + 8): cu.put(x, 30, GOLD); cu.put(x, 31, GOLD)
    for y in range(25, 30): cu.put(fx + 3, y, GOLD_L); cu.put(fx + 4, y, GOLD_L)
cu.save(os.path.join(EQ, "azmar_sunplate.png"))
cl = Image(64, 32)  # leggings-layer placeholder required by the asset (unused by a chest piece)
cl.save(os.path.join(os.path.dirname(EQ), "humanoid_leggings", "azmar_sunplate.png")) if os.path.isdir(os.path.join(os.path.dirname(EQ), "humanoid_leggings")) else None
sa = Image(64, 32)
for (lx) in (0, 16):
    for y in range(20, 32):
        for x in range(lx, lx + 16): sa.put(x, y, (170, 120, 70) if (x + y) % 5 else (140, 96, 56))
    for x in range(lx, lx + 16): sa.put(x, 26, GOLD); sa.put(x, 29, GOLD)
sa.save(os.path.join(EQ, "azmar_sandrunner.png"))
print("items done")

# swatch sheet for the 3D khopesh models (2x2 px cells, uv in model units)
sw = Image(16, 16)
for (x, y, c) in ((0, 0, BROWN), (2, 0, GOLD), (4, 0, LAPIS), (6, 0, TURQ), (8, 0, CARN), (10, 0, GOLD_L), (0, 2, COP), (2, 2, GOLD_L), (4, 2, (200, 200, 215)), (6, 2, COP_L)):
    sw.rect(x, y, x + 2, y + 2, c)
sw.save(os.path.join(ITEM, "azmar_khopesh_parts.png"))
