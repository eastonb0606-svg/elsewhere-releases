package dev.elsewhere.verdigris_ironpeak.s3.groups;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The verdigris_ironpeak module's ARMIES.md 6.2 rows (s-spawngroups, 3.0.0). Generated from the biome data and the pre-3.0 backup
 * ({@code _backup/pre3.0-0920}); see {@link SpawnGroups3} and {@link Herds3}.
 */
final class Groups3Data {
	private Groups3Data() {}

	/** Biomes this module's conversion looks at. */
	static final Set<String> BIOMES = Set.of("storm_pylons", "barnacle_atolls", "foundry_trench", "verdigris_shallows", "kelpwood_reaches", "the_thin_air", "frostbite_shelf", "iron_foothills", "granite_terraces", "cold_springs");

	/** Biomes of other modules (core only adds a spawn there): skipped by the self-test when that module is absent. */
	static final Set<String> FOREIGN_BIOMES = Set.of();
	static final Set<String> FOREIGN_IDS = Set.of();

	/** 3.0 group size per creature (ARMIES 6.2 "Proposed"). */
	static final Map<String, int[]> TARGET = Map.of("clockwork_drowned", new int[] {3, 6}, "tidewing_ray", new int[] {3, 5}, "brass_guardian", new int[] {2, 2}, "highland_yak", new int[] {4, 8}, "ironclan_raider", new int[] {4, 6}, "crag_roc", new int[] {1, 2});

	/** Entries that keep their own size on purpose (a biome-specific pair or crowd that already matches the design). */
	static final Set<String> KEEP = Set.of();

	/** Rows converted in code at biome load (2.1 range -> 3.0 range; weight scaled to keep the expected count). */
	static final List<SpawnGroups3.Size> SIZES = List.of(
		new SpawnGroups3.Size("brass_guardian", 1, 2, 2, 2));

	/** Exact 2.1 entries for the off switch: biome, type, 3.0 min/max/weight -> 2.1 min/max/weight (armies lane data, 9.11). */
	static final List<SpawnGroups3.Old> OLD = List.of(
		new SpawnGroups3.Old("storm_pylons", "clockwork_drowned", 3, 6, 27, 1, 3, 60),
		new SpawnGroups3.Old("storm_pylons", "tidewing_ray", 3, 5, 8, 1, 2, 20),
		new SpawnGroups3.Old("barnacle_atolls", "clockwork_drowned", 3, 6, 27, 1, 3, 60),
		new SpawnGroups3.Old("barnacle_atolls", "tidewing_ray", 3, 5, 8, 1, 2, 20),
		new SpawnGroups3.Old("foundry_trench", "clockwork_drowned", 3, 6, 27, 1, 3, 60),
		new SpawnGroups3.Old("foundry_trench", "tidewing_ray", 3, 5, 8, 1, 2, 20),
		new SpawnGroups3.Old("verdigris_shallows", "clockwork_drowned", 3, 6, 27, 1, 3, 60),
		new SpawnGroups3.Old("verdigris_shallows", "tidewing_ray", 3, 5, 8, 1, 2, 20),
		new SpawnGroups3.Old("kelpwood_reaches", "clockwork_drowned", 3, 6, 27, 1, 3, 60),
		new SpawnGroups3.Old("kelpwood_reaches", "tidewing_ray", 3, 5, 8, 1, 2, 20),
		new SpawnGroups3.Old("the_thin_air", "crag_roc", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("frostbite_shelf", "highland_yak", 4, 8, 4, 2, 4, 8),
		new SpawnGroups3.Old("frostbite_shelf", "ironclan_raider", 4, 6, 18, 3, 3, 30),
		new SpawnGroups3.Old("frostbite_shelf", "crag_roc", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("iron_foothills", "highland_yak", 4, 8, 4, 2, 4, 8),
		new SpawnGroups3.Old("iron_foothills", "ironclan_raider", 4, 6, 18, 3, 3, 30),
		new SpawnGroups3.Old("iron_foothills", "crag_roc", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("granite_terraces", "highland_yak", 4, 8, 4, 2, 4, 8),
		new SpawnGroups3.Old("granite_terraces", "ironclan_raider", 4, 6, 18, 3, 3, 30),
		new SpawnGroups3.Old("granite_terraces", "crag_roc", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("cold_springs", "highland_yak", 4, 8, 4, 2, 4, 8),
		new SpawnGroups3.Old("cold_springs", "ironclan_raider", 4, 6, 18, 3, 3, 30),
		new SpawnGroups3.Old("cold_springs", "crag_roc", 1, 2, 7, 1, 1, 10));

	/** Group behaviour per creature (6.2 "Behaviour"): max, shape, spacing, speed, steer, grow, alert, choir, horn, guard. */
	static final List<Herds3.Rule> RULES = List.of(
		new Herds3.Rule("clockwork_drowned", 6, Herds3.Shape.COLUMN2, 2.0, 1.0, true, true, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("tidewing_ray", 5, Herds3.Shape.RING, 3.0, 1.0, false, false, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("brass_guardian", 2, Herds3.Shape.PAIR, 3.0, 1.0, false, false, Herds3.Alert.HUNT, false, false, true),
		new Herds3.Rule("highland_yak", 8, Herds3.Shape.RING, 3.0, 1.0, true, true, Herds3.Alert.FLEE, false, false, false),
		new Herds3.Rule("ironclan_raider", 6, Herds3.Shape.COLUMN2, 2.0, 1.0, true, true, Herds3.Alert.HUNT, false, true, false),
		new Herds3.Rule("crag_roc", 2, Herds3.Shape.PAIR, 4.0, 1.0, false, false, Herds3.Alert.HUNT, false, false, false));

	/** Self-test: the creature whose live pack is tested, and where (overworld, far from spawn). */
	static final String TEST_TYPE = "ironclan_raider";
	static final int TEST_X = 29100;
}
