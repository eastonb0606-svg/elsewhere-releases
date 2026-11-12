#!/usr/bin/env python3
"""polish-endgame-sets / new-dimension-trims for the Sunwright and Galeweave lines: smithing recipes, ingot and template
recipes, base tags, trim patterns + textures, item definitions, placeholder item textures. Run from the module root."""
import json, math, os, shutil, struct, zlib
R = 'src/main/resources'
def w(path, obj):
    p = os.path.join(R, path); os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w').write(json.dumps(obj, indent=1))
def tag(path, vals): w('data/' + path, {"replace": False, "values": vals})

# ---- tags
for slot, dia, gale in (('helmet', 'diamond_helmet', 'galeweave_hood'), ('chestplate', 'diamond_chestplate', 'galeweave_robe'),
                        ('leggings', 'diamond_leggings', 'galeweave_leggings'), ('boots', 'diamond_boots', 'galeweave_boots')):
    tag('elsewhere/tags/item/sunwright_base/%s.json' % slot, ['minecraft:' + dia, 'elsewhere:' + gale])
for t in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe'):
    tag('elsewhere/tags/item/sunstone_base/%s.json' % t, ['minecraft:diamond_' + t, 'elsewhere:zephyrite_' + t])
SUN = ['elsewhere:sunwright_helmet', 'elsewhere:sunwright_chestplate', 'elsewhere:sunwright_leggings', 'elsewhere:sunwright_boots', 'elsewhere:sunwright_plate',
       'elsewhere:sunwright_visor'] + ['elsewhere:sunstone_' + t for t in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe')] + ['elsewhere:glassfang']
tag('elsewhere/tags/item/slow_mending.json', SUN)
tag('minecraft/tags/item/trimmable_armor.json', SUN[:6] + ['elsewhere:galeweave_hood', 'elsewhere:galeweave_robe', 'elsewhere:galeweave_leggings', 'elsewhere:galeweave_boots', 'elsewhere:petalstep_sandals'])

# ---- smithing: <set>_upgrade_smithing_template + same-slot diamond/early item + 1 ingot
for slot, piece in (('helmet', 'sunwright_helmet'), ('chestplate', 'sunwright_chestplate'), ('leggings', 'sunwright_leggings'), ('boots', 'sunwright_boots')):
    w('data/elsewhere/recipe/%s.json' % piece, {"type": "minecraft:smithing_transform", "category": "equipment", "template": "elsewhere:sunwright_upgrade_smithing_template",
        "base": "#elsewhere:sunwright_base/" + slot, "addition": "elsewhere:sunwright_ingot", "result": {"id": "elsewhere:" + piece, "count": 1}})
for t in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe'):
    w('data/elsewhere/recipe/sunstone_%s.json' % t, {"type": "minecraft:smithing_transform", "category": "equipment", "template": "elsewhere:sunwright_upgrade_smithing_template",
        "base": "#elsewhere:sunstone_base/" + t, "addition": "elsewhere:sunwright_ingot", "result": {"id": "elsewhere:sunstone_" + t, "count": 1}})
w('data/elsewhere/recipe/sunwright_ingot.json', {"type": "minecraft:crafting_shapeless", "category": "misc",
    "ingredients": ["elsewhere:sunstone"] * 4 + ["minecraft:gold_ingot"] * 4, "result": {"id": "elsewhere:sunwright_ingot", "count": 1}})
w('data/elsewhere/recipe/sunwright_template_duplicate.json', {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["DTD", "DBD", "DDD"],
    "key": {"D": "minecraft:diamond", "T": "elsewhere:sunwright_upgrade_smithing_template", "B": "elsewhere:sunwright_bricks"},
    "result": {"id": "elsewhere:sunwright_upgrade_smithing_template", "count": 2}})

# ---- trims (new-dimension-trims): Gale (Petalwind) and Sunburst (Glasswaste)
for name, block in (('gale', 'elsewhere:cloud_block'), ('sunburst', 'elsewhere:sunglass')):
    w('data/elsewhere/trim_pattern/%s.json' % name, {"asset_id": "elsewhere:" + name, "decal": False, "description": {"translate": "trim_pattern.elsewhere." + name}})
    t = 'elsewhere:%s_armor_trim_smithing_template' % name
    w('data/elsewhere/recipe/%s_armor_trim_smithing_template_smithing_trim.json' % name, {"type": "minecraft:smithing_trim", "addition": "#minecraft:trim_materials",
        "base": "#minecraft:trimmable_armor", "pattern": "elsewhere:" + name, "template": t})
    w('data/elsewhere/recipe/%s_armor_trim_smithing_template_duplicate.json' % name, {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["#S#", "#C#", "###"],
        "key": {"#": "minecraft:diamond", "C": block, "S": t}, "result": {"id": t, "count": 2}})
    w('assets/elsewhere/items/%s_armor_trim_smithing_template.json' % name, {"model": {"type": "minecraft:model", "model": "elsewhere:item/%s_armor_trim_smithing_template" % name}})
    w('assets/elsewhere/models/item/%s_armor_trim_smithing_template.json' % name, {"parent": "minecraft:item/generated", "textures": {"layer0": "elsewhere:item/%s_armor_trim_smithing_template" % name}})

def png_gray(wd, h, px):
    raw = b''.join(b'\x00' + bytes(px[y * wd:(y + 1) * wd]) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', wd, h, 8, 0, 0, 0, 0)) + chunk(b'tRNS', b'\x00\x00') + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
def gale(x, y):      # swirling wind lines
    v = (y + round(3 * math.sin(x / 3.0))) % 7
    return 224 if v in (0, 1) else (144 if v == 2 else 0)
def sunburst(x, y):  # rays from a sun at the tile centre
    a = math.atan2(y % 16 - 8, x % 16 - 8); d = math.hypot(x % 16 - 8, y % 16 - 8)
    return 224 if d < 2.5 else (176 if int((a + math.pi) / (math.pi / 6)) % 2 == 0 and 3 < d < 7.5 else 0)
MCMETA = '{\n  "palette" : {\n    "base_palette" : "trim_base"\n  }\n}\n'
for name, fn in (('gale', gale), ('sunburst', sunburst)):
    for sub in ('humanoid', 'humanoid_leggings'):
        d = os.path.join(R, 'assets/elsewhere/textures/trims/entity', sub); os.makedirs(d, exist_ok=True)
        open(os.path.join(d, name + '.png'), 'wb').write(png_gray(64, 32, [fn(x, y) for y in range(32) for x in range(64)]))
        open(os.path.join(d, name + '.png.mcmeta'), 'w').write(MCMETA)

# ---- item definitions + placeholder textures (art lane replaces)
T = os.path.join(R, 'assets/elsewhere/textures/item')
for n, src in (('sunwright_ingot', 'sunstone'), ('sunwright_upgrade_smithing_template', 'zephyr_key'), ('gale_armor_trim_smithing_template', 'zephyr_key'),
               ('sunburst_armor_trim_smithing_template', 'sunstone')):
    if not os.path.exists(os.path.join(T, n + '.png')): shutil.copy(os.path.join(T, src + '.png'), os.path.join(T, n + '.png'))
for n in ('sunwright_ingot', 'sunwright_upgrade_smithing_template'):
    w('assets/elsewhere/items/%s.json' % n, {"model": {"type": "minecraft:model", "model": "elsewhere:item/" + n}})
    w('assets/elsewhere/models/item/%s.json' % n, {"parent": "minecraft:item/generated", "textures": {"layer0": "elsewhere:item/" + n}})

# ---- lang
p = os.path.join(R, 'assets/elsewhere/lang/en_us.json'); L = json.load(open(p))
L.update({
 "item.elsewhere.sunwright_ingot": "Sunwright Ingot", "item.elsewhere.sunwright_upgrade_smithing_template": "Smithing Template",
 "item.elsewhere.sunwright_upgrade_smithing_template.new": "Sunwright Upgrade",
 "item.elsewhere.gale_armor_trim_smithing_template": "Smithing Template", "item.elsewhere.gale_armor_trim_smithing_template.new": "Gale Armor Trim",
 "item.elsewhere.sunburst_armor_trim_smithing_template": "Smithing Template", "item.elsewhere.sunburst_armor_trim_smithing_template.new": "Sunburst Armor Trim",
 "trim_pattern.elsewhere.gale": "Gale Armor Trim", "trim_pattern.elsewhere.sunburst": "Sunburst Armor Trim",
 "set.elsewhere.galeweave.fall": " -10% fall damage on every piece",
 "set.elsewhere.sunwright.title": "Sunwright set (Noonfire):",
 "set.elsewhere.sunwright.noonfire": " Fire, burning, lava and magma can't hurt you",
 "set.elsewhere.sunwright.noonfire.1": " Heat never builds; melee attackers catch fire for 3 s",
 "set.elsewhere.sunwright.noonfire.2": " Freezing builds twice as fast",
})
json.dump(L, open(p, 'w'), indent=2, ensure_ascii=False)
