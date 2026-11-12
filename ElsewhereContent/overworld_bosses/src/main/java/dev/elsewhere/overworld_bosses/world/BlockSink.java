package dev.elsewhere.overworld_bosses.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Where an arena blueprint writes its blocks: a world-gen structure piece (clipped to the chunk being generated) or
 * the live level (dev command, self-test). Coordinates are absolute.
 */
public interface BlockSink {
	void set(BlockPos pos, BlockState state);

	BlockState get(BlockPos pos);

	default void set(int x, int y, int z, BlockState state) {
		set(new BlockPos(x, y, z), state);
	}

	default BlockState get(int x, int y, int z) {
		return get(new BlockPos(x, y, z));
	}

	/** Whether writes at this column happen in this pass (world-gen pieces only write inside the current chunk). */
	default boolean writes(int x, int z) {
		return true;
	}
}
