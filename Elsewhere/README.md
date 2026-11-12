# Elsewhere 0.1.0

New dimensions reached with Wayfinders, and the bosses that guard them. This first version adds the **Pale Hollow**, an endless pale forest stuck at dusk, and its two bosses: **Mother Creak, the Elder Heartwood** and **Selenne, the Unblinking**.

## Install
- Minecraft **26.3**, **Fabric Loader 0.20.1+**, **Fabric API 0.165.2+26.3**, Java 25.
- Put `dist/elsewhere-0.1.0.jar` and Fabric API in `mods/` on the **client and the server** (models, textures and the sky ship inside the jar).
- Settings: `config/elsewhere.json` (accidental trips on/off and scale, Wayfinder cooldown, boss party scaling). It is created on first start.

## Getting to the Pale Hollow
1. Craft a **Wayfinder**: amethyst shards, ender pearls and a compass (`A P A / A C A / · P ·`).
2. Attune it: put the Wayfinder in the middle of a crafting grid with a **resin clump, an open eyeblossom, a pale oak log, a pale moss block and a bone block** (any layout; the recipe book shows `R E R / L W L / M B M`). You get **Wayfinder: The Pale Hollow**.
3. Use it. A rift opens in front of you; after 3 seconds it pulls you through, and it stays open 10 seconds so friends can step in after you. You land on safe ground near a **Return Rift** (a ruin with a tall amber light beam).
4. Getting home: use the Wayfinder again inside the Hollow (a rift back to where you left; sneak-use to arrive at the Overworld spot under you, coordinates ×4), or walk into any Return Rift (sneak to go to your bed instead). Beds explode there, so dying sends you home too. All Wayfinders share a 60 s cooldown.
- **By accident:** rarely, after a full night's sleep away from home (more likely near a pale garden), the world pulls at you for 3 seconds (whispers, a coloured vignette). Taking damage cancels it. If it happens you arrive with a **Frayed Wayfinder**: its needle points to the nearest Return Rift and it takes you home once. It never happens in your first hour, within 2 hours of the last trip, near your bed or in places you know well. Turn it off with `"enabled": false` in the config.

## What is there
- Five biomes (Hollow Thicket, Resin Bog, Weeping Deeps, Heartwood Grove, Silent Clearing): marsh at dusk, fog, pale oaks with creaking hearts, huge world-trunks with glowing heartwood veins, ash and firefly particles.
- **Creaking Husk**: freezes while you watch it, creeps up when you don't. Drops Heartwood Shards (35%) and resin.
- **Eyeblossom Wisp**: stare at it for a second and it shrieks (Darkness); ignore it and it nibbles your XP.
- Items: Heartwood Shard, Pale Bark, Pale Sap (night vision), Unblinking Eye (6 shards + ender eye: freezes Stillness mobs for 8 s), Heartwood Log, Ashen Moss, Moonring Stone.

## Bosses
Both need good gear (enchanted diamond at least for Mother Creak, full netherite for Selenne). Boss health scales with the number of players in the arena; an arena seal closes it while the fight runs, and if everyone leaves or dies the boss resets and the arena repairs itself. Each player who took part gets their own drops.

**Mother Creak** (Tier I). Craft a **Heartwood Effigy** (4 Heartwood Shards, 4 pale oak logs, a creaking heart) and use it on the ground in the Pale Hollow: the **Elder Trunk** rises around you. Walk down the spiral ramp; she wakes when you reach the floor.
- She only moves when nobody is looking at her, so keep a watcher on her.
- She can't be hurt while any of the 4 **Resin Hearts** in the walls is intact. Breaking one stuns her for 6 s: hit her then.
- Each heart moves her to a harder phase: Root Burst (an amber dust line on the ground, then roots erupt along it: step off the line), Leaf Litter (Darkness pulses, Creaking Husks climb out of the walls), and with all hearts gone, Heartwood Fury (no armor, and she moves even while watched, at half speed).
- Drops: Resin Heart Locket (saves you from death once: Regeneration II, roots nearby attackers), Heartwood Greatbow (arrows briefly root), 10 Pale Bark.

**Selenne** (Tier III). Craft a **Moonring Seed** (4 Pale Bark, 4 closed eyeblossoms, an ender eye) and use it in the Pale Hollow: 12 tuff monoliths rise in a ring. Pick the closed **Moon Bloom** in the centre to wake her.
- Don't look at her open eye: it burns (2 HP/s, armor doesn't help) and brings Darkness. When the lids close she sinks low and can be hit.
- **Moonbeam**: a line of end-rod sparks marks where it will fire a second later. The monoliths block it.
- Below 60% she summons Eyeblossom Wisps and cracks the monoliths. Below 30% her lids tear off: she never blinks, and only hits from players standing at a monolith's **water cauldron** (her reflection) count; you're safe from the gaze there too. Under 10% the **Full Moon** charges for 10 s: hide behind a monolith.
- Drops: Eyeblossom Circlet (helmet; endermen and gaze attacks ignore your look), the Moon-Eye (makes the mob you look at glow), Pale Bark.

Op commands for testing: `/elsewhere travel <dimension>`, `/elsewhere home`, `/elsewhere rift <dimension>`, `/elsewhere arena mother_creak|selenne`, `/elsewhere trips`, `/elsewhere trip <trigger>`.

## Planned next
The other six dimensions and their bosses (Petalwind Isles, Glasswaste, Verdigris Sea, Ironpeak, The Hush, The Greenhaze: being built as separate modules), the Pale Hollow's Heartwood tools, Paleweave armor and Amber ore, the Sap Tapper trader, Hollow Stag and Bog Wight, natural Elder Trunks and Moonrings with Lantern Processions, the Atlas screen, boss music, and first-kill/repeat drop rates.

## Building from source
`./gradlew build` (JDK 25) → `build/libs/`. `./gradlew runServer` runs the self-test on port 25611 and stops; `./gradlew runClientGameTest` opens a client and saves screenshots to `screenshots/`. Textures and entity geometry are generated by `tools/gen_assets.py` (Pillow) and the data/asset JSON by `tools/gen_data.py`.
