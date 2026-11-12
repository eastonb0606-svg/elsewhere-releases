"""Prism Bow art (s-prismbow, 3.0 swarm): the three pull-stage frames, drawn from the prism_bow sprite in
ROOT/Ideas/overhaul/sprites.json (same palette, same glass limbs and grip) so they match the item icon that gen_textures.py
makes from that sprite.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/prism_bow_art.py [--check] [--preview DIR]
Writes src/main/resources/assets/elsewhere/textures/item/:
  prism_bow.png              the idle icon, byte-identical to what gen_textures.py writes from the sprite (so the bow has its
                             icon even before the catalogue entry is regenerated)
  prism_bow_pulling_0..2.png the string drawn back to the right (the sprite's bow stands upright, string on the right), a
                             nocked arrow of light lying across the grip; it brightens with the draw (dim gold, gold, white-gold
                             with a cyan prism head) because a full draw is what turns it into a light arrow
Models and the item definition (vanilla bow pull stages) come from tools/bow_models.py (gen_resources.py, catalogue "model": "bow").
Pure stdlib apart from the optional --preview (PIL, 16x upscale for looking at the art).
"""
import json, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_textures import png, render  # noqa: E402  same PNG writer and sprite renderer as every other gear3 texture

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOD))
SPRITES = os.path.join(ROOT, "Ideas", "overhaul", "sprites.json")
OUT = os.path.join(MOD, "src", "main", "resources", "assets", "elsewhere", "textures", "item")
ID = "prism_bow"

STRING_X = 13            # the sprite's string column (rows 2..11), tied to the limb tips at (13, 1) and (13, 12)
STRING_TOP, STRING_BOTTOM = 2, 11
NOCK_Y = 7               # the arrow rests across the grip (grip rows 6..7)
ARROW_LEN = 13           # tip to nock
# per frame: how far the string's middle is drawn back (px right of the rest column), how many rows of it stay at full draw,
# and the arrow's colours (shaft, head flanks, tip)
FRAMES = [
    {"pull": 1, "flat": 4, "shaft": "#d9b45e", "flank": "#a9c9d6", "tip": "#e0f4fa"},
    {"pull": 2, "flat": 4, "shaft": "#ffd77a", "flank": "#bfefff", "tip": "#ffffff"},
    {"pull": 2, "flat": 2, "shaft": "#fff1c2", "flank": "#bfefff", "tip": "#ffffff", "glow": "#ffe9a8"},
]


def grid(spec):
    return [list(r) for r in spec["rows"]]


def string_columns(pull, flat):
    """x of the string on each row 2..11 for a draw of `pull` px: straight runs from the two tips to a flat middle of `flat` rows."""
    mid_top = max(STRING_TOP + 1, NOCK_Y - flat // 2)  # the flat middle holds the nock row
    mid_bot = min(STRING_BOTTOM - 1, mid_top + flat - 1)
    cols = {}
    for y in range(STRING_TOP, STRING_BOTTOM + 1):
        if mid_top <= y <= mid_bot:
            cols[y] = STRING_X + pull
        elif y < mid_top:
            t = (y - STRING_TOP + 1) / (mid_top - STRING_TOP + 1)
            cols[y] = STRING_X + round(pull * t)
        else:
            t = (STRING_BOTTOM - y + 1) / (STRING_BOTTOM - mid_bot + 1)
            cols[y] = STRING_X + round(pull * t)
    return cols


def frame(spec, f):
    g = grid(spec)
    pal = dict(spec["palette"])
    # 1) take the resting string off (only the string pixels, the limbs and tips stay exactly as in the icon)
    for y in range(STRING_TOP, STRING_BOTTOM + 1):
        if g[y][STRING_X] == "a":
            g[y][STRING_X] = "."
    # 2) the drawn string
    for y, x in string_columns(f["pull"], f["flat"]).items():
        g[y][x] = "a"
    # 3) the arrow of light: tip, two prism flanks, shaft over the grip, nock on the string
    nock = STRING_X + f["pull"]
    tip = nock - ARROW_LEN
    pal.update({"s": f["shaft"], "f": f["flank"], "t": f["tip"]})
    g[NOCK_Y][tip] = "t"
    g[NOCK_Y - 1][tip + 1] = "f"
    g[NOCK_Y + 1][tip + 1] = "f"
    for x in range(tip + 1, nock):
        g[NOCK_Y][x] = "s"
    if "glow" in f:  # full draw: the light gathers round the head
        pal["w"] = f["glow"]
        for (x, y) in ((tip - 1, NOCK_Y), (tip + 2, NOCK_Y - 1), (tip + 2, NOCK_Y + 1)):
            if 0 <= x < 16 and g[y][x] == ".":
                g[y][x] = "w"
    return {"palette": pal, "rows": ["".join(r) for r in g]}


def main():
    args = sys.argv[1:]
    data = json.load(open(SPRITES, encoding="utf-8"))
    if ID not in data:
        print(f"prism_bow_art: no '{ID}' sprite in {SPRITES}")
        return 1
    spec = data[ID]
    frames = [frame(spec, f) for f in FRAMES]
    out = {ID: spec}
    for n, fr in enumerate(frames):
        out[f"{ID}_pulling_{n}"] = fr
    for name, s in out.items():
        bad = [y for y, r in enumerate(s["rows"]) if len(r) != 16 or any(ch != "." and ch not in s["palette"] for ch in r)]
        if len(s["rows"]) != 16 or bad:
            print(f"prism_bow_art: {name} is malformed (rows {bad})")
            return 1
    if "--check" in args:
        print(f"prism_bow_art: {len(out)} texture(s) OK")
        return 0
    os.makedirs(OUT, exist_ok=True)
    for name, s in out.items():
        open(os.path.join(OUT, name + ".png"), "wb").write(png(16, 16, render(s)))
    if "--preview" in args:
        from PIL import Image  # noqa: E402  (venv only; the textures above need nothing but the stdlib)
        d = args[args.index("--preview") + 1]
        os.makedirs(d, exist_ok=True)
        sheet = Image.new("RGBA", (len(out) * 16 * 12 + (len(out) - 1) * 8, 16 * 12), (58, 58, 68, 255))
        for n, name in enumerate(out):
            im = Image.open(os.path.join(OUT, name + ".png")).convert("RGBA").resize((192, 192), Image.NEAREST)
            sheet.alpha_composite(im, (n * 200, 0))
        sheet.save(os.path.join(d, "prism_bow_frames.png"))
    for name, s in out.items():
        print(name)
        print("\n".join(s["rows"]))
    print(f"prism_bow_art: wrote {len(out)} texture(s) to {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
