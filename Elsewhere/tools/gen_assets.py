"""Generates every Elsewhere texture (original pixel art) plus the entity model geometry.

Entity models are described once here as parts + cubes. The script packs their box UVs,
paints each face with a material, and writes a Java class per model
(src/client/java/dev/elsewhere/client/model/gen/<Name>Geometry.java) so geometry and
texture can never drift apart.

Run:  python3 tools/gen_assets.py      (needs Pillow)
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src/main/resources/assets/elsewhere/textures")
JAVA = os.path.join(ROOT, "src/client/java/dev/elsewhere/client/model/gen")


# ---------------------------------------------------------------- helpers
def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(c1, c2, t):
    return tuple(int(round(c1[i] + (c2[i] - c1[i]) * t)) for i in range(4))


def shade(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


def ramp(colors, t):
    t = max(0.0, min(0.999, t))
    i = t * (len(colors) - 1)
    k = int(i)
    return mix(colors[k], colors[k + 1], i - k)


def vnoise(seed):
    """Smooth value noise in [0,1]."""
    rnd = random.Random(seed)
    grid = {}

    def g(ix, iy):
        if (ix, iy) not in grid:
            grid[(ix, iy)] = rnd.random()
        return grid[(ix, iy)]

    def f(x, y, s=4.0):
        x /= s
        y /= s
        x0, y0 = math.floor(x), math.floor(y)
        tx, ty = x - x0, y - y0
        tx = tx * tx * (3 - 2 * tx)
        ty = ty * ty * (3 - 2 * ty)
        a = g(x0, y0) + (g(x0 + 1, y0) - g(x0, y0)) * tx
        b = g(x0, y0 + 1) + (g(x0 + 1, y0 + 1) - g(x0, y0 + 1)) * tx
        return a + (b - a) * ty

    return f


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


CLEAR = (0, 0, 0, 0)

# ---------------------------------------------------------------- palettes
BARK_PALE = [hexc("#5d5955"), hexc("#8a857e"), hexc("#b9b3a8"), hexc("#d9d4c8")]
BARK_DARK = [hexc("#1f1c1b"), hexc("#34302d"), hexc("#4a4541"), hexc("#625c55")]
MOSS = [hexc("#4d5449"), hexc("#6c7466"), hexc("#8e9686"), hexc("#b3b9a8")]
RESIN = [hexc("#7a2e06"), hexc("#c45a10"), hexc("#f08a1c"), hexc("#ffc14a"), hexc("#fff0a8")]
TUFF = [hexc("#3d3f3b"), hexc("#565954"), hexc("#6d706a"), hexc("#868981")]
SCLERA = [hexc("#9aa5b6"), hexc("#c3cad8"), hexc("#dfe4ee"), hexc("#f4f6fb")]
LID = [hexc("#16151a"), hexc("#25232b"), hexc("#37343e"), hexc("#4a4652")]
PETAL = [hexc("#7e7a74"), hexc("#a8a39a"), hexc("#d2cdc2"), hexc("#ece8df")]
AMETHYST = [hexc("#3f2366"), hexc("#6a3fa6"), hexc("#9a6bd6"), hexc("#d2b4ff")]
BRASS = [hexc("#4a3110"), hexc("#7b5520"), hexc("#b98a35"), hexc("#e8c46a"), hexc("#fff0b0")]


# ---------------------------------------------------------------- entity model spec
class Cube:
    def __init__(self, mat, x, y, z, w, h, d, inflate=0.0, detail=None):
        self.mat, self.x, self.y, self.z, self.w, self.h, self.d = mat, x, y, z, w, h, d
        self.inflate = inflate
        self.detail = detail
        self.u = self.v = 0

    def footprint(self):
        if self.d == 0:
            return 2 * self.w, self.h
        if self.h == 0:
            return 2 * (self.w + self.d), self.d
        return 2 * (self.w + self.d), self.d + self.h

    def faces(self):
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {
            "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
            "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h),
        }


class Part:
    def __init__(self, name, parent, pivot, rot=(0, 0, 0), cubes=()):
        self.name, self.parent, self.pivot, self.rot, self.cubes = name, parent, pivot, rot, list(cubes)


def pack(model_name, parts, tw, th):
    cubes = [c for p in parts for c in p.cubes]
    order = sorted(cubes, key=lambda c: (-c.footprint()[1], -c.footprint()[0]))
    x = y = shelf = 0
    for c in order:
        fw, fh = c.footprint()
        if x + fw > tw:
            x, y, shelf = 0, y + shelf, 0
        c.u, c.v = x, y
        x += fw
        shelf = max(shelf, fh)
    if y + shelf > th:
        raise SystemExit(f"{model_name}: UVs do not fit {tw}x{th} (need {y + shelf})")


def java_float(f):
    return f"{float(f)}F"


def write_geometry(cls, parts, tw, th):
    lines = [
        "package dev.elsewhere.client.model.gen;",
        "",
        "import net.minecraft.client.model.geom.PartPose;",
        "import net.minecraft.client.model.geom.builders.CubeDeformation;",
        "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
        "import net.minecraft.client.model.geom.builders.MeshDefinition;",
        "import net.minecraft.client.model.geom.builders.PartDefinition;",
        "",
        "/** Generated by tools/gen_assets.py. Do not edit; change the spec there and re-run. */",
        f"public final class {cls} {{",
        f"\tpublic static final int TEXTURE_WIDTH = {tw};",
        f"\tpublic static final int TEXTURE_HEIGHT = {th};",
        "",
        f"\tprivate {cls}() {{",
        "\t}",
        "",
        "\tpublic static MeshDefinition mesh() {",
        "\t\tMeshDefinition mesh = new MeshDefinition();",
        "\t\tPartDefinition part_ = mesh.getRoot();",
    ]
    for p in parts:
        cb = "CubeListBuilder.create()"
        for c in p.cubes:
            dims = ", ".join(java_float(v) for v in (c.x, c.y, c.z, c.w, c.h, c.d))
            if c.inflate:
                cb += f".texOffs({c.u}, {c.v}).addBox({dims}, new CubeDeformation({java_float(c.inflate)}))"
            else:
                cb += f".texOffs({c.u}, {c.v}).addBox({dims})"
        px, py, pz = (java_float(v) for v in p.pivot)
        rx, ry, rz = (java_float(math.radians(v)) for v in p.rot)
        parent = "part_" if p.parent is None else f"part_{p.parent}"
        lines.append(
            f"\t\tPartDefinition part_{p.name} = {parent}.addOrReplaceChild(\"{p.name}\", {cb}, "
            f"PartPose.offsetAndRotation({px}, {py}, {pz}, {rx}, {ry}, {rz}));"
        )
    lines += ["\t\treturn mesh;", "\t}", "}", ""]
    os.makedirs(JAVA, exist_ok=True)
    with open(os.path.join(JAVA, cls + ".java"), "w") as fh:
        fh.write("\n".join(lines))


# ---------------------------------------------------------------- materials (paint one face)
def mat_bark(pal, seed, streak=0.25):
    n = vnoise(seed)

    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        for yy in range(fh):
            for xx in range(fw):
                gx, gy = fx + xx, fy + yy
                t = 0.35 + 0.45 * n(gx * 2.5, gy * 0.6, 3.0) + rnd.uniform(-0.12, 0.12)
                if face not in ("up", "down") and (gx * 7 + seed) % 5 == 0 and rnd.random() < 0.85:
                    t -= streak  # vertical grain
                c = ramp(pal, t)
                if yy == 0 or xx == 0 or yy == fh - 1 or xx == fw - 1:
                    c = shade(c, 0.86)
                img.putpixel((gx, gy), c)

    return paint


def mat_flat_ramp(pal, seed, scale=3.0, jitter=0.1):
    n = vnoise(seed)

    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        for yy in range(fh):
            for xx in range(fw):
                t = 0.25 + 0.6 * n(fx + xx, fy + yy, scale) + rnd.uniform(-jitter, jitter)
                img.putpixel((fx + xx, fy + yy), ramp(pal, t))

    return paint


def mat_glow(pal, seed):
    base = mat_flat_ramp(pal, seed, 2.0, 0.15)

    def paint(img, glow, rect, face, rnd):
        base(img, glow, rect, face, rnd)
        fx, fy, fw, fh = rect
        for yy in range(fh):
            for xx in range(fw):
                glow.putpixel((fx + xx, fy + yy), img.getpixel((fx + xx, fy + yy)))

    return paint


def mat_moss_drape(seed):
    """Hanging moss sheet: ragged bottom edge, transparent gaps."""
    n = vnoise(seed)

    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        cut = [int(fh * (0.55 + 0.45 * n(x * 3, 0, 2.0))) for x in range(fw)]
        for xx in range(fw):
            for yy in range(fh):
                if yy <= cut[xx] and not (yy > fh * 0.4 and rnd.random() < 0.12):
                    t = 0.3 + 0.5 * n(fx + xx, fy + yy, 2.0) + rnd.uniform(-0.1, 0.1) - yy / fh * 0.25
                    img.putpixel((fx + xx, fy + yy), ramp(MOSS, t))

    return paint


def mat_branches(pal, seed, tips=None):
    """Transparent plane with a branching twig drawn on it (antlers, claws)."""

    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        r = random.Random(seed + (1 if face == "back" else 0))

        def line(x0, y0, x1, y1, c):
            steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
            for i in range(steps + 1):
                t = i / max(1, steps)
                x, y = int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))
                if 0 <= x < fw and 0 <= y < fh:
                    img.putpixel((fx + x, fy + y), c)

        def branch(x, y, ang, length, depth):
            if depth == 0 or length < 1.5:
                if tips and 0 <= int(x) < fw and 0 <= int(y) < fh:
                    img.putpixel((fx + int(x), fy + int(y)), tips)
                    glow.putpixel((fx + int(x), fy + int(y)), tips)
                return
            x1, y1 = x + math.cos(ang) * length, y + math.sin(ang) * length
            line(x, y, x1, y1, ramp(pal, 0.35 + 0.4 * r.random()))
            branch(x1, y1, ang - r.uniform(0.3, 0.7), length * 0.62, depth - 1)
            branch(x1, y1, ang + r.uniform(0.3, 0.7), length * 0.62, depth - 1)

        # grows from the bottom-centre upward (antlers) by default
        branch(fw / 2, fh - 1, -math.pi / 2 + r.uniform(-0.2, 0.2), fh * 0.45, 3)

    return paint


def mat_claws(pal, seed):
    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        for k in range(3):
            x0 = 1 + k * (fw - 2) // 2
            for yy in range(fh):
                x = int(round(x0 + (k - 1) * yy * 0.25))
                if 0 <= x < fw:
                    img.putpixel((fx + x, fy + yy), ramp(pal, 0.6 - yy / fh * 0.4))

    return paint


def mat_petal(seed):
    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        for yy in range(fh):
            half = (fw / 2) * math.sin(math.pi * (yy + 0.5) / fh) ** 0.6
            for xx in range(fw):
                if abs(xx + 0.5 - fw / 2) <= half + 0.2:
                    t = 0.45 + 0.4 * (1 - yy / fh) + rnd.uniform(-0.08, 0.08)
                    img.putpixel((fx + xx, fy + yy), ramp(PETAL, t))

    return paint


def mat_halo(seed):
    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        cx, cy = fw / 2, fh / 2
        for yy in range(fh):
            for xx in range(fw):
                d = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy) / (fw / 2)
                # crescent: outer ring minus an offset disc
                d2 = math.hypot(xx + 0.5 - cx - fw * 0.18, yy + 0.5 - cy + fh * 0.1) / (fw / 2)
                if 0.62 < d < 0.98 and d2 > 0.82:
                    a = int(150 + 90 * (1 - abs(d - 0.8) / 0.18))
                    c = mix(hexc("#d9e6ff"), hexc("#fff6d8"), rnd.random() * 0.5)
                    img.putpixel((fx + xx, fy + yy), (c[0], c[1], c[2], max(0, min(255, a))))
                    glow.putpixel((fx + xx, fy + yy), (c[0], c[1], c[2], 160))

    return paint


def mat_swirl():
    def paint(img, glow, rect, face, rnd):
        fx, fy, fw, fh = rect
        cx, cy = fw / 2, fh / 2
        for yy in range(fh):
            for xx in range(fw):
                dx, dy = (xx + 0.5 - cx) / (fw / 2), (yy + 0.5 - cy) / (fh / 2)
                r = math.hypot(dx, dy)
                if r >= 1:
                    continue
                ang = math.atan2(dy, dx)
                s = 0.5 + 0.5 * math.sin(ang * 3 + r * 9.0)
                a = (1 - r) ** 0.6 * (0.35 + 0.65 * s)
                base = mix(hexc("#ffffff"), hexc("#9a8cff"), r)
                img.putpixel((fx + xx, fy + yy), (base[0], base[1], base[2], int(255 * min(1, a))))

    return paint


MATS = {}


def make_mats():
    MATS.update({
        "bark": mat_bark(BARK_PALE, 11),
        "bark_dark": mat_bark(BARK_DARK, 23, 0.18),
        "bark_plate": mat_bark([hexc("#3a3532"), hexc("#55504a"), hexc("#7a736a"), hexc("#9a9288")], 31, 0.3),
        "moss_drape": mat_moss_drape(5),
        "moss": mat_flat_ramp(MOSS, 7),
        "twigs": mat_branches(BARK_PALE, 3),
        "antlers": mat_branches(BARK_DARK[1:] + [hexc("#7d756b")], 9, tips=hexc("#ffb43c")),
        "claws": mat_claws(BARK_DARK[1:] + [hexc("#7d756b")], 4),
        "pale_claws": mat_claws(BARK_PALE, 6),
        "roots": mat_flat_ramp([hexc("#2a211b"), hexc("#3e3128"), hexc("#55443a"), hexc("#6a5649")], 8),
        "resin": mat_glow(RESIN, 12),
        "sclera": mat_flat_ramp(SCLERA, 13, 2.0, 0.08),
        "iris": mat_glow(RESIN[1:], 14),
        "lid": mat_bark(LID, 15, 0.1),
        "stalk": mat_flat_ramp([hexc("#55534f"), hexc("#7d7a73"), hexc("#a29e95"), hexc("#c2beb4")], 16),
        "bloom_tip": mat_glow([hexc("#d68a12"), hexc("#ffc23a"), hexc("#ffe27a"), hexc("#fff6c2")], 17),
        "petal": mat_petal(18),
        "stem": mat_flat_ramp([hexc("#3a4632"), hexc("#55663f"), hexc("#6f7f52"), hexc("#8c9a6a")], 19),
        "halo": mat_halo(20),
        "swirl": mat_swirl(),
        "spike": mat_bark([hexc("#2b2420"), hexc("#4a3f36"), hexc("#8c8274"), hexc("#d4ccbe")], 21, 0.2),
    })


# ---------------------------------------------------------------- per-face details
def eyes(color, y_frac=0.45, gap=1, size=(1, 2)):
    """Two glowing eye slits on the front face."""

    def detail(img, glow, faces, rnd):
        fx, fy, fw, fh = faces["front"]
        ey = fy + int(fh * y_frac)
        cx = fx + fw // 2
        for sx in (cx - gap - size[0], cx + gap):
            for dx in range(size[0]):
                for dy in range(size[1]):
                    img.putpixel((sx + dx, ey + dy), color)
                    glow.putpixel((sx + dx, ey + dy), color)
        # dark sockets around them
        for sx in (cx - gap - size[0] - 1, cx + gap + size[0]):
            for dy in range(size[1]):
                img.putpixel((sx, ey + dy), shade(img.getpixel((sx, ey + dy)), 0.55))

    return detail


def knots(color, pts):
    def detail(img, glow, faces, rnd):
        fx, fy, fw, fh = faces["front"]
        for (px, py) in pts:
            x, y = fx + int(px * fw), fy + int(py * fh)
            for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
                c = mix(color, hexc("#fff3b0"), 0.35 if (dx, dy) == (0, 0) else 0.0)
                img.putpixel((x + dx, y + dy), c)
                glow.putpixel((x + dx, y + dy), c)

    return detail


def heart_socket():
    def detail(img, glow, faces, rnd):
        fx, fy, fw, fh = faces["front"]
        cx, cy = fx + fw // 2, fy + int(fh * 0.38)
        for dy in range(-3, 4):
            for dx in range(-3, 3):
                if abs(dx + 0.5) + abs(dy) <= 4:
                    img.putpixel((cx + dx, cy + dy), shade(img.getpixel((cx + dx, cy + dy)), 0.45))
        # resin cracks running from the heart
        r = random.Random(3)
        for k in range(5):
            x, y = cx, cy
            ang = r.uniform(0, math.tau)
            for s in range(r.randint(3, 6)):
                x += math.cos(ang)
                y += math.sin(ang)
                ang += r.uniform(-0.5, 0.5)
                xi, yi = int(x), int(y)
                if fx <= xi < fx + fw and fy <= yi < fy + fh:
                    c = ramp(RESIN, 0.55 - s * 0.05)
                    img.putpixel((xi, yi), c)
                    glow.putpixel((xi, yi), shade(c, 0.8))

    return detail


def big_iris():
    def detail(img, glow, faces, rnd):
        fx, fy, fw, fh = faces["front"]
        cx, cy = fw / 2, fh / 2
        for yy in range(fh):
            for xx in range(fw):
                d = math.hypot(xx + 0.5 - cx, yy + 0.5 - cy) / (fw / 2)
                if d < 0.98:
                    c = ramp(RESIN[1:], 0.95 - d * 0.75 + rnd.uniform(-0.06, 0.06))
                    if abs(xx + 0.5 - cx) < 0.9 and abs(yy + 0.5 - cy) < fh * 0.38:
                        c = hexc("#120d0b")
                        img.putpixel((fx + xx, fy + yy), c)
                        glow.putpixel((fx + xx, fy + yy), CLEAR)
                        continue
                    img.putpixel((fx + xx, fy + yy), c)
                    glow.putpixel((fx + xx, fy + yy), c)
                else:
                    img.putpixel((fx + xx, fy + yy), hexc("#3a2010"))
                    glow.putpixel((fx + xx, fy + yy), CLEAR)

    return detail


def wisp_eye():
    def detail(img, glow, faces, rnd):
        fx, fy, fw, fh = faces["front"]
        for yy in range(fh):
            for xx in range(fw):
                d = math.hypot(xx + 0.5 - fw / 2, yy + 0.5 - fh / 2)
                if d < 2.3:
                    c = hexc("#1a0f08") if d < 0.9 else ramp(RESIN[1:], 0.8 - d * 0.2)
                    img.putpixel((fx + xx, fy + yy), c)
                    glow.putpixel((fx + xx, fy + yy), c if d >= 0.9 else CLEAR)

    return detail


# ---------------------------------------------------------------- the models
def models():
    C, P = Cube, Part
    out = {}
    out["CreakingHusk"] = ("creaking_husk", 64, 64, [
        P("root", None, (0, 24, 0)),
        P("right_leg", "root", (-2.5, -18, 0), cubes=[C("bark", -1.5, 0, -1.5, 3, 18, 3)]),
        P("left_leg", "root", (2.5, -18, 0), cubes=[C("bark", -1.5, 0, -1.5, 3, 18, 3)]),
        P("body", "root", (0, -18, 0), cubes=[
            C("bark", -4, -14, -2.5, 8, 14, 5, detail=knots(hexc("#ff9a26"), [(0.2, 0.25), (0.62, 0.55)])),
            C("moss_drape", -4.5, -15, 2.7, 9, 15, 0)]),
        P("head", "body", (0, -14, 0), cubes=[
            C("bark", -3, -9, -3, 6, 9, 6, detail=eyes(hexc("#ffb02e"), 0.42)),
            C("twigs", -6, -16, 0, 12, 8, 0)]),
        P("right_arm", "body", (-5, -13, 0), cubes=[C("bark", -2, -1, -1.5, 3, 19, 3), C("pale_claws", -2.5, 17, 0, 4, 5, 0)]),
        P("left_arm", "body", (5, -13, 0), cubes=[C("bark", -1, -1, -1.5, 3, 19, 3), C("pale_claws", -1.5, 17, 0, 4, 5, 0)]),
    ])
    wisp = [
        P("root", None, (0, 24, 0)),
        P("core", "root", (0, -12, 0), cubes=[C("sclera", -3, -3, -3, 6, 6, 6, detail=wisp_eye())]),
        P("lid", "core", (0, 0, 0), cubes=[C("lid", -3, -3, -3.3, 6, 6, 0)]),
        P("tail", "core", (0, 3, 0), cubes=[C("stem", -0.5, 0, -0.5, 1, 8, 1), C("petal", 0.5, 3, 0, 3, 3, 0)]),
    ]
    for i in range(6):
        wisp.append(P(f"petal{i}", "core", (0, 0, 1.2), (0, 0, i * 60), [C("petal", -1.5, -9, 0, 3, 6, 0)]))
    out["EyeblossomWisp"] = ("eyeblossom_wisp", 64, 32, wisp)

    out["MotherCreak"] = ("mother_creak", 128, 128, [
        P("root", None, (0, 24, 0)),
        P("right_leg", "root", (-3.5, -16, 0), cubes=[C("bark_dark", -2, 0, -2, 4, 16, 4), C("roots", -4, 15.9, -4, 6, 0, 8)]),
        P("left_leg", "root", (3.5, -16, 0), cubes=[C("bark_dark", -2, 0, -2, 4, 16, 4), C("roots", -2, 15.9, -4, 6, 0, 8)]),
        P("body", "root", (0, -16, 0), cubes=[C("bark_dark", -5, -15, -3, 10, 15, 6, detail=heart_socket())]),
        P("plates", "body", (0, 0, 0), cubes=[C("bark_plate", -5.5, -14, -3.6, 4, 9, 1), C("bark_plate", 1.5, -14, -3.6, 4, 9, 1),
                                               C("bark_plate", -5.6, -6, -3.4, 11, 4, 7)]),
        P("heart", "body", (0, -9.5, -3.2), cubes=[C("resin", -2, -2, -1, 4, 4, 2)]),
        P("head", "body", (0, -15, 0), cubes=[C("bark_dark", -3.5, -9, -3.5, 7, 9, 7, detail=eyes(hexc("#ffd24a"), 0.4, 1, (2, 1)))]),
        P("antler_r", "head", (-3, -8, 0), (0, -25, -20), cubes=[C("antlers", -12, -14, 0, 12, 14, 0)]),
        P("antler_l", "head", (3, -8, 0), (0, 25, 20), cubes=[C("antlers", 0, -14, 0, 12, 14, 0)]),
        P("right_arm", "body", (-6.5, -13.5, 0), cubes=[C("bark_dark", -3, -1.5, -2, 3, 20, 4)]),
        P("right_claw", "right_arm", (-1.5, 18, 0), cubes=[C("claws", -3, 0, 0, 6, 9, 0)]),
        P("left_arm", "body", (6.5, -13.5, 0), cubes=[C("bark_dark", 0, -1.5, -2, 3, 20, 4)]),
        P("left_claw", "left_arm", (1.5, 18, 0), cubes=[C("claws", -3, 0, 0, 6, 9, 0)]),
    ])

    sel = [
        P("root", None, (0, 24, 0)),
        P("eye", "root", (0, -9.5, 0), cubes=[C("sclera", -8, -8, -6, 16, 16, 12)]),
        P("iris", "eye", (0, 0, -6), cubes=[C("iris", -3.5, -3.5, -0.7, 7, 7, 1, detail=big_iris())]),
        P("upper_lid", "eye", (0, -8.5, -6), cubes=[C("lid", -9, 0, -1, 18, 9, 2), C("lid", -9, -1, -1, 18, 1, 14)]),
        P("lower_lid", "eye", (0, 8.5, -6), cubes=[C("lid", -9, -9, -1, 18, 9, 2), C("lid", -9, 0, -1, 18, 1, 14)]),
        P("halo", "eye", (0, 0, 7), cubes=[C("halo", -16, -16, 0, 32, 32, 0)]),
    ]
    for i in range(8):
        sel.append(P(f"lash{i}", "eye", (0, 0, -1), (0, 0, 22.5 + i * 45),
                     [C("stalk", -0.5, -18, -0.5, 1, 8, 1), C("bloom_tip", -1.5, -21, -1.5, 3, 3, 3)]))
    out["Selenne"] = ("selenne", 128, 128, sel)

    out["RootSpike"] = ("root_spike", 32, 32, [
        P("root", None, (0, 24, 0)),
        P("spikes", "root", (0, 0, 0), cubes=[C("spike", -1.5, -14, -1.5, 3, 14, 3)]),
        P("spike_a", "spikes", (2, 0, 1), (0, 0, -18), cubes=[C("spike", -1, -9, -1, 2, 9, 2)]),
        P("spike_b", "spikes", (-2, 0, -1), (-12, 0, 20), cubes=[C("spike", -1, -10, -1, 2, 10, 2)]),
    ])

    out["Rift"] = ("rift", 64, 64, [
        P("root", None, (0, 0, 0)),
        P("outer", "root", (0, 0, 0), cubes=[C("swirl", -16, -16, 0, 32, 32, 0)]),
        P("inner", "root", (0, 0, -0.05), cubes=[C("swirl", -16, -16, 0, 32, 32, 0)]),
    ])
    return out


def build_models():
    make_mats()
    for cls, (tex, tw, th, parts) in models().items():
        pack(cls, parts, tw, th)
        img = Image.new("RGBA", (tw, th), CLEAR)
        glow = Image.new("RGBA", (tw, th), CLEAR)
        rnd = random.Random(hash(cls) & 0xFFFF)
        seen = set()
        for p in parts:
            for c in p.cubes:
                key = (c.u, c.v)
                if key in seen:
                    continue
                seen.add(key)
                paint = MATS[c.mat]
                faces = c.faces()
                if c.d == 0:
                    faces = {"front": (c.u, c.v, c.w, c.h), "back": (c.u + c.w, c.v, c.w, c.h)}
                elif c.h == 0:
                    faces = {"up": (c.u + c.d, c.v, c.w, c.d), "down": (c.u + c.d + c.w, c.v, c.w, c.d)}
                for face, rect in faces.items():
                    if rect[2] > 0 and rect[3] > 0:
                        paint(img, glow, rect, face, rnd)
                if c.detail:
                    c.detail(img, glow, faces, rnd)
        save(img, f"entity/{tex}.png")
        save(glow, f"entity/{tex}_glow.png")
        write_geometry(cls + "Geometry", parts, tw, th)
        print("model", cls, tw, th)


# ---------------------------------------------------------------- 16x16 item and block art
class Canvas:
    def __init__(self, w=16, h=16):
        self.img = Image.new("RGBA", (w, h), CLEAR)
        self.w, self.h = w, h

    def px(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            self.img.putpixel((int(x), int(y)), c)

    def get(self, x, y):
        return self.img.getpixel((x, y))

    def disc(self, cx, cy, r, fn):
        for y in range(self.h):
            for x in range(self.w):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d <= r:
                    self.px(x, y, fn(x, y, d / r))

    def rect(self, x0, y0, x1, y1, fn):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.px(x, y, fn(x, y))

    def line(self, x0, y0, x1, y1, c):
        steps = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(steps + 1):
            t = i / steps
            self.px(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c)

    def outline(self, color=None):
        """Darken-outline every opaque pixel that touches transparency (classic item look)."""
        src = self.img.copy()
        for y in range(self.h):
            for x in range(self.w):
                if src.getpixel((x, y))[3] == 0:
                    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                        nx, ny = x + dx, y + dy
                        if 0 <= nx < self.w and 0 <= ny < self.h and src.getpixel((nx, ny))[3] > 0:
                            c = src.getpixel((nx, ny))
                            self.img.putpixel((x, y), color or shade(c, 0.35))
                            break


def compass_base(c, needle_pal, glass=hexc("#2a2238")):
    c.disc(8, 8.5, 7, lambda x, y, d: ramp(BRASS, 0.85 - d * 0.6 - (0.15 if y > 9 else 0)))
    c.disc(8, 8.5, 5, lambda x, y, d: mix(glass, hexc("#433a58"), 0.3 * (1 - d)))
    # rivets
    for (x, y) in ((8, 2), (8, 14), (2, 8), (14, 8)):
        c.px(x, y, BRASS[4])
    c.line(5, 11, 11, 6, needle_pal[2])
    c.line(5, 12, 11, 7, needle_pal[1])
    c.px(11, 6, needle_pal[3])
    c.px(8, 9, BRASS[3])


def item_wayfinder():
    c = Canvas()
    compass_base(c, AMETHYST)
    c.outline()
    return c.img


def item_wayfinder_pale_hollow():
    c = Canvas()
    compass_base(c, RESIN[1:], glass=hexc("#3b3a36"))
    # pale bark frame wrap + moss tuft
    for (x, y) in ((2, 5), (3, 4), (1, 7), (13, 12), (12, 13), (14, 10)):
        c.px(x, y, BARK_PALE[2])
    for (x, y) in ((4, 13), (5, 14), (6, 14), (3, 12)):
        c.px(x, y, MOSS[2])
    c.px(10, 4, hexc("#ffd36a"))
    c.outline()
    return c.img


def item_frayed_wayfinder():
    c = Canvas()
    compass_base(c, [hexc("#3a3a3a"), hexc("#7a7a7a"), hexc("#bdbdbd"), hexc("#ffffff")], glass=hexc("#2b2b30"))
    r = random.Random(4)
    # cracks and missing chunks
    for (x, y) in ((12, 3), (13, 4), (13, 5), (3, 12), (2, 11)):
        c.px(x, y, CLEAR)
    c.line(6, 4, 9, 8, hexc("#1b1712"))
    c.line(9, 8, 8, 12, hexc("#1b1712"))
    for _ in range(4):
        c.px(r.randint(3, 12), r.randint(3, 12), hexc("#9a8f80"))
    c.outline()
    return c.img


def item_frayed_frame(k):
    """Frayed Wayfinder compass frame k (0..31); frame 16 points straight up (target ahead)."""
    c = Canvas()
    compass_base(c, [hexc("#3a3a3a"), hexc("#7a7a7a"), hexc("#bdbdbd"), hexc("#ffffff")], glass=hexc("#2b2b30"))
    # wipe the default needle, draw the rotated one
    c.disc(8, 8.5, 5, lambda x, y, d: mix(hexc("#2b2b30"), hexc("#433a58"), 0.3 * (1 - d)))
    ang = (k - 16) / 32.0 * math.tau
    dx, dy = math.sin(ang), -math.cos(ang)
    for t in range(0, 5):
        c.px(round(8 + dx * t), round(8.5 + dy * t), RESIN[3] if t > 2 else RESIN[2])
    for t in range(1, 4):
        c.px(round(8 - dx * t), round(8.5 - dy * t), hexc("#8a8f99"))
    for (x, y) in ((12, 3), (13, 4), (3, 12), (2, 11)):
        c.px(x, y, CLEAR)
    c.outline()
    return c.img


def item_heartwood_shard():
    c = Canvas()
    pts = [(8, 1), (12, 6), (11, 12), (7, 15), (4, 10), (5, 4)]

    def inside(x, y):
        n = len(pts)
        ins = False
        j = n - 1
        for i in range(n):
            xi, yi = pts[i]
            xj, yj = pts[j]
            if ((yi > y) != (yj > y)) and (x < (xj - xi) * (y - yi) / (yj - yi + 1e-9) + xi):
                ins = not ins
            j = i
        return ins

    for y in range(16):
        for x in range(16):
            if inside(x + 0.5, y + 0.5):
                t = 0.35 + 0.5 * (1 - (x + y) / 28) + (0.18 if (x - y) % 5 == 0 else 0)
                c.px(x, y, ramp(RESIN, t))
    c.px(7, 4, RESIN[4]); c.px(8, 5, RESIN[4]); c.px(6, 8, RESIN[3])
    c.outline()
    return c.img


def item_pale_sap():
    c = Canvas()
    glass = hexc("#cfd8e0", 170)
    c.rect(6, 1, 9, 2, lambda x, y: hexc("#8a6a48"))  # cork
    c.rect(6, 3, 9, 4, lambda x, y: glass)
    c.disc(8, 10, 5.2, lambda x, y, d: mix(hexc("#f0e6c8"), hexc("#b9a77a"), d) if y > 6 else glass)
    c.px(5, 8, hexc("#ffffff")); c.px(5, 9, hexc("#ffffff"))
    c.outline(hexc("#3a3833"))
    return c.img


def item_unblinking_eye():
    c = Canvas()
    c.disc(8, 8, 6.5, lambda x, y, d: ramp(SCLERA, 0.9 - d * 0.6))
    c.disc(8, 8, 3.6, lambda x, y, d: ramp(RESIN[1:], 0.95 - d * 0.6))
    c.rect(8, 6, 8, 10, lambda x, y: hexc("#120b07"))
    c.px(6, 6, hexc("#ffffff"))
    for (x, y) in ((2, 3), (13, 3), (1, 8), (14, 8), (3, 13), (12, 13)):
        c.px(x, y, PETAL[2])
    c.outline()
    return c.img


def item_pale_bark():
    c = Canvas()
    n = vnoise(77)
    for y in range(2, 14):
        for x in range(3, 13):
            if (x - 3) * 0.3 + (y - 2) * 0.1 < 4.2 and not (x == 12 and y < 5):
                t = 0.35 + 0.5 * n(x * 2, y * 0.5, 2) - (0.25 if x % 3 == 0 else 0)
                c.px(x, y + (1 if x > 9 else 0), ramp(BARK_PALE, t))
    c.outline()
    return c.img


def item_locket():
    c = Canvas()
    c.line(4, 1, 8, 4, BRASS[2]); c.line(12, 1, 8, 4, BRASS[2])
    c.disc(8, 10, 5.4, lambda x, y, d: ramp(BRASS, 0.9 - d * 0.7))
    # resin heart inside
    heart = [(7, 8), (9, 8), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10),
             (7, 11), (8, 11), (9, 11), (8, 12)]
    for (x, y) in heart:
        c.px(x, y, ramp(RESIN, 0.9 - (y - 8) * 0.12))
    c.px(7, 9, RESIN[4])
    c.outline()
    return c.img


def item_greatbow(pull):
    c = Canvas()
    curve = 0.9 - pull * 0.25
    # bow limb: an arc of pale-dark heartwood with amber wraps
    for t in range(0, 101):
        a = -math.pi / 2 + math.pi * t / 100
        x = 4 + math.cos(a) * 9.5 * curve
        y = 8 + math.sin(a) * 7.2
        col = ramp(BARK_DARK, 0.5 + 0.3 * math.sin(t))
        if 44 < t < 56:
            col = RESIN[2]
        c.px(round(x), round(y), col)
        c.px(round(x) + 1, round(y), shade(col, 1.25))
    # antler prongs at the tips
    c.px(5, 0, BARK_PALE[2]); c.px(6, 0, BARK_PALE[3]); c.px(5, 15, BARK_PALE[2]); c.px(6, 15, BARK_PALE[3])
    # string
    sx = 4 - pull * 2
    c.line(5, 1, sx, 8, hexc("#e8e2d0")); c.line(sx, 8, 5, 14, hexc("#e8e2d0"))
    if pull > 0:
        c.line(sx, 8, sx + 8, 8, hexc("#8a7a66"))
        c.px(sx + 8, 8, hexc("#cfd6dd")); c.px(sx + 9, 8, hexc("#cfd6dd"))
    c.outline()
    return c.img


def item_circlet():
    c = Canvas()
    for t in range(0, 360, 6):
        a = math.radians(t)
        c.px(round(8 + math.cos(a) * 6), round(10 + math.sin(a) * 2.5), ramp(BARK_PALE, 0.4 + 0.3 * math.sin(a)))
    for (x, y) in ((4, 7), (8, 5), (12, 7)):
        for dx, dy in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            c.px(x + dx, y + dy, PETAL[2])
        c.px(x, y, RESIN[3])
    c.outline()
    return c.img


def item_moon_eye():
    c = Canvas()
    c.disc(8, 8, 6.8, lambda x, y, d: mix(hexc("#e9eef8"), hexc("#8c9bb5"), d))
    c.disc(10.5, 6.5, 5.2, lambda x, y, d: CLEAR)
    c.disc(6, 9.5, 2.2, lambda x, y, d: ramp(RESIN[1:], 0.9 - d * 0.5))
    c.px(6, 9, hexc("#120b07")); c.px(6, 10, hexc("#120b07"))
    c.outline()
    return c.img


def item_effigy():
    c = Canvas()
    c.rect(6, 4, 9, 13, lambda x, y: ramp(BARK_DARK, 0.4 + (0.2 if x == 6 else 0)))
    c.rect(5, 1, 10, 4, lambda x, y: ramp(BARK_DARK, 0.6))
    c.px(6, 2, hexc("#ffd24a")); c.px(9, 2, hexc("#ffd24a"))
    c.px(7, 7, RESIN[3]); c.px(8, 7, RESIN[3]); c.px(7, 8, RESIN[2]); c.px(8, 8, RESIN[2])
    c.line(5, 6, 2, 3, BARK_DARK[2]); c.line(10, 6, 13, 3, BARK_DARK[2])
    c.line(6, 13, 4, 15, BARK_DARK[1]); c.line(9, 13, 11, 15, BARK_DARK[1])
    c.outline()
    return c.img


def item_moonring_seed():
    c = Canvas()
    c.disc(8, 9, 5.5, lambda x, y, d: ramp(TUFF, 0.85 - d * 0.6))
    c.disc(8, 9, 2.5, lambda x, y, d: mix(hexc("#e8eefc"), hexc("#9fb0cc"), d))
    for k in range(6):
        a = k * math.tau / 6
        c.px(round(8 + math.cos(a) * 6.5), round(9 + math.sin(a) * 6.5), TUFF[3])
    c.line(8, 3, 8, 1, PETAL[2]); c.px(7, 1, PETAL[1]); c.px(9, 1, PETAL[1])
    c.outline()
    return c.img


def item_spawn_egg(base, spots):
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 5.6, (y + 0.5 - 9) / 7.2
            if dx * dx + dy * dy <= 1:
                c.px(x, y, ramp(base, 0.8 - (dx * dx + dy * dy) * 0.4 - (0.15 if x > 9 else 0)))
    r = random.Random(sum(spots[0][:3]))
    for _ in range(7):
        x, y = r.randint(4, 11), r.randint(4, 14)
        if c.get(x, y)[3]:
            c.px(x, y, spots[r.randint(0, len(spots) - 1)])
    c.outline()
    return c.img


def block_resin_heart():
    c = Canvas()
    n = vnoise(91)
    for y in range(16):
        for x in range(16):
            c.px(x, y, ramp(BARK_DARK, 0.3 + 0.4 * n(x, y * 0.4, 2)))
    c.disc(8, 8, 5.5, lambda x, y, d: ramp(RESIN, 0.95 - d * 0.75 + (0.1 if (x + y) % 3 == 0 else 0)))
    for (x, y) in ((8, 1), (8, 2), (1, 8), (2, 8), (14, 8), (13, 8), (8, 14), (8, 13)):
        c.px(x, y, RESIN[2])
    return c.img


def block_heartwood_side():
    c = Canvas()
    n = vnoise(92)
    for y in range(16):
        for x in range(16):
            t = 0.45 + 0.35 * n(x * 3, y * 0.5, 2) - (0.25 if x % 4 == 1 else 0)
            c.px(x, y, ramp(BARK_PALE, t))
    r = random.Random(5)
    for k in range(2):
        x = r.randint(2, 13)
        for y in range(16):
            if r.random() < 0.85:
                c.px(x, y, ramp(RESIN, 0.55 + 0.2 * math.sin(y)))
            x = max(1, min(14, x + r.choice((-1, 0, 0, 1))))
    return c.img


def block_heartwood_top():
    c = Canvas()
    c.disc(8, 8, 8.5, lambda x, y, d: ramp(BARK_PALE, 0.5 + 0.3 * math.sin(d * 9)))
    c.disc(8, 8, 3, lambda x, y, d: ramp(RESIN, 0.8 - d * 0.4))
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15):
                c.px(x, y, BARK_PALE[1])
    return c.img


def block_moonring_stone():
    c = Canvas()
    n = vnoise(93)
    for y in range(16):
        for x in range(16):
            c.px(x, y, ramp(TUFF, 0.3 + 0.5 * n(x, y, 2.5) + random.Random(x * 31 + y).uniform(-0.08, 0.08)))
    # carved crescent moon
    for y in range(16):
        for x in range(16):
            d1 = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            d2 = math.hypot(x + 0.5 - 10, y + 0.5 - 6.5)
            if 3.0 < d1 < 5.0 and d2 > 4.2:
                c.px(x, y, hexc("#c9d6ee"))
            elif 3.0 < d1 < 5.6 and d2 > 3.8:
                c.px(x, y, TUFF[0])
    return c.img


def block_ashen_moss():
    c = Canvas()
    n = vnoise(94)
    r = random.Random(6)
    for y in range(16):
        for x in range(16):
            c.px(x, y, ramp([hexc("#6f736c"), hexc("#8f948a"), hexc("#b0b4aa"), hexc("#cfd2c8")], 0.3 + 0.5 * n(x, y, 2) + r.uniform(-0.1, 0.1)))
    for _ in range(14):
        c.px(r.randint(0, 15), r.randint(0, 15), hexc("#f2f2ec"))
    return c.img


def block_moon_bloom():
    c = Canvas()
    c.line(8, 15, 8, 8, hexc("#55663f"))
    c.px(7, 12, hexc("#6f7f52")); c.px(6, 11, hexc("#6f7f52")); c.px(9, 13, hexc("#6f7f52")); c.px(10, 12, hexc("#6f7f52"))
    # closed bud: grey petals wrapped around a sliver of pale blue light
    c.rect(6, 3, 9, 7, lambda x, y: ramp(PETAL, 0.35 + (0.3 if x in (7, 8) else 0)))
    c.px(7, 2, PETAL[1]); c.px(8, 2, PETAL[2]); c.px(5, 5, PETAL[1]); c.px(10, 5, PETAL[1])
    c.px(7, 5, hexc("#cfe0ff")); c.px(8, 4, hexc("#cfe0ff")); c.px(8, 5, hexc("#eef4ff"))
    return c.img


def block_arena_seal():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            hexpat = (x + y * 2) % 7 == 0 or (x * 2 - y) % 9 == 0
            a = 170 if edge else (110 if hexpat else 45)
            c.px(x, y, (190, 200, 255, a))
    return c.img


def block_return_rift():
    c = Canvas()
    for y in range(16):
        for x in range(16):
            dx, dy = (x + 0.5 - 8) / 6.0, (y + 0.5 - 8) / 8.0
            r = math.hypot(dx, dy)
            if r < 1:
                ang = math.atan2(dy, dx)
                s = 0.5 + 0.5 * math.sin(ang * 3 + r * 8)
                base = mix(hexc("#ffffff"), hexc("#f2c27a"), r)
                c.px(x, y, (base[0], base[1], base[2], int(255 * min(1, (1 - r) ** 0.5 * (0.45 + 0.55 * s)))))
    return c.img


def beam_texture():
    img = Image.new("RGBA", (16, 64), CLEAR)
    for y in range(64):
        for x in range(16):
            stripe = 0.6 + 0.4 * math.sin((y * 0.4) + x * 0.8)
            v = int(255 * stripe)
            img.putpixel((x, y), (v, v, v, int(150 + 100 * stripe)))
    save(img, "entity/return_rift_beam.png")


def particle_frames():
    for i in range(4):
        c = Canvas(8, 8)
        r = 3.6 - i * 0.7
        c.disc(4, 4, r, lambda x, y, d: (255, 255, 255, int(255 * (1 - d) ** 0.5)))
        if i < 2:
            c.px(4, 0, (255, 255, 255, 200)); c.px(4, 7, (255, 255, 255, 200)); c.px(0, 4, (255, 255, 255, 200)); c.px(7, 4, (255, 255, 255, 200))
        save(c.img, f"particle/rift_spark_{i}.png")
    for i in range(3):
        c = Canvas(8, 8)
        r = 2.8 - i * 0.6
        c.disc(4, 4, r, lambda x, y, d: mix(hexc("#ffd36a"), hexc("#b45a12"), d))
        save(c.img, f"particle/resin_dust_{i}.png")
    for i in range(3):
        c = Canvas(8, 8)
        c.disc(4, 4, 3.4 - i * 0.8, lambda x, y, d: (220, 232, 255, int(255 * (1 - d))))
        save(c.img, f"particle/moon_mote_{i}.png")


def icon():
    img = Image.new("RGBA", (128, 128), hexc("#1c1b22"))
    big = item_wayfinder_pale_hollow().resize((96, 96), Image.NEAREST)
    img.alpha_composite(big, (16, 16))
    img.save(os.path.join(ROOT, "src/main/resources/assets/elsewhere/icon.png"))


def build_items_blocks():
    items = {
        "wayfinder": item_wayfinder(), "wayfinder_pale_hollow": item_wayfinder_pale_hollow(),
        "frayed_wayfinder": item_frayed_wayfinder(), "heartwood_shard": item_heartwood_shard(),
        "pale_sap": item_pale_sap(), "unblinking_eye": item_unblinking_eye(), "pale_bark": item_pale_bark(),
        "resin_heart_locket": item_locket(), "eyeblossom_circlet": item_circlet(), "moon_eye": item_moon_eye(),
        "heartwood_effigy": item_effigy(), "moonring_seed": item_moonring_seed(),
        "heartwood_greatbow": item_greatbow(0),
        "heartwood_greatbow_pulling_0": item_greatbow(1), "heartwood_greatbow_pulling_1": item_greatbow(2),
        "heartwood_greatbow_pulling_2": item_greatbow(3),
        "creaking_husk_spawn_egg": item_spawn_egg(BARK_PALE, [hexc("#ff9a26"), MOSS[2]]),
        "eyeblossom_wisp_spawn_egg": item_spawn_egg(PETAL, [hexc("#ffb02e"), hexc("#3a4632")]),
    }
    for k in range(32):
        items[f"frayed_wayfinder_{k:02d}"] = item_frayed_frame(k)
    for k, v in items.items():
        save(v, f"item/{k}.png")
    blocks = {
        "resin_heart": block_resin_heart(), "heartwood_log": block_heartwood_side(), "heartwood_log_top": block_heartwood_top(),
        "moonring_stone": block_moonring_stone(), "ashen_moss": block_ashen_moss(), "moon_bloom": block_moon_bloom(),
        "arena_seal": block_arena_seal(), "return_rift": block_return_rift(),
    }
    for k, v in blocks.items():
        save(v, f"block/{k}.png")
    particle_frames()
    beam_texture()
    icon()
    print("items", len(items), "blocks", len(blocks))


if __name__ == "__main__":
    build_models()
    build_items_blocks()
