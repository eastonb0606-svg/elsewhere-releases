import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from boxpaint import Tex, faces, paint_box
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(A2, 'verdigris_ironpeak/src/main/resources/assets/elsewhere/textures/entity/ironclan_raider.png')

SK = ['#6e4630', '#8a5a3c', '#a8714b', '#c48a5f']
BD = ['#3b2014', '#5a3020', '#7a4329', '#97573a']
IR = ['#2f343a', '#4b5259', '#6b737b', '#9098a0', '#c3c9ce']
FU = ['#3e342b', '#5c4e40', '#7b6a57', '#9a876f', '#b8a68b']
WO = ['#1f2836', '#2c3a50', '#3b4f6b', '#506a8a']
LE = ['#2e1f15', '#4a3220', '#654630', '#7f5b3f']
TR = ['#2b2522', '#3a322d', '#4a403a']
HO = ['#5e5443', '#9c8f72', '#c4b897', '#e2d8bc']
EYEW, EYEP = '#ece6da', '#2b3f63'


def fur(i, j):
    k = (i + 2 * (j // 2)) % 4
    if k == 0:
        return FU[1]
    if k == 2 and j % 2 == 0:
        return FU[3]
    return FU[2]


def mail(i, j):
    if j % 2 == 0:
        return IR[3] if i % 2 == 0 else IR[1]
    return IR[2] if i % 2 == 1 else IR[1]


t = Tex(64, 64)
# ---------------- head (0,0) 8x8x8
f = faces(0, 0, 8, 8, 8)
t.fn(*f['top'], lambda i, j: BD[2] if i % 3 == 1 else BD[1])
t.fn(*f['bottom'], lambda i, j: BD[1] if i % 3 else BD[0])


def side(i, j):  # right side; col 7 = front edge
    if j >= 5:
        return BD[2] if i % 2 == 0 else BD[1]
    if i >= 5 and j >= 2:
        return SK[2] if i < 7 else SK[3]
    if i in (3, 4) and j == 4:
        return SK[1]  # ear
    return BD[2] if i % 3 == 1 and j < 4 else BD[1]


t.fn(*f['right'], side)
x, y, w, h = f['left']
t.fn(x, y, w, h, lambda i, j: side(7 - i, j))
t.fn(*f['back'], lambda i, j: (IR[3] if j == 6 else BD[2]) if i in (3, 4) and j >= 4 else (BD[2] if i % 3 == 1 else BD[1]))
fx, fy, _, _ = f['front']
t.ascii(fx, fy, [
    'hhhhhhhh',
    'hhhhhhhh',
    'SsSSSSsS',
    'Sbbssbbs',
    'sWPnnPWs',
    'SMMnnMMS',
    'mMMooMMm',
    'mmMmmMmm',
], {'h': BD[1], 'S': SK[2], 's': SK[1], 'b': BD[0], 'W': EYEW, 'P': EYEP, 'n': SK[1], 'M': BD[2], 'm': BD[1], 'o': '#2a1510'})
# ---------------- helmet = hat (32,0) 8x8x8
f = faces(32, 0, 8, 8, 8)
t.fn(*f['top'], lambda i, j: IR[4] if i in (3, 4) and j % 3 != 2 else (IR[3] if 1 <= i <= 6 and 1 <= j <= 6 else IR[2]))
fx, fy, _, _ = f['front']
t.ascii(fx, fy, [
    'RrRRRRrR',
    'RRRRRRRR',
    'ddddDddd',
    '...NN...',
    '...Nn...',
], {'R': IR[2], 'r': IR[4], 'd': IR[1], 'D': IR[3], 'N': IR[3], 'n': IR[2]})
for k in ('right', 'left'):
    x, y, w, h = f[k]
    t.fn(x, y, w, 4, lambda i, j: IR[1] if j == 3 else (IR[4] if (j == 1 and i == 4) else (IR[3] if j == 0 else IR[2])))
x, y, w, h = f['back']
t.fn(x, y, w, 5, lambda i, j: IR[1] if j == 4 else (IR[4] if j == 1 and i in (1, 6) else (IR[3] if j == 0 else IR[2])))
# ---------------- body (16,16) 8x12x4
f = faces(16, 16, 8, 12, 4)


def torso(i, j, w, tabard=True):
    if j in (8, 9):
        if tabard and 3 <= i <= 4 and w == 8:
            return IR[4] if j == 8 else IR[2]
        return LE[2] if j == 8 else LE[1]
    if j >= 10:
        if j == 11:
            return WO[1]
        return WO[1] if i % 3 == 1 else WO[2]
    if tabard and w == 8 and 2 <= i <= 5 and j >= 1:
        return WO[2] if i in (3, 4) else WO[1]
    return mail(i, j)


t.fn(*f['front'], lambda i, j: torso(i, j, 8))
x, y, _, _ = f['front']
t.ascii(x, y, ['', '', '', '...##...', '..#..#..', '.#....#.' if False else '..####..'], {'#': IR[4]})
t.fn(*f['back'], lambda i, j: torso(i, j, 8))
t.fn(*f['right'], lambda i, j: torso(i, j, 4, False))
t.fn(*f['left'], lambda i, j: torso(i, j, 4, False))
t.fn(*f['top'], lambda i, j: mail(i, j))
t.fn(*f['bottom'], lambda i, j: WO[0])
# ---------------- right arm (40,16) 4x12x4 (left arm mirrors it)


def arm(n, i, j, w, h):
    if n == 'top':
        return mail(i, j)
    if n == 'bottom':
        return SK[1]
    if j <= 3:
        return mail(i, j)
    if j <= 7:
        edge = (n in ('front', 'back') and i in (0, 3))
        return SK[1] if edge or j == 4 else SK[2]
    if j <= 10:
        if j == 8:
            return LE[3]
        if j == 9 and i == 1 and n in ('front', 'right', 'left', 'back'):
            return IR[3]
        return LE[2] if j == 9 else LE[1]
    return SK[2] if n != 'back' else SK[1]


paint_box(t, 40, 16, 4, 12, 4, arm)


# ---------------- right leg (0,16) 4x12x4
def leg(n, i, j, w, h):
    if n == 'top':
        return TR[1]
    if n == 'bottom':
        return LE[0]
    if j <= 5:
        return TR[0] if (n == 'front' and i == 3) or j == 5 else (TR[2] if j == 0 else TR[1])
    if j == 11:
        return LE[0]
    if (j in (7, 9) and (i + j) % 2 == 0) or (j == 8 and (i + j) % 2 == 1):
        return LE[1]
    return fur(i, j)


paint_box(t, 0, 16, 4, 12, 4, leg)


# ---------------- horns: base (0,32) 3x2x2, tip (10,32) 1x3x1
def horn_base(n, i, j, w, h):
    if n == 'left':
        return None  # inner face sits on the helmet: keep empty (no z-fight)
    if n == 'top':
        return HO[3]
    if n == 'bottom':
        return HO[1]
    return HO[2] if j == 0 else HO[1]


paint_box(t, 0, 32, 3, 2, 2, horn_base)
paint_box(t, 10, 32, 1, 3, 1, lambda n, i, j, w, h: HO[0] if n == 'top' or j == 0 else (HO[1] if j == 1 else HO[2]))
# ---------------- beard (16,32) 8x5x1
f = faces(16, 32, 8, 5, 1)
x, y, _, _ = f['front']
t.ascii(x, y, [
    'aBBaaBBa',
    'aBBBBBBa',
    'aaBbbBaa',
    '_aB__Ba_',
    '_t____t_',
], {'a': BD[1], 'B': BD[2], 'b': BD[0], 't': IR[3]})
for k in ('right', 'left'):
    t.fn(*f[k], lambda i, j: None if j >= 3 else BD[1])
t.fn(*f['top'], lambda i, j: BD[1])
t.fn(*f['bottom'], lambda i, j: BD[0] if i in (1, 2, 5, 6) else None)
# back face stays empty: it sits on the face plane
# ---------------- collar (0,40) 9x3x5 fur
f = faces(0, 40, 9, 3, 5)
for k in ('front', 'back', 'right', 'left'):
    t.fn(*f[k], lambda i, j: FU[3] if j == 0 and i % 2 == 0 else (FU[1] if j == 2 and i % 2 == 1 else fur(i, j)))
t.fn(*f['top'], lambda i, j: FU[0] if 2 <= i <= 6 and 1 <= j <= 3 else (FU[3] if (i + j) % 3 == 0 else FU[2]))
t.fn(*f['bottom'], lambda i, j: FU[1])


# ---------------- pauldron (32,40) 5x4x5: iron plate with fur fringe
def paul(n, i, j, w, h):
    if n == 'top':
        if i == 2 and j == 2:
            return IR[4]
        return IR[3] if 1 <= i <= 3 and 1 <= j <= 3 else IR[2]
    if n == 'bottom':
        return FU[1]
    if j == 0:
        return IR[3]
    if j == 1:
        return IR[4] if i == 2 else IR[2]
    if j == 2:
        return IR[1]
    return FU[3] if i % 2 == 0 else FU[1]


paint_box(t, 32, 40, 5, 4, 5, paul)
t.save(OUT)
print(OUT)
