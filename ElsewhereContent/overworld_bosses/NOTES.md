# overworld_bosses — Elsewhere content module

The five Overworld bosses from `Ideas/bosses/BOSSES.md` (#1–#5): **Old Gulpmother**, **Strawgrim**, **Neferkhet**,
**Rimeback**, **Vorlagh** (multipart). Dev mod id `elsewhere_overworld_bosses`; every id is `elsewhere:*`.

Status at the beta freeze (19:35, 2026-11-10):
- Compiles (main, client, gametest). Dev server boots with **zero errors**.
- Server self-test: **50 passed, 0 failed** (`run/selftest-4.log`): for each boss it builds the arena, summons through
  the real summon path, checks base HP / armor / toughness, arena snapshot + seal, the lethal-hit guard, drives it
  through all 3 phases with player hits, kills it, checks per-player loot and removal after the death animation;
  plus the overpower guard (20 → 20, 38 → 31), and world-gen: each of the 4 sites is located and generated with no
  errors.
- `dist/elsewhere_overworld_bosses-0.1.0.jar` built.
- Screenshots: `screenshots/overworld_bosses_*.png` (client gametest).

## How to test
- `/elsewhere_dev boss <gulpmother|strawgrim|neferkhet|rimeback|vorlagh>` (op): builds the arena 18 blocks south of
  you and summons the boss through its summon block. Dev-only, drop at merge.
- `./gradlew runServer` runs the self-test and exits (`-Doverworld_bosses.selftest=true`). `S/desk.sh <dir> runServer`
  runs it on the desktop. `./gradlew runClientGameTest` takes the screenshots.

## What is built (per boss)
All bosses: custom entity extending the stub `ElsewhereBoss` (Monster + Enemy), base HP/armor/toughness from
BOSSES.md (Test Meter's x1.53 applies on top), 3 HP-threshold phases with 2 s invulnerable transitions + roar,
scheduled attacks with cooldowns (tunable in `data/elsewhere/boss_tuning/<id>.json`, optional), telegraphs
(yellow dodge / red big hit / cyan interact dust decals), the attack name flashing in a second bar under the boss
bar, arena snapshot + translucent seal (`elsewhere:arena_seal`, players pass, mobs don't), 30 s drop-out, reset to
full with arena restored when everyone leaves/dies (boss goes dormant until players return), party HP scaling,
overpower guard, lethal-hit guard (mixin), per-player owner-locked loot, first-kill tier signature, duplicate
uniques → Boss Token, boss music hook (client mixin; vanilla tracks as placeholders).

| Boss | Summon (in world) | Attacks / phases implemented |
|---|---|---|
| Gulpmother `elsewhere:gulpmother` 340 HP, 8/2 | 3 slimeballs into `elsewhere:gulpmother_shrine_cauldron` (thrown in or used) at night | Tongue Lash (pink line, 1 s, hit → Swallow: held inside, Darkness, 1/s armor-bypassing, sneak 6× or an ally's hit frees, 6 s max), Belly Flop (red shadow ring, scripted arc, 36/18, knockback, sinks lily pads), P2 Spawning Season (frogspawn globs → clutch blocks hatch 2 Mire Biters in 8 s unless broken), P3 tongue ×1.5 and Devour (green line, eats a biter, heals 5%) |
| Strawgrim `elsewhere:strawgrim` 350 HP, 8/4 | Full moon + night, farm field (≥16 farmland in 12 blocks), fence + 2 hay bales + carved pumpkin stacked, light the pumpkin with flint and steel | Reap (270° crit arc, 1 s, 36), Furrow (coarse-dirt line + evoker fangs), Crow Murder (5 Murder Crows dive at a Glowing-marked player), hops between furrows; P2 burning effigy, 3-shot seed fireball volleys, hay fires; P3 body collapses (invulnerable, blind reaps), the head flies as `elsewhere:strawgrim_head`, the only hittable part (forwards damage) |
| Neferkhet `elsewhere:neferkhet` 360 HP, 10/2 | Gold block on/into `elsewhere:neferkhet_sun_altar` at noon (4500–7500) | Sun Lance from the 4 pillar idols (2 s tracking charge, blocked by line of sight, 38 + fire), Quicksand (falling dust 1.5 s, 3×3 tiles drop into a 3-deep pit + scarabs), Staff Swing; P2 sarcophagi open → Wrapped Sentinels, Curse of the Ankh (☥ text display, Hunger II + Wither I until you stand in the sunbeam); P3 eclipse (roof seals, light off, Darkness pulses), 4 lances clockwise |
| Rimeback `elsewhere:rimeback` 460 HP, 16/4 | Break `elsewhere:frozen_heart` (survival) | Glacier Hurl (rip, 1.5 s hold, red ring, boulder 45 centre / 6 + Slowness splash), Frost Breath (cone of frost shards, freeze), Fist Slam (28 + launch); P2 Avalanche (3-s countdown, wave from the north, 30 + freeze unless an outcrop shelters you), Frostbound Revenants; P3 Whiteout (freeze builds >4 blocks from a lit campfire), stomps campfires (relight them), sheds armor (−6 armor, +30% speed). Fire / Fire Aspect ×1.5 |
| Vorlagh `elsewhere:vorlagh` 520 HP, 14/6 | Siren Conch used within 24 blocks of `elsewhere:vorlagh_capstan` during a thunderstorm | 5 tentacles = separate `elsewhere:vorlagh_tentacle` entities (60 HP each, own hitbox; severed → stump), body invulnerable in P1 until 3 are severed; Slam (red line 1.2 s, 44, breaks deck planks), Grab (cyan glow, held at the tip, dragged to the beak 1.5/s, any hit frees, bite 30 after 4 s); P2 Ink Cloud (Blindness cloud), Whirlpool (pulls swimmers), Tidewrack Drowned boarders; P3 telegraphed lightning (5 + fire), a severed tentacle regrows every 20 s. Lightning / Channeling ×2. Second bar "Tentacles (n of 5 standing)" |

Minions: `mire_biter` (Slime), `murder_crow` (Phantom, no sun burn, ignores Scarecrow's Grin wearers),
`wrapped_sentinel` (Husk; first hit Hunger II, rises once unless killed by fire), `frostbound_revenant` (Stray,
ice-blue plates, shatters into 3 frost shards), `tidewrack_drowned` (Drowned, 8% Siren Conch). Projectiles/parts:
`frogspawn_glob`, `seed_fireball`, `sun_lance`, `glacier_boulder`, `frost_shard`, `ink_glob`, `strawgrim_head`,
`vorlagh_tentacle`.

## Registered ids
- Entities: gulpmother, mire_biter, frogspawn_glob, strawgrim, strawgrim_head, murder_crow, seed_fireball, neferkhet,
  sun_lance, wrapped_sentinel, rimeback, glacier_boulder, frost_shard, frostbound_revenant, vorlagh, vorlagh_tentacle,
  ink_glob, tidewrack_drowned.
- Blocks (+ block items): gulpmother_shrine_cauldron, neferkhet_sun_altar, neferkhet_sun_idol, frozen_heart,
  vorlagh_capstan; block only: gulpmother_frogspawn; stub: arena_seal.
- Items: lilycrown, gulp_tongue, frog_legs, bufo_gland, frogspawn_glob, harvest_scythe, scarecrows_grin,
  harvest_moon_seeds, harvest_loaf, harvest_moon_bud, crow_feather, seed_fireball, ankh_of_neferkhet, sunfire_khopesh,
  pharaohs_nemes, royal_honey, linen_wrap, rimeheart_maul, rimewalker_boots, glacier_water, frost_shard, undertow,
  kraken_ink_bomb, abyssal_eye, siren_conch, kraken_calamari; stub (shared): challengers_cleaver, aegis_of_the_ladder,
  phasewalker_greaves, boss_token.
- Damage types: boss_attack, boss_crush (bypasses shield), boss_dot (bypasses armor + shield), boss_magic.
- Sounds: boss.<id>.*, music.boss.<id>, arena.seal (sounds.json maps them to pitched vanilla events).
- Structures (+ structure types, piece types, structure sets, biome tags `elsewhere:has_structure/*`): croaking_shrine,
  sun_chamber, glacier_shelf, wreck_ring.
- Creative tab: elsewhere:overworld_bosses (skipped if the id already exists).
- Item tag `elsewhere:glare_immune` (Pharaoh's Nemes); vanilla tags extended: undead, piglin_safe_armor,
  freeze_immune_wearables, head/foot/leg_armor, swords, bypasses_shield/armor.

## Gear numbers
All in `dev.elsewhere.overworld_bosses.GearStats` (module) and `dev.elsewhere.stub.overworld_bosses.SignatureGear`
(tier I–III signatures), updated to the **final BALANCE.md**: Lilycrown 3/2 helmet + oxygen/water/jump; Gulp Tongue
256 dur, 3 s cd; Harvest Scythe 9 dmg / 1.0, 1561; Scarecrow's Grin 3/2; Ankh first kill 100%, repeat 5%; Sunfire
Khopesh 7/1.6, 1561, ench 22; Nemes 3/2; Rimeheart Maul vanilla mace; Rimewalker Boots 3/2 dyed ice blue; Undertow
trident 500 dur; Cleaver 7/1.6 +10% vs bosses; Aegis 900 dur, 30 s; Greaves 6/2/0, dash 4, 0.25 s, 10 s cd, 1 hunger.
Boss own-drop chances (first kill one unique guaranteed, repeat 35% each) are PLACEHOLDERS (BALANCE gives none).

## Disabled for beta / changed from the docs
- **World-gen sites are ON** (`/locate structure elsewhere:<croaking_shrine|sun_chamber|glacier_shelf|wreck_ring>`):
  Croaking Shrine (mangrove swamp, spacing 28), Sun Chamber (under desert pyramids: same placement + salt as the
  pyramids, hall at y<=30 under the pyramid, ladder shaft + kiosk just east of it), Glacier Shelf (frozen/jagged
  peaks, spacing 36), Wreck Ring (deep ocean, spacing 40; Tidewrack Drowned spawn there and drop the Siren Conch).
  Tidewrack Drowned do not spawn in open ocean outside the Wreck Ring. If a site looks wrong in play, delete its
  `data/elsewhere/worldgen/structure_set/<id>.json` to turn it off cleanly.
- **Models:** the bosses render with scaled, tinted vanilla models (frog ×6, creaking ×1.6, husk ×3.1, iron golem
  ×2.6, squid ×7) and vanilla animations, not the concept-art models; tentacles have a simple custom segmented model;
  the Strawgrim head and glacier boulder render as blocks (jack o'lantern, packed ice). Minions use their vanilla
  base renderers. The sun lance beam is drawn with particles (no beam renderer).
- **Item abilities not implemented yet:** Gulp Tongue grapple (it is a fishing rod), Harvest Scythe 5×5 harvest,
  Scarecrow's Grin vs vanilla phantoms (Murder Crows do ignore it), Ankh's "undead ignore you" (stand-in:
  Invisibility 20 s) and the G4 30 s lockout, Sunfire Khopesh igniting undead, Rimeheart Maul freeze, Undertow
  pull/riptide/Loyalty III preset, Abyssal Eye effects, Harvest Moon Seeds growth, Glacier Water = Fire Resistance
  30 s stand-in, Phasewalker dash (coded in SignatureGear, untested), Challenger's Cleaver weak-point glow.
- Boss bars are vanilla bars (colour + notches); no custom frames/icons. Music uses vanilla tracks; sounds are pitched
  vanilla events. No colourblind/large-decal client options.
- Loot is delivered as owner-locked item entities, not a personal loot chest.
- Test Meter / Green Leaf tie-in foods are plain items (no Diet+/high effects).
- Glacier Hurl: BOSSES.md gives both "6 dmg + Slowness" and "45 raw": built as 45 within 1.8 blocks, 6 + Slowness II
  in the 4-block splash. Tentacle Slam: "8 dmg" vs "44 raw": built as 44 raw (≈ 8.7 HP after netherite).
- Vorlagh's tentacles are separate linked entities, not EnderDragonPart children (vanilla hard-codes those to the
  dragon). Tentacle armor 10/2 is a placeholder (doc gives none).

## Merge instructions
- Main: `dev.elsewhere.overworld_bosses.OverworldBossesContent.init()`; client:
  `dev.elsewhere.overworld_bosses.OverworldBossesClient.initClient()`. Drop `DevEntry`, `DevClientEntry`,
  `dev/DevCommands`, `dev/SelfTest` (or keep the self-test under the core's flag).
- Stub package `dev.elsewhere.stub.overworld_bosses` → swap for the core's framework: `ElsewhereBoss` (base class),
  `BossAttack`, `BossArena`, `BossDamage` (guards, damage types), `BossLoot`, `BossTuning`, `Telegraph`, `BossPart`,
  `ArenaSealBlock`, `BossRegistry` (arena_seal, boss_token, damage type keys; registration is idempotent),
  `SignatureGear` (cleaver/aegis/greaves), `TripTrigger`, mixins `LethalHitGuardMixin` and client
  `MinecraftMusicMixin` + `BossHealthOverlayAccessor` (mixin configs `elsewhere_overworld_bosses.stub*.mixins.json`).
  The core needs: a boss base with the hooks used here (`bossId, phaseThresholds, registerAttacks, bossTick,
  onPhaseChange, onFightStart/Reset/Won, rollLoot, tierSignature, isDamageGated, modifyIncomingDamage,
  addMinion/minions/minionCap, playersInArena, showCast, setAction/actionAnim`), arenas, the four damage types.
- Resources: `assets/elsewhere/**`, `data/elsewhere/**`, `data/minecraft/tags/**` merge as-is (tags use
  `replace: false`).

## Known gaps
- No custom boss models/animations yet (see above); item abilities listed above. World-gen sites were tested by
  generation only (no visual check of every site yet).
- Self-test drives phases with direct hits (FakePlayer) and skips the empty-arena reset (`testNoReset`); the reset
  path is exercised in play, not in the self-test.

## Beta 2 module tasks (20:30, module-task agent after the restart)
- **`overworldContent` switch** (`dev.elsewhere.overworld_bosses.OwbConfig`): reads the core's `ElsewhereConfig.get().overworldContent` reflectively when the core has that field, else the top-level `"overworldContent"` key in `config/elsewhere.json`, default true; re-read at server start. When false: `OwbStructure.findGenerationPoint` places no boss site (Croaking Shrine, Sun Chamber, Glacier Shelf, Wreck Ring), Tidewrack Drowned refuse natural/structure spawns (existing Wreck Rings too), and Strawgrim's farm ritual does nothing. Murder Crows, Mire Biters etc. only come from their bosses, so they are covered.
- **Loot tables**: the 5 old-format entity tables converted to 26.3 keys (before: the Siren Conch dropped from every Tidewrack Drowned, every set_count ignored).
- Self-test: **56 passed, 0 failed**, 0 ERROR lines (`run/selftest-5.log`, desktop): adds overworldContent=false (no sites placed, drowned refuse spawns, default true) and the Siren Conch 8% / rotten flesh 0–2 loot checks.

## 0.4.0 bosses fixes (arena seal, QA issue 12)
- **Root cause:** QA's Vorlagh run had two overlapping arenas (a summon-path arena plus a direct spawn that built its default arena around itself; 1257 seal blocks in the self-test repro vs QA's 1272). (a) A boss removed without dying or resetting (discard, dimension change) never restored its arena, so its whole wall stayed. (b) The first arena's restore wrote its snapshot over the second, still-live arena's wall, and the second arena's snapshot had recorded the first arena's seals as air (water arenas got air holes).
- **Fix (stub framework, all 5 bosses):** new `stub/overworld_bosses/ArenaSeals.java` keeps a level-wide record of every seal placed: the block that was there before it and which arenas still need it. `BossArena.seal` places through it, `capture` records a foreign seal as the block underneath, and `restore`/`unseal` release this arena's claims (a seal another live arena holds stays up) and never write a seal back. `ElsewhereBoss.beginFight` and load set the arena's owner. An arena is live only while its boss is loaded, alive and still owns it. `ElsewhereBoss.remove` restores the arena on any removal except a chunk/player unload (the boss reloads, resets and restores its snapshot then). A sweep runs once a second (`ServerTickEvents.END_SERVER_TICK`, registered from `BossRegistry.init`) and lifts any tracked seal with no live owner back to its old block, waiting for unloaded chunks to load. It only touches positions it placed, never other modules' seals.
- **Self-test 72/72** (was 56): for each of the 5 bosses, a check that the seal was placed and every seal block is tracked, and that after death 0 seals are left and every seal spot holds its old block. Vorlagh edge cases: two overlapping arenas (1257 seals), first killed → the live one keeps its whole wall (637/637); both killed → 0; discarded mid-fight → 640 → 0; a direct spawn discarded while a summoned one fights → the wall stays whole, then 0; an orphaned seal with no boss is swept (640 → 0, 0 tracked). Log: `run-desk/selftest-0.4.0-seals.txt`.
- Changed files: `stub/overworld_bosses/{ArenaSeals (new), BossArena, ElsewhereBoss, BossRegistry}.java`, `overworld_bosses/dev/SelfTest.java`.
