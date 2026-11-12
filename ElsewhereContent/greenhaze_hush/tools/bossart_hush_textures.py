"""Boss art (hush group): paints the UV-mapped textures for Auris and Lithara.
python3 tools/bossart_hush_textures.py  ->  src/main/resources/assets/elsewhere/textures/entity/boss/{auris,auris_glow,lithara,lithara_glow}.png
UV offsets mirror dev.elsewhere.bossart.{auris.AurisModel,lithara.LitharaModel}.createLayer(); palettes come from Ideas/art/src/bosses_art.py."""
import os
import random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'elsewhere', 'textures', 'entity', 'boss')
rng = random.Random(15)


def hx(c, a=255):
    c = c.lstrip('#')
    return (int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a)


def mul(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


def faces(u, v, w, h, d):
    """Minecraft box UV faces: name -> (x, y, width, height)."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


SHADE = {'top': 1.18, 'bottom': 0.62, 'front': 1.0, 'back': 0.82, 'right': 0.9, 'left': 0.9}


def paint_box(img, uv, size, pal, weights=None, jit=0.08, edge=0.78, shade=True):
    u, v = uv
    w, h, d = size
    pal = [hx(c) for c in pal]
    for name, (x0, y0, fw, fh) in faces(u, v, w, h, d).items():
        k = SHADE[name] if shade else 1.0
        for y in range(fh):
            for x in range(fw):
                c = rng.choices(pal, weights)[0]
                f = k * (1 + rng.uniform(-jit, jit))
                if x == 0 or y == 0 or x == fw - 1 or y == fh - 1:
                    f *= edge
                if name in ('front', 'back', 'left', 'right'):
                    f *= 1.06 - 0.12 * (y / max(1, fh - 1))  # light from above
                img.putpixel((x0 + x, y0 + y), mul(c, f))


def px(img, xy, col, a=255):
    img.putpixel(xy, hx(col, a))


def fill(img, x0, y0, w, h, col, a=255, jit=0.0):
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            c = hx(col, a)
            if jit:
                c = mul(c, 1 + rng.uniform(-jit, jit))
            img.putpixel((x, y), c)


def fill_faces(img, uv, size, col, a=255, jit=0.06, names=None):
    for name, (x0, y0, fw, fh) in faces(*uv, *size).items():
        if names is None or name in names:
            fill(img, x0, y0, fw, fh, col, a, jit)


# ------------------------------------------------------------------------------------------------ Auris
def auris():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    glow = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    BODY = ['#0e3a44', '#0a2e36', '#1a4a50', '#0c3540']
    LEG = ['#0a2a34', '#08222a', '#0e3036']
    paint_box(img, (0, 0), (14, 12, 7), BODY, [4, 4, 2, 3])
    paint_box(img, (0, 20), (12, 10, 8), ['#0e3a44', '#0a2e36', '#123f48'], [4, 4, 2])
    paint_box(img, (0, 40), (5, 12, 5), LEG)
    paint_box(img, (20, 40), (5, 12, 5), LEG)
    paint_box(img, (40, 40), (5, 13, 5), BODY, [4, 4, 2, 3])
    paint_box(img, (60, 40), (5, 13, 5), BODY, [4, 4, 2, 3])
    paint_box(img, (44, 0), (2, 10, 6), BODY)
    paint_box(img, (60, 0), (2, 10, 6), BODY)
    paint_box(img, (76, 0), (1, 7, 4), ['#40f0ff', '#30e0f0', '#80f8ff'], shade=False, edge=0.85)
    paint_box(img, (88, 0), (1, 7, 4), ['#40f0ff', '#30e0f0', '#80f8ff'], shade=False, edge=0.85)
    paint_box(img, (0, 60), (6, 5, 1), ['#40f0ff', '#60f4ff'], shade=False, edge=0.8)
    paint_box(img, (16, 60), (2, 2, 1), ['#e8e0c8', '#d8d0b8'])
    # body: plated chest seams, rib lines on the sides, a spine ridge on the back
    fx, fy, fw, fh = faces(0, 0, 14, 12, 7)['front']
    for x in range(fw):
        px(img, (fx + x, fy + 8), '#06181e')
    for y in range(fh):
        px(img, (fx + 3, fy + y), '#081f26') if y > 8 else None
        px(img, (fx + 10, fy + y), '#081f26') if y > 8 else None
    bx, by, bw, bh = faces(0, 0, 14, 12, 7)['back']
    for y in range(bh):
        px(img, (bx + 6, by + y), '#1f5058')
        px(img, (bx + 7, by + y), '#1f5058')
    for side in ('left', 'right'):
        sx, sy, sw, sh = faces(0, 0, 14, 12, 7)[side]
        for y in range(1, sh, 3):
            for x in range(1, sw - 1):
                px(img, (sx + x, sy + y), '#082028')
    # head: blind face, dark mouth slit, two shallow sockets, bone-coloured brow studs
    hx0, hy0, hw, hh = faces(0, 20, 12, 10, 8)['front']
    for x in range(3, 9):
        px(img, (hx0 + x, hy0 + 6), '#020a0e')
        px(img, (hx0 + x, hy0 + 7), '#041016')
    for x in (2, 3, 8, 9):
        px(img, (hx0 + x, hy0 + 3), '#062028')
    for x in (5, 6):
        px(img, (hx0 + x, hy0 + 1), '#c8c0a8')
    # knuckle/ankle bands
    for (u, v, w, h, d) in ((40, 40, 5, 13, 5), (60, 40, 5, 13, 5)):
        for name in ('front', 'back', 'left', 'right'):
            x0, y0, fw, fh = faces(u, v, w, h, d)[name]
            for x in range(fw):
                px(img, (x0 + x, y0 + fh - 3), '#06181e')
    for (u, v) in ((0, 40), (20, 40)):
        for name in ('front', 'back', 'left', 'right'):
            x0, y0, fw, fh = faces(u, v, 5, 12, 5)[name]
            for x in range(fw):
                px(img, (x0 + x, y0 + fh - 2), '#1a4a50')
    # ear fins: a darker rim with sculk teal edges
    for u in (44, 60):
        for name in ('right', 'left'):
            x0, y0, fw, fh = faces(u, 0, 2, 10, 6)[name]
            for y in range(fh):
                for x in range(fw):
                    if 1 <= x < fw - 1 and 1 <= y < fh - 1:
                        img.putpixel((x0 + x, y0 + y), mul(hx('#0a2a32'), 1 + rng.uniform(-0.1, 0.1)))
    # core: white-hot centre
    cx, cy, cw, ch = faces(0, 60, 6, 5, 1)['front']
    for x in range(2, 4):
        for y in range(1, 4):
            px(img, (cx + x, cy + y), '#d0ffff')

    # emissive layer: core, ear membranes, sculk veins
    for uv, size in (((0, 60), (6, 5, 1)), ((76, 0), (1, 7, 4)), ((88, 0), (1, 7, 4))):
        for name, (x0, y0, fw, fh) in faces(*uv, *size).items():
            for y in range(fh):
                for x in range(fw):
                    glow.putpixel((x0 + x, y0 + y), img.getpixel((x0 + x, y0 + y))[:3] + (255,))
    veins = [(fx + 2, fy + 2), (fx + 3, fy + 3), (fx + 3, fy + 4), (fx + 11, fy + 2), (fx + 10, fy + 3), (fx + 10, fy + 4),
             (fx + 6, fy + 10), (fx + 7, fy + 10), (bx + 6, by + 2), (bx + 7, by + 5), (bx + 6, by + 8)]
    for u in (40, 60):
        x0, y0, fw, fh = faces(u, 40, 5, 13, 5)['front']
        veins += [(x0 + 2, y0 + 2), (x0 + 2, y0 + 3), (x0 + 1, y0 + 4), (x0 + 3, y0 + 7)]
    for u in (0, 20):
        x0, y0, fw, fh = faces(u, 40, 5, 12, 5)['front']
        veins += [(x0 + 2, y0 + 3), (x0 + 3, y0 + 4)]
    for (x, y) in veins:
        img.putpixel((x, y), hx('#20b8c8'))
        glow.putpixel((x, y), hx('#40f0ff', 200))
    for u in (44, 60):  # ear rims glow faintly
        for name in ('front', 'back', 'top'):
            x0, y0, fw, fh = faces(u, 0, 2, 10, 6)[name]
            for y in range(fh):
                for x in range(fw):
                    glow.putpixel((x0 + x, y0 + y), hx('#30e8ff', 110))
    return img, glow


# ------------------------------------------------------------------------------------------------ Lithara
SPIRES = [(-6, -35, 12), (-3, -15, 15), (3, 15, 15), (6, 35, 12), (0, 0, 18)]


def lithara():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    glow = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    ROBE = ['#3a1a5a', '#2e1450', '#4a2a6a', '#341656']
    AM = ['#9a5ad8', '#8a4ac8', '#b07aec', '#6a3aa8']
    STONE = ['#a8a8a0', '#9a9a92', '#b4b4ac', '#8e8e86']
    GOLD = '#c8a050'
    paint_box(img, (0, 0), (11, 12, 6), ROBE)
    paint_box(img, (0, 20), (8, 12, 4), ROBE)
    paint_box(img, (40, 20), (9, 6, 1), AM, shade=True, edge=0.85)
    paint_box(img, (0, 38), (8, 8, 8), STONE)
    paint_box(img, (32, 38), (3, 2, 1), ['#6a6a60', '#5e5e56'])
    paint_box(img, (48, 0), (4, 12, 4), ROBE)
    paint_box(img, (64, 0), (4, 12, 4), ROBE)
    paint_box(img, (60, 20), (5, 3, 5), AM, edge=0.85)
    paint_box(img, (60, 30), (5, 3, 5), AM, edge=0.85)
    paint_box(img, (80, 0), (3, 3, 3), ['#f0d0ff', '#ffffff', '#e0b8ff'], shade=False, edge=0.9)
    paint_box(img, (80, 8), (3, 3, 3), ['#f0d0ff', '#ffffff', '#e0b8ff'], shade=False, edge=0.9)
    for i, (_, _, h) in enumerate(SPIRES):
        paint_box(img, (i * 12, 60), (3, h, 3), AM, [3, 3, 2, 1], edge=0.8)
    # robe: gold hem, a gold stripe down the front, folds
    for name in ('front', 'back', 'left', 'right'):
        x0, y0, fw, fh = faces(0, 0, 11, 12, 6)[name]
        for x in range(fw):
            px(img, (x0 + x, y0 + fh - 1), GOLD)
            if x % 3 == 1:
                for y in range(1, fh - 2):
                    img.putpixel((x0 + x, y0 + y), mul(img.getpixel((x0 + x, y0 + y)), 0.78))
    fx, fy, fw, fh = faces(0, 0, 11, 12, 6)['front']
    for y in range(fh):
        px(img, (fx + 5, fy + y), GOLD if y % 4 else '#e0c070')
    # body: gold collar and belt
    for name in ('front', 'back', 'left', 'right'):
        x0, y0, fw, fh = faces(0, 20, 8, 12, 4)[name]
        for x in range(fw):
            px(img, (x0 + x, y0), GOLD)
            px(img, (x0 + x, y0 + fh - 2), GOLD)
    # sleeves: gold cuffs; hands are the stone-grey bottom face
    for u in (48, 64):
        for name in ('front', 'back', 'left', 'right'):
            x0, y0, fw, fh = faces(u, 0, 4, 12, 4)[name]
            for x in range(fw):
                px(img, (x0 + x, y0 + fh - 2), GOLD)
                img.putpixel((x0 + x, y0 + fh - 1), mul(hx(rng.choice(STONE)), 0.9))
        x0, y0, fw, fh = faces(u, 0, 4, 12, 4)['bottom']
        fill(img, x0, y0, fw, fh, '#9a9a92', jit=0.08)
    # head: purple cowl over top/back/sides, stone face with glowing eyes
    for name in ('top', 'back'):
        x0, y0, fw, fh = faces(0, 38, 8, 8, 8)[name]
        for y in range(fh):
            for x in range(fw):
                img.putpixel((x0 + x, y0 + y), mul(hx(rng.choice(ROBE)), SHADE[name] * (1 + rng.uniform(-0.08, 0.08))))
    for name in ('left', 'right'):
        x0, y0, fw, fh = faces(0, 38, 8, 8, 8)[name]
        for y in range(fh):
            for x in range(fw):
                if y < 2 or x >= fw - 4 if name == 'left' else y < 2 or x < 4:
                    img.putpixel((x0 + x, y0 + y), mul(hx(rng.choice(ROBE)), 0.88 * (1 + rng.uniform(-0.08, 0.08))))
    hx0, hy0, hw, hh = faces(0, 38, 8, 8, 8)['front']
    for x in range(hw):
        img.putpixel((hx0 + x, hy0), mul(hx(rng.choice(ROBE)), 1.0))
        px(img, (hx0 + x, hy0 + 1), GOLD if x in (0, 7) else '#4a2a6a')
    eyes = [(1, 4), (2, 4), (5, 4), (6, 4)]
    for (x, y) in eyes:
        px(img, (hx0 + x, hy0 + y), '#ffffff')
        px(img, (hx0 + x, hy0 + y + 1), '#5a3a7a')
    for x in range(2, 6):
        px(img, (hx0 + x, hy0 + 6), '#6e6e66')
    # spires: a pale streak and bright tip
    for i, (_, _, h) in enumerate(SPIRES):
        for name in ('front', 'back', 'left', 'right'):
            x0, y0, fw, fh = faces(i * 12, 60, 3, h, 3)[name]
            for y in range(fh):
                px(img, (x0 + 1, y0 + y), '#d0a0ff' if y % 5 else '#f0d8ff')
            for y in range(min(3, fh)):
                for x in range(fw):
                    px(img, (x0 + x, y0 + y), '#e8c8ff')
        x0, y0, fw, fh = faces(i * 12, 60, 3, h, 3)['top']
        fill(img, x0, y0, fw, fh, '#f4e0ff')
    # chest plate & pauldrons: facet highlights
    x0, y0, fw, fh = faces(40, 20, 9, 6, 1)['front']
    for (x, y) in ((1, 1), (2, 2), (4, 1), (6, 3), (7, 2), (4, 4)):
        px(img, (x0 + x, y0 + y), '#d8b0ff')

    # emissive layer
    for (x, y) in eyes:
        glow.putpixel((hx0 + x, hy0 + y), hx('#ffffff'))
    for uv in ((80, 0), (80, 8)):
        for name, (x0, y0, fw, fh) in faces(*uv, 3, 3, 3).items():
            for y in range(fh):
                for x in range(fw):
                    glow.putpixel((x0 + x, y0 + y), img.getpixel((x0 + x, y0 + y))[:3] + (255,))
    for i, (_, _, h) in enumerate(SPIRES):
        for name, (x0, y0, fw, fh) in faces(i * 12, 60, 3, h, 3).items():
            for y in range(fh):
                for x in range(fw):
                    c = img.getpixel((x0 + x, y0 + y))
                    a = 230 if (name == 'top' or y < 3 or x == 1) else 70
                    glow.putpixel((x0 + x, y0 + y), c[:3] + (a,))
    x0, y0, fw, fh = faces(40, 20, 9, 6, 1)['front']
    for (x, y) in ((1, 1), (2, 2), (4, 1), (6, 3), (7, 2), (4, 4)):
        glow.putpixel((x0 + x, y0 + y), hx('#e0a0ff', 160))
    return img, glow


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in (('auris', auris), ('lithara', lithara)):
        base, glow = fn()
        base.save(os.path.join(OUT, name + '.png'))
        glow.save(os.path.join(OUT, name + '_glow.png'))
        print('wrote', name)
