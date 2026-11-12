"""Generates the rival-kingdom lore assets (textures, item/model JSON, recipes, advancements, loot tables, lang).
Run from anywhere: ../../../_tools/venv/bin/python gen_rivals.py   (idempotent)."""
import json, os
from PIL import Image, ImageDraw
HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src/main/resources")
A = os.path.join(RES, "assets/elsewhere")
D = os.path.join(RES, "data/elsewhere")

def wr(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=1)

# palette: paper, edge, ink, accent (seal), accent2
PAL = {
 "hrafnholt": dict(paper=(205, 205, 210, 255), edge=(70, 72, 80, 255), ink=(25, 25, 30, 255), seal=(20, 20, 24, 255), hi=(235, 235, 240, 255)),
 "azmar": dict(paper=(236, 214, 140, 255), edge=(176, 120, 40, 255), ink=(31, 63, 143, 255), seal=(212, 165, 55, 255), hi=(43, 179, 163, 255)),
 "mirewick": dict(paper=(222, 218, 190, 255), edge=(79, 107, 58, 255), ink=(58, 47, 38, 255), seal=(224, 168, 58, 255), hi=(79, 107, 58, 255)),
}
def img(): return Image.new("RGBA", (16, 16), (0, 0, 0, 0))
def save(i, name): i.save(os.path.join(A, "textures/item", name + ".png"))

def scroll(k):
    p = PAL[k]; i = img(); d = ImageDraw.Draw(i)
    d.rectangle([3, 2, 12, 13], fill=p["paper"], outline=p["edge"])
    d.rectangle([2, 1, 13, 2], fill=p["edge"]); d.rectangle([2, 13, 13, 14], fill=p["edge"])
    for y in (5, 7, 9): d.line([5, y, 10, y], fill=p["ink"])
    d.ellipse([9, 10, 12, 13], fill=p["seal"], outline=p["edge"])
    if k == "hrafnholt": d.point((10, 11), fill=p["hi"])     # raven eye
    if k == "azmar": d.point((10, 11), fill=p["hi"])         # turquoise scarab
    if k == "mirewick": d.rectangle([10, 10, 11, 12], fill=p["ink"])  # lantern slit
    return i

def fragment(k):
    p = PAL[k]; i = img(); d = ImageDraw.Draw(i)
    d.polygon([(2, 3), (12, 2), (13, 9), (9, 8), (8, 13), (3, 12)], fill=p["paper"], outline=p["edge"])
    d.line([4, 5, 9, 5], fill=p["ink"]); d.line([4, 7, 7, 7], fill=p["ink"]); d.point((10, 5), fill=p["seal"])
    d.point((4, 10), fill=p["hi"])
    return i

def foldedmap(k):
    p = PAL[k]; i = img(); d = ImageDraw.Draw(i)
    d.rectangle([2, 3, 13, 12], fill=p["paper"], outline=p["edge"])
    d.line([6, 3, 6, 12], fill=p["edge"]); d.line([10, 3, 10, 12], fill=p["edge"])
    d.line([4, 6, 5, 9], fill=p["ink"]); d.line([8, 5, 8, 10], fill=p["ink"])
    d.rectangle([11, 6, 12, 7], fill=p["seal"] if k != "hrafnholt" else (170, 30, 40, 255))
    d.ellipse([6, 8, 9, 11], fill=p["seal"], outline=p["edge"])
    return i

def wellstone():
    i = img(); d = ImageDraw.Draw(i)
    d.ellipse([3, 3, 12, 13], fill=(214, 190, 130, 255), outline=(150, 112, 60, 255))
    d.ellipse([5, 5, 9, 9], fill=(226, 205, 150, 255))
    for xy in [(6, 6), (8, 6), (7, 7), (6, 8), (8, 8)]: d.point(xy, fill=(43, 179, 163, 255))  # faience glyph
    d.line([5, 11, 10, 11], fill=(31, 63, 143, 255))
    return i

ITEMS = []
for k in PAL:
    book = "mirewick_council_proclamation" if k == "mirewick" else k + "_royal_proclamation"
    for name, im in [(book, scroll(k)), (k + "_survey_fragment", fragment(k)), (k + "_survey_map", foldedmap(k))]:
        os.makedirs(os.path.join(A, "textures/item"), exist_ok=True)
        save(im, name); ITEMS.append(name)
save(wellstone(), "azmar_well_stone"); ITEMS.append("azmar_well_stone")
for n in ITEMS:
    wr(os.path.join(A, "items", n + ".json"), {"model": {"type": "minecraft:model", "model": "elsewhere:item/" + n}})
    wr(os.path.join(A, "models/item", n + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": "elsewhere:item/" + n}})

# recipes
for k in PAL:
    wr(os.path.join(D, "recipe", k + "_survey_map.json"), {"type": "minecraft:crafting_shapeless", "category": "misc",
       "ingredients": ["elsewhere:%s_survey_fragment" % k] * 3, "result": {"id": "elsewhere:%s_survey_map" % k}})

# loot tables
def pool_item(name, wt, lo, hi):
    e = {"type": "minecraft:item", "name": name, "weight": wt}
    if hi > 1: e["modifier"] = [{"type": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e
FILL = {
 "hrafnholt": [("minecraft:cooked_beef", 4, 1, 3), ("minecraft:iron_nugget", 3, 2, 6), ("minecraft:feather", 3, 1, 4), ("minecraft:arrow", 2, 4, 8), ("minecraft:bread", 2, 1, 3)],
 "azmar": [("minecraft:bread", 4, 1, 3), ("minecraft:gold_nugget", 4, 2, 6), ("minecraft:paper", 3, 1, 3), ("minecraft:arrow", 2, 4, 8), ("minecraft:lapis_lazuli", 2, 1, 3)],
 "mirewick": [("minecraft:emerald", 4, 1, 2), ("minecraft:paper", 4, 1, 4), ("minecraft:gold_nugget", 3, 2, 5), ("minecraft:bread", 3, 1, 3), ("minecraft:lantern", 1, 1, 1)],
}
for k in PAL:
    book = "elsewhere:mirewick_council_proclamation" if k == "mirewick" else "elsewhere:%s_royal_proclamation" % k
    pools = [
      {"rolls": 1, "entries": [{"type": "minecraft:item", "name": book, "weight": 1}]},
      {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "elsewhere:%s_survey_fragment" % k, "weight": 1}],
       "condition": {"type": "minecraft:random_chance", "chance": 0.4}},
      {"rolls": {"type": "minecraft:uniform", "min": 1, "max": 3}, "entries": [pool_item(*f) for f in FILL[k]]},
    ]
    if k == "azmar":
        pools.insert(1, {"rolls": 1, "entries": [{"type": "minecraft:item", "name": "elsewhere:azmar_well_stone", "weight": 1}]})
    wr(os.path.join(D, "loot_table/chests", k + "_courier.json"), {"type": "minecraft:chest", "pools": pools})

# advancements
def adv(name, icon, parent, trigger_item=None, frame="task"):
    crit = {"trigger": "minecraft:impossible"}
    if trigger_item:
        crit = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [trigger_item]}]}}
    wr(os.path.join(D, "advancement/whispers", name + ".json"), {
        "display": {"icon": {"id": icon}, "title": {"translate": "advancement.elsewhere.whispers.%s.title" % name},
                    "description": {"translate": "advancement.elsewhere.whispers.%s.desc" % name}, "frame": frame, "show_toast": True,
                    "announce_to_chat": True, "hidden": False},
        "criteria": {"done": crit}, "parent": "elsewhere:whispers/" + parent})
ROAD = {"hrafnholt": "minecraft:iron_axe", "azmar": "elsewhere:azmar_well_stone", "mirewick": "minecraft:lantern"}
BORDER = {"hrafnholt": "minecraft:black_banner", "azmar": "minecraft:cut_sandstone", "mirewick": "minecraft:soul_lantern"}
adv("border_hint", "minecraft:white_banner", "root")
for k in PAL:
    book = "elsewhere:mirewick_council_proclamation" if k == "mirewick" else "elsewhere:%s_royal_proclamation" % k
    adv(k + "_road", ROAD[k], "root")
    adv(k + "_decree", book, k + "_road", book)
    adv(k + "_border", BORDER[k], k + "_road")

# lang
LANG = os.path.join(A, "lang/en_us.json")
lang = json.load(open(LANG))
NAMES = {"hrafnholt": "Hrafnholt", "azmar": "Azmar", "mirewick": "Mirewick"}
BOOK = {"hrafnholt": ("Hearth-Oath of Hrafnholt", "A blunt oath sworn at the Hearth-Fire.", "Sworn by High Jarl Halvard Ironbrow."),
        "azmar": ("Well-Ledger of Azmar", "A patient ledger of wells and roads.", "Written by the Sultana, the Unsetting Sun."),
        "mirewick": ("Notice of the Lantern Council", "Terms of passage, in small print.", "Signed by five Magnates and invoiced.")}
FRAG = {"hrafnholt": "A scrap of a clan-scout's chart, stained with soot.", "azmar": "A torn corner of a caravan-master's chart.",
        "mirewick": "A corner of the Dikemaster's survey, creased where it was folded for the canal-side."}
POINT = {"hrafnholt": "Castle Hrafnholt, in the fjord forests", "azmar": "the Sultan's Citadel of Azmar", "mirewick": "the Counting-House of Mirewick"}
for k, n in NAMES.items():
    book = "mirewick_council_proclamation" if k == "mirewick" else k + "_royal_proclamation"
    lang["item.elsewhere." + book] = BOOK[k][0]
    lang["item.elsewhere.%s_survey_fragment" % k] = n + " Survey Fragment"
    lang["item.elsewhere.%s_survey_fragment.lore1" % k] = FRAG[k]
    lang["item.elsewhere.%s_survey_fragment.lore2" % k] = "Three of these, joined, make a Survey Map."
    lang["item.elsewhere.%s_survey_map" % k] = n + " Survey Map (folded)"
    lang["item.elsewhere.%s_survey_map.lore1" % k] = "Three joined fragments of a " + n + " chart."
    lang["item.elsewhere.%s_survey_map.lore2" % k] = "Use it in the Overworld to unfold it."
    lang["item.elsewhere.%s_survey_map.lore3" % k] = "It points to " + POINT[k] + "."
    lang["item.elsewhere.%s_survey_map_filled" % k] = n + " Survey Map"
    lang["commands.elsewhere.%s_locate.success" % k] = "The %s realm centre is at %%s (%%s blocks away)" % n
    lang["commands.elsewhere.%s_locate.failed" % k] = "This world has no standing %s realm (module absent, no castle placed, or the realm has fallen)." % n
lang["item.elsewhere.azmar_well_stone"] = "Azmar Well-Stone"
lang["item.elsewhere.azmar_well_stone.lore1"] = "A carved stone from a caravan's well-marker."
lang["item.elsewhere.azmar_well_stone.lore2"] = "Where one stands, there is water, and a road."
lang["message.elsewhere.lore_courier_hrafnholt_1"] = "<Hird-Sergeant> The Jarl's oath, stranger. Read it by a fire, and keep your axe in its loop."
lang["message.elsewhere.lore_courier_hrafnholt_2"] = "<Hird-Sergeant> Take it. The ravens saw you coming, so you may as well know what you are walking into."
lang["message.elsewhere.lore_courier_azmar_1"] = "<Caravan-Master> The Sultana's ledger, traveller. It is longer than it looks, and so is the road."
lang["message.elsewhere.lore_courier_azmar_2"] = "<Caravan-Master> Water first, then words. The Well-Ledger is yours; the well-stone is for the way."
lang["message.elsewhere.lore_courier_mirewick_1"] = "<Reeve> Notice of the Council, citizen. The terms are in order and the fee is waived. Today."
lang["message.elsewhere.lore_courier_mirewick_2"] = "<Reeve> Please keep to the quay. A copy of the Notice for your records."
ADV = {
 "border_hint": ("Marked Ground", "Come near a kingdom's border marker: a tower-flag, a raven post, an obelisk or a lantern."),
 "hrafnholt_road": ("A Raven on the Road", "Meet a Hrafnholt war-band on the road."),
 "hrafnholt_decree": ("The Hearth-Oath", "Receive or find the High Jarl's Hearth-Oath."),
 "hrafnholt_border": ("Where the Ravens Sit", "Find a raven post in the fjord forests at the edge of Hrafnholt's land, where the sea-raiders watch."),
 "azmar_road": ("A Caravan in the Sand", "Meet an Azmar caravan on the road."),
 "azmar_decree": ("The Well-Ledger", "Receive or find the Sultana's Well-Ledger."),
 "azmar_border": ("An Obelisk at the Edge", "Find an Azmar obelisk marking its claimed ground."),
 "mirewick_road": ("A Lantern in the Mist", "Meet a lantern-carrying Mirewick patrol."),
 "mirewick_decree": ("The Small Print", "Receive or find the Council's Notice."),
 "mirewick_border": ("Keep to the Quay", "Find a lantern post along the canals at the edge of Mirewick's land."),
}
for n, (t, dsc) in ADV.items():
    lang["advancement.elsewhere.whispers.%s.title" % n] = t
    lang["advancement.elsewhere.whispers.%s.desc" % n] = dsc
json.dump(lang, open(LANG, "w"), indent=1)
print("ok", len(ITEMS), "items")
