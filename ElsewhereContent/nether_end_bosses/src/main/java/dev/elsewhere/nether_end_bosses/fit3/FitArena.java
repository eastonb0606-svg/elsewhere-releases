package dev.elsewhere.nether_end_bosses.fit3;

import net.minecraft.core.BlockPos;

/**
 * A v2 (3.0) arena: writes through a clipped {@link FitW}, so the same code builds one chunk of a structure piece, the whole
 * arena for the dev command or a fight rebuild, and the {@code /place feature} twin. Everything stays within 23 blocks of the
 * origin horizontally (the origin is a chunk middle), so all reads and writes stay in the 3x3 chunks around it.
 */
public interface FitArena {
	String id();

	void build(FitW w, BlockPos origin);

	BlockPos anchor(BlockPos origin);

	/** Radius of the main floor, whose columns must all reach the ground (or a pier) in the float check. */
	int floorRadius();

	/** Floor columns stand on a footing below this y offset (the floor's own lowest layer is just above). */
	default int footingTop() {
		return -3;
	}

	/** Whether the main floor must stand on real ground (End halo fragments are planned floating islands and are not checked). */
	default boolean grounded() {
		return true;
	}
}
