# cloud-e results

Tested commit (cloud-src): f7708fa381532cc2e49da198d4ad8d1307b53953
Machine: 4 cores, 15 GB RAM, Linux 6.18.44-fc-v378, JDK jdk-25.0.5+8
Report time: 2026-11-12T20:18:16Z

## kingdom_court
- build: ok
- wall time: 507 s (build + run)
- runServer rc 0 (124 = killed by 900 s timeout)
- TOTAL/PASS/FAIL lines:
```
PASS lines: 755  FAIL lines: 1
[STDOUT]: [court-selftest] FAIL hraf: nausts with slips on the seeds' water sites (at least 2 nausts on at least 2 water sites) (4 water sites, 0 nausts)
[STDOUT]: [court-selftest] TOTAL 755/756 FAIL
```
- /ERROR] count: 0
```
```

## kingdom_villages
- build: ok
- wall time: 379 s (build + run)
- runServer rc 0 (124 = killed by 900 s timeout)
- TOTAL/PASS/FAIL lines:
```
PASS lines: 296  FAIL lines: 11
[STDOUT]: [villages-selftest] FAIL load test: full siege p95 tick cost at most 8 ms (server-thread CPU; lag budget) :: CPU p95 10.43 mean 7.16; wall p95 11.16 mean 7.46; machine load 3.2
[STDOUT]: [villages-selftest] FAIL [rivals] Hrafnholt waterfront never floods: water in and around the pool is exactly what was there less each pile :: 541 -> 872 piles 25 | flowing 673, -60, -2 near: 674, -60, 0 oak_log>spruce_log 674, -59, 0 oak_log>spruce_log 674, -58, 0 oak_log>spruce_log 675, -60, 0 cobblestone>mossy_cobblestone | flowing 674, -60, -3 near: | flowing 675, -60, -3 near: | flowing 676, -60, -3 near:
[STDOUT]: [villages-selftest] FAIL [hrafn-look] nothing floating: every post, wall, pile, stone and fence stands whole on solid ground (836 members) :: gap at 672 -60 5 (Block{minecraft:water}[level=1])
[STDOUT]: [villages-selftest] FAIL [hrafn-look] Hrafnholt village: FitAudit finds nothing floating, unsupported or unable to survive :: written 4816, added 3685, standing columns 993, floating 14 (max gap 1), unsupported 7, cannot survive 0 [gap1@669, -59, 10=spruce_log, gap1@671, -59, 10=spruce_log, gap1@670, -59, 2=hay_block, gap1@669, -59, 8=spruce_log, unsupported@669, -58, 9=dark_oak_log, unsupported@670, -58, 10=dark_oak_log, unsupported@669, -58, 10=spruce_log, unsupported@669, -58, 6=spruce_log]
[STDOUT]: [villages-selftest] FAIL [hrafn-look] waterfront: one pier box with a jetty on piles to the bed, a naust (boathouse) astride the shore :: pier boxes 1 pier BlockPos{x=684, y=-60, z=-8} piles 10 naust false
[STDOUT]: [villages-selftest] FAIL [hrafn-look] a thing-stead: eight standing stones round a mossy law rock :: stones 0 at null
[STDOUT]: [villages-selftest] FAIL [hrafn-look] fenced tofts beside the houses (gate, drying rack, hay, woodpile) :: tofts 6 gates 5 hay 11
[STDOUT]: [villages-selftest] FAIL [mirewick-look] Mirewick village palette is purple, crimson and gold in balance, not mostly crimson (purple >= 30%, crimson 15-50%, gold >= 5%) :: purple 548 (32%) crimson 1012 (59%) gold 158 (9%)
[STDOUT]: [villages-selftest] FAIL [rivals] flip-back restores Aldermark blue banners and a blue-flag garrison :: 19 blue
[STDOUT]: [villages-selftest] FAIL [rivals] hrafnholt outpost carries its border hint (raven post / obelisk / lantern post) :: Block{minecraft:air}
[STDOUT]: [villages-selftest] FAIL [rivals] mirewick outpost carries its border hint (raven post / obelisk / lantern post) :: Block{minecraft:air}
[STDOUT]: [villages-selftest] TOTAL 296/307 FAIL
```
- /ERROR] count: 0
```
```

## boss_music
- build: ok
- wall time: 915 s (build + run)
- runServer rc 124 (124 = killed by 900 s timeout)
- TOTAL/PASS/FAIL lines:
```
PASS lines: 0  FAIL lines: 0
```
- /ERROR] count: 0
```
```

