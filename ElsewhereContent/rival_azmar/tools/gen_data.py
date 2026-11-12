"""Generates Azmar's data/ and assets json (faction, units, items, models, equipment, tags, recipes, lang, jei, boss music).
Run: python3 tools/gen_data.py   (idempotent; rewrites everything it owns)"""
import json, os, math
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
EXTRA = os.path.join(HERE, "..", "INTEGRATION")
A = os.path.join(RES, "assets", "elsewhere"); D = os.path.join(RES, "data", "elsewhere")

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f: json.dump(obj, f, indent=1, ensure_ascii=False); f.write("\n")

SK = "elsewhere:textures/entity/kingdom/azmar/"
LAPIS, GOLD, TURQ = 0x1F3F8F, 0xD4A537, 0x2BB3A3
def trim(mat, *pats): return {"material": "minecraft:" + mat, "patterns": ["elsewhere:azmar"] + ["minecraft:" + p for p in pats]}
def arm(h, c, l, f): return {"head": h, "chest": c, "legs": l, "feet": f}
CH = lambda p: f"minecraft:chainmail_{p}"; LE = lambda p: f"minecraft:leather_{p}"; GO = lambda p: f"minecraft:golden_{p}"; IR = lambda p: f"minecraft:iron_{p}"
CK, GK, TK, SP, BOW, POT, BR = ("elsewhere:azmar_copper_khopesh", "elsewhere:azmar_gilded_khopesh", "elsewhere:azmar_twin_khopesh",
    "elsewhere:azmar_scarab_spear", "elsewhere:azmar_composite_bow", "elsewhere:azmar_fire_pot", None)

# ---- units (base numbers; the faction's 0.8 / 0.9 / 1.1 multipliers apply on top) ------------------------------------
def unit(name, **kw):
    w(os.path.join(D, "kingdom_unit", name + ".json"), kw)
combat_melee = {"crit_cooldown": 200, "sprint_chance": 0.2, "strafe": 0.4, "retreat_chance": 0.35, "block_chance": 0.55, "crit_chance": 0.06, "retreat_health": 0.3}
unit("azmar_spearguard", role="melee", skin=SK + "spearguard.png", armor=arm(GO("helmet"), CH("chestplate"), LE("leggings"), CH("boots")),
     armor_dye=LAPIS, main_hand=CK, off_hand="minecraft:shield", stats={"health": 20, "speed": 0.28}, combat=combat_melee, trim=trim("lapis", "dune", "wayfinder"))
unit("azmar_sunguard", role="melee", ability="bash", skin=SK + "sunguard.png", armor=arm(GO("helmet"), CH("chestplate"), CH("leggings"), GO("boots")),
     main_hand=CK, off_hand="minecraft:shield", stats={"health": 26, "speed": 0.28, "knockback_resistance": 0.1},
     combat={**combat_melee, "crit_cooldown": 160, "block_chance": 0.7, "crit_chance": 0.1, "retreat_health": 0.25}, trim=trim("lapis", "dune", "wayfinder"))
unit("azmar_jackal_guard", role="melee", ability="bash", skin=SK + "sunguard.png", armor=arm("elsewhere:azmar_jackal_helm", CH("chestplate"), CH("leggings"), GO("boots")),
     main_hand=GK, off_hand="minecraft:shield", stats={"health": 26, "speed": 0.28, "knockback_resistance": 0.1},
     combat={**combat_melee, "crit_cooldown": 160, "block_chance": 0.7, "crit_chance": 0.1, "retreat_health": 0.0}, trim=trim("gold", "dune", "wayfinder"))
unit("azmar_camel_lancer", role="lancer", skin=SK + "spearguard.png", armor=arm(IR("helmet"), CH("chestplate"), CH("leggings"), CH("boots")), main_hand=SP,
     mount="minecraft:camel", stats={"health": 20, "speed": 0.28}, combat={"block_chance": 0.0, "crit_chance": 0.0, "retreat_health": 0.0}, trim=trim("copper", "dune", "wayfinder"))
unit("azmar_bowman", role="archer", skin=SK + "bowman.png", armor=arm(LE("helmet"), LE("chestplate"), LE("leggings"), LE("boots")), armor_dye=TURQ, main_hand=BOW,
     stats={"health": 16, "speed": 0.27}, combat={"block_chance": 0.0, "crit_chance": 0.0, "retreat_health": 0.0}, trim=trim("lapis", "dune"))
unit("azmar_arbalester", role="crossbow", skin=SK + "bowman.png", armor=arm(LE("helmet"), CH("chestplate"), LE("leggings"), LE("boots")), armor_dye=LAPIS, main_hand="minecraft:crossbow",
     stats={"health": 18, "speed": 0.27}, combat={"block_chance": 0.0, "crit_chance": 0.0, "retreat_health": 0.0}, trim=trim("lapis", "wayfinder"))
unit("azmar_jemadar", role="melee", officer=True, ability="captain", skin=SK + "jemadar.png", armor=arm(GO("helmet"), CH("chestplate"), CH("leggings"), GO("boots")),
     main_hand=GK, off_hand="minecraft:shield", stats={"health": 34, "speed": 0.29}, combat={**combat_melee, "block_chance": 0.7, "retreat_health": 0.0}, trim=trim("gold", "dune", "wayfinder"), aggression=2)
unit("azmar_qaid", role="melee", officer=True, ability="captain", skin=SK + "qaid.png", armor=arm(GO("helmet"), IR("chestplate"), CH("leggings"), IR("boots")),
     main_hand=GK, off_hand="minecraft:shield", stats={"health": 46, "speed": 0.29, "knockback_resistance": 0.3},
     combat={"crit_cooldown": 120, "sprint_chance": 0.3, "strafe": 0.6, "retreat_chance": 0.35, "block_chance": 0.8, "crit_chance": 0.15, "retreat_health": 0.0}, trim=trim("gold", "dune", "wayfinder"), aggression=3)
unit("azmar_scarab_bearer", role="melee", ability="standard", skin=SK + "scarab_bearer.png", armor=arm("minecraft:blue_banner", CH("chestplate"), CH("leggings"), CH("boots")),
     main_hand=CK, stats={"health": 34, "speed": 0.28}, combat={**combat_melee, "block_chance": 0.0, "retreat_chance": 0.5, "retreat_health": 0.4}, trim=trim("gold", "dune"), aggression=2)
unit("azmar_crier", role="melee", ability="herald", skin=SK + "herald.png", armor=arm(GO("helmet"), CH("chestplate"), LE("leggings"), LE("boots")),
     main_hand=CK, stats={"health": 24, "speed": 0.28}, combat={**combat_melee, "block_chance": 0.0}, trim=trim("gold", "wayfinder"), aggression=2)
unit("azmar_physician", role="melee", ability="surgeon", skin=SK + "physician.png", armor=arm(LE("helmet"), LE("chestplate"), LE("leggings"), LE("boots")), armor_dye=0xEDE3CC,
     main_hand=CK, stats={"health": 20, "speed": 0.28}, combat={**combat_melee, "block_chance": 0.0}, trim=trim("gold", "wayfinder"), aggression=1)
unit("azmar_page", role="melee", ability="squire", skin=SK + "porter.png", armor=arm(LE("helmet"), LE("chestplate"), LE("leggings"), LE("boots")), armor_dye=0xEDE3CC,
     main_hand=CK, stats={"health": 18, "speed": 0.28}, combat={**combat_melee, "block_chance": 0.0}, trim=trim("copper", "dune"))
unit("azmar_pikeman", role="melee", skin=SK + "spearguard.png", armor=arm(IR("helmet"), LE("chestplate"), LE("leggings"), LE("boots")), armor_dye=LAPIS, main_hand=SP,
     stats={"health": 18, "speed": 0.28}, combat={**combat_melee, "block_chance": 0.0, "crit_chance": 0.05, "mounted_bonus": 2.0}, trim=trim("lapis", "dune", "wayfinder"))
# the two unique soldiers (base numbers; faction multipliers apply)
unit("azmar_sand_dancer", role="melee", skin=SK + "sand_dancer.png", armor=arm(None, LE("chestplate"), LE("leggings"), LE("boots")), armor_dye=0xEDE3CC,
     main_hand=TK, stats={"health": 14, "speed": 0.31}, combat={**combat_melee, "block_chance": 0.0, "strafe": 0.8, "sprint_chance": 0.35, "crit_chance": 0.1, "retreat_health": 0.2}, trim=trim("lapis", "dune"))
unit("azmar_naffatun", role="archer", skin=SK + "naffatun.png", armor=arm(LE("helmet"), LE("chestplate"), LE("leggings"), LE("boots")), armor_dye=0x8C5A38,
     main_hand=POT, stats={"health": 14, "speed": 0.28}, combat={"block_chance": 0.0, "crit_chance": 0.0, "retreat_health": 0.3}, trim=trim("copper", "dune"))
# unit dicts need no None keys
for fn in os.listdir(os.path.join(D, "kingdom_unit")):
    if fn.startswith("azmar_"):
        p = os.path.join(D, "kingdom_unit", fn); o = json.load(open(p))
        o["armor"] = {k: v for k, v in o.get("armor", {}).items() if v}
        w(p, o)

# ---- faction -----------------------------------------------------------------------------------------------------
def drops(*items): return [dict(item=i[0], **({"min": i[1], "max": i[2]} if len(i) > 2 else {}), **({"chance": i[1]} if len(i) == 2 else {})) for i in items]
SEALS = {"sultana": "elsewhere:azmar_seal_sultana", "wazir": "elsewhere:azmar_seal_wazir", "falconer": "elsewhere:azmar_seal_falconer", "amir": "elsewhere:azmar_seal_amir"}
def ruler(name, key, title, hp, sig, cap=None, extra=()):
    d = {"name": name, "name_key": f"entity.elsewhere.azmar_{key}", "title": title, "health": hp}
    if cap: d["health_cap"] = cap
    d["drops"] = [dict(item=s) if isinstance(s, str) else s for s in sig] + list(extra)
    return d
dinar = "elsewhere:azmar_dinar"
# RIVALS-DECISIONS 13: human bosses never exceed 300 max health. HP = round(card HP x 300/560) (card: 420/340/300/320);
# only the Sultana has a cap (her +12 per living court boss stays under it). Matches the shipped azmar.json.
rulers = {   # court slots (rank order): king = Sultana, marshal = Amir, castellan = Wazir, huntsman = Falconer. Titles carry the brain keyword the war lane matches.
    "king": ruler("Sultana Nahira Zahr-Qadir", "sultana", "Sultana, the Unsetting Sun", 225,
        ["elsewhere:azmar_nemes_of_the_uraeus", "elsewhere:azmar_winged_scarab_standard", SEALS["sultana"], "elsewhere:oathkeepers_seal"], 300,
        [{"item": dinar, "min": 80, "max": 96}, {"item": "minecraft:emerald", "min": 6, "max": 12}]),
    "marshal": ruler("Amir Tariq Vashti", "amir", "Amir of Spears, the Horned Scarab", 182,
        ["elsewhere:azmar_sandrunner_sandals", "elsewhere:azmar_horned_scarab_crown", SEALS["amir"], "elsewhere:oathkeepers_seal"], None, [{"item": dinar, "min": 16, "max": 32}]),
    "castellan": ruler("Grand Wazir Idris Halvane", "wazir", "Grand Wazir, Keeper of the Ibis Glyphs", 161,
        ["elsewhere:azmar_ibis_crest", SEALS["wazir"], "elsewhere:oathkeepers_seal"], None, [{"item": dinar, "min": 16, "max": 32}]),
    "huntsman": ruler("Royal Falconer Leyla Dunmar", "falconer", "Royal Falconer, Mistress of the Falcons", 171,
        ["elsewhere:azmar_falconers_gauntlet", "elsewhere:azmar_falcon_helm", SEALS["falconer"], "elsewhere:oathkeepers_seal"], None, [{"item": dinar, "min": 16, "max": 32}]),
}
assert all(r["health"] <= 300 and r.get("health_cap", 0) <= 300 for r in rulers.values()), "human boss over 300 HP (RIVALS-DECISIONS 13)"
def ro(unit_id, **kw): return {"unit": f"elsewhere:{unit_id}", **kw}
roster = {
    "elsewhere:man_at_arms": ro("azmar_spearguard"), "elsewhere:knight": ro("azmar_sunguard"), "elsewhere:lancer": ro("azmar_camel_lancer"),
    "elsewhere:archer": ro("azmar_bowman"), "elsewhere:crossbowman": ro("azmar_arbalester"), "elsewhere:sergeant": ro("azmar_jemadar"),
    "elsewhere:captain": ro("azmar_qaid"), "elsewhere:standard_bearer": ro("azmar_scarab_bearer"), "elsewhere:herald": ro("azmar_crier"),
    "elsewhere:surgeon": ro("azmar_physician"), "elsewhere:kingdom_royal_envoy": {"skin": SK + "envoy.png"}, "elsewhere:squire": ro("azmar_page"), "elsewhere:pikeman": ro("azmar_pikeman"),
}
shop = [  # prices in Dinars, same range as Aldermark's (cosmetics 20-40, Seals 220, books, templates)
    {"item": "elsewhere:oathkeepers_seal", "price": 220, "weekly_limit": 2},
    {"item": "elsewhere:azmar_armor_trim_smithing_template", "price": 30, "weekly_limit": 2},
    {"item": "elsewhere:azmar_banner_pattern", "price": 24},
    {"item": "elsewhere:azmar_copper_khopesh", "price": 28},
    {"item": "elsewhere:azmar_composite_bow", "price": 36},
    {"item": "elsewhere:azmar_scarab_spear", "price": 36},
    {"item": "elsewhere:azmar_fire_pot", "count": 4, "price": 20},
    {"item": "minecraft:enchanted_book", "price": 40, "extra": [], "note": "a book of Unbreaking III (data-driven stack by the shop code)"},
    {"item": "minecraft:netherite_upgrade_smithing_template", "price": 275, "extra": [{"item": "minecraft:diamond", "min": 20}], "weekly_limit": 2},
]
shop = [{k: v for k, v in s.items() if k != "note"} for s in shop if s["item"] != "minecraft:enchanted_book"]
faction = {
    "name": "Sultanate of Azmar", "name_key": "faction.elsewhere.azmar",
    "banner": {"primary": "#1F3F8F", "secondary": "#D4A537"}, "tabard": {"color": "#1F3F8F", "trim": "#2BB3A3"},
    "hostile_to_players": False, "allies": [], "peace": [], "roster": roster,
    "biomes": ["#elsewhere:azmar_realm_biomes"], "music": "elsewhere:azmar_music", "realm": True,
    "rulers": rulers,
    "society": {"government": "monarchy", "ruler_bonus": {"type": "hp_per_lord", "amount": 12},
                "leaders": ["king", "marshal", "castellan", "huntsman"], "standing": {"start": 0, "cap_until_ruler_killed": False},
                "succession": {"mode": "regent", "ruler_dies": "highest_living_lord_becomes_regent", "lord_dies": "never_replaced", "brain_loss": "permanent", "never_promoted": ["hajib", "ustad"], "seat_refill_days": 0, "falls_when": "all_dead"},
                "serfs": {"count_multiplier": 0.5, "health": 14}},
    "economy": {"currency": dinar, "shop": shop},
    "army": {
        "units": [v["unit"] for v in roster.values() if "unit" in v], "unique_units": ["elsewhere:azmar_sand_dancer", "elsewhere:azmar_naffatun"],
        "makeup": {"elsewhere:azmar_spearguard": 0.175, "elsewhere:azmar_sunguard": 0.175, "elsewhere:azmar_bowman": 0.25, "elsewhere:azmar_camel_lancer": 0.20,
                   "elsewhere:azmar_sand_dancer": 0.06, "elsewhere:azmar_naffatun": 0.06, "elsewhere:azmar_pikeman": 0.0},
        "health_multiplier": 0.8, "damage_multiplier": 0.9, "speed_multiplier": 1.1, "size_multiplier": 0.75, "patrol_day": 5, "patrol_night": 4,
        "brain": {  # the war lane has the WAR-AI section 3 Azmar values built in by id; these legacy fields only repeat them
            "war_frequency": 1.0, "min_attack_odds": 1.5, "retreat_instead_of_die": True, "veterancy_cap": 0.30, "intelligence_noise": 0.07,
            "supply_line_only": True, "decision_delay_days": 0, "hire_mercenaries": False, "mercenary_daily_cost": 0,
            "brain_roles": [
                {"role": "king", "degrades": ["fort_rate", "retreat", "commit"]},
                {"role": "castellan", "degrades": ["timing", "broker", "alliances", "caravan_income"]},
                {"role": "huntsman", "degrades": ["noise", "instant_read"]}]},
        },
    "tactics": {  # FIELD-TACTICS section 3: the deepest profile (war lane framework reads this block)
        "profile": "azmar", "decision_interval": 10, "min_local_odds": 1.5, "retreat_odds": 0.85, "blunder_rate": 0.03, "reaction_ticks": [6, 14],
        "behaviours": {"refuse_fight": {"weight": 1.0}, "call_help": {"weight": 1.0}, "shield_wall": {"weight": 1.0}, "hammer_anvil": {"weight": 1.0},
                       "crescent": {"weight": 1.0, "min_ratio": 2.0}, "feigned_retreat": {"weight": 1.0}, "focus_fire": {"weight": 1.0},
                       "kite_melee": {"weight": 1.0}, "break_los": {"weight": 1.0}, "back_off_creeper": {"weight": 1.0}, "avoid_warden": {"weight": 1.0},
                       "ambush_road": {"weight": 1.0}, "regroup_at_obelisk": {"weight": 1.0}, "high_ground": {"weight": 1.0}},
        "roles": {"ARCHER": {"standoff": 10, "strafe": True}, "MELEE": {"standoff": 0}, "LANCER": {"hold_back": 24}},
        "mob_policy": "careful", "signals": {"horn": True, "banner": True, "shout_chance": 0.5}},
}
w(os.path.join(D, "kingdom_faction", "azmar.json"), faction)

# ---- castle look (court lane's CastleStyles: data/<ns>/kingdom_castle/<faction path>.json) ---------------------------------
def col(x, h, cap="minecraft:cyan_terracotta"): return {"type": "column", "at": [x, 0, 33], "height": h, "block": "minecraft:cut_sandstone", "cap": cap}
pieces = []
for side in (-1, 1):
    x = side * 7
    pieces += [
        {"type": "box", "from": [x - 2, 0, 31], "to": [x + 2, 0, 34], "block": "minecraft:smooth_sandstone", "hollow": False},      # plinth
        {"type": "box", "from": [x - 1, 1, 31], "to": [x + 1, 2, 33], "block": "minecraft:cut_sandstone", "hollow": False},      # lion body
        {"type": "box", "from": [x - 1, 1, 34], "to": [x - 1, 1, 34], "block": "minecraft:cut_sandstone", "hollow": False},      # forepaws
        {"type": "box", "from": [x + 1, 1, 34], "to": [x + 1, 1, 34], "block": "minecraft:cut_sandstone", "hollow": False},
        {"type": "box", "from": [x - 1, 3, 33], "to": [x + 1, 5, 33], "block": "minecraft:chiseled_sandstone", "hollow": False},   # head
        {"type": "box", "from": [x - 2, 4, 33], "to": [x + 2, 5, 33], "block": "minecraft:blue_glazed_terracotta", "hollow": False},          # nemes headdress
        {"type": "set", "at": [x, 5, 34], "block": "minecraft:yellow_terracotta"},                                                        # uraeus
        {"type": "obelisk", "at": [side * 13, 0, 33], "height": 12, "base": 2, "block": "minecraft:smooth_sandstone", "cap": "minecraft:yellow_terracotta"},
    ]
    for k, xx in enumerate((16, 20, 24, 28)): pieces.append(col(side * xx, 6))   # lotus-capital colonnade along the south wall
    pieces.append({"type": "set", "at": [side * 13, 4, 34], "block": "minecraft:blue_glazed_terracotta"})  # lapis band on the obelisk
w(os.path.join(D, "kingdom_castle", "azmar.json"), {"palette": {
    "minecraft:stone_bricks": "minecraft:cut_sandstone", "minecraft:cobblestone": "minecraft:sandstone", "minecraft:stone": "minecraft:smooth_sandstone",
    "minecraft:stone_brick_wall": "minecraft:sandstone_wall", "minecraft:stone_brick_stairs": "minecraft:sandstone_stairs", "minecraft:stone_brick_slab": "minecraft:sandstone_slab",
    "minecraft:mossy_stone_bricks": "minecraft:chiseled_sandstone",
    "minecraft:spruce_planks": "minecraft:acacia_planks", "minecraft:spruce_log": "minecraft:acacia_log", "minecraft:spruce_slab": "minecraft:acacia_slab",
    "minecraft:spruce_fence": "minecraft:acacia_fence", "minecraft:spruce_trapdoor": "minecraft:acacia_trapdoor", "minecraft:spruce_fence_gate": "minecraft:acacia_fence_gate",
    "minecraft:oak_planks": "minecraft:acacia_planks", "minecraft:oak_log": "minecraft:acacia_log", "minecraft:oak_fence": "minecraft:acacia_fence", "minecraft:oak_stairs": "minecraft:acacia_stairs",
    "minecraft:glass_pane": "minecraft:iron_bars", "minecraft:blue_stained_glass_pane": "minecraft:iron_bars", "minecraft:blue_wool": "minecraft:blue_terracotta",
    "minecraft:dirt_path": "minecraft:smooth_sandstone"}, "pieces": pieces})

# ---- trim / banner pattern / tags / recipes ---------------------------------------------------------------------------
w(os.path.join(D, "banner_pattern", "azmar.json"), {"asset_id": "elsewhere:azmar", "translation_key": "block.elsewhere.banner.azmar"})
w(os.path.join(D, "trim_pattern", "azmar.json"), {"asset_id": "elsewhere:azmar", "decal": False, "description": {"translate": "trim_pattern.elsewhere.azmar"}})
w(os.path.join(D, "tags", "banner_pattern", "pattern_item", "azmar.json"), {"values": ["elsewhere:azmar"]})
w(os.path.join(D, "recipe", "azmar_armor_trim_smithing_template.json"), {"type": "minecraft:crafting_shaped", "key": {"#": "minecraft:diamond", "C": "minecraft:lapis_block", "S": "elsewhere:azmar_armor_trim_smithing_template"},
    "pattern": ["#S#", "#C#", "###"], "result": {"count": 2, "id": "elsewhere:azmar_armor_trim_smithing_template"}})
w(os.path.join(D, "recipe", "azmar_armor_trim_smithing_template_smithing_trim.json"), {"type": "minecraft:smithing_trim", "addition": "#minecraft:trim_materials", "base": "#minecraft:trimmable_armor",
    "pattern": "elsewhere:azmar", "template": "elsewhere:azmar_armor_trim_smithing_template"})
w(os.path.join(D, "tags", "worldgen", "biome", "azmar_realm_biomes.json"), {"values": ["minecraft:desert", "minecraft:badlands", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_savanna"]})
ALLITEMS = ["azmar_dinar", "azmar_seal_sultana", "azmar_seal_wazir", "azmar_seal_falconer", "azmar_seal_amir", "azmar_nemes_of_the_uraeus", "azmar_ibis_crest", "azmar_falconers_gauntlet",
            "azmar_sandrunner_sandals", "azmar_winged_scarab_standard", "azmar_copper_khopesh", "azmar_gilded_khopesh", "azmar_twin_khopesh", "azmar_scarab_spear", "azmar_composite_bow",
            "azmar_fire_pot", "azmar_jackal_helm", "azmar_falcon_helm", "azmar_horned_scarab_crown", "azmar_sunplate_cuirass", "azmar_armor_trim_smithing_template", "azmar_banner_pattern"]
# Test Meter exclusion tag: every new currency/boss item joins it (merge.py unions "values" across modules)
w(os.path.join(RES, "data", "testmeter", "tags", "item", "transmutation_excluded.json"), {"replace": False, "values": [{"id": "elsewhere:" + i, "required": False} for i in ALLITEMS]})
w(os.path.join(D, "tags", "item", "lore_seals.json"), {"replace": False, "values": [{"id": "elsewhere:azmar_seal_" + k, "required": False} for k in SEALS]})
w(os.path.join(D, "tags", "item", "khopesh.json"), {"replace": False, "values": ["elsewhere:azmar_copper_khopesh", "elsewhere:azmar_gilded_khopesh", "elsewhere:azmar_twin_khopesh"]})
w(os.path.join(D, "tags", "item", "azmar_khopesh.json"), {"values": ["elsewhere:azmar_copper_khopesh", "elsewhere:azmar_gilded_khopesh", "elsewhere:azmar_twin_khopesh"]})
w(os.path.join(D, "tags", "item", "azmar_headpieces.json"), {"values": ["elsewhere:azmar_jackal_helm", "elsewhere:azmar_falcon_helm", "elsewhere:azmar_ibis_crest", "elsewhere:azmar_nemes_of_the_uraeus", "elsewhere:azmar_horned_scarab_crown"]})
w(os.path.join(D, "tags", "item", "azmar_repair_gilded.json"), {"values": ["minecraft:gold_ingot"]})
w(os.path.join(D, "tags", "item", "azmar_repair_linen.json"), {"values": ["#minecraft:wool", "minecraft:string"]})
w(os.path.join(D, "tags", "item", "azmar_repair_copper.json"), {"values": ["minecraft:copper_ingot"]})
w(os.path.join(RES, "data", "minecraft", "tags", "item", "trimmable_armor.json"), {"replace": False, "values": ["elsewhere:azmar_jackal_helm", "elsewhere:azmar_falcon_helm", "elsewhere:azmar_ibis_crest", "elsewhere:azmar_nemes_of_the_uraeus", "elsewhere:azmar_horned_scarab_crown", "elsewhere:azmar_sunplate_cuirass", "elsewhere:azmar_sandrunner_sandals"]})
w(os.path.join(RES, "data", "minecraft", "tags", "item", "swords.json"), {"replace": False, "values": ["elsewhere:azmar_copper_khopesh", "elsewhere:azmar_gilded_khopesh", "elsewhere:azmar_twin_khopesh"]})
w(os.path.join(RES, "data", "minecraft", "tags", "item", "head_armor.json"), {"replace": False, "values": ["elsewhere:azmar_jackal_helm", "elsewhere:azmar_falcon_helm", "elsewhere:azmar_ibis_crest", "elsewhere:azmar_nemes_of_the_uraeus", "elsewhere:azmar_horned_scarab_crown"]})
w(os.path.join(RES, "data", "minecraft", "tags", "item", "arrows.json"), {"replace": False, "values": []}) if False else None

# ---- equipment assets (headpieces are REAL equipment, drawn on the head) ---------------------------------------------
for n in ("nemes", "ibis", "falcon", "jackal", "scarab", "sunplate", "sandrunner"):
    w(os.path.join(A, "equipment", f"azmar_{n}.json"), {"layers": {"humanoid": [{"texture": f"elsewhere:azmar_{n}"}]}})

# ---- item definitions and models ------------------------------------------------------------------------------------
def item_def(i, model=None): w(os.path.join(A, "items", i + ".json"), {"model": {"type": "minecraft:model", "model": model or f"elsewhere:item/{i}"}})
def gen_model(i, parent="minecraft:item/generated"): w(os.path.join(A, "models", "item", i + ".json"), {"parent": parent, "textures": {"layer0": f"elsewhere:item/{i}"}})
for i in ALLITEMS:
    if i in ("azmar_composite_bow", "azmar_copper_khopesh", "azmar_gilded_khopesh", "azmar_twin_khopesh"): continue
    item_def(i)
    gen_model(i, "minecraft:item/handheld" if i == "azmar_scarab_spear" else "minecraft:item/generated")
# bow with draw states
bm = lambda s: {"type": "minecraft:model", "model": f"elsewhere:item/azmar_composite_bow{s}"}
w(os.path.join(A, "items", "azmar_composite_bow.json"), {"model": {"type": "minecraft:condition", "property": "minecraft:using_item", "on_false": bm(""),
    "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05, "entries": [
        {"model": bm("_pulling_1"), "threshold": 0.65}, {"model": bm("_pulling_2"), "threshold": 0.9}], "fallback": bm("_pulling_0")}}})
for s in ("", "_pulling_0", "_pulling_1", "_pulling_2"): gen_model("azmar_composite_bow" + s, "minecraft:item/bow" if False else "minecraft:item/generated")

# Khopesh: sprite in the GUI/ground/frame, a real 3D sickle model (arc of rotated cubes) in the hand
def khopesh_model(name, blade_uv, twin=False):
    """Elements in the x-y plane (thickness z). Blade rises then curves toward +x into a hook. Texture 'parts' is a 16x16 swatch sheet."""
    el = []
    def box(x0, y0, x1, y1, z0, z1, uv, rot=None):
        face = {"uv": uv, "texture": "#parts"}
        e = {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": {k: dict(face) for k in ("north", "south", "east", "west", "up", "down")}}
        if rot: e["rotation"] = rot
        el.append(e)
    HANDLE, GUARD, LAP, TQ, BLD, EDGE = [0, 0, 2, 2], [2, 0, 4, 2], [4, 0, 6, 2], [6, 0, 8, 2], blade_uv, [10, 0, 12, 2]
    box(7.2, -1, 8.8, 4.2, 7.3, 8.7, HANDLE)                     # grip
    box(6.6, -2, 9.4, -0.8, 7.0, 9.0, GUARD)                     # pommel
    box(4.6, 4.2, 11.4, 5.4, 7.0, 9.0, GUARD)                    # guard bar
    box(7.0, 4.3, 9.0, 5.3, 6.8, 9.2, LAP)                       # lapis inlay
    box(7.4, 5.3, 8.6, 6.0, 6.4, 9.6, TQ)                        # scarab shell
    box(7.2, 5.4, 8.8, 10.0, 7.45, 8.55, BLD)                    # straight blade
    # curved part: segments along an arc, each rotated about z
    R = 6.5; prev = (8.0, 10.0)
    for k, ang in enumerate((22.5, 45.0, 67.5, 90.0)):
        th = math.radians(ang)
        cx, cy = 8.0 + R * (1 - math.cos(th)), 10.0 + R * math.sin(th)
        wd, ln = 1.6 - 0.15 * k, 2.6
        rot = {"origin": [cx, cy, 8], "axis": "z", "angle": -45 if ang >= 45 else -22.5}
        box(cx - wd / 2, cy - ln / 2, cx + wd / 2, cy + ln / 2, 7.45, 8.55, BLD, rot if ang < 90 else {"origin": [cx, cy, 8], "axis": "z", "angle": -45})
    box(8.6, 6.0, 9.6, 9.6, 7.5, 8.5, EDGE)                      # belly edge (lighter) along the inside of the sickle
    disp = {"thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
            "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]}}
    w(os.path.join(A, "models", "item", name + "_3d.json"), {"textures": {"parts": "elsewhere:item/azmar_khopesh_parts", "particle": "elsewhere:item/azmar_khopesh_parts"}, "elements": el, "display": disp})
    gen_model(name, "minecraft:item/handheld")
    sprite = {"type": "minecraft:model", "model": f"elsewhere:item/{name}"}
    w(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:select", "property": "minecraft:display_context", "cases": [
        {"when": ["gui", "ground", "fixed", "on_shelf"], "model": sprite}], "fallback": {"type": "minecraft:model", "model": f"elsewhere:item/{name}_3d"}}})
khopesh_model("azmar_copper_khopesh", [0, 2, 2, 4])
khopesh_model("azmar_gilded_khopesh", [2, 2, 4, 4])
khopesh_model("azmar_twin_khopesh", [4, 2, 6, 4], True)

# ---- lang ---------------------------------------------------------------------------------------------------------------
L = {}
def lore(i, name, *lines):
    L[f"item.elsewhere.{i}"] = name
    for n, t in enumerate(lines, 1): L[f"item.elsewhere.{i}.desc{n}"] = t
lore("azmar_dinar", "Azmar Dinar", "Gold coin of the Sultanate of Azmar.", "Spent at the Counting-Table; tribute for Standing.")
lore("azmar_seal_sultana", "Seal of the Unsetting Sun", "A Lord Seal of Azmar: the scarab and the cobra.")
lore("azmar_seal_wazir", "Seal of the Ibis Glyphs", "A Lord Seal of Azmar: the ibis of wisdom and writing.")
lore("azmar_seal_falconer", "Seal of the Falcons", "A Lord Seal of Azmar: the falcon of sight.")
lore("azmar_seal_amir", "Seal of the Horned Scarab", "A Lord Seal of Azmar: the horned scarab of spears.")
lore("azmar_nemes_of_the_uraeus", "Nemes of the Uraeus", "Gold-and-lapis headcloth with a cobra crown.", "Uraeus Ward: in open sky and daylight, 10% less damage taken, +1% per Lord Seal (max 12%).", "Strong by day outdoors, weak otherwise.")
lore("azmar_ibis_crest", "Ibis Crest", "The Grand Wazir's crest of wisdom.", "Calling: +25% experience from villager trades.", "A real downgrade for fighting.")
lore("azmar_falconers_gauntlet", "Falconer's Gauntlet", "Right-click: a falcon dives at the target in view (32 blocks): Glowing 8 s and 3 damage.", "Cooldown 20 s. A scouting tool, not a weapon.")
lore("azmar_sandrunner_sandals", "Sandrunner Sandals", "+10% speed on sand, red sand and terracotta.", "Fall damage reduced by 2.")
lore("azmar_winged_scarab_standard", "Winged-Scarab Standard", "Plant it: Absorption I within 16 blocks, refreshed every 10 s.", "One standard per player.")
lore("azmar_copper_khopesh", "Copper Khopesh", "A sickle-curved blade with a scarab-shell guard.", "Hooks a raised shield down.")
lore("azmar_gilded_khopesh", "Gilded Khopesh", "An officer's khopesh, gilt and lapis-inlaid.", "Hooks a raised shield down.")
lore("azmar_twin_khopesh", "Twinblade Khopesh", "Two curved blades joined at a scarab.", "Hooks a raised shield down.")
lore("azmar_scarab_spear", "Scarab-Head Spear", "A spear tipped with a gilded scarab.")
lore("azmar_composite_bow", "Composite Bow", "A recurved bow of horn, sinew and wood.")
lore("azmar_fire_pot", "Faience Fire Pot", "Throw it: a short fire on the ground that never spreads and never burns blocks.", "6 damage on impact, then 2 per second for 3 s.")
lore("azmar_jackal_helm", "Jackal Helm", "The animal-headed guard of the Sultana.", "Chainmail-helmet values.")
lore("azmar_falcon_helm", "Falcon Helm", "A turquoise falcon head: sight and the scout.", "Leather-cap values.")
lore("azmar_horned_scarab_crown", "Horned-Scarab War-Crown", "The Amir's war-crown.", "Iron-helmet values.")
lore("azmar_sunplate_cuirass", "Sunplate Cuirass", "The Hajib's linen-and-gold cuirass.", "Light armor, iron-chestplate values.")
L["item.elsewhere.azmar_armor_trim_smithing_template"] = "Azmar Armor Trim"
L["item.elsewhere.azmar_armor_trim_smithing_template.new"] = "Azmar Armor Trim"
L["item.elsewhere.azmar_banner_pattern"] = "Azmar Banner Pattern"
L["item.elsewhere.azmar_banner_pattern.new"] = "Azmar Banner Pattern"
L["item.elsewhere.azmar_banner_pattern.desc"] = "Scarab and Sun"
L["trim_pattern.elsewhere.azmar"] = "Azmar Armor Trim"
L["block.elsewhere.banner.azmar"] = "Scarab and Sun"
for c in ("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"):
    L[f"block.elsewhere.banner.azmar.{c}"] = f"{c.replace('_', ' ').title()} Scarab and Sun"
L["faction.elsewhere.azmar"] = "Sultanate of Azmar"
L["itemGroup.elsewhere.azmar"] = "Elsewhere: Azmar"
for k, n in (("sultana", "Sultana Nahira Zahr-Qadir"), ("wazir", "Grand Wazir Idris Halvane"), ("falconer", "Royal Falconer Leyla Dunmar"), ("amir", "Amir Tariq Vashti")):
    L[f"entity.elsewhere.azmar_{k}"] = n
for k, n in (("falcon", "Falcon of the Falconer"), ("war_camel", "Azmar War Camel"), ("pack_camel", "Azmar Pack Camel"), ("hired_porter", "Hired Porter"),
             ("jackal_guard", "Jackal Guard"), ("sand_dancer", "Sand-Dancer"), ("naffatun", "Naffatun"), ("spearguard", "Spearguard"), ("sunguard", "Sun-Guard"),
             ("camel_lancer", "Camel Lancer"), ("bowman", "Bowman"), ("arbalester", "Arbalester"), ("jemadar", "Jemadar"), ("qaid", "Qaid"),
             ("scarab_bearer", "Scarab-Bearer"), ("crier", "Crier"), ("physician", "Physician"), ("page", "Page"), ("pikeman", "Azmar Pikeman")):
    L[f"entity.elsewhere.azmar_{k}"] = n
L["entity.elsewhere.azmar_falcon"] = "Falcon of the Falconer"
for k, n in (("hajib", "Hajib Rustam Qalat"), ("ustad", "Ustad Zayan Sorel")): L[f"entity.elsewhere.azmar_{k}"] = n
for k, n in (("sultana", "Sultana"), ("wazir", "Grand Wazir"), ("falconer", "Royal Falconer"), ("amir", "Amir")): L[f"azmar.title.{k}"] = n
L.update({
 "message.elsewhere.azmar_hook": "Your shield is hooked down!", "message.elsewhere.azmar_standard_planted": "The Winged-Scarab Standard is planted.",
 "message.elsewhere.azmar_gauntlet_cooldown": "The falcon is not ready yet.", "message.elsewhere.azmar_gauntlet_nothing": "Nothing in view for the falcon.",
 "message.elsewhere.azmar_read": "The court reads you.", "azmar.signal.screen": "Screen!", "azmar.signal.ring": "Ring!", "azmar.signal.open": "Open!",
 "azmar.signal.withdraw": "Withdraw!", "azmar.signal.hook": "Hook!", "azmar.flare.amber": "Amber flare: Screen", "azmar.flare.blue": "Blue flare: Ring",
 "azmar.flare.red": "Red flare: Charge", "azmar.flare.white": "White flare: Withdraw",
 "azmar.boss.sultana.tagline": "The Unsetting Sun, the Scarab-Queen", "azmar.boss.wazir.tagline": "Keeper of the Ibis Glyphs", "azmar.boss.falconer.tagline": "Mistress of the Falcons", "azmar.boss.amir.tagline": "Amir of Spears, the Horned Scarab",
 "subtitles.elsewhere.azmar.horn_short": "Azmar horn", "subtitles.elsewhere.azmar.horn_long": "Azmar horn sounds long", "subtitles.elsewhere.azmar.sistrum": "Sistrum rattles", "subtitles.elsewhere.azmar.bell": "Bell rings"})
w(os.path.join(A, "lang", "en_us.json"), dict(sorted(L.items())))   # merged into lang/en_us.json by tools/merge.py (see INTEGRATION)

# ---- integration payloads (not shipped in the jar) ------------------------------------------------------------------------
w(os.path.join(EXTRA, "boss_music_azmar.json"), {"bosses": {
    "elsewhere:azmar_sultana": "elsewhere:boss_music.theme.azmar_sultana", "elsewhere:azmar_wazir": "elsewhere:boss_music.theme.azmar_wazir",
    "elsewhere:azmar_falconer": "elsewhere:boss_music.theme.azmar_falconer", "elsewhere:azmar_amir": "elsewhere:boss_music.theme.azmar_amir"},
    "realm": "elsewhere:boss_music.theme.azmar_realm", "_note": "slot ids only; tracks come later (music lane). Azmar: oud, ney, darbuka."})
JEI = {
 "azmar_dinar": ["Azmar Dinar: the Sultanate's gold coin. Dropped by Azmar soldiers (1-3), officers (4-8), Lords (16-32) and the Sultana (80-96).", "How to get: Azmar drops; spend it at the Counting-Table in a liberated Azmar village."],
 "azmar_copper_khopesh": ["Copper Khopesh: copper-sword stats, a sickle blade that hooks a raised shield down (3 s, once per 6 s).", "How to get: Azmar Spearguards and Sun-Guards drop it rarely (4%); the Counting-Table sells it."],
 "azmar_gilded_khopesh": ["Gilded Khopesh: iron-sword stats with the shield hook.", "How to get: dropped by Azmar officers (Jemadar, Qaid)."],
 "azmar_twin_khopesh": ["Twinblade Khopesh: the Sand-Dancers' paired blades; iron stats, quick, with the shield hook.", "How to get: Ustad Zayan Sorel drops it 20% of the time; Sand-Dancers 4%."],
 "azmar_scarab_spear": ["Scarab-Head Spear: a diamond-spear-class lance with a gilded scarab head.", "How to get: Camel Lancers and Pikemen (4%); the Counting-Table."],
 "azmar_composite_bow": ["Composite Bow: a recurved bow, vanilla bow stats.", "How to get: Azmar Bowmen (4%); the Counting-Table."],
 "azmar_fire_pot": ["Faience Fire Pot: throw for 6 damage and a 3x3 ground fire for 3 s (2 per second). It never spreads and never burns blocks.", "How to get: Naffatun drop them; the Counting-Table sells 4 for 20 Dinars."],
 "azmar_jackal_helm": ["Jackal Helm: chainmail-helmet values, rendered on the head (not painted on the skin).", "How to get: Jackal Guards of the Sultana drop it 2% of the time."],
 "azmar_falcon_helm": ["Falcon Helm: leather-cap values.", "How to get: Royal Falconer Leyla Dunmar's guard and the Falconer drop it."],
 "azmar_horned_scarab_crown": ["Horned-Scarab War-Crown: iron-helmet values.", "How to get: dropped by Amir Tariq Vashti (50%)."],
 "azmar_sunplate_cuirass": ["Sunplate Cuirass: iron-chestplate values in linen and gold.", "How to get: Hajib Rustam Qalat drops it 20% of the time."],
 "azmar_nemes_of_the_uraeus": ["Nemes of the Uraeus: helmet 3/2/0.1 with the Uraeus Ward (10% less damage by day in the open, +1% per Lord Seal up to 12%).", "How to get: the Sultana Nahira Zahr-Qadir (DropRule)."],
 "azmar_ibis_crest": ["Ibis Crest: a scribe's helmet, +25% experience from villager trades.", "How to get: Grand Wazir Idris Halvane (DropRule)."],
 "azmar_falconers_gauntlet": ["Falconer's Gauntlet: right-click to send a falcon at the target in view: Glowing 8 s and 3 damage, 20 s cooldown.", "How to get: Royal Falconer Leyla Dunmar (DropRule)."],
 "azmar_sandrunner_sandals": ["Sandrunner Sandals: boots 3/2/0, +10% speed on sand, red sand and terracotta, fall damage -2.", "How to get: Amir Tariq Vashti (DropRule)."],
 "azmar_winged_scarab_standard": ["Winged-Scarab Standard: plant it for Absorption I within 16 blocks (refreshed every 10 s).", "How to get: the Sultana drops it half of the time."],
 "azmar_armor_trim_smithing_template": ["Armor trim template: applies the Azmar scarab-wing trim to any trimmable armor with a trim material.", "How to get: the Counting-Table; copy it with 7 Diamonds and a Lapis Block (makes 2)."],
 "azmar_banner_pattern": ["Banner pattern: the winged scarab holding a sun disc.", "How to get: the Counting-Table."],
}
for k in SEALS: JEI["azmar_seal_" + k] = [f"Lord Seal of Azmar ({k}): a trophy that boosts the Nemes of the Uraeus and counts for the Counting-Table.", "How to get: dropped by that boss of Azmar."]
w(os.path.join(EXTRA, "jei_info_azmar.json"), {"elsewhere:" + k: v for k, v in JEI.items()})
print("data done")
