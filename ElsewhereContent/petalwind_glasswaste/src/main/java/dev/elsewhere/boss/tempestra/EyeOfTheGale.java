package dev.elsewhere.boss.tempestra;

import dev.elsewhere.petalwind.PetalwindBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Builds the Eye of the Gale: 8 calcite shard platforms around an open eye, with an updraft basin below the centre. */
public final class EyeOfTheGale {
	private EyeOfTheGale() {}

	public static final int ARENA_RADIUS = 18;
	public static final int RING = 12;

	/** Platform centres for a given arena centre (the arena centre is at platform height). */
	public static List<BlockPos> platforms(BlockPos center) {
		List<BlockPos> out = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			double a = Math.PI * 2 * i / 8;
			out.add(center.offset((int) Math.round(Math.cos(a) * RING), (i % 3) - 1, (int) Math.round(Math.sin(a) * RING)));
		}
		return out;
	}

	public static BlockPos build(WorldGenLevel level, BlockPos center) {
		BlockState calcite = Blocks.CALCITE.defaultBlockState();
		for (BlockPos p : platforms(center)) {
			for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
				if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
				level.setBlock(p.offset(dx, -1, dz), calcite, 2);
				if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) level.setBlock(p.offset(dx, -2, dz), calcite, 2);
				for (int dy = 0; dy < 4; dy++) level.setBlock(p.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
			}
			level.setBlock(p.offset(0, -3, 0), calcite, 2);
		}
		// central basin with updraft vents (catches players who fall into the eye)
		BlockPos basin = center.below(14);
		for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
			if (dx * dx + dz * dz > 18) continue;
			level.setBlock(basin.offset(dx, 0, dz), calcite, 2);
			if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) level.setBlock(basin.offset(dx, 1, dz), PetalwindBlocks.UPDRAFT_VENT.defaultBlockState(), 2);
		}
		return center;
	}
}
