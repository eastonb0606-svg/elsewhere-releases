package dev.elsewhere.greenhaze_hush.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;

/** A structure built in code. Origin is the centre of the footprint at floor level. */
public interface Blueprint {
	/** Half-size of the footprint (blocks), how far below and above the origin it reaches. */
	int radius();

	int below();

	int above();

	void build(Sink sink, BlockPos origin, RandomSource random);
}
