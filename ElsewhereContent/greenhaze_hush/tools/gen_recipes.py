"""Building-block recipes cloned from vanilla families (jungle wood -> Kushwood, deepslate -> Hushstone, mud bricks -> Ossuary Bone).
Run: python3 tools/gen_recipes.py"""
import json, os
HERE = os.path.dirname(os.path.abspath(__file__))
R = os.path.join(HERE, "..", "src", "main", "resources", "data", "elsewhere", "recipe")
TAGS = os.path.join(HERE, "..", "src", "main", "resources", "data", "elsewhere", "tags", "item")
V = os.environ.get("MCRECIPES", "/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad/mcfull/data/minecraft/recipe")
os.makedirs(R, exist_ok=True)
os.makedirs(TAGS, exist_ok=True)
n = 0

def clone(vname, newname, repl, allowed_vanilla=()):
    global n
    s = open(os.path.join(V, vname + ".json")).read()
    for a, b in repl: s = s.replace(a, b)
    # skip if any vanilla block id other than allowed remains
    import re
    left = [m for m in re.findall(r'"#?minecraft:[a-z_]+"', s) if m.strip('"').lstrip('#') not in allowed_vanilla
            and not m.startswith('"minecraft:crafting') and not m.startswith('"minecraft:stonecutting')]
    if left: return
    open(os.path.join(R, newname + ".json"), "w").write(s)
    n += 1

KUSH = [("#minecraft:jungle_logs", "#elsewhere:kushwood_logs"), ("minecraft:stripped_jungle_", "elsewhere:stripped_kushwood_"), ("minecraft:jungle_", "elsewhere:kushwood_")]
for v in ["jungle_planks", "jungle_stairs", "jungle_slab", "jungle_fence", "jungle_fence_gate", "jungle_door", "jungle_trapdoor", "jungle_button",
          "jungle_pressure_plate", "jungle_wood"]:
    clone(v, v.replace("jungle", "kushwood"), KUSH, ("minecraft:stick",))
json.dump({"replace": False, "values": ["elsewhere:kushwood_log", "elsewhere:kushwood_wood", "elsewhere:stripped_kushwood_log", "elsewhere:stripped_kushwood_wood"]},
          open(os.path.join(TAGS, "kushwood_logs.json"), "w"), indent=2)

HUSH = [("minecraft:cobbled_deepslate", "elsewhere:hushstone"), ("minecraft:polished_deepslate", "elsewhere:polished_hushstone"),
        ("minecraft:deepslate_bricks", "elsewhere:hushstone_bricks"), ("minecraft:deepslate_brick_", "elsewhere:hushstone_brick_")]
for f in sorted(os.listdir(V)):
    name = f[:-5]
    if not (name.startswith("cobbled_deepslate_") or name.startswith("deepslate_brick") or name.startswith("polished_deepslate")): continue
    new = name.replace("cobbled_deepslate", "hushstone").replace("polished_deepslate", "polished_hushstone").replace("deepslate_bricks", "hushstone_bricks").replace("deepslate_brick", "hushstone_brick")
    clone(name, new, HUSH)

BONE = [("minecraft:mud_bricks", "elsewhere:ossuary_bone_bricks"), ("minecraft:mud_brick_", "elsewhere:ossuary_bone_brick_")]
for v in ["mud_brick_slab", "mud_brick_stairs", "mud_brick_wall", "mud_brick_slab_from_mud_bricks_stonecutting",
          "mud_brick_stairs_from_mud_bricks_stonecutting", "mud_brick_wall_from_mud_bricks_stonecutting"]:
    clone(v, v.replace("mud_bricks", "ossuary_bone_bricks").replace("mud_brick", "ossuary_bone_brick"), BONE)

def shaped(name, pattern, key, result, count=1, cat="building"):
    global n
    json.dump({"type": "minecraft:crafting_shaped", "category": cat, "key": key, "pattern": pattern,
               "result": {"count": count, "id": result}}, open(os.path.join(R, name + ".json"), "w"), indent=2)
    n += 1
shaped("ossuary_bone_bricks", ["##", "##"], {"#": "minecraft:bone_block"}, "elsewhere:ossuary_bone_bricks", 4)
shaped("trichome_quartz_block", ["##", "##"], {"#": "elsewhere:trichome_quartz"}, "elsewhere:trichome_quartz_block")
shaped("echo_crystal_block", ["##", "##"], {"#": "elsewhere:echo_crystal"}, "elsewhere:echo_crystal_block")
shaped("haze_resin_block", ["##", "##"], {"#": "elsewhere:haze_resin"}, "elsewhere:haze_resin_block")
shaped("hushweave_carpet", ["##"], {"#": "elsewhere:hushweave"}, "elsewhere:hushweave_carpet", 3, "misc")
# drop clones whose result we don't register (e.g. polished_hushstone slabs)
import re as _re, glob as _glob
_src = "".join(open(f).read() for f in _glob.glob(os.path.join(HERE, "..", "src", "main", "java", "**", "*.java"), recursive=True))
_ids = set(_re.findall(r'Reg\.(?:block|item)\("([a-z_]+)"', _src))
for f in _glob.glob(os.path.join(R, "*.json")):
    d = json.load(open(f))
    if "fabric:load_conditions" in d: continue
    if any(m not in _ids for m in _re.findall(r'"elsewhere:([a-z_]+)"', json.dumps(d))): os.remove(f); n -= 1
def shapeless(name, ingredients, result, count=1, cat="building"):
    global n
    json.dump({"type": "minecraft:crafting_shapeless", "category": cat, "ingredients": ingredients,
               "result": {"count": count, "id": result}}, open(os.path.join(R, name + ".json"), "w"), indent=2)
    n += 1
# QUESTIONS.md answers (design agent)
shapeless("hushstone", ["minecraft:cobbled_deepslate"] * 4 + ["elsewhere:hush_membrane"], "elsewhere:hushstone", 4)
shapeless("hushweave", ["minecraft:black_wool", "elsewhere:hush_membrane"], "elsewhere:hushweave", 1)
print("recipes:", n)
