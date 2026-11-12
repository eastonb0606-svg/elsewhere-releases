#!/usr/bin/env python3
"""Static 0.4.0 checks on a built Elsewhere jar. usage: v04_static.py <jar>
JEI info page count, Pale Hollow drop tables, Herding Crook enchantable tags, arena worldgen data, version."""
import json, re, sys, zipfile
jar = zipfile.ZipFile(sys.argv[1]); names = set(jar.namelist())
def js(p):
    try: return json.loads(jar.read(p))
    except Exception: return None
fmj = js("fabric.mod.json") or {}
print("version:", fmj.get("version"))
for p in sorted(n for n in names if n.endswith("info.json") and "jei" in n):
    d = js(p)
    n = len(d) if isinstance(d, list) else len(d.get("pages", d)) if isinstance(d, dict) else "?"
    print(f"jei info: {p}: {n} pages")
def items_in(o, acc):
    if isinstance(o, dict):
        if o.get("type") in ("minecraft:item", "item") and "name" in o: acc.add(o["name"])
        for v in o.values(): items_in(v, acc)
    elif isinstance(o, list):
        for v in o: items_in(v, acc)
    return acc
for ent in ["selenne", "mother_creak", "eyeblossom_wisp", "tempestra"]:
    p = f"data/elsewhere/loot_table/entities/{ent}.json"
    print(f"loot {ent}:", sorted(items_in(js(p), set())) if p in names else "NO TABLE")
for p in sorted(n for n in names if n.startswith("data/minecraft/tags/item/enchantable/") or n.startswith("data/elsewhere/tags/item/enchantable")):
    d = js(p) or {}
    vals = d.get("values", [])
    if any("herding_crook" in str(v) for v in vals): print("crook in tag:", p)
for p in sorted(n for n in names if "tags/enchantment" in n or "enchantment" in n and n.endswith(".json") and "crook" in n):
    print("enchantment data:", p)
for p in sorted(n for n in names if "worldgen" in n and any(b in n for b in ["grunthar", "solaxis", "mournmaw", "ossyra", "aphelion"])):
    d = js(p); print("worldgen:", p, json.dumps(d)[:200])
for p in sorted(n for n in names if "biome_modifier" in n or "fabric/biome" in n): print("biome mod data:", p)
cls = [n for n in names if n.endswith("NetherEndBossesContent.class")]
for c in cls:
    b = jar.read(c); print("NetherEndBossesContent strings:", sorted(set(re.findall(rb"[a-z_]+_(?:hall|cradle|basin|spire|halo)", b))), "WORLDGEN const bytes present:", b"WORLDGEN" in b)
