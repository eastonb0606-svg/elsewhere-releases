#!/usr/bin/env python3
"""s-clientload (3.0.0): fit the M1 client cost model from the calibration run and predict FPS for every server-measured scene.

Inputs : reports/clientload/calib/calib.json (+ bg-samples.txt), reports/clientload/server/clientload.json
Output : reports/clientload/model.json (coefficients, checks, predictions) -- the report and the HTML page are written from it.
Run    : _tools/venv/bin/python -I ElsewhereQA/tools/clientload_fit.py <ElsewhereQA dir>
"""
import json, math, re, sys
from pathlib import Path

Q = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
R = Q / "reports" / "clientload"
cal = [r for r in json.loads((R / "calib" / "calib.json").read_text()) if r]
srv_path = R / "server" / "clientload.json"
srv = json.loads(srv_path.read_text())["scenarios"] if srv_path.exists() else []


def row(label):
    for r in cal:
        if r["label"] == label:
            return r
    return None


def lsq(xs, ys):
    n = len(xs)
    mx, my = sum(xs) / n, sum(ys) / n
    sxx = sum((x - mx) ** 2 for x in xs)
    sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    b = sxy / sxx if sxx else 0.0
    a = my - b * mx
    res = [y - (a + b * x) for x, y in zip(xs, ys)]
    se = math.sqrt(sum(e * e for e in res) / max(1, n - 2) / sxx) if sxx and n > 2 else abs(b) * 0.15
    r2 = 1 - sum(e * e for e in res) / max(1e-12, sum((y - my) ** 2 for y in ys))
    return a, b, se, r2


# ---------------------------------------------------------------- lockstep -> free-running frame time
# The Fabric client gametest renders one frame per client tick; a real client ticks 20 times a second whatever its frame rate.
def free_ms(r):
    f = r["meanMs"]
    tick = r["clientTickMs"]
    per_frame_ticks = r["ticks"] / max(1, r["frames"])  # ~1 in lockstep
    # solve f_real = f - tick*per_frame_ticks + tick * 20 * f_real / 1000
    k = tick * 20 / 1000.0
    return max(0.5, (f - tick * per_frame_ticks) / max(0.2, 1 - k))


A = row("A idle")
A2 = row("A2 idle") or A
# x = soldiers actually standing in the world when the row was measured (the label is the plan; KingdomApi caps an area at 32)
sweep = [(0, A)] + [(r.get("soldiersInWorld") or int(re.findall(r"\d+", r["label"])[0]), r) for r in cal if r["label"].startswith("B ")]
sx = [n for n, _ in sweep]
sy = [free_ms(r) for _, r in sweep]
s_a, s_b, s_se, s_r2 = lsq(sx, sy)
# quadratic check: slope of the upper half vs lower half
lo = lsq(sx[:3], sy[:3])[1] if len(sx) >= 3 else s_b
hi = lsq(sx[-3:], sy[-3:])[1] if len(sx) >= 3 else s_b

pts = [(0.0, free_ms(A2))]
for r in cal:
    if r["label"].startswith("particles"):
        pts.append((r["liveParticles"], free_ms(r)))
p_a, p_b, p_se, p_r2 = lsq([p[0] for p in pts], [p[1] for p in pts]) if len(pts) >= 2 else (0, 0, 0, 0)
PART_MEASURED = any(p[0] > 0 for p in pts[1:])
if not PART_MEASURED:
    # the emitter rows read 0 live particles on the client, so the slope is not measurable: use 2 us per live particle (vanilla, M1) and say so
    p_b, p_se, p_r2 = 0.002, 0.001, float("nan")

arr = row("60 arrows in view")
c_arrow = (free_ms(arr) - free_ms(A2)) / 60 if arr else 0.0
ban = row("200 banners in view")
c_be = (free_ms(ban) - free_ms(A2)) / 200 if ban else 0.0
bat = row("battle 32v32 live")

base = (free_ms(A) + free_ms(A2)) / 2
drift = (free_ms(A2) - free_ms(A)) / free_ms(A) if A and A2 else 0
coef = {
    "base_ms": base, "base_A_ms": free_ms(A), "base_A2_ms": free_ms(A2), "drift_pct": drift * 100,
    "soldier_ms": max(0.0, s_b), "soldier_se": s_se, "soldier_r2": s_r2, "soldier_slope_low_half": lo, "soldier_slope_high_half": hi,
    "particle_ms": max(0.0, p_b), "particle_se": p_se, "particle_r2": p_r2,
    "arrow_ms": max(0.0, c_arrow), "banner_ms": max(0.0, c_be),
    "lockstep_frames_per_tick": A["frames"] / max(1, A["ticks"]) if A else 1, "particle_measured": PART_MEASURED,
}
# render passes per calibration soldier: the calibration soldiers are the same unit kits the battles use; the server census of the
# 32 v 32 battle gives passes per soldier (body + armor pieces + trims + held items)
PASS_SOLDIER = None
for s in srv:
    if s.get("id") == "b32" and "census" in s:
        c = s["census"]["avg"]
        if c.get("soldier", 0) > 0:
            PASS_SOLDIER = 1 + (c.get("soldierArmorPieces", 0) + c.get("soldierTrims", 0) + c.get("soldierHeld", 0)) / c["soldier"]
if not PASS_SOLDIER:
    PASS_SOLDIER = 6.0
coef["passes_per_soldier"] = PASS_SOLDIER
coef["pass_ms"] = coef["soldier_ms"] / PASS_SOLDIER

# battle check: predicted vs measured live battle on the same client
check = None
if bat:
    pred = base + coef["soldier_ms"] * bat["soldiersInWorld"] + coef["particle_ms"] * bat["liveParticles"]
    check = {"measured_ms": free_ms(bat), "predicted_ms": pred, "soldiers": bat["soldiersInWorld"], "particles": bat["liveParticles"],
             "error_pct": (pred - free_ms(bat)) / free_ms(bat) * 100}

BER = ("banner", "sign", "hanging_sign", "chest", "trapped_chest", "ender_chest", "shulker_box", "bed", "bell", "skull", "decorated_pot", "lectern",
       "campfire", "enchanting_table", "beacon", "conduit", "brushable_block", "vault", "trial_spawner", "mob_spawner", "creaking_heart", "shelf", "copper_golem_statue")
LIFE = {"dust": 1.0, "flame": 0.7, "crit": 0.5, "poof": 0.5, "damage_indicator": 1.0, "sweep_attack": 0.2, "smoke": 1.0, "electric_spark": 0.3, "end_rod": 2.0,
        "enchant": 1.5, "soul_fire_flame": 0.7, "portal": 1.5, "witch": 1.0, "dust_color_transition": 1.0, "glow": 1.0, "cloud": 1.0}


def life(k):
    k = k.split(":")[-1]
    return LIFE.get(k, 0.8)


preds = []
for s in srv:
    if "census" not in s:
        preds.append({"id": s["id"], "title": s["title"], "error": s.get("error", "no data")})
        continue
    c = s["census"]["avg"]
    allv = max(1e-9, c.get("all", 0))
    view = min(1.0, s["census"].get("inViewAvg", allv) / allv) if allv > 1e-6 else 1.0
    sold = c.get("soldier", 0) * view
    sold_pass = c.get("soldier", 0) + c.get("soldierArmorPieces", 0) + c.get("soldierTrims", 0) + c.get("soldierHeld", 0)
    proj = c.get("projectile", 0) * view
    other_pass = max(0.0, s["census"].get("passesAvg", 0) - sold_pass - c.get("projectile", 0)) * view
    note = ""
    if s["id"] == "idle" and c.get("item", 0) > 50:
        # the arena was cleared with setBlock just before this scene; its block drops lay there until the first teardown
        other_pass = max(0.0, other_pass - c["item"] * view)
        note = f"{c['item']:.0f} item drops from clearing the arena excluded"
    part_rate = s["particles"]["serverPerSec"] + s["particles"]["clientEstPerSec"]
    live = 0.0
    for k, v in s["particles"]["byType"].items():
        live += v * life(k)
    live += s["particles"]["clientEstPerSec"] * 0.5
    bes = sum(v for k, v in s["blockEntities"]["within64ByType"].items() if any(k.endswith(":" + b) or k.split(":")[-1].endswith(b) for b in BER))
    pps = s["net"]["packetsPerSec"]
    terms = {
        "terrain": base,
        "soldiers": coef["soldier_ms"] * sold,
        "other entities": coef["pass_ms"] * other_pass,
        "projectiles": coef["arrow_ms"] * proj,
        "particles": coef["particle_ms"] * live,
        "block entities": coef["banner_ms"] * bes,
    }
    ms = sum(terms.values())
    # packet handling on the client's network thread + main-thread apply: about 2 us per packet (amortised per frame)
    fps0 = 1000 / ms
    terms["packets"] = pps * 0.002 / max(1, fps0)
    ms = sum(terms.values())
    # range: coefficients +-25% (or their standard error if larger), terrain +-10%
    var = sum(v for k, v in terms.items() if k != "terrain")
    lo_ms = terms["terrain"] * 1.10 + var * 1.25
    hi_ms = terms["terrain"] * 0.90 + var * 0.75
    low1_ratio = 0.55
    near = bat if (bat and sold > 20) else A
    if near:
        low1_ratio = max(0.2, min(0.95, near["low1"] / max(1e-6, near["fps"])))
    drivers = sorted(((k, v) for k, v in terms.items() if k != "terrain"), key=lambda kv: -kv[1])
    preds.append({
        "id": s["id"], "title": s["title"], "staged": s.get("staged", ""), "note": note,
        "inputs": {"soldiersInView": sold, "soldiersTracked": c.get("soldier", 0), "armoredSoldiers": c.get("armoredSoldier", 0), "mounts": c.get("mount", 0),
                   "projectiles": c.get("projectile", 0), "ranks": c.get("rank", 0), "otherPasses": other_pass, "liveParticles": live,
                   "particleRate": part_rate, "blockEntitiesRendered64": bes, "packetsPerSec": pps, "kbPerSec": s["net"]["kbPerSec"],
                   "msptServer": s["mspt"]["avg"], "bands": [c.get("soldier0to24", 0), c.get("soldier24to48", 0), c.get("soldier48to96", 0), c.get("soldierOver96", 0)]},
        "terms_ms": terms, "frame_ms": ms, "fps": 1000 / ms, "fps_low": 1000 / lo_ms, "fps_high": 1000 / hi_ms,
        "low1": 1000 / ms * low1_ratio, "driver": drivers[0][0] if drivers and drivers[0][1] > 0.05 * ms else "terrain",
    })

# ---------------------------------------------------------------- ARMIES targets at design scale (modelled: the 11:34 jar has no ranks or client LOD)
# B9b straight from the calibration sweep (standing armored soldiers 12-40 blocks ahead, full detail): FPS with 40 against the empty scene
def fps_at(n):
    pts = sorted(zip(sx, sy))
    for (n0, m0), (n1, m1) in zip(pts, pts[1:]):
        if n0 <= n <= n1:
            return 1000 / (m0 + (m1 - m0) * (n - n0) / max(1, n1 - n0))
    return 1000 / (s_a + s_b * n)


b9b = {"fps_empty": 1000 / sy[0], "fps_40": fps_at(40)}
b9b["drop_pct"] = (1 - b9b["fps_40"] / b9b["fps_empty"]) * 100
# B9a: 2 x 40 live front + about 140 more figures in view (ARMIES 0: about 220 seen at once), watched from 40 blocks
cb = next((s["census"]["avg"] for s in srv if s.get("id") == "b32" and "census" in s), {})
nb = max(1e-9, cb.get("soldier", 0))
armor_ps, trim_ps, held_ps = (cb.get("soldierArmorPieces", 0) / nb, cb.get("soldierTrims", 0) / nb, cb.get("soldierHeld", 0) / nb) if cb else (4.0, 4.0, 1.6)
full = 1 + armor_ps + trim_ps + held_ps
bs = [0.0] * 4
for s in srv:
    if s.get("id", "").startswith("w150") and "census" in s:
        for i, k in enumerate(("soldier0to24", "soldier24to48", "soldier48to96", "soldierOver96")):
            bs[i] += s["census"]["avg"].get(k, 0)
share = [b / sum(bs) for b in bs] if sum(bs) > 0 else [0.1, 0.45, 0.45, 0.0]
p32 = next((q for q in preds if q["id"] == "b32" and "fps" in q), None)
part_per_soldier = p32["inputs"]["liveParticles"] / max(1e-9, p32["inputs"]["soldiersTracked"]) if p32 else 0.6
pps_per_soldier = p32["inputs"]["packetsPerSec"] / max(1e-9, p32["inputs"]["soldiersTracked"]) if p32 else 27
LIVE, SEEN = 80, 220
RANKS = math.ceil((SEEN - LIVE) / 12)
low1_battle = max(0.2, min(0.95, bat["low1"] / max(1e-6, bat["fps"]))) if bat else 0.55


def design(pid, title, terms, var_lo=1.25, var_hi=0.75, extra_lo=0.0, extra_hi=0.0, note=""):
    terms = dict(terms)
    ms = sum(terms.values())
    terms["packets"] = pps_per_soldier * LIVE * 0.002 / max(1, 1000 / ms)
    ms = sum(terms.values())
    var = ms - terms["terrain"]
    lo_ms = terms["terrain"] * 1.10 + var * var_lo + extra_lo
    hi_ms = terms["terrain"] * 0.90 + var * var_hi - extra_hi
    drivers = sorted(((k, v) for k, v in terms.items() if k != "terrain"), key=lambda kv: -kv[1])
    return {"id": pid, "title": title, "design": True, "note": note, "terms_ms": terms, "frame_ms": ms, "fps": 1000 / ms,
            "fps_low": 1000 / lo_ms, "fps_high": 1000 / hi_ms, "low1": 1000 / ms * low1_battle,
            "driver": drivers[0][0] if drivers and drivers[0][1] > 0.05 * ms else "terrain",
            "inputs": {"soldiersInView": SEEN, "liveSoldiers": LIVE, "ranks": RANKS, "liveParticles": part_per_soldier * LIVE, "bands": share}}


parts_ms = coef["particle_ms"] * part_per_soldier * LIVE
naive = design("b9a-now", f"B9a great battle at design scale, as drawn today ({SEEN} full soldiers)",
               {"terrain": base, "soldiers": coef["soldier_ms"] * SEEN, "particles": parts_ms},
               note=f"{LIVE} live + {SEEN - LIVE} more in view, every one a full-detail soldier (no client LOD, no rank impostors)")
lod_passes = LIVE * (share[0] * full + share[1] * (full - trim_ps) + (share[2] + share[3]) * (1 + held_ps))
spec = design("b9a-spec", f"B9a great battle at design scale with the 3.6/3.7 spec ({LIVE} live with LOD tiers + {RANKS} rank impostors)",
              {"terrain": base, "soldiers": lod_passes * coef["pass_ms"], "rank impostors": RANKS * coef["soldier_ms"], "particles": parts_ms},
              extra_lo=RANKS * coef["soldier_ms"] * 2, note="ranks at 1 soldier each; the range's low end allows 3 (ARMIES 3.6: about 1-3)")
preds += [naive, spec]
targets = {"b9b": b9b, "b9a_now": naive["fps"], "b9a_spec": spec["fps"], "b9a_spec_low1": spec["low1"],
           "share_bands": share, "live": LIVE, "seen": SEEN, "ranks": RANKS, "lod_passes": lod_passes, "full_passes": full,
           "idle_fps": next((q["fps"] for q in preds if q["id"] == "idle" and "fps" in q), None),
           "b0a_fps": next((q["fps"] for q in preds if q["id"] == "b0a" and "fps" in q), None)}
print("targets", json.dumps(targets))

out = {"coef": coef, "battle_check": check, "calibration": cal, "predictions": preds, "targets": targets}
(R / "model.json").write_text(json.dumps(out, indent=1))
print(json.dumps(coef, indent=1))
print("battle check", check)
for p in preds:
    if "fps" in p:
        print(f'{p["id"]:8s} {p["fps"]:6.0f} fps ({p["fps_low"]:.0f}-{p["fps_high"]:.0f}), 1% low {p["low1"]:.0f}, driver {p["driver"]}')
    else:
        print(p["id"], p.get("error"))
