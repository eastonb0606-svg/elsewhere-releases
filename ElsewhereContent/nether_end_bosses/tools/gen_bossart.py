"""Boss art for Grunthar + Ashtusk, Solaxis and Mournmaw, translated from the hero=True boxes of
Ideas/art/src/bosses_art.py. Emits Java LayerDefinitions into dev.elsewhere.bossart.<id> and painted textures
(+ emissive layers) into assets/elsewhere/textures/entity/boss/. Run: S/venv/bin/python tools/gen_bossart.py"""
import os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from modelgen import Cube as C, Part as P, pack, paint, java, noise_mat, stripes_mat, rect, hexc, save
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "src")
TEX = os.path.join(SRC, "main", "resources", "assets", "elsewhere", "textures", "entity", "boss")
JAVA = os.path.join(SRC, "client", "java")

def clear_mat(tile, face, rng):
    pass

def emit(boss, cls, parts, tw, th, mats, decos, emis_mats=(), emis_decos=(), variants=None, doc=""):
    pack(parts, tw, th)
    pkg = "dev.elsewhere.bossart.%s" % boss
    path = os.path.join(JAVA, *pkg.split("."), cls + ".java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "w").write(java(pkg, cls, parts, tw, th, doc))
    name = cls.replace("ModelLayer", "").lower()
    save(paint(parts, tw, th, mats, decos), os.path.join(TEX, name + ".png"))
    em = {k: (v if k in emis_mats else clear_mat) for k, v in mats.items()}
    ed = {k: (v if k in emis_decos else (lambda t, f, r: None)) for k, v in decos.items()}
    save(paint(parts, tw, th, em, ed), os.path.join(TEX, name + "_emissive.png"))
    for vname, over in (variants or {}).items():
        m2 = dict(mats)
        m2.update(over)
        save(paint(parts, tw, th, m2, decos), os.path.join(TEX, name + "_" + vname + ".png"))
    print("generated", cls)

def art_cube(c, s, pivot, k, mat, **kw):
    """Art box (centre, size; y up, +z front) -> model cube local to a part pivot (y down, front = -z)."""
    w, h, d = (max(1, int(round(v * k))) for v in s)
    x0 = (c[0] - pivot[0]) * k - w / 2.0
    y0 = -(c[1] - pivot[1]) * k - h / 2.0
    z0 = -(c[2] - pivot[2]) * k - d / 2.0
    return C(round(x0, 2), round(y0, 2), round(z0, 2), w, h, d, mat, **kw)

def art_pivot(p, parent, k, ground=0.0, root=False):
    if root:
        return (p[0] * k, 24 - (p[1] - ground) * k, -p[2] * k)
    return ((p[0] - parent[0]) * k, -(p[1] - parent[1]) * k, -(p[2] - parent[2]) * k)

def rot(r):
    return (r[0], -r[1], -r[2])

def deco_rects(spec):
    def deco(tile, face, rng):
        for (x0, y0, x1, y1, col) in spec:
            rect(tile, int(x0), int(y0), int(x1), int(y1), hexc(col))
    return deco

# ------------------------------------------------------------------------------------------- Ashtusk
def ashtusk():
    k = 6.0
    body_p, head_p = (0, 3.4, -0.5), (0, 3.0, 3.2)
    parts = [P("body", "root", art_pivot(body_p, None, k, root=True), (0, 0, 0), [
        art_cube((0, 3.4, -0.5), (4.6, 3.8, 7.0), body_p, k, "hide"),
        art_cube((0, 5.6, -1.5), (3.8, 1.4, 5.0), body_p, k, "mane"),
        art_cube((0, 5.5, -3), (5.0, 0.6, 1.4), body_p, k, "band", name="band"),
        art_cube((0, 5.5, -1), (5.0, 0.6, 1.4), body_p, k, "band", share="band"),
        art_cube((0, 5.5, 1), (5.0, 0.6, 1.4), body_p, k, "band", share="band")])]
    for name, sx, zz in (("leg_fr", -1, 2), ("leg_fl", 1, 2), ("leg_br", -1, -3), ("leg_bl", 1, -3)):
        lp = (sx * 1.5, 2.3, zz)
        parts.append(P(name, "root", art_pivot(lp, None, k, root=True), (0, 0, 0),
                       [art_cube((sx * 1.5, 1.0, zz), (1.5, 2.6, 1.5), lp, k, "leg", name="leg" if name == "leg_fr" else None, share=None if name == "leg_fr" else "leg")]))
    o = (0, 0, 0)
    parts.append(P("head", "body", art_pivot(head_p, body_p, k), rot((18, 0, 0)), [
        art_cube((0, 0, 1.6), (3.8, 3.0, 3.6), o, k, "hide", faces={"north": "eyes"}),
        art_cube((0, -0.4, 3.6), (2.4, 1.6, 0.6), o, k, "snout", faces={"north": "nostrils"}),
        art_cube((0, 1.6, 1.0), (4.2, 0.6, 2.4), o, k, "plate")]))
    for sx, nm in ((-1, "tusk_r"), (1, "tusk_l")):
        tp = (sx * 1.6, 0.4, 3.4)
        parts.append(P(nm, "head", art_pivot(tp, o, k), rot((-30, 0, sx * 15)),
                       [art_cube((0, 1.0, 0), (0.45, 2.4, 0.45), (0, 0, 0), k, "tusk", name="tusk" if sx < 0 else None, share=None if sx < 0 else "tusk")]))
    mats = {"hide": noise_mat(["8a5a4a", "7a4a3c", "9a6a54", "6a3a2e"], [3, 3, 2, 2], vgrad=("9a6a54", "5a3226", 0.35)),
            "mane": noise_mat(["d8c098", "c8b088", "e8d4ac", "b09870"], [3, 3, 1, 1]),
            "band": stripes_mat("2a2228", "c89a20", 3, horizontal=False, jitter=["3a2f30", "e0b030"]),
            "leg": noise_mat(["7a4a3c", "6a3a2e", "8a5a4a"], vgrad=("8a5a4a", "3a2018", 0.5)),
            "snout": noise_mat(["c88a7a", "b87a6a", "d89a8a"]),
            "plate": noise_mat(["2a2228", "3a2f30", "c89a20"], [4, 3, 1], border="1a1418"),
            "tusk": noise_mat(["f0e8d0", "e0d8c0", "fff8e8"])}
    eyes = deco_rects([(2, 4, 6, 7, "ff5020"), (17, 4, 21, 7, "ff5020")])
    nostrils = deco_rects([(3, 3, 5, 6, "3a1a14"), (9, 3, 11, 6, "3a1a14")])
    emit("grunthar", "AshtuskModelLayer", parts, 256, 128, mats, {"eyes": eyes, "nostrils": nostrils}, emis_decos=("eyes",),
         doc="Ashtusk, the armored war-hoglin (bosses_art.py grunthar(), hoglin group).")

# ------------------------------------------------------------------------------------------- Grunthar
def grunthar():
    head = [C(-5, -8, -4, 10, 8, 8, "skin", faces={"north": "face"}),
            C(-2, -5.9, -5.4, 4, 3, 2, "snout", faces={"north": "nostrils"}),
            C(-5.5, -9, -4.5, 11, 1, 9, "gold"),
            C(-4.5, -11.5, -0.5, 1, 3, 1, "crown", name="spike"), C(-2.5, -11.5, -0.5, 1, 3, 1, "crown", share="spike"),
            C(1.5, -11.5, -0.5, 1, 3, 1, "crown", share="spike"), C(3.5, -11.5, -0.5, 1, 3, 1, "crown", share="spike"),
            C(-0.5, -13, -0.5, 1, 4, 1, "crown")]
    parts = [P("body", "root", (0, 0, 0), (0, 0, 0), [C(-4, 0, -2, 8, 12, 4, "robe", faces={"north": "sash", "south": "sash"})]),
             P("head", "root", (0, 0, 0), (0, 0, 0), head),
             P("arm_r", "root", (-6, 2, 0), (0, 0, 0), [C(-2, -2, -2, 4, 12, 4, "skin", name="arm")]),
             P("arm_l", "root", (6, 2, 0), (0, 0, 0), [C(-2, -2, -2, 4, 12, 4, "skin", share="arm")]),
             P("axe", "arm_r", (0, 9, 0), (90, 0, 0), [C(-0.5, -17, -0.5, 1, 22, 1, "handle"), C(0, -19.5, -0.5, 7, 9, 1, "blade")]),
             P("leg_r", "root", (-2, 12, 0), (0, 0, 0), [C(-2, 0, -2, 4, 12, 4, "robe_dark", name="leg")]),
             P("leg_l", "root", (2, 12, 0), (0, 0, 0), [C(-2, 0, -2, 4, 12, 4, "robe_dark", share="leg")])]
    mats = {"skin": noise_mat(["d88a8a", "c87a7a", "e89a9a", "b86a6a"], [4, 3, 2, 1]),
            "snout": noise_mat(["c87a7a", "b86a6a"]),
            "gold": noise_mat(["e0b020", "ffcc30", "c89a20"], border="a07a10"),
            "crown": noise_mat(["ffcc30", "ffe070", "e0b020"]),
            "robe": noise_mat(["3a3030", "2e2626", "4a3a3a"], vgrad=("4a3a3a", "201818", 0.3)),
            "robe_dark": noise_mat(["2e2626", "3a3030", "201818"]),
            "handle": stripes_mat("4a3020", "3a2414", 2),
            "blade": noise_mat(["ffcc30", "ffe070", "e0b020", "fff0a0"], [3, 2, 2, 1], border="a07a10")}
    face = deco_rects([(2, 3, 4, 4, "ffa060"), (6, 3, 8, 4, "ffa060"), (1, 2, 5, 3, "5a2a2a"), (5, 2, 9, 3, "5a2a2a")])
    sash = deco_rects([(0, 0, 8, 1, "c89a20"), (3, 1, 5, 12, "c89a20")])
    nostrils = deco_rects([(0, 1, 1, 2, "5a2a2a"), (3, 1, 4, 2, "5a2a2a")])
    emit("grunthar", "GruntharModelLayer", parts, 128, 64, mats, {"face": face, "sash": sash, "nostrils": nostrils},
         emis_mats=("crown", "blade"), emis_decos=(), doc="Grunthar the Gilded Tyrant (bosses_art.py grunthar(), humanoid s=1.6).")
    # eyes glow: separate tiny emissive pass on the face
    em = Image.open(os.path.join(TEX, "gruntharmodel_emissive.png") if False else os.path.join(TEX, "grunthar_emissive.png")).convert("RGBA")
    base = Image.open(os.path.join(TEX, "grunthar.png")).convert("RGBA")
    px, pe = base.load(), em.load()
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if px[x, y][:3] == (0xff, 0xa0, 0x60):
                pe[x, y] = px[x, y]
    em.save(os.path.join(TEX, "grunthar_emissive.png"))

# ------------------------------------------------------------------------------------------- Solaxis
def solaxis():
    k = 8.0
    head_p, col_p = (0, 9.2, 0), (0, 6.2, 0)
    parts = [P("head", "root", art_pivot(head_p, None, k, root=True), (0, 0, 0), [art_cube(head_p, (3.2, 3.2, 3.2), head_p, k, "molten", faces={"north": "eyes"})]),
             P("column", "root", art_pivot(col_p, None, k, root=True), (0, 0, 0), [art_cube(col_p, (1.6, 3.4, 1.6), col_p, k, "basalt", faces={f: "cracks" for f in ("north", "south", "east", "west")})])]
    for i in range(9):
        parts.append(P("rod%d" % i, "root", (0, 0, 0), (0, 0, 0), [C(-3, -13.5, -3, 6, 27, 6, "rod", name="rod" if i == 0 else None, share=None if i == 0 else "rod")]))
    mats = {"molten": noise_mat(["ffd040", "ffb020", "ffe880", "e89a10"], [4, 3, 2, 2], border="d07a10"),
            "basalt": noise_mat(["3a2a2a", "2a1e1e", "4a3434"]),
            "rod": stripes_mat("ffb020", "e89010", 3, jitter=["ffd040", "ffe880"])}
    eyes = deco_rects([(4, 9, 11, 13, "ff4000"), (15, 9, 22, 13, "ff4000"), (5, 10, 10, 12, "2a0a00"), (16, 10, 21, 12, "2a0a00")])
    def cracks(tile, face, rng):
        w, h = tile.size
        for y in range(0, h, 4):
            x = rng.randrange(w)
            for yy in range(y, min(h, y + 3)):
                tile.putpixel((min(w - 1, max(0, x + rng.choice((-1, 0, 1)))), yy), hexc("ff8020"))
    emit("solaxis", "SolaxisArtModelLayer", parts, 256, 128, mats, {"eyes": eyes, "cracks": cracks},
         emis_mats=("molten",), emis_decos=("cracks", "eyes"),
         variants={"charged": {"rod": stripes_mat("fffbe8", "fff2a0", 3, jitter=["ffffff"])}},
         doc="Solaxis, the Ninefold Pyre (bosses_art.py solaxis()): molten head, basalt column, nine rods in three rings.")

# ------------------------------------------------------------------------------------------- Mournmaw
def mournmaw():
    k = 4.0
    ground = 1.5
    body_p = (0, 6, 0)
    parts = [P("body", "root", art_pivot(body_p, None, k, ground, root=True), (0, 0, 0), [art_cube(body_p, (9, 9, 9), body_p, k, "ghast", faces={"north": "face"})])]
    parts.append(P("crown", "body", (0, 0, 0), (0, 0, 0), [
        art_cube((-3, 11.2, 0), (0.9, 2, 0.9), body_p, k, "bone", name="bone"), art_cube((3, 11.2, 0), (0.9, 2, 0.9), body_p, k, "bone", share="bone"),
        art_cube((-1, 12.0, 0), (0.9, 3.6, 0.9), body_p, k, "bone", name="bone2"), art_cube((1, 12.0, 0), (0.9, 3.6, 0.9), body_p, k, "bone", share="bone2")]))
    rng = random.Random(5)
    for i in range(9):
        x, z = -3.4 + (i % 3) * 3.4, -3 + (i // 3) * 3
        L = rng.uniform(4, 8)
        rx, rz = rng.uniform(-14, 14), rng.uniform(-14, 14)
        tp = (x, 1.5, z)
        parts.append(P("tentacle%d" % i, "body", art_pivot(tp, body_p, k), rot((rx, 0, rz)), [C(-2, 0, -2, 4, int(round(L * k)), 4, "tentacle")]))
    for sx, nm in ((-1, "chain_r"), (1, "chain_l")):
        cp = (sx * 4.6, 10.0, 3)
        parts.append(P(nm, "body", art_pivot(cp, body_p, k), (0, 0, 0), [C(-0.5, 0, -0.5, 1, 26, 1, "chain", name="chain" if sx < 0 else None, share=None if sx < 0 else "chain"),
                                                                       C(-2, 26, -2, 4, 4, 4, "lantern", name="lantern" if sx < 0 else None, share=None if sx < 0 else "lantern")]))
    mats = {"ghast": noise_mat(["ece8ec", "dcd8dc", "f8f4f8", "c8c4c8"], [4, 3, 2, 1]),
            "bone": noise_mat(["e8e0c8", "d8d0b4", "f2eedc"]),
            "tentacle": noise_mat(["e0dce0", "d0ccd0", "eceaec"], vgrad=("eceaec", "a8a4a8", 0.4)),
            "chain": stripes_mat("3a4a50", "2a3438", 2),
            "lantern": noise_mat(["60f0ff", "40d8ff", "a0f8ff"], border="2a3a44")}
    # face (36 x 36): slanted brows/eyes, glowing tear streaks, mouth
    def face(tile, face_name, rng):
        for i in range(8):
            for dy in range(3):
                tile.putpixel((5 + i, 11 + dy + (i * 2) // 8), hexc("202024"))
                tile.putpixel((30 - i, 11 + dy + (i * 2) // 8), hexc("202024"))
        rect(tile, 7, 15, 11, 29, hexc("60e8ff"))
        rect(tile, 25, 15, 29, 29, hexc("60e8ff"))
        rect(tile, 11, 23, 25, 29, hexc("202024"))
    def tears(tile, face_name, rng):
        rect(tile, 7, 15, 11, 29, hexc("60e8ff"))
        rect(tile, 25, 15, 29, 29, hexc("60e8ff"))
    decos = {"face": face}
    emit("mournmaw", "MournmawArtModelLayer", parts, 256, 128, mats, decos, emis_mats=("lantern",), emis_decos=(),
         variants={"wail": {"ghast": noise_mat(["f8fcff", "e8f8ff", "dcf4fc"])}},
         doc="Mournmaw, the Grieving Ghast (bosses_art.py mournmaw()): weeping mask, bone crown, 9 tentacles, soul chains with lanterns.")
    em = Image.open(os.path.join(TEX, "mournmawart_emissive.png")).convert("RGBA")
    base = Image.open(os.path.join(TEX, "mournmawart.png")).convert("RGBA")
    px, pe = base.load(), em.load()
    for y in range(base.size[1]):
        for x in range(base.size[0]):
            if px[x, y][:3] == (0x60, 0xe8, 0xff):
                pe[x, y] = px[x, y]
    em.save(os.path.join(TEX, "mournmawart_emissive.png"))

if __name__ == "__main__":
    for w in sys.argv[1:] or ["ashtusk", "grunthar", "solaxis", "mournmaw"]:
        globals()[w]()
