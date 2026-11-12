"""Mire Snare art (s-miresnare, 3.0 swarm): the placed snare's block textures and the Snared effect icon, in one palette with
the item sprite (ROOT/Ideas/overhaul/sprites.json "mirewick_mire_snare", which this script prints with --sprite).

A Mirewick trapper's snare: two toothed iron jaws on a woven reed mat, hinged on side rods with coil springs, a gold trigger
pan in the middle, bound with the purple Mirewick cord.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/mire_snare_art.py [--check] [--sprite] [--preview FILE]
Writes src/main/resources/assets/elsewhere/textures/:
  block/mirewick_mire_snare.png         armed, seen from above: the jaws lie open flat on the mat, pan in the middle
  block/mirewick_mire_snare_sprung.png  sprung, the mat under the shut jaws (shadow, stretched springs, mud flecks)
  block/mirewick_mire_snare_parts.png   the 3D parts the block models cut from: jaw A (rows 0-5) and jaw B (rows 6-11)
                                        with interlocking teeth, the open jaws' teeth strip (12-13), iron (14-15), the gold pan
                                        top (x 11-14, rows 0-3) and side (row 4), a rod strip (x 15, rows 6-13)
  mob_effect/mire_snared.png            18x18 Snared icon: shut jaws, cord ends, mud drips
--check writes nothing and fails when a file is missing or differs. --preview needs PIL (8x upscale sheet for looking at it).
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import png, hex_rgb  # noqa: E402  same PNG writer as every other gear3 texture

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
TEX = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures")
ID = "mirewick_mire_snare"

PAL = {
    "K": "#1a1118",                                                      # outline
    "i": "#3e3b47", "I": "#6d6a78", "j": "#a5a2b2", "J": "#dcd9e4",      # iron, dark to bright
    "g": "#8c5c16", "G": "#cf9a30", "Y": "#f3d27a",                      # gold trigger pan
    "p": "#3d1a44", "P": "#74328a", "L": "#b06ac0",                      # Mirewick purple cord
    "c": "#9a2a3a", "C": "#c94a56",                                      # crimson trapper's tag
    "r": "#4b3b20", "R": "#74602f", "w": "#a48c52", "W": "#c9b47a",      # reed mat
    "m": "#3a2d1c", "M": "#58452b",                                      # mud
}


def canvas(w=16, h=16):
    return [["."] * w for _ in range(h)]


def put(g, x, y, c, mirror=False):
    g[y][x] = c
    if mirror:
        g[y][len(g[0]) - 1 - x] = c


def rows(g):
    return ["".join(r) for r in g]


def item():
    """The inventory icon: the open snare seen from above, tether and crimson tag at the corner."""
    g = canvas()
    for x in range(4, 12):
        put(g, x, 2, "K"); put(g, x, 13, "K")
    for x in range(3, 13):
        put(g, x, 3, "J"); put(g, x, 4, "j"); put(g, x, 12, "I"); put(g, x, 11, "j")
    put(g, 3, 3, "K", True); put(g, 3, 12, "K", True)
    for y in (4, 11):
        put(g, 2, y, "K", True)
    for x in range(3, 13):
        put(g, x, 5, "K"); put(g, x, 10, "K")
    for x in (4, 6, 9, 11):
        put(g, x, 5, "J"); put(g, x, 10, "j")
    for x in (5, 10):
        put(g, x, 6, "j"); put(g, x, 9, "I")
    for y in range(5, 11):
        put(g, 2, y, "I", True); put(g, 1, y, "K", True)
    for y in (5, 10):
        put(g, 2, y, "P", True)
    put(g, 2, 6, "L", True)
    for y, c in zip(range(6, 10), "JiJi"):
        put(g, 0, y, c, True)
    for x in range(6, 10):
        put(g, x, 6, "K"); put(g, x, 9, "K")
    for y in (7, 8):
        put(g, 5, y, "K", True)
        for x in range(6, 10):
            put(g, x, y, "G")
    put(g, 6, 7, "Y"); put(g, 7, 7, "Y"); put(g, 9, 8, "g"); put(g, 8, 8, "g")
    put(g, 7, 5, "I"); put(g, 8, 5, "I")
    put(g, 12, 13, "K"); put(g, 13, 13, "P"); put(g, 14, 14, "P"); put(g, 14, 13, "K"); put(g, 13, 14, "K"); put(g, 15, 14, "K")
    put(g, 14, 15, "c"); put(g, 15, 15, "C")
    return rows(g)


def mat(g):
    """Basket-weave reed mat (3x3 blocks, alternating), corners cut, purple cord round the edge."""
    for y in range(1, 15):
        for x in range(1, 15):
            if x in (1, 14) and y in (1, 14):
                continue
            if x in (1, 14) or y in (1, 14):
                g[y][x] = "L" if (x + y) % 3 == 0 else ("P" if (x + y) % 3 == 1 else "p")
            else:
                bx, by = (x - 2) // 3, (y - 2) // 3
                horiz = (bx + by) % 2 == 0
                k = (y - 2) % 3 if horiz else (x - 2) % 3
                g[y][x] = ["w", "W", "R"][k] if horiz else ["R", "w", "r"][k]


def armed():
    g = canvas(); mat(g)
    for x in range(3, 13):
        put(g, x, 3, "j"); put(g, x, 4, "I"); put(g, x, 5, "i")     # N jaw lying open (teeth plane stands at z=3)
        put(g, x, 12, "I"); put(g, x, 11, "j"); put(g, x, 10, "i")  # S jaw (teeth plane at z=13)
    for y in range(4, 12):
        put(g, 3, y, "I", True)                                      # side rods (the model's rod boxes sit on these)
    for y, c in zip(range(6, 10), "JiJi"):
        put(g, 2, y, c, True)                                        # coil springs
    for y in (5, 10):
        put(g, 3, y, "P", True)                                      # cord bindings
    for x in range(6, 10):
        for y in range(6, 10):
            put(g, x, y, "K")                                        # pan shadow (the 3D pan sits on it)
    put(g, 7, 5, "K"); put(g, 8, 5, "K")                             # trigger dog
    return rows(g)


def sprung():
    g = canvas(); mat(g)
    for y in range(4, 12):
        put(g, 3, y, "I", True)
    for y, c in zip(range(5, 11), "JiJiJi"):
        put(g, 2, y, c, True)                                        # springs stretched
    for y in (4, 11):
        put(g, 3, y, "K", True)                                      # hinge pins
    for x in range(4, 12):
        put(g, x, 7, "i"); put(g, x, 8, "i")                         # shadow of the shut jaws
    for (x, y) in ((5, 5), (10, 6), (6, 10), (11, 10), (4, 9), (9, 11), (12, 5)):
        put(g, x, y, "m")
    for (x, y) in ((5, 6), (10, 5), (7, 10), (11, 9)):
        put(g, x, y, "M")
    return rows(g)


def parts():
    g = canvas()

    def jaw(y0, phase):
        for x in range(0, 10):
            tooth = x % 2 == phase
            put(g, x, y0, "J" if tooth else ".")
            put(g, x, y0 + 1, "j" if tooth else "J")
            put(g, x, y0 + 2, "j"); put(g, x, y0 + 3, "I"); put(g, x, y0 + 4, "I"); put(g, x, y0 + 5, "i")
        for x in (1, 8):
            put(g, x, y0 + 3, "P"); put(g, x, y0 + 4, "L" if x == 1 else "P")
        put(g, 0, y0 + 2, "K"); put(g, 9, y0 + 2, "K")
    jaw(0, 0)
    jaw(6, 1)
    for x in range(0, 10):
        put(g, x, 12, "J" if x % 2 == 0 else "."); put(g, x, 13, "j" if x % 2 == 0 else "I")
        put(g, x, 14, "j"); put(g, x, 15, "I")
    for y, r in enumerate(["YYGG", "YGGG", "GGGg", "GGgg"]):
        for x, c in enumerate(r):
            put(g, 11 + x, y, c)
    for x in range(11, 15):
        put(g, x, 4, "g")
    for y in range(6, 14):
        put(g, 15, y, "P" if y in (7, 12) else "j")                 # rod strip with its two cord bindings
    return rows(g)


ICON = [
    "..................",
    "..................",
    "..................",
    ".....KKKKKKKK.....",
    "...KKjJJJJJJjKK...",
    "..KjJjjjjjjjjJjK..",
    "..KIjIIIIIIIIjIK..",
    ".PKIKJKJKJKJKJIKP.",
    ".LKIJKJKJKJKJKIKL.",
    ".PKiiIIIIIIIIiiKP.",
    "..KiiiiiiiiiiiiK..",
    "...KKiiiiiiiiKK...",
    ".....KKKKKKKK.....",
    "......mM..Mm......",
    ".......m....m.....",
    "..................",
    "..................",
    "..................",
]

FILES = {
    os.path.join("block", ID + ".png"): armed,
    os.path.join("block", ID + "_sprung.png"): sprung,
    os.path.join("block", ID + "_parts.png"): parts,
    os.path.join("mob_effect", "mire_snared.png"): lambda: ICON,
}


def encode(grid):
    w, h = len(grid[0]), len(grid)
    assert all(len(r) == w for r in grid), [len(r) for r in grid]
    px = [[(0, 0, 0, 0) if ch == "." else hex_rgb(PAL[ch]) + (255,) for ch in r] for r in grid]
    return png(w, h, px)


def sprite_entry():
    used = sorted({ch for r in item() for ch in r if ch != "."})
    return {"palette": {c: PAL[c] for c in used}, "rows": item(), "template": "snare"}


def main(argv):
    if "--sprite" in argv:
        print(json.dumps({ID: sprite_entry()}, indent=1))
        return 0
    check = "--check" in argv
    bad = 0
    for rel, fn in FILES.items():
        path = os.path.join(TEX, rel)
        data = encode(fn())
        if check:
            if not os.path.exists(path) or open(path, "rb").read() != data:
                print("stale:", rel); bad += 1
            continue
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "wb") as f:
            f.write(data)
        print("wrote", rel)
    if "--preview" in argv:
        from PIL import Image  # optional, only for looking at the art
        sets = [item(), armed(), sprung(), parts(), ICON]
        tiles = []
        for s in sets:
            im = Image.new("RGBA", (len(s[0]), len(s)), (0, 0, 0, 0))
            for y, r in enumerate(s):
                for x, ch in enumerate(r):
                    if ch != ".":
                        im.putpixel((x, y), hex_rgb(PAL[ch]) + (255,))
            tiles.append(im.resize((im.width * 8, im.height * 8), Image.NEAREST))
        out = Image.new("RGBA", (sum(t.width + 8 for t in tiles) + 8, 160), (70, 70, 70, 255))
        x = 8
        for t in tiles:
            out.alpha_composite(t, (x, 8)); x += t.width + 8
        out.save(argv[argv.index("--preview") + 1])
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
