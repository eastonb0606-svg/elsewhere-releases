#!/usr/bin/env python3
"""3.0 polish P3 (s-p3-nameplates): the nameplate glyph atlas for kingdom soldiers and court rulers.

Writes src/main/resources/assets/elsewhere/textures/font/nameplate.png (8 x 2 cells of 16 x 16, drawn at text height
by assets/elsewhere/font/nameplate.json) and, if a path is given as the first argument, an 8x preview on a dark background (not shipped).
Row 0 (U+E000..): officer chevrons, captain star, lord coronet, sovereign crown, Mirewick chain of office.
Row 1 (U+E010..): crests: Aldermark heater shield, Hrafnholt round shield, Azmar ankh, Mirewick lantern, rebels torch,
generic banner (white: the game tints it with the faction's tabard colour).
Glyph content stays in rows 0-13 of each cell (cap height of the default font at 2x) so icons sit on the baseline.
Run: _tools/venv/bin/python -I kingdom/tools/gen_nameplate_glyphs.py
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "font", "nameplate.png")
PREVIEW = sys.argv[1] if len(sys.argv) > 1 else None  # optional 8x preview path (not shipped)
C = 16


def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


GOLD_HI, GOLD, GOLD_SH = hexc("FFE58A"), hexc("F2C14E"), hexc("B9821E")
SILV_HI, SILV, SILV_SH = hexc("F2F4F7"), hexc("C9CFD8"), hexc("7E8794")
RED, BLUE_GEM = hexc("D23A3A"), hexc("4A7BE0")


class Cell:
    def __init__(self, img, col, row):
        self.img, self.ox, self.oy = img, col * C, row * C

    def px(self, x, y, c):
        if 0 <= x < C and 0 <= y < C:
            self.img.putpixel((self.ox + x, self.oy + y), c)

    def get(self, x, y):
        return self.img.getpixel((self.ox + x, self.oy + y))

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, c)

    def outline(self, c, inside_alpha_min=1):
        """1px outline in colour c around every opaque pixel (outside only)."""
        opaque = {(x, y) for x in range(C) for y in range(C) if self.get(x, y)[3] >= inside_alpha_min}
        for (x, y) in list(opaque):
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                q = (x + dx, y + dy)
                if q not in opaque and 0 <= q[0] < C and 0 <= q[1] < C:
                    self.px(q[0], q[1], c)


# ---- row 0: rank marks ----------------------------------------------------------------------------------------------

def chevrons(c):
    """Sergeant: two stacked silver chevrons pointing up."""
    for top in (2, 7):
        for i in range(7):
            for t in range(3):
                y = top + i + t - 0  # thickness 3 going down
                xl, xr = 7 - i, 8 + i
                col = SILV_HI if t == 0 else (SILV if t == 1 else SILV_SH)
                if y <= top + 7:
                    c.px(xl, y, col)
                    c.px(xr, y, col)


def star(c):
    """Captain: a gold five-pointed star."""
    cx, cy, ro, ri = 7.5, 7.2, 7.4, 3.0
    pts = []
    for k in range(10):
        r = ro if k % 2 == 0 else ri
        a = -math.pi / 2 + k * math.pi / 5
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))

    def inside(x, y):
        n, j, ok = len(pts), len(pts) - 1, False
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi:
                ok = not ok
            j = i
        return ok

    for y in range(C):
        for x in range(C):
            if inside(x + 0.5, y + 0.5):
                col = GOLD_HI if (x + 0.5 < cx and y + 0.5 < cy + 1) else (GOLD if y < 10 else GOLD_SH)
                c.px(x, y, col)


def band(c, y0, y1, gems):
    c.rect(1, y0, 14, y1, GOLD)
    c.rect(1, y0, 14, y0, GOLD_HI)
    c.rect(1, y1, 14, y1, GOLD_SH)
    for x, col in gems:
        c.px(x, (y0 + y1) // 2, col)


def coronet(c):
    """Lord: a low gold coronet with three pearl-tipped points."""
    band(c, 9, 12, [(4, BLUE_GEM), (11, BLUE_GEM), (7, RED), (8, RED)])
    for x in (2, 7, 13):
        c.rect(x, 6, x + 1 if x != 7 else x + 1, 8, GOLD)
        c.px(x, 6, GOLD_HI)
        c.rect(x, 4, x + 1 if x != 7 else x + 1, 5, SILV_HI)  # pearls
    c.px(14, 6, GOLD_SH)
    c.px(14, 7, GOLD_SH)


def crown(c):
    """Sovereign: a tall gold crown, five points, red centre stone, blue side stones."""
    band(c, 10, 13, [(4, BLUE_GEM), (11, BLUE_GEM), (7, RED), (8, RED)])
    # body between band and points
    c.rect(1, 6, 14, 9, GOLD)
    for x in (1, 2):
        c.px(x, 6, GOLD_HI)
    # points: x positions and heights
    for x0, top in ((1, 2), (4, 4), (7, 1), (10, 4), (13, 2)):
        c.rect(x0, top + 1, x0 + 1, 6, GOLD)
        c.px(x0, top + 1, GOLD_HI)
        c.px(x0, top, SILV_HI)
        c.px(x0 + 1, top, SILV_HI)
    # notches between points (keep the zig-zag readable)
    for x in (3, 6, 9, 12):
        c.px(x, 6, (0, 0, 0, 0))
    c.rect(7, 7, 8, 8, RED)  # centre jewel on the body
    c.px(7, 7, hexc("FF7A7A"))


def chain(c):
    """Mirewick: a magnate's chain of office (gold links, round medallion with a purple lantern-glass centre)."""
    links = [(2, 1), (2, 2), (3, 3), (3, 4), (4, 5), (5, 6), (6, 7), (9, 7), (10, 6), (11, 5), (12, 4), (12, 3), (13, 2), (13, 1)]
    for i, (x, y) in enumerate(links):
        c.px(x, y, GOLD_HI if i % 2 == 0 else GOLD_SH)
    cx, cy, r = 7.5, 10.5, 3.4
    for y in range(C):
        for x in range(C):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                col = GOLD if d > r - 1.1 else hexc("5B2A86")
                if d > r - 1.1 and y + 0.5 < cy:
                    col = GOLD_HI
                if d > r - 1.1 and y + 0.5 > cy + 1.5:
                    col = GOLD_SH
                c.px(x, y, col)
    c.px(7, 10, hexc("D6A832"))  # flame glint in the glass
    c.px(7, 7, GOLD)
    c.px(8, 7, GOLD)


# ---- row 1: crests --------------------------------------------------------------------------------------------------

def heater(c, field, field_hi, border, charge):
    """Aldermark: heater shield, blue field, cream border and cross."""
    def inside(x, y):
        if y < 1 or y > 14 or x < 2 or x > 13:
            return False
        if y <= 7:
            return True
        # curve to the point at (7.5, 14.5)
        half = 6.0 * math.sqrt(max(0.0, 1 - ((y + 0.5 - 7.5) / 7.2) ** 2))
        return abs(x + 0.5 - 8.0) <= half

    pix = {(x, y) for x in range(C) for y in range(C) if inside(x, y)}
    for (x, y) in pix:
        edge = any((x + dx, y + dy) not in pix for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        c.px(x, y, border if edge else field)
    # highlight on the upper-left of the field
    for (x, y) in pix:
        if c.get(x, y) == field and x < 6 and y < 6:
            c.px(x, y, field_hi)
    for y in range(3, 13):
        for x in (7, 8):
            if (x, y) in pix and c.get(x, y)[:3] != border[:3]:
                c.px(x, y, charge)
    for x in range(4, 12):
        for y in (5, 6):
            if (x, y) in pix and c.get(x, y)[:3] != border[:3]:
                c.px(x, y, charge)


def round_shield(c):
    """Hrafnholt: a painted round shield in quarters (green and brown), brown rim, iron boss."""
    cx, cy, r = 7.5, 7.0, 6.9
    green, green_hi, brown, rim = hexc("3F8A3A"), hexc("5DAA55"), hexc("8A5E34"), hexc("5A3B20")
    for y in range(C):
        for x in range(C):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d <= r:
                if d > r - 1.2:
                    col = rim
                else:
                    col = green if (dx < 0) == (dy < 0) else brown
                    if d > r - 2.2 and dx < 0 and dy < 0:
                        col = green_hi  # light catching the upper-left edge
                c.px(x, y, col)
    # iron boss
    for y in range(C):
        for x in range(C):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= 2.2:
                c.px(x, y, hexc("C6CCD2") if (x + 0.5 < cx and y + 0.5 < cy) else hexc("8E959C"))


def ankh(c):
    """Azmar: a golden ankh with a teal stone in the loop."""
    # loop: oval ring
    cx, cy, rx, ry = 7.5, 4.0, 3.6, 4.0
    for y in range(C):
        for x in range(C):
            v = ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2
            if 0.38 <= v <= 1.0 and y <= 7:
                c.px(x, y, GOLD_HI if x + 0.5 < cx else GOLD)
            elif v < 0.38 and y <= 6:
                pass
    c.rect(7, 3, 8, 5, hexc("2BB3A3"))  # teal stone inside the loop
    c.px(7, 3, hexc("7FE3D8"))
    # crossbar
    c.rect(2, 8, 13, 9, GOLD)
    c.rect(2, 8, 13, 8, GOLD_HI)
    c.px(2, 9, GOLD_SH)
    c.px(13, 9, GOLD_SH)
    # stem, flaring slightly at the foot
    c.rect(7, 10, 8, 13, GOLD)
    c.px(8, 10, GOLD_SH)
    c.px(8, 11, GOLD_SH)
    c.rect(6, 14, 9, 14, GOLD_SH)
    c.rect(6, 13, 9, 13, GOLD)


def lantern(c):
    """Mirewick: the Lantern Republic's lantern: gold frame, purple cap and foot, crimson-cored amber glow."""
    purple, purple_hi = hexc("5B2A86"), hexc("7E45AE")
    # ring
    c.px(7, 0, GOLD)
    c.px(8, 0, GOLD)
    c.px(6, 1, GOLD)
    c.px(9, 1, GOLD)
    # cap
    c.rect(6, 2, 9, 2, purple_hi)
    c.rect(4, 3, 11, 3, purple)
    c.px(4, 3, purple_hi)
    # glass body with frame
    for y in range(4, 12):
        c.px(4, y, GOLD)
        c.px(11, y, GOLD_SH)
        for x in range(5, 11):
            d = math.hypot(x + 0.5 - 8.0, y + 0.5 - 8.2)
            col = hexc("FFC864") if d > 2.6 else (hexc("FF8A3A") if d > 1.4 else hexc("C2203E"))
            c.px(x, y, col)
    c.rect(5, 7, 10, 7, GOLD)  # middle cross bar of the frame
    c.px(7, 7, hexc("FFE58A"))
    # foot
    c.rect(4, 12, 11, 12, purple)
    c.rect(5, 13, 10, 13, purple_hi)


def torch(c):
    """Rebels: a raised torch (rebellion)."""
    handle, handle_sh, wrap = hexc("8A5E34"), hexc("5A3B20"), hexc("A9B0BA")
    c.rect(7, 8, 8, 14, handle)
    c.rect(8, 8, 8, 14, handle_sh)
    c.rect(6, 7, 9, 8, wrap)
    flame = {
        0: [(8, hexc("FFD75A"))],
        1: [(7, hexc("FFD75A")), (8, hexc("F08A24"))],
        2: [(6, hexc("F08A24")), (7, hexc("FFE58A")), (8, hexc("FFD75A")), (9, hexc("C2352B"))],
        3: [(5, hexc("C2352B")), (6, hexc("F08A24")), (7, hexc("FFE58A")), (8, hexc("FFD75A")), (9, hexc("F08A24")), (10, hexc("C2352B"))],
        4: [(5, hexc("C2352B")), (6, hexc("F08A24")), (7, hexc("FFD75A")), (8, hexc("FFE58A")), (9, hexc("F08A24")), (10, hexc("C2352B"))],
        5: [(6, hexc("C2352B")), (7, hexc("F08A24")), (8, hexc("FFD75A")), (9, hexc("C2352B"))],
        6: [(6, hexc("9E2E2E")), (7, hexc("C2352B")), (8, hexc("F08A24")), (9, hexc("9E2E2E"))],
    }
    for y, row in flame.items():
        for x, col in row:
            c.px(x, y, col)


def banner(c):
    """Generic: a hanging banner on a crossbar with a notched foot, white so the faction tint shows."""
    pole, white, shade = hexc("E6E6E6"), hexc("FFFFFF"), hexc("CFCFCF")
    c.rect(7, 0, 8, 0, pole)  # finial
    c.rect(2, 1, 13, 1, pole)  # crossbar
    for y in range(2, 14):
        for x in range(3, 13):
            if y >= 11 and abs(x + 0.5 - 8.0) < (y - 10) * 1.0:
                continue  # V notch at the foot
            c.px(x, y, shade if (x == 12 or x == 3 or y == 2) else white)


def main():
    img = Image.new("RGBA", (8 * C, 2 * C), (0, 0, 0, 0))
    row0 = [chevrons, star, coronet, crown, chain]
    for i, f in enumerate(row0):
        f(Cell(img, i, 0))
    heater_args = (hexc("3A5FB8"), hexc("5A80D6"), hexc("E8D9A0"), hexc("E8D9A0"))
    row1 = [lambda c: heater(c, *heater_args), round_shield, ankh, lantern, torch, banner]
    for i, f in enumerate(row1):
        f(Cell(img, i, 1))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    # preview: 8x, on the nameplate's dark background
    if not PREVIEW:
        print("wrote", os.path.normpath(OUT), img.size)
        return 0
    bg = Image.new("RGBA", img.size, (24, 24, 24, 255))
    bg.alpha_composite(img)
    bg.resize((img.width * 8, img.height * 8), Image.NEAREST).save(PREVIEW)
    print("wrote", os.path.normpath(OUT), img.size)


if __name__ == "__main__":
    sys.exit(main())
