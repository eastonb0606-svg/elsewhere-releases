"""3.0 polish P10 (s-p10-tabard): tabard emblems and soldier capes for the four kingdoms.

Writes textures/entity/kingdom/heraldry/:
  tabard_plain.png          the white tabard without the generic grey tower motif (drawn under an emblem, tinted in game)
  <kingdom>_emblem.png      64x32 overlay in the tabard's UV layout (chest + back emblem, belt, skirt hem); full colour
  <kingdom>_cape.png        32x16 captain cape (long, with the emblem), box UV: lining u0-9, outer u10-19, rows = height
  <kingdom>_cape_short.png  32x16 officer cape (short, no emblem)
Kingdoms: aldermark (tower under a crown, red chief), hrafnholt (brown raven on green), azmar (winged scarab, usekh
collar, Egyptian stripes), mirewick (gold lantern with crimson glass, purple/crimson/gold).
Run: _tools/venv/bin/python -I kingdom/tools/gen_heraldry.py [--preview <dir>]
"""
import os, random, sys, zlib
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ENT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "kingdom")
OUT = os.path.join(ENT, "heraldry")

# tabard UV panels (HumanoidModel 64x32, body texOffs 16,16: front u20-27, back u32-39, v20-31; legs u0-15 v20-24)
FRONT_U, BACK_U, PANEL_V = 20, 32, 20
SKIRT_U0, SKIRT_U1, SKIRT_HEM_V, SKIRT_DAG_V = 0, 16, 23, 24
CAPE_W, CAPE_H, LINING_U, OUTER_U = 10, 16, 0, 10


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def jitter(c, r, n=5):
    d = r.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c) + (255,)


# ---------------------------------------------------------------- emblems (8 wide x 11 rows; row 11 is the dagged waist, left clear)
EMBLEMS = {
    "aldermark": {
        "pal": {"R": "#A3302E", "r": "#7A2321", "Y": "#F0C94A", "y": "#C4962F", "W": "#ECE3C2", "w": "#C9BC8E",
                "d": "#1A2340", "B": "#5C3E26", "b": "#E2B84A"},
        "front": ["RRRRRRRR",
                  "RYrYYrYR",
                  "rYYYYYYr",
                  ".W.WW.W.",
                  ".WWWWWw.",
                  "..WWWw..",
                  "..WddW..",
                  "..WWWw..",
                  "..WddW..",
                  ".WWddWw.",
                  "BBBbbBBB"],
        "back": None,  # same as front, belt without buckle
        "belt_back": "BBBBBBBB",
        "hem": ("#E8D9A0", "#E8D9A0", "#BFA96E"),  # hem row colours a/b alternate, dag colour
    },
    "hrafnholt": {
        "pal": {"M": "#6B4A2B", "D": "#33231A", "L": "#8E6A43", "E": "#D9A23A", "k": "#4A4440", "B": "#4A3420",
                "I": "#9A9A96"},
        "front": ["MLMLMLML",
                  "D......D",
                  "DD.kDE.D",
                  "DDLDDDLD",
                  ".DDDDDD.",
                  "..DLLD..",
                  "..DMMD..",
                  "...DD...",
                  "..DDDD..",
                  "..L..L..",
                  "BBBIIBBB"],
        "back": None,
        "belt_back": "BBBBBBBB",
        "hem": ("#6B4A2B", "#8E6A43", "#553A22"),
    },
    "azmar": {
        "pal": {"G": "#E2B240", "g": "#B07F28", "T": "#2BB3A3", "t": "#1C8074", "L": "#16245C", "r": "#C8462A",
                "S": "#7A4E1C", "s": "#A86E28"},
        "front": ["GTGTTGTG",
                  ".GLGGLG.",
                  "..TttT..",
                  "...rr...",
                  "G..SS..G",
                  "GGgSSgGG",
                  ".GGssGG.",
                  "..gSSg..",
                  "...SS...",
                  "..g..g..",
                  "TGTLLTGT"],
        "back": None,
        "belt_back": "TGTLLTGT",
        "hem": ("#2BB3A3", "#E2B240", "#B07F28"),
    },
    "mirewick": {
        "pal": {"C": "#9E1B32", "c": "#CC3440", "Y": "#D6A832", "y": "#9E7620", "o": "#FFC45A", "P": "#3E1A5E"},
        "front": ["CYCCCCYC",
                  "...yy...",
                  "..YYYY..",
                  ".YyyyyY.",
                  ".YCooCY.",
                  ".YcocCY.",
                  ".YCccCY.",
                  ".YyyyyY.",
                  "..YYYY..",
                  "........",
                  "CCCYYCCC"],
        "back": None,
        "belt_back": "CCCCCCCC",
        "hem": ("#D6A832", "#D6A832", "#9E1B32"),
    },
}


def emblem(name, spec, tabard):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    pal = {k: rgb(v) for k, v in spec["pal"].items()}
    r = random.Random(zlib.crc32(name.encode()))
    front = spec["front"]
    back = spec["back"] or front[:-1] + [spec["belt_back"]]
    for u0, grid in ((FRONT_U, front), (BACK_U, back)):
        for y, row in enumerate(grid):
            for x, ch in enumerate(row):
                if ch == "." or tabard.getpixel((u0 + x, PANEL_V + y))[3] == 0:
                    continue
                img.putpixel((u0 + x, PANEL_V + y), jitter(pal[ch], r, 4))
    a, b, dag = (rgb(c) for c in spec["hem"])
    for u in range(SKIRT_U0, SKIRT_U1):
        if tabard.getpixel((u, SKIRT_HEM_V))[3]:
            img.putpixel((u, SKIRT_HEM_V), jitter(a if u % 2 == 0 else b, r, 3))
        if tabard.getpixel((u, SKIRT_DAG_V))[3]:
            img.putpixel((u, SKIRT_DAG_V), jitter(dag, r, 3))
    return img


def plain_tabard(tabard):
    out = tabard.copy()
    for u0 in (FRONT_U, BACK_U):
        for y in range(PANEL_V, PANEL_V + 11):
            for x in range(8):
                p = out.getpixel((u0 + x, y))
                if p[3] and p[0] == 150:  # the generic tower motif -> the cloth stripe it sits on
                    v = 232 if x in (2, 5) else 250
                    out.putpixel((u0 + x, y), (v, v, v, 255))
    return out


# ---------------------------------------------------------------- capes (10 wide x 16 tall, outer face as seen from behind)
def cape_grid(rows):
    rows = list(rows) + ["." * CAPE_W] * (CAPE_H - len(rows))
    assert len(rows) == CAPE_H and all(len(r) == CAPE_W for r in rows), rows
    return rows


CAPES = {
    "aldermark": {
        "pal": {"T": "#E8D9A0", "t": "#BFA96E", "B": "#2E4C9E", "b": "#243C7E", "h": "#3A5CB4", "Y": "#F0C94A",
                "y": "#C4962F", "W": "#ECE3C2", "w": "#C9BC8E", "d": "#1A2340"},
        "lining": ("#E2D6B0", "#CBBE92"),
        "long": ["TTTTTTTTTT",
                 "TBbBhhBbBT",
                 "TBYbYYbYBT",
                 "TBYYYYYYBT",
                 "TbW.WW.WbT".replace(".", "B"),
                 "TbWWWWWwbT",
                 "TBbWWWwbBT",
                 "TBbWddWbBT",
                 "TBbWWWwbBT",
                 "TBbWddWbBT",
                 "TBWWddWwBT",
                 "TBbBhhBbBT",
                 "TBbBhhBbBT",
                 "TBbBhhBbBT",
                 "TTTTTTTTTT",
                 "t.t.t.t.t."],
        "short": ["TTTTTTTTTT",
                  "TBbBhhBbBT",
                  "TBbBhhBbBT",
                  "TBbBhhBbBT",
                  "TBbBhhBbBT",
                  "TBbBhhBbBT",
                  "TBbBhhBbBT",
                  "TTTTTTTTTT",
                  "t.t.t.t.t."],
    },
    "hrafnholt": {
        "pal": {"F": "#7A6650", "f": "#5C4A38", "e": "#9A8468", "G": "#2E5A2C", "g": "#244A23", "h": "#386B35",
                "M": "#6B4A2B", "L": "#8E6A43", "D": "#33231A", "E": "#D9A23A", "k": "#4A4440"},
        "lining": ("#5A3F25", "#4A3420"),
        "lining_fur_rows": 2,
        "long": ["FeFfFeFFfF",
                 "fFeFfFFeFf",
                 "MGfGFgGfGM",
                 "MGD.....DM".replace(".", "G"),
                 "MDD.kDE.DM".replace(".", "G"),
                 "MDDLDDDLDM",
                 "MGDDDDDDGM",
                 "MGgDLLDgGM",
                 "MGgDMMDgGM",
                 "MGhgDDghGM",
                 "MGgDDDDgGM",
                 "MGhgLgLhGM",
                 "MLMLMLMLML",
                 "MMMMMMMMMM",
                 "G.GG.GG.GG",
                 "..G....G.."],
        "short": ["FeFfFeFFfF",
                  "fFeFfFFeFf",
                  "MGfGFgGfGM",
                  "MGghGGhgGM",
                  "MGghGGhgGM",
                  "MGghGGhgGM",
                  "MLMLMLMLML",
                  "MMMMMMMMMM",
                  "G.GG.GGG.G",
                  ".G....G..."],
    },
    "azmar": {
        "pal": {"G": "#E2B240", "g": "#B07F28", "W": "#E9E1CC", "w": "#D2C8B0", "L": "#1F3F8F", "l": "#16245C",
                "T": "#2BB3A3", "r": "#C8462A", "S": "#7A4E1C", "s": "#A86E28"},
        "lining": ("#1F3F8F", "#1A3678"),
        "lining_keep": "Gg",  # the gold collar shows on both sides
        "long": ["GGGGGGGGGG",
                 "WwWWwWWwWW",
                 "WwWWrrWwWW",
                 "WGwWSSWwGW",
                 "GGgGSSGgGG",
                 "WGGGssGGGW",
                 "WwWgSSgWwW",
                 "WwWWSSWwWW",
                 "WwWgWWgwWW",
                 "WwWWwWWwWW",
                 "LLLLLLLLLL",
                 "GgGgGgGgGg",
                 "TTTTTTTTTT",
                 "llllllllll"],
        "short": ["GGGGGGGGGG",
                  "WwWWwWWwWW",
                  "WwWWwWWwWW",
                  "WwWWwWWwWW",
                  "WwWWwWWwWW",
                  "LLLLLLLLLL",
                  "GgGgGgGgGg",
                  "TTTTTTTTTT"],
    },
    "mirewick": {
        "pal": {"Y": "#D6A832", "y": "#9E7620", "P": "#5B2A86", "p": "#45206A", "q": "#6E3A9C", "C": "#9E1B32",
                "c": "#CC3440", "o": "#FFC45A"},
        "lining": ("#9E1B32", "#82162A"),
        "lining_keep": "Yy",
        "long": ["YYYYYYYYYY",
                 "YPpPyyPpPY",
                 "YPpYYYYpPY",
                 "YPYyyyyYPY",
                 "YPYCooCYPY",
                 "YPYcocCYPY",
                 "YPYCccCYPY",
                 "YPYyyyyYPY",
                 "YPpYYYYpPY",
                 "YPpPqqPpPY",
                 "YPpPqqPpPY",
                 "YCCCCCCCCY",
                 "YPpPqqPpPY",
                 "YPpPqqPpPY",
                 "YYYYYYYYYY",
                 "Y.Y.Y.Y.Y."],
        "short": ["YYYYYYYYYY",
                  "YPpPqqPpPY",
                  "YPpPqqPpPY",
                  "YPpPqqPpPY",
                  "YPpPqqPpPY",
                  "YPpPqqPpPY",
                  "YCCCCCCCCY",
                  "YYYYYYYYYY",
                  "Y.Y.Y.Y.Y."],
    },
}


def cape(name, spec, rows):
    grid = cape_grid(rows)
    pal = {k: rgb(v) for k, v in spec["pal"].items()}
    lin_a, lin_b = rgb(spec["lining"][0]), rgb(spec["lining"][1])
    keep = spec.get("lining_keep", "")
    fur_rows = spec.get("lining_fur_rows", 0)
    r = random.Random(zlib.crc32(name.encode()) ^ len(rows))
    img = Image.new("RGBA", (32, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            img.putpixel((OUTER_U + x, y), jitter(pal[ch], r, 4))
            # lining: the same pixel seen from the front sits at the mirrored column of the lining region
            lx = LINING_U + CAPE_W - 1 - x
            if ch in keep or y < fur_rows:
                c = pal[ch]
            else:
                c = lin_a if (x + y) % 3 else lin_b
            img.putpixel((lx, y), jitter(c, r, 3))
    return img


def main():
    preview = None
    if "--preview" in sys.argv:
        preview = sys.argv[sys.argv.index("--preview") + 1]
    os.makedirs(OUT, exist_ok=True)
    tabard = Image.open(os.path.join(ENT, "tabard.png")).convert("RGBA")
    plain = plain_tabard(tabard)
    plain.save(os.path.join(OUT, "tabard_plain.png"))
    made = {"tabard_plain": plain}
    for name, spec in EMBLEMS.items():
        im = emblem(name, spec, tabard)
        im.save(os.path.join(OUT, name + "_emblem.png"))
        made[name + "_emblem"] = im
    for name, spec in CAPES.items():
        for cut, suffix in (("long", "_cape"), ("short", "_cape_short")):
            im = cape(name, spec, spec[cut])
            im.save(os.path.join(OUT, name + suffix + ".png"))
            made[name + suffix] = im
    print("wrote", len(made), "textures to", os.path.normpath(OUT))
    if preview:
        write_preview(preview, plain, made)


TABARD_TINT = {"aldermark": "#2E4C9E", "hrafnholt": "#2E5A2C", "azmar": "#1F3F8F", "mirewick": "#5B2A86"}


def tinted(img, hexcol):
    c = rgb(hexcol)
    out = Image.new("RGBA", img.size, (0, 0, 0, 0))
    for x in range(img.width):
        for y in range(img.height):
            p = img.getpixel((x, y))
            if p[3]:
                out.putpixel((x, y), tuple(p[i] * c[i] // 255 for i in range(3)) + (255,))
    return out


def write_preview(d, plain, made):
    os.makedirs(d, exist_ok=True)
    S = 14
    names = list(TABARD_TINT)
    # emblems: tinted plain tabard + overlay; front panel, back panel, skirt strip
    sheet = Image.new("RGBA", (len(names) * (30 * S) + 20, 18 * S), (52, 52, 56, 255))
    for i, n in enumerate(names):
        base = tinted(plain, TABARD_TINT[n])
        base.alpha_composite(made[n + "_emblem"])
        ox = 10 + i * 30 * S
        sheet.alpha_composite(base.crop((FRONT_U, 16, FRONT_U + 8, 32)).resize((8 * S, 16 * S), Image.NEAREST), (ox, 6))
        sheet.alpha_composite(base.crop((BACK_U, 20, BACK_U + 8, 32)).resize((8 * S, 12 * S), Image.NEAREST), (ox + 9 * S, 6 + 4 * S))
        sheet.alpha_composite(base.crop((0, 20, 16, 25)).resize((8 * S, 5 * S // 2), Image.NEAREST), (ox + 18 * S, 6 + 13 * S))
    sheet.save(os.path.join(d, "emblems.png"))
    # capes: outer (long, short) and lining (long)
    sheet = Image.new("RGBA", (len(names) * (34 * S) + 20, 17 * S), (52, 52, 56, 255))
    for i, n in enumerate(names):
        ox = 10 + i * 34 * S
        for j, (key, u) in enumerate(((n + "_cape", OUTER_U), (n + "_cape_short", OUTER_U), (n + "_cape", LINING_U))):
            im = made[key].crop((u, 0, u + CAPE_W, CAPE_H)).resize((CAPE_W * S, CAPE_H * S), Image.NEAREST)
            sheet.alpha_composite(im, (ox + j * 11 * S, 6))
    sheet.save(os.path.join(d, "capes.png"))


if __name__ == "__main__":
    main()
