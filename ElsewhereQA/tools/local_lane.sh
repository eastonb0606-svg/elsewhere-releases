#!/bin/zsh
# QA client lane on this MacBook: takes the local client slot (mcslot.sh) ONCE and keeps it for run 1 now (beta 1),
# then each new beta 2 build (mods-under-test/.installed-sha) until 21:15. Usage: local_lane.sh "<sections run1>" "<sections beta2>"
setopt nullglob
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
P="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1/ElsewhereQA"
SEC1="$1"; SEC2="$2"
SLOT=${HAVE_SLOT:-$($S/mcslot.sh acquire client qa)}
trap '$S/mcslot.sh release $SLOT client' EXIT
echo "$(date +%T) local client slot $SLOT"
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
run() { # $1 label $2 sections $3 shotdir
  touch $SLOT $(cat $SLOT/partner 2>/dev/null) $S/locks/mc-client
  mkdir -p "$P/reports/$1"
  ( cd "$P" && ./gradlew --console=plain runClientGameTest ${=EXTRA} -PqaSections=$2 -PqaShotDir=$3 > "$P/reports/$1/client.log" 2>&1; echo $? > "$P/reports/$1/rc" )
  cp "$P/build/run/clientGameTest/logs/latest.log" "$P/reports/$1/client-latest.log" 2>/dev/null
  echo "$(date +%T) $1 done rc=$(cat "$P/reports/$1/rc")"
}
[ -n "$SEC1" ] && run local-${RUN1:-beta1} "$SEC1" screenshots/${RUN1:-beta1}
done_sha="${DONE_SHA:-}"
while [ "$(date +%H%M)" -lt 2115 ]; do
  cur=$(cat "$P/mods-under-test/.installed-sha" 2>/dev/null)
  if [ -n "$cur" ] && [ "$cur" != "$done_sha" ]; then
    done_sha=$cur
    run local-b2-$cur "$SEC2" screenshots/b2-$cur
  fi
  touch $SLOT $(cat $SLOT/partner 2>/dev/null) $S/locks/mc-client
  sleep 5
done
