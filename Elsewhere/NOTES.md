# Elsewhere core — NOTES (v0.1.0 beta)

Core project for the Elsewhere mod: Wayfinders and rifts, Return Rifts, the accidental-trip framework, the shared boss framework, the set-bonus API, the Pale Hollow, Mother Creak and Selenne. API for content modules: `CORE-API.md`.

## Built and working (server self-test: see run/selftest-*.log, run-desk/)
- **Wayfinder** (`elsewhere:wayfinder`, recipe APA/ACA/.P.) and **Wayfinder: The Pale Hollow** (data-driven attunement `data/elsewhere/attunement/pale_hollow.json`, any layout, plus a recipe-book shaped copy R E R / L W L / M B M). Shared 60 s cooldown (`use_cooldown` group `elsewhere:wayfinder`).
- **Rifts** (`elsewhere:rift` entity): open in front of you, take you after 3 s, stay open 10 s for followers, close. Swirl disc renderer + coloured spark particles + layered sounds. Using the Wayfinder inside its dimension opens a rift home; sneak-use inside the Pale Hollow goes to the Overworld at X/Z x4 (BALANCE.md R).
- **Safe landing** (solid, dry, 2 air, no logs/leaves/magma; platform fallback), return point saved per player (survives death).
- **Return Rifts** (`elsewhere:return_rift` block + beam BER + ruin feature `elsewhere:return_rift`): one per 600x600 cell, deterministic positions; landings pulled within 250 blocks; walk in = home (sneak = respawn point).
- **Frayed Wayfinder**: given on every accidental arrival; lodestone-style needle to the nearest Return Rift; one forced rift home, then it crumbles.
- **Accidental trips**: `TripTrigger` (id, target, kind, baseChance) + `AccidentalTrips.fire`; familiarity damping per 512x512 region (LOCATION and, per BALANCE.md, ACTIVITY), home radius 0.03, hourly budgets, 2 h cooldown, 60 min grace, pity x2 (not at home), Overworld only, one roll per sleep, 3 s pull with vignette that damage cancels. Pale Hollow triggers: any sleep 2%, sleep near pale oak/creaking heart 12%. Simulations in the self-test.
- **Boss framework** (`dev.elsewhere.core.boss`): dormant/awake, party scaling, boss bar, arena snapshot + seal + 30 s reset, phase transitions, overpower guard, lethal-hit guard (also applied to any boss-sourced hit via `BossEvents`), per-player loot (items only the owner can pick up).
- **Set-bonus API** (`dev.elsewhere.core.gear.ArmorSets`): register a set; full-set attribute bonuses/effects/ability hook applied and removed cleanly; tooltip lines. No Pale Hollow sets registered yet (see gaps).
- **Pale Hollow** (`elsewhere:pale_hollow`): fixed dusk sky (dimension_type attributes), fog, coordinate scale 4, beds explode, anchors off; custom noise settings (marsh around sea level 63, noodle caves), 5 biomes (Hollow Thicket, Resin Bog, Weeping Deeps, Heartwood Grove, Silent Clearing) with their colours, particles and music; world-trunk feature (pale oak columns with heartwood veins, canopies, hanging moss); vanilla pale oak/creaking hearts; mob spawns.
- **Blocks**: resin_heart, heartwood_log, moonring_stone, ashen_moss, moon_bloom, arena_seal, return_rift.
- **Items**: heartwood_shard, pale_bark, pale_sap, unblinking_eye (craftable stand-in for the trader), resin_heart_locket, heartwood_greatbow, eyeblossom_circlet, moon_eye, heartwood_effigy (summon), moonring_seed (summon), 2 spawn eggs.
- **Mobs**: Creaking Husk (freezes when watched, roots moss), Eyeblossom Wisp (stare 1 s -> shriek + Darkness; nibbles XP).
- **Mother Creak** (Tier I): Elder Trunk arena (from a Heartwood Effigy or `/elsewhere arena mother_creak`), 4 wall hearts, stun windows, Stillness / Root Burst (root_spike entities after a 1 s resin-dust line) / Leaf Litter (Darkness, husk minions) / Heartwood Fury (armor -8, moves when watched). 360 HP, armor 8, toughness 2. Drops: Resin Heart Locket, Heartwood Greatbow, 10 Pale Bark, shards.
- **Selenne** (Tier III): Moonring (12 monoliths with water cauldrons, Moon Bloom at the centre; from a Moonring Seed or `/elsewhere arena selenne`). Open/closed eye cycle, gaze DoT, Moonbeam (1 s end-rod line, blocked by monoliths), wisps + cracking monoliths in phase 2, lids torn + reflections at the cauldrons + Full Moon in phase 3. 460 HP, armor 10, toughness 4. Drops: Eyeblossom Circlet, Moon-Eye, Pale Bark.

## BALANCE.md (FINAL) applied
Trip bases (sleep 2%, near pale oak 12%, module defaults in config), damping on ACTIVITY too, Overworld only, one roll per event, pity multiplier config; Return Rift sneak = respawn; Pale Hollow beam amber #E07B2A and sneak fast travel x4; Greatbow 768 durability, Slowness III 1 s per 4 s per target, bosses Slowness I 1 s per 10 s (G5); Locket roots non-boss attackers within 4 blocks 3 s; Unblinking Eye never freezes bosses; Circlet 3 armor / 2 toughness. Numbers live in `dev.elsewhere.palehollow.GearStats`.

## Beta 2 (ARSENAL.md) applied
- Moon-Eye: 3 Glimmer via `dev.elsewhere.stub.core.GlimmerStub.trySpend` (always true until the arsenal's pool is merged), 1.0 s use delay instead of the 20 s cooldown. Trinket behaviour (ARSENAL.md rev 3: caster-only trinket, +20% spell damage vs mobs you marked; not minions) belongs to the arsenal's trinket scanning; the core item only marks and spends Glimmer.
- Heartwood Greatbow: +1 `elsewhere:projectile_pierce` (mainhand) added through `DefaultItemComponentEvents.MODIFY` only if that attribute is registered.
- Drops by id lookup (skipped if the item is not registered): see 0.4.0 below (Elder Bark Crown / Rootcall Staff 50% first kill, 20% after; Moonsilk Thread Selenne 4-8, Eyeblossom Wisp 40%).

## Not done / changed from the docs (gaps)
- Heartwood tools, Paleweave armor set, Amber ore, Antler Bow, Hollow Stag, Bog Wight, Sap Tapper trader, Pale Hollow structures (shrines, chapel, lantern procession), natural Elder Trunk/Moonring generation: not in v0.1 (arenas come from summon items).
- BALANCE G3 slow Mending, G4 death-protection 30 s lockout, Boss Tokens, innate Power II on the Greatbow: not implemented. G7 first-kill/repeat rates exist for the arsenal/summoner uniques (BossDrops); the core's own loot-table uniques still drop on every kill.
- Return Rifts use a feature on a 600-block grid instead of a `random_spread` structure set; content modules add the placed feature to their biomes (CORE-API.md).
- Atlas UI for the base Wayfinder: chat list instead. Rift Anchor block: not built. "Wrong Turn" advancement: not built (recipe unlock on first visit works).
- Boss music, custom boss-bar frames, telegraph decals: vanilla boss bars and particle telegraphs instead. Sounds are re-pitched vanilla sound files (no new audio).
- Arena snapshots are in memory: a server restart mid-fight keeps the arena as it is.

## Disabled for beta
(none so far)

## Merge notes
- Entrypoints `dev.elsewhere.Elsewhere` / `dev.elsewhere.client.ElsewhereClient`; content modules' `init()` / `initClient()` go after `ModTabs.init()` / at the end of `onInitializeClient()`.
- Shared ids owned here: blocks `arena_seal`, `return_rift`; items `wayfinder`, `frayed_wayfinder`; entity `rift`; particles `rift_spark`, `resin_dust`, `moon_mote`; recipe serializer `attunement`; feature types `return_rift`, `world_trunk`; payload `pull`; attachments `return_point`, `trip_state`; command `/elsewhere`.
- Textures and entity geometry are generated: `python3 tools/gen_assets.py` (Pillow) then `python3 tools/gen_data.py`.

## 0.4.0-beta (core, delivered 21:57; self-test 141/141 dev and in the combined source)
- **`/elsewhere trips on|off`** (any player; saved per player in attachment `elsewhere:trips_enabled`, the id the 0.3.0 overlay used, copied on death) and **`/elsewhere trips`** (any player: your on/off status, then the current chances). All other `/elsewhere` subcommands are op-only (per subcommand; the root no longer requires op). Messages `message.elsewhere.trips.on/off/server_off`.
- **`overworldContent`**: `public boolean overworldContent = true` on `ElsewhereConfig.get()` (top level of `config/elsewhere.json`). False: `AccidentalTrips.fire`/`startPull` never start a trip (gate `AccidentalTrips.allowed(player)` = overworldContent && the player's own switch). The Overworld boss sites/site-only mobs are gated by overworld_bosses itself (`OwbConfig` reads this field); the overlay's StructureStateMixin is redundant with it.
- **Wayfinder home bug (QA #8) fixed:** cause: the client rebuilds its player on a dimension change and forgets item cooldowns, while the server keeps them, so after a Wayfinder trip the hotbar showed no cooldown but the server still had ~57 s left and ignored every use silently. `RiftTravel.resyncWayfinderCooldown` re-sends the remaining shared Wayfinder cooldown (`ClientboundCooldownPacket`) on every level change; uses then work as soon as it ends. Self-test reproduces it through the real `ServerPlayerGameMode.useItem` path.
- **Trip-warning vignette** follows `dev.elsewhere.hud.ElsewhereHud.screenEffects()` (off = no vignette) and `reduceMotion()` (static vignette, no closing-in), read by reflection in `dev.elsewhere.client.HudSettings` (defaults on / full motion when the arsenal is absent). The warning itself (overlay text, whispers, particles, 3 s cancellable pull) always plays.
- **Boss uniques with first-kill / repeat rates** (`dev.elsewhere.core.boss.BossDrops`, per-player kill counts in attachment `elsewhere:boss_kills`): Elder Bark Crown from Mother Creak and Rootcall Staff from Selenne, 50% on your first kill, 20% after (`GearStats.UNIQUE_FIRST_KILL/REPEAT`), per participant, owner-only pickup; items looked up by id, skipped when absent. Other bosses on `ElsewhereBoss` can register their own uniques the same way.
- **Echo-Forged Blade** (greenhaze_hush item): `elsewhere:kill_heal` +2 in the main hand, added on top of its sword modifiers by id (`ArsenalLinks`). The heal itself is the arsenal's (`AttributeHooks.killHeal`), which skips bosses and boss fights through `#elsewhere:bosses`: the core now ships `data/elsewhere/tags/entity_type/bosses.json` (Mother Creak, Selenne + every module boss as optional entries; before this the tag didn't exist, so kill heal and dodge also worked in boss fights). Exact check for merges: `ElsewhereBoss.inActiveArena(player)` (inside the arena of an awake framework boss).
- **Heartwood Greatbow** `projectile_pierce` +1 now added on top of existing modifiers (same helper).
- **Moon-Eye** caster trinket: using it (3 Glimmer, 1.0 s) also calls the arsenal's `Spells.mark(target, player)` by reflection, so spells (only spells) deal +20% to that mob while the Moon-Eye is an active trinket (`#elsewhere:trinket`/caster trinket tags are the arsenal's). Tooltip lines added.
- **Moonsilk Thread source**: Eyeblossom Wisp 40%, Selenne 4–8 per participant (by id; unchanged, now self-tested).
- Self-test: new `update040` section (trips commands as a non-op, overworldContent=false, Wayfinder real use path + cooldown resync, first/repeat drops, attribute links, boss tag, arena check, Moon-Eye mark).

## Coming soon (core)
- **Sap Tapper trader** (Pale Hollow) and its Rootcall Staff trade (30 Pale Bark + 2 Moonsilk Thread): the trader NPC isn't built yet; the staff comes from Selenne only.
- Heartwood tools, Paleweave armor, Pale Hollow structures (see gaps above).
