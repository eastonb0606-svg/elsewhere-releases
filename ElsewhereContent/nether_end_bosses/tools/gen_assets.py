"""Generates the nether_end_bosses resources: 16x16 item/block pixel art, item/block models, blockstates, equipment
assets, lang, sounds.json, damage types + tags, worldgen features. Run: S/venv/bin/python tools/gen_assets.py"""
import json, math, os, random
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
A = os.path.join(RES, "assets", "elsewhere")
D = os.path.join(RES, "data", "elsewhere")
MC = os.path.join(RES, "data", "minecraft")

def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")

def hexc(s, a=255):
    s = s.lstrip("#")
    return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), a)

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)

def canvas():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))

def outline(img, col="1b1020"):
    px = img.load()
    out = img.copy()
    po = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 200 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = hexc(col)
    return out

def put(img, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), c if isinstance(c, tuple) else hexc(c))

def line(img, pts, c):
    for (x, y) in pts:
        put(img, x, y, c)

def diag(x0, y0, n):
    return [(x0 + i, y0 - i) for i in range(n)]

# ------------------------------------------------------------------------------------------------ item art

def art_sword(blade, edge, guard, grip):
    img = canvas()
    for i in range(10):
        put(img, 5 + i, 10 - i, blade)
        put(img, 6 + i, 10 - i, edge)
        put(img, 5 + i, 9 - i, edge)
    for (x, y) in [(3, 10), (4, 11), (5, 12), (6, 13), (4, 9), (7, 12)]:
        put(img, x, y, guard)
    for i in range(3):
        put(img, 2 + i - 0, 13 - i + 0, grip)
    put(img, 1, 14, guard)
    return outline(img)

def art_axe(head, edge, handle):
    img = canvas()
    for i in range(12):
        put(img, 3 + i, 14 - i, handle)
    for y in range(2, 9):
        for x in range(8, 14):
            if (x - 8) + (8 - y) >= 0 and x - 8 <= 1 + (y - 2) * 0.9:
                put(img, x, y, head)
    for y in range(2, 9):
        put(img, 13, y, edge)
        put(img, 14, y + 1 if y < 8 else y, edge)
    # the tusk curling over the head
    for (x, y) in [(7, 3), (6, 2), (6, 1), (7, 0), (8, 0)]:
        put(img, x, y, "f4ead2")
    return outline(img)

def art_rod(c1, c2, tip):
    img = canvas()
    for i in range(12):
        put(img, 2 + i, 13 - i, c1 if i % 2 else c2)
        put(img, 3 + i, 13 - i, shade(hexc(c1), 0.8))
    for (x, y) in [(13, 1), (14, 1), (14, 2), (12, 0), (15, 0)]:
        put(img, x, y, tip)
    return outline(img, "4a1a06")

def art_crown(base, gem, spike):
    img = canvas()
    for x in range(2, 14):
        for y in range(8, 13):
            put(img, x, y, base if y != 10 else shade(hexc(base), 0.8))
    for sx in (2, 6, 9, 13):
        for y in range(4, 8):
            put(img, sx, y + (1 if sx in (6, 9) else 0), spike)
            put(img, sx + (1 if sx < 13 else -1), y + 1, spike)
    for gx in (4, 8, 11):
        put(img, gx, 10, gem)
    return outline(img)

def art_orb(core, mid, rim):
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3: put(img, x, y, core)
            elif d < 5.2: put(img, x, y, mid)
            elif d < 6.8: put(img, x, y, rim)
    return outline(img)

def art_ember():
    img = art_orb("fffbe0", "ffd24a", "ff8a1c")
    for (x, y) in [(7, 1), (8, 0), (9, 2), (6, 3)]:
        put(img, x, y, "ffb02e")
    return img

def art_harness(strap, metal, accent):
    img = canvas()
    for x in range(2, 14):
        put(img, x, 5, strap)
        put(img, x, 6, shade(hexc(strap), 0.8))
    for y in range(5, 14):
        put(img, 3, y, strap)
        put(img, 12, y, strap)
    for x in range(4, 12):
        put(img, x, 11, strap)
    for (x, y) in [(3, 5), (12, 5), (3, 11), (12, 11), (7, 11), (8, 11)]:
        put(img, x, y, metal)
    for x in range(5, 11):
        put(img, x, 3, accent)
        put(img, x, 4, accent)
    return outline(img)

def art_gland(c1, c2):
    img = canvas()
    for y in range(16):
        for x in range(16):
            if ((x - 7.5) / 5.5) ** 2 + ((y - 8.5) / 4.5) ** 2 < 1:
                put(img, x, y, c1 if (x + y) % 3 else c2)
    for (x, y) in [(5, 7), (6, 6), (9, 9)]:
        put(img, x, y, "ffd0c0")
    for (x, y) in [(11, 4), (12, 3), (13, 2)]:
        put(img, x, y, c2)
    return outline(img)

def art_bomb():
    img = art_orb("ffe066", "e8b72a", "a8741a")
    for (x, y) in [(8, 1), (9, 0), (7, 2), (8, 2)]:
        put(img, x, y, "5a4a3a")
    put(img, 10, 0, "ff5a10")
    return img

def art_lantern(glass, frame):
    img = canvas()
    for y in range(4, 14):
        for x in range(4, 12):
            put(img, x, y, frame if x in (4, 11) or y in (4, 13) else glass)
    for x in range(6, 10):
        put(img, x, 2, frame)
        put(img, x, 3, frame)
    for (x, y) in [(7, 7), (8, 8), (7, 9), (8, 10)]:
        put(img, x, y, "e8ffff")
    return outline(img)

def art_tear(c1, c2):
    img = canvas()
    for y in range(3, 14):
        w = max(0, min((y - 3) * 0.7, 4.5 - max(0, y - 10) * 1.2))
        for x in range(16):
            if abs(x - 7.5) <= w:
                put(img, x, y, c1 if x > 7 else c2)
    put(img, 6, 8, "ffffff")
    put(img, 6, 9, "ffffff")
    return outline(img)

def art_pearl(c1, c2):
    img = art_orb(c1, c2, shade(hexc(c2), 0.7))
    for (x, y) in [(5, 5), (6, 5), (5, 6)]:
        put(img, x, y, "ffffff")
    for (x, y) in [(7, 11), (8, 4)]:
        put(img, x, y, "fff7ff")
    return img

def art_shell(c1, c2):
    img = canvas()
    for y in range(3, 14):
        for x in range(2, 14):
            if y < 9 and abs(x - 7.5) <= 2 + (y - 3):
                put(img, x, y, c1 if (x + y) % 4 else c2)
            elif y >= 9 and 3 <= x <= 12:
                put(img, x, y, shade(hexc(c1), 0.75) if y % 2 else c2)
    return outline(img)

def art_elytra(c1, c2, c3):
    img = canvas()
    for y in range(1, 15):
        for x in range(1, 15):
            left = x < 8
            dx = (x - 1) if left else (14 - x)
            if dx >= 0 and y >= 1 + dx * 0.3 and y <= 14 - (6 - dx) * 0.8 + (dx * 0.5):
                put(img, x, y, c1 if (x + 2 * y) % 5 else c2)
    for y in range(2, 13, 3):
        put(img, 4, y, c3)
        put(img, 11, y, c3)
    return outline(img)

def art_spear(shaft, head, glow):
    img = canvas()
    for i in range(11):
        put(img, 1 + i, 14 - i, shaft)
    for (x, y) in [(11, 4), (12, 3), (13, 2), (14, 1), (12, 4), (13, 3), (11, 3), (12, 2), (13, 1), (15, 0)]:
        put(img, x, y, head)
    for (x, y) in [(12, 3), (13, 2)]:
        put(img, x, y, glow)
    return outline(img)

def art_heart(c1, c2, glow):
    img = canvas()
    for y in range(16):
        for x in range(16):
            t = ((x - 7.5) / 6) ** 2 + ((y - 7) / 6) ** 2
            heart = (x - 7.5) ** 2 + ((y - 7.5) * 1.15) ** 2 < 36 and not (y < 6 and abs(x - 7.5) < 1.5)
            if heart:
                put(img, x, y, glow if t < 0.15 else (c1 if (x * y) % 5 else c2))
    return outline(img)

def art_halo(c1, c2):
    img = canvas()
    for y in range(16):
        for x in range(16):
            d = math.hypot((x - 7.5), (y - 7.5) * 2.2)
            if 4.8 < d < 7.4:
                put(img, x, y, c1 if (x + y) % 3 else c2)
    return outline(img, "1a0f24")

def art_fungus_stick():
    img = canvas()
    for i in range(10):
        put(img, 2 + i, 14 - i, "6b4a2a")
    for (x, y) in [(12, 4), (13, 5), (13, 6), (12, 7)]:
        put(img, x, y, "cfcfcf")
    for y in range(7, 12):
        for x in range(11, 15):
            if abs(x - 12.5) + abs(y - 9) < 3:
                put(img, x, y, "b02a2a" if (x + y) % 2 else "e04a3a")
    return outline(img)

def art_shield(face, rim, sigil):
    img = canvas()
    for y in range(1, 15):
        for x in range(2, 14):
            if y < 11 or abs(x - 7.5) < (15 - y) * 1.2:
                put(img, x, y, rim if x in (2, 13) or y == 1 else face)
    for y in range(4, 11):
        put(img, 7, y, sigil)
        put(img, 8, y, sigil)
    for x in range(5, 11):
        put(img, x, 6, sigil)
    return outline(img)

ITEMS = {
    # stub signature gear (core-owned)
    "challengers_cleaver": lambda: art_sword("c9d6e3", "f2f7ff", "e0a63a", "5a3a22"),
    "aegis_of_the_ladder": lambda: art_shield("3a5a8a", "c9a24a", "f2e6b0"),
    "halo_of_the_outer_end": lambda: art_halo("f2e9ff", "c9a2ff"),
    # grunthar
    "tyrants_tusk": lambda: art_axe("f2c43a", "fff0a0", "5a3a22"),
    "ashtusk_harness": lambda: art_harness("6a3a1a", "f2c43a", "a8441a"),
    "ashtusk_gland": lambda: art_gland("c4585a", "8a2a30"),
    "gold_bomb": art_bomb,
    "crimson_fungus_on_a_stick": art_fungus_stick,
    # solaxis
    "ninefold_rod": lambda: art_rod("ffd84a", "ffbf2a", "fff7d0"),
    "pyre_crown": lambda: art_crown("e8a21e", "ff4a1a", "ffd24a"),
    "sun_orb": lambda: art_orb("fffbe0", "ffb627", "ff6a10"),
    # mournmaw
    "lament_harness": lambda: art_harness("3a5a66", "9ff6ff", "6fd8e8"),
    "soulwick_lantern": lambda: art_lantern("5fd8e8", "3a3f4a"),
    "mournmaws_blessing": lambda: art_tear("bff8ff", "7fe6f2"),
    "mournmaws_tear": lambda: art_tear("e8fbff", "a8d8e0"),
    "soul_tear": lambda: art_tear("6fe8ff", "3ab8d8"),
    "soul_fireball": lambda: art_orb("e8ffff", "5fd8e8", "1f8a9a"),
    # ossyra
    "inverted_crown": lambda: art_crown("a678c8", "f2e9ff", "e8d8ff"),
    "gravity_pearl": lambda: art_pearl("f0d8ff", "a678c8"),
    "matriarchs_shell": lambda: art_shell("9a6ab8", "6a4288"),
    # aphelion
    "void_scale_elytra": lambda: art_elytra("2a1a3a", "5a2a8a", "c9a2ff"),
    "wyrmfang": lambda: art_spear("2a1f33", "6a3aa8", "e0c0ff"),
    "void_breath": lambda: art_orb("f0d8ff", "a050ff", "4a1a7a"),
    "void_scale": lambda: art_shell("3a1a5a", "c9a2ff"),
}

# blocks: name -> (texture painter, model kind)
def block_noise(colors, weights=None, border=None, alpha=255, seed=0):
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = hexc(rng.choices(colors, weights=weights)[0], alpha)
            if border and (x in (0, 15) or y in (0, 15)):
                c = hexc(border, alpha)
            img.putpixel((x, y), c)
    return img

def tex_seal():
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            hexline = (x + y) % 8 == 0 or (x - y) % 8 == 0
            a = 170 if edge else (120 if hexline else 70)
            img.putpixel((x, y), (90, 230, 255, a) if not edge else (170, 250, 255, a))
    return img

def tex_throne():
    img = block_noise(["f2c43a", "e8b72a", "ffd85a", "c99a1a"], [3, 3, 1, 1], seed=3)
    for x in range(16):
        img.putpixel((x, 0), hexc("2a2026"))
        img.putpixel((x, 15), hexc("2a2026"))
    for (x, y) in [(7, 6), (8, 6), (7, 7), (8, 7)]:
        img.putpixel((x, y), hexc("c4283a"))
    return img

def tex_core():
    img = block_noise(["3a0e05", "5a1608", "8a2a0c"], [2, 3, 2], seed=4)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 3.2: img.putpixel((x, y), hexc("fff0a0"))
            elif d < 5: img.putpixel((x, y), hexc("ffa23a"))
            elif abs(d - 6.5) < 0.6: img.putpixel((x, y), hexc("ff6a10"))
    return img

def tex_ember():
    img = block_noise(["ffd24a", "ffe88a", "ffb02e"], seed=5)
    return img

def tex_pedestal():
    img = block_noise(["a678c8", "9a6ab8", "b48ad2"], [3, 2, 1], border="6a4288", seed=6)
    for y in range(5, 11):
        for x in range(5, 11):
            img.putpixel((x, y), hexc("2a1a3a"))
    return img

def tex_altar():
    img = block_noise(["140c1c", "1f1229", "2a1a3a"], seed=7)
    for (x, y) in [(3, 3), (12, 3), (3, 12), (12, 12), (7, 2), (8, 13), (2, 8), (13, 7)]:
        img.putpixel((x, y), hexc("b060ff"))
    return img

def tex_heart():
    img = block_noise(["1a0f24", "2a1a3a", "3a1a5a"], seed=8)
    for y in range(16):
        for x in range(16):
            if math.hypot(x - 7.5, y - 7.5) < 3.5:
                img.putpixel((x, y), hexc("d8a8ff"))
    return img

BLOCKS = {
    "arena_seal": (tex_seal, "cube"),
    "gilded_throne": (tex_throne, "cube"),
    "pyre_cradle_core": (tex_core, "cube"),
    "solaxis_ember": (tex_ember, "ember"),
    "matriarch_pedestal": (tex_pedestal, "cube"),
    "halo_altar": (tex_altar, "cube"),
    "aphelion_heart": (tex_heart, "cube"),
}

# equipment textures (humanoid head layer, 64x32) for the crowns and halo
def equip_crown(base, gem, spike):
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    faces = [(0, 8), (8, 8), (16, 8), (24, 8)]
    for (fx, fy) in faces:
        for x in range(8):
            for y in range(0, 3):
                img.putpixel((fx + x, fy + y), hexc(base))
            if x in (0, 3, 4, 7):
                img.putpixel((fx + x, fy), hexc(spike))
        img.putpixel((fx + 2, fy + 1), hexc(gem))
        img.putpixel((fx + 5, fy + 1), hexc(gem))
    # top rim only
    for x in range(8):
        for y in range(8):
            if x in (0, 7) or y in (0, 7):
                img.putpixel((8 + x, y), hexc(base))
    return img

def equip_halo():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    for x in range(8):
        for y in range(8):
            if x in (0, 7) or y in (0, 7):
                img.putpixel((8 + x, y), hexc("f2e9ff"))
    return img

def equip_wings():
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    rng = random.Random(9)
    for y in range(0, 22):
        for x in range(22, 46):
            c = rng.choice(["2a1a3a", "3a2050", "5a2a8a", "1f1229"])
            if (x + y) % 6 == 0:
                c = "c9a2ff"
            img.putpixel((x, y), hexc(c))
    return img

LANG = {
    "itemGroup.elsewhere.nether_end_bosses": "Elsewhere: Nether & End Bosses",
    "entity.elsewhere.grunthar": "Grunthar the Gilded Tyrant",
    "entity.elsewhere.ashtusk": "Ashtusk",
    "entity.elsewhere.solaxis": "Solaxis, the Ninefold Pyre",
    "entity.elsewhere.mournmaw": "Mournmaw, the Grieving Ghast",
    "entity.elsewhere.ossyra": "Ossyra, Mother of Shells",
    "entity.elsewhere.aphelion": "Aphelion, the Coil Beyond the End",
    "entity.elsewhere.gold_bomb": "Gold Bomb",
    "entity.elsewhere.pyre_bolt": "Pyre Bolt",
    "entity.elsewhere.sun_orb": "Sun Orb",
    "entity.elsewhere.soul_tear": "Soul Tear",
    "entity.elsewhere.soul_fireball": "Soul Fireball",
    "entity.elsewhere.matriarch_bullet": "Matriarch Bullet",
    "entity.elsewhere.void_breath": "Void Breath",
    "entity.elsewhere.rift_gateway": "Rift Gateway",
    "entity.elsewhere.pyre_shard": "Pyre Shard",
    "entity.elsewhere.gilded_enforcer": "Gilded Enforcer",
    "entity.elsewhere.mourner": "Mourner",
    "entity.elsewhere.shulkling": "Shulkling",
    "entity.elsewhere.voidborn": "Voidborn",
    "item.elsewhere.challengers_cleaver": "Challenger's Cleaver",
    "item.elsewhere.aegis_of_the_ladder": "Aegis of the Ladder",
    "item.elsewhere.halo_of_the_outer_end": "Halo of the Outer End",
    "item.elsewhere.tyrants_tusk": "Tyrant's Tusk",
    "item.elsewhere.ashtusk_harness": "Ashtusk Harness",
    "item.elsewhere.ashtusk_gland": "Ashtusk Gland",
    "item.elsewhere.gold_bomb": "Gold Bomb",
    "item.elsewhere.crimson_fungus_on_a_stick": "Crimson Fungus on a Stick",
    "item.elsewhere.ninefold_rod": "Ninefold Rod",
    "item.elsewhere.pyre_crown": "Pyre Crown",
    "item.elsewhere.sun_orb": "Sun Orb",
    "item.elsewhere.lament_harness": "Lament Harness",
    "item.elsewhere.soulwick_lantern": "Soulwick Lantern",
    "item.elsewhere.mournmaws_blessing": "Mournmaw's Blessing",
    "item.elsewhere.mournmaws_tear": "Mournmaw's Tear",
    "item.elsewhere.soul_tear": "Soul Tear",
    "item.elsewhere.soul_fireball": "Soul Fireball",
    "item.elsewhere.inverted_crown": "Inverted Crown",
    "item.elsewhere.gravity_pearl": "Gravity Pearl",
    "item.elsewhere.matriarchs_shell": "Matriarch's Shell",
    "item.elsewhere.void_scale_elytra": "Void-Scale Elytra",
    "item.elsewhere.wyrmfang": "Wyrmfang",
    "item.elsewhere.void_breath": "Void Breath",
    "item.elsewhere.void_scale": "Void Scale",
    "block.elsewhere.arena_seal": "Arena Seal",
    "block.elsewhere.gilded_throne": "Gilded Throne",
    "block.elsewhere.pyre_cradle_core": "Pyre Cradle Core",
    "block.elsewhere.solaxis_ember": "Solaxis Ember",
    "block.elsewhere.matriarch_pedestal": "Matriarch Pedestal",
    "block.elsewhere.halo_altar": "Halo Altar",
    "block.elsewhere.aphelion_heart": "Aphelion Heart",
    "boss.elsewhere.phase": "Phase %s",
    "boss.elsewhere.dropped_out": "You left the arena too long and dropped out of the fight.",
    "boss.elsewhere.halo_return": "The Halo catches you: a free return to the ledge.",
    "boss.elsewhere.loot": "Your boss loot (%s) is at your feet. Only you can pick it up.",
    "boss.elsewhere.void_return": "Returned to the ledge (-8 HP).",
    "boss.elsewhere.needs_dragon": "The pedestal stays cold. The Ender Dragon still lives.",
    "boss.elsewhere.needs_crystals": "The altar needs 4 end crystals around the spire.",
    "boss.elsewhere.gravity_flip": "▲ GRAVITY FLIPS ▲",
    "boss.elsewhere.mercy": "Mournmaw calms. The basin is quiet.",
    "boss.elsewhere.mercy_failed": "The dried ghast was lost.",
    "boss.elsewhere.greed": "Grunthar can't resist the gold!",
    "subtitles.elsewhere.music": "Boss music plays",
    "death.attack.elsewhere.boss_melee": "%1$s was crushed by %2$s",
    "death.attack.elsewhere.boss_projectile": "%1$s was shot down by %2$s",
    "death.attack.elsewhere.boss_blast": "%1$s was blown apart by %2$s",
    "death.attack.elsewhere.boss_fire": "%1$s was burned to ash by %2$s",
    "death.attack.elsewhere.boss_magic": "%1$s dissolved in %2$s's breath",
    "death.attack.elsewhere.void_return": "%1$s fell out of the arena one time too many",
}
ATTACKS = {
    "grunthar": {"charge": "Ashtusk Charge", "tusk_toss": "Tusk Toss", "gold_bombs": "Gold Bombs", "axe_cleave": "Golden Axe", "call_enforcers": "Call the Enforcers", "molten_hoard": "Molten Hoard", "greed": "Greed!"},
    "ashtusk": {"charge": "Ashtusk Charge", "tusk_toss": "Tusk Toss"},
    "solaxis": {"rod_volley": "Rod Volley", "solar_flare": "Solar Flare", "eclipse_orb": "Eclipse Core", "supernova": "SUPERNOVA", "supernova_timer": "Supernova: deal damage or take cover!", "supernova_fizzled": "Supernova fizzled"},
    "mournmaw": {"wail": "Wail", "tear_rain": "Tear Rain", "soul_fireball": "Soul Fireballs", "chain_whip": "Chain Whip", "mourners": "Mourners Rise", "grief_storm": "Grief Storm"},
    "ossyra": {"bullet_storm": "Bullet Storm", "shell_slam": "Shell Slam", "gravity_flip": "Gravity Flip", "shulklings": "Shulklings", "void_bloom": "Void Bloom", "surface_swap": "Surface Swap"},
    "aphelion": {"coil": "Coil", "void_breath": "Void Breath", "bite": "Bite", "devour_dive": "Devour", "rift_emerge": "Rift Emergence", "voidborn": "Voidborn", "collapse": "Collapse", "crystal_feast": "Crystal Feast"},
}
for boss, attacks in ATTACKS.items():
    for k, v in attacks.items():
        LANG["attack.elsewhere.%s.%s" % (boss, k)] = v

MUSIC = {
    "grunthar": "minecraft:music/game/nether/crimson_forest/chrysopoeia",
    "solaxis": "minecraft:music/game/nether/nether_wastes/rubedo",
    "mournmaw": "minecraft:music/game/nether/soulsand_valley/so_below",
    "ossyra": "minecraft:music/game/end/alpha",
    "aphelion": "minecraft:music/game/end/boss",
}

DAMAGE = {
    "boss_melee": {"exhaustion": 0.1, "message_id": "elsewhere.boss_melee", "scaling": "never"},
    "boss_projectile": {"exhaustion": 0.1, "message_id": "elsewhere.boss_projectile", "scaling": "never"},
    "boss_blast": {"exhaustion": 0.1, "message_id": "elsewhere.boss_blast", "scaling": "never"},
    "boss_fire": {"exhaustion": 0.1, "message_id": "elsewhere.boss_fire", "scaling": "never", "effects": "burning"},
    "boss_magic": {"exhaustion": 0.0, "message_id": "elsewhere.boss_magic", "scaling": "never"},
    "void_return": {"exhaustion": 0.0, "message_id": "elsewhere.void_return", "scaling": "never"},
}
DAMAGE_TAGS = {
    "is_projectile": ["elsewhere:boss_projectile"],
    "is_explosion": ["elsewhere:boss_blast"],
    "is_fire": ["elsewhere:boss_fire"],
    "bypasses_armor": ["elsewhere:boss_magic", "elsewhere:void_return"],
    "bypasses_shield": ["elsewhere:boss_magic", "elsewhere:void_return"],
    "no_knockback": ["elsewhere:boss_magic", "elsewhere:void_return"],
    "bypasses_enchantments": ["elsewhere:void_return"],
}

# boss id -> (placed feature id, rarity in chunks, placement step note)
ARENAS = {
    "grunthar": ("grunthar_treasure_hall", 520),
    "solaxis": ("solaxis_pyre_cradle", 480),
    "mournmaw": ("mournmaw_weeping_basin", 200),
    "ossyra": ("ossyra_matriarch_spire", 320),
    "aphelion": ("aphelion_shattered_halo", 420),
}

def main():
    for name, fn in ITEMS.items():
        fn().save(os.path.join(A, "textures", "item", name + ".png")) if os.path.isdir(os.path.join(A, "textures", "item")) else None
        os.makedirs(os.path.join(A, "textures", "item"), exist_ok=True)
        fn().save(os.path.join(A, "textures", "item", name + ".png"))
        if name == "aegis_of_the_ladder":
            # shields use the vanilla special shield renderer (banner-capable) until the core ships its own model
            wjson(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:condition", "property": "minecraft:using_item",
                "on_false": {"type": "minecraft:special", "base": "minecraft:item/shield", "model": {"type": "minecraft:shield"}},
                "on_true": {"type": "minecraft:special", "base": "minecraft:item/shield_blocking", "model": {"type": "minecraft:shield"}}}})
            continue
        handheld = name in ("challengers_cleaver", "tyrants_tusk", "ninefold_rod", "wyrmfang", "crimson_fungus_on_a_stick")
        wjson(os.path.join(A, "models", "item", name + ".json"), {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": {"layer0": "elsewhere:item/" + name}})
        wjson(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "elsewhere:item/" + name}})
    for name, (fn, kind) in BLOCKS.items():
        os.makedirs(os.path.join(A, "textures", "block"), exist_ok=True)
        fn().save(os.path.join(A, "textures", "block", name + ".png"))
        if kind == "ember":
            wjson(os.path.join(A, "models", "block", name + ".json"), {"parent": "minecraft:block/block", "textures": {"all": "elsewhere:block/" + name, "particle": "elsewhere:block/" + name},
                "elements": [{"from": [5, 0, 5], "to": [11, 6, 11], "faces": {f: {"texture": "#all", "uv": [5, 5, 11, 11]} for f in ("north", "south", "east", "west", "up", "down")}}]})
        else:
            tex = {"sprite": "elsewhere:block/" + name, "force_translucent": True} if name == "arena_seal" else "elsewhere:block/" + name
            wjson(os.path.join(A, "models", "block", name + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": tex}})
        wjson(os.path.join(A, "blockstates", name + ".json"), {"variants": {"": {"model": "elsewhere:block/" + name}}})
        wjson(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": "elsewhere:block/" + name}})
        wjson(os.path.join(D, "loot_table", "blocks", name + ".json"), {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "elsewhere:" + name}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    # equipment
    for name, img in (("pyre_crown", equip_crown("e8a21e", "ff4a1a", "ffd24a")), ("inverted_crown", equip_crown("a678c8", "f2e9ff", "e8d8ff")), ("halo_of_the_outer_end", equip_halo())):
        os.makedirs(os.path.join(A, "textures", "entity", "equipment", "humanoid"), exist_ok=True)
        img.save(os.path.join(A, "textures", "entity", "equipment", "humanoid", name + ".png"))
        wjson(os.path.join(A, "equipment", name + ".json"), {"layers": {"humanoid": [{"texture": "elsewhere:" + name}]}})
    os.makedirs(os.path.join(A, "textures", "entity", "equipment", "wings"), exist_ok=True)
    equip_wings().save(os.path.join(A, "textures", "entity", "equipment", "wings", "void_scale_elytra.png"))
    wjson(os.path.join(A, "equipment", "void_scale_elytra.json"), {"layers": {"wings": [{"texture": "elsewhere:void_scale_elytra", "use_player_texture": True}]}})
    wjson(os.path.join(A, "lang", "en_us.json"), LANG)
    wjson(os.path.join(A, "sounds.json"), {"music.boss." + b: {"category": "music", "subtitle": "subtitles.elsewhere.music", "sounds": [{"name": f, "stream": True}]} for b, f in MUSIC.items()})
    for name, obj in DAMAGE.items():
        wjson(os.path.join(D, "damage_type", name + ".json"), obj)
    for tag, vals in DAMAGE_TAGS.items():
        wjson(os.path.join(MC, "tags", "damage_type", tag + ".json"), {"replace": False, "values": vals})
    for boss, (placed, rarity) in ARENAS.items():
        wjson(os.path.join(D, "worldgen", "feature", placed + ".json"), {"type": "elsewhere:nether_end_boss_arena", "boss": boss})
        wjson(os.path.join(D, "worldgen", "placed_feature", placed + ".json"), {"feature": "elsewhere:" + placed,
            "placement": [{"type": "minecraft:rarity_filter", "chance": rarity}, {"type": "minecraft:in_square"},
                          {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 40}, "max_inclusive": {"absolute": 80}}},
                          {"type": "minecraft:biome"}]})
    wjson(os.path.join(D, "recipe", "void_scale_elytra.json"), {"type": "minecraft:smithing_transform", "template": "elsewhere:void_scale",
        "base": "minecraft:netherite_chestplate", "addition": "minecraft:elytra", "result": {"id": "elsewhere:void_scale_elytra"}})
    wjson(os.path.join(D, "recipe", "crimson_fungus_on_a_stick.json"), {"type": "minecraft:crafting_shaped", "category": "misc",
        "pattern": ["# ", " X"], "key": {"#": "minecraft:fishing_rod", "X": "minecraft:crimson_fungus"}, "result": {"id": "elsewhere:crimson_fungus_on_a_stick"}})
    print("assets written")

if __name__ == "__main__":
    main()
