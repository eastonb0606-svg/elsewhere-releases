"""War-Yak entity texture (128x128) for WarYakModel (src/client/java/.../s3/waryak/client/WarYakModel.java).
Owner: s-waryak (3.0 swarm). Run: ROOT/_tools/venv/bin/python -I gear3/tools/war_yak_texture.py [--preview out.png]
Box UV layout (vanilla): top (u+d, v) w x d, bottom (u+d+w, v) w x d; strip at v+d: -x side d x h, front (-z) w x h,
+x side d x h, back w x h. Palette: Ironpeak highland-yak fur (verdigris_ironpeak gen_assets highland_yak ramp, darker
for a war beast), skyiron barding (brow plate, horn caps, blanket trim) and the Ironclan's crimson war blanket with a
frost-grey border and a white peak sigil.
"""
import os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "war_yak.png")
W = H = 128
rng = random.Random(26_3_0)


def hx(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


FUR = [hx("#140c08"), hx("#24160e"), hx("#352013"), hx("#4a2c19"), hx("#5e3a21"), hx("#7a5032")]
SKIRT = [hx("#100906"), hx("#1c110b"), hx("#2b1a10"), hx("#3c2416"), hx("#4e301c")]
HORN = [hx("#8e8270"), hx("#b8ab90"), hx("#d6ccb6"), hx("#ece4d2")]
SKY = [hx("#3c4a58"), hx("#5e7286"), hx("#8aa2b8"), hx("#b8cad8"), hx("#e2ecf4")]
CRIMSON = [hx("#4a1010"), hx("#6a1818"), hx("#8a2420"), hx("#a8342a")]
FROST = [hx("#8a9096"), hx("#c8ccd2"), hx("#eef0f2")]
MUZZLE = [hx("#3a2e28"), hx("#5a4c42"), hx("#7a6a5c"), hx("#948472")]
HOOF = [hx("#0e0b09"), hx("#1e1914"), hx("#2e2620")]
EYE = hx("#060403")
EYE_HI = hx("#d8e4ee")

img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
px = img.load()


def put(x, y, c):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = c


def faces(u, v, w, h, d):
    """name -> (x0, y0, width, height)"""
    return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "west": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}


def fur_fill(rect, ramp, shade=0, streak=False, bottom_dark=True):
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            t = rng.random()
            i = 2 + (1 if t > 0.72 else 0) + (1 if t > 0.93 else 0) - (1 if t < 0.22 else 0)
            if streak and ((x * 7 + (x // 2) * 3) % 5 == 0):
                i -= 1
            if bottom_dark and fh > 2:
                i -= int(2.2 * y / fh)
            i += shade
            put(x0 + x, y0 + y, ramp[max(0, min(len(ramp) - 1, i))])


def box(u, v, w, h, d, ramp, **kw):
    f = faces(u, v, w, h, d)
    fur_fill(f["top"], ramp, shade=1, bottom_dark=False, streak=kw.get("streak", False))
    fur_fill(f["bottom"], ramp, shade=-1, bottom_dark=False)
    for n in ("west", "front", "east", "back"):
        fur_fill(f[n], ramp, shade=kw.get("side_shade", 0), streak=kw.get("streak", False), bottom_dark=kw.get("bottom_dark", True))
    return f


def flat(rect, ramp, base=1, speck=0.25):
    x0, y0, fw, fh = rect
    for y in range(fh):
        for x in range(fw):
            i = base + (1 if rng.random() < speck else 0) - (1 if rng.random() < speck * 0.5 else 0)
            put(x0 + x, y0 + y, ramp[max(0, min(len(ramp) - 1, i))])


# ---- body 14x12x20 @ (0,0): shaggy, darker belly; the blanket and hump cover the back
body = box(0, 0, 14, 12, 20, FUR)
# ---- skirt 15x6x20 @ (0,32): long hanging strands, ragged dark hem
skirt = box(0, 32, 15, 6, 20, SKIRT, streak=True, side_shade=1)
for n in ("west", "front", "east", "back"):
    x0, y0, fw, fh = skirt[n]
    for x in range(fw):
        if rng.random() < 0.45:
            put(x0 + x, y0 + fh - 1, (0, 0, 0, 0))  # ragged hem (cutout)
# ---- hump 10x5x9 @ (70,0): sun-bleached tips on top
hump = box(70, 0, 10, 5, 9, FUR, side_shade=1)
x0, y0, fw, fh = hump["top"]
for y in range(fh):
    for x in range(fw):
        if rng.random() < 0.35:
            put(x0 + x, y0 + y, FUR[5])
# ---- war blanket 15x7x11 @ (0,58): Ironclan crimson, frost-grey border, skyiron spine trim, white peak sigil on the flanks
bl = faces(0, 58, 15, 7, 11)
for n, r in bl.items():
    flat(r, CRIMSON, base=2 if n == "top" else 1, speck=0.2)
tx, ty, tw, td = bl["top"]
for y in range(td):
    put(tx, ty + y, FROST[1]); put(tx + tw - 1, ty + y, FROST[1])
    put(tx + tw // 2, ty + y, SKY[3]); put(tx + tw // 2 - 1, ty + y, SKY[2]); put(tx + tw // 2 + 1, ty + y, SKY[2])
for x in range(tw):
    put(tx + x, ty, FROST[1]); put(tx + x, ty + td - 1, FROST[1])
for n in ("west", "east", "front", "back"):
    x0, y0, fw, fh = bl[n]
    for x in range(fw):
        put(x0 + x, y0 + fh - 1, FROST[0])  # hem
        put(x0 + x, y0 + fh - 2, FROST[1] if (x % 2 == 0) else FROST[2])  # fringe band
    put(x0, y0 + fh - 3, FROST[1]); put(x0 + fw - 1, y0 + fh - 3, FROST[1])
for n in ("west", "east"):
    x0, y0, fw, fh = bl[n]  # 11 x 7: a white peak (chevron) with a skyiron dot, centred
    cx = x0 + fw // 2
    for k in range(4):
        put(cx - k, y0 + 1 + k, FROST[2]); put(cx + k, y0 + 1 + k, FROST[2])
    put(cx, y0 + 3, SKY[3])
# ---- head 8x8x8 @ (70,14): fur; eyes set on the sides near the front
head = box(70, 14, 8, 8, 8, FUR, side_shade=0)
x0, y0, fw, fh = head["west"]
put(x0 + fw - 2, y0 + 3, EYE); put(x0 + fw - 2, y0 + 2, EYE_HI)
x0, y0, fw, fh = head["east"]
put(x0 + 1, y0 + 3, EYE); put(x0 + 1, y0 + 2, EYE_HI)
x0, y0, fw, fh = head["front"]
for x in range(fw):  # forelock above the brow plate
    put(x0 + x, y0, FUR[4] if x % 2 else FUR[3])
# ---- muzzle 6x5x3 @ (102,14): pale grey-brown, two nostrils, dark mouth line
mz = faces(102, 14, 6, 5, 3)
for n, r in mz.items():
    flat(r, MUZZLE, base=2 if n in ("top", "front") else 1, speck=0.3)
x0, y0, fw, fh = mz["front"]
put(x0 + 1, y0 + 1, EYE); put(x0 + 4, y0 + 1, EYE)
put(x0 + 1, y0 + 2, MUZZLE[0]); put(x0 + 4, y0 + 2, MUZZLE[0])
for x in range(1, fw - 1):
    put(x0 + x, y0 + 4, MUZZLE[0])
# ---- skyiron brow plate 7x4x1 @ (102,22): rivets at the corners and a raised centre ridge
bp = faces(102, 22, 7, 4, 1)
for n, r in bp.items():
    flat(r, SKY, base=2, speck=0.2)
x0, y0, fw, fh = bp["front"]
for x in range(fw):
    put(x0 + x, y0, SKY[3])
    put(x0 + x, y0 + fh - 1, SKY[1])
for y in range(fh):
    put(x0 + fw // 2, y0 + y, SKY[4] if y < fh - 1 else SKY[3])
for (rx, ry) in ((0, 1), (fw - 1, 1), (1, fh - 2), (fw - 2, fh - 2)):
    put(x0 + rx, y0 + ry, SKY[0])
# ---- horns: cream bases 4x2x2 @ (108,0) and (108,4); tips 2x4x2 @ (120,0) and (120,6) with skyiron caps
for (u, v) in ((108, 0), (108, 4)):
    for n, r in faces(u, v, 4, 2, 2).items():
        flat(r, HORN, base=2 if n == "top" else 1, speck=0.3)
for (u, v) in ((120, 0), (120, 6)):
    f = faces(u, v, 2, 4, 2)
    for n, r in f.items():
        flat(r, HORN, base=2, speck=0.25)
    flat(f["top"], SKY, base=3, speck=0.3)
    for n in ("west", "front", "east", "back"):
        x0, y0, fw, fh = f[n]
        for x in range(fw):
            put(x0 + x, y0, SKY[3]); put(x0 + x, y0 + 1, SKY[2])
# ---- legs 4x9x4 @ (70,30) (86,30) (102,30) (70,43): shaggy above, dark hooves (bottom 2 rows), feathered fetlock
for (u, v) in ((70, 30), (86, 30), (102, 30), (70, 43)):
    f = box(u, v, 4, 9, 4, FUR, bottom_dark=False)
    fur_fill(f["bottom"], HOOF, shade=-1, bottom_dark=False)
    for n in ("west", "front", "east", "back"):
        x0, y0, fw, fh = f[n]
        for x in range(fw):
            put(x0 + x, y0 + fh - 1, HOOF[0]); put(x0 + x, y0 + fh - 2, HOOF[1] if x % 2 else HOOF[2])
            if rng.random() < 0.6:
                put(x0 + x, y0 + fh - 3, FUR[1])
# ---- tail 2x9x2 @ (86,43) and tuft 3x4x3 @ (94,43)
box(86, 43, 2, 9, 2, FUR, bottom_dark=False)
box(94, 43, 3, 4, 3, SKIRT, streak=True)

if __name__ == "__main__":
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("war_yak_texture: wrote", OUT)
    if "--preview" in sys.argv:
        p = sys.argv[sys.argv.index("--preview") + 1]
        img.resize((W * 6, H * 6), Image.NEAREST).save(p)
        print("preview", p)
