"""3.0 B4 phase-aura textures (s-p4-rivalanims): per kingdom a glyph sheet on the 64x64 player-skin layout (eyes render type:
bright marks on transparent) and a 64x64 tileable energy-swirl. Run: python3 tools/gen_aura.py (writes into src/main/resources)."""
import math, os, random
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/elsewhere/textures/entity/duelist_aura")
os.makedirs(OUT, exist_ok=True)

def rgba(h, a=255):
    h = h.lstrip("#"); return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

# base-layer faces (x0, y0, w, h) of the 64x64 player skin (wide arms)
FACE = {
    "head_front": (8, 8, 8, 8), "head_right": (0, 8, 8, 8), "head_left": (16, 8, 8, 8), "head_back": (24, 8, 8, 8),
    "body_front": (20, 20, 8, 12), "body_back": (32, 20, 8, 12), "body_right": (16, 20, 4, 12), "body_left": (28, 20, 4, 12),
    "rarm_front": (44, 20, 4, 12), "rarm_out": (40, 20, 4, 12), "rarm_in": (48, 20, 4, 12), "rarm_back": (52, 20, 4, 12),
    "larm_front": (36, 52, 4, 12), "larm_in": (32, 52, 4, 12), "larm_out": (40, 52, 4, 12), "larm_back": (44, 52, 4, 12),
    "rleg_front": (4, 20, 4, 12), "rleg_out": (0, 20, 4, 12), "rleg_in": (8, 20, 4, 12), "rleg_back": (12, 20, 4, 12),
    "lleg_front": (20, 52, 4, 12), "lleg_in": (16, 52, 4, 12), "lleg_out": (24, 52, 4, 12), "lleg_back": (28, 52, 4, 12),
}
ARMS = ["rarm_front", "rarm_out", "rarm_in", "rarm_back", "larm_front", "larm_in", "larm_out", "larm_back"]
LEGS = ["rleg_front", "rleg_out", "rleg_in", "rleg_back", "lleg_front", "lleg_in", "lleg_out", "lleg_back"]
HEAD = ["head_front", "head_right", "head_left", "head_back"]
BODY = ["body_front", "body_right", "body_back", "body_left"]

class Sheet:
    def __init__(self): self.im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    def px(self, face, x, y, c):
        fx, fy, w, h = FACE[face]
        if 0 <= x < w and 0 <= y < h: self.im.putpixel((fx + x, fy + y), c)
    def row(self, faces, y, c):
        for f in faces:
            for x in range(FACE[f][2]): self.px(f, x, y, c)
    def pattern(self, face, rows, palette, ox=0, oy=0):
        for j, line in enumerate(rows):
            for i, ch in enumerate(line):
                if ch in palette: self.px(face, ox + i, oy + j, palette[ch])
    def save(self, name): self.im.save(os.path.join(OUT, name))

def glyphs_hrafnholt():
    s = Sheet(); C = rgba("#E8FAFF"); B = rgba("#8FD8FF"); D = rgba("#4FA6E0", 200)
    pal = {"#": C, "+": B, ":": D}
    # algiz (protection) stave on the chest, hagall on the back
    s.pattern("body_front", ["+..#..+", ".+.#.+.", "..+#+..", "...#...", "...#...", "...#...", "...#...", "..+#+..", "...#...", ".......", "..+.+..", "...+..."], pal, 0, 0)
    s.pattern("body_back", ["...#...", "+..#..+", ".+.#.+.", "..+#+..", "...#...", "..+#+..", ".+.#.+.", "+..#..+", "...#...", "...#...", ".......", "...+..."], pal, 0, 0)
    # forearm runes: isa / naudiz / tiwaz ticks down the lower arms
    for f, glyph in zip(ARMS, ["#+#", "+#+", "#.#", "+#+", "#+#", "+#+", "#.#", "+#+"]):
        s.pattern(f, [".#..", ".#+.", ".#..", "+#..", ".#..", "....", glyph[0] + glyph[1] + glyph[2] + "."], pal, 0, 5)
    # frost band across the brow and a shin band of ticks
    s.row(HEAD, 2, D)
    for f in HEAD: s.px(f, 3, 2, C); s.px(f, 4, 2, B)
    for f in LEGS: s.pattern(f, ["+.+.", ".#.#", "+.+."], pal, 0, 6)
    s.save("hrafnholt_glyphs.png")

def glyphs_azmar():
    s = Sheet(); G = rgba("#FFF2B0"); Y = rgba("#FFD15A"); O = rgba("#E89A2A", 210); L = rgba("#4F8BFF")
    pal = {"#": G, "+": Y, "o": O, "l": L}
    # the sun disk with rays on the chest, the eye (wedjat) below it
    s.pattern("body_front", ["o..+..o", ".o.+.o.", "..###..", "+##l##+", "..###..", ".o.+.o.", "o..+..o", ".......", ".+++++.", "+.ll#.+", ".+++++.", "...+..."], pal, 0, 0)
    s.pattern("body_back", ["...+...", "..+#+..", ".+#l#+.", "..+#+..", "...+...", ".......", "...o...", "...o...", "...o...", "..ooo..", ".......", "......."], pal, 0, 0)
    # two gold armbands and a gold cuff on every arm
    s.row(ARMS, 1, Y); s.row(ARMS, 2, O); s.row(ARMS, 9, Y); s.row(ARMS, 10, G)
    for f in ARMS: s.px(f, 1, 1, L)
    # circlet with the uraeus on the brow, knee bands
    s.row(HEAD, 1, Y)
    s.pattern("head_front", ["...##...", "..+##+..", "...l....".replace(".", ".")], pal, 0, 0)
    s.row(LEGS, 5, Y); s.row(LEGS, 11, O)
    s.save("azmar_glyphs.png")

def glyphs_mirewick():
    s = Sheet(); R = rgba("#FF4F78"); C = rgba("#D8325A", 220); P = rgba("#A764FF"); Gd = rgba("#FFD36A"); Gl = rgba("#FFF0B8")
    pal = {"r": R, "c": C, "p": P, "g": Gd, "#": Gl}
    # crimson ledger sash shoulder to hip, gold coin seal over the heart
    s.pattern("body_front", ["r......", ".r.....", "..r.ggg", "...rg#g", "....gpg", ".....r.", "......r", "c.c.c.c", ".......", "p.p.p.p", ".......", "c.c.c.c"], pal, 0, 0)
    s.pattern("body_back", ["......r", ".....r.", "....r..", "...r...", "..r....", ".r.....", "r......", ".......", "..ggg..", "..g#g..", "..ggg..", "......."], pal, 0, 0)
    # violet veins spiralling down the arms, crimson cuff
    for i, f in enumerate(ARMS):
        for y in range(1, 10): s.px(f, (y + i) % 4, y, P if y % 3 else R)
        s.row([f], 11, C)
    # lantern-gold eyes of the debt on the brow band, crimson stripes on the legs
    s.row(HEAD, 2, C)
    s.px("head_front", 2, 2, Gl); s.px("head_front", 5, 2, Gl)
    for f in LEGS:
        for y in (2, 5, 8, 11): s.px(f, 1, y, R); s.px(f, 2, y, C)
    s.save("mirewick_glyphs.png")

def glyphs_aldermark():
    s = Sheet(); W = rgba("#FFFBEA"); A = rgba("#FFE08A"); D = rgba("#C9A64A", 200)
    pal = {"#": W, "+": A, ":": D}
    # the oath cross on chest and back, a line down each arm and leg
    for f in ("body_front", "body_back"):
        s.pattern(f, ["...+...", "...#...", ".++#++.", "...#...", "...#...", "...#...", "...#...", "...+...", ".......", "..+.+..", ".......", "......."], pal, 0, 0)
    for f in ARMS + LEGS:
        for y in range(1, 11): s.px(f, 1 if f.endswith("front") or f.endswith("back") else 2, y, A if y % 2 else W)
    s.row(HEAD, 2, D)
    s.save("aldermark_glyphs.png")

def swirl(name, base, accent, kind, seed):
    random.seed(seed)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    br, bg, bb = base; ar, ag, ab = accent
    for y in range(64):
        for x in range(64):
            if kind == "sleet":     # diagonal sleet streaks
                v = (math.sin((x + y * 2) * 2 * math.pi / 16) + 1) / 2
                v = v ** 6
            elif kind == "heat":    # rising heat-shimmer bands
                v = (math.sin(y * 2 * math.pi / 8 + math.sin(x * 2 * math.pi / 32) * 1.6) + 1) / 2
                v = v ** 5
            elif kind == "mist":    # soft lantern mist wisps
                v = (math.sin(x * 2 * math.pi / 32 + math.sin(y * 2 * math.pi / 16) * 2.2) * math.sin(y * 2 * math.pi / 32 + x * 2 * math.pi / 64) + 1) / 2
                v = v ** 4
            else:                   # aldermark: clean diagonal oath stripes
                v = (math.sin((x + y) * 2 * math.pi / 16) + 1) / 2
                v = v ** 8
            im.putpixel((x, y), (int(br * v), int(bg * v), int(bb * v), 255))
    # accent motes (wrapped, so the tile stays seamless)
    for _ in range(26):
        x, y = random.randrange(64), random.randrange(64)
        for dx, dy, f in ((0, 0, 1.0), (1, 0, 0.45), (0, 1, 0.45), (-1, 0, 0.3), (0, -1, 0.3)):
            px = ((x + dx) % 64, (y + dy) % 64)
            r, g, b, _ = im.getpixel(px)
            im.putpixel(px, (min(255, r + int(ar * f)), min(255, g + int(ag * f)), min(255, b + int(ab * f)), 255))
    im.save(os.path.join(OUT, name))

glyphs_hrafnholt(); glyphs_azmar(); glyphs_mirewick(); glyphs_aldermark()
swirl("hrafnholt_swirl.png", (150, 215, 255), (240, 250, 255), "sleet", 1)
swirl("azmar_swirl.png", (255, 196, 80), (90, 140, 255), "heat", 2)
swirl("mirewick_swirl.png", (170, 90, 255), (255, 70, 110), "mist", 3)
swirl("aldermark_swirl.png", (255, 240, 200), (255, 215, 120), "oath", 4)
print("ok", sorted(os.listdir(OUT)))
