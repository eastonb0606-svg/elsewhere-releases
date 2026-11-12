package dev.elsewhere.boss.kazehana;

import dev.elsewhere.petalwind.PetalwindBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

/** Builds the Petal Pagoda (Kazehana's arena): three red-roofed tiers on a calcite base, an open roof terrace with 4 wind-bell posts. */
public final class PetalPagoda {
	private PetalPagoda() {}

	public static final int ARENA_RADIUS = 9;
	public static final int TERRACE_Y = 17;

	/** Builds with the ground floor at {@code base}; returns the arena centre (standing position on the terrace). */
	public static BlockPos build(WorldGenLevel level, BlockPos base, boolean withIsland) {
		BlockState calcite = Blocks.CALCITE.defaultBlockState();
		BlockState planks = PetalwindBlocks.SKYBLOSSOM_PLANKS.defaultBlockState();
		BlockState log = PetalwindBlocks.SKYBLOSSOM_LOG.defaultBlockState();
		BlockState roof = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		if (withIsland) {
			for (int dy = 0; dy < 10; dy++) {
				int r = 11 - dy;
				for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dz * dz <= r * r) set(level, base.offset(dx, -1 - dy, dz), dy == 0 ? Blocks.GRASS_BLOCK.defaultBlockState()
						: dy < 3 ? Blocks.DIRT.defaultBlockState() : calcite);
				}
			}
		}
		for (int dx = -7; dx <= 7; dx++) for (int dz = -7; dz <= 7; dz++) set(level, base.offset(dx, 0, dz), calcite);
		int[][] tiers = {{6, 1, 5}, {5, 7, 4}, {4, 12, 4}};
		for (int[] t : tiers) {
			int hw = t[0], y0 = t[1], h = t[2];
			for (int dy = 0; dy < h; dy++) for (int dx = -hw; dx <= hw; dx++) for (int dz = -hw; dz <= hw; dz++) {
				boolean edge = Math.abs(dx) == hw || Math.abs(dz) == hw;
				boolean corner = Math.abs(dx) == hw && Math.abs(dz) == hw;
				BlockPos p = base.offset(dx, y0 + dy, dz);
				if (corner) set(level, p, log);
				else if (edge) set(level, p, (dy == 1 || dy == 2) && Math.abs(dx) <= 1 ? Blocks.AIR.defaultBlockState() : planks);
				else set(level, p, air);
			}
			int ry = y0 + h, rr = hw + 2;
			for (int dx = -rr; dx <= rr; dx++) for (int dz = -rr; dz <= rr; dz++) set(level, base.offset(dx, ry, dz), roof);
		}
		// central ladder column up to the terrace
		for (int y = 1; y <= TERRACE_Y; y++) {
			set(level, base.offset(0, y, 0), calcite);
			set(level, base.offset(0, y, 1), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
		}
		// roof terrace
		int ty = TERRACE_Y;
		for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
			boolean rim = Math.abs(dx) == 8 || Math.abs(dz) == 8;
			set(level, base.offset(dx, ty, dz), rim ? calcite : planks);
			for (int dy = 1; dy <= 6; dy++) set(level, base.offset(dx, ty + dy, dz), air);
		}
		set(level, base.offset(0, ty, 1), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			BlockPos post = base.offset(sx * 7, ty + 1, sz * 7);
			set(level, post, PetalwindBlocks.SKYBLOSSOM_FENCE.defaultBlockState());
			set(level, post.above(), PetalwindBlocks.SKYBLOSSOM_FENCE.defaultBlockState());
			set(level, post.above(2), PetalwindBlocks.WINDCHIME.defaultBlockState());
		}
		for (int i = 0; i < 6; i++) {
			int dx = (i % 3 - 1) * 9, dz = i < 3 ? -9 : 9;
			set(level, base.offset(dx, 0, dz), Blocks.GRASS_BLOCK.defaultBlockState());
			set(level, base.offset(dx, 1, dz), PetalwindBlocks.SKYBLOSSOM_SAPLING.defaultBlockState());
		}
		return base.above(ty + 1);
	}

	static void set(WorldGenLevel level, BlockPos pos, BlockState state) {
		level.setBlock(pos, state, 2);
	}
}
