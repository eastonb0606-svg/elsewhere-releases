import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from boxpaint import Tex, faces, paint_box
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(A2, 'verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/clockwork_drowned.png')
BR = ['#3f2a10', '#6b4a1c', '#9a6e2a', '#c79a3e', '#ecc96a']
CU = ['#4a2416', '#7a3d22', '#a8572f', '#c97a4a', '#e39f6c']
VG = ['#2b5a4c', '#3f8a72', '#62b39a']
ID = ['#151413', '#2e2b29', '#46413c', '#5e5852']
AM = ['#a85a14', '#ffb340', '#ffe9a8']

t = Tex(64, 64, os.path.join(A2, 'vanilla/drowned.png'))


def brass(i, j, w, h, seam=4, verd=()):
    if (i, j) in verd:
        return VG[1]
    r = j % seam
    if r == seam - 1:
        return BR[1]
    if r == 0:
        return BR[4] if i in (0, w - 1) else BR[3]
    return BR[2] if i not in (0, w - 1) else BR[2]


# ---------------- head (0,0): entity's left half is a brass plate with an amber lens
f = faces(0, 0, 8, 8, 8)
x, y, _, _ = f['front']
t.fn(x + 4, y, 4, 8, lambda i, j: BR[1] if i == 0 else (BR[4] if (i, j) in ((3, 0), (3, 7)) else (BR[3] if j == 0 else (BR[1] if j == 7 else BR[2]))))
t.ascii(x + 4, y + 2, ['.aaa', '.aLa' if False else 'aHLa', 'aLLa', '.aa.'], {'a': AM[0], 'L': AM[1], 'H': AM[2]})
x, y, _, _ = f['top']
t.fn(x + 4, y, 4, 8, lambda i, j: BR[1] if i == 0 else (BR[3] if (i + j) % 4 else BR[4]))
t.fn(*f['left'], lambda i, j: brass(i, j, 8, 8, 4, ((2, 5),)) if not (3 <= i <= 4 and 3 <= j <= 4) else (ID[2] if (i + j) % 2 else BR[4]))
x, y, _, _ = f['back']
t.fn(x, y, 4, 8, lambda i, j: BR[1] if i == 3 else brass(i, j, 4, 8))
# ---------------- body (16,16) 8x12x4: brass on the left half, harness straps for the boiler
f = faces(16, 16, 8, 12, 4)
x, y, _, _ = f['front']
t.fn(x + 4, y, 4, 9, lambda i, j: BR[1] if i == 0 else brass(i, j, 4, 9, 9))
t.ascii(x + 4, y + 2, ['.gg.', 'gHgg' if False else 'gccg', 'gccg', '.gg.'], {'g': BR[3], 'c': ID[1]})
for cx in (1, 6):
    t.fn(x + cx, y, 1, 9, lambda i, j: ID[3] if j in (2, 6) else ID[1])
t.fn(x, y + 8, 8, 1, lambda i, j: ID[1] if i not in (3, 4) else BR[4])
x, y, _, _ = f['back']
t.fn(x, y, 4, 9, lambda i, j: BR[1] if i == 3 else brass(i, j, 4, 9, 9))
for cx in (1, 6):
    t.fn(x + cx, y, 1, 9, lambda i, j: ID[3] if j in (2, 6) else ID[1])
t.fn(x, y + 8, 8, 1, lambda i, j: ID[1])
t.fn(*f['left'], lambda i, j: ID[1] if j == 8 else brass(i, j, 4, 12, 4, ((1, 6),)))
x, y, _, _ = f['top']
t.fn(x + 4, y, 4, 4, lambda i, j: BR[3])
t.fn(x + 1, y, 1, 4, lambda i, j: ID[1]); t.fn(x + 6, y, 1, 4, lambda i, j: ID[1])


# ---------------- left arm (32,48) and left leg (16,48): clockwork limbs
def limb(n, i, j, w, h, joints):
    if n == 'top':
        return BR[3]
    if n == 'bottom':
        return ID[1]
    if j in joints:
        return CU[3] if i == 1 and n in ('front', 'back') else ID[1]
    if j == h - 1:
        return ID[2]
    seg = [k for k in joints if k < j]
    first = (max(seg) + 1) if seg else 0
    if j == first:
        return BR[4] if i in (0, 3) else BR[3]
    if n in ('front', 'back') and i == 2 and j > first + 1:
        return ID[2]  # piston rod
    if n in ('right', 'left') and i == 1:
        return BR[1]
    return BR[2]


paint_box(t, 32, 48, 4, 12, 4, lambda n, i, j, w, h: limb(n, i, j, w, h, (4, 9)))
paint_box(t, 16, 48, 4, 12, 4, lambda n, i, j, w, h: limb(n, i, j, w, h, (5,)))
# ---------------- boiler (0,32) 6x7x4
f = faces(0, 32, 6, 7, 4)


def boiler_side(i, j, w):
    if j in (0, 6):
        return BR[4] if i in (0, w - 1) else BR[3] if j == 0 else BR[1]
    if i == 1:
        return CU[3]
    if i == w - 1 and j >= 3:
        return VG[1] if j < 5 else VG[0]
    return CU[2] if j < 5 else CU[1]


for k in ('right', 'left', 'front'):
    x, y, w, h = f[k]
    t.fn(x, y, w, h, lambda i, j, w=w: boiler_side(i, j, w))
x, y, _, _ = f['back']
t.ascii(x, y, [
    'rBBBBr',
    'cCwwcc',
    'cCwnvc',
    'cCcccv',
    'dFfFfd',
    'dfFfFd',
    'rbbbbr',
], {'r': BR[4], 'B': BR[3], 'b': BR[1], 'c': CU[2], 'C': CU[3], 'w': '#e8dfc4', 'n': '#2a1d14', 'v': VG[1], 'd': ID[1], 'F': AM[1], 'f': ID[0]})
t.fn(*f['top'], lambda i, j: BR[3] if i in (0, 5) or j in (0, 3) else CU[3])
t.fn(*f['bottom'], lambda i, j: CU[0])
# chimney (20,32) 2x3x2
paint_box(t, 20, 32, 2, 3, 2, lambda n, i, j, w, h: '#0b0a09' if n == 'top' else (ID[3] if j == 0 else (ID[2] if i == 0 else ID[1])))
# key shaft (20,38) 1x1x2 and bow (28,32) 0x4x3
paint_box(t, 20, 38, 1, 1, 2, lambda n, i, j, w, h: BR[3])
for (x, y) in ((28, 35), (31, 35)):
    t.ascii(x, y, ['HHH', 'B_B', 'B_B', 'bbb'], {'H': BR[4], 'B': BR[3], 'b': BR[1]})
t.save(OUT)
print(OUT)
