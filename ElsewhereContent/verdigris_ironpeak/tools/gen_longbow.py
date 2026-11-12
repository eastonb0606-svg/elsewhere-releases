"""Roc-Feather Longbow pulling frames + item definition (beta 2). Run with S/venv/bin/python from the module folder.
Reads the static sprite textures/item/roc_feather_longbow.png (from gen_assets.py) and draws the string pulled back with
an arrow nocked, in three stages like vanilla's bow_pulling_0..2. The item definition dispatches on use_duration scaled
for the 1.4 s draw (vanilla: scale 0.05 for 1.0 s)."""
import json, os
from PIL import Image
A = 'src/main/resources/assets/elsewhere'
base = Image.open(f'{A}/textures/item/roc_feather_longbow.png').convert('RGBA')
STRING = (230, 230, 220, 255); SHAFT = (150, 110, 60, 255); HEAD = (200, 200, 210, 255); FLETCH = (176, 72, 42, 255); OUT = (30, 22, 14, 255)
for stage, pull in enumerate((1, 2, 3)):
    im = base.copy()
    # erase the straight string at x=3 (and its outline neighbours that are only string outline)
    # erase the straight string at x=3 and its dark outline columns (x 0..4), keeping the limb pixels
    for x in range(0, 5):
        for y in range(16):
            r, g, b, a = im.getpixel((x, y))
            if a and ((r, g, b) == STRING[:3] or r + g + b < 120): im.putpixel((x, y), (0, 0, 0, 0))
    apex = 3 - pull
    top, bot, mid = 1, 14, 7.5
    for y in range(top, bot + 1):
        t = 1 - abs(y - mid) / (mid - top + 0.5)
        x = int(round(3 - pull * t))
        im.putpixel((max(0, x), y), STRING)
    # arrow from the apex forward through the grip
    for x in range(max(0, apex), 15):
        im.putpixel((x, 7), SHAFT)
    im.putpixel((14, 7), HEAD); im.putpixel((15, 7), HEAD); im.putpixel((14, 6), HEAD); im.putpixel((14, 8), HEAD)
    im.putpixel((max(0, apex), 6), FLETCH); im.putpixel((max(0, apex), 8), FLETCH)
    if apex + 1 < 16: im.putpixel((apex + 1, 6), FLETCH); im.putpixel((apex + 1, 8), FLETCH)
    im.save(f'{A}/textures/item/roc_feather_longbow_pulling_{stage}.png')
    with open(f'{A}/models/item/roc_feather_longbow_pulling_{stage}.json', 'w') as f:
        json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"elsewhere:item/roc_feather_longbow_pulling_{stage}"}}, f, indent=1)
scale = round(0.05 / 1.4, 5)
m = lambda n: {"type": "minecraft:model", "model": f"elsewhere:item/{n}"}
item = {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
    "on_false": m("roc_feather_longbow"),
    "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": scale,
        "entries": [{"threshold": 0.65, "model": m("roc_feather_longbow_pulling_1")}, {"threshold": 0.9, "model": m("roc_feather_longbow_pulling_2")}],
        "fallback": m("roc_feather_longbow_pulling_0")}}}
with open(f'{A}/items/roc_feather_longbow.json', 'w') as f: json.dump(item, f, indent=1)
print('ok', scale)
