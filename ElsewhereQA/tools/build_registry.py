#!/usr/bin/env python3
"""Build ElsewhereQA/vqa/work/author/registry.txt from the Java registration code (not lang keys).
For every Java file with Registry.register(BuiltInRegistries.ENTITY_TYPE|ITEM|BLOCK, ...) it finds the wrapper methods that do the
registration, then collects the string-literal id passed to each wrapper call (and `X_ID = "..."` constants in single-registration files).
Output lines: `<entity|item|block> elsewhere:<id>  <source file>`. Heuristic (wrapper calls with a literal first argument)."""
import glob, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
files = glob.glob(ROOT + "/Elsewhere/src/**/*.java", recursive=True) + glob.glob(ROOT + "/ElsewhereContent/*/src/main/java/**/*.java", recursive=True)
KIND = {"ENTITY_TYPE": "entity", "ITEM": "item", "BLOCK": "block"}
REG = re.compile(r"Registry\.register\(\s*(?:net\.minecraft\.core\.registries\.)?BuiltInRegistries\.(ENTITY_TYPE|ITEM|BLOCK)\b")
out = {}
def add(kind, i, src):
    out.setdefault((kind, i), src)
for f in files:
    t = open(f, encoding="utf-8", errors="replace").read()
    regs = [(m.start(), KIND[m.group(1)]) for m in REG.finditer(t)]
    if not regs:
        continue
    src = os.path.relpath(f, ROOT)
    kinds = {k for _, k in regs}
    wrappers = {}
    for pos, k in regs:
        head = t[:pos]
        ms = list(re.finditer(r"(?:public |private |protected |static |final |<[^>(]*> )+[\w<>?,.\[\] ]+? (\w+)\(([^)]*)\)\s*(?:throws \w+\s*)?\{", head))
        if ms and pos - ms[-1].start() < 1500 and re.match(r"(final )?String ", ms[-1].group(2).strip()):
            wrappers.setdefault(ms[-1].group(1), set()).add(k)
    for name, ks in wrappers.items():
        for m in re.finditer(r"\b%s\(\s*\"([a-z][a-z0-9_]+)\"" % re.escape(name), t):
            for k in ks:
                add(k, m.group(1), src)
            if "ModBlocks" in src and "block" in ks:
                add("item", m.group(1), src)
    if not wrappers or len(regs) > 0:
        # direct registrations: id constants / id("x") in the same file
        for m in re.finditer(r"\b(?:String\s+)?\w*ID\w*\s*=\s*\"([a-z][a-z0-9_]+)\"", t):
            for k in kinds:
                add(k, m.group(1), src)
        for m in re.finditer(r"(?:Content|Elsewhere|KingdomContent|Reg)?\.?\bid\(\s*\"([a-z][a-z0-9_]+)\"\s*\)", t):
            pass
    if "registerSoldierType" in t:
        for m in re.finditer(r"registerSoldierType\(\s*\"([a-z_0-9]+)\"", t):
            add("entity", m.group(1), src)
            add("item", m.group(1) + "_spawn_egg", src)
# Registrations done in loops/tables/other wrappers: a lang-listed id (kind from lang) counts only when its bare name is also a
# quoted string literal in some main-source Java file (ids that exist only in lang files are dropped).
alltext = []
for f in files:
    if "/src/main/java/" in f.replace(os.sep, "/") or "/src/main/" in f:
        alltext.append(open(f, encoding="utf-8", errors="replace").read())
blob = "\n".join(alltext)
literals = set(re.findall(r"\"([a-z][a-z0-9_]+)\"", blob))
try:
    for line in open(os.path.join(ROOT, "ElsewhereQA/vqa/work/author/ids.txt")):
        parts = line.split()
        if len(parts) >= 2 and parts[1].startswith("elsewhere:"):
            i = parts[1][10:]
            base = i[:-len("_spawn_egg")] if i.endswith("_spawn_egg") else i
            if i in literals or ((i.endswith("_spawn_egg") and base in literals) and False):
                add(parts[0], i, "java literal + lang kind")
except OSError:
    pass
# Townsfolk are registered by role id (elsewhere:<role>), listed in the kingdom skin self-test: {"kingdom", "role"} pairs
tf = os.path.join(ROOT, "ElsewhereContent/kingdom/src/main/java/dev/elsewhere/kingdom/dev/SelfTestTownsfolkSkins.java")
if os.path.exists(tf):
    for m in re.finditer(r"\{\"(?:aldermark|hrafnholt|azmar|mirewick)\",\s*\"([a-z_]+)\"\}", open(tf, encoding="utf-8").read()):
        add("entity", m.group(1), "kingdom SelfTestTownsfolkSkins ROLES")
with open(os.path.join(ROOT, "ElsewhereQA/vqa/work/author/registry.txt"), "w") as fh:
    for (k, i), s in sorted(out.items()):
        fh.write("%s elsewhere:%s  %s\n" % (k, i, s))
print(len(out), "ids")
