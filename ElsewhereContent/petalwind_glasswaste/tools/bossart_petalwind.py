"""Boss art for Kazehana and Tempestra: writes the cube geometry (Java LayerDefinition) and the matching UV-mapped
textures (base + emissive) from one description, so UVs always line up.

Run: python tools/bossart_petalwind.py   (needs Pillow)
Geometry follows the hero boxes of kazehana() / tempestra() in Ideas/art/src/bosses_art.py.
"""
import math
import os
import random

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJ = os.path.dirname(HERE)
TEX = os.path.join(PROJ, 'src/main/resources/assets/elsewhere/textures/entity/boss')
JAVA = os.path.join(PROJ, 'src/client/java/dev/elsewhere/bossart')


def hx(c):
    c = c.lstrip('#')
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


class Cube:
    def __init__(self, x, y, z, w, h, d, pal, paint=None, alpha=255, emis=None, mirror=False, key=None):
        self.o = (x, y, z)
        self.s = (w, h, d)
        self.pal = [hx(p) for p in pal]
        self.paint = paint
        self.alpha = alpha
        self.emis = emis  # None, 'all', or a callable(face, u, v, w, h) -> bool
        self.key = key  # cubes with the same key share one UV region
        self.uv = None


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name, self.pivot, self.rot = name, pivot, rot
        self.cubes = list(cubes)
        self.children = list(children)


def faces(c):
    """Box-UV face rectangles: name -> (u, v, w, h)."""
    u, v = c.uv
    w, h, d = c.s
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h),
        'west': (u + d + w, v + d, d, h), 'south': (u + d + w + d, v + d, w, h),
    }


FACE_SHADE = {'top': 1.12, 'bottom': 0.62, 'north': 1.0, 'south': 0.8, 'east': 0.88, 'west': 0.9}


def all_cubes(p):
    for c in p.cubes:
        yield c
    for ch in p.children:
        yield from all_cubes(ch)


def pack(root, size):
    seen = {}
    x = y = row = 0
    W, H = size
    cubes = sorted(all_cubes(root), key=lambda c: -(c.s[1] + c.s[2]))
    for c in cubes:
        if c.key and c.key in seen:
            c.uv = seen[c.key].uv
            c.shared = True
            continue
        c.shared = False
        fw = 2 * (c.s[0] + c.s[2])
        fh = c.s[1] + c.s[2]
        if x + fw > W:
            x, y, row = 0, y + row, 0
        if y + fh > H:
            raise SystemExit('texture too small for ' + str(c.s))
        c.uv = (x, y)
        x += fw
        row = max(row, fh)
        if c.key:
            seen[c.key] = c


def paint_cube(img, emi, c, rnd):
    if c.shared:
        return
    for fname, (u, v, w, h) in faces(c).items():
        k = FACE_SHADE[fname]
        for j in range(h):
            for i in range(w):
                col = rnd.choice(c.pal)
                jit = 1.0 + rnd.uniform(-0.06, 0.06)
                edge = (i == 0 or j == 0 or i == w - 1 or j == h - 1) and w > 2 and h > 2
                kk = k * jit * (0.86 if edge else 1.0)
                # soft vertical gradient on sides: darker at the bottom
                if fname not in ('top', 'bottom') and h > 3:
                    kk *= 1.05 - 0.15 * j / (h - 1)
                px = shade(col, kk)
                a = c.alpha
                if c.paint:
                    r = c.paint(fname, i, j, w, h, px, rnd)
                    if r is not None:
                        px = r[:3]
                        if len(r) > 3:
                            a = r[3]
                img.putpixel((u + i, v + j), px + (a,))
                glow = c.emis == 'all' or (callable(c.emis) and c.emis(fname, i, j, w, h))
                if glow:
                    emi.putpixel((u + i, v + j), px + (255,))


def jf(v):
    s = ('%.4f' % v).rstrip('0').rstrip('.')
    if s in ('-0', ''):
        s = '0'
    return s + 'F'


def java_part(p, parent_var, out, depth=1):
    var = 'p_' + p.name
    cubes = ''.join('.texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)' % (c.uv[0], c.uv[1], jf(c.o[0]), jf(c.o[1]), jf(c.o[2]),
                                                                       jf(c.s[0]), jf(c.s[1]), jf(c.s[2])) for c in p.cubes)
    rx, ry, rz = p.rot
    pose = 'PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)' % (jf(p.pivot[0]), jf(p.pivot[1]), jf(p.pivot[2]), jf(rx), jf(ry), jf(rz))
    out.append('\t\tPartDefinition %s = %s.addOrReplaceChild("%s", CubeListBuilder.create()%s, %s);' % (var, parent_var, p.name, cubes, pose))
    for ch in p.children:
        java_part(ch, var, out)


def write(boss, cls, root, size, seed):
    rnd = random.Random(seed)
    pack(root, size)
    img = Image.new('RGBA', size, (0, 0, 0, 0))
    emi = Image.new('RGBA', size, (0, 0, 0, 0))
    for c in all_cubes(root):
        paint_cube(img, emi, c, rnd)
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, boss + '.png'))
    emi.save(os.path.join(TEX, boss + '_emissive.png'))
    lines = []
    for ch in root.children:
        java_part(ch, 'root', lines)
    pkg = 'dev.elsewhere.bossart.' + boss
    src = '''package %s;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Generated by tools/bossart_petalwind.py from the concept-art boxes; edit the script, not this file. */
@SuppressWarnings("unused")
public final class %s {
	private %s() {}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
%s
		return LayerDefinition.create(mesh, %d, %d);
	}
}
''' % (pkg, cls, cls, '\n'.join(lines), size[0], size[1])
    d = os.path.join(JAVA, boss)
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, cls + '.java'), 'w') as f:
        f.write(src)
    print('wrote', boss, size)


R = math.radians


# ------------------------------------------------------------------ Kazehana, the Gale Ronin ---
def head_face(f, i, j, w, h, px, rnd):
    if f != 'north':
        return None
    # glowing eyes (row 3), dark cloth mask over the lower face (rows 5-7)
    if j == 3 and i in (1, 2, 5, 6):
        return hx('#d0ffff')
    if j == 2 and i in (1, 2, 5, 6):
        return shade(hx('#4a5050'), 1.0)
    if j >= 5:
        return shade(hx('#2a1a2a'), 0.9 + 0.2 * rnd.random())
    return None


def head_emis(f, i, j, w, h):
    return f == 'north' and j == 3 and i in (1, 2, 5, 6)


def kimono(f, i, j, w, h, px, rnd):
    # cherry blossom dots and a crossed collar on the front
    if f == 'north' and abs(i - w / 2 + 0.5) + 0.5 >= (h - j) * 0.5 - 2.5 and j < 6 and abs(i - w / 2 + 0.5) < 3:
        return hx('#f8e8f0')
    if rnd.random() < 0.05:
        return hx('#fff0f6')
    if j == h - 2 and f in ('north', 'south', 'east', 'west'):
        return shade(hx('#2a1a2a'), 1.0)  # obi edge
    return None


def obi(f, i, j, w, h, px, rnd):
    if f == 'north' and i in (w // 2 - 1, w // 2) and 0 < j < h - 1:
        return hx('#c8a050')
    return None


def straw(f, i, j, w, h, px, rnd):
    if f in ('top', 'bottom'):
        cx, cz = i - w / 2 + 0.5, j - h / 2 + 0.5
        r = math.hypot(cx, cz)
        k = 0.85 if int(r) % 3 == 0 else 1.0
        return shade(px, k)
    if (i + j) % 3 == 0:
        return shade(px, 0.9)
    return None


def hakama(f, i, j, w, h, px, rnd):
    if f in ('north', 'south') and i % 2 == 1:
        return shade(px, 0.82)
    if j >= h - 2 and f not in ('top', 'bottom'):
        return hx('#e8e0d0') if j == h - 2 else hx('#6a4a30')  # tabi sock and sandal
    return None


def blade(f, i, j, w, h, px, rnd):
    edge = (f in ('east', 'west') and (i == 0)) or f == 'top'
    return hx('#ffffff') if edge else shade(hx('#e8f0f8'), 0.95 + 0.05 * rnd.random())


def haori(f, i, j, w, h, px, rnd):
    # petal pattern and a darker hem
    if j >= h - 2 and f in ('north', 'south', 'east', 'west'):
        return shade(hx('#b04a78'), 0.95)
    if rnd.random() < 0.07:
        return hx('#ffd8e8')
    return None


def kazehana():
    CH = ['#f0a0c0', '#e890b0', '#f8b8d0', '#d87aa0']
    DARK = ['#3a2a3a', '#33243a', '#402e40']
    LEG = ['#2a2030', '#251c2b', '#30253a']
    STRAW = ['#d8b878', '#c8a868', '#e0c488']
    head = Part('head', (0, -12, 0), cubes=[Cube(-4, -8, -4, 8, 8, 8, ['#8a9090', '#828888', '#929a9a'], paint=head_face, emis=head_emis)],
                children=[Part('hat', (0, -8, 0), cubes=[
                    Cube(-10, -2, -10, 20, 1, 20, STRAW, paint=straw),
                    Cube(-7, -3, -7, 14, 1, 14, STRAW, paint=straw),
                    Cube(-4.5, -5, -4.5, 9, 3, 9, ['#c8a868', '#b89858'], paint=straw),
                    Cube(-4.5, -1, -4.5, 9, 1, 9, ['#7a2a3a', '#6a2030']),  # hat band
                ])])
    katana = Part('katana', (-1, 10, 0), (0, 0, 0), cubes=[
        Cube(-1, -1, -3, 2, 2, 5, ['#2a1a2a', '#3a2a3a', '#c8c0b0']),  # wrapped grip
        Cube(-2, -2, -4, 4, 4, 1, ['#c8a050', '#b89040', '#d8b060']),  # tsuba
        Cube(-0.5, -1, -23, 1, 2, 19, ['#e8f0f8'], paint=blade, emis='all'),
    ])
    rarm = Part('right_arm', (-5, -10, 0), (R(-35), 0, 0), cubes=[
        Cube(-3, -2, -2, 4, 12, 4, DARK),
        Cube(-3.5, -2.5, -2.5, 5, 6, 5, CH, paint=haori),
    ], children=[katana])
    larm = Part('left_arm', (5, -10, 0), (R(-30), R(25), 0), cubes=[
        Cube(-1, -2, -2, 4, 12, 4, DARK),
        Cube(-1.5, -2.5, -2.5, 5, 6, 5, CH, paint=haori),
    ])
    saya = Part('saya', (4.6, -3, -2), (R(-25), R(-8), 0), cubes=[Cube(-1, -1, 0, 2, 2, 16, ['#2a1a2a', '#3a2030'], paint=None)])
    body = Part('body', (0, 0, 0), cubes=[
        Cube(-4, -12, -2, 8, 12, 4, CH, paint=kimono),
        Cube(-4.5, -4, -2.5, 9, 2, 5, ['#2a1a2a', '#33202f'], paint=obi),
    ], children=[head, rarm, larm, saya,
                 Part('haori', (0, -11.5, 2.2), (R(14), 0, 0), cubes=[Cube(-5.5, 0, 0, 11, 17, 1, CH, paint=haori)])])
    waist = Part('waist', (0, 12, 0), children=[body])
    rleg = Part('right_leg', (-2.1, 12, 0), cubes=[Cube(-2.5, 0, -2.5, 5, 12, 5, LEG, paint=hakama)])
    lleg = Part('left_leg', (2.1, 12, 0), cubes=[Cube(-2.5, 0, -2.5, 5, 12, 5, LEG, paint=hakama)])
    root = Part('root', children=[waist, rleg, lleg])
    write('kazehana', 'KazehanaGeometry', root, (128, 128), 43)


# ------------------------------------------------------------------ Tempestra, Eye of the Gale ---
def storm_face(f, i, j, w, h, px, rnd):
    if f != 'north':
        # swirling cloud streaks on the sides
        if (i + 2 * j) % 7 == 0:
            return shade(hx('#d8f0ff'), 1.0)
        return None
    if j in (6, 7) and (3 <= i <= 6 or 9 <= i <= 12):
        return hx('#ffffff')
    if j == 5 and (3 <= i <= 6 or 9 <= i <= 12):
        return shade(hx('#6a90b8'), 1.0)
    if j == 11 and 4 <= i <= 11:
        return hx('#2a3a5a')
    if j == 12 and 5 <= i <= 10:
        return hx('#2a3a5a')
    return None


def storm_emis(f, i, j, w, h):
    return f == 'north' and j in (6, 7) and (3 <= i <= 6 or 9 <= i <= 12)


def rod(f, i, j, w, h, px, rnd):
    return shade(hx('#c8e8ff'), 0.92 + 0.16 * rnd.random()) if 0 < j < h - 1 else hx('#ffffff')


def wisp(f, i, j, w, h, px, rnd):
    a = 150 + int(60 * rnd.random()) if (i + j) % 5 else 110
    return px + (a,)


def debris(f, i, j, w, h, px, rnd):
    if f == 'top':
        return None
    return shade(hx('#c8a0b8'), 0.9 + 0.1 * rnd.random())


def tempestra():
    SKY = ['#a8d0f0', '#98c0e8', '#b8e0ff']
    rings = []
    for k in range(3):
        rods = []
        for i in range(5):
            a = math.radians(i * 72 + k * 30)
            r = (2.4 + k * 1.2) * 4.44
            rods.append(Part('rod%d_%d' % (k + 1, i + 1), (round(math.cos(a) * r, 3), 0, round(math.sin(a) * r, 3)),
                             (R(25), -a + R(90), R(18)), cubes=[Cube(-1.5, -6, -1.5, 3, 12, 3, ['#c8e8ff'], paint=rod, emis='all', key='rod')]))
        rings.append(Part('ring%d' % (k + 1), (0, -12 + k * 8, 0), children=rods))
    bits = []
    for i in range(8):
        a = math.radians(i * 45)
        bits.append(Part('debris%d' % (i + 1), (round(math.cos(a) * 29, 3), round(-math.sin(a * 2) * 4.4, 3), round(math.sin(a) * 29, 3)),
                         (R(20), R(i * 30), 0), cubes=[Cube(-2.5, -1.5, -2.5, 5, 3, 5, ['#ffd0e8', '#f8c0dc', '#ffe0f0'], paint=debris, key='debris')]))
    tail4 = Part('tail4', (0, 6, 0), (0, R(30), 0), cubes=[Cube(-2.5, -2.5, -2.5, 5, 5, 5, ['#ffffff', '#eef6ff'], paint=wisp, alpha=170)])
    tail3 = Part('tail3', (0, 6, 0), (0, R(30), 0), cubes=[Cube(-3.5, -2.5, -3.5, 7, 5, 7, ['#ffffff', '#eef6ff'], paint=wisp, alpha=180)],
                 children=[tail4])
    tail2 = Part('tail2', (0, 6, 0), (0, R(30), 0), cubes=[Cube(-4.5, -2.5, -4.5, 9, 5, 9, ['#ffffff', '#eef6ff'], paint=wisp, alpha=190)],
                 children=[tail3])
    tail1 = Part('tail1', (0, 13, 0), cubes=[Cube(-6, -2.5, -6, 12, 5, 12, ['#ffffff', '#eef6ff'], paint=wisp, alpha=200)], children=[tail2])
    head = Part('head', (0, -16, 0), cubes=[Cube(-8, -16, -8, 16, 16, 16, SKY, paint=storm_face, emis=storm_emis)])
    funnel = Part('funnel', (0, -16, 0), cubes=[Cube(-4, 0, -4, 8, 26, 8, ['#e8f4ff', '#ffffff', '#d0e8ff'], paint=wisp, alpha=140)])
    body = Part('body', (0, -8, 0), children=[head, funnel] + rings + [Part('debris', (0, -14, 0), children=bits), tail1])
    root = Part('root', children=[body])
    write('tempestra', 'TempestraGeometry', root, (128, 64), 44)


if __name__ == '__main__':
    kazehana()
    tempestra()
