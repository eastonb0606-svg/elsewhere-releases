#!/usr/bin/env python3
"""P6 weapon state textures (s-p6-useanims): derived 16x16 variants of the final (art2) weapon sprites.
Run from ElsewhereContent:  python3 arsenal/tools/useanim_textures.py [contact_sheet.png]"""
import colorsys, sys
from PIL import Image

A2 = 'art2/arsenal/src/main/resources/assets/elsewhere/textures/item/'
AM = 'arsenal/src/main/resources/assets/elsewhere/textures/item/'
G2 = 'art2/gear2/src/main/resources/assets/elsewhere/textures/item/'
GM = 'gear2/src/main/resources/assets/elsewhere/textures/item/'

def load(p):
    return Image.open(p).convert('RGBA')

def hsv(px):
    r, g, b, a = px
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return h, s, v, a

def rgb(h, s, v, a):
    r, g, b = colorsys.hsv_to_rgb(h, max(0, min(1, s)), max(0, min(1, v)))
    return (round(r * 255), round(g * 255), round(b * 255), a)

def head(x, y, x0=8, y1=7):
    return x >= x0 and y <= y1

def edit(img, fn):
    out = img.copy()
    p = out.load()
    for y in range(16):
        for x in range(16):
            if p[x, y][3] == 0:
                continue
            p[x, y] = fn(x, y, p[x, y])
    return out

def put(img, pts, col):
    p = img.load()
    for x, y in pts:
        p[x, y] = col

def opaque(img):
    p = img.load()
    return {(x, y) for y in range(16) for x in range(16) if p[x, y][3] > 0}

out = {}
# ---- Ironclad Flail, charged: polished head with a hot glint (attack recharged)
fl = load(G2 + 'ironclad_flail.png')
def flail(x, y, c):
    h, s, v, a = hsv(c)
    if head(x, y, 7, 9) and v > 0.35:
        return rgb(h, s * 0.8, v * 1.12 + 0.06, a)
    return c
fc = edit(fl, flail)
body = opaque(fl)
glints = [(x, y) for (x, y) in body if head(x, y, 7, 9) and (x + 1, y - 1) not in body]
put(fc, sorted(glints, key=lambda t: (t[1], -t[0]))[:3], (255, 255, 255, 255))
out[GM + 'ironclad_flail_charged.png'] = fc

# ---- Gale Rapier, lunge ready (sprinting): bright blade + wind streak beside it
ra = load(G2 + 'gale_rapier.png')
def rapier(x, y, c):
    h, s, v, a = hsv(c)
    if x + (15 - y) >= 15 and v > 0.55 and s < 0.5:  # pale blade pixels toward the tip
        return rgb(h, s * 0.7, v * 1.1 + 0.05, a)
    return c
rl = edit(ra, rapier)
body = opaque(ra)
# one dashed wind line parallel to the blade, a pixel diagonal above it (pixels 2 on, 1 off)
streak = []
for (x, y) in sorted(body, key=lambda t: t[0]):
    q = (x - 1, y - 1)
    if 8 <= x <= 14 and y <= 7 and q not in body and 0 <= q[1] and (x % 3) != 0 and q not in streak:
        streak.append(q)
put(rl, streak, (190, 245, 255, 255))
out[GM + 'gale_rapier_lunge.png'] = rl

# ---- instant casters, spent (recharging): the focus dims and greys
def spent(src, x0=8, y1=7):
    def f(x, y, c):
        h, s, v, a = hsv(c)
        if head(x, y, x0, y1):
            return rgb(h, s * 0.45, v * 0.55, a)
        return c
    return edit(load(src), f)
out[AM + 'amethyst_wand_spent.png'] = spent(AM + 'amethyst_wand.png', 9, 6)
out[AM + 'sunlance_staff_spent.png'] = spent(AM + 'sunlance_staff.png', 8, 7)
out[AM + 'stormcoil_staff_spent.png'] = spent(AM + 'stormcoil_staff.png', 8, 7)

# ---- Sculk Resonator charging: the frame lights up from dim teal to bright cyan
res = load(AM + 'sculk_resonator.png')
def charge(level):
    def f(x, y, c):
        h, s, v, a = hsv(c)
        if head(x, y, 6, 9) and v > 0.25:
            return rgb(0.49, s * (1 - 0.15 * level), v * (1 + 0.28 * level) + 0.05 * level, a)
        return c
    return f
out[AM + 'sculk_resonator_charging_0.png'] = edit(res, charge(1))
out[AM + 'sculk_resonator_charging_1.png'] = edit(res, charge(2))
rc = edit(res, charge(3))
body = opaque(res)
core = [(x, y) for (x, y) in body if head(x, y, 6, 9) and (x - 1, y) in body and (x + 1, y) in body and (x, y - 1) in body and (x, y + 1) in body]
put(rc, core[:4], (230, 255, 255, 255))
out[AM + 'sculk_resonator_charged.png'] = rc

# ---- Mirrorglass Prism channel: brighter glass and a sparkle on each side
pr = load(A2 + 'mirrorglass_prism.png')
def prism(x, y, c):
    h, s, v, a = hsv(c)
    return rgb(h, s * 1.15, v * 1.08 + 0.04, a)
pc = edit(pr, prism)
body = opaque(pr)
spark = [(x, y) for (x, y) in [(2, 3), (13, 3), (1, 7), (14, 7)] if (x, y) not in body]
put(pc, spark, (255, 250, 220, 255))
out[AM + 'mirrorglass_prism_channel.png'] = pc

# ---- Hushward Bell ringing: the ward glyph glows and two hush rings show at the sides
be = load(A2 + 'hushward_bell.png')
def bell(x, y, c):
    h, s, v, a = hsv(c)
    if 0.40 < h < 0.56 and s > 0.3:
        return rgb(h, s * 0.85, v * 1.2 + 0.08, a)
    return c
bc = edit(be, bell)
body = opaque(be)
rings = [(x, y) for (x, y) in [(1, 6), (0, 7), (0, 8), (1, 9), (14, 6), (15, 7), (15, 8), (14, 9)] if (x, y) not in body]
put(bc, rings, (120, 235, 225, 255))
out[AM + 'hushward_bell_ringing.png'] = bc

for path, img in out.items():
    img.save(path)
    print('wrote', path)

if len(sys.argv) > 1:
    pairs = [(G2 + 'ironclad_flail.png', GM + 'ironclad_flail_charged.png'), (G2 + 'gale_rapier.png', GM + 'gale_rapier_lunge.png'),
             (AM + 'amethyst_wand.png', AM + 'amethyst_wand_spent.png'), (AM + 'sunlance_staff.png', AM + 'sunlance_staff_spent.png'),
             (AM + 'stormcoil_staff.png', AM + 'stormcoil_staff_spent.png'), (AM + 'sculk_resonator.png', AM + 'sculk_resonator_charging_0.png'),
             (AM + 'sculk_resonator_charging_1.png', AM + 'sculk_resonator_charged.png'), (A2 + 'mirrorglass_prism.png', AM + 'mirrorglass_prism_channel.png'),
             (A2 + 'hushward_bell.png', AM + 'hushward_bell_ringing.png')]
    S = 10
    sheet = Image.new('RGBA', (len(pairs) * (32 * S + 24), 16 * S), (58, 58, 68, 255))
    for i, (a, b) in enumerate(pairs):
        sheet.alpha_composite(load(a).resize((16 * S, 16 * S), Image.NEAREST), (i * (32 * S + 24), 0))
        sheet.alpha_composite(load(b).resize((16 * S, 16 * S), Image.NEAREST), (i * (32 * S + 24) + 16 * S, 0))
    sheet.save(sys.argv[1])
