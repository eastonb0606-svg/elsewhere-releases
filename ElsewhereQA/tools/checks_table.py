#!/usr/bin/env python3
"""Turn the [qa] lines of a server log into a markdown checks table (for QA-REPORT.md).

usage: checks_table.py <latest.log>
"""
import re
import sys

rows = []
tot = None
for ln in open(sys.argv[1], errors="replace"):
    m = re.search(r"\[qa\] (PASS|FAIL|WARN) ([^:]+): (.*)$", ln)
    if m:
        rows.append(m.groups())
    m = re.search(r"\[qa\] TOTAL (.*)$", ln)
    if m:
        tot = m.group(1)
counts = {k: sum(1 for r in rows if r[0] == k) for k in ("PASS", "FAIL", "WARN")}
print(f"**Totals:** {counts['PASS']} pass, {counts['FAIL']} fail, {counts['WARN']} warn" + ("" if tot else " (run did not finish)"))
print()
print("| Result | Area | Check |")
print("|---|---|---|")
for s, a, d in rows:
    d = d.replace("|", "/")
    if len(d) > 330:
        d = d[:330] + "..."
    print(f"| {'**FAIL**' if s == 'FAIL' else s} | {a} | {d} |")
