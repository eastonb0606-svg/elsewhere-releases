#!/bin/bash
# s-cloudshots (13:58): ONE client shot shard on a fresh Anthropic cloud Linux box (4 cores, 15 GB, JDK 21 only).
# Run from a checkout of branch cloud-src of the private repo:
#   bash ElsewhereQA/tools/cloud_shots.sh <shard> <scene spec> [watchdog minutes]     e.g.  ... 1 279-284
# Scene spec = ShotRunner's -PqaShotOnly: comma list of scene numbers ("n" in shotlist.json, 1-based) / ranges / lanes.
# Steps: JDK 25 (Temurin from api.adoptium.net), apt Xvfb + Mesa, merged jar (ElsewhereBeta: merge.py --sync, build dist),
# then ElsewhereQA runClientGameTest -PqaClient=shots under xvfb-run (llvmpipe). PNGs land in ~/cloudshots-<shard>/shots.
# Publishing: at the first PNGs, every ~20 new PNGs (or 8 min with new ones) and at the end, the PNGs plus this shard's
# index.json and logs are written as ONE commit (author/committer "Elsewhere build" <build@localhost>) built from a
# private index (the checkout is never touched) and force-pushed to refs/heads/cloud-shots-<shard>. Nothing else is
# pushed, ever. Hard stop: the client is stopped by 23:05 UTC (shots after 17:05 Chicago are not collected).
# Status for the polling agent: ~/cloudshots-<shard>/status (one line), ~/cloudshots-<shard>/DONE when finished.
set -u
SHARD="${1:?shard}"; SPEC="${2:?scene spec}"; WD_MAX="${3:-90}"
# boss_music client self-test only in m* shards (it adds a stand-in boss bar that would spoil scene shots; lead 16:47)
MUSICVM=""; case "$SHARD" in m*) MUSICVM="-PqaVm=-Dboss_music.selftest=true";; esac
R="$(git rev-parse --show-toplevel)"; cd "$R" || exit 2
W="$HOME/cloudshots-$SHARD"; S="$W/shots"; mkdir -p "$W" "$S" "$W/mods"; rm -f "$W/DONE" "$W/client.done"
LOG="$W/run.log"; exec > >(tee -a "$LOG") 2>&1
BRANCH="cloud-shots-$SHARD"; DEADLINE=$(date -u -d '2026-11-13T02:00:00Z' +%s)
st() { echo "$(date -u +%H:%M:%SZ) $*" | tee "$W/status"; }
export GIT_AUTHOR_NAME="Elsewhere build" GIT_AUTHOR_EMAIL="build@localhost" GIT_COMMITTER_NAME="Elsewhere build" GIT_COMMITTER_EMAIL="build@localhost"
QALOG="$R/ElsewhereQA/build/run/clientGameTest/logs/latest.log"

publish() {   # $1 = label. PNGs -> shots-3.0/<file>.png, logs/index -> shard-<n>/
  local idx="$W/pub.index" n=0 f b tree c parent=()
  rm -f "$idx"
  for f in "$S"/*.png; do
    [ -s "$f" ] || continue
    b=$(git -C "$R" hash-object -w "$f") || continue
    GIT_INDEX_FILE="$idx" git -C "$R" update-index --add --cacheinfo "100644,$b,shots-3.0/$(basename "$f")"; n=$((n+1))
  done
  for f in "$S/index.json" "$LOG" "$W/status" "$W/client.out" "$W/beta-build.out" "$W/merge.out" "$QALOG"; do
    [ -f "$f" ] || continue
    b=$(git -C "$R" hash-object -w "$f") && GIT_INDEX_FILE="$idx" git -C "$R" update-index --add --cacheinfo "100644,$b,shard-$SHARD/$(basename "$f")"
  done
  tree=$(GIT_INDEX_FILE="$idx" git -C "$R" write-tree) || return 1
  [ -f "$W/last-commit" ] && parent=(-p "$(cat "$W/last-commit")")
  c=$(echo "Cloud shots shard $SHARD ($SPEC): $n PNGs, $1" | git -C "$R" commit-tree "$tree" "${parent[@]}") || return 1
  if git -C "$R" push -q -f origin "$c:refs/heads/$BRANCH"; then echo "$c" > "$W/last-commit"; echo "published $n PNGs ($1) -> $BRANCH ${c:0:7}"
  else echo "push failed ($1)"; fi
}
fail() { st "FAILED: $*"; publish "failed: $*"; touch "$W/DONE"; exit 1; }

st "start shard $SHARD scenes $SPEC on $(nproc) cores, $(free -g | awk '/Mem/{print $2}') GB, $(uname -m), commit $(git rev-parse --short HEAD)"

# 1. JDK 25
if ! java -version 2>&1 | grep -q '"25'; then
  J=$(ls -d "$HOME"/jdk/jdk-25* 2>/dev/null | head -1)
  if [ -z "$J" ]; then
    A=x64; [ "$(uname -m)" = aarch64 ] && A=aarch64
    mkdir -p "$HOME/jdk" && curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/$A/jdk/hotspot/normal/eclipse" | tar -xz -C "$HOME/jdk"
    J=$(ls -d "$HOME"/jdk/jdk-25* 2>/dev/null | head -1)
  fi
  [ -n "$J" ] || fail "no JDK 25"
  export JAVA_HOME="$J" PATH="$J/bin:$PATH"
fi
java -version 2>&1 | head -1

# 2. Xvfb + Mesa (software GL/Vulkan) + the X libs GLFW/LWJGL load
SUDO=""; [ "$(id -u)" = 0 ] || SUDO=sudo
export DEBIAN_FRONTEND=noninteractive
st "apt"
$SUDO apt-get update -qq >/dev/null 2>&1
$SUDO apt-get install -y -qq xvfb xauth x11-utils libgl1 libgl1-mesa-dri libglx-mesa0 libegl1 mesa-utils libx11-6 libxext6 libxrender1 \
  libxrandr2 libxcursor1 libxi6 libxinerama1 libxxf86vm1 libxtst6 libxkbcommon0 rsync python3 >/dev/null 2>&1 || fail "apt install (xvfb/mesa)"
$SUDO apt-get install -y -qq mesa-vulkan-drivers libvulkan1 >/dev/null 2>&1 || echo "note: no mesa-vulkan-drivers"
$SUDO apt-get install -y -qq libasound2t64 >/dev/null 2>&1 || $SUDO apt-get install -y -qq libasound2 >/dev/null 2>&1 || true
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe
xvfb-run -a -s "-screen 0 1920x1080x24" glxinfo -B 2>&1 | grep -E 'OpenGL (renderer|core profile version)|Accelerated' || fail "glxinfo under xvfb"

# 3. merged jar
st "merged jar: merge.py --sync + build dist"
cd "$R/ElsewhereBeta" || fail "no ElsewhereBeta"
t0=$(date +%s)
build_jar() {
  cd "$R/ElsewhereBeta" && python3 tools/merge.py --sync > "$W/merge.out" 2>&1 || { tail -20 "$W/merge.out"; return 1; }
  grep -E '^(JAVA-DUP|MISSING|PATCH-FAILED|ENTRYPOINT-MISSING|EXTRA-MISSING)' merge-report.txt 2>/dev/null | head -5
  chmod +x gradlew
  ./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx1536m build dist > "$W/beta-build.out" 2>&1 \
    || { grep -E 'error:|FAILED|What went wrong' -A3 "$W/beta-build.out" | head -40; return 1; }
}
# Live source can be mid-edit (red). Fallback: the source of cloud-src KNOWN_GOOD (the 13:59 sync every first-wave shard
# built), with ElsewhereQA (runner + shot list) kept at HEAD.
KG="${KNOWN_GOOD:-f7708fa381532cc2e49da198d4ad8d1307b53953}"
if ! build_jar; then
  CUR=$(git -C "$R" rev-parse HEAD)
  git -C "$R" cat-file -e "$KG^{commit}" 2>/dev/null || git -C "$R" fetch -q --deepen=200 origin cloud-src 2>/dev/null || git -C "$R" fetch -q origin "$KG" 2>/dev/null
  if [ "$CUR" != "$KG" ] && git -C "$R" cat-file -e "$KG^{commit}" 2>/dev/null; then
    st "merged jar failed at ${CUR:0:7}; retrying with known-good source ${KG:0:7} (ElsewhereQA stays at ${CUR:0:7})"
    git -C "$R" checkout -q -f "$KG" && git -C "$R" checkout -q "$CUR" -- ElsewhereQA || fail "checkout $KG"
    rm -rf "$R/ElsewhereBeta/src/main" "$R/ElsewhereBeta/src/client" "$R/ElsewhereBeta/snap"
    build_jar || fail "merged jar build (${CUR:0:7} and ${KG:0:7})"
  else
    fail "merged jar build"
  fi
fi
JAR=$(ls -S dist/elsewhere-*.jar 2>/dev/null | head -1); [ -n "$JAR" ] || fail "no dist/elsewhere-*.jar"
rm -f "$W/mods"/*.jar; cp "$JAR" "$W/mods/"
# g* shards: compat scenes need Green Leaf + Test Meter beside the merged jar, and JEI (-Pjei) (lead 17:00)
JEIFLAG=""; case "$SHARD" in g*) cp "$R"/ElsewhereQA/cloud-extra-mods/*.jar "$W/mods/"; JEIFLAG="-Pjei";; esac
ls "$W/mods"
echo "merged jar $(basename "$JAR") $(stat -c %s "$JAR") bytes in $(( $(date +%s) - t0 )) s"

# 4. client shots under xvfb
cd "$R/ElsewhereQA" || fail "no ElsewhereQA"
chmod +x gradlew
now=$(date +%s); WD=$(( (DEADLINE - now) / 60 - 6 )); [ "$WD" -gt "$WD_MAX" ] && WD=$WD_MAX
[ "$WD" -ge 5 ] || fail "too late to start (deadline 02:00Z)"
st "client: scenes $SPEC, watchdog $WD min"
rm -rf build/run/clientGameTest/saves build/run/clientGameTest/logs
(
  last=0; lastt=$(date +%s)
  while [ ! -f "$W/client.done" ]; do
    sleep 45
    n=$(ls "$S"/*.png 2>/dev/null | wc -l)
    if { [ "$last" = 0 ] && [ "$n" -ge 3 ]; } || [ "$n" -ge $((last + 20)) ] || { [ "$n" -gt "$last" ] && [ $(( $(date +%s) - lastt )) -ge 480 ]; }; then
      [ -f "$W/client.done" ] && break
      publish "progress"; last=$n; lastt=$(date +%s)
    fi
  done
) &
PUB=$!
timeout --kill-after=60 $(( (WD + 12) * 60 )) xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx1g --console=plain \
  -Pfapi=0.166.0+26.3 -PqaMods="$W/mods" -PqaClient=shots -PqaShotList="$R/ElsewhereQA/shots-3.0/shotlist.json" -PqaShotDir="$S" \
  -PqaShotOnly="$SPEC" -PqaUser=ShotRunner -PqaWatchdog="$WD" $MUSICVM $JEIFLAG runClientGameTest > "$W/client.out" 2>&1
rc=$?
touch "$W/client.done"; wait "$PUB" 2>/dev/null
echo "client rc=$rc"; grep -E '\[shots\] (TOTAL|FAIL|MANUAL)|watchdog' "$QALOG" 2>/dev/null | tail -25
grep -E 'What went wrong|FAILED|Exception' -A2 "$W/client.out" | head -20
echo "boss_music client selftest:"; grep -E '\[boss_music-(ingame-)?selftest\] (FAIL|TOTAL)' "$W/client.out" | head -20
st "finished rc=$rc, $(ls "$S"/*.png 2>/dev/null | wc -l) PNGs; $(grep -E '\[shots\] TOTAL' "$QALOG" 2>/dev/null | tail -1 | sed 's/.*\[shots\]//')"
publish "final rc=$rc"
touch "$W/DONE"
