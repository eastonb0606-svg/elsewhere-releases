# Elsewhere core API (for content modules and the beta merge)

Mod id `elsewhere`, packages under `dev.elsewhere`. Entrypoints: `dev.elsewhere.Elsewhere` (main), `dev.elsewhere.client.ElsewhereClient` (client).
Content modules should call their `<Module>Content.init()` / `<Module>Client.initClient()` from these at merge time (after `ModItems/ModEntities` init, before `ElsewhereCommands.init()`).

## Dimensions — `dev.elsewhere.core.travel.ElsewhereDimensions`
Register every dimension once (common side, so client and server both know its colour):
```java
ElsewhereDimensions.register(new ElsewhereDimensions.Info(LEVEL_KEY, "petalwind", 0xF4B6D6 /*rift+beam colour*/,
    200 /*max landing Y*/, Blocks.CHERRY_PLANKS /*emergency platform*/, true /*Return Rifts; false for The Hush*/));
```
Used by rifts, safe landing, Return Rifts, accidental trips, the Frayed Wayfinder.

## Wayfinders and attunement — `dev.elsewhere.core.wayfinder`
- Item per dimension: `new AttunedWayfinderItem(props.stacksTo(1).component(DataComponents.USE_COOLDOWN, ModItems.WAYFINDER_COOLDOWN), LEVEL_KEY, COLOR)` registered as `elsewhere:wayfinder_<dimension path>` (textures `item/wayfinder_<path>.png`).
- Recipe: drop `data/elsewhere/attunement/<dimension>.json` (`{"dimension","ingredients":[5 ids],"requires":"none|ender_dragon_killed","notes"}`). The special recipe `elsewhere:attunement` crafts base Wayfinder + exactly that ingredient set in any layout. Optional recipe-book copy: a shaped recipe `data/elsewhere/recipe/wayfinder_<path>.json` (also unlocked on first visit).
- Travel helpers: `RiftTravel.openWayfinderRift(player, key)`, `openForcedRift(player, keyOrNullForHome)`, `travelNow(player, keyOrNull)`, `homeDestination(player)`, `teleport(...)`. Return point is saved automatically (`ModAttachments.RETURN_POINT`).

## Return Rifts — `dev.elsewhere.core.riftruin`
- Block `elsewhere:return_rift` (walk in = one-way home, beam in the dimension colour), feature type `elsewhere:return_rift`.
- To add them to a dimension: register its Info with `returnRifts = true`, then put the placed feature `"elsewhere:return_rift"` first in step 9 (vegetal_decoration) of every biome of that dimension (same order in all biomes). One ruin per 600x600 cell; `ReturnRifts.nearestRift(level, x, z)` finds the closest without loading chunks. Landings are pulled within 250 blocks of one.

## Accidental trips — `dev.elsewhere.core.trip`
```java
public interface TripTrigger { String id(); ResourceKey<Level> target(); Kind kind(); double baseChance(ServerPlayer p); enum Kind { LOCATION, ACTIVITY } }
AccidentalTrips.register(trigger);            // once, at init
AccidentalTrips.fire(serverPlayer, trigger);  // from your event hook (fall, launch, item use, timed check)
```
The core applies all rules (familiarity per 512x512 region, home radius 0.03, hourly budgets, 2 h cooldown, 60 min grace, pity x2 not at home, 3 s cancellable pull, Frayed Wayfinder on arrival). Config `config/elsewhere.json` → `accidentalTrips.triggers[id]` overrides the base chance; `hourlyBudgets[id]` sets activity budgets. Pure maths in `TripRules` (used by the self-test simulation).

## Boss framework — `dev.elsewhere.core.boss`
Extend `ElsewhereBoss` (a `Monster`, so `Enemy`):
```java
super(type, level, BossEvent.BossBarColor.X, BossEvent.BossBarOverlay.Y);
protected double baseMaxHealth();                                 // solo base HP (Test Meter x1.53 applies on top)
protected void bossTick(ServerLevel level);                       // AI while awake, not mid-transition
protected boolean canBeHurtNow(ServerLevel level, DamageSource s); // mechanic gating
protected void onWake / onReset / onPhaseChange(level, from, to);
```
Provided: `setArena(center, radius, below, above)` (snapshot + seal ring + reset after 30 s empty), `wake(level)` (party scaling x(1+0.5 per extra player)), `setPhase(n)` (2 s invulnerable transition + roar), `hitGuarded(level, target, raw, source)` (lethal-hit guard), overpower guard on damage taken, boss bar, per-player loot (each participant rolls the entity loot table; items only they can pick up). `BossGuards` has the two guards; `BossEvents` caps any boss-sourced hit on players. `ArenaSeal` is usable on its own.

## Armor sets — `dev.elsewhere.core.gear.ArmorSets`
`ArmorSets.register(new ArmorSet(id, pieces, bonuses /*attribute modifiers*/, effects /*suppliers*/, ability /*per-second hook or null*/, description))`. Bonus applied while all pieces are worn, removed when one comes off; client tooltip lines via `ArmorSets.tooltip(stack, wearer)` (wired in `ElsewhereClient`). Lang key `set.<ns>.<path>`.

## Registries and ids owned by the core
Blocks `arena_seal`, `return_rift`; items `wayfinder`, `frayed_wayfinder`; entity `rift`; particles `rift_spark` (ColorParticleOption, use `ModParticles.spark(rgb)`), `resin_dust`, `moon_mote`; sounds `rift.*`, `trip.pull`, `boss.seal`; recipe serializer `attunement`; feature type `return_rift`; payload `elsewhere:pull`; attachments `return_point`, `trip_state`; command `/elsewhere` (op).
Pale Hollow ids: see NOTES.md.

## 0.4.0 additions
- **Trips switches** (`dev.elsewhere.core.trip.AccidentalTrips`): `allowed(player)` (= `overworldContent()` && `playerEnabled(player)`), checked first by `fire` and `startPull`; `setPlayerEnabled(player, on)` (attachment `elsewhere:trips_enabled`, `ModAttachments.TRIPS_ENABLED`). Command `/elsewhere trips [on|off]` for any player; other `/elsewhere` subcommands op-only.
- **Config**: `ElsewhereConfig.get().overworldContent` (public boolean, default true).
- **Boss uniques** (`dev.elsewhere.core.boss.BossDrops`): `register(entityType, new Unique(name, () -> itemOrNull, firstKillChance, repeatChance))`; rolled per participant by `ElsewhereBoss` on death, kills saved per player (`elsewhere:boss_kills`).
- **Arena check**: `ElsewhereBoss.inActiveArena(serverPlayer)`; entity tag `#elsewhere:bosses` lists every boss (optional entries for module bosses).
- **Wayfinder cooldown resync**: `RiftTravel.resyncWayfinderCooldown(player)` runs on every level change.
