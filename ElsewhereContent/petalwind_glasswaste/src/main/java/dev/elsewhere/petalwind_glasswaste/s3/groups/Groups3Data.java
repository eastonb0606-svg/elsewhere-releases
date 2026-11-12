package dev.elsewhere.petalwind_glasswaste.s3.groups;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The petalwind_glasswaste module's ARMIES.md 6.2 rows (s-spawngroups, 3.0.0). Generated from the biome data and the pre-3.0 backup
 * ({@code _backup/pre3.0-0920}); see {@link SpawnGroups3} and {@link Herds3}.
 */
final class Groups3Data {
	private Groups3Data() {}

	/** Biomes this module's conversion looks at. */
	static final Set<String> BIOMES = Set.of("cloud_nine", "blossom_crowns", "hanging_gardens", "stormrim", "gale_steppes", "cinder_basin", "spirefields", "ember_dunes", "bonebed_hollows", "glass_flats");

	/** Biomes of other modules (core only adds a spawn there): skipped by the self-test when that module is absent. */
	static final Set<String> FOREIGN_BIOMES = Set.of();
	static final Set<String> FOREIGN_IDS = Set.of();

	/** 3.0 group size per creature (ARMIES 6.2 "Proposed"). */
	static final Map<String, int[]> TARGET = Map.of("gale_kite", new int[] {4, 8}, "cumulus", new int[] {3, 5}, "skywhale", new int[] {1, 3}, "glass_scarab", new int[] {4, 8}, "sunwright_revenant", new int[] {4, 6}, "sun_mote", new int[] {2, 3});

	/** Entries that keep their own size on purpose (a biome-specific pair or crowd that already matches the design). */
	static final Set<String> KEEP = Set.of();

	/** Rows converted in code at biome load (2.1 range -> 3.0 range; weight scaled to keep the expected count). */
	static final List<SpawnGroups3.Size> SIZES = List.of(
		new SpawnGroups3.Size("skywhale", 1, 1, 1, 3));

	/** Exact 2.1 entries for the off switch: biome, type, 3.0 min/max/weight -> 2.1 min/max/weight (armies lane data, 9.11). */
	static final List<SpawnGroups3.Old> OLD = List.of(
		new SpawnGroups3.Old("cloud_nine", "gale_kite", 4, 8, 10, 1, 2, 40),
		new SpawnGroups3.Old("cloud_nine", "cumulus", 3, 5, 10, 1, 3, 20),
		new SpawnGroups3.Old("blossom_crowns", "gale_kite", 4, 8, 10, 1, 2, 40),
		new SpawnGroups3.Old("blossom_crowns", "cumulus", 3, 5, 10, 1, 3, 20),
		new SpawnGroups3.Old("hanging_gardens", "gale_kite", 4, 8, 10, 1, 2, 40),
		new SpawnGroups3.Old("hanging_gardens", "cumulus", 3, 5, 10, 1, 3, 20),
		new SpawnGroups3.Old("stormrim", "gale_kite", 4, 8, 10, 1, 2, 40),
		new SpawnGroups3.Old("stormrim", "cumulus", 3, 5, 10, 1, 3, 20),
		new SpawnGroups3.Old("gale_steppes", "gale_kite", 4, 8, 10, 1, 2, 40),
		new SpawnGroups3.Old("gale_steppes", "cumulus", 3, 5, 10, 1, 3, 20),
		new SpawnGroups3.Old("cinder_basin", "glass_scarab", 4, 8, 30, 2, 4, 60),
		new SpawnGroups3.Old("cinder_basin", "sunwright_revenant", 4, 6, 8, 1, 2, 25),
		new SpawnGroups3.Old("cinder_basin", "sun_mote", 2, 3, 8, 1, 1, 20),
		new SpawnGroups3.Old("spirefields", "glass_scarab", 4, 8, 30, 2, 4, 60),
		new SpawnGroups3.Old("spirefields", "sunwright_revenant", 4, 6, 8, 1, 2, 25),
		new SpawnGroups3.Old("spirefields", "sun_mote", 2, 3, 8, 1, 1, 20),
		new SpawnGroups3.Old("ember_dunes", "glass_scarab", 4, 8, 30, 2, 4, 60),
		new SpawnGroups3.Old("ember_dunes", "sunwright_revenant", 4, 6, 8, 1, 2, 25),
		new SpawnGroups3.Old("ember_dunes", "sun_mote", 2, 3, 8, 1, 1, 20),
		new SpawnGroups3.Old("bonebed_hollows", "glass_scarab", 4, 8, 30, 2, 4, 60),
		new SpawnGroups3.Old("bonebed_hollows", "sunwright_revenant", 4, 6, 8, 1, 2, 25),
		new SpawnGroups3.Old("bonebed_hollows", "sun_mote", 2, 3, 8, 1, 1, 20),
		new SpawnGroups3.Old("glass_flats", "glass_scarab", 4, 8, 30, 2, 4, 60),
		new SpawnGroups3.Old("glass_flats", "sunwright_revenant", 4, 6, 8, 1, 2, 25),
		new SpawnGroups3.Old("glass_flats", "sun_mote", 2, 3, 8, 1, 1, 20));

	/** Group behaviour per creature (6.2 "Behaviour"): max, shape, spacing, speed, steer, grow, alert, choir, horn, guard. */
	static final List<Herds3.Rule> RULES = List.of(
		new Herds3.Rule("gale_kite", 8, Herds3.Shape.RING, 3.0, 1.0, false, false, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("cumulus", 5, Herds3.Shape.RING, 3.0, 0.6, false, false, Herds3.Alert.NONE, false, false, false),
		new Herds3.Rule("skywhale", 3, Herds3.Shape.RING, 8.0, 0.5, false, false, Herds3.Alert.NONE, false, false, false),
		new Herds3.Rule("glass_scarab", 8, Herds3.Shape.RING, 1.5, 1.1, true, false, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("sunwright_revenant", 6, Herds3.Shape.FILE, 2.0, 1.0, true, false, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("sun_mote", 3, Herds3.Shape.RING, 2.0, 1.0, false, false, Herds3.Alert.HUNT, false, false, false));

	/** Self-test: the creature whose live pack is tested, and where (overworld, far from spawn). */
	static final String TEST_TYPE = "glass_scarab";
	static final int TEST_X = 29200;
}
