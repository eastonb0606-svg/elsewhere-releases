#!/bin/zsh
# sync snapshots, merge, compile. Usage: tools/build.sh [--nosync] [task...]
B="${0:A:h:h}"; cd "$B"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
if [ "$1" = "--nosync" ]; then shift; python3 tools/merge.py; else python3 tools/merge.py --sync; fi
T=${@:-compileClientJava}
./gradlew ${=T} --console=plain > build-compile.log 2>&1; rc=$?
echo "gradle $T exit $rc"
python3 tools/errs.py build-compile.log
grep -E "^\* What went wrong" -A4 build-compile.log | head -12
exit $rc
