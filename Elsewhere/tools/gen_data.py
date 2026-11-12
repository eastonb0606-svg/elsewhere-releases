"""Writes the Pale Hollow datapack (dimension, worldgen, loot, recipes, tags) and the client asset JSON
(block states, item/block models, item definitions, sounds, particles, lang). Run: python3 tools/gen_data.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "src/main/resources/data")
ASSETS = os.path.join(ROOT, "src/main/resources/assets/elsewhere")
NS = "elsewhere"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(obj, fh, indent=2)
        fh.write("\n")


def d(rel, obj):
    write(os.path.join(DATA, rel), obj)


def a(rel, obj):
    write(os.path.join(ASSETS, rel), obj)


# ------------------------------------------------------------------ dimension
d(f"{NS}/dimension_type/pale_hollow.json", {
    "ambient_light": 0.05,
    "attributes": {
        "minecraft:audio/ambient_sounds": {"mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000}},
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "destroy_on_use": True},
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:gameplay/creaking_active": True,
        "minecraft:gameplay/eyeblossom_open": True,
        "minecraft:gameplay/monsters_burn": False,
        "minecraft:gameplay/can_start_raid": False,
        "minecraft:gameplay/sky_light_level": 5.0,
        "minecraft:visual/sun_angle": 101.0,
        "minecraft:visual/moon_angle": 281.0,
        "minecraft:visual/star_angle": 101.0,
        "minecraft:visual/moon_phase": "full_moon",
        "minecraft:visual/star_brightness": 0.35,
        "minecraft:visual/sky_color": "#5d6470",
        "minecraft:visual/fog_color": "#9da3a6",
        "minecraft:visual/sunrise_sunset_color": "#b4e88a4c",
        "minecraft:visual/cloud_color": "#cc8e949b",
        "minecraft:visual/cloud_height": 200.0,
        "minecraft:visual/sky_light_color": "#c7b9e6",
        "minecraft:visual/sky_light_factor": 0.4,
        "minecraft:visual/ambient_light_color": "#141218",
        "minecraft:visual/fog_start_distance": 10.0,
        "minecraft:visual/fog_end_distance": 120.0,
        "minecraft:visual/sky_fog_end_distance": 180.0,
    },
    "coordinate_scale": 4.0,
    "has_ceiling": False,
    "has_ender_dragon_fight": False,
    "has_fixed_time": True,
    "has_skylight": True,
    "height": 384,
    "infiniburn": "#minecraft:infiniburn_overworld",
    "logical_height": 384,
    "min_y": -64,
    "monster_spawn_block_light_limit": 0,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
    "skybox": "overworld",
})

BIOMES = ["hollow_thicket", "resin_bog", "weeping_deeps", "heartwood_grove", "silent_clearing"]
# temperature/humidity cells for the multi-noise source (no other climate axes vary)
CLIMATE = {
    "hollow_thicket": ([-1.0, 0.2], [-0.35, 0.35]),
    "resin_bog": ([-1.0, 0.2], [0.35, 1.0]),
    "weeping_deeps": ([-1.0, 0.2], [-1.0, -0.35]),
    "heartwood_grove": ([0.2, 1.0], [-0.2, 1.0]),
    "silent_clearing": ([0.2, 1.0], [-1.0, -0.2]),
}
d(f"{NS}/dimension/pale_hollow.json", {
    "type": f"{NS}:pale_hollow",
    "generator": {
        "type": "minecraft:noise",
        "settings": f"{NS}:pale_hollow",
        "biome_source": {"type": "minecraft:multi_noise", "biomes": [
            {"biome": f"{NS}:{b}", "parameters": {"temperature": t, "humidity": h, "continentalness": 0.0, "erosion": 0.0,
                                                 "weirdness": 0.0, "depth": 0.0, "offset": 0.0}}
            for b, (t, h) in CLIMATE.items()]},
    },
})

# ------------------------------------------------------------------ terrain
def noise(name, xz, y=0.0):
    return {"type": "minecraft:noise", "noise": name, "xz_scale": xz, "y_scale": y}


def add(l, r):
    return {"type": "minecraft:add", "left": l, "right": r}


def mul(l, r):
    return {"type": "minecraft:mul", "left": l, "right": r}


# Surface height H(x,z) ~ 64 +- a few blocks of marsh, with gentle ridges. density = (H - y) / 12
surface_offset = add(mul(noise("minecraft:erosion", 1.6), 0.55),
                     add(mul(noise("minecraft:surface", 1.2), 0.22),
                         mul({"type": "minecraft:max", "left": noise("minecraft:ridge", 0.9), "right": 0.0}, 1.4)))
d(f"{NS}/worldgen/density_function/pale_hollow/surface_offset.json", {"type": "minecraft:cache", "input": surface_offset})
terrain = add({"type": "minecraft:gradient", "axis": "y", "from_coordinate": -64, "to_coordinate": 320,
               "from_value": 10.75, "to_value": -21.25},
              add(f"{NS}:pale_hollow/surface_offset", mul(noise("minecraft:gravel", 2.0, 2.0), 0.06)))
d(f"{NS}/worldgen/density_function/pale_hollow/terrain.json", terrain)
d(f"{NS}/worldgen/noise_settings/pale_hollow.json", {
    "noise": {"min_y": -64, "height": 384},
    "default_block": "minecraft:stone",
    "default_fluid": "minecraft:water",
    "sea_level": 63,
    "disable_mob_generation": False,
    "legacy_random_source": False,
    "material_rule": f"{NS}:pale_hollow",
    "spawn_target": [],
    "noise_router": {
        "temperature": {"type": "minecraft:noise", "noise": "minecraft:temperature", "shift_x": "minecraft:shift_x",
                        "shift_z": "minecraft:shift_z", "xz_scale": 2.0, "y_scale": 0.0},
        "vegetation": {"type": "minecraft:noise", "noise": "minecraft:vegetation", "shift_x": "minecraft:shift_x",
                       "shift_z": "minecraft:shift_z", "xz_scale": 2.0, "y_scale": 0.0},
        "continents": 0.0, "erosion": 0.0, "depth": 0.0, "ridges": 0.0,
        "chunk_surface_level": {"type": "minecraft:interpolated", "cell_size_xz": 16, "cell_size_y": 1, "input": {
            "type": "minecraft:find_top_surface", "cell_height": 8, "lower_bound": -64, "upper_bound": 200.0,
            "density": f"{NS}:pale_hollow/terrain"}},
        "final_density": {"type": "minecraft:min",
                          "left": {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8, "input": f"{NS}:pale_hollow/terrain"},
                          "right": "minecraft:overworld/caves/noodle"},
    },
})


def block(b):
    return {"type": "minecraft:block", "result_state": b}


def cond(c, then):
    return {"type": "minecraft:condition", "if_true": c, "then_run": then}


def biome_is(*bs):
    return {"type": "minecraft:biome", "biome_is": [f"{NS}:{b}" for b in bs]}


def noise_band(lo, hi, name="minecraft:surface"):
    return {"type": "minecraft:noise_threshold", "noise": name, "min_threshold": lo, "max_threshold": hi}


above_water = {"type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}
near_sea = {"type": "minecraft:y_above", "anchor": {"absolute": 65}, "surface_depth_multiplier": 0, "add_stone_depth": False}
floor_top = cond("minecraft:on_floor", {"type": "minecraft:sequence", "sequence": [
    cond({"type": "minecraft:not", "invert": above_water}, block("minecraft:mud")),
    cond(biome_is("resin_bog"), {"type": "minecraft:sequence", "sequence": [
        cond({"type": "minecraft:not", "invert": near_sea}, block("minecraft:mud")),
        cond(noise_band(0.15, 0.4), block("minecraft:pale_moss_block")),
        block("minecraft:grass_block")]}),
    cond(biome_is("silent_clearing"), {"type": "minecraft:sequence", "sequence": [
        cond(noise_band(0.2, 1.8), block(f"{NS}:ashen_moss")),
        block("minecraft:grass_block")]}),
    cond(biome_is("weeping_deeps"), {"type": "minecraft:sequence", "sequence": [
        cond(noise_band(-0.2, 0.1), block("minecraft:coarse_dirt")),
        cond(noise_band(0.3, 1.8), block("minecraft:pale_moss_block")),
        block("minecraft:podzol")]}),
    cond(noise_band(0.25, 0.6), block("minecraft:pale_moss_block")),
    cond(noise_band(-0.6, -0.4), block(f"{NS}:ashen_moss")),
    block("minecraft:grass_block")]})
under = cond("minecraft:under_floor", {"type": "minecraft:sequence", "sequence": [
    cond(biome_is("resin_bog"), block("minecraft:mud")),
    block("minecraft:dirt")]})
d(f"{NS}/worldgen/material_rule/pale_hollow.json", {"type": "minecraft:sequence", "sequence": [
    "minecraft:bedrock_floor",
    cond({"type": "minecraft:above_preliminary_surface"}, {"type": "minecraft:sequence", "sequence": [floor_top, under]}),
    cond({"type": "minecraft:vertical_gradient", "random_name": f"{NS}:tuff", "true_at_and_below": {"absolute": 0},
          "false_at_and_above": {"absolute": 8}}, block("minecraft:tuff")),
]})

# ------------------------------------------------------------------ features
d(f"{NS}/worldgen/feature/return_rift.json", {"type": f"{NS}:return_rift"})
d(f"{NS}/worldgen/placed_feature/return_rift.json", {"feature": f"{NS}:return_rift", "placement": []})
d(f"{NS}/worldgen/feature/world_trunk.json", {"type": f"{NS}:world_trunk", "min_height": 110, "max_height": 170, "min_radius": 3, "max_radius": 5})
for name, rarity in (("world_trunk", 9), ("world_trunk_dense", 4)):
    d(f"{NS}/worldgen/placed_feature/{name}.json", {"feature": f"{NS}:world_trunk", "placement": [
        {"type": "minecraft:rarity_filter", "chance": rarity},
        {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 1},
        {"type": "minecraft:biome"}]})

ORES = ["minecraft:ore_dirt", "minecraft:ore_gravel", "minecraft:ore_tuff", "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
        "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small", "minecraft:ore_gold", "minecraft:ore_redstone",
        "minecraft:ore_diamond", "minecraft:ore_lapis", "minecraft:ore_copper", "minecraft:disk_clay"]
VEG_ORDER = [f"{NS}:return_rift", f"{NS}:world_trunk_dense", f"{NS}:world_trunk", "minecraft:pale_garden_vegetation",
             "minecraft:pale_moss_patch", "minecraft:pale_garden_flowers", "minecraft:flower_pale_garden", "minecraft:patch_grass_forest",
             "minecraft:patch_firefly_bush_near_water", "minecraft:patch_waterlily"]
VEG = {
    "hollow_thicket": [f"{NS}:return_rift", f"{NS}:world_trunk", "minecraft:pale_garden_vegetation", "minecraft:pale_moss_patch",
                       "minecraft:pale_garden_flowers", "minecraft:flower_pale_garden", "minecraft:patch_grass_forest"],
    "resin_bog": [f"{NS}:return_rift", f"{NS}:world_trunk", "minecraft:pale_moss_patch", "minecraft:patch_grass_forest",
                  "minecraft:patch_firefly_bush_near_water", "minecraft:patch_waterlily"],
    "weeping_deeps": [f"{NS}:return_rift", f"{NS}:world_trunk", "minecraft:pale_garden_vegetation", "minecraft:pale_moss_patch",
                      "minecraft:flower_pale_garden"],
    "heartwood_grove": [f"{NS}:return_rift", f"{NS}:world_trunk_dense", "minecraft:pale_garden_vegetation", "minecraft:pale_moss_patch",
                        "minecraft:pale_garden_flowers"],
    "silent_clearing": [f"{NS}:return_rift", "minecraft:pale_moss_patch", "minecraft:flower_pale_garden", "minecraft:patch_grass_forest"],
}
for b, feats in VEG.items():
    assert feats == [f for f in VEG_ORDER if f in feats], b

LOOK = {  # sky, fog, water, foliage, grass, particles, music, extra sounds
    "hollow_thicket": ("#6E7378", "#9DA3A6", "#5E6B66", "#8A8F86", "#7d8279",
                       {"particle": {"type": "minecraft:pale_oak_leaves"}, "probability": 0.002}, None,
                       {"sound": "minecraft:block.creaking_heart.idle", "tick_chance": 0.0025}),
    "resin_bog": ("#74706A", "#A89A86", "#6B5A3A", "#8C8A70", "#857f66",
                  {"particle": {"type": "minecraft:firefly"}, "probability": 0.006}, "minecraft:music_disc.tears", None),
    "weeping_deeps": ("#4E5458", "#5A5F63", "#3C4A46", "#6F746C", "#666b63",
                      {"particle": {"type": "minecraft:white_ash"}, "probability": 0.004}, None, None),
    "heartwood_grove": ("#7A6A60", "#B08A6A", "#5E5040", "#8A8274", "#82796a",
                        {"particle": {"type": f"{NS}:resin_dust"}, "probability": 0.003}, "minecraft:music.overworld.deep_dark", None),
    "silent_clearing": ("#3E4652", "#7E8794", "#4A5A62", "#9AA096", "#8f968c",
                        {"particle": {"type": "minecraft:white_ash"}, "probability": 0.002}, None, None),
}
SPAWNS = {
    "monster": [
        {"type": f"{NS}:creaking_husk", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}, "weight": 60},
        {"type": f"{NS}:eyeblossom_wisp", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3}, "weight": 45},
        {"type": "minecraft:zombie", "count": 2, "weight": 25},
        {"type": "minecraft:skeleton", "count": 2, "weight": 25},
        {"type": "minecraft:spider", "count": 2, "weight": 20},
        {"type": "minecraft:enderman", "count": 1, "weight": 5},
    ],
    "ambient": [{"type": "minecraft:bat", "count": 4, "weight": 10}],
}
for b in BIOMES:
    sky, fog, water, foliage, grass, particles, music, additions = LOOK[b]
    attrs = {
        "minecraft:visual/sky_color": sky.lower(),
        "minecraft:visual/fog_color": fog.lower(),
        "minecraft:visual/water_fog_color": water.lower(),
        "minecraft:visual/ambient_particles": {"argument": [particles], "modifier": "append"},
        "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": SPAWNS}, "modifier": "overlay"},
    }
    if music:
        attrs["minecraft:audio/background_music"] = {"default": {"min_delay": 6000, "max_delay": 18000, "sound": music}}
    else:
        attrs["minecraft:audio/background_music"] = {}
        attrs["minecraft:audio/music_volume"] = 0.0
    if additions:
        attrs["minecraft:audio/ambient_sounds"] = {"additions": additions,
                                                   "mood": {"block_search_extent": 8, "offset": 2.0, "sound": "minecraft:ambient.cave", "tick_delay": 6000}}
    d(f"{NS}/worldgen/biome/{b}.json", {
        "attributes": attrs,
        "carvers": ["minecraft:cave"],
        "downfall": 0.8,
        "effects": {"dry_foliage_color": foliage.lower(), "foliage_color": foliage.lower(), "grass_color": grass.lower(), "water_color": water.lower()},
        "features": [[], [], [], [], [], [], ORES, [], ["minecraft:spring_water"], VEG[b], []],
        "has_precipitation": False,
        "temperature": 0.6,
    })

# ------------------------------------------------------------------ loot tables
def item_entry(item, lo=1, hi=1, chance=None, looting=False):
    funcs = []
    if (lo, hi) != (1, 1):
        funcs.append({"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if looting:
        funcs.append({"type": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, **({"modifier": funcs} if funcs else {})}]}
    if chance is not None:
        pool["condition"] = {"type": "minecraft:random_chance", "chance": chance}
    return pool


d(f"{NS}/loot_table/entities/creaking_husk.json", {"type": "minecraft:entity", "pools": [
    item_entry(f"{NS}:heartwood_shard", chance=0.35),
    item_entry("minecraft:resin_clump", 1, 3, looting=True),
    item_entry("minecraft:pale_oak_log", 0, 1),
]})
d(f"{NS}/loot_table/entities/eyeblossom_wisp.json", {"type": "minecraft:entity", "pools": [
    item_entry("minecraft:open_eyeblossom", 1, 1),
]})
d(f"{NS}/loot_table/entities/mother_creak.json", {"type": "minecraft:entity", "pools": [
    item_entry(f"{NS}:resin_heart_locket"),
    item_entry(f"{NS}:heartwood_greatbow"),
    item_entry(f"{NS}:pale_bark", 10, 10),
    item_entry(f"{NS}:heartwood_shard", 2, 4),
]})
d(f"{NS}/loot_table/entities/selenne.json", {"type": "minecraft:entity", "pools": [
    item_entry(f"{NS}:eyeblossom_circlet"),
    item_entry(f"{NS}:moon_eye"),
    item_entry(f"{NS}:pale_bark", 6, 10),
]})
for b in ("resin_heart", "heartwood_log", "moonring_stone", "ashen_moss"):
    d(f"{NS}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "bonus_rolls": 0.0, "condition": {"type": "minecraft:survives_explosion"},
         "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}" if b != "resin_heart" else "minecraft:resin_clump"}]}]})
d(f"{NS}/loot_table/blocks/moon_bloom.json", {"type": "minecraft:block", "pools": []})

# ------------------------------------------------------------------ recipes and attunement
d(f"{NS}/recipe/wayfinder.json", {"type": "minecraft:crafting_shaped", "category": "equipment",
                                  "key": {"A": "minecraft:amethyst_shard", "P": "minecraft:ender_pearl", "C": "minecraft:compass"},
                                  "pattern": ["APA", "ACA", " P "], "result": {"id": f"{NS}:wayfinder"}})
d(f"{NS}/recipe/wayfinder_pale_hollow.json", {"type": "minecraft:crafting_shaped", "category": "equipment",
                                              "key": {"R": "minecraft:resin_clump", "E": "minecraft:open_eyeblossom", "L": "minecraft:pale_oak_log",
                                                      "W": f"{NS}:wayfinder", "M": "minecraft:pale_moss_block", "B": "minecraft:bone_block"},
                                              "pattern": ["RER", "LWL", "MBM"], "result": {"id": f"{NS}:wayfinder_pale_hollow"}})
d(f"{NS}/recipe/attunement.json", {"type": f"{NS}:attunement"})
d(f"{NS}/attunement/pale_hollow.json", {
    "dimension": f"{NS}:pale_hollow",
    "ingredients": ["minecraft:resin_clump", "minecraft:open_eyeblossom", "minecraft:pale_oak_log", "minecraft:pale_moss_block", "minecraft:bone_block"],
    "requires": "none",
    "notes": "DIMENSIONS.md section 8: R E R / L W L / M B M. Any layout works through elsewhere:attunement; the shaped recipe is for the recipe book."})
d(f"{NS}/recipe/heartwood_effigy.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                         "key": {"S": f"{NS}:heartwood_shard", "H": "minecraft:creaking_heart", "L": "minecraft:pale_oak_log"},
                                         "pattern": ["LSL", "SHS", "LSL"], "result": {"id": f"{NS}:heartwood_effigy"}})
d(f"{NS}/recipe/moonring_seed.json", {"type": "minecraft:crafting_shaped", "category": "misc",
                                      "key": {"B": f"{NS}:pale_bark", "E": "minecraft:closed_eyeblossom", "Y": "minecraft:ender_eye"},
                                      "pattern": ["BEB", "EYE", "BEB"], "result": {"id": f"{NS}:moonring_seed"}})
d(f"{NS}/recipe/unblinking_eye.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                       "ingredients": [f"{NS}:heartwood_shard"] * 6 + ["minecraft:ender_eye"], "result": {"id": f"{NS}:unblinking_eye"}})
d(f"{NS}/recipe/pale_sap.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                 "ingredients": ["minecraft:glass_bottle", "minecraft:resin_clump", "minecraft:pale_hanging_moss"],
                                 "result": {"id": f"{NS}:pale_sap"}})
for name, unlock in (("wayfinder", ["minecraft:amethyst_shard", "minecraft:ender_pearl"]),):
    d(f"{NS}/advancement/recipes/{name}.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {**{f"has_{i.split(':')[1]}": {"conditions": {"items": [{"items": i}]}, "trigger": "minecraft:inventory_changed"} for i in unlock},
                     "has_the_recipe": {"conditions": {"recipes": f"{NS}:{name}"}, "trigger": "minecraft:recipe_unlocked"}},
        "requirements": [["has_the_recipe"] + [f"has_{i.split(':')[1]}" for i in unlock]],
        "rewards": {"recipes": [f"{NS}:{name}"]}})

# ------------------------------------------------------------------ tags
d("minecraft/tags/item/gaze_disguise_equipment.json", {"replace": False, "values": [f"{NS}:eyeblossom_circlet"]})
d("minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [f"{NS}:heartwood_log", f"{NS}:resin_heart"]})
d("minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"{NS}:moonring_stone"]})
d("minecraft/tags/block/mineable/hoe.json", {"replace": False, "values": [f"{NS}:ashen_moss"]})
d("minecraft/tags/block/logs.json", {"replace": False, "values": [f"{NS}:heartwood_log"]})

# ------------------------------------------------------------------ client assets
def item_model(name, tex=None, parent="minecraft:item/generated"):
    a(f"models/item/{name}.json", {"parent": parent, "textures": {"layer0": f"{NS}:item/{tex or name}"}})


def item_def(name, model=None):
    a(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": model or f"{NS}:item/{name}"}})


for it in ("wayfinder", "heartwood_shard", "pale_sap", "unblinking_eye", "pale_bark", "resin_heart_locket", "eyeblossom_circlet",
           "moon_eye", "heartwood_effigy", "moonring_seed", "creaking_husk_spawn_egg", "eyeblossom_wisp_spawn_egg", "wayfinder_pale_hollow"):
    item_model(it)
    item_def(it)

# frayed wayfinder: compass needle toward the nearest Return Rift (lodestone tracker), spins elsewhere
for k in range(32):
    item_model(f"frayed_wayfinder_{k:02d}")
entries = [{"model": {"type": "minecraft:model", "model": f"{NS}:item/frayed_wayfinder_{(i + 16) % 32:02d}"}, "threshold": 0.0 if i == 0 else i - 0.5}
           for i in range(33)]
a("items/frayed_wayfinder.json", {"model": {
    "type": "minecraft:condition", "component": "minecraft:lodestone_tracker", "property": "minecraft:has_component",
    "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "lodestone", "scale": 32.0, "entries": entries},
    "on_false": {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": "none", "scale": 32.0, "entries": entries},
}})

# greatbow with pulling states
a("models/item/heartwood_greatbow.json", {"parent": "minecraft:item/bow", "textures": {"layer0": f"{NS}:item/heartwood_greatbow"}})
for k in range(3):
    a(f"models/item/heartwood_greatbow_pulling_{k}.json", {"parent": f"{NS}:item/heartwood_greatbow",
                                                          "textures": {"layer0": f"{NS}:item/heartwood_greatbow_pulling_{k}"}})
a("items/heartwood_greatbow.json", {"model": {
    "type": "minecraft:condition", "property": "minecraft:using_item",
    "on_false": {"type": "minecraft:model", "model": f"{NS}:item/heartwood_greatbow"},
    "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                "entries": [{"model": {"type": "minecraft:model", "model": f"{NS}:item/heartwood_greatbow_pulling_1"}, "threshold": 0.65},
                            {"model": {"type": "minecraft:model", "model": f"{NS}:item/heartwood_greatbow_pulling_2"}, "threshold": 0.9}],
                "fallback": {"type": "minecraft:model", "model": f"{NS}:item/heartwood_greatbow_pulling_0"}}}})

# blocks
def cube_block(name, tex=None):
    a(f"models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{tex or name}"}})
    a(f"blockstates/{name}.json", {"variants": {"": {"model": f"{NS}:block/{name}"}}})
    item_def(name, f"{NS}:block/{name}")


for b in ("resin_heart", "moonring_stone", "ashen_moss"):
    cube_block(b)
a("models/block/arena_seal.json", {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/arena_seal"}})
a("blockstates/arena_seal.json", {"variants": {"": {"model": f"{NS}:block/arena_seal"}}})
a("models/block/heartwood_log.json", {"parent": "minecraft:block/cube_column",
                                      "textures": {"end": f"{NS}:block/heartwood_log_top", "side": f"{NS}:block/heartwood_log"}})
a("models/block/heartwood_log_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal",
                                                 "textures": {"end": f"{NS}:block/heartwood_log_top", "side": f"{NS}:block/heartwood_log"}})
a("blockstates/heartwood_log.json", {"variants": {
    "axis=x": {"model": f"{NS}:block/heartwood_log_horizontal", "x": 90, "y": 90},
    "axis=y": {"model": f"{NS}:block/heartwood_log"},
    "axis=z": {"model": f"{NS}:block/heartwood_log_horizontal", "x": 90}}})
item_def("heartwood_log", f"{NS}:block/heartwood_log")
for b in ("moon_bloom", "return_rift"):
    a(f"models/block/{b}.json", {"parent": "minecraft:block/cross", "textures": {"cross": f"{NS}:block/{b}"}})
    a(f"blockstates/{b}.json", {"variants": {"": {"model": f"{NS}:block/{b}"}}})
    item_model(b, parent="minecraft:item/generated")
    a(f"models/item/{b}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:block/{b}"}})
    item_def(b)

# particles
a("particles/rift_spark.json", {"textures": [f"{NS}:rift_spark_{i}" for i in range(4)]})
a("particles/resin_dust.json", {"textures": [f"{NS}:resin_dust_{i}" for i in range(3)]})
a("particles/moon_mote.json", {"textures": [f"{NS}:moon_mote_{i}" for i in range(3)]})

# sounds: re-pitched layers of vanilla sound files (referenced, not copied)
def snd(*layers):
    return {"sounds": [{"name": n, "pitch": p, "volume": v} for (n, p, v) in layers]}


a("sounds.json", {
    "rift.open": snd(("minecraft:block/respawn_anchor/charge1", 0.6, 1.0), ("minecraft:block/beacon/activate", 0.7, 0.8),
                     ("minecraft:block/amethyst/resonate1", 0.5, 1.0)),
    "rift.travel": snd(("minecraft:portal/travel", 1.6, 0.35), ("minecraft:mob/endermen/portal", 0.7, 0.9)),
    "rift.hum": snd(("minecraft:block/beacon/ambient", 0.55, 0.8), ("minecraft:block/amethyst/shimmer", 0.6, 0.6)),
    "trip.pull": snd(("minecraft:ambient/nether/soulsand_valley/mood1", 1.4, 0.8), ("minecraft:ambient/nether/soulsand_valley/mood3", 1.3, 0.8),
                     ("minecraft:ambient/cave/cave13", 1.2, 0.7)),
    "boss.mother_creak.roar": snd(("minecraft:mob/creaking/creaking_spawn", 0.45, 1.0), ("minecraft:mob/creaking/creaking_twitch", 0.4, 1.0)),
    "boss.mother_creak.stun": snd(("minecraft:block/creaking_heart/break/creaking_heart_break", 0.5, 1.0),
                                  ("minecraft:block/creaking_heart/hurt/trail3", 0.5, 1.0)),
    "boss.selenne.open": snd(("minecraft:block/amethyst/resonate2", 0.7, 1.0), ("minecraft:block/bell/resonate", 1.4, 0.8)),
    "boss.selenne.close": snd(("minecraft:block/bamboo_wood/break1", 0.5, 1.0), ("minecraft:note/hat", 0.5, 1.0)),
    "boss.selenne.beam": snd(("minecraft:block/beacon/power1", 1.6, 1.0), ("minecraft:mob/guardian/attack_loop", 1.3, 0.9)),
    "boss.seal": snd(("minecraft:block/beacon/activate", 0.5, 1.0), ("minecraft:block/respawn_anchor/set_spawn1", 0.6, 1.0)),
})

LANG = {
    "itemGroup.elsewhere": "Elsewhere",
    "dimension.elsewhere.pale_hollow": "The Pale Hollow",
    "biome.elsewhere.hollow_thicket": "Hollow Thicket", "biome.elsewhere.resin_bog": "Resin Bog",
    "biome.elsewhere.weeping_deeps": "Weeping Deeps", "biome.elsewhere.heartwood_grove": "Heartwood Grove",
    "biome.elsewhere.silent_clearing": "Silent Clearing",
    "item.elsewhere.wayfinder": "Wayfinder",
    "item.elsewhere.wayfinder.desc": "Craft with five things from a place to attune it.",
    "item.elsewhere.wayfinder_pale_hollow": "Wayfinder: The Pale Hollow",
    "item.elsewhere.attuned_wayfinder.desc": "Use to open a rift there. Use inside it to go home.",
    "item.elsewhere.frayed_wayfinder": "Frayed Wayfinder",
    "item.elsewhere.frayed_wayfinder.desc": "Points to the nearest Return Rift. Takes you home once.",
    "item.elsewhere.heartwood_shard": "Heartwood Shard", "item.elsewhere.pale_bark": "Pale Bark", "item.elsewhere.pale_sap": "Pale Sap",
    "item.elsewhere.unblinking_eye": "Unblinking Eye", "item.elsewhere.resin_heart_locket": "Resin Heart Locket",
    "item.elsewhere.heartwood_greatbow": "Heartwood Greatbow", "item.elsewhere.eyeblossom_circlet": "Eyeblossom Circlet",
    "item.elsewhere.moon_eye": "The Moon-Eye", "item.elsewhere.heartwood_effigy": "Heartwood Effigy",
    "item.elsewhere.moonring_seed": "Moonring Seed",
    "item.elsewhere.creaking_husk_spawn_egg": "Creaking Husk Spawn Egg", "item.elsewhere.eyeblossom_wisp_spawn_egg": "Eyeblossom Wisp Spawn Egg",
    "block.elsewhere.resin_heart": "Resin Heart", "block.elsewhere.heartwood_log": "Heartwood Log",
    "block.elsewhere.moonring_stone": "Moonring Stone", "block.elsewhere.ashen_moss": "Ashen Moss", "block.elsewhere.moon_bloom": "Moon Bloom",
    "block.elsewhere.arena_seal": "Arena Seal", "block.elsewhere.return_rift": "Return Rift",
    "entity.elsewhere.creaking_husk": "Creaking Husk", "entity.elsewhere.eyeblossom_wisp": "Eyeblossom Wisp",
    "entity.elsewhere.mother_creak": "Mother Creak, the Elder Heartwood", "entity.elsewhere.selenne": "Selenne, the Unblinking",
    "entity.elsewhere.rift": "Rift", "entity.elsewhere.root_spike": "Root Spike",
    "message.elsewhere.no_dimension": "That place (%s) is not loaded on this server.",
    "message.elsewhere.rift.open": "A rift tears open. Step through, or let it take you.",
    "message.elsewhere.rift.home": "A rift home opens.",
    "message.elsewhere.return_rift": "The Return Rift carries you home.",
    "message.elsewhere.frayed.used": "The Frayed Wayfinder unravels as the way home opens.",
    "message.elsewhere.frayed.home_already": "It only pulls when you are far from home.",
    "message.elsewhere.wayfinder.atlas": "The Wayfinder knows these places:",
    "message.elsewhere.trip.pull": "Something pulls at you...",
    "message.elsewhere.trip.cancelled": "The pull lets go.",
    "message.elsewhere.trip.arrived": "You drifted into %s. A Frayed Wayfinder will take you back once.",
    "message.elsewhere.effigy.wrong_place": "It only takes root in the Pale Hollow.",
    "tooltip.elsewhere.set": "Set: %s (%s/%s)",
    "subtitles.elsewhere.rift.open": "Rift opens", "subtitles.elsewhere.rift.travel": "Rift pulls",
    "subtitles.elsewhere.trip.pull": "Whispers", "subtitles.elsewhere.boss.mother_creak.roar": "Mother Creak groans",
    "subtitles.elsewhere.boss.selenne.open": "Selenne opens her eye", "subtitles.elsewhere.boss.selenne.close": "Selenne blinks",
    "subtitles.elsewhere.boss.selenne.beam": "Moonbeam fires",
}
a("lang/en_us.json", LANG)
print("data + assets written")
