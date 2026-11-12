"""art lane 2: Glimmer Mote and Clockwork Repeater (arsenal)."""
import os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from px import Spr, load, recolor
A2 = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(A2, 'arsenal/src/main/resources/assets/elsewhere')
OUT = []


def jw(rel, obj):
    p = os.path.join(RES, rel); os.makedirs(os.path.dirname(p), exist_ok=True)
    open(p, 'w').write(json.dumps(obj, indent=2) + '\n'); OUT.append(p)


# ---- Glimmer Mote: a lilac spark of Glimmer (colours from glimmer_tonic / glimmerstone_focus)
W, L, M, D = '#fbf6ff', '#d9c2ff', '#a77bf3', '#6b45b8'
s = Spr()
for y in range(16):
    for x in range(16):
        dx, dy = x - 7, y - 7; r2 = dx * dx + dy * dy
        c = None
        if abs(dx) + abs(dy) <= 1:
            c = W
        elif r2 <= 2:
            c = L
        elif r2 <= 5:
            c = M
        elif dx == 0 or dy == 0:
            c = {3: L, 4: M, 5: M, 6: D}.get(abs(dx) + abs(dy))
        elif r2 <= 10:
            c = D
        elif abs(dx) == abs(dy) == 3:
            c = M
        if c:
            s.px(x, y, c)
for cx, cy in ((12, 2), (2, 12)):
    s.px(cx, cy, W)
    for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        s.px(cx + ox, cy + oy, M)
p = os.path.join(RES, 'textures/item/glimmer_mote.png'); s.save(p); OUT.append(p)
jw('models/item/glimmer_mote.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'elsewhere:item/glimmer_mote'}})

# ---- Clockwork Repeater: vanilla crossbow states, stock in brass, limbs in verdigris copper, a gear drum on the stock
BRASS = ['#2c1c08', '#4a3210', '#6b4a1c', '#9a6e2a', '#c79a3e', '#ecc96a']
VERD = ['#1d3d35', '#2a5a4b', '#3f8a72', '#62b39a', '#93d3b8']
WOODC = {(0x4d, 0x2e, 0x11), (0x2a, 0x1a, 0x09), (0x6d, 0x42, 0x18), (0x90, 0x58, 0x1f), (0xb5, 0x73, 0x2f), (0x3b, 0x24, 0x0d), (0x1f, 0x12, 0x06), (0x48, 0x2b, 0x10), (0x36, 0x20, 0x0b)}
LIMB = lambda x, y: (y <= 2 and x >= 6) or (x <= 3 and y >= 2)
for st in ['standby', 'pulling_0', 'pulling_1', 'pulling_2', 'arrow']:
    c = load('crossbow_%s.png' % st)
    recolor(c, VERD, sel=lambda q: q[:3] in WOODC, region=LIMB)
    recolor(c, BRASS, sel=lambda q: q[:3] in WOODC, region=lambda x, y: not LIMB(x, y))
    # gear drum on the stock, just behind the limbs
    c.ascii(['tat', 'aca', 'tat'], {'a': '#c3c9ce', 't': '#5d656c', 'c': '#2a2622'}, 7, 7)
    p = os.path.join(RES, 'textures/item/clockwork_repeater_%s.png' % st); c.save(p); OUT.append(p)
for st in ['pulling_0', 'pulling_1', 'pulling_2', 'arrow']:
    jw('models/item/clockwork_repeater_%s.json' % st, {'parent': 'minecraft:item/crossbow', 'textures': {'layer0': 'elsewhere:item/clockwork_repeater_%s' % st}})
jw('models/item/clockwork_repeater.json', {'parent': 'minecraft:item/crossbow', 'textures': {'layer0': 'elsewhere:item/clockwork_repeater_standby'}})
m = lambda n: {'type': 'minecraft:model', 'model': 'elsewhere:item/' + n}
jw('items/clockwork_repeater.json', {'model': {
    'type': 'minecraft:condition', 'property': 'minecraft:component', 'predicate': 'minecraft:custom_data', 'value': '{loaded:3}',
    'on_true': m('clockwork_repeater_arrow'),
    'on_false': {'type': 'minecraft:condition', 'property': 'minecraft:using_item',
                 'on_false': m('clockwork_repeater'),
                 'on_true': {'type': 'minecraft:range_dispatch', 'property': 'minecraft:use_duration', 'scale': 0.0125,
                             'entries': [{'threshold': 0.4, 'model': m('clockwork_repeater_pulling_1')}, {'threshold': 0.8, 'model': m('clockwork_repeater_pulling_2')}],
                             'fallback': m('clockwork_repeater_pulling_0')}}}})
print('\n'.join(OUT))
