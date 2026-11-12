# Elsewhere 0.3.0-beta (beta 2)

One jar: `dist/elsewhere-0.3.0-beta.jar` (mod id `elsewhere`). SHA-1 at the bottom of this file.
Beta 1 (`0.2.0-beta`) is **withdrawn**: its clients crash at start-up (an old longbow-zoom mixin). Its jar and notes are kept only in `archive/beta1/`.

## Install (server and every client)
1. Minecraft **26.3**, **Fabric Loader 0.20.1** or newer, **Java 25**.
2. Put the **full Fabric API** (`fabric-api-0.166.0+26.3.jar`; 0.165.2 also works) and `elsewhere-0.3.0-beta.jar` in `mods/` on the server **and** on every client. Clients without the mod cannot join (custom models, sky, HUD).
3. Optional, soft-linked, both sides: Test Meter (`testmeter-1.1.0.jar`), Green Leaf (`greenleaf-2.0.0.jar` or `2.0.1`), JEI for Fabric 26.3 (`jei-26.3-fabric-31.17.0.109.jar`; since 1.21.2 the server must have JEI too, or clients see no recipes).
4. A new world, or new chunks of an existing one, for the Overworld boss sites.

## What is in it
| Part | Content |
|---|---|
| Core | Wayfinder and attunement (7 attuned Wayfinders), rifts, Return Rifts (every dimension except The Hush), Frayed Wayfinder, the **one** accidental-trip framework for every dimension, shared boss framework, `/elsewhere` command |
| Pale Hollow (core) | dimension, 5 biomes; Mother Creak, Selenne |
| petalwind_glasswaste | Petalwind Isles, The Glasswaste (Heat meter + heat screen effects); Kazehana, Tempestra, Heliodor, Ankhara |
| verdigris_ironpeak | Verdigris Sea, Ironpeak (Altitude meter); Verdigrim, Galvanox, Thurgrim, Hrolf Grimhorn (+ Grimhorn); Roc-Feather Longbow with zoom |
| greenhaze_hush | The Greenhaze, The Hush (Haze and Resonance meters); Percolax, Grumblegut, Auris, Lithara |
| overworld_bosses | Gulpmother, Strawgrim, Neferkhet, Rimeback, Vorlagh; their Overworld sites (Croaking Shrine, Sun Chamber, Glacier Shelf, Wreck Ring) now generate |
| nether_end_bosses | Grunthar & Ashtusk, Solaxis, Mournmaw, Ossyra, Aphelion |
| **arsenal (new)** | **Glimmer** (the caster resource, replaces cooldowns on ability items), 9 attributes (`elsewhere:max_glimmer, glimmer_regen, spell_power, spell_cost, projectile_pierce, minion_slots, minion_damage, dodge_chance, kill_heal`), `elsewhere:spell` damage, **Calling**, trinkets (2 active), caster gear: Amethyst Wand, Sunlance Staff, Stormcoil Staff, Sculk Resonator, Glimmerstone Focus, Glimmer Tonic (+ Strong), Clarity Draught, Moonsilk set + Lined Moonsilk, Stormsage Mantle, Cantor's Diadem; the shared HUD/screen-effect settings |
| **JEI plugin (new, optional)** | loaded only when JEI is installed (`jei_mod_plugin` entrypoint, nothing else touches it): a **Wayfinder Attunement** recipe category (base Wayfinder + the dimension's five ingredients, in any layout), **info pages** for 186 Elsewhere items (boss summon items, boss drops, trader goods, how to get each material), and the technical Arena Seal / Return Rift blocks hidden from the item list |
| **summoner (new)** | minions (Rootling, Paper Kite, Brassback) from Rootcall Staff, Kitestring Staff, Brassback Key; Herding Crook, Herdcaller set + Lined Herdcaller (set bonus Pack Bond), Elder Bark Crown, Engineer's Harness, Bell of the Flock, Trail Biscuit, Gorger Fluff |

**Custom boss models and animations (all 24 bosses):** Mother Creak, Selenne, Kazehana, Tempestra, Heliodor, Ankhara, Verdigrim, Galvanox, Thurgrim, Hrolf (+ Grimhorn), Percolax, Grumblegut, Auris, Lithara, Gulpmother, Strawgrim, Neferkhet, Rimeback, Vorlagh (+ tentacles), Grunthar (+ Ashtusk), Solaxis, Mournmaw, Ossyra, Aphelion. They come from 11 boss-art client classes (`dev.elsewhere.bossart.*.BossArt*Client`), which run after every module's client setup, so they replace the earlier stand-in renderers.

### Glimmer and Calling (short)
- Glimmer: 20 to start (`elsewhere:max_glimmer`), regenerates 3/s one second after your last spend, costs a little hunger to regenerate, stops at hunger 6 or lower. Each live minion holds part of it in reserve (6/5/4/4/4 by Summoner Calling 0–4). Ability items of every dimension (Moon-Eye, Gale Heart, Heliograph Lens, Ninefold Rod, Greenhaze/Hush items and others) now spend Glimmer from this one pool.
- **Calling is a reading of the gear you wear**, never a choice: count of worn armor pieces tagged caster (`#elsewhere:caster_armor`) or summoner (`#elsewhere:summoner_armor`), 0–4 each. Spell and minion damage ×0.80/0.90/0.95/0.98/1.00, spell cost ×1.15/1.08/1.04/1.02/1.00. Vanilla armor counts as 0 and changes nothing else.
- Summoning: hold use on a staff for 1 s, 10 Glimmer (× `spell_cost`), 1 slot (base 1, up to 4 with gear).

## Vanilla-friendly switches
- **`/elsewhere trips on|off`** (any player, saved per player, default on): turns accidental trips off for you. `/elsewhere trips` shows your status and current chances. All other `/elsewhere` subcommands stay op-only.
- **Server config `overworldContent`** in `config/elsewhere.json` (top level, default `true`). `false`: the four Overworld boss sites aren't placed (so no Tidewrack Drowned from the Wreck Ring), Strawgrim's farm ritual is off, and **no accidental trips** happen for anyone. Wayfinders, summon items and everything inside the Elsewhere dimensions still work. The file is rewritten with every key on start, so the switch appears after the first boot.
- A player who never crafts a Wayfinder and switches trips off plays near-vanilla. The only HUD changes are Elsewhere's own meters, and they stay hidden until relevant.

## Display toggles (client, per player)
- File **`config/elsewhere-client.json`**:
  `{"meters": {"glimmer": "auto", "heat": "auto", "altitude": "auto", "haze": "auto", "resonance": "auto"}, "hudHidden": false, "screenEffects": true, "reduceMotion": false, "trinketMarks": true, "minionOutline": true, "rankTitles": true, "longbowZoom": true}`.
  Meter modes: `auto` (shown only when relevant), `always`, `hidden`. The game reads, merges and rewrites the file; unknown keys are kept.
- **Client command `/elsewhere_hud`**: no argument prints the settings; `/elsewhere_hud <glimmer|heat|altitude|haze|resonance|all> <auto|always|hidden>`; `/elsewhere_hud effects <on|off|reduced>`; `/elsewhere_hud reduce_motion <on|off>`; `/elsewhere_hud <trinketMarks|minionOutline|rankTitles|longbowZoom> <on|off>`.
- **Keybinds** (Controls → category "Elsewhere", unbound by default): **"Toggle Elsewhere HUD"** (hides or shows every Elsewhere meter; `hudHidden`) and **"Cycle Glimmer bar"**.
- Screen effects (heat vignette and shimmer, haze and fog pulses, the trip "pull" vignette) follow `screenEffects`; `reduceMotion` keeps static tints only. Boss bars always stay (vanilla style).
- A hidden meter still warns you in the action bar when it matters (e.g. "Not enough Glimmer", Heat 75/100).

## How to get there (unchanged)
Craft a Wayfinder, attune it with the dimension's five ingredients (any layout; The Hush also needs the Ender Dragon killed), use it: a rift opens and takes you after 3 s. Way home: use the attuned Wayfinder again inside its dimension, walk into a Return Rift (about one per 600×600 blocks; none in The Hush), or die.

## Accidental trips (now one framework)
Every dimension's triggers (Pale Hollow, Petalwind, Glasswaste, Verdigris Sea, Ironpeak, Greenhaze, The Hush) run through the core's framework. The rules are familiarity damping per 512×512 region, a 128-block home radius, hourly budgets for activity triggers, **one global 2-hour spacing after any trip**, no trips in the first 60 minutes, the pity timer, and a 3 s cancellable pull. Triggers only roll in the Overworld. The modules' own copies of these rules are bypassed at merge. Op commands: `/elsewhere trips`, `/elsewhere trip <trigger>`.

## Left out / coming soon
- Core extras that missed the 20:35 cut: Moon-Eye as a caster trinket, Greatbow pierce, Echo-Forged kill-heal, the Rootcall Staff / Elder Bark Crown / Moonsilk drops from Pale Hollow (these items come from recipes, other modules' trades and drops, or creative).
- Several structures, traders and markets, music discs, custom boss music and boss-bar frames (see each module's NOTES.md).
- Crafting recipes are still missing for some Greenhaze/Hush materials and the endgame smithing lines. Those items come from loot, trades or creative.

## Known issues
- Shared tier gear (Challenger's Cleaver, Aegis of the Ladder, Phasewalker Greaves, Stormcrest Helm) is registered once, by the first module that defines it. Its abilities are mostly simple.
- Arena snapshots are kept in memory. A server restart mid-fight leaves the arena as it was.
- Ironpeak Return Rifts can generate anywhere, not only in the foothills.
- Fixed at merge (were broken on the RC): boss bars that stayed on screen after a boss was removed without dying (all boss frameworks now clear their bars on any removal), and the invisible Crag Roc / Tidewing Ray (model drawn underground).
- Cosmetic: in Always mode the Resonance bar can overlap the "Haze 0% Sober" text. Module meters (Heat, Altitude) follow `/elsewhere_hud` and the HUD key within about 1 s, because they re-read the settings file.
- The attuned Wayfinder has its normal cooldown right after you arrive, so using it again at once does nothing until the cooldown ends.

## Tests run on this jar
Logs are in this folder. Source: `tools/merge.py --sync` builds `src/` from the core and seven modules (arsenal, summoner, petalwind_glasswaste, verdigris_ironpeak, greenhaze_hush, overworld_bosses, nether_end_bosses; live folders as of the 20:35 delivery cut) plus `overlay/` (integration glue, the Glimmer stubs pointed at arsenal, the global-trip bridge, the vanilla-friendly switches, tests) and the patches in `tools/modules.json`.

1. **Combined dev-server smoke test** (`$S/desk.sh ElsewhereBeta runServer`, `BetaSmokeTest` + `BetaSmokeBeta2`), log `final-smoke-latest.log`: **154 passed, 0 failed, 31 info, 0 ERROR/exception lines**. It covers:
   - Registries: 138 blocks, 403 items, 95 entity types, 51 sounds. Every block placed and broken, every non-block item used by a fake player.
   - All 7 dimensions: generated, entered and left, Return Rifts near each landing (none in The Hush). All 24 bosses spawned, stepped through their phases and killed.
   - **Beta 2 checks:**
     - Glimmer: spend, refuse when short, regen after the delay, no regen inside the delay. The core, petalwind, greenhaze and nether Glimmer stubs spend from the real pool.
     - Rootcall Staff, Kitestring Staff and Brassback Key each summon a minion paid from the real pool (10 Glimmer) and reserve Glimmer (6 each at Calling 0).
     - Caster and summoner Calling read 4/4 from tagged worn armor and 0 from none. The Calling multipliers rise monotonically, and a 10-raw spell costs 11.5 Glimmer bare against 10.0 in full caster gear.
     - Module trip triggers are registered in the core framework, and a trip 10 minutes ago puts every trigger of every module at 0% (global spacing).
     - `/elsewhere trips off` run by a non-op player blocks a forced roll, and `/elsewhere trips on` restores it.
     - With `overworldContent=true` all 4 Overworld boss sites are placed.
     - Discarding a boss without killing it clears its boss bars (Mother Creak, Kazehana, Verdigrim, Percolax, Gulpmother, Vorlagh; Grunthar and Solaxis create their bars when the fight starts).
2. **`overworldContent=false` boot** (`config/elsewhere.json` `{"overworldContent": false}`), log `overworldoff-latest.log`: **66 passed, 0 failed, 0 ERROR**. No Overworld boss-site structure set is placed (0 of 4), and accidental trips never fire, even on a forced roll.
3. **Production boot**: clean Fabric 26.3 server (loader 0.20.1) with **fabric-api 0.166.0+26.3**, this jar, testmeter-1.1.0 and greenleaf-2.0.0 (45 mods): **68 passed, 0 failed, 0 ERROR** (`prod-final-latest.log`; all 7 dimensions generated, entered and left, plus the beta 2 checks).
4. **Production boot + JEI 31.17.0.109** (same set plus `jei-26.3-fabric-31.17.0.109.jar`, 47 mods): **68 passed, 0 failed, 0 ERROR** (`prod-finaljei-latest.log`).
5. **Module self-tests inside the combined jar** (RC2 source; `tools/desk_modtests.sh`, `modtests-latest.log`):
   - All pass: core 109/109, verdigris_ironpeak 77/77, overworld_bosses 56/56, greenhaze_hush 67/67, petalwind_glasswaste 40/40.
   - nether_end_bosses 130/131: the "three Ninefold Rod casts from a 20 pool" check assumes the old stub cost of 6. The real pool charges 6.9 at Calling 0 (ARSENAL rev 4), so only 2 casts fit.
   - summoner 19/25, and its test crashed afterwards: the test reads the dev stub's `SPENT` counters, which stay empty once spends go to the real pool. Its later steps then had no minion. The real summon path passes in check 1.
   - arsenal: 41/47 on the beta's normal world, because its test arena sits at y −60, inside stone. On a flat world it scores **45/47** (`arsenal-combined-flat.log`). The 2 remaining checks use the dev minion stand-in, which the real MinionApi replaces (covered in check 1).
   - None of these failures are gameplay bugs.
6. **Client**: run by the QA agent, `ElsewhereQA/`. On RC 31f43db: the client starts clean, there are 0 missing models, and all 7 dimensions, the meters and every boss's custom model render. The two issues QA found (lingering boss bars, invisible Crag Roc and Tidewing Ray) are fixed in this jar. QA's pass on this final jar is in progress; see STATUS.md.


SHA-1 `elsewhere-0.3.0-beta.jar`: b7663821d70d65224b3a4da6fe00af07f8302980

Fallbacks kept: RC1 `archive/rc1/elsewhere-0.3.0-beta.jar` (31f43db, no JEI plugin, QA client-checked) and `archive/rc3/` (05fd0a4). Beta 1 (withdrawn): `archive/beta1/`.

## Known issues found in the final QA pass (none crash or block play)
- Using an attuned Wayfinder a second time inside the Pale Hollow may not open a rift home. Use a Return Rift (follow the beam) or the Frayed Wayfinder instead.
- With the Haze and Resonance meters both set to "Always", the Resonance bar draws over the Haze text.
- The Heat meter takes about a second to follow a HUD toggle.
- Kazehana leaves some leftover effect particles for a few seconds after she dies.
- In JEI, the note on The Hush's attunement page is cut off (it needs the Ender Dragon killed first).

## Final test results (sha1 b7663821d70d65224b3a4da6fe00af07f8302980)
- Combined server test 154/154, near-vanilla (`overworldContent=false`) boot 66/66, production boot with Test Meter + Green Leaf 68/68, and with JEI 68/68 — all with 0 errors.
- Server pack exactly as shipped (Fabric API 0.166.0, Test Meter 1.1.0, Green Leaf 2.0.1, Elsewhere 0.3.0 beta, JEI 31.17.0.109): 68/68, 47 mods, 0 errors.
- Client: starts clean with and without JEI; all 404 items have models; every boss and all 47 mobs render with their models; JEI shows the Wayfinder Attunement category and 201 info pages.
