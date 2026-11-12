#!/usr/bin/env python3
"""Write ElsewhereQA/shots-3.0/overrides/idfix.json: fixes for invalid or unspawnable ids in the compiled shotlist.
Run order (after the author files are final):
  SHOTS_NO_IDFIX=1 python3 -I ElsewhereQA/tools/shots_compile.py && python3 -I ElsewhereQA/tools/make_idfix.py && python3 -I ElsewhereQA/tools/shots_compile.py
Takes the scene steps as compiled (templates + author + lead), rewrites ids (REMAP) or routes the scene to manual (MANUAL), keyed by first shot."""
import json, os, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.argv = sys.argv[:1]
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import shots_compile as sc  # noqa: E402  (only its helpers; main() is not run)

REMAP = {"elsewhere:hrafnholt": "elsewhere:hrafnholt_jarl", "elsewhere:hrafnholt_thrall": "elsewhere:thrall"}
SOLDIER = "elsewhere:kingdom_man_at_arms"   # units are kingdom soldier entities with KingdomFaction NBT (summon elsewhere:kingdom_<unit> ~ ~ ~4 {KingdomFaction:"elsewhere:azmar"})
NOSUMMON = ("elsewhere:hrafnholt_longship", "elsewhere:rift", "elsewhere:army_rank")
MANUAL = {
    "elsewhere:loot": "JEI scene: needs the JEI client section (-Pjei); elsewhere:loot is a JEI category, not an entity or item",
    "elsewhere:kingdom_shop": "JEI scene: needs the JEI client section (-Pjei); elsewhere:kingdom_shop is a JEI category, not an entity or item",
    "elsewhere:court_marshal": "court rulers are CourtRuler entities spawned by the court module (castle/KingdomApi); no summonable id elsewhere:court_marshal",
    "elsewhere:hrafnholt_longship": "HrafnholtLongship is noSummon: /summon refuses it; it only exists through Longship.sail voyages (needs a shore, dev LongshipDemo.start) - ShotRunner has no step for that",
}
path = os.path.join(ROOT, "ElsewhereQA", "shots-3.0", "shotlist.json")
scenes = json.load(open(path))["scenes"]
out, report = {}, []
for s in scenes:
    if not s["shots"] or s["steps"][0][0] == "manual":
        continue
    bad = {i for _, _, i in sc.invalid_ids([s])}
    bad |= {st[1] for st in s["steps"] if st[0] == "summon" and st[1] in NOSUMMON}
    if not bad:
        continue
    man = next((MANUAL[i] for i in sorted(bad) if i in MANUAL), None)
    key = s["shots"][0]["file"]
    if man:
        out[key] = {"manual": man}
        report.append((s["n"], "manual", sorted(bad)))
        continue
    steps = json.loads(json.dumps(s["steps"]))
    for st in steps:
        if st[0] == "summon" and st[1] == "elsewhere:azmar_soldier_spawn_egg":
            st[1], st[2] = SOLDIER, '{PersistenceRequired:1b,KingdomFaction:"elsewhere:azmar"}'
    txt = json.dumps(steps)
    txt = txt.replace("elsewhere:azmar_soldier_spawn_egg", SOLDIER)
    for a, b in REMAP.items():
        txt = re.sub(re.escape(a) + r'(?![a-z0-9_])', b, txt)
    steps = json.loads(txt)
    out[key] = {"n": str(s["n"]), "steps": steps}
    report.append((s["n"], "remap", sorted(bad)))
d = os.path.join(ROOT, "ElsewhereQA", "shots-3.0", "overrides")
os.makedirs(d, exist_ok=True)
json.dump(out, open(os.path.join(d, "idfix.json"), "w"), indent=1)
for r in report:
    print("idfix: scene %d %s %s" % r)
print("idfix.json: %d scenes" % len(out))
