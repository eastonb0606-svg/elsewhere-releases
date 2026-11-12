#!/usr/bin/env python3
"""Builds audit_fixtures/: the designer's 15 cards plus broken and boundary variants, each with the verdict of
_tools/boss_audit.py in expected.txt (PASS or FAIL). The module self-test runs BossIdentityAudit.checkDir on every
set and must agree with each verdict. Usage: gen_audit_fixtures.py <designer dir> <boss_audit.py> <out dir>"""
import copy, json, os, shutil, subprocess, sys

src, audit, out = sys.argv[1:4]
cards = {f[:-5]: json.load(open(os.path.join(src, f), encoding="utf-8")) for f in sorted(os.listdir(src)) if f.endswith(".json")}
sets = {}

def variant(name):
    def deco(fn):
        c = copy.deepcopy(cards)
        text = {}
        fn(c, text)
        sets[name] = (c, text)
        return fn
    return deco

variant("real")(lambda c, t: None)

@variant("three_shared_slots")
def _(c, t):
    for s in ("weapons", "defence", "recovery"): c["azmar_wazir"]["slots"][s] = c["azmar_amir"]["slots"][s]

@variant("two_shared_slots_ok")
def _(c, t):
    for s in ("weapons", "defence"): c["azmar_wazir"]["slots"][s] = c["azmar_amir"]["slots"][s]

@variant("fourth_eater")
def _(c, t): c["azmar_amir"]["generic"].append("eat")

@variant("second_pearl")
def _(c, t): c["azmar_amir"]["generic"].append("pearl")

@variant("totem_last_stand")
def _(c, t):
    c["hrafnholt_jarl"]["last_stand"] = "totem_of_undying"; c["hrafnholt_jarl"]["slots"]["last_stand"] = "totem_of_undying"

@variant("totem_in_idle_text")
def _(c, t): c["azmar_sultana"]["idle"] = "She lifts a golden Totem."

@variant("missing_file")
def _(c, t): del c["mirewick_ames"]

@variant("extra_file")
def _(c, t): c["aldermark_king"] = copy.deepcopy(c["azmar_amir"])

@variant("invalid_json")
def _(c, t): t["azmar_amir"] = "{ \"slots\": "

@variant("trailing_comma")
def _(c, t): t["azmar_amir"] = json.dumps(c["azmar_amir"])[:-1] + ",}"

@variant("no_telegraph_sound")
def _(c, t): del c["azmar_amir"]["telegraph"]["sound"]

@variant("bad_colour")
def _(c, t): c["azmar_amir"]["telegraph"]["colour"] = "red"

@variant("sound_as_string_ok")
def _(c, t): c["azmar_amir"]["telegraph"]["sound"] = "entity.camel.dash"

@variant("sound_without_pitch")
def _(c, t): del c["azmar_amir"]["telegraph"]["sound"]["pitch"]

@variant("shared_sound")
def _(c, t): c["azmar_amir"]["telegraph"]["sound"]["event"] = c["azmar_wazir"]["telegraph"]["sound"]["event"]

@variant("shared_particle")
def _(c, t): c["azmar_amir"]["telegraph"]["particle"] = c["azmar_wazir"]["telegraph"]["particle"]

@variant("shared_colour_other_case")
def _(c, t): c["azmar_amir"]["telegraph"]["colour"] = c["azmar_wazir"]["telegraph"]["colour"].lower()

@variant("shared_signature")
def _(c, t): c["azmar_amir"]["signature"][0] = c["azmar_wazir"]["signature"][0]

@variant("one_signature")
def _(c, t): c["azmar_amir"]["signature"] = c["azmar_amir"]["signature"][:1]

@variant("four_signatures")
def _(c, t): c["azmar_amir"]["signature"] = ["a1", "a2", "a3", "a4"]

@variant("shared_movement")
def _(c, t): c["azmar_amir"]["slots"]["movement"] = c["azmar_wazir"]["slots"]["movement"]

@variant("shared_last_stand")
def _(c, t): c["azmar_amir"]["last_stand"] = c["azmar_wazir"]["last_stand"]

@variant("missing_slot")
def _(c, t): del c["azmar_amir"]["slots"]["reach"]

@variant("extra_slot")
def _(c, t): c["azmar_amir"]["slots"]["mount"] = "camel"

@variant("blank_slot")
def _(c, t): c["azmar_amir"]["slots"]["reach"] = "  "

@variant("unknown_generic")
def _(c, t): c["azmar_amir"]["generic"] = ["totem_pop"]

@variant("repeated_generic")
def _(c, t): c["azmar_sultana"]["generic"] = ["drink", "drink"]

@variant("no_chair_last_stand")
def _(c, t): del c["mirewick_ames"]["chair_last_stand"]

@variant("chair_disagree")
def _(c, t): c["mirewick_ames"]["chair_last_stand"] = "other_rally"

@variant("chair_is_a_boss_last_stand")
def _(c, t):
    for k in c:
        if k.startswith("mirewick_"): c[k]["chair_last_stand"] = c["azmar_amir"]["last_stand"]

@variant("two_mirewick_builders")
def _(c, t): c["mirewick_tolliver"]["generic"].append("temp_blocks")

@variant("fourth_shield")
def _(c, t): c["azmar_amir"]["generic"].append("shield_block")

@variant("no_last_stand")
def _(c, t): c["azmar_amir"]["last_stand"] = ""

if os.path.isdir(out): shutil.rmtree(out)
for name, (c, text) in sorted(sets.items()):
    d = os.path.join(out, name)
    os.makedirs(d)
    for k, v in c.items():
        with open(os.path.join(d, k + ".json"), "w", encoding="utf-8") as fh:
            fh.write(text[k] if k in text else json.dumps(v, indent=2, ensure_ascii=False) + "\n")
    rc = subprocess.run([sys.executable, audit, d], capture_output=True, text=True)
    verdict = "PASS" if rc.returncode == 0 else "FAIL"
    with open(os.path.join(d, "expected.txt"), "w") as fh:
        fh.write(verdict + "\n")
    print("%-28s %s" % (name, verdict))

# keep the packed copy the merged jar's self-test reads in step (the lane self-test fails if they differ)
here = os.path.dirname(os.path.abspath(__file__))
if os.path.realpath(out) == os.path.realpath(os.path.join(here, "..", "audit_fixtures")):
    subprocess.run([sys.executable, os.path.join(here, "pack_audit_fixtures.py"), out], check=True)
