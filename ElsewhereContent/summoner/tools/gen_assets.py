#!/usr/bin/env python3
"""Generates every summoner asset and data file: item/entity/armor textures (procedural pixel art, armor and some
icons recoloured from vanilla shapes), item models, lang, tags, recipes. Run with a Python that has Pillow:
  <venv>/bin/python tools/gen_assets.py <path to vanilla assets/minecraft>"""
import json, os, random, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, '..', 'src', 'main', 'resources')
A = os.path.join(RES, 'assets', 'elsewhere')
D = os.path.join(RES, 'data', 'elsewhere')
VAN = sys.argv[1] if len(sys.argv) > 1 else None
random.seed(7)


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)


def hexc(h, a=255):
    h = h.lstrip('#')
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def lerp(c1, c2, t):
    return tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3)) + (255,)


def ramp(pal, t):
    t = max(0.0, min(1.0, t))
    seg = t * (len(pal) - 1)
    i = min(int(seg), len(pal) - 2)
    return lerp(pal[i], pal[i + 1], seg - i)


def recolor(src, pal, out, gamma=1.0):
    im = Image.open(src).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            l = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            px[x, y] = ramp(pal, l ** gamma)[:3] + (a,)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    im.save(out)
    return im


def jitter(c, n=12):
    d = random.randint(-n, n)
    return tuple(max(0, min(255, c[i] + d)) for i in range(3)) + (c[3] if len(c) > 3 else 255,)


# ---------------------------------------------------------------- palettes
HERD = [hexc('#22351a'), hexc('#4d6e2c'), hexc('#8aa64a'), hexc('#efe6c8')]
LINED = [hexc('#1c1718'), hexc('#3b3436'), hexc('#5e7a3a'), hexc('#d8cfae')]
BARK = [hexc('#2c1e12'), hexc('#5e4229'), hexc('#8a6a42'), hexc('#a9cf6e')]
BRASS = [hexc('#3a250d'), hexc('#8a5d22'), hexc('#c99a3e'), hexc('#f6dc8c')]

ITEMS = os.path.join(A, 'textures', 'item')
os.makedirs(ITEMS, exist_ok=True)

# ---------------------------------------------------------------- armor icons + layers (recoloured vanilla shapes)
pieces = [('cap', 'helmet'), ('vest', 'chestplate'), ('leggings', 'leggings'), ('boots', 'boots')]
if VAN:
    for p, v in pieces:
        recolor(f'{VAN}/textures/item/diamond_{v}.png', HERD, f'{ITEMS}/herdcaller_{p}.png')
        recolor(f'{VAN}/textures/item/diamond_{v}.png', LINED, f'{ITEMS}/lined_herdcaller_{p}.png')
    recolor(f'{VAN}/textures/item/diamond_helmet.png', BARK, f'{ITEMS}/elder_bark_crown.png')
    recolor(f'{VAN}/textures/item/diamond_chestplate.png', BRASS, f'{ITEMS}/engineers_harness.png')
    EQ = os.path.join(A, 'textures', 'entity', 'equipment')
    for name, pal in [('herdcaller', HERD), ('lined_herdcaller', LINED), ('elder_bark_crown', BARK), ('engineers_harness', BRASS)]:
        recolor(f'{VAN}/textures/entity/equipment/humanoid/diamond.png', pal, f'{EQ}/humanoid/{name}.png')
        recolor(f'{VAN}/textures/entity/equipment/humanoid_leggings/diamond.png', pal, f'{EQ}/humanoid_leggings/{name}.png')
    # crown: leafy tips on top of the helmet icon
    im = Image.open(f'{ITEMS}/elder_bark_crown.png')
    px = im.load()
    for x in (3, 6, 9, 12):
        for y in (1, 2):
            if px[x, y + 2][3] > 0:
                px[x, y] = hexc('#7fb24a') if y == 1 else hexc('#5e8a34')
    im.save(f'{ITEMS}/elder_bark_crown.png')
    # harness: rivets
    im = Image.open(f'{ITEMS}/engineers_harness.png')
    px = im.load()
    for (x, y) in [(4, 4), (11, 4), (5, 9), (10, 9), (7, 12), (8, 12)]:
        if px[x, y][3] > 0:
            px[x, y] = hexc('#fff3c0')
    im.save(f'{ITEMS}/engineers_harness.png')
    # bell of the flock: vanilla bell, green ribbon
    im = recolor(f'{VAN}/textures/item/bell.png', [hexc('#3a2a0c'), hexc('#9a7424'), hexc('#e8c45a'), hexc('#fff2b0')], f'{ITEMS}/bell_of_the_flock.png')
    px = im.load()
    for x in range(4, 12):
        if px[x, 6][3] > 0:
            px[x, 6] = hexc('#4d8a3a')
    im.save(f'{ITEMS}/bell_of_the_flock.png')
    # trail biscuit: golden cookie with berries
    im = recolor(f'{VAN}/textures/item/cookie.png', [hexc('#5a3514'), hexc('#a8702e'), hexc('#e0b060'), hexc('#f8e2a6')], f'{ITEMS}/trail_biscuit.png')
    px = im.load()
    for (x, y, c) in [(5, 6, '#c8283a'), (9, 5, '#f0a020'), (7, 9, '#c8283a'), (10, 10, '#f0a020')]:
        if px[x, y][3] > 0:
            px[x, y] = hexc(c)
    im.save(f'{ITEMS}/trail_biscuit.png')


def canvas(n=16):
    return Image.new('RGBA', (n, n), (0, 0, 0, 0))


def line(px, x0, y0, x1, y1, col):
    n = max(abs(x1 - x0), abs(y1 - y0))
    for i in range(n + 1):
        x = round(x0 + (x1 - x0) * i / n)
        y = round(y0 + (y1 - y0) * i / n)
        px[x, y] = jitter(col, 8)


# ---------------------------------------------------------------- staffs (drawn)
def staff(name, shaft, shaft_dark, head):
    im = canvas()
    px = im.load()
    line(px, 2, 14, 10, 6, shaft)
    line(px, 3, 14, 11, 6, shaft_dark)
    head(px)
    im.save(f'{ITEMS}/{name}.png')


def rootcall_head(px):
    bark = hexc('#5e4229'); dark = hexc('#3a2816'); moss = hexc('#6d9a3a'); amber = hexc('#f0a830')
    for (x, y) in [(10, 5), (11, 4), (12, 3), (13, 4), (12, 5), (11, 6), (13, 2), (9, 4), (10, 3), (14, 3), (12, 6)]:
        px[x, y] = jitter(bark)
    for (x, y) in [(9, 3), (14, 2), (13, 6), (10, 2)]:
        px[x, y] = dark
    for (x, y) in [(11, 2), (12, 1), (13, 1), (10, 1), (14, 1)]:
        px[x, y] = jitter(moss, 15)
    px[12, 4] = amber; px[11, 4] = hexc('#ffd070')


def kite_head(px):
    cols = [hexc('#e84a5f'), hexc('#ffd166'), hexc('#4ab0e8'), hexc('#f8f4ea')]
    cx, cy = 12, 4
    for y in range(-3, 4):
        r = 3 - abs(y)
        for x in range(-r, r + 1):
            q = (0 if x < 0 else 1) + (0 if y < 0 else 2)
            px[cx + x, cy + y] = cols[q]
    px[cx, cy] = hexc('#5a3a1a')
    for (x, y) in [(13, 8), (14, 9), (13, 10), (14, 11)]:
        px[x, y] = hexc('#e84a5f') if y % 2 == 0 else hexc('#4ab0e8')


def key_head(px):
    br = hexc('#c99a3e'); dk = hexc('#7a5420'); hi = hexc('#f6dc8c')
    for (x, y) in [(11, 2), (12, 2), (13, 2), (10, 3), (14, 3), (10, 4), (14, 4), (10, 5), (14, 5), (11, 6), (13, 6), (12, 6)]:
        px[x, y] = jitter(br)
    for (x, y) in [(11, 3), (13, 5)]:
        px[x, y] = hi
    px[12, 4] = hexc('#ffe9a0')  # lamp-eye glint in the bow
    for (x, y) in [(3, 11), (4, 12), (2, 12)]:
        px[x, y] = dk


def crook_head(px):
    wood = hexc('#8a6a42'); hi = hexc('#c8a070'); am = hexc('#a77bf3')
    for (x, y) in [(10, 5), (11, 4), (11, 3), (12, 2), (13, 2), (14, 3), (14, 4), (14, 5), (13, 6)]:
        px[x, y] = jitter(wood)
    for (x, y) in [(12, 1), (13, 1)]:
        px[x, y] = hi
    px[13, 7] = am; px[12, 4] = hexc('#efe6c8')


staff('herding_crook', hexc('#8a6a42'), hexc('#5a4228'), crook_head)
staff('rootcall_staff', hexc('#7a5a36'), hexc('#4a3420'), rootcall_head)
staff('kitestring_staff', hexc('#e6dcc0'), hexc('#a89c80'), kite_head)
staff('brassback_key', hexc('#c99a3e'), hexc('#7a5420'), key_head)

# gorger fluff: cream puff
im = canvas(); px = im.load()
for y in range(16):
    for x in range(16):
        d = ((x - 7.5) ** 2 / 30 + (y - 8.5) ** 2 / 22)
        if d < 1 and random.random() > d * 0.35:
            t = 1 - d
            px[x, y] = jitter(lerp(hexc('#b8a888'), hexc('#fbf3dc'), t * 0.9 + 0.1), 10)
im.save(f'{ITEMS}/gorger_fluff.png')


# ---------------------------------------------------------------- entity textures (box UV painting)
def box(px, u, v, w_, h, d, side, top=None, bottom=None, front=None, n=10):
    top = top or side; bottom = bottom or side; front = front or side
    def fill(x0, y0, ww, hh, col):
        for y in range(y0, y0 + hh):
            for x in range(x0, x0 + ww):
                px[x, y] = jitter(col, n)
    fill(u + d, v, w_, d, top)
    fill(u + d + w_, v, w_, d, bottom)
    fill(u, v + d, d, h, side)
    fill(u + d, v + d, w_, h, front)
    fill(u + d + w_, v + d, d, h, side)
    fill(u + 2 * d + w_, v + d, w_, h, side)
    return (u + d, v + d)  # front face origin


ENT = os.path.join(A, 'textures', 'entity')
os.makedirs(ENT, exist_ok=True)

# Rootling 32x32
im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
bark = hexc('#6a4a2c'); barkd = hexc('#4a3220'); moss = hexc('#5e8c34')
fx, fy = box(px, 0, 0, 6, 7, 5, bark, top=moss, front=bark)
for y in range(fy, fy + 7):  # bark grain
    for x in range(0, 22):
        if (x + y) % 4 == 0 and px[x, y][3]:
            px[x, y] = barkd
px[fx + 1, fy + 2] = hexc('#f0d060'); px[fx + 4, fy + 2] = hexc('#f0d060')  # glowing eyes
px[fx + 1, fy + 3] = hexc('#a07a20'); px[fx + 4, fy + 3] = hexc('#a07a20')
for x in range(fx + 2, fx + 4):
    px[x, fy + 5] = barkd  # mouth
box(px, 0, 12, 2, 3, 2, barkd)
box(px, 8, 12, 2, 3, 2, barkd)
box(px, 16, 12, 2, 6, 2, bark)
box(px, 24, 12, 2, 6, 2, bark)
box(px, 0, 20, 7, 2, 6, moss, top=hexc('#7ab04a'), n=18)
box(px, 26, 20, 1, 3, 1, hexc('#7ab04a'), top=hexc('#e86a8a'))
im.save(f'{ENT}/rootling.png')

# Paper Kite 32x32
im = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); px = im.load()
box(px, 0, 0, 10, 10, 1, hexc('#e8dcc0'), n=4)
fx, fy = 1, 1
quad = [hexc('#e84a5f'), hexc('#ffd166'), hexc('#4ab0e8'), hexc('#f4efe4')]
for face_x in (1, 12):
    for y in range(10):
        for x in range(10):
            q = (0 if x < 5 else 1) + (0 if y < 5 else 2)
            c = quad[q]
            if x in (4, 5) or y in (4, 5):
                c = hexc('#f8f4ea')
            px[face_x + x, 1 + y] = jitter(c, 6)
box(px, 22, 11, 1, 14, 1, hexc('#8a6a42'), n=4)
for i, c in enumerate([hexc('#e84a5f'), hexc('#4ab0e8'), hexc('#ffd166')]):
    for y in range(12, 17):
        for x in range(i * 4, i * 4 + 4):
            px[x, y] = jitter(c, 8)
im.save(f'{ENT}/paper_kite.png')

# Brassback 64x32
im = Image.new('RGBA', (64, 32), (0, 0, 0, 0)); px = im.load()
brass = hexc('#b8862e'); brassd = hexc('#7a5420'); brassl = hexc('#e8c060'); verd = hexc('#4fa58a')
box(px, 0, 0, 12, 5, 10, brass, top=brassl, bottom=brassd, n=10)
for (x, y) in [(12, 2), (15, 5), (18, 2), (21, 7), (14, 8), (19, 9), (12, 6), (20, 4)]:
    px[x, y] = hexc('#fff0b8')  # rivets on top
for (x, y) in [(16, 3), (13, 7), (20, 8)]:
    px[x, y] = verd  # verdigris spots
for x in range(10, 22):
    px[x, 12] = brassd  # seam on the front
box(px, 44, 0, 2, 2, 2, hexc('#fff3a0'), n=4)  # lamp
for i in range(6):
    box(px, i * 4, 16, 1, 5, 1, brassd, n=6)
box(px, 24, 16, 3, 3, 4, brass, top=brassl, n=8)
box(px, 38, 16, 3, 3, 4, brass, top=brassl, n=8)
im.save(f'{ENT}/brassback.png')

# effect icon
im = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); px = im.load()
for y in range(18):
    for x in range(18):
        d = ((x - 8.5) ** 2 + (y - 9.5) ** 2) ** 0.5
        if d < 6.5:
            px[x, y] = jitter(lerp(hexc('#a8702e'), hexc('#f0c878'), 1 - d / 6.5), 8)
for (x, y) in [(6, 8), (10, 7), (8, 11), (11, 11)]:
    px[x, y] = hexc('#c8283a')
os.makedirs(f'{A}/textures/mob_effect', exist_ok=True)
im.save(f'{A}/textures/mob_effect/well_fed.png')
im = Image.new('RGBA', (18, 18), (0, 0, 0, 0)); px = im.load()
for y in range(18):
    for x in range(18):
        d = ((x - 8.5) ** 2 + (y - 8.5) ** 2) ** 0.5
        if 4.5 < d < 7.5:
            px[x, y] = jitter(hexc('#a77bf3'), 10)
        if abs(x - 8.5) < 1 and abs(y - 8.5) < 3 or abs(y - 8.5) < 1 and abs(x - 8.5) < 3:
            px[x, y] = hexc('#efe6c8')
im.save(f'{A}/textures/mob_effect/herded.png')

# ---------------------------------------------------------------- models, equipment, lang
STAFFS = ['rootcall_staff', 'kitestring_staff', 'brassback_key', 'herding_crook']
ARMOR = [f'herdcaller_{p}' for p, _ in pieces] + [f'lined_herdcaller_{p}' for p, _ in pieces] + ['elder_bark_crown', 'engineers_harness']
OTHER = ['gorger_fluff', 'bell_of_the_flock', 'trail_biscuit']
for i in STAFFS + ARMOR + OTHER:
    w(f'{A}/models/item/{i}.json', {"parent": "minecraft:item/handheld" if i in STAFFS else "minecraft:item/generated", "textures": {"layer0": f"elsewhere:item/{i}"}})
    w(f'{A}/items/{i}.json', {"model": {"type": "minecraft:model", "model": f"elsewhere:item/{i}"}})
for name in ['herdcaller', 'lined_herdcaller']:
    w(f'{A}/equipment/{name}.json', {"layers": {"humanoid": [{"texture": f"elsewhere:{name}"}], "humanoid_leggings": [{"texture": f"elsewhere:{name}"}]}})
for name in ['elder_bark_crown', 'engineers_harness']:
    w(f'{A}/equipment/{name}.json', {"layers": {"humanoid": [{"texture": f"elsewhere:{name}"}]}})

names = {
    'rootcall_staff': 'Rootcall Staff', 'kitestring_staff': 'Kitestring Staff', 'brassback_key': 'Brassback Key',
    'herdcaller_cap': 'Herdcaller Cap', 'herdcaller_vest': 'Herdcaller Vest', 'herdcaller_leggings': 'Herdcaller Leggings', 'herdcaller_boots': 'Herdcaller Boots',
    'lined_herdcaller_cap': 'Lined Herdcaller Cap', 'lined_herdcaller_vest': 'Lined Herdcaller Vest', 'lined_herdcaller_leggings': 'Lined Herdcaller Leggings', 'lined_herdcaller_boots': 'Lined Herdcaller Boots',
    'elder_bark_crown': 'Elder Bark Crown', 'engineers_harness': "Engineer's Harness",
    'gorger_fluff': 'Gorger Fluff', 'herding_crook': 'Herding Crook', 'bell_of_the_flock': 'Bell of the Flock', 'trail_biscuit': 'Trail Biscuit',
}
desc = {
    'rootcall_staff': 'Hold to call a Rootling (10 Glimmer). Sneak-use: dismiss them.',
    'kitestring_staff': 'Hold to call a Paper Kite (10 Glimmer). Sneak-use: dismiss them.',
    'brassback_key': 'Hold to call a Brassback (10 Glimmer). Sneak-use: dismiss them.',
    'herdcaller_boots': 'Minions move 15% faster and come to you from 16 blocks.',
    'lined_herdcaller_boots': 'Minions move 15% faster and come to you from 16 blocks.',
    'elder_bark_crown': 'Rootlings get +50% health.',
    'engineers_harness': 'Your minions are immune to fire and lightning.',
    'bell_of_the_flock': 'Trinket: +1 minion slot while you wear summoner armor. Ring it to call your minions.',
    'trail_biscuit': 'Eaten with minions out: they get Well Fed for 3 min.',
    'herding_crook': 'Hits mark the target Herded (5 s): your minions switch to it and deal +1 damage.',
}
set_line = 'Full set (Pack Bond): nearby minions heal and get +4 armor; a dying minion refunds 10 Glimmer.'
con_line = 'Summoner gear: your own damage -20%.'
lang = {f'item.elsewhere.{k}': v for k, v in names.items()}
for k in names:
    lines = []
    if k in desc:
        lines.append(desc[k])
    if 'herdcaller' in k:
        lines.append(set_line)
    if k in ARMOR:
        lines.append(con_line)
    for n, l in enumerate(lines):
        lang[f'item.elsewhere.{k}.desc{n}'] = l
lang.update({
    'entity.elsewhere.rootling': 'Rootling', 'entity.elsewhere.paper_kite': 'Paper Kite', 'entity.elsewhere.brassback': 'Brassback',
    'effect.elsewhere.well_fed': 'Well Fed', 'effect.elsewhere.herded': 'Herded',
    'attribute.name.elsewhere.minion_slots': 'Minion Slots', 'attribute.name.elsewhere.minion_damage': 'Minion Damage',
    'death.attack.elsewhere.minion': "%1$s was slain by %2$s's minion",
    'death.attack.elsewhere.minion.item': "%1$s was slain by %2$s's minion using %3$s",
    'itemGroup.elsewhere.summoner': 'Elsewhere: Summoner',
})
w(f'{A}/lang/en_us.json', lang)
w(os.path.join(HERE, 'lore_lines.json'), {k: sum(1 for x in lang if x.startswith(f'item.elsewhere.{k}.desc')) for k in names})

# ---------------------------------------------------------------- tags
def tag(path, values):
    w(f'{D}/tags/item/{path}.json', {"replace": False, "values": values})

E = lambda i: f'elsewhere:{i}'
tag('trinket', [E('bell_of_the_flock')])
tag('summoner_armor', [E(i) for i in ARMOR])
tag('calling/summoner', [E(i) for i in ARMOR])
for (p, _), slot in zip(pieces, ['head', 'chest', 'legs', 'feet']):
    tag(f'set/herdcaller/{slot}', [E(f'herdcaller_{p}'), E(f'lined_herdcaller_{p}')])
tag('slow_mending', [E(f'lined_herdcaller_{p}') for p, _ in pieces] + [E('engineers_harness')])
tag('repairs_herdcaller_armor', [{"id": "elsewhere:trichome_quartz", "required": False}])
tag('repairs_engineers_harness', [])
tag('repairs_rootcall_staff', [{"id": "elsewhere:heartwood_shard", "required": False}])
tag('repairs_kitestring_staff', [{"id": "elsewhere:zephyr_feather", "required": False}])
tag('repairs_brassback_key', [{"id": "elsewhere:verdigris_gear", "required": False}])
MC = os.path.join(RES, 'data', 'minecraft', 'tags', 'item')
for t, items in [('head_armor', ['herdcaller_cap', 'lined_herdcaller_cap', 'elder_bark_crown']), ('chest_armor', ['herdcaller_vest', 'lined_herdcaller_vest', 'engineers_harness']),
                 ('leg_armor', ['herdcaller_leggings', 'lined_herdcaller_leggings']), ('foot_armor', ['herdcaller_boots', 'lined_herdcaller_boots'])]:
    w(f'{MC}/{t}.json', {"replace": False, "values": [E(i) for i in items]})

w(f'{MC}/enchantable/durability.json', {"replace": False, "values": [E('herding_crook')]})
w(f'{MC}/enchantable/melee_weapon.json', {"replace": False, "values": [E('herding_crook')]})

# ---------------------------------------------------------------- recipes
R = f'{D}/recipe'
w(f'{R}/trail_biscuit.json', {"type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": ["minecraft:wheat", "minecraft:wheat", "minecraft:sweet_berries", "minecraft:glow_berries"],
    "result": {"id": "elsewhere:trail_biscuit", "count": 4}})
for p, _ in pieces:
    w(f'{R}/lined_herdcaller_{p}_smithing.json', {"type": "minecraft:smithing_transform",
        "template": "minecraft:netherite_upgrade_smithing_template", "base": f"elsewhere:herdcaller_{p}",
        "addition": "#minecraft:netherite_tool_materials", "result": {"id": f"elsewhere:lined_herdcaller_{p}"}})
print('assets written')
