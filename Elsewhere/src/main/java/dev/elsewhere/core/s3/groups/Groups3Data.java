package dev.elsewhere.core.s3.groups;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The core module's ARMIES.md 6.2 rows (s-spawngroups, 3.0.0). Generated from the biome data and the pre-3.0 backup
 * ({@code _backup/pre3.0-0920}); see {@link SpawnGroups3} and {@link Herds3}.
 */
final class Groups3Data {
	private Groups3Data() {}

	/** Biomes this module's conversion looks at. */
	static final Set<String> BIOMES = Set.of("heartwood_grove", "hollow_thicket", "silent_clearing", "weeping_deeps", "resin_bog", "glass_flats", "ember_dunes", "spirefields", "bonebed_hollows", "kush_canopy", "indica_hollows", "sativa_highlands", "munchie_groves", "hazebottom_swamp");

	/** Biomes of other modules (core only adds a spawn there): skipped by the self-test when that module is absent. */
	static final Set<String> FOREIGN_BIOMES = Set.of("glass_flats", "ember_dunes", "spirefields", "bonebed_hollows", "kush_canopy", "indica_hollows", "sativa_highlands", "munchie_groves", "hazebottom_swamp");
	static final Set<String> FOREIGN_IDS = Set.of("docile_scarab", "haze_ferret");

	/** 3.0 group size per creature (ARMIES 6.2 "Proposed"). */
	static final Map<String, int[]> TARGET = Map.of("creaking_husk", new int[] {2, 3}, "hollow_stag", new int[] {3, 6}, "bog_wight", new int[] {1, 3}, "docile_scarab", new int[] {2, 4}, "haze_ferret", new int[] {2, 3});

	/** Entries that keep their own size on purpose (a biome-specific pair or crowd that already matches the design). */
	static final Set<String> KEEP = Set.of();

	/** Rows converted in code at biome load (2.1 range -> 3.0 range; weight scaled to keep the expected count). */
	static final List<SpawnGroups3.Size> SIZES = List.of(
		new SpawnGroups3.Size("hollow_stag", 1, 3, 3, 6),
		new SpawnGroups3.Size("bog_wight", 1, 2, 1, 3),
		new SpawnGroups3.Size("docile_scarab", 1, 2, 2, 4),
		new SpawnGroups3.Size("haze_ferret", 1, 2, 2, 3));

	/** Exact 2.1 entries for the off switch: biome, type, 3.0 min/max/weight -> 2.1 min/max/weight (armies lane data, 9.11). */
	static final List<SpawnGroups3.Old> OLD = List.of(
		new SpawnGroups3.Old("heartwood_grove", "creaking_husk", 2, 3, 36, 1, 2, 60),
		new SpawnGroups3.Old("hollow_thicket", "creaking_husk", 2, 3, 36, 1, 2, 60),
		new SpawnGroups3.Old("silent_clearing", "creaking_husk", 2, 3, 36, 1, 2, 60),
		new SpawnGroups3.Old("weeping_deeps", "creaking_husk", 2, 3, 36, 1, 2, 60),
		new SpawnGroups3.Old("resin_bog", "creaking_husk", 2, 3, 36, 1, 2, 60));

	/** Group behaviour per creature (6.2 "Behaviour"): max, shape, spacing, speed, steer, grow, alert, choir, horn, guard. */
	static final List<Herds3.Rule> RULES = List.of(
		new Herds3.Rule("creaking_husk", 3, Herds3.Shape.RING, 2.0, 1.0, true, false, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("hollow_stag", 6, Herds3.Shape.RING, 3.0, 1.0, true, true, Herds3.Alert.FLEE, false, false, false),
		new Herds3.Rule("bog_wight", 3, Herds3.Shape.RING, 2.0, 1.0, true, false, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("docile_scarab", 4, Herds3.Shape.RING, 1.5, 1.0, true, false, Herds3.Alert.FLEE, false, false, false),
		new Herds3.Rule("haze_ferret", 3, Herds3.Shape.RING, 1.5, 1.0, true, false, Herds3.Alert.FLEE, false, false, false));

	/** Self-test: the creature whose live pack is tested, and where (overworld, far from spawn). */
	static final String TEST_TYPE = "bog_wight";
	static final int TEST_X = 29300;
}
