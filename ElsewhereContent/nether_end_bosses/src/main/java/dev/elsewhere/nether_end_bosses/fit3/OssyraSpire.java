package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.boss.ossyra.OssyraArena;
import dev.elsewhere.boss.ossyra.OssyraContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * v2 Matriarch Spire (STRUCTURES.md 4, "requires highlands or midlands; the spire roots into its island with buttress roots").
 * The hollow purpur cube on top (15 inside, floor and ceiling walkable, pedestal) is unchanged, so Ossyra's gravity flips and
 * void blooms work as before. Below it the tower now stands on its island: the island cap has a footing down to the end stone it
 * was sited on, and six buttress roots climb the tower from the island (flying ribs from 9 out and 7 down), each root foot on
 * its own footing. The structure JSON keeps it to end_highlands and end_midlands, where the ground is solid.
 */
public final class OssyraSpire implements FitArena {
	static final BlockState STONE = Blocks.END_STONE.defaultBlockState();
	static final FitW.Ladder LADDER = new FitW.Ladder(STONE, STONE, STONE, STONE, Blocks.END_STONE_BRICKS.defaultBlockState());
	static final int INNER = OssyraArena.INNER;

	@Override
	public String id() {
		return "ossyra";
	}

	@Override
	public void build(FitW w, BlockPos o) {
		int half = INNER / 2 + 1;
		int x = o.getX(), y = o.getY(), z = o.getZ();
		BlockState purpur = Blocks.PURPUR_BLOCK.defaultBlockState();
		BlockState pillar = Blocks.PURPUR_PILLAR.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		int y0 = y - 1, y1 = y + INNER, foot = y - 27;
		// the island cap at the tower foot, on real ground
		for (int dz = -9; dz <= 9; dz++) for (int dx = -9; dx <= 9; dx++) {
			double d = Math.sqrt(dx * dx + dz * dz);
			if (d > 8.5) continue;
			int cx = x + dx, cz = z + dz;
			w.set(cx, foot, cz, d < 3.5 ? Blocks.END_STONE_BRICKS.defaultBlockState() : STONE);
			w.footingOrPier(cx, cz, foot - 1, LADDER, 24, 48, o);
		}
		// the cube (as 2.x)
		w.box(x - half, y - 1, z - half, x + half, y + INNER, z + half, purpur);
		w.box(x - half + 1, y, z - half + 1, x + half - 1, y + INNER - 1, z + half - 1, air);
		for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
			for (int yy = y0; yy <= y1; yy++) w.set(x + sx * half, yy, z + sz * half, pillar);
			w.set(x + sx * (half - 1), y, z + sz * (half - 1), Blocks.END_ROD.defaultBlockState());
			w.set(x + sx * (half - 1), y + INNER - 1, z + sz * (half - 1), Blocks.END_ROD.defaultBlockState().setValue(EndRodBlock.FACING, Direction.DOWN));
		}
		for (int i = -6; i <= 6; i += 4) for (int dy = 5; dy <= 9; dy++) {
			w.set(x + i, y + dy, z - half, pillar);
			w.set(x - half, y + dy, z + i, pillar);
			w.set(x + half, y + dy, z + i, pillar);
		}
		w.box(x - 1, y, z + half, x + 1, y + 3, z + half, air);
		for (int dx = -half + 1; dx < half; dx++) for (int dz = -half + 1; dz < half; dz++) {
			if ((dx + dz) % 4 == 0) {
				w.set(x + dx, y0, z + dz, pillar);
				w.set(x + dx, y1, z + dz, pillar);
			}
		}
		// the tower, widening towards the cube ("inverted"), from the island up
		for (int yy = foot + 1; yy < y0; yy++) {
			int r = yy < y - 6 ? 2 : 3 + (yy - (y - 6)) / 2;
			int ir = r + 1;
			for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++)
				if (dx * dx + dz * dz <= (r + 0.5) * (r + 0.5)) w.set(x + dx, yy, z + dz, purpur);
		}
		roots(w, o, foot);
		w.set(o.offset(OssyraArena.PEDESTAL), OssyraContent.MATRIARCH_PEDESTAL.defaultBlockState());
	}

	/** Six flying buttress roots: from the tower 7 above the island down and out to a foot 8 out, each foot on a footing. */
	private static void roots(FitW w, BlockPos o, int foot) {
		int x = o.getX(), z = o.getZ();
		BlockState rib = Blocks.PURPUR_PILLAR.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + Math.PI / 6;
			double ca = Math.cos(a), sa = Math.sin(a);
			for (int k = 0; k <= 6; k++) {
				double rad = 2.5 + k; // 2.5 .. 8.5
				int rx = x + (int) Math.round(ca * rad), rz = z + (int) Math.round(sa * rad);
				int ry = foot + 7 - k;
				w.set(rx, ry, rz, k == 6 ? Blocks.END_STONE_BRICKS.defaultBlockState() : rib);
				if (k < 6) {
					Direction out = Math.abs(ca) > Math.abs(sa) ? (ca > 0 ? Direction.EAST : Direction.WEST) : (sa > 0 ? Direction.SOUTH : Direction.NORTH);
					w.soft(rx, ry - 1, rz, Blocks.PURPUR_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out).setValue(StairBlock.HALF, Half.TOP));
				}
				// the root foot (and any rib cell that reaches the island) stands on the ground
				if (k >= 5) w.footingOrPier(rx, rz, ry - 1, LADDER, 24, 48, o);
			}
		}
	}

	@Override
	public BlockPos anchor(BlockPos o) {
		return o.offset(OssyraArena.PEDESTAL);
	}

	@Override
	public int floorRadius() {
		return 8;
	}

	@Override
	public int footingTop() {
		return -28;
	}
}
