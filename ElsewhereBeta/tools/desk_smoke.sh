#!/bin/zsh
# run the combined smoke test on the desktop Mac; log ends up in run-desk/logs/latest.log
B="${0:A:h:h}"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
rm -rf "$B/run-desk/logs"
$S/desk.sh "$B" runServer "$@" > "$B/desk-run.out" 2>&1; rc=$?
echo "desk exit $rc"; tail -5 "$B/desk-run.out"
[ -f "$B/run-desk/logs/latest.log" ] && "$B/tools/summarize.sh" "$B/run-desk/logs/latest.log"
