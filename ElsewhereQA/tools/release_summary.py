#!/usr/bin/env python3
"""Builds RELEASE-TEST-RESULTS.md from a release-run folder. Usage: release_summary.py <run-dir> <out.md> <jar-label> <jar-sha1>

Run-dir layout (written by run-release-tests.sh):
  server/qa.log            lines "[qa] STATUS area: detail" from the release suite
  server/latest.log        the server's latest.log
  upgrade/seed.log, upgrade/check.log, upgrade/check-latest.log, upgrade/seed-latest.log
  prod/<variant>.log       latest.log of each production boot, prod/<variant>.rc
  client/results-client.tsv, client/client-latest.log, client/*.png
  phases.tsv               phase<TAB>status<TAB>seconds<TAB>note
"""
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

run = Path(sys.argv[1])
out = Path(sys.argv[2])
label = sys.argv[3] if len(sys.argv) > 3 else "?"
sha = sys.argv[4] if len(sys.argv) > 4 else "?"
STATUS = ("FAIL", "WARN", "SKIP", "PASS")
# lines that are environment noise in a dev/offline client or server, not mod defects
NOISE = re.compile(r"401|Realms|Anisotropic|authentication|Failed to verify|Telemetry|Unable to load realms|Yggdrasil|profile|session|Sound.*already|"
                   r"Mismatch in destroy block pos|Failed to fetch|sessionserver|Chat.*signature|Failed to retrieve|Couldn't connect|UnknownHost|FileNotFound.*servers\.dat", re.I)


def read(p):
    try:
        return Path(p).read_text(errors="replace")
    except OSError:
        return ""


def parse_qa(text):
    rows = []
    for m in re.finditer(r"\[qa\] (PASS|FAIL|WARN|SKIP) ([^:\n]+?): (.*)", text):
        rows.append((m.group(1), m.group(2).strip(), m.group(3).strip()))
    return rows


def scan_errors(text):
    """ERROR lines and exceptions that are not known environment noise."""
    bad = []
    for ln in text.splitlines():
        if "/ERROR]" in ln or re.search(r"\bException\b|Caused by:|\tat dev\.elsewhere", ln):
            if NOISE.search(ln):
                continue
            bad.append(ln.strip()[:240])
    return bad


def table(rows):
    c = Counter(r[0] for r in rows)
    return "%d PASS, %d FAIL, %d WARN, %d SKIP" % (c["PASS"], c["FAIL"], c["WARN"], c["SKIP"])


md = []
phases = []
for ln in read(run / "phases.tsv").splitlines():
    p = ln.split("\t")
    if len(p) >= 3:
        phases.append(p)

server = parse_qa(read(run / "server" / "qa.log"))
client = []
for ln in read(run / "client" / "results-client.tsv").splitlines():
    p = ln.split("\t")
    if len(p) >= 3:
        client.append((p[0], p[1], p[2]))
seed = parse_qa(read(run / "upgrade" / "seed.log"))
check = parse_qa(read(run / "upgrade" / "check.log"))
useed_ = parse_qa(read(run / "used" / "seed.log"))
ucheck_ = parse_qa(read(run / "used" / "check.log"))

verdict = "PASS"
if not (server or client or seed or check or phases):
    verdict = "NOT RUN (no phase produced results)"
fails = [r for r in server + client + seed + check + useed_ + ucheck_ if r[0] == "FAIL"]
if fails:
    verdict = "FAIL (%d failing checks)" % len(fails)
phase_bad = [p for p in phases if p[1] not in ("PASS", "SKIP")]
if phase_bad:
    verdict = "FAIL (phase problems)" if verdict == "PASS" else verdict


HINTS = [
    ("transmute", "Test Meter value API / gear pricing: set values >= 5x netherite for craftable gear (gear2 + module item classes; testmeter tag data/testmeter/tags/item/transmutation_excluded.json)"),
    ("trader", "trader offers: ElsewhereContent/verdigris_ironpeak/.../ElsewhereTrader + StructureTraders, greenhaze_hush trader classes (offer factories); price vs Test Meter value"),
    ("kingdom", "ElsewhereContent/kingdom (KingdomPerf / KingdomConfig defaults, conquest module)"),
    ("boss retry", "dev.elsewhere.boss.kit.KitStore / BossKit (core boss kit)"),
    ("boss music", "ElsewhereContent/boss_music (sounds.json + SoundEvents per boss id)"),
    ("boss ", "NOTE tick-time rows are inflated when the iMac is busy (load ~15 from other lanes): re-measure before blaming. Boss module: overworld_bosses / nether_end_bosses / petalwind_glasswaste / verdigris_ironpeak / greenhaze_hush (entity class named after the boss); drops via BossDrops/DropRule"),
    ("site ", "worldgen structure sets in the owning dimension module (data/elsewhere/worldgen/structure*)"),
    ("jei", "ElsewhereContent/jei_compat (jei/info.json, recipe categories)"),
    ("upgrade", "registry ids (never rename/remove) in core/modules; world data compatibility"),
    ("used", "core + kingdom conquest state (village protection), structure sets"),
    ("dim ", "dimension module arrival/return rift code (core travel SafeLanding / RiftTravel)"),
    ("blocks", "block loot tables / placement in the module that owns the block"),
    ("items", "item use code in the owning module"),
    ("registry", "core registries"), ("lang", "missing en_us keys: ElsewhereContent/kingdom_court (court_*_spawn_egg) lang/en_us.json, merged by ElsewhereBeta/tools/merge.py"),
    ("locate", "castle_aldermark: ElsewhereContent/kingdom_castle or kingdom_court worldgen structure + structure_set (spacing/biomes so it generates in the Overworld; rarity may exceed 100 chunks from all 4 test origins)"),
]
def hint_for(area):
    for k, v in HINTS:
        if area.startswith(k.strip()) or k in area:
            return v
    return "see the area name"

allfail = []
for src, rows in (("server", server), ("client", client), ("upgrade", seed + check), ("used-server", useed_ + ucheck_)):
    for s_, a_, d_ in rows:
        if s_ == "FAIL":
            allfail.append((src, a_, d_))
for ln in read(run / "phases.tsv").splitlines():
    p = ln.split("\t")
    if len(p) >= 3 and p[1] == "FAIL":
        pass

md.append("# Elsewhere 2.0 release test results\n")
md.append("- Jar: `%s` (sha1 `%s`)" % (label, sha))
md.append("- Run folder: `%s`" % run)
md.append("- Verdict: **%s**" % verdict)
md.append("- Plan: `RELEASE-TEST-PLAN.md` (items marked Manual there are not covered by this run)\n")

md.append("## Failing checks (%d) - fix list\n" % len(allfail))
for src, a_, d_ in allfail:
    md.append("- [%s] **%s**: %s  \n  Where: %s" % (src, a_, d_[:260], hint_for(a_)))
for ln in read(run / "phases.tsv").splitlines():
    p = ln.split("\t")
    if len(p) >= 3 and p[1] == "FAIL" and p[0] in ("production boots", "client gametest", "client FPS"):
        md.append("- [phase] **%s** failed: %s" % (p[0], p[3] if len(p) > 3 else ""))
if not allfail:
    md.append("(none)")
md.append("")
md.append("## Phases\n")
md.append("| Phase | Status | Seconds | Note |\n|---|---|---|---|")
for p in phases:
    md.append("| %s | %s | %s | %s |" % (p[0], p[1], p[2], p[3] if len(p) > 3 else ""))
md.append("")

# ------------------------------------------------------------------ prod boots
md.append("## Production boots (Fabric Loader 0.20.1, Fabric API 0.166.0+26.3, Loom runServer, 2.5 GB)\n")
md.append("| Variant | Mods | Result | Boot smoke | Error lines |\n|---|---|---|---|---|")
prod_dir = run / "prod"
for f in sorted(prod_dir.glob("*.log")) if prod_dir.exists() else []:
    t = read(f)
    rc = read(f.with_suffix(".rc")).strip()
    m = re.search(r"Loading (\d+) mods?", t)
    tot = re.search(r"\[betatest\] TOTAL (\d+) passed, (\d+) failed", t)
    errs = scan_errors(t)
    done = "Done (" in t or "For help, type" in t
    ok = done and tot and tot.group(2) == "0" and not errs
    mods = re.findall(r"- (elsewhere|testmeter|greenleaf|jei|fabric-api) (\S+)", t)
    mod_s = ", ".join("%s %s" % (a, b) for a, b in OrderedDict(mods).items()) if mods else (m.group(1) + " mods" if m else "?")
    md.append("| %s | %s | %s | %s | %d |" % (f.stem, mod_s, "PASS" if ok else "FAIL (rc %s, started=%s)" % (rc or "?", done),
                                             ("%s passed, %s failed" % tot.groups()) if tot else "no [betatest] TOTAL line", len(errs)))
    if errs:
        for e in errs[:4]:
            md.append("|  |  |  | `%s` |  |" % e.replace("|", "/"))
md.append("")

# ------------------------------------------------------------------ server suite
md.append("## Server scenario suite (headless, fake players)\n")
md.append("Totals: %s\n" % table(server))
by_area = OrderedDict()
for s, a, d in server:
    key = re.sub(r"^(boss|dim|trader|site) .*", r"\1", a)
    by_area.setdefault(key, []).append((s, a, d))
md.append("| Area | Result |\n|---|---|")
for k, rows in by_area.items():
    md.append("| %s | %s |" % (k, table(rows)))
md.append("")
for status in ("FAIL", "WARN"):
    rows = [r for r in server if r[0] == status]
    if rows:
        md.append("### %s (%d)\n" % (status, len(rows)))
        for s, a, d in rows:
            md.append("- **%s**: %s" % (a, d[:400]))
        md.append("")
skips = [r for r in server + client if r[0] == "SKIP"]
if skips:
    md.append("### SKIP (feature not present in this jar or run)\n")
    for s, a, d in skips:
        md.append("- **%s**: %s" % (a, d[:300]))
    md.append("")

bosses = OrderedDict()
for s, a, d in server:
    if a.startswith("boss ") and a[5:] not in ("retry", "xp"):
        bosses.setdefault(a[5:], []).append((s, d))
if bosses:
    md.append("### Boss fights (summon, kill, drops, XP, arena, tick time)\n")
    md.append("| Boss | Summon | Killed | Drops | XP | Drop rule | Arena unsealed | MSPT | Verdict |\n|---|---|---|---|---|---|---|---|---|")
    for b, rows in bosses.items():
        def pick(sub):
            for s, d in rows:
                if sub in d:
                    return s, d
            return None, ""
        sp = pick("spawns via")
        k = pick("dies from")
        dr = pick("drops [")
        xp = pick("always drops XP")
        rl = pick("drop-rule table")
        ar = pick("arena unsealed")
        ms = pick("tick time")
        worst = "FAIL" if any(s == "FAIL" for s, _ in rows) else ("WARN" if any(s == "WARN" for s, _ in rows) else "PASS")
        def short(x, n=70):
            return (x[1][:n].replace("|", "/")) if x[0] else "-"
        md.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
            b, sp[0] or "-", k[0] or "-", (dr[0] or "-") + " " + (dr[1][7:90].replace("|", "/") if dr[0] else ""), (xp[0] or "-") + " " + (xp[1][22:60] if xp[0] else ""),
            (rl[0] or "-"), ar[0] or "-", (ms[1].split("avg ")[1].split(",")[0] if ms[0] and "avg " in ms[1] else "-"), worst))
    md.append("")

loc = [r for r in server if r[1] == "locate"]
if loc:
    md.append("### /locate\n")
    for s, a, d in loc:
        md.append("- %s %s" % (s, d))
    md.append("")
for area, title in (("kingdom battle", "Kingdom 30-soldier battle"), ("kingdom siege", "Kingdom siege safety"), ("kingdom caps", "Kingdom caps")):
    rows = [r for r in server if r[1] == area]
    if rows:
        md.append("### %s\n" % title)
        for s, a, d in rows:
            md.append("- %s %s" % (s, d[:300]))
        md.append("")

# ------------------------------------------------------------------ upgrade
md.append("## 0.3.0 world upgrade\n")
if seed or check:
    md.append("Seed run (old jar): %s. Upgraded run (new jar): %s\n" % (table(seed), table(check)))
    for s, a, d in seed + check:
        if s != "PASS" or a == "upgrade":
            md.append("- %s %s: %s" % (s, a, d[:300]))
    ul = read(run / "upgrade" / "check-latest.log")
    reg = [ln.strip()[:200] for ln in ul.splitlines() if re.search(r"missing|unknown (registry|block|item|entity)|Failed to load|Unregistered|Could not (load|parse)|registry.*not (found|present)", ln, re.I)
           and not NOISE.search(ln)]
    md.append("- Log scan of the upgraded boot: %d registry/load complaints, %d error lines" % (len(reg), len(scan_errors(ul))))
    for e in (reg + scan_errors(ul))[:8]:
        md.append("  - `%s`" % e.replace("|", "/"))
else:
    md.append("Not run in this pass.")
md.append("")


# ------------------------------------------------------------------ used server
useed = parse_qa(read(run / "used" / "seed.log"))
ucheck = parse_qa(read(run / "used" / "check.log"))
md.append("## Used-server upgrade (0.3.0 + Test Meter + Green Leaf with a pre-generated world, then the release jar)\n")
if useed or ucheck:
    md.append("Seed run: %s. Release-jar run: %s\n" % (table(useed), table(ucheck)))
    for s, a, d in useed + ucheck:
        md.append("- %s **%s**: %s" % (s, a, d[:300]))
    ul = read(run / "used" / "check-latest.log")
    errs = scan_errors(ul)
    reg = [ln.strip()[:200] for ln in ul.splitlines() if re.search(r"missing registry|unknown (registry|block|item|entity)|Failed to load|Unregistered|registry.*not (found|present)", ln, re.I) and "[qa]" not in ln and not NOISE.search(ln)]
    md.append("- Release-jar boot log: %d missing-registry/load complaints, %d ERROR/exception lines (must be 0)" % (len(reg), len(errs)))
    for e in (reg + errs)[:8]:
        md.append("  - `%s`" % e.replace("|", "/"))
else:
    md.append("Not run in this pass.")
md.append("")

# ------------------------------------------------------------------ client
md.append("## Client gametest (iMac screen, survival)\n")
if client:
    md.append("Totals: %s\n" % table(client))
    for status in ("FAIL", "WARN"):
        rows = [r for r in client if r[0] == status]
        if rows:
            md.append("### %s (%d)\n" % (status, len(rows)))
            for s, a, d in rows:
                md.append("- **%s**: %s" % (a, d[:400]))
            md.append("")
    md.append("### All client checks\n")
    for s, a, d in client:
        md.append("- %s **%s**: %s" % (s, a, d[:220]))
    shots = sorted((run / "client").glob("*.png")) if (run / "client").exists() else []
    md.append("\nScreenshots: %d in `%s/client/` (rel_site_*, rel_boss_*, rel_hud_*, rel_jei_*)." % (len(shots), run))
    cl = read(run / "client" / "client-latest.log")
    errs = scan_errors(cl)
    md.append("\nClient log: %d ERROR/exception lines after environment noise (auth, Realms, anisotropic option) is filtered." % len(errs))
    for e in errs[:12]:
        md.append("- `%s`" % e.replace("|", "/"))
else:
    md.append("Not run in this pass (or no results-client.tsv was produced).")
md.append("")


# ------------------------------------------------------------------ fps
md.append("## Client FPS with kingdom soldiers (MacBook Air, arm64, 8 GB, flat world, no JEI)\n")
rows = [ln.split("\t") for ln in read(run / "fps" / "fps.tsv").splitlines() if ln.count("\t") >= 3]
if rows:
    md.append("| Case | Avg FPS | 95th pct frame (ms) | 99th pct frame (ms) |\n|---|---|---|---|")
    for r in rows:
        md.append("| %s | %s | %s | %s |" % tuple(r[:4]))
    base = float(rows[0][1])
    big = [r for r in rows if r[0].startswith("c ")]
    if big and base > 0:
        drop = (1 - float(big[0][1]) / base) * 100
        smooth = float(big[0][2]) <= 25.0
        md.append("\nFPS drop at 40 soldiers + 30 zombies vs no soldiers: **%.1f%%**; 95th percentile frame %s ms (smooth = at most 25 ms)." % (drop, big[0][2]))
        if drop <= 10 and smooth:
            md.append("\n**Recommendation: raise the kingdom caps to 20 per player / 40 per area** (`perf.maxRealSoldiersPerPlayer`, `perf.maxRealSoldiersPerArea`).")
        else:
            md.append("\n**Recommendation: keep the kingdom caps at 15 per player / 30 per area.**")
    else:
        md.append("\nSoldier cases were skipped (no kingdom module in the run): no cap recommendation. Keep 15/30.")
else:
    md.append("Not run in this pass (the FPS phase runs last and only on the MacBook Air).")
md.append("")

# ------------------------------------------------------------------ server log scan
sl = read(run / "server" / "latest.log")
if sl:
    errs = scan_errors(sl)
    md.append("## Server suite log scan\n")
    md.append("%d ERROR/exception lines after noise filtering." % len(errs))
    for e in errs[:12]:
        md.append("- `%s`" % e.replace("|", "/"))
    md.append("")

out.write_text("\n".join(md) + "\n")
print("wrote", out, "verdict:", verdict)
