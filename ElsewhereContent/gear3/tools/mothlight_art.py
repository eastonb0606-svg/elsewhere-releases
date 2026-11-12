"""s-mothlight: Mothlight Staff textures (dev.elsewhere.gear3.s3.mothlight).

  textures/entity/mothlight_swarm.png  64x32  UV layout of client/MothlightSwarmModel (cutout: alpha 0 or 255 only)
      body   texOffs(0,0)   2x2x4 box  -> 0..12 x 0..6   pale cream, grey-brown abdomen stripes
      head   texOffs(0,8)   1x1x1 box  -> 0..4  x 8..10  dark eyes on the front and sides
      antenna texOffs(0,12) 3x0x2 plane-> 0..10 x 12..14 two brown feelers (outer columns)
      wings  texOffs(16,0)  8x0x6 plane-> 22..38 x 0..6  both faces; one symmetric oval (both wings share it)
  textures/mob_effect/mothdust.png     18x18  a pale moth with amber eyespots in a drift of dust
Palette (the staff sprite's own): cream #e4e0da, lilac #c3c7ef, outline #8f877c, amber eyespot #f2a43a,
body #f2eee6 / stripe #b8b2a8, antenna #6b4a2a.

Run: ROOT/_tools/venv/bin/python -I gear3/tools/mothlight_art.py   (pure stdlib; rewrites both files)
"""
import os
import struct
import sys
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "elsewhere", "textures")


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


CLEAR = (0, 0, 0, 0)
CREAM, LILAC, OUTLINE, AMBER = hexc("#e4e0da"), hexc("#c3c7ef"), hexc("#8f877c"), hexc("#f2a43a")
BODY, STRIPE, ANTENNA = hexc("#f2eee6"), hexc("#b8b2a8"), hexc("#6b4a2a")
FUZZ, HEAD, EYE = hexc("#fbf8f2"), hexc("#d9d2c6"), hexc("#3b2a1a")
DARK, DUST = hexc("#4f4842"), hexc("#ece6f7")


def png(w, h, px):
    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in px)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


# 8x6 wing, symmetric both ways: o outline, a lilac fringe, l cream, e amber eyespot
WING = ["..oooo..",
        ".oallao.",
        "oaleelao",
        "oaleelao",
        ".oallao.",
        "..oooo.."]
WING_PAL = {"o": OUTLINE, "a": LILAC, "l": CREAM, "e": AMBER, ".": CLEAR}


def swarm():
    px = [[CLEAR] * 64 for _ in range(32)]

    def put(x, y, c):
        px[y][x] = c

    # body (0,0), w=2 h=2 d=4: down 4..6 x 0..4, up 6..8 x 0..4, west 0..4 x 4..6, north 4..6 x 4..6, east 6..10 x 4..6, south 10..12 x 4..6
    for x0 in (4, 6):
        for y in range(4):
            for x in range(x0, x0 + 2):
                put(x, y, FUZZ if y == 0 else (STRIPE if y in (2,) else BODY))
    for x0 in (0, 6):
        for k in range(4):
            for y in (4, 5):
                put(x0 + k, y, STRIPE if k in (1, 3) else BODY)
    for x in (4, 5):
        for y in (4, 5):
            put(x, y, FUZZ)
    for x in (10, 11):
        for y in (4, 5):
            put(x, y, STRIPE)
    # head (0,8), 1x1x1: down (1,8), up (2,8), west (0,9), north (1,9), east (2,9), south (3,9)
    for x, y in ((1, 8), (2, 8), (3, 9)):
        put(x, y, HEAD)
    for x, y in ((0, 9), (1, 9), (2, 9)):
        put(x, y, EYE)
    # antennae (0,12), w=3 h=0 d=2: down 2..5 x 12..14, up 5..8 x 12..14; outer columns only
    for x0 in (2, 5):
        for y in (12, 13):
            put(x0, y, ANTENNA)
            put(x0 + 2, y, ANTENNA)
    # wings (16,0), w=8 h=0 d=6: down 22..30 x 0..6, up 30..38 x 0..6
    for x0 in (22, 30):
        for y, row in enumerate(WING):
            for k, ch in enumerate(row):
                put(x0 + k, y, WING_PAL[ch])
    return png(64, 32, px)


def mothdust():
    n = 18
    fill = [[False] * n for _ in range(n)]
    col = [[CLEAR] * n for _ in range(n)]
    cx = 8.5
    for y in range(n):
        for x in range(n):
            if abs(x - cx) <= 0.5:
                continue
            side = 1 if x > cx else -1
            upper = ((x - (cx + side * 4.0)) / 4.3) ** 2 + ((y - 7.0) / 3.1) ** 2 <= 1.0
            lower = ((x - (cx + side * 2.8)) / 2.9) ** 2 + ((y - 11.6) / 2.2) ** 2 <= 1.0
            if upper or lower:
                fill[y][x] = True
    for y in range(n):
        for x in range(n):
            if not fill[y][x]:
                continue
            edge = any(not (0 <= x + dx < n and 0 <= y + dy < n and fill[y + dy][x + dx]) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            col[y][x] = LILAC if edge else CREAM
    for x, y in ((4, 6), (4, 7), (13, 6), (13, 7)):  # eyespots
        col[y][x] = AMBER
    for y in range(4, 15):  # body, striped abdomen
        for x in (8, 9):
            fill[y][x] = True
            col[y][x] = STRIPE if y >= 9 and y % 2 == 0 else BODY
    for x in (8, 9):
        fill[3][x] = True
        col[3][x] = HEAD
    for x, y in ((7, 2), (6, 1), (10, 2), (11, 1)):  # antennae
        fill[y][x] = True
        col[y][x] = ANTENNA
    out = [row[:] for row in col]
    for y in range(n):
        for x in range(n):
            if fill[y][x]:
                continue
            if any(0 <= x + dx < n and 0 <= y + dy < n and fill[y + dy][x + dx] and col[y + dy][x + dx] != ANTENNA
                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = DARK
    for x, y in ((1, 14), (2, 16), (0, 11), (16, 15), (15, 17), (17, 12), (1, 3), (16, 4)):  # drifting dust
        if out[y][x] == CLEAR:
            out[y][x] = DUST
    return png(n, n, out)


def main():
    files = {os.path.join(TEX, "entity", "mothlight_swarm.png"): swarm(),
             os.path.join(TEX, "mob_effect", "mothdust.png"): mothdust()}
    for path, data in files.items():
        os.makedirs(os.path.dirname(path), exist_ok=True)
        open(path, "wb").write(data)
        print("mothlight_art: wrote", os.path.relpath(path, os.path.dirname(os.path.dirname(HERE))))
    return 0


if __name__ == "__main__":
    sys.exit(main())
