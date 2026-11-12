#!/bin/zsh
# client gametest screenshots (holds the client lock + 2 slots). Runs on a frozen copy of the project
# so merges in ElsewhereBeta/ can't race the client build. Screenshots land in ElsewhereBeta/screenshots/.
setopt null_glob
B="${0:A:h:h}"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
C=$S/betaclient
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
SLOT=$($S/mcslot.sh acquire client elsewhere_beta)
while [ -e "$B/.merging" ]; do sleep 1; done
rsync -a --delete --exclude run/ --exclude run-desk/ --exclude snap/ --exclude screenshots/ --exclude dist/ "$B/" "$C/"
trap '$S/mcslot.sh release $SLOT client' EXIT
echo "slot $SLOT $(date +%T)"
( while true; do sleep 300; touch $SLOT; done ) & TOUCH=$!
cd $C
./gradlew runClientGameTest -Dorg.gradle.jvmargs=-Xmx1g --console=plain > "$B/client-run.log" 2>&1; rc=$?
kill $TOUCH 2>/dev/null
mkdir -p "$B/screenshots"; rm -f "$B"/screenshots/*.png; cp $C/screenshots/*.png "$B/screenshots/" 2>/dev/null
echo "client exit $rc $(date +%T)"; ls "$B/screenshots" | wc -l
grep -E 'beta-gametest|No renderer|\[beta\]|Exception|/ERROR' "$B/client-run.log" | cut -c1-250 | head -40
exit $rc
