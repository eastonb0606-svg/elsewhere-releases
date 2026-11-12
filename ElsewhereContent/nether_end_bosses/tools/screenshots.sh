#!/bin/zsh
# Runs the client gametest (screenshots) under the shared client lock and saves the log as run/clienttest.log.
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
cd "$(dirname "$0")/.."
SLOT=$($S/mcslot.sh acquire client nether_end_bosses)
trap "$S/mcslot.sh release $SLOT client" EXIT INT TERM
( while sleep 300; do touch $SLOT $S/locks/mc-client 2>/dev/null || exit; done ) &
TOUCHER=$!
./gradlew --offline runClientGameTest > run/clienttest.log 2>&1
RC=$?
kill $TOUCHER 2>/dev/null
grep -E "ERROR|Exception|FAIL|Screenshot|screenshot" run/clienttest.log | grep -v "^\s*at " | tail -40
echo "rc=$RC"; ls -la screenshots
