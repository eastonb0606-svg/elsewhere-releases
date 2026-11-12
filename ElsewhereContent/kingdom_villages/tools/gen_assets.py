#!/usr/bin/env python3
"""Generates the kingdom_villages placeholder textures and the block/item JSON (run with python3 -I)."""
import json, os, random, struct, sys, zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "elsewhere")

def png(path, w, h, px):
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *px[y * w + x]) for x in range(w)) for y in range(h))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    data = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)

def tex(name, fn, w=16, h=16):
    r = random.Random(name)
    png(os.path.join(ROOT, "textures", name + ".png"), w, h, [fn(x, y, r) for y in range(h) for x in range(w)])

def jw(rel, obj):
    p = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    json.dump(obj, open(p, "w"), indent=2)

def charred(x, y, r):
    seam = y % 4 == 3
    v = 22 + r.randint(0, 14) - (10 if seam else 0)
    if r.random() < 0.06: return (150 + r.randint(0, 60), 60, 20, 255)  # embers
    return (v + 6, v + 2, v, 255)

def ash(x, y, r):
    v = 150 + r.randint(-25, 25)
    return (v, v, v - 4, 255)

def coffer_side(x, y, r):
    if x in (0, 15) or y in (0, 15) or x in (4, 11): return (70, 72, 78, 255)  # iron bands
    v = r.randint(-8, 8)
    return (96 + v, 62 + v, 34 + v, 255)

def coffer_top(x, y, r):
    if x in (0, 15) or y in (0, 15) or y in (7, 8): return (70, 72, 78, 255)
    v = r.randint(-8, 8)
    return (104 + v, 68 + v, 38 + v, 255)

def coffer_front(x, y, r):
    if 6 <= x <= 9 and 5 <= y <= 9: return (212, 175, 55, 255)  # gold lock
    return coffer_side(x, y, r)

def coffer_sealed(x, y, r):
    if 6 <= x <= 9 and 5 <= y <= 9: return (40, 70, 160, 255)  # Aldermark blue seal
    if y == 7: return (40, 70, 160, 255)
    return coffer_side(x, y, r)

def purse(x, y, r):
    cx, cy = 7.5, 9
    d = ((x - cx) / 5.5) ** 2 + ((y - cy) / 5.0) ** 2
    if 2 <= y <= 4 and 6 <= x <= 9: return (180, 150, 60, 255)
    if d <= 1.0:
        if y == 5: return (212, 175, 55, 255)
        v = r.randint(-6, 6)
        return (46 + v, 72 + v, 150 + v, 255)
    return (0, 0, 0, 0)

def steward(x, y, r):
    # villager profession overlay (64x64): robe on body/arms/legs, gold trim; transparent elsewhere
    body = 16 <= x < 40 and 20 <= y < 38
    legs = 0 <= x < 16 and 22 <= y < 32
    arms = 40 <= x < 56 and 22 <= y < 32
    if not (body or legs or arms): return (0, 0, 0, 0)
    if y in (20, 21, 37) or (body and x in (22, 23)): return (212, 175, 55, 255)
    v = r.randint(-6, 6)
    return (36 + v, 58 + v, 130 + v, 255)

tex("block/village_charred_planks", charred)
tex("block/village_ash", ash)
tex("block/village_royal_coffer_side", coffer_side)
tex("block/village_royal_coffer_top", coffer_top)
tex("block/village_royal_coffer_front", coffer_front)
tex("block/village_royal_coffer_front_sealed", coffer_sealed)
tex("item/village_royal_purse", purse)
tex("entity/villager/profession/royal_steward", steward, 64, 64)

ns = "elsewhere:"
jw("blockstates/village_siege_fire.json", {"variants": {"": [{"model": "minecraft:block/fire_floor0"}, {"model": "minecraft:block/fire_floor1"}]}})
jw("blockstates/village_charred_planks.json", {"variants": {"": {"model": ns + "block/village_charred_planks"}}})
jw("models/block/village_charred_planks.json", {"parent": "minecraft:block/cube_all", "textures": {"all": ns + "block/village_charred_planks"}})
jw("blockstates/village_ash.json", {"variants": {"": {"model": ns + "block/village_ash"}}})
jw("models/block/village_ash.json", {"parent": "minecraft:block/carpet", "textures": {"wool": ns + "block/village_ash", "particle": ns + "block/village_ash"}})
variants = {}
for sealed in ("false", "true"):
    for facing, rot in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        m = {"model": ns + "block/village_royal_coffer" + ("_sealed" if sealed == "true" else "")}
        if rot: m["y"] = rot
        variants["facing=%s,sealed=%s" % (facing, sealed)] = m
jw("blockstates/village_royal_coffer.json", {"variants": variants})
for suffix, front in (("", "village_royal_coffer_front"), ("_sealed", "village_royal_coffer_front_sealed")):
    jw("models/block/village_royal_coffer%s.json" % suffix, {"parent": "minecraft:block/orientable", "textures": {
        "top": ns + "block/village_royal_coffer_top", "front": ns + "block/" + front, "side": ns + "block/village_royal_coffer_side"}})
for b in ("village_charred_planks", "village_ash", "village_royal_coffer"):
    jw("items/%s.json" % b, {"model": {"type": "minecraft:model", "model": ns + "block/" + b}})
jw("models/item/village_royal_purse.json", {"parent": "minecraft:item/generated", "textures": {"layer0": ns + "item/village_royal_purse"}})
jw("items/village_royal_purse.json", {"model": {"type": "minecraft:model", "model": ns + "item/village_royal_purse"}})

lang = {
    "block.elsewhere.village_siege_fire": "Siege Fire",
    "block.elsewhere.village_charred_planks": "Charred Planks",
    "block.elsewhere.village_ash": "Ash",
    "block.elsewhere.village_royal_coffer": "Royal Coffer",
    "item.elsewhere.village_royal_purse": "Royal Purse",
    "entity.elsewhere.serf": "Serf",
    "entity.elsewhere.villager.royal_steward": "Royal Steward",
    "entity.elsewhere.village.tax_collector": "Royal Tax Collector",
    "entity.elsewhere.village.refugee": "Refugee, fled the soldiers from the %s",
    "direction.elsewhere.north": "north", "direction.elsewhere.northeast": "north-east", "direction.elsewhere.east": "east",
    "direction.elsewhere.southeast": "south-east", "direction.elsewhere.south": "south", "direction.elsewhere.southwest": "south-west",
    "direction.elsewhere.west": "west", "direction.elsewhere.northwest": "north-west",
    "message.elsewhere.village.captured": "This villager is held by the Crown's soldiers.",
    "message.elsewhere.village.warn": "<%s> In the name of King Osric, sheathe your weapon, traveller. This town is held by the Crown.",
    "message.elsewhere.village.liberated": "The village is free! The Crown's tax collector surrenders and keeps the seized coffer.",
    "message.elsewhere.village.outpost_captured": "The Aldermark outpost has fallen.",
    "sign.elsewhere.village.milestone": "Aldermark",
    "sign.elsewhere.village.milestone_dist": "%s blocks",
}
jw("lang/en_us.json", lang)
print("assets written")
