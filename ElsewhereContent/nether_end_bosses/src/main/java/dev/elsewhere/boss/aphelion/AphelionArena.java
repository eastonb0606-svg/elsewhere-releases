package dev.elsewhere.boss.aphelion;

import dev.elsewhere.nether_end_bosses.ArenaBuilder;
import dev.elsewhere.nether_end_bosses.Build;
import dev.elsewhere.stub.nether_end_bosses.BossArena;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Shattered Halo: a central end-stone island with an obsidian spire, the Halo Altar at its foot and four
 * crystal pillars, ringed by seven floating islands over the void. A void arena (return to the ledge).
 */
public final class AphelionArena implements ArenaBuilder {
	public static final AphelionArena INSTANCE = new AphelionArena();
	public static final int ISLANDS = 7;
	public static final int RING = 18;
	public static final BlockPos ALTAR = new BlockPos(0, 0, 3);
	public static final int[][] CRYSTALS = {{6, 6}, {-6, 6}, {6, -6}, {-6, -6}};

	public static BossArena arena(BlockPos o) {
		return new BossArena(o, 24, o.getY() - 8, o.getY() + 26, true, Vec3.atBottomCenterOf(o.offset(0, 0, -4)));
	}

	public static BlockPos islandCenter(BlockPos o, int i) {
		double a = i * Math.PI * 2 / ISLANDS;
		return o.offset((int) Math.round(Math.cos(a) * RING), (i % 3) - 1, (int) Math.round(Math.sin(a) * RING));
	}

	private static void island(WorldGenLevel level, BlockPos top, double r) {
		for (int d = 0; d < 6; d++) {
			double rr = r - d * (r / 5.0);
			if (rr < 0.8) break;
			Build.disc(level, top, top.getY() - 1 - d, rr, Blocks.END_STONE.defaultBlockState());
		}
	}

	@Override
	public void build(WorldGenLevel level, BlockPos o, RandomSource random) {
		island(level, o, 10.5);
		Build.ring(level, o, o.getY() - 1, 3.5, 4.5, Blocks.END_STONE_BRICKS.defaultBlockState());
		for (int dy = 0; dy < 14; dy++) Build.disc(level, o, o.getY() + dy, dy < 10 ? 1.5 : 0.9, dy % 4 == 3 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState());
		Build.set(level, o.offset(0, 14, 0), Blocks.PURPUR_BLOCK.defaultBlockState());
		for (int[] c : CRYSTALS) {
			for (int dy = 0; dy < 4; dy++) Build.set(level, o.getX() + c[0], o.getY() + dy, o.getZ() + c[1], Blocks.OBSIDIAN.defaultBlockState());
			Build.set(level, o.getX() + c[0], o.getY() + 4, o.getZ() + c[1], Blocks.BEDROCK.defaultBlockState());
		}
		for (int i = 0; i < ISLANDS; i++) island(level, islandCenter(o, i), 3.5 + (i % 2));
		Build.set(level, o.offset(ALTAR), AphelionContent.HALO_ALTAR.defaultBlockState());
	}

	@Override
	public @Nullable BlockPos findOrigin(WorldGenLevel level, BlockPos center, RandomSource random) {
		return null; // world generation disabled for the beta; the dev command builds it
	}

	@Override
	public BlockPos anchor(BlockPos origin) {
		return origin.offset(ALTAR);
	}
}
