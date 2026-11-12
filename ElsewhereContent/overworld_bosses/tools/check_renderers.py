#!/usr/bin/env python3
"""fix-no-invisible-mobs: every entity registered in src/main must have a renderer registered in src/client.
Usage: python3 tools/check_renderers.py   (run from the module folder; exit 1 on a missing renderer)."""
import re, sys, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
def text(d):
    return "\n".join(p.read_text() for p in (root / "src" / d).rglob("*.java"))
main, client = text("main"), text("client")
# constant name per registered id: `X = reg("id", ...)` / `X = Reg.entity("id", ...)`
reg = dict((m.group(1), m.group(2)) for m in re.finditer(r"([A-Z][A-Z0-9_]+)\s*=\s*(?:\w+\.)?(?:reg|entity)\w*\(\s*\"([a-z_0-9]+)\"", main))
rend = set(m.group(1) for m in re.finditer(r"EntityRenderer(?:s|Registry)\.register\(\s*(?:\w+\.)?([A-Z][A-Z0-9_]+)\s*,", client))
missing = sorted(i for c, i in reg.items() if c not in rend)
print(f"{root.name}: {len(reg)} entities registered, {len(rend)} renderers registered, missing: {missing or 'none'}")
sys.exit(1 if missing or not reg else 0)
