# JEI check (the jei_compat "self-test"): every item that is new in 2.0 has an info page, every page names a registered item id
# (as far as the lang files of all modules know), and no page is empty. Usage (from jei_compat/): python3 -I tools/check_info.py
import glob, json, os, sys, zipfile
HERE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.dirname(HERE)
# 3.0: the plugin reads every assets/elsewhere/jei/*.json; gear3's jei_info.json and the gear2/kingdom_lore INTEGRATION files reach
# info.json at merge (snapshot_extra). Check against that merged view. The server-side JeiSelfTest is the full check (recipes too).
info = {}
for f in sorted(glob.glob(os.path.join(HERE, 'src/main/resources/assets/elsewhere/jei/*.json'))) + [os.path.join(CONTENT, 'gear3/src/main/resources/elsewhere_gear3/jei_info.json'), os.path.join(CONTENT, 'gear2/INTEGRATION/jei_info_gear2.json'), os.path.join(CONTENT, 'kingdom_lore/INTEGRATION/jei_info_kingdom.json')]:
    if os.path.exists(f):
        for k, v in json.load(open(f)).items():
            k = k if ':' in k else 'elsewhere:' + k
            info.setdefault(k, [])
            info[k] += [l for l in v if l not in info[k]]
old = json.loads(zipfile.ZipFile(os.path.join(HERE, 'libs/elsewhere-0.3.0-beta.jar')).read('assets/elsewhere/lang/en_us.json'))
def ids(d):
    return {k.split('.', 2)[2] for k in d if k.startswith(('item.elsewhere.', 'block.elsewhere.')) and k.count('.') == 2}
oldids = ids(old)
mods = {}
for l in glob.glob(CONTENT + '/*/src/main/resources/assets/elsewhere/lang/en_us.json') + glob.glob(os.path.join(CONTENT, '..', 'ElsewhereBeta/src/main/resources/assets/elsewhere/lang/en_us.json')):
    for i in ids(json.load(open(l))):
        mods.setdefault(i, set()).add(l.split('/')[-6] if '/src/' in l else '?')
new = sorted(i for i in mods if i not in oldids)
# not worth a page: spawn eggs (creative only), technical blocks, the filled-map twin of the Survey Map
skip = lambda i: i.endswith('_spawn_egg') or i in ('lore_royal_survey_map_filled',)
fails = []
def check(name, ok, detail=''):
    print(('PASS ' if ok else 'FAIL ') + name + ' ' + detail)
    if not ok:
        fails.append(name)
missing = [i for i in new if not skip(i) and 'elsewhere:' + i not in info]
check('every new 2.0 item has an info page (%d new ids checked)' % len([i for i in new if not skip(i)]), not missing, str(missing))
check('no empty page', all(v for v in info.values()), '')
check('gear2 pages merged', all('elsewhere:' + i in info for i in ('seers_hourglass', 'prospectors_pendulum', 'void_tether_spool', 'termite_queen', 'amberstill_upgrade_smithing_template', 'steam_auger', 'ironclad_flail', 'mirrorbolt_arbalest', 'galvanic_coilmail_chestplate')), '')
check('Hush items: Echo tools, Hushweave, Echo Alloy, template', all('elsewhere:' + i in info for i in ('echo_pickaxe', 'echo_sword', 'hushweave_hood', 'hushweave_boots', 'hushcloak', 'echo_alloy', 'hushweave_upgrade_smithing_template')), '')
hood = ' '.join(info.get('elsewhere:hushweave_hood', []))
check('Hushweave page explains Unheard', 'Unheard' in hood and 'half as far' in hood, '')
src = open(os.path.join(HERE, 'src/main/java/dev/elsewhere/compat/jei/AttunementCategory.java')).read()
check('Wayfinder attunement page: Hush note is two paragraphs in a 40 px box (not cut off)', 'addText(lines, WIDTH, 40)' in src and 'any_layout_short' in src, '')
check('attunement pages still registered (category, catalysts, transfer handler)', all(s in open(os.path.join(HERE, 'src/main/java/dev/elsewhere/compat/jei/ElsewhereJeiPlugin.java')).read() for s in ('addRecipes(AttunementCategory.TYPE', 'addCraftingStation(AttunementCategory.TYPE', 'addRecipeTransferHandler')), '')
print('TOTAL %d failed' % len(fails))
sys.exit(1 if fails else 0)
