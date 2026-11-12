#!/usr/bin/env python3
"""Procedural textures, block/item models, blockstates, lang, loot tables and tags for the module.
Run with the pillow venv: S/venv/bin/python tools/gen_assets.py  (S = shared scratchpad; vanilla assets at S/mcfull)."""
import json, os, random, re, sys, colorsys
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
A = os.path.join(RES, 'assets', 'elsewhere')
MC = sys.argv[1] if len(sys.argv) > 1 else '/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad/mcfull/assets/minecraft'
LANG = {}

def wj(path, obj):
    p = os.path.join(RES, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f: json.dump(obj, f, indent=1)

def hexc(h):
    h = h.lstrip('#'); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])
def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

def save(img, kind, name):
    p = os.path.join(A, 'textures', kind, name + '.png')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)

def noise_tex(base, var=0.12, seed=0, size=16):
    r = random.Random(seed); im = Image.new('RGBA', (size, size))
    for x in range(size):
        for y in range(size):
            im.putpixel((x, y), shade(base, 1 + (r.random() - 0.5) * 2 * var) + (255,))
    return im

# ---------------------------------------------------------------- block textures
def plate(base, rivets=True, seed=1):
    im = noise_tex(base, 0.08, seed)
    for i in range(16):
        im.putpixel((i, 0), shade(base, 1.25) + (255,)); im.putpixel((0, i), shade(base, 1.2) + (255,))
        im.putpixel((i, 15), shade(base, 0.6) + (255,)); im.putpixel((15, i), shade(base, 0.65) + (255,))
        im.putpixel((i, 7), shade(base, 0.75) + (255,))
    if rivets:
        for x, y in [(2, 2), (13, 2), (2, 12), (13, 12), (7, 3), (7, 11)]:
            im.putpixel((x, y), shade(base, 1.45) + (255,)); im.putpixel((x + 1, y + 1), shade(base, 0.55) + (255,))
    r = random.Random(seed)
    for _ in range(10):  # patina blotches
        x, y = r.randrange(16), r.randrange(16)
        im.putpixel((x, y), mix(base, (90, 200, 170), 0.5) + (255,))
    return im

def grating(base):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    for x in range(16):
        for y in range(16):
            if x % 4 == 0 or y % 4 == 0 or x == 15 or y == 15:
                im.putpixel((x, y), shade(base, 0.9 + 0.2 * ((x + y) % 3 == 0)) + (255,))
    return im

def pipe(base):
    im = noise_tex(base, 0.06, 3)
    for y in range(16):
        for x in range(16):
            f = 0.7 + 0.6 * (1 - abs(x - 7.5) / 8)
            if y in (0, 1, 14, 15): f *= 1.2
            im.putpixel((x, y), shade(base, f) + (255,))
    return im

def stone_like(base, seed, var=0.15):
    im = noise_tex(base, var, seed)
    r = random.Random(seed)
    for _ in range(18):
        x, y = r.randrange(15), r.randrange(15)
        c = shade(base, 0.8 if r.random() < 0.5 else 1.15)
        im.putpixel((x, y), c + (255,)); im.putpixel((x + 1, y), c + (255,))
    return im

def ore(stone, speck, seed):
    im = stone_like(stone, seed)
    r = random.Random(seed + 9)
    for _ in range(5):
        cx, cy = r.randrange(2, 13), r.randrange(2, 13)
        for dx, dy in [(0, 0), (1, 0), (0, 1), (1, 1), (-1, 0)]:
            if r.random() < 0.85:
                im.putpixel((cx + dx, cy + dy), shade(speck, 0.85 + r.random() * 0.4) + (255,))
        im.putpixel((cx, cy), shade(speck, 1.4) + (255,))
    return im

def frostgranite(seed, kind):
    base = hexc('#9a5b4c')
    im = stone_like(base, seed, 0.18)
    r = random.Random(seed)
    if kind == 'raw':
        x = r.randrange(16)
        for y in range(16):  # frost vein
            x = max(0, min(15, x + r.choice([-1, 0, 1])))
            im.putpixel((x, y), (215, 235, 250, 255))
            if r.random() < 0.4: im.putpixel((min(15, x + 1), y), (180, 210, 235, 255))
    elif kind == 'polished':
        im = noise_tex(base, 0.06, seed)
        for i in range(16):
            im.putpixel((i, 0), shade(base, 1.2) + (255,)); im.putpixel((0, i), shade(base, 1.2) + (255,))
            im.putpixel((i, 15), shade(base, 0.7) + (255,)); im.putpixel((15, i), shade(base, 0.7) + (255,))
        for x, y in [(4, 5), (5, 5), (10, 11), (11, 10)]: im.putpixel((x, y), (215, 235, 250, 255))
    else:
        for y in range(16):
            for x in range(16):
                if y % 8 == 7 or (x + (8 if (y // 8) % 2 else 0)) % 16 == 0:
                    im.putpixel((x, y), shade(base, 0.6) + (255,))
        for x in range(2, 6): im.putpixel((x, 3), (215, 235, 250, 255))
    return im

def planks(base, seed):
    im = noise_tex(base, 0.07, seed)
    for y in range(16):
        for x in range(16):
            if y % 4 == 3: im.putpixel((x, y), shade(base, 0.7) + (255,))
            elif (x + (y // 4) * 5) % 16 == 0: im.putpixel((x, y), shade(base, 0.75) + (255,))
    return im

def log_side(base, seed):
    im = noise_tex(base, 0.08, seed); r = random.Random(seed)
    for x in range(16):
        f = 0.75 + 0.4 * r.random()
        for y in range(16):
            if r.random() < 0.8: im.putpixel((x, y), shade(base, f * (0.92 + 0.16 * r.random())) + (255,))
    return im

def log_top(bark, inner, seed):
    im = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            d = max(abs(x - 7.5), abs(y - 7.5))
            c = bark if d > 6.5 else shade(inner, 0.85 if int(d) % 2 else 1.05)
            im.putpixel((x, y), c + (255,))
    return im

def ice(base, seed, grit=False):
    im = noise_tex(base, 0.05, seed); r = random.Random(seed)
    for _ in range(6):
        x, y = r.randrange(16), r.randrange(16)
        for i in range(r.randrange(2, 6)):
            if 0 <= x + i < 16 and 0 <= y + i < 16: im.putpixel((x + i, y + i), (235, 245, 255, 255))
    if grit:
        for _ in range(30): im.putpixel((r.randrange(16), r.randrange(16)), (120, 130, 150, 255))
    return im

def glow(base, core, seed):
    im = noise_tex(base, 0.05, seed)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 5: im.putpixel((x, y), mix(core, (255, 255, 255), max(0, 1 - d / 5) * 0.6) + (255,))
    for i in range(16):
        for c in ((i, 0), (0, i), (i, 15), (15, i)): im.putpixel(c, shade(base, 0.6) + (255,))
    return im

def coil(lit):
    base = hexc('#b87333')
    im = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            c = shade(base, 1.15 if y % 3 == 0 else 0.8)
            if x in (0, 15): c = shade(base, 0.5)
            if lit and (x + y * 3) % 7 == 0: c = (170, 255, 255)
            im.putpixel((x, y), c + (255,))
    return im

def barnacle():
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); r = random.Random(5)
    for _ in range(9):
        cx, cy = r.randrange(3, 13), r.randrange(6, 15)
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                if abs(dx) + abs(dy) < 2: im.putpixel((cx + dx, cy + dy), (226, 220, 205, 255) if (dx or dy) else (70, 60, 60, 255))
    return im

def flags_anim():
    cols = [(40, 90, 200), (240, 240, 240), (200, 40, 40), (40, 160, 70), (240, 200, 40)]
    frames = 4
    im = Image.new('RGBA', (16, 16 * frames), (0, 0, 0, 0))
    for f in range(frames):
        for x in range(16):
            im.putpixel((x, f * 16 + 1), (200, 190, 170, 255))
        for i, c in enumerate(cols):
            x0 = 1 + i * 3
            for dx in range(3):
                sway = int(round(1.2 * __import__('math').sin((f + i + dx * 0.5) * 1.6)))
                for y in range(2, 9):
                    yy = f * 16 + y + (sway if y > 4 else 0)
                    if x0 + dx < 16 and yy < f * 16 + 16: im.putpixel((x0 + dx, yy), shade(c, 0.9 + 0.1 * dx) + (255,))
    return im, frames

def rift():
    im = Image.new('RGBA', (16, 16 * 4), (0, 0, 0, 0))
    import math
    for f in range(4):
        for x in range(16):
            for y in range(16):
                a = math.atan2(y - 7.5, x - 7.5) + f * 0.8; d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if d < 7.5:
                    v = 0.5 + 0.5 * math.sin(a * 3 + d)
                    im.putpixel((x, f * 16 + y), (int(120 + 100 * v), int(40 + 60 * v), 220, int(150 + 80 * (1 - d / 8))))
    return im

# ---------------------------------------------------------------- item textures
def canvas(): return Image.new('RGBA', (16, 16), (0, 0, 0, 0))
def px(im, pts, c):
    for x, y in pts:
        if 0 <= x < 16 and 0 <= y < 16: im.putpixel((x, y), tuple(c[:3]) + (255,))
def outline(im):
    out = im.copy()
    for x in range(16):
        for y in range(16):
            if im.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and im.getpixel((x + dx, y + dy))[3] > 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), (30, 30, 36, 255))
    return out
def disc(im, cx, cy, r, c, hi=None):
    for x in range(16):
        for y in range(16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= r: im.putpixel((x, y), (shade(c, 1.3) if hi and x + y < cx + cy - r * 0.5 else c) + (255,))

def gear_icon(c):
    im = canvas(); import math
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5; a = math.atan2(y - 7.5, x - 7.5)
            tooth = (math.cos(a * 8) > 0.3)
            if 2.2 < d < 5.5 or (5.5 <= d < 7.2 and tooth): im.putpixel((x, y), shade(c, 1.2 if y < 8 else 0.85) + (255,))
    return outline(im)
def gem(c):
    im = canvas()
    for y in range(4, 13):
        w = 4 - abs(y - 8) // 1
        for x in range(8 - w, 8 + w): im.putpixel((x, y), shade(c, 1.35 if x < 8 and y < 8 else 0.9) + (255,))
    return outline(im)
def nugget(c):
    im = canvas(); disc(im, 7.5, 8, 4.2, c, True); return outline(im)
def key(c):
    im = canvas(); disc(im, 5, 5, 3, c, True)
    px(im, [(4, 4), (5, 5), (4, 5), (5, 4)], (0, 0, 0)); im.putpixel((4, 4), (0, 0, 0, 0)); im.putpixel((5, 5), (0, 0, 0, 0)); im.putpixel((4, 5), (0, 0, 0, 0)); im.putpixel((5, 4), (0, 0, 0, 0))
    px(im, [(6 + i, 6 + i) for i in range(8)] + [(7 + i, 6 + i) for i in range(7)], shade(c, 0.9))
    px(im, [(11, 13), (12, 12), (13, 11), (10, 12)], shade(c, 0.8)); return outline(im)
def stick_tool(head_pts, head_c, handle=(110, 80, 50)):
    im = canvas()
    px(im, [(2 + i, 13 - i) for i in range(9)], handle)
    px(im, head_pts, head_c)
    for x, y in head_pts[:len(head_pts) // 2]:
        if 0 <= x < 16 and 0 <= y < 16: im.putpixel((x, y), shade(head_c, 1.3) + (255,))
    return outline(im)
def sword(c):
    im = canvas()
    px(im, [(5 + i, 10 - i) for i in range(9)] + [(6 + i, 10 - i) for i in range(8)], c)
    px(im, [(5 + i, 10 - i) for i in range(9)], shade(c, 1.3))
    px(im, [(3, 10), (4, 11), (5, 12), (6, 13)], (90, 70, 50)); px(im, [(2, 13), (3, 12)], (80, 60, 40)); px(im, [(2, 9), (3, 10), (5, 12), (6, 13), (4, 9), (6, 11)], (60, 60, 70))
    return outline(im)
def pickaxe(c):
    return stick_tool([(3, 3), (4, 2), (5, 2), (6, 2), (7, 2), (8, 2), (9, 3), (10, 3), (11, 4), (12, 5), (12, 6), (13, 7), (13, 8), (2, 4)], c)
def axe(c):
    return stick_tool([(7, 2), (8, 2), (9, 2), (10, 3), (11, 4), (11, 5), (10, 6), (9, 6), (8, 5), (7, 4), (8, 3), (9, 3), (9, 4), (10, 4), (10, 5), (9, 5)], c)
def shovel(c):
    return stick_tool([(9, 3), (10, 2), (11, 2), (12, 3), (12, 4), (11, 5), (10, 5), (11, 3), (11, 4), (10, 3), (10, 4)], c)
def hoe(c):
    return stick_tool([(8, 2), (9, 2), (10, 2), (11, 2), (11, 3), (7, 3), (8, 3)], c)
def trident(c):
    im = canvas()
    px(im, [(2 + i, 13 - i) for i in range(10)], shade(c, 0.8))
    px(im, [(11, 2), (12, 1), (12, 4), (13, 3), (10, 1), (9, 0), (14, 5), (15, 6), (11, 3), (12, 2), (13, 4)], c)
    return outline(im)
def armor_icon(kind, c):
    im = canvas()
    shapes = {
        'helmet': [(x, y) for x in range(3, 13) for y in range(3, 9) if not (y > 5 and 6 <= x <= 9)],
        'chestplate': [(x, y) for x in range(2, 14) for y in range(2, 14) if (y < 6 or 4 <= x <= 11) and not (y < 4 and 6 <= x <= 9)],
        'leggings': [(x, y) for x in range(3, 13) for y in range(2, 14) if y < 6 or x < 7 or x > 8],
        'boots': [(x, y) for x in range(2, 14) for y in range(6, 13) if (x < 7 or x > 8) and not (y < 10 and (x < 4 or x > 11))],
    }
    px(im, shapes[kind], c)
    for x, y in shapes[kind]:
        if y == min(yy for _, yy in shapes[kind]) or x == min(xx for xx, _ in shapes[kind]): im.putpixel((x, y), shade(c, 1.3) + (255,))
    return outline(im)
def food(c, kind):
    im = canvas()
    if kind == 'meat':
        disc(im, 8, 8, 5, c, True); px(im, [(11, 4), (12, 3), (13, 3), (13, 2)], (240, 235, 220))
    elif kind == 'bowl':
        for x in range(2, 14):
            for y in range(7, 13):
                if (x - 7.5) ** 2 / 36 + (y - 7) ** 2 / 36 <= 1: im.putpixel((x, y), (120, 85, 55, 255))
        px(im, [(x, 7) for x in range(3, 13)] + [(x, 8) for x in range(4, 12)], c)
    elif kind == 'bucket':
        for x in range(3, 13):
            for y in range(4, 14):
                if 3 + (y - 4) // 4 <= x <= 12 - (y - 4) // 4: im.putpixel((x, y), (190, 190, 200, 255))
        px(im, [(x, 4) for x in range(4, 12)] + [(x, 5) for x in range(4, 12)], c)
    elif kind == 'lung':
        disc(im, 6, 8, 3.5, c, True); disc(im, 10, 8, 3.5, shade(c, 0.85)); px(im, [(8, y) for y in range(2, 7)], (60, 120, 90))
    elif kind == 'gland':
        disc(im, 8, 9, 4.5, c, True); px(im, [(8, 3), (8, 4), (9, 4)], shade(c, 0.6))
    return outline(im)
def horn(c):
    im = canvas()
    for i in range(11):
        w = 1 + i // 4
        for d in range(w): px(im, [(2 + i, 12 - i // 2 - d)], shade(c, 1.1 - i * 0.03))
    return outline(im)
def disc_item(c):
    im = canvas(); disc(im, 7.5, 7.5, 6.5, (30, 30, 34)); disc(im, 7.5, 7.5, 2.5, c); im.putpixel((7, 7), (0, 0, 0, 0)); return im
def lantern(c):
    im = canvas()
    for x in range(4, 12):
        for y in range(4, 14): im.putpixel((x, y), ((70, 70, 80) if x in (4, 11) or y in (4, 13) else c) + (255,))
    px(im, [(7, 2), (8, 2), (7, 3), (8, 3)], (70, 70, 80)); return outline(im)
def feather(c):
    im = canvas(); px(im, [(3 + i, 12 - i) for i in range(10)], (220, 220, 210))
    for i in range(8): px(im, [(4 + i, 9 - i), (5 + i, 10 - i), (6 + i, 11 - i)], c)
    return outline(im)
def leather(c):
    im = canvas()
    for x in range(3, 13):
        for y in range(3, 13):
            if not ((x in (3, 12)) and (y in (3, 12))): im.putpixel((x, y), shade(c, 0.9 + 0.2 * ((x * 7 + y * 3) % 5 == 0)) + (255,))
    return outline(im)
def crown(c):
    im = canvas()
    px(im, [(x, y) for x in range(3, 13) for y in range(8, 12)], c)
    px(im, [(3, 5), (3, 6), (3, 7), (8, 4), (8, 5), (8, 6), (8, 7), (7, 6), (7, 7), (12, 5), (12, 6), (12, 7), (5, 7), (10, 7)], c)
    px(im, [(5, 9), (8, 9), (11, 9)], (200, 40, 40)); return outline(im)
def belt(c):
    im = canvas(); px(im, [(x, y) for x in range(1, 15) for y in range(6, 10)], c)
    px(im, [(x, y) for x in range(6, 10) for y in range(5, 11)], (220, 180, 60)); px(im, [(7, 7), (8, 7), (7, 8), (8, 8)], (90, 60, 30)); return outline(im)
def shield_icon(c):
    im = canvas()
    for x in range(3, 13):
        for y in range(2, 14):
            if y < 10 or abs(x - 7.5) < 13.5 - y: im.putpixel((x, y), (shade(c, 1.2) if x < 8 else c) + (255,))
    px(im, [(7, y) for y in range(3, 13)] + [(8, y) for y in range(3, 13)], (220, 190, 80)); return outline(im)
def longbow(c):
    im = canvas()
    import math
    for t in range(0, 15):
        a = -1.1 + t * 2.2 / 14
        x = int(round(3 + 9 * math.cos(a) * 0.55 + 2)); y = int(round(7.5 + 7 * math.sin(a)))
        px(im, [(x, y), (x - 1, y)], c)
    px(im, [(3, y) for y in range(1, 15)], (230, 230, 220))
    px(im, [(9, 7), (10, 7), (9, 8)], (176, 72, 42))
    return outline(im)
def compass(c):
    im = canvas(); disc(im, 7.5, 7.5, 6, (150, 110, 60)); disc(im, 7.5, 7.5, 4.5, (40, 40, 50))
    px(im, [(7, 4), (8, 5), (8, 6), (7, 7), (8, 8)], c); return outline(im)
def egg(c1, c2, seed):
    im = canvas(); r = random.Random(seed)
    for x in range(16):
        for y in range(16):
            if ((x - 7.5) / 5) ** 2 + ((y - 8.5) / 6.5) ** 2 <= 1: im.putpixel((x, y), (c2 if r.random() < 0.2 else c1) + (255,))
    return outline(im)

# ---------------------------------------------------------------- models
def bmodel(name, parent, textures):
    wj(f'assets/elsewhere/models/block/{name}.json', {"parent": parent, "textures": textures})
def bstate(name, obj): wj(f'assets/elsewhere/blockstates/{name}.json', obj)
def simple_state(name, model=None): bstate(name, {"variants": {"": {"model": model or f"elsewhere:block/{name}"}}})
def item_def(name, model): wj(f'assets/elsewhere/items/{name}.json', {"model": {"type": "minecraft:model", "model": model}})
def gen_item(name, handheld=False, tex=None):
    wj(f'assets/elsewhere/models/item/{name}.json', {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": {"layer0": f"elsewhere:item/{tex or name}"}})
    item_def(name, f"elsewhere:item/{name}")

def cube_block(name, tex_img=None, tex=None, render=None):
    if tex_img is not None: save(tex_img, 'block', tex or name)
    m = {"parent": "minecraft:block/cube_all", "textures": {"all": f"elsewhere:block/{tex or name}"}}
    wj(f'assets/elsewhere/models/block/{name}.json', m)
    simple_state(name); item_def(name, f"elsewhere:block/{name}")

def copy_vanilla(kind, vname, newname, subs):
    src = os.path.join(MC, kind, vname + '.json')
    s = open(src).read()
    for a, b in subs: s = s.replace(a, b)
    p = os.path.join(A, kind, newname + '.json')
    os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w').write(s)

def stone_family(full, stairs=None, slab=None, wall=None):
    subs = lambda v: [(f'minecraft:block/stone_bricks', f'elsewhere:block/{full}'), (f'minecraft:block/stone_brick_', f'elsewhere:block/{v}_'), ('"minecraft:block/stone_brick', f'"elsewhere:block/{v}')]
    for kind, val in (('stairs', stairs), ('slab', slab), ('wall', wall)):
        if not val: continue
        stem = val.rsplit('_', 1)[0]
        S = [('minecraft:block/stone_bricks', f'elsewhere:block/{full}'), ('minecraft:block/stone_brick_' + kind, f'elsewhere:block/{val}')]
        copy_vanilla('blockstates', 'stone_brick_' + kind, val, S)
        files = {'stairs': ['', '_inner', '_outer'], 'slab': ['', '_top'], 'wall': ['_post', '_side', '_side_tall', '_inventory']}[kind]
        for suf in files:
            copy_vanilla('models/block', 'stone_brick_' + kind + suf, val + suf, S)
        item_def(val, f"elsewhere:block/{val}" + ('_inventory' if kind == 'wall' else ''))

def wood_family():
    S = [('minecraft:block/stripped_mangrove', 'elsewhere:block/stripped_kelpwood'), ('minecraft:block/mangrove', 'elsewhere:block/kelpwood'), ('minecraft:item/mangrove', 'elsewhere:item/kelpwood')]
    for b in ['mangrove_log', 'stripped_mangrove_log', 'mangrove_wood', 'stripped_mangrove_wood', 'mangrove_planks', 'mangrove_stairs', 'mangrove_slab', 'mangrove_fence', 'mangrove_fence_gate', 'mangrove_door', 'mangrove_trapdoor', 'mangrove_button', 'mangrove_pressure_plate']:
        new = b.replace('mangrove', 'kelpwood')
        copy_vanilla('blockstates', b, new, S)
        copy_vanilla('items', b, new, S)
    for f in os.listdir(os.path.join(MC, 'models/block')):
        if f.startswith('mangrove_') or f.startswith('stripped_mangrove_'):
            if 'propagule' in f or 'leaves' in f or 'roots' in f or 'sign' in f: continue
            copy_vanilla('models/block', f[:-5], f[:-5].replace('mangrove', 'kelpwood'), S)
    copy_vanilla('models/item', 'mangrove_door', 'kelpwood_door', S)

def main():
    random.seed(7)
    copper, verd = hexc('#4f9a86'), hexc('#3d8a78')
    # Verdigris plate family
    cube_block('verdigris_plate', plate(verd, False, 1))
    cube_block('riveted_verdigris_plate', plate(verd, True, 2))
    stone_family('verdigris_plate', 'verdigris_plate_stairs', 'verdigris_plate_slab', 'verdigris_plate_wall')
    save(grating(hexc('#4a9886')), 'block', 'verdigris_grating')
    wj('assets/elsewhere/models/block/verdigris_grating.json', {"parent": "minecraft:block/cube_all", "render_type": "minecraft:cutout", "textures": {"all": "elsewhere:block/verdigris_grating"}})
    simple_state('verdigris_grating'); item_def('verdigris_grating', 'elsewhere:block/verdigris_grating')
    save(pipe(hexc('#4a9886')), 'block', 'verdigris_pipe'); save(plate(verd, True, 4), 'block', 'verdigris_pipe_end')
    wj('assets/elsewhere/models/block/verdigris_pipe.json', {"parent": "minecraft:block/block", "textures": {"side": "elsewhere:block/verdigris_pipe", "end": "elsewhere:block/verdigris_pipe_end", "particle": "elsewhere:block/verdigris_pipe"},
        "elements": [{"from": [3, 0, 3], "to": [13, 16, 13], "faces": {"north": {"texture": "#side"}, "south": {"texture": "#side"}, "east": {"texture": "#side"}, "west": {"texture": "#side"},
                      "up": {"texture": "#end", "uv": [3, 3, 13, 13]}, "down": {"texture": "#end", "uv": [3, 3, 13, 13]}}}]})
    bstate('verdigris_pipe', {"variants": {"axis=y": {"model": "elsewhere:block/verdigris_pipe"}, "axis=x": {"model": "elsewhere:block/verdigris_pipe", "x": 90, "y": 90}, "axis=z": {"model": "elsewhere:block/verdigris_pipe", "x": 90}}})
    item_def('verdigris_pipe', 'elsewhere:block/verdigris_pipe')
    junction = plate(verd, True, 5)
    for x in range(5, 11):
        for y in range(5, 11): junction.putpixel((x, y), shade(verd, 0.45 if 6 <= x <= 9 and 6 <= y <= 9 else 1.3) + (255,))
    cube_block('verdigris_pipe_junction', junction)
    save(coil(False), 'block', 'storm_coil'); save(coil(True), 'block', 'storm_coil_charged')
    bmodel('storm_coil', 'minecraft:block/cube_column', {"side": "elsewhere:block/storm_coil", "end": "elsewhere:block/verdigris_pipe_end"})
    bmodel('storm_coil_charged', 'minecraft:block/cube_column', {"side": "elsewhere:block/storm_coil_charged", "end": "elsewhere:block/verdigris_pipe_end"})
    bstate('storm_coil', {"variants": {f"power={i}": {"model": "elsewhere:block/storm_coil" + ("_charged" if i else "")} for i in range(16)}})
    item_def('storm_coil', 'elsewhere:block/storm_coil')
    # Kelpwood
    bark, wood = hexc('#2f4a32'), hexc('#5d7a55')
    save(log_side(bark, 11), 'block', 'kelpwood_log'); save(log_top(bark, wood, 12), 'block', 'kelpwood_log_top')
    save(log_side(wood, 13), 'block', 'stripped_kelpwood_log'); save(log_top(wood, shade(wood, 1.1), 14), 'block', 'stripped_kelpwood_log_top')
    save(planks(wood, 15), 'block', 'kelpwood_planks')
    door_t = planks(shade(wood, 0.9), 16)
    for y in range(16):
        for x in (0, 15): door_t.putpixel((x, y), shade(wood, 0.6) + (255,))
    door_b = door_t.copy()
    for x in range(4, 12):
        for y in range(3, 7): door_t.putpixel((x, y), (60, 110, 100, 255))
    save(door_t, 'block', 'kelpwood_door_top'); save(door_b, 'block', 'kelpwood_door_bottom')
    trap = planks(wood, 17)
    for x in range(3, 13):
        for y in (4, 5, 10, 11): trap.putpixel((x, y), (0, 0, 0, 0))
    save(trap, 'block', 'kelpwood_trapdoor')
    ditem = canvas()
    for x in range(4, 12):
        for y in range(1, 15): ditem.putpixel((x, y), shade(wood, 0.95 if y % 4 else 0.7) + (255,))
    save(outline(ditem), 'item', 'kelpwood_door')
    wood_family()
    # Barnacle, ore
    save(barnacle(), 'block', 'barnacle_cluster')
    wj('assets/elsewhere/models/block/barnacle_cluster.json', {"parent": "minecraft:block/cross", "render_type": "minecraft:cutout", "textures": {"cross": "elsewhere:block/barnacle_cluster"}})
    simple_state('barnacle_cluster'); gen_item('barnacle_cluster', tex=None) if False else None
    wj('assets/elsewhere/models/item/barnacle_cluster.json', {"parent": "minecraft:item/generated", "textures": {"layer0": "elsewhere:block/barnacle_cluster"}}); item_def('barnacle_cluster', 'elsewhere:item/barnacle_cluster')
    cube_block('pearlite_ore', ore(hexc('#7d7f7a'), hexc('#e8e0f5'), 21))
    cube_block('drowned_organ', plate(hexc('#2e6f60'), True, 22))
    cube_block('lighthouse_lantern', glow(hexc('#3d8a78'), (255, 250, 210), 23))
    # Ironpeak
    cube_block('frostgranite', frostgranite(31, 'raw'))
    cube_block('polished_frostgranite', frostgranite(32, 'polished'))
    cube_block('frostgranite_bricks', frostgranite(33, 'bricks'))
    stone_family('frostgranite', 'frostgranite_stairs', 'frostgranite_slab', 'frostgranite_wall')
    stone_family('polished_frostgranite', 'polished_frostgranite_stairs', 'polished_frostgranite_slab')
    stone_family('frostgranite_bricks', 'frostgranite_brick_stairs', 'frostgranite_brick_slab', 'frostgranite_brick_wall')
    cube_block('skyiron_ore', ore(hexc('#5d6470'), hexc('#a8c8f0'), 34))
    cube_block('hearthstone', glow(hexc('#6b4a3a'), (255, 150, 60), 35))
    cube_block('glacier_ice', ice(hexc('#74a8e8'), 36, True))
    fl, frames = flags_anim(); save(fl, 'block', 'prayer_flags')
    with open(os.path.join(A, 'textures/block/prayer_flags.png.mcmeta'), 'w') as f: json.dump({"animation": {"frametime": 6, "interpolate": True}}, f)
    wj('assets/elsewhere/models/block/prayer_flags.json', {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": {"flags": "elsewhere:block/prayer_flags", "particle": "elsewhere:block/prayer_flags"},
        "elements": [{"from": [0, 6, 8], "to": [16, 16, 8], "faces": {"north": {"texture": "#flags", "uv": [0, 0, 16, 10]}, "south": {"texture": "#flags", "uv": [0, 0, 16, 10]}}}]})
    bstate('prayer_flags', {"variants": {"facing=north": {"model": "elsewhere:block/prayer_flags"}, "facing=south": {"model": "elsewhere:block/prayer_flags", "y": 180},
                                          "facing=east": {"model": "elsewhere:block/prayer_flags", "y": 90}, "facing=west": {"model": "elsewhere:block/prayer_flags", "y": 270}}})
    pf_item = canvas()
    for x, c in zip(range(1, 16, 3), [(40, 90, 200), (240, 240, 240), (200, 40, 40), (40, 160, 70), (240, 200, 40)]):
        for dx in range(2):
            for y in range(4, 11): pf_item.putpixel((x + dx, y), c + (255,))
    for x in range(16): pf_item.putpixel((x, 3), (200, 190, 170, 255))
    save(pf_item, 'item', 'prayer_flags'); gen_item('prayer_flags')
    drum_side = planks(hexc('#7a4a2a'), 37)
    for y in range(16):
        for x in range(16):
            if y in (1, 2, 13, 14): drum_side.putpixel((x, y), (180, 40, 40, 255))
    save(drum_side, 'block', 'summit_war_drum'); save(noise_tex(hexc('#d8c8a8'), 0.05, 38), 'block', 'summit_war_drum_top')
    wj('assets/elsewhere/models/block/summit_war_drum.json', {"parent": "minecraft:block/block", "textures": {"side": "elsewhere:block/summit_war_drum", "top": "elsewhere:block/summit_war_drum_top", "particle": "elsewhere:block/summit_war_drum"},
        "elements": [{"from": [1, 0, 1], "to": [15, 14, 15], "faces": {d: {"texture": "#side", "uv": [1, 2, 15, 16]} for d in ("north", "south", "east", "west")} | {"up": {"texture": "#top"}, "down": {"texture": "#top"}}}]})
    simple_state('summit_war_drum'); item_def('summit_war_drum', 'elsewhere:block/summit_war_drum')
    horn_tex = noise_tex(hexc('#cfc4a8'), 0.08, 39)
    for y in range(0, 16, 3):
        for x in range(16): horn_tex.putpixel((x, y), (90, 70, 50, 255))
    save(horn_tex, 'block', 'proving_challenge_horn')
    wj('assets/elsewhere/models/block/proving_challenge_horn.json', {"parent": "minecraft:block/block", "textures": {"t": "elsewhere:block/proving_challenge_horn", "particle": "elsewhere:block/proving_challenge_horn"},
        "elements": [{"from": [6, 0, 6], "to": [10, 12, 10], "faces": {d: {"texture": "#t"} for d in ("north", "south", "east", "west", "up", "down")}},
                     {"from": [3, 10, 5], "to": [13, 14, 11], "faces": {d: {"texture": "#t"} for d in ("north", "south", "east", "west", "up", "down")}}]})
    simple_state('proving_challenge_horn'); item_def('proving_challenge_horn', 'elsewhere:block/proving_challenge_horn')
    # Return rift (stub)
    save(rift(), 'block', 'return_rift')
    with open(os.path.join(A, 'textures/block/return_rift.png.mcmeta'), 'w') as f: json.dump({"animation": {"frametime": 3, "interpolate": True}}, f)
    wj('assets/elsewhere/models/block/return_rift.json', {"parent": "minecraft:block/cross", "render_type": "minecraft:translucent", "textures": {"cross": "elsewhere:block/return_rift"}})
    simple_state('return_rift')

    # ---------------------------------------------------------------- items
    pearl, tide, sky, copper_c = hexc('#e6dcf2'), hexc('#3fb3a0'), hexc('#a9c9f2'), hexc('#c07a45')
    items = {
        'verdigris_gear': gear_icon(hexc('#4fae95')), 'pearlite': gem(pearl), 'ray_leather': leather(hexc('#2d4f5a')), 'scraped_patina': nugget(hexc('#6fd0b0')),
        'tide_key': key(hexc('#3fb3a0')), 'brine_lung': food(hexc('#5aa070'), 'lung'), 'music_disc_low_tide_engine': disc_item(hexc('#3fb3a0')),
        'stormcaller_harpoon': trident(hexc('#5ab5a5')), 'galvanic_trident': trident(hexc('#f2e66a')), 'engineers_lantern': lantern((255, 236, 160)),
        'hymnal_horn': horn(hexc('#3d8a78')), 'diving_helm': armor_icon('helmet', copper_c), 'verdigris_mitre': crown(hexc('#3fb3a0')),
        'skyiron': nugget(sky), 'roc_feather': feather(hexc('#b0482a')), 'yak_meat': food(hexc('#b04a40'), 'meat'), 'cooked_yak_meat': food(hexc('#7a4a30'), 'meat'),
        'yak_milk': food((245, 240, 225), 'bucket'), 'yak_butter_tea': food(hexc('#d8b070'), 'bowl'), 'ironjaw_gland': food(hexc('#8a3050'), 'gland'),
        'music_disc_thin_air': disc_item(hexc('#a9c9f2')), 'ice_axe': axe(hexc('#bfe0ff')), 'crampons': armor_icon('boots', hexc('#8a8d91')),
        'avalanche_horn': horn(hexc('#e8e8f0')), 'champions_belt': belt(hexc('#6a3a20')), 'summit_crown': crown(hexc('#c0c4cc')), 'kingsplitter': axe(hexc('#7a7f8a')),
        'aegis_of_the_ladder': shield_icon(hexc('#6070a0')), 'phasewalker_greaves': armor_icon('leggings', hexc('#5a3a7a')), 'stormcrest_helm': armor_icon('helmet', hexc('#40c0e0')),
        'frayed_wayfinder': compass(hexc('#b090e0')), 'roc_feather_longbow': longbow(hexc('#7a5230')),
    }
    handheld = {'stormcaller_harpoon', 'galvanic_trident', 'ice_axe', 'kingsplitter', 'roc_feather_longbow'}
    for n, im in items.items():
        save(im, 'item', n); gen_item(n, n in handheld)
    for mat, c in (('tidesteel', hexc('#4fc0b0')), ('skyiron', sky)):
        for t, f in (('sword', sword), ('pickaxe', pickaxe), ('axe', axe), ('shovel', shovel), ('hoe', hoe)):
            save(f(c), 'item', f'{mat}_{t}'); gen_item(f'{mat}_{t}', True)
    for mat, c in (('diving_suit', copper_c), ('climber', hexc('#8a6a4a'))):
        for t in ('helmet', 'chestplate', 'leggings', 'boots'):
            save(armor_icon(t, c), 'item', f'{mat}_{t}'); gen_item(f'{mat}_{t}')
    # Equipment layers (humanoid armor textures, 64x32): tinted from vanilla layer shapes.
    for asset, c, src in (('diving_suit', copper_c, 'iron'), ('climber', hexc('#8a6a4a'), 'leather'), ('diving_helm', copper_c, 'iron'), ('verdigris_mitre', hexc('#3fb3a0'), 'gold'),
                          ('crampons', hexc('#8a8d91'), 'leather'), ('champions_belt', hexc('#6a3a20'), 'leather'), ('summit_crown', hexc('#c0c4cc'), 'gold'),
                          ('phasewalker', hexc('#5a3a7a'), 'netherite'), ('stormcrest', hexc('#40c0e0'), 'netherite')):
        layers = {}
        for layer, folder in (('humanoid', 'humanoid'), ('humanoid_leggings', 'humanoid_leggings')):
            sp = os.path.join(MC, 'textures/entity/equipment', folder, src + '.png')
            if os.path.exists(sp):
                im = Image.open(sp).convert('RGBA'); out = Image.new('RGBA', im.size)
                for x in range(im.width):
                    for y in range(im.height):
                        p = im.getpixel((x, y))
                        if p[3]:
                            l = (0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]) / 255
                            out.putpixel((x, y), shade(c, 0.45 + l * 0.9) + (p[3],))
                os.makedirs(os.path.join(A, 'textures/entity/equipment', folder), exist_ok=True)
                out.save(os.path.join(A, 'textures/entity/equipment', folder, asset + '.png'))
                layers[layer] = [{"texture": f"elsewhere:{asset}"}]
        wj(f'assets/elsewhere/equipment/{asset}.json', {"layers": layers})
    # Spawn-egg-less; entity textures
    entity_textures()
    lang()

def gradient_map(src, dst, stops, seed=0, emissive=None):
    im = Image.open(os.path.join(MC, 'textures/entity', src)).convert('RGBA'); out = Image.new('RGBA', im.size)
    cols = [hexc(c) for c in stops]
    for x in range(im.width):
        for y in range(im.height):
            p = im.getpixel((x, y))
            if not p[3]: continue
            l = (0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]) / 255
            t = l * (len(cols) - 1); i = min(int(t), len(cols) - 2)
            out.putpixel((x, y), mix(cols[i], cols[i + 1], t - i) + (p[3],))
    p = os.path.join(A, 'textures/entity', dst + '.png'); os.makedirs(os.path.dirname(p), exist_ok=True); out.save(p)

def entity_textures():
    gradient_map('zombie/drowned.png', 'verdigrim', ['#08201c', '#1f5a4e', '#3fb39a', '#c8f0e0'])
    gradient_map('zombie/drowned.png', 'clockwork_drowned', ['#1a1410', '#6a4020', '#c07a45', '#5fd0b0'])
    gradient_map('zombie/zombie.png', 'thurgrim', ['#141418', '#4a3a30', '#8a8f9a', '#e0e4ea'])
    gradient_map('zombie/zombie.png', 'hrolf', ['#1a1210', '#5a3020', '#9a6a4a', '#d0c0a0'])
    gradient_map('zombie/zombie.png', 'ironclan_raider', ['#141414', '#4a3424', '#8a7a6a', '#c8ccd2'])
    gradient_map('iron_golem/iron_golem.png', 'galvanox', ['#0a2420', '#246a5a', '#5fc0a0', '#f0fff0'])
    gradient_map('iron_golem/iron_golem.png', 'barnacle_golem', ['#201a14', '#6a5a40', '#a89a7a', '#e8e0d0'])
    gradient_map('iron_golem/iron_golem.png', 'rimeguard', ['#14181e', '#4a5a6a', '#9ab0c8', '#f0f8ff'])
    gradient_map('goat/goat.png', 'grimhorn', ['#100c0a', '#4a3020', '#8a6a50', '#e0d4c0'])
    gradient_map('cow/cow_cold.png', 'highland_yak', ['#140c08', '#4a2a18', '#8a5a34', '#d8b890'])
    gradient_map('phantom/phantom.png', 'tidewing_ray', ['#04100e', '#0e3a34', '#2a8a78', '#9af0e0'])
    gradient_map('phantom/phantom.png', 'crag_roc', ['#1a0c06', '#6a2a10', '#c06a2a', '#f0d080'])
    gradient_map('guardian/guardian.png', 'brass_guardian', ['#1a1006', '#6a4a18', '#c0943a', '#fff0b0'])

def title(s):
    return ' '.join(w.capitalize() for w in s.split('_'))

def lang():
    L = {}
    import glob
    names = {'kelpwood_wood': 'Kelpwood Wood', 'music_disc_low_tide_engine': 'Music Disc', 'music_disc_thin_air': 'Music Disc', 'engineers_lantern': "Engineer's Lantern",
             'champions_belt': "Champion's Belt", 'roc_feather_longbow': 'Roc-Feather Longbow', 'climbers_cairn': "Climber's Cairn", 'yak_butter_tea': 'Yak Butter Tea'}
    for f in glob.glob(os.path.join(A, 'blockstates', '*.json')):
        n = os.path.basename(f)[:-5]; L[f'block.elsewhere.{n}'] = names.get(n, title(n))
    for f in glob.glob(os.path.join(A, 'items', '*.json')):
        n = os.path.basename(f)[:-5]
        if not os.path.exists(os.path.join(A, 'blockstates', n + '.json')): L[f'item.elsewhere.{n}'] = names.get(n, title(n))
    L['item.elsewhere.music_disc_low_tide_engine.desc'] = 'Low Tide Engine'
    L['item.elsewhere.music_disc_thin_air.desc'] = 'Thin Air'
    L['jukebox_song.elsewhere.low_tide_engine'] = 'Elsewhere - Low Tide Engine'
    L['jukebox_song.elsewhere.thin_air'] = 'Elsewhere - Thin Air'
    ents = {'verdigrim': 'Archdeacon Verdigrim, the Drowned Organist', 'galvanox': 'Galvanox, the Lighthouse Engine', 'thurgrim': 'Thurgrim Ironjaw, King of the Summit',
            'hrolf': 'Hrolf Grimhorn, Champion of the Proving Grounds', 'grimhorn': 'Grimhorn', 'clockwork_drowned': 'Clockwork Drowned', 'barnacle_golem': 'Barnacle Golem',
            'tidewing_ray': 'Tidewing Ray', 'brass_guardian': 'Brass Guardian', 'tidewright': 'Tidewright', 'highland_yak': 'Highland Yak', 'ironclan_raider': 'Ironclan Raider',
            'crag_roc': 'Crag Roc', 'rimeguard': 'Rimeguard', 'clan_quartermaster': 'Clan Quartermaster', 'hymn_trident': 'Hymn Trident', 'wax_shell': 'Wax Shell', 'arc_bolt': 'Arc Bolt'}
    for k, v in ents.items(): L[f'entity.elsewhere.{k}'] = v
    for k, v in {'verdigris_sea': 'The Verdigris Sea', 'ironpeak': 'Ironpeak'}.items(): L[f'dimension.elsewhere.{k}'] = v
    for b in ['verdigris_shallows', 'kelpwood_reaches', 'foundry_trench', 'storm_pylons', 'barnacle_atolls', 'iron_foothills', 'granite_terraces', 'frostbite_shelf', 'cold_springs', 'the_thin_air']:
        L[f'biome.elsewhere.{b}'] = title(b)
    L.update({
        'itemGroup.elsewhere.verdigris_ironpeak': 'Elsewhere: Verdigris Sea & Ironpeak',
        'message.elsewhere.drifted': 'You drifted into %s. A Frayed Wayfinder will take you back once.',
        'message.elsewhere.verdigrim.bells': 'The cathedral bells answer (%s of 4).', 'message.elsewhere.verdigrim.organ': 'The organ is silent. Ring all four bells.',
        'message.elsewhere.verdigrim.bell_cooldown': 'This bell still hums (%s s).', 'message.elsewhere.galvanox.waxed': 'The plates are waxed! Shoot down the wax shells.',
        'message.elsewhere.galvanox.call': 'The lantern draws the storm...', 'message.elsewhere.thurgrim.unworthy': 'The drum is silent for you. Defeat Hrolf Grimhorn first.',
        'message.elsewhere.thurgrim.resist': 'You shrug off the Kingly Roar.',
        'attack.elsewhere.verdigrim.slam': 'Crozier Slam', 'attack.elsewhere.verdigrim.flood': 'The Flood Rises', 'attack.elsewhere.verdigrim.requiem': 'Requiem',
        'attack.elsewhere.verdigrim.chord': 'The Last Chord - play it back!', 'attack.elsewhere.verdigrim.stunned': 'Verdigrim is stunned!',
        'attack.elsewhere.galvanox.hammer': 'Overhead Hammer', 'attack.elsewhere.galvanox.arc': 'Arc', 'attack.elsewhere.galvanox.chain': 'Chain Lightning - spread out!',
        'attack.elsewhere.galvanox.wax': 'Wax Shell - shoot it down!', 'attack.elsewhere.thurgrim.split': 'Overhead Split', 'attack.elsewhere.thurgrim.roar': 'Kingly Roar',
        'attack.elsewhere.thurgrim.avalanche_split': 'Avalanche Split', 'attack.elsewhere.thurgrim.gust': 'Gust %s - sneak to brace!',
        'attack.elsewhere.hrolf.horn': 'Horn Blast', 'attack.elsewhere.hrolf.overhead': 'Overhead Axe', 'attack.elsewhere.hrolf.kick': 'Shield-Break Kick',
        'attack.elsewhere.hrolf.warcry': 'War-Cry', 'attack.elsewhere.hrolf.remount': 'Hrolf remounts Grimhorn!', 'attack.elsewhere.hrolf.berserk': 'Hrolf berserks!',
        'set.elsewhere.diving_suit': 'Full set: Diving Suit', 'set.elsewhere.diving_suit.1': ' Water breathing', 'set.elsewhere.diving_suit.2': ' Walk on the sea floor',
        'hud.elsewhere.altitude': 'Altitude', 'hud.elsewhere.thin_air': 'THIN AIR',
    })
    wj('assets/elsewhere/lang/en_us.json', dict(sorted(L.items())))

main()
print('assets ok')
