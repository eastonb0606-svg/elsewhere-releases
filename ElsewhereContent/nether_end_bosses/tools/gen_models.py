"""Generates the boss box models (Java LayerDefinitions) and their textures.
Run: S/venv/bin/python tools/gen_models.py   (needs Pillow)."""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from modelgen import Cube as C, Part as P, pack, paint, java, noise_mat, stripes_mat, rect, hexc, save

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src")
TEX = os.path.join(SRC, "main", "resources", "assets", "elsewhere", "textures", "entity")
JAVA = os.path.join(SRC, "client", "java")

def emit(boss, cls, parts, tw, th, materials, decorators, variants=None, doc=""):
    pack(parts, tw, th)
    pkg = "dev.elsewhere.boss.%s.client" % boss
    path = os.path.join(JAVA, *pkg.split("."), cls + ".java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(java(pkg, cls, parts, tw, th, doc))
    base = cls.replace("ModelLayer", "").lower()
    save(paint(parts, tw, th, materials, decorators), os.path.join(TEX, boss, base + ".png"))
    for vname, over in (variants or {}).items():
        mats = dict(materials)
        mats.update(over)
        save(paint(parts, tw, th, mats, decorators), os.path.join(TEX, boss, base + "_" + vname + ".png"))
    print("generated", cls)

# ------------------------------------------------------------------------------------------ Solaxis

def eyes(color, glow, y0, y1, xs):
    def deco(tile, face, rng):
        w, h = tile.size
        for (x0, x1) in xs:
            rect(tile, x0 - 1, y0 - 1, x1 + 1, y1 + 1, hexc(glow))
            rect(tile, x0, y0, x1, y1, hexc(color))
    return deco

def solaxis():
    rod = C(-1.5, -7, -1.5, 3, 14, 3, "rod", name="rod")
    spike = C(-9, -15, -9, 3, 5, 3, "magma", name="spike")
    mid = C(-1.5, -16, -9.5, 3, 6, 3, "magma", name="mid")
    parts = [
        P("head", "root", (0, -16, 0), (0, 0, 0), [C(-8, -8, -8, 16, 16, 16, "molten", faces={"north": "eyes"})]),
        P("crown", "head", (0, 0, 0), (0, 0, 0), [
            C(-9, -10, -9, 18, 3, 18, "magma"), spike,
            C(6, -15, -9, 3, 5, 3, "magma", share="spike"), C(-9, -15, 6, 3, 5, 3, "magma", share="spike"),
            C(6, -15, 6, 3, 5, 3, "magma", share="spike"), mid,
            C(-1.5, -16, 6.5, 3, 6, 3, "magma", share="mid"), C(-9.5, -16, -1.5, 3, 6, 3, "magma", share="mid"),
            C(6.5, -16, -1.5, 3, 6, 3, "magma", share="mid")]),
        P("core", "root", (0, 0, 0), (0, 0, 0), [C(-6, -8, -5, 12, 12, 10, "core", faces={"north": "core_glow"})]),
        P("tail1", "root", (0, 6, 0), (0, 0, 0), [C(-4, -2, -4, 8, 6, 8, "flame")]),
        P("tail2", "tail1", (0, 6, 0), (0, 0, 0), [C(-2.5, -2, -2.5, 5, 6, 5, "flame")]),
        P("tail3", "tail2", (0, 5, 0), (0, 0, 0), [C(-1.5, -1, -1.5, 3, 5, 3, "flame")]),
    ]
    for i in range(9):
        parts.append(P("rod%d" % i, "root", (0, 0, 0), (0, 0, 0), [rod if i == 0 else C(-1.5, -7, -1.5, 3, 14, 3, "rod", share="rod")]))
    mats = {
        "molten": noise_mat(["ffd23f", "ffe066", "ffb627", "fff1a8"], [4, 3, 2, 1], border="e08a1e"),
        "magma": noise_mat(["5a1608", "8a2a0c", "ff7b1c", "3a0e05"], [3, 3, 1, 2]),
        "core": noise_mat(["f08a3c", "e07a30", "ffa556"], [3, 2, 2], border="b8521e"),
        "flame": noise_mat(["ff8a1c", "ffb02e", "ff5a10", "ffd24a"], [3, 2, 2, 1], vgrad=("ffd24a", "c03a08", 0.5)),
        "rod": stripes_mat("ffd84a", "ffbf2a", 2, jitter=["fff0a0", "e89a1a"]),
    }
    def core_glow(tile, face, rng):
        w, h = tile.size
        rect(tile, 3, 3, w - 3, h - 3, hexc("ffc070"))
        rect(tile, 4, 4, w - 4, h - 4, hexc("fff0b0"))
    deco = {"eyes": eyes("5c0d06", "c2410f", 6, 9, [(3, 6), (10, 13)]), "core_glow": core_glow}
    emit("solaxis", "SolaxisModelLayer", parts, 128, 128, mats, deco,
         variants={"charged": {"rod": stripes_mat("fffdf2", "fff1b8", 2, jitter=["ffffff"])}},
         doc="Solaxis, the Ninefold Pyre: molten head with a magma crown, core plate, flame tail, 9 rods.")

def face_deco(spec):
    def deco(tile, face, rng):
        for (x0, y0, x1, y1, col) in spec:
            rect(tile, x0, y0, x1, y1, hexc(col))
    return deco

def mournmaw():
    parts = [P("body", "root", (0, 6, 0), (0, 0, 0), [C(-8, -8, -8, 16, 16, 16, "ghast", faces={"north": "sadface"})]),
             P("crown", "body", (0, 0, 0), (0, 0, 0), [C(-6, -11, -2, 2, 3, 2, "bone", name="bone"), C(-1, -12, -2, 2, 4, 2, "bone", name="bone2"),
                                                     C(4, -11, -2, 2, 3, 2, "bone", share="bone"), C(-7, -9, -7, 14, 1, 14, "bone")])]
    for i in range(9):
        x = -5 + (i % 3) * 5
        z = -5 + (i // 3) * 5
        cubes = [C(-1, 0, -1, 2, 10, 2, "tentacle", name="tent" if i == 0 else None, share=None if i == 0 else "tent")]
        if i in (0, 2, 6, 8):
            cubes.append(C(-1.5, 10, -1.5, 3, 3, 3, "lantern", name="lantern" if i == 0 else None, share=None if i == 0 else "lantern"))
        parts.append(P("tentacle%d" % i, "body", (x, 8, z), (0, 0, 0), cubes))
    mats = {"ghast": noise_mat(["b8dfe6", "c8ecf0", "a6ccd6", "d8f4f6"], [3, 3, 2, 1], border="8fb6c0"),
            "bone": noise_mat(["e8e2cc", "d8d0b4", "f2eedc"]),
            "tentacle": noise_mat(["a6ccd6", "98bec8", "b8dfe6"], vgrad=("c8ecf0", "6f98a4", 0.4)),
            "lantern": noise_mat(["7ff6ff", "5fd8e8", "c8ffff"], border="2a3a44")}
    sad = face_deco([(2, 4, 6, 5, "1a2a33"), (3, 5, 6, 6, "1a2a33"), (10, 4, 14, 5, "1a2a33"), (10, 5, 13, 6, "1a2a33"),
                     (3, 6, 5, 13, "7ff6ff"), (11, 6, 13, 13, "7ff6ff"), (6, 9, 10, 12, "1a2a33")])
    emit("mournmaw", "MournmawModelLayer", parts, 128, 64, mats, {"sadface": sad},
         variants={"wail": {"ghast": noise_mat(["d8f4f6", "e8fbff", "c8ecf0"], border="8fb6c0")}},
         doc="Mournmaw, the Grieving Ghast: weeping mask face, bone crown, nine tentacles with soul lanterns.")

def ossyra():
    frag = C(-1.5, -0.5, -1.5, 3, 1, 3, "shell", name="frag")
    rod = C(-0.5, -5, -0.5, 1, 5, 1, "rod", name="rod")
    parts = [P("base", "root", (0, 24, 0), (0, 0, 0), [C(-8, -8, -8, 16, 8, 16, "shell")]),
             P("lid", "root", (0, 16, 0), (0, 0, 0), [C(-8, -8, -8, 16, 8, 16, "shell"), rod,
                C(-6, -13, -6, 1, 5, 1, "rod", share="rod"), C(5, -13, -6, 1, 5, 1, "rod", share="rod"), C(-6, -13, 5, 1, 5, 1, "rod", share="rod"), C(5, -13, 5, 1, 5, 1, "rod", share="rod")]),
             P("pearl", "root", (0, 17, 0), (0, 0, 0), [C(-4, -4, -4, 8, 8, 8, "pearl", faces={"north": "pearlface"})])]
    for i in range(4):
        parts.append(P("frag%d" % i, "root", (0, 12, 0), (0, 0, 0), [frag if i == 0 else C(-1.5, -0.5, -1.5, 3, 1, 3, "shell", share="frag")]))
    mats = {"shell": noise_mat(["a678c8", "9a6ab8", "b48ad2", "8a5aa8"], [3, 3, 2, 1], border="6a4288"),
            "pearl": noise_mat(["fff7c8", "ffefa0", "fffbe0"]),
            "rod": noise_mat(["ffffff", "f2e9ff"])}
    emit("ossyra", "OssyraModelLayer", parts, 128, 64, mats, {"pearlface": face_deco([(1, 2, 3, 4, "b060d0"), (5, 2, 7, 4, "b060d0"), (3, 6, 5, 7, "8a3aa8")])},
         doc="Ossyra, Mother of Shells: purpur shell base and lid with an end-rod crown, the pearl core with a face, orbiting shell fragments.")

def aphelion():
    parts = [P("skull", "root", (0, 18, 0), (0, 0, 0), [C(-8, -6, -10, 16, 9, 20, "obsidian", faces={"north": "eyes"})]),
             P("jaw", "skull", (0, 3, 8), (0, 0, 0), [C(-7, 0, -17, 14, 3, 17, "obsidian")]),
             P("horn_l", "skull", (-6, -6, 6), (-35, 0, -15), [C(-1, -8, -1, 2, 8, 2, "horn", name="horn")]),
             P("horn_r", "skull", (6, -6, 6), (-35, 0, 15), [C(-1, -8, -1, 2, 8, 2, "horn", share="horn")]),
             P("fin", "skull", (0, -6, 0), (0, 0, 0), [C(-1, -3, -4, 2, 3, 10, "amethyst")])]
    mats = {"obsidian": noise_mat(["140c1c", "1f1229", "2a1a3a", "5a2a8a"], [4, 3, 2, 1]),
            "horn": noise_mat(["e8d8ff", "c9a2ff"]),
            "amethyst": noise_mat(["a050ff", "c080ff", "7a30d0"])}
    emit("aphelion", "AphelionModelLayer", parts, 128, 64, mats, {"eyes": face_deco([(2, 2, 5, 4, "e0a0ff"), (11, 2, 14, 4, "e0a0ff")])},
         doc="Aphelion's head: obsidian skull and jaw, amethyst fin, pale horns, violet eyes.")
    seg = [P("segment", "root", (0, 18, 0), (0, 0, 0), [C(-6, -6, -6, 12, 12, 12, "scales"), C(-1, -9, -3, 2, 3, 6, "amethyst")])]
    mats2 = {"scales": noise_mat(["140c1c", "1f1229", "2a1a3a", "7a3ab8"], [4, 3, 2, 1], border="3a1a5a"), "amethyst": mats["amethyst"]}
    emit("aphelion", "AphelionSegmentModelLayer", seg, 64, 64, mats2, {}, doc="One of Aphelion's 21 body segments.")

if __name__ == "__main__":
    which = sys.argv[1:] or ["solaxis", "mournmaw", "ossyra", "aphelion"]
    for w in which:
        globals()[w]()
