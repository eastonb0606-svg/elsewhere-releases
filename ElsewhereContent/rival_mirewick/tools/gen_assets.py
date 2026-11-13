"""Mirewick asset generator: skins (64x64 player layout, plain clothing, no painted armor), item textures, trim, banner and
shield patterns, armor layers, spawn eggs, item/model json and lang. Run: python3 tools/gen_assets.py (from rival_mirewick/).
Palette (RIVALS-DECISIONS 16): royal purple 5B2A86, crimson 9E1B32, gold accents D6A832, pale stone E8E0CE. Open plains, canals and dikes."""
import os, json, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
A = os.path.join(RES, "assets", "elsewhere")
TEX = os.path.join(A, "textures")
ENT = os.path.join(TEX, "entity", "kingdom", "mirewick")
ITEM = os.path.join(TEX, "item")
for d in (ENT, ITEM, os.path.join(A, "items"), os.path.join(A, "models", "item"), os.path.join(A, "lang"),
          os.path.join(TEX, "trims", "entity", "humanoid"), os.path.join(TEX, "trims", "entity", "humanoid_leggings"),
          os.path.join(TEX, "entity", "banner"), os.path.join(TEX, "entity", "shield"),
          os.path.join(TEX, "entity", "equipment", "humanoid"), os.path.join(TEX, "entity", "equipment", "humanoid_leggings"),
          os.path.join(A, "equipment")):
    os.makedirs(d, exist_ok=True)

PURPLE, DPURPLE, CRIM, DCRIM, PLUM = (91, 42, 134), (58, 26, 88), (158, 27, 50), (104, 18, 36), (72, 34, 70)
MOSS, PEAT, AMBER, BONE = PURPLE, (46, 30, 44), (214, 168, 50), (232, 224, 206)   # old names kept: MOSS=purple, PEAT=dark plum, AMBER=gold, BONE=pale stone


def rect(img, box, col, noise=7, seed=0):
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for x in range(x0, x1):
        for y in range(y0, y1):
            n = r.randint(-noise, noise)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in col[:3]) + (255,))


def clear(img, box):
    """Real transparency (rect() always writes alpha 255, so clearing with it painted opaque black)."""
    for x in range(box[0], box[2]):
        for y in range(box[1], box[3]): img.putpixel((x, y), (0, 0, 0, 0))


def cube(img, u, v, w, h, d, col, seed, noise=7):
    rect(img, (u + d, v, u + d + w, v + d), col, noise, seed)
    rect(img, (u + d + w, v, u + d + 2 * w, v + d), col, noise, seed + 1)
    rect(img, (u, v + d, u + 2 * d + 2 * w, v + d + h), col, noise, seed + 2)


def shade(c, k):
    return tuple(max(0, min(255, int(x * k))) for x in c)


def skin(name, tone=(190, 150, 118), hair=(80, 60, 40), beard=None, shirt=(110, 100, 80), hose=(70, 55, 42), coat=None,
         hat=None, waders=None, sash=None, boots=(50, 38, 30), seed=1, hood=False, apron=None, eyes=(55, 45, 38)):
    """coat: oilcloth overlay (body+arms+hip); hat: wide-brim hat overlay colour; waders: leg overlay colour with straps."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    cube(img, 0, 0, 8, 8, 8, tone, seed)
    rect(img, (8, 0, 16, 8), hair, 5, seed + 3)
    rect(img, (0, 8, 8, 11), hair, 5, seed + 4); rect(img, (16, 8, 32, 11), hair, 5, seed + 5)
    rect(img, (24, 8, 32, 16), hair, 5, seed + 6); rect(img, (8, 8, 16, 10), hair, 5, seed + 7)
    for x, y, c in [(9, 12, (235, 235, 235)), (10, 12, eyes), (13, 12, eyes), (14, 12, (235, 235, 235)),
                    (9, 11, shade(hair, .7)), (10, 11, shade(hair, .7)), (13, 11, shade(hair, .7)), (14, 11, shade(hair, .7)),
                    (11, 13, shade(tone, .85)), (12, 13, shade(tone, .85)), (11, 14, (125, 75, 65)), (12, 14, (125, 75, 65))]:
        img.putpixel((x, y), c + (255,))
    if beard:
        rect(img, (8, 14, 16, 16), beard, 5, seed + 8); rect(img, (9, 13, 11, 14), beard, 3, seed + 9); rect(img, (13, 13, 15, 14), beard, 3, seed + 10)
        img.putpixel((11, 14), (125, 75, 65, 255)); img.putpixel((12, 14), (125, 75, 65, 255))
    cube(img, 16, 16, 8, 12, 4, shirt, seed + 11)
    for (u, v) in ((40, 16), (32, 48)):
        cube(img, u, v, 4, 12, 4, shirt, seed + 13)
        rect(img, (u, v + 13, u + 16, v + 16), tone, 5, seed + 14)
    for (u, v) in ((0, 16), (16, 48)):
        cube(img, u, v, 4, 12, 4, hose, seed + 16)
        rect(img, (u, v + 4 + 8, u + 16, v + 16), boots, 4, seed + 17)
    if sash:
        for i in range(12):                                   # diagonal sash on the torso front
            x = 20 + (i * 8) // 12
            if 20 <= x < 28: img.putpixel((x, 20 + i), sash + (255,)); img.putpixel((min(27, x + 1), 20 + i), sash + (255,))
    if apron:
        rect(img, (21, 24, 27, 32), apron, 3, seed + 30)
    if coat:                                                   # oilcloth: slick overlay on body, arms and hip
        c2 = shade(coat, 1.25)
        cube(img, 16, 32, 8, 12, 4, coat, seed + 20, 4)
        for yy in range(33 + 4, 33 + 16, 4):
            for xx in range(20, 28): img.putpixel((xx, yy), c2 + (255,))
        for (u, v) in ((40, 32), (48, 48)):
            cube(img, u, v, 4, 12, 4, coat, seed + 21, 4)
            clear(img, (u, v + 13, u + 16, v + 16))    # hands stay bare
        for xx in range(20, 28): img.putpixel((xx, 33 + 5), shade(coat, .6) + (255,))
        for xx in range(20, 28): img.putpixel((xx, 33 + 11), shade(coat, .7) + (255,))
        img.putpixel((23, 33 + 2), AMBER + (255,)); img.putpixel((24, 33 + 2), AMBER + (255,))   # lantern clasp
        clear(img, (16, 44, 40, 45))
    if waders:                                                 # chest-high waders: leg overlays plus two shoulder straps
        for (u, v) in ((0, 32), (0, 48)):
            cube(img, u, v, 4, 12, 4, waders, seed + 24, 4)
        for x in (21, 26):
            for y in range(20, 32): img.putpixel((x, y), shade(waders, 1.15) + (255,))
    if hat:
        hb = shade(hat, 1.12)
        rect(img, (40, 0, 48, 8), hat, 4, seed + 40)                        # crown top (overlay top face)
        for k, (x0, x1) in enumerate(((32, 40), (40, 48), (48, 56), (56, 64))):
            rect(img, (x0, 8, x1, 11), hat, 4, seed + 41 + k)               # crown sides
            rect(img, (x0, 11, x1, 12), shade(hat, .6), 2, seed + 45 + k)   # brim shadow
            rect(img, (x0, 12, x1, 13), hb, 3, seed + 49 + k)               # brim band round the head
        for x in (42, 43, 44, 45): img.putpixel((x, 10), AMBER + (255,))     # hat band on the front
    return img


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# ---- unit skins ------------------------------------------------------------------------------------------------------
OIL_GREEN, OIL_BLACK, OIL_BROWN, OIL_GREY = PURPLE, DPURPLE, CRIM, (112, 70, 130)   # old names: now velvet purple, deep violet, crimson, lilac
CREAM, GOLD = (226, 214, 196), AMBER
SK = {
    "dikeguard": dict(hair=(70, 55, 40), shirt=CREAM, hose=PEAT, coat=PURPLE, hat=DPURPLE, waders=DCRIM, sash=GOLD, seed=1),
    "reed_knight": dict(hair=(40, 34, 30), beard=(52, 40, 32), shirt=(206, 196, 186), hose=DPURPLE, coat=DPURPLE, hat=CRIM, waders=PLUM, seed=2),
    "fowler": dict(hair=(150, 112, 62), shirt=(196, 160, 170), hose=(84, 50, 70), coat=CRIM, hat=PURPLE, seed=3),
    "punt_crossbow": dict(hair=(110, 80, 52), shirt=CREAM, hose=(80, 44, 84), coat=(112, 70, 130), hat=DCRIM, waders=PLUM, seed=4),
    "marsh_rider": dict(hair=(60, 44, 34), beard=(70, 52, 38), shirt=(214, 200, 180), hose=PLUM, coat=PURPLE, hat=CRIM, sash=GOLD, seed=5),
    "pikeman": dict(hair=(100, 84, 60), shirt=CREAM, hose=PEAT, coat=DPURPLE, hat=(120, 40, 60), waders=DCRIM, seed=6),
    "reeve": dict(hair=(130, 130, 130), beard=(145, 145, 145), shirt=CREAM, hose=PEAT, coat=PURPLE, hat=DCRIM, sash=GOLD, seed=7),
    "wardman": dict(hair=(150, 150, 150), beard=(160, 160, 160), shirt=(236, 226, 210), hose=PEAT, coat=CRIM, hat=DPURPLE, sash=GOLD, seed=8),
    "lantern_bearer": dict(hair=(86, 60, 40), shirt=CREAM, hose=PLUM, coat=(120, 60, 150), hat=GOLD, sash=CRIM, seed=9),
    "bellman": dict(hair=(60, 40, 30), beard=(70, 48, 34), shirt=(230, 220, 200), hose=PLUM, coat=CRIM, hat=PURPLE, sash=GOLD, seed=10),
    "almoners_hand": dict(hair=(200, 196, 180), shirt=BONE, hose=(176, 160, 190), coat=(220, 208, 228), hat=(198, 180, 214), apron=(236, 228, 238), sash=GOLD, seed=11),
    "linkboy": dict(hair=(120, 90, 50), shirt=(190, 160, 150), hose=(84, 50, 70), hat=CRIM, seed=12),
    "bog_warden": dict(hair=(30, 26, 22), beard=(36, 30, 26), shirt=(196, 186, 176), hose=PEAT, coat=DPURPLE, hat=(46, 24, 60), waders=(84, 20, 40), sash=GOLD, seed=13),
    "mire_trapper": dict(hair=(90, 70, 46), shirt=(170, 140, 150), hose=PLUM, coat=(120, 40, 70), hat=(72, 34, 90), waders=PLUM, seed=14),
    "peat_cutter": dict(hair=(70, 52, 36), shirt=(150, 122, 128), hose=(84, 62, 74), hat=(130, 100, 110), apron=(92, 56, 84), boots=(44, 32, 36), seed=15),
    # Free Company sellswords (mixed, no kingdom colours): their own grey and black
    "fc_sellsword": dict(hair=(60, 52, 46), beard=(74, 62, 52), shirt=(120, 110, 100), hose=(64, 58, 54), sash=(120, 120, 126), seed=21),
    "fc_crossbow": dict(hair=(150, 90, 50), shirt=(96, 100, 104), hose=(60, 56, 50), coat=(78, 78, 84), hat=(60, 60, 66), seed=22),
    "fc_brute": dict(hair=(30, 28, 26), beard=(40, 34, 30), shirt=(130, 116, 100), hose=(56, 52, 50), sash=(190, 190, 196), seed=23),
    "fc_captain": dict(hair=(110, 110, 114), beard=(120, 120, 124), shirt=(80, 80, 88), hose=(48, 46, 50), coat=(60, 60, 68), hat=(40, 40, 46), sash=(200, 200, 208), seed=24),
    # the five Magnates, plain clothing (armor is real and worn on top): rich merchant-royal
    "magnate_marrow": dict(tone=(200, 160, 130), hair=(40, 36, 36), shirt=GOLD, hose=PEAT, coat=PURPLE, hat=DPURPLE, sash=CRIM, seed=31),
    "magnate_thwaite": dict(tone=(176, 138, 108), hair=(100, 70, 44), beard=(110, 78, 50), shirt=CREAM, hose=PEAT, coat=DCRIM, sash=GOLD, seed=32),
    "magnate_sorrel": dict(tone=(196, 156, 124), hair=(130, 74, 44), shirt=(150, 40, 70), hose=PLUM, waders=(84, 40, 110), sash=GOLD, seed=33),
    "magnate_tolliver": dict(tone=(204, 166, 134), hair=(166, 124, 70), shirt=(196, 170, 190), hose=PLUM, coat=(120, 60, 150), hat=CRIM, sash=GOLD, seed=34),
    "magnate_ames": dict(tone=(212, 176, 146), hair=(220, 216, 204), beard=(226, 222, 210), shirt=BONE, hose=(176, 160, 190), coat=(226, 214, 236), apron=GOLD, sash=PURPLE, seed=35),
}
for n, kw in SK.items():
    kw = dict(kw); kw.pop("hood", None)
    save(skin(n, **kw), os.path.join(ENT, n + ".png"))

# ---- 16x16 item art from ascii masks ---------------------------------------------------------------------------------
def draw(rows, pal):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in pal: im.putpixel((x, y), pal[ch] + (255,))
    return im


def pal4(base, accent=AMBER, dark=None):
    return {"a": shade(base, 1.35), "b": base, "c": shade(base, .72), "d": shade(base, .5), "x": accent, "y": shade(accent, .7), "w": BONE, "k": (24, 20, 18)}


BOOTS = ["................", "................", "................", "................", "...bbb....bbb...", "...bab....bab...", "...bbb....bbb...", "...bcb....bcb...", "..bbbb....bbbb..", "..bxbb....bbxb..", ".bbbbbb..bbbbbb.", ".bccccb..bccccb.", ".bddddb..bddddb.", "................", "................", "................"]
LEGS = ["................", "..dbbbbbbbbbbd..", "..bxbbbbbbbbxb..", "..babbbbbbbbab..", "..bbbbbbbbbbbb..", "..bbbbbbbbbbbb..", "..bbbb....bbbb..", "..bbab....babb..", "..bbbb....bbbb..", "..bcbb....bbcb..", "..bcbb....bbcb..", "..bbbb....bbbb..", "..bcbb....bbcb..", "..dddd....dddd..", "................", "................"]
CHEST = ["................", "..bb........bb..", ".bbbb.bbbb.bbbb.", ".bbab.bxxb.babb.", ".bbbbbbxxbbbbbb.", ".bbbbbbbbbbbbbb.", ".bcbbbbbbbbbbcb.", ".bcbbbaabbbbbcb.", "..bbbbaabbbbbb..", "..bbbbbbbbbbbb..", "..bcbbbbbbbbcb..", "..bbbbbbbbbbbb..", "..bcccccccccdb..", "..dddddddddddd..", "................", "................"]
HELM = ["................", "................", "................", "....bbbbbbbb....", "...bbaabbbbbb...", "..bbbbbbbbbbbb..", "..bbbbbbbbbbbb..", ".bbcbbbbbbbbcbb.", ".bcccccccccccdb.", ".bbbb......bbbb.", ".bbbb......bbbb.", "..dd........dd..", "................", "................", "................", "................"]
CORONET = ["................", "................", "................", "..x..x..x..x....", "..xx.xx.xx.xx...", "..xxxxxxxxxxx...", "..xwbxxwxxbwx...", "..xbbbbbbbbbx...", "..yyyyyyyyyyy...", "................", "................", "................", "................", "................", "................", "................"]
BOW = ["................", ".............bb.", "...........bbb..", "..........bb.w..", ".........bb..w..", "........bb...w..", ".......bb....w..", ".......bb....w..", ".......bb....w..", "........bb...w..", ".........bb..w..", "..........bb.w..", "...........bbb..", ".............bb.", "................", "................"]
PIKE = ["..............ww", ".............www", "............www.", "...........bww..", "..........bb....", ".........bb.....", "........bb......", ".......bb.......", "......bb........", ".....bb.........", "....bb..........", "...bb...........", "..bb............", ".bb.............", "b...............", "................"]
SEAL = ["................", "......bbbb......", "....bbbbbbbb....", "...bbaabbbbbb...", "..bbaabbbbbbbb..", "..bbabbxxbbbcb..", ".bbbbbbxxbbbbcb.", ".bbbbbxxxxbbbcb.", ".bbbbbbxxbbbbcb.", "..bbbbbxxbbbcb..", "..bbbbbbbbbcdb..", "...bbbbbbbbcd...", "....bbcccccd....", "......dddd......", "................", "................"]
NOTE = ["................", "................", ".wwwwwwwwwwwww..", ".wBBBBBBBBBBBw..", ".wBwwwwwwwwBw...", ".wBwwwwwwwwBw...", ".wBwwwxxwwwBw...", ".wBwwwxxwwwBw...", ".wBwwwwwwwwBw...", ".wBBBBBBBBBBw...", ".wwwwwwwwwwww...", "................", "................", "................", "................", "................"]
STANDARD = ["......xx........", "......xxx.......", "......xxxx......", "......bbbbb.....", "......bbbbbb....", "......bbbbbbb...", "......bbbbbb....", "......bbbbb.....", "......bb........", "......bb........", "......bb........", "......bb........", "......bb........", "......bb........", "......dd........", "................"]
TEMPLATE = ["................", "..ddddddddddddd.", "..dbbbbbbbbbbbd.", "..dbbaaabbbbbbd.", "..dbbbxxbbbbbbd.", "..dbbxxxxbbbbbd.", "..dbbxwwxbbbbbd.", "..dbbxxxxbbbbbd.", "..dbbbxxbbbbbbd.", "..dbbbbbbbbbbbd.", "..dbbbbbbbbbbbd.", "..ddddddddddddd.", "................", "................", "................", "................"]
SHIELDI = ["................", "...bbbbbbbbbb...", "..bbaabbbbbbbb..", "..bbbbbxxbbbcb..", "..bbbbxxxxbbcb..", "..bbbbxwwxbbcb..", "..bbbbxxxxbbcb..", "..bbbbbxxbbbcb..", "..bbbbbbbbbbcb..", "...bbbbbbbbcb...", "....bbbbbbcb....", ".....bbbbcb.....", "......bbcb......", ".......dd.......", "................", "................"]
DYE = ["................", "................", "......xxxx......", ".....xxxxxx.....", "....xxaaxxxx....", "....xxaxxxxx....", "....xxxxxxxx....", "....cxxxxxxc....", ".....cccccc.....", "................", "................", "................", "................", "................", "................", "................"]
LANT = ["................", "......yyyy......", ".....y....y.....", "....bbbbbbbb....", "....bxxxxxxb....", "....bxxwwxxb....", "....bxxwwxxb....", "....bxxxxxxb....", "....bbbbbbbb....", ".....d....d.....", "................", "................", "................", "................", "................", "................"]

ITEMS = {}   # id -> (rows, palette)
LEATHER, STEEL, GREENLEAT = (122, 90, 56), (150, 156, 160), (70, 98, 60)
ITEMS["mirewick_scrip"] = (NOTE, {**pal4(BONE), "B": PURPLE, "x": CRIM})
SEALCOL = {"marrow": PURPLE, "thwaite": CRIM, "sorrel": (120, 60, 160), "tolliver": (130, 30, 90), "ames": (226, 214, 236)}
for k, c in SEALCOL.items():
    ITEMS[f"mirewick_seal_{k}"] = (SEAL, pal4(c, AMBER if k != "ames" else (120, 90, 40)))
ITEMS["mirewick_debt_collectors_leggings"] = (LEGS, pal4(PURPLE, AMBER))
ITEMS["mirewick_fen_marshals_waders"] = (BOOTS, pal4(CRIM, AMBER))
ITEMS["mirewick_dikewardens_brigandine"] = (CHEST, pal4(DPURPLE, (214, 168, 50)))
ITEMS["mirewick_fowlers_marshbow"] = (BOW, {**pal4((120, 50, 110)), "w": BONE})
ITEMS["mirewick_almoners_cowl"] = (HELM, pal4((226, 214, 236), AMBER))
ITEMS["mirewick_lantern_coronet"] = (CORONET, pal4(AMBER, (150, 40, 90)))
ITEMS["mirewick_marsh_lantern_standard"] = (STANDARD, pal4(PURPLE, AMBER))
ITEMS["mirewick_reedcutter_pike"] = (PIKE, {**pal4((120, 70, 40)), "w": STEEL})
ITEMS["mirewick_armor_trim_smithing_template"] = (TEMPLATE, pal4(DPURPLE, AMBER))
ITEMS["mirewick_banner_pattern"] = (NOTE, {**pal4(BONE), "B": PURPLE, "x": AMBER})
ITEMS["mirewick_lantern_shield"] = (SHIELDI, pal4(CRIM, AMBER))
ITEMS["mirewick_oilcloth_dye"] = (DYE, pal4(PURPLE, (150, 90, 190)))
for n, (rows, pal) in ITEMS.items():
    save(draw(rows, pal), os.path.join(ITEM, n + ".png"))

# spawn eggs
EGG = {"bog_warden": (DPURPLE, (214, 168, 50)), "mire_trapper": ((120, 40, 70), (214, 168, 50)),
       "marrow": (PURPLE, AMBER), "thwaite": (CRIM, AMBER), "sorrel": ((120, 60, 160), CRIM),
       "tolliver": ((130, 30, 90), AMBER), "ames": ((226, 214, 236), PURPLE)}
for k, (base, spot) in EGG.items():
    e = Image.new("RGBA", (16, 16), (0, 0, 0, 0)); dd = ImageDraw.Draw(e)
    dd.ellipse((3, 1, 12, 14), fill=base + (255,), outline=shade(base, .5) + (255,))
    r = random.Random(k)
    for _ in range(5):
        x, y = r.randint(5, 10), r.randint(3, 11); dd.rectangle((x, y, x + 1, y + 1), fill=spot + (255,))
    e.putpixel((5, 3), (255, 255, 255, 200))
    e.save(os.path.join(ITEM, f"mirewick_{k}_spawn_egg.png"))

# ---- armor layer textures (own, not leather) ---------------------------------------------------------------------------
def armor_layer(path, base, accent, leggings, seed):
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    regions = [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)] if not leggings else [(16, 16, 40, 32), (0, 16, 16, 32)]
    for i, rg in enumerate(regions): rect(im, rg, base, 9, seed + i)
    d = ImageDraw.Draw(im)
    if not leggings:
        for y in (13, 14): d.line((0, y, 31, y), fill=accent + (255,))        # helmet band
        d.rectangle((22, 18, 25, 25), fill=accent + (255,)); d.rectangle((23, 19, 24, 20), fill=shade(accent, 1.4) + (255,))   # lantern on chest
        d.line((40, 22, 55, 22), fill=accent + (255,)); d.line((0, 28, 15, 28), fill=accent + (255,))
    else:
        d.line((16, 17, 39, 17), fill=accent + (255,)); d.line((0, 23, 15, 23), fill=accent + (255,)); d.line((16, 31, 39, 31), fill=shade(base, .6) + (255,))
    im.save(path)


EQ = {"debt_collector": (PURPLE, AMBER, True), "fen_marshal": (CRIM, AMBER, False), "dikewarden": (DPURPLE, (214, 168, 50), False),
      "almoner": ((226, 214, 236), AMBER, False), "lantern_coronet": (AMBER, (150, 40, 90), False)}
for i, (k, (b, a, leg)) in enumerate(EQ.items()):
    armor_layer(os.path.join(TEX, "entity", "equipment", "humanoid_leggings" if leg else "humanoid", f"mirewick_{k}.png"), b, a, leg, 40 + i)
    layer = "humanoid_leggings" if leg else "humanoid"
    with open(os.path.join(A, "equipment", f"mirewick_{k}.json"), "w") as f:
        json.dump({"layers": {layer: [{"texture": f"elsewhere:mirewick_{k}"}]}}, f, indent=2)

# ---- trim (custom 'mirewick' lantern), banner pattern and shield pattern ------------------------------------------------
LANTERN = ["..xx..", ".x..x.", "xxxxxx", "x.ww.x", "x.ww.x", "xxxxxx", ".x..x.", "..xx.."]
def trim(path, leggings):
    im = Image.new("RGBA", (64, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    g = [(224, 224, 224), (192, 192, 192), (160, 160, 160), (128, 128, 128)]
    if not leggings:
        d.rectangle((0, 13, 31, 14), fill=g[1] + (255,))
        for x in (1, 9, 17, 25): d.rectangle((x, 11, x, 12), fill=g[0] + (255,))
        d.rectangle((20, 20, 27, 31), fill=g[3] + (255,))
        for y, row in enumerate(LANTERN):
            for x, ch in enumerate(row):
                if ch != ".": im.putpixel((21 + x, 21 + y), (g[0] if ch == "w" else g[1]) + (255,))
        d.rectangle((40, 20, 55, 21), fill=g[2] + (255,)); d.rectangle((0, 28, 15, 29), fill=g[2] + (255,)); d.rectangle((16, 31, 39, 31), fill=g[1] + (255,))
    else:
        d.rectangle((16, 26, 39, 27), fill=g[1] + (255,)); d.rectangle((5, 20, 6, 31), fill=g[2] + (255,)); d.rectangle((13, 20, 14, 31), fill=g[2] + (255,))
        d.rectangle((0, 24, 3, 25), fill=g[0] + (255,))
    im.save(path)


trim(os.path.join(TEX, "trims", "entity", "humanoid", "mirewick.png"), False)
trim(os.path.join(TEX, "trims", "entity", "humanoid_leggings", "mirewick.png"), True)
for sub in ("humanoid", "humanoid_leggings"):
    with open(os.path.join(TEX, "trims", "entity", sub, "mirewick.png.mcmeta"), "w") as f: json.dump({"palette": {"base_palette": "trim_base"}}, f, indent=2)

BIG = ["....xxxx....", "...x....x...", "..xx....xx..", ".xxxxxxxxxx.", ".x........x.", ".x..wwww..x.", ".x.wwwwww.x.", ".x.wwwwww.x.", ".x.wwwwww.x.", ".x..wwww..x.", ".x........x.", ".xxxxxxxxxx.", "..x......x..", "...xxxxxx..."]
def banner(path, shield):
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    centers = [(11, 14)] if shield else [(11, 14), (32, 14)]
    if shield: centers = [(7, 8)]
    for cx, cy in centers:
        for y, row in enumerate(BIG):
            for x, ch in enumerate(row):
                if ch != ".": im.putpixel((cx - 6 + x, cy + y), (255, 255, 255, 255))
        if not shield:
            for yy in range(30, 34):
                for xx in range(cx - 4, cx + 5): im.putpixel((xx, yy), (255, 255, 255, 255))     # chevron stub under the lantern
    im.save(path)


banner(os.path.join(TEX, "entity", "banner", "mirewick.png"), False)
banner(os.path.join(TEX, "entity", "shield", "mirewick.png"), True)

# ---- item/model json -------------------------------------------------------------------------------------------------
models = list(ITEMS) + [f"mirewick_{k}_spawn_egg" for k in EGG]
for n in models:
    with open(os.path.join(A, "items", n + ".json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"elsewhere:item/{n}"}}, f, indent=2)
    with open(os.path.join(A, "models", "item", n + ".json"), "w") as f:
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"elsewhere:item/{n}"}}, f, indent=2)

print("assets written:", len(SK), "skins,", len(ITEMS), "items,", len(EGG), "eggs")
