"""s-totems: entity textures for the three gear3 totems (64x64, UV boxes match client TotemModel) + the beam strip.

Writes src/main/resources/assets/elsewhere/textures/entity/totem/{bramble_totem,clockwork_sentry,azmar_sunglass_obelisk,totem_beam}.png
Deterministic (seeded). Run: _tools/venv/bin/python -I gear3/tools/totems_art.py
"""
import os
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "totem")


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def faces(u, v, w, h, d):
    """Vanilla cube UV: name -> (x0, y0, width, height)."""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def paint(img, rect, fn):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            c = fn(x, y, w, h)
            if c is not None:
                img.putpixel((x0 + x, y0 + y), c)


def box(img, u, v, w, h, d, side, top=None, bottom=None, front=None):
    f = faces(u, v, w, h, d)
    for name in ("right", "left", "back"):
        paint(img, f[name], side)
    paint(img, f["front"], front or side)
    paint(img, f["top"], top or side)
    paint(img, f["bottom"], bottom or top or side)


def pick(rng, pal):
    return pal[rng.randrange(len(pal))]


# ---------------------------------------------------------------- Bramble Totem (Greenhaze)
def bramble():
    rng = random.Random(7)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    bark = [hexc("4a3423"), hexc("553c28"), hexc("5f4430"), hexc("3f2c1d")]
    groove = hexc("2c1f14")
    moss = [hexc("4f7a2e"), hexc("5d8c34"), hexc("6e9e3c")]
    thorn = hexc("c9b38a")
    pollen = hexc("d9e36a")

    def bark_side(x, y, w, h):
        if x % 3 == 1 and rng.random() < 0.7:
            return groove
        if rng.random() < 0.06:
            return thorn
        if y < 3 and rng.random() < 0.5:
            return pick(rng, moss)
        return pick(rng, bark)

    def ring_top(x, y, w, h):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = max(abs(x - cx), abs(y - cy))
        return hexc("8a6a45") if int(r) % 2 == 0 else hexc("6e5234")

    box(img, 0, 0, 6, 16, 6, bark_side, top=ring_top)

    def crown_side(x, y, w, h):
        if y == 0 or rng.random() < 0.35:
            return pick(rng, moss)
        if rng.random() < 0.08:
            return pollen
        return pick(rng, bark)

    def crown_top(x, y, w, h):
        if rng.random() < 0.12:
            return pollen
        return pick(rng, moss)

    box(img, 24, 0, 8, 3, 8, crown_side, top=crown_top)

    def fern(x, y, w, h):
        cx = w // 2
        if abs(x - cx + 0.5) < 0.6 and y >= 1:
            return hexc("3d5e22")
        # leaflets: pairs that shorten toward the tip (y=0 is the top)
        reach = max(0, (y + 1) * 5 // h)
        if 1 <= y and abs(x - cx + 0.5) <= reach + 0.5 and (y % 2 == 0 or abs(x - cx + 0.5) <= reach - 0.5):
            return pick(rng, [hexc("5f9a35"), hexc("74b043"), hexc("4f8a2c")])
        return None

    paint(img, (0, 24, 10, 8), fern)
    paint(img, (10, 24, 10, 8), fern)
    paint(img, (20, 24, 10, 8), fern)
    paint(img, (30, 24, 10, 8), fern)

    def vine(x, y, w, h):
        if rng.random() < 0.18:
            return thorn
        return pick(rng, [hexc("3f5f26"), hexc("4b6e2c"), hexc("56402a")])

    box(img, 40, 24, 2, 10, 2, vine)
    box(img, 48, 24, 2, 10, 2, vine)

    def roots(x, y, w, h):
        return pick(rng, [hexc("3a281a"), hexc("4a3423"), hexc("2f2116")]) if rng.random() < 0.85 else pick(rng, moss)

    box(img, 0, 40, 8, 2, 8, roots)
    return img


# ---------------------------------------------------------------- Clockwork Sentry (Verdigris Sea)
def sentry():
    rng = random.Random(11)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    copper = [hexc("b8733f"), hexc("a9663a"), hexc("c47f48")]
    patina = [hexc("4fa28c"), hexc("3e8a78"), hexc("5cb39b")]
    brass = [hexc("c9a23f"), hexc("b8902f"), hexc("d8b452")]
    iron = [hexc("3b3f45"), hexc("2e3136"), hexc("474c53")]

    def leg(x, y, w, h):
        if y >= h - 2:
            return pick(rng, iron)
        return pick(rng, patina) if rng.random() < 0.3 + y * 0.03 else pick(rng, copper)

    for i in range(3):
        box(img, i * 8, 10, 2, 10, 2, leg)

    def plate(x, y, w, h):
        if (x in (0, w - 1)) and (y in (0, h - 1)):
            return hexc("e0c071")  # rivets
        if y == h - 1 or rng.random() < 0.25:
            return pick(rng, patina)
        return pick(rng, copper)

    box(img, 24, 10, 6, 6, 6, plate)

    def head_side(x, y, w, h):
        if y == 0:
            return pick(rng, brass)
        if rng.random() < 0.2:
            return pick(rng, patina)
        return pick(rng, copper)

    def head_front(x, y, w, h):
        if y == 2 and 1 <= x <= w - 2:
            return hexc("ffb347") if x == w // 2 else hexc("1d1f22")  # sighting slit with a lit eye
        return head_side(x, y, w, h)

    def head_top(x, y, w, h):
        return pick(rng, brass) if rng.random() < 0.7 else pick(rng, patina)

    box(img, 0, 24, 7, 5, 7, head_side, top=head_top, front=head_front)
    box(img, 28, 24, 2, 2, 6, lambda x, y, w, h: pick(rng, iron))
    box(img, 28, 32, 12, 1, 1, lambda x, y, w, h: pick(rng, [hexc("6b4a2b"), hexc("7a5532")]) if 2 < x < 22 else hexc("c9a23f"))
    box(img, 44, 24, 2, 2, 2, lambda x, y, w, h: hexc("ffcf5a") if (x + y) % 2 == 0 else hexc("ffb347"))

    def key(x, y, w, h):
        if (x in (0, w - 1)) and (y in (0, h - 1)):
            return None
        return pick(rng, brass)

    box(img, 48, 0, 4, 4, 1, key)
    return img


# ---------------------------------------------------------------- Sunglass Obelisk (Azmar)
def obelisk():
    rng = random.Random(23)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    sand = [hexc("d8c38a"), hexc("cdb57a"), hexc("dcc893"), hexc("c8af72")]
    shade = hexc("b89b5e")
    gold = [hexc("e8c04a"), hexc("f5da6a"), hexc("d4a93a")]
    lapis = hexc("2e4fa0")
    ink = hexc("6b4e2a")

    def plinth(x, y, w, h):
        if y == h - 1:
            return shade
        if y == 0 and x % 2 == 0:
            return pick(rng, gold)
        return pick(rng, sand)

    box(img, 0, 0, 10, 3, 10, plinth, top=lambda x, y, w, h: pick(rng, sand))

    # hieroglyph columns on the shaft sides: eye, sun disc, reed, water lines (2-px wide glyphs, one per 4 rows)
    glyphs = [
        ["..", "XX", "X.", ".."], [".X", "XX", ".X", ".."], ["X.", "X.", "XX", ".."], ["XX", "..", "XX", ".."],
    ]

    def shaft(x, y, w, h):
        if x in (0, w - 1):
            return shade if y % 4 == 0 else pick(rng, sand)
        g = glyphs[(y // 4 + x // 3) % len(glyphs)]
        gx, gy = (x - 1) % 3, y % 4
        if gx < 2 and g[gy][gx] == "X" and 2 <= y <= h - 3:
            return lapis if (y // 4) % 2 == 0 else ink
        return pick(rng, sand)

    box(img, 0, 13, 6, 18, 6, shaft, top=lambda x, y, w, h: pick(rng, sand))
    box(img, 24, 13, 4, 3, 4, lambda x, y, w, h: pick(rng, gold))
    band = lambda x, y, w, h: pick(rng, gold) if (x + y) % 3 else hexc("fff0a8")
    box(img, 0, 40, 7, 1, 7, band)
    box(img, 28, 40, 7, 1, 7, band)

    def lens(x, y, w, h):
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
        if r < 1.0:
            return hexc("fffbe0")
        if r < 1.6:
            return hexc("ffe38a")
        return hexc("f7b733")

    box(img, 48, 0, 4, 4, 1, lens)
    return img


def beam():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        for x in range(16):
            t = abs(x - 7.5) / 7.5
            a = int(255 * max(0.0, 1.0 - t ** 1.6))
            wob = 225 + (30 if (y // 2) % 2 == 0 else 0)
            img.putpixel((x, y), (255, 255, 255, min(255, a * wob // 255)))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (("bramble_totem", bramble), ("clockwork_sentry", sentry), ("azmar_sunglass_obelisk", obelisk), ("totem_beam", beam)):
        p = os.path.join(OUT, name + ".png")
        fn().save(p)
        print("totems_art:", os.path.relpath(p, os.path.join(HERE, "..")))


if __name__ == "__main__":
    main()
