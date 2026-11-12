package dev.elsewhere.boss.verdigrim;

import dev.elsewhere.verdigris_ironpeak.world.BlockSink;
import dev.elsewhere.verdigris_ironpeak.world.Blueprint;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The Drowned Cathedral nave: a domed air pocket (radius 15, 14 high) in the Foundry Trench, with an 8-pipe organ on
 * the north wall (copper-bulb pipe tops glow before each note, a note block in front of each pipe), 4 copper bells,
 * 3 conduits and a door to the south. Coordinates are relative to the centre of the nave floor (y = 0).
 */
public final class VerdigrimArena {
	public static final int RADIUS = 15, HEIGHT = 15, PIPES = 8;
	public static final int[][] BELLS = {{-12, 2, -6}, {12, 2, -6}, {-12, 2, 8}, {12, 2, 8}};
	public static final int[][] CONDUITS = {{-10, 3, 6}, {10, 3, 6}, {0, 3, 12}};
	public static final int[] ORGAN = {0, 1, -12};
	public static final int[] BOSS_SPAWN = {0, 1, -6};

	private VerdigrimArena() {}

	public static int pipeX(int i) {
		return -7 + 2 * i;
	}

	public static int pipeHeight(int i) {
		return 12 - i;
	}

	public static BlockPos pipeTop(BlockPos c, int i) {
		return c.offset(pipeX(i), pipeHeight(i) + 1, -14);
	}

	public static BlockPos noteBlock(BlockPos c, int i) {
		return c.offset(pipeX(i), 1, -11);
	}

	public static BlockPos centerFromOrgan(BlockPos organ) {
		return organ.offset(-ORGAN[0], -ORGAN[1], -ORGAN[2]);
	}

	public static final Blueprint BLUEPRINT = Blueprint.register("drowned_cathedral", new Blueprint() {
		public int radius() {
			return RADIUS + 2;
		}

		public int height() {
			return HEIGHT + 2;
		}

		public int depth() {
			return 4;
		}

		public void build(BlockSink s, RandomSource r) {
			BlockState brick = Blocks.PRISMARINE_BRICKS.defaultBlockState(), dark = Blocks.DARK_PRISMARINE.defaultBlockState();
			BlockState plate = VerdigrisBlocks.VERDIGRIS_PLATE.defaultBlockState(), riveted = VerdigrisBlocks.RIVETED_VERDIGRIS_PLATE.defaultBlockState();
			int R = RADIUS + 2;
			for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
				double d = Math.sqrt(x * x + z * z);
				if (d > RADIUS + 1.6) continue;
				for (int y = -3; y <= -1; y++) s.set(x, y, z, y == -1 ? brick : Blocks.TUFF.defaultBlockState());
				// Floor: prismarine bricks with a dark cross and a copper aisle.
				s.set(x, 0, z, Math.abs(x) <= 1 ? riveted : (x == z || x == -z) ? dark : brick);
				boolean wall = d > RADIUS - 0.5;
				// Dome: rises from y 14 at the wall to y 16 in the middle.
				int roof = HEIGHT - 1 + (int) Math.round(2 * (1 - d / (RADIUS + 1.6)));
				for (int y = 1; y <= roof + 1; y++) {
					if (wall || y >= roof) {
						boolean pillar = wall && (Math.round(Math.toDegrees(Math.atan2(z, x))) % 30 + 30) % 30 < 4;
						BlockState w = pillar ? Blocks.CUT_COPPER.weathering().oxidized().defaultBlockState() : y >= roof ? ((x + z) % 5 == 0 && !wall ? Blocks.SEA_LANTERN.defaultBlockState() : dark) : (y % 4 == 0 ? brick : dark);
						s.set(x, y, z, w);
					} else {
						s.set(x, y, z, Blocks.AIR.defaultBlockState());
					}
				}
			}
			// Windows of sea lantern in the walls.
			for (int a = 0; a < 12; a++) {
				double ang = Math.PI * 2 * a / 12 + 0.26;
				int x = (int) Math.round(Math.cos(ang) * (RADIUS + 0.5)), z = (int) Math.round(Math.sin(ang) * (RADIUS + 0.5));
				for (int y = 5; y <= 8; y++) s.set(x, y, z, Blocks.SEA_LANTERN.defaultBlockState());
			}
			// South doors (doors keep the water out even when open).
			for (int dx = -1; dx <= 0; dx++) {
				for (int dz = RADIUS - 1; dz <= RADIUS + 1; dz++) for (int y = 1; y <= 3; y++) s.set(dx, y, dz, Blocks.AIR.defaultBlockState());
				BlockState door = VerdigrisBlocks.KELPWOOD_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
					.setValue(DoorBlock.HINGE, dx == -1 ? net.minecraft.world.level.block.state.properties.DoorHingeSide.LEFT : net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT);
				s.set(dx, 1, RADIUS + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
				s.set(dx, 2, RADIUS + 1, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
				s.set(dx, 3, RADIUS + 1, dark);
				s.set(dx, 0, RADIUS + 1, brick);
			}
			for (int y = 1; y <= 3; y++) {
				s.set(-2, y, RADIUS + 1, dark);
				s.set(1, y, RADIUS + 1, dark);
			}
			// The organ: 8 pipes (tallest = lowest note) with glowing bulb tops, a note block in front of each, the console.
			for (int i = 0; i < PIPES; i++) {
				int x = pipeX(i);
				for (int y = 1; y <= pipeHeight(i); y++) s.set(x, y, -14, VerdigrisBlocks.VERDIGRIS_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
				s.set(x, pipeHeight(i) + 1, -14, Blocks.COPPER_BULB.waxed().unaffected().defaultBlockState().setValue(CopperBulbBlock.LIT, false));
				s.set(x, 0, -11, Blocks.COPPER_BLOCK.waxed().unaffected().defaultBlockState());
				s.set(x, 1, -11, Blocks.NOTE_BLOCK.defaultBlockState().setValue(NoteBlock.NOTE, Math.min(24, i * 3)));
				s.set(x, 1, -13, VerdigrisBlocks.VERDIGRIS_PIPE_JUNCTION.defaultBlockState());
			}
			s.set(ORGAN[0], ORGAN[1], ORGAN[2], VerdigrisBlocks.DROWNED_ORGAN.defaultBlockState());
			for (int x = -9; x <= 9; x++) s.set(x, 0, -12, plate);
			// Bells on copper plinths.
			for (int[] b : BELLS) {
				s.set(b[0], b[1] - 1, b[2], Blocks.CUT_COPPER.waxed().unaffected().defaultBlockState());
				s.set(b[0], b[1], b[2], Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.FLOOR).setValue(BellBlock.FACING, b[0] < 0 ? Direction.EAST : Direction.WEST));
			}
			// Conduits on prismarine pedestals.
			for (int[] c : CONDUITS) {
				for (int y = 1; y < c[1]; y++) s.set(c[0], y, c[2], Blocks.PRISMARINE_BRICKS.defaultBlockState());
				s.set(c[0], c[1], c[2], Blocks.CONDUIT.defaultBlockState());
			}
			// Pews.
			for (int z = -2; z <= 10; z += 3) for (int x : new int[] {-8, -7, -6, -5, -4, 4, 5, 6, 7, 8}) {
				s.set(x, 1, z, VerdigrisBlocks.KELPWOOD_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH));
			}
		}
	});

	public static void init() {}
}
