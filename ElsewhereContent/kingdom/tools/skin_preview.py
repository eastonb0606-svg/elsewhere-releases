#!/usr/bin/env python3
"""Contact sheet of 64x64 player skins: front, back and right side, overlays on, upscaled.
usage: preview.py out.png scale skin1.png [skin2.png ...]"""
import sys, os
from PIL import Image, ImageDraw

def crop(im, x0, y0, x1, y1): return im.crop((x0, y0, x1, y1))

FRONT = [  # (src box, dest x, dest y)
    ((8, 8, 16, 16), 4, 0), ((20, 20, 28, 32), 4, 8), ((44, 20, 48, 32), 0, 8), ((36, 52, 40, 64), 12, 8),
    ((4, 20, 8, 32), 4, 20), ((20, 52, 24, 64), 8, 20)]
FRONT_OV = [((40, 8, 48, 16), 4, 0), ((20, 36, 28, 48), 4, 8), ((44, 36, 48, 48), 0, 8), ((52, 52, 56, 64), 12, 8),
    ((4, 36, 8, 48), 4, 20), ((4, 52, 8, 64), 8, 20)]
BACK = [((24, 8, 32, 16), 4, 0), ((32, 20, 40, 32), 4, 8), ((44, 52, 48, 64), 0, 8), ((52, 20, 56, 32), 12, 8),
    ((28, 52, 32, 64), 4, 20), ((12, 20, 16, 32), 8, 20)]
BACK_OV = [((56, 8, 64, 16), 4, 0), ((32, 36, 40, 48), 4, 8), ((60, 52, 64, 64), 0, 8), ((52, 36, 56, 48), 12, 8),
    ((12, 52, 16, 64), 4, 20), ((12, 36, 16, 48), 8, 20)]
SIDE = [((0, 8, 8, 16), 0, 0), ((16, 20, 20, 32), 2, 8), ((0, 20, 4, 32), 2, 20), ((40, 20, 44, 32), 2, 8)]
SIDE_OV = [((32, 8, 40, 16), 0, 0), ((16, 36, 20, 48), 2, 8), ((0, 36, 4, 48), 2, 20), ((40, 36, 44, 48), 2, 8)]
TOP = [((8, 0, 16, 8), 0, 0)]
TOP_OV = [((40, 0, 48, 8), 0, 0)]

def view(im, base, ov, w, h):
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for box, dx, dy in base:
        out.alpha_composite(crop(im, *box), (dx, dy))
    for box, dx, dy in ov:
        out.alpha_composite(crop(im, *box), (dx, dy))
    return out

def sheet(paths, scale=8):
    cells = []
    for p in paths:
        im = Image.open(p).convert("RGBA")
        f = view(im, FRONT, FRONT_OV, 16, 32); b = view(im, BACK, BACK_OV, 16, 32)
        s = view(im, SIDE, SIDE_OV, 8, 32); t = view(im, TOP, TOP_OV, 8, 8)
        cell = Image.new("RGBA", (16 + 2 + 16 + 2 + 8 + 2 + 8, 32), (0, 0, 0, 0))
        cell.alpha_composite(f, (0, 0)); cell.alpha_composite(b, (18, 0)); cell.alpha_composite(s, (36, 0)); cell.alpha_composite(t, (46, 0))
        cells.append((os.path.basename(p)[:-4], cell.resize((cell.width * scale, cell.height * scale), Image.NEAREST)))
    cw = max(c.width for _, c in cells) + 16; ch = cells[0][1].height + 24
    cols = min(4, len(cells)); rows = (len(cells) + cols - 1) // cols
    out = Image.new("RGBA", (cw * cols + 8, ch * rows + 8), (120, 150, 120, 255))
    d = ImageDraw.Draw(out)
    for i, (n, c) in enumerate(cells):
        x = 8 + (i % cols) * cw; y = 8 + (i // cols) * ch
        out.alpha_composite(c, (x, y + 16)); d.text((x, y), n, fill=(0, 0, 0, 255))
    return out

if __name__ == "__main__":
    out, scale, *paths = sys.argv[1:]
    sheet(paths, int(scale)).save(out)
    print(out)
