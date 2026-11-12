#!/usr/bin/env python3
"""Checks player-layout skins: 64x64 RGBA, every base-layer face fully opaque, overlay alpha only 0 or 255.
usage: check_skins.py file.png [...]"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image
from skinkit import PARTS, face_box
BASE = ("head", "body", "rarm", "larm", "rleg", "lleg")
OVER = ("hat", "jacket", "rsleeve", "lsleeve", "rpants", "lpants")
bad = 0
for p in sys.argv[1:]:
    im = Image.open(p)
    errs = []
    if im.size != (64, 64): errs.append("size %s" % (im.size,))
    if im.mode != "RGBA": errs.append("mode " + im.mode)
    im = im.convert("RGBA"); px = im.load()
    for part in BASE:
        for f in ("top", "bottom", "right", "front", "left", "back"):
            x0, y0, w, h = face_box(part, f)
            holes = sum(1 for y in range(y0, y0 + h) for x in range(x0, x0 + w) if px[x, y][3] != 255)
            if holes: errs.append(f"{part}.{f} {holes} non-opaque px")
    for part in OVER:
        for f in ("top", "bottom", "right", "front", "left", "back"):
            x0, y0, w, h = face_box(part, f)
            semi = sum(1 for y in range(y0, y0 + h) for x in range(x0, x0 + w) if px[x, y][3] not in (0, 255))
            if semi: errs.append(f"{part}.{f} {semi} semi-transparent px")
    if errs:
        bad += 1; print("BAD", p, "; ".join(errs[:6]))
print(f"{len(sys.argv) - 1 - bad}/{len(sys.argv) - 1} skins OK")
sys.exit(1 if bad else 0)
