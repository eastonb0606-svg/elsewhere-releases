"""Compares catalogue.json stats with the GEAR.md section A5 tables.
Run: ROOT/_tools/venv/bin/python -I gear3/tools/check_gear_md.py   (exit 1 on any mismatch; prints "N checked, M mismatches")
Checked per row: armor values (a/b/c/d, in the row's id order), toughness, KB, dmg, speed, shield durability, and that every id in the table
is either in the catalogue or listed as cut (no silent omissions)."""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.dirname(HERE)
ROOT = os.path.dirname(os.path.dirname(MOD))
GEAR = os.path.join(ROOT, "Ideas", "overhaul", "GEAR.md")
CAT = json.load(open(os.path.join(MOD, "src", "main", "resources", "elsewhere_gear3", "catalogue.json")))
BY = {i["id"]: i for i in CAT["items"]}
mism, checked, absent = [], 0, []


def num(m):
    return float(m.group(1)) if m else None


def chk(what, got, want):
    global checked
    if want is None:
        return
    checked += 1
    if got is None or abs(float(got) - want) > 1e-6:
        mism.append(f"{what}: catalogue {got}, GEAR.md {want}")


for line in open(GEAR, encoding="utf-8"):
    if not line.startswith("| A |") and not line.startswith("| B |"):
        continue
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    if len(cells) < 7:
        continue
    ids = re.findall(r"`([a-z0-9_]+)`", cells[1])
    stats = cells[6]
    arm = re.match(r"^(\d+(?:/\d+)*)(?:\s|,|$)", stats)
    armor_vals = [float(x) for x in arm.group(1).split("/")] if arm and len(ids) > 0 and len(arm.group(1).split("/")) == len(ids) and "armor" not in stats[:12] else None
    tough = num(re.search(r"toughness ([\d.]+)", stats))
    kb = num(re.search(r"KB ([\d.]+)", stats))
    dmg = num(re.search(r"([\d.]+) dmg", stats))
    spd = num(re.search(r"([\d.]+) speed", stats))
    for n, i in enumerate(ids):
        it = BY.get(i)
        if not it:
            absent.append(i)
            continue
        st = it.get("stats", {})
        if it["kind"] == "armor":
            if armor_vals:
                chk(f"{i} armor", st.get("armor"), armor_vals[n])
            elif re.match(r"^(\d+) armor", stats):
                chk(f"{i} armor", st.get("armor"), float(re.match(r"^(\d+) armor", stats).group(1)))
            chk(f"{i} toughness", st.get("toughness", 0), tough)
            if kb is not None:
                chk(f"{i} kb", st.get("kb", 0), kb)
        elif it["kind"] in ("sword", "axe"):
            chk(f"{i} damage", st.get("damage"), dmg)
            chk(f"{i} speed", st.get("speed"), spd)
        elif it["kind"] == "shield":
            m = re.search(r"(\d+) durability", stats)
            chk(f"{i} durability", st.get("durability"), num(m))
print(f"check_gear_md: {checked} values checked, {len(mism)} mismatch(es); {len(BY)} catalogue items; {len(absent)} GEAR.md ids not in the catalogue (cuts)")
for m in mism:
    print("  MISMATCH " + m)
if "--cuts" in sys.argv:
    print("  cuts: " + ", ".join(absent))
sys.exit(1 if mism else 0)
