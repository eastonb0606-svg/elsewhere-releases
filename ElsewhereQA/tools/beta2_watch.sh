#!/bin/zsh
# Watches ElsewhereBeta/dist/elsewhere-0.3.0-beta.jar; when a new (size-stable, valid zip) build appears, installs it
# into mods-under-test/ (replacing any elsewhere-*.jar), writes mods-under-test/.installed-sha, and (re)launches the
# desk server runs for it: s0 = world checks (no bosses), s1..s3 = boss shards i/3. A shard that is still running an
# older build is relaunched when it finishes. Results: reports/b2-<sha>/{world,boss0..2}.{out,log}
setopt nullglob
ROOT="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
Q="$ROOT/ElsewhereQA"; J="$ROOT/ElsewhereBeta/dist/elsewhere-0.3.0-beta.jar"
last=""
launch() { # $1 shard n, $2 sha
  local n=$1 sha=$2 d="$Q/shards/ElsewhereQA_s$1" name; local -a args
  if [ $n = 0 ]; then args=(-PqaBosses=none); name=world; else args=(-PqaOnly=bosses -PqaShard=$((n-1))/3); name=boss$((n-1)); fi
  mkdir "$d/.running" 2>/dev/null || return 1
  rm -f "$d"/mods-under-test/elsewhere-*.jar; cp "$Q/mods-under-test/elsewhere-0.3.0-beta.jar" "$d/mods-under-test/"
  cp "$Q/build.gradle" "$d/build.gradle"; rsync -a --delete "$Q/src/" "$d/src/"
  echo $sha > "$d/.lastsha"; mkdir -p "$Q/reports/b2-$sha"
  ( "$S/desk.sh" "$d" runServer $args > "$Q/reports/b2-$sha/$name.out" 2>&1
    cp "$d/run-desk/logs/latest.log" "$Q/reports/b2-$sha/$name.log" 2>/dev/null
    cp "$d"/run-desk/selftest*.log "$Q/reports/b2-$sha/" 2>/dev/null
    rmdir "$d/.running" ) &
  echo "$(date +%T) launched $name for $sha"
}
while true; do
  if [ -f "$J" ]; then
    s1=$(stat -f %z "$J"); sleep 10; s2=$(stat -f %z "$J")
    sha=$(shasum "$J" | cut -c1-7)
    if [ "$s1" = "$s2" ] && [ "$sha" != "$last" ] && unzip -tq "$J" >/dev/null 2>&1; then
      last=$sha
      rm -f "$Q"/mods-under-test/elsewhere-*.jar; cp "$J" "$Q/mods-under-test/elsewhere-0.3.0-beta.jar"
      echo "$sha" > "$Q/mods-under-test/.installed-sha"
      echo "$(date +%T) installed $sha"
    fi
  fi
  cur=$(cat "$Q/mods-under-test/.installed-sha" 2>/dev/null)
  if [ -n "$cur" ]; then
    for n in 0 1 2 3; do
      [ "$(cat "$Q/shards/ElsewhereQA_s$n/.lastsha" 2>/dev/null)" != "$cur" ] && launch $n $cur
    done
  fi
  [ "$(date +%H%M)" -gt 2125 ] && exit 0
  sleep 10
done
