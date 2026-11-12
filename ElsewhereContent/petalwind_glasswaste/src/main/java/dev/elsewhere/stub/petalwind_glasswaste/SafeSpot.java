package dev.elsewhere.stub.petalwind_glasswaste;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/** STUB of the core's safe-arrival search: solid ground, no lava or void below, two blocks of air. */
public final class SafeSpot {
	private SafeSpot() {}

	public static @Nullable BlockPos find(ServerLevel level, BlockPos near, int maxRadius) {
		for (int r = 0; r <= maxRadius; r += 8) {
			int steps = Math.max(1, r / 2);
			for (int i = 0; i < steps * 4; i++) {
				double a = Math.PI * 2 * i / (steps * 4);
				int x = near.getX() + (int) Math.round(Math.cos(a) * r);
				int z = near.getZ() + (int) Math.round(Math.sin(a) * r);
				BlockPos p = column(level, x, z);
				if (p != null) return p;
				if (r == 0) break;
			}
		}
		return null;
	}

	public static @Nullable BlockPos column(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
		if (top <= level.getMinY() + 1) return null;
		BlockPos feet = new BlockPos(x, top, z);
		BlockState ground = level.getBlockState(feet.below());
		if (!ground.isSolidRender() && !ground.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)) return null;
		if (!ground.getFluidState().isEmpty() || ground.getFluidState().is(Fluids.LAVA)) return null;
		if (ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || ground.is(net.minecraft.world.level.block.Blocks.LAVA)) return null;
		if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) return null;
		if (!level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return null;
		return feet;
	}
}
