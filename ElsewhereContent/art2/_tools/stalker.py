import os, sys
sys.path.insert(0, os.path.dirname(__file__))
import numpy as np
from boxpaint import Tex, faces, paint_box, hx
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
D = os.path.join(A2, 'greenhaze_hush/src/main/resources/assets/elsewhere/textures/entity/')
DK = ['#06080b', '#0c1418', '#121f26', '#1a2d35', '#264149']
TE = ['#0b5a61', '#16a3ab', '#36dde3', '#a2fbf4']
P = {'x': DK[0], 'd': DK[1], 'D': DK[2], 'L': DK[3], 'H': DK[4], 'v': TE[0], 'V': TE[1], 'b': TE[2], 'B': TE[3], 'T': '#123c43'}
t = Tex(64, 32)
f = faces(0, 0, 8, 8, 8)
t.ascii(*f['front'][:2], [
    'DDDLLDDD',
    'DvDDDDvD',
    'DDvDDvDD',
    'DxxDDxxD',
    'DVDDDDVD',
    'DbDddDbD',
    'DvDxxDvD',
    'dDDDDDDd'], P)
side = ['DDDLDDDD', 'DDDDvDDD', 'DDDvDDDD', 'DDvVDDDD', 'DDDVvDDD', 'DDDDDVDD', 'DDDDDvDD', 'dDDDDDDd']
t.ascii(*f['right'][:2], side, P)
t.ascii(*f['left'][:2], [r[::-1] for r in side], P)
t.ascii(*f['back'][:2], ['DDDLLDDD', 'DDvDDvDD', 'DDDvvDDD', 'DDDVVDDD', 'DDvDDvDD', 'DvDDDDvD', 'DDDDDDDD', 'dDDDDDDd'], P)
t.ascii(*f['top'][:2], ['DLDDDDLD', 'DDDvvDDD', 'DDvDDvDD', 'LDDDDDDL', 'DDDDDDDD', 'DDvDDvDD', 'DDDvvDDD', 'DLDDDDLD'], P)
t.fn(*f['bottom'], lambda i, j: DK[1])
# jaw (hat, 0,16): throat, teeth, chin; seen when the head lifts
f = faces(0, 16, 8, 8, 8)
for k in f:
    t.fn(*f[k], lambda i, j: DK[1])
t.ascii(*f['front'][:2], ['xxxxxxxx', 'xxxxxxxx', 'xxxxxxxx', 'xxxxxxxx', 'BxBxxBxB', 'DDDDDDDD', 'DvDDDDvD', 'dDDDDDDd'], P)
# body (32,16) 8x12x4
f = faces(32, 16, 8, 12, 4)
t.ascii(*f['front'][:2], [
    'DDDLLDDD',
    'LLLDDLLL',
    'DDDLLDDD',
    'DLLvvLLD',
    'DDDbBDDD',
    'DLLBbLLD',
    'DDDvvDDD',
    'DLLDDLLD',
    'DDDvDDDD',
    'DDDDvDDD',
    'DDDvDDDD',
    'ddDDDDdd'], P)
t.ascii(*f['back'][:2], ['DDDLLDDD', 'DDDvvDDD', 'DLDLLDLD', 'DDDvvDDD', 'DLDLLDLD', 'DDDVVDDD', 'DLDLLDLD', 'DDDvvDDD', 'DDDLLDDD', 'DDDvDDDD', 'DDDDDDDD', 'ddDDDDdd'], P)
for k in ('right', 'left'):
    t.ascii(*f[k][:2], ['DDDD', 'LDDL', 'DDDD', 'LDvL', 'DDVD', 'LDvL', 'DDDD', 'LDDL', 'DDDD', 'DvDD', 'DDDD', 'dDDd'], P)
t.fn(*f['top'], lambda i, j: DK[3] if j in (0, 3) else DK[2])
t.fn(*f['bottom'], lambda i, j: DK[1])


# limbs (56,0) 2x30x2, shared by arms and legs
def limb(n, i, j, w, h):
    if n in ('top', 'bottom'):
        return DK[1]
    if j in (13, 14):
        return TE[1] if (j == 13 and i == 0 and n == 'front') else DK[3]
    if j >= 27:
        return DK[1]
    if n == 'front' and ((i == 0 and 4 <= j <= 10) or (i == 1 and 17 <= j <= 23)):
        return TE[0]
    if n == 'back' and i == 1 and 6 <= j <= 11:
        return TE[0]
    return DK[2] if (j // 3 + i) % 2 else DK[3] if j % 6 == 0 else DK[2]


paint_box(t, 56, 0, 2, 30, 2, limb)
# tendrils (32,0) 6x8x0: front = tip at col 0, back = mirrored
tend = ['b.....', 'Tv..b.', '.Tv.T.', '..TvT.', '...TT.', '...TTv', '....TT', '.....T']
t.ascii(32, 0, tend, P)
t.ascii(38, 0, [r[::-1] for r in tend], P)
t.save(D + 'echo_stalker.png')
# emissive: only the bright teal pixels
e = Tex(64, 32)
keep = [hx(c)[:3] for c in (TE[1], TE[2], TE[3])]
for y in range(32):
    for x in range(64):
        if t.a[y, x, 3] and any((t.a[y, x, :3] == k).all() for k in keep):
            e.a[y, x] = t.a[y, x]
e.save(D + 'echo_stalker_emissive.png')
print('ok')
