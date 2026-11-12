#!/usr/bin/env python3
"""Generates the module's worldgen and gameplay data (dimensions, biomes, noise, material rules, features, structures,
tags, loot tables, attunement, jukebox songs). Run from the module folder: python3 tools/gen_data.py"""
import json, os
R = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')
D = os.path.join(R, 'data', 'elsewhere')

def w(path, obj):
    p = os.path.join(R, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f: json.dump(obj, f, indent=1)

def noise(name, xz, y=0.0):
    return {"type": "minecraft:noise", "noise": name, "shift_x": "minecraft:shift_x", "shift_z": "minecraft:shift_z", "xz_scale": xz, "y_scale": y}
def add(a, b): return {"type": "minecraft:add", "left": a, "right": b}
def mul(a, b): return {"type": "minecraft:mul", "left": a, "right": b}
def mx(a, b): return {"type": "minecraft:max", "left": a, "right": b}
def mn(a, b): return {"type": "minecraft:min", "left": a, "right": b}
def ab(a): return {"type": "minecraft:abs", "input": a}
def grad(y0, v0, y1, v1): return {"type": "minecraft:gradient", "axis": "y", "from_coordinate": y0, "from_value": v0, "to_coordinate": y1, "to_value": v1}
def interp(f): return {"type": "minecraft:interpolated", "cell_size_xz": 4, "cell_size_y": 8, "input": f}
def lin(y, k, at0): # density = (at0 - y) * k over the world height
    return grad(-64, (at0 + 64) * k, 2000, (at0 - 2000) * k)

def block(b): return {"type": "minecraft:block", "result_state": b}
def cond(c, then): return {"type": "minecraft:condition", "if_true": c, "then_run": then}
def biome(*ids): return {"type": "minecraft:biome", "biome_is": list(ids)}
def yabove(y): return {"type": "minecraft:y_above", "add_stone_depth": False, "anchor": {"absolute": y}, "surface_depth_multiplier": 0}
def depth(off, typ="floor"): return {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": off, "secondary_depth_range": 0, "surface_type": typ}
def seq(*r): return {"type": "minecraft:sequence", "sequence": list(r)}
def S(name, props=None):
    return name

def music(sound):
    return {"default": {"max_delay": 24000, "min_delay": 6000, "sound": sound}, "underwater": {"max_delay": 24000, "min_delay": 6000, "sound": "minecraft:music.under_water"}}

def biome_json(sky, fog, water, water_fog, particle, prob, mus, temp, precip, features, spawns, extra=None):
    attrs = {
        "minecraft:visual/sky_color": sky, "minecraft:visual/fog_color": fog, "minecraft:visual/water_fog_color": water_fog,
        "minecraft:audio/background_music": music(mus),
        "minecraft:gameplay/natural_mob_spawns": {"argument": {"spawn_costs": {}, "spawns_by_category": spawns}, "modifier": "overlay"},
    }
    if particle:
        attrs["minecraft:visual/ambient_particles"] = {"argument": [{"particle": {"type": particle}, "probability": prob}], "modifier": "append"}
    if extra: attrs.update(extra)
    return {"attributes": attrs, "carvers": [], "downfall": 0.8 if precip else 0.0, "effects": {"water_color": water},
            "features": features, "has_precipitation": precip, "temperature": temp}

def spawn(t, w_, lo=1, hi=1):
    return {"type": t, "count": lo if lo == hi else {"type": "minecraft:uniform", "min_inclusive": lo, "max_inclusive": hi}, "weight": w_}

def feats(ores=(), veg=(), local=(), top=()):
    f = [[] for _ in range(11)]
    f[6] = list(ores); f[9] = list(veg); f[4] = list(local); f[10] = list(top)
    return f

# ---------------------------------------------------------------- Verdigris Sea
VS = "elsewhere:verdigris_sea"
w('data/elsewhere/dimension_type/verdigris_sea.json', {
    "ambient_light": 0.1,
    "attributes": {
        "minecraft:visual/sky_color": "#3f6f68", "minecraft:visual/fog_color": "#6fa597", "minecraft:visual/cloud_color": "#cc5e8c80",
        "minecraft:visual/cloud_height": 180.0, "minecraft:visual/ambient_light_color": "#0a1412",
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark", "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.under_water"}},
    },
    "coordinate_scale": 2.0, "default_clock": "minecraft:overworld", "has_ceiling": False, "has_ender_dragon_fight": False, "has_skylight": True,
    "height": 384, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 384, "min_y": -64,
    "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
    "timelines": "#minecraft:in_overworld"})

floor = add(lin(0, 1 / 24.0, 40), mul(noise("minecraft:surface", 0.6), 0.5))             # sea floor ~Y 40 +- 12
trench_n = noise("minecraft:ridge", 0.25)
trench = mn(0, mul(add(ab(trench_n), -0.12), 25))                                          # ridged carve down to ~Y -30
atoll_n = noise("minecraft:continentalness", 0.35)
atoll = mul(mx(0, add(atoll_n, -0.45)), 6.5)                                               # rare bumps just above sea level 96
pylon_n = noise("minecraft:pillar", 6.0)
pylon = mn(mul(add(pylon_n, -0.72), 40), lin(0, 0.2, 150))                                 # columns from the floor up to Y 150
fd = mx(add(add(floor, trench), atoll), pylon)
w('data/elsewhere/worldgen/noise_settings/verdigris_sea.json', {
    "default_block": "minecraft:stone", "default_fluid": "minecraft:water", "disable_mob_generation": False, "legacy_random_source": False,
    "material_rule": "elsewhere:verdigris_sea", "noise": {"height": 384, "min_y": -64}, "sea_level": 96, "spawn_target": [],
    "noise_router": {"chunk_surface_level": 0.0, "continents": atoll_n, "depth": 0.0, "erosion": trench_n, "final_density": interp(fd),
                     "ridges": pylon_n, "temperature": 0.0, "vegetation": noise("minecraft:vegetation", 0.3)}})
bands = []
for i, y in enumerate(range(150, 56, -6)):
    bands.append(cond(yabove(y), block(S("minecraft:oxidized_copper" if i % 2 == 0 else "minecraft:dark_prismarine"))))
w('data/elsewhere/worldgen/material_rule/verdigris_sea.json', seq(
    "minecraft:bedrock_floor",
    cond(biome("elsewhere:storm_pylons"), cond(yabove(56), seq(*bands))),
    cond(depth(0), seq(
        cond(biome("elsewhere:barnacle_atolls"), cond(yabove(92), block(S("minecraft:calcite")))),
        cond(biome("elsewhere:barnacle_atolls"), block(S("minecraft:tuff"))),
        cond(biome("elsewhere:foundry_trench"), block(S("minecraft:gravel"))),
        cond(biome("elsewhere:kelpwood_reaches"), block(S("minecraft:mud"))),
        block(S("minecraft:sand")))),
    cond(depth(3), seq(cond(biome("elsewhere:foundry_trench"), block(S("minecraft:tuff"))), block(S("minecraft:sandstone")))),
    cond({"type": "minecraft:not", "invert": yabove(0)}, block(S("minecraft:deepslate", {"axis": "y"}))),
))
SEA_SPAWNS = {
    "monster": [spawn("elsewhere:clockwork_drowned", 60, 1, 3), spawn("elsewhere:tidewing_ray", 20, 1, 2), spawn("elsewhere:brass_guardian", 15, 1, 2)],
    "water_creature": [spawn("minecraft:squid", 4, 1, 3)], "water_ambient": [spawn("minecraft:cod", 10, 3, 6)],
    "creature": [spawn("elsewhere:barnacle_golem", 5, 1, 1)],
}
ore_sea = ["elsewhere:pearlite_ore", "minecraft:ore_copper"]
VEG_ORDER = ["minecraft:warm_ocean_vegetation", "minecraft:kelp_cold", "minecraft:kelp_warm", "minecraft:seagrass_deep_warm", "minecraft:seagrass_deep", "minecraft:sea_pickle"]
def vo(*names): return [v for v in VEG_ORDER if v in names]
veg_sea = ["minecraft:seagrass_deep", "minecraft:sea_pickle"]
for bid, sky, fog, water, wfog, part, prob, mus, veg in [
    ("verdigris_shallows", "#3f6f68", "#6fa597", "#2fa58b", "#0f4a40", "minecraft:underwater", 0.0025, "minecraft:music.under_water", vo("minecraft:kelp_cold", "minecraft:seagrass_deep", "minecraft:sea_pickle")),
    ("kelpwood_reaches", "#3a6458", "#5e9484", "#1e8c6e", "#0b3a30", "minecraft:underwater", 0.0035, "minecraft:music.under_water", vo("minecraft:kelp_cold", "minecraft:kelp_warm", "minecraft:seagrass_deep_warm", "minecraft:seagrass_deep", "minecraft:sea_pickle")),
    ("foundry_trench", "#2b4a48", "#3e6a64", "#147a66", "#08201c", "minecraft:bubble", 0.02, "minecraft:music.overworld.deep_dark", vo("minecraft:seagrass_deep")),
    ("storm_pylons", "#2d4a4a", "#4f7b74", "#2a9a80", "#0e3e36", "minecraft:electric_spark", 0.004, "minecraft:music_disc.otherside", vo("minecraft:seagrass_deep", "minecraft:sea_pickle")),
    ("barnacle_atolls", "#5c8e86", "#8fbdb2", "#3bb89a", "#13574a", "minecraft:falling_water", 0.01, "minecraft:music.overworld.lush_caves", vo("minecraft:warm_ocean_vegetation", "minecraft:seagrass_deep", "minecraft:sea_pickle")),
]:
    w(f'data/elsewhere/worldgen/biome/{bid}.json', biome_json(sky, fog, water, wfog, part, prob, mus, 0.5, True, feats(ores=ore_sea, veg=veg), SEA_SPAWNS))
FULL = [-1.0, 1.0]
def pt(b, **k):
    p = {"temperature": FULL, "humidity": FULL, "continentalness": FULL, "erosion": FULL, "weirdness": FULL, "depth": 0.0, "offset": 0.0}
    p.update(k)
    return {"biome": b, "parameters": p}
w('data/elsewhere/dimension/verdigris_sea.json', {"type": VS, "generator": {"type": "minecraft:noise", "settings": VS, "biome_source": {"type": "minecraft:multi_noise", "biomes": [
    pt("elsewhere:verdigris_shallows", offset=0.15),
    pt("elsewhere:barnacle_atolls", continentalness=[0.6, 1.0]),
    pt("elsewhere:foundry_trench", erosion=[0.0, 0.0]),
    pt("elsewhere:storm_pylons", weirdness=[0.65, 1.0]),
    pt("elsewhere:kelpwood_reaches", humidity=[0.45, 1.0]),
]}}})

# ---------------------------------------------------------------- Ironpeak
IP = "elsewhere:ironpeak"
H = 1536
w('data/elsewhere/dimension_type/ironpeak.json', {
    "ambient_light": 0.0,
    "attributes": {
        "minecraft:visual/sky_color": "#78a7ff", "minecraft:visual/fog_color": "#c0d8ff", "minecraft:visual/cloud_color": "#ccffffff",
        "minecraft:visual/cloud_height": 640.0,
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "when_dark", "error_message": {"translate": "block.minecraft.bed.no_sleep"}},
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.overworld.meadow"}},
    },
    "coordinate_scale": 1.0, "default_clock": "minecraft:overworld", "has_ceiling": False, "has_ender_dragon_fight": False, "has_skylight": True,
    "height": H, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": H, "min_y": -64,
    "monster_spawn_block_light_limit": 0, "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
    "timelines": "#minecraft:in_overworld"})
ridge_n = noise("minecraft:continentalness", 0.12)
r = add(1.0, mul(-1.0, ab(ridge_n)))                 # 1 on the ridge line, 0 far away
r2 = mul(r, r)
r4 = mul(r2, r2)
height = add(1.8, add(mul(r2, 4.5), mul(r4, 8.2)))     # in hundreds of blocks: 180 in the valleys .. ~1450 on the ridge
k = 0.01
dens = add(add(grad(-64, 0.64, 2000, -20.0), height), mul(noise("minecraft:jagged", 1.5, 0.0), 0.35))
dens = add(dens, mul({"type": "minecraft:noise", "noise": "minecraft:surface", "shift_x": "minecraft:shift_x", "shift_z": "minecraft:shift_z", "xz_scale": 2.0, "y_scale": 2.0}, 0.10))
w('data/elsewhere/worldgen/noise_settings/ironpeak.json', {
    "default_block": "minecraft:stone", "default_fluid": "minecraft:water", "disable_mob_generation": False, "legacy_random_source": False,
    "material_rule": "elsewhere:ironpeak", "noise": {"height": H, "min_y": -64}, "sea_level": 0, "spawn_target": [],
    "noise_router": {"chunk_surface_level": 0.0, "continents": ridge_n, "depth": grad(-64, -0.064, 1472, 1.472), "erosion": 0.0,
                     "final_density": interp(dens), "ridges": 0.0, "temperature": 0.0, "vegetation": noise("minecraft:vegetation", 0.4)}})
strata = []
for i, y in enumerate(range(1400, 100, -18)):
    strata.append(cond(yabove(y), block(S(["minecraft:andesite", "minecraft:stone", "minecraft:granite", "minecraft:stone", "minecraft:tuff", "minecraft:calcite", "minecraft:stone"][i % 7]))))
w('data/elsewhere/worldgen/material_rule/ironpeak.json', seq(
    "minecraft:bedrock_floor",
    cond(depth(0), seq(cond(yabove(900), block(S("minecraft:packed_ice"))), cond(yabove(500), block(S("minecraft:snow_block"))))),
    cond(biome("elsewhere:granite_terraces", "elsewhere:frostbite_shelf", "elsewhere:the_thin_air"), seq(*strata)),
    cond(depth(0), seq(cond(biome("elsewhere:cold_springs"), block(S("minecraft:podzol"))), block(S("minecraft:grass_block")))),
    cond(depth(2), block(S("minecraft:dirt"))),
    cond({"type": "minecraft:not", "invert": yabove(0)}, block(S("minecraft:deepslate"))),
))
PEAK_SPAWNS = lambda more: {"creature": [spawn("elsewhere:highland_yak", 8, 2, 4)], "monster": [spawn("elsewhere:ironclan_raider", 30, 3, 3), spawn("elsewhere:crag_roc", 10, 1, 1)] + more}
ores_ip = ["minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "elsewhere:big_iron_cliffs", "minecraft:ore_emerald"]
for bid, sky, fog, water, part, prob, mus, temp, veg, spawns, ores, top in [
    ("iron_foothills", "#78a7ff", "#c0d8ff", "#3f76e4", None, 0, "minecraft:music.overworld.meadow", 0.3, ["minecraft:trees_taiga", "minecraft:patch_grass_taiga"], PEAK_SPAWNS([]), ores_ip, []),
    ("granite_terraces", "#6e9bea", "#b8ccf0", "#3d6fd0", None, 0, "minecraft:music.overworld.stony_peaks", 0.2, ["minecraft:spruce_on_snow"], PEAK_SPAWNS([spawn("elsewhere:rimeguard", 4, 1, 1)]), ores_ip, []),
    ("frostbite_shelf", "#8fb2e8", "#dce9f5", "#3d57d6", "minecraft:snowflake", 0.01, "minecraft:music.overworld.frozen_peaks", -0.3, ["minecraft:pile_snow"], PEAK_SPAWNS([spawn("elsewhere:rimeguard", 12, 1, 1)]), ores_ip + ["elsewhere:skyiron_ore"], ["minecraft:freeze_top_layer"]),
    ("cold_springs", "#86aee8", "#d0e2f2", "#5fc8e0", "minecraft:white_smoke", 0.006, "minecraft:music.overworld.grove", 0.1, ["minecraft:trees_grove"], PEAK_SPAWNS([]), ores_ip, []),
    ("the_thin_air", "#2b4c9a", "#9eb4d8", "#2f4fb0", "minecraft:white_ash", 0.004, "minecraft:music_disc.precipice", -0.7, [], {"monster": [spawn("elsewhere:rimeguard", 20, 1, 1), spawn("elsewhere:crag_roc", 10, 1, 1)]}, ["elsewhere:skyiron_ore"], ["minecraft:freeze_top_layer"]),
]:
    extra = {"minecraft:visual/star_brightness": {"argument": 0.6, "modifier": "maximum"}} if bid == "the_thin_air" else None
    w(f'data/elsewhere/worldgen/biome/{bid}.json', biome_json(sky, fog, water, "#050533", part, prob, mus, temp, True, feats(ores=ores, veg=veg, top=top), spawns, extra))
w('data/elsewhere/dimension/ironpeak.json', {"type": IP, "generator": {"type": "minecraft:noise", "settings": IP, "biome_source": {"type": "minecraft:multi_noise", "biomes": [
    pt("elsewhere:iron_foothills", depth=[-1.0, 0.30], humidity=[-1.0, 0.45]),
    pt("elsewhere:cold_springs", depth=[-1.0, 0.45], humidity=[0.45, 1.0]),
    pt("elsewhere:granite_terraces", depth=[0.30, 0.55], humidity=[-1.0, 0.45]),
    pt("elsewhere:frostbite_shelf", depth=[0.55, 1.10]),
    pt("elsewhere:the_thin_air", depth=[1.10, 2.0]),
]}}})

# ---------------------------------------------------------------- features
def ore(name, block_id, size, count, lo, hi, tag="minecraft:stone_ore_replaceables"):
    w(f'data/elsewhere/worldgen/feature/{name}.json', {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": size,
        "targets": [{"state": S(block_id), "target": {"predicate_type": "minecraft:tag_match", "tag": tag}}]})
    w(f'data/elsewhere/worldgen/placed_feature/{name}.json', {"feature": f"elsewhere:{name}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
        {"type": "minecraft:biome"}]})
ore("pearlite_ore", "elsewhere:pearlite_ore", 6, 8, -40, 70)
ore("skyiron_ore", "elsewhere:skyiron_ore", 5, 10, 640, 1460)
ore("big_iron_cliffs", "minecraft:iron_ore", 18, 12, 120, 1200)


# ---------------------------------------------------------------- structures
def tag(path, values):
    w(path, {"replace": False, "values": values})
VS_ALL = ["elsewhere:verdigris_shallows", "elsewhere:kelpwood_reaches", "elsewhere:foundry_trench", "elsewhere:storm_pylons", "elsewhere:barnacle_atolls"]
tag('data/elsewhere/tags/worldgen/biome/is_verdigris_sea.json', VS_ALL)
tag('data/elsewhere/tags/worldgen/biome/is_ironpeak.json', ["elsewhere:iron_foothills", "elsewhere:granite_terraces", "elsewhere:frostbite_shelf", "elsewhere:cold_springs", "elsewhere:the_thin_air"])
def proc(name, blueprint, biomes, placement="surface", lo=-64, hi=2000, yoff=0, step="surface_structures"):
    w(f'data/elsewhere/worldgen/structure/{name}.json', {"type": "elsewhere:vi_procedural", "biomes": biomes, "spawn_overrides": {}, "step": step,
        "blueprint": blueprint, "placement": placement, "min_height": lo, "max_height": hi, "y_offset": yoff})
def sset(name, structs, spacing, sep, salt):
    w(f'data/elsewhere/worldgen/structure_set/{name}.json', {"placement": {"type": "minecraft:random_spread", "salt": salt, "separation": sep, "spacing": spacing},
        "structures": [{"structure": s, "weight": 1} for s in structs]})
proc("verdigris_sea_return_rift", "return_rift_verdigris_sea", "#elsewhere:is_verdigris_sea", "fixed", 99, 99)
proc("ironpeak_return_rift", "return_rift_ironpeak", "elsewhere:iron_foothills", "surface", 100, 400, 1)
sset("verdigris_sea_return_rifts", ["elsewhere:verdigris_sea_return_rift"], 37, 18, 71231)
sset("ironpeak_return_rifts", ["elsewhere:ironpeak_return_rift"], 37, 18, 71232)
proc("clockwork_rig", "clockwork_rig", "#elsewhere:is_verdigris_sea", "fixed", 96, 96)
sset("clockwork_rigs", ["elsewhere:clockwork_rig"], 20, 8, 71233)
proc("engineers_atoll", "engineers_atoll", "elsewhere:barnacle_atolls", "surface", 94, 140, 1)
sset("engineers_atolls", ["elsewhere:engineers_atoll"], 16, 6, 71234)
proc("drowned_cathedral", "drowned_cathedral", "elsewhere:foundry_trench", "ocean_floor", -60, 40, 1)
sset("drowned_cathedrals", ["elsewhere:drowned_cathedral"], 48, 20, 71235)
proc("lighthouse", "lighthouse", "#elsewhere:is_verdigris_sea", "fixed", 120, 120)
sset("lighthouses", ["elsewhere:lighthouse"], 56, 24, 71236)
proc("clan_hold", "clan_hold", "elsewhere:granite_terraces", "surface", 250, 700, 1)
sset("clan_holds", ["elsewhere:clan_hold"], 14, 5, 71237)
proc("climbers_cairn", "climbers_cairn", "#elsewhere:is_ironpeak", "surface", 150, 1400, 1)
sset("climbers_cairns", ["elsewhere:climbers_cairn"], 10, 4, 71238)
proc("proving_grounds", "proving_grounds", "#elsewhere:is_ironpeak", "surface", 620, 800, 1)
sset("proving_grounds", ["elsewhere:proving_grounds"], 32, 12, 71239)
proc("summit_throne", "summit_throne", "#elsewhere:is_ironpeak", "surface", 1300, 1470, 1)
sset("summit_thrones", ["elsewhere:summit_throne"], 24, 8, 71240)
w('data/elsewhere/worldgen/structure/verdigris_shipwreck.json', {"type": "minecraft:shipwreck", "biomes": "#elsewhere:is_verdigris_sea", "is_beached": False, "spawn_overrides": {}, "step": "surface_structures"})
sset("verdigris_shipwreck_graveyards", ["elsewhere:verdigris_shipwreck"], 8, 3, 71241)

# ---------------------------------------------------------------- attunement, songs
w('data/elsewhere/attunement/verdigris_sea.json', {"dimension": VS, "ingredients": ["minecraft:lightning_rod", "minecraft:nautilus_shell", "minecraft:prismarine_crystals", "minecraft:dried_kelp_block", "minecraft:heart_of_the_sea"], "requires": "none",
    "notes": "R N R / P W P / K O K (DIMENSIONS.md section 8). Mid-late."})
w('data/elsewhere/attunement/ironpeak.json', {"dimension": IP, "ingredients": ["minecraft:iron_block", "minecraft:goat_horn", "minecraft:packed_ice", "minecraft:spruce_log", "minecraft:emerald"], "requires": "none",
    "notes": "I G I / Q W Q / L M L (DIMENSIONS.md section 8). Foothills early, summit late."})
w('data/elsewhere/jukebox_song/low_tide_engine.json', {"comparator_output": 11, "description": {"translate": "jukebox_song.elsewhere.low_tide_engine"}, "length_in_seconds": 208.0, "sound_event": "elsewhere:music_disc.low_tide_engine"})
w('data/elsewhere/jukebox_song/thin_air.json', {"comparator_output": 12, "description": {"translate": "jukebox_song.elsewhere.thin_air"}, "length_in_seconds": 210.0, "sound_event": "elsewhere:music_disc.thin_air"})
print("data ok")

# ---------------------------------------------------------------- loot tables and tags
def drop_self(b, item=None, count=None):
    entry = {"type": "minecraft:item", "name": item or f"elsewhere:{b}"}
    if count: entry["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": count[0], "max": count[1]}}]
    w(f'data/elsewhere/loot_table/blocks/{b}.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [entry], "condition": {"type": "minecraft:survives_explosion"}}]})
def slab_drop(b):
    w(f'data/elsewhere/loot_table/blocks/{b}.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"elsewhere:{b}",
        "modifier": [{"type": "minecraft:set_count", "count": 2, "condition": {"type": "minecraft:match_block", "blocks": f"elsewhere:{b}", "state": {"type": "double"}}}]}]}]})
PICK = ["verdigris_plate", "riveted_verdigris_plate", "verdigris_plate_stairs", "verdigris_plate_slab", "verdigris_plate_wall", "verdigris_grating", "verdigris_pipe",
        "verdigris_pipe_junction", "storm_coil", "pearlite_ore", "frostgranite", "frostgranite_stairs", "frostgranite_slab", "frostgranite_wall", "polished_frostgranite",
        "polished_frostgranite_stairs", "polished_frostgranite_slab", "frostgranite_bricks", "frostgranite_brick_stairs", "frostgranite_brick_slab", "frostgranite_brick_wall",
        "skyiron_ore", "hearthstone", "glacier_ice"]
AXE = ["kelpwood_log", "stripped_kelpwood_log", "kelpwood_wood", "stripped_kelpwood_wood", "kelpwood_planks", "kelpwood_stairs", "kelpwood_slab", "kelpwood_fence",
       "kelpwood_fence_gate", "kelpwood_door", "kelpwood_trapdoor", "kelpwood_button", "kelpwood_pressure_plate", "summit_war_drum"]
for b in PICK + AXE + ["barnacle_cluster", "prayer_flags"]:
    if b.endswith('_slab'): slab_drop(b)
    elif b == 'pearlite_ore': drop_self(b, "elsewhere:pearlite", (1, 2))
    elif b == 'skyiron_ore': drop_self(b, "elsewhere:skyiron")
    elif b == 'kelpwood_door': w(f'data/elsewhere/loot_table/blocks/{b}.json', {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "elsewhere:kelpwood_door"}],
        "condition": {"type": "minecraft:match_block", "blocks": "elsewhere:kelpwood_door", "state": {"half": "lower"}}}]})
    else: drop_self(b)
tag('data/minecraft/tags/block/mineable/pickaxe.json', [f"elsewhere:{b}" for b in PICK])
tag('data/minecraft/tags/block/mineable/axe.json', [f"elsewhere:{b}" for b in AXE])
tag('data/minecraft/tags/block/needs_iron_tool.json', ["elsewhere:pearlite_ore", "elsewhere:skyiron_ore"])
tag('data/minecraft/tags/block/wooden_fences.json', ["elsewhere:kelpwood_fence"])
tag('data/minecraft/tags/block/walls.json', ["elsewhere:verdigris_plate_wall", "elsewhere:frostgranite_wall", "elsewhere:frostgranite_brick_wall"])
tag('data/minecraft/tags/block/fence_gates.json', ["elsewhere:kelpwood_fence_gate"])
tag('data/minecraft/tags/block/logs.json', ["elsewhere:kelpwood_log", "elsewhere:stripped_kelpwood_log", "elsewhere:kelpwood_wood", "elsewhere:stripped_kelpwood_wood"])
tag('data/minecraft/tags/block/planks.json', ["elsewhere:kelpwood_planks"])
tag('data/minecraft/tags/item/planks.json', ["elsewhere:kelpwood_planks"])
tag('data/minecraft/tags/item/logs.json', ["elsewhere:kelpwood_log", "elsewhere:stripped_kelpwood_log", "elsewhere:kelpwood_wood", "elsewhere:stripped_kelpwood_wood"])
tag('data/minecraft/tags/item/freeze_immune_wearables.json', ["elsewhere:climber_helmet", "elsewhere:climber_chestplate", "elsewhere:climber_leggings", "elsewhere:climber_boots", "elsewhere:crampons", "elsewhere:champions_belt"])
tag('data/minecraft/tags/item/axes.json', ["elsewhere:tidesteel_axe", "elsewhere:skyiron_axe", "elsewhere:ice_axe", "elsewhere:kingsplitter"])
tag('data/minecraft/tags/item/pickaxes.json', ["elsewhere:tidesteel_pickaxe", "elsewhere:skyiron_pickaxe"])
tag('data/minecraft/tags/item/swords.json', ["elsewhere:tidesteel_sword", "elsewhere:skyiron_sword"])
tag('data/minecraft/tags/item/shovels.json', ["elsewhere:tidesteel_shovel", "elsewhere:skyiron_shovel"])
tag('data/minecraft/tags/item/hoes.json', ["elsewhere:tidesteel_hoe", "elsewhere:skyiron_hoe"])
tag('data/elsewhere/tags/item/tidesteel_tool_materials.json', ["elsewhere:pearlite"])
tag('data/elsewhere/tags/item/skyiron_tool_materials.json', ["elsewhere:skyiron"])
tag('data/elsewhere/tags/item/repairs_diving_suit.json', ["elsewhere:pearlite"])
tag('data/elsewhere/tags/item/repairs_climber_armor.json', ["elsewhere:skyiron"])
tag('data/c/tags/item/foods/cooked_meat.json', ["elsewhere:cooked_yak_meat"])
tag('data/c/tags/item/foods/raw_meat.json', ["elsewhere:yak_meat"])
def mob_loot(e, entries):
    w(f'data/elsewhere/loot_table/entities/{e}.json', {"type": "minecraft:entity", "pools": [{"rolls": 1, "entries": [x]} for x in entries]})
def it(name, lo=1, hi=1, chance=None):
    e = {"type": "minecraft:item", "name": name, "modifier": [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]}
    if chance is not None: e["condition"] = {"type": "minecraft:random_chance", "chance": chance}
    return e
mob_loot("clockwork_drowned", [it("elsewhere:verdigris_gear", 1, 1, 0.40), it("minecraft:copper_nugget", 1, 3)])
mob_loot("barnacle_golem", [it("minecraft:copper_ingot", 1, 3), it("minecraft:brain_coral", 0, 1), it("elsewhere:verdigris_gear", 1, 1, 0.5)])
mob_loot("tidewing_ray", [it("minecraft:prismarine_crystals", 1, 2), it("elsewhere:ray_leather", 1, 1, 0.6)])
mob_loot("brass_guardian", [it("elsewhere:tide_key", 1, 1, 0.25), it("elsewhere:verdigris_gear", 1, 2), it("minecraft:prismarine_shard", 0, 2)])
mob_loot("highland_yak", [it("elsewhere:yak_meat", 1, 3), it("minecraft:leather", 0, 2)])
mob_loot("ironclan_raider", [it("minecraft:iron_ingot", 1, 2), it("elsewhere:skyiron", 1, 1, 0.10), it("minecraft:emerald", 0, 1)])
mob_loot("crag_roc", [it("elsewhere:roc_feather", 1, 2), it("minecraft:chicken", 1, 2)])
mob_loot("rimeguard", [it("elsewhere:skyiron", 1, 1, 0.5), it("minecraft:packed_ice", 1, 3), it("minecraft:iron_ingot", 1, 3)])
for e in ["verdigrim", "galvanox", "thurgrim", "hrolf", "grimhorn"]:
    w(f'data/elsewhere/loot_table/entities/{e}.json', {"type": "minecraft:entity", "pools": []})
def chest(name, items):
    w(f'data/elsewhere/loot_table/chests/{name}.json', {"type": "minecraft:chest", "pools": [{"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [dict(it(n, lo, hi), weight=wt) for n, lo, hi, wt in items]}]})
chest("clockwork_rig", [("elsewhere:verdigris_gear", 1, 4, 10), ("minecraft:copper_ingot", 2, 6, 10), ("elsewhere:pearlite", 1, 2, 5), ("elsewhere:brine_lung", 1, 2, 4), ("minecraft:prismarine_crystals", 1, 4, 6)])
chest("engineers_atoll", [("elsewhere:verdigris_gear", 2, 5, 10), ("minecraft:nautilus_shell", 1, 1, 2), ("elsewhere:diving_helm", 1, 1, 1), ("minecraft:copper_ingot", 2, 8, 8)])
chest("clan_hold", [("minecraft:iron_ingot", 2, 6, 10), ("elsewhere:cooked_yak_meat", 1, 4, 8), ("elsewhere:skyiron", 1, 2, 3), ("minecraft:iron_bars", 4, 12, 6), ("minecraft:emerald", 1, 3, 5)])
chest("climbers_cairn", [("elsewhere:crampons", 1, 1, 2), ("minecraft:iron_ingot", 1, 3, 10), ("minecraft:bread", 2, 4, 8), ("minecraft:torch", 4, 12, 8), ("elsewhere:yak_butter_tea", 1, 1, 3)])
print("loot ok")
