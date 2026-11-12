"""Generates every resource for the petalwind_glasswaste module: textures (procedural pixel art, PIL), block/item models,
blockstates, item definitions, equipment assets, lang, loot tables, recipes, tags, damage type, dimension types,
dimensions, noise settings, biomes, features and attunement files.
Run: S/venv/bin/python tools/gen_assets.py <path to vanilla extracted assets+data dir (mcfull)>
Vanilla JSON (cherry wood set, stone brick set, cube models) is used as a *template* and re-pointed to elsewhere ids;
no vanilla texture is copied."""
import json, math, os, random, re, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
A = os.path.join(RES, "assets", "elsewhere")
D = os.path.join(RES, "data", "elsewhere")
MC = sys.argv[1] if len(sys.argv) > 1 else None
NS = "elsewhere"
rng = random.Random(1234)


def wj(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


def rj(path):
    with open(path) as f:
        return json.load(f)


def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (int(a[3] + (b[3] - a[3]) * t),)


def save(img, rel):
    p = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)


# ------------------------------------------------------------------------------------------------ block textures

def noise_tex(base, var=0.12, seed=0, size=16, alpha=255):
    r = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    c = hexc(base, alpha)
    for y in range(size):
        for x in range(size):
            img.putpixel((x, y), shade(c, 1 + (r.random() - 0.5) * 2 * var))
    return img


def planks(base, seed):
    img = noise_tex(base, 0.06, seed)
    c = hexc(base)
    for y in range(16):
        if y % 4 == 3:
            for x in range(16):
                img.putpixel((x, y), shade(c, 0.72))
        off = (y // 4) * 5 % 16
        img.putpixel(((off + 7) % 16, y), shade(c, 0.8))
    return img


def log_side(bark, seed):
    r = random.Random(seed)
    img = noise_tex(bark, 0.08, seed)
    c = hexc(bark)
    for x in range(16):
        if r.random() < 0.35:
            for y in range(16):
                if r.random() < 0.8:
                    img.putpixel((x, y), shade(c, 0.78))
    return img


def log_top(bark, inner, seed):
    img = noise_tex(inner, 0.05, seed)
    ci, cb = hexc(inner), hexc(bark)
    for y in range(16):
        for x in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            if d > 6.5:
                img.putpixel((x, y), cb)
            elif int(d) % 2 == 0:
                img.putpixel((x, y), shade(ci, 0.88))
    return img


def leaves(base, seed, flowers=None):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    c = hexc(base)
    for y in range(16):
        for x in range(16):
            if r.random() < 0.78:
                img.putpixel((x, y), shade(c, 0.8 + r.random() * 0.4))
    if flowers:
        fc = hexc(flowers)
        for _ in range(10):
            img.putpixel((r.randrange(16), r.randrange(16)), fc)
    return img


def bricks(base, mortar, seed, vein=None):
    r = random.Random(seed)
    img = noise_tex(base, 0.07, seed)
    m = hexc(mortar)
    for y in range(16):
        for x in range(16):
            row = y // 4
            if y % 4 == 3 or (x + (4 if row % 2 else 0)) % 8 == 7:
                img.putpixel((x, y), m)
    if vein:
        v = hexc(vein)
        x, y = r.randrange(16), 0
        for _ in range(20):
            img.putpixel((x % 16, y % 16), v)
            x += r.choice([-1, 0, 1])
            y += 1
    return img


def ore(base_img, gem, seed):
    r = random.Random(seed)
    img = base_img.copy()
    g = hexc(gem)
    for _ in range(4):
        cx, cy = r.randrange(2, 14), r.randrange(2, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (0, -1), (1, 1)):
            if r.random() < 0.85:
                img.putpixel(((cx + dx) % 16, (cy + dy) % 16), shade(g, 0.85 + r.random() * 0.3))
        img.putpixel((cx, cy), shade(g, 1.3))
    return img


def glass(frame, fill_alpha, fill, seed):
    img = Image.new("RGBA", (16, 16), hexc(fill, fill_alpha))
    f = hexc(frame)
    for i in range(16):
        for p in ((i, 0), (i, 15), (0, i), (15, i)):
            img.putpixel(p, f)
    r = random.Random(seed)
    for k in range(3):
        x, y = r.randrange(3, 10), r.randrange(3, 10)
        for j in range(3):
            img.putpixel((x + j, y + j), hexc("FFFFFF", 160))
    return img


def cross_plant(stem, flower, seed):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    s = hexc(stem)
    for y in range(6, 16):
        img.putpixel((7 + (1 if y < 10 else 0), y), s)
    fc = hexc(flower)
    for _ in range(26):
        x, y = int(7 + r.gauss(0, 2.6)), int(5 + r.gauss(0, 2.2))
        if 0 <= x < 16 and 0 <= y < 16:
            img.putpixel((x, y), shade(fc, 0.85 + r.random() * 0.3))
    return img


def block_textures():
    T = {}
    T["skyblossom_log"] = log_side("C9A4A8", 1)
    T["skyblossom_log_top"] = log_top("C9A4A8", "F3D7C9", 2)
    T["stripped_skyblossom_log"] = log_side("F2C6CF", 3)
    T["stripped_skyblossom_log_top"] = log_top("F2C6CF", "F9E3DA", 4)
    T["skyblossom_planks"] = planks("F4D3C4", 5)
    T["skyblossom_leaves"] = leaves("F6A9C8", 6, "FFE6F0")
    T["skyblossom_sapling"] = cross_plant("8A6A5A", "F6A9C8", 7)
    T["skyblossom_trapdoor"] = planks("EDC7B8", 8)
    T["skyblossom_door_top"] = planks("EDC7B8", 9)
    T["skyblossom_door_bottom"] = planks("E4BBAA", 10)
    T["cloud_block"] = noise_tex("F7F9FC", 0.04, 11)
    cp = noise_tex("FFFFFF", 0.03, 12)
    for y in range(16):
        for x in range(16):
            if (x - 8) ** 2 + (y - 8) ** 2 > 40:
                cp.putpixel((x, y), hexc("DCE8F6"))
    T["cumulus_puff"] = cp
    vent = noise_tex("ECEAE4", 0.05, 13)
    for y in range(2, 14):
        for x in range(2, 14):
            if x % 3 == 0:
                vent.putpixel((x, y), hexc("5F7F95"))
    T["updraft_vent_top"] = vent
    T["updraft_vent_side"] = bricks("ECEAE4", "C9C5BC", 14)
    chime = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for x in (4, 7, 10, 13):
        for y in range(1, 6 + (x % 4) * 2):
            chime.putpixel((x - 1, y + 2), hexc("A9D8F0"))
    for x in range(2, 15):
        chime.putpixel((x, 1), hexc("8C6A55"))
    chime.putpixel((8, 0), hexc("8C6A55"))
    T["windchime"] = chime
    calc = noise_tex("ECEAE4", 0.06, 15)
    T["zephyrite_ore"] = ore(calc, "9EE7F7", 16)
    T["sunglass"] = glass("C9822E", 120, "F2A63A", 17)
    T["scorched_sand"] = noise_tex("D4793E", 0.12, 18)
    qs = noise_tex("DBC98E", 0.08, 19)
    for y in range(16):
        for x in range(16):
            if int(math.sin(x * 0.7 + y * 0.4) * 3) == 0:
                qs.putpixel((x, y), hexc("C8B47A"))
    T["quicksand"] = qs
    mirror = noise_tex("C8964A", 0.05, 20)
    for i in range(16):
        mirror.putpixel((i, i), hexc("FFF0C0"))
        if i < 15:
            mirror.putpixel((i + 1, i), hexc("F0D090"))
    T["mirror_block"] = mirror
    T["sunwright_bricks"] = bricks("E3C98F", "B89A5C", 21, "F5C542")
    ch = bricks("E3C98F", "B89A5C", 22)
    for y in range(4, 12):
        for x in range(4, 12):
            if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 12:
                ch.putpixel((x, y), hexc("F5C542"))
    T["chiseled_sunwright_bricks"] = ch
    T["sunstone_ore"] = ore(noise_tex("E0CC95", 0.07, 23), "FF8A1E", 24)
    T["sun_mirror"] = mirror.copy()
    T["sun_mirror_stand"] = noise_tex("8A5A2A", 0.08, 25)
    for k, v in T.items():
        save(v, "block/" + k)
    return T


# ------------------------------------------------------------------------------------------------ item textures

MASKS = {
    "sword": ["...............X", "..............XX", ".............XX.", "............XX..", "...........XX...", "..........XX....",
              ".........XX.....", "....H...XX......", ".....H.XX.......", "......HX........", ".....SHH........", "....S..H........",
              "...S............", "..S.............", ".S..............", "................"],
    "pickaxe": ["................", "...XXXXXXX......", "..X.......XX....", ".........S..X...", "........S....X..", ".......S......X.",
                "......S.......X.", ".....S..........", "....S...........", "...S............", "..S.............", ".S..............",
                "................", "................", "................", "................"],
    "axe": ["................", "......XXX.......", ".....XXXXX......", ".....XXXXS......", "......XXS.......", ".......S........",
            "......S.........", ".....S..........", "....S...........", "...S............", "..S.............", ".S..............",
            "................", "................", "................", "................"],
    "shovel": ["................", "..........XXX...", ".........XXXXX..", "..........XXXX..", ".........S.XX...", "........S.......",
               ".......S........", "......S.........", ".....S..........", "....S...........", "...S............", "..S.............",
               "................", "................", "................", "................"],
    "hoe": ["................", "....XXXXXX......", "...X....XS......", "........S.......", ".......S........", "......S.........",
            ".....S..........", "....S...........", "...S............", "..S.............", ".S..............", "................",
            "................", "................", "................", "................"],
    "helmet": ["................", "................", "................", "...XXXXXXXXXX...", "..XXXXXXXXXXXX..", "..XX........XX..",
               "..XX........XX..", "..XX........XX..", "................", "................", "................", "................",
               "................", "................", "................", "................"],
    "chestplate": ["................", "..XXX......XXX..", "..XXXX....XXXX..", "..XXXXXXXXXXXX..", "...XXXXXXXXXX...", "....XXXXXXXX....",
                   "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....", "................",
                   "................", "................", "................", "................"],
    "leggings": ["................", "...XXXXXXXXXX...", "...XXXXXXXXXX...", "...XXXX..XXXX...", "...XXX....XXX...", "...XXX....XXX...",
                 "...XXX....XXX...", "...XXX....XXX...", "...XXX....XXX...", "...XXX....XXX...", "................", "................",
                 "................", "................", "................", "................"],
    "boots": ["................", "................", "................", "................", "................", "...XXX....XXX...",
              "...XXX....XXX...", "...XXX....XXX...", "..XXXX...XXXX...", ".XXXXX..XXXXX...", ".XXXXX..XXXXX...", "................",
              "................", "................", "................", "................"],
    "gem": ["................", "................", "................", "......XXXX......", ".....XXXXXX.....", "....XXXXXXXX....",
            "...XXXXXXXXXX...", "....XXXXXXXX....", ".....XXXXXX.....", "......XXXX......", ".......XX.......", "................",
            "................", "................", "................", "................"],
    "feather": ["............XX..", "...........XXX..", "..........XXXX..", ".........XXXX...", "........XXXX....", ".......XXXX.....",
                "......XXXX......", ".....XXXX.......", "....XXXX........", "...SXX..........", "..S.............", ".S..............",
                "................", "................", "................", "................"],
    "wings": ["................", ".XXXX......XXXX.", "XXXXXX....XXXXXX", "XXXXXXX..XXXXXXX", ".XXXXXXSSXXXXXX.", "..XXXXXSSXXXXX..",
              "...XXXX..XXXX...", "...XXX....XXX...", "....XX....XX....", "....X......X....", "................", "................",
              "................", "................", "................", "................"],
    "bread": ["................", "................", "................", "................", "....XXXXXXXX....", "...XXXXXXXXXX...",
              "..XXXXXXXXXXXX..", "..XXXXXXXXXXXX..", "..XXXXXXXXXXXX..", "...XXXXXXXXXX...", "................", "................",
              "................", "................", "................", "................"],
    "key": ["................", "................", "....XXX.........", "...X...X........", "...X...X........", "....XXX.........",
            ".....S..........", "......S.........", ".......S........", "........S.S.....", ".........S.S....", "..........S.....",
            "................", "................", "................", "................"],
    "rod": ["..............XX", ".............X.X", "............S..X", "...........S...X", "..........S....X", ".........S.....X",
            "........S......X", ".......S.......X", "......S........X", ".....S.........X", "....S..........X", "...S.........XXX",
            "..S.........X.X.", ".S...........X..", "................", "................"],
    "lens": ["................", "................", ".....XXXXXX.....", "....X......X....", "...X..HHHH..X...", "...X.H....H.X...",
             "...X.H....H.X...", "...X..HHHH..X...", "....X......X....", ".....XXXXXX.....", "......SS........", "......SS........",
             "......SS........", "................", "................", "................"],
    "bottle": ["................", "......SSSS......", ".......SS.......", ".......SS.......", "......XXXX......", ".....XXXXXX.....",
               "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....", "....XXXXXXXX....", ".....XXXXXX.....", "................",
               "................", "................", "................", "................"],
    "tooth": ["................", "................", "......XXX.......", "......XXXX......", ".......XXX......", ".......XXX......",
              "........XX......", "........XX......", ".........X......", ".........X......", "................", "................",
              "................", "................", "................", "................"],
    "spear": ["..............XX", ".............XXX", "............XX..", "...........S....", "..........S.....", ".........S......",
              "........S.......", ".......S........", "......S.........", ".....S..........", "....S...........", "...S............",
              "..S.............", ".S..............", "................", "................"],
    "heart": ["................", "................", "...XXX...XXX....", "..XXXXX.XXXXX...", "..XXXXXXXXXXX...", "..XXXXXXXXXXX...",
              "...XXXXXXXXX....", "....XXXXXXX.....", ".....XXXXX......", "......XXX.......", ".......X........", "................",
              "................", "................", "................", "................"],
    "stone": ["................", "................", "................", ".....XXXXX......", "....XXXXXXX.....", "...XXXXXXXXX....",
              "...XXXXXXXXX....", "...XXXXXXXXX....", "....XXXXXXX.....", ".....XXXXX......", "................", "................",
              "................", "................", "................", "................"],
    "egg": ["................", "................", "......XXXX......", ".....XXXXXX.....", "....XXXXXXXX....", "....XXXXXXXX....",
            "...XXXXXXXXXX...", "...XXXXXXXXXX...", "...XXXXXXXXXX...", "...XXXXXXXXXX...", "....XXXXXXXX....", ".....XXXXXX.....",
            "................", "................", "................", "................"],
}


def item_icon(mask, main, stick="6B4A2E", hilt=None, seed=0, spots=None):
    r = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    m, s = hexc(main), hexc(stick)
    h = hexc(hilt) if hilt else shade(m, 0.6)
    rows = MASKS[mask]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == "X":
                img.putpixel((x, y), shade(m, 0.85 + 0.3 * r.random() + (0.15 if (x + y) % 5 == 0 else 0)))
            elif ch == "S":
                img.putpixel((x, y), s)
            elif ch == "H":
                img.putpixel((x, y), h)
    if spots:
        sc = hexc(spots)
        pts = [(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch == "X"]
        for p in r.sample(pts, min(len(pts), 6)):
            img.putpixel(p, sc)
    # outline
    out = img.copy()
    for y in range(16):
        for x in range(16):
            if img.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and img.getpixel((x + dx, y + dy))[3] > 0
                                                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), (40, 30, 40, 255))
    return out


ITEMS = {
    # petalwind
    "zephyr_feather": ("feather", "D9F2FF", None, "FFFFFF"), "zephyrite": ("gem", "9EE7F7", None, "FFFFFF"),
    "blossom_seal": ("stone", "F6A9C8", None, "FFF0A0"), "zephyr_key": ("key", "9EE7F7", "6FB7D0", None),
    "cloudbread": ("bread", "F8EEDD", None, "FFFFFF"), "petal_glider": ("wings", "F6A9C8", "8A6A5A", "FFE6F0"),
    "skyhook": ("rod", "C9A4A8", "8A6A5A", None),
    "zephyrite_sword": ("sword", "9EE7F7", None, None), "zephyrite_pickaxe": ("pickaxe", "9EE7F7", None, None),
    "zephyrite_axe": ("axe", "9EE7F7", None, None), "zephyrite_shovel": ("shovel", "9EE7F7", None, None), "zephyrite_hoe": ("hoe", "9EE7F7", None, None),
    "galeweave_hood": ("helmet", "F3E9F7", None, "F6A9C8"), "galeweave_robe": ("chestplate", "F3E9F7", None, "F6A9C8"),
    "galeweave_leggings": ("leggings", "F3E9F7", None, "F6A9C8"), "galeweave_boots": ("boots", "F3E9F7", None, "F6A9C8"),
    "petalstep_sandals": ("boots", "F6A9C8", None, "FFFFFF"), "kaze_katana": ("sword", "E8F0F8", None, "F6A9C8"),
    "zephyr_wings": ("wings", "BFE6FF", "8AA0B8", "FFFFFF"), "gale_heart": ("heart", "9FD0F5", None, "FFFFFF"),
    # glasswaste
    "sunglass_shard": ("gem", "F2A63A", None, "FFF0B0"), "sunstone": ("gem", "FF8A1E", None, "FFD080"),
    "eel_tooth": ("tooth", "F2EEDC", None, None), "sandswimmer_scale": ("stone", "D8B878", None, "F2A63A"),
    "burning_glass": ("lens", "C9964A", "8A5A2A", None), "heliograph_lens": ("lens", "F5C542", "8A5A2A", None),
    "cactus_water": ("bottle", "7FC46A", "B0C8D0", None), "sunwright_visor": ("helmet", "F5C542", None, "9EE7F7"),
    "eel_tooth_spear": ("spear", "F2EEDC", "8A6A5A", None),
    "sunstone_sword": ("sword", "FF8A1E", None, None), "sunstone_pickaxe": ("pickaxe", "FF8A1E", None, None),
    "sunstone_axe": ("axe", "FF8A1E", None, None), "sunstone_shovel": ("shovel", "FF8A1E", None, None), "sunstone_hoe": ("hoe", "FF8A1E", None, None),
    "sunwright_helmet": ("helmet", "F5C542", None, "F2A63A"), "sunwright_chestplate": ("chestplate", "F5C542", None, "F2A63A"),
    "sunwright_leggings": ("leggings", "F5C542", None, "F2A63A"), "sunwright_boots": ("boots", "F5C542", None, "F2A63A"),
    "sunwright_plate": ("chestplate", "FFD34A", None, "9EE7F7"), "glassfang": ("sword", "F2A63A", None, "FFF0B0"),
    "gizzard_stone": ("stone", "E8C080", None, "FF8A1E"),
    # signature gear stubs
    "challengers_cleaver": ("axe", "C8C8D8", None, "F5C542"), "phasewalker_greaves": ("leggings", "5A4A7A", None, "B080FF"),
    "stormcrest_helm": ("helmet", "4A5A7A", None, "9FD0F5"),
}
HANDHELD = {k for k, v in ITEMS.items() if v[0] in ("sword", "pickaxe", "axe", "shovel", "hoe", "rod", "spear")}
EGGS = {"gale_kite": ("F6A9C8", "FFFFFF"), "blossom_breeze": ("BFE6FF", "F6A9C8"), "cumulus": ("FFFFFF", "BFD8F2"),
        "skywhale": ("9FB8E0", "F6A9C8"), "glass_scarab": ("F2A63A", "F5C542"), "sandswimmer": ("D8B878", "F2EEDC"),
        "sunwright_revenant": ("F5C542", "E8DCC0"), "sun_mote": ("FFF0B0", "F2A63A"), "windwright": ("7FB8E0", "F6A9C8"),
        "dune_factor": ("D8B060", "8A5A2A")}


def item_textures():
    for i, (name, (mask, main, stick, spots)) in enumerate(ITEMS.items()):
        save(item_icon(mask, main, stick or "6B4A2E", None, i, spots), "item/" + name)
    for i, (name, (a, b)) in enumerate(EGGS.items()):
        save(item_icon("egg", a, "000000", None, 100 + i, b), "item/" + name + "_spawn_egg")
    save(item_icon("wings", "EDC7B8", "8A6A5A", None, 300, None), "item/skyblossom_door")


# ------------------------------------------------------------------------------------------------ armor layers

def armor_layer(color, accent, seed, legs=False):
    r = random.Random(seed)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    c, a = hexc(color), hexc(accent)
    regions = [(0, 16, 16, 32), (16, 16, 40, 32), (40, 16, 56, 32)] if legs else [(0, 0, 32, 16), (16, 16, 40, 32), (40, 16, 56, 32), (0, 16, 16, 32)]
    for (x0, y0, x1, y1) in regions:
        for y in range(y0, y1):
            for x in range(x0, x1):
                col = shade(c, 0.85 + r.random() * 0.25)
                if (x + y) % 7 == 0:
                    col = a
                img.putpixel((x, y), col)
    if not legs:  # keep the face area of the helmet open
        for y in range(8, 16):
            for x in range(8, 16):
                if 10 <= y <= 13:
                    img.putpixel((x, y), (0, 0, 0, 0))
    return img


def wings_layer(color, accent, seed):
    r = random.Random(seed)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    c, a = hexc(color), hexc(accent)
    for y in range(0, 22):
        for x in range(22, 46):
            col = shade(c, 0.85 + r.random() * 0.25)
            if r.random() < 0.08:
                col = a
            img.putpixel((x, y), col)
    return img


def equipment():
    sets = {"galeweave": ("F3E9F7", "F6A9C8"), "petalstep": ("F6A9C8", "FFFFFF"), "sunwright": ("F5C542", "F2A63A"),
            "sunwright_visor": ("F5C542", "9EE7F7"), "sunwright_plate": ("FFD34A", "9EE7F7"), "phasewalker": ("5A4A7A", "B080FF"),
            "stormcrest": ("4A5A7A", "9FD0F5")}
    for i, (name, (c, a)) in enumerate(sets.items()):
        wj(os.path.join(A, "equipment", name + ".json"), {"layers": {"humanoid": [{"texture": NS + ":" + name}],
                                                                       "humanoid_leggings": [{"texture": NS + ":" + name}]}})
        save(armor_layer(c, a, i), "entity/equipment/humanoid/" + name)
        save(armor_layer(c, a, i + 50, True), "entity/equipment/humanoid_leggings/" + name)
    for i, (name, (c, a)) in enumerate({"petal_glider": ("F6A9C8", "FFE6F0"), "zephyr_wings": ("BFE6FF", "FFFFFF")}.items()):
        wj(os.path.join(A, "equipment", name + ".json"), {"layers": {"wings": [{"texture": NS + ":" + name}]}})
        save(wings_layer(c, a, i), "entity/equipment/wings/" + name)


# ------------------------------------------------------------------------------------------------ block models/states

def repoint(obj, pairs):
    s = json.dumps(obj)
    for a, b in pairs:
        s = s.replace(a, b)
    return json.loads(s)


def copy_vanilla_block(vname, newname, pairs):
    """Copies a vanilla blockstate, its models and item definition, re-pointed to our ids."""
    bs = rj(os.path.join(MC, "assets/minecraft/blockstates", vname + ".json"))
    models = set(re.findall(r'"model": "minecraft:block/([a-z0-9_]+)"', json.dumps(bs)))
    for m in models:
        mj = rj(os.path.join(MC, "assets/minecraft/models/block", m + ".json"))
        newm = m
        for a, b in pairs:
            newm = newm.replace(a.split(":")[-1].split("/")[-1], b.split(":")[-1].split("/")[-1])
        wj(os.path.join(A, "models/block", newm + ".json"), repoint(mj, pairs))
    bs2 = repoint(bs, pairs)
    wj(os.path.join(A, "blockstates", newname + ".json"), bs2)
    ip = os.path.join(MC, "assets/minecraft/items", vname + ".json")
    if os.path.exists(ip):
        item = rj(ip)
        for kind, m in re.findall(r'"minecraft:(block|item)/([a-z0-9_]+)"', json.dumps(item)):
            src = os.path.join(MC, "assets/minecraft/models", kind, m + ".json")
            if not os.path.exists(src):
                continue
            newm = m
            for a, b in pairs:
                newm = newm.replace(a.split(":")[-1].split("/")[-1], b.split(":")[-1].split("/")[-1])
            wj(os.path.join(A, "models", kind, newm + ".json"), repoint(rj(src), pairs))
        wj(os.path.join(A, "items", newname + ".json"), repoint(item, pairs))


def simple_block(name, model):
    wj(os.path.join(A, "blockstates", name + ".json"), {"variants": {"": {"model": NS + ":block/" + name}}})
    wj(os.path.join(A, "models/block", name + ".json"), model)
    wj(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":block/" + name}})


def cube_all(name, tex=None, translucent=False):
    t = NS + ":block/" + (tex or name)
    return {"parent": "minecraft:block/cube_all", "textures": {"all": {"sprite": t, "force_translucent": True} if translucent else t}}


def block_assets():
    cherry = [("minecraft:block/cherry_", NS + ":block/skyblossom_"), ("minecraft:block/stripped_cherry_", NS + ":block/stripped_skyblossom_"),
              ("minecraft:item/cherry_", NS + ":item/skyblossom_")]
    for v in ["cherry_log", "stripped_cherry_log", "cherry_wood", "stripped_cherry_wood", "cherry_planks", "cherry_stairs", "cherry_slab",
              "cherry_fence", "cherry_fence_gate", "cherry_door", "cherry_trapdoor", "cherry_button", "cherry_pressure_plate", "cherry_leaves",
              "cherry_sapling"]:
        copy_vanilla_block(v, v.replace("cherry", "skyblossom"), cherry)
    bricks_p = [("minecraft:block/stone_bricks", NS + ":block/sunwright_bricks"), ("minecraft:block/stone_brick_", NS + ":block/sunwright_brick_")]
    for v in ["stone_brick_stairs", "stone_brick_slab", "stone_brick_wall"]:
        copy_vanilla_block(v, v.replace("stone_brick", "sunwright_brick"), bricks_p)
    for n in ["sunwright_bricks", "chiseled_sunwright_bricks", "zephyrite_ore", "sunstone_ore", "scorched_sand", "quicksand", "mirror_block",
              "cloud_block", "cumulus_puff"]:
        simple_block(n, cube_all(n))
    # sunglass with lit/unlit variants (same look; it emits light when lit)
    wj(os.path.join(A, "blockstates", "sunglass.json"), {"variants": {"lit=false": {"model": NS + ":block/sunglass"}, "lit=true": {"model": NS + ":block/sunglass"}}})
    wj(os.path.join(A, "models/block", "sunglass.json"), cube_all("sunglass", translucent=True))
    wj(os.path.join(A, "items", "sunglass.json"), {"model": {"type": "minecraft:model", "model": NS + ":block/sunglass"}})
    simple_block("updraft_vent", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": NS + ":block/updraft_vent_top",
                 "bottom": NS + ":block/updraft_vent_side", "side": NS + ":block/updraft_vent_side"}})
    simple_block("windchime", {"parent": "minecraft:block/cross", "textures": {"cross": NS + ":block/windchime"}})
    wj(os.path.join(A, "items", "windchime.json"), {"model": {"type": "minecraft:model", "model": NS + ":item/windchime"}})
    wj(os.path.join(A, "models/item", "windchime.json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":block/windchime"}})
    # sun mirror: a stand with a mirror panel, straight and diagonal, rotated in 90 degree steps
    def mirror_model(angle):
        el = [{"from": [6, 0, 6], "to": [10, 8, 10], "faces": {d: {"texture": "#stand"} for d in ["north", "south", "east", "west", "up", "down"]}},
              {"from": [1, 7, 7], "to": [15, 16, 9], "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle},
               "faces": {d: {"texture": "#mirror"} for d in ["north", "south", "east", "west", "up", "down"]}}]
        return {"parent": "minecraft:block/block", "textures": {"particle": NS + ":block/sun_mirror", "stand": NS + ":block/sun_mirror_stand",
                                                               "mirror": NS + ":block/sun_mirror"}, "elements": el}
    wj(os.path.join(A, "models/block", "sun_mirror.json"), mirror_model(0))
    wj(os.path.join(A, "models/block", "sun_mirror_diagonal.json"), mirror_model(45))
    variants = {}
    for aim in range(8):
        variants["aim=%d" % aim] = {"model": NS + ":block/sun_mirror" + ("_diagonal" if aim % 2 else ""), "y": (aim // 2) * 90}
    wj(os.path.join(A, "blockstates", "sun_mirror.json"), {"variants": variants})
    wj(os.path.join(A, "items", "sun_mirror.json"), {"model": {"type": "minecraft:model", "model": NS + ":block/sun_mirror"}})


def item_assets():
    for name in ITEMS:
        parent = "minecraft:item/handheld" if name in HANDHELD else "minecraft:item/generated"
        wj(os.path.join(A, "models/item", name + ".json"), {"parent": parent, "textures": {"layer0": NS + ":item/" + name}})
        wj(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/" + name}})
    for name in EGGS:
        n = name + "_spawn_egg"
        wj(os.path.join(A, "models/item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + n}})
        wj(os.path.join(A, "items", n + ".json"), {"model": {"type": "minecraft:model", "model": NS + ":item/" + n}})


# ------------------------------------------------------------------------------------------------ lang

NAMES = {
    "skyblossom_wood": "Skyblossom Wood", "cloud_block": "Cloud Block", "cumulus_puff": "Cumulus Puff", "updraft_vent": "Updraft Vent",
    "galeweave_hood": "Galeweave Hood", "galeweave_robe": "Galeweave Robe", "kaze_katana": "Kaze Katana", "zephyr_wings": "Zephyr Wings",
    "eel_tooth_spear": "Eel-Tooth Spear", "sunwright_visor": "Sunwright Visor", "challengers_cleaver": "Challenger's Cleaver",
    "cactus_water": "Cactus Water", "petal_glider": "Petal Glider", "sun_mirror": "Sun Mirror",
}


def title(s):
    return " ".join(w.capitalize() for w in s.split("_"))


def lang(blocks, items, entities):
    L = {}
    for b in blocks:
        L["block.elsewhere." + b] = NAMES.get(b, title(b))
    for i in items:
        L["item.elsewhere." + i] = NAMES.get(i, title(i))
    for e in entities:
        L["entity.elsewhere." + e] = title(e)
    L["entity.elsewhere.kazehana"] = "Kazehana, the Gale Ronin"
    L["entity.elsewhere.tempestra"] = "Tempestra, Eye of the Gale"
    L["entity.elsewhere.heliodor"] = "Heliodor, the Sunwright Engine"
    L["entity.elsewhere.ankhara"] = "Ankhara, the Sunpit Devourer"
    L["itemGroup.elsewhere.petalwind"] = "Elsewhere: Petalwind Isles"
    L["itemGroup.elsewhere.glasswaste"] = "Elsewhere: The Glasswaste"
    L["item.elsewhere.petal_glider.no_rockets"] = "The Petal Glider is too light for rockets."
    L["dimension.elsewhere.petalwind.fell"] = "You fall through the clouds into the Overworld sky..."
    L["set.elsewhere.galeweave.title"] = "Galeweave set:"
    L["set.elsewhere.galeweave.jump"] = " Jump again in mid-air"
    L["set.elsewhere.galeweave.fall"] = " -25% fall damage"
    L["set.elsewhere.sunwright.title"] = "Sunwright set:"
    L["set.elsewhere.sunwright.glare"] = " Immune to Glare"
    L["hud.elsewhere.heat"] = "Heat"
    L["death.attack.elsewhere.pg_boss_attack"] = "%1$s was cut down by %2$s"
    L["death.attack.elsewhere.pg_boss_attack.player"] = "%1$s was cut down by %2$s"
    wj(os.path.join(A, "lang", "en_us.json"), L)


# ------------------------------------------------------------------------------------------------ data

def loot_self(name):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": NS + ":" + name}],
                                                 "conditions": [{"condition": "minecraft:survives_explosion"}]}]}


def loot_ore(name, drop):
    return {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": NS + ":" + name, "conditions": [{"condition": "minecraft:match_tool", "predicate": {
            "predicates": {"minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
        {"type": "minecraft:item", "name": drop, "functions": [{"function": "minecraft:explosion_decay"}]}]}]}]}


def mob_loot(entries):
    pools = []
    for name, lo, hi, chance in entries:
        e = {"type": "minecraft:item", "name": name, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}
        pool = {"rolls": 1, "entries": [e]}
        if chance < 1:
            pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
        pools.append(pool)
    return {"type": "minecraft:entity", "pools": pools}


def data(blocks):
    for b in blocks:
        p = os.path.join(D, "loot_table/blocks", b + ".json")
        if b == "zephyrite_ore":
            wj(p, loot_ore(b, NS + ":zephyrite"))
        elif b == "sunstone_ore":
            wj(p, loot_ore(b, NS + ":sunstone"))
        elif b in ("skyblossom_door", "skyblossom_leaves", "skyblossom_slab"):
            v = rj(os.path.join(MC, "data/minecraft/loot_table/blocks", b.replace("skyblossom", "cherry") + ".json"))
            wj(p, repoint(v, [("minecraft:cherry_", NS + ":skyblossom_")]))
        elif b == "sun_mirror":
            continue
        else:
            wj(p, loot_self(b))
    if os.path.exists(os.path.join(MC, "data/minecraft/loot_table/blocks/stone_brick_slab.json")):
        v = rj(os.path.join(MC, "data/minecraft/loot_table/blocks/stone_brick_slab.json"))
        wj(os.path.join(D, "loot_table/blocks", "sunwright_brick_slab.json"), repoint(v, [("minecraft:stone_brick_slab", NS + ":sunwright_brick_slab")]))
    E = lambda n: NS + ":" + n
    mobs = {
        "gale_kite": [(E("zephyr_feather"), 1, 1, 0.5), ("minecraft:string", 0, 2, 1), ("minecraft:pink_petals", 0, 2, 1)],
        "blossom_breeze": [("minecraft:breeze_rod", 1, 2, 1), (E("zephyr_feather"), 1, 1, 0.25), (E("zephyr_key"), 1, 1, 0.08)],
        "cumulus": [("minecraft:white_wool", 0, 1, 1), (E("cloudbread"), 1, 1, 0.05)],
        "skywhale": [],
        "glass_scarab": [(E("sunglass_shard"), 1, 1, 0.2), ("minecraft:gold_nugget", 0, 3, 1)],
        "sandswimmer": [(E("eel_tooth"), 1, 2, 1), ("minecraft:sand", 0, 2, 1)],
        "sunwright_revenant": [("minecraft:gold_nugget", 1, 4, 1), (E("sunwright_visor"), 1, 1, 0.02)],
        "sun_mote": [("minecraft:blaze_powder", 0, 2, 1), (E("sunglass_shard"), 1, 1, 0.3)],
    }
    for m, entries in mobs.items():
        wj(os.path.join(D, "loot_table/entities", m + ".json"), mob_loot(entries))
    wj(os.path.join(D, "damage_type", "pg_boss_attack.json"), {"exhaustion": 0.1, "message_id": "elsewhere.pg_boss_attack", "scaling": "never"})
    # tags
    T = lambda p, vals: wj(os.path.join(RES, "data", p + ".json"), {"replace": False, "values": vals})
    wood = ["skyblossom_log", "stripped_skyblossom_log", "skyblossom_wood", "stripped_skyblossom_wood", "skyblossom_planks", "skyblossom_stairs",
            "skyblossom_slab", "skyblossom_fence", "skyblossom_fence_gate", "skyblossom_door", "skyblossom_trapdoor", "skyblossom_button",
            "skyblossom_pressure_plate"]
    T("minecraft/tags/block/mineable/axe", [E(b) for b in wood + ["windchime"]])
    T("minecraft/tags/block/mineable/pickaxe", [E(b) for b in ["updraft_vent", "zephyrite_ore", "mirror_block", "sunwright_bricks",
        "sunwright_brick_stairs", "sunwright_brick_slab", "sunwright_brick_wall", "chiseled_sunwright_bricks", "sunstone_ore"]])
    T("minecraft/tags/block/mineable/shovel", [E(b) for b in ["scorched_sand", "quicksand", "cloud_block", "cumulus_puff"]])
    T("minecraft/tags/block/mineable/hoe", [E("skyblossom_leaves")])
    T("minecraft/tags/block/needs_diamond_tool", [E("sunstone_ore")])
    T("minecraft/tags/block/needs_iron_tool", [E("zephyrite_ore")])
    T("minecraft/tags/block/logs", [E(b) for b in wood[:4]])
    T("minecraft/tags/block/logs_that_burn", [E(b) for b in wood[:4]])
    T("minecraft/tags/block/leaves", [E("skyblossom_leaves")])
    T("minecraft/tags/block/saplings", [E("skyblossom_sapling")])
    T("minecraft/tags/block/planks", [E("skyblossom_planks")])
    T("minecraft/tags/block/wooden_stairs", [E("skyblossom_stairs")])
    T("minecraft/tags/block/wooden_slabs", [E("skyblossom_slab")])
    T("minecraft/tags/block/wooden_fences", [E("skyblossom_fence")])
    T("minecraft/tags/block/fence_gates", [E("skyblossom_fence_gate")])
    T("minecraft/tags/block/wooden_doors", [E("skyblossom_door")])
    T("minecraft/tags/block/wooden_trapdoors", [E("skyblossom_trapdoor")])
    T("minecraft/tags/block/wooden_buttons", [E("skyblossom_button")])
    T("minecraft/tags/block/wooden_pressure_plates", [E("skyblossom_pressure_plate")])
    T("minecraft/tags/block/walls", [E("sunwright_brick_wall")])
    T("minecraft/tags/item/planks", [E("skyblossom_planks")])
    T("minecraft/tags/item/logs", [E(b) for b in wood[:4]])
    T("minecraft/tags/item/logs_that_burn", [E(b) for b in wood[:4]])
    T("elsewhere/tags/block/glasswaste_glare_blocks", ["minecraft:glass", "minecraft:tinted_glass", "#c:glass_blocks", E("sunglass")]
      if False else ["minecraft:glass", "minecraft:tinted_glass", "minecraft:orange_stained_glass", "minecraft:yellow_stained_glass",
                     "minecraft:white_stained_glass", E("sunglass")])
    T("elsewhere/tags/item/zephyrite_tool_materials", [E("zephyrite")])
    T("elsewhere/tags/item/sunstone_tool_materials", [E("sunstone")])
    T("elsewhere/tags/item/repairs_galeweave_armor", [E("zephyrite"), E("zephyr_feather")])
    T("elsewhere/tags/item/repairs_sunwright_armor", [E("sunstone"), "minecraft:gold_ingot"])
    T("minecraft/tags/item/swords", [E("zephyrite_sword"), E("sunstone_sword"), E("kaze_katana"), E("glassfang"), E("challengers_cleaver")])
    T("minecraft/tags/item/pickaxes", [E("zephyrite_pickaxe"), E("sunstone_pickaxe")])
    T("minecraft/tags/item/axes", [E("zephyrite_axe"), E("sunstone_axe")])
    T("minecraft/tags/item/shovels", [E("zephyrite_shovel"), E("sunstone_shovel")])
    T("minecraft/tags/item/hoes", [E("zephyrite_hoe"), E("sunstone_hoe")])
    T("minecraft/tags/item/head_armor", [E("galeweave_hood"), E("sunwright_helmet"), E("sunwright_visor"), E("stormcrest_helm")])
    T("minecraft/tags/item/chest_armor", [E("galeweave_robe"), E("sunwright_chestplate"), E("sunwright_plate")])
    T("minecraft/tags/item/leg_armor", [E("galeweave_leggings"), E("sunwright_leggings"), E("phasewalker_greaves")])
    T("minecraft/tags/item/foot_armor", [E("galeweave_boots"), E("sunwright_boots"), E("petalstep_sandals")])
    recipes()
    attunement()


def shaped(name, pattern, key, result, count=1):
    wj(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                                                   "key": {k: v for k, v in key.items()}, "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    wj(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
                                                   "result": {"id": result, "count": count}})


def recipes():
    E = lambda n: NS + ":" + n
    shaped("petal_glider", ["F F", "PSP", "F F"], {"F": "minecraft:feather", "P": "minecraft:phantom_membrane", "S": "minecraft:string"}, E("petal_glider"))
    shapeless("cactus_water", ["minecraft:glass_bottle", "minecraft:cactus", "minecraft:cactus_flower"], E("cactus_water"))
    shapeless("skyblossom_planks", ["#" + NS + ":skyblossom_logs"] if False else [E("skyblossom_log")], E("skyblossom_planks"), 4)
    shaped("skyblossom_wood", ["LL", "LL"], {"L": E("skyblossom_log")}, E("skyblossom_wood"), 3)
    shaped("sunwright_bricks", ["SS", "SG"], {"S": "minecraft:sandstone", "G": "minecraft:gold_nugget"}, E("sunwright_bricks"), 3)
    shaped("sunwright_brick_stairs", ["B  ", "BB ", "BBB"], {"B": E("sunwright_bricks")}, E("sunwright_brick_stairs"), 4)
    shaped("sunwright_brick_slab", ["BBB"], {"B": E("sunwright_bricks")}, E("sunwright_brick_slab"), 6)
    shaped("sunwright_brick_wall", ["BBB", "BBB"], {"B": E("sunwright_bricks")}, E("sunwright_brick_wall"), 6)
    shaped("skyhook", ["  F", " RF", "R  "] if False else ["ZZZ", " R ", "   "], {"Z": E("zephyr_feather"), "R": "minecraft:fishing_rod"}, E("skyhook"))
    shaped("burning_glass", ["SSS", " G ", "   "], {"S": E("sunglass_shard"), "G": "minecraft:spyglass"}, E("burning_glass"))
    shaped("eel_tooth_spear", ["TTT", " S ", "   "], {"T": E("eel_tooth"), "S": "minecraft:iron_spear"}, E("eel_tooth_spear"))
    shaped("cloudbread", ["WCW"], {"W": "minecraft:wheat", "C": E("cumulus_puff")}, E("cloudbread"), 2)
    for mat, item in (("zephyrite", E("zephyrite")), ("sunstone", E("sunstone"))):
        shaped(mat + "_sword", ["M", "M", "S"], {"M": item, "S": "minecraft:stick"}, E(mat + "_sword"))
        shaped(mat + "_pickaxe", ["MMM", " S ", " S "], {"M": item, "S": "minecraft:stick"}, E(mat + "_pickaxe"))
        shaped(mat + "_axe", ["MM", "MS", " S"], {"M": item, "S": "minecraft:stick"}, E(mat + "_axe"))
        shaped(mat + "_shovel", ["M", "S", "S"], {"M": item, "S": "minecraft:stick"}, E(mat + "_shovel"))
        shaped(mat + "_hoe", ["MM", " S", " S"], {"M": item, "S": "minecraft:stick"}, E(mat + "_hoe"))
    for piece, pat in (("hood", ["MMM", "M M"]), ("robe", ["M M", "MMM", "MMM"]), ("leggings", ["MMM", "M M", "M M"]), ("boots", ["M M", "M M"])):
        shaped("galeweave_" + piece, pat, {"M": E("zephyrite")}, E("galeweave_" + piece))
    for piece, pat in (("helmet", ["MMM", "M M"]), ("chestplate", ["M M", "MMM", "MMM"]), ("leggings", ["MMM", "M M", "M M"]), ("boots", ["M M", "M M"])):
        shaped("sunwright_" + piece, pat, {"M": E("sunstone")}, E("sunwright_" + piece))


def attunement():
    wj(os.path.join(D, "attunement", "petalwind.json"), {"dimension": "elsewhere:petalwind", "ingredients": [
        "minecraft:feather", "minecraft:wind_charge", "minecraft:pink_petals", "minecraft:cherry_sapling", "minecraft:phantom_membrane"],
        "requires": "none", "notes": "F K F / P W P / S H S around a base Wayfinder (DIMENSIONS.md section 8)."})
    wj(os.path.join(D, "attunement", "glasswaste.json"), {"dimension": "elsewhere:glasswaste", "ingredients": [
        "minecraft:gold_ingot", "minecraft:chiseled_sandstone", "minecraft:tinted_glass", "minecraft:cactus", "minecraft:blaze_powder"],
        "requires": "none", "notes": "G S G / T W T / X Z X around a base Wayfinder (DIMENSIONS.md section 8)."})


# ------------------------------------------------------------------------------------------------ worldgen

def music(sound):
    return {"default": {"max_delay": 24000, "min_delay": 12000, "sound": sound}}


def spawns(monsters, creatures):
    def entries(lst):
        return [{"type": t, "count": {"type": "minecraft:uniform", "min_inclusive": a, "max_inclusive": b}, "weight": w} for t, w, a, b in lst]
    return {"argument": {"spawn_costs": {}, "spawns_by_category": {"monster": entries(monsters), "creature": entries(creatures)}}, "modifier": "overlay"}


def biome(sky, fog, water, foliage, grass, particle, prob, mus, monsters, creatures, features, temp=0.7, precip=True):
    attrs = {"minecraft:visual/sky_color": "#" + sky, "minecraft:visual/fog_color": "#" + fog, "minecraft:visual/water_fog_color": "#" + water,
             "minecraft:audio/background_music": music(mus),
             "minecraft:visual/ambient_particles": [{"particle": {"type": particle}, "probability": prob}],
             "minecraft:gameplay/natural_mob_spawns": spawns(monsters, creatures)}
    eff = {"water_color": "#" + water}
    if foliage:
        eff["foliage_color"] = "#" + foliage
    if grass:
        eff["grass_color"] = "#" + grass
    return {"attributes": attrs, "effects": eff, "carvers": [], "downfall": 0.4 if precip else 0.0, "has_precipitation": precip,
            "temperature": temp if precip else 2.0, "features": features}


def steps(ores=(), veg=(), surface=()):
    s = [[] for _ in range(11)]
    s[4] = list(surface)
    s[6] = list(ores)
    s[9] = list(veg)
    return s


def worldgen():
    W = os.path.join(D, "worldgen")
    E = lambda n: NS + ":" + n
    # features
    cherry = rj(os.path.join(MC, "data/minecraft/worldgen/feature/cherry.json"))
    wj(os.path.join(W, "feature", "skyblossom_tree.json"), repoint(cherry, [("minecraft:cherry_log", E("skyblossom_log")),
                                                                            ("minecraft:cherry_leaves", E("skyblossom_leaves"))]))
    wj(os.path.join(W, "placed_feature", "skyblossom_trees.json"), {"feature": E("skyblossom_tree"), "placement": [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"}, {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:would_survive", "state": E("skyblossom_sapling")}}]})
    for name, block, target, count, lo, hi in (("ore_zephyrite", "zephyrite_ore", "minecraft:stone_ore_replaceables", 10, 40, 250),
                                               ("ore_sunstone", "sunstone_ore", None, 8, 0, 120)):
        tgt = {"predicate_type": "minecraft:tag_match", "tag": target} if target else {"predicate_type": "minecraft:block_match", "block": "minecraft:sandstone"}
        wj(os.path.join(W, "feature", name + ".json"), {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": 6,
                                                        "targets": [{"state": E(block), "target": tgt}]})
        wj(os.path.join(W, "placed_feature", name + ".json"), {"feature": E(name), "placement": [{"type": "minecraft:count", "count": count},
            {"type": "minecraft:in_square"}, {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform",
             "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}}, {"type": "minecraft:biome"}]})
    wj(os.path.join(W, "feature", "updraft_vents.json"), {"type": "minecraft:simple_block", "to_place": {"id": E("updraft_vent")}})
    wj(os.path.join(W, "placed_feature", "updraft_vents.json"), {"feature": E("updraft_vents"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 6}, {"type": "minecraft:in_square"}, {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"},
        {"type": "minecraft:biome"}]})
    for arena, chance in (("petal_pagoda", 90), ("sunwright_ziggurat", 70), ("sunpit", 80)):
        wj(os.path.join(W, "feature", arena + ".json"), {"type": E(arena)})
        wj(os.path.join(W, "placed_feature", arena + ".json"), {"feature": E(arena), "placement": [
            {"type": "minecraft:rarity_filter", "chance": chance}, {"type": "minecraft:in_square"}, {"type": "minecraft:biome"}]})
    # biomes
    pw_mon = [(E("gale_kite"), 40, 1, 2), (E("blossom_breeze"), 20, 1, 1)]
    pw_cre = [(E("cumulus"), 20, 1, 3), (E("skywhale"), 4, 1, 1), (E("windwright"), 1, 1, 1)]
    veg = [E("skyblossom_trees"), "minecraft:flower_cherry", "minecraft:patch_grass_plain", E("updraft_vents")]
    ores = [E("ore_zephyrite")]
    B = os.path.join(W, "biome")
    wj(os.path.join(B, "blossom_crowns.json"), biome("F7C6D9", "FBD9E6", "8FD3F4", "F6A9C8", None, "minecraft:cherry_leaves", 0.01,
        "minecraft:music.overworld.cherry_grove", pw_mon, pw_cre, steps(ores, veg, [E("petal_pagoda")])))
    wj(os.path.join(B, "gale_steppes.json"), biome("9FD0F5", "D6ECFA", "7CC8F0", "8FCB6A", "8FCB6A", "minecraft:small_gust", 0.002,
        "minecraft:music.overworld.meadow", pw_mon, pw_cre, steps(ores, ["minecraft:patch_grass_plain", E("updraft_vents")])))
    wj(os.path.join(B, "hanging_gardens.json"), biome("BFD8F2", "E4F0FA", "6FC0E0", "6FAF5A", "6FAF5A", "minecraft:spore_blossom_air", 0.008,
        "minecraft:music_disc.creator_music_box", pw_mon, pw_cre, steps(ores, [E("skyblossom_trees"), "minecraft:patch_grass_plain"])))
    wj(os.path.join(B, "cloud_nine.json"), biome("D7C4F2", "E8DDF7", "B9A8E8", "7FBF6A", "7FBF6A", "minecraft:white_smoke", 0.004,
        "minecraft:music.overworld.flower_forest", pw_mon, pw_cre, steps(ores, ["minecraft:patch_grass_plain", E("updraft_vents")])))
    wj(os.path.join(B, "stormrim.json"), biome("7F93B8", "A9B6CC", "5C7FA8", "6E8F70", "6E8F70", "minecraft:electric_spark", 0.003,
        "minecraft:music_disc.creator", pw_mon, pw_cre, steps(ores, [E("updraft_vents")])))
    gw_mon = [(E("glass_scarab"), 60, 2, 4), (E("sandswimmer"), 25, 1, 1), (E("sunwright_revenant"), 25, 1, 2), (E("sun_mote"), 20, 1, 1)]
    dvege = ["minecraft:patch_dead_bush_2", "minecraft:patch_cactus_desert"]
    for name, sky, fog, water, part, prob, mus, surf in (
            ("ember_dunes", "FFF4D6", "F2C98A", "D9A441", "minecraft:dust_plume", 0.002, "minecraft:music.overworld.desert", [E("sunpit")]),
            ("glass_flats", "FFFFFF", "FFE9B0", "E8C46A", "minecraft:end_rod", 0.001, "minecraft:music_disc.relic", []),
            ("spirefields", "FFF0C2", "F7D9A0", "D9B060", "minecraft:electric_spark", 0.002, "minecraft:music_disc.relic", [E("sunwright_ziggurat")]),
            ("cinder_basin", "F0B080", "C2643A", "A0522D", "minecraft:ash", 0.02, "minecraft:music.nether.basalt_deltas", []),
            ("bonebed_hollows", "F9E3B5", "E8C08A", "C9A060", "minecraft:white_ash", 0.003, "minecraft:music.overworld.badlands", [])):
        wj(os.path.join(B, name + ".json"), biome(sky, fog, water, "C9B060", "C9B060", part, prob, mus, gw_mon, [(E("dune_factor"), 1, 1, 1)],
                                                  steps([E("ore_sunstone")], dvege, surf), precip=False))
    # noise parameters
    N = os.path.join(W, "noise")
    wj(os.path.join(N, "petalwind_temperature.json"), {"base_octave": -9, "octave_count": 3, "base_amplitude": 1.0})
    wj(os.path.join(N, "petalwind_humidity.json"), {"base_octave": -8, "octave_count": 3, "base_amplitude": 1.0})
    wj(os.path.join(N, "glasswaste_temperature.json"), {"base_octave": -9, "octave_count": 3, "base_amplitude": 1.0})
    wj(os.path.join(N, "glasswaste_humidity.json"), {"base_octave": -9, "octave_count": 3, "base_amplitude": 1.0})

    def climate(noise):
        return {"type": "minecraft:noise", "noise": noise, "xz_scale": 1.0, "y_scale": 0.0}

    # Petalwind: the vanilla floating-islands router with our own climate noises
    fi = rj(os.path.join(MC, "data/minecraft/worldgen/noise_settings/floating_islands.json"))
    fi["noise_router"]["temperature"] = climate(E("petalwind_temperature"))
    fi["noise_router"]["vegetation"] = climate(E("petalwind_humidity"))
    fi.pop("debug_functions", None)
    fi["material_rule"] = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [E("stormrim")]}, "then_run": {"type": "minecraft:block", "result_state": "minecraft:tuff"}},
            {"type": "minecraft:block", "result_state": "minecraft:grass_block"}]}},
        {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": {"type": "minecraft:block", "result_state": "minecraft:dirt"}},
        {"type": "minecraft:condition", "if_true": "minecraft:on_ceiling", "then_run": {"type": "minecraft:block", "result_state": "minecraft:calcite"}}]}
    wj(os.path.join(W, "noise_settings", "petalwind.json"), fi)
    # Glasswaste: the overworld terrain shape, dry (air fluid), sandstone body, biome-painted surfaces
    ow = rj(os.path.join(MC, "data/minecraft/worldgen/noise_settings/overworld.json"))
    ow["default_block"] = "minecraft:sandstone"
    ow["default_fluid"] = "minecraft:air"
    ow["aquifers"]["fluid_level_floodedness"] = -1.0 if False else ow["aquifers"]["fluid_level_floodedness"]
    ow.pop("debug_functions", None)
    ow["noise_router"]["temperature"] = climate(E("glasswaste_temperature"))
    ow["noise_router"]["vegetation"] = climate(E("glasswaste_humidity"))
    floor = lambda b: {"type": "minecraft:block", "result_state": b}
    biome_is = lambda b: {"type": "minecraft:biome", "biome_is": [E(b)]}
    ow["material_rule"] = {"type": "minecraft:sequence", "sequence": [
        "minecraft:bedrock_floor",
        {"type": "minecraft:condition", "if_true": "minecraft:on_floor", "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": biome_is("glass_flats"), "then_run": floor("minecraft:glass")},
            {"type": "minecraft:condition", "if_true": biome_is("cinder_basin"), "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.2, "max_threshold": 1.0},
                 "then_run": floor("minecraft:magma_block")}, floor("minecraft:basalt")]}},
            {"type": "minecraft:condition", "if_true": biome_is("bonebed_hollows"), "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.35, "max_threshold": 1.0},
                 "then_run": floor("minecraft:bone_block")}, floor("minecraft:sand")]}},
            {"type": "minecraft:condition", "if_true": biome_is("spirefields"), "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.4, "max_threshold": 1.0},
                 "then_run": floor(E("scorched_sand"))}, floor("minecraft:red_sand")]}},
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface", "min_threshold": 0.55, "max_threshold": 0.6},
             "then_run": floor(E("quicksand"))},
            floor("minecraft:sand")]}},
        {"type": "minecraft:condition", "if_true": "minecraft:under_floor", "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": biome_is("glass_flats"), "then_run": floor("minecraft:sand")},
            {"type": "minecraft:condition", "if_true": biome_is("cinder_basin"), "then_run": floor("minecraft:blackstone")},
            floor("minecraft:sand")]}}]}
    wj(os.path.join(W, "noise_settings", "glasswaste.json"), ow)
    # dimension types
    DT = os.path.join(D, "dimension_type")
    wj(os.path.join(DT, "petalwind.json"), {
        "ambient_light": 0.1, "coordinate_scale": 1.0, "has_ceiling": False, "has_ender_dragon_fight": False, "has_skylight": True,
        "height": 384, "logical_height": 384, "min_y": 0, "infiniburn": "#minecraft:infiniburn_overworld",
        "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
        "attributes": {"minecraft:visual/sky_color": "#F7C6D9", "minecraft:visual/fog_color": "#FBD9E6", "minecraft:visual/cloud_height": 72.0,
                       "minecraft:visual/cloud_color": "#E6FFF4F8", "minecraft:visual/sun_angle": 280.0, "minecraft:visual/moon_angle": 100.0,
                       "minecraft:visual/sunrise_sunset_color": "#90FFA8C0", "minecraft:visual/star_brightness": 0.0,
                       "minecraft:visual/sky_light_color": "#FFE0EC", "minecraft:gameplay/respawn_anchor_works": False,
                       "minecraft:visual/ambient_light_color": "#1A1418"}})
    wj(os.path.join(DT, "glasswaste.json"), {
        "ambient_light": 0.25, "coordinate_scale": 1.0, "has_ceiling": False, "has_ender_dragon_fight": False, "has_skylight": True,
        "height": 448, "logical_height": 448, "min_y": -64, "infiniburn": "#minecraft:infiniburn_overworld",
        "monster_spawn_block_light_limit": 15, "monster_spawn_light_level": 15,
        "attributes": {"minecraft:visual/sky_color": "#FFF4D6", "minecraft:visual/fog_color": "#F2C98A", "minecraft:visual/cloud_color": "#00FFFFFF",
                       "minecraft:visual/sun_angle": 0.0, "minecraft:visual/star_brightness": 0.0, "minecraft:gameplay/water_evaporates": True,
                       "minecraft:gameplay/fast_lava": True, "minecraft:gameplay/respawn_anchor_works": False,
                       "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "never", "error_message": {"text": "The sun never sets."}},
                       "minecraft:visual/ambient_light_color": "#2A2214"}})
    # dimensions
    def points(biomes):
        out = []
        for b, t, h in biomes:
            out.append({"biome": E(b), "parameters": {"temperature": t, "humidity": h, "continentalness": [-1.0, 1.0], "erosion": [-1.0, 1.0],
                                                     "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})
        return out
    wj(os.path.join(D, "dimension", "petalwind.json"), {"type": E("petalwind"), "generator": {"type": "minecraft:noise", "settings": E("petalwind"),
        "biome_source": {"type": "minecraft:multi_noise", "biomes": points([
            ("blossom_crowns", [-1.0, 0.0], [-1.0, 0.0]), ("gale_steppes", [0.0, 1.0], [-1.0, -0.2]), ("hanging_gardens", [-1.0, 0.0], [0.0, 1.0]),
            ("cloud_nine", [0.0, 1.0], [-0.2, 0.4]), ("stormrim", [0.0, 1.0], [0.4, 1.0])])}}})
    wj(os.path.join(D, "dimension", "glasswaste.json"), {"type": E("glasswaste"), "generator": {"type": "minecraft:noise", "settings": E("glasswaste"),
        "biome_source": {"type": "minecraft:multi_noise", "biomes": points([
            ("ember_dunes", [-1.0, 0.3], [-1.0, 0.45]), ("glass_flats", [0.3, 1.0], [-1.0, -0.3]), ("spirefields", [0.3, 1.0], [-0.3, 0.6]),
            ("cinder_basin", [0.3, 1.0], [0.6, 1.0]), ("bonebed_hollows", [-1.0, 0.3], [0.45, 1.0])])}}})


def main():
    block_textures()
    item_textures()
    equipment()
    block_assets()
    item_assets()
    blocks = ["skyblossom_log", "stripped_skyblossom_log", "skyblossom_wood", "stripped_skyblossom_wood", "skyblossom_planks", "skyblossom_stairs",
              "skyblossom_slab", "skyblossom_fence", "skyblossom_fence_gate", "skyblossom_door", "skyblossom_trapdoor", "skyblossom_button",
              "skyblossom_pressure_plate", "skyblossom_leaves", "skyblossom_sapling", "cloud_block", "cumulus_puff", "updraft_vent", "windchime",
              "zephyrite_ore", "sunglass", "scorched_sand", "quicksand", "mirror_block", "sunwright_bricks", "sunwright_brick_stairs",
              "sunwright_brick_slab", "sunwright_brick_wall", "chiseled_sunwright_bricks", "sunstone_ore", "sun_mirror"]
    entities = ["gale_kite", "blossom_breeze", "cumulus", "skywhale", "glass_scarab", "sandswimmer", "sunwright_revenant", "sun_mote",
                "kazehana", "petal_mirage", "tempestra", "heliodor", "ankhara", "windwright", "dune_factor"]
    lang(blocks, list(ITEMS) + [e + "_spawn_egg" for e in EGGS], entities)
    data(blocks)
    worldgen()
    print("generated", len(blocks), "blocks,", len(ITEMS), "items")


if __name__ == "__main__":
    main()
