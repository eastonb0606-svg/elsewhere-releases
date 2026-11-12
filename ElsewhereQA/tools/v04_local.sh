#!/bin/zsh
# One local client run (MacBook) for 0.4.0 QA. Usage: v04_local.sh <label> <sections> [extra gradle args...]
# Holds the local client slot (mcslot.sh) for the run; log -> reports/<label>/, shots -> screenshots/<label>/.
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
P="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1/ElsewhereQA"
L="$1"; SEC="$2"; shift 2
SLOT=$($S/mcslot.sh acquire client qa-$L)
trap '$S/mcslot.sh release $SLOT client' EXIT
( while sleep 240; do touch $SLOT $(cat $SLOT/partner 2>/dev/null) $S/locks/mc-client 2>/dev/null; done ) &
TP=$!
echo "$(date +%T) $L start (slot $SLOT)"
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
mkdir -p "$P/reports/$L"
cd "$P" && ./gradlew --console=plain -Dorg.gradle.jvmargs=-Xmx1g runClientGameTest "$@" -PqaSections=$SEC -PqaShotDir=screenshots/$L > "$P/reports/$L/client.log" 2>&1
echo $? > "$P/reports/$L/rc"
kill $TP 2>/dev/null
cp "$P/build/run/clientGameTest/logs/latest.log" "$P/reports/$L/client-latest.log" 2>/dev/null
echo "$(date +%T) $L done rc=$(cat "$P/reports/$L/rc")"
