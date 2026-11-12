#!/usr/bin/env python3
"""Stormcoil Staff icon (copper shaft, teal coil, spark tip) and the Dazed effect icon."""
import os, colorsys
from PIL import Image
T = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "elsewhere", "textures")
def recolor(src, dst, hue, sat_mul=1.0, val_mul=1.0):
    im = Image.open(os.path.join(T, src)).convert("RGBA"); px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            nh = hue(h, s, v, x, y)
            r2, g2, b2 = colorsys.hsv_to_rgb(nh, min(1, s * sat_mul), min(1, v * val_mul))
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return im
# Stormcoil: sunlance shape, shaft -> copper, head -> teal/cyan with white sparks
im = recolor("item/sunlance_staff.png", None, lambda h, s, v, x, y: 0.07 if y > 7 else 0.5, 1.0, 1.0)
px = im.load()
for (x, y) in [(13, 1), (14, 0), (15, 2), (11, 2), (12, 0)]:
    if 0 <= x < im.width and 0 <= y < im.height: px[x, y] = (230, 255, 255, 255)
# coil bands along the shaft
for i in range(4, 12, 2):
    x, y = 15 - i, i
    if px[x, y][3]: px[x, y] = (64, 200, 190, 255)
im.save(os.path.join(T, "item/stormcoil_staff.png"))
recolor("mob_effect/clarity.png", None, lambda h, s, v, x, y: 0.72, 0.6, 0.75).save(os.path.join(T, "mob_effect/dazed.png"))
print("ok")
