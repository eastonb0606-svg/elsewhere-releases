# 3.0 JEI info pages (s-compat-jei). Writes two files next to info.json; the plugin reads every assets/elsewhere/jei/*.json and joins
# the lines per item, so nothing here depends on merge.py's json_merge order:
#   info_rivals.json  = the rival kingdoms' and the court-trade INTEGRATION pages (Hrafnholt, Azmar, Mirewick, court trade), which
#                       merge.py's snapshot_extra never picked up (only gear2, kingdom_lore and gear3 are listed there);
#   info_3_0.json     = hand-written pages for every remaining item without a recipe or page (structure and summon blocks, logs,
#                       stripped wood, discs, filled survey maps). gear3's own jei_info.json still reaches info.json via snapshot_extra.
# Usage (from jei_compat/): python3 -I tools/gen_info3.py    (idempotent; re-run after a rival lane edits its INTEGRATION file)
import json, os
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.dirname(HERE)
OUT = os.path.join(HERE, 'src/main/resources/assets/elsewhere/jei')

rivals = {}
for f in ('rival_hrafnholt/INTEGRATION/jei_info_hrafnholt.json', 'rival_azmar/INTEGRATION/jei_info_azmar.json',
          'rival_mirewick/INTEGRATION/jei_info_mirewick.json', 'kingdom_court/INTEGRATION/jei_info_court_trade.json'):
    p = os.path.join(CONTENT, f)
    for k, lines in json.load(open(p)).items():
        k = k if ':' in k else 'elsewhere:' + k
        cur = rivals.setdefault(k, [])
        for l in lines:
            if l.strip() and l not in cur:
                cur.append(l)

P = {}
def add(i, *lines):
    cur = P.setdefault('elsewhere:' + i, [])
    for l in lines:
        if l not in cur:
            cur.append(l)

# --- structure and summon blocks (arena fixtures: most can't be mined; the ones with a loot table drop themselves)
FIXTURE = 'An arena fixture: it is part of the structure and drops nothing when broken, so it cannot be collected in survival.'
add('drowned_organ', 'The playable organ in the nave of the Drowned Cathedral (The Verdigris Sea, Foundry Trench). Archdeacon Verdigrim answers its music.', FIXTURE)
add('vorlagh_capstan', 'The capstan on the deck of the Wreck Ring, three shipwrecks lashed together in deep ocean. Blow a Siren Conch on the deck during a thunderstorm to summon Vorlagh, the Tidewrack Kraken.', FIXTURE)
add('neferkhet_sun_altar', 'The altar of the Sun Chamber, a hidden hall under desert pyramids. Place a gold block on it at noon to summon Neferkhet, the Sand-Bound King.', FIXTURE)
add('neferkhet_sun_idol', 'A sun idol of the Sun Chamber, the hidden hall under desert pyramids where Neferkhet sleeps.', FIXTURE)
add('gulpmother_shrine_cauldron', 'The cauldron of the Croaking Shrine (mangrove swamps). Drop 3 slimeballs into it at night to summon Old Gulpmother.', FIXTURE)
add('proving_challenge_horn', 'The challenge horn of the Proving Grounds in Ironpeak. Sound it to call Hrolf Grimhorn; beating him opens the way to Thurgrim on the Summit Throne.', FIXTURE)
add('choir_pillar', 'One of the 8 crystal pillars of the Choir Geode (The Hush). Strike all 8 once each to summon Lithara, the Geode Cantor.', FIXTURE)
add('tympanum_membrane', 'The membrane of the Listening Engine (The Hush), the ear-shaped deepslate funnel where Auris answers when your Resonance reaches 100%.', FIXTURE)
add('hotbox_vent', 'A vent shutter of the Great Hotbox (The Greenhaze), the smoke-filled dome of Percolax. In his second phase the vents slam shut and the smoke builds up; the lever beside a shutter opens it again.', FIXTURE)
add('vent_lever', 'A vent lever of the Great Hotbox (The Greenhaze). When Percolax slams the vents shut, hold use on a lever for 2 s to open its vent and let the smoke out.', FIXTURE)
add('duel_block', 'A temporary block a kingdom duelist raises during a fight. It cracks and vanishes after 30 s, when its owner falls, or when its chunk unloads, and is never placed so that it walls a player in.', FIXTURE)
add('resin_heart', 'The amber heart of the Elder Trunk in the Pale Hollow, Mother Creak\'s hollow pale-oak column.', FIXTURE)
add('moonring_stone', 'A tuff monolith of the Moonring in the Pale Hollow: 12 of them circle the eyeblossom meadow where Selenne is summoned.', FIXTURE)
add('moon_bloom', 'The single closed eyeblossom in the center of the Moonring (Pale Hollow). Pick it to summon Selenne, the Unblinking.', FIXTURE)
add('heartwood_log', 'The living heartwood of the Elder Trunk in the Pale Hollow, where Mother Creak sleeps. It drops nothing; Pale Hollow trees give pale oak instead.', FIXTURE)
DROPS_SELF = 'Breaking it with the right tool drops the block itself, so you can take it home as a trophy or for building.'
add('gilded_throne', 'The gilded throne of Grunthar\'s hoard (the Nether). Place a gold block on the throne to summon Grunthar & Ashtusk.', DROPS_SELF)
add('summit_war_drum', 'The war drum on the Summit Throne in Ironpeak (about Y 1450). Beat it after defeating Hrolf Grimhorn at the Proving Grounds to summon Thurgrim Ironjaw.', DROPS_SELF)
add('matriarch_pedestal', 'The dragon-egg pedestal on top of the Matriarch Spire (The End). Place a shulker shell on it, with the Ender Dragon dead, to summon Ossyra.', DROPS_SELF)
add('pyre_cradle_core', 'The core of the Pyre Cradle, the Nether fortress chamber of Solaxis. Break all 4 blaze spawners in the fortress to summon Solaxis.', DROPS_SELF)
add('halo_altar', 'The spire altar of the Shattered Halo (The End). Place the dragon egg on it with 4 end crystals to summon Aphelion; you get the egg back.', DROPS_SELF)

# --- dimension building blocks without a recipe
add('frostgranite', 'Frostgranite: the stone of Ironpeak\'s high slopes. Mine it with a pickaxe (it drops itself); stonecut or craft it into slabs, stairs, walls, polished stone and bricks.')
add('chiseled_sunwright_bricks', 'Chiseled Sunwright Bricks: the carved stone of the Sunwright Ziggurat in the Glasswaste. Mine it with a pickaxe; it drops itself.')
for b, d in (('verdigris_grating', 'grating'), ('verdigris_pipe', 'pipe'), ('verdigris_pipe_junction', 'pipe junction')):
    add(b, 'A copper ' + d + ' from the drowned foundries of the Verdigris Sea (Engineer\'s Atoll, the Clockwork Rig). Mine it with a pickaxe; it drops itself.')
add('kelpwood_log', 'Kelpwood: the trees of the Verdigris Sea islands. Chop the log with an axe; 1 log makes 4 Kelpwood Planks.')
add('kushwood_log', 'Kushwood: the trees of the Greenhaze. Chop the log with an axe; 1 log makes 4 Kushwood Planks.')
add('kushwood_leaves', 'Leaves of the Kushwood trees in the Greenhaze. Shears collect them; otherwise they drop sticks.')
add('skyblossom_log', 'Skyblossom: the blossom trees of Petalwind. Chop the log with an axe; 1 log makes 4 Skyblossom Planks.')
add('skyblossom_leaves', 'Leaves of the Skyblossom trees in Petalwind. Shears collect them; they can also drop Skyblossom Saplings.')
add('skyblossom_sapling', 'Dropped by Skyblossom leaves in Petalwind. Plant it on dirt or grass and it grows into a Skyblossom tree.')
for w, where in (('kelpwood', 'Verdigris Sea'), ('kushwood', 'Greenhaze'), ('skyblossom', 'Petalwind')):
    add('stripped_' + w + '_log', 'Use an axe on a ' + w.capitalize() + ' Log (' + where + ') to strip it.')
    add('stripped_' + w + '_wood', 'Use an axe on ' + w.capitalize() + ' Wood (' + where + ') to strip it.')

# --- music discs sold by the dimension traders (MerchantOffer in code: not in any data file)
add('music_disc_four_twenty', 'A music disc: Four Twenty. The Greenhaze Budtender trades it for a Grumblegut Tusk.')
add('music_disc_hush', 'A music disc: Hush. The Mute Archivist of the Hush trades it for a Cantor\'s Bell Shard.')
add('music_disc_low_tide_engine', 'A music disc: Low Tide Engine. The Tidewright of the Verdigris Sea trades it for 4 Scraped Patina.')
add('music_disc_thin_air', 'A music disc: Thin Air. The Ironpeak Clan Quartermaster trades it for 8 Skyiron and 16 emeralds.')

# (the filled survey maps are not separate items: a Survey Map turns into a vanilla filled map, so no page)

# --- brewing (minecraft:brewing data recipes show in JEI's brewing category; the page adds what the potion does)
add('strong_glimmer_tonic', 'A stronger Glimmer Tonic: brew a Glimmer Tonic with glowstone dust. Drinking it restores more Glimmer than the plain tonic.')

for name, data in (('info_rivals.json', rivals), ('info_3_0.json', P)):
    json.dump(dict(sorted(data.items())), open(os.path.join(OUT, name), 'w'), indent=1, ensure_ascii=False)
    print(name, len(data), 'pages')
