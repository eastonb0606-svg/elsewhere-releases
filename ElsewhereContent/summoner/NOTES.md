# Elsewhere: summoner module (ARSENAL.md §3b, §5, §7, §8)

Dev mod id `elsewhere_summoner`, port 25691. Everything registers in the `elsewhere` namespace.
Entry points: `dev.elsewhere.summoner.SummonerContent.init()` (common), `dev.elsewhere.summoner.SummonerClient.initClient()` (client).
Status (20:26): compiles; **ARSENAL.md revision 4** numbers in; desk self-test **107/107 PASS** (`run/selftest-3.log`; rev 4 before the order rules in `run/selftest-2.log`; rev 3 run in `run/selftest-1.log`); client screenshots in `screenshots/`; dev jar `dist/elsewhere_summoner-0.1.0.jar`.

## What is built and working

**Minion framework** (`dev.elsewhere.minion`, common code):
- `MinionEntity` (abstract `PathfinderMob`, not `Enemy`): owned by a player UUID; can't be leashed, ridden, renamed, pushed, picked or bred; never saved with the world; no drops, no XP; no fall damage, no drowning; owner-only amethyst outline (client).
- Summoning (`MinionStaffItem`): hold use for the **1.0 s call** (bow-draw speed; any damage taken interrupts it), then the minion appears for **10 Glimmer** and 1 staff durability. Not enough Glimmer: nothing happens, amethyst clink. Sneak + use dismisses that staff's minions.
- Slots (`MinionApi.maxSlots`): attribute `elsewhere:minion_slots` (base 1, range 0–4), clamped again to `HARD_SLOT_CAP` 4 and `MAX_MINIONS_PER_PLAYER` 4. Sources: Herdcaller Cap / Elder Bark Crown (+1), Herdcaller Vest / Engineer's Harness (+1), Bell of the Flock (+1, only with a summoner piece worn). Over the limit, the oldest minion leaves (no refund); taking gear off trims the herd.
- Battle timer (`fervorTicks`): 30/40/50/55/60 s by Summoner Calling 0–4 (rev 4); reset when the minion or its owner deals damage to / takes damage from a hostile mob; runs out → the minion goes home. Trail Biscuits and armor never extend it.
- Despawn: owner logout, death, respawn, sleep, dimension change, owner offline/out of level; minion death.
- Follow: 3–6 blocks; teleports next to the owner beyond 24 blocks (16 with Herdcaller Boots) and when the owner teleports; Paper Kites hover 2–4 blocks above the owner's head.
- **Targeting by order (user, 20:35/20:40):** minions pick only hostile mobs on their own (`Enemy` that isn't neutral; endermen, zombified piglins and piglins count as neutral); passive and neutral mobs only in self-defence (they are attacking the owner or a minion right now); the owner's own hits never redirect minions onto passive/neutral mobs, players, pets, villagers or golems. A **Herding Crook** hit is an explicit order: the Herded target (any of those, action bar "Your minions attack <name>") is attacked until the 5 s mark ends or the owner sneak-uses the Crook ("Your minions stand down"); never the owner, the owner's own pets, minions, armor stands, or players the owner can't harm (`pvp` game rule off, teammates with friendly fire off). Self-test 107/107 (`run/selftest-3.log`).
- Targeting (`MinionTargeting`): Herded target first, then what the owner hit (5 s), what hurt the owner (5 s), the nearest `Enemy` within 10 blocks targeting the owner; range 16; drops a target out of sight for 5 s.
- Protection: never targets or damages players (PvP included), any minion, tamed pets, villagers/wandering traders, iron/snow/copper golems, passive mobs, or neutral mobs the owner didn't attack (AI filter + an `ALLOW_DAMAGE` guard on every minion damage source).
- No griefing: no block breaking/placing, no farmland trampling, doors, pressure plates or tripwires, no item pickup, no fire, no explosions.
- Kills count as the owner's (XP + normal loot, no Looting); hits are credited to the owner for boss loot share. Damage = base × Calling (×0.80/0.90/0.95/0.98/1.00, rev 4) × `elsewhere:minion_damage` × Test Meter half bonus, + Herded +1 flat. Damage type `elsewhere:minion`.
- Bosses (`BossCheck`: Wither, Ender Dragon, `#elsewhere:bosses`, `#c:bosses`) ignore the Brassback taunt and the Rootling root.
- Glimmer reserve per minion 6/5/4/4/4 by Calling (rev 4) via `MinionApi.reservedGlimmer(ServerPlayer)`; arsenal applies it and the 10%-per-minion regen slow (it reads `MinionApi.activeCount(ServerPlayer)`).

**Minions:** `elsewhere:rootling` (20 HP, armor 4, 4 dmg / 1 s, roots non-bosses Slowness II 1 s once per 4 s per target), `elsewhere:paper_kite` (12 HP, flies, wind bolt 3 dmg / 1.5 s, knockback 0.5, range 16; starter: Calling ≥ 2), `elsewhere:brassback` (40 HP, armor 10, pinch 2 / 1 s, taunts non-boss mobs within 8 blocks every 5 s for 3 s, heals 1 HP/s in water). Custom cube models + PIL textures (`client/MinionModels`, `MinionRenderer`, `textures/entity/{rootling,paper_kite,brassback}.png`).

**Items** (all in creative tab `elsewhere:summoner`):
`herding_crook`, `kitestring_staff`, `rootcall_staff`, `brassback_key`, `herdcaller_cap|vest|leggings|boots`, `lined_herdcaller_cap|vest|leggings|boots`, `elder_bark_crown`, `engineers_harness`, `bell_of_the_flock`, `trail_biscuit`, `gorger_fluff`.
- Herding Crook: 5 dmg, 1.6 speed, +1.5 reach, 1561 durability; a hit marks the target **Herded** (`elsewhere:herded`, 5 s, one target per player); minions switch to it and deal +1 flat per hit.
- Herdcaller 20 / 8 / 0, Lined Herdcaller 20 / 12 / 0.2 (smithing recipes here), per-piece perks (cap/vest +1 slot, leggings +15% minion damage, boots faster minions + 16-block teleport), set bonus **Pack Bond** (minions within 8 blocks heal 1 HP/s and +4 armor; a dying minion refunds 10 Glimmer). Every summoner piece: own `attack_damage` −10% and `spell_power` −10% (multiplied base), arrows −10% per piece (spawn hook). **Herd focus** (rev 4): −10% more own damage (melee, spells via a transient modifier on `attack_damage`/`spell_power`; arrows in the same hook) per minion out beyond the first, additive with the piece cut, floor 0.
- Elder Bark Crown (3/2, +1 slot, Rootlings +50% HP), Engineer's Harness (8/2, +1 slot, +15% minion damage, minions immune to fire and lightning). Neither counts for Pack Bond.
- Bell of the Flock (trinket, `#elsewhere:trinket`): +1 slot and `max_glimmer` +6 (rev 4) with a summoner piece worn; right-click calls the herd to your side.
- Trail Biscuit (stack 16, recipe 2 wheat + sweet berries + glow berries → 4): use with minions out gives **Well Fed** (`elsewhere:well_fed`, 3 min, +20% damage, +4 armor, heal 50% now; refreshes, never stacks); also edible (2 / 0.4).
- Tags: `#elsewhere:summoner_armor` and `#elsewhere:calling/summoner` (the 10 summoner pieces), `#elsewhere:set/herdcaller/<slot>`, `#elsewhere:trinket`, `#elsewhere:slow_mending`, repair tags `#elsewhere:repairs_*`, vanilla armor/enchantable tags.

All numbers live in `dev.elsewhere.summoner.GearStats` (Calling tables `ATTUNE_DAMAGE`, `ATTUNE_FERVOR_TICKS`, `ATTUNE_RESERVE`; ARSENAL.md revision 4 softer Calling numbers plug in there).

## Sources owned by other modules (id lookups there, nothing to do here)
Herdcaller recipes (Frond piece + 4 Gorger Fluff), Gorger Fluff drop (Lazy Gorger 50%) and the Budtender Herding Crook trade: greenhaze_hush. Kitestring Staff + Bell of the Flock trades: petalwind_glasswaste (Windwright). Brassback Key + Engineer's Harness drops / Tidewright trade: verdigris_ironpeak. Rootcall Staff + Elder Bark Crown drops / Sap Tapper trade: core.

## Stubs and merge instructions
- `dev.elsewhere.stub.summoner.*` already **soft-link to arsenal by reflection** when it is on the classpath, so the merge can keep them as they are:
  `GlimmerStub` → `dev.elsewhere.arsenal.glimmer.Glimmer.trySpendScaled / refund / drain` (summon cost = 10 × `elsewhere:spell_cost`, rounded up; the caster Calling cost table is not applied to summons), `CallingStub` → `Calling.summoner(Player)`, `TrinketStub` → `Trinkets.isActive(Player, Item)`, `TestMeterHalf` → `Spells.testMeterHalf(Player)`. Without arsenal they fall back to local equivalents (dev).
- `SummonerAttributes` + `stub/summoner/mixin/PlayerAttributesMixin`: register `elsewhere:minion_slots`, `elsewhere:minion_damage`, `elsewhere:spell_power` and `elsewhere:max_glimmer` (same base/range as arsenal's) only if not registered yet (arsenal's `ArsenalAttributes` does the same), so init order doesn't matter. Keeping them in the merged jar is harmless; dropping them is fine once arsenal inits first.
- `SetBonusStub` (Pack Bond via `#elsewhere:set/herdcaller/*`) and `BossCheck` (boss tags): point at the core's SetBonus / boss framework if it has them; the tag-based versions work as is.
- **Drop at merge:** `dev.elsewhere.summoner.DevEntry`, `dev.elsewhere.summoner.client.DevClientEntry`, `dev.elsewhere.summoner.dev.SelfTest`, the gametest source set.
- Mixins: `elsewhere_summoner.mixins.json` (LivingEntity invoker, AbstractArrow accessor) must be kept; rename the json if needed.
- `MinionApi.activeCount(ServerPlayer)` / `reservedGlimmer(ServerPlayer)` signatures are what arsenal's Glimmer calls by reflection: keep them.

## Changed from the docs / decisions
- Server config `maxMinionsPerPlayer` is the constant `GearStats.MAX_MINIONS_PER_PLAYER` (4) until the core config exposes it (it can only lower the cap).
- Elder Bark Crown repairs with Trichome Quartz (`#elsewhere:repairs_herdcaller_armor`); Engineer's Harness repairs with Verdigris Gear (`#elsewhere:repairs_engineers_harness`, optional entry; lead, rev 4).
- Herding Crook is in `#minecraft:enchantable/melee_weapon` for Knockback, which also allows Looting/Sharpness on it (question open).

## Known gaps / coming soon
- Minion-specific sounds are vanilla placeholders (amethyst chime/clink, poof).
- Glimmer costs "round to 0.1" (rev 4) is arsenal's pool; the summon cost goes through `Glimmer.trySpendScaled(int)` as 10 × spell_cost rounded up until arsenal exposes a float spend.
- Client switch `minionOutline` (BALANCE §D3, default on) is read from `config/elsewhere-client.json` by `client.MinionOutlineSetting` (re-checked every 2 s); the `/elsewhere_hud` command and settings screen that write it belong to the shared HUD code.

## Screenshots (`screenshots/`, client gametest run 20:17, before the rev 4 numbers)
Rootling, Brassback and Paper Kite close-ups and fights, herd fight, armor sets on stands, player in Herdcaller, inventory. Caveats: the inventory shot has a tooltip over the items and still shows the rev 3 "−20%" (now −10%); the herd shot shows one minion (the test player had 1 slot); the kite-fight shot shows the wind bolts but the kite is above the frame. The gametest is already fixed for all three (4 slots, third-person camera for the kite, cursor moved off the slots); the re-shoot didn't get a client slot before the 20:35 deadline. Models and textures themselves look right (no missing textures).

## 0.4.0-beta (polish, 21:55)

- **Server config `maxMinionsPerPlayer`** (default 4, range 0–4; can only lower the limit): a player's minion slots are min(gear slots, hard cap 4, this value); 0 turns summoning off (the staff shows "Minions are turned off on this server"). Class `dev.elsewhere.summoner.SummonerConfig`, loaded on SERVER_STARTING from `config/elsewhere-summoner.json` (`{"maxMinionsPerPlayer": 4}`, written with the default when missing; out-of-range values are clamped with a warning). **Merge:** if the core adds `public int maxMinionsPerPlayer = 4;` to `dev.elsewhere.ElsewhereConfig` (config/elsewhere.json), SummonerConfig reads that field by reflection instead and the separate file is not used; nothing else to wire. `MinionApi.maxSlots` uses `SummonerConfig.maxMinionsPerPlayer()` (GearStats.MAX_MINIONS_PER_PLAYER is now only the default).
- **Herding Crook enchantments:** only Knockback, Unbreaking and Mending. `HerdingCrookItem.initEnchantRules()` (called from `SummonerContent.init`) registers Fabric's `EnchantmentEvents.ALLOW_ENCHANTING`: TRUE for those three, FALSE for everything else (Sharpness, Smite, Bane of Arthropods, Looting, Fire Aspect, Sweeping Edge, curses). Fabric routes the enchanting table, anvil, enchant_randomly loot and `/enchant` through it (the only vanilla callers of `Enchantment.canEnchant`). Item tags unchanged.
- **Self-test in the combined jar:** `GlimmerStub` now looks up arsenal's `trySpendScaled(ServerPlayer, float)` (int fallback; the merge patch for this file now skips itself) and has self-test helpers `arsenalPresent()`, `pool()`, `setPool()` (arsenal `current` / `setForTest` by reflection) and a `testFree` switch. The summoning check spends through the real API (arsenal pool 20 → 10 in the combined jar, the dev record alone) and tests "not enough Glimmer" with the real pool at 0; the rest of the run sets `testFree` so it doesn't run the real pool dry. New checks: maxMinionsPerPlayer (9 → 4, 2 caps 4 slots and drops extras, 0 = off) and Crook enchantments (anvil/loot/command context, enchanting-table rolls at level 30 include Knockback and nothing outside the three, iron sword still takes Sharpness).
- Results: dev module **109/109** (`run/selftest-4.log`, desktop); combined source (`ElsewhereBeta/src` + these files, only `summoner.selftest`) **109/109** (`run/selftest-4-combined.log`; was 19/25 + crash).
