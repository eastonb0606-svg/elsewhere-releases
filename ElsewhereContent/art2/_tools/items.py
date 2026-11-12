import os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from px import Spr, load, recolor, diag, hx, lum, sat, is_grey

ART = sys.argv[1]
OUT = []


def save(spr, module, rel):
    p = os.path.join(ART, module, 'src/main/resources/assets/elsewhere/textures', rel)
    spr.save(p)
    OUT.append(p)


# ---------------------------------------------------------------- palettes (dark -> light)
GOLD = ['#5c3a0e', '#a46a15', '#d9a42a', '#f7d34a', '#fff5a8']
ZEPH = ['#1d4a59', '#3a8ca3', '#79c9dc', '#bdeaf3', '#f4feff']
SKY = ['#283240', '#4f6278', '#7f97b0', '#b4c8dc', '#e8f0f8']
AMBER = ['#3d1c06', '#7a3a0c', '#c4661a', '#ec9a2a', '#ffc95a', '#fff0b0']
WOOD = ['#2e1c0c', '#4a2f14', '#6e4a22', '#93683a']

# ================================================================ Phase 1b (petalwind_glasswaste)
# --- Gale Pinion: a long flight feather, zephyrite vane, gold quill
def pinion():
    s = Spr()
    A = {**{k: 0 for k in range(-20, -5)}, -5: 2, -4: 2, -3: 2, -2: 3, -1: 3, 0: 3, 1: 3, 2: 4, 3: 4, 4: 4, 5: 4, 6: 4, 7: 3, 8: 3, 9: 2, 10: 1, 11: 1}
    B = {**{k: 0 for k in range(-20, -6)}, -6: 1, -5: 1, -4: 1, -3: 2, -2: 2, -1: 2, 0: 2, 1: 3, 2: 3, 3: 3, 4: 3, 5: 3, 6: 3, 7: 2, 8: 2, 9: 1, 10: 1, 11: 0}
    notch = {(0, -3), (1, -3), (4, 3), (-3, -2)}

    def f(sv, u):
        if sv > 12 or sv < -6:
            return None
        if (sv, u) in notch:
            return None
        if u == 0 and (sv + 15) % 2 == 0:
            return '#fffbe6' if sv > -5 else None
        a, b = A.get(sv, 0), B.get(sv, 0)
        if -a <= u < 0:
            return ['#f4feff', '#f4feff', '#d2f2f8', '#bdeaf3', '#9ad8e6'][min(-u, 4)]
        if 0 < u <= b:
            return ['#f4feff', '#bdeaf3', '#86cfe0', '#5aaec4'][min(u, 3)]
        return None
    diag(s, f)
    s.px(14, 1, '#bdeaf3')
    s.outline('#1d4a59')
    for x, y, c in [(4, 11, '#f0d272'), (3, 12, '#d6a838'), (2, 13, '#a87a22'), (1, 14, '#6e4c12')]:
        s.px(x, y, c)
    return s


save(pinion(), 'petalwind_glasswaste', 'item/gale_pinion.png')

# --- Zephyr Wings: vanilla elytra in zephyrite whites with a petal-pink inner edge
z = load('elytra.png')
recolor(z, ['#1d4a59', '#3a8ca3', '#5fb4c8', '#86cfe0', '#a8e0ec', '#c8eef6', '#e6f9fc', '#ffffff'])
for (x, y) in [(5, 3), (10, 3), (5, 4), (10, 4)]:
    pass
save(z, 'petalwind_glasswaste', 'item/zephyr_wings.png')

# --- Blossom Seal Charm: gold-rimmed zephyrite seal with a skyblossom, hanging bail
def charm():
    s = Spr()
    P = {'1': GOLD[4], '2': GOLD[3], '3': GOLD[2], '4': GOLD[1],
         'a': '#f2fcff', 'b': '#c6ecf4', 'c': '#94d2e2',
         'w': '#ffe8f2', 'p': '#f6a6c8', 'q': '#d9679b', 'r': '#a8406f', 'y': '#ffe36b', 'Y': '#f0a830'}
    s.ascii([
        "................",
        "......2223......",
        ".....2....3.....",
        "......3..4......",
        ".....122223.....",
        "....12aaabc3....",
        "...12aawpabc3...",
        "...2awwpqpbc4...",
        "...2wppyYqpc4...",
        "...2apqYqrbc4...",
        "...3abpqrpcc4...",
        "...3abbqrcc44...",
        "....3bccccc4....",
        ".....344444.....",
        "................",
        "................",
    ], P)
    s.outline('#4e2a0a')
    return s


save(charm(), 'petalwind_glasswaste', 'item/blossom_seal_charm.png')

# --- Sunwright Mirror Key: gold key whose bow is a sunglass mirror
def mkey():
    s = Spr()
    P = {'1': GOLD[4], '2': GOLD[3], '3': GOLD[2], '4': GOLD[1], 'a': '#ffffff', 'b': '#fff4b8', 'c': '#ffd860', 'd': '#f0a838'}
    s.ascii([
        "................",
        "................",
        "......2223......",
        ".....21ab33.....",
        ".....2aabc4.....",
        ".....2abcc4.....",
        ".....3bccd4.....",
        "......3444......",
        "......2334......",
        ".......24.......",
        ".......24.......",
        ".......2333.....",
        ".......24.......",
        ".......2343.....",
        ".......34.......",
        "................",
    ], P)
    s.outline('#4e2a0a')
    return s


save(mkey(), 'petalwind_glasswaste', 'item/sunwright_mirror_key.png')

# --- Sunpit Lure: sandswimmer-scale spoon lure baited with rotten flesh, on a line
def lure():
    s = Spr()
    P = {'1': '#fff2cc', '2': '#f2d290', '3': '#d9a95c', '4': '#a8743a', 'e': '#e03a2a', 'r': '#8c3a2a', 'R': '#b8604a', 'g': '#6f8a3a'}
    s.ascii([
        "................",
        "................",
        "......1122......",
        ".....112223.....",
        ".....1e2223.....",
        ".....123233.....",
        ".....132323.....",
        ".....123234.....",
        "......1334......",
        "......RrRr......",
        ".....RrRrRr.....",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], P)
    for x, y, c in [(8, 12, '#d0d0d0'), (8, 13, '#a0a0a0'), (7, 14, '#a0a0a0'), (6, 13, '#d0d0d0')]:
        s.px(x, y, c)
    s.outline('#4a2a10')
    s.px(7, 0, '#d8d8d8')
    return s


save(lure(), 'petalwind_glasswaste', 'item/sunpit_lure.png')

# ================================================================ Phase 1c
# --- Climber upgrade template: vanilla upgrade template in Ironpeak slate with a skyiron arrow
t = load('netherite_upgrade_smithing_template.png')
recolor(t, ['#7f97b0', '#c4d6ea', '#f4f8ff'], sel=lambda c: c[2] > c[0])
recolor(t, ['#1c2230', '#2c3546', '#3e4a5e', '#4c5a70', '#5f6f86'], sel=lambda c: c[0] > c[2])
save(t, 'verdigris_ironpeak', 'item/climber_upgrade_smithing_template.png')


# --- Music discs: vanilla disc body, new label colours
def disc(ramp, module, name):
    d = load('music_disc_cat.png')
    recolor(d, ramp, sel=lambda c: sat(c) > 30)
    save(d, module, 'item/' + name + '.png')


disc(['#2a7f99', '#e8fbff'], 'petalwind_glasswaste', 'music_disc_updraft')
disc(['#c0601a', '#ffd23a'], 'petalwind_glasswaste', 'music_disc_zenith')
disc(['#e07a1c', '#efe8dc'], 'Elsewhere', 'music_disc_pale_light')

# ================================================================ arsenal lane
# --- Sporebloom Staff: dark oak shaft, spore-blossom head with a glowing core
def sporestaff():
    s = Spr()
    P = {'P': '#f7b6d6', 'p': '#e07aac', 'q': '#b04a80', 'y': '#e8ff9a', 'Y': '#a8e05a', 'g': '#6fae3c', 'G': '#3f7a24',
         '1': WOOD[3], '2': WOOD[2], '3': WOOD[1]}
    s.ascii([
        "................",
        "...........P....",
        "..........PpP...",
        ".........PpyYp..",
        "........PpyYYpq.",
        ".........pYYpq..",
        "........gGpqq...",
        ".......gG.q.....",
        "......12........",
        ".....12.........",
        "....12..........",
        "...12...........",
        "..12............",
        ".13.............",
        "................",
        "................",
    ], P)
    s.outline('#2a1020')
    return s


save(sporestaff(), 'summoner', 'item/sporebloom_staff.png')

# --- Gale Ribbon: gold-capped handle, a zephyr-white ribbon whip with a petal tip
def ribbon():
    s = Spr()
    P = {'w': '#f4feff', 'l': '#bdeaf3', 'm': '#79c9dc', 'k': '#f6a6c8', 'K': '#d9679b', '1': GOLD[3], '2': GOLD[2], 'h': WOOD[2], 'H': WOOD[1]}
    s.ascii([
        "................",
        "..........kK....",
        ".........wl.....",
        "........wm......",
        "........lm......",
        ".........wl.....",
        "..........lm....",
        "..........lm....",
        ".........wm.....",
        ".......wlm......",
        "......1.........",
        ".....hH.........",
        "....hH..........",
        "...hH...........",
        "..12............",
        "................",
    ], P)
    s.outline('#1d3f4c')
    return s


save(ribbon(), 'summoner', 'item/gale_ribbon.png')

# --- Mirrorglass Prism: triangular sunglass prism on a gold grip, a small rainbow on the far face
def prism():
    s = Spr()
    P = {'w': '#ffffff', 'a': '#fff8d8', 'b': '#f4e6a8', 'c': '#d8e8ee', 'd': '#a8c8d4',
         'R': '#ff6a5a', 'O': '#ffb03a', 'Y': '#fff04a', 'G': '#7ae05a', 'B': '#5ab0ff',
         '1': GOLD[4], '2': GOLD[3], '3': GOLD[2], '4': GOLD[1]}
    s.ascii([
        "................",
        "................",
        ".......wc.......",
        ".......wc.......",
        "......wacd......",
        "......waRd......",
        ".....waaOcd.....",
        ".....wabYcd.....",
        "....waabGccd....",
        "....wabbBccd....",
        "....12222334....",
        ".......23.......",
        ".......24.......",
        ".......24.......",
        "......2334......",
        "................",
    ], P)
    s.outline('#4e3a1a')
    return s


save(prism(), 'arsenal', 'item/mirrorglass_prism.png')

# --- Hushward Bell: vanilla bell recast in deepslate with sculk-teal rim, handle instead of the beam
def hbell():
    s = Spr()
    P = {'h': WOOD[3], 'H': WOOD[1], 'g': '#7f8a99', 'G': '#4c5666',
         '1': '#6a7480', '2': '#4a525e', '3': '#343a44', '4': '#24282f', 't': '#3ad6cc', 'T': '#1f8f8f', 'r': '#9ff6ec'}
    s.ascii([
        "................",
        ".......hH.......",
        ".......hH.......",
        "......gGGG......",
        "......1223......",
        ".....112233.....",
        ".....12t233.....",
        ".....1tTt33.....",
        ".....12t234.....",
        "....11222334....",
        "....12222334....",
        "...rrrttttTTT...",
        "......3443......",
        "................",
        "................",
        "................",
    ], P)
    s.outline('#0e1418')
    return s


b = hbell()
save(b, 'arsenal', 'item/hushward_bell.png')

# ================================================================ gear2 lane
# --- Galvanic Coilmail: vanilla chainmail recast as copper coils with verdigris in the gaps
COIL = ['#163a36', '#2f7a6a', '#a8492c', '#d86a3e', '#f0965c', '#ffc69a', '#fff0e0']
for piece in ['helmet', 'chestplate', 'leggings', 'boots']:
    c = load('chainmail_%s.png' % piece)
    recolor(c, COIL)
    save(c, 'gear2', 'item/galvanic_coilmail_%s.png' % piece)
# --- Amberstill Plate: vanilla diamond plate cast in amber
AMB = ['#3d1c06', '#7a3a0c', '#b4581a', '#e08a26', '#ffbe4a', '#ffe9a0', '#ffffff']
for piece in ['helmet', 'chestplate', 'leggings', 'boots']:
    c = load('diamond_%s.png' % piece)
    recolor(c, AMB)
    save(c, 'gear2', 'item/amberstill_%s.png' % piece)

# armour layers (humanoid equipment model)
for kind, d in [('humanoid', 'eq/h'), ('humanoid_leggings', 'eq/l')]:
    c = load(d + '/chainmail.png')
    recolor(c, COIL[:6])
    save(c, 'gear2', 'entity/equipment/%s/galvanic_coilmail.png' % kind)
    c = load(d + '/diamond.png')
    recolor(c, AMB)
    save(c, 'gear2', 'entity/equipment/%s/amberstill.png' % kind)


# --- Ironclad Flail: skyiron spiked ball on a short chain, dark grip
def flail():
    s = Spr()
    P = {'1': SKY[4], '2': SKY[3], '3': SKY[2], '4': SKY[1], 'k': '#dfe8f2', 'h': WOOD[2], 'H': WOOD[1], 'g': GOLD[2]}
    s.ascii([
        "................",
        "...........k....",
        ".........1122...",
        "........112223..",
        ".......k122233k.",
        "........222334..",
        "........233344..",
        ".........3444...",
        "...........k....",
        "................",
        "................",
        "....g...........",
        "...hH...........",
        "..hH............",
        ".gH.............",
        "................",
    ], P)
    s.outline('#1c222c')
    for x, y, c in [(8, 8, '#b4c8dc'), (7, 9, '#5d6f84'), (6, 10, '#b4c8dc'), (5, 11, '#5d6f84')]:
        s.px(x, y, c)
    return s


save(flail(), 'gear2', 'item/ironclad_flail.png')


# --- Gale Rapier: needle blade of zephyr steel, gold swept guard
def rapier():
    s = Spr()

    def f(sv, u):
        if 0 <= sv <= 13 and u == 0 and sv % 2 == 1:
            return '#f4feff' if sv > 3 else '#d6eef4'
        if 0 <= sv <= 9 and u == 1 and sv % 2 == 0:
            return '#9ad0de'
        return None
    diag(s, f)
    s.ascii([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......2.........",
        ".....2..........",
        "....2.3.........",
        "...2..34........",
        "....hH4.........",
        "...hH...........",
        "..13............",
        "................",
        "................",
    ], {'2': GOLD[3], '3': GOLD[2], '4': GOLD[1], '1': GOLD[4], 'h': WOOD[2], 'H': WOOD[1]})
    s.outline('#1d3440')
    return s


save(rapier(), 'gear2', 'item/gale_rapier.png')

# --- Mirrorbolt Arbalest: vanilla crossbow states, wood in Glasswaste bronze, metal as polished mirror
BRONZE = ['#24160a', '#3c2610', '#5a3a14', '#7a5220', '#9c6c2c', '#c08a3a', '#e0b45a']
MIRROR = ['#5a7f96', '#a8cfe0', '#e8f8ff', '#ffffff']
WOODC = {(0x4d, 0x2e, 0x11), (0x2a, 0x1a, 0x09), (0x6d, 0x42, 0x18), (0x90, 0x58, 0x1f), (0xb5, 0x73, 0x2f), (0x3b, 0x24, 0x0d), (0x1f, 0x12, 0x06), (0x48, 0x2b, 0x10), (0x36, 0x20, 0x0b)}
LIMB = lambda x, y: (y <= 2 and x >= 6) or (x <= 3 and y >= 2)
for st in ['standby', 'pulling_0', 'pulling_1', 'pulling_2', 'arrow']:
    c = load('crossbow_%s.png' % st)
    recolor(c, ['#1c2a36', '#2f4558', '#5a7f96', '#8fb4c8', '#c8e6f2', '#f4fcff'], sel=lambda c: c[:3] in WOODC, region=LIMB)
    recolor(c, BRONZE, sel=lambda c: c[:3] in WOODC, region=lambda x, y: not LIMB(x, y))
    save(c, 'gear2', 'item/mirrorbolt_arbalest_%s.png' % st)


# --- Steam Auger: skyiron drill bit, brass boiler body, dark grip
def auger():
    s = Spr()

    def f(sv, u):
        if 5 <= sv <= 13:
            w = {5: 2, 6: 2, 7: 2, 8: 1, 9: 1, 10: 1, 11: 0, 12: 0, 13: 0}[sv]
            if abs(u) <= w:
                band = (sv - u) % 4 in (0, 1)
                if u < 0:
                    return '#e8f0f8' if band else '#b4c8dc'
                if u == 0:
                    return '#b4c8dc' if band else '#7f97b0'
                return '#7f97b0' if band else '#4f6278'
            return None
        if 3 <= sv <= 4 and abs(u) <= 3:
            return ['#4f6278', '#7f97b0', '#b4c8dc', '#7f97b0', '#4f6278', '#3a4a5c', '#3a4a5c'][u + 3]
        if -5 <= sv <= 2 and abs(u) <= 3:
            return ['#fff0a0', '#f7d34a', '#e0a83a', '#c4842a', '#a46a15', '#7a4a10', '#7a4a10'][u + 3]
        if -11 <= sv <= -6 and abs(u) <= 1:
            return ['#6e4a22', '#4a2f14', '#2e1c0c'][u + 1]
        return None
    diag(s, f)
    s.outline('#1e1408')
    s.px(5, 5, '#c4842a'); s.px(5, 4, '#f7d34a'); s.px(4, 4, '#a46a15')  # steam valve
    s.outline('#1e1408')
    return s


save(auger(), 'gear2', 'item/steam_auger.png')

# --- Termite Hive Lantern: vanilla lantern, the flame replaced by a glowing amber comb with termites
l = load('lantern.png')
recolor(l, ['#6a2c08', '#b4581a', '#f08a2a', '#ffc04a', '#ffe08a', '#fff4c0'], sel=lambda c: sat(c) > 40 and c[0] > 180)
for (x, y) in [(7, 9), (8, 11)]:
    if l.get(x, y)[3]:
        l.px(x, y, '#3a1c08')
save(l, 'gear2', 'item/termite_hive_lantern.png')


# --- Seer's Hourglass: gold caps, glass bulbs, moon-pale sand
def hourglass():
    s = Spr()
    P = {'1': GOLD[4], '2': GOLD[3], '3': GOLD[2], '4': GOLD[1],
         'w': '#ffffff', 'l': '#d8eef8', 'm': '#9cc4d8', 'S': '#d4dcff', 's': '#9aa8e8'}
    s.ascii([
        "................",
        "...1222222223...",
        "...3333333344...",
        "....wllllllm....",
        "....wSSSSSsm....",
        ".....wSSSsm.....",
        "......wSsm......",
        ".......Ss.......",
        ".......lS.......",
        "......wlSm......",
        ".....wllSlm.....",
        "....wlSSSSsm....",
        "....wSSSSSss....",
        "...1222222223...",
        "...3333333344...",
        "................",
    ], P)
    s.outline('#3a2608')
    return s


save(hourglass(), 'gear2', 'item/seers_hourglass.png')


# --- Prospector's Pendulum: cord, brass cap, green dowsing crystal
def pendulum():
    s = Spr()
    P = {'1': GOLD[4], '2': GOLD[3], '3': GOLD[2], '4': GOLD[1],
         'a': '#e6fff4', 'b': '#8ff0cc', 'c': '#3fbf96', 'd': '#22806a'}
    s.ascii([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......1223......",
        "......3334......",
        ".....abbbcc.....",
        ".....abbccd.....",
        "......bbccd.....",
        "......bccd......",
        ".......cd.......",
        ".......d........",
        "................",
        "................",
    ], P)
    s.outline('#16302a')
    for y in range(0, 5):
        s.px(7, y, '#c9b089' if y % 2 else '#a88c64')
    s.px(8, 5, '#a88c64'); s.px(6, 5, '#a88c64'); s.px(7, 5, '#5a4630')
    return s


save(pendulum(), 'gear2', 'item/prospectors_pendulum.png')


# --- Void Tether Spool: end-stone flanges, void thread, loose end with an anchor bead
def spool():
    s = Spr()
    P = {'1': '#fbfdd8', '2': '#e3e6aa', '3': '#c2c48a', '4': '#9a9a66',
         'v': '#2a1640', 'V': '#45246a', 'p': '#6a3aa0', 'P': '#9a5ad8', 'k': '#e0a8ff'}
    s.ascii([
        "................",
        "................",
        "...1222222223...",
        "...3333333334...",
        "....kPPPPpVv....",
        "....PppppVVv....",
        "....kPPPPpVv....",
        "....PppppVVv....",
        "....kPPPPpVv....",
        "....PppppVVv....",
        "....kPPPPpVv....",
        "...1222222223...",
        "...3333333334...",
        "................",
        "................",
        "................",
    ], P)
    s.outline('#1c1428')
    for x, y, c in [(12, 9, '#9a5ad8'), (13, 10, '#6a3aa0'), (13, 11, '#9a5ad8'), (14, 12, '#6a3aa0'), (14, 13, '#e0a8ff'), (13, 14, '#9a5ad8')]:
        s.px(x, y, c)
    return s


save(spool(), 'gear2', 'item/void_tether_spool.png')

print('\n'.join(OUT))

# ================================================================ gear2 extras (registered in Gear2Items)
t = load('netherite_upgrade_smithing_template.png')
GLYPH = {(x, y) for y in range(16) for x in range(16) if t.get(x, y)[3] and t.get(x, y)[2] > t.get(x, y)[0]}
recolor(t, ['#a8492c', '#f0965c', '#ffd0a8'], region=lambda x, y: (x, y) in GLYPH)
recolor(t, ['#123430', '#1e4a44', '#2a5e56', '#357066', '#43867a'], region=lambda x, y: (x, y) not in GLYPH)
save(t, 'gear2', 'item/galvanic_upgrade_smithing_template.png')


def mcore():
    s = Spr()
    P = {'1': GOLD[4], '2': GOLD[3], '3': GOLD[2], '4': GOLD[1],
         'w': '#ffffff', 'a': '#fffbe0', 'b': '#fff0a0', 'c': '#ffd040', 'd': '#f0a020'}
    s.ascii([
        "................",
        "................",
        "................",
        "......1223......",
        "....12abbc34....",
        "...2aawbbbcc4...",
        "...2awwbbbcd4...",
        "...2abbbbccd4...",
        "...3bbbbcccd4...",
        "...3bbcccccd4...",
        "....3cccdd44....",
        "......3444......",
        "................",
        "................",
        "................",
        "................",
    ], P)
    s.outline('#4e2a0a')
    return s


save(mcore(), 'gear2', 'item/heliodor_mirror_core.png')

ch = load('iron_chain.png')
recolor(ch, SKY)
save(ch, 'gear2', 'item/ironclad_chain.png')


def abit():
    s = Spr()

    def f(sv, u):
        if -5 <= sv <= -3 and abs(u) <= 2:
            return ['#4f6278', '#7f97b0', '#b4c8dc', '#7f97b0', '#3a4a5c'][u + 2]
        if -2 <= sv <= 12:
            w = 3 if sv <= 0 else 2 if sv <= 4 else 1 if sv <= 8 else 0
            if abs(u) <= w:
                band = (sv - u) % 4 in (0, 1)
                if u < 0:
                    return '#e8f0f8' if band else '#b4c8dc'
                if u == 0:
                    return '#b4c8dc' if band else '#7f97b0'
                return '#7f97b0' if band else '#4f6278'
        return None
    diag(s, f)
    s.outline('#1c222c')
    return s


save(abit(), 'gear2', 'item/steam_auger_bit.png')


def queen():
    s = Spr()
    P = {'h': '#4a2e16', 'm': '#2a1808', 't': '#6e4a22', 'l': '#4a2f14',
         '1': '#fff0c8', '2': '#f2d290', '3': '#d9a95c', '4': '#b4783a', '5': '#c88a48'}
    s.ascii([
        "................",
        "......m..m......",
        "......hhhh......",
        "......hhhh......",
        ".....l.tt.l.....",
        "....l..tt..l....",
        "......1223......",
        ".....122233.....",
        ".....455554.....",
        ".....122233.....",
        ".....455554.....",
        ".....122233.....",
        "......4555......",
        ".......23.......",
        "................",
        "................",
    ], P)
    s.outline('#2a1808')
    return s


save(queen(), 'gear2', 'item/termite_queen.png')
