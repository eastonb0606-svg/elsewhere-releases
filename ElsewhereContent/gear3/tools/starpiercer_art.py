"""Starpiercer art (s-starpiercer, 3.0 swarm): the bow's three pull-stage frames and its arrow's entity texture, drawn from the
aphelion_starpiercer sprite in ROOT/Ideas/overhaul/sprites.json (same seven colours: the Aphelion night violets, the star-white
string and the dark wood grip) so they match the item icon that gen_textures.py makes from that sprite.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/starpiercer_art.py [--check] [--preview DIR]
Writes src/main/resources/assets/elsewhere/textures/:
  item/aphelion_starpiercer_pulling_0..2.png  the string drawn back to the right into a V (the sprite's bow stands upright,
                                              string on the right), a nocked star arrow lying across the grip; the head
                                              brightens with the draw (violet, star-white, star-white with a glint)
  entity/projectiles/starpiercer_arrow.png    32x32 in the vanilla arrow layout (26.x: the 32-wide mirrored side strip and
                                              the 5x5 back cross), recoloured: dark violet shaft, pale violet fletching,
                                              star-white head; drawn at full brightness by StarpiercerArrowRenderer
Models and the item definition (bow pull stages over the 1.6 s draw) come from tools/extra/aphelion_starpiercer.json
("files", written by gen_resources.py). Pure stdlib apart from the optional --preview (PIL, 16x upscale for looking at the art).
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import png, hex_rgb  # noqa: E402  same PNG writer as every other gear3 texture

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOD))
SPRITES = os.path.join(ROOT, "Ideas", "overhaul", "sprites.json")
TEX = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures")
ID = "aphelion_starpiercer"

STRING_X, STRING_TOP, STRING_BOTTOM = 13, 2, 11  # the sprite's string column, tied to the limb tips at rows 1 and 12
NOCK_Y = 7                                       # the arrow lies across the grip (grip rows 6..7)
# per frame: nock x (how far the string is drawn), arrow tip x, head style
FRAMES = [(14, 3, "dim"), (15, 3, "bright"), (15, 2, "glint")]

# vanilla arrow.png (26.x layout): each opaque pixel's vanilla colour class -> a sprite palette key, per region
ARROW_LAYOUT = ["ABA..........................ABA",
                ".ABA.........CA..AC.........ABA.",
                "DDEFFFGDFFFFDHCAACHDFFFFDGFFFEDD",
                ".ABA.........HH..HH.........ABA.",
                "ABA..........................ABA",
                "..A....A........................",
                "..B....B........................",
                "ABFBAABFBA......................",
                "..B....B........................",
                "..A....A........................"]
SHAFT = {"D": "d", "E": "o", "F": "m", "G": "l"}  # vanilla wood browns -> Aphelion violets
FLETCH = {"A": "l", "B": "a"}                     # white feathers -> pale violet, star-white
HEAD = {"A": "a", "C": "a", "H": "l"}             # grey head -> star-white


def sprite():
    return json.load(open(SPRITES, encoding="utf-8"))[ID]


def line(x0, y0, x1, y1):
    pts, dx, dy = [], abs(x1 - x0), -abs(y1 - y0)
    sx, sy, err = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1), abs(x1 - x0) - abs(y1 - y0)
    while True:
        pts.append((x0, y0))
        if x0 == x1 and y0 == y1:
            return pts
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def frame(spec, f):
    nock, tip, style = FRAMES[f]
    g = [list(r) for r in spec["rows"]]
    for y in range(STRING_TOP, STRING_BOTTOM + 1):  # take the resting string away
        g[y][STRING_X] = "."
    for (x, y) in line(STRING_X, STRING_TOP, nock, NOCK_Y) + line(nock, NOCK_Y, STRING_X, STRING_BOTTOM):
        g[y][x] = "a"
    for x in range(tip + 1, nock):  # shaft, in front of the grip
        g[NOCK_Y][x] = "l"
    g[NOCK_Y - 1][nock - 1] = g[NOCK_Y + 1][nock - 1] = "a"  # fletching
    g[NOCK_Y - 1][nock - 2] = g[NOCK_Y + 1][nock - 2] = "l"
    head = "m" if style == "dim" else "a"
    g[NOCK_Y][tip] = head
    g[NOCK_Y - 1][tip + 1] = g[NOCK_Y + 1][tip + 1] = "l" if style == "dim" else ("a" if style == "glint" else "l")
    if style == "glint":  # a star glint off the head at full draw
        g[NOCK_Y][tip - 1] = "l"
        g[NOCK_Y - 2][tip] = g[NOCK_Y + 2][tip] = "l"
    return ["".join(r) for r in g]


def rgba(spec, rows):
    pal = {k: hex_rgb(v) for k, v in spec["palette"].items()}
    return [[(*pal[ch], 255) if ch != "." else (0, 0, 0, 0) for ch in row] for row in rows]


def arrow_rows():
    out = [["."] * 32 for _ in range(32)]
    for y, row in enumerate(ARROW_LAYOUT):
        for x, c in enumerate(row):
            if c == ".":
                continue
            if c in SHAFT:
                out[y][x] = SHAFT[c]
            elif y <= 4 and 12 <= x <= 19:
                out[y][x] = HEAD[c]
            else:
                out[y][x] = FLETCH[c]
    return ["".join(r) for r in out]


def outputs():
    spec = sprite()
    files = {}
    for f in range(len(FRAMES)):
        files[os.path.join(TEX, "item", f"{ID}_pulling_{f}.png")] = png(16, 16, rgba(spec, frame(spec, f)))
    files[os.path.join(TEX, "entity", "projectiles", "starpiercer_arrow.png")] = png(32, 32, rgba(spec, arrow_rows()))
    return files


def main():
    args = sys.argv[1:]
    files = outputs()
    if "--check" in args:
        stale = [p for p, data in files.items() if not os.path.exists(p) or open(p, "rb").read() != data]
        print("starpiercer_art: " + ("stale: " + ", ".join(os.path.relpath(p, MOD) for p in stale) if stale else "up to date"))
        return 1 if stale else 0
    for p, data in files.items():
        os.makedirs(os.path.dirname(p), exist_ok=True)
        open(p, "wb").write(data)
        print("wrote", os.path.relpath(p, MOD))
    if "--preview" in args:
        from PIL import Image
        d = args[args.index("--preview") + 1]
        os.makedirs(d, exist_ok=True)
        for p in files:
            im = Image.open(p)
            im.resize((im.width * 16, im.height * 16), Image.NEAREST).save(os.path.join(d, os.path.basename(p)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
