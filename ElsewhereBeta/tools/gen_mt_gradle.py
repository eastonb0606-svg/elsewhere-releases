"""Write a modtests variant of build.gradle (used by imac_integ.sh).

argv: <build.gradle> <out>. env: MT_PROPS (comma list, default: every non-kingdom module self-test; "none" = no test
properties), MT_HALT (seconds, 0 = no halt), MT_LAUNCH=1 appends a task that writes the runServer launch line to
server-launch.txt (modtests-kingdom starts one JVM per module from it).
The property lines of the runs.server block are replaced whatever variant build.gradle holds (betatest, selftest, a
modtests copy), so another lane's temporary edit can no longer break the generator.
"""
import os, re, sys

src, out = sys.argv[1], sys.argv[2]
s = open(src).read()
props = ["elsewhere.selftest", "petalwind_glasswaste.selftest", "verdigris_ironpeak.selftest", "greenhaze_hush.selftest",
         "overworld_bosses.selftest", "nether_end_bosses.selftest", "arsenal.selftest", "summoner.selftest", "boss_ui.selftest",
         "gear2.selftest"]  # the kingdom set runs per module (modtests-kingdom): together they run out of heap and share caps
if os.environ.get("MT_PROPS"):
    props = [] if os.environ["MT_PROPS"] == "none" else os.environ["MT_PROPS"].split(",")
halt = int(os.environ.get("MT_HALT", "420") or 0)

m = re.search(r"(\n\t\tserver \{\n)(.*?)(\n\t\t\})", s, re.S)
assert m, "runs.server block not found in build.gradle"
body = [ln for ln in m.group(2).split("\n") if not ln.strip().startswith("property ")]
body += ['\t\t\tproperty "%s", "true"' % p for p in props]
if halt > 0:
    body.append('\t\t\tproperty "elsewhere.haltAfterSeconds", "%d"' % halt)
s = s[:m.start(2)] + "\n".join(body) + s[m.end(2):]
if os.environ.get("MT_LAUNCH") == "1":
    s += '''
// modtests-kingdom: the runServer launch line (main class, classpath, JVM args), so each module can run in its own JVM/world.
tasks.register('writeServerLaunch') {
	dependsOn 'classes', 'configureLaunch'
	doLast {
		def t = tasks.named('runServer').get()
		def lines = ['MAIN=' + t.mainClass.get(), 'CP=' + t.classpath.asPath]
		t.allJvmArgs.each { lines << 'ARG=' + it }
		t.args.each { lines << 'PARG=' + it }
		file('server-launch.txt').text = lines.join('\\n') + '\\n'
	}
}
'''
open(out, "w").write(s)
