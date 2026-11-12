#!/usr/bin/env python3
"""Echo Knight textures (s-reliquary): textures/entity/echo_knight.png + echo_knight_glow.png, 128x64.

The UV boxes match client EchoKnightModel (texOffs u,v and box w,h,d). Palette = the Echo Reliquary item sprite
(sprites.json): soot-black plate and hood of the Mute Order, echo-teal trim, a cyan visor slit and a bell sigil that glow.
Run: _tools/venv/bin/python -I gear3/tools/echo_knight_art.py
"""
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity")

P = {
    "o": (26, 22, 24), "d": (58, 50, 54), "m": (94, 84, 89), "l": (140, 128, 135), "w": (201, 191, 196),
    "a": (29, 111, 134), "b": (63, 183, 201), "c": (150, 236, 245),
}
W, H = 128, 64
rnd = random.Random(31337)


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


def jitter(c, n=7):
    k = rnd.randint(-n, n)
    return tuple(max(0, min(255, x + k)) for x in c)


def faces(u, v, w, h, d):
    """Vanilla cuboid UV faces: name -> (x, y, width, height)."""
    return {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
    }


def fill(img, rect, col, n=7):
    x0, y0, w, h = rect
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            img.putpixel((x, y), rgba(jitter(P[col], n)))


def px(img, x, y, col, a=255):
    img.putpixel((x, y), rgba(P[col], a))


def box(img, u, v, w, h, d, base, n=7):
    for r in faces(u, v, w, h, d).values():
        fill(img, r, base, n)
    return faces(u, v, w, h, d)


def plate_rows(img, rect, rows, hi="l", lo="o"):
    """Horizontal plate seams: a dark line with a highlight row under it."""
    x0, y0, w, h = rect
    for r in rows:
        if 0 <= r < h:
            for x in range(x0, x0 + w):
                px(img, x, y0 + r, lo)
                if r + 1 < h:
                    px(img, x, y0 + r + 1, hi)


def body_tex():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # ---- head (0,0 8x8x8): a closed great-helm, sealed visor slit, nose guard
    f = box(img, 0, 0, 8, 8, 8, "m")
    x, y, w, h = f["front"]
    fill(img, (x, y, w, h), "l", 6)
    for i in range(w):
        px(img, x + i, y + 3, "o")
        px(img, x + i, y, "m")
    for j in range(1, 8):
        px(img, x + 3, y + j, "w" if j < 3 else "m")
        px(img, x + 4, y + j, "m")
    for j in (5, 6):
        for i in (1, 6):
            px(img, x + i, y + j, "d")  # breaths that never come
    for side in ("right", "left"):
        sx, sy, sw, sh = f[side]
        px(img, sx + 2, sy + 4, "w")
        px(img, sx + 5, sy + 4, "w")
        for i in range(sw):
            px(img, sx + i, sy + 3, "d")
    fill(img, f["back"], "d")
    fill(img, f["top"], "m")

    # ---- hood (32,0 8x8x8, inflated): black cloth framing the helm; the face stays open
    f = box(img, 32, 0, 8, 8, 8, "d", 9)
    x, y, w, h = f["front"]
    for j in range(h):
        for i in range(w):
            edge = j == 0 or i == 0 or i == w - 1
            img.putpixel((x + i, y + j), rgba(jitter(P["o"], 5)) if edge and j < 7 else (0, 0, 0, 0))
    for side in ("right", "left", "back"):
        sx, sy, sw, sh = f[side]
        for i in range(sw):
            if i % 3 == 1:
                for j in range(1, sh):
                    px(img, sx + i, sy + j, "o")  # folds
        for i in range(sw):
            px(img, sx + i, sy + sh - 1, "a")  # echo-teal hem
    fx, fy, fw, fh = f["bottom"]
    for j in range(fh):
        for i in range(fw):
            img.putpixel((fx + i, fy + j), (0, 0, 0, 0))

    # ---- body (16,16 8x12x4): cuirass (mostly under the tabard)
    f = box(img, 16, 16, 8, 12, 4, "m")
    for k in ("front", "back"):
        plate_rows(img, f[k], (3, 7))

    # ---- tabard (0,32 8x14x4, inflated): the Order's black tabard, teal hem, the silenced-bell sigil, belt
    f = box(img, 0, 32, 8, 14, 4, "o", 6)
    for k in ("front", "back"):
        x, y, w, h = f[k]
        for j in range(h):
            px(img, x, y + j, "a")
            px(img, x + w - 1, y + j, "a")
        for i in range(w):
            px(img, x + i, y + h - 1, "a")
            px(img, x + i, y + 8, "d")  # belt
        px(img, x + 3, y + 8, "l")
        px(img, x + 4, y + 8, "l")
    x, y, w, h = f["front"]
    bell = ["..bb..", ".bbbb.", ".bbbb.", "bbbbbb", "......", "aaaaaa"]  # a bell, and the bar that silences it
    for j, row in enumerate(bell):
        for i, ch in enumerate(row):
            if ch != ".":
                px(img, x + 1 + i, y + 1 + j, "a" if ch == "a" else "d")
    for side in ("right", "left"):
        sx, sy, sw, sh = f[side]
        for j in range(sh):
            for i in range(sw):
                if j < 8:
                    img.putpixel((sx + i, sy + j), (0, 0, 0, 0))  # open at the sides: the plate shows
    for k in ("top", "bottom"):
        fx, fy, fw, fh = f[k]
        for j in range(fh):
            for i in range(fw):
                img.putpixel((fx + i, fy + j), (0, 0, 0, 0))

    # ---- arms (40,16 / 40,32 4x12x4): rerebrace, elbow cop, black gauntlet
    for (u, v) in ((40, 16), (40, 32)):
        f = box(img, u, v, 4, 12, 4, "m")
        for k in ("front", "back", "right", "left"):
            x, y, w, h = f[k]
            plate_rows(img, f[k], (4,))
            for i in range(w):
                px(img, x + i, y + 5, "w" if k == "front" and 0 < i < w - 1 else "l")
                for j in range(9, 12):
                    img.putpixel((x + i, y + j), rgba(jitter(P["o"], 4)))
            px(img, x, y + 9, "d")
        fill(img, f["bottom"], "o", 4)

    # ---- legs (0,16 / 24,32 4x12x4): greaves, knee cop, sabatons
    for (u, v) in ((0, 16), (24, 32)):
        f = box(img, u, v, 4, 12, 4, "m")
        for k in ("front", "back", "right", "left"):
            x, y, w, h = f[k]
            plate_rows(img, f[k], (2, 7))
            for i in range(w):
                px(img, x + i, y + 4, "w" if k == "front" and 0 < i < w - 1 else "l")
                px(img, x + i, y + 10, "d")
                px(img, x + i, y + 11, "o")
        fill(img, f["bottom"], "o", 4)

    # ---- pauldrons (80,16 / 80,24 5x3x5): layered lames, teal edge
    for (u, v) in ((80, 16), (80, 24)):
        f = box(img, u, v, 5, 3, 5, "l")
        fill(img, f["top"], "l", 5)
        tx, ty, tw, th = f["top"]
        for i in range(tw):
            px(img, tx + i, ty + 2, "w")
        for k in ("front", "back", "right", "left"):
            x, y, w, h = f[k]
            for i in range(w):
                px(img, x + i, y, "w")
                px(img, x + i, y + 1, "m")
                px(img, x + i, y + h - 1, "a")
        fill(img, f["bottom"], "o", 3)

    # ---- the echo blade (64,0 1x12x2), guard (64,16 5x1x1), grip (64,20 1x4x1)
    f = box(img, 64, 0, 1, 12, 2, "l", 4)
    for k in ("right", "left"):
        x, y, w, h = f[k]
        for j in range(h):
            px(img, x, y + j, "w")
            px(img, x + w - 1, y + j, "b" if j % 3 else "c")  # the edge hums teal
    for k in ("front", "back"):
        x, y, w, h = f[k]
        for j in range(h):
            px(img, x, y + j, "m" if j < 2 else "l")
    x, y, w, h = f["bottom"]
    px(img, x, y, "c")
    box(img, 64, 16, 5, 1, 1, "d", 4)
    fx, fy, fw, fh = faces(64, 16, 5, 1, 1)["front"]
    px(img, fx + 2, fy, "a")
    f = box(img, 64, 20, 1, 4, 1, "o", 3)
    for k in ("front", "back", "right", "left"):
        x, y, w, h = f[k]
        px(img, x, y + 1, "d")
        px(img, x, y + 3, "d")

    # ---- cape (64,32 8x18x0): the Order's black cape, teal-edged, frayed into nothing at the hem
    for (x0, y0) in ((64, 32), (72, 32)):
        for j in range(18):
            for i in range(8):
                col = "o" if (i + j // 4) % 4 else "d"
                if i in (0, 7):
                    col = "a"
                frayed = j >= 15 and (i * 7 + j * 3) % 5 < (j - 14)
                img.putpixel((x0 + i, y0 + j), (0, 0, 0, 0) if frayed else rgba(jitter(P[col], 5)))
    return img


def glow_tex():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    # visor slit (head front, row 3)
    x, y, w, h = faces(0, 0, 8, 8, 8)["front"]
    for i in range(1, w - 1):
        if i not in (3, 4):
            px(img, x + i, y + 3, "c" if i in (2, 5) else "b")
    # the sigil on the tabard front
    x, y, w, h = faces(0, 32, 8, 14, 4)["front"]
    bell = ["..bb..", ".b..b.", ".b..b.", "bbbbbb", "......", "cccccc"]
    for j, row in enumerate(bell):
        for i, ch in enumerate(row):
            if ch != ".":
                px(img, x + 1 + i, y + 1 + j, ch, 220)
    # blade edges
    f = faces(64, 0, 1, 12, 2)
    for k in ("right", "left"):
        x, y, w, h = f[k]
        for j in range(h):
            px(img, x + w - 1, y + j, "b", 200 if j % 3 else 255)
    x, y, w, h = f["bottom"]
    px(img, x, y, "c")
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    body_tex().save(os.path.join(OUT, "echo_knight.png"))
    glow_tex().save(os.path.join(OUT, "echo_knight_glow.png"))
    print("wrote", os.path.join(OUT, "echo_knight.png"), "and echo_knight_glow.png")


if __name__ == "__main__":
    main()
