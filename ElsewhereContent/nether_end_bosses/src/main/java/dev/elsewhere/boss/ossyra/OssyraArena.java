package dev.elsewhere.boss.ossyra;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.Build;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Matriarch Spire: an end-stone island with a purpur tower whose top is a hollow purpur cube (15 inside, floor and
 * ceiling both walkable), end rods on the edges and the Matriarch Pedestal on the floor. A void arena.
 */
public final class OssyraArena implements ArenaBuilder {
	public static final OssyraArena INSTANCE = new OssyraArena();
	public static final int INNER = 15;
	public static final BlockPos PEDESTAL = new BlockPos(0, 0, 5);

	public static BossArena arena(BlockPos o) {
		return new BossArena(o, 12, o.getY() - 1, o.getY() + INNER, true, Vec3.atBottomCenterOf(o.offset(0, 0, -4)));
	}

	@Override
	public void build(WorldGenLevel level, BlockPos o, RandomSource random) {
		int half = INNER / 2 + 1; // 8: walls at +-8
		BlockState purpur = Blocks.PURPUR_BLOCK.defaultBlockState();
		BlockState pillar = Blocks.PURPUR_PILLAR.defaultBlockState();
		int y0 = o.getY() - 1, y1 = o.getY() + INNER;
		Build.box(level, o.offset(-half, -1, -half), o.offset(half, INNER, half), purpur);
		Build.box(level, o.offset(-half + 1, 0, -half + 1), o.offset(half - 1, INNER - 1, half - 1), Blocks.AIR.defaultBlockState());
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			for (int y = y0; y <= y1; y++) Build.set(level, o.getX() + sx * half, y, o.getZ() + sz * half, pillar);
			Build.set(level, o.getX() + sx * (half - 1), o.getY(), o.getZ() + sz * (half - 1), Blocks.END_ROD.defaultBlockState());
			Build.set(level, o.getX() + sx * (half - 1), o.getY() + INNER - 1, o.getZ() + sz * (half - 1), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN));
		}
		// windows of end rods along the walls, a door to the south
		for (int i = -6; i <= 6; i += 4) {
			for (int dy = 5; dy <= 9; dy++) {
				Build.set(level, o.getX() + i, o.getY() + dy, o.getZ() - half, Blocks.PURPUR_PILLAR.defaultBlockState());
				Build.set(level, o.getX() - half, o.getY() + dy, o.getZ() + i, Blocks.PURPUR_PILLAR.defaultBlockState());
				Build.set(level, o.getX() + half, o.getY() + dy, o.getZ() + i, Blocks.PURPUR_PILLAR.defaultBlockState());
			}
		}
		Build.box(level, o.offset(-1, 0, half), o.offset(1, 3, half), Blocks.AIR.defaultBlockState());
		// floor and ceiling patterns
		for (int dx = -half + 1; dx < half; dx++) for (int dz = -half + 1; dz < half; dz++) {
			if ((dx + dz) % 4 == 0) {
				Build.set(level, o.getX() + dx, y0, o.getZ() + dz, Blocks.PURPUR_PILLAR.defaultBlockState());
				Build.set(level, o.getX() + dx, y1, o.getZ() + dz, Blocks.PURPUR_PILLAR.defaultBlockState());
			}
		}
		// the tower below and the island at its foot
		for (int y = o.getY() - 26; y < y0; y++) {
			int r = y < o.getY() - 6 ? 2 : 3 + (y - (o.getY() - 6)) / 2; // widens towards the top ("inverted")
			Build.disc(level, new BlockPos(o.getX(), y, o.getZ()), y, r + 0.5, purpur);
		}
		for (int dy = 0; dy < 5; dy++) Build.disc(level, o, o.getY() - 27 - dy, 7.5 - dy * 1.4, Blocks.END_STONE.defaultBlockState());
		Build.set(level, o.offset(PEDESTAL), OssyraContent.MATRIARCH_PEDESTAL.defaultBlockState());
	}

	@Override
	public @Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random) {
		return null; // world generation disabled for the beta; the dev command builds it
	}

	@Override
	public BlockPos anchor(BlockPos origin) {
		return origin.offset(PEDESTAL);
	}
}
