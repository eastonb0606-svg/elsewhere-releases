# nether_end_bosses — Elsewhere content module (dev mod id `elsewhere_nether_end_bosses`)

Five Nether / post-dragon End bosses for **Elsewhere** (Minecraft 26.3, Fabric, client + server).
Everything registers in the `elsewhere` namespace. Status as of the 2026-11-10 beta freeze.

## Build / run
- `JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home ./gradlew build` → `build/libs/elsewhere_nether_end_bosses-0.1.0.jar` (copied to `dist/`).
- Self-test: `tools/selftest.sh` (local, takes a slot) or `$S/desk.sh <this dir> runServer` (desktop). Logs: `run/selftest-N.log`, `run-desk/`.
- Screenshots: `./gradlew runClientGameTest` (hold `mcslot.sh acquire client`) → `screenshots/*.png`.
- Regenerate art: `$S/venv/bin/python tools/gen_assets.py` (items, blocks, equipment, lang, sounds, damage types, worldgen JSON),
  `$S/venv/bin/python tools/gen_models.py` (box models → Java `*ModelLayer` classes + matching entity textures).
- Dev commands (op): `/elsewhere_dev boss <grunthar|solaxis|mournmaw|ossyra|aphelion> [arena]` builds the arena at your feet
  and summons the boss (or only builds it, so you can use the real trigger); `/elsewhere_dev tp nether|end|overworld`,
  `/elsewhere_dev home`, `/elsewhere_dev attack <id>` (force an attack on the nearest boss), `/elsewhere_dev hp <ratio>`.

## What is built
| Boss | Entity | HP / armor / tough | Phases | Attacks (all telegraphed) | Real summon path |
|---|---|---|---|---|---|
| Grunthar & Ashtusk | `elsewhere:grunthar` (1.6×3) riding `elsewhere:ashtusk` (3×3), two linked entities, one phase machine in Grunthar | 170 + 190 base (551 eff.), 14/2 and 12/2 | 1 Mounted, 2 Dismounted (mount dead or both <50%: dismount leap, berserk), 3 Molten Hoard (<30%) | Ashtusk Charge (1.2 s pawing + red lane, 38), Tusk Toss (20 + launch), Gold Bombs (`elsewhere:gold_bomb`, lands, 2 s fuse, 34 at centre), Golden Axe cone (28), Call the Enforcers (Gilded Enforcers = shielded piglin brutes that block frontal hits + crossbow piglins), Molten Hoard (drip marks 1.5 s, lava fall 5 s, magma patches), Greed (drop a gold block: he walks over, picks it up, stands 3 s; max 3) | Use a gold block on the `elsewhere:gilded_throne` in the Treasure Hall |
| Solaxis | `elsewhere:solaxis` 3.5×6, hovering | 350 (536 eff.), 8/2 | 3 (66%, 33%); 3 rods eject per phase → 3 Pyre Shards (blazes ×0.6) | Rod Volley (rods glow white 1 s, one `elsewhere:pyre_bolt` per rod), Solar Flare (expanding fire ring, jump it, 30), Eclipse Core (`elsewhere:sun_orb` 28, punch it back for 30 to Solaxis), Supernova (20 s DPS timer on a second bar; deal 18% max HP or take cover, else 40 blast) ; snowballs deal 6 | Break all 4 blaze spawners in the Pyre Cradle (`elsewhere:pyre_cradle_core` in the middle) |
| Mournmaw | `elsewhere:mournmaw` 8×8×8, flying | 520 (796 eff.), 8/2 | 1 Lament (high strafe), 2 Descent (60%, low), 3 Grief Storm (30%) | Wail (inhale, Darkness + Mining Fatigue), Tear Rain (cyan circles, `elsewhere:soul_tear` 16 + soul fire), Soul Fireballs (`elsewhere:soul_fireball` 24, deflectable), Chain Whip (chains glow 1.2 s, red ring, 42), Mourners (skeletons with soul lanterns), Grief Storm (spiral tears; bone arches give cover). **Mercy ending**: place a dried ghast in the basin in phase 3, defend it 30 s (tears smother it) → she calms, mercy drops | Dormant, weeping above the Weeping Basin; walk within 20 blocks with a dried ghast in your inventory |
| Ossyra | `elsewhere:ossyra` 6×6×6 | 1100 (1683 eff.), 20/10 | 1 Shell, 2 Inversion (65%), 3 Void Bloom (30%) | Bullet Storm (shulker bullets 4 + Levitation; shell opens while firing), Shell Slam (closes 1.5 s, red ring, 44 to grounded players on her surface, then opens 4 s), Gravity Flip (chime + "▲ GRAVITY FLIPS ▲" subtitle 3 s, then gravity −0.08 for every participant, toggles), Shulklings (shulkers ×0.5 on floor and ceiling), Void Bloom (panels dissolve after 2 s reverse-portal warning), Surface Swap (teleports floor↔ceiling). Closed shell takes 25% damage | Use a shulker shell on the `elsewhere:matriarch_pedestal` (needs the Ender Dragon killed) |
| Aphelion | `elsewhere:aphelion` head 4×3 + 21 rendered body segments | 1500 (2295 eff.), 16/8 | 1 Coil, 2 Devour (70%), 3 Collapse (35%) | Coil (tightening portal ring with a glowing gap, 48), Void Breath (marked line, `elsewhere:void_breath` pools 6 per 0.5 s), Bite (purple line, 36), Devour dive (2 s purple beam, tunnels through the island, 42), Voidborn (endermen ×1.2), Collapse (an outer island falls into the void), Crystal Feast (eats an end crystal: +10% HP). Phase 3 sets player gravity ×0.5. Outside a coil in phase 1 it takes 35% damage | Use the dragon egg on the `elsewhere:halo_altar` with 4 end crystals nearby (needs the dragon killed); the egg is returned after the fight |

Framework behaviour (stub package, see below): 2 s invulnerable phase transitions with a roar and a bar flash; attack
names flash on the boss bar; overpower guard on hits to bosses; lethal-hit guard (mixin) on boss hits to players;
Easy ×0.6 / Hard ×1.25 on scripted damage (custom damage types with `scaling: never`); arena snapshot + seal
(`elsewhere:arena_seal` on the rim, void arenas skip the wall); 30 s drop-out; everyone gone → reset (arena restored,
minions removed, summon item returned; Mournmaw returns to weeping dormant); party HP scaling ×(1+0.5/extra player)
in the first 30 s; per-player loot (≥5% damage or 60 s inside) delivered as owner-locked items at the player's feet;
first kill guarantees the tier signature; void arenas return you to the ledge for −8 HP; music hook (client mixin
overrides situational music with the boss's track while it is active within 64 blocks).

## Registered ids
- Entities: `grunthar`, `ashtusk`, `gold_bomb`, `solaxis`, `pyre_bolt`, `sun_orb`, `mournmaw`, `soul_tear`, `soul_fireball`, `ossyra`, `aphelion`, `void_breath`.
- Blocks (+ block items): `gilded_throne`, `pyre_cradle_core`, `solaxis_ember`, `matriarch_pedestal`, `halo_altar`, `aphelion_heart`; stub: `arena_seal`.
- Items: `tyrants_tusk`, `ashtusk_harness`, `ashtusk_gland`, `crimson_fungus_on_a_stick`, `gold_bomb`, `ninefold_rod`, `pyre_crown`, `sun_orb`,
  `lament_harness`, `soulwick_lantern`, `mournmaws_blessing`, `mournmaws_tear`, `soul_tear`, `soul_fireball`, `inverted_crown`, `gravity_pearl`,
  `matriarchs_shell`, `void_scale`, `void_scale_elytra`, `wyrmfang`, `void_breath`; stub (shared tier gear): `challengers_cleaver`, `aegis_of_the_ladder`, `halo_of_the_outer_end`.
- Sound events: `music.boss.grunthar|solaxis|mournmaw|ossyra|aphelion` (sounds.json points at vanilla Nether/End tracks as placeholders).
- Damage types (stub data): `boss_melee`, `boss_projectile`, `boss_blast`, `boss_fire`, `boss_magic`, `void_return` + vanilla tag entries.
- Feature type `elsewhere:nether_end_boss_arena`; features/placed features `grunthar_treasure_hall`, `solaxis_pyre_cradle`, `mournmaw_weeping_basin`, `ossyra_matriarch_spire`, `aphelion_shattered_halo` (not added to biomes for the beta).
- Recipes: `void_scale_elytra` (smithing: void_scale + netherite chestplate + elytra), `crimson_fungus_on_a_stick`.
- Creative tab `elsewhere:nether_end_bosses`. Equipment assets `pyre_crown`, `inverted_crown`, `halo_of_the_outer_end`, `void_scale_elytra`.

## Boss art (Nether group: Grunthar + Ashtusk, Solaxis, Mournmaw)
- Packages `dev.elsewhere.bossart.{grunthar,solaxis,mournmaw,nether}` (client source set); textures
  `assets/elsewhere/textures/entity/boss/{ashtusk,grunthar,solaxisart,mournmawart}{,_emissive,_charged,_wail}.png`.
- Models are translated from the `hero=True` boxes of `Ideas/art/src/bosses_art.py` (grunthar(): the hoglin group and the
  `humanoid(s=1.6)` rider with crown and golden axe; solaxis(): molten head, basalt column, 9 rods in 3 tilted rings;
  mournmaw(): 9x9x9 mask body, slanted brows, glowing tear streaks, bone crown, 9 tentacles, soul chains + lanterns) by
  `tools/gen_bossart.py` (art box -> LayerDefinition cube with exact UVs, painted from the art palettes with noise and
  vertical shading, plus an emissive texture drawn through an `EyesLayer` for eyes, crown, blade, molten head, lanterns, tears).
- Keyframe animations (`AnimationDefinition`, applied from the bosses' synced `AnimationState`s): idle loops, walk
  (`applyWalk`), Golden Axe 0.8 s overhead wind-up + cleave, Gold Bomb throw, Greed admire, Ashtusk 1.2 s pawing, charge,
  Tusk Toss flick, Solaxis Rod Volley rear-and-snap, Eclipse swell, Solar Flare, Mournmaw Wail inhale, Chain Whip lash,
  phase roar for each, death (Grunthar arms, Solaxis rods fall + core implodes, Mournmaw dissolves; plus vanilla death tip).
- `dev.elsewhere.bossart.nether.BossArtNetherClient.initClient()` registers the layers and replaces the module's renderers
  for those four entity types; the dev client calls it after `NetherEndBossesClient.initClient()`. The old module renderers
  (vanilla-model Grunthar/Ashtusk, generated Solaxis/Mournmaw) stay as fallbacks.
- Screenshots: `screenshots/art/<boss>_{idle,attack,roar}.png`. Ossyra and Aphelion keep the module's own generated models
  until their artist delivers.

## Beta 2 (ARSENAL.md, after the beta-1 freeze)
- Ninefold Rod: spends **6 Glimmer** (a spell) with a 1 s use delay, durability 250 (was a 6 s cooldown, 64 uses).
- Gravity Pearl: spends **12 Glimmer** with a 2 s use delay; still refused inside a boss arena (no Glimmer spent then).
- Wyrmfang: +1.0 `minecraft:entity_interaction_range` while held in the main hand (looked up by id, skipped if absent; a transient
  player modifier, because the spear's default attribute component can't be read safely at registration time).
- Glimmer comes from `dev.elsewhere.stub.nether_end_bosses.GlimmerStub.trySpend(player, cost)` (stand-in pool: max 20, 3/s regen after 1 s;
  creative is free). At merge, point it at the arsenal module's pool.

## Gear numbers
All in `dev.elsewhere.nether_end_bosses.GearStats`, set to BALANCE.md (final): Tyrant's Tusk 9 / 1.0 (diamond axe, 1561),
Ninefold Rod (now Glimmer, see Beta 2), Pyre Crown 3/2 (burning ticks don't hurt, +1 attack while on fire), Lament Harness +25% happy-ghast
flying speed, Mournmaw's Blessing Regen II 10 s + Resistance I 5 s (mercy only), Inverted Crown 3/2, gravity −25%, safe fall +6,
Gravity Pearl gravity ×0.25 for 8 s (Glimmer, not inside a boss arena), Void Scale → smithing Void-Scale Elytra 6/2, 864, Wyrmfang
netherite spear stats with 1600 durability. Drop rule G7 (`GearStats.signatureDrop/otherDrop`). Stub signature gear: Cleaver 7 dmg
+10% vs bosses, Aegis 900 / 0.5 s raise / 8 s axe disable, Halo 3/3, rescue once per 10 min for −4 HP.

## Self-test (latest: `run/selftest-6.log`)
Builds every arena, summons each boss (Solaxis through its real spawner trigger), checks base HP / armor / hitbox, the
overpower guard on a real hit, the lethal-hit guard, forces every attack of every phase to completion, crosses every phase
threshold (Grunthar by killing Ashtusk), checks transitions (invulnerable), rod ejection + Pyre Shards, the Supernova
fizzle, void returns and gravity flip (Ossyra), half gravity (Aphelion), Mournmaw's mercy ending, kills each boss, checks
first-kill loot + tier signature per player, minion cleanup, arena restore after win and after a drop-out reset.
Run 4 (before the last fix): 109 PASS / 1 FAIL (Mournmaw arena restore: tears still in flight landed after the restore —
fixed by removing boss projectiles before restoring); its Aphelion part crashed only because classes were recompiled under
the running server. Known test limits: FakePlayers are invulnerable, so damage *to* players is checked through the guard
functions, not through hits.

## Changed from the docs / not done yet
- **Aphelion is not a true multipart entity for the beta**: the head is the only hitbox; the 21 body segments are rendered
  along the head's path on the client. The multipart mixins and `BossPart` (stub) are written and loaded but unused, so
  there is no hitbox desync risk. Rift scales are modelled as "outside a coil, 65% less damage".
- Rift gateways are not placed: emergence from end-gateway beams is replaced by the 2 s purple warning column (no real `end_gateway` blocks, to avoid accidental teleports and world edits).
- Grunthar's death ends the fight (a riderless Ashtusk collapses); Ashtusk's death drops Grunthar into phase 2.
- Treasure Hall / Pyre Cradle are standalone bastion-/fortress-style structures (not inserted into vanilla bastions/fortresses).
- Grunthar and Ashtusk use the vanilla piglin-brute and hoglin models (scaled 1.6× / 2.1×, tinted); Solaxis, Mournmaw, Ossyra and Aphelion have their own generated box models with animations (Solaxis has a keyframe roar).
- Matriarch bullets are vanilla shulker bullets (owned by Ossyra, so the guards apply); no separate `matriarch_bullet` entity.
- Wyrmfang's armor pierce / airborne bonus, Inverted Crown's ×1.25 knockback con, Blessing's 30 s lockout (core helper G4), the boss-weak-point glow of the Cleaver, the colourblind palette and decal-size options are not implemented.
- Boss bars are vanilla bars (colour/overlay per boss, a second bar for Ashtusk and for the Supernova timer); no custom frames.
- Music: placeholder vanilla tracks; no phase music shift.
- Ceiling walking during Ossyra's gravity flip uses vanilla physics with negative gravity (you fall to the ceiling and can move; jumping off the ceiling is not inverted).

## Disabled for beta
- **Arena world generation** (`NetherEndBossesContent.WORLDGEN = false`): the features exist as data and code, but are not added
  to biomes; Mournmaw/Ossyra/Aphelion builders return no world-gen origin. Arenas come from `/elsewhere_dev boss <id> [arena]`.
  The in-arena summon triggers (throne, spawners, dormant Mournmaw, pedestal, altar) all work on dev-built arenas.

## Merge instructions
- Call `dev.elsewhere.nether_end_bosses.NetherEndBossesContent.init()` from the core's main entrypoint and
  `NetherEndBossesClient.initClient()` from its client entrypoint. Drop `DevMain`, `DevClient`, `DevCommands` (dev only), `SelfTest`, `TickScript`.
- Keep packages `dev.elsewhere.boss.{grunthar,solaxis,mournmaw,ossyra,aphelion}` (+ `.client`), `dev.elsewhere.nether_end_bosses`.
- Swap `dev.elsewhere.stub.nether_end_bosses` for the core's framework. What the bosses use from it:
  `ElsewhereBoss` (abstract `LivingEntity`-based `Monster implements Enemy`: `bossId()`, `tier()`, `barColor/Overlay()`, `music()`,
  `defaultAttacks()` + `tickAttack(level, spec, t)`, `phaseThresholds()`/`computePhase()`, `onPhaseStart`, `bossTick`,
  `modifyIncomingDamage`, `createArena/rebuildArena`, `rollDrops/rollVariantDrops`, `onFightReset`, `primary()`; helpers
  `hit()`, `source()`, `scaled()`, `nextTarget()`, `nearestTarget()`, `targets()`, `forceAttack()`, `setFlag/hasFlag` (bits from `FLAG_CUSTOM`)),
  `BossFight` (`minion()`, `minionCount/minionCap`, `addBar/removeBar`, `flash`, `victory(level, variant)`, `reset`, `arena`),
  `BossArena`, `AttackSpec`, `Decals`, `Guards`, `LinkedBodies`, `PersonalLoot`, `SetBonus` (hook only), `StubFramework` (damage-type
  keys, `FIGHT_GRAVITY` modifier id, arena seal + shared signature gear), client `BossRenderer`/`BossRenderState`/`BossMusic`,
  mixins `LevelMixin`/`ServerLevelMixin` (multipart parts), `LivingEntityMixin` (lethal-hit guard), client `MinecraftMusicMixin`.
- Stub resources to drop if the core ships its own: `data/elsewhere/damage_type/*`, `data/minecraft/tags/damage_type/*`,
  `assets/elsewhere/{blockstates,models/block,items}/arena_seal.json`, textures for `arena_seal`, `challengers_cleaver`,
  `aegis_of_the_ladder`, `halo_of_the_outer_end` (+ equipment asset).
- Shared registrations (`arena_seal`, signature gear) check `BuiltInRegistries.*.containsKey` first, so an earlier module or the core wins.
- Soft tie-ins: Test Meter (Ashtusk Gland adds 150 ng/dL via reflection when `testmeter` is loaded). Green Leaf lighter tag for the Solaxis Ember is not added yet.

## Beta 2 module tasks (20:34, module-task agent after the restart)
- **Loot tables**: the 7 block tables converted to 26.3 keys (`condition: {"type": "minecraft:survives_explosion"}`; the old `conditions` key was ignored, so blocks always dropped even when blown up).
- Self-test (desktop, `run/selftest-7.log`): **131 passed, 0 failed**, 0 ERROR lines. `checkBeta2()`: Ninefold Rod Glimmer (three 6-Glimmer casts from a 20 pool, fourth refused), Wyrmfang +1.0 `entity_interaction_range` while held and removed when put away (`AphelionContent.wyrmfangReach` is now public for the test), and the survives_explosion condition (Gilded Throne always drops when mined, ~25% after a radius-4 explosion).
