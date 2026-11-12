#!/bin/zsh
# Runs the dev server self-test under the shared Minecraft slot lock and saves the log as run/selftest-N.log.
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
cd "$(dirname "$0")/.."
N=$(ls run/selftest-*.log 2>/dev/null | sed 's/.*selftest-\([0-9]*\).log/\1/' | sort -n | tail -1); N=$(( ${N:-0} + 1 ))
SLOT=$($S/mcslot.sh acquire "" nether_end_bosses)
trap "$S/mcslot.sh release $SLOT" EXIT INT TERM
( while sleep 300; do touch $SLOT 2>/dev/null || exit; done ) &
TOUCHER=$!
./gradlew --offline runServer > run/selftest-$N.log 2>&1
kill $TOUCHER 2>/dev/null
grep -E "\[selftest\]|ERROR|Exception" run/selftest-$N.log | grep -v "^\s*at " | tail -150
echo "log: run/selftest-$N.log"
