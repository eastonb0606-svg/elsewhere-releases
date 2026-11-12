#!/usr/bin/env python3
"""Static asset check of a built Elsewhere jar.

usage: assetcheck.py <jar> [registry.json]
For every item: assets/elsewhere/items/<id>.json exists, every model it names exists, every texture those models
(and their elsewhere parents) name exists. For every block: blockstates/<id>.json exists, same model/texture chain.
Every item/block/entity has an en_us name. Entity textures: every textures/entity png referenced in class files is
not checked (see the client screenshots). Prints one line per problem and a summary.
"""
import json
import re
import sys
import zipfile

jar = zipfile.ZipFile(sys.argv[1])
names = set(jar.namelist())
reg = json.load(open(sys.argv[2])) if len(sys.argv) > 2 else None
NS = "elsewhere"
problems = []


def load(path):
    try:
        return json.loads(jar.read(path))
    except KeyError:
        return None
    except Exception as e:  # noqa
        problems.append(f"BADJSON {path}: {e}")
        return None


def split(ref, default_ns="minecraft"):
    if isinstance(ref, dict):
        ref = ref.get("sprite") or ref.get("id") or ""
    if ":" in ref:
        ns, p = ref.split(":", 1)
    else:
        ns, p = default_ns, ref
    return ns, p


checked_models = {}


def check_model(ref, ctx, textures_seen=None):
    ns, p = split(ref)
    if ns != NS:
        return True
    key = f"{ns}:{p}"
    if key in checked_models:
        return checked_models[key]
    path = f"assets/{ns}/models/{p}.json"
    m = load(path)
    if m is None:
        problems.append(f"MISSING-MODEL {key} (used by {ctx})")
        checked_models[key] = False
        return False
    ok = True
    for tk, tv in (m.get("textures") or {}).items():
        if isinstance(tv, str) and tv.startswith("#"):
            continue
        tns, tp = split(tv)
        if tns == NS and f"assets/{tns}/textures/{tp}.png" not in names:
            problems.append(f"MISSING-TEXTURE {tns}:{tp} (model {key}, slot {tk})")
            ok = False
    parent = m.get("parent")
    if parent:
        ok &= check_model(parent, key)
    checked_models[key] = ok
    return ok


def models_in(obj, out):
    if isinstance(obj, dict):
        for k, v in obj.items():
            if k == "model" and isinstance(v, str):
                out.append(v)
            elif k == "model" and isinstance(v, dict) and isinstance(v.get("model"), str) and v.get("type") in (None, "minecraft:model"):
                out.append(v["model"])
                models_in(v, out)
            else:
                models_in(v, out)
    elif isinstance(obj, list):
        for v in obj:
            models_in(v, out)


lang = load(f"assets/{NS}/lang/en_us.json") or {}
items = reg["items"] if reg else sorted({k.split(".", 2)[2] for k in lang if k.startswith("item.elsewhere.") and k.count(".") == 2}
                                         | {n[len("assets/elsewhere/items/"):-5] for n in names if n.startswith("assets/elsewhere/items/")})
blocks = reg["blocks"] if reg else sorted({n[len("assets/elsewhere/blockstates/"):-5] for n in names if n.startswith("assets/elsewhere/blockstates/")})
ents = reg["entities"] if reg else []
items = [i.split(":")[-1] for i in items]
blocks = [b.split(":")[-1] for b in blocks]
ents = [e.split(":")[-1] for e in ents]
block_set = set(blocks)

for it in items:
    d = load(f"assets/{NS}/items/{it}.json")
    if d is None:
        problems.append(f"MISSING-ITEMDEF items/{it}.json")
        continue
    refs = []
    models_in(d.get("model", d), refs)
    for r in refs:
        check_model(r, f"item {it}")
    if f"item.{NS}.{it}" not in lang and f"block.{NS}.{it}" not in lang:
        problems.append(f"MISSING-LANG item.{NS}.{it}")

for b in blocks:
    d = load(f"assets/{NS}/blockstates/{b}.json")
    if d is None:
        problems.append(f"MISSING-BLOCKSTATE blockstates/{b}.json")
        continue
    refs = []
    models_in(d, refs)
    for r in refs:
        check_model(r, f"block {b}")

for e in ents:
    if f"entity.{NS}.{e}" not in lang:
        problems.append(f"MISSING-LANG entity.{NS}.{e}")

# textures referenced from java classes (entity renderers): look for "elsewhere" + "textures/entity/..." strings
cls_tex = set()
for n in names:
    if n.endswith(".class"):
        data = jar.read(n)
        for m in re.finditer(rb"textures/(entity|models|gui|environment|misc|particle)/[a-z0-9_/.\-]+\.png", data):
            cls_tex.add(m.group(0).decode())
for t in sorted(cls_tex):
    if f"assets/{NS}/{t}" not in names and f"assets/minecraft/{t}" not in names:
        # vanilla textures are fine (they live in the game jar); flag only paths that look like elsewhere's own
        problems.append(f"CLASS-TEXTURE? {t} (string in a class file, not in assets/elsewhere/ - vanilla path or missing)")

kinds = {}
for p in problems:
    kinds.setdefault(p.split()[0], []).append(p)
for k, v in sorted(kinds.items()):
    print(f"== {k}: {len(v)}")
    for p in v:
        print("  " + p)
print(f"SUMMARY items={len(items)} blocks={len(blocks)} entities={len(ents)} problems={len(problems)}")
