#!/usr/bin/env python3
"""Packs audit_fixtures/ into src/main/resources/elsewhere_duelist/dev/audit_fixtures.json.gz, so the boss_identity
fixture check also runs where only src/ travels (the merged 3.0 jar has no duelist.auditFixtures directory).
Format: {"format": 1, "sets": {set: {"expected": "PASS"|"FAIL", "files": {file name: raw text}}}}; every file of a set
except expected.txt, byte for byte (the broken-JSON sets stay broken). gzip with mtime 0, so the output only changes
when the fixtures do. The module self-test checks the packed copy against audit_fixtures/ in the lane run.
Usage: pack_audit_fixtures.py [fixtures dir] [out file]   (defaults: the module's own)"""
import gzip, json, os, re, sys

MOD = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = sys.argv[1] if len(sys.argv) > 1 else os.path.join(MOD, "audit_fixtures")
out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(MOD, "src", "main", "resources", "elsewhere_duelist", "dev", "audit_fixtures.json.gz")
NAME = re.compile(r"^[A-Za-z0-9_.-]+$")

sets = {}
for s in sorted(os.listdir(src)):
    d = os.path.join(src, s)
    if not os.path.isdir(d):
        continue
    assert NAME.match(s), "bad set name " + s
    files = {}
    for f in sorted(os.listdir(d)):
        p = os.path.join(d, f)
        if f == "expected.txt" or f == ".DS_Store" or not os.path.isfile(p):
            continue
        assert NAME.match(f), "bad file name " + f
        with open(p, encoding="utf-8", newline="") as fh:
            files[f] = fh.read()
    with open(os.path.join(d, "expected.txt"), encoding="utf-8") as fh:
        sets[s] = {"expected": fh.read().strip(), "files": files}

raw = json.dumps({"format": 1, "sets": sets}, sort_keys=True, ensure_ascii=False, indent=None).encode("utf-8")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "wb") as fh:
    fh.write(gzip.compress(raw, compresslevel=9, mtime=0))
print("packed %d sets (%d PASS) into %s: %d bytes" % (len(sets), sum(1 for v in sets.values() if v["expected"] == "PASS"), out, os.path.getsize(out)))
