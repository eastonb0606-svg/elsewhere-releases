package dev.elsewhere.boss.solaxis;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.Build;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * The Pyre Cradle: a round nether-brick fortress chamber (radius 12, 14 high) with a magma rim, two lavafalls behind
 * iron bars, four blaze spawners on pillars and the dormant pyre core in the middle. Two corridor stubs lead out.
 */
public final class SolaxisArena implements ArenaBuilder {
	public static final SolaxisArena INSTANCE = new SolaxisArena();
	public static final int RADIUS = 12;
	public static final int HEIGHT = 14;
	/** Spawner offsets from the origin (floor centre). */
	public static final int[][] SPAWNERS = {{6, 2, 6}, {-6, 2, 6}, {6, 2, -6}, {-6, 2, -6}};

	@Override
	public void build(WorldGenLevel level, BlockPos o, RandomSource random) {
		BlockState brick = Blocks.NETHER_BRICKS.defaultBlockState();
		BlockState red = Blocks.RED_NETHER_BRICKS.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int y = o.getY();
		// shell: foundation, floor, walls, ceiling
		Build.disc(level, o, y - 2, RADIUS + 0.5, brick);
		Build.disc(level, o, y - 1, RADIUS + 0.5, brick);
		for (int dy = 0; dy < HEIGHT; dy++) {
			Build.disc(level, o, y + dy, RADIUS - 0.5, air);
			Build.ring(level, o, y + dy, RADIUS - 0.5, RADIUS + 0.5, dy % 5 == 4 ? red : brick);
		}
		Build.disc(level, o, y + HEIGHT, RADIUS + 0.5, brick);
		// floor pattern
		Build.ring(level, o, y - 1, 8.5, 9.5, red);
		Build.ring(level, o, y - 1, 3.5, 4.2, Blocks.CHISELED_NETHER_BRICKS.defaultBlockState());
		Build.ring(level, o, y - 1, 10.5, 11.5, Blocks.MAGMA_BLOCK.defaultBlockState());
		for (int i = 0; i < 40; i++) {
			int dx = random.nextInt(21) - 10, dz = random.nextInt(21) - 10;
			if (dx * dx + dz * dz < 80) Build.set(level, o.getX() + dx, y - 1, o.getZ() + dz, Blocks.CRACKED_NETHER_BRICKS.defaultBlockState());
		}
		// ceiling lights
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			Build.set(level, o.getX() + (int) Math.round(Math.cos(a) * 7), y + HEIGHT, o.getZ() + (int) Math.round(Math.sin(a) * 7), Blocks.SHROOMLIGHT.defaultBlockState());
		}
		// windows (fences) between the pillars
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4 + Math.PI / 8;
			int wx = o.getX() + (int) Math.round(Math.cos(a) * RADIUS), wz = o.getZ() + (int) Math.round(Math.sin(a) * RADIUS);
			for (int dy = 4; dy <= 6; dy++) Build.set(level, wx, y + dy, wz, Blocks.NETHER_BRICK_FENCE.defaultBlockState());
		}
		// lavafalls behind iron bars (east and west)
		for (int side = -1; side <= 1; side += 2) {
			int lx = o.getX() + side * RADIUS;
			for (int dy = 0; dy < HEIGHT; dy++) {
				for (int dz = -1; dz <= 1; dz++) {
					Build.set(level, lx + side, y + dy, o.getZ() + dz, brick);
					Build.set(level, lx, y + dy, o.getZ() + dz, Blocks.LAVA.defaultBlockState());
					Build.set(level, lx - side, y + dy, o.getZ() + dz, dy < 12 ? Blocks.IRON_BARS.defaultBlockState() : brick);
				}
			}
		}
		// doors north and south, with short corridors
		for (int side = -1; side <= 1; side += 2) {
			for (int r = RADIUS - 1; r <= RADIUS + 5; r++) {
				int cz = o.getZ() + side * r;
				for (int dx = -2; dx <= 2; dx++) {
					Build.set(level, o.getX() + dx, y - 1, cz, brick);
					Build.set(level, o.getX() + dx, y + 4, cz, brick);
					for (int dy = 0; dy < 4; dy++) {
						Build.set(level, o.getX() + dx, y + dy, cz, Math.abs(dx) == 2 ? brick : air);
					}
				}
			}
		}
		// spawner pillars
		for (int[] s : SPAWNERS) {
			for (int dy = 0; dy < s[1]; dy++) Build.set(level, o.getX() + s[0], y + dy, o.getZ() + s[2], red);
			BlockPos sp = o.offset(s[0], s[1], s[2]);
			Build.set(level, sp, Blocks.SPAWNER.defaultBlockState());
			if (level.getBlockEntity(sp) instanceof SpawnerBlockEntity spawner) spawner.setEntityId(EntityTypes.BLAZE, random);
		}
		// the core
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (dx != 0 || dz != 0) Build.set(level, o.getX() + dx, y - 1, o.getZ() + dz, Blocks.MAGMA_BLOCK.defaultBlockState());
		}
		Build.set(level, o, SolaxisContent.PYRE_CRADLE_CORE.defaultBlockState());
	}

	@Override
	public @Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random) {
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int y = 90; y >= 36; y--) {
			p.set(center.getX(), y, center.getZ());
			if (level.getBlockState(p).isAir() && level.getBlockState(p.below()).isSolid()
				&& level.getBlockState(p.above(2)).isAir()
				&& Build.airFraction(level, p, 10, 0, 12) > 0.45) {
				return p.immutable();
			}
		}
		return null;
	}

	@Override
	public BlockPos anchor(BlockPos origin) {
		return origin;
	}
}
