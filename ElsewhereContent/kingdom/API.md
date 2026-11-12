# Kingdom base API (module `ElsewhereContent/kingdom/`)

Foundation for the Aldermark mini-bosses, King, castle and conquest. Package `dev.elsewhere.kingdom`. Everything is server-side unless marked client. Entry: `KingdomContent.init()` (the merge finds it), client `KingdomClient.initClient()`.

## What you get

- **`KingdomSoldier`**: a player-sized `Monster` (so a `PathfinderMob`; MobCategory.MISC, never natural-spawns, always persistent). It uses the vanilla player model, player armor layers, held items and a faction-tinted tabard.
- **Player-like combat:**
  - Shield blocking runs through vanilla `BlocksAttacks`: frontal hits are blocked after the 5-tick delay, and axes disable the shield for 5 s, as for a player.
  - The shield goes up against a melee wind-up, a drawn bow or crossbow, or after an arrow hit. It stays up until an enemy swing is blocked (at most 0.75 s), then the soldier ripostes.
  - Attack cooldown uses the player formula for the held weapon (`20 / ATTACK_SPEED` ticks; damage scales by `0.2 + 0.8·strength²`).
  - Jump crits deal ×1.5 damage with vanilla crit particles and sound, on a per-unit cooldown.
  - A sprint hit adds +0.5 knockback.
  - Soldiers strafe in melee and back off with the shield up at low health.
  - Archers use the vanilla bow goal (keep distance, strafe) and crossbowmen the vanilla crossbow goal.
  - Lancers use the vanilla `SpearUseGoal`, a kinetic spear charge that works mounted. Mounted swordsmen charge, slash, ride through and turn.
- **Squads:** one officer leads; only the leader pathfinds. Members walk to formation slots (`LINE`, `SHIELD_WALL`, `COLUMN`) with plain move control and only path locally when engaging within 12 blocks. Each squad shares one enemy scan, and morale and rout apply. Squads are saved per level in `data/elsewhere/kingdom.dat`, and each soldier keeps its unit, faction and squad in its NBT.
- **Perf:** these are the `config/elsewhere-kingdom.json` caps.
  - Per player, only the nearest 24 soldiers within 48 blocks run full AI. The rest skip their expensive goals.
  - At most 6 soldier path searches start per tick globally. Leaders repath every 10–20 ticks.
  - Spawn caps: 120 soldiers globally, 40 per 48-block area, 8 mounted, and 16 per squad.
  - AI work is staggered by entity and squad phase.

## Define a unit type (JSON, no code)

`data/<ns>/kingdom_unit/<name>.json` gives unit id `<ns>:<name>` and reloads with `/reload`. Every field except `role` has a default.

```json
{
  "role": "melee",                 // melee | archer | crossbow | lancer | cavalry
  "officer": false,                // officers lead squads, never retreat, and their death hits morale
  "skin": "elsewhere:textures/entity/kingdom/soldier_a.png",   // 64x64 player-layout skin
  "armor": { "head": "minecraft:chainmail_helmet", "chest": "minecraft:chainmail_chestplate",
             "legs": "minecraft:chainmail_leggings", "feet": "minecraft:chainmail_boots" },
  "armor_dye": 3893806,            // 0xRRGGBB, applied to leather_* pieces
  "main_hand": "minecraft:iron_sword",
  "off_hand": "minecraft:shield",
  "mount": "minecraft:horse",      // optional: spawns tamed, saddled, persistent, rider on top
  "mount_armor": "minecraft:iron_horse_armor",
  "stats":  { "health": 24, "speed": 0.28, "attack_damage": 1.0, "follow_range": 32, "knockback_resistance": 0, "armor": 0 },
  "combat": { "block_chance": 0.75, "crit_chance": 0.35, "crit_cooldown": 50, "retreat_health": 0.3 }
}
```

- **Built-in units:** `elsewhere:man_at_arms`, `knight`, `lancer`, `mounted_swordsman`, `archer`, `crossbowman` and `sergeant` (officer).
- **Skins:** `soldier_a`, `soldier_b`, `archer` and `officer` (regenerate them with `tools/gen_textures.py`).
- **Units defined in code:** `UnitTypes.putCodeDefined(id, new UnitType(...))`. These survive `/reload`, and a JSON file with the same id wins.
- **Weapon stats:** damage and attack speed come from the item, exactly as for a player. `attack_damage` is the base (players use 1.0).

## Spawn soldiers and squads

```java
// one soldier (null when a cap refuses it)
KingdomSoldier s = KingdomApi.spawnSoldier(level, UnitTypes.KNIGHT, Factions.ALDERMARK, pos, null);

// a squad: leader first, then members; units refused by caps are skipped
List<KingdomSoldier> sq = KingdomApi.spawnSquad(level, blockPos, Factions.ALDERMARK, Squad.Formation.SHIELD_WALL,
    UnitTypes.SERGEANT, List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.ARCHER, UnitTypes.ARCHER));
Squad squad = sq.get(0).squad();
squad.setObjective(gatePos);      // the leader marches there, members follow in formation; null = hold
squad.setFormation(Squad.Formation.COLUMN);
KingdomApi.join(squad, otherSoldier);   // false if full
```

- `KingdomSoldier` also offers `setUnit(id)` (re-applies the loadout), `setFaction(id)`, `raiseShield(ticks)`, `rout(ticks)`, `squad()`, `isActive()` and `isRouted()`.
- The squad exposes `morale()` (0–100), `enemies()` (the shared scan result), `leader()` and `members()`.
- Spawn eggs `elsewhere:kingdom_<unit>_spawn_egg` and `/summon` ignore the caps; they are op tools. Only `KingdomApi` spawns respect them.

## Faction hostility

```java
Factions.register(id("rebels"), 0x9E2E2E, true);                         // init: tabard colour, default stance
KingdomApi.setFactionHostileToPlayers(server, Factions.ALDERMARK, false);  // runtime, saved with the world
Factions.setAllied(Factions.ALDERMARK, id("vassals"), true);                // init only, not saved
```

- **Default:** `elsewhere:aldermark` (blue) and `elsewhere:rebels` (red) are both hostile to players.
- **Targets:** soldiers fight hostile mobs (creepers only if provoked), soldiers of non-allied factions, and players when the faction is hostile to players or the player hit them or their squad in the last 30 s.
- **Never targeted unless they attacked:** tamed pets, tamed horses and creative or spectator players.
- **No friendly fire** between soldiers of the same faction.
- **PvP:** the mod has no PvP config of its own. `combat.respectPvpGamerule: true` makes soldiers stop picking players on their own while the vanilla `pvp` gamerule is off. They still fight back when attacked.

## Hook a boss (Lords, the King)

1. **A boss leading a squad:** `KingdomApi.setLeader(level, squad, bossMob)`. Any `Mob` works. Members form up on the boss. When it dies, the squad loses 50 morale, each member routs with chance `morale.routChanceOnOfficerDeath` (default 0.5) for `morale.routTicks`, and a new leader is promoted.
2. **A boss that fights like a soldier:** subclass `KingdomSoldier` with the `(EntityType, Level)` constructor. Add goals in `registerGoals()` after `super.registerGoals()`. Then register it:
   ```java
   EntityType<Castellan> T = KingdomEntities.registerSoldierType("castellan", id("castellan"), Castellan::new); // + spawn egg in the kingdom tab
   KingdomClient.registerSoldierRenderer(T);   // client: player model + armor + tabard, texture = unit "skin"
   KingdomApi.spawnSoldier(level, T, id("castellan"), Factions.ALDERMARK, pos, squad);
   ```
   Useful overrides and hooks: `doHurtTarget` (player-like hit, crit and sprint logic), `strike(level, target)` (swing and hit), `blockUsingItem`, `customServerAiStep`, and the public combat fields (`critCooldown`, `shieldDisabledTicks`, `forcedShieldTicks`).
3. **Events** (Fabric `Event`s): `KingdomEvents.SOLDIER_DIED` gives `(level, soldier, source)`. `KingdomEvents.LEADER_DIED` gives `(level, squad, leader, routedCount)` and fires before promotion. Use them for reinforcements, the Herald's horn, boss phases and so on.
4. **Reinforcements:** `KingdomApi.spawnSquad(...)` near the boss. It returns fewer soldiers when caps bite, which is the intended spike guard.

## Config (`config/elsewhere-kingdom.json`, written with defaults on first start)

| Key | Default |
|---|---|
| `perf.maxRealSoldiersPerPlayer` | 24 |
| `perf.maxRealSoldiersPerArea` | 40 |
| `perf.maxRealSoldiersGlobal` | 120 |
| `perf.maxMounted` | 8 |
| `perf.maxSquadSize` | 16 |
| `perf.pathSearchesPerTick` | 6 |
| `perf.leaderRepathMin` | 10 |
| `perf.leaderRepathMax` | 20 |
| `perf.activeRadius` | 48 |
| `combat.useShields` | true |
| `combat.useCrits` | true |
| `combat.useFormations` | true |
| `combat.useMorale` | true |
| `combat.soldierDamageScale` | 1.0 |
| `combat.respectPvpGamerule` | false |
| `morale.routChanceOnOfficerDeath` | 0.5 |
| `morale.routTicks` | 200 |

## Test

`./gradlew runServer` (port 25711) runs the self-test and the benchmark, then exits. It runs 53 checks plus the MSPT benchmark, logged as `[kingdom-selftest]` and saved to `run/selftest-kingdom.log`. Last run: `run/selftest-kingdom.log` in this folder.

## Not done yet (for the second builder or later)

- Virtual squads and LOD rings (ring 2 "no entity" records) and the auto-throttle from KINGDOM.md §2.5.
- Pikemen, squires, standard-bearer and herald. Their data needs no code (JSON units plus events).
- The client "soldier detail" option.
- Never seen in a real client: the renderer compiles, and textures and models exist, but QA should spawn the eggs once.

## 2.1 multi-faction foundation
Factions are data (`data/<ns>/kingdom_faction/*.json`), relations are per pair (war/truce/tension/peace/allied), aggression and Standing are per player and faction, and hostile squads clash abstractly when no player is near. How to add a rival kingdom, the schema and the status of each rival requirement: `FACTIONS.md`.

---

# 2.1 war lane API (jar `elsewhere_kingdom-0.3.0.jar`, package `dev.elsewhere.kingdom` and `dev.elsewhere.kingdom.war`)

Everything below is server side and additive: Aldermark's 2.0 behaviour is unchanged (a world with only Aldermark never creates war-layer data). Published by the war lane for the court, villages, lore, Hrafnholt, Azmar and Mirewick lanes. Items marked **(live)** work in the jar you get now; **(stub)** are final signatures with a provisional body.

## 1. Standing (`Standing`, `KingdomApi.standing/setStanding`) (live)
- `Standing.get/set/add(ServerLevel, UUID, factionId[, value])`, `band(int)`, `bandOf(...)`. Bands Hostile <= -50, Wary -49..-10, Neutral -9..29, Friendly 30..69, Allied >= 70. `set` and `add` are the raw/admin setters (clamped, Aldermark capped at 29 until `KingdomApi.markRulerKilled`); positive `add` respects the Oathbreaker lock.
- Start value: faction file `society.standing.start`; if absent: -60 for a faction flagged `hostile_to_players`, else 0, with built-ins Hrafnholt -30 and Mirewick +10 for a non-hostile-flagged faction. Multipliers (loss/gain) are brain tuning keys `standing_loss` / `standing_gain` (built-ins Hrafnholt 1.5/0.75, Mirewick 0.5/1.25).
- `Standing.attacksOnSight(player, faction)` (what `KingdomTargeting` uses): no recorded Standing -> the faction's `hostile_to_players` flag (Aldermark as 2.0) else the band of its start value; recorded -> Hostile attacks anywhere, Wary only where `Standing.RealmLocator` says so, Neutral and up never; a faction flagged hostile keeps attacking below Friendly. **Court: call `Standing.setRealmLocator((level, pos, faction) -> inRealmOrNearCastle)` once** (default: nowhere, so Wary is harmless until you register it).
- Moving it (call these where you see the event; soldier kills are automatic):
  - `Standing.onKilled(ServerPlayer, victimFaction, Standing.Victim.{SOLDIER,OFFICER,SERF,LORD,RULER})` -> loss -3/-8/-2/-30/-50 times the kingdom multiplier, doubled for Friendly+ or a truce; Oathbreaker (Allied victim: straight to -60, -10 with every other kingdom, no rise for 3 days); enemy-of-my-enemy (half the loss as gain to kingdoms at war with the victim) and the +2 kill gain (the larger applies per faction), both under the +20 per in-game day cap. **Villages: call it for Serf-tier kills. Court: call it for Lord and ruler kills (not for duels, which lose nothing).**
  - `Standing.onVillageDefended(player, faction)` +10; `Standing.onTribute(player, faction, coins)` +1 per coin, +15/week; `Standing.onTrade(player, faction)` +1, +5/week; `Standing.grantTruce(player, faction, ticks)`; `Standing.shopDiscount(level, uuid, faction)` 0 / 0.05 / 0.15.
  - Daily drift of 1 towards the start value is automatic. Hunting squads of rivals need Hostile and a killed officer, Lord or ruler (Aldermark keeps its aggression rule).

## 2. War layer and brains (`war.WarApi`, `war.WarEvents`, `war.Tuning`) (live)
The model of WAR-AI.md, ported from `_tools/warsim.py`, runs one step per in-game day on the overworld clock for every kingdom with a faction file and `realm` true, as soon as there are two. It needs the world from you:
- `WarApi.registerVillage(server, key, BlockPos, @Nullable ownerFaction[, contestable, forceOwner])` (villages lane: every village, owner = faction holding it or null for free; neutral-ground villages `contestable=false`), `removeVillage`, `villageOwner(server, key)`, `villagesOf(server, faction)`, **`WarApi.setCastle(server, faction, BlockPos)`** (court: when a realm is placed).
- Events (Fabric): `WarEvents.WAR_EVENT` (`WarEvent(type, day, faction, other, villageKey, pos(x/z), odds, text)`; types DECLARED, TRUCE, SKIRMISH, ASSAULT_WON, ASSAULT_FAILED, BROKE_OFF, GARRISON_WITHDREW, VILLAGE_CAPTURED, VILLAGE_REVOLTED, COALITION, ALLIANCE, BROKERED, HIRED, DESERTED, BRAIN_KILLED) and **`WarEvents.VILLAGE_CHANGED(level, villageKey, pos, newOwner|null, oldOwner|null, "conquest"|"revolt")`** (villages: start occupation, rebuilding, flip-back handling here).
- **Brain degradation:** `KingdomApi.brainBossKilled(server, faction, role)` (= `WarApi.brainBossKilled`). `role` is matched by keyword (also against the faction file's ruler slot name and title): `sultana`, `wazir`, `falconer` (Azmar), `jarl`, `hersir` (Hrafnholt), `hawk`, `dove`, `miser`/`almoner` (a dead Mirewick Magnate by leaning), `king` (Aldermark: no new wars for 7 days). **Per RIVALS-DECISIONS 10 (overrides WAR-AI 4): there is no generic refill.** Azmar's brain seats stay empty forever (the dead-seat fields are permanent, no relearning ramp; the Regent keeps only their own fields). Hrafnholt's and Mirewick's seats stay degraded until the court fires `public static boolean KingdomApi.successionEvent(MinecraftServer server, Identifier faction, String seat, String kind)` (alias `KingdomApi.onSuccession` with the same arguments, for reflective callers; both in jar 0.4.0) with kind `risen` (Hrafnholt: Hird-Captain promoted into the champion seat, once per seat after 10 days: restores that seat's fields at half strength), `heir` (Mirewick: the vote returns; the court limits each family to 2 heirs), `ruler_succession` or `vacant_forever` (no brain change). Aldermark's King death is the 2.0 baseline (7 days no new wars). Court fires brainBossKilled on the death and successionEvent on the succession.
- Reading the brain: `WarApi.effectiveBrain(server, faction)` (a `Tuning` copy with degradation applied), `brainField(server, faction, "min_odds")`, `brainDegraded(...)`. `Tuning.KEYS` lists the fields (snake_case: `war_freq, attack_rate, free_rate, commit, min_odds, retreat, def_retreat, noise, skirm_odds, scout, vet_gain, vet_cap, vet_decay, supply, fort_rate, fort_cap, fort_border_only, integ, unrest, pursuit, opening, delay_min, delay_max, timing, timing_p, broker, leader_cap, inc_base, inc_village, inc_noise, head_cost, hire_max, hire_cost, hire_pay, hire_reserve, hire_retain, desert, disorg, miser, hp, dmg, size, react, standing_loss, standing_gain`).
- **Faction file `army.brain`:** the four kingdoms have the WAR-AI section 3 values built in (by id path `aldermark`, `hrafnholt`, `azmar`, `mirewick`), so **a rival's JSON needs no brain numbers at all**. To override: the legacy fields (`war_frequency, min_attack_odds, intelligence_noise, veterancy_cap, supply_line_only, decision_delay_days, hire_mercenaries, mercenary_daily_cost`) and the new `"tuning": {"commit": 0.5, ...}` map (any `Tuning.KEYS`). Precedence: built-in < legacy fields (when changed from the schema default) < `tuning`. `army.health_multiplier/damage_multiplier/size_multiplier` feed `hp/dmg/size`.
- Reading and nudging the model: `WarApi.treasury/addTreasury(server, faction, coin)` (caravan raids and Counting-House robberies: add a negative delta), `heads`, `veterancy`, `killHeads`, `relation(server, a, b)` (0 peace, 1 truce, 2 war, 3 allied), `tension`, `coalitionTarget`, `declareWar`, `log`, `stepDays(level, n)` (tests, `/kingdom war step`).
- **Real battles:** an assault or patrol clash within 96 blocks of a player (loaded chunks) is fought by real soldiers inside the 15/30 caps (<= 15 soldiers for a village assault, 12 for a skirmish, the rest as reserves, 90 s timeout, retreat at the brain's `retreat` threshold) and the result is written back. Free-village defenders are faction `elsewhere:village_militia` (neutral to players). The villages lane may take assaults over: `WarApi.setBattleHandler(BattleHandler)` (return true to own it) and later `WarApi.reportAssault(server, id, attackerWon, attackerLostShare, defenderLostShare, playerInvolved)`.

**Standing trade/tribute:** `Standing.onTrade(player, faction)` (+1, max +5 a week) and `onTribute(player, faction, coins)` (+1 per coin, max +15 a week) return the Standing actually gained: 0 while Oathbreaker-locked, over the weekly cap, or at a Standing cap (Aldermark and any faction with `capUntilRulerKilled` stay at 29 until the ruler has been killed once). Multipliers are not applied to these two.

## 3. Free Companies (`FreeCompanies`) (live)
Faction `elsewhere:free_company` (`FreeCompanies.FACTION`, neutral to players, allied to whoever employs it, at war with its employer's enemies, kept current by `refreshStances`). Units `elsewhere:free_company_blade|bolt|captain` (`FreeCompanies.BLADE/BOLT/CAPTAIN`, plain skins, mixed vanilla gear). A kingdom whose brain has `hire_max > 0` (Mirewick) hires bands daily while at war or rich and pays `hire_pay` per head per day from its treasury; broke = they desert (40 percent to the enemy).
- `hire(server, employer, heads)`, `desert(server, companyId, @Nullable toEnemy)`, `companies(server)`, `companiesOf(server, employer)`, `heads(server, employer)`, `outbidPrice(company)` (10 days of pay), `outbid(player, companyId, scrip, enemyChoice)` (true when it turned; **caller removes the coins**; half the heads join the chosen enemy).
- Player sellsword contracts (Friendly+): `startContract(player, employer, enemy, ticks)`, `contractKills(level, uuid, employer)`, `markPaid(...)` (court pays 2 Scrip per kill).

## 4. Serf-tier variants and the levy (`Serfs`) (live)
`Serfs.variant(faction)` -> `Variant(type, countMultiplier, health, levy(fraction, weapon), paidByCouncil, caravanMin/Max)`: Aldermark `elsewhere:serf` x1 16 HP; Hrafnholt `elsewhere:thrall` x2 12 HP, levy 1/3 with `minecraft:stone_axe`; Azmar `elsewhere:porter` x0.5 (plus `caravanPorters(faction, rng)` = 1-2 with each caravan); Mirewick `elsewhere:peat_cutter` x1 14 HP, council paid. The faction file's `society.serfs` wins. `countFor(faction, base)`, `levyCount(faction, present)`, `levyWeapon(faction)`, and the strike: `Serfs.strike(server, faction)` when a Counting-House is raided (3 days), `isOnStrike(server, faction)` (no rebuilding). The villages lane spawns and animates them; the entity ids are only names to pass to your own types.

## 5. Unit id to custom entity class (`KingdomEntities.registerUnitClass`) (live)
`KingdomEntities.registerSoldierType(path, defaultUnit, factory)` (as before) binds the unit to the class and adds an egg; `KingdomEntities.registerUnitClass(unitId, entityType)` binds a unit id to an already registered `EntityType<? extends KingdomSoldier>` (several units may share one). `KingdomApi.spawnSoldier(level, unit, faction, pos, squad)` now picks the type from `Factions.effectiveUnitId(faction, unit)`, which follows the faction roster's `unit` redirect, so a roster line `"elsewhere:knight": {"unit": "elsewhere:hrafnholt_berserker"}` fields the Berserker class when the unit is registered. Unique soldier units are still plain `kingdom_unit` JSON (loadout, stats) plus your subclass for the abilities.

## 6. Multipliers (`Multipliers`) (live)
HP, damage and speed are applied to every unit by `Factions.unit`. New: `Multipliers.size(faction)` (file `size_multiplier`, else built-in Hrafnholt 1.2, Azmar 0.75, Mirewick 0.9), `scale(faction, base)`, `patrolSize(faction, night)` (file `patrol_day/night`, else Hrafnholt 7/8, Azmar 5, others 6), `makeup(faction)` and `roll(faction, rng, allowMounted)` from the file's `army.makeup` (unit id -> weight). `ArmyComposer.members(rng, n, allowMounted, faction)` and `KingdomApi.spawnPatrol/spawnCompany` use the faction's makeup; a faction without one (Aldermark) rolls exactly as 2.0.

## 7. Squad-tactics framework (`dev.elsewhere.kingdom.tactics`) (live, jar 0.4.1)
Typed profile interface, registry and shared behaviour library from FIELD-TACTICS section 1.
- **`TacticsProfile`** (interface, only `faction()` is required, everything else has a default): `decisionInterval()` (10..20 ticks), `behaviours()`, `roles()` (`Map<UnitType.Role, RoleSpec(standoff, strafe, aggression)>`), `minLocalOdds()`, `retreatOdds()`, `reactionTicksMin/Max()`, `blunderRate()`, `signals()` (`SignalSet(horn, banner, shoutChance)`), `intelFilter()` (`Perception.Filter`), `intelNoise()`, `mobPolicy()` (`"default"` or `"careful"`), `cheese()` (`CheeseCounter` list: `id`, `detect(Perception)`, `answer(Perception, SquadMemory, SquadBrain, uses)`), `passThrough()` (true = no brain, the 2.0 goals run; only Aldermark), `onSquadCreated(SquadBrain)`.
- **`SimpleTacticsProfile.builder(faction)`** builds one from values (`interval, minOdds, retreatOdds, reaction, blunder, mobPolicy, signals, intel, intelNoise, behaviour(s), cheese, role, onSquadCreated`).
- **`Behaviour`** (interface): `id()`, `bucket()` (`SURVIVE > REACT > ENGAGE > POSITION > IDLE`, first bucket with a score above 0 wins), `score(Perception, SquadMemory, SquadBrain)`, `plan(...)` returning `Order.Orders(perSoldier, signal, flags)`, plus `cooldownTicks/minCommitTicks/cost/orderTicks` defaults. Behaviours are pure decision code: they read perception and write orders; `OrderGoal` (on every soldier, priority above the 2.0 goals) executes them. `Order.OrderKind`: HOLD, MOVE_TO_SLOT, ADVANCE, FALL_BACK, KEEP_RANGE, FOCUS, SHIELD_UP, CHARGE, FLANK, LURE, RALLY, WITHDRAW, SKIRMISH, GUARD_ALLY, PICKET.
- **`Behaviours.library()`** is the shared set: `engage_default, shield_wall, kite_melee, focus_fire, refuse_fight, fall_back_regroup, back_off_creeper, break_los, high_ground, call_help, picket, protect_unit, corner_trap, counter_cheese` (each also a public constant `Behaviours.SHIELD_WALL` etc.). Mix library entries with your own.
- **Register:** `KingdomApi.registerTactics(faction, profile)` (= `TacticsRegistry.register`) from your mod initialiser. `TacticsRegistry.of(faction)` returns your profile, else Aldermark's baseline (`TacticsRegistry.Baseline.PROFILE`, a pass-through reproducing 2.0). Master switch: `tactics.enabled` in config (and `TacticsRegistry.enabled`).
- **Data overrides:** the faction JSON `tactics` block (`decision_interval, min_local_odds, retreat_odds, reaction_ticks [min,max], blunder_rate, mob_policy, behaviours {id: {weight, min_ratio}}, roles {ROLE: {standoff, strafe}}, signals {horn, banner, shout_chance}`) overrides the registered values and reloads with the faction.
- **Cost rules:** decisions every 10..20 ticks per squad, at most 2 decisions per tick server-wide, interval doubles when the server average tick exceeds 40 ms, perception is cached per decision.

Minimal profile:
```java
public final class MyTactics {
	static final Behaviour LOOT_RUSH = new Behaviour() {
		public String id() { return "my_loot_rush"; }
		public Types.Bucket bucket() { return Types.Bucket.ENGAGE; }
		public double score(Perception p, SquadMemory m, SquadBrain b) { return p.odds().ratio() > 1.0 ? 0.7 : 0; }
		public Order.Orders plan(Perception p, SquadMemory m, SquadBrain b) {
			Map<UUID, Order> out = new HashMap<>();
			for (Perception.Friendly f : p.friends()) out.put(f.id(), Behaviours.o(Types.OrderKind.CHARGE, null, null)); // null target: nearest contact
			return Order.Orders.of(out, null);
		}
	};
	public static void init() {
		TacticsProfile prof = SimpleTacticsProfile.builder(Identifier.parse("elsewhere:mirewick"))
			.interval(10).minOdds(1.5).retreatOdds(0.85).blunder(0.2).mobPolicy("careful")
			.behaviours(Behaviours.library()).behaviour(LOOT_RUSH).build();
		KingdomApi.registerTactics(prof.faction(), prof);
	}
}
```
Public helpers for plans: `Behaviours.targets(p)`, `visible(p)`, `nearest`, `axis`, `slot(centre, axis, forward, lateral)`, `rally`, `priority`, `o(kind, pos, target)`. See `tactics/Behaviours.java` for the library's own plans and `tactics/Perception.java` for what a behaviour may read.

## 8. Client feature layers on NPCs (`client.AvatarLayers`, jar 0.4.2) (live)
26.3 draws every `AvatarRenderState` with vanilla's player `AvatarRenderer`, so layers added to our own renderers never run. `dev.elsewhere.kingdom.client.AvatarLayers.add((parent, models) -> new MyLayer(parent, models))` (client side, from your `ClientModInitializer`) adds the layer to the vanilla avatar renderers (wide and slim) through Fabric's `LivingEntityRenderLayerRegistrationCallback`. The layer is `RenderLayer<AvatarRenderState, PlayerModel>` and **must return at once unless `state` is your own render-state type** (real players use the same renderer). `TabardLayer` is registered this way. Also `NpcSkin.apply(state, texture)` (in the jar since 0.4.1) for the skin.

## Commands and saved data
`/kingdom war status|step <days>|log` (op). Saved data `data/elsewhere/kingdom_war.dat` (new, only written once two kingdoms exist or Standing counters are used). `Factions.Faction.parent()` is public.
