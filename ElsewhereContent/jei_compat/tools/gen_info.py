# Builds src/main/resources/assets/elsewhere/jei/info.json (JEI info pages) from ROOT/Ideas/catalog.json,
# keeping only items that are really registered (names matched through the Elsewhere jar's en_us.json).
# Usage: python3 -I tools/gen_info.py <catalog.json> <elsewhere jar> <out json>
import json, sys, zipfile
cat = json.load(open(sys.argv[1]))
lang = json.loads(zipfile.ZipFile(sys.argv[2]).read('assets/elsewhere/lang/en_us.json'))
ids = {}
for k, v in lang.items():
    p = k.split('.')
    if len(p) == 3 and p[0] in ('item', 'block') and p[1] == 'elsewhere':
        ids.setdefault(v.lower(), 'elsewhere:' + p[2])
info = {}
def add(name, line):
    i = ids.get(name.lower())
    if i and line and line not in info.setdefault(i, []):
        info[i].append(line)
for it in cat['items']:
    add(it['name'], it.get('what', ''))
    if it.get('source'):
        add(it['name'], 'How to get: ' + it['source'])
for g in cat.get('gear', []):
    add(g['name'], g.get('what', ''))
for b in cat['bosses']:
    boss = b['name'] + (', ' + b['title'] if b.get('title') else '')
    for d in b.get('drops', []):
        add(d, 'Dropped by ' + boss + ' (Tier ' + str(b.get('tier', '?')) + ', ' + b.get('where', '') + ').')
        if b.get('summon'):
            add(d, 'Summon ' + b['name'] + ': ' + b['summon'])

# Hand-written lines (lead's JEI list): Herding Crook orders, Calling on class armor.
CALLING = ("Calling is a reading of the gear you wear: each caster (or summoner) armor piece makes your spells "
           "(or minions) a little stronger, two pieces get you most of the way, and the gear of one Calling costs "
           "you a little of everything else. Nothing to choose: take the gear off and it's gone.")
def add_id(i, line):
    if i in ids.values() and line not in info.setdefault(i, []):
        info[i].append(line)
add_id('elsewhere:herding_crook', "Orders: your minions only attack players, pets, villagers or golems when you hit one with the Herding Crook; otherwise they leave them alone.")
for i in sorted(set(ids.values())):
    if any(w in i for w in ('moonsilk_', 'herdcaller_', 'stormsage_mantle', 'cantors_diadem', 'elder_bark_crown', 'engineers_harness')) and not i.endswith('thread'):
        add_id(i, CALLING)

# Boss summons: items named in a boss's summon text, plus the core's two summon items.
import re
for b in cat['bosses']:
    sm = b.get('summon', '')
    boss = b['name'] + (', ' + b['title'] if b.get('title') else '')
    for n, i in ids.items():
        if len(n) > 4 and re.search(r'\b' + re.escape(n) + r's?\b', sm.lower()):
            add_id(i, 'Summons ' + boss + ' (Tier ' + str(b.get('tier', '?')) + '): ' + sm + ' Where: ' + b.get('where', '') + '.')
add_id('elsewhere:heartwood_effigy', 'Summons Mother Creak (Tier I): use it to raise her Elder Trunk arena (Pale Hollow).')
add_id('elsewhere:moonring_seed', 'Summons Selenne (Tier III): use it to raise the Moonring arena (Pale Hollow).')
# Items the catalog doesn't describe: use the built item's own tooltip lines from the jar's en_us.json.
for k in sorted(lang):
    m = re.match(r'(item|block)\.elsewhere\.([a-z0-9_]+)\.desc(\d+)$', k)
    if m:
        i = 'elsewhere:' + m.group(2)
        if i not in info or not info[i] or i in ('elsewhere:heartwood_effigy', 'elsewhere:moonring_seed'):
            add_id(i, lang[k])
info = {k: v for k, v in sorted(info.items()) if v}
json.dump(info, open(sys.argv[3], 'w'), indent=1, ensure_ascii=False)
print(len(info), 'items with info pages')
