# Integrator hand-off (14:15 CST)
## Builds
- **alpha.4** `dist/elsewhere-2.0.0-alpha.4.jar`, sha1 `ee5bc130765909fc6416dcd2cd9c6451a535a0f3` (67 MB, most of it boss_music audio). Contents: alpha.3 + overworld_bosses, nether_end_bosses, arsenal, summoner, boss_ui, boss_music, gear2 (last) + art2. Combined smoke (iMac): **195 passed, 0 failed, 0 ERROR**. Module self-tests in the combined build: core 148/0, verdigris 119/0, overworld 96/0, petalwind 65/0, nether 183/0, gear2 86/86, boss_ui 10/10. Failures: arsenal 67/68 and summoner 125/1 (their "placeholder loot tables exist" check broke because I excluded those tables; reverted in alpha.5), greenhaze 73/1 (alpha.3 pin: munchfruit 0; dims-gh's live module reports 150/0). Archive: `archive/alpha.4/`.
- **alpha.5** (in progress) `dist/elsewhere-2.0.0-alpha.5.jar` is built (no sha1 recorded yet). It adds live verdigris_ironpeak (dims-pv's verdigris half is done), greenhaze_hush + jei_compat (dims-gh DONE), kingdom (K-A **live, still in progress**) and kingdom_lore (K-D DONE), and keeps the arsenal/summoner placeholders. The smoke was running when I stopped: result in `smoke-a5-latest.log` (`tools/summarize.sh smoke-a5-latest.log`). Module self-tests not run yet: `tools/imac_integ.sh modtests a5`. **No STATUS entry yet for alpha.5.**
## How to build (python here, Gradle on the iMac `~/mcbuild/lane-integrator/` only)
1. `cd ElsewhereBeta && BETA_SNAP="$PWD/snap_a4" python3 tools/merge.py --sync` (`--sync` re-snapshots live modules plus art2). Then check `merge-report.txt`: the RES-DUP set (44) is old and harmless; read any new JAVA-DUP, SHARED-DIFF, PATCH-FAILED or EXTRA-MISSING lines.
2. `tools/imac_integ.sh gradle compileJava compileClientJava`, then `gradle dist` (the jar lands in `dist/`), then `smoke <tag>`, then `modtests <tag>`. errs.py maps compile errors to modules.
## Tool changes (all in ElsewhereBeta)
- **merge.py:**
  - `ALL_MODULES` now also has boss_ui, kingdom, kingdom_court, kingdom_villages, kingdom_lore, boss_music, gear2 (last).
  - **modules.json `snapshot_from`** pins a module to `snap_alpha3/<m>` (now only core and petalwind_glasswaste).
  - **`shared`** keeps one owner copy of each shared file. `boss/kit/*` comes from core, except `Openings.java` from greenhaze_hush (superset with its 4 bosses). `ElsewhereHud` comes from arsenal.
  - **Loot rule:** a table with no entries never replaces or blocks a real one, whatever the module order.
  - `json_merge` covers jei/info.json, boss_music.json and boss_bars.json. `snapshot_extra` injects the gear2 and kingdom_lore JEI files. The `art2_overlay` mirror goes into snapshots only.
  - Client entries are de-duplicated.
- **modules.json:** excludes, `post_init` and `post_client` for boss_ui, gear2 and kingdom_lore (`LoreContent.init()`). Kingdom excludes. art2 renderer patches on the merged Verdigris and Greenhaze clients (skipped once the module calls `Art2Mobs.register()` itself). `disabled` is `[kingdom_court, kingdom_villages]`.
- **overlay:**
  - The smoke enlists its FakePlayer into nether/end boss fights.
  - A Grunthar probe (INFO only).
  - Loot is rolled up to 40×. A table with no pools counts as INFO.
  - `BossBarSweep.DISCARDED_BY` records the stack that discarded an entity.
  - The greenhaze GlimmerStub gains `forgetPlayer` and `clearAll` (dims-gh's Stability uses them).
- `build.gradle`: dev server heap 2560m. New tool: `tools/imac_integ.sh`.
## Findings
- **Heliodor "drops: none":** arsenal's empty `entities/heliodor.json` made a pool-less table exist. Heliodor's real drops are coded per participant, and the FakePlayer is not a participant. Fix: pool-less tables become INFO, and gear2's MODIFY pools still apply. In alpha.4 Heliodor drops `heliodor_mirror_core`, and Tempestra's table rolls a Gale Rapier.
- **Grunthar DISCARDED:** a harness artifact, confirmed by the probe. A bare Grunthar (no Ashtusk) sits in a phase-2 transition (immune) from t=3 to past t=41. The FakePlayer is not in `level.players()`, so it never joined, and with no participant `BossFight.reset` → `onFightReset` discarded him at t=43. Fixed by enlisting. Pitfall: `FakePlayer.get` caches by name, so the probe needs its own name.
## Open, and exact next steps for the final merge (lanes hand in by 16:15)
1. Finish alpha.5: read the smoke log, run modtests, write a STATUS entry (jar, sha1, counts, open requests).
2. **INTEGRATION-REQUESTS.md item 1 is outdated:** keep the arsenal/summoner placeholder tables, because their self-tests check them. Corrected below that item.
3. When **core** is done: remove `core` from `snapshot_from`. Re-check `SHARED-DIFF`, because the core kit may change and core should adopt the Openings superset (request 3 also covers `elsewhere:amber`, which causes 5 gear2 recipe WARNs). When **dims-pv** is done: remove `petalwind_glasswaste` from `snapshot_from`, and re-check the `CreativeTabs` patches (0 replacements; harmless).
4. When **K-B / K-C** are done: take their STATUS lines and add them to modules.json. merge.py auto-finds `init()` only for `dev.elsewhere.<module>.*Content`, so another package needs a `post_init` entry. Excludes for DevEntry/dev. Add any `INTEGRATION/` JEI files via `snapshot_extra`. K-B's music goes into boss_music.json (json_merge). Remove the module from `disabled`. Then run `python3 -I ElsewhereContent/jei_compat/tools/gen_info2.py` and `kingdom_lore/tools/gen_jei.py` only if their owners ask; the integrator does not edit module sources.
5. **Final:** set `version=2.0.0` in gradle.properties (make-release.sh requires exactly 2.0.0), merge `--sync`, then dist, smoke, modtests. Target: 0 FAIL and 0 ERROR. STATUS entry. Test Meter 1.1.1 is separate (`TestMeter/dist/testmeter-1.1.1.jar`).
6. Not verifiable on the iMac (no client allowed there): boss_ui and boss_music client mixins, the art2 models, and the Repeater item model. Flag them for client QA.
