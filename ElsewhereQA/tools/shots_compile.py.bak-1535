#!/usr/bin/env python3
"""Compile ElsewhereContent/SHOTS-3.0.md into a machine-readable shot list for the one-session client shot runner.

Usage: python3 -I ElsewhereQA/tools/shots_compile.py [SHOTS.md] [out-dir]
Writes <out-dir>/shotlist.json (scenes with normalised steps) and <out-dir>/index.json (lane -> item -> shots, status
"pending"; the runner rewrites it with ok/fail as it goes). Default out-dir: ElsewhereQA/shots-3.0.

Each SHOTS line is `lane | what | scene setup | what to check`. The setup is free text; it is normalised into steps from a
small vocabulary the runner executes (ShotRunner.java): reset, cmd, tp (abs/anchor), locate, summon, view, camera
(first/front/back), gui (F1), f3, chat, subtitles, wait, chunks, damage, hurt_to, kill, equip, wear_set, hold, inventory,
close, use_down/use_up, attack, client_set, player_cmd, wait_label, wait_label_clear, look_target, fly, mannequin, shot,
logcheck. Lines no family matches fall back on the id they name (entity -> stage view, item -> held + tooltip, structure ->
castle camera); a line with no usable id becomes a `manual` scene, which the runner reports instead of skipping silently.
"""
import json
import math
import os
import re
import sys
import time
import glob
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "ElsewhereContent", "SHOTS-3.0.md")
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "ElsewhereQA", "shots-3.0")

ID = re.compile(r"elsewhere:([a-z0-9_/]+)")
VIEW = re.compile(r"C\+\(\s*(-/\+|[+-]?)([\d.]+)\s*,\s*\+?(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*\)\s*(?:yaw\s*(-?[\d.]+)\s*pitch\s*(-?[\d.]+))?")
FACTIONS = ("aldermark", "hrafnholt", "azmar", "mirewick")


def slugify(s, n=48):
    s = re.sub(r"elsewhere:", "", s.lower())
    s = re.sub(r"[^a-z0-9]+", "_", s).strip("_")
    return s[:n].strip("_") or "shot"


def reset(mode="creative", gui=True, time_of_day=6000):
    return [["reset", mode], ["cmd", "time set %d" % time_of_day], ["cmd", "weather clear"], ["gui", gui]]


def shot(slug):
    return ["shot", slug]


def views_of(text):
    """All C+(dx, dy, dz) yaw Y pitch P views in a setup; `-/+88.5` expands to both sides; a view without yaw/pitch
    inherits the next one that has them (the "(west town; east town: C+(88, +26, 52))" form)."""
    out = []
    for m in VIEW.finditer(text):
        sign, dx, dy, dz, yaw, pitch = m.groups()
        xs = [-float(dx), float(dx)] if sign == "-/+" else [float(dx) * (-1 if sign == "-" else 1)]
        for x in xs:
            out.append([x, float(dy), float(dz), None if yaw is None else float(yaw), None if pitch is None else float(pitch)])
    last = None
    for v in reversed(out):
        if v[3] is None and last is not None:
            v[3], v[4] = last
        elif v[3] is not None:
            last = (v[3], v[4])
    return [v for v in out if v[3] is not None]


SVIEW = re.compile(r"(?:S|kiosk)\+\(\s*(-?[\d.]+)\s*,\s*\+?(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*\)\s*(?:yaw\s*(-?[\d.]+)\s*pitch\s*(-?[\d.]+)|facing (?:S|it),?\s*(?:pitch\s*(-?[\d.]+))?|facing (north|south|east|west))?")
DOWNHILL = re.compile(r"downhill side(?: (\d+) out)?")
FACING = {"north": 180.0, "south": 0.0, "east": -90.0, "west": 90.0}


def s_steps(setup):
    """Views around S in the order the setup gives them: tp S+(..) (yaw/pitch, facing S, facing north) and downhill-side views."""
    found = []
    for m in SVIEW.finditer(setup):
        dx, dy, dz, yaw, pitch, fpitch, card = m.groups()
        dx, dy, dz = float(dx), float(dy), float(dz)
        if m.group(0).startswith("kiosk"):
            dx += 16.0   # "kiosk at pyramid centre +16 east"
        if yaw is not None:
            y, p = float(yaw), float(pitch)
        elif card:
            y, p = FACING[card], 0.0
        else:
            ox, oz = dx - (16.0 if m.group(0).startswith("kiosk") else 0.0), dz
            y = math.degrees(math.atan2(-oz, -ox)) - 90.0 if (ox or oz) else 0.0
            p = float(fpitch) if fpitch else 15.0
        found.append((m.start(), ["tp", "anchor", dx, dy, dz, round(y, 1), p], "s%+d_%+d_%+d" % (round(dx), round(dy), round(dz))))
    for m in DOWNHILL.finditer(setup):
        found.append((m.start(), ["tp_downhill", float(m.group(1) or 20)], "downhill_%s" % (m.group(1) or "20")))
    return [(st, slug) for _, st, slug in sorted(found, key=lambda t: t[0])]


def structures_of(what):
    ids = ["elsewhere:" + i for i in ID.findall(what) if i.startswith(("castle_", "waterfront_"))]
    if not ids:
        for f in FACTIONS:
            if "castle_" + f in what:
                ids.append("elsewhere:castle_" + f)
    if not ids and re.search(r"any castle", what):
        ids = ["elsewhere:castle_aldermark"]
    seen = []
    for i in ids:
        if i not in seen:
            seen.append(i)
    return seen


def view_slug(v):
    x, y, z, yaw, pitch = v
    return "c%+d_%+d_%+d_y%d" % (round(x), round(y), round(z), round(yaw))


def load_registry():
    """Known elsewhere ids by kind and display name -> id, from the newest merged jar's en_us.json and structure data
    (SHOTS_JAR overrides). Lines that name things by display name or bare id resolve through this."""
    jars = sorted(glob.glob(os.path.join(ROOT, "ElsewhereBeta", "dist", "elsewhere-*.jar")), key=os.path.getmtime)
    jar = os.environ.get("SHOTS_JAR") or (jars[-1] if jars else None)
    kinds, names = {}, {}
    if not jar:
        return kinds, names
    try:
        z = zipfile.ZipFile(jar)
        lang = json.loads(z.read("assets/elsewhere/lang/en_us.json"))
        for k, v in lang.items():
            m = re.match(r"(item|entity|block)\.elsewhere\.([a-z0-9_]+)$", k)
            if m:
                kinds.setdefault(m.group(2), m.group(1) if m.group(1) != "block" else "item")
                if isinstance(v, str) and len(v) >= 5:
                    names.setdefault(v.lower(), m.group(2))
        for n in z.namelist():
            m = re.match(r"data/elsewhere/worldgen/structure/([a-z0-9_]+)\.json$", n)
            if m:
                kinds[m.group(1)] = "structure"
    except Exception as e:
        print("registry: could not read %s: %s" % (jar, e))
    # content newer than the jar: the module sources' lang files and structure data
    for lp in glob.glob(os.path.join(ROOT, "ElsewhereContent", "*", "src", "*", "resources", "assets", "elsewhere", "lang", "en_us.json")):
        try:
            lang = json.load(open(lp, encoding="utf-8"))
        except Exception:
            continue
        for k, v in lang.items():
            m = re.match(r"(item|entity|block)\.elsewhere\.([a-z0-9_]+)$", k)
            if m:
                kinds.setdefault(m.group(2), m.group(1) if m.group(1) != "block" else "item")
                if isinstance(v, str) and len(v) >= 5:
                    names.setdefault(v.lower(), m.group(2))
    for sp in glob.glob(os.path.join(ROOT, "ElsewhereContent", "*", "src", "main", "resources", "data", "elsewhere", "worldgen", "structure", "*.json")):
        kinds[os.path.basename(sp)[:-5]] = "structure"
    return kinds, names


KINDS, NAMES = load_registry()


def resolve_ids(text):
    """elsewhere:ids, bare known ids and display names in the text, in order of appearance."""
    hits = []
    for m in ID.finditer(text):
        hits.append((m.start(), m.group(1)))
    for m in re.finditer(r"\b([a-z][a-z0-9]*(?:_[a-z0-9]+)+)\b", text):
        if m.group(1) in KINDS:
            hits.append((m.start(), m.group(1)))
    low = text.lower()
    for name in sorted(NAMES, key=len, reverse=True):
        i = low.find(name)
        if i >= 0 and re.match(r"\W|$", low[i + len(name):i + len(name) + 1] or " ") and (i == 0 or not low[i - 1].isalnum()):
            hits.append((i, NAMES[name]))
    out = []
    for _, h in sorted(hits):
        if h not in out:
            out.append(h)
    return out


class Compiler:
    def __init__(self):
        self.scenes = []
        self.slugs = set()
        self.prev = {}          # keyword -> views of the last line that had them
        self.last_views = None

    def unique(self, lane, slug):
        s, i = slug, 2
        while (lane, s) in self.slugs:
            s = "%s_%d" % (slug, i)
            i += 1
        self.slugs.add((lane, s))
        return s

    def add(self, lineno, lane, item, world, steps, check, setup, note=None):
        shots = []
        for st in steps:
            if st[0] in ("shot", "logcheck"):
                st[1] = self.unique(lane, slugify(st[1], 60))
                shots.append({"slug": st[1], "file": "%s__%s.%s" % (lane, st[1], "png" if st[0] == "shot" else "txt")})
        sc = {"n": len(self.scenes) + 1, "line": lineno, "lane": lane, "item": item, "world": world, "steps": steps,
              "shots": shots, "check": check, "setup": setup}
        if note:
            sc["note"] = note
        self.scenes.append(sc)

    # ------------------------------------------------------------------ families
    def structure_views(self, lineno, lane, what, setup, check, views, note=None):
        structs = structures_of(what) or ["elsewhere:castle_aldermark"]
        for st in structs:
            sid = st.split(":")[1]
            steps = reset("spectator", gui=False) + [["locate", st]]
            for v in views:
                steps += [["tp", "anchor", v[0], v[1], v[2], v[3], v[4]], ["chunks"], ["wait", 40], shot(sid + "_" + view_slug(v))]
            self.add(lineno, lane, st if len(structs) == 1 else "%s (%s)" % (what.strip(), st), "normal", steps, check, setup, note)

    def compile_line(self, lineno, lane, what, setup, check):
        lane_s = slugify(lane, 24)
        ids = ID.findall(what)
        first = "elsewhere:" + ids[0] if ids else None
        low = (what + " " + setup).lower()
        views = views_of(setup) or (views_of(what) if "C+(" in what else [])

        # sfx: grep the client log (no scene)
        if "latest.log" in (what + setup) and "grep" in setup:
            self.add(lineno, lane_s, what.strip(), "none",
                     [["logcheck", "client_log_sfx3", "sfx3", "does not exist|Missing sound|Unable to play|Failed to open|OGG"]], check, setup)
            return
        # structure / generation views around a located castle
        if views:
            self.last_views = views
            if "quay" in what:
                self.prev["quay"] = views
            note = "nearest located instance (the runner cannot pick the 'most sloped of 4 sites')" if "most sloped" in setup else None
            self.structure_views(lineno, lane_s, what, setup, check, views, note)
            return
        if setup.strip().lower().startswith("same views") and ("quay" in setup or self.last_views):
            base = list(self.prev.get("quay") or self.last_views)
            extra = views_of(setup)
            for st in structures_of(what):
                self.structure_views(lineno, lane_s, st, setup, check, base if "mirewick" in st else (extra or base))
            # the Azmar part of the z-castle line has its own explicit view
            m = re.search(r"Azmar:\s*tp\s*(C\+\([^)]*\)\s*yaw\s*-?\d+\s*pitch\s*-?\d+)", setup)
            if m and "elsewhere:castle_azmar" not in structures_of(what):
                self.structure_views(lineno, lane_s, "elsewhere:castle_azmar", setup, check, views_of(m.group(1)))
            return
        # P4 3.0 rival anims: "<id> wind-up: <Move>" then "<id> strike: <Move>" (same scene, merged into the wind-up scene)
        mw = re.search(r"(wind-up|strike): (.+?)(?: \(phase 2\+\))? \(([a-z ]+)\)\s*$", what) or re.search(r"(wind-up|strike): (.+?)(?: via .*)?$", what)
        if first and mw and ("boss bar shows" in setup or setup.startswith("same scene")):
            tid = first.split(":")[1]
            move = mw.group(2).split(" via ")[0].strip()
            key = ("p4", first, move)
            if mw.group(1) == "strike" and key in self.prev:
                sc = self.prev[key]
                extra = [["wait_label_clear", 300], ["wait", 1], ["look_target"], shot(tid + "_" + slugify(move) + "_strike")]
                for st in extra:
                    if st[0] == "shot":
                        st[1] = self.unique(sc["lane"], slugify(st[1], 60))
                        sc["shots"].append({"slug": st[1], "file": "%s__%s.png" % (sc["lane"], st[1])})
                sc["steps"] += extra
                sc["check"] += " || strike: " + check
                return
            ml = re.search(r"boss bar shows ' · ([^']+)'", setup)
            want = ml.group(1) if ml else move
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["cmd", "effect give @s regeneration infinite 255 true"],
                                         ["summon", first, "{PersistenceRequired:1b}"]]
            if "phase 2+" in what:
                steps.append(["hurt_to", 0.48])
            steps += [["view", 0.0, 4.0, 35.0], ["chunks"], ["wait_label", 1800, want], ["wait", 6], ["look_target"], shot(tid + "_" + slugify(move) + "_windup")]
            self.add(lineno, lane_s, "%s %s" % (first, move), "flat", steps, check, setup)
            self.prev[key] = self.scenes[-1]
            return
        if "phase aura" in what:
            ids2 = ["elsewhere:" + i for i in ids] or self.prev.get("aura_ids", [])
            self.prev["aura_ids"] = ids2
            frac = 0.18 if "phase 3" in what else 0.58
            for i in ids2:
                steps = reset("creative", time_of_day=18000) + [["summon", i, "{PersistenceRequired:1b}"], ["hurt_to", frac], ["view", 0.0, 4.0, 30.0], ["chunks"], ["wait", 30],
                                                               ["look_target"], shot(i.split(":")[1] + ("_aura_phase3" if frac < 0.3 else "_aura_phase2"))]
                self.add(lineno, lane_s, "%s (%s)" % (i, "phase 3 swirl" if frac < 0.3 else "phase 2 glyphs"), "flat", steps, check, setup, "night, %d%% HP" % round(frac * 100))
            return
        if first and re.search(r"in a row|same row setup", setup):
            n = 4 if "one of each" in setup else (12 if "12 in two rows" in setup else 6)
            steps = reset("creative")
            for k in range(n):
                steps.append(["summon", first, "{NoAI:1b,PersistenceRequired:1b}", (k % 6) * 2 - 5, 6 + (k // 6) * 2])
            steps += [["tp", "stage", 0, 0, 1, 0, 5], ["chunks"], ["wait", 20], shot(slugify(what, 40) + "_front"),
                      ["tp", "stage", 0, 0, 13, 180, 5], ["wait", 10], shot(slugify(what, 40) + "_back")]
            self.add(lineno, lane_s, what.strip(), "flat", steps, check, setup, "plain summons in a row (role/variant set by the mod's own spawn rules, not by Townsfolk.setup)")
            return
        if "Echo Arrow" in what:
            if "icon" in what:
                steps = reset("survival") + [["cmd", "item replace entity @s hotbar.0 with minecraft:arrow"], ["cmd", "item replace entity @s hotbar.1 with minecraft:spectral_arrow"],
                                             ["cmd", "item replace entity @s hotbar.2 with elsewhere:echo_arrow"], ["inventory", "elsewhere:echo_arrow"], ["wait", 5], shot("echo_arrow_icon_tooltip"), ["close"]]
            elif "flight" in what:
                steps = reset("survival") + [["cmd", "fill ~-4 ~ ~12 ~4 ~5 ~12 minecraft:stone"], ["cmd", "item replace entity @s weapon.mainhand with minecraft:bow"],
                                             ["cmd", "item replace entity @s hotbar.8 with elsewhere:echo_arrow 16"], ["tp", "stage", 0, 0, 0, 0, -2], ["camera", "back"],
                                             ["use_down"], ["wait", 25], ["use_up"], ["wait", 3], shot("echo_arrow_in_flight"), ["wait", 30], ["camera", "first"],
                                             ["tp", "stage", 1, 0, 9.5, 0, 10], ["wait", 5], shot("echo_arrow_stuck_2_blocks")]
            else:
                steps = reset("survival", time_of_day=18000) + [["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}", 0, 24],
                                                                ["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}", 0, 19],
                                                                ["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}", 0, 15],
                                                                ["cmd", "item replace entity @s weapon.mainhand with minecraft:bow"], ["cmd", "item replace entity @s hotbar.8 with elsewhere:echo_arrow 16"],
                                                                ["tp", "stage", 0, 0, 0, 0, 0], ["look_target"], ["use_down"], ["wait", 25], ["use_up"], ["wait", 12],
                                                                ["cmd", "tp @s ~ ~8 ~-10 0 35"], ["wait", 3], shot("echo_arrow_pulse_glow")]
            self.add(lineno, lane_s, "elsewhere:echo_arrow (" + slugify(what, 30) + ")", "flat", steps, check, setup)
            return
        if first and first.endswith("lantern_road"):
            if "night" in what:
                steps = reset("creative", time_of_day=18000) + [["locate", first], ["tp", "anchor", 0, 2, 0, 0, 5], ["chunks"], ["wait", 400], shot("lantern_road_procession_night")]
                self.add(lineno, lane_s, first + " (night procession)", "normal", steps, check, setup, "waits 20 s on the road for a procession; none is forced")
            else:
                steps = reset("spectator", gui=False) + [["locate", first], ["tp", "anchor", 0, 14, 0, 0, 50], ["chunks"], ["wait", 40], shot("lantern_road_above"),
                                                         ["tp", "anchor", 0, 2, -30, 0, 5], ["chunks"], ["wait", 40], shot("lantern_road_end_eye_level")]
                self.add(lineno, lane_s, first, "normal", steps, check, setup)
            return
        if "Return Rift ring" in what:
            steps = reset("spectator", gui=False) + [["anchor_from_log", r"Return Rift ring at \((-?\d+), (-?\d+), (-?\d+)\)", "elsewhere:pale_hollow"],
                                                     ["tp", "anchor", 8, 5, 8, 135, 25], ["chunks"], ["wait", 40], shot("return_rift_ring")]
            self.add(lineno, lane_s, what.strip(), "normal", steps, check, setup, "anchor from the core self-test log line")
            return
        # P8 death
        if first and "/kill" in setup:
            tid = first.split(":")[1]
            steps = reset("creative", gui=False) + [["summon", first, "{NoAI:1b,PersistenceRequired:1b}"], ["view", 1.6, 5.0, 35.0],
                                                    ["chunks"], ["wait", 20], ["kill"], ["wait", 15], shot(tid + "_death_15t")]
            self.add(lineno, lane_s, first, "flat", steps, check, setup)
            return
        # P8 hurt flinch
        if first and "/damage @e" in setup and "1 tick after" in setup:
            tid = first.split(":")[1]
            steps = reset("creative", gui=False) + [["summon", first, "{NoAI:1b,PersistenceRequired:1b}"], ["view", 1.6, 5.0, 35.0],
                                                    ["chunks"], ["wait", 20], ["damage", 1], ["wait", 1], shot(tid + "_hurt_peak"),
                                                    ["wait", 7], shot(tid + "_hurt_rest")]
            self.add(lineno, lane_s, first, "flat", steps, check, setup)
            return
        # P4 move animation (live boss, survival player 4 blocks in front)
        if first and "boss bar title shows" in setup:
            tid = first.split(":")[1]
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["cmd", "effect give @s regeneration infinite 255 true"],
                                         ["cmd", "effect give @s saturation infinite 255 true"], ["summon", first, "{PersistenceRequired:1b}"],
                                         ["view", 0.0, 4.0, 35.0], ["chunks"], ["wait_label", 900, ""], ["wait", 6], ["look_target"],
                                         shot(tid + "_move_windup"), ["wait_label_clear", 200], ["wait", 2], ["look_target"], shot(tid + "_move_strike")]
            self.add(lineno, lane_s, first, "flat", steps, check, setup)
            return
        # boss bar label letters (a human boss mid-move whose label has a lowercase f)
        if "boss bar label" in what.lower() or ("lowercase f" in setup):
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["cmd", "effect give @s regeneration infinite 255 true"],
                                         ["summon", "elsewhere:azmar_wazir", "{PersistenceRequired:1b}"], ["view", 0.0, 5.0, 35.0], ["chunks"],
                                         ["wait_label", 1800, "f"], ["wait", 2], shot("boss_bar_label_lowercase_f")]
            self.add(lineno, lane_s, "boss bar label letters (elsewhere:azmar_wazir)", "flat", steps, check, setup)
            return
        # subtitles on a boss phase change
        if "showsubtitles" in low or "subtitles on" in low:
            tid = first.split(":")[1] if first else "boss"
            frac = 0.68 if "aphelion" in low else 0.45
            steps = reset("creative", time_of_day=18000 if "night" in low else 6000) + [["subtitles", True],
                     ["summon", first, "{PersistenceRequired:1b}"], ["view", 0.0, 12.0, 20.0], ["chunks"], ["wait", 40],
                     ["hurt_to", frac], ["wait", 10], shot(tid + "_subtitle"), ["subtitles", False]]
            self.add(lineno, lane_s, first, "flat", steps, check, setup,
                     "hurt to %d%% of max HP (just past the first threshold); flat stage instead of the arena" % round(frac * 100))
            return
        # z-items 3D shields
        if lane_s == "z_items" and first:
            sid = first.split(":")[1]
            kind = what.split(",", 1)[1].strip() if "," in what else "view"
            base = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["equip", "weapon.offhand", first]]
            if "GUI slot" in what:
                steps = base + [["hold", "mainhand", first], ["inventory", "none"], ["wait", 5], shot(sid + "_gui_slot"), ["close"]]
            elif "third person" in what:
                steps = base + [["camera", "front"], ["wait", 10], ["use_down"], ["wait", 20], shot(sid + "_third_person_blocking"), ["use_up"], ["camera", "first"]]
            elif "blocking" in what:
                steps = base + [["tp", "stage", 0, 0, 0, 0, 0], ["wait", 10], ["use_down"], ["wait", 20], shot(sid + "_first_person_blocking"), ["use_up"]]
            else:
                steps = base + [["tp", "stage", 0, 0, 0, 0, 0], ["wait", 10], shot(sid + "_first_person_idle")]
            # the vanilla shield reference for the same view, once per view kind
            ref = [s if s[0] != "equip" else ["equip", "weapon.offhand", "minecraft:shield"] for s in steps]
            ref = [s if s[0] != "hold" else ["hold", "mainhand", "minecraft:shield"] for s in ref]
            ref_slug = "vanilla_shield_" + slugify(kind)
            ref = [["shot", ref_slug] if s[0] == "shot" else s for s in ref]
            key = ("ref", ref_slug)
            if key not in self.prev:
                self.prev[key] = True
                self.add(lineno, lane_s, "minecraft:shield (reference: %s)" % kind, "flat", [list(s) for s in ref], "vanilla reference for: " + check, setup)
            self.add(lineno, lane_s, "%s (%s)" % (first, kind), "flat", steps, check, setup)
            return
        # gear3 armor (one shot per piece, full set worn) and held items
        if first and "/item replace entity @s armor." in setup:
            sid = first.split(":")[1]
            slot = re.search(r"armor\.(head|chest|legs|feet)", setup).group(1)
            m = re.search(r"full ([a-z0-9_]+) set", setup)
            steps = reset("creative") + ([["wear_set", m.group(1)]] if m else []) + [["equip", "armor." + slot, first], ["camera", "front"], ["wait", 15],
                     shot(sid + "_front"), ["camera", "first"], ["gui", False], ["mannequin", 90.0], ["wait", 10], shot(sid + "_side"), ["gui", True],
                     ["inventory", "armor." + slot], ["wait", 5], shot(sid + "_tooltip"), ["close"]]
            self.add(lineno, lane_s, first, "flat", steps, check, setup)
            return
        if first and "/give @s" in setup and ("held in" in setup or "inventory tooltip" in setup):
            sid = first.split(":")[1]
            hand = "offhand" if "off hand" in setup else "mainhand"
            steps = reset("creative") + [["hold", hand, first], ["camera", "front"], ["wait", 12], shot(sid + "_held"), ["camera", "first"],
                                         ["inventory", hand], ["wait", 5], shot(sid + "_tooltip"), ["close"]]
            self.add(lineno, lane_s, first, "flat", steps, check, setup)
            return
        # armies: battles and patrols
        mb = re.search(r"/kingdom armies battle ([a-z]+) ([a-z]+) (\d+)", setup)
        if mb:
            cmd = mb.group(0)[1:]
            mu = re.search(r"(\d+) up", setup)
            mbk = re.search(r"(\d+) back", setup)
            up, back = int(mu.group(1)) if mu else 12, int(mbk.group(1)) if mbk else 24
            steps = reset("creative") + [["fly"], ["cmd", cmd], ["cmd", "tp @s ^ ^%d ^-%d" % (up, back)]]
            times = sorted(set([int(t) for t in re.findall(r"\+(\d+) s", setup)] + [(int(a) + int(b)) // 2 for a, b in re.findall(r"\+(\d+)-(\d+) s", setup)])) or [30]
            elapsed = 0
            for t in times:
                steps += [["wait", (t - elapsed) * 20]]
                f3 = "F3" in setup and t == max(times) and len(times) > 1
                if f3:
                    steps.append(["f3", True])
                steps.append(shot("battle_%s_%s_%s_%ds%s" % (mb.group(1), mb.group(2), mb.group(3), t, "_f3" if f3 else "")))
                if f3:
                    steps.append(["f3", False])
                elapsed = t
            steps.append(["cmd", "kingdom armies battle stop"])
            self.add(lineno, lane_s, what.strip(), "flat", steps, check, setup)
            return
        mp = re.search(r"/kingdom patrol (\d+)", setup)
        if mp:
            steps = reset("creative") + [["fly"], ["cmd", mp.group(0)[1:]], ["cmd", "tp @s ^ ^2 ^-12"], ["wait", 200], shot("patrol_%s_10s" % mp.group(1))]
            self.add(lineno, lane_s, what.strip(), "flat", steps, check, setup)
            return
        # gear (arsenal): Calling panel, tooltip, /calling readout, Amberstill set
        if "CallingPanel" in setup or "Calling panel" in what:
            steps = reset("survival") + [["wear_set", "moonsilk"], ["hold", "mainhand", "elsewhere:sunlance_staff"], ["wait", 60],
                                         ["client_set", "dev.elsewhere.arsenal.client.CallingPanel", "shown", True], ["wait", 3],
                                         shot("calling_panel_caster"), ["client_set", "dev.elsewhere.arsenal.client.CallingPanel", "shown", False]]
            self.add(lineno, lane_s, "Calling panel (moonsilk + sunlance_staff)", "flat", steps, check, setup)
            return
        if "Calling tooltip" in what:
            steps = reset("survival") + [["wear_set", "moonsilk"], ["hold", "mainhand", "elsewhere:sunlance_staff"], ["wait", 40],
                                         ["inventory", "armor.chest"], ["wait", 5], shot("calling_tooltip_moonsilk_robe"),
                                         ["inventory", "mainhand"], ["wait", 5], shot("calling_tooltip_sunlance_staff"), ["close"]]
            self.add(lineno, lane_s, "Calling tooltip (moonsilk_robe, sunlance_staff)", "flat", steps, check, setup)
            return
        if "/calling" in what or "/calling" in setup:
            steps = reset("survival") + [["wear_set", "moonsilk"], ["hold", "mainhand", "elsewhere:sunlance_staff"], ["wait", 60],
                                         ["chat", True], ["player_cmd", "calling"], ["wait", 10], shot("calling_readout_chat"), ["chat", False]]
            self.add(lineno, lane_s, "/calling readout", "flat", steps, check, setup)
            return
        if "amberstill" in low and "set" in low:
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["wear_set", "amberstill"],
                                         ["hold", "mainhand", "minecraft:iron_sword"], ["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}"],
                                         ["camera", "front"], ["wait", 60], shot("amberstill_plate_standing"), ["camera", "first"], ["look_target"], ["attack"],
                                         ["wait", 2], ["camera", "front"], ["wait", 1], shot("amberstill_plate_swing"), ["camera", "first"],
                                         ["inventory", "armor.chest"], ["wait", 5], shot("amberstill_plate_tooltip"), ["close"]]
            self.add(lineno, lane_s, "Amberstill Plate full set (gear2)", "flat", steps, check, setup, "flat stage, not the Amberstill biome")
            return
        # the court waterfront (Hrafnholt jetty, boathouse, longships on a lake north of the castle)
        if "court_waterfront" in low:
            v = [[0, 45, -30, 180, 40], [-60, 20, -80, -90, 20], [30, 12, -60, 150, 20]]
            steps = reset("spectator", gui=False) + [["locate", "elsewhere:castle_hrafnholt"]]
            for vv, nm in zip(v, ["front", "side", "boathouse"]):
                steps += [["tp", "anchor"] + vv, ["chunks"], ["wait", 40], shot("court_waterfront_" + nm)]
            self.add(lineno, lane_s, "elsewhere:waterfront_hrafnholt", "normal", steps, check, setup, "views north of the located Hrafnholt castle")
            return
        # every Elsewhere structure on its ground (the release client test's rel_<structure> sites section)
        if "rel_<structure>" in setup or "rel_<structure>" in what or "sites section" in what:
            self.add(lineno, lane_s, what.strip(), "normal", reset("spectator", gui=False) + [["sites", 80]], check, setup,
                     "two views per located elsewhere structure; files <lane>__site_<structure>_a|b.png are added to the index as they are taken")
            return
        # tooltips hovered by shown name ("hotbar: A, B, C; hover each in turn" / "hover the A, then the B (and the C once it is in)")
        mh = re.search(r"hotbar:\s*([^;|]+)", setup)
        mv = re.search(r"hover the (.+?), then the (.+?)(?: \(and the (.+?) once it is in\))?\s*$", setup)
        if mh or mv:
            names = [n.strip() for n in mh.group(1).split(",")] if mh else [g for g in mv.groups()[:2]]
            optional = [mv.group(3)] if mv and mv.group(3) else []
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["give_named", names + optional]]
            for nm in names + optional:
                steps += [["hover_named", nm, nm in optional], ["wait", 4], shot("tooltip_" + slugify(nm))]
            steps.append(["close"])
            self.add(lineno, lane_s, what.strip(), "flat", steps, check, setup)
            return
        # longship (summoned on a pool), villages (nearest village to each realm)
        if "longship" in low:
            steps = reset("creative", gui=False) + [["cmd", "fill ~-14 ~-3 ~4 ~14 ~-1 ~40 minecraft:water"], ["summon", first or "elsewhere:hrafnholt_longship", "{PersistenceRequired:1b}", 0, 18],
                                                    ["view", 0.0, 22.0, 35.0], ["chunks"], ["wait", 40], shot("longship_afloat"), ["wait", 200], ["look_target"], shot("longship_10s")]
            self.add(lineno, lane_s, first or "elsewhere:hrafnholt_longship", "flat", steps, check, setup, "summoned on a dug pool on the flat stage")
            return
        if lane_s == "villages" or what.strip().startswith("the faction villages"):
            for f in FACTIONS:
                steps = reset("spectator", gui=False) + [["locate", "elsewhere:castle_" + f], ["locate_near", "#minecraft:village"],
                                                         ["tp", "anchor", 0, 40, 40, 180, 40], ["chunks"], ["wait", 60], shot("village_near_" + f)]
                self.add(lineno, lane_s, "%s (%s realm)" % (what.strip(), f), "normal", steps, check, setup, "the vanilla village nearest the located castle_" + f)
            return
        # structure lanes with their own anchor S (locate the id, in whichever dimension holds it)
        sv = s_steps(setup) if first else []
        if sv and ("S+(" in setup or "kiosk+(" in setup):
            sid = first.split(":")[1]
            steps = reset("spectator", gui=False) + [["locate", first]]
            if "night vision" in setup:
                steps.insert(len(steps) - 1, ["cmd", "effect give @s night_vision infinite 0 true"])
            for st, slug in sv:
                steps += [st, ["chunks"], ["wait", 40], shot(sid + "_" + slug)]
            note = "the hall view is approximate (altar position unknown): S+(0, -6, 0) facing south" if "altar dais" in setup else None
            if note:
                steps += [["tp", "anchor", 0, -6, 0, 0, 10], ["chunks"], ["wait", 40], shot(sid + "_hall_altar_facing_south")]
            self.add(lineno, lane_s, first, "normal", steps, check, setup, note)
            return
        # P13 particle diet scenes (no ids in the line: the bosses and items are named)
        if lane_s == "s_p13_particles":
            if "Ankhara" in what:
                steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["locate", "elsewhere:sunpit"],
                         ["tp", "anchor", 0, 6, 22, 180, 15], ["chunks"], ["wait", 60], shot("ankhara_arena_seal_3s"), ["wait", 100], shot("ankhara_burrow_trail_8s")]
                self.add(lineno, lane_s, "Ankhara arena seal + burrow trail (elsewhere:sunpit)", "normal", steps, check, setup, "fight started by walking in; the burrow is not forced")
            elif "Rimeback" in what:
                steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["locate", "elsewhere:glacier_shelf"],
                         ["tp", "anchor", 22, 6, 0, 90, 10], ["chunks"], ["wait", 200], shot("rimeback_avalanche_10s"), ["wait", 200], shot("rimeback_avalanche_20s")]
                self.add(lineno, lane_s, "Rimeback avalanche wall (elsewhere:glacier_shelf)", "normal", steps, check, setup, "two timed shots of the started fight; the avalanche is not forced")
            elif "Prism" in what:
                steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["give_named", ["Prism", "Burning Glass"]],
                         ["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}", 0, 15], ["tp", "stage", 0, 0, 0, 0, 0], ["look_target"],
                         ["hover_named", "Prism", False], ["close"], ["cmd", "item replace entity @s weapon.mainhand from entity @s hotbar.0"],
                         ["use_down"], ["wait", 40], shot("prism_beam_15"), ["use_up"], ["kill"],
                         ["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}", 0, 10], ["look_target"], ["cmd", "item replace entity @s weapon.mainhand from entity @s hotbar.1"],
                         ["use_down"], ["wait", 40], shot("burning_glass_beam_10"), ["use_up"]]
                self.add(lineno, lane_s, "Prism beam and Burning Glass beam", "flat", steps, check, setup)
            elif "Percolax" in what:
                steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["summon", "elsewhere:percolax", "{PersistenceRequired:1b}"],
                         ["view", 0.0, 9.0, 30.0], ["chunks"], ["wait", 60], ["look_target"], shot("percolax_smoke_hide_3s")]
                self.add(lineno, lane_s, "Percolax phase-0 smoke hide (elsewhere:percolax)", "flat", steps, check, setup)
                steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["locate", "elsewhere:lighthouse"],
                         ["tp", "anchor", 0, 3, 18, 180, 5], ["chunks"], ["wait", 60], shot("verdigris_arena_seal_galvanox")]
                self.add(lineno, lane_s, "verdigris arena seal (elsewhere:lighthouse, Galvanox)", "normal", steps, check, setup, "fight started by walking in")
            else:
                self.add(lineno, lane_s, what.strip(), "none", [["manual", setup]], check, setup, "unrecognised P13 scene")
            return
        if "censer" in what and "channel" in what:
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["hold", "mainhand", "elsewhere:choirlight_censer"],
                     ["summon", "minecraft:wolf", "{Health:8f,PersistenceRequired:1b}", 0, 3], ["tp", "stage", 0, 0, 0, 0, 10], ["use_down"], ["wait", 20],
                     shot("censer_channel_first_person_1s"), ["camera", "front"], ["wait", 20], shot("censer_channel_third_person_2s"), ["camera", "first"],
                     ["cmd", "tp @s ~ ~8 ~ 0 90"], ["wait", 20], shot("censer_channel_top_3s"), ["use_up"]]
            self.add(lineno, lane_s, "elsewhere:choirlight_censer (channel)", "flat", steps, check, setup, "wolf hurt to 8 HP but untamed; Glimmer as the survival player has it")
            return
        if "wiki retakes" in what:
            for nm, v in (("overview", [0, 40, 40, 180, 40]), ("overview_street", [0, 12, 24, 180, 20]), ("palisade", [34, 8, 34, 135, 15]), ("stone", [-20, 6, 12, -120, 10])):
                steps = reset("spectator", gui=False) + [["cmd", "gamerule send_command_feedback false"], ["locate", "elsewhere:castle_aldermark"],
                         ["locate_near", "#minecraft:village"], ["tp", "anchor"] + v, ["chunks"], ["wait", 60], shot("villages_aldermark_" + nm)]
                self.add(lineno, lane_s, "villages_aldermark_" + nm, "normal", steps, check, setup, "the vanilla village nearest the Aldermark castle; views approximate lane-rv-vill's")
            steps = reset("spectator", gui=False) + [["cmd", "gamerule send_command_feedback false"], ["locate", "elsewhere:castle_mirewick"]]
            for nm, v in (("front", [0, 22, 82, 180, 14]), ("corner", [52, 40, 52, 135, 32]), ("gate", [0, 9, 40, 180, 10]), ("quay", [0, 6, 48, 180, 14])):
                steps += [["tp", "anchor"] + v, ["chunks"], ["wait", 40], shot("mirewick_castle_mirewick_" + nm)]
            self.add(lineno, lane_s, "mirewick_castle_mirewick (elsewhere:castle_mirewick)", "normal", steps, check, setup)
            return
        # an item named as "(item)": held third person front, then its tooltip in the survival inventory
        if first and "(item)" in what:
            sid = first.split(":")[1]
            steps = reset("creative") + [["hold", "mainhand", first], ["camera", "front"], ["wait", 12], shot(sid + "_held"), ["camera", "first"],
                                         ["inventory", "mainhand"], ["wait", 5], shot(sid + "_tooltip"), ["close"]]
            self.add(lineno, lane_s, first, "flat", steps, check, setup, "survival inventory tooltip (not the creative tab)")
            return
        # generic: resolve what the line names (ids, bare ids, display names), run its backticked commands, frame by kind
        if self.generic(lineno, lane_s, what, setup, check):
            return
        # fallback on the id the line names; the runner decides entity / item / structure from the registries
        if first:
            self.add(lineno, lane_s, first, "flat", reset("creative") + [["auto", first], shot(first.split(":")[1] + "_auto")], check, setup,
                     "generic: no family matched this setup")
            return
        low2 = low
        if re.match(r"none needed", what.strip().lower()):
            return  # the lane says it has nothing visible
        if re.search(r"nameplate|plates", low2) and "kill switch" not in low2:
            steps = reset("survival") + [["cmd", "effect give @s resistance infinite 255 true"], ["cmd", "kingdom patrol 8"], ["wait", 100],
                                         ["cmd", "tp @s ^ ^ ^-6"], ["wait", 20], shot(slugify(what, 40) + "_6"), ["cmd", "tp @s ^ ^ ^-6"], ["wait", 20],
                                         shot(slugify(what, 40) + "_12"), ["cmd", "tp @s ^ ^ ^-6"], ["wait", 20], shot(slugify(what, 40) + "_18")]
            self.add(lineno, lane_s, what.strip(), "flat", steps, check, setup, "an Aldermark patrol of 8 (/kingdom patrol 8) at 6, 12 and 18 blocks; not KingdomApi.spawnSoldier per rank")
            return
        why = ("needs a separate client launch with the kill switch off (config is read at start)" if "kill switch" in low2 or "kill-switch" in low2
               else "needs JEI loaded (-Pjei, the release client's jei section)" if "jei" in low2
               else "needs a live kingdom town/army state the runner cannot set up by command" if re.search(r"pose\.|serf|townsfolk|tabard|nameplate|plates|soldier", low2)
               else "no id, view or command in the line")
        self.add(lineno, lane_s, what.strip(), "none", [["manual", why]], check, setup, why)

    def generic(self, lineno, lane_s, what, setup, check):
        ids = resolve_ids(what) or resolve_ids(setup)
        kind = KINDS.get(ids[0]) if ids else None
        cmds = [c.strip().lstrip("/") for c in re.findall(r"`(/[^`]+)`", what + " " + setup)]
        cmds += [c.strip().lstrip("/") for c in re.findall(r"(?<![`\w])(/(?:time set \d+|weather \w+|kingdom [a-z ]+\d*|elsewhere_hud [a-z ]+|greenleaf [a-z ]+))", setup)]
        cmds = [c for c in cmds if not c.startswith(("gamemode", "tp ", "locate"))]
        low = (what + " " + setup).lower()
        night = re.search(r"\bnight\b|18000", low) is not None
        cap = re.search(r"(aldermark|hrafnholt|azmar|mirewick) capital", low)
        if cap:
            steps = reset("spectator", gui=False) + [["locate", "elsewhere:castle_" + cap.group(1)]]
            times = [c for c in cmds if c.startswith("time set")] or ["time set 6000"]
            for i, c in enumerate(times):
                steps += [["cmd", c], ["tp", "anchor", -88, 14, 40, 180, 20], ["chunks"], ["wait", 100], shot("%s_capital_%s" % (cap.group(1), slugify(c)))]
            self.add(lineno, lane_s, what.strip(), "normal", steps, check, setup, "west town of the located castle_%s at each /time" % cap.group(1))
            return True
        if kind is None and not cmds:
            return False
        note = "generic: " + ("%s %s" % (kind, ids[0]) if kind else "commands only") + (", %d setup command(s)" % len(cmds) if cmds else "") + "; framing approximate"
        if kind == "structure":
            sid = ids[0]
            steps = reset("spectator", gui=False, time_of_day=18000 if night else 6000) + [["locate", "elsewhere:" + sid]]
            for c in cmds:
                steps.append(["cmd", c])
            steps += [["tp", "anchor", 24, 14, 24, 135, 25], ["chunks"], ["wait", 40], shot(sid + "_overview"), ["tp_downhill", 24], ["chunks"], ["wait", 30], shot(sid + "_ground")]
            self.add(lineno, lane_s, "elsewhere:" + sid, "normal", steps, check, setup, note)
            return True
        mode = "survival" if "survival" in low else "creative"
        steps = reset(mode, time_of_day=18000 if night else 6000) + [["cmd", "effect give @s resistance infinite 255 true"]]
        if "f1" in low and "f1 off" not in low:
            steps.append(["gui", False])
        if "subtitle" in low:
            steps.append(["subtitles", True])
        base = slugify(what, 40)
        if kind == "entity":
            sid = ids[0]
            nbt = "{NoAI:1b,PersistenceRequired:1b}" if "noai" in low or "flinch" in low or "still" in low else "{PersistenceRequired:1b}"
            steps += [["summon", "elsewhere:" + sid, nbt], ["view", 1.6, 5.0, 35.0], ["chunks"], ["wait", 20]]
            for c in cmds:
                steps += [["cmd", c], ["wait", 10]]
            steps += [["look_target"], shot(base)]
            if cmds:
                steps += [["wait", 40], ["look_target"], shot(base + "_later")]
            self.add(lineno, lane_s, "elsewhere:" + sid, "flat", steps, check, setup, note)
            return True
        if kind == "item":
            sid = ids[0]
            hand = "offhand" if "off hand" in low or "offhand" in low else "mainhand"
            steps += [["hold", hand, "elsewhere:" + sid]]
            for c in cmds:
                steps += [["cmd", c], ["wait", 10]]
            if re.search(r"husk|mob|target", low):
                steps += [["summon", "minecraft:husk", "{NoAI:1b,PersistenceRequired:1b}", 0, 5], ["tp", "stage", 0, 0, 0, 0, 0], ["look_target"]]
            steps += [["wait", 10], shot(sid + "_first_person"), ["camera", "front"], ["wait", 10], shot(sid + "_third_person"), ["camera", "first"]]
            if re.search(r"\buse\b|throw|fire|whirl|drawn|draw|swing|channel|cast|crack|charge", low):
                steps += [["use_down"], ["wait", 15], shot(sid + "_in_use_first_person"), ["camera", "front"], ["wait", 5], shot(sid + "_in_use_third_person"),
                          ["use_up"], ["camera", "first"], ["wait", 10], shot(sid + "_after_use")]
            if "tooltip" in low or "lore" in low or "jei" not in low:
                steps += [["inventory", hand], ["wait", 5], shot(sid + "_tooltip"), ["close"]]
            self.add(lineno, lane_s, "elsewhere:" + sid, "flat", steps, check, setup, note)
            return True
        # commands only (HUD, armies, kingdom states, Green Leaf ...)
        steps += [["fly"]] if mode == "creative" else []
        for c in cmds:
            steps += [["cmd", c], ["wait", 40]]
        if "f3" in low:
            steps.append(["f3", True])
        if "chat" in low:
            steps.append(["chat", True])
        steps += [["wait", 20], shot(base), ["wait", 100], shot(base + "_5s")]
        self.add(lineno, lane_s, what.strip(), "flat", steps, check, setup, note)
        return True

    def run(self, text):
        for n, raw in enumerate(text.splitlines(), 1):
            line = raw.strip()
            if not line.startswith("- ") and not re.match(r"^[a-z][a-z0-9_-]* \|", line):
                continue
            body = line[2:] if line.startswith("- ") else line
            if "|" not in body:
                m = re.match(r"([a-z0-9_-]+) \(([a-z]+)\):\s*(.*)", body)
                if m:  # "court (placement): ./gradlew runClientGameTest ... takes court_waterfront_front|side|boathouse (...)"
                    self.compile_line(n, m.group(1), "court_waterfront", m.group(3), m.group(3))
                continue
            parts = [p.strip() for p in body.split(" | ")]
            if len(parts) < 3:
                # a pipe inside the free text ("front|side|boathouse") without the field separator
                m = re.match(r"([a-z0-9_-]+) \(([a-z]+)\):\s*(.*)", body)
                if m:
                    self.compile_line(n, m.group(1), "court_waterfront", m.group(3), m.group(3))
                continue
            lane, what, setup = parts[0], parts[1], parts[2]
            check = " | ".join(parts[3:]) if len(parts) > 3 else ""
            if not re.match(r"^[a-z][a-z0-9_-]*$", lane):
                continue
            self.compile_line(n, lane, what, setup, check)


def main():
    text = open(SRC, encoding="utf-8").read()
    c = Compiler()
    c.run(text)
    os.makedirs(OUT, exist_ok=True)
    stamp = time.strftime("%Y-%m-%d %H:%M")
    doc = {"source": os.path.relpath(SRC, ROOT), "generated": stamp, "scenes": c.scenes,
           "counts": {"scenes": len(c.scenes), "shots": sum(len(s["shots"]) for s in c.scenes),
                      "manual": sum(1 for s in c.scenes if s["world"] == "none" and s["steps"][0][0] == "manual")}}
    with open(os.path.join(OUT, "shotlist.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, indent=1)
    idx_path = os.path.join(OUT, "index.json")
    old = {}
    try:
        old = json.load(open(idx_path, encoding="utf-8")).get("lanes", {})
    except Exception:
        pass
    lanes = {}
    for s in c.scenes:
        for sh in s["shots"]:
            prev = next((e for e in old.get(s["lane"], {}).get(s["item"], []) if e.get("file") == sh["file"]), None)
            exists = os.path.exists(os.path.join(OUT, sh["file"]))
            e = {"file": sh["file"], "scene": s["n"], "line": s["line"], "check": s["check"],
                 "status": prev["status"] if prev and exists else ("ok" if exists else "pending")}
            if prev and exists:
                for k in ("detail", "taken"):
                    if k in prev:
                        e[k] = prev[k]
            lanes.setdefault(s["lane"], {}).setdefault(s["item"], []).append(e)
    with open(idx_path, "w", encoding="utf-8") as f:
        json.dump({"generated": stamp, "dir": "ElsewhereQA/shots-3.0", "lanes": lanes}, f, indent=1)
    print("shotlist: %d scenes, %d shots (%d manual) -> %s" % (doc["counts"]["scenes"], doc["counts"]["shots"], doc["counts"]["manual"], OUT))


if __name__ == "__main__":
    main()
