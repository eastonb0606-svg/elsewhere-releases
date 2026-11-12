"""Aldermark soldier skins (64x64 player layout, base layer only; vanilla armor and the tinted TabardLayer draw on top)."""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from boxpaint import Tex, faces, paint_box
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(A2, 'kingdom/src/main/resources/assets/elsewhere/textures/entity/kingdom/')
BLUE = ['#1a2a5e', '#26408a', '#2e4c9e', '#4566bf']
RED = ['#5e1216', '#8e1e24', '#b42c32', '#d4474c']
GOLD = ['#7a5410', '#c08a1e', '#e8b83a', '#ffe07a']
WHITE = ['#8e949e', '#c9cdd4', '#eeeeea']
CHAIN = ['#33383e', '#555b63', '#7d848b', '#a6adb3']
PLATE = ['#454b53', '#737b83', '#a3abb3', '#cdd3d9', '#eef2f5']
GREEN = ['#1f3b19', '#2c5224', '#3b6a2e', '#52843f']
LEATH = ['#2e1d11', '#4a301c', '#654428', '#81593a']
TUNIC = ['#3f2e1c', '#56402a', '#6d5236', '#866745']
HOSE = ['#3a3530', '#4d4740', '#615a51']
ROPE = '#b39a64'
SKIN = {'light': ['#a8735a', '#c58d6e', '#d9a585'], 'medium': ['#8c5b42', '#a87053', '#bf8765'],
        'dark': ['#57351f', '#6e452c', '#865838'], 'tan': ['#9a6446', '#b67c58', '#c9936c']}
EYEW = '#ecebe6'


def mail(i, j):
    if j % 2 == 0:
        return CHAIN[3] if i % 2 == 0 else CHAIN[1]
    return CHAIN[2] if i % 2 == 1 else CHAIN[1]


def head(t, skin, hair, eye, beard=None, long_hair=False, fringe=2, extra=None):
    S = SKIN[skin]; H = hair
    f = faces(0, 0, 8, 8, 8)
    t.fn(*f['top'], lambda i, j: H[1] if i % 3 == 1 else H[0])
    t.fn(*f['bottom'], lambda i, j: (beard[0] if beard else S[0]))
    rows = []
    for j in range(8):
        r = []
        for i in range(8):
            c = S[1]
            if j < fringe or (j == fringe and i in (0, 7)):
                c = H[1] if i % 3 else H[0]
            elif j == 3 and i in (1, 2, 5, 6):
                c = H[0]  # brows
            elif j == 4:
                c = {1: EYEW, 2: eye, 5: eye, 6: EYEW}.get(i, S[1])
            elif j == 5 and i in (3, 4):
                c = S[0]
            elif j == 6 and 2 <= i <= 5:
                c = '#5a2c22' if i in (3, 4) else S[1]
            if beard and ((j >= 6 and 1 <= i <= 6) or (j == 5 and i in (2, 5)) or (j == 7)):
                c = beard[1] if i % 3 == 1 else beard[0]
                if j == 6 and i in (3, 4):
                    c = '#3a1e16'
            if j == 2 and fringe <= 2 and 1 <= i <= 6:
                c = S[2] if c == S[1] else c
            r.append(c)
        rows.append(r)
    x, y, _, _ = f['front']
    for j in range(8):
        for i in range(8):
            t.px(x + i, y + j, rows[j][i])

    def side(i, j):  # col 7 = front edge
        if j < 3 or (i < 4 and j < (8 if long_hair else 6)):
            return H[1] if i % 3 else H[0]
        if beard and j >= 5 and i >= 4:
            return beard[1] if i % 3 == 1 else beard[0]
        if i == 3 and j == 4:
            return S[0]
        return S[1]
    t.fn(*f['right'], side)
    x, y, w, h = f['left']; t.fn(x, y, w, h, lambda i, j: side(7 - i, j))
    t.fn(*f['back'], lambda i, j: (H[1] if i % 3 else H[0]) if j < (8 if long_hair else 6) else S[0])
    if extra:
        extra(t)


def tabard_front(trim=False, sash=False, tower=True, field=BLUE):
    rows = ['RRRRRRRR', 'rrrrrrrr', 'BBYBBYBB', 'BBYYYYBB', 'BWBWWBWB', 'BWWWWWWB', 'BWWkkWWB', 'BWWkkWWB', 'BWWWWWWB', 'BBBBBBBB', 'BBBBBBBB', 'bbbbbbbb']
    if not tower:
        rows = rows[:2] + ['BBBBBBBB'] * 9 + ['bbbbbbbb']
    pal = {'R': RED[2], 'r': RED[1], 'B': field[2], 'b': field[1], 'Y': GOLD[2], 'W': WHITE[2], 'k': field[0]}

    def f(i, j):
        c = pal[rows[j][i]]
        if trim and (i in (0, 7) and j >= 2 or j == 11):
            c = GOLD[2] if (i + j) % 2 == 0 or j == 11 else GOLD[1]
        if sash and (i - (j - 2) in (0, 1)) and 2 <= j <= 10:
            c = RED[3] if i - (j - 2) == 0 else RED[1]
        return c
    return f


def body(t, front, back, sides, top):
    f = faces(16, 16, 8, 12, 4)
    t.fn(*f['front'], front); t.fn(*f['back'], back)
    t.fn(*f['right'], sides); t.fn(*f['left'], sides)
    t.fn(*f['top'], top); t.fn(*f['bottom'], lambda i, j: front(i, 11))


def arms(t, fn):
    paint_box(t, 40, 16, 4, 12, 4, fn)
    paint_box(t, 32, 48, 4, 12, 4, fn)


def legs(t, fn):
    paint_box(t, 0, 16, 4, 12, 4, fn)
    paint_box(t, 16, 48, 4, 12, 4, fn)


def chain_arm(n, i, j, w, h):
    if n == 'bottom':
        return LEATH[1]
    if j >= 10:
        return LEATH[2] if j == 10 else LEATH[1]
    return mail(i, j)


def plate_arm(n, i, j, w, h):
    if n == 'top':
        return PLATE[3]
    if n == 'bottom':
        return PLATE[1]
    if j in (4, 9):
        return mail(i, j) if j == 4 else PLATE[0]
    if j == 0 or j == 5 or j == 10:
        return PLATE[4] if (n == 'front' and i == 1) else PLATE[3]
    return PLATE[2] if not (n in ('front', 'back') and i == 3) else PLATE[1]


def armored_leg(mat, skirt):
    def fn(n, i, j, w, h):
        if n == 'top':
            return skirt[2]
        if n == 'bottom':
            return LEATH[0]
        if j <= 3 and n in ('front', 'back'):
            return skirt[1] if j == 3 else skirt[2]
        if j >= 10:
            return LEATH[1] if j == 10 else LEATH[0]
        if mat == 'chain':
            return mail(i, j)
        if j in (4, 7):
            return PLATE[3]
        return PLATE[2] if i != 3 else PLATE[1]
    return fn


def soldier(name, skin, hair, eye, beard, arm, legmat, trim=False, sash=False, gorget=False, long_hair=False):
    t = Tex(64, 64)
    head(t, skin, hair, eye, beard, long_hair)
    side = (lambda i, j: mail(i, j)) if arm is chain_arm else (lambda i, j: PLATE[2] if j % 4 else PLATE[3])
    front = tabard_front(trim, sash)
    back = tabard_front(trim, False, tower=False)
    if sash:
        b0 = back
        back = lambda i, j: (RED[3] if (7 - i) - (j - 2) == 0 else RED[1]) if ((7 - i) - (j - 2) in (0, 1) and 2 <= j <= 10) else b0(i, j)
    if gorget:
        f0 = front
        front = lambda i, j: (GOLD[3] if i in (2, 5) else GOLD[2]) if j == 0 and 2 <= i <= 5 else (GOLD[1] if j == 1 and 3 <= i <= 4 else f0(i, j))
    body(t, front, back, side, lambda i, j: CHAIN[2] if arm is chain_arm else PLATE[3])
    arms(t, arm)
    legs(t, armored_leg(legmat, BLUE if not trim else BLUE))
    t.save(OUT + name)
    return t


# man-at-arms: chainmail, blue tabard with the white tower and gold crown, red chief
soldier('soldier_a.png', 'light', ['#4a2f1c', '#5f3d25'], '#3b5a8a', None, chain_arm, 'chain')
# knight: plate, gold-trimmed tabard, dark beard
soldier('soldier_b.png', 'medium', ['#1f1712', '#2e221a'], '#4a3424', ['#2a1d15', '#3a2a1e'], plate_arm, 'plate', trim=True)
# sergeant: plate, red officer sash, grey-streaked beard
soldier('officer.png', 'tan', ['#55504a', '#6e6860'], '#4a5f3a', ['#6e6860', '#8a847b'], plate_arm, 'plate', sash=True)
# captain: plate, gold-trimmed tabard, red sash, gold gorget chain, black moustache-beard
soldier('captain.png', 'dark', ['#141110', '#211b18'], '#2a1a10', ['#141110', '#252019'], plate_arm, 'plate', trim=True, sash=True, gorget=True)

# archer: dyed green leather jerkin with a small Aldermark badge, brown bracers, quiver strap
t = Tex(64, 64)
head(t, 'light', ['#8a3e1c', '#a5502a'], '#4f7a3a', None, long_hair=True)


def jerkin(back=False):
    def f(i, j):
        if j in (8, 9):
            return LEATH[2] if j == 8 else LEATH[1]
        if not back and (i - (j - 0)) in (-1, 0) and j <= 7:
            return LEATH[3] if (i - j) == 0 else LEATH[1]  # quiver strap
        if back and ((7 - i) - j) in (-1, 0) and j <= 7:
            return LEATH[3] if (7 - i) - j == 0 else LEATH[1]
        if not back and 4 <= i <= 6 and 2 <= j <= 4:
            return {(5, 2): WHITE[2], (4, 3): WHITE[2], (5, 3): WHITE[2], (6, 3): WHITE[2], (4, 4): WHITE[2], (6, 4): WHITE[2], (5, 4): BLUE[0]}.get((i, j), BLUE[2])
        if j == 11:
            return GREEN[1]
        return GREEN[2] if (i + j // 2) % 4 else GREEN[1]
    return f


body(t, jerkin(), jerkin(True), lambda i, j: LEATH[1] if j in (8, 9) else GREEN[2], lambda i, j: GREEN[3])
arms(t, lambda n, i, j, w, h: SKIN['light'][1] if j == 11 or n == 'bottom' else (LEATH[2] if 7 <= j <= 10 and j != 8 else (LEATH[3] if j == 8 else (GREEN[3] if n == 'top' else GREEN[2] if j else GREEN[3]))))
legs(t, lambda n, i, j, w, h: LEATH[0] if n == 'bottom' else (GREEN[2] if j <= 2 and n != 'top' else (LEATH[2] if j >= 9 else (LEATH[1] if j == 9 else HOSE[1] if i != 3 else HOSE[0]))) if n != 'top' else GREEN[2])
t.save(OUT + 'archer.png')

# serf: plain brown tunic, rope belt, rolled sleeves, hose, worn shoes; straw hair, no armour
t = Tex(64, 64)
head(t, 'tan', ['#a88a4a', '#c4a660'], '#5a4a30', None, fringe=3)


def tunic(i, j):
    if j == 8:
        return ROPE if i not in (2,) else '#8a7448'
    if j == 11:
        return TUNIC[0]
    if j == 0 and 2 <= i <= 5:
        return TUNIC[0]  # neckline
    return TUNIC[1] if i in (0, 7) or (j == 1 and i in (3, 4)) else TUNIC[2]


body(t, tunic, tunic, lambda i, j: ROPE if j == 8 else (TUNIC[2] if j % 5 else TUNIC[1]), lambda i, j: TUNIC[2])
arms(t, lambda n, i, j, w, h: (TUNIC[2] if j <= 3 and n != 'bottom' else (TUNIC[1] if j == 4 else SKIN['tan'][1 if j < 11 else 0])) if n != 'top' else TUNIC[2])
legs(t, lambda n, i, j, w, h: '#2a1c12' if n == 'bottom' else (TUNIC[1] if j <= 1 and n in ('front', 'back') else (LEATH[1] if j >= 10 else (HOSE[0] if (n == "front" and i == 3) or j == 9 else HOSE[1]))))
t.save(OUT + 'serf.png')
print('ok')
