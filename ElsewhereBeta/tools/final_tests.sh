#!/bin/zsh
# After `tools/merge.py --sync` + `gradlew dist`: run the beta checks in parallel and summarize.
#   desktop: combined smoke test, overworldContent=false boot, module self-tests in the combined jar
#   this Mac: production boots (Fabric API 0.166.0 + Test Meter 1.1.0 + Green Leaf 2.0.1), without and with JEI 31.17.0.109
# Usage: tools/final_tests.sh [tag]   (tag names the copies of the logs, default "final")
setopt null_glob
B="${0:A:h:h}"; cd "$B"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
TAG=${1:-final}
unset JAR
# overworldContent=false copy (its run/config/elsewhere.json holds {"overworldContent": false} and is kept between runs)
OW=$S/beta2ow
rsync -a --delete --exclude build/ --exclude .gradle/ --exclude 'run/world*' --exclude run/logs/ --exclude run/config/ --exclude run-desk/ --exclude snap/ --exclude dist/ --exclude archive/ --exclude screenshots/ --exclude overlay/ --exclude overlay_late/ --exclude 'run/server.properties' --exclude '*.log' --exclude '*.out' --exclude merge-origin.json "$B/" $OW/
python3 - $OW/build.gradle <<'PY'
import sys; p=sys.argv[1]; s=open(p).read()
s=s.replace('property "elsewhere.betatest", "true"\n\t\t\tproperty "elsewhere.betatest.exit", "true"','property "elsewhere.prodcheck", "true"')
open(p,'w').write(s)
PY
( tools/desk_smoke.sh > final-smoke.out 2>&1; cp run-desk/logs/latest.log final-smoke-latest.log ) &
( rm -rf $OW/run-desk; $S/desk.sh $OW runServer > $OW/desk.out 2>&1; cp $OW/run-desk/logs/latest.log overworldoff-latest.log ) &
( [ -n "$NOMODTESTS" ] || tools/desk_modtests.sh > desk-modtests.out.txt 2>&1 ) &
( PORT=25674 tools/prod.sh $TAG > prod-$TAG.out 2>&1 ) &
( sleep 20; PORT=25675 tools/prod.sh ${TAG}jei $S/jei/jei-26.3-fabric-31.17.0.109.jar > prod-${TAG}jei.out 2>&1 ) &
wait
for f in final-smoke-latest.log overworldoff-latest.log prod-$TAG-latest.log prod-${TAG}jei-latest.log; do
  echo "== $f: $(grep -E '\[betatest\] TOTAL' $f | sed 's/.*TOTAL/TOTAL/' | cut -c1-200) | mods: $(grep -oE 'Loading [0-9]+ mods' $f | head -1) | ERROR/Exception lines: $(grep -cE '/ERROR\]|ERROR\]|Exception' $f)"
done
grep -h '\[betatest\] FAIL' final-smoke-latest.log overworldoff-latest.log prod-$TAG-latest.log prod-${TAG}jei-latest.log | cut -c1-250 | head -20
[ -n "$NOMODTESTS" ] || { echo "== module self-tests"; cat desk-modtests.out.txt | head -40; }
