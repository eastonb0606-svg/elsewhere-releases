"""Flat paper-doll previews (front/back/side) composited from box faces, for judging a skin at a glance."""
import sys
from PIL import Image


def comp(tex, parts, size, bg=(96, 104, 112, 255)):
    c = Image.new('RGBA', size, bg)
    for (x, y, w, h), dx, dy, *m in parts:
        p = tex.crop((x, y, x + w, y + h))
        if m and m[0]:
            p = p.transpose(Image.FLIP_LEFT_RIGHT)
        c.alpha_composite(p, (dx, dy))
    return c


def humanoid(tex, extras_front=(), extras_back=(), hat=(32, 0), arm=(40, 16), leg=(0, 16), larm=None, lleg=None):
    ax, ay = arm; lx, ly = leg; hx, hy = hat
    la = larm or arm; ll = lleg or leg; ml = larm is None; mll = lleg is None
    front = [((8, 8, 8, 8), 9, 4), ((20, 20, 8, 12), 9, 12), ((ax + 4, ay + 4, 4, 12), 5, 12), ((la[0] + 4, la[1] + 4, 4, 12), 17, 12, ml),
             ((lx + 4, ly + 4, 4, 12), 9, 24), ((ll[0] + 4, ll[1] + 4, 4, 12), 13, 24, mll), ((hx + 8, hy + 8, 8, 8), 9, 4)] + list(extras_front)
    back = [((24, 8, 8, 8), 9, 4), ((32, 20, 8, 12), 9, 12), ((ax + 12, ay + 4, 4, 12), 17, 12), ((la[0] + 12, la[1] + 4, 4, 12), 5, 12, ml),
            ((lx + 12, ly + 4, 4, 12), 13, 24), ((ll[0] + 12, ll[1] + 4, 4, 12), 9, 24, mll), ((hx + 24, hy + 8, 8, 8), 9, 4)] + list(extras_back)
    side = [((0, 8, 8, 8), 4, 4), ((16, 20, 4, 12), 6, 12), ((lx, ly + 4, 4, 12), 6, 24), ((ax, ay + 4, 4, 12), 6, 12), ((hx, hy + 8, 8, 8), 4, 4)]
    return [comp(tex, front, (26, 37)), comp(tex, back, (26, 37)), comp(tex, side, (16, 37))]


def sheet(imgs, scale, out, tex=None, tscale=None):
    ims = [i.resize((i.width * scale, i.height * scale), Image.NEAREST) for i in imgs]
    if tex is not None:
        ims.append(tex.resize((tex.width * tscale, tex.height * tscale), Image.NEAREST))
    W = sum(i.width for i in ims) + 8 * (len(ims) + 1); H = max(i.height for i in ims) + 16
    s = Image.new('RGBA', (W, H), (50, 50, 54, 255)); x = 8
    for i in ims:
        s.alpha_composite(i, (x, 8)); x += i.width + 8
    s.save(out)
    print(out, s.size)
