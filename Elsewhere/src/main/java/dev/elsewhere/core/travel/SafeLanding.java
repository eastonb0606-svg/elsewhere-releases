package dev.elsewhere.core.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Finds solid, dry ground with two blocks of air above it; builds a small platform if there is none. */
public final class SafeLanding {
	private SafeLanding() {
	}

	/** Spiral search around (x, z) up to {@code radius} blocks; falls back to a platform at (x, z). */
	public static Vec3 find(final ServerLevel level, final int x, final int z, final int radius, final int maxY, final Block platform) {
		for (int r = 0; r <= radius; r += 3) {
			for (int i = -r; i <= r; i += 3) {
				for (int[] d : new int[][] {{i, -r}, {i, r}, {-r, i}, {r, i}}) {
					Vec3 found = column(level, x + d[0], z + d[1], maxY);
					if (found != null) {
						return found;
					}
					if (r == 0) {
						break;
					}
				}
				if (r == 0) {
					break;
				}
			}
		}
		return platform(level, x, z, platform);
	}

	/** Checks an exact spot (e.g. a saved return point) and searches nearby if it is no longer safe. */
	public static Vec3 near(final ServerLevel level, final Vec3 pos, final int radius, final Block platform) {
		BlockPos feet = BlockPos.containing(pos);
		level.getChunk(feet.getX() >> 4, feet.getZ() >> 4);
		if (isSafe(level, feet)) {
			return pos;
		}
		return find(level, feet.getX(), feet.getZ(), radius, level.getMaxY(), platform);
	}

	public static @Nullable Vec3 column(final ServerLevel level, final int x, final int z, final int maxY) {
		level.getChunk(x >> 4, z >> 4);
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= level.getMinY() + 1 || y > maxY) {
			return null;
		}
		BlockPos feet = new BlockPos(x, y, z);
		return isSafe(level, feet) ? Vec3.atBottomCenterOf(feet) : null;
	}

	public static boolean isSafe(final ServerLevel level, final BlockPos feet) {
		BlockState below = level.getBlockState(feet.below());
		if (!below.isFaceSturdy(level, feet.below(), Direction.UP) || !below.getFluidState().isEmpty()) {
			return false;
		}
		if (below.is(BlockTags.LEAVES) || below.is(BlockTags.LOGS) || below.is(Blocks.MAGMA_BLOCK) || below.is(BlockTags.CAMPFIRES)
			|| below.is(Blocks.CACTUS) || below.is(Blocks.POWDER_SNOW) || below.is(Blocks.BEDROCK)) {
			return false;
		}
		for (int dy = 0; dy < 2; dy++) {
			BlockPos p = feet.above(dy);
			BlockState s = level.getBlockState(p);
			if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty() || s.is(BlockTags.FIRE)) {
				return false;
			}
		}
		return true;
	}

	public static Vec3 platform(final ServerLevel level, final int x, final int z, final Block platform) {
		level.getChunk(x >> 4, z >> 4);
		int y = Math.max(level.getSeaLevel() + 1, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
		y = Math.min(y, level.getMaxY() - 4);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				level.setBlockAndUpdate(new BlockPos(x + dx, y - 1, z + dz), platform.defaultBlockState());
				for (int dy = 0; dy < 3; dy++) {
					level.setBlockAndUpdate(new BlockPos(x + dx, y + dy, z + dz), Blocks.AIR.defaultBlockState());
				}
			}
		}
		return Vec3.atBottomCenterOf(new BlockPos(x, y, z));
	}
}
