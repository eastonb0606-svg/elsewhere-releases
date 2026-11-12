#!/usr/bin/env python3
"""Contact sheets of QA screenshots for review: sheet.py <out_prefix> <png>... (12 per sheet, labelled)."""
import sys
from PIL import Image, ImageDraw

out, files = sys.argv[1], sys.argv[2:]
W, H, C = 640, 360, 3
for s in range(0, len(files), 12):
    chunk = files[s:s + 12]
    rows = (len(chunk) + C - 1) // C
    sheet = Image.new("RGB", (W * C, (H + 18) * rows), (20, 20, 20))
    d = ImageDraw.Draw(sheet)
    for i, f in enumerate(chunk):
        im = Image.open(f).convert("RGB").resize((W, H))
        x, y = (i % C) * W, (i // C) * (H + 18)
        sheet.paste(im, (x, y + 18))
        d.text((x + 4, y + 3), f.split("/")[-1], fill=(255, 255, 0))
    sheet.save(f"{out}_{s // 12 + 1}.png")
    print(f"{out}_{s // 12 + 1}.png")
