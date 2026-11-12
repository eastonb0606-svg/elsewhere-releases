# Test batch: every module self-test in one boot per world type (s-testbatch, 3.0.0)

**Integrator one-liner** (after a merge that compiles; holds ONE heavy.sh slot under your lane name, blocks until done):

    LANE=/Users/eastonbrown/mcbuild/lane-int HL=int tools/testbatch.sh <tag>

It replaces the ~20 per-module boots (`imac_int.sh modtests-all` + every `modtests-kingdom` round). Output: per module
`== <mod>: <its own TOTAL/DONE line> | DONE|CRASH|TIMEOUT|JVM-DIED <s> | ERROR lines: N`, its FAIL lines and first
ERROR lines, then `ALL GREEN` / `NOT GREEN` (exit 0 / 1). Logs: `out-imac/testbatch/<tag>/run-tb-<profile>/boot-N.log`,
and one combined log per profile as `modtests-<tag>-<profile>-latest.log` (same place as the old modtests logs).

## What it does
- Copies the merged tree to `$LANE/ElsewhereBeta-tb` and injects the batch runner into **that copy only**
  (`tools/testbatch/java/**`, a `TestBatchHooks` generated from the merged `BetaInit.java`, one line in `BetaHooks`,
  three mixins in `elsewhere_beta.mixins.json`). The merged tree, `dist/` and the shipped jar are untouched.
- One gradle `classes writeServerLaunch`, then one JVM per profile, all inside the same heavy.sh job:
  - `world` (default world, survival/easy, as the old modtests groups): elsewhere, arsenal, summoner,
    petalwind_glasswaste, verdigris_ironpeak, greenhaze_hush, overworld_bosses, nether_end_bosses, boss_ui, gear2, gear3.
  - `flat_<mod>` (superflat plains, no structures, no monsters, view distance 4, court 6: exactly modtests-kingdom),
    one fresh world per kingdom-set module: kingdom, villages, bosshealth, hrafnholt, azmar, court, duelist, mirewick,
    lore (longest first). Two world types are needed: overworld_bosses locates structures in the noise overworld, and
    the kingdom set needs the flat world of its lanes. At most `TB_PAR` (3) JVMs run at once.
- In each JVM the modules run one after another in the same world. Per module: `<id>.selftest=true` and
  `<id>.selftest.exit=true` are set, its hook runs, its start listeners run at once and its tick listeners run only
  while it is the current module; its own `halt(false)` ends the module instead of the server.
- Failure isolation: a throw in a module's hook/start/tick is `CRASH`, no done signal within `TB_TIMEOUT` s (1200) is
  `TIMEOUT`; the batch goes on. If the JVM itself dies, the module it was running is `JVM-DIED` and a new boot (same
  world) continues with the rest. Between modules: force-loaded chunks released, all non-player entities discarded,
  and on flat worlds every loaded overworld chunk (and all within `TB_FLATR`, 8, of 0,0) put back to the flat layers.
  Every module starts at the fresh world's time of day with the weather cycle reset (Hrafnholt's tactics differ at dusk).
- No cost outside the test copy; inside it, nothing is registered without `-Delsewhere.testbatch`.

## Options (env)
- `world=a,b flat=c,d flat2=e` as arguments: your own module lists (`flat*` = superflat world). Ids are the
  `-D<id>.selftest` prefixes.
- `TB_FLAGS="duelist:duelist.auditFixtures=x;k=v|court:k=v"` per-module `-D` flags, set only while that module runs.
  `TB_JD="-Dk=v ..."` JVM-wide flags (for static-final reads).
- `TB_PAR=N` at most N JVMs at once (default 3, 2.5 GB each); `TB_PAR=0` one at a time.
- `TB_SRC=<tree on the iMac>` test a tree already there (e.g. a previous merge's `ElsewhereBeta-mtk`) instead of ROOT's.
- `TB_XMX` (2560m), `TB_TIMEOUT` (1200), `PORT0` (25790), `LANE`, `HL`.

## Measured (merge 30, the 11:33 merge, iMac, one heavy.sh slot each)
- Old (`modtests-all` + 5 `modtests-kingdom` rounds, logs `modtests-t30-*`): 12 JVM boots, 32.5 min wall for the pass
  (11:35:06-12:07:37) plus 7.4 min to re-run arsenal; 70.7 JVM-minutes. Group a died of OutOfMemoryError (the core,
  summoner and overworld_bosses totals were lost) and arsenal showed 77/84 there (84/84 alone); gear3 was not run.
- New (`tools/testbatch.sh tb2`, same build): 3 boots, 20 modules, **21.9 min wall including sync and build** (world
  12.4, flat 13.6, flat2 20.3 min, in parallel); every world module green (core 216/216, arsenal 84/84, gear3 686/686).
  Kingdom-set totals equal the old ones on that build (court 381/438, kingdom 319/319, mirewick 472/472, bosshealth
  2403/2403, duelist 2 fails vs 3, villages 1 vs 3). Two leaks between modules showed up (Hrafnholt's berserker_screen at
  dusk, a villages road outside the old reset radius in Azmar's hash) and are fixed above; see `out-imac/testbatch/tb3`.
- Gaps between modules are 2 s; a boot is about 20-30 s; no 420 s `haltAfterSeconds` idle per group any more.

## Status (13:55) - why the kingdom set is not shared
- `world` (11 modules, one JVM): verified green, equal to or better than the old boots.
- Kingdom set in a shared world (`flat=a,b,c`, still possible): the three order effects found in `tb2` (time of day,
  a leftover village road, MSPT with 3 JVMs) are fixed and verified in `tb3`, but `tb3` then failed 4 checks that pass
  in every single-module boot (hrafnholt: Verse of Winter at 50%, Wolfskin Boots no slipping; azmar: High-Ground Kite,
  Falcon Mark). So the default gives each of these modules its own fresh flat world, as modtests-kingdom did, inside
  the same heavy.sh job. The full default run (world + 9 flat profiles, TB_PAR 3) has not yet run end to end.
