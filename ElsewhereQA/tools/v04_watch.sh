#!/bin/zsh
# 0.4.0 QA orchestrator. Watches ElsewhereBeta/dist/elsewhere-0.4.0-beta.jar; for each new (size-stable, valid zip) build:
# installs it into mods-under-test/ (replacing elsewhere-*.jar), runs the asset check, then launches in parallel
#   desk servers: world checks (s0) + boss shards 0/3..2/3 (s1..s3)      -> reports/v04-<sha>/{world,boss0..2}.log
#   iMac client (no JEI, Fabric API 0.166.0): $IMAC_SECTIONS             -> reports|screenshots/v04-imac-<sha>/
#   local client (JEI 31.17.0.109 + Fabric API 0.166.0): $LOCAL_SECTIONS -> reports|screenshots/v04-local-<sha>/
# A newer jar that lands while a set is running is picked up when that set finishes. Exits at 23:20.
setopt nullglob
ROOT="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
Q="$ROOT/ElsewhereQA"; J="$ROOT/ElsewhereBeta/dist/elsewhere-0.4.0-beta.jar"
IMAC_SECTIONS=${IMAC_SECTIONS:-arenagen,ankhara,dims,mobs,bosses}
LOCAL_SECTIONS=${LOCAL_SECTIONS:-items,jei,crook,palehollow,hudscale,heat,wayhome,wayfinder,arenas}
last="${DONE_SHA:-}"
server_run() { # $1 shard n, $2 sha
  local n=$1 sha=$2 d="$Q/shards/ElsewhereQA_s$1" name; local -a args
  if [ $n = 0 ]; then args=(-PqaBosses=none); name=world; else args=(-PqaOnly=bosses -PqaShard=$((n-1))/3); name=boss$((n-1)); fi
  rm -f "$d"/mods-under-test/elsewhere-*.jar; cp "$Q"/mods-under-test/elsewhere-*.jar "$d/mods-under-test/"
  cp "$Q/build.gradle" "$d/build.gradle"; rsync -a --delete "$Q/src/" "$d/src/"
  echo $sha > "$d/.lastsha"
  "$S/desk.sh" "$d" runServer $args > "$Q/reports/v04-$sha/$name.out" 2>&1
  cp "$d/run-desk/logs/latest.log" "$Q/reports/v04-$sha/$name.log" 2>/dev/null
  cp "$d"/run-desk/selftest-qa-results.log "$Q/reports/v04-$sha/$name-results.log" 2>/dev/null
  echo "$(date +%T) server $name done for $sha"
}
while [ "$(date +%H%M)" -lt 2320 ]; do
  if [ -f "$J" ]; then
    s1=$(stat -f %z "$J"); sleep 8; s2=$(stat -f %z "$J")
    sha=$(shasum "$J" | cut -c1-7)
    if [ "$s1" = "$s2" ] && [ "$sha" != "$last" ] && unzip -tq "$J" >/dev/null 2>&1; then
      last=$sha
      rm -f "$Q"/mods-under-test/elsewhere-*.jar; cp "$J" "$Q/mods-under-test/elsewhere-0.4.0-beta.jar"
      echo "$sha" > "$Q/mods-under-test/.installed-sha"
      mkdir -p "$Q/reports/v04-$sha"
      echo "$(date +%T) installed $sha ($(shasum "$J" | cut -c1-40))"
      python3 -I "$Q/tools/assetcheck.py" "$Q/mods-under-test/elsewhere-0.4.0-beta.jar" > "$Q/reports/v04-$sha/assetcheck.txt" 2>&1
      echo "$(date +%T) assetcheck: $(tail -1 "$Q/reports/v04-$sha/assetcheck.txt")"
      pids=()
      for n in 0 1 2 3; do server_run $n $sha & pids+=($!); done
      "$Q/tools/v04_imac.sh" v04-imac-$sha "$IMAC_SECTIONS" -Pfapi=0.166.0+26.3 > "$Q/reports/v04-$sha/imac.out" 2>&1 & pids+=($!)
      "$Q/tools/v04_local.sh" v04-local-$sha "$LOCAL_SECTIONS" -Pjei -Pfapi=0.166.0+26.3 > "$Q/reports/v04-$sha/local.out" 2>&1 & pids+=($!)
      echo "$(date +%T) launched 4 servers + iMac + local clients for $sha"
      wait $pids
      echo "$(date +%T) all runs done for $sha"
      continue
    fi
  fi
  sleep 10
done
