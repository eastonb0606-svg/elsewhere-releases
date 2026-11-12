# Elsewhere arsenal (dev module `elsewhere_arsenal`) — NOTES

Design: `ElsewhereContent/ARSENAL.md` **revision 4** (Calling numbers, −5% caster con, costs rounded to 0.1) and BALANCE.md §D (display switches). Every number is in `dev.elsewhere.arsenal.ArsenalStats`.
Jar: `dist/elsewhere_arsenal-0.1.0.jar`. Self-test: `run-desk/selftest-1.log` (desk.sh runServer). Screenshots: `screenshots/arsenal_*.png`.

## What is built
- **Glimmer** (`dev.elsewhere.arsenal.glimmer.Glimmer`): float pool in player attachment `elsewhere:glimmer` (persistent), max = `elsewhere:max_glimmer` − minion reserve, regen 3/s × `glimmer_regen` after 1.0 s (Moonlit 0.5 s; Stormsage Mantle airborne: instant and ×2), 0.05 exhaustion per Glimmer (not on Peaceful), no regen at hunger ≤ 6, −10% regen per minion, Glimmer Tonic surge ×2 for 10 s, respawn at half max, dimension change keeps it. Synced by `GlimmerPayload` (`elsewhere:glimmer`, cur/max/reserved/minions/failNeed) when it changes by ≥ 1, on fail, on join, plus a 5 s keep-alive.
- **9 attributes** `elsewhere:max_glimmer, glimmer_regen, spell_power, spell_cost, projectile_pierce, minion_slots, minion_damage, dodge_chance, kill_heal` (added to players; an already-registered holder with the same id is reused). Hooks: dodge (non-boss mob melee only, cap 0.25, whoosh + cloud), kill heal (Enemy, not bosses / boss fights, 1/s), projectile_pierce on arrows, caster-piece arrow con.
- **`elsewhere:spell` damage type**, tagged `#minecraft:is_projectile`. `Spells.damage` = base × spell_power × caster Calling × Dazed × Test Meter half bonus × Moon-Eye × Moonlit.
- **Calling** (`dev.elsewhere.arsenal.spell.Calling`): pieces worn in `#elsewhere:caster_armor` / `#elsewhere:summoner_armor` (also mirrored as `#elsewhere:calling/caster`). Rev 4 table: damage ×0.80/0.90/0.95/0.98/1.00, cost ×1.15/1.08/1.04/1.02/1.00, minion reserve 6/5/4/4/4. Starter items count as Calling 2+. Caster pieces −5% melee (attribute modifier) and −5% arrows (hook) each. Tooltip line on every spell item.
- **Trinkets** (`dev.elsewhere.arsenal.trinket.Trinkets`): `#elsewhere:trinket` items in off hand or hotbar, max 2 distinct (off hand first, then slots 1→9); Glimmerstone Focus +8 max / +25% regen (needs a caster piece worn). Amethyst corner marks on active hotbar slots (switch `trinketMarks`).
- **Test Meter half bonus**: `Spells.testMeterHalf(Player)` = 1 + (m − 1)/2, reflective soft link to `dev.testmeter.TestMeter.data` / `Physiology.damageMultiplier`; 1.0 without Test Meter.
- **Items** (all `elsewhere:`): `amethyst_wand` (bolt 4 dmg, cost 2, 0.4 s), `sunlance_staff` (instant lance 24 blocks, 9 dmg ×3 targets, fire 2 s, through glass, cost 4/5/6 by light), `stormcoil_staff` (chain lightning 8 + 2 arcs × 5, 4 arcs if the target is wet, self-arc 2 if you stand in water near a struck mob, cost 7), `sculk_resonator` (charge 1.25 s, sonic line 20 blocks, 14 dmg ignoring half armor, KB 1.5, cost 10, game event), `moonsilk_hood/robe/leggings/boots`, `lined_moonsilk_*` (smithing), `stormsage_mantle`, `cantors_diadem`, `glimmerstone_focus`, `glimmer_tonic` (12 + surge + Dazed), `strong_glimmer_tonic` (20), `moonsilk_thread`. Potions `elsewhere:clarity_draught` (3 min) and `elsewhere:long_clarity_draught` (8 min). Effects `elsewhere:clarity` (cost −25%, regen +25%), `elsewhere:dazed` (spells −25%, +10 s per tonic, max 30 s). Entity `elsewhere:magic_bolt` (glowing billboard + dust trail). Creative tab `elsewhere:arsenal`.
- **Recipes**: wand (shaped), Moonsilk (shapeless `#elsewhere:moonsilk_base/<slot>` + 4 thread), Lined Moonsilk (netherite smithing), brewing: awkward + amethyst shard → Glimmer Tonic, + glowstone → Strong; awkward + open eyeblossom → Clarity Draught, + redstone → long.
- **Shared client settings** (`dev.elsewhere.hud.ElsewhereHud`, client source set) — the mechanism for **every** Elsewhere meter (BALANCE.md §D):
  - API: `ElsewhereHud.registerMeter(id, id)`; per frame `ElsewhereHud.shouldShow(id, autoCondition)` (Auto = your condition, Always, Hidden; false while "Toggle Elsewhere HUD" is on); `invisible(id)` → show your action-bar fallback; `screenEffects()`, `reduceMotion()`; `flag("trinketMarks"|"minionOutline"|"rankTitles"|"longbowZoom")`, `setFlag`.
  - Meters pre-registered: `glimmer`, `heat`, `altitude`, `haze`, `resonance`.
  - Keybinds (category Elsewhere, unbound): "Toggle Elsewhere HUD" (`hudHidden`), "Cycle Glimmer bar".
  - Client command: `/elsewhere_hud` (prints settings), `/elsewhere_hud <glimmer|heat|altitude|haze|resonance|all> <auto|always|hidden>`, `/elsewhere_hud effects <on|off|reduced>`, `/elsewhere_hud reduce_motion <on|off>`, `/elsewhere_hud <trinketMarks|minionOutline|rankTitles|longbowZoom> <on|off>`.
  - File `config/elsewhere-client.json`: `{"meters": {"glimmer": "auto", ...}, "hudHidden", "screenEffects", "reduceMotion", "trinketMarks", "minionOutline", "rankTitles", "longbowZoom"}`; read-merge-write, other keys are kept (verdigris' `LongbowZoom` writes `longbowZoom` itself; ElsewhereHud never overwrites an existing `longbowZoom`).
- **Glimmer bar** (`dev.elsewhere.arsenal.client.GlimmerHud`): 81×3 above the hunger row, `#A77BF3` on `#2A1F3D`, hatched reserve segment, `cur/max` while a `#elsewhere:glimmer_item` is held, red flash on a failed cast; Auto shows while holding a Glimmer item / minions out / refilling and fades 2 s after full; Hidden shows "Not enough Glimmer (N needed)" in the action bar on a failed cast.

## API for other modules (point the stubs here)
- `GlimmerStub.trySpend(player, cost)` → `dev.elsewhere.arsenal.glimmer.Glimmer.trySpend(ServerPlayer, int raw)` (scales by spell_cost × caster Calling, rounds to 0.1, min 1; creative free). Also `trySpend(p, raw, starter)`, `trySpendScaled(p, float)`, `drain(p, float)` (upkeep, unscaled), `refund(p, float)`, `restore(p, float)`, `current(Player)`, `max(Player)`, `reserved(Player)`, `cost(Player, int) → float`.
- Minion link (reflective, no compile dep): Glimmer calls `dev.elsewhere.minion.MinionApi.activeCount(ServerPlayer)` and `reservedGlimmer(ServerPlayer)` if present; otherwise reserve = count × `CALLING_MINION_RESERVE[summonerCalling]`.
- `CallingStub.summoner(p)` → `Calling.summoner(Player)`; `Calling.caster(Player)`, `casterFor(Player, starter)`, `spellDamage(int)`, `spellCost(int)`; tables `ArsenalStats.CALLING_*`.
- `TrinketStub.isActive/active` → `Trinkets.isActive(Player, Item)` / `active(Player)`.
- `TestMeterHalf.multiplier(p)` → `Spells.testMeterHalf(Player)`.
- Spell hooks: `Spells.ON_CAST` (BiConsumer<ServerPlayer, Item>; the Hush adds Resonance here, incl. +5 for Cantor's Diadem), `Spells.source(level, direct, owner)`, `Spells.mark(target, player)` (Moon-Eye: the core calls this when its Moon-Eye glows a mob).

## Merge steps
1. Copy `src/main/java/dev/elsewhere/arsenal/**`, `src/client/java/dev/elsewhere/arsenal/**`, `src/client/java/dev/elsewhere/hud/**` and `src/main/resources/{assets,data}/**` into the merged mod. Drop `DevEntry`, `DevClientEntry`, `dev/SelfTest`, `mixin/LivingEntityInvoker` (self-test only) and the dev `fabric.mod.json`.
2. Core entrypoints call `ArsenalContent.init()` (before the summoner's init, so arsenal's attributes are registered first; both reuse an existing id anyway) and `ArsenalClient.initClient()`.
3. Add `AbstractArrowAccessor` to the merged mixin config.
4. `dev.elsewhere.stub.arsenal.SetBonus` → core SetBonus (`#elsewhere:set/moonsilk/<slot>` tags are shipped); `BossCheck` → core boss framework (tags `#elsewhere:bosses` / `#c:bosses`, "in boss fight" = boss within 64 blocks).
5. Tag merges: `#elsewhere:trinket`, `#elsewhere:slow_mending`, `#minecraft:head_armor` etc. use `replace: false` and merge with the summoner's.
6. Merge `en_us.json` (keys are arsenal-only except `itemGroup`/`key.category.elsewhere.elsewhere`).

## Stubbed / changed / gaps
- **Sources owned elsewhere**: `moonsilk_thread` drops (Eyeblossom Wisp 40%, Selenne 4–8) are the core's; Sunlance/Focus (Dune Factor), Stormcoil (Tidewright), Resonator (Mute Archivist) trades, Stormsage Mantle (Tempestra) and Cantor's Diadem (Lithara) drops are added by those modules by id lookup.
- **Paleweave doesn't exist yet**: Moonsilk recipes take `#elsewhere:moonsilk_base/<slot>` = optional `elsewhere:paleweave_*` + leather armor piece (fallback). Repair: optional `elsewhere:amber` + Moonsilk Thread; Sunlance: optional Sunglass Shard + amethyst shard; Stormcoil: optional Verdigris Gear + copper ingot; Mantle/Diadem: Moonsilk Thread (design gave no repair item).
- **Bell of the Flock +6 max Glimmer** (rev 4): the summoner owns the Bell; it should add a `max_glimmer` modifier while active (the attribute is arsenal's). Not done here.
- **Herd focus / summoner own-damage on spells**: done by the summoner's own-damage hook (SummonerPerks), not in `Spells.damage`, to avoid double counting.
- **Air bubbles**: instead of moving the vanilla bubbles one row up, the Glimmer bar moves up one row while you're underwater or riding (no mixin).
- **Moon-Eye** is a core item: arsenal tags it as a trinket (optional entry) and gives the +20% on mobs marked via `Spells.mark`.
- Slow Mending (BALANCE G3) is the core's: arsenal only ships the `#elsewhere:slow_mending` tag.
- Sculk sensors/Wardens hear the Resonator via `GameEvent.EXPLODE`; Hush Resonance +10 per cast is the Hush module's `Spells.ON_CAST` hook.
- Roc-Feather Longbow belongs to verdigris_ironpeak; arsenal's leftover longbow textures were removed.

## Screenshots (client gametest on the iMac, 20:31) — `screenshots/arsenal_*.png`
HUD in Auto (holding the wand, `9/20`), Always, Hidden + failed cast (action-bar "Not enough Glimmer"), Auto with a hatched minion reserve and the red fail flash; each weapon firing (wand bolt, Sunlance burning a husk, Stormcoil sparks chaining, Resonator sonic rings); Moonsilk / Lined Moonsilk / Diadem + Mantle on armor stands; the player in Moonsilk; an inventory of every item. Fixed after looking: the Sunlance/Stormcoil particle lines started at the camera (big blobs in view) and now start 1.5 blocks ahead (jar rebuilt 20:35; the screenshots predate that fix and rev 4).

## 0.4.0-beta (polish, 21:52) — self-test passes inside the combined jar
- Only `src/main/java/dev/elsewhere/arsenal/dev/SelfTest.java` changed (merge: re-copy that one file).
- The test arena was at y −60 (inside stone on a normal world). It now builds its own platform at (48, min(maxY−12, 240), 48): it saves, clears and floors a 22×14×6 box, sets rain/thunder to 0 for the "dry" Stormcoil and Sunlance checks, and restores the blocks and weather afterwards.
- Minion reserve/regen checks: when `dev.elsewhere.minion.MinionApi` is present (summoner module / combined jar), the test summons 2 real Rootlings via `MinionManager.summon` (reflection, no compile dependency; slots raised to 2), checks the reserve (12) and regen slow (2.4/s), then `dismissAll` + `SummonerPerks.updateHerdFocus` so Herd Focus doesn't leak into later checks. Without summoner, the dev stand-in `Glimmer.devMinions` is used as before. New check: "minions gone after the check" (48 checks now).
- Results (desktop, normal world): dev module 48/48 (`run/selftest-0.4.0-normal.log`), dev module + summoner dev jar 48/48 (`run/selftest-0.4.0-with-summoner.log`), **inside the combined 0.3.0 merged source with this file 48/48, 0 ERROR** (`run/selftest-0.4.0-combined.log`).
