#!/bin/zsh
# Wait for a beta jar newer than the broken-client one, install it into mods-under-test/, then run the local client pass.
ROOT="/Users/admin/Library/Application Support/Claude/scratch-workspaces/f751a947-8d76-4adb-ba1a-6459a9c78498/076bc1c4-4795-405e-86c1-3b969a057515/scratch-2026-11-07-afb2b1"
J="$ROOT/ElsewhereBeta/dist/elsewhere-0.2.0-beta.jar"
while [ "$(shasum "$J" | cut -c1-7)" = "2ee725b" ]; do sleep 10; done
sleep 5
cp "$J" "$ROOT/ElsewhereQA/mods-under-test/elsewhere-0.2.0-beta.jar"
echo "installed $(shasum "$ROOT/ElsewhereQA/mods-under-test/elsewhere-0.2.0-beta.jar") at $(date)"
"$ROOT/ElsewhereQA/tools/client.sh" "${1:-2}"
