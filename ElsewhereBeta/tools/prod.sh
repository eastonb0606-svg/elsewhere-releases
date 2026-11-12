#!/bin/zsh
# production check: clean Fabric 26.3 server (loader 0.20.1) + Fabric API + the Elsewhere beta jar + Test Meter + Green Leaf (+ extra jars)
# Usage: tools/prod.sh [label] [extra mod jar...]   label picks the server dir ($S/betaprod04_<label>) and log names (prod-<label>-latest.log)
# 0.4.0: Fabric API 0.166.0 from $S/fabricapi, Test Meter and Green Leaf from ROOT/Upload (read only), the jar from dist/elsewhere-<version>.jar
setopt null_glob
B="${0:A:h:h}"; ROOT="${B:h}"
S=/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad
LABEL=${1:-plain}; shift 2>/dev/null
P=$S/betaprod04_$LABEL
if [ ! -d $P ]; then mkdir -p $P; rsync -a --exclude world/ --exclude logs/ --exclude config/ --exclude mods/ --exclude out.log $S/betaprod/ $P/; fi
PORT=${PORT:-25674}; sed -i '' -E "s/^(server-port|query.port)=.*/\1=$PORT/" $P/server.properties
export JAVA_HOME=$S/jdk/jdk-25.0.5+8/Contents/Home
rm -rf $P/world $P/logs $P/config $P/mods; mkdir -p $P/mods
cp "${FAPI:-$S/fabricapi/fabric-api-0.166.0+26.3.jar}" "${TM:-$ROOT/Upload/TestMeter/testmeter-1.1.0.jar}" "${GL:-$ROOT/Upload/GreenLeaf/greenleaf-2.0.1.jar}" $P/mods/
for x in "$@"; do cp "$x" $P/mods/; done
V=$(sed -n "s/^version=//p" "$B/gradle.properties")
cp "${JAR:-$B/dist/elsewhere-$V.jar}" $P/mods/elsewhere-$V.jar
SLOT=$($S/mcslot.sh acquire "" elsewhere_beta_prod_$LABEL)
trap '$S/mcslot.sh release $SLOT' EXIT
cd $P && $JAVA_HOME/bin/java -Xmx1536m -Delsewhere.prodcheck=true -jar fabric-server-launch.jar nogui > $P/out.log 2>&1 < /dev/null; rc=$?
echo "prod[$LABEL] exit $rc"
(cd $P/mods && shasum *.jar)
grep -E 'Loading [0-9]+ mods' $P/logs/latest.log | head -2
cp $P/logs/latest.log "$B/prod-$LABEL-latest.log"
"$B/tools/summarize.sh" $P/logs/latest.log
