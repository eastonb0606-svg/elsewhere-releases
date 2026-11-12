#!/bin/zsh
# run every module's own self-test (plus the core's) inside the combined jar, on the desktop Mac
B="${0:A:h:h}"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
M=$S/betamodtests
while [ -e "$B/.merging" ]; do sleep 1; done
rsync -a --delete --exclude build/ --exclude .gradle/ --exclude run/ --exclude run-desk/ --exclude snap/ --exclude screenshots/ --exclude dist/ "$B/" "$M/"
mkdir -p $M/run; cp "$B/run/eula.txt" $M/run/; sed "s/25671/25672/g" "$B/run/server.properties" > $M/run/server.properties; echo max-tick-time=-1 >> $M/run/server.properties
python3 - "$M/build.gradle" <<'PY'
import sys; p=sys.argv[1]; s=open(p).read()
s=s.replace('''			property "elsewhere.betatest", "true"
			property "elsewhere.betatest.exit", "true"''','''			property "elsewhere.selftest", "true"
			property "petalwind_glasswaste.selftest", "true"
			property "verdigris_ironpeak.selftest", "true"
			property "greenhaze_hush.selftest", "true"
			property "overworld_bosses.selftest", "true"
			property "nether_end_bosses.selftest", "true"
			property "arsenal.selftest", "true"
			property "summoner.selftest", "true"
			property "elsewhere.haltAfterSeconds", "360"''')
open(p,'w').write(s)
PY
rm -rf $M/run-desk
$S/desk.sh $M runServer > "$B/desk-modtests.out" 2>&1; rc=$?
echo "desk exit $rc"
L=$M/run-desk/logs/latest.log
cp $L "$B/modtests-latest.log" 2>/dev/null
grep -iE 'passed|failed|ALL PASSED|TOTAL' $L | grep -iE 'selftest|self-test|total' | cut -c1-300 | tail -20
echo "== FAIL"; grep -E '\bFAIL\b' $L | cut -c1-250 | head -50
echo "== errors"; grep -nE '/ERROR\]|Exception' $L | cut -c1-250 | head -30
