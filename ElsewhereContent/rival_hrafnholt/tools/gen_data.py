"""Hrafnholt data: faction JSON, kingdom_unit JSONs, item models/definitions, equipment layers, trim/banner patterns, recipes,
tags, lang, JEI info and boss_music slot ids. Run: python3 gen_data.py (after gen_art.py). Numbers: RIVALS-DECISIONS 2-4/6/8.1,
RIVALS.md v2, RIVAL-BOSSES.md H1-H6, WAR-AI.md (Hrafnholt column)."""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
DATA = os.path.join(RES, "data", "elsewhere")
ASSET = os.path.join(RES, "assets", "elsewhere")
INTEG = os.path.join(HERE, "..", "INTEGRATION")


def w(path, obj, base=RES):
    p = os.path.join(base, path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)
        f.write("\n")


def E(n): return "elsewhere:" + n
def M(n): return "minecraft:" + n


SK = lambda n: "elsewhere:textures/entity/hrafnholt/%s.png" % n
TRIM = lambda mat, *extra: {"material": M(mat), "patterns": [E("hrafnholt"), M("rib"), M("ward")] + [M(x) for x in extra]}
FUR_DYE = 0x6B4A2B  # bark-brown fur
LEATHER_DYE = 0x4F6B3A  # moss green (archers)

# ------------------------------------------------------------------------------------------------ units (kingdom_unit)
def armor(h=None, c=None, l=None, f=None):
    return {k: M(v) for k, v in (("head", h), ("chest", c), ("legs", l), ("feet", f)) if v}


def combat(block, crit, cd, retreat_health, sprint=0.3, strafe=0.5, retreat_chance=0.0, **kw):
    d = {"block_chance": block, "crit_chance": crit, "crit_cooldown": cd, "retreat_health": retreat_health, "sprint_chance": sprint,
         "strafe": strafe, "retreat_chance": retreat_chance}
    d.update(kw)
    return d

# Hrafnholt never retreats in the field (WAR-AI retreat_threshold 0), so rank and file keep a low retreat chance.
UNITS = {
    "huskarl": {"role": "melee", "skin": SK("hird_a"), "armor": armor("iron_helmet", "chainmail_chestplate", "iron_leggings", "iron_boots"),
                "main_hand": M("iron_axe"), "off_hand": M("shield"), "stats": {"health": 24, "speed": 0.28, "knockback_resistance": 0.1},
                "combat": combat(0.5, 0.08, 180, 0.15, 0.3, 0.4, 0.1), "trim": TRIM("quartz")},
    "shield_thane": {"role": "melee", "skin": SK("hird_b"), "armor": armor("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"),
                     "main_hand": M("iron_axe"), "off_hand": M("shield"), "ability": "bash",
                     "stats": {"health": 30, "speed": 0.28, "knockback_resistance": 0.2},
                     "combat": combat(0.7, 0.1, 150, 0.1, 0.25, 0.4, 0.1), "trim": TRIM("quartz", "sentry")},
    "hunter": {"role": "archer", "skin": SK("hunter"), "armor": armor("chainmail_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
               "armor_dye": LEATHER_DYE, "main_hand": M("bow"), "stats": {"health": 18, "speed": 0.27},
               "combat": combat(0.0, 0.0, 200, 0.0), "trim": TRIM("iron")},
    "bolt_thrall": {"role": "crossbow", "skin": SK("bolt_thrall"), "armor": armor(None, "leather_chestplate", "leather_leggings", "leather_boots"),
                    "armor_dye": 0x4A3A28, "main_hand": M("crossbow"), "stats": {"health": 16, "speed": 0.26},
                    "combat": combat(0.0, 0.0, 200, 0.0)},
    "wolf_rider": {"role": "lancer", "skin": SK("wolf_rider"), "armor": armor("iron_helmet", "chainmail_chestplate", "chainmail_leggings", "chainmail_boots"),
                   "main_hand": M("iron_spear"), "mount": M("horse"), "mount_armor": M("leather_horse_armor"),
                   "stats": {"health": 22, "speed": 0.28}, "combat": combat(0.0, 0.0, 200, 0.0), "trim": TRIM("quartz")},
    "wolf_rider_axe": {"role": "cavalry", "skin": SK("wolf_rider_axe"), "armor": armor("chainmail_helmet", "iron_chestplate", "chainmail_leggings", "chainmail_boots"),
                       "main_hand": M("iron_axe"), "off_hand": M("shield"), "mount": M("horse"), "mount_armor": M("leather_horse_armor"),
                       "stats": {"health": 22, "speed": 0.28}, "combat": combat(0.4, 0.0, 200, 0.0), "trim": TRIM("iron")},
    "hird_sergeant": {"role": "melee", "officer": True, "skin": SK("sergeant"),
                      "armor": armor("iron_helmet", "iron_chestplate", "iron_leggings", "chainmail_boots"), "main_hand": M("iron_axe"),
                      "off_hand": M("shield"), "stats": {"health": 40, "speed": 0.28, "knockback_resistance": 0.25},
                      "combat": combat(0.7, 0.12, 150, 0.0, 0.3, 0.5, 0.0), "aggression": 2, "trim": TRIM("copper")},
    "hird_captain": {"role": "melee", "officer": True, "ability": "captain", "skin": SK("captain"),
                     "armor": armor("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots"), "main_hand": M("iron_axe"),
                     "off_hand": M("shield"), "stats": {"health": 54, "speed": 0.29, "knockback_resistance": 0.35},
                     "combat": combat(0.8, 0.15, 110, 0.0, 0.3, 0.6, 0.0), "aggression": 3, "trim": TRIM("copper", "host")},
    "raven_bearer": {"role": "melee", "ability": "standard", "skin": SK("raven_bearer"),
                     "armor": {"head": M("green_banner"), "chest": M("chainmail_chestplate"), "legs": M("chainmail_leggings"), "feet": M("chainmail_boots")},
                     "main_hand": M("iron_axe"), "stats": {"health": 36, "speed": 0.28}, "combat": combat(0.0, 0.05, 200, 0.0, 0.2, 0.4, 0.0),
                     "aggression": 2},
    "skald": {"role": "melee", "ability": "herald", "skin": SK("skald"), "armor": armor("chainmail_helmet", "leather_chestplate", "chainmail_leggings", "leather_boots"),
              "armor_dye": 0x2E5A2C, "main_hand": M("iron_axe"), "off_hand": M("goat_horn"), "stats": {"health": 28, "speed": 0.29},
              "combat": combat(0.0, 0.04, 200, 0.3, 0.2, 0.4, 0.4)},
    "leech": {"role": "melee", "ability": "surgeon", "skin": SK("leech"), "armor": armor("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
              "armor_dye": 0xD8D0B0, "main_hand": M("iron_axe"), "off_hand": M("splash_potion"), "stats": {"health": 26, "speed": 0.28},
              "combat": combat(0.0, 0.03, 200, 0.5, 0.2, 0.4, 0.8)},
    "shield_lad": {"role": "melee", "ability": "squire", "skin": SK("shield_lad"), "armor": armor("leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots"),
                   "armor_dye": FUR_DYE, "main_hand": M("iron_axe"), "off_hand": M("shield"), "stats": {"health": 16, "speed": 0.3},
                   "combat": combat(0.3, 0.04, 200, 0.3, 0.2, 0.4, 0.4)},
    "spear_thane": {"role": "melee", "skin": SK("spear_thane"), "armor": armor("iron_helmet", "chainmail_chestplate", "leather_leggings", "iron_boots"),
                    "armor_dye": FUR_DYE, "main_hand": M("iron_spear"), "stats": {"health": 20, "speed": 0.28},
                    "combat": combat(0.0, 0.05, 200, 0.2, 0.2, 0.4, 0.2, mounted_bonus=2.0), "trim": TRIM("iron")},
    # unique soldiers (RIVALS.md 1E.1)
    "berserker": {"role": "melee", "skin": SK("berserker"), "armor": {}, "main_hand": M("iron_axe"), "off_hand": M("iron_axe"),
                  "stats": {"health": 22, "speed": 0.32}, "combat": combat(0.0, 0.2, 100, 0.0, 0.6, 0.7, 0.0), "aggression": 1},
    "wolf_handler": {"role": "melee", "skin": SK("wolf_handler"), "armor": armor("chainmail_helmet", "leather_chestplate", "chainmail_leggings", "leather_boots"),
                     "armor_dye": FUR_DYE, "main_hand": M("iron_axe"), "stats": {"health": 20, "speed": 0.29},
                     "combat": combat(0.0, 0.08, 160, 0.2, 0.3, 0.5, 0.2), "trim": TRIM("iron")},
}
for n, u in UNITS.items():
    u.setdefault("stats", {}); u.setdefault("armor", {})
    w("data/elsewhere/kingdom_unit/hrafnholt_%s.json" % n, u)

# ------------------------------------------------------------------------------------------------ bosses and drops
SLOTS = ["jarl", "hersir", "hallwarden", "skald", "chief", "huntmaster"]
# court slot keys (kingdom_court Ruler), in rank order; entity and item ids keep the role names above
CS = {"jarl": "king", "hersir": "marshal", "hallwarden": "castellan", "skald": "master_at_arms", "chief": "huntsman", "huntmaster": "chancellor"}
BOSS = {
    "jarl": ("High Jarl Halvard Ironbrow", "the Ninth Pine", 300, "ruler", ["iron_crown", ("raven_banner", 0.5)]),
    "hersir": ("Hersir Sigrun Askeland", "war-leader", 236, "champion", ["wolfskin_boots"]),
    "hallwarden": ("Hallwarden Torvald Eikrem", "Keeper of the Hearth-Door", 257, "champion", ["hearth_ward_mail"]),
    "skald": ("Weapon-Skald Ulfar Brandsson", "the Axe-Singer", 236, "champion", ["skalds_rune_axe"]),
    "chief": ("Gudrun Ulfhednar", "Wolf-Coat, Chief of the Berserkers", 246, "champion", ["ulfhednar_pelt"]),
    "huntmaster": ("Huntmaster Eydis Nordvik", "of the Wolf-Runs", 214, "champion", ["hornbow"]),
}
rulers = {}
for s in SLOTS:
    name, title, hp, kind, uniq = BOSS[s]
    drops = []
    for u in uniq:
        if isinstance(u, tuple): drops.append({"item": E("hrafnholt_" + u[0]), "chance": u[1]})
        else: drops.append({"item": E("hrafnholt_" + u)})
    drops.append({"item": E("hrafnholt_seal_" + s)})
    drops.append({"item": E("oathkeepers_seal")})
    if kind == "ruler":
        drops.append({"item": E("hacksilver"), "min": 80, "max": 96})  # 64 treasury + 16-32
        drops.append({"item": M("emerald"), "min": 6, "max": 12})
    else:
        drops.append({"item": E("hacksilver"), "min": 16, "max": 32})
    rulers[CS[s]] = {"name": name, "name_key": "entity.elsewhere.hrafnholt_" + s, "title": title, "health": hp, "drops": drops}
rulers["king"]["health_cap"] = 300  # Hird-oath is a damage bonus, not HP

ROSTER = {
    "man_at_arms": "huskarl", "knight": "shield_thane", "archer": "hunter", "crossbowman": "bolt_thrall", "lancer": "wolf_rider",
    "mounted_swordsman": "wolf_rider_axe", "sergeant": "hird_sergeant", "captain": "hird_captain", "standard_bearer": "raven_bearer",
    "herald": "skald", "surgeon": "leech", "squire": "shield_lad", "pikeman": "spear_thane",
}
SHOP = [
    {"item": E("hrafnholt_raven_shield"), "price": 30},
    {"item": E("hrafnholt_wolfpelt_blanket"), "price": 36},
    {"item": E("hrafnholt_fur_collar_dye"), "price": 20},
    {"item": E("hrafnholt_banner_pattern"), "price": 40},
    {"item": E("hrafnholt_armor_trim_smithing_template"), "price": 40},
    {"item": E("bodkin_bolt"), "count": 8, "price": 27},
    {"item": E("oathkeepers_seal"), "price": 220, "weekly_limit": 2},
    {"item": M("netherite_upgrade_smithing_template"), "price": 275, "extra": [{"item": M("diamond"), "min": 20}], "weekly_limit": 2},
]
faction = {
    "name": "Jarldom of Hrafnholt", "name_key": "faction.elsewhere.hrafnholt",
    "banner": {"primary": "#2E5A2C", "secondary": "#6B4A2B"},
    "tabard": {"color": "#2E5A2C", "trim": "#6B4A2B"},
    "trim": TRIM("iron"),
    "hostile_to_players": True, "allies": [], "peace": [],
    "roster": {E(b): {"unit": E("hrafnholt_" + u)} for b, u in ROSTER.items()},
    "rulers": rulers,
    "biomes": ["minecraft:taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:windswept_forest",
               "minecraft:river", "minecraft:frozen_river", "minecraft:cold_ocean", "minecraft:ocean", "minecraft:deep_cold_ocean"],
    "biomes_fallback": ["minecraft:snowy_taiga", "minecraft:grove"],
    "site": {"terrain": "rise", "near_water": True, "waterfront": True},
    "biome_notes": "Pine and spruce taiga and windswept forest first; snowy taiga and grove only as fallback. Build on a rise near water; a taiga village on the water.",
    "music": "elsewhere:hrafnholt_music", "realm": True,
    "society": {
        "government": "monarchy",
        "ruler_bonus": {"type": "damage_per_champion", "amount": 10},
        "leaders": [CS[x] for x in SLOTS],
        "standing": {"start": -30, "cap_until_ruler_killed": False, "loss_multiplier": 1.5, "gain_multiplier": 0.75, "wary_realm_only": True},
        "serfs": {"type": E("thrall"), "variant": "thrall", "name_key": "entity.elsewhere.hrafnholt_thrall", "skin": SK("thrall"),
                  "count_multiplier": 2.0, "health": 12, "rebuild_blocks_per_20_ticks": 6,
                  "levy": {"fraction": 0.33, "weapon": M("stone_axe")}},
        "chair_days": 7, "seat_refill_days": 7,
        "diplomacy_npc": {"id": "lawspeaker_gunnar", "name": "Lawspeaker Gunnar Hallstein", "duel": "holmgang"},
        "shop_keeper": {"name": "Silver-Weigher", "job_site": "silver_scales", "master_needs_bosses": 2, "master_pool": [CS[x] for x in ["hersir", "hallwarden", "skald", "chief", "huntmaster"]]},
    },
    "economy": {"currency": E("hacksilver"), "shop": SHOP},
    "army": {
        "units": [E("hrafnholt_" + u) for u in ROSTER.values()],
        "unique_units": [E("hrafnholt_berserker"), E("hrafnholt_wolf_handler")],
        "makeup": {E("hrafnholt_huskarl"): 0.28, E("hrafnholt_shield_thane"): 0.17, E("hrafnholt_spear_thane"): 0.15, E("hrafnholt_hunter"): 0.15,
                   E("hrafnholt_bolt_thrall"): 0.05, E("hrafnholt_wolf_rider"): 0.05, E("hrafnholt_berserker"): 0.05,
                   E("hrafnholt_wolf_handler"): 0.05, E("hrafnholt_shield_lad"): 0.05},
        "health_multiplier": 1.15, "damage_multiplier": 1.10, "speed_multiplier": 0.90, "size_multiplier": 1.20,
        "patrol_day": 7, "patrol_night": 8,
        "brain": {
            "war_frequency": 1.5, "min_attack_odds": 1.0, "retreat_instead_of_die": False, "veterancy_cap": 0.10,
            "intelligence_noise": 0.45, "supply_line_only": False, "decision_delay_days": 0.0, "hire_mercenaries": False,
            "mercenary_daily_cost": 0,
            "brain_roles": [{"role": "king", "degrades": ["war_freq"]}, {"role": "marshal", "degrades": ["pursuit", "retreat_threshold"]}],
            # WAR-AI.md section 3 field names, Hrafnholt column (the war lane reads these)
            "war_freq": 1.5, "attack_rate": 0.75, "free_siege_rate": 0.32, "commit": 0.50, "min_odds": 1.0, "retreat_threshold": 0.0,
            "def_retreat": 0.0, "intel_noise": 0.45, "scout_targets": 1, "skirmish_min_odds": 0.0, "vet_gain": 0.02, "vet_cap": 0.10,
            "vet_decay_per_day": 0.010, "pursuit": 0.12, "supply_rule": False, "fort_rate_per_day": 0.0, "fort_cap": 0.10,
            "fort_border_only": False, "integration_days": 12, "conquest_unrest": 0.80, "opening_days": 0,
            "hp_mult": 1.15, "dmg_mult": 1.10, "size_mult": 1.2, "income_base": 8, "income_per_village": 1.5, "income_noise": 0.30,
            "head_cost": 7.5, "timing_rule": False, "broker": False,
            "boss_degrade": {
                "king": {"seat_empty_days": 7, "degraded_days": 30, "fields": {"war_freq": 1.0}},
                "marshal": {"seat_empty_days": 7, "degraded_days": 30, "fields": {"pursuit": 0.0, "retreat_threshold": 0.55}},
            },
        },
    },
    # Standing and field behaviour (RIVALS-DECISIONS 2.1): Wary inside the realm and near the castle only.
    "standing_rules": {"start": -30, "loss_multiplier": 1.5, "gain_multiplier": 0.75, "wary_only_in_realm": True,
                       "realm_radius_blocks": 1500},
    "succession": {  # RIVALS-DECISIONS 10: no generic seat refill; the court lane builds the mechanism
        "jarl_dies": "strongest_living_champion_becomes_jarl", "jarl_successor_bonus": {"health_pct": 5, "damage_pct": 5},
        "champion_dies": {"after_days": 10, "raise": "senior_living_hird_captain", "max_per_seat": 1, "requires_living_captain": True},
        "risen": {"base_unit": E("hrafnholt_hird_captain"), "health_pct": 15, "damage_pct": 15, "takes_seat_title": True,
                  "drop_rule": "repeat_kill_odds", "name_key_prefix": "entity.elsewhere.hrafnholt_risen_",
                  "skin_template": "elsewhere:textures/entity/hrafnholt/boss/risen_%s.png",
                  "moves": {"marshal": ["raid_charge", "horn_of_three_calls"], "castellan": ["hearth_ward", "door_breaker"],
                            "master_at_arms": ["rune_throw", "three_beat_verse"], "huntsman": ["furrow_run", "pelt_leap"], "chancellor": ["wolf_pack", "pinning_arrow"]}},
        "falls_when": "jarl_line_and_all_champion_seats_dead"},
    "castle": {  # court lane: castle palette and extra pieces (RIVALS.md 1A: spruce longhalls, palisades, iron-banded stone, ravens)
        "palette": {"wall": M("mossy_stone_bricks"), "wall_accent": M("stone_bricks"), "base": M("mossy_cobblestone"), "frame": M("dark_oak_log"),
                    "plank": M("spruce_planks"), "roof_stairs": M("spruce_stairs"), "roof_slab": M("spruce_slab"), "floor": M("dark_oak_planks"),
                    "banding": M("iron_bars"), "window": M("iron_bars"), "light": M("lantern"), "door": M("spruce_door"),
                    "fence": M("spruce_fence"), "gate": M("spruce_fence_gate"), "accent_wood": M("dark_oak_log"), "ember": M("campfire"),
                    "banner": M("green_banner"), "moss": M("moss_block")},
        "extra_pieces": ["longhall_roofline", "palisade_wall", "raven_perch_tower", "hearth_pit", "hall_door_steps", "wolf_kennel", "weapon_racks"],
        "keep_style": "longhall_keep", "boss_arena": "longhall_floor",
    },
    "rebuild_style": {  # villages lane: rebuild with longhall and palisade; never flood or grief
        "style": "longhall_palisade", "house_roof": M("spruce_stairs"), "house_wall": M("spruce_planks"), "house_frame": M("stripped_spruce_log"),
        "wall": M("spruce_fence"), "palisade": [M("spruce_log"), M("spruce_fence")], "banner": M("green_banner"), "light": M("lantern"),
        "extras": ["raven_post", "longhall_in_town_centre"], "rebuild_speed_multiplier": 1.5,
        "siege_unrest": 0.80, "flip_back_chance_per_day": 0.12,
    },
    "ravens": {"border_hint": "raven_post", "tower_flag": "green_banner"},
}
w("data/elsewhere/kingdom_faction/hrafnholt.json", faction)

# ------------------------------------------------------------------------------------------------ items
# (id, kind, model parent, texture name). kind: armor | axe | bow | plain
ITEMS = {
    "hacksilver": "Hacksilver",
    "hrafnholt_iron_crown": "Iron Crown of Hrafnholt",
    "hrafnholt_raven_banner": "Raven Banner",
    "hrafnholt_wolfskin_boots": "Wolfskin Boots",
    "hrafnholt_hearth_ward_mail": "Hearth-Ward Mail",
    "hrafnholt_skalds_rune_axe": "Skald's Rune-Axe",
    "hrafnholt_ulfhednar_pelt": "Ulfhednar Pelt",
    "hrafnholt_hornbow": "Hornbow of the Hunt",
    "hrafnholt_armor_trim_smithing_template": "Hrafnholt Armor Trim",
    "hrafnholt_banner_pattern": "Hrafnholt Banner Pattern",
    "hrafnholt_raven_shield": "Raven Shield",
    "hrafnholt_wolfpelt_blanket": "Wolf-Pelt Horse Blanket",
    "hrafnholt_fur_collar_dye": "Fur Collar Dye",
}
SEAL_NAMES = {"jarl": "High Jarl Halvard Ironbrow", "hersir": "Hersir Sigrun Askeland", "hallwarden": "Hallwarden Torvald Eikrem",
              "skald": "Weapon-Skald Ulfar Brandsson", "chief": "Gudrun Ulfhednar", "huntmaster": "Huntmaster Eydis Nordvik"}
for s in SLOTS: ITEMS["hrafnholt_seal_" + s] = "Seal of " + SEAL_NAMES[s]
for n in ITEMS:
    parent = "minecraft:item/handheld" if n == "hrafnholt_skalds_rune_axe" else "minecraft:item/generated"
    if n == "hrafnholt_hornbow":
        for suf in ("", "_pulling_0", "_pulling_1", "_pulling_2"):
            w("assets/elsewhere/models/item/hrafnholt_hornbow%s.json" % suf, {"parent": "minecraft:item/bow", "textures": {"layer0": "elsewhere:item/hrafnholt_hornbow"}})
        mk = lambda s: {"type": "minecraft:model", "model": "elsewhere:item/hrafnholt_hornbow" + s}
        w("assets/elsewhere/items/hrafnholt_hornbow.json", {"model": {
            "type": "minecraft:condition", "property": "minecraft:using_item", "on_false": mk(""),
            "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                        "entries": [{"model": mk("_pulling_1"), "threshold": 0.65}, {"model": mk("_pulling_2"), "threshold": 0.9}], "fallback": mk("_pulling_0")}}})
        continue
    w("assets/elsewhere/models/item/%s.json" % n, {"parent": parent, "textures": {"layer0": "elsewhere:item/" + n}})
    w("assets/elsewhere/items/%s.json" % n, {"model": {"type": "minecraft:model", "model": "elsewhere:item/" + n}})

# banner pattern item uses the shared banner-pattern tag, like Aldermark's
w("data/elsewhere/tags/banner_pattern/pattern_item/hrafnholt.json", {"values": [E("hrafnholt")]})
w("data/elsewhere/banner_pattern/hrafnholt.json", {"asset_id": E("hrafnholt"), "translation_key": "block.elsewhere.banner.hrafnholt"})
w("data/elsewhere/trim_pattern/hrafnholt.json", {"asset_id": E("hrafnholt"), "decal": False, "description": {"translate": "trim_pattern.elsewhere.hrafnholt"}})
w("data/elsewhere/recipe/hrafnholt_armor_trim_smithing_template.json", {
    "type": "minecraft:crafting_shaped", "key": {"#": M("diamond"), "C": M("iron_block"), "S": E("hrafnholt_armor_trim_smithing_template")},
    "pattern": ["#S#", "#C#", "###"], "result": {"count": 2, "id": E("hrafnholt_armor_trim_smithing_template")}})
w("data/elsewhere/recipe/hrafnholt_armor_trim_smithing_template_smithing_trim.json", {
    "type": "minecraft:smithing_trim", "addition": "#minecraft:trim_materials", "base": "#minecraft:trimmable_armor",
    "pattern": E("hrafnholt"), "template": E("hrafnholt_armor_trim_smithing_template")})

# equipment layers (vanilla textures: iron, chainmail, leather dyed)
leather = {"layers": {"humanoid": [{"texture": M("leather"), "dyeable": {"color_when_undyed": FUR_DYE}}, {"texture": M("leather_overlay")}],
                      "humanoid_leggings": [{"texture": M("leather"), "dyeable": {"color_when_undyed": FUR_DYE}}, {"texture": M("leather_overlay")}]}}
def vanilla(name): return {"layers": {"humanoid": [{"texture": M(name)}], "humanoid_leggings": [{"texture": M(name)}]}}
w("assets/elsewhere/equipment/hrafnholt_crown.json", vanilla("iron"))
w("assets/elsewhere/equipment/hrafnholt_hearth_ward.json", vanilla("chainmail"))
w("assets/elsewhere/equipment/hrafnholt_wolfskin.json", leather)
w("assets/elsewhere/equipment/hrafnholt_ulfhednar.json", leather)

# item tags
w("data/elsewhere/tags/item/hrafnholt_iron_repair.json", {"values": [M("iron_ingot")]})
w("data/elsewhere/tags/item/hrafnholt_fur_repair.json", {"values": [M("leather"), M("rabbit_hide")]})
w("data/elsewhere/tags/item/lore_seals.json", {"replace": False, "values": [E("hrafnholt_seal_" + s) for s in SLOTS]})
w("data/minecraft/tags/item/head_armor.json", {"values": [E("hrafnholt_iron_crown")]})
w("data/minecraft/tags/item/foot_armor.json", {"values": [E("hrafnholt_wolfskin_boots")]})
w("data/minecraft/tags/item/chest_armor.json", {"values": [E("hrafnholt_hearth_ward_mail"), E("hrafnholt_ulfhednar_pelt")]})
w("data/minecraft/tags/item/freeze_immune_wearables.json", {"values": [E("hrafnholt_wolfskin_boots"), E("hrafnholt_hearth_ward_mail")]})
w("data/minecraft/tags/item/axes.json", {"values": [E("hrafnholt_skalds_rune_axe")]})
w("data/minecraft/tags/item/enchantable/bow.json", {"values": [E("hrafnholt_hornbow")]})
# Test Meter exclusion (all new ids: currency, seals, uniques, cosmetics, template)
w("data/testmeter/tags/item/transmutation_excluded.json", {"replace": False, "values": [{"id": E(i), "required": False} for i in ITEMS]})

# ------------------------------------------------------------------------------------------------ boss music slots (tracks come later)
# the music lane maps the seat bosses to real tracks (boss_music.json); the Risen champions share their seat's theme
THEMES = {"jarl": "tyrant", "hersir": "clash_defiant", "hallwarden": "burnt_spirit", "skald": "firesong", "chief": "trouble_with_tribals", "huntmaster": "death_and_axes"}
music = {"elsewhere:hrafnholt_" + s: "elsewhere:boss_music.theme." + THEMES[s] for s in SLOTS}
music.update({"elsewhere:hrafnholt_risen_" + s: "elsewhere:boss_music.theme." + THEMES[s] for s in SLOTS if s != "jarl"})
music["realm"] = "elsewhere:hrafnholt_music.realm"
w("boss_music_hrafnholt.json", {"_comment": "Merge into boss_music.json 'bosses'. Seat ids match the music lane; the Risen ids (hrafnholt_risen_<slot>) are new and share the seat theme.", "bosses": {k: v for k, v in music.items() if k != "realm"}, "realm": music["realm"]}, base=INTEG)
SLOT_HINT = {"jarl": "low drone, bowed lyre, slow frame drum", "hersir": "frame drum march, low horns, fiddle at 50 percent", "hallwarden": "frame drum, anvil on bashes, low horn",
             "skald": "bowed lyre, throat drone, clapped rhythm, bell", "chief": "drum and throat-singing, howl sample", "huntmaster": "frame drum, hunting horn, howls"}
w("boss_music_hrafnholt_notes.json", SLOT_HINT, base=INTEG)

# ------------------------------------------------------------------------------------------------ lang and JEI
lang = {"faction.elsewhere.hrafnholt": "Jarldom of Hrafnholt", "trim_pattern.elsewhere.hrafnholt": "Hrafnholt Armor Trim",
        "block.elsewhere.banner.hrafnholt": "Raven of Hrafnholt", "item.elsewhere.hrafnholt_banner_pattern.desc": "Raven of Hrafnholt",
        "item.elsewhere.hrafnholt_banner_pattern.new": "Hrafnholt Banner Pattern",
        "item.elsewhere.hrafnholt_armor_trim_smithing_template.new": "Hrafnholt Armor Trim",
        "entity.elsewhere.hrafnholt_thrall": "Thrall", "item.elsewhere.hacksilver.tooltip": "Currency of the Jarldom of Hrafnholt",
        "itemGroup.elsewhere.rival_hrafnholt": "Hrafnholt", "message.elsewhere.hrafnholt_banner_planted": "The raven banner is planted.",
        "message.elsewhere.hrafnholt_banner_cooldown": "The raven banner is not ready yet."}
for k, v in ITEMS.items(): lang["item.elsewhere." + k] = v
for k in ("hersir", "hallwarden", "skald", "chief", "huntmaster"): lang["entity.elsewhere.hrafnholt_risen_" + k] = "Risen " + BOSS[k][1].replace("the ", "The ", 1) if False else "Risen " + BOSS[k][0].split(" ")[0]
lang["entity.elsewhere.hrafnholt_hound"] = "Hrafnholt Hound"
lang["entity.elsewhere.hrafnholt_raven_standard"] = "Raven Standard"
lang["boss.elsewhere.hearth_ember"] = "Hearth Ember"
lang["tactics.hrafnholt.shout.0"] = "To the hall or to the grave!"
lang["tactics.hrafnholt.shout.1"] = "Hrafnholt! Break them!"
lang["tactics.hrafnholt.horn_long.0"] = "Hrafnholt! Charge!"
for u in ("berserker", "wolf_handler"):
    lang["item.elsewhere.hrafnholt_%s_spawn_egg" % u] = "Hrafnholt %s Spawn Egg" % ("Berserker" if u == "berserker" else "Wolf-Handler")
    w("assets/elsewhere/models/item/hrafnholt_%s_spawn_egg.json" % u, {"parent": "minecraft:item/generated", "textures": {"layer0": "elsewhere:item/hrafnholt_%s_spawn_egg" % u}})
    w("assets/elsewhere/items/hrafnholt_%s_spawn_egg.json" % u, {"model": {"type": "minecraft:model", "model": "elsewhere:item/hrafnholt_%s_spawn_egg" % u}})
for s in SLOTS: lang["entity.elsewhere.hrafnholt_" + s] = BOSS[s][0]; lang["entity.elsewhere.hrafnholt_" + s + ".title"] = BOSS[s][1]
UNAMES = {"huskarl": "Huskarl", "shield_thane": "Shield-Thane", "hunter": "Hunter", "bolt_thrall": "Bolt-Thrall", "wolf_rider": "Wolf-Rider",
          "wolf_rider_axe": "Wolf-Rider", "hird_sergeant": "Hird-Sergeant", "hird_captain": "Hird-Captain", "raven_bearer": "Raven-Bearer",
          "skald": "Skald", "leech": "Leech", "shield_lad": "Shield-Lad", "spear_thane": "Spear-Thane", "berserker": "Berserker",
          "wolf_handler": "Wolf-Handler"}
for u, n in UNAMES.items(): lang["entity.elsewhere.hrafnholt_" + u] = "Hrafnholt " + n
LORE = {
    "hacksilver": ["Cut silver ring pieces, the plunder of far landings, weighed rather than counted.", "Spend them with the Silver-Weigher of a liberated Hrafnholt town."],
    "hrafnholt_iron_crown": ["Wolf-Rally: 8 percent less damage taken while a tamed wolf is within 16 blocks.", "Plus 1 percent per Lord Seal you carry, up to 12 percent.", "As good as netherite on armor and knockback, one toughness less."],
    "hrafnholt_raven_banner": ["Plant it: Speed I for 30 seconds to you and your allies within 16 blocks.", "One banner per player; it burns down after 30 seconds."],
    "hrafnholt_wolfskin_boots": ["Sure-footed on ice and mountain scree, 10 percent faster on snow and powder snow, no powder-snow freeze.", "Less toughness than netherite boots, and no knockback resistance."],
    "hrafnholt_hearth_ward_mail": ["Immune to freezing, 10 percent less fire damage, 5 percent slower.", "One armor point below netherite."],
    "hrafnholt_skalds_rune_axe": ["The third hit on one target within 5 seconds deals 3 extra damage and more knockback.", "One damage and 331 durability short of netherite."],
    "hrafnholt_ulfhednar_pelt": ["Pelt-Rage: below 30 percent health, Strength I for 6 seconds, once per 2 minutes.", "Two armor and two toughness under netherite."],
    "hrafnholt_hornbow": ["Full draw in 0.85 seconds. Arrows slow what they hit for 2 seconds.", "Shorter-lived than netherite gear, and no Infinity."],
    "hrafnholt_raven_shield": ["A cosmetic shield-plaque painted with the raven.", "Sold by the Silver-Weigher."],
    "hrafnholt_wolfpelt_blanket": ["A wolf-pelt blanket for a Wolf-Rider's horse, carried up from the longship landing.", "Sold by the Silver-Weigher."],
    "hrafnholt_fur_collar_dye": ["Dyes a collar the brown of a forest wolf.", "Sold by the Silver-Weigher."],
}
for k, lines in LORE.items():
    for i, t in enumerate(lines, 1): lang["item.elsewhere.%s.desc%d" % (k, i)] = t
for s in SLOTS: lang["item.elsewhere.hrafnholt_seal_" + s + ".desc1"] = "Wax and pine-resin, pressed by %s." % BOSS[s][0]
w("assets/elsewhere/lang/en_us.json", lang)

jei = {}
hows = {
    "hacksilver": "How to get: Hrafnholt soldiers drop 1 to 3, officers 4 to 8, Lords 16 to 32, the High Jarl 80 to 96 on his first defeat. Thralls drop none.",
    "hrafnholt_iron_crown": "How to get: the High Jarl Halvard Ironbrow drops it (DropRule: 100 percent first kill, 67 percent repeat, plus pity).",
    "hrafnholt_raven_banner": "How to get: the High Jarl drops it as his second item.",
    "hrafnholt_wolfskin_boots": "How to get: Hersir Sigrun Askeland.", "hrafnholt_hearth_ward_mail": "How to get: Hallwarden Torvald Eikrem.",
    "hrafnholt_skalds_rune_axe": "How to get: Weapon-Skald Ulfar Brandsson.", "hrafnholt_ulfhednar_pelt": "How to get: Gudrun Ulfhednar.",
    "hrafnholt_hornbow": "How to get: Huntmaster Eydis Nordvik.",
    "hrafnholt_armor_trim_smithing_template": "How to get: Hird-Captains drop it 8 percent of the time; the Silver-Weigher sells it for 40 Hacksilver.",
    "hrafnholt_banner_pattern": "How to get: Raven-Bearers drop it; the Silver-Weigher sells a copy for 40 Hacksilver.",
}
for k in ITEMS:
    if k.startswith("hrafnholt_seal_"):
        jei[E(k)] = ["A Lord Seal of the Jarldom of Hrafnholt: " + lang["item.elsewhere." + k] + ".", "How to get: dropped by that boss on every kill. Each Lord Seal you carry adds 1 percent to the Iron Crown's Wolf-Rally."]
    else:
        jei[E(k)] = [" ".join(LORE.get(k, [lang["item.elsewhere." + k]])), hows.get(k, "Part of the Jarldom of Hrafnholt.")]
w("jei_info_hrafnholt.json", jei, base=INTEG)
print("data done:", len(UNITS), "units,", len(ITEMS), "items")
