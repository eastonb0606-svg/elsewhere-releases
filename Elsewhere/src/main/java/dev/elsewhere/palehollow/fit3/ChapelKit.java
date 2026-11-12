package dev.elsewhere.palehollow.fit3;

import dev.elsewhere.palehollow.world.PHSites;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Drowned Chapel (STRUCTURES.md 4, core): a cruciform bog chapel, only where the ground spread is at most 2. Two pieces on their own floors:
 * the nave (floor F-2, flooded 2 deep, waterlogged pews) and the transept with the chancel (floor F-1, one step up at the crossing, 1 deep).
 * A broken pale-oak gable roof (45 degrees, overhang 1) with a fallen-in bay over exposed tie beams; buttresses, lancet windows and stone
 * gables. Water stays inside the walls: every water block is a source whose sides are walls or other planned water, above a solid floor.
 */
public final class ChapelKit {
	private ChapelKit() {}

	public static void plan(CoreFit.Ground g, BlockPos o, long seed, List<StructurePiece> out) {
		int rot = (int) Math.floorMod(seed >>> 9, 4L);
		int f = o.getY();
		int[] data = {f};
		out.add(new FitPiece("chapel_nave", o, rot, f, new int[] {-5, -1, 5, 10}, true, seed, data, f - 2 - CoreFit.UNDERPIN_CAP, f + 13));
		out.add(new FitPiece("chapel_east", o, rot, f, new int[] {-9, -11, 9, -2}, true, seed ^ 0x5A5AL, data, f - 1 - CoreFit.UNDERPIN_CAP, f + 12));
	}

	static boolean nave(int x, int z) {
		return Math.abs(x) <= 4 && z >= -2 && z <= 9;
	}

	static boolean trans(int x, int z) {
		return Math.abs(x) <= 8 && z >= -6 && z <= -2;
	}

	static boolean chancel(int x, int z) {
		return Math.abs(x) <= 3 && z >= -10 && z <= -6;
	}

	static boolean inside(int x, int z) {
		return nave(x, z) || trans(x, z) || chancel(x, z);
	}

	static boolean wall(int x, int z) {
		return inside(x, z) && !(inside(x + 1, z) && inside(x - 1, z) && inside(x, z + 1) && inside(x, z - 1));
	}

	static List<Roof> roofs(int f) {
		return List.of(new Roof(true, 0, 5, f + 6, 1, -1, 10), new Roof(false, -4, 3, f + 6, 1, -9, 9), new Roof(true, 0, 4, f + 5, 1, -11, -4));
	}

	/** Planned water: interior cells, nave F-1..F, east F only. */
	public static boolean water(int f, int x, int y, int z) {
		if (!inside(x, z) || wall(x, z)) return false;
		int floorY = z >= -1 ? f - 2 : f - 1;
		return y > floorY && y <= f;
	}

	public static void build(FitPiece p, W w) {
		int f = p.data[0];
		boolean naveP = p.kind.equals("chapel_nave");
		int z0 = naveP ? -1 : -11, z1 = naveP ? 10 : -2;
		BlockState water = Blocks.WATER.defaultBlockState(), air = Blocks.AIR.defaultBlockState();
		List<Roof> roofs = roofs(f);
		for (int x = -9; x <= 9; x++) for (int z = z0; z <= z1; z++) {
			if (!w.inCol(x, z)) continue;
			boolean in = inside(x, z), wl = wall(x, z);
			int floorY = z >= -1 ? f - 2 : f - 1;
			int top = z <= -7 && Math.abs(x) <= 3 ? f + 4 : f + 5;
			Roof r = Roof.top(roofs, x, z);
			int roofY = r == null ? Roof.NONE : r.y(x, z);
			if (in) {
				w.underpin(x, z, floorY - 1, W.LADDER);
				if (wl) {
					for (int y = floorY; y <= top; y++) w.set(x, y, z, y <= f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : w.brick(x, y, z));
					// door in the west front, lancet windows, transept and apse windows (all above the water line)
					if (z == 9 && Math.abs(x) <= 1) for (int y = f + 1; y <= f + (x == 0 ? 3 : 2); y++) w.set(x, y, z, air);
					boolean lancet = (Math.abs(x) == 4 && (z == 1 || z == 4 || z == 7)) || (Math.abs(x) == 8 && z == -4) || (z == -10 && Math.abs(x) <= 1);
					if (lancet) for (int y = f + 2; y <= f + 3; y++) w.set(x, y, z, w.chance(x, y, z, 25) ? air : Blocks.GLASS_PANE.defaultBlockState());
				} else {
					w.set(x, floorY, z, w.chance(x, floorY, z, 30) ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
					for (int y = floorY + 1; y <= f; y++) w.set(x, y, z, water);
					int ceil = roofY == Roof.NONE ? top : roofY - 1;
					for (int y = f + 1; y <= ceil; y++) w.room(x, y, z);
				}
			} else {
				// outside: buttresses on the nave's long walls, otherwise only plants and leaves under the eaves are removed
				if (Math.abs(x) == 5 && (z == 2 || z == 5 || z == 8)) {
					w.underpin(x, z, f - 1, W.LADDER);
					for (int y = f; y <= f + 2; y++) w.set(x, y, z, y == f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
					w.set(x, f + 3, z, w.stair(Blocks.STONE_BRICK_STAIRS, x > 0 ? Direction.WEST : Direction.EAST, false));
				}
				if (roofY != Roof.NONE) for (int y = f + 1; y < roofY; y++) w.soft(x, y, z);
			}
			// roof: the topmost of the three wins; the bay over z 3..5 has fallen in, and a few stairs are missing
			Roof.column(w, roofs, x, z, Blocks.PALE_OAK_STAIRS, Blocks.PALE_OAK_PLANKS.defaultBlockState(),
				(lx, lz) -> (lz >= 3 && lz <= 5 && lx >= -2 && lx <= 3) || w.chance(lx, 200, lz, 6));
		}
		if (naveP) {
			// pews (submerged), tie beams under the broken bay, the front gable with an oculus, the gable over the crossing
			for (int z = 1; z <= 7; z += 2) for (int x : new int[] {-3, -2, 2, 3}) w.set(x, f - 1, z, W.waterlog(w.stair(Blocks.PALE_OAK_STAIRS, Direction.SOUTH, false)));
			BlockState beam = Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, w.f.d(Direction.EAST).getAxis());
			for (int z : new int[] {0, 4, 8}) for (int x = -4; x <= 4; x++) w.set(x, f + 6, z, beam);
			roofs.get(0).gable(w, 9, f + 5, k -> w.brick(k & 63, k >> 6, 9));
			w.set(0, f + 8, 9, Blocks.GLASS_PANE.defaultBlockState());
			for (int x = -4; x <= 4; x++) {
				int lo = roofs.get(1).y(x, -2), hi = roofs.get(0).y(x, -1);
				for (int y = Math.max(lo, f + 5) + 1; y < hi; y++) w.set(x, y, -2, w.brick(x, y, -2));
			}
			w.set(0, f + 3, 9, air);
			// the approach to the door stays open (a 3x2 cut at most, where a beard left the ground high)
			for (int x = -1; x <= 1; x++) for (int y = f + 1; y <= f + 2; y++) w.room(x, y, 10);
		} else {
			// crossing piers, altar with the chest, waterlogged soul lanterns, transept and apse gables
			BlockState pier = Blocks.PALE_OAK_LOG.defaultBlockState();
			for (int x : new int[] {-4, 4}) for (int y = f; y <= f + 5; y++) w.set(x, y, -2, y == f ? W.waterlog(Blocks.PALE_OAK_FENCE.defaultBlockState()) : pier);
			w.set(0, f, -8, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
			w.set(0, f + 1, -8, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, w.f.d(Direction.SOUTH)));
			if (w.in(0, f + 1, -8)) RandomizableContainer.setBlockEntityLootTable(w.l, w.random, w.pos(0, f + 1, -8), PHSites.loot("drowned_chapel"));
			for (int x : new int[] {-2, 2}) w.set(x, f, -8, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.WATERLOGGED, true));
			roofs.get(1).gable(w, -8, f + 5, k -> w.brick(-8, k >> 6, k & 63));
			roofs.get(1).gable(w, 8, f + 5, k -> w.brick(8, k >> 6, k & 63));
			roofs.get(2).gable(w, -10, f + 4, k -> w.brick(k & 63, k >> 6, -10));
		}
	}

	/** World {x, y, z} of every floor and wall-base block of this piece: the block below each must be solid (tests). */
	public static java.util.List<int[]> floors(FitPiece p) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		Frame fr = p.frame();
		int f = p.data[0];
		boolean naveP = p.kind.equals("chapel_nave");
		for (int x = -9; x <= 9; x++) for (int z = naveP ? -1 : -11; z <= (naveP ? 10 : -2); z++) {
			if (inside(x, z)) out.add(new int[] {fr.x(x, z), z >= -1 ? f - 2 : f - 1, fr.z(x, z)});
			else if (Math.abs(x) == 5 && (z == 2 || z == 5 || z == 8)) out.add(new int[] {fr.x(x, z), f, fr.z(x, z)});
		}
		return out;
	}

	/** World {x, y, z}: the door opening and the cells in front of it, which must be open (tests). */
	public static java.util.List<int[]> door(FitPiece p) {
		Frame fr = p.frame();
		int f = p.data[0];
		java.util.List<int[]> out = new java.util.ArrayList<>();
		for (int z = 9; z <= 10; z++) for (int y = f + 1; y <= f + 2; y++) out.add(new int[] {fr.x(0, z), y, fr.z(0, z)});
		return out;
	}

	/** The altar chest (world). */
	public static BlockPos altarChest(FitPiece p) {
		Frame fr = p.frame();
		return new BlockPos(fr.x(0, -8), p.data[0] + 1, fr.z(0, -8));
	}

	public static BlockPos lantern(FitPiece p, int side) {
		Frame fr = p.frame();
		return new BlockPos(fr.x(2 * side, -8), p.data[0], fr.z(2 * side, -8));
	}
}
