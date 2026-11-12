#!/bin/zsh
# Remote half of imac_integ.sh modtests-kingdom: zsh mtk_run.sh <port0> <run#> <mod...> (mods: kingdom court villages lore).
# Each module gets its own JVM and a FRESH superflat world (bedrock/2 dirt/grass, plains, no structures, no monster
# spawning: the world the kingdom lanes test in) in run-<mod>/. All modules of one round run in parallel; waits for all.
setopt null_glob
P=$1; I=$2; shift 2
M=${0:A:h}; cd $M || exit 1
JAVA=$JH/bin/java
MAIN=$(sed -n 's/^MAIN=//p' server-launch.txt); CP=$(sed -n 's/^CP=//p' server-launch.txt)
ARGS=("${(@f)$(sed -n 's/^ARG=//p' server-launch.txt | grep -vE '^-Xmx|^@|^-D(elsewhere|kingdom|court|villages|lore|duelist|hrafnholt|azmar|mirewick|bosshealth|summoner|arsenal|nether_end_bosses|greenhaze_hush|petalwind_glasswaste|verdigris_ironpeak|overworld_bosses|boss_ui|gear2|gear3)\.')}")  # @argfile: Loom writes it only inside runServer; -cp below replaces it
PARGS=("${(@f)$(sed -n 's/^PARG=//p' server-launch.txt)}")
[ -n "$MAIN" ] && [ -n "$CP" ] || { echo "server-launch.txt incomplete"; exit 1; }
GEN='{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'
pids=(); n=0
for m in "$@"; do
  d=$M/run-$m; rm -rf $d; mkdir -p $d; echo eula=true > $d/eula.txt
  port=$((P + n)); n=$((n + 1)); hx=2048; case $m in verdigris_ironpeak|petalwind_glasswaste|greenhaze_hush|nether_end_bosses|overworld_bosses|elsewhere) hx=2800;; esac; vd=4; [ $m = court ] && vd=6
  {
    print -r -- "online-mode=false"; print -r -- "server-port=$port"; print -r -- "query.port=$port"; print -r -- "level-name=world_${m}_r$I"
    print -r -- "level-type=minecraft:flat"; print -r -- "generator-settings=$GEN"; print -r -- "generate-structures=false"; print -r -- "spawn-monsters=false"
    print -r -- "view-distance=$vd"; print -r -- "simulation-distance=$vd"; print -r -- "max-tick-time=-1"; print -r -- "pause-when-empty-seconds=0"
    print -r -- "enable-status=false"; print -r -- "spawn-protection=0"; print -r -- "motd=Elsewhere $m self-test"
  } > $d/server.properties
  (cd $d && exec $JAVA -Xmx${hx}m "${ARGS[@]}" -D$m.selftest=true -D$m.selftest.exit=true -Delsewhere.haltAfterSeconds=540 -cp "$CP" $MAIN "${PARGS[@]}" > $d/console.log 2>&1) &
  pids+=($!)
done
# safety net for the JVMs started here only (the in-game halt fires at 540 s)
(sleep 660; for p in $pids; do kill $p 2>/dev/null; done) </dev/null >/dev/null 2>&1 & wd=$!  # no inherited fds: ssh must not wait for it
for p in $pids; do wait $p; done
pkill -P $wd 2>/dev/null; kill $wd 2>/dev/null
for m in "$@"; do echo "$m: $(grep -E -- '-selftest\] TOTAL' $M/run-$m/logs/latest.log | sed 's/.*\] TOTAL/TOTAL/')"; done
