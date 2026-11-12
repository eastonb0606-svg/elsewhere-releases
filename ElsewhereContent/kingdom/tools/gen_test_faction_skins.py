#!/usr/bin/env python3
"""Recolours the Aldermark soldier skins into the placeholder skins of the self-test faction `elsewhere:test_marches`
(hue shift blue -> green, plus a darker tabard). Test-only: the merge excludes assets/elsewhere/textures/entity/kingdom/testfaction/.
Run: _tools/venv/bin/python tools/gen_test_faction_skins.py"""
import colorsys, os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "kingdom")
DST = os.path.join(SRC, "testfaction")
os.makedirs(DST, exist_ok=True)
for name in ["soldier_a", "soldier_b", "archer", "officer", "captain"]:
    im = Image.open(os.path.join(SRC, name + ".png")).convert("RGBA")
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            h = (h + 0.28) % 1.0
            r2, g2, b2 = colorsys.hsv_to_rgb(h, min(1.0, s * 1.1), v * 0.95)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    im.save(os.path.join(DST, name + ".png"))
    print("wrote", name)
