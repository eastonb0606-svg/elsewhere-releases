#!/bin/zsh
# Loads the 2.0.2 world (lane-int/old202-run/world_old202, made by run_old202.sh) in the merged build: bosshealth legacy check.
# usage (on the iMac, inside heavy.sh): zsh run_legacy.sh   (needs lane-int/ElsewhereBeta-mtk built with writeServerLaunch)
JH=/Users/eastonbrown/mcbuild/jdk/Contents/Home; L=/Users/eastonbrown/mcbuild/lane-int; M=$L/ElsewhereBeta-mtk; D=$L/legacy-run
rm -rf $D; mkdir -p $D; echo eula=true > $D/eula.txt; rsync -a $L/old202-run/world_old202/ $D/world_old202/
cp $L/ElsewhereBeta/tools/legacy-old202.txt $D/legacy-old202.txt
MAIN=$(sed -n 's/^MAIN=//p' $M/server-launch.txt); CP=$(sed -n 's/^CP=//p' $M/server-launch.txt)
ARGS=("${(@f)$(sed -n 's/^ARG=//p' $M/server-launch.txt | grep -vE '^-Xmx|^@|^-D(elsewhere|kingdom|court|villages|lore|duelist|hrafnholt|azmar|mirewick|bosshealth)\.')}")
PARGS=("${(@f)$(sed -n 's/^PARG=//p' $M/server-launch.txt)}")
printf 'online-mode=false\nserver-port=26291\nquery.port=26291\nlevel-name=world_old202\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-monsters=false\nview-distance=4\nsimulation-distance=4\nmax-tick-time=-1\npause-when-empty-seconds=0\nenable-status=false\n' > $D/server.properties
cd $D && $JH/bin/java -Xmx2048m "${ARGS[@]}" -Dbosshealth.selftest=true -Dbosshealth.legacy=$D/legacy-old202.txt -Dbosshealth.selftest.exit=true -Delsewhere.haltAfterSeconds=300 -cp "$CP" $MAIN "${PARGS[@]}" > $D/console.log 2>&1
grep -E 'bosshealth-selftest|Exception|/ERROR' $D/logs/latest.log | cut -c1-220 | head -60
