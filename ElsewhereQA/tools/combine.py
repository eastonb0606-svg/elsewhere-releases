#!/usr/bin/env python3
"""Combine [qa] lines from several server logs into one checks table.

usage: combine.py <world.log> <boss logs...>   (later logs win for the same boss; boss checks from the world log are
dropped). Prints markdown: totals, then the table. Boss areas use the result of the LAST log that fought that boss.
"""
import re
import sys

PAT = re.compile(r"^\[(\d\d:\d\d:\d\d)\].*\[qa\] (PASS|FAIL|WARN) ([^:]+): (.*)$")


def rows(path):
    out = []
    for ln in open(path, errors="replace"):
        m = PAT.match(ln.rstrip("\n"))
        if m:
            out.append(m.groups())
    return out


world = [r for r in rows(sys.argv[1]) if not r[2].startswith("boss ")]
bosses = {}
for path in sys.argv[2:]:
    per = {}
    for t, s, a, d in rows(path):
        if a.startswith("boss "):
            per.setdefault(a, []).append((t, s, a, d))
    for a, rs in per.items():
        # keep only complete fights (a death or a definite failure), unless nothing else exists
        done = any("dies from" in d or s == "FAIL" for _, s, _, d in rs)
        if done or a not in bosses:
            bosses[a] = rs
allrows = world + [r for a in sorted(bosses) for r in bosses[a]]
c = {k: sum(1 for r in allrows if r[1] == k) for k in ("PASS", "FAIL", "WARN")}
print(f"**Totals:** {c['PASS']} pass, {c['FAIL']} fail, {c['WARN']} warn ({len(bosses)} bosses fought)\n")
print("| Result | Area | Check |")
print("|---|---|---|")
for t, s, a, d in allrows:
    d = d.replace("|", "/")
    if len(d) > 300:
        d = d[:300] + "..."
    print(f"| {'**FAIL**' if s == 'FAIL' else s} | {a} | {d} |")
