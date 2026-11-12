#!/usr/bin/env python3
# Usage: sunglass_arrow_entity.py <vanilla textures/entity/projectiles/arrow.png> <out sunglass_arrow.png> <preview.png>
"""s-sunglass: Sunglass Arrow entity texture = vanilla arrow.png layout, recoloured (pale sun-glass head with a warm glint,
dark wood shaft, amber fletching to match the item sprite in Ideas/overhaul/sprites.json)."""
import sys
from PIL import Image

src, out, preview = sys.argv[1:4]
im = Image.open(src).convert("RGBA")
hexrgb = lambda h: tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
HEAD = {"c1c1c1": "a9c9d6", "9e9e9e": "6f909e", "e2e2e2": "e0f4fa", "ffffff": "ffffff"}
SHAFT = {"bc9862": "8a6440", "b4905a": "7a5634", "9f844d": "6b4a2a", "735e39": "4f361e"}
FLETCH = {"e2e2e2": "ffd77a", "ffffff": "fff3cf"}
GLINT = {(15, 2): "ffe9a8", (16, 2): "ffd77a"}
for y in range(im.height):
    for x in range(im.width):
        r, g, b, a = im.getpixel((x, y))
        if a == 0:
            continue
        k = "%02x%02x%02x" % (r, g, b)
        if (x, y) in GLINT:
            n = GLINT[(x, y)]
        elif k in SHAFT:
            n = SHAFT[k]
        elif x >= 13 and 1 <= y <= 3:
            n = HEAD.get(k, k)
        else:
            n = FLETCH.get(k, k)
        im.putpixel((x, y), hexrgb(n) + (a,))
im.save(out)
im.crop((0, 0, 20, 10)).resize((400, 200), Image.NEAREST).save(preview)
print("ok", im.size)
