# Factions: adding a rival kingdom as data plus skins (Elsewhere 2.1 foundation)

Aldermark is the first faction file; a rival is another file plus skins. This page is the how-to, the schema, and the honest status of what the rival-kingdom decisions (`Ideas/kingdom/RIVALS-DECISIONS.md`, revision 2) need versus what exists. Code: module `kingdom` (package `dev.elsewhere.kingdom`) and `kingdom_court`.

## Add a rival in 5 steps
1. **Skins.** 64x64 player-layout skins on plain clothing (no painted armor; real vanilla armor is equipped by the unit). Put them in `assets/<ns>/textures/entity/kingdom/<faction>/`. Recolouring Aldermark's `soldier_a/soldier_b/archer/officer/captain` with `tools/gen_test_faction_skins.py` is the quick placeholder.
2. **Faction file.** `data/<ns>/kingdom_faction/<name>.json` (faction id `<ns>:<name>`), see the schema below. `/reload` reloads it.
3. **Roster.** In `roster`, map a base unit id (e.g. `elsewhere:knight`) to this faction's version: `skin`, `armor` (per slot), `armor_dye`, `main_hand`, `off_hand`, `mount`, `mount_armor`, `trim`, `health_scale`, or `unit` (use another unit type wholesale, e.g. a new `data/<ns>/kingdom_unit/berserker.json` for a unique soldier). Units not listed are the base unit with the faction trim and multipliers.
4. **Realm (optional).** For a castle: copy `worldgen/structure/castle_aldermark.json` and `worldgen/structure_set/castle_aldermark.json`, add `"faction": "<ns>:<name>"` to the structure and to the placement, add the structure to a locate tag. Realm biomes come from the file's `biomes`. The castle layout is Aldermark's (palette reskin per faction is NOT built).
5. **Items.** Drops and shop lines name items by id. A rival-specific currency or signature item must be registered in code (a one-line `reg(...)` plus model, lang and the Test Meter exclusion tag); everything else is data.

Nothing is registered in production for a rival until its file exists. The self-test faction `elsewhere:test_marches` (green recolour of Aldermark) lives in `dev/TestFactions.java`, is registered only by the self-tests, and its placeholder skins are in `assets/elsewhere/textures/entity/kingdom/testfaction/` (**the merge should exclude that folder**, like `dev/`).

## Schema (`kingdom_faction/<name>.json`, all optional except `name`)
| Field | Meaning |
|---|---|
| `name`, `name_key` | display name; a translation key wins |
| `banner {primary, secondary}`, `tabard {color, trim}` | heraldry and tabard colours (int or `"#RRGGBB"`) |
| `trim {material, patterns}` | replaces the armor trim of every unit that has one |
| `hostile_to_players` | default stance towards players with no recorded Standing |
| `allies`, `peace` | relations to other factions by id; everything else is at war (config `factions.defaultStance`) |
| `roster` | see step 3 |
| `rulers` | slots `king, marshal, castellan, master_at_arms, huntsman, chancellor`: `name`, `name_key`, `title`, `health`, `health_cap`, `drops [{item,min,max,chance}]` |
| `biomes`, `music`, `realm` | realm biome ids or `#tags`; sound-event prefix (`<prefix>.<slot>`, `<prefix>.realm`); false = no castle |
| `society` | `government` (`monarchy`/`oligarchy`), `ruler_bonus {type, amount}` (`hp_per_lord`, `hp_per_other_magnate`, `damage_per_champion`, `none`), `leaders` (slots in rank order; default every key of `rulers`), `standing {start, cap_until_ruler_killed}`, `serfs {type, count_multiplier, health, levy {fraction, weapon}}`, `chair_days`, `seat_refill_days` |
| `economy` | `currency` item id, `shop [{item, count, price, extra, weekly_limit}]` |
| `army` | `units`, `unique_units`, `makeup`, `health_multiplier`, `damage_multiplier`, `speed_multiplier`, `size_multiplier`, `patrol_day/night`, `brain {war_frequency, min_attack_odds, retreat_instead_of_die, veterancy_cap, intelligence_noise, supply_line_only, decision_delay_days, hire_mercenaries, mercenary_daily_cost, brain_roles [{role, degrades[]}]}` |

## What the core does now
- `Factions`: live registry; `Factions.unit(faction, unit)` resolves a soldier's unit through the roster (Aldermark: the identical base instance), applies HP/damage/speed multipliers.
- Relations per pair: `WAR` (fight), `TRUCE`, `TENSION`, `PEACE`, `ALLIED` (all non-war states ignore each other). Default war; JSON `allies`/`peace`; `KingdomApi.setStance` and `/kingdom stance a b war|peace|allied` (saved).
- Caps (15 per player, 30 per area, 120 global, 8 mounted) count every faction together.
- Patrols of different factions fight when they meet (target scan). With no player near, virtual squads that meet resolve **abstractly**: `FactionConflict` (seeded roll, casualties on the virtual squads, record stored in `KingdomState`, `factions.abstractConflicts`).
- Per-player, per-faction aggression ledger (marks, hunts) and Standing ledger (-100..100, bands, start value, Aldermark's Wary cap until its ruler is killed).
- Court: one `CourtState` per faction (Aldermark's keeps its 2.0 data file, others live in `CourtRealms`), realm site per faction (one per faction per 6,000-block square, 600+ blocks apart, 1,300-2,400 from spawn), rulers named/sized/dropping from the file, succession, the fall, remnants, the oligarchy's Chair and seat refills, per faction.

## Status of the RIVALS-DECISIONS requirements
| # | Requirement | State |
|---|---|---|
| 1 | Leader list + `government` from data; ruler bonus types | **In place**: slots from data (Hrafnholt 6, Azmar 4, Mirewick 5 are just lists), monarchy/oligarchy, the three bonus types, Chair rotation by rank (stub for "largest vault"), 7-day seat refill, fall when all dead. **Stubbed**: the Chair's 1-day vacancy (next Magnate at once), council votes, Totem, Dark Lanterns aftermath, new kits (the six existing Aldermark kits are the only fight styles; "Human Duelist" is future work) |
| 2 | Currency + shop table per faction | **Schema + loading + Aldermark data**. Shops are not consumed yet (the Steward lives in kingdom_villages); weekly limits per faction are not counted yet |
| 3 | Standing -100..100, bands, start, hostility from Standing | **In place**: ledger, bands, start values, targeting rule, Aldermark cap. **Stubbed**: Standing gains/losses (kills, tribute, trade), enemy-of-my-enemy, oathbreaker, drift, Wary realm-only, ally perks, duels. A player with no recorded Standing uses the faction's stance flag (so Aldermark and the lore module's neutral switch behave exactly as in 2.0) |
| 4 | Alliance next to peace/tension/war/truce | **In place** as relation states and per-faction lists; the Tension/war/truce/alliance *simulation* is not built |
| 5 | Serf tier + 2 unique units per faction | **Schema + loading** (serfs, levy, `unique_units`, `units`); consuming it is the villages module's job. Unique unit types are plain `kingdom_unit` JSON (new AI abilities such as fire pots are future code) |
| 6 | Per-faction HP/damage/speed/size multipliers, makeup | **In place** for HP/damage/speed (applied to every unit); `size_multiplier`, `makeup`, patrol sizes are loaded but read by no one yet |
| rev 2 | `brain` block + brain roles | **Schema + loading only** (Aldermark carries the baseline); the abstract war layer does not read it yet. Tuned numbers come from WAR-AI.md |

## Tests
`./gradlew runServer` in `kingdom/` (fresh superflat world, own JVM) and in `kingdom_court/`. Faction checks are `dev/SelfTestFactions.java` in each module.

## Follow-ups from RIVALS-DECISIONS (added by the lead, 2026-11-12)
- Realm placement must follow RIVALS-DECISIONS section 7: castles at least 1,800 apart, every realm with a neighbour within 3,000, ring 1,200-2,500. The foundation currently uses 600+ apart and 1,300-2,400; fix this in the rivals build.
- Brain block values come from Ideas/kingdom/WAR-AI.md (war simulation), not hand-picked.
