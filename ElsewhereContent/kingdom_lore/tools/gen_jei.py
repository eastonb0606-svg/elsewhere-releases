"""Builds INTEGRATION/jei_info_kingdom.json (JEI info pages for every kingdom item of all 4 lanes) and the Test Meter exclusion tag.
Run from anywhere: python3 gen_jei.py.  Items found in the other lanes' lang files get an auto page unless TEXT has one."""
import json, os, glob, re
HERE = os.path.dirname(os.path.abspath(__file__))
CONTENT = os.path.abspath(os.path.join(HERE, "..", ".."))
OUT = os.path.join(HERE, "..", "INTEGRATION", "jei_info_kingdom.json")
TAG = os.path.join(HERE, "..", "src/main/resources/data/testmeter/tags/item/transmutation_excluded.json")
CROWN = "Aldermark Crowns"
SEALS = {"marshal": "Marshal Ysolde Varr", "castellan": "Castellan Bertrand Holm", "master_at_arms": "Master-at-Arms Corvin Thale",
         "huntsman": "Royal Huntsman Lisbet Ashgrove", "chancellor": "Lord Chancellor Marek Dunmere"}
TEXT = {
 "lore_royal_proclamation": ["A 12-page written book in the voice of King Osric Vael, the Hundredth Crown. It tells of the five Lords, the Seals, the villages and the Survey.",
   "How to get: a royal patrol's Sergeant (the courier) hands you one if you right-click him while he is not fighting you; it is also in the courier's pouch if he dies, in the Reeve's stall (3 emeralds) and rarely in castle dungeons."],
 "lore_survey_fragment": ["A torn corner of the royal surveyors' chart. Three of them craft one folded Royal Survey Map.",
   "How to get: Captains drop one 25% of the time, patrol couriers 40%, other patrol soldiers 4%. Also found in Aldermark outpost, armory and dungeon chests, and 12% of village cartographer chests."],
 "lore_royal_survey_map": ["Craft 3 Survey Map Fragments (shapeless). Use it in the Overworld to unfold it: it turns into an explorer-style map centred on the nearest Castle Aldermark, with a red X.",
   "If no castle is known within about 4,000 blocks, the map stays folded and tells you so.",
   "A cartographer in a village you liberated also sells the finished map: 12 emeralds and a compass."],
 "aldermark_crown": ["The coin of the Kingdom of Aldermark. Soldiers carry them: about 1 to 3 per rank-and-file soldier, 4 to 8 per officer, 16 to 32 per Lord and a 64-Crown treasury from the King on his first defeat.",
   "Spend them with the Royal Steward of a liberated town. Prices are deliberately very high, so Crowns are a long-term alternative to a trading hall, not a shortcut."],
 "bodkin_bolt": ["An arrow that ignores 25% of the target's armor. Good against late-game armored targets, not a damage boost.",
   "How to get: Crossbowmen drop 2 to 6 at 15%. The Royal Steward sells 8 for 32 Crowns (Novice)."],
 "whetstone": ["Restores 15% durability to a sword, axe or tool in your other hand. A consumable, not a Mending replacement.", "How to get: Men-at-Arms drop it 5% of the time."],
 "fletchers_bundle": ["Use it for 8 arrows and 1 spectral arrow.", "How to get: Archers drop it 10% of the time."],
 "sergeants_horn": ["Blow it: allies within 12 blocks get Strength I for 8 seconds. 90 second cooldown.", "How to get: Sergeants drop it 20% of the time."],
 "oathkeepers_seal": ["A weaker totem. Held, it saves you from death once: you stay at 3 hearts with Resistance II for 2 seconds. Single use, shares the 30 second death-protection lockout with other extra lives.",
   "How to get: officers drop it 4% of the time; each Lord drops one on your first kill of him; the Royal Steward sells it for 256 Crowns (Expert, once per in-game week)."],
 "aldermark_armor_trim_smithing_template": ["Cosmetic armor trim. Duplicate it like any vanilla template.", "How to get: Captains drop it 8% of the time; the Royal Steward sells copies for 24 to 48 Crowns (Novice)."],
 "aldermark_banner_pattern": ["A cosmetic banner pattern: the Aldermark Tower.", "How to get: Standard-Bearers always drop one; the Royal Steward sells copies (24 to 48 Crowns, Novice). Quartermasters sell vanilla patterns for Lord Seals."],
 "village_royal_coffer": ["The iron-bound job site of the Royal Steward, the kingdom's former tax collector, who surrendered when the village was liberated and stayed on.",
   "Serfs build it into the tax office of a fortified town. It cannot be crafted and drops nothing when broken, so a Steward exists only in towns you really liberated: at most one per town. A jobless villager can take the job the vanilla way if the Steward dies.",
   "Royal Steward trades (Crowns): cosmetics 24-48 and Bodkin Bolts x8 for 32 (Novice); Oathkeeper's Seal 256, one per week (Expert); enchanted books Sharpness V, Protection IV, Efficiency V or Unbreaking III, 192 Crowns + 4 emerald blocks each, 2 of the 4 per Steward, restock weekly (Master); Netherite Upgrade Template 320 Crowns + 24 diamonds, one per week (Master). Master needs at least 2 Lords dead."],
 "village_royal_purse": ["The seized village treasury, handed back after a liberation."],
 "village_ash": ["Ash left where a siege burned a village. The restore command clears it."],
 "village_charred_planks": ["Burnt planks from a besieged village. Restoring the village brings the originals back."],
 "village_siege_fire": ["A controlled siege fire. It burns only village blocks and goes out on its own."],
}
RIV = {"hrafnholt": ("Hrafnholt", "High Jarl Halvard Ironbrow's blunt Hearth-Oath", "Hird-Sergeant", "Hrafnholt war-band", "Castle Hrafnholt", "raven"),
       "azmar": ("Azmar", "the Sultana's Well-Ledger", "Caravan-Master", "Azmar caravan", "the Sultan's Citadel", "well-stone"),
       "mirewick": ("Mirewick", "the Lantern Council's Notice", "Reeve", "Mirewick lantern patrol", "the Counting-House", "lantern")}
for k, (n, what, courier, patrol, castle, mark) in RIV.items():
    book = "mirewick_council_proclamation" if k == "mirewick" else k + "_royal_proclamation"
    TEXT[book] = ["A 10-page written book: " + what + ". Each page is a few lines in the kingdom's own voice, and it mentions Aldermark's tower-flag.",
        "How to get: the " + courier + " of a " + patrol + " hands you one if you right-click him while he is not fighting you; also in the courier's pouch if he dies."]
    TEXT[k + "_survey_fragment"] = ["A torn corner of a " + n + " chart. Three of them craft one folded " + n + " Survey Map.",
        "How to get: " + n + " couriers drop one 40% of the time, other patrol soldiers 4%."]
    TEXT[k + "_survey_map"] = ["Craft 3 " + n + " Survey Map Fragments (shapeless). Use it in the Overworld to unfold it: it becomes an explorer-style map centred on " + castle + ", with a red X.",
        "A cartographer in a village you liberated from " + n + " also sells the finished map: 12 emeralds and a compass.",
        "Operators can also run /" + k + " locate."]
TEXT["azmar_well_stone"] = ["A carved well-marker of the Sultanate. A keepsake and trade good, it does nothing by itself.", "How to get: always in the Azmar caravan-master's pouch."]
for k, n in SEALS.items():
    TEXT["lore_seal_" + k] = ["The seal of " + n + ", pressed in wax and Glimmer. Quartermasters and the Reeve buy any Seal for 12 emeralds, and swap Marshal and Chancellor Seals plus 6 emeralds for banner patterns.",
                              "How to get: dropped by " + n + " when defeated. Each Seal counts toward the Sovereign's Standard."]

def lang_items():
    found = {}
    for m in ("kingdom", "kingdom_court", "kingdom_villages", "kingdom_lore"):
        for f in glob.glob(os.path.join(CONTENT, m, "src/main/resources/assets/elsewhere/lang/en_us.json")):
            d = json.load(open(f))
            for k, v in d.items():
                mm = re.match(r"(item|block)\.elsewhere\.([a-z0-9_]+)$", k)
                if mm and not mm.group(2).startswith("banner") and mm.group(2) not in ("lore_aldermark_surcoat", "lore_royal_survey_map_filled") and not mm.group(2).endswith("_survey_map_filled"):
                    found[mm.group(2)] = v
    return found

items = lang_items()
pages = {}
for name, lines in TEXT.items():
    pages["elsewhere:" + name] = lines
for name, title in sorted(items.items()):
    key = "elsewhere:" + name
    if key in pages: continue
    if name.endswith("_spawn_egg"):
        pages[key] = ["Creative-only spawn egg for the Kingdom of Aldermark. It ignores the army caps, so it is an operator tool."]
    elif name.endswith((".new", ".desc", ".tooltip")):
        continue
    else:
        pages[key] = ["Part of the Kingdom of Aldermark: " + title + ". See the Kingdom of Aldermark wiki page for how to get it."]
json.dump(pages, open(OUT, "w"), indent=1)
print("JEI pages:", len(pages))

# Test Meter exclusion: every kingdom item (currency, Seals, uniques, loot)
ids = sorted(set("elsewhere:" + n for n in items) | {"elsewhere:" + n for n in TEXT} | {"elsewhere:royal_coffer", "elsewhere:sovereigns_standard", "elsewhere:crown_of_aldermark", "elsewhere:court_crown_of_aldermark"})
ids = [i for i in ids if not i.endswith(("_spawn_egg",))]
json.dump({"replace": False, "values": [{"id": i, "required": False} for i in ids]}, open(TAG, "w"), indent=2)
print("excluded:", len(ids))
