package dev.elsewhere.boss.galvanox;

import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Lighthouse of the Last Engineer: a banded copper tower rising from the sea to a 13-radius platform ringed with
 * 10 lightning rods, its lantern on a spire at the north edge. Coordinates are relative to the platform centre (y = 0).
 */
public final class GalvanoxArena {
	public static final int RADIUS = 13, HEIGHT = 14, RODS = 10, TOWER_RADIUS = 7;
	public static final int[] LANTERN = {0, 7, -10};

	private GalvanoxArena() {}

	public static BlockPos rod(BlockPos c, int i) {
		double a = Math.toRadians(36 * i + 18);
		return c.offset((int) Math.round(Math.cos(a) * 12), 2, (int) Math.round(Math.sin(a) * 12));
	}

	/** Quadrant 0..3 of a point: bit 0 = east (x >= centre), bit 1 = south (z >= centre). */
	public static int quadrant(BlockPos c, double x, double z) {
		return (x >= c.getX() + 0.5 ? 1 : 0) + (z >= c.getZ() + 0.5 ? 2 : 0);
	}

	public static BlockPos centerFromLantern(BlockPos lantern) {
		return lantern.offset(-LANTERN[0], -LANTERN[1], -LANTERN[2]);
	}

	public static final Blueprint BLUEPRINT = Blueprint.register("lighthouse", new Blueprint() {
		public int radius() {
			return RADIUS + 2;
		}

		public int height() {
			return HEIGHT;
		}

		public int depth() {
			return 112;
		}

		public void build(BlockSink s, RandomSource r) {
			BlockState white = Blocks.CALCITE.defaultBlockState(), teal = Blocks.CUT_COPPER.weathering().oxidized().defaultBlockState();
			// Tower: banded teal and white, hollow with a ladder, down to the sea floor.
			for (int y = -1; y > -110; y--) {
				boolean any = false;
				for (int x = -TOWER_RADIUS; x <= TOWER_RADIUS; x++) for (int z = -TOWER_RADIUS; z <= TOWER_RADIUS; z++) {
					double d = Math.sqrt(x * x + z * z);
					if (d > TOWER_RADIUS + 0.3) continue;
					if (!s.inside(s.at(x, y, z))) continue;
					if (y < -12 && s.solid(x, y, z)) continue;
					any = true;
					if (d > TOWER_RADIUS - 1.2) s.set(x, y, z, ((-y) / 4) % 2 == 0 ? teal : white);
					else s.set(x, y, z, Blocks.AIR.defaultBlockState());
				}
				if (y < -12 && s.solid(0, y, TOWER_RADIUS - 1)) break;
				s.set(0, y, TOWER_RADIUS - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
				s.set(0, y, TOWER_RADIUS - 1, white);
				if (!any && s.inside(s.at(0, y, 0))) break;
			}
			// A door at sea level on the south side.
			// Platform.
			for (int x = -RADIUS - 1; x <= RADIUS + 1; x++) for (int z = -RADIUS - 1; z <= RADIUS + 1; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > RADIUS + 1.5) continue;
				boolean cross = x == 0 || z == 0;
				s.set(x, 0, z, cross ? Blocks.DARK_PRISMARINE.defaultBlockState() : ((x + z) & 1) == 0 ? Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState() : Blocks.CUT_COPPER.waxed().weathered().defaultBlockState());
				s.set(x, -1, z, d > TOWER_RADIUS - 1.2 ? VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE.defaultBlockState() : s.get(x, -1, z));
				for (int y = 1; y <= HEIGHT; y++) s.set(x, y, z, Blocks.AIR.defaultBlockState());
				if (d > RADIUS + 0.5) s.set(x, 1, z, VerdigrisBlocks.VERDIGRIS_PLATE_WALL.defaultBlockState());
			}
			s.set(0, 0, TOWER_RADIUS - 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH));
			s.set(0, 1, TOWER_RADIUS - 2, Blocks.AIR.defaultBlockState());
			// Lightning rods on copper pedestals.
			for (int i = 0; i < RODS; i++) {
				BlockPos p = rod(s.origin, i);
				int dx = p.getX() - s.origin.getX(), dz = p.getZ() - s.origin.getZ();
				s.set(dx, 1, dz, Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState()); // cut copper pedestal, not a copper storage block (s-throne B9)
				s.set(dx, 2, dz, Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
			}
			// The lantern spire.
			for (int y = 1; y < LANTERN[1]; y++) s.set(LANTERN[0], y, LANTERN[2], VerdigrisBlocks.VERDIGRIS_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
			s.set(LANTERN[0], LANTERN[1], LANTERN[2], VerdigrisBlocks.LIGHTHOUSE_LANTERN.defaultBlockState());
			s.set(LANTERN[0], LANTERN[1] + 1, LANTERN[2], Blocks.LIGHTNING_ROD.weathering().unaffected().defaultBlockState());
			for (int[] c : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) s.set(LANTERN[0] + c[0], LANTERN[1], LANTERN[2] + c[1], Blocks.COPPER_GRATE.waxed().unaffected().defaultBlockState());
		}
	});

	public static void init() {}
}
