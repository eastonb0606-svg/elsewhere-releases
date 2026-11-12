#!/usr/bin/env python3
"""Writes every arsenal JSON resource (item models, equipment, lang, tags, recipes, damage type). Re-run after edits."""
import json, os
R = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
def w(rel, obj):
    p = os.path.join(R, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w") as f: json.dump(obj, f, indent=2); f.write("\n")
def opt(i): return {"id": i, "required": False}
E = "elsewhere:"
HANDHELD = ["amethyst_wand", "sunlance_staff", "stormcoil_staff", "sculk_resonator"]
PIECES = ["hood", "robe", "leggings", "boots"]
MOON = ["moonsilk_" + p for p in PIECES]; LINED = ["lined_moonsilk_" + p for p in PIECES]
FLAT = MOON + LINED + ["stormsage_mantle", "cantors_diadem", "glimmerstone_focus", "glimmer_tonic", "strong_glimmer_tonic", "moonsilk_thread"]
for n in HANDHELD + FLAT:
    w(f"assets/elsewhere/items/{n}.json", {"model": {"type": "minecraft:model", "model": f"elsewhere:item/{n}"}})
    w(f"assets/elsewhere/models/item/{n}.json", {"parent": "minecraft:item/handheld" if n in HANDHELD else "minecraft:item/generated", "textures": {"layer0": f"elsewhere:item/{n}"}})
# equipment assets
for a in ["moonsilk", "lined_moonsilk"]:
    w(f"assets/elsewhere/equipment/{a}.json", {"layers": {"humanoid": [{"texture": f"elsewhere:{a}"}], "humanoid_leggings": [{"texture": f"elsewhere:{a}"}]}})
for a in ["stormsage_mantle", "cantors_diadem"]:
    w(f"assets/elsewhere/equipment/{a}.json", {"layers": {"humanoid": [{"texture": f"elsewhere:{a}"}]}})
# tags
caster = [E + n for n in MOON + LINED + ["stormsage_mantle", "cantors_diadem"]]
w("data/elsewhere/tags/item/caster_armor.json", {"replace": False, "values": caster})
w("data/elsewhere/tags/item/calling/caster.json", {"replace": False, "values": caster})
w("data/elsewhere/tags/item/trinket.json", {"replace": False, "values": [E + "glimmerstone_focus", opt(E + "moon_eye")]})
w("data/elsewhere/tags/item/caster_trinket.json", {"replace": False, "values": [E + "glimmerstone_focus", opt(E + "moon_eye")]})
for i, s in enumerate(["head", "chest", "legs", "feet"]):
    w(f"data/elsewhere/tags/item/set/moonsilk/{s}.json", {"replace": False, "values": [E + MOON[i], E + LINED[i]]})
w("data/elsewhere/tags/item/slow_mending.json", {"replace": False, "values": [E + n for n in LINED + ["stormsage_mantle", "cantors_diadem"]]})
w("data/elsewhere/tags/item/repairs_moonsilk_armor.json", {"replace": False, "values": [opt(E + "amber"), E + "moonsilk_thread"]})
w("data/elsewhere/tags/item/repairs_sunlance_staff.json", {"replace": False, "values": [opt(E + "sunglass_shard"), "minecraft:amethyst_shard"]})
w("data/elsewhere/tags/item/repairs_stormcoil_staff.json", {"replace": False, "values": [opt(E + "verdigris_gear"), "minecraft:copper_ingot"]})
w("data/elsewhere/tags/item/repairs_stormsage_mantle.json", {"replace": False, "values": [E + "moonsilk_thread"]})
w("data/elsewhere/tags/item/repairs_cantors_diadem.json", {"replace": False, "values": [E + "moonsilk_thread"]})
w("data/elsewhere/tags/item/glimmer_item.json", {"replace": False, "values": [E + n for n in HANDHELD] + [E + "glimmer_tonic", E + "strong_glimmer_tonic", E + "glimmerstone_focus",
    opt(E + "heliograph_lens"), opt(E + "ninefold_rod"), opt(E + "moon_eye"), opt(E + "gale_heart"), opt(E + "gravity_pearl"), opt(E + "cloud_jar"), opt(E + "tuning_fork_of_auris"),
    opt(E + "kitestring_staff"), opt(E + "rootcall_staff"), opt(E + "brassback_key"), opt(E + "herding_crook"), opt(E + "bell_of_the_flock")]})
slots = {"head_armor": [0], "chest_armor": [1], "leg_armor": [2], "foot_armor": [3]}
for t, (i,) in slots.items():
    vals = [E + MOON[i], E + LINED[i]] + ([E + "cantors_diadem"] if i == 0 else []) + ([E + "stormsage_mantle"] if i == 1 else [])
    w(f"data/minecraft/tags/item/{t}.json", {"replace": False, "values": vals})
w("data/minecraft/tags/item/enchantable/durability.json", {"replace": False, "values": [E + n for n in HANDHELD]})
w("data/elsewhere/tags/item/moonsilk_base/head.json", {"replace": False, "values": [opt(E + "paleweave_hood"), opt(E + "paleweave_helmet"), "minecraft:leather_helmet"]})
w("data/elsewhere/tags/item/moonsilk_base/chest.json", {"replace": False, "values": [opt(E + "paleweave_robe"), opt(E + "paleweave_chestplate"), "minecraft:leather_chestplate"]})
w("data/elsewhere/tags/item/moonsilk_base/legs.json", {"replace": False, "values": [opt(E + "paleweave_leggings"), "minecraft:leather_leggings"]})
w("data/elsewhere/tags/item/moonsilk_base/feet.json", {"replace": False, "values": [opt(E + "paleweave_boots"), "minecraft:leather_boots"]})
# damage type
w("data/elsewhere/damage_type/spell.json", {"message_id": "elsewhere.spell", "exhaustion": 0.1, "scaling": "when_caused_by_living_non_player"})
w("data/minecraft/tags/damage_type/is_projectile.json", {"replace": False, "values": [E + "spell"]})
# recipes
w("data/elsewhere/recipe/amethyst_wand.json", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": ["  A", " CA", "S  "],
    "key": {"A": "minecraft:amethyst_shard", "C": "minecraft:copper_ingot", "S": "minecraft:stick"}, "result": {"id": E + "amethyst_wand"}})
for i, s in enumerate(["head", "chest", "legs", "feet"]):
    w(f"data/elsewhere/recipe/{MOON[i]}.json", {"type": "minecraft:crafting_shapeless", "category": "equipment",
        "ingredients": [f"#elsewhere:moonsilk_base/{s}"] + [E + "moonsilk_thread"] * 4, "result": {"id": E + MOON[i]}})
    w(f"data/elsewhere/recipe/{LINED[i]}_smithing.json", {"type": "minecraft:smithing_transform", "template": "minecraft:netherite_upgrade_smithing_template",
        "base": E + MOON[i], "addition": "#minecraft:netherite_tool_materials", "result": {"id": E + LINED[i]}})
def brew(name, inp, reagent, out):
    w(f"data/elsewhere/recipe/brewing/{name}.json", {"type": "minecraft:brewing", "input": inp, "reagent": {"item": reagent}, "output": out})
awk = {"item": "minecraft:potion", "potion_contents": {"potions": "minecraft:awkward"}}
brew("glimmer_tonic", awk, "minecraft:amethyst_shard", {"id": E + "glimmer_tonic"})
brew("strong_glimmer_tonic", {"item": E + "glimmer_tonic"}, "minecraft:glowstone_dust", {"id": E + "strong_glimmer_tonic"})
brew("clarity_draught", awk, "minecraft:open_eyeblossom", {"id": "minecraft:potion", "components": {"minecraft:potion_contents": {"potion": E + "clarity_draught"}}})
brew("long_clarity_draught", {"item": "minecraft:potion", "potion_contents": {"potions": E + "clarity_draught"}}, "minecraft:redstone",
     {"id": "minecraft:potion", "components": {"minecraft:potion_contents": {"potion": E + "long_clarity_draught"}}})
# lang
L = {
 "itemGroup.elsewhere.arsenal": "Elsewhere: Arsenal",
 "item.elsewhere.amethyst_wand": "Amethyst Wand", "item.elsewhere.sunlance_staff": "Sunlance Staff", "item.elsewhere.stormcoil_staff": "Stormcoil Staff",
 "item.elsewhere.sculk_resonator": "Sculk Resonator", "item.elsewhere.moonsilk_hood": "Moonsilk Hood", "item.elsewhere.moonsilk_robe": "Moonsilk Robe",
 "item.elsewhere.moonsilk_leggings": "Moonsilk Leggings", "item.elsewhere.moonsilk_boots": "Moonsilk Boots",
 "item.elsewhere.lined_moonsilk_hood": "Lined Moonsilk Hood", "item.elsewhere.lined_moonsilk_robe": "Lined Moonsilk Robe",
 "item.elsewhere.lined_moonsilk_leggings": "Lined Moonsilk Leggings", "item.elsewhere.lined_moonsilk_boots": "Lined Moonsilk Boots",
 "item.elsewhere.stormsage_mantle": "Stormsage Mantle", "item.elsewhere.cantors_diadem": "Cantor's Diadem",
 "item.elsewhere.glimmerstone_focus": "Glimmerstone Focus", "item.elsewhere.glimmer_tonic": "Glimmer Tonic",
 "item.elsewhere.strong_glimmer_tonic": "Strong Glimmer Tonic", "item.elsewhere.moonsilk_thread": "Moonsilk Thread",
 "item.elsewhere.glimmerstone_focus.lore1": "Trinket: works in your off hand or hotbar (max 2 trinkets)",
 "item.elsewhere.glimmerstone_focus.lore2": "+8 Max Glimmer", "item.elsewhere.glimmerstone_focus.lore3": "+25% Glimmer Regen (needs a caster piece worn)",
 "effect.elsewhere.clarity": "Clarity", "effect.elsewhere.dazed": "Dazed",
 "item.minecraft.potion.effect.clarity_draught": "Clarity Draught", "item.minecraft.splash_potion.effect.clarity_draught": "Splash Clarity Draught",
 "item.minecraft.lingering_potion.effect.clarity_draught": "Lingering Clarity Draught", "item.minecraft.tipped_arrow.effect.clarity_draught": "Arrow of Clarity",
 "attribute.name.elsewhere.max_glimmer": "Max Glimmer", "attribute.name.elsewhere.glimmer_regen": "Glimmer Regen",
 "attribute.name.elsewhere.spell_power": "Spell Power", "attribute.name.elsewhere.spell_cost": "Spell Cost",
 "attribute.name.elsewhere.projectile_pierce": "Projectile Pierce", "attribute.name.elsewhere.minion_slots": "Minion Slots",
 "attribute.name.elsewhere.minion_damage": "Minion Damage", "attribute.name.elsewhere.dodge_chance": "Dodge Chance",
 "attribute.name.elsewhere.kill_heal": "Kill Heal",
 "death.attack.elsewhere.spell": "%1$s was struck down by a spell", "death.attack.elsewhere.spell.player": "%1$s was struck down by %2$s's spell",
 "death.attack.elsewhere.spell.item": "%1$s was struck down by %2$s using %3$s",
 "entity.elsewhere.magic_bolt": "Amethyst Bolt",
 "elsewhere.glimmer.not_enough": "Not enough Glimmer (%s needed)",
 "elsewhere.calling.caster": "Caster Calling %s/4: spells %s%%, cost %s%%",
 "elsewhere.calling.starter": "Starter: counts as Calling 2+",
 "elsewhere.tooltip.cost": "Glimmer cost: %s",
 "elsewhere.tooltip.amethyst_wand": "Fires an amethyst bolt (4 damage)",
 "elsewhere.tooltip.sunlance_staff": "Light lance: 9 damage to up to 3 in a line, burns, passes glass",
 "elsewhere.tooltip.stormcoil_staff": "Chain lightning: 8 damage, arcs to 2 more (4 if wet)",
 "elsewhere.tooltip.sculk_resonator": "Hold to charge, release a sonic boom: 14 damage, ignores half armor",
 "elsewhere.tooltip.caster_piece": "Caster piece: your melee and arrows -15%",
 "elsewhere.tooltip.moonlit": "Set bonus Moonlit: at full Glimmer your next spell deals +50%",
 "elsewhere.tooltip.stormsage_mantle": "Airborne: Glimmer regen x2, starts instantly",
 "elsewhere.tooltip.tonic": "Restores %s Glimmer, then regen x2 for 10 s. Causes Dazed",
 "key.category.elsewhere.elsewhere": "Elsewhere",
 "key.elsewhere.toggle_hud": "Toggle Elsewhere HUD", "key.elsewhere.cycle_glimmer": "Cycle Glimmer bar",
 "elsewhere.hud.mode": "Elsewhere HUD: %s is now %s", "elsewhere.hud.all_hidden": "Elsewhere meters hidden", "elsewhere.hud.all_shown": "Elsewhere meters shown",
 "elsewhere.hud.unknown": "Unknown meter %s (known: %s)",
 "elsewhere.hud.meter.glimmer": "Glimmer", "elsewhere.hud.mode.auto": "Auto", "elsewhere.hud.mode.always": "Always", "elsewhere.hud.mode.hidden": "Hidden",
}
w("assets/elsewhere/lang/en_us.json", L)
print("ok")
