package dev.elsewhere.boss.ankhara;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds the Sunpit (scaled down from the doc's 120 blocks to a 45-block crater): concentric sand rings over magma, an
 * inner sandstone floor, 6 glass pillars that bait Ankhara's lunge, and a sandstone/glass rim.
 */
public final class Sunpit {
	private Sunpit() {}

	public static final int ARENA_RADIUS = 21;
	public static final int OUTER_IN = 16, OUTER_OUT = 21, MID_IN = 10, MID_OUT = 15;

	public static List<BlockPos> pillars(BlockPos floorCenter) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			double a = Math.PI * 2 * i / 6 + 0.3;
			out.add(floorCenter.offset((int) Math.round(Math.cos(a) * 12.5), 0, (int) Math.round(Math.sin(a) * 12.5)));
		}
		return out;
	}

	/** Builds with the pit floor (standing level) at {@code center}; returns it. */
	public static BlockPos build(WorldGenLevel level, BlockPos center) {
		BlockState sand = Blocks.SAND.defaultBlockState();
		BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
		BlockState magma = Blocks.MAGMA_BLOCK.defaultBlockState();
		for (int dx = -23; dx <= 23; dx++) for (int dz = -23; dz <= 23; dz++) {
			double r = Math.sqrt(dx * dx + dz * dz);
			if (r > 23) continue;
			for (int dy = 0; dy < 10; dy++) level.setBlock(center.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
			if (r > ARENA_RADIUS) {
				for (int dy = -4; dy < 3; dy++) level.setBlock(center.offset(dx, dy, dz), dy == 2 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : sandstone, 2);
				if ((dx + dz) % 5 == 0) level.setBlock(center.offset(dx, 3, dz), Blocks.STAINED_GLASS.orange().defaultBlockState(), 2);
				continue;
			}
			boolean ring = (r >= OUTER_IN && r <= OUTER_OUT) || (r >= MID_IN && r <= MID_OUT);
			for (int dy = -5; dy <= -3; dy++) level.setBlock(center.offset(dx, dy, dz), sandstone, 2);
			level.setBlock(center.offset(dx, -2, dz), ring ? magma : sandstone, 2);
			level.setBlock(center.offset(dx, -1, dz), ring ? sand : Blocks.SMOOTH_SANDSTONE.defaultBlockState(), 2);
		}
		for (BlockPos p : pillars(center)) for (int dy = 0; dy < 6; dy++) level.setBlock(p.above(dy), Blocks.GLASS.defaultBlockState(), 2);
		return center;
	}

	public static boolean inRing(BlockPos center, BlockPos p, int in, int out) {
		double dx = p.getX() - center.getX(), dz = p.getZ() - center.getZ();
		double r = Math.sqrt(dx * dx + dz * dz);
		return r >= in && r <= out;
	}
}
