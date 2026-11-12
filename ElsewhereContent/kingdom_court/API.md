# Kingdom court API (module `ElsewhereContent/kingdom_court/`, package `dev.elsewhere.kingdom_court`)

Compile against `devlibs/elsewhere_kingdom_court.jar` (the court lane copies each new jar into your `devlibs/`; it needs `elsewhere_kingdom.jar` beside it).
Entry `CourtContent.init()` is idempotent. **Everything is server-side unless marked.** Version in the jar name (this page: 0.5.0, 2.1 rivals build).

Leader **slots** are the keys of a faction file's `rulers` map: `king, marshal, castellan, master_at_arms, huntsman, chancellor` (rank order; `society.leaders` picks the ones a faction uses, so Hrafnholt lists six, Azmar four, Mirewick five without `king`). A slot's number in `Ruler` (`Ruler.byKey("marshal")`) is its rank. Monarchies: the `king` slot is the ruler. Oligarchy (`society.government = oligarchy`): the Chair.

## 1. Leader role to custom boss entity (`api.Leaders`)
```java
// mod init, after your EntityType exists. Any Mob; implementing Leaders.LeaderBoss is optional.
Leaders.register(id("hrafnholt"), "king", HrafnholtBosses.HALVARD);          // EntityType<? extends Mob>
public class Halvard extends DuelistBoss implements Leaders.LeaderBoss {
    public void onCourtSpawn(Identifier faction, String slot, @Nullable BlockPos home) { ... }  // before addFreshEntity
    public void onHolder(boolean holder) { ... }                               // monarch or Chair status changed (Totem, crown, bonus)
}
```
The court spawns it at the slot's post (castle) or as a roaming Lord, tags it `kingdom_court_leader:<faction>|<slot>` (`Leaders.of(entity)` reads it), sets the faction on a `KingdomSoldier`, applies the faction's ruler bonus to MAX_HEALTH and ATTACK_DAMAGE (`Leaders.applyGating`), names a refilled seat's new holder, and tracks death: drops (section 2 of `Shops`/drops below), succession or Chair, brain-boss event, the fall. A slot with no registration keeps Aldermark's built-in `CourtRuler`. Your boss must not add its own death drops for the faction's `drops` lines.
- **Drops** (non-Aldermark): the file's `drops` lines. A line that is a single item (min=max=1) of a mod namespace that is not the currency and not a "seal" is a **unique**; the first unique is the signature. Uniques roll per credited player (killer, else players within 48 blocks) with the boss DropRule: first kill 100/50, repeat 67/33, pity +10 up to 60, counted per faction and slot (a refilled seat's new holder is a repeat kill). Everything else (currency, `lore_seal_*`, emeralds) drops once at its own chance. `CourtDrops.rollRival(...)`, `CourtDrops.chance(...)` are public. Aldermark keeps its 2.0 flat table.
- Events: `CourtEvents.RULER_DIED(level, ruler, killer)`, `FACTION_FALLEN(server, faction)`, `BRAIN_ROLE(server, faction, slot, alive)`.

## 2. Currency and shop per faction (`api.Shops`)
Data: faction file `economy {currency, shop [{item, count, price, extra, weekly_limit}]}`. Calls (all take a `ServerPlayer`):
`currency(faction)`, `count(p, faction)`, `take/give`, `offers(p, faction)` (list of `Offer(index, stack, basePrice, price, extra, weeklyLimit, remaining)`, Standing discount Friendly -5 percent, Allied -15 percent), `buy(p, faction, index)` returns `Result` (OK, NO_SHOP, WEEKLY_LIMIT, CANT_AFFORD, HOSTILE ...). Weekly limits are counted **per player per faction** (court ledger, in-game weeks). One trade = Standing +1 (cap +5 a week). `tribute(p, faction, coins)`: coins taken, Standing +1 each (cap +15 a week). `exchange(p, from, to, amount)`: only Mirewick's Counting-House (`Shops.exchangeFaction`): anything to Scrip at 4:3, Scrip to anything 1:1 while Friendly or better.
Villages: your Steward equivalent can call `Shops.offers/buy` (or just use the court NPC). Currency items must exist in `BuiltInRegistries.ITEM` (lines for missing items are skipped).

## 3. Diplomacy NPCs (`api.Diplomacy`, `npc.DiplomatNpc`, command `/diplomacy`)
Entity types `elsewhere:kingdom_lawspeaker`, `kingdom_royal_envoy`, `kingdom_clerk` (invulnerable, non-combat `KingdomSoldier`s; renderer is the soldier renderer). One per realm is spawned inside the castle gate (`Diplomacy.bind(faction, Role)`; defaults: hrafnholt Lawspeaker, azmar Envoy, mirewick Clerk). **Skin:** the unit ids `elsewhere:kingdom_lawspeaker|kingdom_royal_envoy|kingdom_clerk`; reskin per faction with a `roster` entry in the faction file (`"elsewhere:kingdom_lawspeaker": {"skin": "..."}`). Name: lang `entity.elsewhere.kingdom_lawspeaker` etc.
Right-click prints a menu; `/diplomacy status | tribute <n> | shop | buy <n> | duel <slot> | offer | contract | votes <topic> | bribe <slot> <topic> <up|down> | exchange <from> <to> <n>` acts on the nearest NPC within 8 blocks.
Hooks: `Diplomacy.readout/offerAvailable/acceptOffer/hasPact(level, faction, player)` (Azmar's tribute offer: a player with aggression 6+ and Standing under 30 is paid 32 Dinars and left out of its wars for the week; **the war lane's hunting squads should skip a player with `hasPact`**), `signContract/contractActive/payContractKill` (Mirewick sellsword contract), `npcNear`.

## 4. Duel challenge (`api.Duel`)
`Duel.challenge(player, faction, slot)` returns `Result`: Allied only, not the ruler (King, or the seated Chair), alive and loaded, once per in-game week per player and faction. The Lord fights; damage that would take him under 10 percent is cancelled and he yields: he lives, the challenger gets the kill drops (DropRule, counted as a kill), no Standing is lost. `Duel.isDueling(a, b)` is true for the pair: **the war lane must skip Standing loss and aggression for them.** Names: `Duel.registerName(faction, "holmgang")`. Event `CourtEvents.DUEL_ENDED`.

## 5. Oligarchy (`api.Oligarchy`, events in `CourtEvents`)
- Chair = living Magnate with the largest vault, re-checked every `chair_days`. Chair dies: vacant 1 in-game day (no bonus, `Oligarchy.chair(...)` null), then the largest vault takes it. Vaults: `Oligarchy.vault/addVault` (seeded 300-1000, daily income per Magnate; your Mirewick code may add to them).
- Votes: `Oligarchy.vote(server, faction, Topic)` (WAR, PEACE, ALLIANCE, HIRE) returns `VoteResult(yes, no, abstain, passes, delayDays 1-2, byMagnate)` and fires `COUNCIL_VOTE`; `tally` does not fire. Leanings hawk/dove/miser per Magnate (`registerLeanings(faction, list)`, `setLeaning`). `bribe(player, faction, slot, topic, +1/-1)`: Friendly or better, 40 of the currency, one Magnate per player per week; moves that stance one step this week. Dead Magnates have no vote.
- Seats (decision 10, no generic refill): after `seat_refill_days` (7) an HEIR of the same family sits (same surname, new first name, same slot, kit and leaning; `Succession.registerHeirNames`), `SEAT_REFILLED` + `SUCCESSION(heir)`. 2 heirs per family (3 holders), then the seat is empty for good (`SUCCESSION(vacant_forever)`). Heirs are repeat kills. All dead before any heir: `darkLanterns` flag, `DARK_LANTERNS(server, faction)`, then the normal fall (below) plus the Counting-House vault chest opens.
- Events: `CHAIR_CHANGED(level, faction, oldSlot, newSlot, reason)`, `COUNCIL_VOTE`, `SEAT_REFILLED`, `DARK_LANTERNS`.

## 6. The fall (every faction)
All leaders dead: `CourtLife.fall(server, faction)`: faction hostility off, conquest stops, sixth of the soldiers (by UUID) become roaming neutrals of `elsewhere:<faction>_remnant`, the rest vanish, squads disband, `FACTION_FALLEN(server, faction)`. Saved; survives restarts. `CourtApi.kingdomFallen(server, faction)`.

## 7. Castle palette and extra pieces from data (`world.CastleStyles`)
`data/<ns>/kingdom_castle/<faction path>.json` with `palette` (block-for-block replace of Aldermark's layout, properties kept) and `pieces` (`box`, `column`, `obelisk`, `set`), see the class javadoc. Reloads with `/reload`. `CastleStyles.register(faction, style)` for code. Structures `elsewhere:castle_hrafnholt|azmar|mirewick` (+ structure sets and `#elsewhere:<faction>_castle` tags) ship with the court; the site is picked by the placement code below.

## 8. Realm music (`api.RealmMusic`)
`ENTER`/`LEAVE` events (return true from ENTER to play it yourself, e.g. boss_music), `registerRealm(faction, SoundEvent)`, `registerTheme(faction, slot, SoundEvent)`, or the file's `music` prefix (`<prefix>.realm`, `<prefix>.<slot>`).

## 9. Brain-boss death into the war lane
When a leader whose slot is in the faction file's `army.brain.brain_roles` dies, or its seat comes back, the court fires `CourtEvents.BRAIN_ROLE(server, faction, slot, alive)` and calls by reflection `dev.elsewhere.kingdom.KingdomApi.onBrainRoleChanged(MinecraftServer, Identifier faction, String slot, boolean alive)` if it exists (see INTEGRATION-REQUESTS).

## 10. Placement (RIVALS-DECISIONS 7 and 15; court 0.5.1)
**One realm per faction in the whole world, ever**: the site is chosen once at first start (`CourtState.siteChosen`, saved), and `CourtWorld.Placement` only builds at that one site.
- **Aldermark** (the anchor): unchanged 2.0 first-fit on seeded rings 1,300-2,400 blocks from spawn (`CourtSite.choose`), temperate tag, never-generated chunks. Existing worlds keep their saved site.
- **Rivals** (`CourtSite.chooseRivals`, called once from `CourtLife.syncRealms` for every faction without a site): scored, not first-fit, and chosen jointly.
  - Wedge: 90 degrees centred on Aldermark's bearing from spawn (seeded bearing if Aldermark has no site), 1,500-5,000 blocks from spawn.
  - Spacing: castles at least 1,800 apart; every realm has another realm within 3,000.
  - Score: biome fit over 25 samples within 192 blocks, terraform cost (castle height range above 16 is rejected), dry centre, the faction's terrain wish, a moderate distance. A cheap biome pass runs first; heights are sampled only for each faction's best 40. Best joint combination of the top 15 (then 40) wins. Total time is logged ("Rival realm placement ... took N ms"), at most 1.5 s.
  - Faction JSON (all optional, read at runtime from `data/<ns>/kingdom_faction/<id>.json`): `"site": {"terrain": "rise" | "flat", "near_water": true}` (defaults: Hrafnholt rise; everyone else flat + near water) and `"biomes_fallback": [ids or #tags]` (default: the faction's `biomes` plus `#elsewhere:court_realm_biomes`). `biomes` itself is the first-choice set.
  - Existing worlds: footprints must be in never-generated chunks (castle, both towns, road). If nothing fits, the band moves outward 1,000 blocks at a time up to 12,000; only after that do biomes relax (own, then fallback, then any dry land). Deterministic from the seed.
  - API for tools and tests: `CourtSite.Spec`, `CourtSite.spec(resourceManager, faction)`, `CourtSite.chooseRivals(seed, spawn, gen, randomState, heightAccessor, generated, aldermarkSite, fixedSites, specs)`, `CourtSite.lastSearchMs`.
- **Waterfront (RIVALS-DECISIONS 16a, Hrafnholt):** a site with `site.waterfront: true` (default for Hrafnholt) wants water: at least 25 percent of 24 cheap samples at 32-96 blocks are water (floor at or below sea level), the castle faces north or south (the towns lie east and west), and the shore is 56-136 blocks out along that axis. The bearing (radians, `CourtState.waterBearing`) and shore distance (`CourtState.waterDist`) are saved with the site; both are empty when the realm had to be placed without water (last-resort levels).
  - Pieces (`world.Waterfront`): a 5-wide plank jetty from the castle down to and out over the water (pilings, fence posts, lanterns), a boathouse beside it and one or two block longships (hull, keel, stems, mast, striped sail, shield rail) moored beside the jetty end. Styled from the optional `waterfront` object of `kingdom_castle/<faction>.json` (`jetty`, `boathouse`, `longship`), spruce/dark oak/wool defaults otherwise. Only air and plain water are ever replaced, nothing is flooded.
  - Separate structure `elsewhere:waterfront_hrafnholt` (+ structure set, same placement) with `terrain_adaptation: none`, so the shore is never dug or flooded; the castle structure keeps `beard_box`. The shore is searched from 56 blocks out because the castle's own blending ends about 46 blocks out. Its chunks are part of the never-generated check.
  - For the jetty raids: `CourtState.waterBearing/waterDist` give the jetty line (end of deck at castle + (waterDist + 14) along the bearing, x/z of the site).
- Tested over 20 seeds plus an existing-world case with the inner wedge pre-generated (`SelfTestRivals.placement`).

Older calls (2.0): `CourtApi.kingdomFallen/realmCenter/rulerAlive/crowned(server[, faction])`, `CourtEvents.RULER_DIED/KINGDOM_FALLEN/TOWN_POPULATED`.

## 11. Succession (`api.Succession`, event `CourtEvents.SUCCESSION(server, faction, seat, kind)`)
Kinds: ruler_succession (highest living Lord takes the throne, Azmar's is titled "Regent" via `registerRulerTitle`), risen (Hrafnholt: after 10 days the living Hird-Captain, unit `elsewhere:kingdom_captain`, is promoted into the empty champion seat `chancellor`, once per seat, +15 percent HP and damage, repeat-kill drop odds, `LeaderBoss.onRisen(2)`; `registerRisen(faction, seat)`), heir (Mirewick), vacant_forever. Azmar: seats are never filled. Also passed straight to `KingdomApi.successionEvent(server, faction, seat, kindName)` (0.5.0+: no reflection; Risen restores that seat's brain fields at half strength, heir returns the vote).
## 12. Realm music
SoundEvents `elsewhere:hrafnholt_music.realm`, `azmar_music.realm`, `mirewick_music.realm` are registered and bound as realm themes; re-sent every 240 s.
## Brain-boss (updated)
Every leader death fires `BRAIN_ROLE` and calls `KingdomApi.brainBossKilled(server, faction, "<slot> <title> <name>")` directly (war API is live). Lord and ruler kills call `Standing.onKilled` (not duels). The court registers the `Standing.setRealmLocator` and calls `WarApi.setCastle`.

## 13. Trading per kingdom (`trade.*`, RIVALS-DECISIONS 12)
Aldermark's Steward is unchanged. A faction trades the new way when its stock has a `mode` (below); otherwise `Shops` and the 2.0 shop menu apply. Mechanics: `trade.HrafnholtTrade`, `AzmarTrade`, `MirewickTrade` (plumbing in `Trade`, saved state in `TradeState`, world data `kingdom_trade`), the menus and `/diplomacy` sub-commands in `TradeUi`. Right-click a trader NPC for its menu (every line is clickable). All server-side; Standing gains go through `Standing.onTrade` (the war lane's +5 a week cap).

**Trader NPCs** (invulnerable; entity ids `elsewhere:kingdom_smith|kingdom_market_inspector|kingdom_auctioneer|kingdom_caravan_master`, plus the three diplomats): Hrafnholt Lawspeaker (the Thing) and Smith; Azmar Envoy (treasury, tribute offer), Market Inspector and, on caravan days, the Caravan Master; Mirewick Clerk (market, loans, exchange, bribes) and Auctioneer. The court spawns the NPCs once at the castle gate (`Diplomacy.bindExtra(faction, role)` adds another). Skins `textures/entity/kingdom/trader_*.png`; lang `entity.elsewhere.kingdom_*`; a faction file `roster` entry may reskin the unit ids.

**The 4 items:** `elsewhere:hrafnholt_arm_ring`, `elsewhere:azmar_commission_slip`, `elsewhere:azmar_letter_of_credit`, `elsewhere:mirewick_loan_note`. The slip, letter and note are lore over server-held state (a forged item buys nothing). All four are in `data/testmeter/tags/item/transmutation_excluded.json`; JEI lines are in `INTEGRATION/jei_info_court_trade.json` (the integrator merges them into `assets/elsewhere/jei/info.json`).

**Stock schema.** Lookup order: `TradeData.put(faction, Stock)` in code; the `trade` object of the faction file `data/<ns>/kingdom_faction/<path>.json`; `data/<ns>/kingdom_trade/<path>.json`; the court's shipped defaults `data/elsewhere/kingdom_trade_default/{hrafnholt,azmar,mirewick}.json`; last resort: derived from the faction file's `economy.shop`. A line of an unregistered item is skipped. `/reload` re-reads it.
```
{ "mode": "thing" | "posted" | "floating",
  "goods":   { "minecraft:iron_ingot": 30, "minecraft:leather": 20, "minecraft:cod": 10, "minecraft:salmon": 10, "minecraft:wheat": 5 },
                 // Hrafnholt barter: tenths of one coin per item (default shown: 1 iron ingot = 3 coins)
  "lines": [ { "item": "ns:id", "count": 1, "price": 30, "tier": "smith|thing|top  |  common|workshop|royal  |  market",
               "rings": 4,            // top tier only: Arm-Rings (coin and barter are refused)
               "weekly_limit": 2,     // per player, per faction, per line, in-game weeks
               "barter": [ {"item": "ns:id", "count": 10} ],   // Hrafnholt: the line's own goods prices (default: every good in "goods" at its value)
               "extra": [ {"item": "ns:id", "count": 20} ],    // goods taken on top of the price
               "commission": false,   // Azmar: made to order at the royal workshop
               "sell": 12 } ],        // Mirewick: what the market pays for one (default price / 2)
  "caravan": [ [ lines... ], [ lines... ] ],   // Azmar: one list per visit, rotating every 4 days (a line's index inside its list is what /diplomacy buy takes)
  "auction": [ { "item": "ns:id", "count": 1, "start": 40, "max": 120 } ] }   // Mirewick: lot pool (3 are picked per auction day)
```
Defaults for a missing field: `mode` by faction path (`hrafnholt` thing, `azmar` posted, `mirewick` floating); tier `thing` / `common` / `market`; count 1; rings 0; weekly_limit 0 (none); sell = price / 2; goods as above; auction `start` 20, `max` 3x start. The shipped defaults are in `data/elsewhere/kingdom_trade_default/`.

**Hrafnholt.** `HrafnholtTrade`: the Thing is open every 3rd in-game day (day number divisible by 3), dawn to dusk (ticks 0-12,000); on other days only `smith` lines (the Smith's stall) are sold. Every coin price lists a goods price beside it (`quotes`, `barterOptions`, `buyBarter`). `top` lines cost `rings` Arm-Rings only (`buyRings`). Deeds: `addDeed(p, faction, n, why)` (kills of an enemy soldier or monster inside the realm, duel wins; at most 10 a day; none while Hostile); 5 deeds = 1 ring, handed over by `claimRings` from the Jarl, or the highest-ranking living champion once he is dead (`giver`). `cutRing` gives 8 Hacksilver, `repair` mends the held tool for up to 6. A ring in the pack takes 10 percent off every price. There is no haggling.

**Azmar.** `AzmarTrade`: fixed prices (Standing discount only; no drift), tiers by Standing (Neutral `common`, Friendly `workshop`, Allied `royal`; TIER_LOCKED otherwise). `order(p, faction, line, colour)` pays half and gives a Commission Slip; `collect(p)` after 3 days pays the rest and gives the item with the buyer's name and trim colour in its lore. Caravan: every 4th day (day number divisible by 4) a Caravan Master, Hired Porters and camels stand at one of the faction's villages (`villageProvider`, by default `VillagesApi.villageCenters` by reflection, else a castle town) from the moment a player is within 96 blocks until the day turns, selling the rotating `caravan` stock; killing mobs or enemy soldiers that attack it, within 48 blocks of a member, pays 2-6 Dinars and +3 Standing (`onKill`, at most 6 a day). Letters of Credit: `deposit` / `withdraw` at the Envoy, Market Inspector or Caravan Master; the balance is world data, so death or a lost letter loses nothing.

**Mirewick.** `MirewickTrade`: floating prices per line (+5 percent a purchase up to +50, selling -5, drift back 10 points a day, +20 percent while at war via `warProvider`), saved per world. Haggle (sneak-use a trader, `haggle`): chance 30 + Standing/4, minus 15 at a trader whose patron Magnate is a miser (`DiplomatNpc.patron()`); success is 20 percent off from that trader for the day, a failure locks that trader until the next day. Loans (`borrow`: up to 64 Scrip, 96 Friendly, 128 Allied; 25 percent interest; due in 7 days; a Loan Note): when late, Standing -20 once and, only while the borrower is inside the realm, one debt-collector squad (3 soldiers and a sellsword, tag `kingdom_debt_collector:<uuid>`); a death dealt by them pays the debt from Scrip (`onPlayerDeath`). Auction every 7th day: 3 lots from the pool; opens at midday for 1 in-game hour (1,000 ticks); NPC bidders climb to a ceiling; right-click the Auctioneer with Scrip in hand (or `/diplomacy bid <lot> <amount>`) to outbid by at least 5 percent; outbid coins are refunded at once; at the bell the winner gets the lot, and if the NPC bidders beat the best bid the coins return. The exchange and bribes are unchanged.

**Commands** (`/diplomacy ...`, nearest trader within 8 blocks): `shop`, `buy <n>`, `barter <n> [good]`, `rings <n>`, `claim`, `cut`, `repair`, `deposit <n>`, `withdraw <n>`, `order <n> <colour>`, `pickup`, `sell`, `haggle`, `loan <n>`, `repay`, `lots`, `bid <lot> <amount>`, plus the existing `status|tribute|duel|offer|contract|collect|votes|bribe|exchange`.

Dev only: `Trade.testOffset` moves the trade clock; `MirewickTrade.warProvider` and `AzmarTrade.villageProvider` are replaceable.

## 14. The 300 max-health cap (`api.HpCap`, RIVALS-DECISIONS 13)
No human kingdom boss exceeds 300 max health after every modifier (gating, succession, Risen +15 percent, Regents, heirs, phases, the Test Meter multiplier). `HpCap.clamp(entity)` adds a negative `elsewhere:human_hp_cap` ADD_VALUE modifier that brings the total down to 300 (and removes it when not needed); `HpCap.uncapped(entity)` is the value without it. The court calls it on spawn, in `Leaders.applyGating`, `CourtRuler.refreshGating`, `Succession.promote` (Risen) and on entity load. There is no periodic re-clamp any more (RIVALS-DECISIONS 17; vanilla `setHealth` already stops overhealing), so a mod that changes a leader's max-health modifiers should call `HpCap.clamp(entity)` itself. Anything `Leaders.register` spawns is covered; a boss that raises its own max health between those calls should call `HpCap.clamp(this)` itself. Aldermark's King and Lords are scaled by 300/360 (King 250 + 10 per living Lord, Marshal 158.3, ...) and compensated: +6 armor +3 toughness (King +8 and +4), +0.4 knockback resistance, +25 percent damage, move cooldowns 20 percent shorter and defensive moves (parry, portcullis) 30 percent shorter (`CourtRuler.compensate`). The rivals' numbers come from their faction files and are only clamped.
