"""Tiny pixel-art helpers: ASCII sprites, auto outline, luminance-rank recolour of vanilla textures."""
import os
import numpy as np
from PIL import Image

VAN = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'vanilla')


def hx(h):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


class Spr:
    def __init__(self, w=16, h=16, arr=None):
        self.a = np.zeros((h, w, 4), np.uint8) if arr is None else arr.copy()

    @property
    def w(self):
        return self.a.shape[1]

    @property
    def h(self):
        return self.a.shape[0]

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.a[y, x] = hx(c) if isinstance(c, str) else c

    def get(self, x, y):
        return tuple(int(v) for v in self.a[y, x])

    def ascii(self, rows, pal, x0=0, y0=0):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == '.' or ch == ' ':
                    continue
                if ch == '_':  # erase
                    self.a[y0 + j, x0 + i] = 0
                    continue
                self.px(x0 + i, y0 + j, pal[ch])
        return self

    def outline(self, col=None, k=0.42, keep=None):
        """4-neighbour outline. col fixed colour or None = darkened neighbour (material-tinted)."""
        a = self.a
        h, w = a.shape[:2]
        out = a.copy()
        for y in range(h):
            for x in range(w):
                if a[y, x, 3]:
                    continue
                nb = [(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
                nb = [a[yy, xx] for xx, yy in nb if 0 <= xx < w and 0 <= yy < h and a[yy, xx, 3]]
                if not nb:
                    continue
                if col is not None:
                    out[y, x] = hx(col)
                else:
                    d = min(nb, key=lambda c: lum(c))
                    out[y, x] = (int(d[0] * k), int(d[1] * k), int(d[2] * k), 255)
        self.a = out
        return self

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        Image.fromarray(self.a, 'RGBA').save(path)
        return path


def load(name):
    return Spr(arr=np.array(Image.open(os.path.join(VAN, name)).convert('RGBA')))


def recolor(spr, ramp, sel=None, region=None):
    """Map the distinct colours of the selected pixels (by luminance rank) onto ramp (dark -> light)."""
    a = spr.a
    h, w = a.shape[:2]
    pts = []
    for y in range(h):
        for x in range(w):
            if not a[y, x, 3]:
                continue
            if region and not region(x, y):
                continue
            c = tuple(int(v) for v in a[y, x])
            if sel and not sel(c):
                continue
            pts.append((x, y, c))
    cols = sorted({p[2] for p in pts}, key=lum)
    n = len(cols)
    ramp = [hx(c) for c in ramp]
    m = {}
    for i, c in enumerate(cols):
        j = 0 if n == 1 else round(i * (len(ramp) - 1) / (n - 1))
        r = ramp[j]
        m[c] = (r[0], r[1], r[2], c[3])
    for x, y, c in pts:
        a[y, x] = m[c]
    return spr


def is_grey(c, tol=14):
    return max(c[:3]) - min(c[:3]) <= tol


def sat(c):
    return max(c[:3]) - min(c[:3])


def diag(spr, fn, n=16):
    """Paint along the main up-right diagonal: fn(s, u) -> colour or None, s = x - y (along), u = x + y - 15 (across)."""
    for y in range(n):
        for x in range(n):
            c = fn(x - y, x + y - 15)
            if c:
                spr.px(x, y, c)
    return spr
