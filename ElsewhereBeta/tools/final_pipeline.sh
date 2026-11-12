#!/bin/zsh
# Integrator final pipeline (run from ElsewhereBeta/): merge + dist, then four iMac chains in parallel (each one heavy slot, its own
# remote copy and ports), logs in /tmp/fp-*.out. Chains (one JVM + fresh superflat world per module; <=3 JVMs per round):
#   A (int1): elsewhere arsenal summoner | verdigris_ironpeak overworld_bosses boss_ui | gear2 gear3
#   B (int2): petalwind_glasswaste greenhaze_hush nether_end_bosses | kingdom court villages
#   C (int3): lore duelist hrafnholt | azmar mirewick bosshealth
#   D (qaint1): release server suite (prod boots + server) then upgrade + used, NO_FPS=1
# Then run tools/run_legacy.sh (2.0.2 world) under heavy int1.
B="${0:A:h:h}"; cd $B || exit 1; ROOT="${B:h}"
python3 tools/merge.py --sync > merge-final.out 2>&1
rm -f out-imac/build-imac.rc; HL=int1 zsh tools/imac_int.sh gradle dist > /tmp/fp-dist.out 2>&1 || { echo "BUILD FAILED"; tail -20 /tmp/fp-dist.out; exit 1; }
cp dist/elsewhere-3.0.0.jar /tmp/elsewhere-3.0.0.jar
chain() { # $1 lane, $2 MT_DIR, $3 port0, rest: groups
  local hl=$1 md=$2 p0=$3; shift 3; local i=0
  for g in "$@"; do i=$((i+1)); echo "=== $hl round $i: $g"; HL=$hl MT_DIR=$md PORT0=$p0 zsh tools/imac_int.sh modtests-kingdom fp-$hl-$i 1 "$g"; done; echo CHAIN-DONE
}
chain int1 -fa 25900 "elsewhere arsenal summoner" "verdigris_ironpeak overworld_bosses boss_ui" "gear2 gear3" > /tmp/fp-A.out 2>&1 &
sleep 30; chain int2 -fb 25920 "petalwind_glasswaste greenhaze_hush nether_end_bosses" "kingdom court villages" > /tmp/fp-B.out 2>&1 &
sleep 30; chain int3 -fc 25940 "lore duelist hrafnholt" "azmar mirewick bosshealth" > /tmp/fp-C.out 2>&1 &
sleep 30; (NO_FPS=1 QSUB=/qs QL=qaint1 $ROOT/ElsewhereQA/run-release-int.sh /tmp/elsewhere-3.0.0.jar prod,server,upgrade,used > /tmp/fp-D.out 2>&1 &)
wait
echo ALL-CHAINS-DONE
