"""Writes src/main/resources/elsewhere_gear3/catalogue.json from the GEAR.md section A5 table values (every number below is copied from
the GEAR.md tables; check_gear_md.py re-checks them against the tables). Run: ROOT/_tools/venv/bin/python -I gear3/tools/build_catalogue.py
Only items finished and tested ship; a (B) mechanic from GEAR.md is not built, so its text is not in the lore."""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(HERE), "src", "main", "resources", "elsewhere_gear3", "catalogue.json")
ITEMS = []
SLOTS = ["helmet", "chestplate", "leggings", "boots"]
TEMPLATE = "elsewhere:gear3_forging_template"


def smith(template, base, addition, rid=None):
    return {"type": "minecraft:smithing_transform", "template": template, "base": base, "addition": addition}


def armor_set(set_name, ids, names, armor_vals, tough, kb, rarity, lore, calling, attrs_by_slot=None, fireproof=False, recipe_fn=None, loot=None, repair="minecraft:diamond",
              mult=37, extra_attrs=None, sound="netherite"):
    """ids/names/armor_vals are parallel lists over the slots present; slots is a list of the slot names those pieces use."""
    for i, (iid, nm, a, slot) in enumerate(zip(ids, names, armor_vals, set_name[1])):
        stats = {"armor": a, "toughness": tough, "kb": kb, "durability_mult": mult, "enchantability": 15, "repair": repair, "sound": sound}
        if fireproof:
            stats["fireproof"] = 1
        it = {"id": iid, "name": nm, "kind": "armor", "set": set_name[0], "slot": slot, "rarity": rarity, "stats": stats, "lore": lore(slot) if callable(lore) else lore,
              "calling": calling, "attrs": list((attrs_by_slot or {}).get(slot, [])) + list(extra_attrs or [])}
        if recipe_fn:
            it["recipe"] = recipe_fn(slot, iid)
        if loot:
            it["loot"] = loot
        ITEMS.append(it)


def attr(name, amount, op="add"):
    return {"attr": name, "amount": amount, "op": op, "slot": "armor"}


def pct(x):
    return f"{x * 100:g}%"


def vanilla(slot, tier):
    return f"minecraft:{tier}_{slot}"


# ---------------------------------------------------------------- Vanguard
# Galeplate (Duelist, G4): smithing: forging template + netherite piece + 2 Zephyrite (one in the smithing slot)
armor_set(("galeplate", SLOTS), ["galeplate_helmet", "galeplate_chestplate", "galeplate_leggings", "galeplate_boots"],
          ["Galeplate Helmet", "Galeplate Chestplate", "Galeplate Leggings", "Galeplate Boots"], [3, 8, 6, 3], 2.5, 0.05, "epic",
          ["Duelist armor, Vanguard Leaning", "+4% dodge chance per piece (max 25%)"], ["vanguard"], extra_attrs=[attr("elsewhere:dodge_chance", 0.04)], fireproof=True,
          repair="elsewhere:zephyrite", recipe_fn=lambda s, i: smith(TEMPLATE, vanilla(s, "netherite"), "elsewhere:zephyrite"),
          loot=[{"table": "chests/breeze_temple_vault", "chance": 0.06}])

# Hearthguard (Tank, G4): helm / greaves / boots 3/6/3, toughness 3.5, KB 0.15, -3% speed each
armor_set(("hrafnholt_hearthguard", ["helmet", "leggings", "boots"]), ["hrafnholt_hearthguard_helm", "hrafnholt_hearthguard_greaves", "hrafnholt_hearthguard_boots"],
          ["Hearthguard Helm", "Hearthguard Greaves", "Hearthguard Boots"], [3, 6, 3], 3.5, 0.15, "epic",
          ["Tank armor of Hrafnholt, Vanguard Leaning", "!-3% movement speed per piece"], ["vanguard"], extra_attrs=[attr("minecraft:movement_speed", -0.03, "mul_base")],
          repair="elsewhere:hacksilver", recipe_fn=lambda s, i: smith("elsewhere:hrafnholt_arm_ring", vanilla(s, "diamond"), "elsewhere:hacksilver"),
          loot=[{"table": "chests/hrafnholt_courier", "chance": 0.06}], mult=37, sound="iron")

# Ulfhednar (Berserker, G4): mask + breeches 3/6, toughness 3, no knockback resistance
armor_set(("hrafnholt_ulfhednar", ["helmet", "leggings"]), ["hrafnholt_ulfhednar_mask", "hrafnholt_ulfhednar_breeches"], ["Ulfhednar Mask", "Ulfhednar Breeches"], [3, 6], 3.0, 0.0, "epic",
          ["Berserker armor of Hrafnholt, Vanguard Leaning", "No knockback resistance"], ["vanguard"],
          repair="elsewhere:hacksilver", recipe_fn=lambda s, i: smith("elsewhere:hrafnholt_arm_ring", vanilla(s, "diamond"), "elsewhere:hrafnholt_wolfpelt_blanket"),
          loot=[{"table": "chests/hrafnholt_courier", "chance": 0.06}], sound="leather")

# Glyphmail pair (Spellblade V+C, G4): hood + hauberk 3/8, toughness 2.5, KB 0.1, max_glimmer +4 each
armor_set(("azmar_glyphmail", ["helmet", "chestplate"]), ["azmar_glyphmail_hood", "azmar_glyphmail_hauberk"], ["Glyphmail Hood", "Glyphmail Hauberk"], [3, 8], 2.5, 0.1, "epic",
          ["Spellblade pair of Azmar, Vanguard and Caster Leaning", "+4 max Glimmer per piece"], ["vanguard", "caster"], extra_attrs=[attr("elsewhere:max_glimmer", 4)],
          repair="elsewhere:azmar_dinar", recipe_fn=lambda s, i: smith("elsewhere:azmar_seal_wazir", vanilla(s, "diamond"), "elsewhere:azmar_dinar"),
          loot=[{"table": "chests/azmar_courier", "chance": 0.06}], sound="chain")

# Outrider pair (Skirmisher V+M, G4): chaps + boots 6/3, toughness 2.5, KB 0.05, +3% speed each
armor_set(("aldermark_outrider", ["leggings", "boots"]), ["aldermark_outrider_chaps", "aldermark_outrider_boots"], ["Outrider Chaps", "Outrider Boots"], [6, 3], 2.5, 0.05, "epic",
          ["Skirmisher pair of Aldermark, Vanguard and Marksman Leaning", "+3% movement speed per piece"], ["vanguard", "marksman"], extra_attrs=[attr("minecraft:movement_speed", 0.03, "mul_base")],
          repair="elsewhere:aldermark_crown", recipe_fn=lambda s, i: smith("elsewhere:aldermark_crown", vanilla(s, "diamond"), "minecraft:gold_ingot"),
          loot=[{"table": "chests/kingdom_armory", "chance": 0.05}], sound="leather")

# ---------------------------------------------------------------- Marksman
armor_set(("ridgewatch", ["helmet", "chestplate", "leggings", "boots"]), ["ridgewatch_hood", "ridgewatch_coat", "ridgewatch_leggings", "ridgewatch_boots"],
          ["Ridgewatch Hood", "Ridgewatch Coat", "Ridgewatch Leggings", "Ridgewatch Boots"], [3, 7, 6, 3], 2.5, 0.0, "epic",
          lambda s: ["Sniper armor, Marksman Leaning"] + (["+1 projectile pierce"] if s == "chestplate" else []), ["marksman"], attrs_by_slot={"chestplate": [attr("elsewhere:projectile_pierce", 1)]},
          fireproof=True, repair="elsewhere:skyiron_ingot", recipe_fn=lambda s, i: smith(TEMPLATE, vanilla(s, "netherite"), "elsewhere:skyiron_ingot"),
          loot=[{"table": "chests/climbers_cairn", "chance": 0.06}])

armor_set(("glasswaste_starsight", ["helmet", "chestplate"]), ["glasswaste_starsight_visor", "glasswaste_prism_mantle"], ["Starsight Visor", "Prism Mantle"], [3, 8], 2.5, 0.0, "epic",
          lambda s: ["Arcane archer pair, Marksman and Caster Leaning", "+4 max Glimmer"] + (["+1 projectile pierce"] if s == "chestplate" else []), ["marksman", "caster"],
          extra_attrs=[attr("elsewhere:max_glimmer", 4)], attrs_by_slot={"chestplate": [attr("elsewhere:projectile_pierce", 1)]},
          repair="elsewhere:sunglass_shard", recipe_fn=lambda s, i: smith("elsewhere:sunglass_shard", vanilla(s, "diamond"), "elsewhere:sunglass_shard"),
          loot=[{"table": "chests/sunwright_ziggurat", "chance": 0.06}])

armor_set(("azmar_falconer", ["leggings"]), ["azmar_falconer_kilt"], ["Falconer's Kilt"], [6], 2.5, 0.05, "epic",
          ["Falconer pair (with Sandrunner Sandals), Marksman and Summoner Leaning", "+5% minion damage"], ["marksman", "summoner"], extra_attrs=[attr("elsewhere:minion_damage", 0.05)],
          repair="elsewhere:azmar_dinar", recipe_fn=lambda s, i: smith("elsewhere:azmar_seal_falconer", vanilla(s, "diamond"), "elsewhere:azmar_dinar"),
          loot=[{"table": "chests/azmar_courier", "chance": 0.06}], sound="leather")

# ---------------------------------------------------------------- Caster
armor_set(("sunscribe", ["helmet", "chestplate", "leggings", "boots"]), ["sunscribe_hood", "sunscribe_robe", "sunscribe_leggings", "sunscribe_boots"],
          ["Sunscribe Hood", "Sunscribe Robe", "Sunscribe Leggings", "Sunscribe Boots"], [3, 8, 6, 3], 2.5, 0.0, "epic",
          ["Artillery vestments, Caster Leaning", "+6% spell power and +3 max Glimmer per piece"], ["caster"],
          extra_attrs=[attr("elsewhere:spell_power", 0.06), attr("elsewhere:max_glimmer", 3)], fireproof=True, repair="elsewhere:sunwright_ingot",
          recipe_fn=lambda s, i: smith(TEMPLATE, vanilla(s, "netherite"), "elsewhere:sunwright_ingot"), loot=[{"table": "chests/sunwright_ziggurat", "chance": 0.06}])

armor_set(("mirewick_lamplighter", ["helmet", "chestplate"]), ["mirewick_lamplighter_hood", "mirewick_lamplighter_coat"], ["Lamplighter Hood", "Lamplighter Coat"], [3, 8], 2.5, 0.0, "epic",
          ["Ritualist pair of Mirewick, Caster and Summoner Leaning", "+5% spell power and +5% minion damage per piece"], ["caster", "summoner"],
          extra_attrs=[attr("elsewhere:spell_power", 0.05), attr("elsewhere:minion_damage", 0.05)], repair="elsewhere:mirewick_scrip",
          recipe_fn=lambda s, i: smith("elsewhere:mirewick_seal_tolliver", vanilla(s, "diamond"), "elsewhere:mirewick_scrip"),
          loot=[{"table": "chests/mirewick_courier", "chance": 0.06}], sound="leather")

# ---------------------------------------------------------------- Summoner
armor_set(("elderbark", ["chestplate", "leggings", "boots"]), ["elderbark_mantle", "elderbark_leggings", "elderbark_boots"], ["Elderbark Mantle", "Elderbark Leggings", "Elderbark Boots"], [8, 6, 3], 3.0, 0.05, "epic",
          ["Elite regalia (with the Elder Bark Crown), Summoner Leaning", "+5% minion damage per piece"], ["summoner"], extra_attrs=[attr("elsewhere:minion_damage", 0.05)],
          fireproof=True, repair="elsewhere:pale_bark", recipe_fn=lambda s, i: smith(TEMPLATE, vanilla(s, "netherite"), "elsewhere:pale_bark"),
          loot=[{"table": "chests/hanging_undercity", "chance": 0.06}])

armor_set(("court_marshals", ["helmet", "chestplate", "leggings"]), ["court_marshals_helm", "court_marshals_tabard", "court_marshals_tassets"], ["Marshal's Helm", "Marshal's Tabard", "Marshal's Tassets"], [3, 8, 6], 3.0, 0.1, "epic",
          ["Commander panoply (with the Warmarshal's Spurs), Summoner Leaning", "+5% minion damage per piece"], ["summoner"], extra_attrs=[attr("elsewhere:minion_damage", 0.05)],
          repair="elsewhere:aldermark_crown", recipe_fn=lambda s, i: smith("elsewhere:aldermark_crown", vanilla(s, "diamond"), "minecraft:gold_ingot"),
          loot=[{"table": "chests/kingdom_armory", "chance": 0.06}], sound="chain")

armor_set(("hrafnholt_hird", ["leggings", "boots"]), ["hrafnholt_hird_leggings", "hrafnholt_hird_boots"], ["Hird Leggings", "Hird Boots"], [6, 3], 3.0, 0.05, "epic",
          ["Warband pair of Hrafnholt, Vanguard and Summoner Leaning"], ["vanguard", "summoner"],
          repair="elsewhere:hacksilver", recipe_fn=lambda s, i: smith("elsewhere:hrafnholt_arm_ring", vanilla(s, "diamond"), "elsewhere:hacksilver"),
          loot=[{"table": "chests/hrafnholt_courier", "chance": 0.06}], sound="iron")

# ---------------------------------------------------------------- weapons
def weapon(wid, name, kind, damage, speed, rarity, lore, calling, recipe, loot, repair="minecraft:diamond", attrs=None, durability=2031, extra=None):
    st = {"damage": damage, "speed": speed, "durability": durability, "tier": "netherite", "enchantability": 15, "repair": repair, "mining_speed": 6.0}
    st.update(extra or {})
    it = {"id": wid, "name": name, "kind": kind, "rarity": rarity, "stats": st, "lore": lore, "calling": calling, "recipe": recipe, "loot": loot}
    if attrs:
        it["attrs"] = attrs
    ITEMS.append(it)


weapon("mirewick_lanternfang_estoc", "Lanternfang Estoc", "sword", 6, 1.8, "epic", ["Duelist estoc of Mirewick, Vanguard weapon", "Reach +0.5"], ["vanguard"],
       smith("elsewhere:mirewick_scrip", "minecraft:diamond_sword", "elsewhere:mirewick_seal_tolliver"), [{"table": "chests/mirewick_courier", "chance": 0.08}],
       repair="elsewhere:mirewick_scrip", attrs=[{"attr": "minecraft:entity_interaction_range", "amount": 0.5, "slot": "mainhand"}])
weapon("bellbreaker_maul", "Bellbreaker Maul", "sword", 10, 0.9, "epic", ["Tank maul, Vanguard weapon"], ["vanguard"],
       smith("elsewhere:cantors_bell_shard", "minecraft:diamond_sword", "elsewhere:resonant_shard"), [{"table": "chests/silent_monastery", "chance": 0.08}], repair="elsewhere:resonant_shard")
weapon("hrafnholt_huginns_bite", "Huginn's Bite", "axe", 12, 1.0, "epic", ["Berserker war-axe of Hrafnholt, Vanguard weapon", "Disables shields for 5 s"], ["vanguard"],
       smith("elsewhere:hrafnholt_seal_jarl", "minecraft:diamond_axe", "elsewhere:hacksilver"), [{"table": "chests/hrafnholt_courier", "chance": 0.08}], repair="elsewhere:hacksilver")
weapon("hrafnholt_runeseax", "Runeseax", "sword", 7, 1.7, "epic", ["Spellblade seax of Hrafnholt, Vanguard weapon"], ["vanguard"],
       smith("elsewhere:hrafnholt_seal_skald", "minecraft:diamond_sword", "elsewhere:hacksilver"), [{"table": "chests/hrafnholt_courier", "chance": 0.08}], repair="elsewhere:hacksilver")

# ---------------------------------------------------------------- shields
ITEMS.append({"id": "aldermark_parrying_buckler", "name": "Parrying Buckler", "kind": "shield", "rarity": "rare",
              "stats": {"durability": 448, "shield_delay": 0.1, "disable_scale": 0.6, "block": 0.9, "melee_only": 1, "repair": "minecraft:oak_planks"},
              "lore": ["Duelist buckler of Aldermark", "Raises in 0.1 s and blocks 90% of melee; arrows pass through", "Axes disable it for only 3 s"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["PIP", "PSP", " P "], "key": {"P": "minecraft:oak_planks", "I": "minecraft:iron_ingot", "S": "minecraft:shield"}},
              "loot": [{"table": "chests/kingdom_armory", "chance": 0.08}]})
ITEMS.append({"id": "hrafnholt_skjaldborg_shield", "name": "Skjaldborg Round-Shield", "kind": "shield", "rarity": "epic",
              "stats": {"durability": 672, "shield_delay": 0.25, "disable_scale": 1.0, "block": 1.0, "repair": "elsewhere:hacksilver"},
              "lore": ["Tank shield of Hrafnholt", "Blocks like a vanilla shield, with 672 durability"],
              "recipe": smith("elsewhere:hrafnholt_arm_ring", "minecraft:shield", "elsewhere:hacksilver"),
              "loot": [{"table": "chests/hrafnholt_courier", "chance": 0.08}]})

# ---------------------------------------------------------------- shared
ITEMS.append({"id": "gear3_forging_template", "name": "Elsewhere Forging Template", "kind": "material", "rarity": "epic", "stats": {"stack": 64},
              "lore": ["Smithing Template (Elsewhere)", "Netherite armor piece + a set material: a G4 set piece"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": ["DND", "DTD", "DDD"],
                         "key": {"D": "minecraft:diamond", "N": "minecraft:netherite_scrap", "T": "elsewhere:gear3_forging_template"}, "result": {"id": "elsewhere:gear3_forging_template", "count": 2}},
              "loot": [{"table": "chests/sunwright_ziggurat", "chance": 0.5}, {"table": "chests/tidelock_vault", "chance": 0.5}, {"table": "chests/clan_hold", "chance": 0.5},
                       {"table": "chests/hanging_undercity", "chance": 0.5}, {"table": "chests/breeze_temple_vault", "chance": 0.5}],
              "jei": "How to get: about half of the Sunwright Ziggurat, Tidelock Vault, Clan Hold, Hanging Undercity and Breeze Temple chests hold one. Copy it with 7 diamonds and a netherite scrap."})
ITEMS.append({"id": "aldermark_masons_chisel", "name": "Mason's Chisel", "kind": "plain", "rarity": "uncommon", "mechanic": "chisel", "stats": {"durability": 512},
              "lore": ["Aldermark stonework tool", "Use on stone, bricks, deepslate, sandstone, blackstone or tuff: plain, bricks, chiseled, cracked, plain"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": [" I", "S "], "key": {"I": "minecraft:iron_ingot", "S": "minecraft:stick"}},
              "loot": [{"table": "chests/kingdom_market_stall", "chance": 0.25}]})

# ---------------------------------------------------------------- B-list items that fit without a new entity
def shaped(pattern, key, cat="equipment"):
    return {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern, "key": key}


WHIP_LORE = "Tags its target like the Gale Ribbon: your minions switch to it"
for wid, name, dmg, spd, reach, dur, rar, lore0, rec, loot, repair in [
    ("thornlash", "Thornlash", 4, 1.4, 2, 700, "uncommon", "Swarm whip", shaped(["  R", " RT", "S  "], {"R": "elsewhere:haze_resin", "T": "elsewhere:trichome_quartz", "S": "minecraft:stick"}),
     [{"table": "chests/great_hotbox", "chance": 0.08}], "elsewhere:haze_resin"),
    ("tidechain_lash", "Tidechain Lash", 5, 1.3, 3, 900, "rare", "Commander whip", shaped(["VPV", "VPV", " S "], {"V": "elsewhere:verdigris_gear", "P": "elsewhere:scraped_patina", "S": "minecraft:stick"}),
     [{"table": "entities/galvanox", "chance": 0.3}], "elsewhere:verdigris_gear"),
    ("azmar_sunscourge", "Sunscourge", 6, 1.2, 2.5, 1200, "epic", "Commander whip of Azmar", smith("elsewhere:azmar_dinar", "minecraft:diamond_sword", "elsewhere:azmar_seal_amir"),
     [{"table": "chests/azmar_courier", "chance": 0.08}], "elsewhere:azmar_dinar"),
    ("echo_chord", "Echo Chord", 7, 1.4, 3.5, 1600, "epic", "Commander whip", smith("elsewhere:resonant_shard", "minecraft:diamond_sword", "elsewhere:echo_alloy"),
     [{"table": "chests/silent_monastery", "chance": 0.08}], "elsewhere:echo_alloy"),
]:
    ITEMS.append({"id": wid, "name": name, "kind": "sword", "rarity": rar, "mechanic": "whip",
                  "stats": {"damage": dmg, "speed": spd, "durability": dur, "tier": "diamond", "repair": repair, "enchantability": 15},
                  "attrs": [{"attr": "minecraft:entity_interaction_range", "amount": reach, "slot": "mainhand"}],
                  "lore": [lore0, f"Reach +{reach:g}", WHIP_LORE], "recipe": rec, "loot": loot})
ITEMS.append({"id": "mirewick_lantern_crook", "name": "Lantern Crook", "kind": "sword", "rarity": "rare", "mechanic": "crook",
              "stats": {"damage": 6, "speed": 1.6, "durability": 900, "tier": "diamond", "repair": "elsewhere:mirewick_scrip", "enchantability": 15},
              "attrs": [{"attr": "minecraft:entity_interaction_range", "amount": 1.5, "slot": "mainhand"}],
              "lore": ["Commander crook of Mirewick", "Reach +1.5", "Gives the Herding Crook's orders"],
              "recipe": shaped([" SS", " T ", " T "], {"S": "elsewhere:mirewick_scrip", "T": "minecraft:stick"}), "loot": [{"table": "chests/mirewick_courier", "chance": 0.08}]})
ITEMS.append({"id": "hrafnholt_mead_horn", "name": "Mead-Horn of the Hall", "kind": "plain", "rarity": "uncommon", "mechanic": "mead_horn", "stats": {"stack": 1},
              "lore": ["Berserker's horn of Hrafnholt", "Drink: heal 6 over 3 s, 30 s cooldown"],
              "recipe": shaped(["HBH", " H "], {"H": "elsewhere:hacksilver", "B": "minecraft:honey_bottle"}, "misc"), "loot": [{"table": "chests/hrafnholt_courier", "chance": 0.08}]})
ITEMS.append({"id": "rangefinder_lens", "name": "Rangefinder Lens", "kind": "trinket", "rarity": "rare", "stats": {},
              "lore": ["Sniper trinket (off hand or hotbar)", "Arrows deal +1% damage per block beyond 16 (max +20%)"],
              "recipe": shaped(["SSS", "SGS", "SSS"], {"S": "elsewhere:sunglass_shard", "G": "minecraft:spyglass"}, "misc"), "loot": [{"table": "chests/sunwright_ziggurat", "chance": 0.08}]})

def trinket(tid, name, rarity, lore, rec, loot):
    ITEMS.append({"id": tid, "name": name, "kind": "trinket", "rarity": rarity, "stats": {}, "lore": lore, "recipe": rec, "loot": loot})


trinket("aldermark_oath_ring", "Oath Ring", "rare", ["Vanguard trinket of Aldermark (off hand or hotbar)", "While holding a Vanguard weapon: +1 armor toughness and +1 kill heal"],
        shaped([" G ", "GDG", " G "], {"G": "minecraft:gold_ingot", "D": "minecraft:diamond"}, "misc"), [{"table": "chests/kingdom_armory", "chance": 0.08}])
trinket("aldermark_quickdraw_quiver", "Quickdraw Quiver", "rare", ["Skirmisher trinket of Aldermark (off hand or hotbar)", "Bow and crossbow draw time -15%"],
        shaped(["LSL", "LAL", "LLL"], {"L": "minecraft:leather", "S": "minecraft:string", "A": "minecraft:arrow"}, "misc"), [{"table": "chests/kingdom_armory", "chance": 0.08}])
trinket("mirewick_fowlers_bandolier", "Fowler's Bandolier", "rare", ["Trapper trinket of Mirewick (off hand or hotbar)", "Tipped and spectral arrows are not used up 40% of the time"],
        shaped(["LSL", "ASA", "LLL"], {"L": "minecraft:leather", "S": "minecraft:string", "A": "minecraft:spectral_arrow"}, "misc"), [{"table": "chests/mirewick_courier", "chance": 0.08}])
trinket("hush_binding_sigil", "Binding Sigil", "epic", ["Control trinket (off hand or hotbar)", "Slowness, Weakness, Blindness, Mining Fatigue, Nausea, Darkness and Levitation you apply last 30% longer on non-bosses"],
        shaped(["RER", "ECE", "RER"], {"R": "elsewhere:resonant_shard", "E": "minecraft:ender_pearl", "C": "minecraft:amethyst_shard"}, "misc"), [{"table": "chests/silent_monastery", "chance": 0.08}])

def spell(sid, name, rarity, mech, dur, lore, rec, loot):
    ITEMS.append({"id": sid, "name": name, "kind": "plain", "rarity": rarity, "mechanic": mech, "stats": {"durability": dur}, "lore": lore, "recipe": rec, "loot": loot})


spell("amber_grimoire", "Amber Grimoire", "rare", "amber_grimoire", 400, ["Control grimoire (6 Glimmer, 1 s)", "20-block bolt: 4 damage; non-bosses are amber-locked (Slowness IV and Weakness II, 3 s)", "Bosses: Slowness I for 1 s"],
      {"type": "minecraft:crafting_shapeless", "category": "equipment", "ingredients": ["minecraft:book", "elsewhere:amber", "elsewhere:amber", "elsewhere:amber", "elsewhere:amber"]},
      [{"table": "entities/mother_creak", "chance": 0.3}])
spell("mirewick_almoners_chalice", "Almoner's Chalice", "rare", "chalice", 300, ["Support chalice of Mirewick (8 Glimmer, 2 s)", "Heal splash where you look: 4 HP to players and your minions within 3 blocks", "+25% with the Almoner's Cowl"],
      shaped([" S ", "SBS", " G "], {"S": "elsewhere:mirewick_scrip", "B": "minecraft:glass_bottle", "G": "minecraft:gold_ingot"}), [{"table": "chests/mirewick_courier", "chance": 0.08}])
spell("azmar_ka_sigil", "Ka Sigil", "epic", "ka_sigil", 300, ["Support sigil of Azmar (10 Glimmer, 3 s)", "Ward the player you look at (sneak: yourself): Absorption 6 HP for 8 s"],
      shaped(["GSG", "ASA", "GGG"], {"G": "minecraft:gold_ingot", "S": "elsewhere:azmar_seal_sultana", "A": "minecraft:amethyst_shard"}), [{"table": "chests/azmar_courier", "chance": 0.08}])
spell("glimmer_lantern", "Glimmer Lantern", "uncommon", "glimmer_lantern", 256, ["Light utility (2 Glimmer)", "Places an invisible light (level 14) in the air you look at for 5 minutes, up to 8 at once"],
      shaped([" A ", "ALA", " A "], {"A": "minecraft:amethyst_shard", "L": "minecraft:lantern"}, "misc"), [{"table": "chests/kingdom_market_stall", "chance": 0.2}])
spell("blinkstone", "Blinkstone", "epic", "blinkstone", 200, ["Utility stone (12 Glimmer, 2 s)", "Teleport up to 8 blocks along your view, stopping before blocks", "!Does not work near a boss"],
      shaped([" M ", "MEM", " M "], {"M": "elsewhere:glimmer_mote", "E": "minecraft:ender_pearl"}, "misc"), [{"table": "entities/ossyra", "chance": 0.2}])
spell("emberfall_rod", "Emberfall Rod", "rare", "emberfall", 400, ["Artillery rod (8 Glimmer, 1.2 s)", "Fire burst 24 blocks away: 10 damage within 3 blocks, fire 3 s; never breaks or lights blocks"],
      shaped(["F", "B", "B"], {"F": "minecraft:fire_charge", "B": "minecraft:blaze_rod"}), [{"table": "chests/sunwright_ziggurat", "chance": 0.08}])
spell("frostwake_scepter", "Frostwake Scepter", "rare", "frostwake", 400, ["Control scepter (7 Glimmer)", "10-block frost lane: Slowness II 3 s and 3 freeze damage per second for 3 s"],
      shaped(["  F", " S ", "S  "], {"F": "elsewhere:frost_shard", "S": "minecraft:stick"}), [{"table": "chests/climbers_cairn", "chance": 0.08}])
spell("starfall_orrery", "Starfall Orrery", "epic", "orrery", 300, ["Artillery orrery (20 Glimmer, 2 s channel)", "Five stars of 9 damage fall in a 4-block circle up to 32 blocks away", "!Taking damage during the channel cancels it"],
      shaped(["NAN", "DRD", " R "], {"N": "minecraft:nether_star", "A": "minecraft:amethyst_shard", "D": "minecraft:diamond", "R": "minecraft:blaze_rod"}), [{"table": "entities/ossyra", "chance": 0.3}])

# ---------------------------------------------------------------- s-echoarrow: Echo Arrow (Marksman Trapper ammo, G4). Entity, pulse,
# renderer and self-test: dev.elsewhere.gear3.s3.echoarrow (mechanic "echo_arrow" = EchoArrowItem). GEAR.md: craft 8 arrows + Resonant Shard -> 8.
ITEMS.append({"id": "echo_arrow", "name": "Echo Arrow", "kind": "plain", "rarity": "uncommon", "mechanic": "echo_arrow", "stats": {}, "tags": ["minecraft:arrows"],
              "lore": ["Trapper arrow", "Impact: an echo pulse makes every mob within 6 blocks glow for 5 s",
                       "Silent flight: no sound, and sculk sensors and Wardens never hear it"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["AAA", "ARA", "AAA"],
                         "key": {"A": "minecraft:arrow", "R": "elsewhere:resonant_shard"}, "result": {"id": "elsewhere:echo_arrow", "count": 8}},
              "jei": ["How to get: craft 8 arrows around a Resonant Shard (Hush currency) for 8 Echo Arrows.",
                      "Shoot it from any bow, crossbow or dispenser. Where it hits, every mob within 6 blocks glows for 5 s.",
                      "It flies and lands silently: no sound, and no vibration for sculk sensors, shriekers or Wardens."]})

# ---------------------------------------------------------------- calling tags (s-callingtags): tools/calling_tags.py sets every item's
# "calling" (and the caster_trinket tag) from its GEAR.md table; keep this block last, after every ITEMS.append.
import sys  # noqa: E402
sys.path.insert(0, HERE)  # -I drops the script dir from sys.path
import calling_tags  # noqa: E402
for _note in calling_tags.apply(ITEMS):
    print(_note)

cat = {"tab_name": "Elsewhere 3.0 Gear", "placeholder_tables": ["entities/mother_creak", "entities/ossyra"], "items": ITEMS}
json.dump(cat, open(OUT, "w"), indent=1, ensure_ascii=False)
print(f"build_catalogue: {len(ITEMS)} item(s) -> {OUT}")
