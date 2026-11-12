#!/bin/zsh
# Remote half of tools/testbatch.sh (runs inside the heavy.sh slot, in the tree copy that holds server-launch.txt):
#   zsh tb_run.sh <port0> <profile>=<mod,mod,...> [<profile>=<mods> ...]
# profile world: the default world (noise overworld, structures, survival/easy), as the old modtests groups a/b.
# profile flat*: superflat plains (any profile name starting with flat, e.g. flat2 for a second flat JVM), no structures, no monster spawning (the kingdom lanes' world), as modtests-kingdom.
# One JVM per profile runs its modules one after another (-Delsewhere.testbatch). If that JVM dies mid-batch, the
# module it was running is recorded as JVM-DIED and a new boot (same world) goes on with the modules not yet run.
# Env: JH (JDK home), TB_XMX (heap, default 2560m), TB_TIMEOUT (s per module, default 1200), TB_FLATR (flat reset
# radius in chunks, default 8), TB_JD ("-Dk=v ..." for every JVM), TB_FLAGS ("mod:k=v;k=v|mod2:k=v": per-module -D),
# TB_PAR=1 (run the profiles in parallel: one JVM each at the same time; 0 or unset = one after another).
setopt null_glob
P=$1; shift
M=${0:A:h}; cd $M || exit 1
JAVA=$JH/bin/java; XMX=${TB_XMX:-2560m}; TO=${TB_TIMEOUT:-1200}
MAIN=$(sed -n 's/^MAIN=//p' server-launch.txt); CP=$(sed -n 's/^CP=//p' server-launch.txt)
ARGS=("${(@f)$(sed -n 's/^ARG=//p' server-launch.txt | grep -vE '^-Xmx|^@|^-D[a-z0-9_.]*selftest|^-Delsewhere\.(haltAfterSeconds|testbatch|betatest)')}")
PARGS=("${(@f)$(sed -n 's/^PARG=//p' server-launch.txt)}")
[ -n "$MAIN" ] && [ -n "$CP" ] || { echo "server-launch.txt incomplete"; exit 1; }
GEN='{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'
FLAGS=(); for e in ${(s:|:)TB_FLAGS}; do FLAGS+=("-Delsewhere.testbatch.flags.${e%%:*}=${e#*:}"); done

runprofile() { # <profile> <mods> <port>
  local prof=$1 mods=$2 port=$3 d=$M/run-tb-$1 boot=0 todo=$2 t0 pid wd rc
  rm -rf $d; mkdir -p $d; echo eula=true > $d/eula.txt
  {
    print -r -- "online-mode=false"; print -r -- "server-port=$port"; print -r -- "query.port=$port"; print -r -- "level-name=world_tb_$prof"
    print -r -- "max-tick-time=-1"; print -r -- "pause-when-empty-seconds=0"; print -r -- "enable-status=false"; print -r -- "spawn-protection=0"
    print -r -- "motd=Elsewhere test batch $prof"
    if [[ $prof == flat* ]]; then
      print -r -- "level-type=minecraft:flat"; print -r -- "generator-settings=$GEN"; print -r -- "generate-structures=false"
      print -r -- "spawn-monsters=false"; print -r -- "view-distance=6"; print -r -- "simulation-distance=6"
    else
      print -r -- "gamemode=survival"; print -r -- "difficulty=easy"
    fi
  } > $d/server.properties
  local extra=(); [[ $prof == flat* ]] && extra+=(-Delsewhere.testbatch.flatReset=${TB_FLATR:-8})
  : > $d/batch.txt
  while [ -n "$todo" ]; do
    boot=$((boot + 1)); t0=$(date +%s)
    local n=${#${(s:,:)todo}}
    (cd $d && exec $JAVA -Xmx$XMX "${ARGS[@]}" -Dtestbatch.selftest=true -Delsewhere.testbatch=$todo -Delsewhere.testbatch.timeout=$TO \
      "${FLAGS[@]}" "${extra[@]}" ${=TB_JD} -cp "$CP" $MAIN "${PARGS[@]}" > $d/console-$boot.log 2>&1) &
    pid=$!
    (sleep $(( n * TO + 300 )); kill $pid 2>/dev/null) </dev/null >/dev/null 2>&1 & wd=$!   # safety net for this JVM only
    wait $pid; rc=$?
    pkill -P $wd 2>/dev/null; kill $wd 2>/dev/null
    cp $d/logs/latest.log $d/boot-$boot.log 2>/dev/null
    echo "boot $boot exit $rc $(( $(date +%s) - t0 )) s: $todo" >> $d/batch.txt
    grep -q '\[testbatch\] FINISHED' $d/boot-$boot.log 2>/dev/null && break
    local began=(${(f)"$(grep -oE '\[testbatch\] BEGIN [a-z0-9_]+' $d/boot-$boot.log 2>/dev/null | awk '{print $3}')"})
    local ended=(${(f)"$(grep -oE '\[testbatch\] END [a-z0-9_]+' $d/boot-$boot.log 2>/dev/null | awk '{print $3}')"})
    local rest=() died=()
    for m in ${(s:,:)todo}; do
      if (( ${began[(Ie)$m]} )); then (( ${ended[(Ie)$m]} )) || died+=($m); else rest+=($m); fi
    done
    for m in $died; do echo "JVM-DIED $m (boot $boot, exit $rc)" >> $d/batch.txt; done
    if [ ${#began} = 0 ]; then echo "BOOT-FAILED (no module began; not retrying): $todo" >> $d/batch.txt; break; fi
    todo=${(j:,:)rest}
  done
}

rm -rf $M/run-tb-*(N)   # no stale profile of an earlier run reaches the summary
pids=(); i=0
for spec in "$@"; do
  prof=${spec%%=*}; mods=${spec#*=}
  if [ "${TB_PAR:-0}" != 0 ]; then runprofile $prof $mods $((P + i)) & pids+=($!); else runprofile $prof $mods $((P + i)); fi
  i=$((i + 1))
done
for p in $pids; do wait $p; done
for spec in "$@"; do echo "== ${spec%%=*}"; cat $M/run-tb-${spec%%=*}/batch.txt; done
