#!/usr/bin/env python3
"""Generates every asset and data file of the overworld_bosses module: item sprites (procedural pixel art), block
textures, the tentacle texture, item/block models, blockstates, lang, sounds.json, damage types, tags, loot tables.
Run: S/venv/bin/python tools/gen_assets.py"""
import json, os, random, math
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'elsewhere')
D = os.path.join(ROOT, 'data', 'elsewhere')
DM = os.path.join(ROOT, 'data', 'minecraft')

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)

def rgb(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255, 255)

def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)

def sprite(name, kind, c1, c2=None, c3=None):
    rnd = random.Random(name)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    a, b = rgb(c1), rgb(c2 if c2 is not None else c1)
    c = rgb(c3) if c3 is not None else shade(a, 0.55)
    o = shade(a, 0.35)
    if kind == 'sword':
        for i in range(10):
            d.rectangle([4 + i, 11 - i, 5 + i, 12 - i], fill=a)
            d.point((5 + i, 11 - i), fill=shade(a, 1.3))
        d.line([2, 10, 6, 14], fill=b, width=2)
        d.line([1, 14, 3, 12], fill=c, width=2)
    elif kind == 'khopesh':
        for i in range(7):
            d.rectangle([3 + i, 11 - i, 4 + i, 12 - i], fill=a)
        d.arc([7, 1, 15, 9], 180, 360, fill=a, width=2)
        d.line([1, 14, 4, 11], fill=b, width=2)
    elif kind == 'scythe':
        d.line([3, 14, 10, 2], fill=b, width=2)
        d.arc([2, 0, 14, 8], 200, 340, fill=a, width=2)
    elif kind == 'helmet':
        d.rectangle([3, 4, 12, 10], fill=a)
        d.rectangle([3, 9, 5, 12], fill=a)
        d.rectangle([10, 9, 12, 12], fill=a)
        d.rectangle([5, 7, 10, 9], fill=(0, 0, 0, 0))
        d.line([3, 4, 12, 4], fill=shade(a, 1.3))
        if c2 is not None:
            d.rectangle([4, 5, 11, 6], fill=b)
    elif kind == 'crown':
        d.rectangle([3, 8, 12, 11], fill=a)
        for x in (3, 6, 9, 12):
            d.rectangle([x - 1, 5, x, 8], fill=a)
        d.ellipse([6, 2, 9, 5], fill=b)
    elif kind == 'pumpkin':
        d.ellipse([2, 3, 13, 14], fill=a, outline=o)
        d.rectangle([7, 1, 8, 4], fill=rgb(0x3B6E2A))
        d.polygon([(4, 7), (6, 7), (5, 9)], fill=b)
        d.polygon([(9, 7), (11, 7), (10, 9)], fill=b)
        d.rectangle([5, 10, 10, 11], fill=b)
    elif kind == 'boots':
        d.rectangle([3, 3, 6, 11], fill=a)
        d.rectangle([9, 3, 12, 11], fill=a)
        d.rectangle([1, 11, 6, 13], fill=shade(a, 0.8))
        d.rectangle([9, 11, 14, 13], fill=shade(a, 0.8))
    elif kind == 'legs':
        d.rectangle([3, 2, 12, 5], fill=a)
        d.rectangle([3, 5, 6, 14], fill=a)
        d.rectangle([9, 5, 12, 14], fill=a)
        d.line([4, 6, 4, 13], fill=b)
        d.line([11, 6, 11, 13], fill=b)
    elif kind == 'mace':
        d.line([3, 14, 9, 8], fill=b, width=2)
        d.ellipse([7, 2, 14, 9], fill=a, outline=o)
        d.point((10, 4), fill=shade(a, 1.4))
    elif kind == 'trident':
        d.line([3, 14, 11, 4], fill=b, width=2)
        d.line([9, 2, 14, 7], fill=a, width=2)
        d.line([12, 1, 12, 4], fill=a)
        d.line([15, 4, 12, 4], fill=a)
    elif kind == 'rod':
        d.line([2, 14, 13, 2], fill=b, width=2)
        d.line([13, 2, 13, 12], fill=a)
        d.ellipse([11, 11, 15, 15], fill=a)
    elif kind == 'shield':
        d.polygon([(3, 2), (12, 2), (12, 9), (7, 14), (3, 9)], fill=a, outline=o)
        d.line([7, 3, 7, 12], fill=b, width=2)
        d.line([4, 6, 11, 6], fill=b)
    elif kind == 'ankh':
        d.ellipse([5, 1, 10, 7], outline=a, width=2)
        d.rectangle([7, 6, 8, 14], fill=a)
        d.rectangle([3, 7, 12, 8], fill=a)
        d.point((6, 2), fill=b)
    elif kind == 'orb':
        d.ellipse([3, 3, 12, 12], fill=a, outline=o)
        d.ellipse([5, 5, 10, 10], fill=b)
        d.ellipse([7, 6, 8, 8], fill=c)
    elif kind == 'eye':
        d.ellipse([2, 4, 13, 11], fill=a, outline=o)
        d.ellipse([6, 5, 9, 10], fill=b)
        d.rectangle([7, 5, 7, 10], fill=c)
    elif kind == 'shell':
        d.ellipse([2, 4, 13, 13], fill=a, outline=o)
        for r in range(2, 6):
            d.arc([7 - r, 8 - r, 7 + r, 8 + r], 0, 300, fill=b)
        d.polygon([(12, 5), (15, 2), (14, 7)], fill=a)
    elif kind == 'food':
        d.ellipse([3, 5, 12, 12], fill=a, outline=o)
        d.ellipse([5, 6, 9, 9], fill=shade(a, 1.25))
        if c2 is not None:
            d.line([10, 3, 13, 1], fill=b, width=2)
    elif kind == 'legs_food':
        d.line([3, 13, 9, 6], fill=a, width=3)
        d.line([6, 13, 12, 6], fill=a, width=3)
        d.ellipse([9, 3, 14, 8], fill=shade(a, 1.2))
    elif kind == 'loaf':
        d.rounded_rectangle([2, 6, 13, 12], 3, fill=a, outline=o)
        for x in (5, 8, 11):
            d.line([x, 7, x - 1, 10], fill=shade(a, 1.3))
    elif kind == 'bottle':
        d.rectangle([6, 2, 9, 4], fill=rgb(0x8B5A2B))
        d.rectangle([6, 4, 9, 6], fill=(200, 220, 230, 200))
        d.ellipse([3, 6, 12, 14], fill=a, outline=o)
        d.point((5, 8), fill=(255, 255, 255, 255))
    elif kind == 'seeds':
        for i in range(6):
            x, y = rnd.randint(3, 11), rnd.randint(3, 11)
            d.ellipse([x, y, x + 2, y + 3], fill=a if i % 2 else b)
    elif kind == 'feather':
        d.line([3, 14, 12, 2], fill=b)
        d.polygon([(12, 2), (5, 8), (4, 12), (9, 9), (13, 4)], fill=a)
    elif kind == 'paper':
        d.polygon([(3, 3), (12, 2), (13, 12), (4, 13)], fill=a, outline=o)
        for y in (5, 7, 9):
            d.line([5, y, 11, y], fill=b)
    elif kind == 'bud':
        d.ellipse([4, 3, 11, 12], fill=a, outline=o)
        d.line([7, 12, 7, 15], fill=b)
        d.point((6, 5), fill=shade(a, 1.4))
    elif kind == 'glob':
        d.ellipse([4, 4, 12, 12], fill=a)
        for i in range(5):
            x, y = rnd.randint(5, 10), rnd.randint(5, 10)
            d.point((x, y), fill=b)
    elif kind == 'shard':
        d.polygon([(8, 1), (12, 8), (8, 15), (4, 8)], fill=a, outline=o)
        d.line([8, 3, 8, 13], fill=b)
    elif kind == 'flame_seed':
        d.ellipse([5, 6, 11, 13], fill=b)
        d.polygon([(8, 1), (12, 8), (4, 8)], fill=a)
    elif kind == 'token':
        d.ellipse([2, 2, 13, 13], fill=a, outline=o)
        d.ellipse([4, 4, 11, 11], outline=b)
        d.polygon([(7, 5), (9, 8), (7, 11), (5, 8)], fill=b)
    elif kind == 'ink':
        d.ellipse([3, 5, 12, 14], fill=a, outline=o)
        d.rectangle([6, 2, 9, 5], fill=b)
        d.point((5, 8), fill=c)
    # noise + outline
    px = im.load()
    for y in range(16):
        for x in range(16):
            p = px[x, y]
            if p[3] > 0:
                k = 0.9 + rnd.random() * 0.2
                px[x, y] = shade(p, k)[:3] + (p[3],)
    out = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    po = out.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0 and any(0 <= x + dx < 16 and 0 <= y + dy < 16 and px[x + dx, y + dy][3] > 0 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                po[x, y] = (20, 16, 24, 255)
            else:
                po[x, y] = px[x, y]
    path = os.path.join(A, 'textures', 'item', name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    out.save(path)

ITEMS = {
    # id: (kind, c1, c2, c3, name, lore)
    'lilycrown': ('crown', 0x4E8A2E, 0xE8F070, None, 'Lilycrown'),
    'gulp_tongue': ('rod', 0xE07090, 0x6B4A2B, None, 'Gulp Tongue'),
    'frog_legs': ('legs_food', 0xD9A07A, None, None, 'Frog Legs'),
    'bufo_gland': ('glob', 0x9DB84A, 0x5A7A20, None, 'Bufo Gland'),
    'frogspawn_glob': ('glob', 0x6C8F5A, 0x1E2A18, None, 'Frogspawn Glob'),
    'harvest_scythe': ('scythe', 0xC8CDD2, 0x6B4A2B, None, 'Harvest Scythe'),
    'scarecrows_grin': ('pumpkin', 0xE08A1E, 0xFFE070, None, "Scarecrow's Grin"),
    'harvest_moon_seeds': ('seeds', 0xF2C14E, 0xC77B2A, None, 'Harvest Moon Seeds'),
    'harvest_loaf': ('loaf', 0xC9873E, None, None, 'Harvest Loaf'),
    'harvest_moon_bud': ('bud', 0x6FA83A, 0x3D6B22, None, 'Harvest Moon Bud'),
    'crow_feather': ('feather', 0x2A2530, 0x5A5060, None, 'Crow Feather'),
    'seed_fireball': ('flame_seed', 0xFF8A1E, 0xC77B2A, None, 'Seed Fireball'),
    'ankh_of_neferkhet': ('ankh', 0xF2C230, 0x2B4FB0, None, 'Ankh of Neferkhet'),
    'sunfire_khopesh': ('khopesh', 0xF2C230, 0x2B4FB0, None, 'Sunfire Khopesh'),
    'pharaohs_nemes': ('helmet', 0xF2C230, 0x2B4FB0, None, "Pharaoh's Nemes"),
    'royal_honey': ('bottle', 0xF2A81E, None, None, 'Royal Honey'),
    'linen_wrap': ('paper', 0xE8DCC0, 0xB8A888, None, 'Linen Wrap'),
    'rimeheart_maul': ('mace', 0x9FD8F5, 0x5A6470, None, 'Rimeheart Maul'),
    'rimewalker_boots': ('boots', 0x9AD8F5, None, None, 'Rimewalker Boots'),
    'glacier_water': ('bottle', 0x8FD0F0, None, None, 'Glacier Water'),
    'frost_shard': ('shard', 0xBFEAFF, 0x6FB8E0, None, 'Frost Shard'),
    'undertow': ('trident', 0x3FA89A, 0x2E4A5A, None, 'Undertow'),
    'kraken_ink_bomb': ('ink', 0x2A1A3A, 0x6B4A2B, 0x8A6AB0, 'Kraken Ink Bomb'),
    'abyssal_eye': ('eye', 0x2A3A6A, 0xF0D040, 0x101010, 'Abyssal Eye'),
    'siren_conch': ('shell', 0xE8C8B0, 0xB07A6A, None, 'Siren Conch'),
    'kraken_calamari': ('food', 0xE8D0E0, 0xB080C0, None, 'Kraken Calamari'),
    'challengers_cleaver': ('sword', 0xD0D8E0, 0x8A2A2A, 0x3A2A1A, "Challenger's Cleaver"),
    'aegis_of_the_ladder': ('shield', 0x7A8AA0, 0xF2C230, None, 'Aegis of the Ladder'),
    'phasewalker_greaves': ('legs', 0x4A3A6A, 0x9A7AE0, None, 'Phasewalker Greaves'),
    'boss_token': ('token', 0xB08A30, 0xF2E08A, None, 'Boss Token'),
}

BLOCK_ITEMS = {
    'gulpmother_shrine_cauldron': 'Croaking Shrine Cauldron',
    'neferkhet_sun_altar': 'Sun Altar',
    'neferkhet_sun_idol': 'Sun Idol',
    'frozen_heart': 'Frozen Heart',
    'vorlagh_capstan': 'Wreck Ring Capstan',
}

def block_tex(name, base, accent, pattern):
    rnd = random.Random(name)
    im = Image.new('RGBA', (16, 16))
    px = im.load()
    a, b = rgb(base), rgb(accent)
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(a, 0.85 + rnd.random() * 0.25)
    d = ImageDraw.Draw(im)
    if pattern == 'sun':
        d.ellipse([4, 4, 11, 11], fill=b)
        for i in range(8):
            ang = i * math.pi / 4
            d.line([8 + math.cos(ang) * 5, 8 + math.sin(ang) * 5, 8 + math.cos(ang) * 7, 8 + math.sin(ang) * 7], fill=b)
    elif pattern == 'idol':
        d.rectangle([4, 2, 11, 13], fill=b)
        d.rectangle([6, 5, 7, 6], fill=(40, 200, 255, 255))
        d.rectangle([9, 5, 10, 6], fill=(40, 200, 255, 255))
        d.rectangle([3, 0, 12, 2], fill=rgb(0x2B4FB0))
    elif pattern == 'heart':
        d.polygon([(8, 13), (2, 7), (4, 3), (8, 6), (12, 3), (14, 7)], fill=b)
        d.point((6, 5), fill=(255, 255, 255, 255))
    elif pattern == 'rim':
        d.rectangle([0, 0, 15, 1], fill=b)
        d.rectangle([0, 14, 15, 15], fill=b)
    path = os.path.join(A, 'textures', 'block', name + '.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)

def tentacle_tex():
    rnd = random.Random(7)
    im = Image.new('RGBA', (128, 128))
    px = im.load()
    for y in range(128):
        for x in range(128):
            k = 0.8 + rnd.random() * 0.3
            px[x, y] = shade(rgb(0x4A2A6A), k)
    d = ImageDraw.Draw(im)
    for i in range(70):
        x, y = rnd.randint(0, 124), rnd.randint(0, 124)
        d.ellipse([x, y, x + 3, y + 3], fill=rgb(0xC090D8), outline=rgb(0x2A1A3A))
    path = os.path.join(A, 'textures', 'entity', 'vorlagh_tentacle.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    im.save(path)

def main():
    lang = {}
    for iid, (kind, c1, c2, c3, nm) in ITEMS.items():
        sprite(iid, kind, c1, c2, c3)
        if iid in ('undertow',):
            pass
        model = {'parent': 'minecraft:item/handheld' if kind in ('sword', 'khopesh', 'scythe', 'mace', 'trident', 'rod') else 'minecraft:item/generated',
                 'textures': {'layer0': 'elsewhere:item/' + iid}}
        w(os.path.join(A, 'models', 'item', iid + '.json'), model)
        w(os.path.join(A, 'items', iid + '.json'), {'model': {'type': 'minecraft:model', 'model': 'elsewhere:item/' + iid}})
        lang['item.elsewhere.' + iid] = nm
    # block textures and models
    block_tex('neferkhet_sun_altar_top', 0xD8C890, 0xF2C230, 'sun')
    block_tex('neferkhet_sun_altar_side', 0xD8C890, 0x2B4FB0, 'rim')
    block_tex('neferkhet_sun_idol', 0xD8C890, 0xF2C230, 'idol')
    block_tex('frozen_heart', 0x9FD8F5, 0x2BE4FF, 'heart')
    block_tex('arena_seal', 0x2BE4FF, 0x2BE4FF, 'rim')
    seal = Image.new('RGBA', (16, 16))
    sp = seal.load()
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            sp[x, y] = (70, 225, 255, 230 if edge else 120 + ((x * 7 + y * 3) % 5) * 8)
    seal.save(os.path.join(A, 'textures', 'block', 'arena_seal.png'))
    tentacle_tex()
    blocks = {
        'gulpmother_shrine_cauldron': {'parent': 'minecraft:block/cauldron'},
        'neferkhet_sun_altar': {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'top': 'elsewhere:block/neferkhet_sun_altar_top', 'bottom': 'minecraft:block/sandstone_bottom', 'side': 'elsewhere:block/neferkhet_sun_altar_side'}},
        'neferkhet_sun_idol': {'parent': 'minecraft:block/cube_column', 'textures': {'end': 'minecraft:block/gold_block', 'side': 'elsewhere:block/neferkhet_sun_idol'}},
        'frozen_heart': {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'elsewhere:block/frozen_heart'}},
        'vorlagh_capstan': {'parent': 'minecraft:block/cube_column', 'textures': {'end': 'minecraft:block/stripped_spruce_log_top', 'side': 'minecraft:block/spruce_log'}},
        'arena_seal': {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'elsewhere:block/arena_seal'}},
    }
    for bid, model in blocks.items():
        w(os.path.join(A, 'models', 'block', bid + '.json'), model)
        w(os.path.join(A, 'blockstates', bid + '.json'), {'variants': {'': {'model': 'elsewhere:block/' + bid}}})
    w(os.path.join(A, 'blockstates', 'gulpmother_shrine_cauldron.json'), {'variants': {'slime=%d' % i: {'model': 'elsewhere:block/gulpmother_shrine_cauldron'} for i in range(4)}})
    w(os.path.join(A, 'blockstates', 'gulpmother_frogspawn.json'), {'variants': {'': {'model': 'minecraft:block/frogspawn'}}})
    for bid, nm in BLOCK_ITEMS.items():
        w(os.path.join(A, 'items', bid + '.json'), {'model': {'type': 'minecraft:model', 'model': 'elsewhere:block/' + bid}})
        lang['block.elsewhere.' + bid] = nm
    lang['block.elsewhere.arena_seal'] = 'Arena Seal'
    lang['block.elsewhere.gulpmother_frogspawn'] = 'Gulpmother Frogspawn'
    ents = {'gulpmother': 'Old Gulpmother, the Bog Sovereign', 'strawgrim': 'Strawgrim, the Hollow Shepherd', 'neferkhet': 'Neferkhet, the Sand-Bound King',
            'rimeback': 'Rimeback, the Avalanche Colossus', 'vorlagh': 'Vorlagh, the Tidewrack Kraken', 'vorlagh_tentacle': 'Vorlagh Tentacle',
            'strawgrim_head': "Strawgrim's Head", 'mire_biter': 'Mire Biter', 'murder_crow': 'Murder Crow', 'wrapped_sentinel': 'Wrapped Sentinel',
            'frostbound_revenant': 'Frostbound Revenant', 'tidewrack_drowned': 'Tidewrack Drowned', 'frogspawn_glob': 'Frogspawn Glob',
            'seed_fireball': 'Seed Fireball', 'sun_lance': 'Sun Lance', 'glacier_boulder': 'Glacier Boulder', 'frost_shard': 'Frost Shard',
            'ink_glob': 'Ink Glob', 'neferkhet_scarab': 'Scarab'}
    for e, nm in ents.items():
        lang['entity.elsewhere.' + e] = nm
    attacks = {'gulpmother': {'tongue_lash': 'Tongue Lash', 'belly_flop': 'Belly Flop', 'spawning_season': 'Spawning Season', 'devour': 'Devour'},
               'strawgrim': {'reap': 'Reap', 'furrow': 'Furrow', 'crow_murder': 'Crow Murder', 'fire_volley': 'Fire Volley', 'hay_fire': 'Hay Fire'},
               'neferkhet': {'sun_lance': 'Sunbeam Lance', 'quicksand': 'Quicksand', 'staff_swing': 'Staff Swing', 'sarcophagi': 'The Sarcophagi Open',
                             'curse_of_the_ankh': 'Curse of the Ankh', 'eclipse_lances': 'Eclipse Lances'},
               'rimeback': {'glacier_hurl': 'Glacier Hurl', 'frost_breath': 'Frost Breath', 'fist_slam': 'Fist Slam', 'avalanche': 'Avalanche',
                            'revenants': 'Frostbound Revenants', 'campfire_stomp': 'Stomp'},
               'vorlagh': {'tentacle_slam': 'Tentacle Slam', 'grab': 'Grab', 'ink_cloud': 'Ink Cloud', 'whirlpool': 'Whirlpool', 'boarders': 'Boarders', 'lightning': 'Abyssal Lightning'}}
    for boss, m in attacks.items():
        for k, v in m.items():
            lang['attack.elsewhere.%s.%s' % (boss, k)] = v
    phases = {'gulpmother': ['Lash', 'Spawning Season', 'Hungry'], 'strawgrim': ['Reap', 'Burning Effigy', 'Headless'],
              'neferkhet': ['Court', 'Sarcophagi', 'Eclipse'], 'rimeback': ['Hurl', 'Avalanche', 'Whiteout'], 'vorlagh': ['Tentacles', 'Undertow', 'Abyssal Wrath']}
    for boss, names in phases.items():
        for i, n in enumerate(names):
            lang['phase.elsewhere.%s.%d' % (boss, i + 1)] = 'Phase %d: %s' % (i + 1, n)
    lang.update({
        'attack.elsewhere.rimeback.avalanche_in': 'Avalanche in %s...',
        'bar.elsewhere.vorlagh.tentacles': 'Tentacles (%s of %s standing)',
        'message.elsewhere.gulpmother.swallowed': 'Swallowed! Press sneak to struggle free.',
        'message.elsewhere.gulpmother.not_night': 'The pool stays still. Come back at night.',
        'message.elsewhere.strawgrim.not_night': 'The scarecrow will not wake by daylight.',
        'message.elsewhere.strawgrim.not_full_moon': 'Strawgrim only answers under a full moon.',
        'message.elsewhere.strawgrim.no_farm': 'The scarecrow needs a farm field around it.',
        'message.elsewhere.neferkhet.not_noon': 'The altar waits for the noon sun.',
        'message.elsewhere.neferkhet.cursed': 'Curse of the Ankh! Stand in the sunbeam to cleanse it.',
        'message.elsewhere.neferkhet.cleansed': 'The sunbeam burns the curse away.',
        'message.elsewhere.vorlagh.no_wreck': 'The conch echoes, but nothing answers here. Blow it on the Wreck Ring deck.',
        'message.elsewhere.vorlagh.no_storm': 'The sea is too calm. Vorlagh rises only in a thunderstorm.',
        'itemGroup.elsewhere.overworld_bosses': 'Elsewhere: Overworld Bosses',
        'death.attack.elsewhere.boss_attack': '%1$s was struck down by %2$s',
        'death.attack.elsewhere.boss_attack.player': '%1$s was struck down by %2$s',
        'death.attack.elsewhere.boss_crush': '%1$s was crushed by %2$s',
        'death.attack.elsewhere.boss_crush.player': '%1$s was crushed by %2$s',
        'death.attack.elsewhere.boss_dot': '%1$s was slowly devoured by %2$s',
        'death.attack.elsewhere.boss_dot.player': '%1$s was slowly devoured by %2$s',
        'death.attack.elsewhere.boss_magic': '%1$s was smitten by %2$s',
        'death.attack.elsewhere.boss_magic.player': '%1$s was smitten by %2$s',
    })
    w(os.path.join(A, 'lang', 'en_us.json'), dict(sorted(lang.items())))
    # sounds: map onto pitched vanilla events; music onto vanilla tracks (placeholders)
    def ev(e, pitch=1.0, vol=1.0):
        return {'sounds': [{'name': e, 'type': 'event', 'pitch': pitch, 'volume': vol}]}
    sounds = {
        'arena.seal': ev('minecraft:block.beacon.activate', 0.7),
        'boss.gulpmother.croak': ev('minecraft:entity.frog.ambient', 0.45),
        'boss.gulpmother.gape': ev('minecraft:entity.frog.tongue', 0.5),
        'boss.gulpmother.tongue': ev('minecraft:entity.frog.tongue', 0.7),
        'boss.gulpmother.gulp': ev('minecraft:entity.frog.eat', 0.5),
        'boss.gulpmother.splat': ev('minecraft:entity.slime.squish', 0.4),
        'boss.strawgrim.caw': ev('minecraft:entity.parrot.imitate.phantom', 0.6),
        'boss.strawgrim.creak': ev('minecraft:entity.creaking.ambient', 0.7),
        'boss.strawgrim.sweep': ev('minecraft:entity.player.attack.sweep', 0.6),
        'boss.neferkhet.chant': ev('minecraft:entity.evoker.prepare_summon', 0.6),
        'boss.neferkhet.lance_charge': ev('minecraft:block.beacon.ambient', 1.6),
        'boss.neferkhet.lance_fire': ev('minecraft:entity.guardian.attack', 1.4),
        'boss.rimeback.groan': ev('minecraft:entity.ravager.roar', 0.5),
        'boss.vorlagh.groan': ev('minecraft:entity.elder_guardian.ambient', 0.5),
        'music.boss.gulpmother': ev('minecraft:music.overworld.swamp'),
        'music.boss.strawgrim': ev('minecraft:music_disc.mellohi'),
        'music.boss.neferkhet': ev('minecraft:music.overworld.desert'),
        'music.boss.rimeback': ev('minecraft:music.overworld.frozen_peaks'),
        'music.boss.vorlagh': ev('minecraft:music_disc.otherside'),
    }
    for k, v in sounds.items():
        v['subtitle'] = 'subtitles.elsewhere.' + k
        lang['subtitles.elsewhere.' + k] = k.split('.')[-1].replace('_', ' ').capitalize()
    w(os.path.join(A, 'sounds.json'), sounds)
    w(os.path.join(A, 'lang', 'en_us.json'), dict(sorted(lang.items())))
    # data: damage types and tags
    for dt in ('boss_attack', 'boss_crush', 'boss_dot', 'boss_magic'):
        w(os.path.join(D, 'damage_type', dt + '.json'), {'message_id': 'elsewhere.' + dt, 'scaling': 'never', 'exhaustion': 0.1})
    w(os.path.join(DM, 'tags', 'damage_type', 'bypasses_shield.json'), {'replace': False, 'values': ['elsewhere:boss_crush', 'elsewhere:boss_dot']})
    w(os.path.join(DM, 'tags', 'damage_type', 'bypasses_armor.json'), {'replace': False, 'values': ['elsewhere:boss_dot']})
    w(os.path.join(DM, 'tags', 'entity_type', 'undead.json'), {'replace': False, 'values': ['elsewhere:wrapped_sentinel', 'elsewhere:frostbound_revenant', 'elsewhere:tidewrack_drowned']})
    w(os.path.join(DM, 'tags', 'item', 'piglin_safe_armor.json'), {'replace': False, 'values': ['elsewhere:pharaohs_nemes']})
    w(os.path.join(DM, 'tags', 'item', 'freeze_immune_wearables.json'), {'replace': False, 'values': ['elsewhere:rimewalker_boots']})
    w(os.path.join(DM, 'tags', 'item', 'head_armor.json'), {'replace': False, 'values': ['elsewhere:pharaohs_nemes']})
    w(os.path.join(DM, 'tags', 'item', 'foot_armor.json'), {'replace': False, 'values': ['elsewhere:rimewalker_boots']})
    w(os.path.join(DM, 'tags', 'item', 'leg_armor.json'), {'replace': False, 'values': ['elsewhere:phasewalker_greaves']})
    w(os.path.join(DM, 'tags', 'item', 'swords.json'), {'replace': False, 'values': ['elsewhere:challengers_cleaver', 'elsewhere:sunfire_khopesh']})
    w(os.path.join(D, 'tags', 'item', 'glare_immune.json'), {'replace': False, 'values': ['elsewhere:pharaohs_nemes']})
    # minion loot tables
    def lt(entries):
        return {'type': 'minecraft:entity', 'pools': [{'rolls': 1, 'entries': [e]} for e in entries]}
    def item(i, mn=1, mx=1, chance=None):
        e = {'type': 'minecraft:item', 'name': i, 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': mn, 'max': mx}}]}
        if chance:
            e['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': chance}]
        return e
    w(os.path.join(D, 'loot_table', 'entities', 'mire_biter.json'), lt([item('minecraft:slime_ball', 0, 2)]))
    w(os.path.join(D, 'loot_table', 'entities', 'murder_crow.json'), lt([item('elsewhere:crow_feather', 0, 2)]))
    w(os.path.join(D, 'loot_table', 'entities', 'wrapped_sentinel.json'), lt([item('elsewhere:linen_wrap', 0, 2), item('minecraft:rotten_flesh', 0, 1)]))
    w(os.path.join(D, 'loot_table', 'entities', 'frostbound_revenant.json'), lt([item('minecraft:packed_ice', 0, 1), item('minecraft:arrow', 0, 2)]))
    w(os.path.join(D, 'loot_table', 'entities', 'tidewrack_drowned.json'), lt([item('minecraft:rotten_flesh', 0, 2), item('elsewhere:siren_conch', 1, 1, 0.08)]))

main()
