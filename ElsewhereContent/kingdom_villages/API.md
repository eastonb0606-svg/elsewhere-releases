# kingdom_villages API (2.1: faction-aware)

Package `dev.elsewhere.kingdom_villages`. Overworld, server side. Aldermark's 2.0 behaviour is unchanged: with only Aldermark every number and plan below is the 2.0 one.

## Who owns a village
- `VillageRecord.owner` (faction id string; default `elsewhere:aldermark`): the realm whose castle is nearest (RealmLocator.findRealm, within 2,600 blocks; fallen kingdoms skipped) at discovery, then whoever the war layer or a siege gives it to. `prevOwner`, `heldSince`, `lockUntil`, `wallSteps`, `hint` are saved with the ledger.
- Occupation and sieges use the same distance bands for every faction (<= 275 occupied, 400-1000 (+40/week) 35% siege per evaluation), measured to that faction's castle.
- War layer link (`WarBridge`): every village is registered (`WarApi.registerVillage`, owner = occupier or null) and `WarEvents.VILLAGE_CHANGED` calls `VillageManager.onVillageChanged`: ownership changes at once, a held village queues the cosmetic swap (banners, signature piece; never houses, walls or chests; 400 blocks per load), garrison and Serfs are replaced, `OWNER_CHANGED` fires. Revolt to a previous owner is the same call; to free the village becomes FREE (walls stay). `CourtEvents.FACTION_FALLEN`: every village of the fallen kingdom goes free.

## Public methods
- `VillagesApi.raidCountingHouse(server, faction)`: Peat-Cutters strike 3 days (rebuilding of that faction's villages stops). `isStriking`.
- `VillagesApi.ownerOf(level, pos)`, `villageAt`, `sieges(level)`, `layout(level, village)` (gates, 4 pickets, high ground, obelisks), `flip(...)`.
- Events: `VillageEvents.OWNER_CHANGED`, `STRIKE`, `LIBERATED`, `STATE_CHANGED`.
- `FactionVillages.of(faction)`: shape (ALDERMARK/HRAFNHOLT/AZMAR/MIREWICK), banner colour, Serf tier (from `Serfs.variant`), policy (fort_rate/fort_cap/border-only/integration days/unrest from WAR-AI via `WarApi.brainField`, style JSON as fallback).
- `VillageManager.fort(level, v)` (`min(cap, rate * days held)`, border-only for Azmar), `unrest(level, v)` (falls 1/integ per day after a conquest), `isBorder`.
- `Hints`: border hint routing to `LoreApi.hintSeen`.

## Rebuild styles (`RivalPlans`, only via `BlockWriter`: pieces + air above, never fluids, block entities, job sites or player blocks; no water, lava or fire placed)
- Hrafnholt: spruce longhall (deepslate footing, gable roof, Hearth-Fire, iron-bar windows), spruce-log palisade (3 logs + fence tip), deepslate-and-spruce watch tower, black banners, raven posts (border hint).
- Azmar: sandstone restyle, obelisks (gold pyramidion, lapis band; border hint), palm-shaded sandstone wall (persistent jungle leaves), pavilion, cyan banners. Border villages grow their wall with time held (`fort/0.1` steps, 1-9: courses, lantern posts, obelisks).
- Mirewick: mud-brick and dark oak, stilt hut watch post, raised boardwalks on stilts with rails and lanterns, low mud-brick wall with lantern posts, green banners.
- Serf tier: Thralls (x2, 12 HP, a third take stone axes while the garrison is attacked, 1.5x rebuild pace), Hired Porters (few), Peat-Cutters (strike).

## Tactics (FIELD-TACTICS 3.2)
Rival sieges of free villages break and burn nothing (Aldermark burns, 2.0). Azmar never targets or hurts the golems of a village it holds or takes (until they attack). Soldiers never hurt villagers (`Protect`) and never trample farmland (`VillageFarmlandMixin`). Garrisons defend their village against hostile mobs; policing per faction (Azmar picket at gate or obelisk by day, Mirewick holds the boardwalk and carries lanterns at night, Hrafnholt patrols).

## Test
`./gradlew runServer` (port 25823): Aldermark part (108 checks) then the rivals part, `selftest-villages.log`.
