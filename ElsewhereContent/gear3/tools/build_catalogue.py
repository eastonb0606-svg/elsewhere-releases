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


WHIP_MARK = {  # s-whips: each whip's own mark (gear3 s3/whips/WhipKind)
    "thornlash": "Hit marks it Thorned 5 s: minions switch to it, +2 damage",
    "tidechain_lash": "Hit marks it Shackled 5 s (7 s wet): minions switch, +20% damage",
    "azmar_sunscourge": "Hit marks it Venom 5 s: minions switch, +15% damage, ignore 3 armor",
    "echo_chord": "Hit marks it Resonant 5 s: minions switch to it, +25% damage",
}
WHIP_CRACK = "Right-click cracks it: the mark fades 3 s after your last swing"
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
                  "lore": [lore0, f"Reach +{reach:g}", WHIP_MARK[wid], WHIP_CRACK], "recipe": rec, "loot": loot})
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
trinket("mirewick_fowlers_bandolier", "Fowler's Bandolier", "rare", ["Trapper trinket of Mirewick (off hand or hotbar)", "Tipped, spectral and special arrows (Echo, Sunglass, Harpoon Bolt) are not used up 40% of the time"],
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

# ---------------------------------------------------------------- s-galesling: Gale Sling (Marksman Skirmisher, G2). Item, slung stone,
# Windwright trade and self-test: dev.elsewhere.gear3.s3.galesling (mechanic "gale_sling" = GaleSlingItem). Whirl item definition,
# pebble models, sounds, extra lang: tools/extra/gale_sling.json. Whirl/pebble textures: tools/gale_sling_art.py.
# GEAR.md: Windwright 6 Zephyr Feathers + string; 4 dmg, 0.5 s charge, cobblestone or gravel ammo; wind charge ammo launches a wind charge.
ITEMS.append({"id": "gale_sling", "name": "Gale Sling", "kind": "plain", "rarity": "uncommon", "mechanic": "gale_sling", "model": "handheld",
              "stats": {"durability": 384, "repair": "elsewhere:zephyr_feather"},
              "lore": ["Skirmisher sling", "Hold to whirl, release: a stone for 4 damage (full whirl 0.5 s)",
                       "Ammo: cobblestone or gravel; a wind charge flies as a wind charge"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" S ", "FFF", "FFF"],
                         "key": {"S": "minecraft:string", "F": "elsewhere:zephyr_feather"}},
              "loot": [{"table": "chests/breeze_temple_vault", "chance": 0.08}],
              "jei": ["How to get: a Windwright (Petalwind kite market) trades it for 6 Zephyr Feathers + 1 String; or craft it from the same; Breeze Temple vaults (8%).",
                      "Hold use to whirl it overhead: a full whirl takes 0.5 s; release to sling. 4 damage at full charge, less if let go early.",
                      "Ammo (a held stack first, then the inventory): cobblestone or gravel, slung as a stone that crumbles where it hits; a wind charge is flung as a wind charge, faster than by hand."]})


# Choirlight Censer (Support, G4; s-censer: s3/censer/ChoirlightCenser.java). GEAR.md source "Lithara 30%; Mute Archivist": the
# Lithara loot hook, and a recipe in the Mute Archivist's currency like the module's other Archivist items (Resonant Shards plus a
# Cantor's Bell Shard, which only Lithara drops, so the censer always needs a Lithara kill).
spell("choirlight_censer", "Choirlight Censer", "epic", "censer", 600,
      ["Support censer of the Hush choir (3 Glimmer a second while swung)", "Hold use: other players and your minions within 6 blocks heal 1.5 HP a second, you 0.5",
       "!You walk at 40% speed while swinging it"],
      shaped([" N ", "RBR", "GRG"], {"N": "minecraft:iron_chain", "R": "elsewhere:resonant_shard", "B": "elsewhere:cantors_bell_shard", "G": "minecraft:gold_ingot"}),
      [{"table": "entities/lithara", "chance": 0.3}])
ITEMS[-1]["jei"] = ["How to get: Lithara drops it (30%); or craft it from the Mute Archivist's currency: 3 Resonant Shards, a Cantor's Bell Shard (from Lithara), 2 gold ingots and an iron chain.",
                    "Hold use to swing it. Every second it spends 3 Glimmer and heals other players and your own minions and pets within 6 blocks by 1.5 HP, and you by 0.5 HP. You walk at 40% while swinging; it stops when your Glimmer runs out."]

# Tidebrass Dredge (shared tools, G4, source Tidewright; s-dredge: s3/dredge/TidebrassDredgeItem.java + mixin/TidebrassDredgeMixin.java).
# Netherite-class shovel: underwater it digs a 3x3 of #elsewhere:tidebrass_dredge_mineable at full speed. "Tidewright" = forged with
# Tidebrass Ingot (the Tidewright's pearlite + copper) like tidechain_lash, plus Engineer's Atoll chests (the Tidewright's home).
ITEMS.append({"id": "tidebrass_dredge", "name": "Tidebrass Dredge", "kind": "shovel", "rarity": "epic", "mechanic": "dredge",
              "stats": {"damage": 6.5, "speed": 1.0, "durability": 2031, "tier": "netherite", "mining_speed": 9.0, "enchantability": 15,
                        "repair": "elsewhere:tidebrass_ingot", "fireproof": 1},
              "tags": ["minecraft:shovels"],
              "lore": ["Tidewright dredging shovel", "Underwater: digs a 3x3 of sand, gravel, clay and mud (sneak: one block)",
                       "Full mining speed underwater, even while swimming"],
              "recipe": smith(TEMPLATE, "minecraft:netherite_shovel", "elsewhere:tidebrass_ingot"),
              "loot": [{"table": "chests/engineers_atoll", "chance": 0.06}],
              "jei": "How to get: forge it at a smithing table from an Elsewhere Forging Template, a Netherite Shovel and a Tidebrass Ingot (the Tidewright's metal: Pearlite from the Tidewright of any Engineer's Atoll, plus copper). Engineer's Atoll chests sometimes hold one (6%). Underwater it digs a 3x3 of sand, gravel, clay and mud at full speed, even while swimming; sneak to dig one block."})

# ---------------------------------------------------------------- s-wolfcall: Wolf-Call Horn (V+S hybrid, Hrafnholt Warband, G3). Warg
# minion, howl, Gudrun/Wolf-Handler drops (an AFTER_DEATH hook, not a loot table) and self-test: dev.elsewhere.gear3.s3.wolfcall (mechanic
# "wolfcall_horn" = WolfcallHornItem). Goat-horn item definition + tooting model, sounds, Warg lang: tools/extra/hrafnholt_wolfcall_horn.json.
# Warg skin + howl: tools/wolfcall_assets.py. GEAR.md: 1 slot, Warg 26 HP, armor 6, 5 dmg / 1 s; howl +10% attack speed within 8, x H.
ITEMS.append({"id": "hrafnholt_wolfcall_horn", "name": "Wolf-Call Horn", "kind": "plain", "rarity": "rare", "mechanic": "wolfcall_horn",
              "stats": {"durability": 128, "repair": "elsewhere:hacksilver"},
              "lore": ["Warband horn of Hrafnholt (Vanguard + Summoner)", "Blow it (hold use 1 s, 10 Glimmer): a Warg answers, 1 minion slot",
                       "Warg: 26 HP, armor 6, bites for 5 every second", "Howl: +10% attack speed while one of your Wargs is within 8 blocks (x hybrid strength)",
                       "Sneak-use: your Wargs go home"],
              "recipe": shaped(["BAB", "HGH", " H "], {"B": "minecraft:bone", "A": "elsewhere:hrafnholt_arm_ring", "H": "elsewhere:hacksilver",
                                                     "G": "minecraft:goat_horn"}, "misc"),
              "jei": ["How to get: Gudrun Ulfhednar (and Risen Gudrun) drops it (30%); Hrafnholt Wolf-Handlers (5%); or craft it: a goat horn bound with 3 Hacksilver, an Arm-Ring and 2 bones.",
                      "Hold use for 1 s to blow it (10 Glimmer, 1 durability): a Warg answers. Each Warg takes one minion slot; at the cap your oldest one leaves. More Wargs out means less Herd focus on your own swings, as with every minion.",
                      "Warg: 26 HP, armor 6, bites for 5 every second, scaled by your minion damage. While a Warg of yours is within 8 blocks you swing 10% faster (times your Vanguard + Summoner hybrid strength). Sneak-use sends the pack home."]})

# G2 hoe from Mirewick's Scrip-Desk (24 Lantern Scrip, market line in rival_mirewick kingdom_trade/mirewick.json; also craftable).
# Use on a ripe crop: harvests and replants every ripe crop in the 3x3; mining a leaf or vine cuts the 3x3 of #elsewhere:mirewick_lantern_sickle_cuts.
ITEMS.append({"id": "mirewick_lantern_sickle", "name": "Lantern Sickle", "kind": "hoe", "rarity": "uncommon", "mechanic": "lantern_sickle",
              "stats": {"damage": 3, "speed": 2.5, "durability": 1561, "tier": "diamond", "mining_speed": 8, "enchantability": 12,
                        "repair": "minecraft:diamond,elsewhere:mirewick_scrip"},
              "tags": ["minecraft:hoes"],
              "lore": ["Polder sickle of Mirewick", "Use on a crop: harvests and replants every ripe crop in a 3x3",
                       "Cuts leaves and vines in a 3x3 (sneak: one)"],
              "recipe": shaped(["DD ", "SLS", " T "], {"D": "minecraft:diamond", "S": "elsewhere:mirewick_scrip", "L": "minecraft:lantern", "T": "minecraft:stick"}),
              "jei": "How to get: sold at Mirewick's Scrip-Desk for 24 Lantern Scrip (sells back for 9), or craft it from 2 diamonds, 2 Lantern Scrip, a lantern and a stick. Use it on a ripe crop to harvest and replant every ripe crop in the 3x3 around it (one seed from each crop's drops goes back in the ground); mining a leaf or vine cuts the 3x3 of leaves or vines facing you (sneak to cut one). It tills like any hoe."})

# ---------------------------------------------------------------- s-starpiercer: Aphelion's Starpiercer (Marksman Sniper, G5, Aphelion 30%).
# Bow, arrow entity, sounds, zoom and self-test: dev.elsewhere.gear3.s3.starpiercer (mechanic "starpiercer" = StarpiercerItem, put by
# Starpiercer.register() before Gear3Items). Pull-stage item definition/models, sounds, arrow name: tools/extra/aphelion_starpiercer.json.
# Pull frames and arrow texture: tools/starpiercer_art.py. Transmutation: excluded (s-compat-transmute's gear3 tag).
ITEMS.append({"id": "aphelion_starpiercer", "name": "Starpiercer", "kind": "plain", "rarity": "epic", "mechanic": "starpiercer",
              "stats": {"durability": 1024, "fireproof": 1, "repair": "elsewhere:void_scale"},
              "tags": ["minecraft:enchantable/bow", "minecraft:enchantable/durability"],
              "lore": ["Sniper bow of Aphelion", "Full draw 1.6 s; arrows fly 1.6x faster and pierce 2 more targets",
                       "Arrows fly straight for 32 blocks; sprinting cancels the draw", "Drawing zooms in like a longbow (sneak: closer)"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" VS", "H S", " VS"],
                         "key": {"V": "elsewhere:void_scale", "H": "elsewhere:aphelion_heart", "S": "minecraft:string"}},
              "loot": [{"table": "entities/aphelion", "chance": 0.3}],
              "jei": ["How to get: Aphelion drops it (30% per kill), or craft it from 2 Void Scales, an Aphelion Heart and 3 String, laid out like a bow.",
                      "A bow with a 1.6 s draw (a chime marks the full draw). Arrows leave 1.6x faster, pierce 2 more targets and fly with no drop for their first 32 blocks.",
                      "Plain arrows become star arrows (they pick up as arrows); tipped, spectral and other special arrows keep their effect. Sprinting cancels the draw. Drawing zooms in: 1.5x at full draw, 2x while sneaking."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-ascension.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-ascension: Ascension Shard (GEAR.md A6 shared table, G5 material).
# Mechanic "ascension_shard" (factory registered by dev.elsewhere.gear3.s3.ascension.Ascension.init, before Gear3Items): a plain item with glint.
# No crafting recipe and no chest loot: the smithing recipe that USES it is data/elsewhere/recipe/gear3_ascension.json (serializer
# elsewhere:gear3_ascension), and the drops (T5/T6 bosses + kingdom rulers, 1 each) are code (AscensionSources). Paste right after the
# gear3_forging_template entry (or anywhere before the calling block).
ITEMS.append({"id": "gear3_ascension_shard", "name": "Ascension Shard", "kind": "material", "rarity": "epic", "mechanic": "ascension_shard",
              "stats": {"stack": 64, "fireproof": 1},
              "lore": ["Smithing material (Elsewhere, G5)",
                       "Smithing table, empty template slot: a G4 set piece + this shard makes that piece Ascendant",
                       "Ascendant: +1 armor toughness; a full Ascendant set raises its set bonus to II"],
              "jei": ["How to get: Auris, Lithara, Ossyra and Aphelion always drop one; so does every kingdom ruler (the Aldermark King, the Sultana of Azmar, the High Jarl of Hrafnholt, and whichever Magnate holds Mirewick's Chair) when a player kills them. Court Lords and lesser bosses drop none.",
                      "Use: in a smithing table, leave the template slot empty and put a G4 set piece (any Elsewhere 3.0 armor set piece) with one shard. The piece stays itself, enchantments and all, and becomes Ascendant: +1 armor toughness.",
                      "Wear a whole set of Ascendant pieces for set bonus II: the set or pair bonus is half again as strong. A piece can only be Ascended once."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-harpoon.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-harpoon: Harpoon Bolt (Marksman Trapper crossbow ammo, G3). Entity, pull,
# water bonus, bow refusal (HarpoonBowMixin), renderer and self-test: dev.elsewhere.gear3.s3.harpoon (mechanic "harpoon_bolt" = HarpoonBoltItem).
# Entity texture: tools/harpoon_bolt_entity.py; entity lang: tools/extra/tidebrass_harpoon_bolt.json. GEAR.md: craft 4 arrows + Tidebrass Ingot -> 8;
# crossbow ammo; pulls non-boss target 3 blocks; x1.5 in water. No calling tag (ammo, calling_tags.py).
ITEMS.append({"id": "tidebrass_harpoon_bolt", "name": "Harpoon Bolt", "kind": "plain", "rarity": "uncommon", "mechanic": "harpoon_bolt", "stats": {},
              "tags": ["minecraft:arrows", "elsewhere:crossbow_only_ammo"],
              "lore": ["Trapper crossbow bolt of Tidebrass", "Hit: pulls a non-boss target 3 blocks toward you",
                       "x1.5 damage to a target in water; flies true underwater", "!Crossbows only: bows can't fire it"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" A ", "ATA", " A "],
                         "key": {"A": "minecraft:arrow", "T": "elsewhere:tidebrass_ingot"}, "result": {"id": "elsewhere:tidebrass_harpoon_bolt", "count": 8}},
              "jei": ["How to get: craft 4 arrows around a Tidebrass Ingot (the Tidewright's metal) for 8 Harpoon Bolts.",
                      "Crossbow ammo (crossbows and dispensers fire it; bows can't). A hit pulls the target 3 blocks toward the shooter, never closer than 1.5 blocks; players too, never bosses or riders. A dispensed bolt pulls back toward the dispenser.",
                      "Against a target standing or swimming in water it deals x1.5 damage, and it keeps its speed underwater like a trident."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-mothlight.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-mothlight: Mothlight Staff (GEAR.md A5 Summoner table, Swarm, G3).
# Swarm minion (3 moths in one slot), Mothdust, Sap Tapper offer, renderer and self-test: dev.elsewhere.gear3.s3.mothlight (mechanic
# "mothlight_staff" = MothlightStaffItem, put into Gear3Mechanics.FACTORIES by Mothlight.init()). Entity/effect/message lang:
# tools/extra/mothlight_staff.json. Swarm + effect textures: tools/mothlight_art.py. Calling: calling_tags.py (summoner).
# GEAR.md source "Selenne 30%; Sap Tapper 20 Pale Bark + 4 Moonsilk Thread": the Selenne loot hook, the Sap Tapper offer (pseudo mixin on
# core's SapTapper.offers()) and a recipe in the same Pale Hollow materials (the Unblinking Eye is the Sap Tapper's 10-bark item).
ITEMS.append({"id": "mothlight_staff", "name": "Mothlight Staff", "kind": "plain", "rarity": "rare", "mechanic": "mothlight_staff", "model": "handheld",
              "stats": {"durability": 128, "repair": "elsewhere:moonsilk_thread"},
              "lore": ["Swarm staff of the Pale Hollow (10 Glimmer, 1 s call)",
                       "Calls a Mothlight Swarm: 3 moths in one minion slot, 6 HP each, each strikes for 1.5 every 0.75 s",
                       "Moth hits: a non-boss target deals 10% less damage for 3 s",
                       "Sneak-use: send the moths home"],
              "recipe": shaped(["TET", "TBT", " B "], {"T": "elsewhere:moonsilk_thread", "E": "elsewhere:unblinking_eye", "B": "elsewhere:pale_bark"}),
              "loot": [{"table": "entities/selenne", "chance": 0.3}],
              "jei": ["How to get: Selenne drops it (30%); the Sap Tapper (Resin Kilns, Pale Hollow) trades it for 20 Pale Bark + 4 Moonsilk Thread; or craft it from 4 Moonsilk Thread, 2 Pale Bark and an Unblinking Eye.",
                      "Hold use for 1 s to call a Mothlight Swarm (10 Glimmer, 1 durability): three moths that share ONE minion slot, 6 HP each. Every 0.75 s each living moth strikes for 1.5 (x minion damage).",
                      "One blow fells at most one moth, and a fallen moth stays down until you call a new swarm. Moth hits dust non-boss targets: for 3 s they deal 10% less damage. Sneak-use sends the moths home."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-pennant.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-pennant: Herdcaller's Pennant (Summoner Swarm standard, G2). Planted
# standard entity, aura (Gear3Scheduler pulses over MinionManager.minions, no scan), Command +8 hook and self-test: dev.elsewhere.gear3.s3.pennant
# (mechanic "pennant" = PennantItem, put by Pennant.init() before Gear3Items). Standard name + messages: tools/extra/herdcaller_pennant.json.
# Standard texture: tools/pennant_art.py. GEAR.md: minions within 12 regen 1 HP/s and +10% speed for 30 s; 1 per player, 60 s cooldown, x D(Summoner).
ITEMS.append({"id": "herdcaller_pennant", "name": "Herdcaller's Pennant", "kind": "plain", "rarity": "uncommon", "mechanic": "pennant", "stats": {"stack": 1},
              "lore": ["Swarm standard (Summoner), 60 s cooldown", "Plant it: your minions within 12 blocks heal 1 HP/s and move 10% faster for 30 s",
                       "One standard at a time; scales with your Summoner Calling"],
              "recipe": shaped(["KFK", "R R"], {"K": "elsewhere:haze_resin_block", "F": "#minecraft:banners", "R": "elsewhere:haze_resin"}),
              "jei": ["How to get: craft it from any banner and 10 Haze Resin (a Haze Resin Block on each side of the banner, a Haze Resin under each block). Haze Resin comes from the Green Haze; its Budtender trades it.",
                      "Use it on the ground (in the open while standing: at your feet) to plant your standard. For 30 s your minions within 12 blocks of it heal 1 HP every second and move 10% faster, both times your Summoner Calling strength.",
                      "It needs a solid block under it and 3 blocks of room. One standard per player: planting again takes the old one down. The pennant is kept and cools down for 60 s; the standard comes down early if you leave, die or its block is broken."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-prismbow.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-prismbow: Prism Bow (GEAR.md A5 Marksman table, M+C hybrid, Arcane archer,
# G4, Heliodor 30%). Bow, light arrow and self-test: dev.elsewhere.gear3.s3.prismbow (mechanic "prism_bow" = PrismBowItem, Gear3Mechanics).
# "model": "bow": item definition + pull models from tools/bow_models.py (hand display turned for the upright sprite; "bow.grip" = the
# sprite's grip centre, so the hand holds the grip). Pull frames: tools/prism_bow_art.py. GEAR.md's "Dune Factor 16 Sunglass Shards + bow
# + Heliodor Mirror Core" is a 3x3 recipe here (7 shards around the Mirror Core and the bow), like the other Dune Factor pieces.
ITEMS.append({"id": "prism_bow", "name": "Prism Bow", "kind": "plain", "rarity": "epic", "mechanic": "prism_bow", "model": "bow",
              "bow": {"grip": [7.3, 7.0]},
              "stats": {"durability": 640, "repair": "elsewhere:sunglass_shard"},
              "tags": ["minecraft:enchantable/bow", "minecraft:enchantable/durability"],
              "lore": ["Arcane archer bow (Marksman + Caster)",
                       "Full draw: spends 2 Glimmer to loose a light arrow (8 x spell power x hybrid strength) instead of your arrow",
                       "The light arrow flies dead straight for 24 blocks, then fades; arrows in your other hand are always loosed as arrows",
                       "!Bow enchantments only work on real arrows"],
              "recipe": shaped(["SMS", "SBS", "SSS"], {"S": "elsewhere:sunglass_shard", "M": "elsewhere:heliodor_mirror_core", "B": "minecraft:bow"}),
              "loot": [{"table": "entities/heliodor", "chance": 0.3}],
              "jei": ["How to get: Heliodor drops it (30% per kill), or craft it from 7 Sunglass Shards around a Heliodor Mirror Core and a bow.",
                      "A full draw with 2 Glimmer to spare (spell cost applies) looses a light arrow instead of your arrow, which is not used: 8 x spell power, scaled by your Marksman/Caster hybrid strength. It flies dead straight and fades after 24 blocks.",
                      "Without the Glimmer, or on a short draw, it shoots your arrow like any bow. Hold arrows in your other hand to always loose real arrows: tipped, spectral and special arrows keep their effects, and Power, Punch and Flame work on those only."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-reliquary.catalogue.py (lead 13:23) ====
# s-reliquary -> s-asm-gear3: paste this block before the calling-tags block in gear3/tools/build_catalogue.py, AND add
# "entities/auris" to the placeholder_tables list at the end of build_catalogue.py (Auris's table is not in the gear3 devlib, so
# gen_resources writes an empty placeholder; the merged jar's Hush module ships the real one). Then build_catalogue + gen_textures
# + gen_resources (sprite echo_reliquary is already in Ideas/overhaul/sprites.json; knight lang is in tools/extra/echo_reliquary.json).
# Dry-run 13:17 on a copy: 69 items, calling -> ["summoner"] (calling_tags), placeholder_tables gains entities/auris, no notes.

# ---------------------------------------------------------------- s-reliquary: Echo Reliquary (Summoner, Elite, G5). Echo Knight minion
# (2 slots via gear3 minion/MinionSlots, 60 HP, armor 16, 10 dmg / 1.2 s, blows ignore 40% armor, silent: mixin/reliquary/EchoSilenceMixin),
# Mute Archivist offer (32 Resonant Shards + a Cantor's Bell Shard) and self-test: dev.elsewhere.gear3.s3.reliquary (mechanic "echo_reliquary",
# factory put by EchoReliquary.init()). No recipe: GEAR.md sources are Auris 30% and the Mute Archivist only. Knight lang: tools/extra/echo_reliquary.json.
# Knight art: tools/echo_knight_art.py. Kill switch: "echo_reliquary": false in config/elsewhere-overhaul3.json.
ITEMS.append({"id": "echo_reliquary", "name": "Echo Reliquary", "kind": "plain", "rarity": "epic", "mechanic": "echo_reliquary",
              "stats": {"durability": 256},
              "lore": ["Reliquary of the Mute Order (Summoner, Elite)",
                       "Hold use 1 s (10 Glimmer): an Echo Knight answers, without a sound",
                       "Echo Knight: 60 HP, armor 16, strikes for 10 every 1.2 s",
                       "Its blows ignore 40% of armor and make no sound or vibration",
                       "Sneak-use: your knights go home", "!Takes 2 minion slots"],
              "loot": [{"table": "entities/auris", "chance": 0.3}],
              "jei": ["How to get: Auris, the Listening Engine, drops it (30%); the Mute Archivist of a Silent Monastery sells one for 32 Resonant Shards and a Cantor's Bell Shard. It can't be crafted.",
                      "Hold use for 1 s (10 Glimmer, 1 durability): an Echo Knight answers. It takes 2 minion slots, so you need 2 slots to call one; older minions go home to make room. Sneak-use sends your knights home.",
                      "Echo Knight: 60 HP, armor 16, strikes for 10 every 1.2 s, scaled by your minion damage. Its blows ignore 40% of the target's armor, and neither it nor its fights make a sound or a vibration: sculk, Wardens and the Hush's listeners never hear it."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-sunglass.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-sunglass: Sunglass Arrow (Marksman Trapper ammo, G3). Entity, glass
# pass-through, fire, undead bonus, renderer and self-test: dev.elsewhere.gear3.s3.sunglass (mechanic "sunglass_arrow" = SunglassArrowItem,
# factory put by SunglassArrows.init() before Gear3Items). Entity lang: tools/extra/sunglass_arrow.json. GEAR.md: craft 4 arrows + Sunglass Shard -> 4.
ITEMS.append({"id": "sunglass_arrow", "name": "Sunglass Arrow", "kind": "plain", "rarity": "uncommon", "mechanic": "sunglass_arrow", "stats": {}, "tags": ["minecraft:arrows"],
              "lore": ["Trapper arrow", "Flies straight through glass and glass panes",
                       "Sets the target on fire for 3 s; +25% damage against undead"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": [" A ", "ASA", " A "],
                         "key": {"A": "minecraft:arrow", "S": "elsewhere:sunglass_shard"}, "result": {"id": "elsewhere:sunglass_arrow", "count": 4}},
              "jei": ["How to get: craft 4 arrows around a Sunglass Shard (Glass Scarabs and Sun Motes in the Glasswaste drop it) for 4 Sunglass Arrows.",
                      "Shoot it from any bow, crossbow or dispenser. It flies straight through glass, stained and tinted glass and glass panes without breaking them, and stops at the first other block or mob.",
                      "A hit sets the target on fire for 3 s and deals +25% damage to undead (zombies, skeletons, phantoms, the Wither)."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-totems.catalogue.py (lead 13:23) ====
# s-totems: the three GEAR.md totems (Summoner, placed minions that never move: 1 slot each, max 2 per player).
# For s-asm-gear3: paste this block into gear3/tools/build_catalogue.py before the calling-tags block (uses ITEMS and shaped()), then run the pipeline.
# Entities, effects, placement, Budtender trade and self-test: dev.elsewhere.gear3.s3.totems (mechanic "totem" = TotemItem).
# Entity names + messages: tools/extra/totems.json. Entity textures + beam: tools/totems_art.py.
# Calling tags (calling_weapon/summoner, held) come from calling_tags.py. Transmutation: excluded (s-compat-transmute's gear3 tag).

ITEMS.append({"id": "bramble_totem", "name": "Bramble Totem", "kind": "plain", "rarity": "uncommon", "mechanic": "totem",
              "stats": {"durability": 128},
              "lore": ["Swarm totem of the Greenhaze (10 Glimmer, 1 minion slot)",
                       "Use on the ground: a 30 HP bramble post that lashes up to 3 mobs within 5 blocks every 2 s",
                       "Each lash: 2 minion damage and Slowness I for 1 s",
                       "Thick with haze pollen: every 4th mob it lashes sneezes",
                       "Max 2 totems out; sneak-use to take yours down"],
              "jei": ["How to get: only from a Greenhaze Budtender, for 12 Haze Resin + 4 Giant Fern (each Budtender sells 2, and never restocks).",
                      "Use it on solid ground to set a 30 HP bramble post that never moves (10 Glimmer, 1 minion slot, 1 durability). Every 2 s it lashes the nearest 3 hostile mobs within 5 blocks for 2 minion damage and Slowness I (1 s). Every 4th mob it lashes sneezes from the haze pollen (harmless).",
                      "At most 2 totems per player (a 3rd takes down the oldest). A totem crumbles when you go more than 48 blocks away. Sneak-use the item to take yours down."]})

ITEMS.append({"id": "clockwork_sentry", "name": "Clockwork Sentry", "kind": "plain", "rarity": "rare", "mechanic": "totem",
              "stats": {"durability": 128},
              "lore": ["Sentry totem of the Verdigris Sea (10 Glimmer, 1 minion slot)",
                       "Use on the ground: a 40 HP, armor 8 copper turret",
                       "Fires a 3-damage bolt every second at one mob within 16 blocks it can see",
                       "Max 2 totems out; sneak-use to take yours down"],
              "recipe": shaped(["PXP", "GDG", "CGC"], {"P": "elsewhere:scraped_patina", "X": "minecraft:crossbow", "G": "elsewhere:verdigris_gear",
                                                       "D": "minecraft:dispenser", "C": "minecraft:copper_block"}),
              "loot": [{"table": "entities/verdigrim", "chance": 0.3}],
              "jei": ["How to get: Verdigrim drops it (30% per kill), or craft it from 2 Scraped Patina, a crossbow, 3 Verdigris Gears, a dispenser and 2 copper blocks.",
                      "Use it on solid ground to set a 40 HP, armor 8 copper turret that never moves (10 Glimmer, 1 minion slot, 1 durability). Once a second it fires a 3-damage minion bolt at one mob within 16 blocks that it can see: the one you are fighting first, else the nearest hostile.",
                      "At most 2 totems per player (a 3rd takes down the oldest). A totem crumbles when you go more than 48 blocks away. Sneak-use the item to take yours down."]})

ITEMS.append({"id": "azmar_sunglass_obelisk", "name": "Sunglass Obelisk", "kind": "plain", "rarity": "epic", "mechanic": "totem",
              "stats": {"durability": 128},
              "lore": ["Sentry totem of the Sultanate of Azmar (10 Glimmer, 1 minion slot)",
                       "Use on the ground: a 50 HP obelisk with a floating sunglass lens",
                       "Sunbeam on one mob within 16 blocks: 4 damage a second, 8 after 3 s on the same target",
                       "!Needs daylight or bright light (12+); dormant in the dark",
                       "Max 2 totems out; sneak-use to take yours down"],
              "recipe": shaped(["SGS", "DWD", "CCC"], {"S": "elsewhere:sunglass_shard", "G": "minecraft:gold_block", "D": "elsewhere:azmar_dinar",
                                                       "W": "elsewhere:azmar_seal_wazir", "C": "minecraft:chiseled_sandstone"}),
              "loot": [{"table": "chests/azmar_courier", "chance": 0.05}],
              "jei": ["How to get: the Royal Workshop of Azmar: craft it from 2 Sunglass Shards, a gold block, 2 Azmar Dinars, a Wazir's Seal and 3 chiseled sandstone; rarely found in Azmar courier chests (5%).",
                      "Use it on solid ground to set a 50 HP obelisk that never moves (10 Glimmer, 1 minion slot, 1 durability). In daylight or bright light (12+) its lens holds a sunbeam on one mob within 16 blocks: 4 minion damage a second, 8 a second once the beam has held the same target for 3 s. In the dark it stands dormant.",
                      "At most 2 totems per player (a 3rd takes down the oldest). A totem crumbles when you go more than 48 blocks away. Sneak-use the item to take yours down."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-waryak.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-waryak: War-Yak Horn (Summoner, Elite, G4; Thurgrim 30%, Clan Quartermaster
# rank 5). War-Yak minion (2 slots via gear3 minion.MinionSlots, charge/trample), horn item, Quartermaster trade mixin and self-test:
# dev.elsewhere.gear3.s3.waryak (mechanic "war_yak_horn" = WarYakHornItem). Goat-horn item definition + tooting model, sounds, lang, extra JEI
# lines: tools/extra/ironpeak_war_yak_horn.json. Entity skin: tools/war_yak_texture.py. Transmutation: already excluded (s-compat-transmute's tag).
# GEAR.md: 2 slots: 80 HP, armor 12, 9 dmg / 1.5 s charge; tramples through up to 3 mobs.
ITEMS.append({"id": "ironpeak_war_yak_horn", "name": "War-Yak Horn", "kind": "plain", "rarity": "epic", "mechanic": "war_yak_horn",
              "stats": {"durability": 256, "repair": "elsewhere:skyiron"},
              "lore": ["Elite war-horn of Ironpeak (Summoner)", "Blow it (hold use 1 s, 10 Glimmer): a War-Yak answers, 2 minion slots",
                       "War-Yak: 80 HP, armor 12; charges every 1.5 s for 9 damage", "Its charge tramples through up to 3 mobs in a line",
                       "Sneak-use: your War-Yaks go home"],
              "recipe": shaped(["KTK", "KGK", "L L"], {"K": "elsewhere:skyiron", "T": "elsewhere:gear3_forging_template", "G": "minecraft:goat_horn",
                                                     "L": "minecraft:leather"}),
              "loot": [{"table": "entities/thurgrim", "chance": 0.3}],
              "jei": ["How to get: Thurgrim drops it (30% per kill); the Clan Quartermaster sells one from clan rank 5 (8 Skyiron and a goat horn); or craft it: a goat horn bound with 4 Skyiron, an Elsewhere Forging Template and 2 leather straps."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-willolantern.catalogue.py (lead 13:23) ====
# ---------------------------------------------------------------- s-willolantern: Will-o'-Lantern (GEAR.md Caster table, C+S hybrid, Ritualist, G4).
# Item, Marsh Wisp minion, recast, light, Chair-holder drop and self-test: dev.elsewhere.gear3.s3.willo (mechanic "will_o_lantern" =
# WillOLanternItem). Wisp name + overlay messages: tools/extra/will_o_lantern.json. GEAR.md source "Chair-holder 30%; weekly auction":
# the 30% Chair-holder drop is a Java hook (WillOLantern.onDeath); there is no auction system, so Mirewick courier chests (5%) and a
# smithing upgrade of the Glimmer Lantern stand in for it, as for the other Mirewick items.
ITEMS.append({"id": "mirewick_will_o_lantern", "name": "Will-o'-Lantern", "kind": "plain", "rarity": "epic", "mechanic": "will_o_lantern",
              "stats": {"durability": 384, "repair": "elsewhere:mirewick_scrip"},
              "lore": ["Ritualist lantern of Mirewick, Caster and Summoner (10 Glimmer, 1 minion slot)",
                       "Hold use to call a Marsh Wisp; sneak-use sends it home",
                       "Every 4 s the wisp recasts your last damage spell at its target at 40% power (x Ritualist hybrid)",
                       "Its marsh-light lights the air around you",
                       "!One wisp per player; teleports, wards, heals, channels and crowd control are never echoed"],
              "recipe": smith("elsewhere:mirewick_seal_ames", "elsewhere:glimmer_lantern", "minecraft:ghast_tear"),
              "loot": [{"table": "chests/mirewick_courier", "chance": 0.05}],
              "jei": ["How to get: a Mirewick Magnate who holds the Chair when he falls drops it (30%); Mirewick courier chests (5%); or smith it: Seal of the Lantern-Oil + Glimmer Lantern + Ghast Tear.",
                      "Hold use for 1 s to call a Marsh Wisp (10 Glimmer, 1 minion slot, 1 durability). It floats at your shoulder and lights the air around it; sneak-use sends it home. One wisp per player.",
                      "Every 4 s, while your minions have a target you can see, the wisp recasts your last damage spell (Amethyst Wand, Sunlance Staff, Stormcoil Staff, Sculk Resonator, Emberfall Rod) at it for free at 40% power, times your Caster/Summoner hybrid strength."]})

# ==== pasted by s-asm-gear3 from the s-hurlbat queue (scratchpad hurlbat_cat.py, 13:08; lost in the build_catalogue queue) ====
# ---------------------------------------------------------------- s-hurlbat: Hurlbat (Marksman Skirmisher, G3; a Hrafnholt throwing axe).
# Thrown entity, item, Huntmaster drop, shield disable and self-test: dev.elsewhere.gear3.s3.hurlbat (mechanic "hurlbat" = HurlbatItem,
# factory put by Hurlbats.putFactory()); renderer: client s3/hurlbat/client. Entity name, sounds, death lines: tools/extra/hrafnholt_hurlbat.json.
# GEAR.md A5: Huntmaster 50%; Thing-day 12 Hacksilver for 8 (rival_hrafnholt kingdom_trade line); thrown axe 7 dmg, 0.6 s between throws,
# retrievable, 50% break, disables shields 2 s. The recipe costs what the Thing-day charges: 3 Hacksilver + 2 sticks -> 2 (12 for 8).
ITEMS.append({"id": "hrafnholt_hurlbat", "name": "Hurlbat", "kind": "plain", "rarity": "rare", "mechanic": "hurlbat", "model": "handheld",
              "stats": {"stack": 8},
              "lore": ["Hrafnholt throwing axe (Marksman)", "Use: throw it for 7 damage, 0.6 s between throws",
                       "Disables a raised shield for 2 s", "Sticks where it lands; its thrower picks it back up",
                       "!Breaks on half of its hits"],
              "recipe": {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["HH", "HS", " S"],
                         "key": {"H": "elsewhere:hacksilver", "S": "minecraft:stick"}, "result": {"count": 2}},
              "loot": [],
              "jei": ["How to get: the Hrafnholt Huntmaster drops a bundle of 8 (50%, when a player kills him); the Hrafnholt Thing-day sells 8 for 12 Hacksilver; or craft 2 from 3 Hacksilver and 2 sticks.",
                      "Use to throw one: 7 damage, then 0.6 s before the next. A hit on a raised shield knocks it down for 2 s, like an axe blow.",
                      "It sticks where it lands, or glances off what it hit and drops; walk over it to pick it back up (only its thrower can). It breaks on half of its hits on a creature."]})

# ==== pasted by s-asm-gear3 from _swarm/gear3/s-miresnare.done (ASSEMBLER TODO 1) ====
# ---------------------------------------------------------------- s-miresnare: Mire Snare (Marksman / Trapper, Mirewick, G2). Placed trap
# block + block entity + Snared effect + mire_snare damage type: dev.elsewhere.gear3.s3.miresnare (mechanic "mire_snare" = MireSnareItem,
# put into FACTORIES by MireSnare.init() before Gear3Items). Block/effect/death lang: tools/extra/mirewick_mire_snare.json. Block art:
# tools/mire_snare_art.py. No calling tag (calling_tags.py: a placed trap). Recipe makes 1, so crafting is not cheaper than the 6 Scrip shop.
ITEMS.append({"id": "mirewick_mire_snare", "name": "Mire Snare", "kind": "plain", "rarity": "uncommon", "mechanic": "mire_snare",
              "stats": {"stack": 8},
              "lore": ["Trapper snare of Mirewick (place it on the floor)", "Roots the first non-boss mob to step in for 3 s and deals 6 damage",
                       "Never springs for players, villagers, golems, pets or minions", "!Single use: a sprung snare falls apart after 3 s"],
              "recipe": shaped(["SCS", "RIR"], {"S": "minecraft:string", "C": "elsewhere:mirewick_scrip", "R": "minecraft:mangrove_roots",
                                              "I": "minecraft:iron_ingot"}, "misc"),
              "jei": "How to get: Mirewick market (6 Lantern Scrip), or craft one from 2 string, 1 Mirewick Scrip, 1 iron ingot and 2 mangrove roots. Place it on a floor (also in shallow water): the first non-boss mob to step in is rooted for 3 s and takes 6 damage, credited to you. Players, villagers, golems, pets and minions never spring it. Single use: a sprung snare falls apart; an armed one breaks back into your hand."})

# ---------------------------------------------------------------- calling tags (s-callingtags): tools/calling_tags.py sets every item's
# "calling" (and the caster_trinket tag) from its GEAR.md table; keep this block last, after every ITEMS.append.
import sys  # noqa: E402
sys.path.insert(0, HERE)  # -I drops the script dir from sys.path
import calling_tags  # noqa: E402
for _note in calling_tags.apply(ITEMS):
    print(_note)

cat = {"tab_name": "Elsewhere 3.0 Gear", "placeholder_tables": ["entities/mother_creak", "entities/ossyra", "entities/lithara", "entities/aphelion", "entities/auris"], "items": ITEMS}
json.dump(cat, open(OUT, "w"), indent=1, ensure_ascii=False)
print(f"build_catalogue: {len(ITEMS)} item(s) -> {OUT}")
