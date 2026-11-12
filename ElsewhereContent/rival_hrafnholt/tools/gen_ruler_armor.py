#!/usr/bin/env python3
"""s-rulerarmor (3.0.0, final review B10 + K29): faction-styled armor layers for every kingdom ruler and boss.

The bosses keep their real vanilla armor items (stats, trims and drop rules unchanged); only the item's equippable
asset is swapped, so these 64x32 humanoid layers draw instead of cyan vanilla diamond (or iron/gold/netherite):
  hrafnholt_ruler  green and bark-brown Viking: spangenhelm with nasal and bronze bands, fur collar over a mail byrnie,
                   leather belt and green tunic, brown trousers with green leg wraps, leather boots
  mirewick_ruler   royal purple brigandine with gold rivets and a crimson sash, gold-combed blackened morion,
                   purple and crimson slashed hose, black boots with gold buckles
  azmar_ruler      gold-and-lapis striped khat, usekh broad collar (gold, lapis, turquoise, carnelian), winged-scarab
                   pectoral over gold and lapis scales, linen shendyt kilt with a striped apron, gold sandal straps
  aldermark_ruler  baseline knight: steel plate with Aldermark's royal-blue surcoat, cream border and cream tower
plus the Amir's war camel caparison (camel_saddle layer, 128x128, gold-and-lapis drape with winged scarabs, K29) and the
Aldermark Marshal's warhorse barding (horse_body layer, replaces cyan diamond horse armour). Vanilla textures (shape masks and metal shading) are read from
the local Loom cache. Run: python3 rival_hrafnholt/tools/gen_ruler_armor.py (from ElsewhereContent or anywhere).
"""
import io
import json
import os
import random
import zipfile

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
EC = os.path.abspath(os.path.join(HERE, "..", ".."))
JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar")
_zip = zipfile.ZipFile(JAR)


def vanilla(path):
    return Image.open(io.BytesIO(_zip.read("assets/minecraft/textures/" + path))).convert("RGBA")


def hx(s):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)


def mul(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), c[3])


IRON_H = vanilla("entity/equipment/humanoid/iron.png")
IRON_L = vanilla("entity/equipment/humanoid_leggings/iron.png")


def lum(px):
    return 0.299 * px[0] + 0.587 * px[1] + 0.114 * px[2]


def metal_shade(src, x, y):
    """Shading factor from the vanilla iron layer (its grey ramp carries the bevels and rivets)."""
    p = src.getpixel((x, y))
    return 0.62 + 0.55 * (lum(p) / 255.0)


# ---- humanoid UV regions (64x32): head (0,0) 8x8x8, body (16,16) 8x12x4, arm (40,16) 4x12x4, leg (0,16) 4x12x4 ----------
def region(x, y):
    """(part, face, local x, local y) of a pixel in the humanoid armor layout."""
    def cube(u, v, w, h, d, name):
        if v <= y < v + d:
            if u + d <= x < u + d + w:
                return (name, "top", x - u - d, y - v)
            if u + d + w <= x < u + d + 2 * w:
                return (name, "bottom", x - u - d - w, y - v)
        elif v + d <= y < v + d + h:
            if u <= x < u + d:
                return (name, "right", x - u, y - v - d)
            if u + d <= x < u + d + w:
                return (name, "front", x - u - d, y - v - d)
            if u + d + w <= x < u + 2 * d + w:
                return (name, "left", x - u - d - w, y - v - d)
            if u + 2 * d + w <= x < u + 2 * d + 2 * w:
                return (name, "back", x - u - 2 * d - w, y - v - d)
        return None
    for args in ((0, 0, 8, 8, 8, "head"), (16, 16, 8, 12, 4, "body"), (40, 16, 4, 12, 4, "arm"), (0, 16, 4, 12, 4, "leg")):
        r = cube(*args)
        if r:
            return r
    return None


def paint(mask, fn, seed):
    rnd = random.Random(seed)
    out = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(64):
            if mask.getpixel((x, y))[3] == 0:
                continue
            r = region(x, y)
            if r is None:
                continue
            c = fn(x, y, *r, rnd)
            if c is not None:
                out.putpixel((x, y), c)
    return out


def noise(rnd, c, amt=0.07):
    return mul(c, 1.0 + rnd.uniform(-amt, amt))


# ======================================================================================================================
# Hrafnholt: forest green #2E5A2C, bark brown #6B4A2B, iron grey, bone, bronze
G, GD, GL = hx("2E5A2C"), hx("1F3F1E"), hx("4A7A3F")
B, BD, BL = hx("6B4A2B"), hx("47301C"), hx("8A6440")
IR, IRD, IRL = hx("8C9094"), hx("55595E"), hx("BFC3C7")
BONE, BRONZE, BRONZED = hx("E3D9C0"), hx("B07F3E"), hx("7E5726")


def mail(x, y, rnd):
    """Riveted ring mail: a staggered light/dark ring pattern."""
    on = (x + (y % 2)) % 2 == 0
    return noise(rnd, IRL if on else IRD, 0.05) if (x + y) % 4 else noise(rnd, IR, 0.05)


def hraf_h(x, y, part, face, lx, ly, rnd):
    if part == "head":
        base = mul(IR, metal_shade(IRON_H, x, y))
        if face == "top":
            if lx in (3, 4) or ly in (3, 4):
                return BRONZE if (lx + ly) % 3 else BRONZED          # the spangen frame
            return base
        if face == "front":
            if lx in (3, 4) and ly >= 1:
                return IRD if ly > 2 else BRONZE                     # nasal guard
            if ly == 2:
                return BRONZE                                        # brow band
            return base
        if face == "back" and ly >= 4:
            return mail(x, y, rnd)                                   # mail camail at the neck
        if ly == 2:
            return BRONZE if lx % 3 else BRONZED                     # brim band
        return base
    if part == "body":
        if face == "top" or face == "bottom":
            return noise(rnd, B)
        if ly <= 1:
            return noise(rnd, BL if (lx + ly) % 2 else B, 0.12)      # bear-fur collar
        if face == "front" and ly == 2 and lx in (3, 4):
            return BONE                                              # bone cloak clasp
        if ly == 6:
            if face == "front" and lx in (3, 4):
                return BRONZE                                        # belt buckle
            return BD                                                # leather belt
        if ly >= 7:
            return noise(rnd, GD if ly == 8 or ly >= 9 else G)       # green tunic below the belt
        return mail(x, y, rnd)
    if part == "arm":
        if face == "top":
            return noise(rnd, BL if (lx + ly) % 2 else B, 0.12)      # fur over the shoulder
        if ly <= 0:
            return noise(rnd, B, 0.12)
        if ly == 4:
            return GL                                                # green sleeve edge
        return mail(x, y, rnd)
    if part == "leg":
        if face in ("top", "bottom"):
            return BD
        if ly == 11:
            return BD                                                # sole
        if ly <= 8 and (lx + ly) % 3 == 0:
            return noise(rnd, G)                                     # winingas wraps
        return noise(rnd, B, 0.08)
    return None


def hraf_l(x, y, part, face, lx, ly, rnd):
    if part == "body":
        if face in ("top", "bottom"):
            return noise(rnd, B)
        if ly == 7:
            return BRONZE if (face == "front" and lx in (3, 4)) else BD   # belt
        if ly >= 8:
            return noise(rnd, GD if ly == 11 else G)                 # tunic skirt, dark hem
        return noise(rnd, G)
    if part == "leg":
        if face in ("top", "bottom"):
            return B
        if ly >= 3 and (lx + ly) % 3 == 0:
            return noise(rnd, G if ly % 2 else GL)                   # wraps from the knee
        return noise(rnd, B if ly % 4 else BL, 0.08)                 # wool trousers
    return None


# ======================================================================================================================
# Mirewick: royal purple #5B2A86, crimson #9E1B32, gold accents, blackened steel
P, PD, PL = hx("5B2A86"), hx("3E1C5E"), hx("7A44A8")
C, CD, CL = hx("9E1B32"), hx("6E1222"), hx("C0344B")
AU, AUD, AUL = hx("D4A537"), hx("9C7424"), hx("F0CC6A")
BS = hx("4A4552")


def mire_h(x, y, part, face, lx, ly, rnd):
    if part == "head":
        base = mul(BS, metal_shade(IRON_H, x, y) * 1.05)
        if face == "top":
            if lx in (3, 4):
                return AUL if ly % 2 else AU                         # gold comb of the morion
            return base
        if ly == 2:
            return AU if lx % 2 else AUD                             # gilt brim
        if face == "back" and ly >= 4:
            return noise(rnd, C)                                     # crimson neck lining
        return base
    if part == "body":
        if face in ("top", "bottom"):
            return noise(rnd, P)
        if ly == 0:
            return AU                                                # gilt neckline
        if ly == 8:
            return AUD if lx % 2 else AU                             # gilt lower edge
        if face == "front":
            d = lx - (ly - 1)                                        # crimson sash, shoulder to hip
            if d in (0, 1):
                return noise(rnd, CL if d == 0 else C, 0.05)
            if lx in (5, 6) and ly in (2, 3):
                return AUL if (lx + ly) % 2 else AU                  # gold lantern badge
        if face in ("right", "left"):
            return noise(rnd, C)
        if (lx + 2 * ly) % 4 == 0:
            return AUL                                               # brigandine rivets
        return noise(rnd, PD if ly >= 9 else P, 0.06)
    if part == "arm":
        if face == "top":
            return noise(rnd, C)
        if ly == 4:
            return AU                                                # gilt cuff
        return noise(rnd, C if (lx + (1 if face in ("front", "back") else 0)) % 2 else P, 0.05)   # slashed sleeve
    if part == "leg":
        if face in ("top", "bottom"):
            return hx("201C24")
        if ly <= 6:
            return noise(rnd, C if ly == 6 else hx("2A2530"), 0.05) # black boot, crimson turn-over
        if ly == 8 and face == "front" and lx in (1, 2):
            return AUL                                               # gold buckle
        if ly == 11:
            return hx("15121A")
        return noise(rnd, hx("2A2530"), 0.05)
    return None


def mire_l(x, y, part, face, lx, ly, rnd):
    if part == "body":
        if face in ("top", "bottom"):
            return P
        if ly == 7:
            return AU if lx % 2 else AUD                             # gilt girdle
        if ly == 11:
            return AU                                                # skirt hem
        return noise(rnd, P, 0.05)
    if part == "leg":
        if face in ("top", "bottom"):
            return P
        if ly == 0:
            return AU
        stripe = (lx // 1 + (0 if face in ("front", "back") else 1)) % 2
        return noise(rnd, C if stripe else P, 0.06)                  # purple-and-crimson slashed hose
    return None


# ======================================================================================================================
# Azmar: gold #D4A537, lapis #1F3F8F, faience turquoise #2BB3A3, obsidian #16131A, linen #EDE3CC, carnelian #B0362B
L, LD, LL = hx("1F3F8F"), hx("142B63"), hx("3358B0")
T, TD = hx("2BB3A3"), hx("1E7F74")
OB, LIN, LINS, CAR = hx("16131A"), hx("EDE3CC"), hx("CDBF9F"), hx("B0362B")


def azm_h(x, y, part, face, lx, ly, rnd):
    if part == "head":
        if face == "top":
            return AU if lx % 2 else L                               # striped khat crown
        if face == "front":
            if ly == 0:
                return CAR if lx in (3, 4) else AU                   # gold band with a carnelian jewel
            return AU if ly % 2 else L
        return AU if ly % 2 == 0 else L                              # gold-and-lapis stripes
    if part == "body":
        if face in ("top", "bottom"):
            return AU
        band = [AU, L, T, CAR if (lx % 2) else AU, AUD]              # usekh broad collar rows
        if ly <= 4:
            if ly == 3:
                return CAR if lx % 2 else AU
            return band[ly]
        if face == "front":
            if ly == 5 and lx not in (3, 4):
                return AUL if lx in (0, 7) else AU                   # scarab's spread wings
            if lx in (3, 4) and ly in (5, 6, 7):
                return LL if ly == 5 else (L if ly == 6 else OB)     # scarab body
        # gold-and-lapis scale corselet, staggered
        sc = ((lx + (ly % 2)) // 2 + ly) % 2
        edge = (lx + (ly % 2)) % 2 == 0
        c = AU if sc else L
        return mul(c, 0.8) if edge else c
    if part == "arm":
        if face == "top":
            return AU
        if ly <= 2:
            return [AU, L, T][ly]                                    # the collar over the shoulder
        if ly == 3:
            return AU
        return L if lx % 2 else AU                                   # arm band
    if part == "leg":
        if face in ("top", "bottom"):
            return AUD
        if ly in (6, 9):
            return AU                                                # sandal straps
        if ly == 11:
            return AUD                                               # sole
        if face == "front" and ly == 10 and lx in (1, 2):
            return T                                                 # faience bead
        if ly < 6:
            return None                                              # bare shin above the ankle wraps
        return noise(rnd, LIN, 0.04)
    return None


def azm_l(x, y, part, face, lx, ly, rnd):
    if part == "body":
        if face in ("top", "bottom"):
            return LIN
        if ly == 7:
            return AU if (lx % 2) else L                             # checkered gold-and-lapis belt
        if face == "front" and lx in (3, 4) and ly >= 8:
            return AU if ly % 2 else L                               # the striped royal apron
        return LINS if lx % 2 else LIN                               # pleated linen
    if part == "leg":
        if face in ("top", "bottom"):
            return LIN
        if ly <= 3:
            return LINS if (lx + (1 if face == "front" else 0)) % 2 else LIN   # kilt pleats
        if ly == 4:
            return AUD if lx % 2 else LINS                           # gold-edged hem
        return None                                                  # bare legs below the kilt
    return None


# ======================================================================================================================
# Aldermark baseline: steel plate, royal blue #2E4C9E surcoat, cream #E8D9A0 border and tower
A, AD, AL = hx("2E4C9E"), hx("1F3570"), hx("4766BF")
CR, CRS = hx("E8D9A0"), hx("C4B57E")
ST = hx("A4A8AE")
TOWER = ["..#..#..", "..####..", "..####..", "..#..#..", ".######.", "........"]


def ald_h(x, y, part, face, lx, ly, rnd):
    if part == "head":
        base = mul(ST, metal_shade(IRON_H, x, y))
        if face == "top" and lx in (3, 4):
            return A if ly % 2 else AL                               # blue crest
        if ly == 2 and face != "top":
            return CR if lx % 2 else CRS                             # cream brow band (orle)
        return base
    if part == "body":
        if face in ("top", "bottom"):
            return mul(ST, metal_shade(IRON_H, x, y))
        if face in ("front", "back"):
            if ly == 0:
                return mul(ST, metal_shade(IRON_H, x, y))            # gorget
            if ly == 8:
                return CR                                            # cream hem
            if lx in (0, 7):
                return AD                                            # dark-blue seam
            if face == "front" and 1 <= ly <= 6 and TOWER[ly - 1][lx] == "#":
                return CR                                            # the Aldermark tower
            return noise(rnd, A if ly < 7 else AD, 0.04)
        return mul(ST, metal_shade(IRON_H, x, y))                    # plate at the flanks
    if part == "arm":
        if ly == 4 and face != "top":
            return AU                                                # gilt pauldron rim
        return mul(ST, metal_shade(IRON_H, x, y))
    if part == "leg":
        if ly == 6 and face != "top" and face != "bottom":
            return AU                                                # gilt sabaton edge
        return mul(ST, metal_shade(IRON_H, x, y))
    return None


def ald_l(x, y, part, face, lx, ly, rnd):
    if part == "body":
        if face in ("top", "bottom"):
            return A
        if face == "front" and lx in (3, 4) and ly >= 9:
            return None                                              # the surcoat's riding split
        if ly == 11 or (face in ("front", "back") and lx in (0, 7)):
            return CR
        return noise(rnd, A, 0.04)
    if part == "leg":
        return mul(ST, metal_shade(IRON_L, x, y))                    # steel cuisses and greaves
    return None


# ======================================================================================================================
def write(img, module, rel):
    p = os.path.join(EC, module, "src/main/resources/assets/elsewhere/textures/entity/equipment", rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)
    return p


def write_json(module, name, obj):
    p = os.path.join(EC, module, "src/main/resources/assets/elsewhere/equipment", name + ".json")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(obj, f, indent=1)
        f.write("\n")
    return p


def humanoid_asset(module, name, fh, fl, seed):
    write(paint(IRON_H, fh, seed), module, "humanoid/" + name + ".png")
    write(paint(IRON_L, fl, seed + 1), module, "humanoid_leggings/" + name + ".png")
    write_json(module, name, {"layers": {"humanoid": [{"texture": "elsewhere:" + name}],
                                         "humanoid_leggings": [{"texture": "elsewhere:" + name}]}})


# ---- the Amir's war-camel caparison (camel_saddle layer, the saddle model's 15x12x27 body shell at texOffs 0,89) ---------
def caparison():
    van = vanilla("entity/equipment/camel_saddle/saddle.png")
    out = Image.new("RGBA", van.size, (0, 0, 0, 0))
    # 1) vanilla saddle, seat and tack recoloured: leather -> lapis, dark outline -> obsidian, steel fittings -> gold
    for y in range(van.height):
        for x in range(van.width):
            p = van.getpixel((x, y))
            if p[3] == 0:
                continue
            r, g, b, _ = p
            l = lum(p)
            if abs(r - g) < 25 and abs(g - b) < 25:                  # greys: buckles and rings -> faience
                out.putpixel((x, y), T if l > 120 else TD)
            elif l < 60:
                out.putpixel((x, y), OB)
            elif l > 140:
                out.putpixel((x, y), AUL)                            # gilded seat and tack on the lapis drape
            elif l > 100:
                out.putpixel((x, y), AU)
            else:
                out.putpixel((x, y), AUD)
    u0, v0, w, h, d = 0, 89, 15, 12, 27
    rnd = random.Random(29)

    def side_panel(ox, oy, width, mirror):
        """A drape face: gold border, lapis field, winged scarab, turquoise band, gold-and-carnelian fringe."""
        for ly in range(h):
            for lx in range(width):
                x, y = ox + lx, oy + ly
                c = None
                if ly == 0:
                    c = AU
                elif ly <= 7:
                    if lx in (0, width - 1):
                        c = AU
                    else:
                        c = noise(rnd, L, 0.05)
                elif ly == 8:
                    c = T
                elif ly == 9:
                    c = AU
                elif ly == 10:
                    c = (AU if (lx // 2) % 2 == 0 else CAR) if lx % 2 == 0 else None   # tassels
                out.putpixel((x, y), c if c else (0, 0, 0, 0))
        # winged scarab with a sun disc, centred
        cx = ox + width // 2
        art = ["....C....",
               "...COC...",
               "WWWOOOWWW",
               ".WWLOLWW.",
               "...LOL...",
               "....O...."]
        for j, row in enumerate(art):
            for i, ch in enumerate(row):
                col = {"C": CAR, "O": OB, "W": AU, "L": LL}.get(ch)
                if col:
                    out.putpixel((cx - 4 + i, oy + 1 + j), col)
        # glyph-style frieze dots along the top band
        for lx in range(2, width - 2, 3):
            out.putpixel((ox + lx, oy + 0), AUL)

    # top (blanket under the seat), lapis with a gold edge
    for ly in range(d):
        for lx in range(w):
            x, y = u0 + d + lx, v0 + ly
            edge = lx in (0, w - 1) or ly in (0, d - 1)
            out.putpixel((x, y), AU if edge else (noise(rnd, L, 0.05) if (lx + ly) % 6 else T))
    side_panel(u0, v0 + d, d, False)                   # right flank
    side_panel(u0 + d + w, v0 + d, d, True)            # left flank
    # chest (front) and rump (back): short gold-edged lapis breast band
    for ox in (u0 + d, u0 + 2 * d + w):
        for ly in range(h):
            for lx in range(w):
                c = None
                if ly == 0 or ly == 3:
                    c = AU
                elif ly in (1, 2):
                    c = T if lx % 3 == 1 else L
                elif ly == 4 and lx % 2 == 0:
                    c = CAR
                out.putpixel((ox + lx, v0 + d + ly), c if c else (0, 0, 0, 0))
    # the bottom face (belly) stays bare
    for ly in range(d):
        for lx in range(w):
            out.putpixel((u0 + d + w + lx, v0 + ly), (0, 0, 0, 0))
    return out


def barding():
    """Aldermark warhorse barding: the vanilla iron horse-armour shape, enamelled royal blue with bright steel and cream."""
    src = vanilla("entity/equipment/horse_body/iron.png")
    out = Image.new("RGBA", src.size, (0, 0, 0, 0))
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3] == 0:
                continue
            l = lum(p) / 255.0
            if l < 0.30:
                c = AD                                               # deep blue seams
            elif l < 0.52:
                c = mul(A, 0.85 + 0.5 * l)                           # royal-blue enamel
            elif l < 0.78:
                c = mul(ST, 0.75 + 0.35 * l)                         # steel plates
            else:
                c = CR                                               # cream-gilt highlights
            out.putpixel((x, y), (c[0], c[1], c[2], p[3]))
    return out


def saddle_layers(camel_tex):
    names = ["camel_husk_saddle", "donkey_saddle", "horse_saddle", "mule_saddle", "nautilus_saddle", "pig_saddle",
             "skeleton_horse_saddle", "strider_saddle", "zombie_horse_saddle"]
    layers = {n: [{"texture": "minecraft:saddle"}] for n in names}
    layers["camel_saddle"] = [{"texture": camel_tex}]
    return {"layers": dict(sorted(layers.items()))}


if __name__ == "__main__":
    humanoid_asset("rival_hrafnholt", "hrafnholt_ruler", hraf_h, hraf_l, 101)
    humanoid_asset("rival_mirewick", "mirewick_ruler", mire_h, mire_l, 202)
    humanoid_asset("rival_azmar", "azmar_ruler", azm_h, azm_l, 303)
    humanoid_asset("kingdom_court", "aldermark_ruler", ald_h, ald_l, 404)
    write(caparison(), "rival_azmar", "camel_saddle/azmar_caparison.png")
    # every other saddled animal keeps the vanilla saddle look if this saddle ever ends up on it
    write_json("rival_azmar", "azmar_caparison", saddle_layers("elsewhere:azmar_caparison"))
    write(barding(), "kingdom_court", "horse_body/aldermark_barding.png")
    write_json("kingdom_court", "aldermark_barding", {"layers": {"horse_body": [{"texture": "elsewhere:aldermark_barding"}]}})
    print("ok")
