# K-A army lane handoff (14:58 CST; hard hand-in 16:15)

**Build:** `scratchpad/ka.sh <gradle tasks>` from the K-A session. It rsyncs `ElsewhereContent/kingdom/` to the iMac `~/mcbuild/lane-k-a/kingdom`, runs Gradle with JDK 25 and fetches the logs. To rebuild it by hand: rsync with `--exclude build/ run/ .gradle/ *.jar dist/`, then `./gradlew build runServer`. Grep `run/selftest-kingdom.log` and `run/logs/latest.log` for FAIL, TOTAL, BENCH and ERROR.

## Done (all code compiles: compileJava and compileClientJava green at 14:58)
- **Jar 0.2.0** is published to `dist/elsewhere_kingdom-0.2.0.jar` (13:58) with a STATUS note. 0.1.0 is kept beside it. At that build the self-test was 53/53 PASS with 0 ERROR.
- **Items** (`item/KingdomItems.java` + item classes):
  - aldermark_crown, bodkin_bolt (BodkinArrow: armor treated as 25% lower), whetstone (15% repair), fletchers_bundle (8 arrows + 1 spectral), sergeants_horn (Strength I 8 s within 12 blocks, 90 s cooldown).
  - oathkeepers_seal: works through ALLOW_DEATH and leaves you at 6 HP with Resistance II for 2 s. It shares the 30 s lockout through `markDeathProtection`/`isDeathProtectionLocked`, and totems are tracked by their use stat.
  - Trim template, banner pattern, and Aldermark shield/banner heraldry (`applyHeraldry`).
- **Resources:**
  - Textures, models and lang.
  - Trim and banner pattern data, recipes, tags (`minecraft:arrows`, `testmeter:transmutation_excluded`).
  - 13 loot tables `data/elsewhere/loot_table/entities/kingdom_<unit>.json`, following the "Rewards at every tier" rates.
- **Look:**
  - Every unit wears real armor with a trim (`UnitType.trim`; iron for men-at-arms, lapis for knights, gold for officers, copper for archers; the aldermark pattern plus 1-2 vanilla patterns, picked per UUID). Done in `KingdomSoldier.dress()`/`applyLoadout()`.
  - Drop chances are 2% per armor piece and 4% for hands. Skins have been repainted to plain gambesons, and the tabard reads as a cloth surcoat.
- **Units:**
  - 13 JSONs in `data/elsewhere/kingdom_unit/`, with slightly weaker stats. The new ones are captain, standard_bearer, herald, surgeon, squire and pikeman, with entity types and eggs in `KingdomEntities`.
  - `UnitType` gains `ability`, `trim` and `aggression`, and Combat gains `sprint_chance`, `strafe`, `retreat_chance` and `mounted_bonus` (default 1.0 keeps Lords player-like). The old constructors are kept.
- **Combat** (`ai/SoldierMeleeGoal`, `RetreatGoal`):
  - Crit and sprint are rolled once per attack. Strafing is light, retreat is a chance, and knights shield-bash.
  - Pikemen deal x2 to mounted targets, and archers use a slower bow goal.
- **Formation and morale** (`Squad.tick`, `KingdomState.onDeath`):
  - Role slots: shields in front, pikemen and support in the middle, ranged at the back.
  - The sergeant calls the shield wall against archers.
  - A standard-bearer's death costs 25 morale. Heavy losses cost 15, morale under 20 breaks the squad, it regroups at 40, and morale regenerates out of combat.
- **Abilities** (`KingdomAbilities`): standard aura +10%, captain war cry, herald horn with reinforcements (`KingdomLod.callReinforcements`), surgeon and squire heals.
- **Load:**
  - Caps are 15 per player and 30 per area (`KingdomPerf.canSpawn`/`room`/`playerRoom`). The config migrates old 24/40 values.
  - LOD virtual squads (`KingdomLod`): virtualize beyond 96 blocks after 10 s, materialize within 80 with the same UUIDs and HP, move as a point. Dismissed hunts are removed.
  - Client LOD: `client/KingdomClientConfig` plus `/kingdomdetail low|medium|high`, with the renderer and tabard reading distance.
- **Behaviour:**
  - `KingdomAggression`: officers add 2 and captains 3. At 4 or more the player is marked and attacked on sight. At 6 or more a hunt may start (hourly-ish chance, 30 min cooldown, 5 min duration, decay 1 per day).
  - Daily routines: `Squad.setRoutine(PATROL|GUARD, ...)` (day patrols, return home at dusk, half rest at night, attacks wake them, bell at dawn).
  - Op command `/kingdom info|stress|company|patrol|aggression|hunt` (`KingdomCommands`).
- **API (additive):**
  - `KingdomApi.spawnCompany`/`spawnPatrol`/`createVirtualSquad`/`virtualize`/`materialize`/`addAggression`.
  - Events `HORN_BLOWN`/`HUNT_STARTED`, and `ArmyComposer` (cavalry about 10%).

## In progress: not yet run
- `dev/SelfTest2.java` (about 60 new checks: items, drops, look, combat, formation, morale, abilities, caps, LOD, aggression, routine) and the new `SelfTest.addBench(name, soldiers, observers)` load test (baseline; 30 mixed vs 30 zombies; the 15-per-player case; 2 players). Both compile but have **never been run**.
- Next step: `./gradlew build runServer`. Fix any FAIL by adjusting the test tolerance or the code. Likely fragile spots: the formation `fwd` thresholds, the herald horn timing (60 ticks), the routine night rest (needs `time set` to work in 26.3), the bodkin live arrow, and the loot rate windows.

## Remaining
1. Run the self-test until it is green with 0 ERROR lines, and record the TOTAL and BENCH MSPT lines.
2. Rebuild, copy `build/libs/elsewhere_kingdom-0.2.0.jar` to `dist/`, and update the sha in STATUS.
3. Update API.md: a 0.2.0 section covering the items, new units, unit fields, caps 15/30, LOD, routines, aggression, `/kingdom`, `/kingdomdetail` and events.
4. Append a final STATUS.md entry with the test counts, MSPT and the integrator lines below.

**Not verified:** nothing has been seen in game (no client): skins, trims, tabard, banner/shield pattern textures, client LOD.

## Integrator lines
- merge.py `ALL_MODULES`: add `"kingdom"`.
- In modules.json, under `"exclude"`, add `"kingdom": ["^dev/elsewhere/kingdom/DevEntry\\.java$", "^dev/elsewhere/kingdom/dev/.*$"],`
- Entries: `dev.elsewhere.kingdom.KingdomContent.init()` and `dev.elsewhere.kingdom.client.KingdomClient.initClient()`. There are no mixins.
- `lang/en_us.json` adds new keys only. The module also ships `data/minecraft/tags/item/arrows.json` and `data/testmeter/tags/item/transmutation_excluded.json` (both `replace: false`).
- K-B should call `KingdomApi.addAggression(player, 6)` for Lords and 10 for the King. The captain's 25% Survey fragment uses `elsewhere:lore_survey_fragment` and drops only if K-D registers that item.
