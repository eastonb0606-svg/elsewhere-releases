"""Generates every client asset of the module (blockstates, models, item definitions, textures, lang, equipment) and the
block/entity loot tables and tags. Textures are recoloured from vanilla 26.3 textures (S/mcfull) so every model has a
matching texture. Run with the pillow venv: S/venv/bin/python tools/gen_assets.py"""
import json, os, re, copy, random
from PIL import Image
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")
A = os.path.join(RES, "assets", "elsewhere")
D = os.path.join(RES, "data")
V = os.environ.get("MCASSETS", "/private/tmp/claude-501/-Users-admin-Library-Application-Support-Claude-scratch-workspaces-f751a947-8d76-4adb-ba1a-6459a9c78498-076bc1c4-4795-405e-86c1-3b969a057515-scratch-2026-11-07-afb2b1/f9df1f0f-c65a-4205-bfa5-7632c67e599e/scratchpad/mcfull/assets/minecraft")
LANG = {}
DROPS_SELF = []

def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    json.dump(obj, open(path, "w"), indent=2)

def hexrgb(h): return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))

def tint(src, color, contrast=1.15, out=None, glow=0.0):
    im = Image.open(src).convert("RGBA")
    r0, g0, b0 = hexrgb(color)
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            l = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            l = max(0.0, min(1.0, (l - 0.5) * contrast + 0.5 + glow))
            px[x, y] = (min(255, int(r0 * l * 1.35)), min(255, int(g0 * l * 1.35)), min(255, int(b0 * l * 1.35)), a)
    if out:
        os.makedirs(os.path.dirname(out), exist_ok=True)
        im.save(out)
    return im

def vtex(kind, name): return os.path.join(V, "textures", kind, name + ".png")

# ---------------------------------------------------------------------------------------------- block families cloned from vanilla
def clone_block(vname, myname, color, texmap=None, lang=None, item=True):
    """Copy a vanilla block's blockstate + models + item definition, renaming vanilla prefix -> my prefix; recolour textures."""
    texmap = texmap or {}
    bs = json.load(open(os.path.join(V, "blockstates", vname + ".json")))
    s = json.dumps(bs)
    models = set(re.findall(r'"minecraft:block/([a-z0-9_/]+)"', s))
    def ren(t):
        for a, b in sorted(texmap.items(), key=lambda kv: -len(kv[0])):
            if a in t: return t.replace(a, b)
        return t
    for m in models:
        mj = json.load(open(os.path.join(V, "models", "block", m + ".json")))
        ms = json.dumps(mj)
        for tex in set(re.findall(r'"minecraft:block/([a-z0-9_/]+)"', ms)):
            if os.path.exists(vtex("block", tex)) and any(k in tex for k in texmap):
                newt = ren(tex)
                ms = ms.replace(f'"minecraft:block/{tex}"', f'"elsewhere:block/{newt}"')
                tint(vtex("block", tex), color, out=os.path.join(A, "textures", "block", newt + ".png"))
        mj = json.loads(ms)
        w(os.path.join(A, "models", "block", ren(m) + ".json"), mj)
        s = s.replace(f'"minecraft:block/{m}"', f'"elsewhere:block/{ren(m)}"')
    w(os.path.join(A, "blockstates", myname + ".json"), json.loads(s))
    if item:
        ipath = os.path.join(V, "items", vname + ".json")
        it = json.load(open(ipath))
        its = json.dumps(it)
        for ref in set(re.findall(r'"minecraft:(block|item)/([a-z0-9_/]+)"', its)):
            kind, nm = ref
            new = ren(nm)
            if kind == "item":
                mj = json.load(open(os.path.join(V, "models", "item", nm + ".json")))
                ms = json.dumps(mj)
                for tex in set(re.findall(r'"minecraft:item/([a-z0-9_/]+)"', ms)):
                    if not os.path.exists(vtex("item", tex)): continue
                    tint(vtex("item", tex), color, out=os.path.join(A, "textures", "item", ren(tex) + ".png"))
                    ms = ms.replace(f'"minecraft:item/{tex}"', f'"elsewhere:item/{ren(tex)}"')
                w(os.path.join(A, "models", "item", new + ".json"), json.loads(ms))
            elif not os.path.exists(os.path.join(A, "models", "block", new + ".json")):
                mj = json.load(open(os.path.join(V, "models", "block", nm + ".json")))
                ms = json.dumps(mj)
                for tex in set(re.findall(r'"minecraft:block/([a-z0-9_/]+)"', ms)):
                    if any(k in tex for k in texmap):
                        ms = ms.replace(f'"minecraft:block/{tex}"', f'"elsewhere:block/{ren(tex)}"')
                        tint(vtex("block", tex), color, out=os.path.join(A, "textures", "block", ren(tex) + ".png"))
                w(os.path.join(A, "models", "block", new + ".json"), json.loads(ms))
            its = its.replace(f'"minecraft:{kind}/{nm}"', f'"elsewhere:{kind}/{new}"')
        w(os.path.join(A, "items", myname + ".json"), json.loads(its))
    LANG["block.elsewhere." + myname] = lang or myname.replace("_", " ").title()

def simple_block(myname, vtexname, color, lang=None, item=True, glow=0.0, model_parent="minecraft:block/cube_all"):
    tint(vtex("block", vtexname), color, out=os.path.join(A, "textures", "block", myname + ".png"), glow=glow)
    w(os.path.join(A, "models", "block", myname + ".json"), {"parent": model_parent, "textures": {"all": "elsewhere:block/" + myname}})
    w(os.path.join(A, "blockstates", myname + ".json"), {"variants": {"": {"model": "elsewhere:block/" + myname}}})
    if item: w(os.path.join(A, "items", myname + ".json"), {"model": {"type": "minecraft:model", "model": "elsewhere:block/" + myname}})
    LANG["block.elsewhere." + myname] = lang or myname.replace("_", " ").title()

def states_block(myname, prop_variants, lang=None, item_model=None):
    """prop_variants: {"open=true": ("name_suffix", vtex, color, glow)}"""
    variants = {}
    for key, (suffix, vt, col, glow) in prop_variants.items():
        mn = myname + suffix
        tint(vtex("block", vt), col, out=os.path.join(A, "textures", "block", mn + ".png"), glow=glow)
        w(os.path.join(A, "models", "block", mn + ".json"), {"parent": "minecraft:block/cube_all", "textures": {"all": "elsewhere:block/" + mn}})
        variants[key] = {"model": "elsewhere:block/" + mn}
    w(os.path.join(A, "blockstates", myname + ".json"), {"variants": variants})
    first = item_model or ("elsewhere:block/" + myname + list(prop_variants.values())[0][0])
    w(os.path.join(A, "items", myname + ".json"), {"model": {"type": "minecraft:model", "model": first}})
    LANG["block.elsewhere." + myname] = lang or myname.replace("_", " ").title()

def flat_item(myname, vname, color, lang=None, kind="item", glow=0.0, handheld=False):
    tint(vtex(kind, vname), color, out=os.path.join(A, "textures", "item", myname + ".png"), glow=glow)
    w(os.path.join(A, "models", "item", myname + ".json"), {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated",
        "textures": {"layer0": "elsewhere:item/" + myname}})
    w(os.path.join(A, "items", myname + ".json"), {"model": {"type": "minecraft:model", "model": "elsewhere:item/" + myname}})
    LANG["item.elsewhere." + myname] = lang or myname.replace("_", " ").title()

# ============================================================================================== GREENHAZE blocks
KUSH = "6fb85a"
kushmap = {"jungle": "kushwood"}
for v in ["jungle_log", "stripped_jungle_log", "jungle_wood", "stripped_jungle_wood", "jungle_planks", "jungle_stairs", "jungle_slab",
          "jungle_fence", "jungle_fence_gate", "jungle_door", "jungle_trapdoor", "jungle_button", "jungle_pressure_plate", "jungle_leaves"]:
    clone_block(v, v.replace("jungle", "kushwood"), KUSH if "leaves" not in v else "86d470", kushmap)
# leaves models use tintindex: drop it by retexturing with a pre-coloured texture (tint provider absent -> untinted)
simple_block("haze_vent", "mud", "8a5fb0", glow=0.05)
simple_block("trichome_quartz_ore", "emerald_ore", "e8f4ea", lang="Trichome Quartz Ore")
simple_block("trichome_quartz_block", "quartz_block_side", "e9f7e2", lang="Block of Trichome Quartz")
simple_block("haze_resin_block", "honeycomb_block", "c8a23a", lang="Haze Resin Block")
simple_block("hotbox_altar", "chiseled_tuff_bricks", "8ec26a", glow=0.1)
simple_block("snack_altar", "barrel_top", "d8a060")
states_block("hotbox_vent", {"open=true": ("_open", "bamboo_mosaic", "a0c070", 0.0), "open=false": ("_closed", "bamboo_block", "6a7a50", -0.1)})
simple_block("vent_lever", "bamboo_planks", "c0d080")
# giant fern: 4 cross parts
for i, (vt, col) in enumerate([("large_fern_bottom", "3e9b3a"), ("large_fern_bottom", "4caf44"), ("large_fern_top", "56b84a"), ("large_fern_top", "8fd659")]):
    tint(vtex("block", vt), col, out=os.path.join(A, "textures", "block", f"giant_fern_{i}.png"))
    w(os.path.join(A, "models", "block", f"giant_fern_{i}.json"), {"parent": "minecraft:block/cross", "render_type": "cutout",
        "textures": {"cross": f"elsewhere:block/giant_fern_{i}"}})
w(os.path.join(A, "blockstates", "giant_fern.json"), {"variants": {f"part={i}": {"model": f"elsewhere:block/giant_fern_{i}"} for i in range(4)}})
flat_item("giant_fern", "large_fern_top", "6cc24a", kind="block")
LANG["block.elsewhere.giant_fern"] = "Giant Fern"
# lucid lily
tint(vtex("block", "lily_pad"), "5ff0e8", out=os.path.join(A, "textures", "block", "lucid_lily.png"), glow=0.15)
lp = json.load(open(os.path.join(V, "models", "block", "lily_pad.json")))
ls = json.dumps(lp).replace('"minecraft:block/lily_pad"', '"elsewhere:block/lucid_lily"').replace(', "tintindex": 0', '').replace('"tintindex": 0, ', '')
w(os.path.join(A, "models", "block", "lucid_lily.json"), json.loads(ls))
w(os.path.join(A, "blockstates", "lucid_lily.json"), {"variants": {"": {"model": "elsewhere:block/lucid_lily"}}})
flat_item("lucid_lily", "lily_pad", "5ff0e8", kind="block", glow=0.15)
LANG["block.elsewhere.lucid_lily"] = "Lucid Lily"
# munchfruit bush (4 ages)
for i in range(4):
    tint(vtex("block", f"sweet_berry_bush_stage{i}"), "7cc24a" if i < 2 else "e0a040", contrast=1.0,
         out=os.path.join(A, "textures", "block", f"munchfruit_bush_stage{i}.png"))
    w(os.path.join(A, "models", "block", f"munchfruit_bush_stage{i}.json"), {"parent": "minecraft:block/cross", "render_type": "cutout",
        "textures": {"cross": f"elsewhere:block/munchfruit_bush_stage{i}"}})
w(os.path.join(A, "blockstates", "munchfruit_bush.json"), {"variants": {f"age={i}": {"model": f"elsewhere:block/munchfruit_bush_stage{i}"} for i in range(4)}})
LANG["block.elsewhere.munchfruit_bush"] = "Munchfruit Bush"

# ============================================================================================== HUSH blocks
HS = "3a5560"
clone_block("cobbled_deepslate", "hushstone", HS, {"cobbled_deepslate": "hushstone"})
clone_block("cobbled_deepslate_stairs", "hushstone_stairs", HS, {"cobbled_deepslate": "hushstone"})
clone_block("cobbled_deepslate_slab", "hushstone_slab", HS, {"cobbled_deepslate": "hushstone"})
clone_block("cobbled_deepslate_wall", "hushstone_wall", HS, {"cobbled_deepslate": "hushstone"})
clone_block("polished_deepslate", "polished_hushstone", HS, {"polished_deepslate": "polished_hushstone"})
clone_block("deepslate_bricks", "hushstone_bricks", HS, {"deepslate_bricks": "hushstone_bricks"})
clone_block("deepslate_brick_stairs", "hushstone_brick_stairs", HS, {"deepslate_brick": "hushstone_brick", "deepslate_bricks": "hushstone_bricks"})
clone_block("deepslate_brick_slab", "hushstone_brick_slab", HS, {"deepslate_brick": "hushstone_brick", "deepslate_bricks": "hushstone_bricks"})
clone_block("deepslate_brick_wall", "hushstone_brick_wall", HS, {"deepslate_brick": "hushstone_brick", "deepslate_bricks": "hushstone_bricks"})
BONE = "e6dcc0"
clone_block("mud_bricks", "ossuary_bone_bricks", BONE, {"mud_bricks": "ossuary_bone_bricks"})
clone_block("mud_brick_stairs", "ossuary_bone_brick_stairs", BONE, {"mud_brick": "ossuary_bone_brick", "mud_bricks": "ossuary_bone_bricks"})
clone_block("mud_brick_slab", "ossuary_bone_brick_slab", BONE, {"mud_brick": "ossuary_bone_brick", "mud_bricks": "ossuary_bone_bricks"})
clone_block("mud_brick_wall", "ossuary_bone_brick_wall", BONE, {"mud_brick": "ossuary_bone_brick", "mud_bricks": "ossuary_bone_bricks"})
simple_block("hushweave", "white_wool", "1b5a62")
tint(vtex("block", "white_wool"), "1b5a62", out=os.path.join(A, "textures", "block", "hushweave_carpet.png"))
w(os.path.join(A, "models", "block", "hushweave_carpet.json"), {"parent": "minecraft:block/carpet", "textures": {"wool": "elsewhere:block/hushweave_carpet"}})
w(os.path.join(A, "blockstates", "hushweave_carpet.json"), {"variants": {"": {"model": "elsewhere:block/hushweave_carpet"}}})
w(os.path.join(A, "items", "hushweave_carpet.json"), {"model": {"type": "minecraft:model", "model": "elsewhere:block/hushweave_carpet"}})
LANG["block.elsewhere.hushweave_carpet"] = "Hushweave Carpet"
simple_block("echo_crystal_ore", "deepslate_diamond_ore", "3ae8ff")
simple_block("echo_crystal_block", "diamond_block", "2ac8d8", lang="Block of Echo Crystal")
states_block("sculk_lantern", {f"pulse={i}": (f"_{i}", "sculk_catalyst_top", "3ae8ff", -0.2 + 0.12 * i) for i in range(4)})
states_block("tympanum_membrane", {"exposed=false": ("", "sculk", "1e5560", 0.0), "exposed=true": ("_exposed", "sculk", "6ff8ff", 0.25)})
cp = {}
for n in range(8):
    cp[f"lit=false,note={n}"] = ("", "amethyst_block", "9b6ad8", 0.0)
    cp[f"lit=true,note={n}"] = ("_lit", "amethyst_block", "f0d0ff", 0.3)
states_block("choir_pillar", cp)
# stub blocks
seal = Image.new("RGBA", (16, 16))
for yy in range(16):
    for xx in range(16): seal.putpixel((xx, yy), (90, 235, 255, 110 if xx in (0, 15) or yy in (0, 15) else 45))
seal.save(os.path.join(A, "textures", "block", "arena_seal.png"))
w(os.path.join(A, "models", "block", "arena_seal.json"), {"parent": "minecraft:block/cube_all", "render_type": "translucent", "textures": {"all": "elsewhere:block/arena_seal"}})
w(os.path.join(A, "blockstates", "arena_seal.json"), {"variants": {"": {"model": "elsewhere:block/arena_seal"}}})
LANG["block.elsewhere.arena_seal"] = "Arena Seal"
tint(vtex("block", "nether_portal"), "c070ff", out=os.path.join(A, "textures", "block", "return_rift.png"), glow=0.1)
w(os.path.join(A, "models", "block", "return_rift.json"), {"parent": "minecraft:block/cube_all", "render_type": "translucent", "textures": {"all": "elsewhere:block/return_rift"}})
w(os.path.join(A, "blockstates", "return_rift.json"), {"variants": {"": {"model": "elsewhere:block/return_rift"}}})
LANG["block.elsewhere.return_rift"] = "Return Rift"

# ============================================================================================== items
ITEMS = [
    # greenhaze
    ("haze_resin", "honeycomb", "b8b040"), ("hazeborn_bud", "sweet_berries", "a07ad8"), ("lucid_water", "potion", "7ff0f0"),
    ("munchie_bacon", "cooked_porkchop", "d07a4a"), ("munchfruit", "sweet_berries", "f0a040"), ("hotbox_lantern", "lantern", "8ec26a"),
    ("trichome_quartz", "quartz", "eaf8e8"), ("toad_slime", "slime_ball", "a070d0"), ("shade_wisp", "ghast_tear", "403850"),
    ("smoke_ring", "snowball", "c8c0d0"), ("ember_flick", "fire_charge", "ff9030"), ("spit_seed", "melon_seeds", "8a6a30"),
    ("everflow_bong", "glass_bottle", "70e070"), ("cloud_jar", "glass_bottle", "d0d0e0"), ("endless_snack_pouch", "bundle", "c09050"),
    ("grumblegut_tusk", "bone", "f0e8c8"), ("music_disc_four_twenty", "music_disc_cat", "7cc24a"),
    # hush
    ("echo_crystal", "echo_shard", "3ae8ff"), ("resonant_shard", "amethyst_shard", "40c8c8"), ("hush_membrane", "phantom_membrane", "2a6a70"),
    ("crystal_shard", "amethyst_shard", "e0b0ff"), ("sonar_horn", "goat_horn", "3a8a90"), ("sculk_censer", "soul_lantern", "2ad0d8"),
    ("silence_draught", "potion", "0d3b3e"), ("tuning_fork_of_auris", "echo_shard", "9ff8ff"), ("cantors_bell_shard", "amethyst_shard", "b070ff"),
    ("resonant_tuning", "amethyst_shard", "60f0e0"), ("music_disc_hush", "music_disc_cat", "1b5a62"),
]
for name, v, col in ITEMS: flat_item(name, v, col)
TOOLS = [("trichome", "e8f4e0"), ("echo", "2ac8d8")]
for mat, col in TOOLS:
    for t in ["sword", "pickaxe", "axe", "shovel", "hoe"]: flat_item(f"{mat}_{t}", f"diamond_{t}", col, handheld=True)
ARMOR = [("frond_hood", "diamond_helmet", "5aa040"), ("frond_cloak", "diamond_chestplate", "5aa040"), ("frond_leggings", "diamond_leggings", "5aa040"),
         ("frond_sandals", "diamond_boots", "5aa040"), ("hotbox_visor", "diamond_helmet", "a0e090"),
         ("hushweave_hood", "leather_helmet", "1b4a52"), ("hushcloak", "leather_chestplate", "1b2a30"), ("hushstep_greaves", "netherite_leggings", "1b5a62"),
         ("hushweave_boots", "leather_boots", "1b4a52"), ("choir_vestment", "netherite_chestplate", "9b6ad8"),
         ("phasewalker_greaves", "netherite_leggings", "8a50c0")]
for name, v, col in ARMOR: flat_item(name, v, col)
flat_item("couchlock_club", "wooden_sword", "6a9a40", handheld=True)
flat_item("challengers_cleaver", "iron_sword", "f0c040", handheld=True)
flat_item("echo_forged_blade", "netherite_sword", "2ad0d8", handheld=True)
EGGS = {"lazy_gorger": "7cc24a", "munchie_boar": "c08040", "toke_toad": "a070d0", "hotbox_smog": "c8c0d0", "paranoia_shade": "302838",
        "bud_bomb": "8ec26a", "budtender": "6aa050", "echo_stalker": "1b2a30", "shrieker_swarm": "2a6a70", "ossuary_chorister": "e6dcc0",
        "catalyst_mimic": "2ad0d8", "mute_archivist": "202020"}
for n, col in EGGS.items(): flat_item(n + "_spawn_egg", "zombie_spawn_egg" if os.path.exists(vtex("item", "zombie_spawn_egg")) else "egg", col)

# equipment
for asset, base, col in [("frond", "diamond", "5aa040"), ("hotbox_visor", "diamond", "a0e090"), ("hushweave", "leather", "1b4a52"),
                         ("hushstep", "netherite", "1b5a62"), ("choir_vestment", "netherite", "9b6ad8"), ("phasewalker", "netherite", "8a50c0")]:
    for layer in ["humanoid", "humanoid_leggings"]:
        tint(os.path.join(V, "textures", "entity", "equipment", layer, base + ".png"), col,
             out=os.path.join(A, "textures", "entity", "equipment", layer, asset + ".png"))
    w(os.path.join(A, "equipment", asset + ".json"), {"layers": {"humanoid": [{"texture": "elsewhere:" + asset}],
        "humanoid_leggings": [{"texture": "elsewhere:" + asset}]}})

# entity textures (vanilla rigs, recoloured)
ENT = {"lazy_gorger": ("panda/panda", "7cc24a"), "munchie_boar": ("hoglin/hoglin", "c08a40"), "toke_toad": ("frog/frog_temperate", "a070d0"),
       "hotbox_smog": ("slime/slime", "c8c0d8"), "paranoia_shade": ("illager/vex", "3a3048"), "bud_bomb": ("creeper/creeper", "8ec26a"),
       "budtender": ("wandering_trader/wandering_trader", "6aa050"), "percolax": ("iron_golem/iron_golem", "9fe8a0"),
       "grumblegut": ("hoglin/hoglin", "5f8a3a"), "echo_stalker": ("enderman/enderman", "1b3a40"), "shrieker_swarm": ("bat/bat", "2a6a70"),
       "ossuary_chorister": ("skeleton/skeleton", "e6dcc0"), "catalyst_mimic": ("shulker/shulker", "1b5a62"),
       "mute_archivist": ("wandering_trader/wandering_trader", "2a2a30"), "auris": ("warden/warden", "1b6a78"), "lithara": ("illager/evoker", "a070e8")}
for n, (src, col) in ENT.items():
    tint(os.path.join(V, "textures", "entity", src + ".png"), col, out=os.path.join(A, "textures", "entity", n + ".png"))

# ============================================================================================== lang
NAMES = {
    "entity.elsewhere.lazy_gorger": "Lazy Gorger", "entity.elsewhere.munchie_boar": "Munchie Boar", "entity.elsewhere.toke_toad": "Toke Toad",
    "entity.elsewhere.hotbox_smog": "Hotbox Smog", "entity.elsewhere.paranoia_shade": "Paranoia Shade", "entity.elsewhere.bud_bomb": "Bud Bomb",
    "entity.elsewhere.budtender": "Budtender", "entity.elsewhere.percolax": "Percolax, the Hotbox Colossus",
    "entity.elsewhere.grumblegut": "Grumblegut, the Endless Munchies", "entity.elsewhere.smoke_ring": "Smoke Ring",
    "entity.elsewhere.ember_flick": "Ember Flick", "entity.elsewhere.spit_seed": "Spit Seed", "entity.elsewhere.cloud_jar": "Cloud Jar",
    "entity.elsewhere.echo_stalker": "Echo Stalker", "entity.elsewhere.shrieker_swarm": "Shrieker Swarm",
    "entity.elsewhere.ossuary_chorister": "Ossuary Chorister", "entity.elsewhere.catalyst_mimic": "Catalyst Mimic",
    "entity.elsewhere.mute_archivist": "Mute Archivist", "entity.elsewhere.auris": "Auris, the Listening Engine",
    "entity.elsewhere.lithara": "Lithara, the Geode Cantor", "entity.elsewhere.crystal_shard": "Crystal Shard",
    "entity.elsewhere.tuning_fork_of_auris": "Tuning Fork of Auris",
    "itemGroup.elsewhere.greenhaze": "Elsewhere: The Greenhaze", "itemGroup.elsewhere.the_hush": "Elsewhere: The Hush",
    "effect.elsewhere.muted": "Muted", "jukebox_song.elsewhere.four_twenty": "Elsewhere - Four Twenty", "jukebox_song.elsewhere.hush": "Elsewhere - Hush",
    "item.elsewhere.tuning_fork_of_auris": "Tuning Fork of Auris", "item.elsewhere.cantors_bell_shard": "Cantor's Bell Shard",
    "item.elsewhere.challengers_cleaver": "Challenger's Cleaver", "item.elsewhere.echo_forged_blade": "Echo-Forged Blade",
    "item.elsewhere.music_disc_four_twenty": "Music Disc", "item.elsewhere.music_disc_hush": "Music Disc",
    "item.elsewhere.hushcloak": "Hushcloak", "item.elsewhere.endless_snack_pouch": "Endless Snack Pouch",
    "biome.elsewhere.kush_canopy": "Kush Canopy", "biome.elsewhere.sativa_highlands": "Sativa Highlands", "biome.elsewhere.indica_hollows": "Indica Hollows",
    "biome.elsewhere.hazebottom_swamp": "Hazebottom Swamp", "biome.elsewhere.munchie_groves": "Munchie Groves", "biome.elsewhere.the_murmur": "The Murmur",
    "biome.elsewhere.echo_shafts": "Echo Shafts", "biome.elsewhere.ossuary_galleries": "Ossuary Galleries",
    "biome.elsewhere.amethyst_choir": "Amethyst Choir", "biome.elsewhere.drowned_silence": "Drowned Silence",
}
for n in EGGS: NAMES[f"item.elsewhere.{n}_spawn_egg"] = NAMES["entity.elsewhere." + n].split(",")[0] + " Spawn Egg"
NAMES.update({"tag.item.elsewhere.trichome_tool_materials": "Trichome Tool Materials", "tag.item.elsewhere.repairs_frond_armor": "Repairs Frond Armor",
    "tag.item.elsewhere.echo_tool_materials": "Echo Tool Materials", "tag.item.elsewhere.kushwood_logs": "Kushwood Logs", "tag.item.elsewhere.repairs_hushweave_armor": "Repairs Hushweave Armor"})
LANG.update(NAMES)
w(os.path.join(A, "lang", "en_us.json"), dict(sorted(LANG.items())))

# ============================================================================================== loot tables and tags
def self_drop(name):
    w(os.path.join(D, "elsewhere", "loot_table", "blocks", name + ".json"), {"type": "minecraft:block", "pools": [{"rolls": 1,
        "entries": [{"type": "minecraft:item", "name": "elsewhere:" + name}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
def drop(name, item, mn, mx, chance=1.0):
    pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item, "functions": [
        {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": mn, "max": mx}}]}]}
    if chance < 1: pool["conditions"] = [{"condition": "minecraft:random_chance", "chance": chance}]
    return pool
def table(path, pools, typ="minecraft:block"):
    w(os.path.join(D, "elsewhere", "loot_table", path + ".json"), {"type": typ, "pools": pools})

SELF = ["kushwood_log", "stripped_kushwood_log", "kushwood_wood", "stripped_kushwood_wood", "kushwood_planks", "kushwood_stairs", "kushwood_slab",
        "kushwood_fence", "kushwood_fence_gate", "kushwood_trapdoor", "kushwood_button", "kushwood_pressure_plate", "haze_vent",
        "trichome_quartz_block", "haze_resin_block", "lucid_lily", "hushstone", "hushstone_stairs", "hushstone_slab", "hushstone_wall",
        "polished_hushstone", "hushstone_bricks", "hushstone_brick_stairs", "hushstone_brick_slab", "hushstone_brick_wall", "hushweave",
        "hushweave_carpet", "echo_crystal_block", "sculk_lantern", "ossuary_bone_bricks", "ossuary_bone_brick_stairs", "ossuary_bone_brick_slab",
        "ossuary_bone_brick_wall"]
for n in SELF: self_drop(n)
table("blocks/kushwood_door", [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": "elsewhere:kushwood_door"}],
    "conditions": [{"condition": "minecraft:block_state_property", "block": "elsewhere:kushwood_door", "properties": {"half": "lower"}}]}])
table("blocks/trichome_quartz_ore", [drop("trichome_quartz_ore", "elsewhere:trichome_quartz", 1, 2)])
table("blocks/echo_crystal_ore", [drop("echo_crystal_ore", "elsewhere:echo_crystal", 1, 2)])
table("blocks/giant_fern", [drop("x", "elsewhere:haze_resin", 1, 2, 0.5), drop("x", "elsewhere:hazeborn_bud", 1, 1, 0.12)])
table("blocks/kushwood_leaves", [drop("x", "minecraft:stick", 1, 2, 0.05)])
table("blocks/munchfruit_bush", [drop("x", "elsewhere:munchfruit", 1, 1)])
for n in ["hotbox_altar", "snack_altar", "hotbox_vent", "vent_lever"]: table("blocks/" + n, [])
ENTL = {"lazy_gorger": [drop("", "minecraft:bamboo", 1, 3), drop("", "elsewhere:haze_resin", 0, 2)],
        "munchie_boar": [drop("", "elsewhere:munchie_bacon", 1, 2), drop("", "minecraft:leather", 0, 1)],
        "toke_toad": [drop("", "elsewhere:toad_slime", 1, 2)], "hotbox_smog": [drop("", "elsewhere:haze_resin", 0, 1)],
        "paranoia_shade": [drop("", "elsewhere:shade_wisp", 1, 1, 0.6)], "bud_bomb": [drop("", "elsewhere:haze_resin", 0, 1)],
        "echo_stalker": [drop("", "elsewhere:resonant_shard", 1, 1, 0.3), drop("", "minecraft:sculk", 1, 2)],
        "shrieker_swarm": [drop("", "elsewhere:hush_membrane", 1, 1, 0.5)],
        "ossuary_chorister": [drop("", "minecraft:bone", 1, 3), drop("", "minecraft:candle", 0, 2), drop("", "elsewhere:silence_draught", 1, 1, 0.05)],
        "catalyst_mimic": [drop("", "elsewhere:resonant_shard", 1, 2), drop("", "minecraft:sculk", 1, 3)]}
for n, pools in ENTL.items(): table("entities/" + n, pools, "minecraft:entity")
table("chests/sesh_hut", [{"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
    {"type": "minecraft:item", "name": "elsewhere:haze_resin", "weight": 20, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 6}}]},
    {"type": "minecraft:item", "name": "elsewhere:munchie_bacon", "weight": 15}, {"type": "minecraft:item", "name": "elsewhere:lucid_water", "weight": 12},
    {"type": "minecraft:item", "name": "elsewhere:hazeborn_bud", "weight": 10}, {"type": "minecraft:item", "name": "elsewhere:frond_cloak", "weight": 3},
    {"type": "minecraft:item", "name": "elsewhere:trichome_quartz", "weight": 8}]}], "minecraft:chest")
table("chests/hanging_undercity", [{"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
    {"type": "minecraft:item", "name": "minecraft:echo_shard", "weight": 15}, {"type": "minecraft:item", "name": "minecraft:disc_fragment_5", "weight": 10},
    {"type": "minecraft:item", "name": "elsewhere:resonant_shard", "weight": 20, "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 4}}]},
    {"type": "minecraft:item", "name": "elsewhere:echo_crystal", "weight": 12}, {"type": "minecraft:item", "name": "elsewhere:silence_draught", "weight": 6},
    {"type": "minecraft:item", "name": "minecraft:soul_torch", "weight": 10}]}], "minecraft:chest")
table("chests/silent_monastery", [{"rolls": {"type": "minecraft:uniform", "min": 2, "max": 4}, "entries": [
    {"type": "minecraft:item", "name": "elsewhere:sculk_censer", "weight": 6}, {"type": "minecraft:item", "name": "elsewhere:hushweave_carpet", "weight": 15},
    {"type": "minecraft:item", "name": "elsewhere:silence_draught", "weight": 10}, {"type": "minecraft:item", "name": "minecraft:candle", "weight": 12},
    {"type": "minecraft:item", "name": "elsewhere:resonant_shard", "weight": 10}]}], "minecraft:chest")

def tag(path, values):
    w(os.path.join(D, path + ".json"), {"replace": False, "values": values})
E = lambda l: ["elsewhere:" + x for x in l]
tag("minecraft/tags/block/mineable/pickaxe", E(["trichome_quartz_ore", "trichome_quartz_block", "hushstone", "hushstone_stairs", "hushstone_slab",
    "hushstone_wall", "polished_hushstone", "hushstone_bricks", "hushstone_brick_stairs", "hushstone_brick_slab", "hushstone_brick_wall",
    "echo_crystal_ore", "echo_crystal_block", "ossuary_bone_bricks", "ossuary_bone_brick_stairs", "ossuary_bone_brick_slab", "ossuary_bone_brick_wall"]))
tag("minecraft/tags/block/mineable/axe", E(["kushwood_log", "stripped_kushwood_log", "kushwood_wood", "stripped_kushwood_wood", "kushwood_planks",
    "kushwood_stairs", "kushwood_slab", "kushwood_fence", "kushwood_fence_gate", "kushwood_door", "kushwood_trapdoor", "kushwood_button",
    "kushwood_pressure_plate", "giant_fern"]))
tag("minecraft/tags/block/mineable/hoe", E(["kushwood_leaves", "haze_resin_block"]))
tag("minecraft/tags/block/mineable/shovel", E(["haze_vent"]))
tag("minecraft/tags/block/needs_iron_tool", E(["trichome_quartz_ore", "echo_crystal_ore"]))
tag("minecraft/tags/block/walls", E(["hushstone_wall", "hushstone_brick_wall", "ossuary_bone_brick_wall"]))
tag("minecraft/tags/block/wooden_fences", E(["kushwood_fence"]))
tag("minecraft/tags/block/fence_gates", E(["kushwood_fence_gate"]))
tag("minecraft/tags/block/leaves", E(["kushwood_leaves"]))
tag("minecraft/tags/block/logs", E(["kushwood_log", "stripped_kushwood_log", "kushwood_wood", "stripped_kushwood_wood"]))
tag("minecraft/tags/block/wool", E(["hushweave"]))
tag("minecraft/tags/block/dampens_vibrations", E(["hushweave", "hushweave_carpet"]))
tag("minecraft/tags/block/occludes_vibration_signals", E(["hushweave", "hushstone", "hushstone_bricks", "polished_hushstone"]))
tag("elsewhere/tags/item/trichome_tool_materials", E(["trichome_quartz"]))
tag("elsewhere/tags/item/repairs_frond_armor", E(["haze_resin"]))
tag("elsewhere/tags/item/echo_tool_materials", E(["echo_crystal"]))
tag("elsewhere/tags/item/repairs_hushweave_armor", E(["hush_membrane"]))
print("assets ok", len(LANG), "lang entries")
