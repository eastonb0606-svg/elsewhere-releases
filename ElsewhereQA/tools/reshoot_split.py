#!/usr/bin/env python3
"""Split the scenes that need a reshoot into N shards of roughly equal shot counts.

Usage: python3 -I ElsewhereQA/tools/reshoot_split.py N [--old shotlist.bak-1530.json] [--vqa Review/vqa-3.0.json] [--shotlist shots-3.0/shotlist.json]
A scene is listed when any of its shots has a vqa status not in (pass, manual), or when its steps differ from the old shotlist
(default ElsewhereQA/shots-3.0/shotlist.bak-1530.json). Scenes whose steps are a manual step are never listed.
Prints one comma-separated scene-number list per shard ("shard i: n,n,n  (shots=K)"), then a summary.
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def main():
    args = sys.argv[1:]
    opt = {"--old": os.path.join(ROOT, "ElsewhereQA", "shots-3.0", "shotlist.bak-1530.json"),
           "--vqa": os.path.join(ROOT, "Review", "vqa-3.0.json"),
           "--shotlist": os.path.join(ROOT, "ElsewhereQA", "shots-3.0", "shotlist.json")}
    pos = []
    i = 0
    while i < len(args):
        if args[i] in opt and i + 1 < len(args):
            opt[args[i]] = args[i + 1]
            i += 2
        else:
            pos.append(args[i])
            i += 1
    nshards = int(pos[0]) if pos else 6
    new = json.load(open(opt["--shotlist"], encoding="utf-8"))["scenes"]
    status = {s["file"]: s.get("status") for s in json.load(open(opt["--vqa"], encoding="utf-8"))["shots"]}
    try:
        old = {s["shots"][0]["file"]: s["steps"] for s in json.load(open(opt["--old"], encoding="utf-8"))["scenes"] if s["shots"]}
    except Exception as e:
        print("note: no old shotlist (%s); steps-changed test skipped" % e, file=sys.stderr)
        old = None
    picked, why = [], {"status": 0, "changed": 0}
    for sc in new:
        if not sc["shots"] or (sc["steps"] and sc["steps"][0][0] == "manual"):
            continue
        bad = any(status.get(sh["file"], "missing") not in ("pass", "manual") for sh in sc["shots"])
        changed = old is not None and old.get(sc["shots"][0]["file"]) != sc["steps"]
        if bad or changed:
            picked.append(sc)
            why["status"] += bad
            why["changed"] += changed
    shards = [[] for _ in range(nshards)]
    loads = [0] * nshards
    for sc in sorted(picked, key=lambda s: -len(s["shots"])):   # greedy: biggest scene to the lightest shard
        k = loads.index(min(loads))
        shards[k].append(sc["n"])
        loads[k] += len(sc["shots"])
    for k, (ns, ld) in enumerate(zip(shards, loads), 1):
        print("shard %d: %s  (scenes=%d shots=%d)" % (k, ",".join(map(str, sorted(ns))), len(ns), ld))
    print("total: %d scenes, %d shots (status-bad %d, steps-changed %d)" % (len(picked), sum(loads), why["status"], why["changed"]))


if __name__ == "__main__":
    main()
