#!/usr/bin/env python3
"""Townsfolk skins (s-townsfolk-art, 3.0.0): 12 roles x 3 people = 36 skins, 64x64 wide-arm player layout.

Texture ids (from _swarm/kingdom/TOWNSFOLK-IDS.md): elsewhere:textures/entity/townsfolk/<kingdom>/<role>_<n>.png,
n = 0 and 2 men, 1 a woman. Each role keeps one recognisable outfit; the three people vary skin, hair and accents.
Lore palettes: Aldermark undyed wool, browns, woad and royal blue; Hrafnholt forest green and bark brown with
iron-grey and bone (RIVALS-DECISIONS 16); Azmar linen, gold, lapis, turquoise, carnelian, kohl (RIVALS-DECISIONS 9.1);
Mirewick royal purple, crimson and gold, wide hats, oilskin, peat mud (RIVALS-DECISIONS 16).
Run: python3 tools/gen_townsfolk_skins.py [outdir]   (Pillow)
"""
import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from skinkit import *

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "src", "main", "resources", "assets", "elsewhere", "textures", "entity", "townsfolk")
ROLES = {}


def role(kingdom, rid):
    def deco(fn):
        ROLES[(kingdom, rid)] = fn
        return fn
    return deco


# ---- people -------------------------------------------------------------------------------------------------------
def person(s, tone, hair_c, style="short", beard_s=None, iris=(84, 60, 40), woman=False, brow="flat", cheeks=None,
           age=0, beard_c=None, kohl=None, mouth=None, smile=False):
    head_base(s, tone); ears(s, tone)
    hair(s, hair_c, style)
    eyes(s, iris, lash=dk(hair_c, 20) if woman and not kohl else None, kohl=kohl)
    if not kohl:
        brows(s, dk(hair_c, 18), style="thin" if woman else brow)
    nose_mouth(s, tone, mouth=mouth or ((168, 84, 74) if woman else None), smile=smile)
    if cheeks:
        s.fpx("head", "front", 1, 5, cheeks); s.fpx("head", "front", 6, 5, cheeks)
    if age:
        s.fpx("head", "front", 0, 4, dk(tone, 22)); s.fpx("head", "front", 7, 4, dk(tone, 22))
        s.fpx("head", "front", 2, 2, dk(tone, 12)); s.fpx("head", "front", 5, 2, dk(tone, 12))
    if beard_s:
        beard(s, beard_c or hair_c, beard_s, tone)
    return tone


def clear_face(s):
    """The hat layer never covers eyes, nose or mouth."""
    s.fclear("hat", "front", 1, 2, 7, 8)


LEATHER = (120, 84, 50); DLEATHER = (74, 52, 34); IRON = (108, 112, 118); BONE = (216, 208, 186); ROPE = (186, 158, 98)
OAT = (206, 192, 160); LINENW = (236, 232, 218)

# ================================================================================================================
# ALDERMARK: the bell kingdom (orderly, feudal). Undyed wool, oatmeal, browns, woad blue; royal blue and gold only on
# the guild craftsmen's best.
# ================================================================================================================
WOAD = (86, 106, 150); ROYAL = (46, 74, 150); MADDER = (150, 62, 44); RUSSET = (128, 74, 46); AGOLD = (212, 170, 60)
GREYWOOL = (150, 142, 128)


def coif(s, c):
    s.fill("hat", c, 3, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, c, 3)
    s.frect("hat", "front", 0, 2, 1, 7, c, 3); s.frect("hat", "front", 7, 2, 8, 7, c, 3)
    for f in ("right", "left", "back"):
        s.frect("hat", f, 0, 0, 8, 6, c, 3)
        s.frect("hat", f, 0, 6, 8, 7, dk(c, 22), 2)
    s.frect("hat", "front", 0, 1, 8, 2, dk(c, 16), 1)          # seam over the brow
    s.fpx("hat", "front", 0, 7, dk(c, 26)); s.fpx("hat", "front", 7, 7, dk(c, 26))   # chin ties
    s.frect("hat", "bottom", 0, 0, 1, 8, dk(c, 26)); s.frect("hat", "bottom", 7, 0, 8, 8, dk(c, 26))


def kerchief(s, c, rows=4, edge=None, knot=True):
    s.fill("hat", c, 3, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, c, 3)
    for f in ("right", "left"):
        s.frect("hat", f, 0, 0, 8, rows, c, 3)
    s.frect("hat", "back", 0, 0, 8, rows + 1, c, 3)
    if edge:
        s.frect("hat", "front", 0, 1, 8, 2, edge, 1)
        for f in ("right", "left"):
            s.frect("hat", f, 0, rows - 1, 8, rows, edge, 1)
    if knot:
        s.frect("hat", "back", 3, rows + 1, 5, rows + 3, dk(c, 16), 2)


def chaperon(s, c, cape=True, dag=3):
    """Hood with a liripipe tail and a dagged shoulder cape."""
    s.fill("hat", c, 3, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, c, 3)
    s.frect("hat", "front", 0, 2, 1, 8, c, 3); s.frect("hat", "front", 7, 2, 8, 8, c, 3)
    s.frect("hat", "front", 1, 1, 7, 2, dk(c, 18), 1)
    for f in ("right", "left", "back"):
        s.frect("hat", f, 0, 0, 8, 8, c, 3)
    for y in range(8):
        s.fpx("hat", "back", 3 + (y % 2), y, dk(c, 24))
    if cape:
        shoulder_cape(s, c, rows=3, dag=dag)


def tunic_legs(s, c, rows=3, tex=None):
    for p in LEGS:
        for r in range(rows):
            s.ring(p, r, c, 4)
        s.ring(p, rows - 1, dk(c, 16), 2)


@role("aldermark", "peasant")
def ald_peasant(s, v):
    if v == 1:
        tone = person(s, (226, 184, 150), (138, 70, 38), "braid", woman=True, iris=(60, 80, 120), cheeks=(232, 160, 140))
        kerchief(s, LINENW, rows=4, edge=(214, 208, 192))
        clear_face(s); s.fclear("hat", "back", 2, 5, 6, 8)
        weave(s, "body", MADDER, 4)
        for p in ARMS:
            weave(s, p, MADDER, 4)
        hands(s, tone, 10)
        for p in ARMS:
            s.ring(p, 9, dk(MADDER, 22), 2)
        for y in range(1, 6):                                  # laced bodice
            s.fpx("body", "front", 3 + (y % 2), y, OAT)
        s.fpx("body", "front", 3, 0, (240, 236, 222)); s.fpx("body", "front", 4, 0, (240, 236, 222))
        skirt(s, MADDER, 12, hem=dk(MADDER, 26), texfn=lambda p, c: weave(s, p, c, 4))
        belt(s, 6, OAT)
        apron(s, (226, 214, 186), top=6, legs_to=9)
        for p in LEGS:
            s.ring(p, 11, DLEATHER, 2)
            s.frect(p, "bottom", 0, 0, 4, 4, dk(DLEATHER, 20), 2)
        s.frect("rpants", "front", 0, 11, 4, 12, DLEATHER, 2); s.frect("lpants", "front", 0, 11, 4, 12, DLEATHER, 2)
        return
    if v == 0:
        tone = person(s, (214, 160, 120), (104, 70, 42), "short", beard_s="stubble", iris=(70, 92, 60))
        coif(s, LINENW); clear_face(s)
        tun, hose = WOAD, (96, 72, 50)
    else:
        tone = person(s, (232, 192, 156), (196, 160, 92), "short", beard_s="short", iris=(84, 110, 150), age=1)
        tun, hose = GREYWOOL, (110, 96, 70)
        # brown woollen hood worn down: a cowl round the neck and shoulders
        shoulder_cape(s, (112, 80, 52), rows=3)
    weave(s, "body", tun, 5)
    for p in ARMS:
        weave(s, p, tun, 5)
    forearms(s, tone, 7)
    for p in ARMS:
        s.ring(p, 6, dk(tun, 20), 2)                           # sleeve pushed up
    for p in LEGS:
        weave(s, p, hose, 5)
    tunic_legs(s, tun, 3)
    belt(s, 8, ROPE)
    s.fpx("body", "front", 2, 9, ROPE); s.fpx("body", "front", 2, 10, dk(ROPE, 24))
    vneck(s, tone, 2)
    s.frect("rleg", "front", 1, 4, 3, 6, (124, 100, 70) if v == 0 else (88, 76, 60), 2)   # knee patch
    legwraps(s, OAT if v == 0 else (176, 166, 140), dk(OAT, 44), 6, 10)
    shoes(s, DLEATHER, 10)
    if v == 2:
        s.frect("body", "left", 1, 9, 3, 11, (150, 120, 70), 2)   # seed pouch


@role("aldermark", "craftsman")
def ald_craftsman(s, v):
    if v == 0:      # the smith
        tone = person(s, (196, 140, 100), (62, 42, 28), "receding", beard_s="full", iris=(80, 56, 36), brow="stern")
        s.fpx("head", "front", 6, 2, dk(tone, 34))
        linen(s, "body", ROYAL, fold=12, every=4)
        for p in ARMS:
            linen(s, p, ROYAL, fold=12, every=4)
        forearms(s, tone, 5)
        for p in ARMS:
            s.ring(p, 4, dk(ROYAL, 24), 2)
            s.ring(p, 11, mix(tone, (60, 54, 50), 0.45), 2)
        for p in LEGS:
            weave(s, p, (72, 64, 58), 4)
        shoes(s, (52, 38, 28), 8)
        for p in LEGS:
            s.ring(p, 8, (82, 60, 42), 2)
        apron(s, (124, 84, 50), top=4, legs_to=9, bib=True, strap=(80, 56, 36))
        for y in range(4, 12):
            if y % 3 == 0:
                s.fpx("body", "front", 2 + y % 4, y, (94, 60, 36))
        belt(s, 7, (60, 42, 28), buckle=AGOLD)
        s.frect("body", "right", 1, 7, 3, 9, IRON, 2)
        s.frect("body", "right", 2, 9, 3, 12, (110, 80, 50), 1)
        vneck(s, tone, 1)
        return
    if v == 2:      # the weaver: russet doublet, blue chaperon with a dagged cape
        tone = person(s, (220, 170, 132), (176, 140, 84), "short", beard_s="mustache", iris=(70, 110, 150), smile=True)
        chaperon(s, ROYAL); clear_face(s)
        weave(s, "body", RUSSET, 4)
        for p in ARMS:
            weave(s, p, RUSSET, 4)
        hands(s, tone, 10)
        for p in ARMS:
            s.ring(p, 9, OAT, 1)
        s.frect("body", "front", 3, 0, 5, 12, dk(RUSSET, 14), 2)
        buttons(s, AGOLD, 4, range(3, 10, 2))
        belt(s, 8, DLEATHER, buckle=AGOLD)
        s.frect("body", "left", 1, 9, 3, 11, (190, 192, 196), 1); s.fpx("body", "left", 1, 9, IRON)
        s.frect("body", "right", 1, 9, 3, 11, (200, 180, 120), 2)
        for p in LEGS:
            flat(s, p, (60, 66, 92), 4)
            s.rows(p, 0, 2, RUSSET, 3)
        shoes(s, (64, 44, 30), 10)
        s.fpx("rleg", "front", 2, 10, AGOLD); s.fpx("lleg", "front", 1, 10, AGOLD)
        return
    # v == 1: the brewster / chandler: blue guild gown, white wimple, leather work apron, keys and a ladle
    tone = person(s, (218, 170, 136), (92, 60, 40), "long", woman=True, iris=(90, 70, 50), cheeks=(224, 150, 128))
    # linen veil over the hair, a blue-edged band at the brow; hair shows at the temples
    s.fill("hat", LINENW, 2, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, LINENW, 2)
    s.frect("hat", "front", 0, 1, 8, 2, ROYAL, 1)
    for f in ("right", "left"):
        s.frect("hat", f, 0, 0, 8, 7, LINENW, 2)
        s.frect("hat", f, 0, 1, 8, 2, ROYAL, 1)
        s.fclear("hat", f, 4 if f == "left" else 0, 2, 8 if f == "left" else 4, 7)
    s.frect("hat", "back", 0, 0, 8, 8, LINENW, 2)
    s.frect("hat", "back", 0, 1, 8, 2, ROYAL, 1)
    for y in range(2, 8):                                    # folds in the veil
        s.fpx("hat", "back", 2, y, (218, 214, 200)); s.fpx("hat", "back", 5, y, (218, 214, 200))
    weave(s, "body", ROYAL, 4)
    for p in ARMS:
        weave(s, p, ROYAL, 4)
    hands(s, tone, 10)
    for p in ARMS:
        s.ring(p, 9, LINENW, 1)
    skirt(s, ROYAL, 12, hem=dk(ROYAL, 26), texfn=lambda p, c: weave(s, p, c, 4))
    apron(s, (128, 88, 54), top=5, legs_to=10, strap=(84, 60, 40))
    belt(s, 7, (84, 60, 40), buckle=AGOLD)
    s.frect("body", "right", 1, 8, 2, 11, AGOLD, 1); s.fpx("body", "right", 2, 10, AGOLD)   # ring of keys
    s.frect("body", "left", 2, 8, 3, 11, (150, 110, 70), 1); s.fpx("body", "left", 1, 11, (150, 110, 70))   # ladle
    for p in LEGS:
        s.ring(p, 11, DLEATHER, 2)
        s.frect(p, "bottom", 0, 0, 4, 4, dk(DLEATHER, 20), 2)


# ================================================================================================================
# HRAFNHOLT: green-and-brown pine-forest vikings. Forest green #2E5A2C, bark brown #6B4A2B, iron grey, bone; thralls in
# undyed wadmal with iron collars; fisherfolk in green wool, oiled sealskin and rope.
# ================================================================================================================
FOREST = (46, 90, 44); FOREST_L = (62, 108, 58); BARK = (107, 74, 43); WADMAL = (150, 138, 116); WADMAL_D = (118, 106, 88)
SEALSKIN = (74, 70, 62); SEA = (70, 98, 118); BRONZE = (176, 132, 64); AMBER = (214, 150, 44)


@role("hrafnholt", "thrall")
def hraf_thrall(s, v):
    if v == 1:
        tone = person(s, (222, 186, 156), (118, 84, 54), "cropped", woman=True, iris=(80, 96, 80), cheeks=(214, 150, 130))
        kerchief(s, WADMAL_D, rows=3, knot=True)
        clear_face(s)
        weave(s, "body", WADMAL, 8)
        for p in ARMS:
            weave(s, p, WADMAL, 8)
        forearms(s, tone, 8)
        for p in ARMS:
            s.ring(p, 7, dk(WADMAL, 22), 2)
        skirt(s, WADMAL, 11, hem=dk(WADMAL, 28), texfn=lambda p, c: weave(s, p, c, 8))
        apron(s, BARK, top=5, legs_to=9)
        belt(s, 6, ROPE)
        iron_collar(s, IRON)
        barefoot(s, tone, (70, 56, 40))
        for p in LEGS:
            s.ring(p, 9, dk(tone, 10), 2)
        s.frect("lleg", "back", 1, 3, 3, 5, WADMAL_D, 3)       # mend
        return
    if v == 0:
        tone = person(s, (214, 172, 136), (92, 66, 44), "cropped", beard_s="stubble", iris=(70, 70, 64))
        cloth = WADMAL
    else:
        tone = person(s, (196, 150, 112), (60, 44, 32), "cropped", beard_s="short", iris=(90, 70, 46), age=1)
        cloth = (132, 116, 94)
        # a coarse brown hood, worn down on the shoulders
        shoulder_cape(s, BARK, rows=3)
    weave(s, "body", cloth, 8)
    for p in ARMS:
        weave(s, p, cloth, 8)
    forearms(s, tone, 6)
    for p in ARMS:
        s.ring(p, 5, dk(cloth, 24), 2)
        s.ring(p, 11, mix(tone, (90, 70, 50), 0.3), 2)        # work-dirty hands
    for p in LEGS:
        weave(s, p, dk(cloth, 30), 6)
    tunic_legs(s, cloth, 4)
    for p in LEGS:                                             # ragged hem
        for i in range(s.ring_w(p)):
            if i % 3 == 0:
                s.ringpx(p, i, 4, dk(cloth, 16))
    belt(s, 8, ROPE)
    iron_collar(s, IRON)
    s.frect("body", "front", 5, 4, 7, 6, (128, 104, 80), 2)    # patch
    legwraps(s, (128, 112, 92), (84, 70, 54), 6, 10)
    if v == 0:
        barefoot(s, tone, (70, 56, 40))
        s.rows("rleg", 9, 10, (128, 112, 92), 3); s.rows("lleg", 9, 10, (128, 112, 92), 3)
    else:
        shoes(s, (86, 64, 44), 10)


@role("hrafnholt", "fisherfolk")
def hraf_fisher(s, v):
    if v == 1:      # apron dress with oval brooches and a bead string; braids; a gutting knife
        tone = person(s, (234, 200, 172), (214, 176, 104), "braid", woman=True, iris=(76, 118, 140), cheeks=(230, 164, 140))
        for f in ("right", "left"):                            # side braids hang down by the cheeks
            s.frect("head", f, 3 if f == "left" else 4, 4, 5 if f == "left" else 6, 8, dk((214, 176, 104), 20), 2)
        # head band
        for f in SIDES:
            s.frect("hat", f, 0, 1, 8, 2, FOREST, 1)
        s.fclear("hat", "front", 1, 1, 7, 2)
        s.frect("hat", "front", 0, 1, 1, 2, FOREST); s.frect("hat", "front", 7, 1, 8, 2, FOREST)
        # linen underdress (bone) with green smokkr over it
        weave(s, "body", BONE, 4)
        for p in ARMS:
            weave(s, p, BONE, 4)
        hands(s, tone, 10)
        for p in ARMS:
            s.ring(p, 9, dk(BONE, 26), 1)
        s.frect("body", "front", 1, 2, 7, 12, FOREST, 4)
        s.frect("body", "back", 1, 2, 7, 12, FOREST, 4)
        for f in ("right", "left"):
            s.frect("body", f, 0, 2, 4, 12, FOREST, 4)
        for x in (1, 6):                                      # straps over the shoulders
            s.frect("body", "front", x, 0, x + 1, 2, FOREST, 2); s.frect("body", "back", x, 0, x + 1, 2, FOREST, 2)
        for x in (1, 5):                                      # oval brooches
            s.frect("body", "front", x, 2, x + 2, 4, BRONZE, 3)
            s.fpx("body", "front", x, 2, lt(BRONZE, 30))
        for i, x in enumerate(range(2, 6)):                    # bead string between the brooches
            s.fpx("body", "front", x, 4 + (1 if x in (3, 4) else 0), (AMBER, SEA, AMBER, SEA)[i])
        skirt(s, FOREST, 12, hem=BARK, texfn=lambda p, c: weave(s, p, c, 4))
        s.frect("body", "front", 1, 8, 7, 12, dk(FOREST, 10), 3)
        s.frect("body", "left", 1, 7, 2, 11, BONE, 1); s.fpx("body", "left", 1, 7, IRON)   # knife at the belt
        for p in LEGS:
            s.ring(p, 11, DLEATHER, 2)
            s.frect(p, "bottom", 0, 0, 4, 4, dk(DLEATHER, 20), 2)
        return
    if v == 0:
        tone = person(s, (228, 188, 156), (206, 164, 92), "long", beard_s="forked", iris=(70, 110, 140))
        knit_cap(s, (150, 146, 136), rows=3)
        clear_face(s)
        tunic = FOREST_L
    else:
        tone = person(s, (220, 168, 130), (168, 84, 40), "braid", beard_s="full", iris=(80, 110, 90), age=1)
        tunic = (88, 84, 60)                                   # weathered olive wool
        # oiled sealskin hood with a fur rim
        s.fill("hat", SEALSKIN, 4, faces=("top",))
        s.frect("hat", "front", 0, 0, 8, 2, (166, 150, 126), 4)
        s.frect("hat", "front", 0, 2, 1, 8, (166, 150, 126), 4); s.frect("hat", "front", 7, 2, 8, 8, (166, 150, 126), 4)
        for f in ("right", "left", "back"):
            s.frect("hat", f, 0, 0, 8, 7, SEALSKIN, 4)
        s.frect("hat", "back", 2, 7, 6, 8, dk(SEALSKIN, 12), 2)
        clear_face(s)
    weave(s, "body", tunic, 4)
    for p in ARMS:
        weave(s, p, tunic, 4)
    hands(s, tone, 10)
    for p in ARMS:
        s.ring(p, 9, dk(tunic, 24), 2)
    # oiled sealskin apron for gutting fish
    apron(s, SEALSKIN, top=6, legs_to=7)
    for y in range(6, 12):
        if y % 2 == 0:
            s.fpx("body", "front", 2 + (y // 2) % 3, y, lt(SEALSKIN, 26))  # oily sheen
    # coiled line over one shoulder
    diag(s, ROPE, x0=0, x1=6, rows=(0, 9), width=1)
    belt(s, 9, DLEATHER, buckle=IRON)
    for p in LEGS:
        weave(s, p, BARK, 4)
    legwraps(s, (124, 92, 60), (78, 56, 36), 5, 10)
    shoes(s, (66, 50, 36), 10)
    if v == 2:
        s.frect("body", "right", 1, 9, 3, 12, (180, 186, 190), 2)   # a hanging fish
        s.fpx("body", "right", 2, 11, (120, 130, 136))


# ================================================================================================================
# AZMAR: ancient-Egyptian epic. Gold #D4A537, lapis #1F3F8F, turquoise #2BB3A3, obsidian #16131A, linen #EDE3CC,
# carnelian #B0362B; kohl-lined eyes, wigs or shaved heads, headcloths, sandals.
# ================================================================================================================
ZGOLD = (212, 165, 55); LAPIS = (31, 63, 143); TURQ = (43, 179, 163); OBS = (22, 19, 26); LINEN = (237, 227, 204)
CARN = (176, 54, 43); SAND = (204, 182, 140); TAN = (176, 124, 88); DARK = (132, 88, 60); PALE = (196, 148, 108)
MID = (154, 104, 70)


def az_face(s, tone, iris=(40, 30, 25), woman=False, mouth=None):
    eyes(s, iris, kohl=OBS)
    s.fpx("head", "front", 3, 5, dk(tone, 25)); s.fpx("head", "front", 4, 5, dk(tone, 25))
    m = mouth or ((150, 70, 60) if woman else dk(tone, 55))
    s.fpx("head", "front", 3, 6, m); s.fpx("head", "front", 4, 6, m)


def az_head(s, tone, style, hair_c=OBS, fillet=None, woman=False):
    head_base(s, tone); ears(s, tone)
    if style == "shaved":
        for x in range(8):
            for y in range(8):
                s.fpx("head", "top", x, y, dk(tone, 6), 2)
        s.fpx("head", "top", 3, 3, lt(tone, 26)); s.fpx("head", "top", 4, 3, lt(tone, 18))   # sheen
    elif style == "wig":
        wig(s, hair_c, rows_side=7, fringe=2, fillet=fillet)
    elif style == "short_wig":
        wig(s, hair_c, rows_side=5, fringe=2, fillet=fillet)
    elif style == "long_wig":
        wig(s, hair_c, rows_side=8, fringe=2, fillet=fillet)
        for y in range(2, 8):
            s.fpx("head", "front", 0, y, hair_c, 2); s.fpx("head", "front", 7, y, hair_c, 2)
    az_face(s, tone, woman=woman)


def headcloth(s, c, stripe=None, band=None, lappets=True):
    """Khat/nemes-style cloth on the hat layer: over the top and back, side lappets down to the shoulders."""
    s.fill("hat", c, 3, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, c, 3)
    if lappets:
        s.frect("hat", "front", 0, 2, 1, 8, c, 3); s.frect("hat", "front", 7, 2, 8, 8, c, 3)
    for f in ("right", "left", "back"):
        s.frect("hat", f, 0, 0, 8, 8, c, 3)
    if stripe:
        for f in ("right", "left", "back"):
            for y in range(1, 8, 2):
                s.frect("hat", f, 0, y, 8, y + 1, stripe, 1)
        for y in range(3, 8, 2):
            s.fpx("hat", "front", 0, y, stripe); s.fpx("hat", "front", 7, y, stripe)
    if band:
        s.frect("hat", "front", 0, 1, 8, 2, band, 1)
        for f in ("right", "left", "back"):
            s.frect("hat", f, 0, 1, 8, 2, band, 1)
    s.frect("hat", "back", 2, 7, 6, 8, dk(c, 20), 2)          # the cloth gathers into a tail at the nape


def bare_torso(s, tone, nipples=False):
    s.fill("body", tone, 3)
    s.frect("body", "front", 2, 5, 6, 6, dk(tone, 10), 1) if False else None
    for x in (2, 5):
        s.fpx("body", "front", x, 4, dk(tone, 16))             # chest shadow
    s.fpx("body", "front", 3, 8, dk(tone, 14)); s.fpx("body", "front", 4, 8, dk(tone, 14))


@role("azmar", "priest")
def az_priest(s, v):
    if v == 1:      # priestess: long wig with a lotus at the brow, sheath dress, turquoise collar, sistrum-free hands
        tone = MID
        az_head(s, tone, "long_wig", fillet=ZGOLD, woman=True)
        s.fpx("head", "front", 3, 0, LINEN); s.fpx("head", "front", 4, 0, LINEN); s.fpx("head", "front", 3, 1, TURQ); s.fpx("head", "front", 4, 1, TURQ)
        linen(s, "body", LINEN, fold=8, every=3)
        for p in ARMS:
            s.fill(p, tone, 3)
        armbands(s, ZGOLD, 2); armbands(s, ZGOLD, 9); armbands(s, TURQ, 10)
        usekh(s, ZGOLD, TURQ, LAPIS, rows=4)
        skirt(s, LINEN, 12, hem=ZGOLD, texfn=lambda p, c: linen(s, p, c, fold=8, every=3), hem_row=10)
        belt(s, 6, ZGOLD); s.frect("body", "front", 3, 6, 5, 9, LAPIS, 1)   # sash ends
        sandals(s, tone, ZGOLD)
        for p in LEGS:
            s.rows(p, 11, 12, tone, 2); s.ring(p, 11, ZGOLD, 1)
        return
    tone = TAN if v == 0 else DARK
    az_head(s, tone, "shaved")
    linen(s, "body", LINEN, fold=8, every=3)
    for p in ARMS:
        s.fill(p, tone, 3)
    armbands(s, ZGOLD, 2)
    armbands(s, ZGOLD, 10)
    if v == 0:      # sem-priest: leopard pelt over the right shoulder, long white robe
        spots(s, "body", ("front",), (208, 160, 84), (70, 46, 24), region=lambda f, x, y: abs(x - (1 + y * 0.5)) < 2.6 and y < 11)
        spots(s, "body", ("back",), (208, 160, 84), (70, 46, 24), region=lambda f, x, y: abs((7 - x) - (1 + y * 0.5)) < 2.6 and y < 11)
        s.frect("body", "top", 0, 0, 3, 4, (208, 160, 84), 4)
        s.frect("body", "right", 0, 0, 4, 6, (208, 160, 84), 4)
        usekh(s, ZGOLD, LAPIS, rows=2, back=False)
        skirt(s, LINEN, 12, hem=dk(LINEN, 30), texfn=lambda p, c: linen(s, p, c, fold=8, every=3), hem_row=10)
        belt(s, 6, ZGOLD)
    else:           # lector priest: broad white sash across the chest, knee kilt with a flap
        bare_torso(s, tone)
        diag(s, LINENW, x0=0, x1=5, rows=(0, 9), width=3)
        usekh(s, ZGOLD, TURQ, rows=2, back=False)
        for p in LEGS:
            s.fill(p, tone, 3)
        kilt(s, LINEN, to_row=9, flap=dk(LINEN, 12), hem=ZGOLD)
        belt(s, 10, ZGOLD); belt(s, 11, LINEN)
        s.fpx("body", "front", 3, 11, CARN); s.fpx("body", "front", 4, 11, CARN)
        s.fpx("body", "left", 1, 9, LAPIS); s.fpx("body", "left", 1, 10, LAPIS)    # a papyrus roll case
    sandals(s, tone, ZGOLD)
    for p in LEGS:
        s.ring(p, 11, lt(tone, 6), 2); s.ring(p, 11, ZGOLD, 1) if False else None
        s.rows(p, 10, 11, ZGOLD, 1) if False else None


@role("azmar", "scribe")
def az_scribe(s, v):
    tone = (PALE, MID, TAN)[v]
    if v == 1:
        az_head(s, tone, "short_wig", woman=True)
        for f in SIDES:                                        # a plain cord band, no gold: a working scribe
            s.frect("head", f, 0, 1, 8, 2, (120, 86, 52), 1)
    else:
        az_head(s, tone, "short_wig" if v == 0 else "shaved")
    # reed pen tucked behind the ear (right side of the head, base layer and hat layer)
    for y in range(1, 6):
        s.fpx("head", "right", 5, y, (196, 170, 96))
    s.fpx("head", "right", 5, 0, (40, 30, 24))
    if v == 2:      # older scribe: grey stubble at the temples
        for f in ("right", "left"):
            for x in range(8):
                s.fpx("head", f, x, 1, (150, 146, 140)) if (x % 2) else None
    if v == 1:      # sand-ochre working dress with sleeves, lapis-edged shawl, palette on a cord
        OCH = (196, 162, 104)
        linen(s, "body", OCH, fold=10, every=3)
        for p in ARMS:
            linen(s, p, OCH, fold=10, every=3)
        forearms(s, tone, 6)
        for p in ARMS:
            s.ring(p, 5, dk(OCH, 26), 1)
        skirt(s, OCH, 12, hem=dk(OCH, 34), texfn=lambda p, c: linen(s, p, c, fold=10, every=3), hem_row=10)
        belt(s, 6, CARN, h=2)
        shoulder_cape(s, LINEN, rows=3)
        for f in SIDES:
            fx, fy, w, h = face_box("jacket", f)
            for x in range(w):
                s.fpx("jacket", f, x, 2, LAPIS)
        diag(s, (90, 64, 40), x0=1, x1=6, rows=(2, 9), width=1, shoulder="left")
        sandals(s, tone, (150, 110, 60))
        for p in LEGS:
            s.rows(p, 11, 12, tone, 2); s.ring(p, 11, (150, 110, 60), 1)
    else:
        # sheer linen shirt (tone showing through) and a long wrapped kilt
        sheer = mix(LINEN, tone, 0.28)
        linen(s, "body", sheer, fold=8, every=3)
        for p in ARMS:
            linen(s, p, sheer, fold=8, every=3)
        forearms(s, tone, 5)
        for p in ARMS:
            s.ring(p, 4, LINEN, 1)
        usekh(s, ZGOLD, LAPIS if v == 0 else TURQ, rows=2, back=False)
        for p in LEGS:
            s.fill(p, tone, 3)
        kilt(s, LINEN, to_row=10, hem=dk(LINEN, 26))
        belt(s, 10, LINEN); belt(s, 11, dk(LINEN, 20))
        sandals(s, tone, (150, 110, 60))
        if v == 2:      # lapis-edged shawl
            shoulder_cape(s, LINEN, rows=3)
            for f in SIDES:
                fx, fy, w, h = face_box("jacket", f)
                for x in range(w):
                    s.fpx("jacket", f, x, 2, LAPIS)
    # the scribe's palette (wood with a red and a black ink cake) and an ink-stained right hand
    s.frect("body", "left", 0, 8, 4, 11, (190, 160, 110), 2)
    s.fpx("body", "left", 1, 9, CARN); s.fpx("body", "left", 2, 9, OBS)
    s.fpx("rarm", "front", 1, 11, OBS); s.fpx("rarm", "front", 2, 11, (60, 40, 60))


@role("azmar", "porter")
def az_porter(s, v):
    if v == 1:
        tone = MID
        az_head(s, tone, "short_wig", hair_c=(30, 24, 28), woman=True)
        headcloth(s, SAND, band=CARN, lappets=False)
        clear_face(s)
        s.fclear("hat", "front", 0, 2, 8, 8)
        weave(s, "body", SAND, 3)
        for p in ARMS:
            s.fill(p, tone, 3)
        armbands(s, CARN, 9)
        skirt(s, SAND, 10, hem=dk(SAND, 30), texfn=lambda p, c: weave(s, p, c, 3))
        for p in LEGS:
            s.rows(p, 10, 12, tone, 3)
        belt(s, 6, (120, 90, 60))
        # tumpline strap across the chest and a carrying pad on the back
        diag(s, (120, 86, 52), x0=0, x1=6, rows=(0, 11), width=1)
        s.frect("body", "back", 2, 1, 6, 7, (140, 104, 64), 3)
        barefoot(s, tone, (140, 110, 70))
        return
    tone = DARK if v == 0 else (112, 74, 50)
    head_base(s, tone); ears(s, tone)
    hair(s, (26, 22, 24), "cropped")
    az_face(s, tone)
    if v == 0:
        headcloth(s, LINEN, stripe=SAND, lappets=True)
    else:
        headcloth(s, SAND, band=TURQ, lappets=False)
        s.fclear("hat", "front", 0, 2, 8, 8)
    clear_face(s)
    bare_torso(s, tone)
    for p in ARMS:
        s.fill(p, tone, 3)
    armbands(s, (120, 90, 60), 3)
    for p in LEGS:
        s.fill(p, tone, 3)
    kilt(s, SAND if v == 0 else (150, 120, 84), to_row=6, hem=dk(SAND, 34), pleats=False)
    belt(s, 10, ROPE); belt(s, 11, SAND if v == 0 else (150, 120, 84))
    # leather shoulder pad and carrying strap
    s.frect("rarm", "top", 0, 0, 4, 4, (130, 90, 54), 3)
    s.frect("rarm", "right", 0, 0, 4, 3, (130, 90, 54), 3); s.frect("rarm", "front", 0, 0, 4, 3, (130, 90, 54), 3)
    s.frect("rarm", "back", 0, 0, 4, 3, (130, 90, 54), 3); s.frect("rarm", "left", 0, 0, 4, 3, (130, 90, 54), 3)
    diag(s, (120, 86, 52), x0=0, x1=6, rows=(0, 10), width=1)
    if v == 2:
        s.frect("body", "back", 1, 1, 7, 8, (150, 112, 70), 3)   # back pad for loads
        for y in range(2, 8, 2):
            s.frect("body", "back", 1, y, 7, y + 1, (124, 90, 56), 1)
    barefoot(s, tone, (140, 110, 70))


@role("azmar", "merchant")
def az_merchant(s, v):
    if v == 1:      # bead-net dress, gold diadem, much jewellery
        tone = PALE
        az_head(s, tone, "long_wig", fillet=ZGOLD, woman=True)
        for f in ("right", "left"):
            s.fpx("head", f, 3 if f == "left" else 4, 6, ZGOLD)   # earrings
        linen(s, "body", LINEN, fold=8, every=3)
        for p in ARMS:
            s.fill(p, tone, 3)
        armbands(s, ZGOLD, 3); armbands(s, ZGOLD, 9); armbands(s, CARN, 10)
        skirt(s, LINEN, 12, hem=ZGOLD, texfn=lambda p, c: linen(s, p, c, fold=8, every=3), hem_row=10)
        # faience bead net: a turquoise diamond lattice over the dress
        for part in ("body",) + LEGS:
            for f in ("front", "back"):
                fx, fy, w, h = face_box(part, f)
                for y in range(4 if part == "body" else 0, h if part == "body" else 10):
                    for x in range(w):
                        if (x + y) % 3 == 0 and (x - y) % 3 == 0:
                            s.fpx(part, f, x, y, TURQ)
                        elif (x + y) % 3 == 0 or (x - y) % 3 == 0:
                            s.fpx(part, f, x, y, mix(LINEN, TURQ, 0.35))
        usekh(s, ZGOLD, CARN, LAPIS, rows=4)
        sandals(s, tone, ZGOLD)
        for p in LEGS:
            s.rows(p, 11, 12, tone, 2); s.ring(p, 11, ZGOLD, 1)
        return
    if v == 0:      # city merchant: striped robe, carnelian sash, heavy gold collar, purse
        tone = TAN
        az_head(s, tone, "wig", fillet=ZGOLD)
        for f in ("right", "left"):
            s.fpx("head", f, 3 if f == "left" else 4, 6, ZGOLD)
        stripes(s, "body", LINEN, LAPIS, every=3)
        for p in ARMS:
            stripes(s, p, LINEN, LAPIS, every=3)
        forearms(s, tone, 7)
        for p in ARMS:
            s.ring(p, 6, ZGOLD, 1)
        armbands(s, ZGOLD, 10)
        usekh(s, ZGOLD, CARN, TURQ, rows=4)
        for p in LEGS:
            stripes(s, p, LINEN, LAPIS, every=3)
        for p in LEGS:
            s.ring(p, 9, ZGOLD, 1)
        sandals(s, tone, ZGOLD)
        flare(s, 6, 10)
        belt(s, 7, CARN, h=2)
        s.frect("body", "front", 5, 9, 7, 11, CARN, 2)         # sash ends
        s.frect("body", "right", 1, 8, 3, 11, (140, 96, 56), 2); s.fpx("body", "right", 2, 8, ZGOLD)   # purse
        return
    # v == 2: caravan trader: striped headcloth, sand travelling robe, carnelian sash, gold earrings
    tone = MID
    head_base(s, tone); ears(s, tone)
    hair(s, OBS, "short")
    az_face(s, tone)
    beard(s, OBS, "short")
    headcloth(s, LINEN, stripe=LAPIS, band=ZGOLD, lappets=True)
    clear_face(s)
    for f in ("right", "left"):
        s.fpx("head", f, 3 if f == "left" else 4, 6, ZGOLD)
    weave(s, "body", SAND, 3)
    for p in ARMS:
        weave(s, p, SAND, 3)
    hands(s, tone, 10)
    for p in ARMS:
        s.ring(p, 9, CARN, 1)
    for p in LEGS:
        weave(s, p, SAND, 3)
    coat_skirts(s, SAND, to_row=10, opening=LINEN, hem=dk(SAND, 34))
    s.frect("body", "front", 3, 0, 5, 12, LINEN, 1)
    belt(s, 7, CARN, h=2, buckle=ZGOLD)
    usekh(s, ZGOLD, TURQ, rows=2, back=False)
    s.frect("body", "left", 1, 8, 3, 11, (140, 96, 56), 2)
    for p in LEGS:
        s.rows(p, 10, 12, (150, 110, 60), 3)
        s.frect(p, "bottom", 0, 0, 4, 4, (110, 80, 44), 2)


# ================================================================================================================
# MIREWICK: the Lantern Republic of the polders. Royal purple #5B2A86, crimson #9E1B32, gold accents; a Golden-Age
# burgher look: falling white collars, felt hats, clogs, oilskin on the canals, peat mud in the polders.
# ================================================================================================================
PURPLE = (91, 42, 134); PURPLE_D = (58, 28, 84); CRIMSON = (158, 27, 50); MGOLD = (214, 170, 58); LANTERN = (224, 168, 58)
BLACKCLOTH = (40, 34, 46); OILSKIN = (156, 128, 62); OILSKIN_D = (116, 94, 46); PEAT = (58, 47, 38); PEAT_L = (92, 74, 56)
CLAY = (110, 98, 84)


def falling_collar(s, c=LINENW, rows=2):
    """Flat white collar over the shoulders (jacket layer), the Mirewick burgher's mark."""
    for f in SIDES:
        fx, fy, w, h = face_box("jacket", f)
        for x in range(w):
            for y in range(rows):
                s.fpx("jacket", f, x, y, c if y < rows - 1 else dk(c, 14), 1)
    s.fill("jacket", c, 1, faces=("top",))
    for x in (3, 4):
        s.fpx("jacket", "front", x, rows, c)
    for y in range(rows):
        s.fpx("body", "front", 3, y, c); s.fpx("body", "front", 4, y, c)


def dutch_cap(s, c=LINENW):
    s.fill("hat", c, 2, faces=("top",))
    s.frect("hat", "front", 0, 0, 8, 2, c, 2)
    s.frect("hat", "front", 0, 2, 1, 6, c, 2); s.frect("hat", "front", 7, 2, 8, 6, c, 2)   # side wings
    for f in ("right", "left"):
        s.frect("hat", f, 0, 0, 8, 6, c, 2)
        s.frect("hat", f, 4 if f == "right" else 0, 5, 8 if f == "right" else 4, 6, dk(c, 18), 1)
    s.frect("hat", "back", 0, 0, 8, 5, c, 2)
    s.frect("hat", "front", 1, 1, 7, 2, dk(c, 14), 1)


def breeches(s, c, stock=LINENW, to_row=7):
    for p in LEGS:
        flat(s, p, c, 4)
        s.rows(p, to_row, 10, stock, 2)
        s.ring(p, to_row - 1, dk(c, 22), 2)
        s.fpx(p, "front", 1 if p == "rleg" else 2, to_row - 1, MGOLD)   # garter button


def shoes_buckle(s, c=(30, 26, 30), buckle=MGOLD):
    shoes(s, c, 10)
    s.fpx("rleg", "front", 1, 10, buckle); s.fpx("rleg", "front", 2, 10, buckle)
    s.fpx("lleg", "front", 1, 10, buckle); s.fpx("lleg", "front", 2, 10, buckle)


@role("mirewick", "clerk")
def mw_clerk(s, v):
    if v == 1:
        tone = person(s, (232, 196, 170), (96, 62, 40), "bob", woman=True, iris=(90, 80, 120), cheeks=(230, 168, 150))
        dutch_cap(s); clear_face(s)
        flat(s, "body", BLACKCLOTH, 3)
        for p in ARMS:
            flat(s, p, BLACKCLOTH, 3)
        hands(s, tone, 10)
        for p in ARMS:
            s.ring(p, 9, LINENW, 1)
        skirt(s, PURPLE_D, 12, hem=MGOLD, texfn=lambda p, c: flat(s, p, c, 3), hem_row=11)
        apron(s, CRIMSON, top=7, legs_to=9)
        falling_collar(s, rows=2)
        belt(s, 6, MGOLD)
        s.frect("body", "right", 1, 7, 2, 10, MGOLD, 1); s.fpx("body", "right", 2, 9, MGOLD)   # keys
        s.frect("body", "left", 0, 7, 4, 10, (210, 200, 170), 2)   # a tied bundle of papers
        s.frect("body", "left", 0, 8, 4, 9, CRIMSON, 0)
        for p in LEGS:
            s.ring(p, 11, BLACKCLOTH, 2)
        return
    if v == 0:
        tone = person(s, (226, 186, 156), (62, 44, 34), "short", beard_s="mustache", iris=(70, 60, 50), brow="stern")
        coat = PURPLE_D
        # spectacles
        for x in (1, 2, 5, 6):
            s.fpx("head", "front", x, 3, (180, 160, 80))
        s.fpx("head", "front", 3, 4, (180, 160, 80)); s.fpx("head", "front", 4, 4, (180, 160, 80))
    else:
        tone = person(s, (240, 204, 178), (176, 96, 48), "short", iris=(80, 110, 70), cheeks=(236, 170, 150))
        coat = CRIMSON
    # flat black felt cap with a gold pin
    s.fill("hat", BLACKCLOTH, 2, faces=("top",))
    for f in SIDES:
        s.frect("hat", f, 0, 0, 8, 2, BLACKCLOTH, 2)
    s.fpx("hat", "front", 6, 1, MGOLD)
    flat(s, "body", coat, 3)
    for p in ARMS:
        flat(s, p, coat, 3)
    hands(s, tone, 10)
    for p in ARMS:
        s.ring(p, 9, LINENW, 1)
    s.fpx("rarm", "front", 1, 11, (40, 40, 70)); s.fpx("rarm", "front", 2, 10, (40, 40, 70))   # ink-stained fingers
    s.frect("body", "front", 3, 0, 5, 12, dk(coat, 16), 1)
    buttons(s, MGOLD, 4, range(3, 11, 2))
    falling_collar(s, rows=2)
    belt(s, 8, BLACKCLOTH, buckle=MGOLD)
    s.frect("body", "left", 1, 8, 3, 12, (40, 40, 60), 1); s.fpx("body", "left", 2, 7, (230, 228, 220))   # ink horn and quill
    breeches(s, PURPLE if v == 0 else PURPLE_D, stock=LINENW if v == 0 else (170, 160, 150))
    shoes_buckle(s)
    # quill behind the ear
    for y in range(0, 5):
        s.fpx("head", "right", 5, y, (236, 232, 220))


@role("mirewick", "bargeman")
def mw_bargeman(s, v):
    if v == 1:      # bargewoman: purple bodice, crimson skirt, oilskin shoulder cape, white cap
        tone = person(s, (222, 176, 140), (60, 44, 34), "long", woman=True, iris=(70, 70, 60), cheeks=(220, 150, 126))
        dutch_cap(s); clear_face(s)
        flat(s, "body", PURPLE, 3)
        for p in ARMS:
            flat(s, p, LINENW, 2)
        forearms(s, tone, 6)
        for p in ARMS:
            s.ring(p, 5, dk(LINENW, 18), 1)
        for y in range(1, 6):
            s.fpx("body", "front", 3 + (y % 2), y, MGOLD)
        skirt(s, CRIMSON, 11, hem=dk(CRIMSON, 30), texfn=lambda p, c: weave(s, p, c, 4))
        apron(s, (196, 186, 160), top=7, legs_to=8)
        shoulder_cape(s, OILSKIN, rows=3)
        belt(s, 6, DLEATHER)
        clogs(s)
        return
    if v == 0:
        tone = person(s, (210, 160, 124), (88, 60, 40), "short", beard_s="short", iris=(70, 90, 110))
        # oilskin wide hat
        wide_hat(s, OILSKIN, band=OILSKIN_D, crown_rows=2)
    else:
        tone = person(s, (196, 146, 110), (140, 136, 130), "short", beard_s="full", iris=(70, 80, 90), age=1)
        knit_cap(s, CRIMSON, rows=3)
    clear_face(s)
    # rolled shirt, purple waistcoat, oilskin smock-apron, crimson neckerchief
    for p in ARMS:
        linen(s, p, (220, 214, 196), fold=10, every=3)
    forearms(s, tone, 6)
    for p in ARMS:
        s.ring(p, 5, (196, 188, 168), 1)
    linen(s, "body", (220, 214, 196), fold=10, every=3)
    if v == 0:
        s.frect("body", "front", 0, 0, 8, 12, OILSKIN, 4)           # oilskin smock
        s.frect("body", "back", 0, 0, 8, 12, OILSKIN, 4)
        for f in ("right", "left"):
            s.frect("body", f, 0, 0, 4, 12, OILSKIN, 4)
        for y in range(1, 12, 3):
            s.fpx("body", "front", 2, y, lt(OILSKIN, 28)); s.fpx("body", "back", 5, y, lt(OILSKIN, 28))
        for p in ARMS:
            s.rows(p, 0, 3, OILSKIN, 4)
    else:
        s.frect("body", "front", 0, 0, 8, 10, PURPLE, 3)           # waistcoat
        s.frect("body", "back", 0, 0, 8, 10, PURPLE, 3)
        for f in ("right", "left"):
            s.frect("body", f, 0, 0, 4, 10, PURPLE, 3)
        s.frect("body", "front", 3, 2, 5, 10, (220, 214, 196), 1)
        buttons(s, MGOLD, 2, range(3, 10, 2)); buttons(s, MGOLD, 5, range(3, 10, 2))
    # neckerchief, knotted at the front
    for f in SIDES:
        fx, fy, w, h = face_box("body", f)
        for x in range(w):
            s.fpx("body", f, x, 0, CRIMSON, 2)
    s.frect("body", "front", 3, 1, 5, 3, CRIMSON, 2); s.frect("body", "top", 0, 0, 8, 4, CRIMSON, 2)
    belt(s, 10, DLEATHER, buckle=MGOLD)
    # coil of rope at the hip
    s.frect("body", "right", 0, 8, 4, 12, ROPE, 3)
    s.frect("body", "right", 1, 9, 3, 11, dk(ROPE, 30), 1)
    # wide canvas breeches tucked into long socks, wooden clogs
    for p in LEGS:
        weave(s, p, (98, 84, 110) if v == 0 else (76, 70, 64), 4)
        s.rows(p, 7, 10, (196, 186, 166), 2)
        s.ring(p, 6, dk((98, 84, 110), 20), 2)
    flare(s, 3, 7)
    clogs(s)


@role("mirewick", "lamplighter")
def mw_lamp(s, v):
    if v == 1:      # lamplighter woman: crimson hooded cloak, purple dress, lantern-amber trim
        tone = person(s, (230, 192, 164), (128, 58, 34), "long", woman=True, iris=(80, 110, 80), cheeks=(228, 160, 140))
        s.fill("hat", CRIMSON, 3, faces=("top",))
        s.frect("hat", "front", 0, 0, 8, 2, CRIMSON, 3)
        s.frect("hat", "front", 0, 2, 1, 8, CRIMSON, 3); s.frect("hat", "front", 7, 2, 8, 8, CRIMSON, 3)
        for f in ("right", "left", "back"):
            s.frect("hat", f, 0, 0, 8, 8, CRIMSON, 3)
        s.frect("hat", "front", 0, 1, 8, 2, LANTERN, 1)
        clear_face(s)
        flat(s, "body", PURPLE, 3)
        for p in ARMS:
            flat(s, p, PURPLE, 3)
        hands(s, (90, 64, 44), 10)                              # leather gloves
        skirt(s, PURPLE_D, 12, hem=LANTERN, texfn=lambda p, c: flat(s, p, c, 3), hem_row=11)
        # cloak: crimson on the jacket and sleeve layers, open at the front
        for f in ("right", "left", "back"):
            s.frect("jacket", f, 0, 0, 8, 12, CRIMSON, 3)
        s.frect("jacket", "front", 0, 0, 2, 12, CRIMSON, 3); s.frect("jacket", "front", 6, 0, 8, 12, CRIMSON, 3)
        s.frect("jacket", "front", 2, 0, 6, 1, CRIMSON, 3)
        s.fill("jacket", CRIMSON, 3, faces=("top",))
        s.fpx("jacket", "front", 2, 0, MGOLD); s.fpx("jacket", "front", 5, 0, MGOLD)
        for p in SLEEVES:
            s.fill(p, CRIMSON, 3, faces=("top",))
            s.rows(p, 0, 8, CRIMSON, 3)
        belt(s, 7, MGOLD)
        for p in LEGS:
            s.ring(p, 11, BLACKCLOTH, 2)
        return
    if v == 0:
        tone = person(s, (226, 184, 150), (70, 50, 36), "short", beard_s="stubble", iris=(70, 90, 70))
        coat, hat_c = PURPLE, BLACKCLOTH
    else:
        tone = person(s, (214, 170, 140), (176, 172, 166), "short", beard_s="mustache", iris=(90, 90, 100), age=1)
        coat, hat_c = CRIMSON, PURPLE_D
    # tall felt hat with a gold band (crown rows 0-3 on the hat layer)
    s.fill("hat", hat_c, 2, faces=("top",))
    for f in SIDES:
        s.frect("hat", f, 0, 0, 8, 3, hat_c, 2)
        s.frect("hat", f, 0, 2, 8, 3, MGOLD, 1)
    clear_face(s)
    # long coat with gold buttons and lantern-amber cuffs, coat skirts to the knee
    flat(s, "body", coat, 3)
    for p in ARMS:
        flat(s, p, coat, 3)
    hands(s, (90, 64, 44), 10)
    for p in ARMS:
        s.ring(p, 9, LANTERN, 1)
    s.frect("body", "front", 3, 0, 5, 12, dk(coat, 18), 1)
    buttons(s, MGOLD, 2, range(2, 11, 2)); buttons(s, MGOLD, 5, range(2, 11, 2))
    falling_collar(s, LANTERN if v == 0 else LINENW, rows=1)
    for p in LEGS:
        flat(s, p, (50, 40, 52), 3)
    coat_skirts(s, coat, to_row=7, opening=(50, 40, 52), hem=dk(coat, 26))
    belt(s, 8, DLEATHER, buckle=MGOLD)
    # ladder strap over the left shoulder and a wick-pole hook at the belt
    diag(s, (110, 80, 50), x0=1, x1=6, rows=(0, 9), width=1, shoulder="left")
    s.frect("body", "right", 1, 8, 2, 12, (150, 120, 80), 1); s.fpx("body", "right", 1, 8, IRON)
    s.frect("body", "left", 1, 9, 3, 11, LANTERN, 2)           # tinderbox
    for p in LEGS:
        s.rows(p, 8, 12, (36, 30, 34), 3)                       # tall boots
        s.ring(p, 8, (60, 48, 40), 2)
        s.frect(p, "bottom", 0, 0, 4, 4, (24, 20, 22), 2)


@role("mirewick", "peat_cutter")
def mw_peat(s, v):
    if v == 1:      # hitched skirt, purple kerchief, sackcloth apron, mud to the knee
        tone = person(s, (218, 172, 138), (110, 74, 48), "braid", woman=True, iris=(80, 90, 70), cheeks=(216, 146, 122))
        kerchief(s, PURPLE, rows=4, edge=CRIMSON)
        clear_face(s); s.fclear("hat", "back", 2, 5, 6, 8)
        weave(s, "body", PEAT_L, 5)
        for p in ARMS:
            weave(s, p, (196, 186, 160), 3)
        forearms(s, tone, 6)
        for p in ARMS:
            s.ring(p, 5, dk((196, 186, 160), 20), 1)
        skirt(s, PEAT_L, 8, hem=dk(PEAT_L, 22), texfn=lambda p, c: weave(s, p, c, 5), overlay=True)
        for p in LEGS:
            s.rows(p, 8, 12, tone, 3)
        apron(s, (150, 132, 100), top=6, legs_to=7)
        belt(s, 6, CRIMSON)
        mud(s, LEGS, 6, 12, PEAT, 0.5)
        barefoot(s, tone, PEAT)
        for p in LEGS:
            s.rows(p, 10, 12, mix(tone, PEAT, 0.6), 6)
        return
    if v == 0:
        tone = person(s, (206, 156, 118), (92, 64, 42), "short", beard_s="short", iris=(70, 80, 60))
        wide_hat(s, (96, 76, 54), band=CRIMSON, crown_rows=2)
        smock = PEAT_L
    else:
        tone = person(s, (222, 178, 146), (60, 44, 34), "short", beard_s="stubble", iris=(80, 70, 60), age=1)
        knit_cap(s, PURPLE_D, rows=2, fold=False)
        smock = (120, 100, 76)
    clear_face(s)
    weave(s, "body", smock, 5)
    for p in ARMS:
        weave(s, p, smock, 5)
    forearms(s, tone, 7)
    for p in ARMS:
        s.ring(p, 6, dk(smock, 22), 2)
        s.ring(p, 11, mix(tone, PEAT, 0.4), 3)                  # peat-black hands
    if v == 2:      # faded purple waistcoat
        s.frect("body", "front", 0, 1, 8, 9, (88, 62, 104), 3)
        s.frect("body", "back", 0, 1, 8, 9, (88, 62, 104), 3)
        for f in ("right", "left"):
            s.frect("body", f, 0, 1, 4, 9, (88, 62, 104), 3)
        s.frect("body", "front", 3, 1, 5, 9, smock, 2)
    # crimson neckerchief
    for f in SIDES:
        fx, fy, w, h = face_box("body", f)
        for x in range(w):
            s.fpx("body", f, x, 0, CRIMSON, 2)
    s.fpx("body", "front", 3, 1, CRIMSON); s.fpx("body", "front", 4, 1, CRIMSON)
    belt(s, 8, DLEATHER)
    s.frect("body", "front", 1, 9, 7, 12, dk(smock, 14), 3)
    for p in LEGS:
        weave(s, p, CLAY, 4)
    # tall muddy boots and peat stains all over the legs
    for p in LEGS:
        s.rows(p, 6, 12, (52, 42, 34), 3)
        s.ring(p, 6, (70, 56, 44), 2)
        s.frect(p, "bottom", 0, 0, 4, 4, PEAT, 2)
    mud(s, LEGS, 3, 12, PEAT_L, 0.35)
    mud(s, ["body"], 9, 12, PEAT, 0.25, faces=("front",))
    s.frect("body", "back", 2, 4, 6, 8, dk(smock, 18), 3)      # sweat-dark back


def main():
    n = 0
    for (kingdom, rid), fn in ROLES.items():
        d = os.path.join(OUT, kingdom)
        os.makedirs(d, exist_ok=True)
        for v in range(3):
            name = f"{kingdom}/{rid}_{v}"
            s = Skin(sum(ord(c) * (i + 1) for i, c in enumerate(name)))
            fn(s, v)
            s.save(os.path.join(d, f"{rid}_{v}.png"))
            n += 1
    print("wrote", n, "townsfolk skins to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
