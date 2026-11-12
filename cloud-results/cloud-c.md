# cloud-c results

Tested commit: `96b5b4f78167af0babdac5f25ceb707da3875b8c` (origin/cloud-src)

Machine: 4 cores, 15 GB RAM, Linux 6.18, JDK Temurin 25.0.5+8

History: nether_end_bosses fit3 FAILed on 7d3350a, 869354d, 8d6b2ce and f7708fa; it passed 222/222 on 4eb8021, 6c83a4b and this commit. fit3 looks flaky.
## overworld_bosses
- build: ok
- wall time (run; builds in parallel): 112 s
- /ERROR] lines: 0
- self-test lines:
```
[20:14:52] [Server thread/INFO] (elsewhere/overworld_bosses) [selftest] 181 passed, 0 failed: ALL PASSED
```
## nether_end_bosses
- build: ok
- wall time (run; builds in parallel): 248 s
- /ERROR] lines: 0
- self-test lines:
```
[20:17:14] [Server thread/INFO] (Elsewhere nether_end_bosses) [selftest] ALL PASSED  (222 passed, 0 failed)
```
## boss_ui
- build: ok
- wall time (run; builds in parallel): 59 s
- /ERROR] lines: 0
- self-test lines:
```
[20:14:05] [Server thread/INFO] (Minecraft) [STDOUT]: [boss_ui-selftest] TOTAL 65/65 PASS
```
## jei_compat
- build: ok
- wall time (run; builds in parallel): 23 s
- /ERROR] lines: 0
- NOTE: runServer needs -PjeiSelftest=true (not in CLOUD-RUN.md). Run against the default libs/elsewhere-0.3.0-beta.jar runtime, NOT the merged 3.0.0 jar (NOTES.md says the self-test is meant for the merged jar via -PelsewhereRuntime), so these FAILs are probably an artifact of the old runtime jar. Not re-run against the merged jar.
- self-test lines:
```
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL JEI data built: info pages, attunements, loot and shop pages: 461 info, 7 attunements, 41 loot, 0 shop
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL every registered Elsewhere item has a JEI recipe or page (390 items, 51 missing): [kelpwood_wood, tidesteel_shovel, climber_helmet, frostgranite_stairs, kelpwood_planks, kelpwood_fence, climber_boots, skyblossom_slab, skyiron_axe, kelpwood_slab, verdigris_plate_wall, polished_frostgranite, kelpwood_button, tidesteel_axe, frostgranite_brick_wall, climber_chestplate, frostgranite_wall, diving_suit_leggings, kelpwood_fence_gate, frostgranite_brick_slab, polished_frostgranite_slab, tidesteel_sword, skyiron_shovel, verdigris_plate_stairs, kelpwood_stairs, skyblossom_trapdoor, crystal_shard, kelpwood_trapdoor, skyblossom_button, verdigris_plate_slab, frostgranite_bricks, verdigris_plate, skyiron_hoe, tidesteel_pickaxe, tidesteel_hoe, diving_suit_chestplate, diving_suit_helmet, skyblossom_fence, polished_frostgranite_stairs, skyiron_pickaxe, frostgranite_brick_stairs, kelpwood_pressure_plate, skyblossom_fence_gate, skyblossom_stairs, kelpwood_door, frostgranite_slab, skyblossom_door, diving_suit_boots, riveted_verdigris_plate, climber_leggings, skyblossom_pressure_plate]
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL every info page names a registered item: [elsewhere:aldermark_armor_trim_smithing_template, elsewhere:aldermark_banner_pattern, elsewhere:aldermark_crown, elsewhere:amber, elsewhere:amber_ore, elsewhere:amberstill_boots, elsewhere:amberstill_chestplate, elsewhere:amberstill_helmet, elsewhere:amberstill_leggings, elsewhere:amberstill_upgrade_smithing_template, elsewhere:antler_bow, elsewhere:azmar_armor_trim_smithing_template, elsewhere:azmar_banner_pattern, elsewhere:azmar_commission_slip, elsewhere:azmar_composite_bow, elsewhere:azmar_copper_khopesh, elsewhere:azmar_dinar, elsewhere:azmar_falcon_helm, elsewhere:azmar_falconers_gauntlet, elsewhere:azmar_fire_pot, elsewhere:azmar_gilded_khopesh, elsewhere:azmar_horned_scarab_crown, elsewhere:azmar_ibis_crest, elsewhere:azmar_jackal_helm, elsewhere:azmar_letter_of_credit, elsewhere:azmar_nemes_of_the_uraeus, elsewhere:azmar_sandrunner_sandals, elsewhere:azmar_scarab_spear, elsewhere:azmar_seal_amir, elsewhere:azmar_seal_falconer, elsewhere:azmar_seal_sultana, elsewhere:azmar_seal_wazir, elsewhere:azmar_sunplate_cuirass, elsewhere:azmar_twin_khopesh, elsewhere:azmar_winged_scarab_standard, elsewhere:blossom_seal_charm, elsewhere:bodkin_bolt, elsewhere:boss_trophy, elsewhere:climber_upgrade_smithing_template, elsewhere:clockwork_repeater, elsewhere:court_ashgrove_longbow, elsewhere:court_chancellors_chaperon, elsewhere:court_crown_of_aldermark, elsewhere:court_duelists_sabre, elsewhere:court_gatewarden_plate, elsewhere:court_sovereigns_standard, elsewhere:court_warmarshal_spurs, elsewhere:duel_block, elsewhere:echo_alloy, elsewhere:fletchers_bundle, elsewhere:frostjaw_trout, elsewhere:gale_armor_trim_smithing_template, elsewhere:gale_pinion, elsewhere:gale_rapier, elsewhere:gale_ribbon, elsewhere:galvanic_coilmail_boots, elsewhere:galvanic_coilmail_chestplate, elsewhere:galvanic_coilmail_helmet, elsewhere:galvanic_coilmail_leggings, elsewhere:galvanic_upgrade_smithing_template, elsewhere:ghostfin, elsewhere:glassfin_darter, elsewhere:glimmer_mote, elsewhere:grove_fruit_platter, elsewhere:hacksilver, elsewhere:haze_guppy, elsewhere:heartwood_axe, elsewhere:heartwood_hoe, elsewhere:heartwood_pickaxe, elsewhere:heartwood_shovel, elsewhere:heartwood_sword, elsewhere:heliodor_mirror_core, elsewhere:hrafnholt_arm_ring, elsewhere:hrafnholt_armor_trim_smithing_template, elsewhere:hrafnholt_banner_pattern, elsewhere:hrafnholt_fur_collar_dye, elsewhere:hrafnholt_hearth_ward_mail, elsewhere:hrafnholt_hornbow, elsewhere:hrafnholt_iron_crown, elsewhere:hrafnholt_raven_banner, elsewhere:hrafnholt_raven_shield, elsewhere:hrafnholt_seal_chief, elsewhere:hrafnholt_seal_hallwarden, elsewhere:hrafnholt_seal_hersir, elsewhere:hrafnholt_seal_huntmaster, elsewhere:hrafnholt_seal_jarl, elsewhere:hrafnholt_seal_skald, elsewhere:hrafnholt_skalds_rune_axe, elsewhere:hrafnholt_ulfhednar_pelt, elsewhere:hrafnholt_wolfpelt_blanket, elsewhere:hrafnholt_wolfskin_boots, elsewhere:hush_lanternfish, elsewhere:hushward_bell, elsewhere:hushweave_upgrade_smithing_template, elsewhere:ironclad_chain, elsewhere:ironclad_flail, elsewhere:kingdom_archer_spawn_egg, elsewhere:kingdom_captain_spawn_egg, elsewhere:kingdom_crossbowman_spawn_egg, elsewhere:kingdom_herald_spawn_egg, elsewhere:kingdom_knight_spawn_egg, elsewhere:kingdom_lancer_spawn_egg, elsewhere:kingdom_man_at_arms_spawn_egg, elsewhere:kingdom_mounted_swordsman_spawn_egg, elsewhere:kingdom_pikeman_spawn_egg, elsewhere:kingdom_sergeant_spawn_egg, elsewhere:kingdom_squire_spawn_egg, elsewhere:kingdom_standard_bearer_spawn_egg, elsewhere:kingdom_surgeon_spawn_egg, elsewhere:lore_royal_proclamation, elsewhere:lore_royal_survey_map, elsewhere:lore_seal_castellan, elsewhere:lore_seal_chancellor, elsewhere:lore_seal_huntsman, elsewhere:lore_seal_marshal, elsewhere:lore_seal_master_at_arms, elsewhere:lore_survey_fragment, elsewhere:mirewick_almoners_cowl, elsewhere:mirewick_armor_trim_smithing_template, elsewhere:mirewick_banner_pattern, elsewhere:mirewick_debt_collectors_leggings, elsewhere:mirewick_dikewardens_brigandine, elsewhere:mirewick_fen_marshals_waders, elsewhere:mirewick_fowlers_marshbow, elsewhere:mirewick_lantern_coronet, elsewhere:mirewick_lantern_shield, elsewhere:mirewick_loan_note, elsewhere:mirewick_marsh_lantern_standard, elsewhere:mirewick_oilcloth_dye, elsewhere:mirewick_reedcutter_pike, elsewhere:mirewick_scrip, elsewhere:mirewick_seal_ames, elsewhere:mirewick_seal_marrow, elsewhere:mirewick_seal_sorrel, elsewhere:mirewick_seal_thwaite, elsewhere:mirewick_seal_tolliver, elsewhere:mirrorbolt_arbalest, elsewhere:mirrorglass_prism, elsewhere:music_disc_pale_light, elsewhere:music_disc_updraft, elsewhere:music_disc_zenith, elsewhere:oathkeepers_seal, elsewhere:paleweave_boots, elsewhere:paleweave_helmet, elsewhere:paleweave_leggings, elsewhere:paleweave_mantle, elsewhere:petalscale_koi, elsewhere:prospectors_pendulum, elsewhere:rift_anchor, elsewhere:seers_hourglass, elsewhere:sergeants_horn, elsewhere:skyiron_ingot, elsewhere:sporebloom_staff, elsewhere:stag_antler, elsewhere:steam_auger, elsewhere:steam_auger_bit, elsewhere:summit_armor_trim_smithing_template, elsewhere:sunburst_armor_trim_smithing_template, elsewhere:sunpit_lure, elsewhere:sunwright_ingot, elsewhere:sunwright_mirror_key, elsewhere:sunwright_upgrade_smithing_template, elsewhere:termite_hive_lantern, elsewhere:termite_queen, elsewhere:tidal_armor_trim_smithing_template, elsewhere:tidebrass_ingot, elsewhere:tidebrass_upgrade_smithing_template, elsewhere:verdigris_eel, elsewhere:village_ash, elsewhere:village_charred_planks, elsewhere:village_royal_coffer, elsewhere:village_royal_purse, elsewhere:void_tether_spool, elsewhere:whetstone]
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL kingdom shops: Aldermark, Hrafnholt, Azmar and Mirewick each have lines: []
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL kingdom shops are priced in each kingdom's own coin (Crown, Hacksilver, Dinar, Scrip): []
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL Mirewick Scrip-Desk: weekly auction lots shown
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] FAIL structure-chest loot pages (kingdom caches, couriers, dimension structures): [clan_hold, climbers_cairn, clockwork_rig, engineers_atoll, hanging_undercity, sesh_hut, silent_monastery]
[20:17:46] [Server thread/INFO] (Minecraft) [STDOUT]: [jei-selftest] TOTAL 8 passed, 7 failed
```
