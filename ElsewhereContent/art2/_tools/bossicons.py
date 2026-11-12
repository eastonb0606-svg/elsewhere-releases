"""24 boss-bar icons (16x16) for boss_ui: a round badge in the bar colour with a 10x10 glyph of the boss."""
import os, sys, json
import numpy as np
from PIL import Image
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(A2, 'boss_ui/src/main/resources/assets/elsewhere/textures/gui/boss_icons')
BARS = os.path.join(A2, '..', 'boss_ui/src/main/resources/assets/elsewhere/boss_bars.json')


def rgb(h):
    h = h.lstrip('#'); return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def mix(a, b, t):
    return a * (1 - t) + b * t


ACC = {'y': '#fff0a0', 'o': '#ff9a2a', 'r': '#e0302a', 'p': '#f29ab8', 't': '#6fc8ff', 'g': '#5fd35f', 'b': '#2f4f9a', 'Y': '#f2c94c'}
S = 'sym'  # left halves (5 wide) mirrored
G = {
    'gulpmother': [S, '.kk..', 'kwyk.', 'kykk.', '.kk..', '.....', 'k....', '.kkkk', '..kpp', '...kk', '.....'],
    'strawgrim': [S, '...kk', '...kk', '.kkkk', '.....', '..oo.', '..ok.', '.....', '.k.k.', '.kkkk', '.k.k.'],
    'neferkhet': [S, '...kk', '..kYY', '.kYbY', 'kYb..', 'kbYk.', 'kYb..', 'kbY.k', 'kYb.k', '.kk.k', '....k'],
    'rimeback': [S, '....k', '...kw', '..kww', '..kwk', '.kkk.', '.k...', 'k...k', 'k..k.', 'k.k..', 'kkkkk'],
    'vorlagh': [S, '...kk', '..k..', '.k...', '.k.wk', '.k...', '..k..', '.k.kk', 'k.k.k', 'k.k.k', '.k..k'],
    'grunthar': [S, '.....', '.....', 'k...k', 'kk.kk', 'kkkkk', 'krkwk', 'kkkkk', '.....', '..w..', '..ww.'],
    'solaxis': [S, '....k', '.k..k', '..k..', '...kk', 'k.kyy', 'k.kyy', '...kk', '..k..', '.k..k', '....k'],
    'mournmaw': [S, 'kkkkk', 'k....', 'k.kk.', 'k..t.', 'k..t.', 'k....', 'k..kk', 'k.k..', 'k....', 'kk.k.'],
    'ossyra': [S, '.kkkk', 'k....', 'k.k.k', 'k....', 'kkkkk', 'k..kw', 'k..kk', 'kkkkk', 'k....', '.kkkk'],
    'aphelion': ['...kkkk...', '.kk....kk.', '.k.kkkk.k.', 'k.k....k.k', 'k.k.kk.k.k', 'k.k.k..k.k', 'k..k..k..k', '.k..kk..k.', '.kk....kw.', '...kkkkk..'],
    'tempestra': ['...kkkk...', '.kk....k..', 'k...kk..k.', 'k..k..k..k', 'k.k.wk.k.k', 'k.k.kw.k.k', 'k..k..k..k', '.k..kk...k', '..k....kk.', '...kkkk...'],
    'ankhara': [S, '..kkk', '.k...', 'k....', 'k.rk.', 'k....', '.k...', '.kkkk', '.kw..', '..w..', '.....'],
    'heliodor': [S, '....k', '.k.kk', '..k..', '.k.yy', 'kk.yw', 'kk.yw', '.k.yy', '..k..', '.k.kk', '....k'],
    'kazehana': ['....kk....', '...kwwk...', '..kwwwwk..', '.kwwwwwwk.', 'kkkkkkkkkk', '..........', '..kk..kk..', '..........', 'kkwwwwwwww', '..........'],
    'auris': [S, '..kkk', '.k...', 'k..kk', 'k.k..', 'k.k.w', 'k.k.w', 'k.k..', 'k..kk', '.k...', '..kkk'],
    'grumblegut': [S, '.....', '.kk..', 'k..k.', '.....', '.kw..', '.....', 'kkkkk', 'kwkwk', 'kpppp', '.kkkk'],
    'lithara': [S, '....k', '...kw', '...kw', '.k.kw', 'kwkkw', 'kwkkw', 'kwkkw', 'kkkkk', '.kkkk', '.....'],
    'percolax': [S, '.....', '...kk', '.kk..', 'k....', 'k..r.', 'k....', '.kkkk', '.....', '..o..', '.....'],
    'galvanox': ['....kk....', '...kyyk...', '...kkkk...', '....kk....', '...kwwk...', '...kkkk...', '...kwwk...', '..kkkkkk..', '..kwwwwk..', '.kkkkkkkk.'],
    'hrolf': [S, '.....', 'kk...', 'k.k..', 'kk.kk', '.kkww', '..kww', '..kkk', '..k.k', '..ooo', '...oo'],
    'thurgrim': [S, 'k..k.', 'kk.kk', 'kkkkk', '.....', '.kw..', '.....', '.....', 'kkkkk', 'kwkwk', '.kkkk'],
    'verdigrim': [S, '....k', '..k.k', '..k.k', 'k.k.k', 'k.k.k', 'w.w.w', 'k.k.k', 'kkkkk', 'kwkwk', 'kkkkk'],
    'selenne': ['..kkk.....', '.kwwk.....', 'kwwk......', 'kwk....k..', 'kwk...kpk.', 'kwk..kpypk', 'kwwk..kpk.', '.kwwk..k..', '..kkkk....', '..........'],
    'mother_creak': [S, '.k..k', '..kkk', '.k...', '.k.o.', '.k.O.', '.k...', '.k.kk', '.k...', '.k...', '.kkkk'],
}
ACC['O'] = '#ffcc4a'
colors = {k.split(':')[1]: v['color'] for k, v in json.load(open(BARS))['bosses'].items()}
os.makedirs(OUT, exist_ok=True)
for name, rows in G.items():
    if rows[0] == S:
        rows = [r + r[::-1] for r in rows[1:]]
    assert len(rows) == 10 and all(len(r) == 10 for r in rows), name
    c = rgb(colors[name]); dark = c * 0.22; light = mix(c, rgb('#ffffff'), 0.72)
    a = np.zeros((16, 16, 4), np.uint8)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 7.9:
                continue
            if d > 6.9:
                col = rgb('#16121a')
            elif d > 5.9:
                col = c * 0.62
            else:
                col = mix(c, rgb('#ffffff'), 0.18) if y < 5 else (c * 0.86 if y > 10 else c)
            a[y, x] = [*col.astype(int), 255]
    for j, r in enumerate(rows):
        for i, ch in enumerate(r):
            if ch == '.':
                continue
            col = dark if ch == 'k' else light if ch == 'w' else rgb(ACC[ch])
            a[3 + j, 3 + i] = [*col.astype(int), 255]
    Image.fromarray(a).save(os.path.join(OUT, name + '.png'))
print(len(G), 'icons')
