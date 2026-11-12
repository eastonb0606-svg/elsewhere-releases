"""Generates the module's data pack JSON (dimensions, biomes, noise, structures, loot, tags, attunement).
Run: python3 tools/gen_data.py  (from the module folder or anywhere)."""
import json, os, copy
HERE = os.path.dirname(os.path.abspath(__file__))
D = os.path.join(HERE, "..", "src", "main", "resources", "data")
VANILLA = os.environ.get("MCDATA", "/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad/mcfull/data/minecraft")

def w(rel, obj):
    p = os.path.join(D, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(obj, open(p, "w"), indent=2)

E = "elsewhere:"
# ---------------------------------------------------------------------------------------------- dimension types
w("elsewhere/dimension_type/greenhaze.json", {
    "ambient_light": 0.1,
    "attributes": {
        "minecraft:visual/sky_color": "#9acd8c", "minecraft:visual/fog_color": "#c9b6e0",
        "minecraft:visual/cloud_color": "#ccf4e3ff", "minecraft:visual/cloud_height": 180.0,
        "minecraft:visual/sun_angle": 295.0, "minecraft:visual/moon_angle": 115.0, "minecraft:visual/star_angle": 295.0,
        "minecraft:visual/sunrise_sunset_color": "#ffd8a050",
        "minecraft:visual/fog_start_distance": 24.0,
        "minecraft:visual/ambient_light_color": "#141a10",
        "minecraft:gameplay/sky_light_level": 1.0,
        "minecraft:gameplay/bed_rule": {"can_set_spawn": "always", "can_sleep": "always"},
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.overworld.jungle"}},
    },
    "coordinate_scale": 1.0, "has_ceiling": False, "has_ender_dragon_fight": False, "has_fixed_time": True,
    "has_skylight": True, "height": 384, "infiniburn": "#minecraft:infiniburn_overworld", "logical_height": 384,
    "min_y": -64, "monster_spawn_block_light_limit": 0,
    "monster_spawn_light_level": {"type": "minecraft:uniform", "max_inclusive": 7, "min_inclusive": 0},
})
nether = json.load(open(os.path.join(VANILLA, "dimension_type/the_nether.json")))
w("elsewhere/dimension_type/the_hush.json", {
    "ambient_light": 0.0,
    "attributes": {
        "minecraft:visual/fog_color": "#061316", "minecraft:visual/sky_color": "#000000",
        "minecraft:visual/fog_start_distance": 4.0, "minecraft:visual/fog_end_distance": 56.0,
        "minecraft:visual/sky_light_color": "#3ae8ff", "minecraft:visual/sky_light_factor": 0.0,
        "minecraft:visual/ambient_light_color": "#0b1d22",
        "minecraft:gameplay/sky_light_level": 0.0,
        "minecraft:gameplay/bed_rule": nether["attributes"]["minecraft:gameplay/bed_rule"],
        "minecraft:gameplay/respawn_anchor_works": False,
        "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.overworld.deep_dark"}},
    },
    "cardinal_light": "nether", "coordinate_scale": 1.0, "has_ceiling": True, "has_ender_dragon_fight": False,
    "has_fixed_time": True, "has_skylight": False, "height": 384, "infiniburn": "#minecraft:infiniburn_overworld",
    "logical_height": 384, "min_y": -128, "monster_spawn_block_light_limit": 15, "monster_spawn_light_level": 15, "skybox": "none",
})

# ---------------------------------------------------------------------------------------------- noise (Hush: caves preset, taller)
caves = json.load(open(os.path.join(VANILLA, "worldgen/noise_settings/caves.json")))
hush = copy.deepcopy(caves)
hush["noise"] = {"height": 384, "min_y": -128}
hush["sea_level"] = -96
hush["default_block"] = "minecraft:deepslate"
hush["disable_mob_generation"] = False
s = json.dumps(hush["noise_router"]["final_density"])
s = s.replace('"from_coordinate": -72', '"from_coordinate": -136').replace('"to_coordinate": -40', '"to_coordinate": -104')
s = s.replace('"from_coordinate": 104', '"from_coordinate": 216').replace('"to_coordinate": 128', '"to_coordinate": 248')
hush["noise_router"]["final_density"] = json.loads(s)
hush.pop("debug_functions", None)
hush["material_rule"] = "elsewhere:the_hush"
w("elsewhere/worldgen/material_rule/the_hush.json", {"type": "minecraft:sequence", "sequence": [
    "minecraft:bedrock_roof", "minecraft:bedrock_floor",
    {"type": "minecraft:condition", "if_true": {"type": "minecraft:vertical_gradient", "false_at_and_above": {"absolute": -40},
        "random_name": "elsewhere:hush_tuff", "true_at_and_below": {"absolute": -110}}, "then_run": {"type": "minecraft:block", "result_state": "minecraft:deepslate"}},
    {"type": "minecraft:block", "result_state": "minecraft:deepslate"}]})
w("elsewhere/worldgen/noise_settings/the_hush.json", hush)

# ---------------------------------------------------------------------------------------------- biomes
def spawns(monster=(), creature=(), ambient=()):
    return {"argument": {"spawn_costs": {}, "spawns_by_category": {
        "monster": [{"type": t, "count": c, "weight": wt} for t, c, wt in monster],
        "creature": [{"type": t, "count": c, "weight": wt} for t, c, wt in creature],
        "ambient": [{"type": t, "count": c, "weight": wt} for t, c, wt in ambient]}}, "modifier": "overlay"}

def biome(name, temp, down, water, foliage, grass, sky, fog, music, particle, prob, features, sp, precip=True, water_fog=None):
    attrs = {"minecraft:visual/sky_color": sky, "minecraft:visual/fog_color": fog,
             "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": music}},
             "minecraft:gameplay/natural_mob_spawns": sp,
             "minecraft:visual/ambient_particles": {"argument": [{"particle": {"type": particle}, "probability": prob}], "modifier": "append"}}
    if water_fog: attrs["minecraft:visual/water_fog_color"] = water_fog
    eff = {"water_color": water}
    if foliage: eff["foliage_color"] = foliage
    if grass: eff["grass_color"] = grass
    w(f"elsewhere/worldgen/biome/{name}.json", {"attributes": attrs, "carvers": ["minecraft:cave", "minecraft:cave_extra_underground"],
        "downfall": down, "effects": eff, "features": features, "has_precipitation": precip, "temperature": temp})

GH_MON = [(E + "munchie_boar", 2, 40), (E + "hotbox_smog", 3, 40), (E + "paranoia_shade", 1, 15), ("minecraft:spider", 2, 20)]
GH_CRE = [(E + "lazy_gorger", 1, 10), (E + "toke_toad", 2, 14), ("minecraft:parrot", 2, 10)]
def gh_features(trees, extra_veg=()):
    veg = list(trees) + ["minecraft:patch_large_fern", "minecraft:patch_grass_jungle"] + list(extra_veg) + ["minecraft:vines"]
    return [[], [], [], [], [], [], ["minecraft:ore_coal_upper"], [], [], veg, []]
biome("kush_canopy", 0.95, 0.9, "#3fa58a", "#3e9b3a", "#4fae44", "#9acd8c", "#8fb88a", "minecraft:music.overworld.jungle",
      "minecraft:spore_blossom_air", 0.008, gh_features(["minecraft:bamboo_vegetation", "minecraft:trees_jungle"], ["minecraft:jungle_bush"]),
      spawns(GH_MON, GH_CRE))
biome("sativa_highlands", 0.8, 0.6, "#4cc3b0", "#6cc24a", "#7ccf55", "#bfe6ff", "#e9f2c4", "minecraft:music.overworld.jungle",
      "minecraft:firefly", 0.004, gh_features(["minecraft:trees_sparse_jungle"]), spawns(GH_MON, GH_CRE))
biome("indica_hollows", 0.7, 0.8, "#4a5a8a", "#5b3f7a", "#5f5a7a", "#6a5a8c", "#7a6a9c", "minecraft:music.overworld.jungle",
      "minecraft:spore_blossom_air", 0.015, gh_features(["minecraft:trees_jungle"]), spawns(GH_MON + [(E + "paranoia_shade", 2, 20)], GH_CRE))
biome("hazebottom_swamp", 0.85, 0.95, "#5e7f4a", "#4f7a3a", "#5a7f45", "#8fa37e", "#a595c2", "minecraft:music.overworld.swamp",
      "minecraft:campfire_cosy_smoke", 0.0006, gh_features([]), spawns(GH_MON + [(E + "hotbox_smog", 4, 60)], GH_CRE + [(E + "toke_toad", 3, 30)]))
biome("munchie_groves", 0.9, 0.7, "#6fc4a8", "#7cc24a", "#8ad055", "#ffd8a0", "#f4e3b4", "minecraft:music.overworld.flower_forest",
      "minecraft:falling_honey", 0.002, gh_features(["minecraft:trees_sparse_jungle"], ["minecraft:jungle_bush"]),
      spawns(GH_MON + [(E + "munchie_boar", 3, 60)], GH_CRE + [(E + "lazy_gorger", 2, 25)]))

HU_MON = [(E + "echo_stalker", 1, 30), (E + "ossuary_chorister", 1, 20), (E + "catalyst_mimic", 1, 10)]
HU_AMB = [(E + "shrieker_swarm", 4, 10)]
# Hushstone blobs replace deepslate in every Hush biome (QUESTIONS.md answer: size 33, 12 per chunk, whole height)
w("elsewhere/worldgen/feature/ore_hushstone.json", {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": 33,
    "targets": [{"state": "elsewhere:hushstone", "target": {"predicate_type": "minecraft:block_match", "block": "minecraft:deepslate"}}]})
w("elsewhere/worldgen/placed_feature/ore_hushstone.json", {"feature": "elsewhere:ore_hushstone", "placement": [
    {"type": "minecraft:count", "count": 12}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"above_bottom": 0}, "max_inclusive": {"below_top": 0}}},
    {"type": "minecraft:biome"}]})
def hu_features(geode=False):
    return [[], [], ["minecraft:amethyst_geode"] if geode else [], [], [], [], ["elsewhere:ore_hushstone"], ["minecraft:sculk_vein"],
            [], [], []]
for name, fog, water, wfog, particle, prob, music, geode in [
    ("the_murmur", "#061316", "#0f2a30", "#030b0d", "minecraft:sculk_soul", 0.002, "minecraft:music.overworld.deep_dark", False),
    ("echo_shafts", "#0b0f14", "#10202a", "#04080b", "minecraft:white_ash", 0.006, "minecraft:music.overworld.deep_dark", False),
    ("ossuary_galleries", "#0e1a1f", "#14262c", "#060e11", "minecraft:soul", 0.001, "minecraft:music.nether.soul_sand_valley", False),
    ("amethyst_choir", "#160e24", "#2a1a40", "#0a0612", "minecraft:end_rod", 0.0015, "minecraft:music.overworld.dripstone_caves", True),
    ("drowned_silence", "#04080a", "#0b1b22", "#02090c", "minecraft:underwater", 0.002, "minecraft:music.overworld.deep_dark", False)]:
    biome(name, 0.5, 0.0, water, None, None, "#000000", fog, music, particle, prob, hu_features(geode),
          spawns(HU_MON, [], HU_AMB), precip=False, water_fog=wfog)

# ---------------------------------------------------------------------------------------------- dimensions
def p(t, h, c, e, wd):
    return {"temperature": t, "humidity": h, "continentalness": c, "erosion": e, "weirdness": wd, "depth": 0.0, "offset": 0.0}
w("elsewhere/dimension/greenhaze.json", {"type": E + "greenhaze", "generator": {"type": "minecraft:noise", "settings": "minecraft:overworld",
    "biome_source": {"type": "minecraft:multi_noise", "biomes": [
        {"biome": E + "kush_canopy", "parameters": p([-1.0, 0.2], [-0.1, 1.0], [-0.1, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "sativa_highlands", "parameters": p([0.2, 1.0], [-1.0, 1.0], [0.3, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "indica_hollows", "parameters": p([-1.0, 0.2], [-1.0, -0.1], [-0.1, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "hazebottom_swamp", "parameters": p([-1.0, 1.0], [-1.0, 1.0], [-1.2, -0.1], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "munchie_groves", "parameters": p([0.2, 1.0], [-1.0, 1.0], [-0.1, 0.3], [-1.0, 1.0], [-1.0, 1.0])}]}}})
w("elsewhere/dimension/the_hush.json", {"type": E + "the_hush", "generator": {"type": "minecraft:noise", "settings": E + "the_hush",
    "biome_source": {"type": "minecraft:multi_noise", "biomes": [
        {"biome": E + "the_murmur", "parameters": p([-1.0, 0.0], [-1.0, 0.0], [-1.2, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "echo_shafts", "parameters": p([0.0, 1.0], [-1.0, -0.3], [-1.2, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "ossuary_galleries", "parameters": p([0.0, 1.0], [-0.3, 0.3], [-1.2, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "amethyst_choir", "parameters": p([0.0, 1.0], [0.3, 1.0], [-1.2, 1.0], [-1.0, 1.0], [-1.0, 1.0])},
        {"biome": E + "drowned_silence", "parameters": p([-1.0, 0.0], [0.0, 1.0], [-1.2, 1.0], [-1.0, 1.0], [-1.0, 1.0])}]}}})

# ---------------------------------------------------------------------------------------------- structures
GH = [E + b for b in ["kush_canopy", "sativa_highlands", "indica_hollows", "hazebottom_swamp", "munchie_groves"]]
HU = [E + b for b in ["the_murmur", "echo_shafts", "ossuary_galleries", "amethyst_choir", "drowned_silence"]]
def structure(name, kind, biomes, anchor, y, spacing, separation, salt, step="surface_structures"):
    w(f"elsewhere/worldgen/structure/{name}.json", {"type": E + "greenhaze_hush_built", "biomes": biomes, "step": step,
        "spawn_overrides": {}, "kind": kind, "anchor": anchor, "y": y})
    w(f"elsewhere/worldgen/structure_set/{name}.json", {"placement": {"type": "minecraft:random_spread", "salt": salt,
        "separation": separation, "spacing": spacing}, "structures": [{"structure": E + name, "weight": 1}]})
structure("great_hotbox", "great_hotbox", [E + "kush_canopy", E + "munchie_groves", E + "sativa_highlands"], "surface", 64, 48, 16, 61470101)
structure("bonfire_terrace", "bonfire_terrace", [E + "sativa_highlands", E + "munchie_groves", E + "kush_canopy"], "surface", 64, 44, 14, 61470102)
structure("sesh_hut", "sesh_hut", GH, "surface", 64, 20, 8, 61470103)
structure("lucid_spring", "lucid_spring", GH, "surface", 64, 16, 6, 61470104)
structure("greenhaze_return_rift", "greenhaze_return_rift", [b for b in GH if "swamp" not in b], "surface", 64, 37, 12, 61470105)
structure("listening_engine", "listening_engine", HU, "fixed", -20, 40, 14, 61470201, "underground_structures")
structure("choir_geode", "choir_geode", HU, "fixed", -72, 48, 16, 61470202, "underground_structures")
structure("silent_monastery", "silent_monastery", HU, "cave_floor", 40, 18, 6, 61470203, "underground_structures")
structure("hanging_undercity", "hanging_undercity", HU, "fixed", 150, 36, 12, 61470204, "underground_structures")

# ---------------------------------------------------------------------------------------------- misc data
w("elsewhere/damage_type/boss_attack.json", {"exhaustion": 0.1, "message_id": "mob", "scaling": "never"})
w("elsewhere/attunement/greenhaze.json", {"dimension": E + "greenhaze", "ingredients": ["minecraft:large_fern", "minecraft:campfire",
    "minecraft:honeycomb", "minecraft:cocoa_beans", "minecraft:glow_berries"], "requires": "none",
    "notes": "L C L / H W H / B G B with the base Wayfinder in the centre (DIMENSIONS.md 8)."})
w("elsewhere/attunement/the_hush.json", {"dimension": E + "the_hush", "ingredients": ["minecraft:echo_shard", "minecraft:sculk_shrieker",
    "minecraft:sculk_catalyst", "minecraft:disc_fragment_5", "minecraft:dragon_breath"], "requires": "ender_dragon_killed",
    "notes": "E S E / K W K / F D F; only craftable after the Ender Dragon dies (recipe condition + crafting-result check, core)."})
w("elsewhere/jukebox_song/four_twenty.json", {"comparator_output": 9, "description": {"translate": "jukebox_song.elsewhere.four_twenty"},
    "length_in_seconds": 185.0, "sound_event": "minecraft:music_disc.cat"})
w("elsewhere/jukebox_song/hush.json", {"comparator_output": 5, "description": {"translate": "jukebox_song.elsewhere.hush"},
    "length_in_seconds": 178.0, "sound_event": "minecraft:music_disc.5"})
