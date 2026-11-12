# Elsewhere 3.0.0: cloud test runs (fresh Linux box)

Branch `cloud-src` of the private repo `eastonb0606-svg/elsewhere-releases` is a mirror of the source.
`_cloud/sync.sh` on the Mac overwrites it. Do not commit to `cloud-src` from a cloud session. Report results in chat or push them to a branch of your own.
Layout matches ROOT: `Elsewhere/` (core), `ElsewhereBeta/` (merge glue, tools, overlay), `ElsewhereContent/<module>/`, `ElsewhereContent/art2/`.

## 0. Network hosts (an empty Gradle/Loom cache downloads from these)
These hosts come from the build files:
- `services.gradle.org`: the Gradle 9.8.2 wrapper distribution. It may redirect to `github.com` / `objects.githubusercontent.com`.
- `maven.fabricmc.net`: Fabric Loom 1.19-SNAPSHOT, fabric-loader 0.20.1, fabric-api 0.165.2+26.3.
- `repo.maven.apache.org` (mavenCentral).
- `plugins.gradle.org`, which redirects to `plugins-artifacts.gradle.org` (gradlePluginPortal).
- `maven.blamejared.com`: the JEI 31.17.0.109 API, used by ElsewhereBeta and jei_compat.

Loom downloads these implicitly for Minecraft 26.3:
- `piston-meta.mojang.com`: the version manifest and version JSON.
- `piston-data.mojang.com`: the server and client jars.
- `libraries.minecraft.net`: Minecraft's libraries.
- `resources.download.minecraft.net`: assets, needed only for client runs and `downloadAssets`.

JDK 25 comes from the distro, or from `api.adoptium.net` / `github.com` (Temurin).

## 1. JDK 25
```bash
java -version 2>&1 | grep -q '"25' || {
  sudo apt-get update && (sudo apt-get install -y temurin-25-jdk || sudo apt-get install -y openjdk-25-jdk-headless) || {
    mkdir -p ~/jdk && curl -fsSL "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse" | tar -xz -C ~/jdk
    export JAVA_HOME=$(ls -d ~/jdk/jdk-25*) && export PATH=$JAVA_HOME/bin:$PATH; }; }
sudo apt-get install -y rsync python3 >/dev/null 2>&1 || true   # merge.py --sync uses rsync
java -version
```

## 2. Checkout
```bash
git fetch origin cloud-src && git checkout cloud-src      # in a clone of eastonb0606-svg/elsewhere-releases
cd "$(git rev-parse --show-toplevel)"; export R=$PWD
```

## 3. One module: build + headless self-test (boss_ui)
In `build.gradle`, every module's `runs.server` block already sets `-D<flag>.selftest=true -D<flag>.selftest.exit=true`. `runServer` starts a dedicated server, runs the self-test and exits.
```bash
cd $R/ElsewhereContent/boss_ui
chmod +x gradlew && ./gradlew --no-daemon --console=plain build
mkdir -p run && echo eula=true > run/eula.txt
printf 'online-mode=false\nserver-port=25681\nmax-tick-time=-1\npause-when-empty-seconds=0\nenable-status=false\nspawn-protection=0\n' > run/server.properties
./gradlew --no-daemon --console=plain runServer -Dorg.gradle.jvmargs=-Xmx1536m 2>&1 | tee selftest.out
grep -E -- 'selftest\] (TOTAL|FAIL)|PASS|/ERROR\]' run/logs/latest.log | tail -20
```
To run the self-test by hand, add `-D<flag>.selftest=true -D<flag>.selftest.exit=true -Delsewhere.haltAfterSeconds=540` to the server JVM.
For other modules, use the module folder name. Exceptions:
- kingdom_court uses `court`, kingdom_villages uses `villages`, kingdom_lore uses `lore`, and kingdom_duelist uses `duelist`.
- The rivals use `hrafnholt`, `azmar` and `mirewick`.
- The core uses `elsewhere.selftest`.

Kingdom-set modules expect a superflat plains world with no structures and no monster spawning. `ElsewhereBeta/tools/mtk_run.sh` has the exact server.properties.

Note: `kingdom_court/devlibs/elsewhere.jar` (67 MB) is not in the mirror because of the 50 MB cap. It is only a runtime extra, guarded by `if exists`, so the court module still compiles.

## 4. Merged jar + full module self-test list
```bash
cd $R/ElsewhereBeta
python3 tools/merge.py --sync            # snapshots ElsewhereContent/* + art2 into snap/, regenerates src/main + src/client
cat merge-report.txt | grep -E '^(JAVA-DUP|RES-DUP|MISSING|PATCH-FAILED|ENTRYPOINT-MISSING|EXTRA-MISSING)' | head
chmod +x gradlew && ./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx1536m build dist   # jar -> dist/elsewhere-3.0.0.jar
mkdir -p run && echo eula=true > run/eula.txt
printf 'online-mode=false\nserver-port=25671\nmax-tick-time=-1\npause-when-empty-seconds=0\nenable-status=false\nspawn-protection=0\ndifficulty=easy\n' > run/server.properties
./gradlew --no-daemon --console=plain runServer -Dorg.gradle.jvmargs=-Xmx1536m   # combined smoke ([betatest] TOTAL)
```
Self-tests inside the merged build use the same groups as `imac_int.sh modtests-all`. One JVM holding every module self-test runs out of heap.
```bash
mt() { # $1 tag, $2 comma list of -D flags
  cp build.gradle build.gradle.orig
  MT_PROPS=$2 MT_HALT=420 python3 tools/gen_mt_gradle.py build.gradle.orig build.gradle
  rm -rf run/world run/logs; ./gradlew --no-daemon --console=plain runServer -Dorg.gradle.jvmargs=-Xmx1536m > mt-$1.out 2>&1
  cp run/logs/latest.log modtests-$1-latest.log; mv build.gradle.orig build.gradle
  grep -E -- '-selftest\] TOTAL|selftest.*(passed|TOTAL)' modtests-$1-latest.log; grep -cE '/ERROR\]|Exception' modtests-$1-latest.log; }
mt a elsewhere.selftest,arsenal.selftest,summoner.selftest,verdigris_ironpeak.selftest,overworld_bosses.selftest,boss_ui.selftest
mt b petalwind_glasswaste.selftest,greenhaze_hush.selftest,nether_end_bosses.selftest,gear2.selftest
mt c gear3.selftest,bosshealth.selftest
```
Kingdom set: run each module in its own JVM and a fresh superflat world. These are kingdom, court, villages, lore, duelist, hrafnholt, azmar and mirewick, the same as `imac_int.sh modtests-kingdom`.
```bash
cp build.gradle build.gradle.orig
MT_PROPS=none MT_HALT=0 MT_LAUNCH=1 python3 tools/gen_mt_gradle.py build.gradle.orig build.gradle
./gradlew --no-daemon --console=plain -Dorg.gradle.jvmargs=-Xmx1536m classes writeServerLaunch
cp tools/mtk_run.sh . && JH=$JAVA_HOME zsh mtk_run.sh 25751 1 kingdom court villages lore duelist hrafnholt azmar mirewick   # needs zsh (apt-get install zsh); 8 JVMs x 2 GB: use fewer modules per call on small boxes
mv build.gradle.orig build.gradle
for m in kingdom court villages lore duelist hrafnholt azmar mirewick; do echo "$m: $(grep -E -- '-selftest\] TOTAL' run-$m/logs/latest.log)"; grep -E '\bFAIL\b' run-$m/logs/latest.log | grep -v TOTAL | head; done
```
Pass bar: every TOTAL line has 0 failed, and there are 0 `/ERROR]` lines apart from the known vanilla "No key layers" notice.
