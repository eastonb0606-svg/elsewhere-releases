# HANDOFF: 2.0.1 villages fixes (paused, nothing built or verified)

State: PAUSED on lead's request. No iMac/ssh use any more. The 2.0.1 build resumes later on this Mac only.
The iMac self-test I started (lane-201, `modtests-kingdom diag 3 villages`) was stopped on this side only. Its JVMs on the iMac were NOT killed (that needs ssh). Check/clean `~/mcbuild/lane-201/` later if needed.

## Edits made so far
- `ElsewhereBeta/gradle.properties`: version 2.0.0 -> 2.0.1. (2.0.0 jar in dist untouched.)
- `src/main/java/dev/elsewhere/kingdom_villages/dev/SelfTest.java` (~line 754): the "load test: siege running and rebuild in progress" check now also prints `E.reason`, `D.reason` and `assessProtection(E)`. Diagnostic only, to learn which rule protects E. Result not obtained.
- `python3 ElsewhereBeta/tools/merge.py --sync` was run once (merge regenerated from live modules).

## Findings (code reading, NOT yet confirmed by a run)
1. Protection (`VillageManager.assessProtection`, line ~245) has 4 triggers: ledger `playerChunks` ("player block"), `tradeChunks` ("player trade"), chunk InhabitedTime >= 72000 ("player presence"), villager XP/level/offer uses ("villager trade history"), and `BlockWriter.isPlayerBlock` in pieces ("player-built block"). Unknown which fires on E; run with the new diagnostic to see `Ereason`.
   Suspects, in order:
   - Test setup: earlier phases add marks (`playerChunks.add(mid)` for the road test, FakePlayer glowstone in A, `setInhabitedTime(72000)` for a road chunk and for F's chunk). Those persist in the same world/ledger; if E's footprint (+8 for marks) touches one, E is protected. Fix the test: clear/reset those marks (and reset the chunk InhabitedTime) before `loadSetup`, or use chunks outside E.
   - Production: `PlayerMarks.mark` fires for any `BlockItem.place` with a non-null player and for every non-spectator `PlayerBlockBreakEvents.AFTER`, including FakePlayers/test players; `UseEntityCallback` and `notifyTrade` likewise. Real logic should only count real players (not `FakePlayer`), and the `notifyTrade` mark should require a real player trade. InhabitedTime is vanilla and counts any nearby player.
2. Farmland restore (`BlockWriter.restore`, line ~107): `Plans` writes farmland -> DIRT (Plans.java:60). While burned, grass spreads onto that dirt. In `restore`, `ours` is only true when `cur.getBlock() == written.getBlock()` (dirt), so a grass_block over our dirt is treated as player-changed: skipped and journal entry removed. That matches "9 farmland came back as grass_block". Planned fix: treat grass_block/dirt/coarse dirt/podzol/mycelium over our written DIRT as ours; then restore the original state. Keep restored farmland as farmland (restore with moisture 7 when no water nearby, or ensure no solid block above); add a check that restored farmland blocks are still FARMLAND after ~100 ticks.

## Still to do (on this Mac, using `_tools/mac-build.sh`)
- Apply both fixes above + new self-test checks (protection reason with a real player action vs. a FakePlayer/test mark; farmland restored exact and still farmland).
- Run villages self-test 3x (fresh world/JVM), build `ElsewhereBeta/dist/elsewhere-2.0.1.jar`, combined smoke, `NO_FPS=1 zsh ElsewhereQA/run-release-tests.sh <jar> prod,client`.
- Write `Release/2.0.1/CHANGELOG.md` (3-6 plain lines) and append STATUS entry with sha1.
