#!/usr/bin/env python3
"""s-clientload (3.0.0): write ElsewhereQA/reports/client-load-3.0.md and Review/client-load.html from reports/clientload/model.json.
Run: _tools/venv/bin/python -I ElsewhereQA/tools/clientload_report.py <ROOT>
"""
import html, json, re, sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve()
Q = ROOT / "ElsewhereQA"
R = Q / "reports" / "clientload"
M = json.loads((R / "model.json").read_text())
coef, preds, cal, chk = M["coef"], M["predictions"], M["calibration"], M["battle_check"]
tg = M.get("targets", {})
srv_path = R / "server" / "clientload.json"
srv = {s["id"]: s for s in json.loads(srv_path.read_text())["scenarios"]} if srv_path.exists() else {}
info_rows = [l.split("\t", 2) for l in (R / "calib" / "calib.tsv").read_text().splitlines() if l.startswith("INFO")] if (R / "calib" / "calib.tsv").exists() else []
info = info_rows[0][2] if info_rows else ""

# ---------------------------------------------------------------- background load
bg = (R / "calib" / "bg-samples.txt").read_text() if (R / "calib" / "bg-samples.txt").exists() else ""
loads = [float(x) for x in re.findall(r"Load Avg: ([0-9.]+)", bg)]
idle = [float(x) for x in re.findall(r"([0-9.]+)% idle", bg)]
swap = [float(x) for x in re.findall(r"swapusage: total = [0-9.]+M\s+used = ([0-9.]+)M", bg)]
freep = [int(x) for x in re.findall(r"free percentage: (\d+)%", bg)]
rss = [int(x) for x in re.findall(r"rss_mb (\d+)", bg)]
ccpu = [float(x) for x in re.findall(r"rss_mb \d+ cpu ([0-9.]+)", bg)]
before = bg.split("== ")[1] if "== " in bg else ""
top_before = [l for l in before.splitlines() if re.match(r"^\d+\s", l)][:8]


def avg(l):
    return sum(l) / len(l) if l else float("nan")


bgs = {"load_avg": avg(loads), "load_max": max(loads) if loads else float("nan"), "cpu_idle_pct": avg(idle), "swap_used_mb": avg(swap),
       "mem_free_pct": avg(freep), "client_rss_mb_max": max(rss) if rss else 0, "client_cpu_pct": avg(ccpu), "samples": len(loads)}

# ---------------------------------------------------------------- fix specs (what each owner must build, and the predicted effect)
def lod_effect(p):
    """s-lod: <=24 full; 24-48 no trims + static cape; 48-96 baked skin (1 pass + held); nearest 48 at full detail at most."""
    b = p["inputs"]["bands"]
    s = srv.get(p["id"], {}).get("census", {}).get("avg", {})
    n = max(1e-9, s.get("soldier", 0))
    armor, trims, held = s.get("soldierArmorPieces", 0) / n, s.get("soldierTrims", 0) / n, s.get("soldierHeld", 0) / n
    full = 1 + armor + trims + held
    view = p["inputs"]["soldiersInView"] / n if n > 1e-6 else 1
    cost_now = p["terms_ms"]["soldiers"]
    passes = (b[0] * full + b[1] * (full - trims) + (b[2] + b[3]) * (1 + held)) * view
    return passes * coef["pass_ms"], cost_now


def particle_cap(p, cap=400):
    live = p["inputs"]["liveParticles"]
    return min(live, cap) * coef["particle_ms"], p["terms_ms"]["particles"]


rows = []
for p in preds:
    if "fps" not in p:
        continue
    fixes = []
    ms = p["frame_ms"]
    if p.get("design"):
        t = p["terms_ms"]
        if p["id"] == "b9a-now" and (p["fps"] < 60 or p["fps_low"] < 60):
            sp = next((q for q in preds if q["id"] == "b9a-spec"), None)
            if sp:
                fixes.append(("s-lod", f"{tg['seen']} full soldiers cost {t['soldiers']:.1f} ms/frame; the {tg['live']} live ones with the 3.7 tiers cost "
                                       f"{sp['terms_ms']['soldiers']:.1f} ms ({tg['lod_passes']:.0f} passes instead of {tg['live'] * tg['full_passes']:.0f})", 0))
                fixes.append(("s-impostors", f"the other {tg['seen'] - tg['live']} drawn as {tg['ranks']} rank impostors at about one soldier each cost "
                                             f"{sp['terms_ms']['rank impostors']:.1f} ms instead of {(tg['seen'] - tg['live']) * coef['soldier_ms']:.1f} ms", 0))
                p["after_fix_fps"] = sp["fps"]
        elif p["id"] == "b9a-spec":
            idle_ms = 1000 / tg["idle_fps"] if tg.get("idle_fps") else t["terrain"]
            b0a_ms = 1000 / tg["b0a_fps"] if tg.get("b0a_fps") else idle_ms
            budget = min(idle_ms / 0.75, b0a_ms / 0.90, 1000 / 60) - t["terrain"]  # what the battle may add on top of terrain
            battle = p["frame_ms"] - t["terrain"]
            if battle > budget or p["low1"] < 40:
                tight = (tg["live"] * tg["share_bands"][0] * tg["full_passes"] + tg["live"] * (1 - tg["share_bands"][0]) * 2) * coef["pass_ms"]
                after = battle - t["soldiers"] + tight
                tail = ("which fits the budget" if after <= budget else
                        f"still {after - budget:.1f} ms over: on these numbers the B9a ratios cannot be met with {tg['seen']} soldiers in view, so the lead "
                        "decides between keeping the ratio and lowering the in-view count (fewer figures per rank or fewer ranks drawn beyond 96 blocks)")
                fixes.append(("s-lod", f"with the spec the battle adds {battle:.1f} ms per frame (soldiers {t['soldiers']:.1f}, ranks {t['rank impostors']:.1f}, particles "
                                       f"{t['particles']:.1f}); the B9a ratios (75% of idle, 90% of the 100-zombie scene) allow {budget:.1f} ms. Tighter tiers (full detail "
                                       f"only within 24 blocks, the baked skin plus one held item beyond) bring it to {after:.1f} ms, {tail}", 0))
                p["after_fix_fps"] = 1000 / (t["terrain"] + after)
        p["fixes"] = fixes
        rows.append(p)
        continue
    if p["fps"] < 60 or p["fps_low"] < 60:
        new_s, old_s = lod_effect(p)
        new_p, old_p = particle_cap(p)
        if old_s > 0.5:
            fixes.append(("s-lod", f"soldiers cost {old_s:.1f} ms/frame now; with the 3.7 tiers (full <=24, no trims 24-48, baked skin 48-96, nearest 48 full) "
                                   f"{new_s:.1f} ms", old_s - new_s))
        if old_p > 0.5:
            fixes.append(("s-p13-particles", f"particles cost {old_p:.1f} ms/frame ({p['inputs']['liveParticles']:.0f} live); a 400-live cap with crits only "
                                             f"within 24 and no poofs beyond 32 gives {new_p:.1f} ms", old_p - new_p))
        if p["terms_ms"].get("block entities", 0) > 0.5:
            fixes.append(("structures/court", f"block entities within 64 blocks cost {p['terms_ms']['block entities']:.1f} ms (banners, chests, signs)", 0))
        saved = sum(f[2] for f in fixes)
        p["after_fix_fps"] = 1000 / max(0.5, ms - saved)
    p["fixes"] = fixes
    rows.append(p)

# rank impostors (not in the measured jar): 12 ranks x 16 figures drawn with the baked skin
rank_cost_naive = 12 * 16 * 1.0 * coef["pass_ms"]  # one pass per figure, drawn as separate models
rank_cost_target = 12 * 3 * coef["soldier_ms"] / max(1e-9, coef["passes_per_soldier"]) * coef["passes_per_soldier"] / 3  # 1 soldier-eq per rank

# ---------------------------------------------------------------- markdown
def f1(x, d=1):
    return f"{x:.{d}f}" if isinstance(x, (int, float)) and x == x else "n/a"


L = []
L.append("# Client load and M1 Air FPS for 3.0.0 (s-clientload)\n")
L.append("Measured 2026-11-12 on the merged 11:34 jar (`ElsewhereBeta/dist/elsewhere-3.0.0.jar`). Server half on the iMac (dedicated server, view and simulation distance 10); "
         "client calibration on this MacBook (Apple M1, 8 GB, 7-core GPU, 2560x1600 panel), vanilla renderer, render distance 12, no Sodium.\n")
KINGDOM = ("b9a-spec", "b9a-now", "b15", "b24", "b32", "w150h", "w150a", "w150m", "r32a", "r32m", "r32h", "siege", "market", "castle")
krows = sorted((p for p in rows if p["id"] in KINGDOM), key=lambda p: KINGDOM.index(p["id"]))
L.append("## Kingdom scenes first: predicted M1 Air FPS\n")
L.append("Vanilla renderer, render distance 12, 1280x800 window; range = cost coefficients +-25%, terrain +-10%. Rows marked modelled are the ARMIES design-scale "
         "battle, which the 11:34 jar cannot stage yet.\n")
L.append("| Kingdom scene | FPS | Range | 1% low | Under 60? | Biggest cost |")
L.append("|---|---|---|---|---|---|")
for p in krows:
    under = "**yes**" if p["fps"] < 60 else ("at the low end" if p["fps_low"] < 60 else "no")
    L.append(f"| {p['title']}{' (modelled)' if p.get('design') else ''} | {f1(p['fps'],0)} | {f1(p['fps_low'],0)}-{f1(p['fps_high'],0)} | {f1(p['low1'],0)} | {under} | {p['driver']} |")
others = [p for p in rows if p["id"] not in KINGDOM]
L.append("\nOther scenes: " + "; ".join(f"{p['id']} {f1(p['fps'],0)} FPS ({f1(p['fps_low'],0)}-{f1(p['fps_high'],0)})" for p in others) + ". Full table below.\n")

L.append("## How it works\n")
L.append("1. **Server measurement** (`ElsewhereQA/src/main/java/dev/elsewhereqa/ClientLoad.java`, suite `clientload`). A measuring player (`ClientLoadProbe`) is a real "
         "`ServerPlayer` in the level and player list, so the chunk map tracks entities for it, particles and sounds reach it, and the kingdom LOD counts it as a watcher. "
         "Every clientbound packet is counted and encoded with the real play protocol for its size. Each scenario samples, once a second, the entities the client "
         "is tracking (by kind, render passes = body + armor pieces + trims + held items, soldier distance bands 0-24/24-48/48-96/96+) and, at the end, the block "
         "entities within view distance and within 64 blocks.")
L.append("2. **Client calibration** (`ElsewhereQA/src/gametest/java/dev/elsewhereqa/test/ClientCalib.java`, `-PqaClient=calib`, run by `ElsewhereQA/run-clientload-calib.sh`). "
         "A Fabric dev client with the offline name (no account, no launcher) times every frame on the render thread. Run 1: load A idle and load B with 16/32/64/128 "
         "armored soldiers in view. Run 2: idle again (drift), a live 32 v 32 battle, 2 particle loads, 60 hovering arrows, 200 patterned banners. The window closed itself.")
L.append("3. **Fit** (`ElsewhereQA/tools/clientload_fit.py`): frame ms = terrain + soldiers x c_soldier + other entities' passes x c_pass + projectiles x c_arrow + live "
         "particles x c_particle + rendered block entities x c_banner + packets. The gametest renders exactly one frame per client tick, so the client tick share "
         "is re-amortised to 20 ticks a second before fitting.")
L.append("4. **Predict** each server scene from its census (only entities inside the camera's horizontal view count) and give a range: coefficients +-25%, terrain +-10%.\n")

L.append("## Calibration runs (this MacBook)\n")
L.append(f"Setup: {info}\n")
L.append("| Run | Load | FPS | 1% low | mean ms | p95 ms | p99 ms | client tick ms | soldiers | live particles | heap MB | game FPS counter |")
L.append("|---|---|---|---|---|---|---|---|---|---|---|---|")
for r in cal:
    L.append(f"| {r['run']} | {r['label']} | {f1(r['fps'])} | {f1(r['low1'])} | {f1(r['meanMs'],2)} | {f1(r['p95Ms'],2)} | {f1(r['p99Ms'],2)} | {f1(r['clientTickMs'],3)} | "
             f"{f1(r['soldiersInWorld'],0)} | {f1(r['liveParticles'],0)} | {r['heapUsedMb']} | {f1(r['mcFps'])} |")
L.append("")
L.append("**Background load while it ran** (the swarm's builds plus the user's apps): "
         f"load average {f1(bgs['load_avg'])} (peak {f1(bgs['load_max'])}) on 8 cores, CPU idle {f1(bgs['cpu_idle_pct'])}%, swap used {f1(bgs['swap_used_mb'],0)} MB, "
         f"memory free {f1(bgs['mem_free_pct'],0)}%, client RSS peak {bgs['client_rss_mb_max']} MB, client CPU {f1(bgs['client_cpu_pct'],0)}%. "
         "So the numbers are a loaded-laptop floor: on a quiet Air the same scenes run faster (the range's high end).\n")

L.append("## Fitted cost model (M1, vanilla renderer)\n")
L.append("| Term | Cost per frame | Fit |")
L.append("|---|---|---|")
L.append(f"| Terrain base, RD 12 (idle A, A2) | {f1(coef['base_ms'],2)} ms ({f1(1000/coef['base_ms'],0)} FPS) | A {f1(coef['base_A_ms'],2)}, A2 {f1(coef['base_A2_ms'],2)} ms, drift {f1(coef['drift_pct'])}% |")
L.append(f"| Armored soldier in view | {f1(coef['soldier_ms']*1000,0)} us | R2 {f1(coef['soldier_r2'],3)}, slope 0-32: {f1(coef['soldier_slope_low_half']*1000,0)} us, 32-128: {f1(coef['soldier_slope_high_half']*1000,0)} us |")
L.append(f"| Render pass (body, armor piece, trim, held item) | {f1(coef['pass_ms']*1000,0)} us | {f1(coef['passes_per_soldier'],1)} passes per soldier (server census, 32 v 32) |")
L.append(f"| Live particle | {f1(coef['particle_ms']*1000,1)} us | " + (f"R2 {f1(coef['particle_r2'],3)} |" if coef.get("particle_measured", True) else
         "**assumed, not measured**: the two emitter rows read 0 live particles on the client, so there is no slope; battles here make under 25 particles/s, so it barely moves any prediction |"))
L.append(f"| Projectile (arrow) | {f1(coef['arrow_ms']*1000,0)} us | 60 hovering arrows |")
L.append(f"| Rendered block entity (patterned banner) | {f1(coef['banner_ms']*1000,0)} us | 200 banners |")
if chk:
    L.append(f"\nModel check on the live 32 v 32 battle: measured {f1(chk['measured_ms'],2)} ms per frame, predicted {f1(chk['predicted_ms'],2)} ms "
             f"({f1(chk['error_pct'])}% error; {f1(chk['soldiers'],0)} soldiers, {f1(chk['particles'],0)} live particles).\n")

L.append("## Server measurement per scene (what one watching client gets)\n")
L.append("| Scene | Entities tracked (soldiers / armored / mounts / projectiles / ranks) | Render passes | Packets/s | KB/s | Particles/s (server + client est) | BEs <=64 | MSPT |")
L.append("|---|---|---|---|---|---|---|---|")
for sid, s in srv.items():
    if "census" not in s:
        L.append(f"| {s['title']} | error: {s.get('error','')} | | | | | | |")
        continue
    c = s["census"]["avg"]
    L.append(f"| {s['title']} | {f1(c.get('all',0),0)} ({f1(c.get('soldier',0),0)} / {f1(c.get('armoredSoldier',0),0)} / {f1(c.get('mount',0),0)} / "
             f"{f1(c.get('projectile',0),0)} / {f1(c.get('rank',0),0)}) | {f1(s['census']['passesAvg'],0)} | {f1(s['net']['packetsPerSec'],0)} | {f1(s['net']['kbPerSec'])} | "
             f"{f1(s['particles']['serverPerSec'],0)} + {f1(s['particles']['clientEstPerSec'],0)} | {s['blockEntities']['within64']} | {f1(s['mspt']['avg'],2)} |")
L.append("")
L.append("How each scene was staged: " + "; ".join(f"**{sid}** {s.get('staged','')}" for sid, s in srv.items()) + "\n")

L.append("## Predicted FPS on the M1 Air (vanilla, RD 12, 1280x800 window)\n")
L.append("| Scene | FPS | Range | 1% low | Frame ms | Biggest cost | Under 60? |")
L.append("|---|---|---|---|---|---|---|")
for p in rows:
    under = "**yes**" if p["fps"] < 60 else ("at the low end" if p["fps_low"] < 60 else "no")
    L.append(f"| {p['title']}{' (modelled)' if p.get('design') else ''}{' (' + p['note'] + ')' if p.get('note') and not p.get('design') else ''} | {f1(p['fps'],0)} | {f1(p['fps_low'],0)}-{f1(p['fps_high'],0)} | {f1(p['low1'],0)} | {f1(p['frame_ms'],2)} | {p['driver']} | {under} |")
for p in preds:
    if "fps" not in p:
        L.append(f"| {p['title']} | n/a | | | | {p.get('error','')} | |")
L.append("")

if tg:
    b9b = tg["b9b"]
    sp = next((q for q in preds if q["id"] == "b9a-spec"), None)
    nw = next((q for q in preds if q["id"] == "b9a-now"), None)
    L.append("## ARMIES client targets (B9a, B9b)\n")
    L.append("| Target | Needed | This model (M1, vanilla) | Verdict |")
    L.append("|---|---|---|---|")
    L.append(f"| B9b: 40 live soldiers in view | FPS drop 10% or less | measured {f1(b9b['fps_empty'],0)} -> {f1(b9b['fps_40'],0)} FPS, drop {f1(b9b['drop_pct'])}% "
             f"(calibration sweep, full detail, no LOD) | {'pass' if b9b['drop_pct'] <= 10 else '**fails without s-lod**'} |")
    if nw and sp:
        idle_fps, b0a_fps = tg.get("idle_fps"), tg.get("b0a_fps")
        r_idle = sp["fps"] / idle_fps * 100 if idle_fps else float("nan")
        r_b0a = sp["fps"] / b0a_fps * 100 if b0a_fps else float("nan")
        ok = sp["fps"] >= 60 and sp["low1"] >= 40 and r_idle >= 75 and r_b0a >= 90
        L.append(f"| B9a: great battle, {tg['live']} live + about {tg['seen'] - tg['live']} more in view | 60+ FPS, 1% low 40+, at least 75% of idle and 90% of the 100-zombie scene | "
                 f"as drawn today {f1(nw['fps'],0)} FPS ({f1(nw['fps_low'],0)}-{f1(nw['fps_high'],0)}); with the 3.6/3.7 spec {f1(sp['fps'],0)} FPS "
                 f"({f1(sp['fps_low'],0)}-{f1(sp['fps_high'],0)}), 1% low {f1(sp['low1'],0)}, {f1(r_idle,0)}% of idle, {f1(r_b0a,0)}% of 100 zombies | "
                 f"{'pass with the spec' if ok else '**short even with the spec**'} |")
    L.append("")
    L.append("The 11:34 jar never shows the design-scale battle (its battles keep about 30 soldiers alive in view), so B9a is modelled: the soldier bands are "
             "the 150 v 150 scenes' distance mix, " + ", ".join(f"{a} {f1(b*100,0)}%" for a, b in zip(("0-24", "24-48", "48-96", "96+"), tg["share_bands"])) +
             f"; particles and packets scale from the 32 v 32 scene per live soldier. ARMIES measures B9a with Sodium, which cuts mostly the terrain base, so the "
             "entity share of the frame, and with it the 75%/90% ratios, gets harder there, not easier.\n")

L.append("## Under 60 FPS: cost drivers and fix specs for the owners\n")
anyfix = False
for p in rows:
    if not p["fixes"]:
        continue
    anyfix = True
    L.append(f"### {p['title']}: {f1(p['fps'],0)} FPS ({f1(p['fps_low'],0)}-{f1(p['fps_high'],0)}), driver: {p['driver']}")
    for owner, txt, saved in p["fixes"]:
        L.append(f"- **{owner}**: {txt}.")
    if "after_fix_fps" in p:
        L.append(f"- With those fixes: about **{f1(p['after_fix_fps'],0)} FPS**.")
    L.append("")
if not anyfix:
    L.append("No scene is predicted under 60 FPS at the range's low end.\n")
L.append("### Specs that hold for every scene\n")
L.append(f"- **s-lod** (ARMIES 3.7): at most 48 soldiers at full detail (nearest first); 24-48 blocks no trim passes and a static cape; 48-96 the baked 64x64 skin with only "
         f"banners and spears as held items. Each pass costs about {f1(coef['pass_ms']*1000,0)} us here, a full soldier {f1(coef['soldier_ms']*1000,0)} us. "
         "Self-test: a client gametest with 128 standing soldiers at 12-40 blocks must take no more than 0.45x the frame time of the same scene with LOD off.")
L.append(f"- **s-impostors** (ARMIES 3.6): a rank of 16 figures must cost about one soldier ({f1(coef['soldier_ms']*1000,0)} us), not 16 separate model draws "
         f"(16 soldier models would be {f1(16*coef['soldier_ms'],2)} ms per rank, {f1(12*16*coef['soldier_ms'],1)} ms for 12 ranks; even 16 single-pass figures "
         f"would be {f1(16*coef['pass_ms']*1000,0)} us per rank). Draw a rank's figures in one buffer "
         "(shared simplified humanoid, one baked skin, one draw per rank), cull per rank, no per-figure name or shadow.")
L.append(f"- **s-p13-particles** (POLISH 3, ARMIES 3.7): at most 400 live particles per client in a battle (a live particle costs about {f1(coef['particle_ms']*1000,1)} us here, so "
         f"400 cost {f1(400*coef['particle_ms'],1)} ms); crit and sweep particles only within 24 blocks; no death poofs beyond 32; telegraphs as P2 shapes "
         "(one payload per shape) instead of dust every tick.")
L.append("")
L.append("## Limits of this measurement (all stated, none hidden)\n")
L.append("- Vanilla renderer only; the ARMIES targets assume Sodium, which mostly cuts the terrain base, less so entities. The vanilla numbers are the conservative case.")
L.append("- The 11:34 merged jar has no rank impostors, townsfolk or client LOD yet (s-impostors, s-townsfolk and s-lod are building them now); their costs are "
         "the spec numbers above, applied to the measured counts. Re-run `run-clientload-server.sh` and `run-clientload-calib.sh` on the 14:00 merge to confirm.")
L.append("- 1% lows come from this MacBook while it was swapping (6+ GB of swap, load average 13-16, half the CPU in the kernel), so its worst frames are "
         "background stalls, not the game: the 1% low column is a loaded-machine figure, and the B9a 1% low criterion (40) cannot be judged from it. "
         "Average FPS and the per-item costs are what the model rests on.")
L.append("- Bytes are the encoded packet sizes before compression (threshold 256), so KB/s is an upper bound.")
L.append("- Client-side particles that the server never sees (crit emitters, death poofs, crit-arrow trails) are estimated from the events that cause them.")
L.append("- The calibration ran with the swarm's builds in the background (see above), so absolute FPS is the loaded floor; the slopes are what the model uses.")
L.append("- The battles keep fewer soldiers alive and tracked than the head count (waves, deaths, the kingdom's live cap): the 32 v 32 scene tracked about "
         f"{f1(srv.get('b32', {}).get('census', {}).get('avg', {}).get('soldier', 0), 0)} soldiers on average, the 150 v 150 wave scenes about "
         f"{f1(srv.get('w150h', {}).get('census', {}).get('avg', {}).get('soldier', 0), 0)}. The predictions use what the client is actually sent. If s-wave or "
         "the battle lanes raise the live front, re-run the server suite: every extra full-detail soldier in view costs the per-soldier figure above.")
L.append("- The server half (iMac) and the client calibration (MacBook) ran at the same time, one slot on each machine. They do not share hardware, so neither "
         "affected the other's numbers. Two calibration attempts were thrown away: 13:06 stopped at Fabric's 10-second chunk-render wait while the MacBook was "
         "saturated (wait lengthened), and 13:33 ran into vanilla's AFK frame limiter, which caps a client with no input for 60 s at 30 FPS, so idle and 32 "
         "soldiers both read 35 ms (the calibration now sets the inactivity limit to Minimized). KingdomApi also refuses soldiers past 32 in one area, so the "
         "sweep clones spawned soldiers (same type, armor, trims, held items) past that; the fit uses the soldiers actually standing there.")
L.append("")
L.append("## Files\n")
L.append("- `ElsewhereQA/src/main/java/dev/elsewhereqa/ClientLoad.java`, `ClientLoadProbe.java` (server suite), one case line in `ReleaseSuite.plan`.")
L.append("- `ElsewhereQA/src/gametest/java/dev/elsewhereqa/test/ClientCalib.java` (client calibration), entrypoint in the gametest `fabric.mod.json`, `calib` skip in `QAClientTest`.")
L.append("- `ElsewhereQA/run-clientload-server.sh`, `ElsewhereQA/run-clientload-calib.sh`, `ElsewhereQA/tools/clientload_fit.py`, `ElsewhereQA/tools/clientload_report.py`.")
L.append("- Raw data: `ElsewhereQA/reports/clientload/` (server/clientload.json, calib/calib.json, calib/bg-samples.txt, model.json, calibration screenshots).")
(Q / "reports" / "client-load-3.0.md").write_text("\n".join(L) + "\n")

# ---------------------------------------------------------------- HTML
def e(s):
    return html.escape(str(s))


bars = [p for p in rows]
bars.sort(key=lambda p: p["fps"])
vmax = max([p["fps_high"] for p in bars] + [120]) * 1.08
W, rowh, left = 760, 30, 190
H = 40 + rowh * len(bars) + 30


def x(v):
    return left + (W - left - 20) * min(v, vmax) / vmax


svg = [f'<svg viewBox="0 0 {W} {H}" role="img" aria-label="Predicted FPS per scene on the M1 Air" class="chart">']
for t in range(0, int(vmax) + 1, 30):
    svg.append(f'<line x1="{x(t):.1f}" x2="{x(t):.1f}" y1="24" y2="{H-26}" class="grid"/><text x="{x(t):.1f}" y="{H-8}" class="tick" text-anchor="middle">{t}</text>')
svg.append(f'<line x1="{x(60):.1f}" x2="{x(60):.1f}" y1="18" y2="{H-26}" class="ref"/><text x="{x(60)+4:.1f}" y="16" class="reflab">60 FPS</text>')
for i, p in enumerate(bars):
    y = 30 + i * rowh
    under = p["fps"] < 60
    tip = f"{p['title']}: {p['fps']:.0f} FPS (range {p['fps_low']:.0f}-{p['fps_high']:.0f}), 1% low {p['low1']:.0f}, frame {p['frame_ms']:.1f} ms, driver {p['driver']}"
    svg.append(f'<g class="mark" data-tip="{e(tip)}" tabindex="0"><rect x="{left}" y="{y}" width="{W-left}" height="{rowh}" class="hit"/>'
               f'<text x="{left-8}" y="{y+19}" class="lab" text-anchor="end">{e(p["id"])}{" (model)" if p.get("design") else ""} {"&#9888;" if under else ""}</text>'
               f'<rect x="{left}" y="{y+6}" width="{max(2, x(p["fps"])-left):.1f}" height="{rowh-12}" rx="4" class="{"bar under" if under else "bar"}"/>'
               f'<line x1="{x(p["fps_low"]):.1f}" x2="{x(p["fps_high"]):.1f}" y1="{y+rowh/2}" y2="{y+rowh/2}" class="whisk"/>'
               f'<text x="{x(max(p["fps"], p["fps_high"]))+6:.1f}" y="{y+19}" class="val">{p["fps"]:.0f}</text></g>')
svg.append("</svg>")

trs = []
_k = ("b9a-spec", "b9a-now", "b15", "b24", "b32", "w150h", "w150a", "w150m", "r32a", "r32m", "r32h", "siege", "market", "castle")
for p in sorted(rows, key=lambda p: (_k.index(p["id"]) if p["id"] in _k else 99)):
    under = p["fps"] < 60
    stat = '<span class="st bad">&#9888; under 60</span>' if under else ('<span class="st warn">&#9888; low end under 60</span>' if p["fps_low"] < 60 else '<span class="st ok">&#10003; 60+</span>')
    trs.append(f"<tr><td>{e(p['title'])}</td><td class=n>{p['fps']:.0f}</td><td class=n>{p['fps_low']:.0f}&ndash;{p['fps_high']:.0f}</td><td class=n>{p['low1']:.0f}</td>"
               f"<td class=n>{p['frame_ms']:.1f}</td><td>{e(p['driver'])}</td><td>{stat}</td></tr>")
fixes = []
for p in rows:
    for owner, txt, _ in p["fixes"]:
        fixes.append(f"<li><b>{e(p['id'])}</b> &rarr; <b>{e(owner)}</b>: {e(txt)}</li>")
calrows = "".join(f"<tr><td>{e(r['run'])}</td><td>{e(r['label'])}</td><td class=n>{r['fps']:.0f}</td><td class=n>{r['low1']:.0f}</td><td class=n>{r['meanMs']:.2f}</td>"
                  f"<td class=n>{r['p95Ms']:.2f}</td><td class=n>{r['soldiersInWorld']:.0f}</td><td class=n>{r['liveParticles']:.0f}</td><td class=n>{r['heapUsedMb']}</td></tr>" for r in cal)
tgt_html = ""
if tg:
    b9b = tg["b9b"]
    sp = next((q for q in preds if q["id"] == "b9a-spec"), None)
    nw = next((q for q in preds if q["id"] == "b9a-now"), None)
    def badge(ok):
        return '<span class="st ok">&#10003; pass</span>' if ok else '<span class="st bad">&#9888; short</span>'
    trs_t = [f"<tr><td>B9b: 40 live soldiers in view</td><td>drop &le; 10%</td><td>{b9b['fps_empty']:.0f} &rarr; {b9b['fps_40']:.0f} FPS, drop {b9b['drop_pct']:.1f}% "
             f"(measured, no LOD)</td><td>{badge(b9b['drop_pct'] <= 10)}</td></tr>"]
    if sp and nw:
        idle_fps, b0a_fps = tg.get("idle_fps") or 0, tg.get("b0a_fps") or 0
        r_idle = sp["fps"] / idle_fps * 100 if idle_fps else 0
        r_b0a = sp["fps"] / b0a_fps * 100 if b0a_fps else 0
        ok = sp["fps"] >= 60 and sp["low1"] >= 40 and r_idle >= 75 and r_b0a >= 90
        trs_t.append(f"<tr><td>B9a: great battle at design scale ({tg['live']} live + {tg['seen'] - tg['live']} in ranks)</td><td>60+ FPS, 1% low 40+, &ge;75% of idle, "
                     f"&ge;90% of 100 zombies</td><td>today {nw['fps']:.0f} FPS; with the spec {sp['fps']:.0f} ({sp['fps_low']:.0f}&ndash;{sp['fps_high']:.0f}), 1% low "
                     f"{sp['low1']:.0f}, {r_idle:.0f}% of idle, {r_b0a:.0f}% of 100 zombies (modelled)</td><td>{badge(ok)}</td></tr>")
    tgt_html = ('<h2>ARMIES client targets</h2><div class="card scroll"><table><thead><tr><th>Target</th><th>Needed</th><th>Result</th><th>Verdict</th></tr></thead><tbody>'
                + "".join(trs_t) + "</tbody></table></div>")
md_html = "".join(f"<p>{e(l)}</p>" for l in [])
page = f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>Client Load 3.0</title>
<style>
:root {{ color-scheme: light; --bg:#fcfcfb; --surface:#ffffff; --ink:#0b0b0b; --ink2:#52514e; --muted:#8a8984; --line:#e4e3de; --grid:#ecebe7;
  --series:#2a78d6; --good:#0ca30c; --warn:#fab219; --serious:#ec835a; --critical:#d03b3b; }}
@media (prefers-color-scheme: dark) {{ :root:not([data-theme="light"]) {{ color-scheme: dark; --bg:#141413; --surface:#1a1a19; --ink:#f0efec; --ink2:#c3c2b7;
  --muted:#8f8e86; --line:#33332f; --grid:#2a2a27; --series:#3987e5; }} }}
:root[data-theme="dark"] {{ color-scheme: dark; --bg:#141413; --surface:#1a1a19; --ink:#f0efec; --ink2:#c3c2b7; --muted:#8f8e86; --line:#33332f; --grid:#2a2a27; --series:#3987e5; }}
* {{ box-sizing: border-box; }}
body {{ margin:0; background:var(--bg); color:var(--ink); font:15px/1.5 -apple-system, "Segoe UI", system-ui, sans-serif; }}
main {{ max-width: 980px; margin: 0 auto; padding: 24px 16px 48px; }}
h1 {{ font-size: 24px; margin: 0 0 4px; }} h2 {{ font-size: 18px; margin: 28px 0 8px; }}
.sub {{ color: var(--ink2); margin: 0 0 16px; }}
.card {{ background: var(--surface); border: 1px solid var(--line); border-radius: 10px; padding: 14px; margin: 12px 0; }}
.tiles {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; }}
.tile b {{ display:block; font-size: 22px; }} .tile span {{ color: var(--ink2); font-size: 13px; }}
.scroll {{ overflow-x: auto; }}
table {{ border-collapse: collapse; width: 100%; font-size: 13px; }}
th, td {{ text-align: left; padding: 6px 8px; border-bottom: 1px solid var(--line); vertical-align: top; }}
th {{ color: var(--ink2); font-weight: 600; }} td.n {{ text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }}
.chart {{ width: 100%; height: auto; display: block; }}
.chart .grid {{ stroke: var(--grid); }} .chart .tick, .chart .reflab {{ fill: var(--muted); font-size: 11px; }}
.chart .ref {{ stroke: var(--ink2); stroke-dasharray: 4 3; }} .chart .lab {{ fill: var(--ink); font-size: 12px; }} .chart .val {{ fill: var(--ink2); font-size: 12px; }}
.chart .bar {{ fill: var(--series); }} .chart .bar.under {{ fill: var(--serious); }} .chart .whisk {{ stroke: var(--ink2); stroke-width: 2; }}
.chart .hit {{ fill: transparent; }} .chart .mark:hover .hit, .chart .mark:focus .hit {{ fill: var(--grid); }}
#tip {{ position: fixed; pointer-events: none; background: var(--surface); color: var(--ink); border: 1px solid var(--line); border-radius: 6px; padding: 6px 8px;
  font-size: 12px; max-width: 320px; display: none; box-shadow: 0 2px 8px rgba(0,0,0,.15); }}
.st {{ white-space: nowrap; }} .st.bad {{ color: var(--critical); }} .st.warn {{ color: var(--ink2); }} .st.ok {{ color: var(--good); }}
ul {{ padding-left: 20px; }} code {{ font-size: 12px; }}
</style></head><body><main>
<h1>Client load and M1 Air FPS, 3.0.0</h1>
<p class="sub">Merged 11:34 jar. Server load measured on the iMac per scene; the cost of each thing measured on this MacBook (Apple M1, 8 GB, 7-core GPU), vanilla renderer, render distance 12. Absolute FPS is this MacBook's floor while it was loaded (swap in use, load average about {bgs['load_avg']:.0f}); a quiet Air runs faster, and the per-item costs and ratios are what carry over.</p>
<div class="tiles">
<div class="card tile"><b>{1000/coef['base_ms']:.0f} FPS</b><span>idle terrain base ({coef['base_ms']:.2f} ms)</span></div>
<div class="card tile"><b>{coef['soldier_ms']*1000:.0f} &micro;s</b><span>per armored soldier in view</span></div>
<div class="card tile"><b>{coef['particle_ms']*1000:.1f} &micro;s</b><span>per live particle{'' if coef.get('particle_measured', True) else ' (assumed, not measured)'}</span></div>
<div class="card tile"><b>{sum(1 for p in rows if p['fps'] < 60 and not p.get('design'))} of {sum(1 for p in rows if not p.get('design'))}</b><span>measured scenes predicted under 60 FPS</span></div>
</div>
{tgt_html}
<h2>Predicted FPS per scene</h2>
<div class="card">{''.join(svg)}<p class="sub" style="margin:6px 0 0">Bar: predicted average FPS. Whisker: range (cost coefficients &plusmn;25%, terrain &plusmn;10%). Orange with &#9888;: under 60. Dashed: 60 FPS. &ldquo;(model)&rdquo;: design-scale battle, modelled because the 11:34 jar cannot stage it yet.</p></div>
<div class="card scroll"><table><thead><tr><th>Scene</th><th>FPS</th><th>Range</th><th>1% low</th><th>Frame ms</th><th>Biggest cost</th><th>Status</th></tr></thead><tbody>{''.join(trs)}</tbody></table></div>
<h2>Fix specs for the owners</h2>
<div class="card"><ul>{''.join(fixes) or '<li>No scene under 60 FPS.</li>'}</ul>
<p class="sub">Full specs, fit quality and limits: <code>ElsewhereQA/reports/client-load-3.0.md</code>.</p></div>
<h2>Calibration runs on this MacBook</h2>
<p class="sub">Background load during the runs: load average {bgs['load_avg']:.1f} on 8 cores, CPU idle {bgs['cpu_idle_pct']:.0f}%, swap {bgs['swap_used_mb']:.0f} MB used, client RSS peak {bgs['client_rss_mb_max']} MB.</p>
<div class="card scroll"><table><thead><tr><th>Run</th><th>Load</th><th>FPS</th><th>1% low</th><th>Mean ms</th><th>p95 ms</th><th>Soldiers</th><th>Particles</th><th>Heap MB</th></tr></thead><tbody>{calrows}</tbody></table></div>
</main><div id="tip" role="tooltip"></div>
<script>
const tip = document.getElementById('tip');
document.querySelectorAll('.mark').forEach(m => {{
  const show = ev => {{ tip.textContent = m.dataset.tip; tip.style.display = 'block';
    const r = ev.clientX !== undefined ? {{x: ev.clientX, y: ev.clientY}} : (() => {{ const b = m.getBoundingClientRect(); return {{x: b.left + 40, y: b.top}}; }})();
    tip.style.left = Math.min(window.innerWidth - 330, r.x + 12) + 'px'; tip.style.top = (r.y + 12) + 'px'; }};
  m.addEventListener('mousemove', show); m.addEventListener('focus', show);
  m.addEventListener('mouseleave', () => tip.style.display = 'none'); m.addEventListener('blur', () => tip.style.display = 'none');
}});
</script></body></html>
"""
(ROOT / "Review" / "client-load.html").write_text(page)
print("wrote", Q / "reports" / "client-load-3.0.md", "and", ROOT / "Review" / "client-load.html")
