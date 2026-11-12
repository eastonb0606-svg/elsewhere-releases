gear3 catalogue schema  (src/main/resources/elsewhere_gear3/catalogue.json)
{
  "tab_name": "Elsewhere 3.0 Gear",
  "placeholder_tables": ["entities/some_boss"],          // empty tables so the loot MODIFY event fires for tables with no file yet
  "sets": { "<set>": {"armor":[h,c,l,b], "toughness":0, "kb":0, "durability_mult":33, "enchantability":12, "sound":"iron|chain|gold|diamond|netherite|leather", "repair":"elsewhere:x,#tag"} },
  "items": [ {
    "id": "name",            // elsewhere:name
    "name": "Display Name",
    "kind": "plain|material|trinket|sword|axe|pickaxe|shovel|hoe|armor",
    "rarity": "common|uncommon|rare|epic",
    "stats": { "damage": 7, "speed": 1.6, "durability": 1500, "tier": "diamond", "mining_speed": 6, "enchantability": 10, "repair": "elsewhere:skyiron", "stack": 1, "tab_icon": 1 },
    "set": "<set>", "slot": "helmet|chestplate|leggings|boots",     // armor only
    "lore": ["grey first line", "blue ability line", "!red drawback line"],
    "tags": ["elsewhere:caster_armor"],                              // item tags this item joins (trinkets join elsewhere:trinket automatically)
    "mechanic": "key",       // Gear3Mechanics.FACTORIES key (an Item subclass); event hooks live in Gear3Mechanics.init()
    "recipe": { ...vanilla recipe json, "file": "optional_name" } | [ ... ],   // result defaults to this item
    "loot": [ {"table": "entities/galvanox", "chance": 0.3, "min": 1, "max": 1} ],   // MODIFY hook; "ns:path" for non-elsewhere tables
    "jei": "How to get: ..." // optional; auto-built from loot + recipe
  } ]
}
Pipeline:  gen_textures.py (sprites.json -> PNGs)  ->  gen_resources.py (models, lang, recipes, tags, JEI)  ->  gradle runServer (self-test)
Shields (kind "shield"): shield3d.py makes them real 3D shields: a 32x32 item/<id>_3d.png (gen_textures.py) and element models
  <id>.json + <id>_blocking.json in vanilla ShieldModel space with vanilla shield display transforms, switched on
  minecraft:using_item (gen_resources.py). Sprite "template": "shield" = 14 px round shield, "buckler" = 10 px.
Run python as  ROOT/_tools/venv/bin/python -I <script>.   Both scripts take --check.
Calling tags (s-callingtags): tools/calling_tags.py CALLING is the one table for all 82 GEAR.md ids. build_catalogue.py sets each item's
  "calling"/caster_trinket from it (don't write "calling" by hand); gen_resources.py adds not-yet-catalogued ids as required:false.
