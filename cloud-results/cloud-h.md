# cloud-h results
Tested commit: 284f38cc2692d2ef9873a2cdac49ad21c93c90d7 (cloud-src). Newer head 96b5b4f seen during the run; re-run follows.
Machine: 4 cores, 15 GB RAM, Linux 6.18, Temurin JDK 25.0.5

## Merged jar (ElsewhereBeta)
- Earlier head 7d3350a: build FAILED (StandingCommand missing, EntityType.ARMOR_STAND). Build passes at 284f38c.
- merge.py --sync needs snap/<module>/src pre-created on a fresh box (rsync exit 11 otherwise; pre-created, not a source edit).
- merge-report: JAVA-DUP 0, MISSING 0, PATCH-FAILED 0, ENTRYPOINT-MISSING 0, EXTRA-MISSING 0, RES-DUP 69 (all kept/dropped resolutions)
- build dist: BUILD SUCCESSFUL (48s)
- Combined runServer smoke: **[betatest] TOTAL 332 passed, 0 failed (34 info) - ALL PASSED**; /ERROR] lines: 0; wall 256s

## Self-test groups (one JVM each)
- **Group a** (wall 552s; /ERROR] 1; 4 WARN 'bad kingdom_townsfolk ... Components not bound yet' for mirewick/aldermark/azmar/hrafnholt):
  - elsewhere core: 70 passed, **4 failed**. FAIL (full):
    - struct3_core road on a 1:3 hillside: 41 cells, max step 1 half-blocks, 12 missing deck cells, 0 unsupported stone/earth cells
    - struct3_core lantern_road site 0 at 78160, 0, -70224: 55 cells, max step 1 half-blocks, 5 missing deck cells, 0 unsupported stone/earth cells
    - struct3_core lantern_road site 1 at 114384, 0, -93584: 71 cells, max step 1 half-blocks, 10 missing deck cells, 0 unsupported stone/earth cells
    - self-test crashed: NullPointerException: WeightedList.unwrap() because MobSpawnSettings.getMobsInCategory(MobCategory) is null
  - summoner: 126 passed, 0 failed
  - verdigris_ironpeak: TOTAL 218 passed, **6 failed**:
    - clan_hold: exactly one persistent clan_quartermaster spawns (1, then 1 more)
    - ironpeak return rifts: 11 of 12 located rifts are in foothills biomes below Y 400 (ruin included)
    - struct fit clan_hold: earth-sheltered (soil banked against both side walls on 0 of 11 rows)
    - struct fit summit_throne: no valuable blocks (4) and no lit campfires (0)
    - struct fit return_rift_verdigris_sea: no valuable blocks (65) and no lit campfires (0)
    - struct fit real clan_hold at 372, 519, 484: a 3.0 piece (v2) with its saved ground model, 1 of 220 floor cells floating on generated terrain
  - overworld_bosses: 181 passed, 0 failed; boss_ui: TOTAL 65/65 PASS; arsenal: TOTAL 128/128 PASS
- **Group b** (petalwind_glasswaste, greenhaze_hush, nether_end_bosses, gear2): wall 29s; /ERROR] 3. **Server crashed at start**: petalwind_glasswaste SpawnGroups3.selfTest(SpawnGroups3.java:183) NPE: MobSpawnSettings.getMobsInCategory(...) is null (same NPE as core). No TOTAL line for any of the 4 modules. ERROR lines: '[selftest] crashed', 'Encountered an unexpected exception', 'This crash report has been saved to ...'.
- **Group c** (gear3, bosshealth): wall 451s; /ERROR] 0; bosshealth TOTAL 1097 passed, 0 failed; gear3 PASS lines seen (82/82 defs), no gear3 TOTAL line found.

## Kingdom set (3 rounds of <=3 JVMs, superflat; per-module TOTAL, ERR count, FAIL lines)
```
## kingdom: TOTAL 352/352 PASS | ERR 0
## court: TOTAL 735/750 FAIL | ERR 0
[20:00:44] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL Azmar seed 7: nothing floating (no island off the ground; no footprint column over more than 1 air/fluid block, overhangs and spans excepted) (2 islands, 0 columns; islands: 1 blocks from 7,0,3 (Block{minecraft:lily_pad}) | 1 blocks from -7,0,3 (Block{minecraft:lily_pad}))
[20:01:33] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL Azmar seed 4242: nothing floating (no island off the ground; no footprint column over more than 1 air/fluid block, overhangs and spans excepted) (2 islands, 0 columns; islands: 1 blocks from 7,0,3 (Block{minecraft:lily_pad}) | 1 blocks from -7,0,3 (Block{minecraft:lily_pad}
[20:02:22] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL Azmar seed 20261112: nothing floating (no island off the ground; no footprint column over more than 1 air/fluid block, overhangs and spans excepted) (2 islands, 0 columns; islands: 1 blocks from -7,0,3 (Block{minecraft:lily_pad}) | 1 blocks from 7,0,3 (Block{minecraft:lily_
[20:02:41] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL Mirewick seed 20261112: nothing buried (no door, gate or window against terrain; terrain against at most 5% of the wall faces above their ground) (4 blocked, 1.0%; Block{minecraft:purple_stained_glass_pane} at 3,7,30 against terrain at 3,7,31; terrain against Block{minecraf
[20:05:40] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 1: the palisade ring is closed except at its open gates (10; gap at bearing 6 (10,32))
[20:05:41] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 1: nothing floating (every block of the fort and the shore works stands on the ground) (32; floating Block{minecraft:spruce_stairs} at -6,2,-23)
[20:05:41] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 1: the shore road has steps of at most 1 and meets the jetty at sea level (3 steps, 3 missing; no road at u 42 v 7)
[20:05:54] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 42: the palisade ring is closed except at its open gates (13; gap at bearing 6 (10,32))
[20:05:55] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 42: nothing floating (every block of the fort and the shore works stands on the ground) (32; floating Block{minecraft:spruce_stairs} at -6,2,-23)
[20:06:12] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 2026: the palisade ring is closed except at its open gates (8; gap at bearing 6 (10,32))
[20:06:12] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 2026: nothing floating (every block of the fort and the shore works stands on the ground) (32; floating Block{minecraft:spruce_stairs} at -6,2,-23)
[20:06:12] [Server thread/INFO] (Minecraft) [STDOUT]: [court-selftest] FAIL hraf seed 2026: the shore road has steps of at most 1 and meets the jetty at sea level (3 steps, 3 missing; no road at u 43 v 7)
## villages: TOTAL 287/300 FAIL | ERR 0
[19:57:42] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL ald-look: B: nothing the rebuild wrote floats :: 4 [BlockPos{x=297, y=-46, z=205}, BlockPos{x=297, y=-49, z=205}, BlockPos{x=297, y=-47, z=205}, BlockPos{x=297, y=-48, z=205}]
[19:57:42] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL ald-look: hill village: watchtower on the high point :: BlockPos{x=3006, y=-51, z=3006} ground -51 peak -42 best -51
[19:57:42] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL ald-look: hill village: nothing floating, no ground carved :: 4 floating [BlockPos{x=3006, y=-38, z=3006}, BlockPos{x=3006, y=-39, z=3006}, BlockPos{x=3006, y=-37, z=3006}, BlockPos{x=3006, y=-36, z=3006}], 0 carved
[19:57:43] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL ald-look: outpost: tower on the highest point within 24 :: floor -52 peak -41 tower BlockPos{x=2997, y=-51, z=3101}
[19:57:43] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL ald-look: outpost: plinth and palisade reach the ground (nothing floating, nothing carved) :: 76 floating [BlockPos{x=3000, y=-41, z=3101}, BlockPos{x=2998, y=-37, z=3101}, BlockPos{x=2994, y=-41, z=3100}, BlockPos{x=2996, y=-40, z=3099}], 0 carved, plinth 13 batter 16
[19:58:21] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL load test: full siege p95 tick cost at most 8 ms (server-thread CPU; lag budget) :: CPU p95 11.89 mean 7.32; wall p95 15.68 mean 8.29; machine load 3.0
[19:58:51] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] ALDERMARK part 150/156 FAIL
[19:59:07] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL [rivals] Hrafnholt waterfront never floods: water in and around the pool is exactly what was there less each pile :: 648 -> 650 piles 24
[19:59:07] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL [hrafn-look] a bowed longhall on a plinth: turf roof, bone finials, dark-oak posts, hearth :: floor -2147483648 len 0 moss 1 bone 132 dark-oak 798
[19:59:07] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL [hrafn-look] nothing buried: the hall floor is one above the highest ground under it and the hall stays open inside :: floor -2147483648 ground 0..0, blocked cells 0
[19:59:07] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL [hrafn-look] waterfront: one pier box with a jetty on piles to the bed, a naust (boathouse) astride the shore :: pier boxes 1 pier BlockPos{x=676, y=-60, z=1} piles 10 naust false
[19:59:07] [Server thread/INFO] (Minecraft) [STDOUT]: [villages-selftest] FAIL [hrafn-look] a thing-stead: eight standing stones round a mossy law rock :: stones 0 at null
## lore: TOTAL 119/119 PASS | ERR 0
## duelist:  | ERR 0
## hrafnholt: TOTAL 502/504 FAIL | ERR 0
[20:12:04] [Server thread/INFO] (Minecraft) [STDOUT]: [hrafnholt-selftest] FAIL 15 v 15 MSPT average at most 2.0 ms (2.26 ms)
[20:12:04] [Server thread/INFO] (Minecraft) [STDOUT]: [hrafnholt-selftest] FAIL 15 v 15 MSPT p95 at most 3.0 ms (3.45 ms)
## azmar: TOTAL 234/234 PASS | ERR 0
## mirewick: TOTAL 512/516 (4 failed) FAIL | ERR 0
[20:12:34] [Server thread/INFO] (Minecraft) [STDOUT]: [mirewick-selftest] FAIL B10 ruler armor: every iron/gold/diamond piece on a Magnate draws the purple-crimson-gold mirewick_ruler layer (0 pieces)
[20:12:34] [Server thread/INFO] (Minecraft) [STDOUT]: [mirewick-selftest] FAIL B10 ruler armor: the restyled pieces keep their vanilla item, armor attributes, trim and slot (Thwaite's trimmed diamond chestplate)
[20:14:01] [Server thread/INFO] (Minecraft) [STDOUT]: [mirewick-selftest] FAIL 15 v 15 MSPT average at most 2.0 ms (2.46 ms)
[20:14:01] [Server thread/INFO] (Minecraft) [STDOUT]: [mirewick-selftest] FAIL 15 v 15 MSPT p95 at most 3.0 ms (3.82 ms)
```
Console TOTALs: kingdom 352/352 PASS; court 735/750 FAIL; villages 287/300 FAIL; lore 119/119 PASS; duelist no TOTAL (hit 540s halt in 'telegraph audit and arena restored'); hrafnholt 502/504 FAIL; azmar 234/234 PASS; mirewick 512/516 FAIL.
