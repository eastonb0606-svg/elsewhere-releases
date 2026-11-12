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
  - `flat` and `flat2` (superflat plains, no structures, no monsters, as modtests-kingdom): kingdom, court, lore,
    duelist, mirewick | villages, hrafnholt, azmar, bosshealth. Two world types are needed: overworld_bosses locates
    structures in the noise overworld, and the kingdom set needs the flat world of its lanes.
- In each JVM the modules run one after another in the same world. Per module: `<id>.selftest=true` and
  `<id>.selftest.exit=true` are set, its hook runs, its start listeners run at once and its tick listeners run only
  while it is the current module; its own `halt(false)` ends the module instead of the server.
- Failure isolation: a throw in a module's hook/start/tick is `CRASH`, no done signal within `TB_TIMEOUT` s (1200) is
  `TIMEOUT`; the batch goes on. If the JVM itself dies, the module it was running is `JVM-DIED` and a new boot (same
  world) continues with the rest. Between modules: force-loaded chunks released, all non-player entities discarded,
  and on flat worlds the chunks within `TB_FLATR` (8) of 0,0 put back to the flat layers.
- No cost outside the test copy; inside it, nothing is registered without `-Delsewhere.testbatch`.

## Options (env)
- `world=a,b flat=c,d flat2=e` as arguments: your own module lists (`flat*` = superflat world). Ids are the
  `-D<id>.selftest` prefixes.
- `TB_FLAGS="duelist:duelist.auditFixtures=x;k=v|court:k=v"` per-module `-D` flags, set only while that module runs.
  `TB_JD="-Dk=v ..."` JVM-wide flags (for static-final reads).
- `TB_PAR=0` one JVM at a time (strictly one slot's worth of memory); default runs the profiles at once.
- `TB_SRC=<tree on the iMac>` test a tree already there (e.g. a previous merge's `ElsewhereBeta-mtk`) instead of ROOT's.
- `TB_XMX` (2560m), `TB_TIMEOUT` (1200), `PORT0` (25790), `LANE`, `HL`.
