package dev.elsewhere.palehollow.fit3;

import dev.elsewhere.palehollow.world.PHSites;
import dev.elsewhere.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Hollow Shrine (STRUCTURES.md 4, core): a 7x7 ruin on a 2-step plinth that follows the slope (each step ring underpinned to its own ground),
 * a colonnade of leaning, broken columns with one standing lintel and a hanging lantern, a raised altar with the chest, and a fallen drum
 * lying on the ground outside. No air box: only the shrine floor is cleared, and the beard blends the hillside into the plinth.
 */
public final class ShrineKit {
	private ShrineKit() {}

	/** Column positions on the 7x7 rim (local). The first two always stand with a lintel. */
	static final int[][] COLS = {{-3, -3}, {0, -3}, {3, -3}, {-3, 0}, {3, 0}, {-3, 3}, {0, 3}, {3, 3}};

	public static void plan(CoreFit.Ground g, BlockPos o, long seed, List<StructurePiece> out) {
		int rot = (int) Math.floorMod(seed >>> 7, 4L);
		int f = o.getY();
		// beard ground at the lower plinth step (F - 2): the hillside meets the plinth's foot, the core stands on its steps
		out.add(new FitPiece("shrine", o, rot, f - 2, new int[] {-5, -5, 5, 5}, true, seed, new int[] {f}, f - 2 - CoreFit.UNDERPIN_CAP, f + 8));
	}

	/** Column height 2..5 (5 = whole, with a capital) and whether its top drum has slipped outward. */
	static int colHeight(W w, int i) {
		return i < 2 ? 5 : 2 + w.hash(COLS[i][0], 7, COLS[i][1]) % 4;
	}

	static boolean leans(W w, int i) {
		return i >= 2 && colHeight(w, i) >= 3 && w.hash(COLS[i][0], 11, COLS[i][1]) % 3 == 0;
	}

	public static void build(FitPiece p, W w) {
		int f = p.data[0];
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int lx = -5; lx <= 5; lx++) for (int lz = -5; lz <= 5; lz++) {
			if (!w.inCol(lx, lz)) continue;
			int d = Math.max(Math.abs(lx), Math.abs(lz));
			boolean corner = Math.abs(lx) == Math.abs(lz);
			Direction in = Math.abs(lx) >= Math.abs(lz) ? (lx > 0 ? Direction.WEST : Direction.EAST) : (lz > 0 ? Direction.NORTH : Direction.SOUTH);
			int top;
			BlockState s;
			if (d <= 3) {
				top = f;
				s = lx == 0 && lz == 0 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : lx == 0 && Math.abs(lz) == 2 ? Blocks.PALE_MOSS_BLOCK.defaultBlockState() : w.brick(lx, f, lz);
			} else if (d == 4) {
				top = f - 1;
				s = corner ? Blocks.STONE_BRICKS.defaultBlockState() : w.stair(Blocks.STONE_BRICK_STAIRS, in, false);
			} else {
				top = f - 2;
				s = corner || w.chance(lx, top, lz, 20) ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : w.stair(Blocks.MOSSY_STONE_BRICK_STAIRS, in, false);
			}
			w.set(lx, top, lz, s);
			w.underpin(lx, lz, top - 1, W.LADDER);
			// the shrine floor is the room; above the steps, natural blocks are cut back so the steps show (cut <= 4, above the beard)
			for (int y = top + 1; y <= f + 4; y++) {
				if (d <= 3) w.room(lx, y, lz);
				else if (!w.get(lx, y, lz).getFluidState().isEmpty()) continue;
				else w.room(lx, y, lz);
			}
			if (d <= 3 && d >= 1 && !(lx == 0 && Math.abs(lz) == 2) && w.chance(lx, f + 1, lz, 12)) w.set(lx, f + 1, lz, Blocks.PALE_MOSS_CARPET.defaultBlockState());
		}
		// colonnade: leaning, broken columns
		for (int i = 0; i < COLS.length; i++) {
			int cx = COLS[i][0], cz = COLS[i][1];
			int h = colHeight(w, i);
			boolean lean = leans(w, i);
			int sx = Integer.signum(cx), sz = Integer.signum(cz);
			for (int k = 1; k <= h; k++) {
				int lx = cx, lz = cz;
				if (lean && k == h) {
					lx += sx;
					lz += sz;
				}
				BlockState s;
				if (k == 1) s = Blocks.STONE_BRICKS.defaultBlockState();
				else if (k == h && h == 5 && !lean) s = Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
				else if (k == h) s = w.chance(cx, k, cz, 50) ? W.slab(Blocks.MOSSY_STONE_BRICK_SLAB, false) : Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
				else s = Blocks.STONE_BRICK_WALL.defaultBlockState();
				w.set(lx, f + k, lz, s);
			}
			if (lean) w.set(cx, f + h, cz, w.stair(Blocks.STONE_BRICK_STAIRS, sx != 0 ? (sx > 0 ? Direction.WEST : Direction.EAST) : (sz > 0 ? Direction.NORTH : Direction.SOUTH), true));
		}
		// the standing pair carries a lintel with corbels, hanging moss and a lantern
		for (int lx = -4; lx <= 1; lx++) {
			BlockState s = lx == -4 ? w.stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true) : lx == 1 ? w.stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true) : w.brick(lx, f + 6, -3);
			w.set(lx, f + 6, -3, s);
		}
		w.set(-1, f + 5, -3, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
		w.set(-2, f + 5, -3, Blocks.PALE_HANGING_MOSS.defaultBlockState());
		// altar: pedestal, chest, two candles, moon blooms on moss
		w.set(0, f + 1, 0, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
		w.set(0, f + 2, 0, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, w.f.d(Direction.SOUTH)));
		if (w.in(0, f + 2, 0)) RandomizableContainer.setBlockEntityLootTable(w.l, w.random, w.pos(0, f + 2, 0), PHSites.loot("hollow_shrine"));
		w.set(-1, f + 1, 0, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 3));
		w.set(1, f + 1, 0, Blocks.CANDLE.defaultBlockState().setValue(CandleBlock.LIT, true).setValue(CandleBlock.CANDLES, 2));
		w.set(0, f + 1, -2, ModBlocks.MOON_BLOOM.defaultBlockState());
		w.set(0, f + 1, 2, ModBlocks.MOON_BLOOM.defaultBlockState());
		// a fallen drum lying on its own ground outside the plinth (dry land only)
		int fx = 7 + w.hash(0, 3, 0) % 2, fz = (w.hash(0, 5, 0) % 5) - 2;
		for (int k = 0; k < 3; k++) {
			int lx = fx + k, lz = fz;
			if (!w.inCol(lx, lz)) continue;
			int g = w.l.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, w.f.x(lx, lz), w.f.z(lx, lz)) - 1;
			int s = w.l.getHeight(Heightmap.Types.WORLD_SURFACE_WG, w.f.x(lx, lz), w.f.z(lx, lz)) - 1;
			if (s > g || Math.abs(g - (f - 2)) > 6) continue;
			w.set(lx, g + 1, lz, k == 2 ? w.stair(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.WEST, false) : k == 1 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
			if (w.get(lx, g + 2, lz).canBeReplaced()) w.set(lx, g + 2, lz, k == 0 ? Blocks.PALE_MOSS_CARPET.defaultBlockState() : air);
		}
	}

	/** World {x, y, z} of every plinth/floor block: the block below each must be solid (tests). */
	public static java.util.List<int[]> floors(FitPiece p) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		Frame fr = p.frame();
		int f = p.data[0];
		for (int lx = -5; lx <= 5; lx++) for (int lz = -5; lz <= 5; lz++) {
			int d = Math.max(Math.abs(lx), Math.abs(lz));
			out.add(new int[] {fr.x(lx, lz), d <= 3 ? f : d == 4 ? f - 1 : f - 2, fr.z(lx, lz)});
		}
		return out;
	}

	/** World {x, y, z} cells that must be open (the shrine floor's headroom, inside the colonnade). */
	public static java.util.List<int[]> headroom(FitPiece p) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		Frame fr = p.frame();
		int f = p.data[0];
		for (int lx = -2; lx <= 2; lx++) for (int lz = -2; lz <= 2; lz++) for (int y = f + 3; y <= f + 4; y++) out.add(new int[] {fr.x(lx, lz), y, fr.z(lx, lz)});
		return out;
	}

	/** Where the shrine's chest sits (for tests). */
	public static BlockPos chest(FitPiece p) {
		return new BlockPos(p.origin.getX(), p.data[0] + 2, p.origin.getZ());
	}
}
