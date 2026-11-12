"""Box-UV painting helpers for entity textures (vanilla Cube layout)."""
import numpy as np
from PIL import Image


def hx(h):
    h = h.lstrip('#')
    return np.array([int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255], np.uint8)


class Tex:
    def __init__(self, w, h, base=None):
        self.a = np.zeros((h, w, 4), np.uint8) if base is None else np.array(Image.open(base).convert('RGBA'))

    def px(self, x, y, c):
        if 0 <= y < self.a.shape[0] and 0 <= x < self.a.shape[1]:
            self.a[y, x] = hx(c) if isinstance(c, str) else c

    def rect(self, x, y, w, h, c):
        for j in range(h):
            for i in range(w):
                self.px(x + i, y + j, c)

    def fn(self, x, y, w, h, f):
        """f(i, j) -> colour or None, i/j local."""
        for j in range(h):
            for i in range(w):
                c = f(i, j)
                if c is not None:
                    self.px(x + i, y + j, c)

    def ascii(self, x, y, rows, pal):
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch in '. ':
                    continue
                if ch == '_':
                    self.a[y + j, x + i] = 0
                else:
                    self.px(x + i, y + j, pal[ch])

    def clear(self, x, y, w, h):
        self.a[y:y + h, x:x + w] = 0

    def save(self, p):
        import os
        os.makedirs(os.path.dirname(p), exist_ok=True)
        Image.fromarray(self.a, 'RGBA').save(p)


def faces(u, v, w, h, d):
    """Face rects (x, y, w, h) of a box at texOffs(u, v) with size w x h x d."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def paint_box(t, u, v, w, h, d, f):
    """f(face, i, j, fw, fh) -> colour or None for every face."""
    for name, (x, y, fw, fh) in faces(u, v, w, h, d).items():
        t.fn(x, y, fw, fh, lambda i, j, n=name, fw=fw, fh=fh: f(n, i, j, fw, fh))
