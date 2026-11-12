# petalwind_glasswaste — Petalwind Isles, The Glasswaste, Kazehana, Tempestra, Heliodor, Ankhara

Dev mod id `elsewhere_petalwind_glasswaste`; every registry id, asset and data file is in the `elsewhere` namespace.
Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2+26.3 (full artifact), Java 25. Dev port 25621.

## Status (beta freeze 19:35)

- Compiles (main, client, gametest); `dist/elsewhere_petalwind_glasswaste-0.1.0.jar` built.
- Server boots with zero errors in the log. Self-test **31 passed, 0 failed** (`run/selftest-1.log`): both dimensions
  generate with safe arrival spots, guards, trip-damping simulation (living 100 h at a desert spot: 0.91 trips; explorer
  15 h: 0.88 from this module's triggers), and all four bosses spawn in their arenas, run phases 1→3, die and drop loot
  (Kazehana Draw Cut, Heliodor mirror crack + arena reset/heal, Ankhara erupts and ring collapse checked). The lethal-hit
  guard can't be measured on a FakePlayer (they ignore damage) and is logged as SKIP.
- Run: `./gradlew runServer` (needs `pause-when-empty-seconds=0`, already in run/server.properties) or `S/desk.sh <dir> runServer`.
- Screenshots: `./gradlew runClientGameTest` → `screenshots/`: `items.png` (every item in glow frames), `petalwind.png`
  (Blossom Crowns islands), `glasswaste.png` (Ember Dunes), `kazehana.png` (Petal Pagoda roof, bowing), `tempestra.png`
  (Eye of the Gale), `heliodor.png` (Ziggurat summit), `ankhara.png` (the Sunpit), `petalwind_mobs.png` (Skywhale,
  Windwright, Cumulus, Blossom Breeze, Gale Kite), `glasswaste_mobs.png` (Sun Mote, Dune Factor, Revenant, Sandswimmer,
  Glass Scarab). Self-test after beta 2: 31/31 again (`run/selftest-2.log`).

## What is built

### Dimensions (data in `data/elsewhere/{dimension,dimension_type,worldgen}`)
- `elsewhere:petalwind` — floating islands (vanilla floating-islands density router, own climate noises), 5 biomes
  (`blossom_crowns`, `gale_steppes`, `hanging_gardens`, `cloud_nine`, `stormrim`) with the doc's sky/fog/water colours,
  particles and music; permanent sunrise (fixed `visual/sun_angle` 280, pink sunrise colour), cloud sea at Y 72.
  Features: Skyblossom trees, cherry flowers, Zephyrite ore, Updraft Vents, the Petal Pagoda (with a dormant Kazehana).
  Rule: below Y −16 you drop into the Overworld sky at the same X/Z, Y 330, Slow Falling 25 s.
- `elsewhere:glasswaste` — the overworld terrain shape, dry (air fluid), sandstone body; fixed noon; ultrawarm-style
  attributes (water evaporates, fast lava), bed rule "The sun never sets" (can set spawn, can't sleep), monsters spawn in
  full light. 5 biomes (`ember_dunes`, `glass_flats` (glass surface), `spirefields`, `cinder_basin` (basalt/magma),
  `bonebed_hollows`) with doc colours/particles/music; quicksand and scorched-sand patches; Sunstone ore; Sunwright
  Ziggurat (Heliodor) and Sunpit (Ankhara) placed as features.
- Glasswaste Heat (server `glasswaste.Heat`, HUD bar above the hotbar via payload `elsewhere:glasswaste_heat`): builds in
  direct sun, drops in shade/water, water bottle −40, Cactus Water resets; at 100 Nausea + Slowness, then fire ticks.
  Glare: standing on glass under the sun without a helmet blinds in pulses (any helmet protects outside boss fights;
  Sunwright Visor / full Sunwright set always).
- Attunement data: `data/elsewhere/attunement/{petalwind,glasswaste}.json` (section 8 ingredients).

### Blocks
`skyblossom_{log,wood,planks,stairs,slab,fence,fence_gate,door,trapdoor,button,pressure_plate,leaves,sapling}`,
`stripped_skyblossom_{log,wood}`, `cloud_block`, `cumulus_puff`, `updraft_vent` (block entity `elsewhere:updraft_vent`),
`windchime`, `zephyrite_ore`, `sunglass` (glows only under open sky), `scorched_sand`, `quicksand`, `mirror_block`,
`sunwright_bricks`, `sunwright_brick_{stairs,slab,wall}`, `chiseled_sunwright_bricks`, `sunstone_ore`, `sun_mirror`.

### Items
Petalwind: `zephyr_feather`, `zephyrite`, `blossom_seal`, `zephyr_key`, `cloudbread`, `petal_glider` (glider; rockets
blocked; extra drag client-side), `skyhook`, `zephyrite_{sword,pickaxe,axe,shovel,hoe}`, `galeweave_{hood,robe,leggings,boots}`
(full set: mid-air jump, less fall damage via the stub set-bonus API), `petalstep_sandals`, `kaze_katana`, `zephyr_wings`,
`gale_heart`. Glasswaste: `sunglass_shard`, `sunstone`, `eel_tooth`, `sandswimmer_scale`, `burning_glass`, `cactus_water`,
`sunwright_visor`, `eel_tooth_spear`, `sunstone_{sword,pickaxe,axe,shovel,hoe}`, `sunwright_{helmet,chestplate,leggings,boots}`,
`heliograph_lens`, `sunwright_plate`, `glassfang`, `gizzard_stone`. Spawn eggs for the 8 mobs. Creative tabs
`elsewhere:petalwind`, `elsewhere:glasswaste`. All numbers live in `dev.elsewhere.petalwind_glasswaste.GearStats`
(BALANCE.md final values applied where listed below).

### Mobs (`elsewhere:`)
`gale_kite` (circles, wind charges, dives), `blossom_breeze` (Breeze + blinding petal gust), `cumulus` (harmless bouncy
cloud, Jump Boost II when you land on it, splits), `skywhale` (HappyGhast subclass: harness and ride), `glass_scarab`
(burrows when hurt, resurfaces behind you), `sandswimmer` (swims under sand, erupts to bite; 5 segments drawn with the
shared `SerpentChain`), `sunwright_revenant` (Husk in gold, rides a camel husk, camel dash), `sun_mote` (Blaze + flash).

### Bosses (shared stub framework `dev.elsewhere.stub.petalwind_glasswaste.BossBase`)
Boss bar, HP-threshold phases with 2 s invulnerable transitions, attack-name flashes, arena seal ring (particles),
arena snapshot + restore on reset and after death, 30 s drop-out, party HP scaling, per-player loot + first-kill tag,
overpower guard (hits >24 halved past 24, before armor) and lethal-hit guard (≤60% max HP) in `BossGuardMixin`.
Scripted attacks use damage type `elsewhere:pg_boss_attack` (scaling never) × the doc's Easy/Hard factors.
- `kazehana` (340 HP, 10/4): dormant & bowing until you sneak twice (or hit him). Draw Cut (white floor line 0.8 s,
  36 raw), 3-hit combo (20 raw), Petal Parry (reflects melee), phase 2 wind charges to the edges + wind-bell (windchime)
  stuns + 2 Blossom Breezes, phase 3 petal clouds + 3 `petal_mirage` copies (1 HP; only the real one has a shadow ring),
  finishing Draw Cut across the roof. Arena: Petal Pagoda.
- `tempestra` (520 HP, 8/4): ring shield (×0.2 damage, opens 8 s every 20 s), Gust Volley (5 wind charges), Downburst
  (red circle 1.5 s, 42 raw), phase 2 levitation pulses + cyclone bolts (16 raw + knock-off) + 2 harnessed Skywhales,
  phase 3 lightning on marked spots (30 raw) + Gale Kites. Arena: Eye of the Gale (8 calcite platforms, updraft basin).
- `heliodor` (500 HP, 20/8): Solar Lance (2 s charge, shade blocks it, 44 raw), fist slam (30 raw), sun pulse every 8 s:
  each Sun Mirror throws a beam along its aim; a beam through Heliodor cracks 4 armor and stuns 3 s. Phase 2 Heat ×2,
  magma vents, Sun Motes; phase 3 sweeping beam on a scorched path (44 raw), mirrors on the path turn it back at him.
- `ankhara` (560 HP, 12/6): burrowed = untargetable (wake particles); Erupt (red circle 1.5 s, 45 raw, every 8 s / 4 s
  under 10%), Lunge (32 raw; hitting a glass pillar stuns it 5 s and exposes the gizzards, ×1.5 damage), phase 2 outer
  sand ring collapses into magma (3 s warning) + Glass Scarabs, phase 3 middle ring collapses. 13 body segments rendered
  along the head's trail.

### Dev tools (dropped at merge)
`/elsewhere_dev tp petalwind|glasswaste`, `/elsewhere_dev home`, `/elsewhere_dev boss kazehana|tempestra|heliodor|ankhara`
(builds the arena and spawns the boss; Kazehana: sneak twice to bow and start). `DevMain`, `DevCommands`, `SelfTest`.

### Accidental trips
`PetalwindGlasswasteContent.TRIPS`: `petalwind.long_fall` (ACTIVITY 15%), `petalwind.wind_launch` (ACTIVITY 8%),
`glasswaste.desert_noon` (LOCATION 15%, day time 5000–7000, 30 s, once per day) — BALANCE.md final base chances.
The stub framework (`TripRules`, `TripStub`) applies familiarity damping, hourly budgets, 2 h cooldown, 60 min minimum,
pity, the 3 s cancellable pull, safe arrival. Note: BALANCE says familiarity also applies to ACTIVITY triggers — the stub
only damps LOCATION ones (core owns the real rule).

## Beta 2 (ARSENAL.md, after the freeze)
- Gale Heart (10 Glimmer, 2 s use delay, 10 s updraft) and Heliograph Lens (6 Glimmer, 1 s use delay) spend Glimmer via
  `stub.petalwind_glasswaste.GlimmerStub.trySpend(player, cost)` (always true until merged with the arsenal module).
- Dodge: `ArsenalHooks` looks up `elsewhere:dodge_chance` by id and applies a transient modifier `elsewhere:pg_gear_dodge`
  (+0.03 per Galeweave piece, +0.10 with the Kaze Katana in hand); skipped when the attribute or the player's instance is absent.
- Traders: `elsewhere:windwright` (Petalwind, rare creature spawn + spawn egg) sells Skyhook, Cloudbread, Zephyr Feathers and,
  by id, `kitestring_staff` (8 Zephyr Feathers + 2 Zephyrite) and `bell_of_the_flock` (2 Blossom Seals + bell);
  `elsewhere:dune_factor` (Glasswaste) sells Burning Glass, Cactus Water, buys Sandswimmer Scales and, by id,
  `sunlance_staff` (Amethyst Wand + 12 Sunglass Shards) and `glimmerstone_focus` (10 Sunglass Shards + 4 amethyst shards).
  Both are stay-put wandering-trader subclasses; trades for missing ids are skipped.
- Tempestra drops `stormsage_mantle` by id (50% on first kill, 10% after) when the arsenal module registers it.

## Changed from the docs (beta)
- Ankhara's 14-segment body is drawn client-side from the head's trail; the per-segment child hitboxes are not built.
- Tempestra's moving shard platforms are static calcite platforms; Downburst damages instead of slamming a platform;
  ring-shard weak points are a timed shield window; cyclone bolts are an instant hit-scan.
- Heliodor's `solar_beam` entity is drawn with particles; arena mirrors are fixed blocks you rotate (right-click).
- Sunpit scaled to a 45-block crater; Petal Pagoda/Ziggurat/Sunpit are features (not jigsaw structures).
- Mob/boss models are generated box models (`tools/gen_models.py`) with procedural animation; no armor/held-item
  layers on the Revenant. No custom sounds or music (vanilla sounds reused).
- Gear numbers: BALANCE.md final values applied for trips, Zephyrite tools, Kaze Katana cooldown, Zephyr Wings
  (640, 1.5 s boost, armor 0), Gale Heart 10 s, Sunstone tools (1800, 9, netherite-tier mining, ignite 3 s), Sunwright
  durability, Sunwright Visor 3/2, Glassfang 8/1.5 pierce 3, Heliograph Lens durability 256.

## Disabled / not done for beta
- Return Rift structure set (needs the core's `return_rift` piece) — not added yet.
- Breeze Temples, Kite Markets, Cloud Bakeries, Heliograph Towers, Glass Necropolis, Caravan Wrecks; Windwright and Dune
  Factor traders; music discs "Updraft"/"Zenith"; custom sky/cloud renderers beyond environment attributes; Spirefield
  glass columns.
- BALANCE.md items not yet implemented: Sunwright smithing line (ingot, template, per-piece perks, Noonfire set bonus),
  `gale_pinion` smithing for Zephyr Wings, Galeweave per-piece fall reduction (applied as a full-set −40%), Petalstep 1 s
  cooldown, Sunstone daylight/underground mining-speed swing, `elsewhere:glare_immune` item tag.

## Merge notes
- Call `PetalwindGlasswasteContent.init()` from the core initializer and `PetalwindGlasswasteClient.initClient()` from the
  core client initializer; drop `DevMain`, `DevClient`, `DevCommands`, `SelfTest` and the dev `fabric.mod.json`.
- Replace `dev.elsewhere.stub.petalwind_glasswaste.*` with the core's framework: BossBase (bar/phases/seal/snapshot/
  loot), Telegraph (decals), TripTrigger/TripStub/TripRules/SafeSpot, SetBonus, SignatureGearStub (registers
  `challengers_cleaver`, `phasewalker_greaves`, `stormcrest_helm` only if absent), BossGuardMixin (guards).
- Mixins: `BossGuardMixin` (LivingEntity damage pipeline), `ConsumableMixin` (water bottles cool Heat).
- Resources: copy `src/main/resources/{assets,data}/elsewhere` and the `data/minecraft/tags` additions.
- Return Rifts: the core places them with its `elsewhere:return_rift` feature for dimensions that opt in. At merge, register
  `elsewhere:petalwind` and `elsewhere:glasswaste` in the core's `ElsewhereDimensions` with `returnRifts = true` and add
  `"elsewhere:return_rift"` to the surface-structures step (index 4) of the 10 biomes in `data/elsewhere/worldgen/biome/`
  (not done here because the module must boot on its own without the core's feature).
- Arsenal/summoner: point `GlimmerStub.trySpend` at the arsenal Glimmer pool; `ArsenalHooks` needs nothing (id lookups).

## Beta 2 module tasks (module-task agent, 20:29)

Changes on top of the "Beta 2 (ARSENAL.md)" section above:
- **Dodge is now a real item attribute** (shows in tooltips like vanilla): `ArsenalHooks.init()` registers a Fabric
  `DefaultItemComponentEvents.MODIFY` callback that looks up `elsewhere:dodge_chance` by id and adds ADD_VALUE modifiers
  `elsewhere:pg_galeweave_dodge_{head,chest,legs,feet}` (+0.03 each, own armor slot) and `elsewhere:pg_kaze_katana_dodge`
  (+0.10, main hand). MODIFY runs after every initialiser, so the arsenal attribute exists by then in the merged mod; when
  it's absent (this dev mod) it logs one info line and skips. The old per-tick transient modifier (`ArsenalHooks.tick`)
  is gone. Numbers: `GearStats.GALEWEAVE_DODGE_PER_PIECE`, `KAZE_KATANA_DODGE`.
- **Glimmer items:** Gale Heart now checks the target block before spending (no Glimmer lost on an invalid block); both
  Gale Heart and Heliograph Lens send "Not enough Glimmer" (`message.elsewhere.not_enough_glimmer`) on a failed spend
  and do nothing else; the Lens damage is multiplied by `elsewhere:spell_power` (looked up by id, ×1.0 when absent).
  `GlimmerStub` gained `denyForTest` / `spentForTest` (dev/self-test only).
- **Trades by id** (ARSENAL rev. 3 numbers, in `GearStats`): Windwright Kitestring Staff = 12 Zephyr Feathers + 2 Zephyrite
  (was 8), Bell of the Flock = 2 Blossom Seals + bell; Dune Factor Sunlance Staff = Amethyst Wand + 12 Sunglass Shards,
  Glimmerstone Focus = 10 Sunglass Shards + 4 amethyst shards. Missing ids are skipped.
- **Tempestra → Stormsage Mantle** stays as the builder wrote it in `Tempestra` (id lookup, 50% first kill, 10% after).
  Not changed to 20% after: the boss class belongs to the artists now, and ARSENAL §4b only gives "50% first kill".
- **Per-player display settings** (BALANCE.md §D, revision 4). Client stub `dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub`
  (main source set only so the self-test can check parsing; no client classes) reads `config/elsewhere-client.json` in the
  §D4 format (`"meters": {"heat": "auto"|"always"|"hidden"}`, flat `hudHidden`, `screenEffects`, `reduceMotion`; it also
  accepts a flat `heatMeter`), re-read on mtime change (≤ once per second). API shared with the other module stubs:
  `registerMeter(key)`, `registerToggle(key, def)`, `meter(key)` → `Mode.AUTO/ALWAYS/HIDDEN`, `toggle(key, def)`.
  - Meter `heat`: `auto` (default) = in the Glasswaste, or Heat above 0 anywhere, hidden 2 s after it settles at 0;
    `always`; `hidden` = no bar, an action-bar line at Heat 75 and 100 instead (`hud.elsewhere.heat.high/.max`).
  - `screenEffects` (default true): new `HeatEffects` HUD layer (drawn first, under the HUD): warm vignette + rising
    heat-shimmer bands from Heat 60, and a white glare wash while the server reports glare. Off = none of it.
  - `reduceMotion` (default false): no shimmer or vignette (§D2: shimmer is pure motion); the glare wash becomes a
    static tint.
  The glare Blindness pulses and heat Nausea are gameplay and stay (hiding a display never hides the hazard).
  `Heat.sync` now also resends when the glare flag changes.
- **Merge:** repoint `ClientSettingsStub` at the arsenal module's shared client settings (keybind "Toggle Elsewhere HUD",
  `/elsewhere_hud`), keeping the keys above; `GlimmerStub` at the arsenal pool; `ArsenalHooks` needs nothing.
- Self-test 40/40 (`run/selftest-4.log`): new `checkBeta2()` (dodge skip/apply + modifier helper, Windwright/Dune Factor trades by id, Stormsage lookup,
  Gale Heart and Heliograph Lens Glimmer spend / denial / use delay, settings parsing).

## 0.4.0 bosses fixes (bosses agent)
- **Boss summons never outlive the fight (QA 0.3.0 issue 9).** New `stub/petalwind_glasswaste/BossSummons` (generic, keep
  it at merge; `Bosses.init()` calls `BossSummons.init()`). Every entity a boss spawns goes through
  `BossSummons.spawn(boss, level, e)`, which tags it `elsewhere.boss_summon` and binds it to the boss:
  Kazehana's Gale wind charges, Cherry Storm petal clouds, petal mirages and phase-2 Blossom Breezes; Tempestra's volley
  wind charges, harnessed Skywhales and Gale Kites; Heliodor's Sun Motes; Ankhara's Glass Scarabs (all four bosses had
  the same leak: their adds were never tied to them). Cleanup paths:
  - `BossBase.die`, `BossBase.resetFight` and a new `BossBase.remove(RemovalReason)` (discard, unload, kill) discard
    every live summon of that boss at once;
  - a sweep every 10 ticks discards a summon whose owner is gone, dead or out of combat, and any summon older than
    `MAX_LIFETIME` (3600 ticks);
  - a tagged summon that loads again without a live binding (server restart, chunk reloaded after the fight) is
    discarded on the next tick.
  Note on QA's count ("~55-73 elsewhere entities within 64 blocks 10 s after Kazehana died"): it counts every
  `elsewhere:` entity, so it also includes Petalwind's natural spawns (gale_kite, blossom_breeze, cumulus are in every
  Petalwind biome's spawn list and a real player keeps the mob cap filled). Only the boss's own adds were a leak; those
  are now 0. Natural Petalwind mobs near an arena are expected.
- **Stormsage Mantle** (Tempestra): 50% on a player's first kill, **20%** on repeat kills (was 10%):
  `Tempestra.STORMSAGE_FIRST_KILL/STORMSAGE_REPEAT`, `Tempestra.stormsageChance(firstKill)`. First kill is per player
  (BossLoot tag `elsewhere.killed.tempestra`; the core's personal-loot first-kill flag replaces it at merge).
- **Self-test** (`SelfTest`): after each of the 4 boss kills it waits 200 ticks and checks that 0 of the boss's fight
  summons are left within 64 blocks (and lists any `elsewhere:` entities there); Kazehana discarded mid-fight with 3
  bound summons leaves 0 one tick later; an orphaned tagged summon is discarded on the next tick; a summon whose owner
  vanishes is swept within 1 s; Stormsage chance 0.50/0.20 (constants + 20000-kill sample) and per-player first kill.
- Result on the desktop (copy `S/work/petalwind_bosses`, 21:51): **51/51 PASS**, 0 ERROR lines (40 earlier checks + 11 new).
  Fight summons left 10 s after death: Kazehana 0 of 9, Tempestra 0 of 17, Heliodor 0 of 0 (no vent that run), Ankhara
  0 of 3. Remaining `elsewhere:` entities near the arenas were all natural spawns (Petalwind: cumulus/gale_kite/
  blossom_breeze; Glasswaste: glass_scarab/sandswimmer/dune_factor/sunwright_revenant/sun_mote). Module compiles in place
  (desk compileJava + compileClientJava).
