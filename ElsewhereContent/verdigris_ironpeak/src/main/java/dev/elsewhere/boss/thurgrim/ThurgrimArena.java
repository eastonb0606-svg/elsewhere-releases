package dev.elsewhere.boss.thurgrim;

import dev.elsewhere.ironpeak.IronpeakBlocks;
import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;

/** The Summit Throne: an icy plateau (radius 16) with 6 iron obelisks topped by lightning rods, a throne and the war drum. */
public final class ThurgrimArena {
	public static final int RADIUS = 16, HEIGHT = 14, OBELISKS = 6;
	public static final int[] DRUM = {0, 1, -11};

	private ThurgrimArena() {}

	public static BlockPos obelisk(BlockPos c, int i) {
		double a = Math.toRadians(60 * i + 30);
		return c.offset((int) Math.round(Math.cos(a) * 13), 1, (int) Math.round(Math.sin(a) * 13));
	}

	public static BlockPos centerFromDrum(BlockPos drum) {
		return drum.offset(-DRUM[0], -DRUM[1], -DRUM[2]);
	}

	public static final Blueprint BLUEPRINT = Blueprint.register("summit_throne", new Blueprint() {
		public int radius() {
			return RADIUS + 1;
		}

		public int height() {
			return HEIGHT;
		}

		public void build(BlockSink s, RandomSource r) {
			for (int x = -RADIUS; x <= RADIUS; x++) for (int z = -RADIUS; z <= RADIUS; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > RADIUS + 0.5) continue;
				s.foundation(x, -1, z, Blocks.PACKED_ICE.defaultBlockState());
				s.set(x, 0, z, d < 5 ? IronpeakBlocks.POLISHED_FROSTGRANITE.defaultBlockState() : r.nextInt(5) == 0 ? Blocks.SNOW_BLOCK.defaultBlockState()
					: r.nextInt(3) == 0 ? IronpeakBlocks.GLACIER_ICE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState());
				for (int y = 1; y <= HEIGHT; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
			}
			for (int i = 0; i < OBELISKS; i++) {
				BlockPos p = obelisk(s.origin, i);
				int dx = p.getX() - s.origin.getX(), dz = p.getZ() - s.origin.getZ();
				for (int y = 1; y <= 6; y++) s.set(dx, y, dz, y == 6 ? Blocks.IRON_BLOCK.defaultBlockState() : Blocks.POLISHED_DEEPSLATE.defaultBlockState());
				s.set(dx, 7, dz, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
			}
			// Throne at the north edge, drum in front of it.
			for (int x = -2; x <= 2; x++) {
				s.set(x, 1, -14, IronpeakBlocks.FROSTGRANITE_BRICKS.defaultBlockState());
				s.set(x, 2, -15, IronpeakBlocks.FROSTGRANITE_BRICKS.defaultBlockState());
				s.set(x, 3, -15, Math.abs(x) == 2 ? Blocks.IRON_BLOCK.defaultBlockState() : IronpeakBlocks.FROSTGRANITE_BRICKS.defaultBlockState());
			}
			s.set(0, 1, -13, IronpeakBlocks.POLISHED_FROSTGRANITE_STAIRS.defaultBlockState());
			s.set(DRUM[0], DRUM[1], DRUM[2], IronpeakBlocks.SUMMIT_WAR_DRUM.defaultBlockState());
			for (int[] f : new int[][] {{-5, -12}, {5, -12}}) {
				s.set(f[0], 1, f[1], Blocks.SPRUCE_FENCE.defaultBlockState());
				s.set(f[0], 2, f[1], IronpeakBlocks.PRAYER_FLAGS.defaultBlockState());
			}
		}
	});

	public static void init() {}
}
