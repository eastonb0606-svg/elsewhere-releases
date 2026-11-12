#!/bin/zsh
# Local client pass: holds the client slot (mcslot.sh), runs the client gametest, copies the log to reports/.
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
Q="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1/ElsewhereQA"
SLOT=$($S/mcslot.sh acquire client qa)
trap '$S/mcslot.sh release $SLOT client' EXIT
( while sleep 300; do touch $SLOT $(cat $SLOT/partner 2>/dev/null); done ) &
TOUCH=$!
cd "$Q"
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
./gradlew --console=plain runClientGameTest > "$Q/reports/client-${1:-1}.out" 2>&1
RC=$?
kill $TOUCH 2>/dev/null
cp "$Q/build/run/clientGameTest/logs/latest.log" "$Q/reports/client-${1:-1}.log" 2>/dev/null
echo "client exit $RC"
exit $RC
