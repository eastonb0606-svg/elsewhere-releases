"""Mirewick data generator: faction, units, trim and banner patterns, recipes, tags, castle palette and village rebuild data,
lang, JEI info and the boss_music slot ids. Run after gen_assets.py: python3 tools/gen_data.py (from rival_mirewick/)."""
import os, json

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
KING = os.path.join(HERE, "..", "..", "kingdom", "src", "main", "resources", "data", "elsewhere", "kingdom_unit")
D = os.path.join(RES, "data", "elsewhere")
A = os.path.join(RES, "assets", "elsewhere")


def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)


def skin(n): return f"elsewhere:textures/entity/kingdom/mirewick/{n}.png"
def tr(mat, pats=("elsewhere:mirewick", "minecraft:tide", "minecraft:wild")):
    mat = "amethyst" if mat in ("emerald", "iron") else mat   # royal purple trims (RIVALS-DECISIONS 16); gold stays for officers
    return {"material": f"minecraft:{mat}", "patterns": list(pats)}


# ---- units: Aldermark's role stats, Mirewick's look (mixed leather, chainmail, iron on pikes; oilcloth, waders, wide hats) ----
def unit(new, base, skin_name, armor=None, dye=None, main=None, off="keep", trim=None, patch=None):
    u = json.load(open(os.path.join(KING, base + ".json")))
    u["skin"] = skin(skin_name)
    if armor: u["armor"] = armor
    if dye is not None: u["armor_dye"] = dye
    elif "armor_dye" in u and armor: u.pop("armor_dye")
    if main: u["main_hand"] = main
    if off != "keep":
        if off is None: u.pop("off_hand", None)
        else: u["off_hand"] = off
    u["trim"] = trim
    if trim is None: u.pop("trim")
    if patch: patch(u)
    w(os.path.join(D, "kingdom_unit", new + ".json"), u)


def A4(h, c, l, f): return {"head": h, "chest": c, "legs": l, "feet": f}
M = "minecraft:"
GREEN, BROWN, OLIVE = 0x5B2A86, 0x9E1B32, 0x7A3A9E   # names kept: royal purple, crimson, violet
unit("mirewick_dikeguard", "man_at_arms", "dikeguard", A4(M + "leather_helmet", M + "chainmail_chestplate", M + "leather_leggings", M + "leather_boots"), GREEN, M + "iron_sword", trim=tr("emerald"))
unit("mirewick_reed_knight", "knight", "reed_knight", A4(M + "iron_helmet", M + "chainmail_chestplate", M + "chainmail_leggings", M + "iron_boots"), None, M + "iron_axe", trim=tr("iron"))
unit("mirewick_fowler", "archer", "fowler", A4(M + "leather_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), BROWN, trim=tr("emerald"))
unit("mirewick_punt_crossbow", "crossbowman", "punt_crossbow", A4(M + "leather_helmet", M + "chainmail_chestplate", M + "leather_leggings", M + "leather_boots"), OLIVE, trim=tr("emerald"))
unit("mirewick_marsh_rider", "lancer", "marsh_rider", A4(M + "chainmail_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), GREEN, trim=tr("iron"))
unit("mirewick_pikeman", "pikeman", "pikeman", A4(M + "iron_helmet", M + "chainmail_chestplate", M + "leather_leggings", M + "iron_boots"), None, M + "iron_spear", trim=tr("iron"))
unit("mirewick_reeve", "sergeant", "reeve", A4(M + "chainmail_helmet", M + "leather_chestplate", M + "chainmail_leggings", M + "leather_boots"), GREEN, trim=tr("gold"))
unit("mirewick_wardman", "captain", "wardman", A4(M + "iron_helmet", M + "chainmail_chestplate", M + "iron_leggings", M + "leather_boots"), None, trim=tr("gold", ("elsewhere:mirewick", "minecraft:tide", "minecraft:wild")))
unit("mirewick_lantern_bearer", "standard_bearer", "lantern_bearer", A4(M + "leather_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), OLIVE, trim=tr("gold"))
unit("mirewick_bellman", "herald", "bellman", A4(M + "leather_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), BROWN, trim=tr("gold"))
unit("mirewick_almoners_hand", "surgeon", "almoners_hand", A4(M + "leather_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), 0xE0D4EC, trim=tr("gold"))
unit("mirewick_linkboy", "squire", "linkboy", A4(M + "leather_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), BROWN, trim=tr("emerald"))
# the two unique soldiers (behaviour in code: BogWarden, MireTrapper)
unit("mirewick_bog_warden", "knight", "bog_warden", A4(M + "iron_helmet", M + "chainmail_chestplate", M + "chainmail_leggings", M + "leather_boots"), None, M + "iron_axe", M + "shield", tr("iron"),
     lambda u: (u["stats"].update({"health": 30, "speed": 0.22, "knockback_resistance": 0.4}), u["combat"].update({"block_chance": 0.95, "crit_chance": 0.02, "strafe": 0.1, "sprint_chance": 0.0, "retreat_health": 0.0})))
unit("mirewick_mire_trapper", "crossbowman", "mire_trapper", A4(M + "leather_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), 0x7A2A55, trim=tr("emerald"),
     patch=lambda u: u["stats"].update({"health": 16}))
# Peat-Cutters: the Serf tier (indentured diggers, 16 HP, neutral, fight back like a wolf)
unit("mirewick_peat_cutter", "squire", "peat_cutter", A4(None, None, None, None), None, M + "iron_shovel", None, None,
     lambda u: (u["stats"].update({"health": 16, "speed": 0.26}), u.update({"armor": {}}), u.pop("ability", None), u["combat"].update({"block_chance": 0.0, "crit_chance": 0.0})))
# Free Company sellswords: not Mirewick soldiers; mixed vanilla gear, no kingdom trim, their own grey banner
unit("mirewick_fc_sellsword", "man_at_arms", "fc_sellsword", A4(M + "iron_helmet", M + "leather_chestplate", M + "chainmail_leggings", M + "iron_boots"), 0x808084, M + "iron_sword", M + "shield", None)
unit("mirewick_fc_crossbow", "crossbowman", "fc_crossbow", A4(M + "chainmail_helmet", M + "chainmail_chestplate", M + "leather_leggings", M + "leather_boots"), 0x5A5A66, None, None, None)
unit("mirewick_fc_brute", "knight", "fc_brute", A4(None, M + "iron_chestplate", M + "leather_leggings", M + "iron_boots"), 0x3A3A40, M + "iron_axe", M + "shield", None)
unit("mirewick_fc_captain", "captain", "fc_captain", A4(M + "iron_helmet", M + "iron_chestplate", M + "chainmail_leggings", M + "iron_boots"), None, M + "iron_sword", M + "shield", None)
unit("mirewick_fc_banner", "standard_bearer", "fc_sellsword", A4(M + "iron_helmet", M + "leather_chestplate", M + "leather_leggings", M + "leather_boots"), 0x808084, M + "gray_banner", None, None)

# ---- faction ------------------------------------------------------------------------------------------------------------
BOSSES = [  # court slot, key, name, title, hp, unique, leaning
    ("master_at_arms", "marrow", "Ottoline Marrow", "Magnate of the Counting-House", 460, "mirewick_debt_collectors_leggings", "miser"),
    ("marshal", "thwaite", "Fen-Marshal Garrick Thwaite", "Marshal of the Dikes", 460, "mirewick_fen_marshals_waders", "hawk"),
    ("castellan", "sorrel", "Dikemaster Hilde Sorrel", "Warden of the Sluices", 480, "mirewick_dikewardens_brigandine", "dove"),
    ("huntsman", "tolliver", "Wildfowler Wren Tolliver", "of the Reed Hides", 440, "mirewick_fowlers_marshbow", "hawk"),
    ("chancellor", "ames", "Lord Almoner Cuthbert Ames", "Keeper of the Lantern-Oil", 380, "mirewick_almoners_cowl", "miser"),
]
HEIRS = [("master_at_arms", "Marrow", ["Perpetua", "Hester"]), ("marshal", "Thwaite", ["Osbert", "Maud"]), ("castellan", "Sorrel", ["Ivo", "Agnes"]),
         ("huntsman", "Tolliver", ["Piers", "Linnet"]), ("chancellor", "Ames", ["Jory", "Matilda"])]
rulers = {}
for slot, key, name, title, card_hp, uniq, lean in BOSSES:
    # RIVALS-DECISIONS 13: human bosses never exceed 300 max health. HP = round(card HP x 300/560); the Chair's bonus room
    # (+48) is capped at 300 too. Matches the shipped mirewick.json (246/246/257/236/204, caps 294/294/300/284/252).
    hp = round(card_hp * 300 / 560)
    rulers[slot] = {"name": name, "name_key": f"entity.elsewhere.mirewick_{key}", "title": title, "health": hp, "health_cap": min(hp + 48, 300),
                    "drops": [{"item": f"elsewhere:{uniq}"}, {"item": f"elsewhere:mirewick_seal_{key}"}, {"item": "elsewhere:oathkeepers_seal"},
                              {"item": "elsewhere:mirewick_scrip", "min": 16, "max": 32}]}
roster = {f"elsewhere:{b}": {"unit": f"elsewhere:mirewick_{m}"} for b, m in [
    ("man_at_arms", "dikeguard"), ("knight", "reed_knight"), ("archer", "fowler"), ("crossbowman", "punt_crossbow"), ("lancer", "marsh_rider"),
    ("mounted_swordsman", "marsh_rider"), ("sergeant", "reeve"), ("captain", "wardman"), ("standard_bearer", "lantern_bearer"),
    ("herald", "bellman"), ("surgeon", "almoners_hand"), ("squire", "linkboy"), ("pikeman", "pikeman")]}
shop = [
    {"item": "elsewhere:oathkeepers_seal", "price": 220, "weekly_limit": 2},
    {"item": "elsewhere:mirewick_lantern_shield", "price": 36},
    {"item": "elsewhere:mirewick_oilcloth_dye", "count": 4, "price": 24},
    {"item": "elsewhere:bodkin_bolt", "count": 8, "price": 27},
    {"item": "minecraft:netherite_upgrade_smithing_template", "price": 275, "extra": [{"item": "minecraft:diamond", "min": 20}], "weekly_limit": 2},
]
faction = {
    "name": "Lantern Republic of Mirewick", "name_key": "faction.elsewhere.mirewick",
    "banner": {"primary": "#5B2A86", "secondary": "#9E1B32"},
    "tabard": {"color": "#5B2A86", "trim": "#D6A832"},
    "trim": tr("emerald"),
    "hostile_to_players": False, "allies": [], "peace": [],
    "roster": roster, "rulers": rulers,
    "biomes": ["#elsewhere:mirewick_realm_biomes"], "biomes_fallback": ["minecraft:forest", "minecraft:birch_forest"], "site": {"terrain": "flat", "near_water": True}, "music": "elsewhere:mirewick_music", "realm": True,
    "society": {
        "government": "oligarchy", "ruler_bonus": {"type": "hp_per_other_magnate", "amount": 12.0},
        "leaders": ["marshal", "castellan", "master_at_arms", "huntsman", "chancellor"],
        "standing": {"start": 10, "cap_until_ruler_killed": False},
        "serfs": {"type": "elsewhere:peat_cutter", "count_multiplier": 1.0, "health": 16.0},
        "chair_days": 7, "seat_refill_days": 7,
        "succession": {"mode": "inheritance", "delay_days": 7, "heirs_per_family": 2, "inherit_leaning": True, "repeat_kill": True,
                       "heirs": {s: {"surname": sn, "first_names": fn} for s, sn, fn in HEIRS}},
    },
    "economy": {"currency": "elsewhere:mirewick_scrip", "shop": shop},
    "army": {
        "units": [f"elsewhere:mirewick_{u}" for u in ("dikeguard", "reed_knight", "fowler", "punt_crossbow", "marsh_rider", "pikeman", "reeve", "wardman", "lantern_bearer", "bellman", "almoners_hand", "linkboy")],
        "unique_units": ["elsewhere:mirewick_bog_warden", "elsewhere:mirewick_mire_trapper"],
        "makeup": {"elsewhere:mirewick_dikeguard": 0.18, "elsewhere:mirewick_reed_knight": 0.12, "elsewhere:mirewick_fowler": 0.17, "elsewhere:mirewick_punt_crossbow": 0.18,
                   "elsewhere:mirewick_pikeman": 0.20, "elsewhere:mirewick_marsh_rider": 0.05, "elsewhere:mirewick_bog_warden": 0.05, "elsewhere:mirewick_mire_trapper": 0.05},
        "health_multiplier": 1.0, "speed_multiplier": 1.0, "damage_multiplier": 1.0, "size_multiplier": 0.9,
        "patrol_day": 6, "patrol_night": 6,
        "brain": {
            "war_frequency": 0.5, "min_attack_odds": 0.9, "retreat_instead_of_die": False, "veterancy_cap": 0.0, "intelligence_noise": 0.6,
            "supply_line_only": False, "decision_delay_days": 1.5, "hire_mercenaries": True, "mercenary_daily_cost": 40, "brain_roles": [],
        },
    },
}
w(os.path.join(D, "kingdom_faction", "mirewick.json"), faction)

# ---- Mirewick-only data the other lanes read: council, free companies, castle palette, rebuild style -----------------------
w(os.path.join(D, "mirewick", "council.json"), {
    "succession": "inheritance", "seats": [{"slot": b[0], "key": b[1], "leaning": b[6], "boss": f"elsewhere:mirewick_{b[1]}", "tier": 3 if b[1] in ("marrow", "thwaite", "sorrel") else 2,
               "surname": b[2].split()[-1], "heirs": [f"{n} {b[2].split()[-1]}" for n in names], "max_holders": 3} for b, names in zip(BOSSES, [
        ["Perpetua", "Hester"], ["Osbert", "Maud"], ["Ivo", "Agnes"], ["Piers", "Linnet"], ["Jory", "Matilda"]])],
    "chair": {"vault_base": 100, "per_village": 10, "per_outpost": 5, "weekly_draw": 20, "tie_goes_to": "marrow", "vacancy_days": 1,
              "bonus_hp_per_other_magnate": 12, "bonus_damage": 0.15, "totem": True, "lantern_ally_damage_reduction": 0.10, "lantern_out_hits": 3, "lantern_out_ticks": 80, "lantern_out_seconds": 20},
    "votes": {"hawk": "war", "dove": "peace", "miser": "hire", "tie": ["hire", "peace"], "decision_delay_days": [1, 2], "bribe_scrip": 80, "bribes_per_week": 1},
    "dark_lanterns": {"window_days": 7, "villages_contested": True, "vault_opens": True, "vault": {"treasury": 64, "extra": [16, 32], "emeralds": [6, 12]}, "ends_contracts": True, "permanent": True},
    "squabble": {"convene_radius": 64, "escort_each": 2, "ally_lantern_avoid": True, "richest_flees_at": 0.40, "flee_ticks": 1200, "return_heal": 0.10,
                 "almoner_heal_pct": 0.12, "almoner_casts": 3, "payment_first_below": 0.70, "payment_step": 0.20}})
w(os.path.join(D, "mirewick", "free_companies.json"), {
    "faction": "elsewhere:free_company", "banner": "minecraft:gray_banner", "max_companies": 3, "size": [6, 8], "pay_per_day_scrip": 40,
    "desert_chance_when_broke": 0.5, "outbid": {"factor": 1.25, "minimum": 100, "turn_days": 3},
    "units": {"melee": "elsewhere:mirewick_fc_sellsword", "ranged": "elsewhere:mirewick_fc_crossbow", "heavy": "elsewhere:mirewick_fc_brute",
              "captain": "elsewhere:mirewick_fc_captain", "banner": "elsewhere:mirewick_fc_banner"},
    "makeup": [["melee", 3], ["ranged", 2], ["heavy", 2], ["banner", 1]], "captain_names": ["Black Ned Harrow", "Sister Quill", "Old Brisket", "Tam of the Three Knives", "Corporal Mudd"],
    "player_contract": {"min_standing": 30, "pay": "scrip", "per_kill": 6, "hold_village_per_day": 40}})
w(os.path.join(D, "mirewick", "castle_style.json"), json.loads(r'''{"name": "mirewick_palace", "palette": {"wall": "minecraft:quartz_bricks", "wall_alt": "minecraft:calcite", "pillar": "minecraft:quartz_pillar", "floor": "minecraft:smooth_stone", "roof": "minecraft:red_nether_brick_stairs", "trim": "minecraft:purple_terracotta", "accent": "minecraft:yellow_terracotta", "wood": "minecraft:mangrove_planks", "crimson": "minecraft:crimson_planks", "lantern": "minecraft:lantern", "light": "minecraft:lantern", "window": "minecraft:purple_stained_glass_pane", "path": "minecraft:smooth_stone", "fence": "minecraft:mangrove_fence"}, "extra_pieces": [{"id": "counting_house", "size": [15, 9, 11], "notes": "Counting-House: purple-and-gold vault, the Clerk's desk, Scrip-Desk block, vault chest (Dark Lanterns opens it)"}, {"id": "lantern_hall", "size": [13, 8, 13], "notes": "Council chamber: five seats, the First Lantern on a pedestal (never lit by the player)"}, {"id": "quay_gate", "size": [9, 6, 5], "notes": "gatehouse of pale stone over a dry canal quay, purple banners"}, {"id": "dike_tower", "size": [5, 12, 5], "notes": "Sluice tower on the dike: lantern roof, Dikemaster's post"}, {"id": "scrip_desk_stall", "size": [5, 4, 4], "notes": "shop kiosk for the Scrip-Desk villager"}], "never": ["flood water", "lava", "fire", "block griefing outside the footprint", "valuable blocks (gold, diamond, emerald, iron blocks, gilded blackstone)"]}'''))
w(os.path.join(D, "mirewick", "rebuild_style.json"), json.loads(r'''{"style": "canal_quays_dikes", "budget_blocks_per_serf_per_20_ticks": 4, "budget_blocks_per_chunk_load": 400, "swap": {"banner": "mirewick", "lantern": "minecraft:lantern", "decor_wall": "minecraft:quartz_bricks", "road": "minecraft:smooth_stone", "road_edge": "minecraft:mangrove_fence", "quay": "minecraft:stone_bricks", "dike": "minecraft:grass_block", "post": "minecraft:stripped_mangrove_log", "accent": "minecraft:yellow_terracotta", "roof": "minecraft:red_nether_brick_stairs"}, "never_place_water": true, "only_replace_tracked_old_rebuild_blocks": true, "builders": "elsewhere:mirewick_peat_cutter", "strike_days_after_counting_house_raid": 3, "counting_house_raid_triggers": ["vault_opened", "clerk_killed", "keep_outpost_sacked"], "roads_as_canal_quays": true, "quay_blocks_beside_existing_water_only": true, "dikes_raised_with_grass_and_stone_never_flooding": true}'''))
w(os.path.join(D, "kingdom_castle", "mirewick.json"), json.loads(r'''{"palette": {"minecraft:stone_bricks": "minecraft:quartz_bricks", "minecraft:stone_brick_stairs": "minecraft:quartz_stairs", "minecraft:stone_brick_slab": "minecraft:quartz_slab", "minecraft:stone_brick_wall": "minecraft:diorite_wall", "minecraft:cobblestone": "minecraft:calcite", "minecraft:stone": "minecraft:smooth_stone", "minecraft:mossy_stone_bricks": "minecraft:chiseled_quartz_block", "minecraft:cracked_stone_bricks": "minecraft:smooth_quartz", "minecraft:chiseled_stone_bricks": "minecraft:chiseled_quartz_block", "minecraft:andesite": "minecraft:diorite", "minecraft:polished_andesite": "minecraft:polished_diorite", "minecraft:spruce_planks": "minecraft:mangrove_planks", "minecraft:spruce_log": "minecraft:mangrove_log", "minecraft:spruce_slab": "minecraft:mangrove_slab", "minecraft:spruce_stairs": "minecraft:mangrove_stairs", "minecraft:spruce_fence": "minecraft:mangrove_fence", "minecraft:spruce_trapdoor": "minecraft:mangrove_trapdoor", "minecraft:spruce_fence_gate": "minecraft:mangrove_fence_gate", "minecraft:oak_planks": "minecraft:crimson_planks", "minecraft:oak_log": "minecraft:crimson_stem", "minecraft:oak_fence": "minecraft:mangrove_fence", "minecraft:oak_stairs": "minecraft:crimson_stairs", "minecraft:dark_oak_planks": "minecraft:red_nether_bricks", "minecraft:dark_oak_stairs": "minecraft:red_nether_brick_stairs", "minecraft:dark_oak_slab": "minecraft:red_nether_brick_slab", "minecraft:dark_oak_fence": "minecraft:mangrove_fence", "minecraft:deepslate_bricks": "minecraft:purple_terracotta", "minecraft:deepslate_tiles": "minecraft:purple_terracotta", "minecraft:polished_deepslate": "minecraft:purple_terracotta", "minecraft:polished_blackstone_bricks": "minecraft:purple_terracotta", "minecraft:gold_block": "minecraft:yellow_terracotta", "minecraft:blue_wool": "minecraft:purple_wool", "minecraft:blue_stained_glass_pane": "minecraft:purple_stained_glass_pane", "minecraft:dirt_path": "minecraft:smooth_stone"}, "pieces": [{"type": "box", "from": [-16, 0, 31], "to": [16, 0, 33], "block": "minecraft:smooth_stone", "hollow": false}, {"type": "box", "from": [-16, 1, 34], "to": [16, 1, 34], "block": "minecraft:quartz_bricks", "hollow": false}, {"type": "box", "from": [-16, 2, 34], "to": [16, 2, 34], "block": "minecraft:quartz_slab", "hollow": false}, {"type": "column", "at": [-14, 1, 32], "height": 3, "block": "minecraft:mangrove_fence", "cap": "minecraft:lantern"}, {"type": "box", "from": [-15, 1, 34], "to": [-13, 1, 34], "block": "minecraft:yellow_terracotta", "hollow": false}, {"type": "column", "at": [-7, 1, 32], "height": 3, "block": "minecraft:mangrove_fence", "cap": "minecraft:lantern"}, {"type": "box", "from": [-8, 1, 34], "to": [-6, 1, 34], "block": "minecraft:yellow_terracotta", "hollow": false}, {"type": "column", "at": [0, 1, 32], "height": 3, "block": "minecraft:mangrove_fence", "cap": "minecraft:lantern"}, {"type": "box", "from": [-1, 1, 34], "to": [1, 1, 34], "block": "minecraft:yellow_terracotta", "hollow": false}, {"type": "column", "at": [7, 1, 32], "height": 3, "block": "minecraft:mangrove_fence", "cap": "minecraft:lantern"}, {"type": "box", "from": [6, 1, 34], "to": [8, 1, 34], "block": "minecraft:yellow_terracotta", "hollow": false}, {"type": "column", "at": [14, 1, 32], "height": 3, "block": "minecraft:mangrove_fence", "cap": "minecraft:lantern"}, {"type": "box", "from": [13, 1, 34], "to": [15, 1, 34], "block": "minecraft:yellow_terracotta", "hollow": false}, {"type": "column", "at": [-17, 0, 32], "height": 5, "block": "minecraft:quartz_pillar", "cap": "minecraft:yellow_terracotta"}, {"type": "set", "at": [-17, 6, 32], "block": "minecraft:lantern"}, {"type": "column", "at": [17, 0, 32], "height": 5, "block": "minecraft:quartz_pillar", "cap": "minecraft:yellow_terracotta"}, {"type": "set", "at": [17, 6, 32], "block": "minecraft:lantern"}]}'''))
w(os.path.join(D, "tags", "worldgen", "biome", "mirewick_realm_biomes.json"), {"replace": False, "values": ["minecraft:plains", "minecraft:sunflower_plains", "minecraft:flower_forest", "minecraft:meadow"]})

# ---- trim, banner, recipes, tags -----------------------------------------------------------------------------------------
w(os.path.join(D, "trim_pattern", "mirewick.json"), {"asset_id": "elsewhere:mirewick", "decal": False, "description": {"translate": "trim_pattern.elsewhere.mirewick"}})
w(os.path.join(D, "banner_pattern", "mirewick.json"), {"asset_id": "elsewhere:mirewick", "translation_key": "block.elsewhere.banner.mirewick"})
w(os.path.join(D, "tags", "banner_pattern", "pattern_item", "mirewick.json"), {"values": ["elsewhere:mirewick"]})
w(os.path.join(D, "recipe", "mirewick_armor_trim_smithing_template.json"), {
    "type": "minecraft:crafting_shaped", "key": {"#": "minecraft:diamond", "C": "minecraft:mud_bricks", "S": "elsewhere:mirewick_armor_trim_smithing_template"},
    "pattern": ["#S#", "#C#", "###"], "result": {"count": 2, "id": "elsewhere:mirewick_armor_trim_smithing_template"}})
w(os.path.join(D, "recipe", "mirewick_armor_trim_smithing_template_smithing_trim.json"), {
    "type": "minecraft:smithing_trim", "addition": "#minecraft:trim_materials", "base": "#minecraft:trimmable_armor", "pattern": "elsewhere:mirewick",
    "template": "elsewhere:mirewick_armor_trim_smithing_template"})
ALLITEMS = ["mirewick_scrip", "mirewick_seal_marrow", "mirewick_seal_thwaite", "mirewick_seal_sorrel", "mirewick_seal_tolliver", "mirewick_seal_ames",
            "mirewick_debt_collectors_leggings", "mirewick_fen_marshals_waders", "mirewick_dikewardens_brigandine", "mirewick_fowlers_marshbow", "mirewick_almoners_cowl",
            "mirewick_lantern_coronet", "mirewick_marsh_lantern_standard", "mirewick_reedcutter_pike", "mirewick_armor_trim_smithing_template", "mirewick_banner_pattern",
            "mirewick_lantern_shield", "mirewick_oilcloth_dye"]
w(os.path.join(RES, "data", "testmeter", "tags", "item", "transmutation_excluded.json"), {"replace": False, "values": [{"id": "elsewhere:" + i, "required": False} for i in ALLITEMS]})
w(os.path.join(D, "tags", "item", "lore_seals.json"), {"replace": False, "values": [{"id": f"elsewhere:mirewick_seal_{b[1]}", "required": False} for b in BOSSES]})
w(os.path.join(D, "tags", "item", "mirewick_cloth_repair.json"), {"replace": False, "values": ["#minecraft:wool", "minecraft:leather"]})
w(os.path.join(D, "tags", "item", "mirewick_iron_repair.json"), {"replace": False, "values": ["minecraft:iron_ingot", "minecraft:emerald"]})
w(os.path.join(D, "tags", "item", "mirewick_wood_repair.json"), {"replace": False, "values": ["#minecraft:planks"]})
w(os.path.join(D, "tags", "entity_type", "mirewick_bosses.json"), {"replace": False, "values": [f"elsewhere:mirewick_{b[1]}" for b in BOSSES]})

# ---- boss_music slot ids (tracks come later; the integrator merges these lines into boss_music.json) ----------------------
w(os.path.join(HERE, "..", "INTEGRATION", "boss_music_mirewick.json"), {
    "_comment": "Merge into boss_music.json 'bosses'. Slot ids only: sounds.json entries and OGGs arrive with the music lane (6 tracks: five Magnates plus the realm; the Chair's theme adds a second fiddle line while the First Lantern is lit).",
    "bosses": {f"elsewhere:mirewick_{b[1]}": f"elsewhere:mirewick_music.{b[1]}" for b in BOSSES},
    "realm": "elsewhere:mirewick_music.realm", "chair_layer": "elsewhere:mirewick_music.chair_layer",
    "moods": {"marrow": "fiddle and a muffled bell, bog-drum walking pace", "thwaite": "bog-drum, low bowed bass, brass fanfare on Long Thrust",
              "sorrel": "heartbeat bog-drum, tolling bells, organ drone", "tolliver": "thin pipe, duck-call whistle, reed wind", "ames": "plucked line, soft bell, choir hum on Dusk-Rite"}})

# ---- lang ------------------------------------------------------------------------------------------------------------------
L = {
    "faction.elsewhere.mirewick": "Lantern Republic of Mirewick",
    "itemGroup.elsewhere.rival_mirewick": "Mirewick",
    "item.elsewhere.mirewick_scrip": "Lantern Scrip", "item.elsewhere.mirewick_scrip.tooltip": "Sealed paper notes of the Lantern Republic of Mirewick",
    "item.elsewhere.mirewick_armor_trim_smithing_template": "Mirewick Armor Trim", "item.elsewhere.mirewick_armor_trim_smithing_template.new": "Mirewick Armor Trim",
    "item.elsewhere.mirewick_banner_pattern": "Mirewick Banner Pattern", "item.elsewhere.mirewick_banner_pattern.new": "Mirewick Banner Pattern", "item.elsewhere.mirewick_banner_pattern.desc": "Mirewick Lantern",
    "trim_pattern.elsewhere.mirewick": "Mirewick Armor Trim", "block.elsewhere.banner.mirewick": "Mirewick Lantern",
    "item.elsewhere.mirewick_lantern_shield": "Lantern Shield", "item.elsewhere.mirewick_oilcloth_dye": "Oilcloth Coat Dye",
    "item.elsewhere.mirewick_lantern_shield.desc1": "Blocks like a shield. The First Lantern, never allowed to go out.",
    "item.elsewhere.mirewick_oilcloth_dye.desc1": "Dyes leather armor the royal purple of a Mirewick dikeguard's coat.",
    "item.elsewhere.mirewick_debt_collectors_leggings": "Debt-Collector's Leggings", "item.elsewhere.mirewick_debt_collectors_leggings.desc1": "Cobweb-Walker: no slowdown from cobweb or mud.",
    "item.elsewhere.mirewick_debt_collectors_leggings.desc2": "+5% movement on planks and mud-brick.", "item.elsewhere.mirewick_debt_collectors_leggings.desc3": "Below netherite: less armor, less toughness, half the knockback resistance.",
    "item.elsewhere.mirewick_fen_marshals_waders": "Fen-Marshal's Waders", "item.elsewhere.mirewick_fen_marshals_waders.desc1": "Walks the water as Depth Strider II would.",
    "item.elsewhere.mirewick_fen_marshals_waders.desc2": "No slowdown from soul sand.", "item.elsewhere.mirewick_fen_marshals_waders.desc3": "Less toughness than netherite.",
    "item.elsewhere.mirewick_dikewardens_brigandine": "Dikewarden's Brigandine", "item.elsewhere.mirewick_dikewardens_brigandine.desc1": "Armor 8, toughness 3 while in water or rain.",
    "item.elsewhere.mirewick_dikewardens_brigandine.desc2": "Armor 6, toughness 2 when dry.", "item.elsewhere.mirewick_dikewardens_brigandine.desc3": "Equal to netherite wet, a clear downgrade dry.",
    "item.elsewhere.mirewick_fowlers_marshbow": "Fowler's Marshbow", "item.elsewhere.mirewick_fowlers_marshbow.desc1": "Arrows keep full speed and damage underwater and float on the surface.",
    "item.elsewhere.mirewick_fowlers_marshbow.desc2": "The same draw time as a vanilla bow.",
    "item.elsewhere.mirewick_almoners_cowl": "Almoner's Cowl", "item.elsewhere.mirewick_almoners_cowl.desc1": "Calling: +10% duration on beneficial potions you drink.",
    "item.elsewhere.mirewick_almoners_cowl.desc2": "A real downgrade for fighting, nice for brewers.",
    "item.elsewhere.mirewick_lantern_coronet": "Lantern Coronet", "item.elsewhere.mirewick_lantern_coronet.desc1": "Marsh-Light: Night Vision in swamp, mangrove swamp and underwater.",
    "item.elsewhere.mirewick_lantern_coronet.desc2": "-10% damage taken in rain or water; +1% per Lord Seal (max 12%).", "item.elsewhere.mirewick_lantern_coronet.desc3": "Dropped by whoever held the Chair.",
    "item.elsewhere.mirewick_marsh_lantern_standard": "Marsh-Lantern Standard", "item.elsewhere.mirewick_marsh_lantern_standard.desc1": "Plant it: Water Breathing and Dolphin's Grace within 16 blocks.",
    "item.elsewhere.mirewick_reedcutter_pike": "Reedcutter Pike", "item.elsewhere.mirewick_reedcutter_pike.desc1": "+0.75 blocks of reach. Pikemaster Dunstan Reeve's weapon.",
    "message.elsewhere.mirewick_standard_planted": "The standard takes root: the water gives way around you.",
    "entity.elsewhere.mirewick_bog_warden": "Bog-Warden", "entity.elsewhere.mirewick_mire_trapper": "Mire-Trapper",
    "item.elsewhere.mirewick_bog_warden_spawn_egg": "Bog-Warden Spawn Egg", "item.elsewhere.mirewick_mire_trapper_spawn_egg": "Mire-Trapper Spawn Egg",
    "message.elsewhere.mirewick_almoner_paid": "%s pays the Almoner.", "message.elsewhere.mirewick_flees": "%s withdraws to guard the vault.",
    "message.elsewhere.mirewick_chair_lantern_out": "The First Lantern gutters out.",
    "message.elsewhere.mirewick_no_pay": "No pay, no fight.", "message.elsewhere.mirewick_mind_mud": "Mind the mud.",
}
BN = {"marrow": ("Ottoline Marrow", "Seal of the Counting-House"), "thwaite": ("Fen-Marshal Garrick Thwaite", "Seal of the Dikes"),
      "sorrel": ("Dikemaster Hilde Sorrel", "Seal of the Sluices"), "tolliver": ("Wildfowler Wren Tolliver", "Seal of the Reed Hides"), "ames": ("Lord Almoner Cuthbert Ames", "Seal of the Lantern-Oil")}
for k, (n, s) in BN.items():
    L[f"entity.elsewhere.mirewick_{k}"] = n
    L[f"item.elsewhere.mirewick_{k}_spawn_egg"] = n + " Spawn Egg"
    L[f"item.elsewhere.mirewick_seal_{k}"] = s
    L[f"item.elsewhere.mirewick_seal_{k}.desc1"] = "A Lord Seal of the Lantern Council. A collector's token: +1% Rally with the Crown of Aldermark."
for s, sn, fn in HEIRS:
    for i, n in enumerate(fn, 1):
        L[f"mirewick.heir.{s}.{i}"] = f"{n} {sn}"
for c in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"):
    L[f"block.elsewhere.banner.mirewick.{c}"] = c.replace("_", " ").title() + " Mirewick Lantern"
w(os.path.join(A, "lang", "en_us.json"), L)

# ---- JEI info lines (jei_compat/tools/gen_info2.py merges INTEGRATION/jei_info_*.json) ----------------------------------------
w(os.path.join(HERE, "..", "INTEGRATION", "jei_info_mirewick.json"), {
    "elsewhere:mirewick_scrip": ["The currency of the Lantern Republic of Mirewick. Dropped by Mirewick soldiers (1-3), officers (4-8), Magnates (16-32) and the Chair (64 treasury plus more).",
                                 "Spent at Mirewick's Scrip-Desk shop. The Clerk of the Counting-House also exchanges any currency into Scrip at 4:3, and Scrip into other currencies 1:1 while you are Friendly."],
    "elsewhere:mirewick_debt_collectors_leggings": ["Dropped by Ottoline Marrow, Magnate of the Counting-House (100% on a first kill, then 67%). No slowdown from cobweb or mud."],
    "elsewhere:mirewick_fen_marshals_waders": ["Dropped by Fen-Marshal Garrick Thwaite. Depth Strider II equivalent; no slowdown from soul sand."],
    "elsewhere:mirewick_dikewardens_brigandine": ["Dropped by Dikemaster Hilde Sorrel. Netherite-level armor while in water or rain, weaker when dry."],
    "elsewhere:mirewick_fowlers_marshbow": ["Dropped by Wildfowler Wren Tolliver. Arrows keep their speed underwater and float on the surface."],
    "elsewhere:mirewick_almoners_cowl": ["Dropped by Lord Almoner Cuthbert Ames. Beneficial potions you drink last 10% longer."],
    "elsewhere:mirewick_lantern_coronet": ["Dropped by whichever Magnate holds the Chair when killed (first kill 100%). Night Vision in swamps and underwater."],
    "elsewhere:mirewick_marsh_lantern_standard": ["50% from the Chair-holder. Plant it for Water Breathing and Dolphin's Grace within 16 blocks."],
    "elsewhere:mirewick_reedcutter_pike": ["20% from Pikemaster Dunstan Reeve, the keep's elite captain. +0.75 blocks of reach."],
    "elsewhere:mirewick_armor_trim_smithing_template": ["Dropped by Mirewick Wardmen (captains), 8%. Duplicate with diamonds and mud bricks."],
    "elsewhere:mirewick_banner_pattern": ["Dropped by Lantern-Bearers (standard-bearers). Adds the Mirewick lantern to a banner."],
    "elsewhere:mirewick_lantern_shield": ["A shield that blocks like the vanilla one (336 durability, an axe disables it), sold at Mirewick's Scrip-Desk."],
    "elsewhere:mirewick_oilcloth_dye": ["A cosmetic dye sold at Mirewick's Scrip-Desk."],
    **{f"elsewhere:mirewick_seal_{k}": [f"A Lord Seal dropped by {BN[k][0]}. Collectors' token; counts toward the Crown of Aldermark's Rally bonus."] for k in BN}})
print("data written")
