"""Hrafnholt art: unit and boss skins (64x64 player layout, plain clothing, no painted armor), Thrall, item icons,
trim pattern layers, banner and shield pattern. Run: python3 gen_art.py   (needs Pillow). Output: src/main/resources/assets/elsewhere/textures/."""
import os, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures")
ENT = os.path.join(TEX, "entity", "hrafnholt")
BOSS = os.path.join(ENT, "boss")
ITEM = os.path.join(TEX, "item")
for d in (ENT, BOSS, ITEM, os.path.join(TEX, "entity", "banner"), os.path.join(TEX, "entity", "shield"),
          os.path.join(TEX, "trims", "entity", "humanoid"), os.path.join(TEX, "trims", "entity", "humanoid_leggings")):
    os.makedirs(d, exist_ok=True)

IRON = (0x4A, 0x55, 0x62); FROST = (0xE2, 0xDA, 0xC2); RAVEN = (0x2E, 0x5A, 0x2C); EMBER = (0x6B, 0x4A, 0x2B)  # FROST is bone, RAVEN forest green, EMBER bark


def rect(img, box, col, noise=8, seed=0):
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        for y in range(y0, y1):
            n = r.randint(-noise, noise)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in col[:3]) + (255,))


def cube(img, u, v, w, h, d, col, seed):
    rect(img, (u + d, v, u + d + w, v + d), col, seed=seed)
    rect(img, (u + d + w, v, u + d + 2 * w, v + d), col, seed=seed + 1)
    rect(img, (u, v + d, u + 2 * d + 2 * w, v + d + h), col, seed=seed + 2)


def dark(c, n):
    return tuple(max(0, min(255, x - n)) for x in c[:3])


def skin(path, hair, beard, tunic, hose, seed, tone=(196, 150, 116), collar=None, hood=None, paint=None, bare_chest=False,
         braid=False, long_hair=False, runes=None, belt=(70, 50, 35), scars=False):
    """A 64x64 player-layout skin. collar = fur collar colour, hood = hood colour, paint = (colour, kind) face paint,
    bare_chest = torso is skin (Berserker), braid = braided beard strands, runes = colour of rune marks on tunic/arms."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    cube(img, 0, 0, 8, 8, 8, tone, seed)  # head
    rect(img, (8, 0, 16, 8), hair, seed=seed + 3)
    rect(img, (0, 8, 8, 11), hair, seed=seed + 4); rect(img, (16, 8, 32, 11), hair, seed=seed + 5)
    rect(img, (24, 8, 32, 16), hair, seed=seed + 6)
    rect(img, (8, 8, 16, 10), hair, seed=seed + 7)
    if long_hair:
        rect(img, (0, 11, 8, 16), hair, seed=seed + 20); rect(img, (16, 11, 24, 16), hair, seed=seed + 21)
    if hood:
        rect(img, (8, 0, 16, 8), hood, seed=seed + 30); rect(img, (0, 8, 8, 12), hood, seed=seed + 31)
        rect(img, (16, 8, 32, 14), hood, seed=seed + 32); rect(img, (8, 8, 16, 11), hood, seed=seed + 33)
        rect(img, (24, 8, 32, 16), hood, seed=seed + 34)
    eye = [(9, 12, (240, 240, 240)), (10, 12, (50, 60, 80)), (13, 12, (50, 60, 80)), (14, 12, (240, 240, 240)),
           (9, 11, dark(hair, 30)), (10, 11, dark(hair, 30)), (13, 11, dark(hair, 30)), (14, 11, dark(hair, 30)),
           (11, 13, (170, 120, 90)), (12, 13, (170, 120, 90))]
    for x, y, c in eye:
        img.putpixel((x, y), c + (255,))
    if beard:
        rect(img, (8, 14, 16, 16), beard, seed=seed + 8)
        rect(img, (9, 13, 11, 14), beard, 4, seed + 9); rect(img, (13, 13, 15, 14), beard, 4, seed + 10)
        if braid:
            for y in range(15, 16):
                for x in (9, 11, 13, 14):
                    img.putpixel((x, y), dark(beard, 40) + (255,))
    img.putpixel((11, 14), (120, 70, 60, 255)); img.putpixel((12, 14), (120, 70, 60, 255))
    if paint:
        col, kind = paint
        if kind == "stripe":  # red stripe across the eyes
            for x in range(8, 16): img.putpixel((x, 11), col + (255,))
        elif kind == "woad":  # woad hand-prints: two bars under each eye
            for x in (9, 10, 13, 14): img.putpixel((x, 13), col + (255,))
            for x in range(8, 16): img.putpixel((x, 10), col + (255,))
        elif kind == "half":
            for y in range(8, 16):
                for x in range(8, 12): img.putpixel((x, y), col + (255,))
    # body
    if bare_chest:
        cube(img, 16, 16, 8, 12, 4, tone, seed + 11)
        rect(img, (20, 20, 28, 22), dark(tone, 25), 3, seed + 40)  # chest
        for yy in (23, 25): [img.putpixel((xx, yy), dark(tone, 45) + (255,)) for xx in range(21, 27)]
        if scars:
            for k in range(5): img.putpixel((21 + k, 21 + k // 2), (140, 70, 70, 255))
        if paint: [img.putpixel((xx, 20), paint[0] + (255,)) for xx in range(20, 28)]
    else:
        cube(img, 16, 16, 8, 12, 4, tunic, seed + 11)
        for yy in range(21, 32, 3):
            for xx in range(20, 28): img.putpixel((xx, yy), dark(tunic, 22) + (255,))
            for xx in range(32, 40): img.putpixel((xx, yy), dark(tunic, 22) + (255,))
        if runes:
            for (xx, yy) in ((23, 24), (24, 24), (23, 25), (24, 26), (22, 26), (25, 26)): img.putpixel((xx, yy), runes + (255,))
    rect(img, (20, 30, 28, 32), belt, 4, seed + 12)
    img.putpixel((23, 30), (190, 190, 200, 255)); img.putpixel((24, 30), (190, 190, 200, 255))  # iron buckle
    if collar:  # fur collar: shaggy rim around the neck on torso top and shoulders
        for xx in range(20, 28):
            for yy in (20, 21):
                c = tuple(max(0, min(255, v + random.Random(xx * 7 + yy + seed).randint(-14, 14))) for v in collar)
                img.putpixel((xx, yy), c + (255,))
        for xx in range(32, 40):
            for yy in (20, 21): img.putpixel((xx, yy), collar + (255,))
    for (u, v) in ((40, 16), (32, 48)):  # arms
        cube(img, u, v, 4, 12, 4, tone if bare_chest else tunic, seed + 13)
        rect(img, (u, v + 4 + 9, u + 16, v + 16), tone, seed=seed + 14)
        if not bare_chest:
            rect(img, (u + 4, v, u + 8, v + 4), tunic, seed=seed + 15)
            for xx in range(u, u + 16): img.putpixel((xx, v + 4 + 8), (60, 45, 35, 255))  # leather wrist wrap
        elif runes or paint:
            col = (paint[0] if paint else runes)
            for k in range(0, 8, 2): img.putpixel((u + 5, v + 6 + k), col + (255,))
    for (u, v) in ((0, 16), (16, 48)):  # legs
        cube(img, u, v, 4, 12, 4, hose, seed + 16)
        rect(img, (u, v + 4 + 8, u + 16, v + 16), (55, 40, 30), seed=seed + 17)
        for xx in range(u, u + 16): img.putpixel((xx, v + 4 + 7), (95, 85, 70, 255))  # wool leg wraps
    img.save(path)


def hexc(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


# ---------------------------------------------------------------- unit skins (plain clothing; real armor goes on top)
U = lambda n: os.path.join(ENT, n + ".png")
GREY, GREY2, WOOL = (110, 112, 116), (92, 96, 102), (128, 124, 116)
# Hrafnholt palette (RIVALS-DECISIONS 16): forest green, bark brown, iron-grey and bone accents
FOREST, FOREST_D, MOSS, PINE = (46, 90, 44), (34, 66, 34), (82, 112, 60), (28, 52, 36)
BARK, BARK_D, BARK_L, LEATHER = (107, 74, 43), (76, 52, 32), (140, 104, 66), (92, 66, 40)
BONE, BONE_D, IRONG = (222, 214, 190), (186, 176, 150), (104, 110, 114)
skin(U("hird_a"), (96, 66, 40), (110, 74, 44), FOREST, BARK_D, 1, collar=(88, 78, 66), braid=True)
skin(U("hird_b"), (150, 90, 50), (160, 96, 52), MOSS, (70, 56, 40), 2, collar=BARK_L, braid=True)
skin(U("hunter"), (80, 62, 44), (70, 56, 40), (84, 96, 60), (80, 62, 44), 3, hood=FOREST_D, collar=BARK_L)
skin(U("bolt_thrall"), (74, 56, 40), None, (96, 90, 60), (82, 66, 48), 4, belt=(90, 70, 46))
skin(U("wolf_rider"), (120, 80, 48), (120, 80, 48), BARK, (56, 62, 44), 5, collar=(150, 148, 144), braid=True)
skin(U("sergeant"), (140, 140, 140), (150, 150, 150), FOREST_D, BARK_D, 6, collar=BARK, braid=True)
skin(U("captain"), (60, 40, 30), (70, 44, 32), PINE, (52, 40, 30), 7, collar=BARK_D, braid=True, paint=(BONE, "stripe"))
skin(U("raven_bearer"), (36, 32, 30), (40, 34, 30), (30, 60, 34), BARK_D, 8, collar=PINE)
skin(U("skald"), (176, 150, 100), (176, 150, 100), (60, 98, 58), BARK_D, 9, long_hair=True, braid=True, runes=BONE)
skin(U("leech"), (150, 130, 100), None, (176, 172, 140), (96, 84, 62), 10, tone=(205, 165, 135))
skin(U("shield_lad"), (170, 120, 70), None, MOSS, BARK_D, 11, tone=(210, 165, 130))
skin(U("spear_thane"), (84, 70, 56), (84, 70, 56), BARK, (54, 60, 44), 12, collar=MOSS, braid=True)
skin(U("berserker"), (70, 40, 28), (80, 44, 28), (0, 0, 0), BARK_D, 13, bare_chest=True, paint=(MOSS, "woad"), scars=True,
     collar=None)
skin(U("wolf_handler"), (100, 90, 76), (100, 90, 76), LEATHER, (66, 58, 44), 14, hood=FOREST, collar=(138, 136, 134))
skin(U("wolf_rider_axe"), (120, 80, 48), None, FOREST_D, BARK_D, 15, collar=(140, 138, 134))
# Thrall: ragged drab tunic, bare head, 64x64 like the Serf
skin(U("thrall"), (88, 68, 48), None, (104, 92, 62), (84, 70, 52), 16, tone=(190, 150, 120), belt=(120, 100, 70))

# Risen champions: the Hird-Captain skin plus the seat's signature piece (coloured collar/paint per seat)
RISEN = {"hersir": (110, 70, 40), "hallwarden": (90, 62, 38), "skald": (70, 120, 70), "chief": BONE, "huntmaster": (60, 104, 58)}
for k, c in RISEN.items():
    skin(os.path.join(BOSS, "risen_" + k + ".png"), (60, 40, 30), (70, 44, 32), PINE, (52, 40, 30), 30, collar=c, braid=True, paint=(BONE, "stripe"))

# ---------------------------------------------------------------- boss skins (plain clothing)
B = lambda n: os.path.join(BOSS, n + ".png")
skin(B("halvard"), (190, 190, 192), (200, 200, 204), FOREST_D, BARK_D, 21, collar=BARK, braid=True,
     paint=(BONE, "stripe"), tone=(190, 144, 112))
skin(B("sigrun"), (150, 56, 34), None, FOREST, BARK_D, 22, long_hair=True, tone=(214, 170, 140), collar=(120, 118, 114),
     paint=(MOSS, "half"))
skin(B("torvald"), (60, 44, 34), (74, 52, 36), BARK, BARK_D, 23, braid=True, collar=MOSS, tone=(180, 136, 104))
skin(B("ulfar"), (30, 28, 32), (34, 30, 30), PINE, BARK_D, 24, runes=BONE, braid=True, collar=BARK_L,
     paint=(BONE, "woad"))
skin(B("gudrun"), (190, 190, 196), None, (0, 0, 0), BARK, 25, bare_chest=True, hood=IRONG, paint=(FOREST, "woad"),
     tone=(205, 160, 130), scars=True)
skin(B("eydis"), (100, 76, 52), None, MOSS, BARK_D, 26, hood=FOREST_D, collar=BARK_L, tone=(204, 160, 128))
# bosses share the Aldermark-style 64x64 layout; the court lane's renderer takes the texture path from the boss definition.

# ---------------------------------------------------------------- item icons (16x16)


def icon(name, fn):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    fn(im, d)
    im.save(os.path.join(ITEM, name + ".png"))


def P(d, pts, c):
    for x, y in pts: d.point((x, y), fill=c + (255,) if len(c) == 3 else c)


SIL, SIL2, SIL3 = (214, 220, 228), (160, 168, 180), (98, 106, 118)


def hacksilver(im, d):  # cut silver ring pieces: a bent ring segment, a short cut piece and chips
    for (x, y) in [(3,9),(3,8),(3,7),(4,6),(5,5),(6,4),(7,4),(8,4),(9,5),(10,6)]:
        d.point((x, y), fill=SIL + (255,)); d.point((x + (1 if x < 8 else 0), y + (1 if x < 8 else 1)), fill=SIL2 + (255,))
    for (x, y) in [(4,11),(5,12),(6,12),(7,11)]:
        d.point((x, y), fill=SIL + (255,)); d.point((x, y + 1), fill=SIL3 + (255,))
    for (x, y) in [(10,10),(11,10),(12,11),(11,11),(12,12)]:
        d.point((x, y), fill=SIL2 + (255,))
    P(d, [(3, 7), (5, 5), (8, 4), (5, 12)], (255, 255, 255))


def seal_art(wax):
    def f(im, d):
        d.ellipse((2, 2, 13, 13), fill=wax + (255,))
        d.ellipse((3, 3, 12, 12), outline=tuple(min(255, c + 30) for c in wax) + (255,))
        # raven stamp
        P(d, [(5, 6), (6, 7), (7, 7), (8, 7), (9, 6), (10, 6), (6, 8), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9), (9, 9), (10, 10)], (30, 30, 36))
        P(d, [(5, 5), (10, 5)], (30, 30, 36))
        P(d, [(7, 10), (8, 11)], (30, 30, 36))
    return f


def crown(im, d):
    d.rectangle((3, 9, 12, 11), fill=(120, 130, 142, 255))
    d.rectangle((3, 9, 12, 9), fill=(190, 200, 214, 255))
    for x in (3, 6, 9, 12):
        d.line((x, 9, x, 5), fill=(160, 170, 184, 255)); d.point((x, 4), fill=(220, 230, 240, 255))
    P(d, [(7, 6), (8, 6), (7, 7), (8, 7)], (150, 150, 160)); P(d, [(7, 6)], (30, 30, 36)); P(d, [(8, 10)], EMBER)
    d.line((3, 11, 12, 11), fill=(70, 78, 88, 255))


def raven_banner(im, d):
    d.line((3, 1, 3, 15), fill=(110, 80, 50, 255))
    d.rectangle((4, 2, 12, 9), fill=RAVEN + (255,)); d.rectangle((4, 10, 12, 10), fill=EMBER + (255,))
    P(d, [(5, 5), (6, 4), (7, 5), (8, 5), (9, 4), (10, 5), (11, 6), (7, 6), (8, 7), (9, 6), (6, 6), (10, 6)], FROST)
    P(d, [(7, 11), (8, 11), (9, 11)], (30, 32, 36)); d.point((12, 11), fill=(30, 32, 36, 255))


def boots(im, d):
    fur, fur2, leather = (186, 184, 180), (140, 138, 134), (86, 70, 54)
    for ox in (2, 9):
        d.rectangle((ox, 4, ox + 4, 11), fill=leather + (255,))
        d.rectangle((ox - 1, 2, ox + 5, 4), fill=fur + (255,))
        P(d, [(ox, 3), (ox + 2, 2), (ox + 4, 3)], fur2)
        d.rectangle((ox - 1, 11, ox + 5, 13), fill=(60, 48, 38, 255)); d.rectangle((ox + 3, 12, ox + 6, 13), fill=(60, 48, 38, 255))
        d.point((ox + 1, 6), fill=fur + (255,)); d.point((ox + 3, 8), fill=fur2 + (255,))


def mail(im, d):
    base, hi, lo = (122, 130, 142), (176, 184, 196), (70, 76, 86)
    d.polygon([(2, 3), (5, 2), (7, 4), (9, 4), (11, 2), (14, 3), (14, 7), (12, 7), (12, 14), (4, 14), (4, 7), (2, 7)], fill=base + (255,))
    for y in range(3, 14, 2):
        for x in range(3 + (y % 4 == 3), 14, 2): d.point((x, y), fill=hi + (255,))
    d.line((4, 14, 12, 14), fill=lo + (255,)); d.rectangle((6, 3, 9, 4), fill=(60, 66, 76, 255))
    d.rectangle((7, 7, 8, 12), fill=EMBER + (255,))  # hearth-ember stripe
    P(d, [(7, 8), (8, 9)], (255, 190, 90))


def axe(im, d):
    d.line((3, 13, 11, 5), fill=(110, 80, 50, 255), width=1)
    d.line((3, 12, 11, 4), fill=(140, 104, 64, 255))
    d.polygon([(8, 2), (13, 2), (14, 7), (11, 8), (9, 6)], fill=(190, 198, 210, 255))
    d.line((9, 3, 13, 3), fill=(236, 240, 246, 255)); d.line((12, 3, 13, 7), fill=(120, 128, 140, 255))
    P(d, [(10, 4), (11, 5), (11, 4)], (170, 214, 120))  # rune
    P(d, [(5, 11), (7, 9)], (170, 214, 120))


def pelt(im, d):
    fur, fur2, fur3 = (148, 146, 142), (110, 108, 104), (200, 198, 194)
    d.polygon([(1, 3), (5, 2), (7, 4), (9, 4), (11, 2), (15, 3), (15, 8), (13, 8), (13, 14), (3, 14), (3, 8), (1, 8)], fill=fur + (255,))
    rnd = random.Random(7)
    for _ in range(26): d.point((rnd.randint(2, 13), rnd.randint(3, 13)), fill=rnd.choice((fur2, fur3)) + (255,))
    d.rectangle((6, 2, 9, 4), fill=(52, 50, 48, 255))  # wolf-head hood nub
    P(d, [(7, 3), (8, 3)], (230, 90, 70)); d.line((3, 14, 13, 14), fill=fur2 + (255,))


def hornbow(im, d):
    wood, horn = (124, 88, 52), (232, 224, 200)
    pts = [(10, 1), (12, 3), (13, 6), (13, 9), (12, 12), (10, 14)]
    for a, b in zip(pts, pts[1:]): d.line(a + b, fill=wood + (255,))
    P(d, [(10, 1), (10, 14), (11, 2), (11, 13)], horn)
    d.line((10, 2, 10, 13), fill=(220, 220, 220, 255))
    d.line((3, 13, 12, 7), fill=(150, 110, 70, 255)); P(d, [(2, 14), (3, 14)], (255, 255, 255)); P(d, [(1, 15)], (190, 190, 190))
    P(d, [(12, 7), (11, 7)], (226, 218, 194))  # slowness-frost nock


def trim_template(im, d):
    d.rectangle((1, 1, 14, 14), fill=(40, 62, 38, 255)); d.rectangle((2, 2, 13, 13), fill=(88, 112, 72, 255))
    d.rectangle((3, 3, 12, 12), fill=(36, 54, 34, 255))
    P(d, [(4, 5), (5, 6), (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (11, 5), (6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (7, 9), (8, 10)], (226, 234, 244))
    P(d, [(1, 1), (14, 1), (1, 14), (14, 14)], (150, 160, 174))


def banner_pattern_item(im, d):
    d.rectangle((3, 1, 12, 14), fill=(236, 232, 220, 255)); d.rectangle((3, 1, 12, 2), fill=(190, 186, 176, 255))
    d.rectangle((4, 3, 11, 12), fill=RAVEN + (255,))
    P(d, [(5, 5), (6, 6), (7, 6), (8, 6), (9, 6), (10, 5), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 10)], FROST)
    d.rectangle((4, 12, 11, 12), fill=EMBER + (255,))


def raven_shield(im, d):
    d.polygon([(3, 2), (12, 2), (12, 9), (8, 14), (3, 9)], fill=RAVEN + (255,)); d.polygon([(3, 2), (12, 2), (12, 9), (8, 14), (3, 9)], outline=(150, 156, 166, 255))
    P(d, [(5, 5), (6, 6), (7, 6), (8, 6), (9, 5), (7, 7), (7, 8), (6, 9)], FROST); d.point((7, 4), fill=EMBER + (255,))
    d.ellipse((6, 6, 8, 8), outline=(180, 188, 200, 255))


def blanket(im, d):
    d.rectangle((2, 4, 13, 12), fill=(150, 146, 140, 255))
    rnd = random.Random(3)
    for _ in range(34): d.point((rnd.randint(2, 13), rnd.randint(4, 12)), fill=rnd.choice(((112, 108, 102), (196, 192, 186))) + (255,))
    d.rectangle((2, 4, 13, 5), fill=(40, 38, 40, 255)); d.rectangle((2, 11, 13, 12), fill=(40, 38, 40, 255))
    P(d, [(7, 8), (8, 8), (7, 7)], EMBER)


def collar_dye(im, d):
    d.rectangle((5, 3, 10, 5), fill=(222, 218, 210, 255)); d.polygon([(4, 5), (11, 5), (12, 12), (3, 12)], fill=(96, 98, 104, 255))
    rnd = random.Random(5)
    for _ in range(18): d.point((rnd.randint(4, 11), rnd.randint(6, 12)), fill=rnd.choice(((140, 142, 148), (72, 74, 80))) + (255,))
    P(d, [(6, 3), (9, 4), (7, 4)], (255, 255, 255)); d.rectangle((6, 1, 9, 2), fill=(107, 74, 43, 255))


icon("hacksilver", hacksilver)
SEALS = {"jarl": (46, 90, 44), "hersir": (107, 74, 43), "hallwarden": (76, 52, 32), "skald": (82, 112, 60), "chief": (130, 134, 140), "huntmaster": (60, 104, 58)}
for k, w in SEALS.items(): icon("hrafnholt_seal_" + k, seal_art(w))
icon("hrafnholt_iron_crown", crown); icon("hrafnholt_raven_banner", raven_banner); icon("hrafnholt_wolfskin_boots", boots)
icon("hrafnholt_hearth_ward_mail", mail); icon("hrafnholt_skalds_rune_axe", axe); icon("hrafnholt_ulfhednar_pelt", pelt)
icon("hrafnholt_hornbow", hornbow); icon("hrafnholt_armor_trim_smithing_template", trim_template)
icon("hrafnholt_banner_pattern", banner_pattern_item); icon("hrafnholt_raven_shield", raven_shield)
icon("hrafnholt_wolfpelt_blanket", blanket); icon("hrafnholt_fur_collar_dye", collar_dye)

# spawn eggs (16x16, base colour + spots), for the two unique units
def egg(name, base, spot):
    def f(im, d):
        rows = ["....######....", "...########...", "..##########..", "..##########..", ".############.", ".############.", ".############.", ".############.", "..##########..", "..##########..", "...########...", "....######...."]
        rnd = random.Random(sum(map(ord, name)))
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch == "#":
                    c = spot if rnd.random() < 0.22 else base
                    im.putpixel((i + 1, j + 2), c + (255,))
    icon(name, f)
egg("hrafnholt_berserker_spawn_egg", (46, 90, 44), (107, 74, 43))
egg("hrafnholt_wolf_handler_spawn_egg", (107, 74, 43), (190, 190, 190))

# ---------------------------------------------------------------- raven bitmap: banner (front x=1.., back x=22..), shield, trims
RAVEN_BM = [
    "#..............#",
    "##............##",
    "###....##....###",
    "####..####..####",
    ".#####.##.#####.",
    "..#####..#####..",
    "...####..####...",
    "....###..###....",
    ".....##..##.....",
    "......######....",
    ".......####.....",
    ".......####.....",
    "......######....",
    ".......#..#.....",
    "......##..##....",
]


def paint_bm(img, bm, ox, oy, val=240, scale=1):
    for j, row in enumerate(bm):
        for i, ch in enumerate(row):
            if ch == "#":
                for a in range(scale):
                    for b in range(scale): img.putpixel((ox + i * scale + a, oy + j * scale + b), (val, 255))


ban = Image.new("LA", (64, 64), (0, 0))
for ox in (1 + 1, 22 + 1):  # 16-wide raven, banner face is 20 wide
    paint_bm(ban, RAVEN_BM, ox + 1, 6)
    for x in range(ox, ox + 18): ban.putpixel((x, 30), (240, 255)); ban.putpixel((x, 31), (240, 255))  # foot stripe (the Jarl's tongue)
    for y in range(34, 36):
        for x in range(ox + 6, ox + 12): ban.putpixel((x, y), (240, 255))
ban.save(os.path.join(TEX, "entity", "banner", "hrafnholt.png"))
sh = Image.new("LA", (64, 64), (0, 0))
small = ["#.....#", "##...##", "###.###", ".#####.", "..###..", "..###..", ".#####.", "..#.#.."]
paint_bm(sh, small, 3, 3)
for x in range(3, 11): sh.putpixel((x, 14), (240, 255))
sh.save(os.path.join(TEX, "entity", "shield", "hrafnholt.png"))

# trim layers (grayscale palette index in L; A = 255 on pattern pixels). Raven-wing: helmet band with feather notches,
# a spread wing on the chest, wing-tip boot stripe, wing bars on the legs.
tr = Image.new("LA", (64, 32), (0, 0))
def px(im, pts, v):
    for x, y in pts: im.putpixel((x, y), (v, 255))
for x in range(0, 32):  # helmet band (rows 13-14 of the head cube) with feather notches above
    px(tr, [(x, 13), (x, 14)], 192)
    if x % 4 == 1: px(tr, [(x, 12), (x, 11)], 160)
    if x % 8 == 5: px(tr, [(x, 10)], 224)
for y in range(20, 31):  # chest: a wing spreading from the centre
    w = 1 + (y - 20) // 2
    for k in range(w):
        px(tr, [(23 - k, y), (24 + k, y)], 160 + 16 * (k % 3))
px(tr, [(23, 20), (24, 20), (23, 21), (24, 21)], 224)
for x in range(16, 32): px(tr, [(x, 31)], 128)
for x in range(0, 16):  # boots stripe, wing-tip notches
    px(tr, [(x, 28), (x, 29)], 192)
    if x % 3 == 0: px(tr, [(x, 27)], 160)
for x in range(40, 56): px(tr, [(x, 20), (x, 21)], 128)  # arm bars
tr.save(os.path.join(TEX, "trims", "entity", "humanoid", "hrafnholt.png"))
tl = Image.new("LA", (64, 32), (0, 0))
for x in range(0, 16): px(tl, [(x, 20), (x, 21)], 160)
for x in range(16, 32):
    if x % 3 != 0: px(tl, [(x, 22), (x, 23)], 128)
    px(tl, [(x, 21)], 192)
for y in range(20, 30): px(tl, [(2, y), (3, y), (12, y), (13, y)], 128)
tl.save(os.path.join(TEX, "trims", "entity", "humanoid_leggings", "hrafnholt.png"))
for sub in ("humanoid", "humanoid_leggings"):
    open(os.path.join(TEX, "trims", "entity", sub, "hrafnholt.png.mcmeta"), "w").write('{\n  "palette" : {\n    "base_palette" : "trim_base"\n  }\n}\n')
print("art done")
