#!/usr/bin/env python3
"""Generates the two dimension trim pattern textures (grayscale 8-bit, black = transparent, levels 96..224 like vanilla).
Run from the module root: python3 tools/gen_trims.py"""
import math, os, struct, zlib

def png_gray(w, h, px):
    raw = b''.join(b'\x00' + bytes(px[y * w:(y + 1) * w]) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 0, 0, 0, 0)) + chunk(b'tRNS', b'\x00\x00') + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

def tidal(x, y):  # waves
    v = (y + round(2 * math.sin(x / 2.0))) % 6
    return (224, 160)[v % 2] if v < 2 else (128 if v == 3 else 0)

def summit(x, y):  # zigzag peaks
    t = abs((x % 8) - 4) + (y % 8)
    return 224 if t in (4, 5) else (160 if t == 6 else (96 if t == 3 else 0))

MCMETA = '{\n  "palette" : {\n    "base_palette" : "trim_base"\n  }\n}\n'
for name, fn in (('tidal', tidal), ('summit', summit)):
    for sub in ('humanoid', 'humanoid_leggings'):
        d = os.path.join('src/main/resources/assets/elsewhere/textures/trims/entity', sub)
        os.makedirs(d, exist_ok=True)
        open(os.path.join(d, name + '.png'), 'wb').write(png_gray(64, 32, [fn(x, y) for y in range(32) for x in range(64)]))
        open(os.path.join(d, name + '.png.mcmeta'), 'w').write(MCMETA)
