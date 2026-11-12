1. assets/elsewhere/lang/en_us.json: 7 item names + desc lines, 6 ruler entities + royal hound/warhorse/courtier, itemGroup, 9 message keys.
2. assets/elsewhere/items/court_*.json (7) and models/item/court_*.json (7): generated models, textures reused from vanilla or kingdom (aldermark_crown, aldermark_banner_pattern).
3. assets/elsewhere/equipment/court_{warmarshal,gatewarden,chaperon,crown}.json: humanoid layers reuse vanilla iron/netherite/leather/gold.
4. assets/elsewhere/sounds.json: court_music.{king,marshal,castellan,master_at_arms,huntsman,chancellor,realm} streaming the 7 OGGs (no category field; sounds.json has none).
5. data/elsewhere/worldgen/structure/castle_aldermark.json: custom type elsewhere:castle_aldermark, biomes #elsewhere:court_realm_biomes, beard_box.
6. data/elsewhere/worldgen/structure_set/castle_aldermark.json: single structure, custom placement elsewhere:castle_aldermark.
7. data/elsewhere/tags/worldgen/biome/court_realm_biomes.json: plains, sunflower_plains, meadow, forest, birch_forest, flower_forest, taiga.
8. data/elsewhere/tags/worldgen/structure/{aldermark_castle,on_royal_survey_maps}.json: elsewhere:castle_aldermark (replace false).
9. data/elsewhere/tags/item/{court_gilded_repair,court_cloth_repair,court_steel_repair,lore_seals}.json (lore seals required:false, lords only).
10. data/elsewhere/loot_table/chests/kingdom_{cache,armory,dungeon,market_stall}.json: copied verbatim from kingdom_lore.
