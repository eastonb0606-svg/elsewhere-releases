import json, os, re, sys
# Static check: every mapped boss id is a registered entity (and in #elsewhere:bosses, except kingdom_court's King/Lords,
# which are fights through this map alone), and its theme has an intro and a loop sound event whose OGG files exist, plus intro_ticks.
here = os.path.dirname(os.path.abspath(__file__)); mod = os.path.dirname(here)
res = os.path.join(mod, "src/main/resources/assets/elsewhere")
beta = sys.argv[1] if len(sys.argv) > 1 else os.path.join(mod, "../../ElsewhereBeta/src")
court = os.path.join(mod, "../kingdom_court/src/main/java")
m = json.load(open(res + "/boss_music.json")); s = json.load(open(res + "/sounds.json"))
bad = []
tag = json.load(open(beta + "/main/resources/data/elsewhere/tags/entity_type/bosses.json"))["values"]
tag = {(v if isinstance(v, str) else v["id"]) for v in tag}
def src_of(root):
    out = ""
    for d, _, fs in os.walk(root):
        for f in fs:
            if f.endswith(".java"): out += open(os.path.join(d, f), errors="ignore").read()
    return out
src = src_of(beta + "/main/java"); csrc = src_of(court)
for b in m["bosses"]:
    k = b.split(":")[1]
    if k.startswith("court_"):  # kingdom_court: Ruler.entityId() = court_<key>
        if not re.search(r'\b[A-Z_]+\("%s",' % k[len("court_"):], csrc): bad.append("no Ruler entry for " + b)
        continue
    if b not in tag: bad.append("not in #elsewhere:bosses: " + b)
    if not re.search(r'(register|reg|living|entity)\(\s*"%s"' % k, src):
        bad.append("no entity registration found: " + b)
for b in tag - set(m["bosses"]): bad.append("tag boss without theme: " + b)
for ev in m["stingers"].values():
    k = ev.split(":")[1]
    if k not in s: bad.append("no sound event " + ev); continue
    for snd in s[k]["sounds"]:
        n = snd["name"].split(":")[1] if isinstance(snd, dict) else snd.split(":")[1]
        p = f"{res}/sounds/{n}.ogg"
        if not os.path.exists(p) or os.path.getsize(p) < 20000: bad.append("missing/small file " + p)
for th in set(m["bosses"].values()):
    if th not in m["intro_ticks"]: bad.append("no intro_ticks for " + th)
    for part in ("intro", "loop"):
        k = th.split(":")[1] + "." + part
        if k not in s: bad.append("no sound event " + k); continue
        for snd in s[k]["sounds"]:
            if not (isinstance(snd, dict) and snd.get("stream")): bad.append("not streamed: " + k)
            n = snd["name"].split(":")[1] if isinstance(snd, dict) else snd.split(":")[1]
            p = f"{res}/sounds/{n}.ogg"
            if not os.path.exists(p) or os.path.getsize(p) < 20000: bad.append("missing/small file " + p)
themes = set(m["bosses"].values())
print(len(m["bosses"]), "bosses,", len(themes), "distinct themes,", len(tag), "tag entries")
print("OK" if not bad else "\n".join(bad)); sys.exit(1 if bad else 0)
