"""Entity models for the petalwind_glasswaste module: one spec per model drives both the Java LayerDefinitions
(src/client/java/.../client/GeneratedModels.java) and the entity textures (box-UV packed, procedural colours).
Run: S/venv/bin/python tools/gen_models.py"""
import math, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity")
JAVA = os.path.join(HERE, "..", "src", "client", "java", "dev", "elsewhere", "petalwind_glasswaste", "client", "GeneratedModels.java")


def hexc(s):
    s = s.lstrip("#")
    a = int(s[6:8], 16) if len(s) == 8 else 255
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)


# part: (name, parent, pivot(x,y,z), rotation(x,y,z) radians, [box (x,y,z,w,h,d,colour, front_detail)])
def humanoid(skin, robe, legs, extra):
    parts = [
        ("body", None, (0, 0, 0), (0, 0, 0), [(-4, 0, -2, 8, 12, 4, robe, None)]),
        ("head", None, (0, 0, 0), (0, 0, 0), [(-4, -8, -4, 8, 8, 8, skin, "eyes")]),
        ("right_arm", None, (-5, 2, 0), (0, 0, 0), [(-3, -2, -2, 4, 12, 4, robe, None)]),
        ("left_arm", None, (5, 2, 0), (0, 0, 0), [(-1, -2, -2, 4, 12, 4, robe, None)]),
        ("right_leg", None, (-1.9, 12, 0), (0, 0, 0), [(-2, 0, -2, 4, 12, 4, legs, None)]),
        ("left_leg", None, (1.9, 12, 0), (0, 0, 0), [(-2, 0, -2, 4, 12, 4, legs, None)]),
    ]
    return parts + extra


MODELS = {
    "kazehana": humanoid("3A3440", "C9667E", "2A2230", [
        ("hat", "head", (0, -8, 0), (0, 0, 0), [(-10, -1, -10, 20, 1, 20, "C9A060", None), (-4, -4, -4, 8, 3, 8, "B08850", None)]),
        ("haori", "body", (0, 0, 0), (0, 0, 0), [(-5, -0.5, -2.6, 10, 18, 5.2, "E58FAA", None)]),
        ("katana", "right_arm", (-1, 9, 0), (0, 0, 0), [(-0.5, -0.5, -18, 1, 1, 18, "F0F4FF", None), (-1, -1, -1, 2, 2, 4, "2A1A1A", None)]),
    ]),
    "sunwright_revenant": humanoid("E8DCC0", "D9C08A", "C8B078", [
        ("wrap", "head", (0, 0, 0), (0, 0, 0), [(-4.5, -8.5, -4.5, 9, 3, 9, "F5C542", None)]),
        ("banner", "body", (0, 0, 2), (0.15, 0, 0), [(-4, -6, 0, 8, 18, 1, "8A2A2A", None)]),
    ]),
    "windwright": humanoid("E8C8B0", "7FB8E0", "F3E9F7", [
        ("hat", "head", (0, -8, 0), (0, 0, 0), [(-6, -1, -6, 12, 1, 12, "F6A9C8", None), (-3, -4, -3, 6, 3, 6, "F6A9C8", None)]),
        ("kitepack", "body", (0, 2, 2), (0, 0, 0.785), [(-5, -5, 0, 10, 10, 1, "F6A9C8", "kite")]),
    ]),
    "dune_factor": humanoid("C89870", "D8B060", "8A5A2A", [
        ("hood", "head", (0, 0, 0), (0, 0, 0), [(-4.5, -8.5, -4.5, 9, 9, 9, "E8D8B0", None)]),
        ("pack", "body", (0, 1, 2), (0, 0, 0), [(-4, 0, 0, 8, 9, 4, "8A5A2A", None)]),
    ]),
    "tempestra": [
        ("head", None, (0, -18, 0), (0, 0, 0), [(-8, -16, -8, 16, 16, 16, "B8CDE0", "glow_eyes")]),
        ("ring1", None, (0, -20, 0), (0, 0, 0), [(math.cos(a) * 18 - 2, -1, math.sin(a) * 18 - 4, 4, 3, 8, "ECEAE4" if i % 2 else "D8A8C0", None)
                                                  for i, a in enumerate([k * math.pi / 3 for k in range(6)])]),
        ("ring2", None, (0, -8, 0), (0, 0, 0), [(math.cos(a) * 22 - 2, -1, math.sin(a) * 22 - 4, 4, 3, 8, "ECEAE4", None)
                                                 for a in [k * math.pi / 3 + 0.5 for k in range(6)]]),
        ("ring3", None, (0, 4, 0), (0, 0, 0), [(math.cos(a) * 16 - 1.5, -1, math.sin(a) * 16 - 3, 3, 2, 6, "BFE6FF", None)
                                                for a in [k * math.pi / 3 + 1.0 for k in range(6)]]),
        ("tail1", None, (0, -2, 0), (0, 0, 0), [(-7, 0, -7, 14, 8, 14, "DCE8F6AA", None)]),
        ("tail2", "tail1", (0, 8, 0), (0, 0, 0), [(-5, 0, -5, 10, 8, 10, "CADCEEAA", None)]),
        ("tail3", "tail2", (0, 8, 0), (0, 0, 0), [(-3, 0, -3, 6, 8, 6, "B8D0EAAA", None)]),
        ("cloud", None, (0, -24, 0), (0, 0, 0), [(-12, -4, -12, 24, 6, 24, "F4F8FFAA", None)]),
    ],
    "heliodor": [
        ("body", None, (0, -6, 0), (0, 0, 0), [(-10, -18, -6, 20, 18, 12, "D9A62E", "window")]),
        ("head", "body", (0, -18, 0), (0, 0, 0), [(-6, -12, -5, 12, 12, 8, "8A5A2A", "lens")]),
        ("rays", "head", (0, -6, 0), (0, 0, 0), [(-1, -15, -1, 2, 6, 2, "FFE070", None), (-1, 9, -1, 2, 4, 2, "FFE070", None),
                                                 (-14, -1, -1, 6, 2, 2, "FFE070", None), (8, -1, -1, 6, 2, 2, "FFE070", None),
                                                 (-12, -12, -1, 3, 3, 2, "FFE070", None), (9, -12, -1, 3, 3, 2, "FFE070", None)]),
        ("right_arm", "body", (-12, -15, 0), (0, 0, 0), [(-6, -2, -3, 6, 26, 6, "E0B040", None)]),
        ("left_arm", "body", (12, -15, 0), (0, 0, 0), [(0, -2, -3, 6, 26, 6, "E0B040", None)]),
        ("right_leg", None, (-5, -6, 0), (0, 0, 0), [(-3, 0, -3, 6, 30, 6, "B86A3A", None)]),
        ("left_leg", None, (5, -6, 0), (0, 0, 0), [(-3, 0, -3, 6, 30, 6, "B86A3A", None)]),
    ],
    "ankhara_head": [
        ("head", None, (0, 12, 0), (0, 0, 0), [(-12, -12, -16, 24, 22, 28, "D8B878", "maw"), (-2, -20, -6, 4, 8, 12, "F2A63A", None)]),
        ("jaw", "head", (0, 8, -14), (0, 0, 0), [(-10, 0, -4, 20, 4, 18, "C8A868", "teeth")]),
        ("fin_l", "head", (12, -4, 0), (0, 0, 0.4), [(0, -1, -6, 8, 2, 12, "F2A63ACC", None)]),
        ("fin_r", "head", (-12, -4, 0), (0, 0, -0.4), [(-8, -1, -6, 8, 2, 12, "F2A63ACC", None)]),
    ],
    "ankhara_segment": [
        ("seg", None, (0, 12, 0), (0, 0, 0), [(-10, -10, -10, 20, 20, 20, "D0B070", "plates"), (-1.5, -16, -6, 3, 6, 12, "F2A63ACC", None)]),
        ("gizzard", "seg", (0, 0, 0), (0, 0, 0), [(-4, 6, -4, 8, 5, 8, "FF8A1E", None)]),
    ],
    "sandswimmer_head": [
        ("head", None, (0, 20, 0), (0, 0, 0), [(-4, -4, -6, 8, 7, 10, "E0CC95", "eyes"), (-3, 2, -7, 6, 2, 6, "F2EEDC", "teeth")]),
    ],
    "sandswimmer_segment": [
        ("seg", None, (0, 20, 0), (0, 0, 0), [(-3.5, -3, -4, 7, 6, 8, "D4BC84", None), (-0.5, -5, -2, 1, 2, 4, "F2A63A", None)]),
    ],
    "gale_kite": [
        ("kite", None, (0, 18, 0), (0, 0, 0.785), [(-6, -6, -0.5, 12, 12, 1, "F6A9C8", "kite")]),
        ("spar", "kite", (0, 0, 0), (0, 0, 0), [(-0.5, -7, -1, 1, 14, 1, "8A6A5A", None)]),
        ("tail1", None, (0, 22, 1), (0, 0, 0), [(-0.5, 0, 0, 1, 1, 5, "FFFFFF", None)]),
        ("tail2", "tail1", (0, 0, 5), (0, 0, 0), [(-0.5, 0, 0, 1, 1, 5, "F6A9C8", None)]),
        ("tail3", "tail2", (0, 0, 5), (0, 0, 0), [(-0.5, 0, 0, 1, 1, 5, "FFFFFF", None)]),
    ],
    "blossom_breeze": [
        ("head", None, (0, 4, 0), (0, 0, 0), [(-4, -8, -4, 8, 8, 8, "BFE6FF", "glow_eyes")]),
        ("wind", None, (0, 8, 0), (0, 0, 0), [(-6, 0, -6, 12, 6, 12, "DCEBF5AA", None), (-3, 6, -3, 6, 8, 6, "CADCEEAA", None)]),
        ("ring1", None, (0, 2, 0), (0, 0, 0), [(math.cos(a) * 9 - 1, -1, math.sin(a) * 9 - 1, 2, 2, 2, "F6A9C8", None) for a in [k * math.pi / 4 for k in range(8)]]),
    ],
    "cumulus": [
        ("cube", None, (0, 24, 0), (0, 0, 0), [(-8, -16, -8, 16, 16, 16, "F7F9FC", "sleepy"), (-5, -18, -5, 10, 3, 10, "FFFFFF", None)]),
    ],
    "skywhale": [
        ("body", None, (0, 8, 0), (0, 0, 0), [(-14, -14, -24, 28, 22, 44, "9FB8E0", "whale")]),
        ("belly", "body", (0, 0, 0), (0, 0, 0), [(-12, 8, -20, 24, 3, 36, "E8F0F8", None)]),
        ("blossom", "body", (0, -14, 0), (0, 0, 0), [(-6, -5, -12, 12, 5, 10, "F6A9C8", None), (-4, -4, 4, 8, 4, 8, "F8C0D8", None)]),
        ("tail1", "body", (0, -2, 20), (0, 0, 0), [(-5, -4, 0, 10, 8, 10, "8FA8D0", None)]),
        ("tail2", "tail1", (0, 0, 10), (0, 0, 0), [(-12, -1, 0, 24, 2, 8, "8FA8D0", None)]),
        ("fin_l", "body", (14, 4, -6), (0, 0, 0.3), [(0, -1, -4, 10, 2, 8, "8FA8D0", None)]),
        ("fin_r", "body", (-14, 4, -6), (0, 0, -0.3), [(-10, -1, -4, 10, 2, 8, "8FA8D0", None)]),
        ("harness", "body", (0, -14, -4), (0, 0, 0), [(-15, -1, -4, 30, 2, 8, "8A5A2A", None)]),
    ],
    "glass_scarab": [
        ("body", None, (0, 19, 0), (0, 0, 0), [(-5, -4, -6, 10, 6, 12, "F2A63ACC", None), (-3, -1, -9, 6, 4, 4, "5A3A1A", "eyes")]),
        ("shell", "body", (0, -4, 0), (0, 0, 0), [(-5.5, -1.5, -6, 11, 2, 12, "F5C542CC", None)]),
        ("leg1", None, (-5, 21, -3), (0, 0, 0.6), [(-4, 0, -0.5, 4, 1, 1, "3A2A1A", None)]),
        ("leg2", None, (-5, 21, 1), (0, 0, 0.6), [(-4, 0, -0.5, 4, 1, 1, "3A2A1A", None)]),
        ("leg3", None, (5, 21, -3), (0, 0, -0.6), [(0, 0, -0.5, 4, 1, 1, "3A2A1A", None)]),
        ("leg4", None, (5, 21, 1), (0, 0, -0.6), [(0, 0, -0.5, 4, 1, 1, "3A2A1A", None)]),
    ],
    "sun_mote": [
        ("orb", None, (0, 12, 0), (0, 0, 0), [(-5, -5, -5, 10, 10, 10, "FFB020", "glow_eyes")]),
        ("ring1", None, (0, 12, 0), (0, 0, 0), [(-7, -0.5, -7, 14, 1, 1, "C0E8FF", None), (-7, -0.5, 6, 14, 1, 1, "C0E8FF", None),
                                                (-7, -0.5, -6, 1, 1, 12, "C0E8FF", None), (6, -0.5, -6, 1, 1, 12, "C0E8FF", None)]),
        ("ring2", None, (0, 12, 0), (1.57, 0, 0), [(-6, -0.5, -6, 12, 1, 1, "F2A63AAA", None), (-6, -0.5, 5, 12, 1, 1, "F2A63AAA", None),
                                                   (-6, -0.5, -5, 1, 1, 10, "F2A63AAA", None), (5, -0.5, -5, 1, 1, 10, "F2A63AAA", None)]),
    ],
}


def pack(model):
    """Shelf-pack every box's UV block; returns (texture size, list of (u, v) per box in order)."""
    boxes = [b for p in model for b in p[4]]
    sizes = [(int(math.ceil(2 * (b[3] + b[5]))), int(math.ceil(b[4] + b[5]))) for b in boxes]
    for W in (64, 128, 256, 512):
        x = y = rowh = 0
        out = []
        ok = True
        for w, h in sizes:
            if x + w > W:
                x, y, rowh = 0, y + rowh, 0
            if w > W or y + h > W:
                ok = False
                break
            out.append((x, y))
            x += w
            rowh = max(rowh, h)
        if ok:
            return W, out
    raise RuntimeError("model too big")


def paint(img, u, v, box, seed):
    x0, y0, z0, w, h, d, col, detail = box
    w, h, d = int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d))
    r = random.Random(seed)
    c = hexc(col)

    def fill(fx, fy, fw, fh, f):
        for yy in range(fy, fy + fh):
            for xx in range(fx, fx + fw):
                k = f * (0.9 + r.random() * 0.2)
                img.putpixel((xx, yy), (min(255, int(c[0] * k)), min(255, int(c[1] * k)), min(255, int(c[2] * k)), c[3]))
    fill(u + d, v, w, d, 1.12)          # top
    fill(u + d + w, v, w, d, 0.7)       # bottom
    fill(u, v + d, d, h, 0.88)          # side
    fill(u + d, v + d, w, h, 1.0)       # front
    fill(u + d + w, v + d, d, h, 0.88)  # side
    fill(u + 2 * d + w, v + d, w, h, 0.8)  # back
    fx, fy = u + d, v + d
    if detail in ("eyes", "glow_eyes") and w >= 6 and h >= 4:
        ec = (255, 255, 240, 255) if detail == "glow_eyes" else (20, 16, 24, 255)
        ey = fy + max(1, h // 3)
        for ex in (fx + w // 4, fx + w - w // 4 - 1):
            img.putpixel((ex, ey), ec)
            if w >= 12:
                img.putpixel((ex + (1 if ex < fx + w // 2 else -1), ey), ec)
    if detail == "sleepy":
        for ex in (fx + 4, fx + 10):
            for k in range(3):
                img.putpixel((ex + k, fy + 7), (60, 60, 80, 255))
        img.putpixel((fx + 7, fy + 11), (240, 150, 170, 255))
        img.putpixel((fx + 8, fy + 11), (240, 150, 170, 255))
    if detail == "lens":
        cx, cy = fx + w // 2, fy + h // 2
        for yy in range(fy + 1, fy + h - 1):
            for xx in range(fx + 1, fx + w - 1):
                dd = (xx - cx + 0.5) ** 2 + (yy - cy + 0.5) ** 2
                if dd < (min(w, h) / 2 - 1) ** 2:
                    img.putpixel((xx, yy), (255, 255, 230, 255) if dd < 6 else (255, 236, 150, 255))
    if detail == "window":
        for yy in range(fy + 4, fy + h - 4):
            for xx in range(fx + 5, fx + w - 5):
                img.putpixel((xx, yy), (150, 220, 240, 255) if (xx + yy) % 5 else (90, 70, 40, 255))
    if detail in ("maw", "teeth"):
        for xx in range(fx + 1, fx + w - 1, 2):
            img.putpixel((xx, fy + h - 2 if detail == "maw" else fy), (250, 245, 230, 255))
        if detail == "maw":
            for yy in range(fy + h // 2, fy + h - 2):
                for xx in range(fx + 3, fx + w - 3):
                    img.putpixel((xx, yy), (255, 120, 40, 255))
    if detail == "plates":
        for yy in range(v, v + d + h):
            for xx in range(u, u + 2 * (w + d)):
                if (xx // 5 + yy // 5) % 2 == 0:
                    p = img.getpixel((xx, yy))
                    img.putpixel((xx, yy), (int(p[0] * 0.85), int(p[1] * 0.85), int(p[2] * 0.85), p[3]))
    if detail == "kite":
        for i in range(w):
            img.putpixel((fx + i, fy + i if i < h else fy), (255, 255, 255, 255))
    if detail == "whale":
        for ex in (fx + 4, fx + w - 5):
            img.putpixel((ex, fy + h // 2), (20, 20, 40, 255))
        for xx in range(fx + 6, fx + w - 6):
            img.putpixel((xx, fy + h - 5), (60, 80, 120, 255))


def fmt(v):
    return ("%.4f" % v).rstrip("0").rstrip(".") + "F"


def main():
    java = ["package dev.elsewhere.petalwind_glasswaste.client;", "",
            "import net.minecraft.client.model.geom.PartPose;", "import net.minecraft.client.model.geom.builders.CubeListBuilder;",
            "import net.minecraft.client.model.geom.builders.LayerDefinition;", "import net.minecraft.client.model.geom.builders.MeshDefinition;",
            "import net.minecraft.client.model.geom.builders.PartDefinition;", "",
            "/** GENERATED by tools/gen_models.py (geometry + matching textures). Do not edit by hand. */",
            "public final class GeneratedModels {", "\tprivate GeneratedModels() {}", ""]
    os.makedirs(TEX, exist_ok=True)
    for name, model in MODELS.items():
        W, uvs = pack(model)
        img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        java.append("\tpublic static LayerDefinition %s() {" % "".join(w.capitalize() if i else w for i, w in enumerate(name.split("_"))))
        java.append("\t\tMeshDefinition mesh = new MeshDefinition();")
        java.append("\t\tPartDefinition root = mesh.getRoot();")
        k = 0
        for (pname, parent, pivot, rot, boxes) in model:
            cl = "CubeListBuilder.create()"
            for b in boxes:
                u, v = uvs[k]
                paint(img, u, v, b, k * 31 + len(name))
                cl += ".texOffs(%d, %d).addBox(%s, %s, %s, %s, %s, %s)" % (u, v, fmt(b[0]), fmt(b[1]), fmt(b[2]), fmt(b[3]), fmt(b[4]), fmt(b[5]))
                k += 1
            pv = "PartPose.offsetAndRotation(%s, %s, %s, %s, %s, %s)" % tuple(fmt(x) for x in (pivot + rot))
            java.append("\t\tPartDefinition %s = %s.addOrReplaceChild(\"%s\", %s, %s);" % ("p_" + pname, "root" if parent is None else "p_" + parent,
                                                                                          pname, cl, pv))
        java.append("\t\treturn LayerDefinition.create(mesh, %d, %d);" % (W, W))
        java.append("\t}")
        java.append("")
        img.save(os.path.join(TEX, name + ".png"))
    java.append("}")
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write("\n".join(java) + "\n")
    print("models:", len(MODELS))


if __name__ == "__main__":
    main()
