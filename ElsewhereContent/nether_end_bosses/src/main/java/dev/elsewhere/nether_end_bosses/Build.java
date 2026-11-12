package dev.elsewhere.nether_end_bosses;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Tiny block-placing helpers for the arena builders (flags avoid neighbour updates so lava and water stay put). */
public final class Build {
	public static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private Build() {}

	public static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, FLAGS);
	}

	public static void set(WorldGenLevel level, int x, int y, int z, BlockState state) {
		level.setBlock(new BlockPos(x, y, z), state, FLAGS);
	}

	public static void box(WorldGenLevel level, BlockPos a, BlockPos b, BlockState state) {
		int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
		int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
		int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) level.setBlock(p.set(x, y, z), state, FLAGS);
	}

	/** Filled horizontal disc of radius r at height y. */
	public static void disc(WorldGenLevel level, BlockPos c, int y, double r, BlockState state) {
		int ir = (int) Math.ceil(r);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			if (dx * dx + dz * dz <= r * r) level.setBlock(p.set(c.getX() + dx, y, c.getZ() + dz), state, FLAGS);
		}
	}

	/** Ring between r0 and r1 at height y. */
	public static void ring(WorldGenLevel level, BlockPos c, int y, double r0, double r1, BlockState state) {
		int ir = (int) Math.ceil(r1);
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			double d = dx * dx + dz * dz;
			if (d >= r0 * r0 && d <= r1 * r1) level.setBlock(p.set(c.getX() + dx, y, c.getZ() + dz), state, FLAGS);
		}
	}

	public static double airFraction(WorldGenLevel level, BlockPos c, int r, int y0, int y1) {
		int air = 0, total = 0;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = y0; y <= y1; y += 2) for (int dz = -r; dz <= r; dz += 3) for (int dx = -r; dx <= r; dx += 3) {
			total++;
			if (level.getBlockState(p.set(c.getX() + dx, c.getY() + y, c.getZ() + dz)).isAir()) air++;
		}
		return total == 0 ? 0 : air / (double) total;
	}
}
