# 2.0 JEI info pages. Merges, into src/main/resources/assets/elsewhere/jei/info.json:
#   - the existing pages (0.3.0 catalog, kept),
#   - ../gear2/INTEGRATION/jei_info_gear2.json and ../kingdom_lore/INTEGRATION/jei_info_kingdom.json (lines appended, no duplicates),
#   - the hand-written 2.0 pages below (every new 2.0 item of every module that has no page yet, plus the Greenhaze/Hush
#     items whose source changed: Echo tools, Hushweave, Frond, Trichome, Munchfruit, the new structures).
# Usage (from jei_compat/): python3 -I tools/gen_info2.py   (idempotent)
import json, os

HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
INFO = os.path.join(HERE, 'src/main/resources/assets/elsewhere/jei/info.json')
info = json.load(open(INFO))

def add(i, *lines, replace=False):
    k = i if ':' in i else 'elsewhere:' + i
    cur = [] if replace else info.setdefault(k, [])
    if replace:
        info[k] = cur
    for l in lines:
        if l not in cur:
            cur.append(l)

# --- merge the INTEGRATION files of other lanes
for f in ('../gear2/INTEGRATION/jei_info_gear2.json', '../kingdom_lore/INTEGRATION/jei_info_kingdom.json'):
    p = os.path.normpath(os.path.join(HERE, f))
    if os.path.exists(p):
        for k, lines in json.load(open(p)).items():
            add(k, *lines)

# --- gear2 (gear-armor-b, weapon1-b, weapon2-a, ranged-a, drill-a, areaminer-b, trinkets, Amberstill): the per-item ability lines
CAP = "Set bonus Capacitor: each hit you take stores a charge (up to 5, each fades after 8 s); your next melee hit discharges into up to 3 other mobs within 4 blocks, 2 damage per charge."
for p in ('helmet', 'chestplate', 'leggings', 'boots'):
    add('galvanic_coilmail_' + p, 'Galvanic Coilmail (Verdigris): 3/7/5/3 armor with 3 toughness on every piece, a little below netherite.',
        CAP, 'Con: no knockback resistance, lightning (including the Stormcoil Staff) hurts you 12.5% more per piece, and the set Mends slowly.',
        'How to get: Smithing: Galvanic template, the diamond ' + p + ' and a Storm Coil.')
    add('amberstill_' + p, 'Amberstill Plate (Pale Hollow): 3/8/6/3 armor, 2 toughness and a little knockback resistance per piece.',
        'Set bonus Stillness: after 2 s without sprinting or attacking you gain +4 toughness; while moving and fighting you only have 8.',
        'Con: slow Mending, not fire-proof.', 'How to get: Smithing: Amberstill template, the diamond ' + p + ' and an Amber ingot.')
add('ironclad_flail', 'A flail (7 damage, 1.2 speed). Hitting the same target again within 2 s adds +10% damage per hit, up to +50%; switching target or missing resets it.',
    'Con: no Sweeping Edge, and you are 20% slower when moving backward. Repair with Skyiron.', 'How to get: Smithing: Ironclad Chain (Thurgrim, 50%), a diamond sword and Skyiron.')
add('gale_rapier', 'A fast rapier (5 damage, 2.4 attack speed). Sprint-hits deal +3 and dash you forward. Repair with Zephyrite.',
    'Con: -25% damage while standing still, no Sweeping Edge, frail (1100 durability).', 'Where: Petalwind. Tempestra drops it (30%); the Windwright trades 16 Zephyr Feathers and 4 Zephyrite for it.')
add('mirrorbolt_arbalest', 'A heavy crossbow: 14-damage bolts that pierce 3 mobs, never crit randomly, and take Multishot and Quick Charge.',
    'Con: 2.5 s reload, arrows only (no fireworks), refuses Piercing and Infinity.', 'How to get: Crafting: 3 Sunglass Shards, 2 diamonds, a Heliodor Mirror Core and a crossbow.')
add('steam_auger', 'A drill: mines stone, ores and deepslate 2.5 times faster than a netherite pickaxe, but its reach is very short (2 blocks).',
    'Burns 1 coal or charcoal per 100 blocks; out of fuel it mines at bare-hand speed. No Fortune, no Mending.', 'How to get: Smithing: Steam Auger Bit (Thurgrim, 50%), a diamond pickaxe and Skyiron.')
add('termite_hive_lantern', 'Mine one block and termites eat the other 8 blocks of the 3x3x1 face over 2 s, with normal drops. Fast on wood, dirt and soft stone; useless on ores and ancient debris.',
    'Con: it chews builds (planks, logs, stone bricks), and stops if you put it away.', 'How to get: Crafting: 4 Amber, a Termite Queen (Mother Creak, 50%) and a lantern; or the Greenhaze Budtender sells it for 24 Haze Resin and a lantern.')

# --- arsenal / summoner / bosses2 (WP8, Phase 1b)
add('sporebloom_staff', 'Hold to call a Sporeling (10 Glimmer, uses one of your 4 minion slots). It never attacks: it heals you and your other minions 2 hearts every 6 s (about a third as strong without summoner armor).',
    'How to get: Percolax drops it, or buy it from the Greenhaze Budtender for 32 Haze Resin and 4 Hazeborn Buds. Repair with Haze Resin.')
add('gale_ribbon', 'A whip-style summoner weapon: a hit tags one target for 5 s, your minions switch to it and hit 30% harder, and ranged minions gain +4 range against it. The tag ends 3 s after you stop swinging.',
    'How to get: Kazehana drops it. Where: Petalwind, the Petal Pagoda.')
add('hushward_bell', 'Hold 0.5 s (8 Glimmer): a 6 s ward around you that absorbs 20 damage and silences nearby mob spell-casting and sound attacks. You cannot ring it again while the ward is up.',
    'How to get: Auris drops it; the Mute Archivist sells one for 24 Resonant Shards and a Cantor\'s Bell Shard.')
add('mirrorglass_prism', 'Hold to channel a 20-block beam: 4 damage per second, 12 per second after 3 s on one target. Costs 3 Glimmer per second.',
    'How to get: Crafting: a Sunglass, 2 Sunglass Shards, a Mirror Block and an amethyst shard (the Glasswaste).')
add('clockwork_repeater', 'A crossbow-like repeater: hold use for 4 s to load 3 arrows, use again to fire a 3-arrow burst. 520 durability, plain arrows only, no Multishot, Quick Charge or Piercing.',
    'How to get: Crafting: a crossbow, 2 Verdigris Gears, a Verdigris Plate and redstone; the Tidewright and Clan Quartermaster can also sell it.')
add('glimmer_mote', 'A tiny spark dropped (20%) by mobs killed with a spell or by your minions. Walk within 1.5 blocks to pick it up: +3 Glimmer.', 'How to get: Kill mobs with spells or minions.')
add('blossom_seal_charm', 'Re-summons Kazehana. Use it on a block inside the Petal Pagoda arena when she is not alive and 10 minutes have passed since her last kill.',
    'How to get: Crafting (shapeless): 4 Zephyrite, 4 Skyblossom Leaves and a bell. Where: Petalwind.')
add('sunwright_mirror_key', 'Re-summons Heliodor. Use it on a block inside the Sunwright Ziggurat when he is not alive and 10 minutes have passed since his last kill.', 'How to get: Crafting (shapeless): 4 Sunglass Shards, 4 gold ingots and a Heliograph Lens. Where: Glasswaste.')
add('sunpit_lure', 'Re-summons Ankhara. Use it in the Sunpit when she is not alive and 10 minutes have passed since her last kill.', 'How to get: Crafting (shapeless): 4 Sandswimmer Scales, 4 rotten flesh and a Gizzard Stone. Where: Glasswaste.')
add('gale_pinion', 'Tempestra\'s feather: the smithing template of the Zephyr Wings (with an elytra and a Petal Glider).',
    'How to get: Tempestra drops it. She wakes when 3 wind charges are thrown inside the Eye of the Gale (Petalwind, y 190).')
for n, w in (('music_disc_updraft', 'Updraft'), ('music_disc_zenith', 'Zenith'), ('music_disc_pale_light', 'Pale Light')):
    add(n, 'A music disc: ' + w + '.')
add('music_disc_updraft', 'How to get: Petalwind, Breeze Temple vault chests.')
add('music_disc_zenith', 'How to get: Trade with the Dune Factor (the Glasswaste).')
add('music_disc_pale_light', 'How to get: Pale Hollow, Resin Kiln barrels (15%).')

# --- verdigris / ironpeak
add('tidebrass_ingot', 'The Tidebrass Diving Suit and tidal tools are smithed from it.', 'How to get: Crafting: 4 Pearlite and 4 copper ingots (shapeless).')
add('skyiron_ingot', 'The Climber armor and Skyiron tools are smithed from it.', 'How to get: Crafting: 4 Skyiron and 4 iron ingots (shapeless).')
add('tidebrass_upgrade_smithing_template', 'Smithing template: the Tidebrass Diving Suit (diamond piece + Tidebrass Ingot).',
    'How to get: Found in Clockwork Rig and Tidelock Vault chests. Copy it: 7 diamonds, the template and a Verdigris Plate make 2.')
add('climber_upgrade_smithing_template', 'Smithing template: Climber armor (diamond piece + Skyiron Ingot).',
    'How to get: Found in Climber\'s Cairn and Clan Hold chests.')
add('tidal_armor_trim_smithing_template', 'Armor trim: Tidal (the Verdigris Sea).', 'How to get: Found in Clockwork Rig, Engineer\'s Atoll and Tidelock Vault chests. Copy it: 7 diamonds, the template and a Verdigris Plate make 2.')
add('summit_armor_trim_smithing_template', 'Armor trim: Summit (Ironpeak).', 'How to get: Found in Climber\'s Cairn and Clan Hold chests. Copy it: 7 diamonds, the template and Polished Frostgranite make 2.')

# --- Greenhaze (WP6): items, structures, sources
add('echo_alloy', 'The metal of the Hush: Hushweave armor and Echo tools are smithed with it, and it repairs them.', 'How to get: Crafting: 4 Echo Crystal and 4 amethyst shards (shapeless).')
add('hushweave_upgrade_smithing_template', 'Smithing template for the Hushweave armor and the Echo tools (a diamond piece of the same kind + Echo Alloy).',
    'How to get: About half of the chests in the Hanging Undercity, Silent Monasteries, the Listening Engine and the Choir Geode; copy it with 7 diamonds and an Echo Crystal Block.')
add('hushweave_hood', 'Hushweave armor: 3/7/5/3 with 2.5 toughness per piece and no knockback resistance. Hood: immune to Darkness, and the Resonance meter shows anywhere.',
    'Full set (hood, Hushcloak, Hushstep Greaves, boots) = Unheard: sneaking makes no vibrations and Resonance does not rise; mobs that have not spotted you notice you from half as far. Con: sprinting vibrations travel 50% farther; slow Mending.',
    'How to get: Smithing: Hushweave template, a diamond helmet and Echo Alloy.')
add('hushcloak', 'Hushweave chest piece: your noise travels half as far (Resonance gain x0.5).', 'Part of the Unheard set bonus (see the Hushweave Hood). Slow Mending.', replace=False)
add('hushweave_boots', 'Hushweave boots: walking and landing make no vibrations.', 'Part of the Unheard set bonus (see the Hushweave Hood). Slow Mending.',
    'How to get: Smithing: Hushweave template, diamond boots and Echo Alloy.')
add('hushstep_greaves', 'Sneaking speed +0.3 and part of the Unheard set (see the Hushweave Hood). Slow Mending.',
    'Drop rule: Auris\'s guaranteed first-kill item (67% on later kills).')
for t, extra in (('sword', '8 damage, 1.6 speed.'), ('pickaxe', 'Mines at speed 9 in the Hush and below Y 0, and 7 elsewhere.'), ('axe', '10 damage, 1.0 speed.'), ('shovel', ''), ('hoe', '')):
    add('echo_' + t, 'An Echo tool: hits and mining make no vibrations and no Resonance (sculk sensors, shriekers and Wardens do not hear it). ' + extra + ' 1600 durability, not fire-proof, slow Mending.',
        'How to get: Smithing: Hushweave template, a diamond ' + t + ' and Echo Alloy.')
add('echo_crystal', 'Second source: Lithara drops 4-8, and Hush structure chests hold it. Needed for Echo Alloy (4 per alloy).')
add('echo_crystal_ore', 'During the Great Silence in the Hush, Echo Crystal veins near you glow (and Resonance builds twice as fast).')
for t in ('sword', 'pickaxe', 'axe', 'shovel', 'hoe'):
    add('trichome_' + t, 'A Trichome tool: hits slow the target. Made from Trichome Quartz and sticks in the usual pattern.', 'How to get: Crafting: Trichome Quartz and sticks.')
add('frond_hood', 'Frond armor: a diamond-grade set woven from Giant Ferns; each piece makes mobs notice you from 12.5% less far while you stand in ferns, leaves or grass.',
    'How to get: Crafting: 4 Giant Ferns and a Trichome Quartz.')
add('frond_cloak', 'Frond chest piece (camouflage in ferns, leaves and grass).', 'How to get: Crafting: Giant Ferns and Trichome Quartz.')
add('frond_leggings', 'Frond leggings (camouflage in ferns, leaves and grass).', 'How to get: Crafting: Giant Ferns and Trichome Quartz.')
add('frond_sandals', 'Frond sandals (camouflage in ferns, leaves and grass).', 'How to get: Crafting: Giant Ferns and Trichome Quartz.')
add('munchfruit', 'Sweet fruit from Munchfruit bushes: food. Grumblegut sniffs out ripe fruit trees.', 'How to get: Munchfruit bushes in the Munchie Groves, around the Bonfire Terrace trees, in the Munchie Market and Fern Terrace Farms; the Budtender sells 3 for 2 Haze Resin.')
add('grove_fruit_platter', 'A heavy snack from the Greenhaze.', 'How to get: Crafting (shapeless): 3 Munchfruit, a Munchie Bacon and a bowl; also found in Bonfire Terrace and Munchie Market chests.')
add('giant_fern', 'How to get: Found in the Kush Canopy; the Budtender sells 2 for 3 Haze Resin; Fern Terrace Farms chests.')
add('trichome_quartz_ore', 'Second source: Trichome Quartz also turns up in Sesh Hut, Munchie Market and Fern Terrace Farms chests.')
add('haze_vent', 'The Budtender sells a Haze Vent for 6 Haze Resin.')
add('lucid_lily', 'The Budtender sells 2 for 3 Haze Resin; the Lucid Spring pool holds an offering chest with more.')
add('resonant_shard', 'Auris drops 4-8; the Hush chests hold more. The Mute Archivist trades 1 echo shard for 2 Resonant Shards.')
add('sculk_resonator', 'How to get (2.0): Mute Archivist trade: an Amethyst Wand and 12 Resonant Shards (2 echo shards are worth 4 shards at the same trader). 2 per restock.', replace=False)
# remove the old 0.3.0 line that said 8 shards + 2 echo shards
info['elsewhere:sculk_resonator'] = [l for l in info['elsewhere:sculk_resonator'] if '8 Resonant Shards and 2 echo shards' not in l]

# --- structures (info pages on their key items)
add('hotbox_altar', 'Centre of the Great Hotbox (Greenhaze). Sneak-use with a lit Green Leaf joint, blunt or vape, or 4 Haze Resin, to summon Percolax. If the fight is lost, the 4 Haze Resin come back after 30 s.')
add('snack_altar', 'Centre of the Bonfire Terrace (Greenhaze). Leave a full stack of food to summon Grumblegut. If the fight is lost, the stack comes back after 30 s.')
add('sculk_lantern', 'Pulses with the noise near it (the Hush).')
# --- boss drop rules, shown on the summon items' pages
add('phasewalker_greaves', 'Percolax\'s signature: guaranteed on your first kill, 67% afterwards.')
add('challengers_cleaver', 'Grumblegut\'s signature: guaranteed on your first kill, 67% afterwards.')
add('echo_forged_blade', 'Dropped by Auris and Lithara: 50% on a first kill and 33% afterwards, with bad-luck protection (+10% per miss, up to 60%). Hits make no vibrations; Ambush: the first hit on a mob that is not targeting you deals +50% (not bosses).')

add('lore_aldermark_surcoat', 'A blue surcoat in the colors of Aldermark.', 'How to get: The Reeve (royal quartermaster) sells it for 8 emeralds.')

# --- the Hush note (QA #11)
add('wayfinder_the_hush', 'The Hush attunement needs the Ender Dragon defeated first: see the "Wayfinder Attunement" page of this item.')

# --- core + dims-pv 2.0 items (release fixer)
add('amber', 'Amber (Pale Hollow): the crafting and repair material of the Heartwood tools and Paleweave armor.',
    'How to get: mine Amber Ore (tuff below Y 30 in the Pale Hollow).')
add('amber_ore', 'Amber Ore: found in tuff below Y 30 in the Pale Hollow, about as rare as diamond. Needs an iron pickaxe or better.',
    'Drops 1 Amber (Fortune applies) and a little experience.')
add('stag_antler', 'Stag Antler: crafts the Antler Bow and repairs it.', 'How to get: Hollow Stags drop 1-2; also found in Hollow Shrine chests.')
add('antler_bow', 'Antler Bow (Pale Hollow): 640 durability with innate Power II (+1.5 arrow damage).',
    'Its arrows root Stillness mobs (Creaking, Creaking Husk; not bosses) for 2 s, once per 4 s per target.',
    'How to get: craft 3 Stag Antlers and 3 String (bow shape). Repair with Stag Antler.')
HW = 'Diamond-tier (1561 durability, mining speed 8, enchantability 14). Hits root Stillness mobs (Creaking, Creaking Husk; not bosses) for 2 s, once per 6 s per target. Repair with Amber.'
add('heartwood_sword', 'Heartwood Sword (Pale Hollow): 7 damage, 1.6 attack speed.', HW,
    'How to get: craft 2 Amber over a Heartwood Shard (sword shape).')
for t in ('pickaxe', 'axe', 'shovel', 'hoe'):
    add('heartwood_' + t, 'Heartwood ' + t.capitalize() + ' (Pale Hollow).', HW,
        'How to get: craft with Amber and sticks, in the shape of the vanilla ' + t + '.')
PW = 'Each Paleweave piece cuts the range at which Hollow mobs and Creakings notice you by 12.5%.'
for t, name, d in (('helmet', 'Helmet', 3), ('leggings', 'Leggings', 6), ('boots', 'Boots', 3)):
    add('paleweave_' + t, 'Paleweave ' + name + ' (Pale Hollow): ' + str(d) + ' armor, 2 toughness, enchantability 14. Repair with Amber.', PW,
        'How to get: craft with Amber and Pale Bark.')
add('paleweave_mantle', 'Paleweave Mantle (Pale Hollow): the chest piece of the Paleweave set; 8 armor, 2 toughness and a little knockback resistance (0.1). Repair with Amber.', PW,
    'How to get: found in Drowned Chapel chests (not craftable).')
add('rift_anchor', 'Rift Anchor: place it in an Elsewhere dimension and your attuned Wayfinder lands next to it when you travel there.',
    'One per player per dimension: placing a new one pops the old one. It does nothing outside the Elsewhere dimensions.',
    'How to get: craft a Lodestone, 4 Amethyst Shards and 4 anchor stones (Pale Oak Log, Heartwood Log, Moonring Stone, Tuff Bricks or a dimension stone).')
add('boss_waystone', 'Boss Waystone: appears near the spot where a boss falls and is remembered for everyone credited with the kill (use it to remember it yourself).',
    'Sneak-use an attuned Wayfinder outside its dimension to warp to your latest waystone there, or the base Wayfinder to warp to your latest waystone in the dimension you are in.',
    'Unbreakable; it cannot be crafted or picked up.')
add('boss_trophy', 'Boss Trophy: a statue of a boss you helped defeat. Place it as a block; breaking it gives the same trophy back.',
    'How to get: your first kill of each boss (killer and players within 48 blocks). Collect all 24 for the Hall of Trophies advancement.')
add('sunwright_ingot', 'Sunwright Ingot (Glasswaste): the addition for the Sunwright armor and Sunstone tool smithing upgrades. Fire-resistant.',
    'How to get: craft 4 Sunstone and 4 Gold Ingots (shapeless).')
add('sunwright_upgrade_smithing_template', 'Smithing template for the Sunwright line: template + a base piece + a Sunwright Ingot gives Sunwright armor or a Sunstone tool.',
    'How to get: Sunwright Ziggurat chests (about 1 in 3). Copy it with 7 Diamonds and Sunwright Bricks (makes 2).')
add('gale_armor_trim_smithing_template', 'Armor trim template (Petalwind): applies the Gale trim to any trimmable armor with a trim material.',
    'Copy it with 7 Diamonds and a Cloud Block (makes 2).')
add('sunburst_armor_trim_smithing_template', 'Armor trim template (Glasswaste): applies the Sunburst trim to any trimmable armor with a trim material.',
    'Copy it with 7 Diamonds and Sunglass (makes 2).')

# --- 2.0 fishing catches (new-dimension-fishing)
FISH = (('ghostfin', 'Ghostfin', 'the Pale Hollow', 2, 'Night Vision', 30, 'a Pale Light music disc'),
    ('petalscale_koi', 'Petalscale Koi', 'Petalwind', 2, 'Slow Falling', 20, 'a Heart of the Sea'),
    ('glassfin_darter', 'Glassfin Darter', 'the Glasswaste', 2, 'Fire Resistance', 15, 'a Diamond'),
    ('verdigris_eel', 'Verdigris Eel', 'the Verdigris Sea', 3, 'Water Breathing', 30, 'a Nautilus Shell'),
    ('frostjaw_trout', 'Frostjaw Trout', 'Ironpeak', 3, 'Resistance', 10, 'an Emerald Block'),
    ('haze_guppy', 'Haze Guppy', 'Greenhaze', 2, 'Regeneration', 5, 'a Golden Apple'),
    ('hush_lanternfish', 'Hush Lanternfish', 'the Hush', 2, 'Invisibility', 10, 'an Echo Shard'))
for i, n, d, food, eff, sec, tr in FISH:
    add(i, n + ': the fish caught in ' + d + '. Eating it restores ' + str(food) + ' hunger and gives ' + eff + ' for ' + str(sec) + ' seconds.',
        'How to get: fish in ' + d + ' (about 70% of catches there; cod and salmon still bite, and about 3% are a rare treasure: ' + tr + ').')

json.dump(dict(sorted(info.items())), open(INFO, 'w'), indent=1, ensure_ascii=False)
print(len(info), 'pages')
