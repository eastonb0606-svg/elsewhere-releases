#!/bin/zsh
# Summarise the 0.4.0 QA runs for one sha: v04_summary.sh <sha>
setopt nullglob
P="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1/ElsewhereQA"
sha=$1; R="$P/reports"
for c in v04-imac-$sha v04-local-$sha; do
  f="$R/$c/client-latest.log"; echo "=== $c rc=$(cat $R/$c/rc 2>/dev/null) shots=$(ls "$P/screenshots/$c" 2>/dev/null | wc -l | tr -d ' ')"
  [ -f "$f" ] || { echo "  (no log yet)"; continue; }
  grep -a -m1 "Loading [0-9]* mods" "$f" | sed 's/.*Loading/  Loading/'
  grep -a -o "JEI loaded: [a-z]*; fabric-api [^ ]*" "$f" | head -1 | sed 's/^/  /'
  grep -a -o "[0-9]* items with a missing item model: .*" "$f" | cut -c1-200 | sed 's/^/  /'
  grep -a -c "Exception\|/ERROR\]" "$f" | sed 's/^/  ERROR\/exception lines: /'
  grep -a "Mixin\|Crash\|crash report\|FATAL" "$f" | grep -v -i "realms" | head -5 | cut -c1-200 | sed 's/^/  !! /'
  grep -a "V04 \|RECHECK\|done:" "$f" | sed -E 's/^.*\[qa-client\] //' | cut -c1-600 | sed 's/^/  /'
  grep -a "renderer boss\|renderer mob" "$f" | grep -c "no client entity" | sed 's/^/  renderer lookups with no client entity: /'
  grep -a "arena [a-z_]*: \|arena [a-z_]* after" "$f" | grep -v " ok " | sed -E 's/^.*\[qa-client\] //' | cut -c1-200 | sed 's/^/  arena: /'
done
for n in world boss0 boss1 boss2; do
  f="$R/v04-$sha/$n.log"; [ -f "$f" ] || { echo "=== server $n: (no log yet)"; continue; }
  echo "=== server $n: $(grep -a -o "TOTAL .*" "$f" | tail -1)  ERROR lines: $(grep -a -c '/ERROR\]' "$f")"
  grep -a "\[qa\] FAIL\|\[qa\] WARN" "$f" | sed -E 's/^.*\[qa\] //' | cut -c1-260 | sed 's/^/  /'
  grep -a "\[qa\] PASS boss [a-z_]*: \(drops\|arena unsealed\|[0-9]* elsewhere entities\)" "$f" | sed -E 's/^.*\[qa\] //' | grep -i "kazehana\|vorlagh\|tempestra\|grunthar\|solaxis\|mournmaw\|ossyra\|aphelion\|mother_creak\|selenne" | cut -c1-300 | sed 's/^/  /'
done
