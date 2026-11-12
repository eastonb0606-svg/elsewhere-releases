package dev.elsewhere.nether_end_bosses;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import org.jspecify.annotations.Nullable;

/**
 * Procedural arena generator. The same code builds the arena in world generation (as a feature) and for
 * {@code /elsewhere_dev boss <id>}, and rebuilds it if a fight is reset after a restart (no snapshot).
 * The origin is the floor centre: the first air block above the arena floor at the summon anchor.
 */
public interface ArenaBuilder {
	Map<String, ArenaBuilder> BY_BOSS = new LinkedHashMap<>();

	void build(WorldGenLevel level, BlockPos origin, RandomSource random);

	/** World generation: picks the origin near {@code center} (a chunk centre), or null to skip this chunk. */
	@Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random);

	/** Where the summon anchor sits relative to the origin (gold throne, pyre core, pedestal, altar). */
	BlockPos anchor(BlockPos origin);
}
