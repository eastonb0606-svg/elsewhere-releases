package dev.elsewhere.boss.hrolf;

import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * The Proving Grounds: a round shelf colosseum (radius 14) at about Y 700, iron-bar rails on three sides, an open cliff
 * edge to the south, 4 clan-banner posts and the Challenge Horn at the north gate.
 */
public final class ProvingGroundsArena {
	public static final int RADIUS = 14, HEIGHT = 10;
	public static final int[] HORN = {0, 1, -13};

	private ProvingGroundsArena() {}

	/** The open cliff edge faces +Z (south): angles within 45 degrees of south. */
	public static boolean isOpenEdge(double dx, double dz) {
		return dz > 0 && Math.abs(dx) < dz;
	}

	public static net.minecraft.core.BlockPos centerFromHorn(net.minecraft.core.BlockPos horn) {
		return horn.offset(-HORN[0], -HORN[1], -HORN[2]);
	}

	public static final Blueprint BLUEPRINT = Blueprint.register("proving_grounds", new Blueprint() {
		public int radius() {
			return RADIUS + 1;
		}

		public int height() {
			return HEIGHT;
		}

		public void build(BlockSink s, RandomSource r) {
			for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > RADIUS + 1.5) continue;
				s.foundation(x, -1, z, Blocks.STONE.defaultBlockState());
				s.set(x, 0, z, d < 4 ? Blocks.POLISHED_ANDESITE.defaultBlockState() : ((int) d) % 4 == 0 ? Blocks.POLISHED_GRANITE.defaultBlockState()
					: r.nextInt(6) == 0 ? Blocks.ANDESITE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
				for (int y = 1; y <= HEIGHT; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
				if (d > RADIUS + 0.4 && !isOpenEdge(x, z)) {
					s.set(x, 1, z, Blocks.IRON_BARS.defaultBlockState());
					s.set(x, 2, z, Blocks.IRON_BARS.defaultBlockState());
				}
			}
			// Clan-banner posts.
			for (int[] p : new int[][] {{8, -8}, {-8, -8}, {9, 6}, {-9, 6}}) {
				for (int y = 1; y <= 3; y++) s.set(p[0], y, p[1], Blocks.SPRUCE_LOG.defaultBlockState());
				s.set(p[0], 4, p[1], Blocks.BANNER.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(BannerBlock.ROTATION, 0));
				s.set(p[0], 0, p[1], Blocks.CAMPFIRE.defaultBlockState());
				s.set(p[0], 0, p[1], Blocks.POLISHED_ANDESITE.defaultBlockState());
			}
			s.set(HORN[0], HORN[1], HORN[2], IronpeakBlocks.PROVING_CHALLENGE_HORN.defaultBlockState());
			s.set(-3, 1, -13, IronpeakBlocks.PRAYER_FLAGS.defaultBlockState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.EAST));
			s.set(3, 1, -13, IronpeakBlocks.HEARTHSTONE.defaultBlockState());
		}
	});

	public static void init() {}
}
