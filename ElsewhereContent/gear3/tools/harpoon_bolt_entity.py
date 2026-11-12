"""Harpoon Bolt entity texture (s-harpoon, 3.0 swarm): the flying / stuck bolt, drawn in vanilla's 32x32 arrow layout
(ArrowRenderer model) with the tidebrass_harpoon_bolt item sprite's palette (ROOT/Ideas/overhaul/sprites.json), so the bolt in
flight matches its icon: a dark-brass barbed head with a pale edge, a verdigris collar, a wooden shaft and verdigris fletching.

Layout (same as vanilla textures/entity/projectiles/arrow.png):
  rows 0-4, x 0-15   the side plane (fletching at x 0-3, head at x 11-15, barbs at x 11)
  rows 0-4, x 16-31  the same plane seen from the other side (mirrored)
  rows 5-9, x 0-9    the two 5x5 back faces (the fletching seen end on, a cross)

Run: ROOT/_tools/venv/bin/python -I gear3/tools/harpoon_bolt_entity.py [--check] [--preview DIR]
Writes src/main/resources/assets/elsewhere/textures/entity/projectiles/tidebrass_harpoon_bolt.png.
Pure stdlib apart from the optional --preview (PIL, 16x upscale for looking at the art).
"""
import os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import png, hex_rgb  # noqa: E402  same PNG writer as every other gear3 texture

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
OUT = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "projectiles", "tidebrass_harpoon_bolt.png")

# the item sprite's palette (sprites.json tidebrass_harpoon_bolt): o outline, m/l/w brass head shades, a/b verdigris, h/k shaft,
# d dark verdigris
PAL = {"o": "#3b1e12", "m": "#c9764a", "l": "#eaa77c", "w": "#ffe0c8", "a": "#4fb39a", "b": "#9be0c8",
       "h": "#6b4a2a", "k": "#a8743e", "d": "#2f7a66"}

SIDE = ["bab........m....",   # fletching tip, upper barb
        ".bab........mll.",   # fletching, upper head
        "aadhkhkhhbammlww",   # nock, shaft (grain), verdigris collar, head to the point
        ".dbd........mmo.",   # fletching, lower head (shadowed edge)
        "dbd........m...."]   # fletching tip, lower barb
BACK = ["..b..",
        "..a..",
        "bahab",
        "..a..",
        "..d.."]


def pixels():
    img = [[(0, 0, 0, 0)] * 32 for _ in range(32)]

    def put(x, y, c):
        if c != ".":
            img[y][x] = hex_rgb(PAL[c]) + (255,)

    for y, row in enumerate(SIDE):
        assert len(row) == 16, row
        for x, c in enumerate(row):
            put(x, y, c)
            put(31 - x, y, c)  # the other side of the plane, mirrored
    for face in (0, 5):
        for y, row in enumerate(BACK):
            for x, c in enumerate(row):
                put(face + x, 5 + y, c)
    return img


def main():
    args = sys.argv[1:]
    data = png(32, 32, pixels())
    if "--check" in args:
        ok = os.path.isfile(OUT) and open(OUT, "rb").read() == data
        print("harpoon_bolt_entity: " + ("up to date" if ok else "OUT OF DATE (run without --check)"))
        sys.exit(0 if ok else 1)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(data)
    print("wrote " + os.path.relpath(OUT, MOD))
    if "--preview" in args:
        from PIL import Image
        d = args[args.index("--preview") + 1]
        os.makedirs(d, exist_ok=True)
        Image.open(OUT).resize((512, 512), Image.NEAREST).save(os.path.join(d, "tidebrass_harpoon_bolt_entity_x16.png"))


if __name__ == "__main__":
    main()
