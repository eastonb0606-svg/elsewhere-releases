"""Gale Sling extra art (s-galesling, 3.0 swarm): the whirl frames shown while the sling is charging and the two slung-stone
projectile sprites, drawn from the gale_sling sprite in ROOT/Ideas/overhaul/sprites.json (same palette, same handle) so they
match the item icon that gen_textures.py makes from that sprite.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/gale_sling_art.py [--check] [--preview DIR]
Writes src/main/resources/assets/elsewhere/textures/item/:
  gale_sling_whirl_0..3.png  the sling whirled overhead: the pouch runs round a flat ellipse above the handle, a pale wind ring
                             trails it (frame 0 left, 1 back, 2 right, 3 front); the cord runs from the handle tip to the pouch
  gale_sling_stone.png       a slung cobblestone pebble (the projectile's look with cobblestone ammo)
  gale_sling_gravel.png      a slung gravel pebble (gravel ammo)
Models and item definitions for them come from the catalogue entry (gen_resources.py, "extra_files").
Pure stdlib apart from the optional --preview (PIL, 16x upscale for looking at the art).
"""
import json, math, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import png, hex_rgb  # noqa: E402  same PNG writer as every other gear3 texture

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOD))
SPRITES = os.path.join(ROOT, "Ideas", "overhaul", "sprites.json")
OUT = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures", "item")
ID = "gale_sling"
# wind ring colours: the Galeplate (Zephyrite) teals, so the gale reads as the same wind as the rest of the Petalwind gear
WIND_FAR, WIND_NEAR = "#7cc6c9", "#c4f0ea"
# the whirl: an ellipse above the handle tip (x right, y down; pixel centres)
CX, CY, RX, RY = 9.5, 4.0, 5.5, 2.6
PIVOT = (9, 8)  # the handle tip the cord is tied to (row 9 of the sprite is the handle's top)
FRAMES = [180.0, 270.0, 0.0, 90.0]  # pouch angle per frame: left, back (up the screen), right, front (towards the viewer)
STONE_PAL = {"o": "#3a3d42", "d": "#5f6369", "m": "#8a8f96", "l": "#b4b9bf"}
GRAVEL_PAL = {"o": "#3d3532", "d": "#6b605a", "m": "#8f8580", "l": "#b3aaa3", "r": "#7a6f69"}
# 6x6 pebbles, centred in the 16x16 sprite; thrown items render at half a block, so these read as a fist-sized stone
STONE = [".oooo.",
         "omllmo",
         "omddlo",
         "odmlmo",
         "odmmdo",
         ".oooo."]
GRAVEL = [".oooo.",
          "orlmdo",
          "omdrlo",
          "oldmro",
          "odrmdo",
          ".oooo."]


def blank():
    return [["."] * 16 for _ in range(16)]


def put(g, x, y, ch):
    if 0 <= x < 16 and 0 <= y < 16:
        g[y][x] = ch


def line(g, a, b, ch, keep=()):
    (x0, y0), (x1, y1) = a, b
    n = max(abs(x1 - x0), abs(y1 - y0), 1)
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        if 0 <= x < 16 and 0 <= y < 16 and g[y][x] not in keep:
            g[y][x] = ch


def ellipse_point(deg):
    r = math.radians(deg)
    return CX + RX * math.cos(r), CY + RY * math.sin(r)


def whirl(spec, frame):
    rows = spec["rows"]
    g = blank()
    # 1) wind trail: the 220 degrees the pouch just swept, bright near the pouch and fading to the far teal (motion, not a ring)
    ang = FRAMES[frame]
    for k in range(220, -1, -2):  # far end first, so the bright part wins where they share a pixel
        x, y = ellipse_point(ang - k)  # the pouch runs anticlockwise on screen (left, back, right, front)
        put(g, round(x), round(y), "v" if k <= 100 else "w")
    # 2) the handle, untouched from the sprite (rows 9..15)
    for y in range(9, 16):
        for x in range(16):
            if rows[y][x] != ".":
                g[y][x] = rows[y][x]
    # 3) cord from the handle tip to the pouch, over the ring
    px, py = ellipse_point(ang)
    pc = (round(px), round(py))
    line(g, PIVOT, pc, "a", keep=("o", "h", "g"))
    # 4) the pouch with its stone: a rounded 4x4 (outline o, stone b), as on the icon; the front one drawn last, over the cord
    x0, y0 = round(px - 1.5), round(py - 1.5)
    for dy, r in enumerate([".oo.", "obbo", "obbo", ".oo."]):
        for dx, ch in enumerate(r):
            if ch != ".":
                put(g, x0 + dx, y0 + dy, ch)
    pal = dict(spec["palette"])
    pal["w"], pal["v"] = WIND_FAR, WIND_NEAR
    return ["".join(r) for r in g], pal


def pebble(shape, pal):
    g = blank()
    for dy, r in enumerate(shape):
        for dx, ch in enumerate(r):
            if ch != ".":
                g[5 + dy][5 + dx] = ch
    return ["".join(r) for r in g], pal


def render(rows, pal):
    p = {k: hex_rgb(v) for k, v in pal.items()}
    return [[(*p[ch], 255) if ch != "." else (0, 0, 0, 0) for ch in row] for row in rows]


def outputs():
    spec = json.load(open(SPRITES, encoding="utf-8"))[ID]
    out = {}
    for f in range(len(FRAMES)):
        out[f"{ID}_whirl_{f}"] = whirl(spec, f)
    out[f"{ID}_stone"] = pebble(STONE, STONE_PAL)
    out[f"{ID}_gravel"] = pebble(GRAVEL, GRAVEL_PAL)
    return out


def main():
    args = sys.argv[1:]
    outs = outputs()
    if "--check" in args:
        bad = []
        for name, (rows, pal) in outs.items():
            p = os.path.join(OUT, name + ".png")
            if not os.path.exists(p) or open(p, "rb").read() != png(16, 16, render(rows, pal)):
                bad.append(name)
        print(f"gale_sling_art: {len(outs)} texture(s), {len(bad)} stale" + ("\n  " + "\n  ".join(bad) if bad else ""))
        return 1 if bad else 0
    if "--preview" in args:
        from PIL import Image  # only for looking at the art
        d = args[args.index("--preview") + 1]
        os.makedirs(d, exist_ok=True)
        sheet = Image.new("RGBA", (16 * 16 * (len(outs) + 1) + 8 * len(outs), 256), (60, 60, 70, 255))
        spec = json.load(open(SPRITES, encoding="utf-8"))[ID]
        items = [(ID, (spec["rows"], spec["palette"]))] + list(outs.items())
        for i, (name, art) in enumerate(items):
            img = Image.frombytes("RGBA", (16, 16), bytes(c for row in render(*art) for px in row for c in px))
            sheet.paste(img.convert("RGBA").resize((256, 256), Image.NEAREST), (i * 264, 0), img.convert("RGBA").resize((256, 256), Image.NEAREST))
        sheet.save(os.path.join(d, "gale_sling_art_preview.png"))
        print("preview: " + os.path.join(d, "gale_sling_art_preview.png"))
        return 0
    os.makedirs(OUT, exist_ok=True)
    for name, (rows, pal) in outs.items():
        open(os.path.join(OUT, name + ".png"), "wb").write(png(16, 16, render(rows, pal)))
    print(f"gale_sling_art: wrote {len(outs)} texture(s) to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
