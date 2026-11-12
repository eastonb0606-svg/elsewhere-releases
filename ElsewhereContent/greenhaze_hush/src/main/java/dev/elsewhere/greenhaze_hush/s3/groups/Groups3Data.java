package dev.elsewhere.greenhaze_hush.s3.groups;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The greenhaze_hush module's ARMIES.md 6.2 rows (s-spawngroups, 3.0.0). Generated from the biome data and the pre-3.0 backup
 * ({@code _backup/pre3.0-0920}); see {@link SpawnGroups3} and {@link Herds3}.
 */
final class Groups3Data {
	private Groups3Data() {}

	/** Biomes this module's conversion looks at. */
	static final Set<String> BIOMES = Set.of("kush_canopy", "sativa_highlands", "hazebottom_swamp", "indica_hollows", "munchie_groves", "the_murmur", "amethyst_choir", "echo_shafts", "drowned_silence", "ossuary_galleries");

	/** Biomes of other modules (core only adds a spawn there): skipped by the self-test when that module is absent. */
	static final Set<String> FOREIGN_BIOMES = Set.of();
	static final Set<String> FOREIGN_IDS = Set.of();

	/** 3.0 group size per creature (ARMIES 6.2 "Proposed"). */
	static final Map<String, int[]> TARGET = Map.of("munchie_boar", new int[] {3, 5}, "toke_toad", new int[] {4, 7}, "lazy_gorger", new int[] {1, 2}, "ossuary_chorister", new int[] {3, 5});

	/** Entries that keep their own size on purpose (a biome-specific pair or crowd that already matches the design). */
	static final Set<String> KEEP = Set.of("munchie_groves|lazy_gorger|2|2");

	/** Rows converted in code at biome load (2.1 range -> 3.0 range; weight scaled to keep the expected count). */
	static final List<SpawnGroups3.Size> SIZES = List.of(
		new SpawnGroups3.Size("ossuary_chorister", 1, 1, 3, 5));

	/** Exact 2.1 entries for the off switch: biome, type, 3.0 min/max/weight -> 2.1 min/max/weight (armies lane data, 9.11). */
	static final List<SpawnGroups3.Old> OLD = List.of(
		new SpawnGroups3.Old("kush_canopy", "munchie_boar", 3, 5, 20, 2, 2, 40),
		new SpawnGroups3.Old("kush_canopy", "lazy_gorger", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("kush_canopy", "toke_toad", 4, 7, 5, 2, 2, 14),
		new SpawnGroups3.Old("sativa_highlands", "munchie_boar", 3, 5, 20, 2, 2, 40),
		new SpawnGroups3.Old("sativa_highlands", "lazy_gorger", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("sativa_highlands", "toke_toad", 4, 7, 5, 2, 2, 14),
		new SpawnGroups3.Old("hazebottom_swamp", "munchie_boar", 3, 5, 20, 2, 2, 40),
		new SpawnGroups3.Old("hazebottom_swamp", "lazy_gorger", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("hazebottom_swamp", "toke_toad", 4, 7, 5, 2, 2, 14),
		new SpawnGroups3.Old("hazebottom_swamp", "toke_toad", 4, 7, 16, 3, 3, 30),
		new SpawnGroups3.Old("indica_hollows", "munchie_boar", 3, 5, 20, 2, 2, 40),
		new SpawnGroups3.Old("indica_hollows", "lazy_gorger", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("indica_hollows", "toke_toad", 4, 7, 5, 2, 2, 14),
		new SpawnGroups3.Old("munchie_groves", "munchie_boar", 3, 5, 20, 2, 2, 40),
		new SpawnGroups3.Old("munchie_groves", "munchie_boar", 3, 5, 45, 3, 3, 60),
		new SpawnGroups3.Old("munchie_groves", "lazy_gorger", 1, 2, 7, 1, 1, 10),
		new SpawnGroups3.Old("munchie_groves", "toke_toad", 4, 7, 5, 2, 2, 14));

	/** Group behaviour per creature (6.2 "Behaviour"): max, shape, spacing, speed, steer, grow, alert, choir, horn, guard. */
	static final List<Herds3.Rule> RULES = List.of(
		new Herds3.Rule("munchie_boar", 5, Herds3.Shape.RING, 2.5, 1.0, true, true, Herds3.Alert.HUNT, false, false, false),
		new Herds3.Rule("toke_toad", 7, Herds3.Shape.RING, 2.0, 1.0, true, false, Herds3.Alert.FLEE, false, false, false),
		new Herds3.Rule("lazy_gorger", 2, Herds3.Shape.PAIR, 2.0, 0.8, true, false, Herds3.Alert.FLEE, false, false, false),
		new Herds3.Rule("ossuary_chorister", 5, Herds3.Shape.RING, 2.5, 1.0, true, true, Herds3.Alert.HUNT, true, false, false));

	/** Self-test: the creature whose live pack is tested, and where (overworld, far from spawn). */
	static final String TEST_TYPE = "munchie_boar";
	static final int TEST_X = 29000;
}
