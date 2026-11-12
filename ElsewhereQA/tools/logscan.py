#!/usr/bin/env python3
"""Scan a Minecraft log for ERROR / Exception / missing / Unknown / Failed to, grouped into distinct problems.

usage: logscan.py <latest.log>
Prints each distinct problem once (numbers and coordinates normalised), with a count and the first stack frame
that names an Elsewhere class.
"""
import re
import sys

PAT = re.compile(r"ERROR|Exception|[Mm]issing|Unknown|Failed to|Couldn't|Unable to")
IGNORE = [
    re.compile(p) for p in [
        r"\[qa\] (PASS|WARN)",
        r"Failed to (verify|fetch) .*(authentication|profile|session|signature)",
        r"Unable to resolve.*telemetry",
        r"\bsocket\b.*Connection refused",
        r"\[qa-client\]",
        r"Missing (data pack|resource pack)",  # empty optional packs
    ]
]

lines = open(sys.argv[1], errors="replace").read().splitlines()
groups = {}
order = []
i = 0
while i < len(lines):
    ln = lines[i]
    if PAT.search(ln) and not any(p.search(ln) for p in IGNORE) and not ln.startswith("\tat "):
        msg = re.sub(r"^\[[^\]]*\] \[[^\]]*\]( \([^)]*\))?:? ?", "", ln)
        key = re.sub(r"-?\d+(\.\d+)?", "N", msg)[:220]
        frame = ""
        j = i + 1
        while j < len(lines) and (lines[j].startswith("\tat ") or lines[j].startswith("Caused by") or lines[j].startswith("\t...")):
            if not frame and "dev.elsewhere" in lines[j]:
                frame = lines[j].strip()
            if lines[j].startswith("Caused by"):
                msg += " | " + lines[j]
            j += 1
        if key not in groups:
            groups[key] = [0, msg, frame, i + 1]
            order.append(key)
        groups[key][0] += 1
        i = j
        continue
    i += 1

print(f"{len(order)} distinct problems in {sys.argv[1]}")
for k in order:
    n, msg, frame, line = groups[k]
    print(f"- x{n} (line {line}) {msg[:400]}" + (f"\n    at {frame}" if frame else ""))
