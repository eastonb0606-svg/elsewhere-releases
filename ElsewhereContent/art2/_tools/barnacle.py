import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from boxpaint import Tex, faces, paint_box, hx
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(A2, 'verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/barnacle_golem.png')
t = Tex(128, 128, os.path.join(A2, 'vanilla/iron_golem.png'))
a = t.a
lum = lambda c: 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]
# weathered copper: copper by luminance rank; crevices and the streaks below them turn verdigris
COP = ['#5e3220', '#77422a', '#8e5133', '#a5613c', '#b97247', '#cb8556', '#d99a6a']
VER = ['#1d3d35', '#285446', '#356d5a', '#46866f', '#5f9f86', '#7db79c']
KELP = ['#1f3a2a', '#2b5236', '#3a6b40', '#4d8448']
SPECIAL = {(0x1e, 0x00, 0x0e): '#0b2e30', (0x8d, 0x00, 0x2a): '#3fe0c8', (0xff, 0xd5, 0x00): '#ffd27a'}
body = set(); vines = set()
for y in range(128):
    for x in range(128):
        if not a[y, x, 3]:
            continue
        c = tuple(int(v) for v in a[y, x, :3])
        if c in SPECIAL:
            continue
        if c[1] > c[0] and c[1] > c[2] and max(c) - min(c) > 60:
            vines.add(c)
        else:
            body.add(c)
bs = sorted(body, key=lum); rank = {c: i / max(1, len(bs) - 1) for i, c in enumerate(bs)}
vs = sorted(vines, key=lum)
crev = np.zeros((128, 128), bool)
for y in range(128):
    for x in range(128):
        if a[y, x, 3]:
            c = tuple(int(v) for v in a[y, x, :3])
            crev[y, x] = c in rank and rank[c] < 0.42
out = a.copy()


def waterline(x, y):
    """Verdigris tide line on the lower part of the arms, legs and waist (the golem wades the rigs)."""
    if 60 <= x < 80 and (51 <= y < 58 or 88 <= y < 95):
        return (x + y) % 4 != 0 or y >= 54 and y < 58 or y >= 91
    if (37 <= x < 59 or 60 <= x < 82) and 15 <= y < 21:
        return (x + y) % 4 != 0 or y >= 18
    if x < 30 and 76 <= y < 81:
        return (x + y) % 3 != 0
    return False

for y in range(128):
    for x in range(128):
        if not a[y, x, 3]:
            continue
        c = tuple(int(v) for v in a[y, x, :3])
        if c in SPECIAL:
            out[y, x, :3] = hx(SPECIAL[c])[:3]
        elif c in vines:
            out[y, x, :3] = hx(KELP[round(vs.index(c) * 3 / max(1, len(vs) - 1))])[:3]
        else:
            r = rank[c]
            streak = (any(crev[y - k, x] for k in (1, 2, 3, 4) if y - k >= 0) and (x % 3 != 1)) or waterline(x, y)
            if waterline(x, y):
                out[y, x, :3] = hx(VER[1 + round(r * 2.4)])[:3]
            elif crev[y, x] or streak:
                out[y, x, :3] = hx(VER[min(5, round(r * 1.6 * 5))])[:3]
            else:
                out[y, x, :3] = hx(COP[min(6, round(r * 6.4) - 0)])[:3]
a[:] = out
# ---------------- barnacles (UVs at x >= 84)
SH = ['#5f5a4e', '#8f8877', '#b9b29c', '#dcd6c2', '#f1ecdc']
HOLE = '#26221e'


def shell(n, i, j, w, h, open_face):
    if n == open_face:
        if w >= 3 and h >= 3:
            if (i, j) == (w // 2, h // 2):
                return HOLE
            if i in (0, w - 1) or j in (0, h - 1):
                return SH[3] if (i + j) % 2 == 0 else SH[2]
            return SH[1]
        return HOLE if (i + j) % 2 == 0 else SH[3]
    if n in ('top', 'bottom'):
        return SH[2] if n == 'top' else SH[0]
    # ridged sides: light ridges, darker base row
    if j == h - 1 and h > 1:
        return SH[1]
    return SH[3] if i % 2 == 0 else SH[2]


paint_box(t, 84, 0, 3, 2, 3, lambda n, i, j, w, h: shell(n, i, j, w, h, 'top'))
paint_box(t, 84, 8, 2, 2, 2, lambda n, i, j, w, h: shell(n, i, j, w, h, 'top'))
paint_box(t, 96, 8, 2, 1, 2, lambda n, i, j, w, h: shell(n, i, j, w, h, 'top'))
paint_box(t, 108, 0, 3, 3, 2, lambda n, i, j, w, h: shell(n, i, j, w, h, 'back'))
paint_box(t, 104, 8, 2, 2, 2, lambda n, i, j, w, h: shell(n, i, j, w, h, 'back'))
paint_box(t, 112, 8, 2, 2, 2, lambda n, i, j, w, h: shell(n, i, j, w, h, 'right'))
t.save(OUT)
print(OUT)
