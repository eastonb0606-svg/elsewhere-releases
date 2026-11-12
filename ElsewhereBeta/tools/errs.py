import json, re, sys, collections, os
B = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
origin = json.load(open(os.path.join(B, "merge-origin.json")))
log = open(sys.argv[1] if len(sys.argv) > 1 else os.path.join(B, "build-compile.log")).read()
c = collections.Counter(); first = {}
for m in re.finditer(r"/ElsewhereBeta/src/(\S+?\.java):(\d+): error: (.*)", log):
    rel, line, msg = m.group(1), m.group(2), m.group(3)
    mod = origin.get(rel, "?")
    c[mod] += 1
    first.setdefault(mod, []).append(f"{rel}:{line}: {msg}")
for mod, n in c.most_common():
    print(f"{mod}: {n} errors")
    for x in first[mod][:int(os.environ.get("N", "4"))]:
        print("   ", x)
