package dev.elsewhere.palehollow.fit3;

import dev.elsewhere.palehollow.world.ResinKiln;
import dev.elsewhere.registry.ModEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.StructurePiece;

/**
 * Resin Kiln (STRUCTURES.md 4, core): an L-shaped half-timbered workshop (two wings, each on its own floor and stone plinth, joined by a step),
 * steep 2:1 pale-oak roofs meeting in a valley, a stone chimney stack on the west gable, a lean-to over the work yard and a beehive kiln of mud
 * brick. Built per chunk (every piece clipped to the chunk being generated); the Sap Tapper is spawned by the chunk that holds its spot.
 * No lit furnaces or campfires (STRUCTURES.md 5).
 */
public final class KilnKit {
	private KilnKit() {}

	public static void plan(CoreFit.Ground g, BlockPos o, long seed, List<StructurePiece> out) {
		int rot = (int) Math.floorMod(seed >>> 11, 4L);
		Frame fr = new Frame(o.getX(), o.getZ(), rot);
		int oy = o.getY();
		int fa = Mth.clamp(CoreFit.median(g, fr, -5, -4, 3, 0), oy - 1, oy + 1);
		int fb = Mth.clamp(CoreFit.median(g, fr, -5, 1, -1, 6), fa - 1, fa + 1);
		int fy = Mth.clamp(CoreFit.median(g, fr, 0, 1, 4, 6), fa - 2, fa);
		int[] data = {fa, fb, fy};
		out.add(new FitPiece("kiln_a", o, rot, fa, new int[] {-6, -5, 4, 0}, true, seed, data, fa - CoreFit.UNDERPIN_CAP - 1, fa + 14));
		out.add(new FitPiece("kiln_b", o, rot, fb, new int[] {-6, 1, 0, 7}, true, seed ^ 0xB0B0L, data, fb - CoreFit.UNDERPIN_CAP - 1, fb + 14));
		out.add(new FitPiece("kiln_yard", o, rot, fy, new int[] {0, 1, 4, 6}, true, seed ^ 0x7A7AL, data, fy - CoreFit.UNDERPIN_CAP - 1, fa + 8));
	}

	static List<Roof> roofs(int fa, int fb) {
		return List.of(new Roof(false, -2, 3, fa + 5, 2, -6, 4), new Roof(true, -3, 3, fb + 5, 2, -1, 7));
	}

	static boolean wingA(int x, int z) {
		return x >= -5 && x <= 3 && z >= -4 && z <= 0;
	}

	static boolean wingB(int x, int z) {
		return x >= -5 && x <= -1 && z >= 1 && z <= 6;
	}

	public static BlockPos traderSpot(FitPiece p) {
		Frame fr = p.frame();
		return new BlockPos(fr.x(-3, 3), p.data[1] + 1, fr.z(-3, 3));
	}

	/** The barrel with the kiln's loot (world). */
	public static BlockPos barrel(FitPiece p) {
		Frame fr = p.frame();
		return new BlockPos(fr.x(2, -3), p.data[0] + 1, fr.z(2, -3));
	}

	/** World {x, y, z} of every floor, plinth and chimney-base block of this piece: the block below each must be solid (tests). */
	public static java.util.List<int[]> floors(FitPiece p) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		Frame fr = p.frame();
		int fa = p.data[0], fb = p.data[1], fy = p.data[2];
		for (int x = -6; x <= 4; x++) for (int z = -5; z <= 7; z++) {
			int y = Integer.MIN_VALUE;
			if (p.kind.equals("kiln_a") && (wingA(x, z) || x == -6 && z >= -3 && z <= -1)) y = fa;
			else if (p.kind.equals("kiln_b") && (wingB(x, z) || x == -3 && z == 7)) y = fb;
			else if (p.kind.equals("kiln_yard") && x >= 0 && z >= 1 && z <= 6) y = fy;
			if (y != Integer.MIN_VALUE) out.add(new int[] {fr.x(x, z), y, fr.z(x, z)});
		}
		return out;
	}

	/** World {x, y, z}: the wings' headroom and the door approach, which must be open of natural blocks (tests). */
	public static java.util.List<int[]> headroom(FitPiece p) {
		java.util.List<int[]> out = new java.util.ArrayList<>();
		Frame fr = p.frame();
		int fa = p.data[0], fb = p.data[1];
		for (int x = -6; x <= 4; x++) for (int z = -5; z <= 7; z++) {
			boolean a = p.kind.equals("kiln_a") && wingA(x, z) && x > -5 && x < 3 && z > -4 && z < 0;
			boolean b = p.kind.equals("kiln_b") && (wingB(x, z) && x > -5 && x < -1 && z < 6 || x == -3 && z == 7);
			if (a) out.add(new int[] {fr.x(x, z), fa + 3, fr.z(x, z)});
			if (b) out.add(new int[] {fr.x(x, z), fb + (z == 7 ? 2 : 3), fr.z(x, z)});
		}
		return out;
	}

	private static BlockState timber(W w, int x, int y, int z, boolean post, int floor) {
		if (y == floor) return Blocks.STONE_BRICKS.defaultBlockState();
		if (post) return Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState();
		return Blocks.PALE_OAK_PLANKS.defaultBlockState();
	}

	public static void build(FitPiece p, W w) {
		int fa = p.data[0], fb = p.data[1], fy = p.data[2];
		List<Roof> roofs = roofs(fa, fb);
		BlockState air = Blocks.AIR.defaultBlockState(), planks = Blocks.PALE_OAK_PLANKS.defaultBlockState();
		switch (p.kind) {
			case "kiln_a" -> {
				for (int x = -6; x <= 4; x++) for (int z = -5; z <= 0; z++) {
					if (!w.inCol(x, z)) continue;
					if (wingA(x, z)) {
						boolean wall = x == -5 || x == 3 || z == -4 || (z == 0 && (x < -4 || x > -2));
						w.underpin(x, z, fa - 1, W.LADDER);
						if (wall) {
							boolean post = (x == -5 || x == 3) && (z == -4 || z == 0 || z == -2) || (z == -4 || z == 0) && Math.floorMod(x + 5, 4) == 0;
							for (int y = fa; y <= fa + 4; y++) w.set(x, y, z, timber(w, x, y, z, post, fa));
							if (!post && (x + z) % 2 != 0 && (z == -4 || x == 3)) w.set(x, fa + 2, z, Blocks.GLASS_PANE.defaultBlockState());
						} else {
							w.set(x, fa, z, planks);
							for (int y = fa + 1; y <= fa + 10; y++) w.room(x, y, z);
							if (z == 0) {
								// the opening into wing B: a step when the floors differ
								if (fb > fa) w.set(x, fa + 1, z, w.stair(Blocks.PALE_OAK_STAIRS, Direction.SOUTH, false));
							}
						}
					} else {
						for (int y = fa + 1; y <= fa + 11; y++) w.soft(x, y, z);
					}
					if (z <= 0) Roof.column(w, roofs, x, z, Blocks.PALE_OAK_STAIRS, planks, null);
				}
				roofs.get(0).gable(w, -5, fa + 4, k -> Math.floorMod(k, 64) % 3 == 1 ? Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState() : planks);
				roofs.get(0).gable(w, 3, fa + 4, k -> Math.floorMod(k, 64) % 3 == 1 ? Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState() : planks);
				w.set(3, fa + 6, -2, Blocks.GLASS_PANE.defaultBlockState());
				// chimney stack on the west gable, with the hearth (unlit) in the wall
				for (int z = -3; z <= -1; z++) {
					w.underpin(-6, z, fa - 1, W.LADDER);
					for (int y = fa; y <= fa + 5; y++) w.set(-6, y, z, w.brick(-6, y, z));
				}
				w.set(-6, fa + 6, -3, w.stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
				w.set(-6, fa + 6, -1, w.stair(Blocks.STONE_BRICK_STAIRS, Direction.NORTH, false));
				for (int y = fa + 6; y <= fa + 13; y++) w.set(-6, y, -2, w.brick(-6, y, -2));
				w.set(-6, fa + 14, -2, Blocks.STONE_BRICK_WALL.defaultBlockState());
				w.set(-5, fa + 1, -2, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false).setValue(CampfireBlock.FACING, w.f.d(Direction.EAST)));
				w.set(-5, fa + 2, -2, air);
				w.set(-5, fa + 3, -2, w.stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
				// work room: loot barrel, crafting table, a table, resin stock, beam with a hanging lantern
				w.set(2, fa + 1, -3, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP));
				if (w.in(2, fa + 1, -3)) RandomizableContainer.setBlockEntityLootTable(w.l, w.random, w.pos(2, fa + 1, -3), ResinKiln.loot());
				w.set(1, fa + 1, -3, Blocks.CRAFTING_TABLE.defaultBlockState());
				w.set(-1, fa + 1, -3, Blocks.PALE_OAK_FENCE.defaultBlockState());
				w.set(-1, fa + 2, -3, Blocks.PALE_OAK_PRESSURE_PLATE.defaultBlockState());
				w.set(2, fa + 1, -1, Blocks.RESIN_BLOCK.defaultBlockState());
				w.set(2, fa + 2, -1, Blocks.RESIN_BLOCK.defaultBlockState());
				w.set(1, fa + 1, -1, Blocks.RESIN_BRICKS.defaultBlockState());
				BlockState beam = Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, w.f.d(Direction.SOUTH).getAxis());
				for (int z = -4; z <= 0; z++) w.set(-1, fa + 5, z, beam);
				w.set(-1, fa + 4, -2, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
			}
			case "kiln_b" -> {
				for (int x = -6; x <= 4; x++) for (int z = 1; z <= 7; z++) {
					if (!w.inCol(x, z)) continue;
					if (wingB(x, z)) {
						boolean wall = x == -5 || x == -1 || z == 6;
						w.underpin(x, z, fb - 1, W.LADDER);
						if (wall) {
							boolean post = (x == -5 || x == -1) && (z == 3 || z == 6);
							for (int y = fb; y <= fb + 4; y++) w.set(x, y, z, timber(w, x, y, z, post, fb));
							if (!post && x != -3 && z != 6 && z % 2 == 0) w.set(x, fb + 2, z, Blocks.GLASS_PANE.defaultBlockState());
						} else {
							w.set(x, fb, z, planks);
							for (int y = fb + 1; y <= fb + 10; y++) w.room(x, y, z);
							if (z == 1 && fb < fa) w.set(x, fb + 1, z, w.stair(Blocks.PALE_OAK_STAIRS, Direction.NORTH, false));
						}
					} else if (!(x >= 0 && z <= 6)) {
						for (int y = fb + 1; y <= fb + 11; y++) w.soft(x, y, z);
					}
					Roof.column(w, roofs, x, z, Blocks.PALE_OAK_STAIRS, planks, null);
				}
				roofs.get(1).gable(w, 6, fb + 4, k -> Math.floorMod(k, 64) % 3 == 0 ? Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState() : planks);
				// the door in the south gable, a step stone outside
				BlockState door = Blocks.PALE_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, w.f.d(Direction.NORTH));
				w.set(-3, fb + 1, 6, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
				w.set(-3, fb + 2, 6, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
				w.set(-3, fb + 6, 6, Blocks.GLASS_PANE.defaultBlockState());
				w.underpin(-3, 7, fb - 1, W.LADDER);
				w.set(-3, fb, 7, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
				for (int y = fb + 1; y <= fb + 2; y++) w.room(-3, y, 7);
				w.soft(-3, fb + 3, 7);
				// storeroom: resin stock, a barrel, a lantern
				w.set(-4, fb + 1, 1, Blocks.RESIN_BLOCK.defaultBlockState());
				w.set(-4, fb + 1, 2, Blocks.RESIN_BLOCK.defaultBlockState());
				w.set(-4, fb + 2, 1, Blocks.RESIN_BLOCK.defaultBlockState());
				w.set(-2, fb + 1, 5, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, w.f.d(Direction.NORTH)));
				w.set(-4, fb + 1, 5, Blocks.PALE_OAK_SLAB.defaultBlockState());
				w.set(-4, fb + 2, 5, Blocks.LANTERN.defaultBlockState());
				BlockPos spot = traderSpot(p);
				if (w.clip.isInside(spot)) {
					var tapper = ModEntities.SAP_TAPPER.create(w.l.getLevel(), EntitySpawnReason.STRUCTURE);
					if (tapper != null) {
						tapper.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, w.random.nextFloat() * 360F, 0F);
						tapper.setWanderTarget(spot);
						w.l.addFreshEntity(tapper);
					}
				}
			}
			default -> {
				// the work yard: a path floor, a lean-to on posts against wing A, a mud-brick beehive kiln with its flue, a fence
				for (int x = 0; x <= 4; x++) for (int z = 1; z <= 6; z++) {
					if (!w.inCol(x, z)) continue;
					int h = w.hash(x, fy, z) % 100;
					w.set(x, fy, z, h < 35 ? Blocks.GRAVEL.defaultBlockState() : h < 65 ? Blocks.COARSE_DIRT.defaultBlockState() : h < 85 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState());
					w.underpin(x, z, fy - 1, W.RUBBLE);
					for (int y = fy + 1; y <= fa + 4; y++) {
						if (!w.get(x, y, z).getFluidState().isEmpty()) continue;
						w.room(x, y, z);
					}
				}
				for (int x = 0; x <= 3; x++) {
					w.set(x, fa + 4, 2, w.stair(Blocks.PALE_OAK_STAIRS, Direction.NORTH, false));
					w.set(x, fa + 3, 3, w.stair(Blocks.PALE_OAK_STAIRS, Direction.NORTH, false));
				}
				for (int x : new int[] {0, 3}) for (int y = fy + 1; y <= fa + 2; y++) w.set(x, y, 3, Blocks.PALE_OAK_FENCE.defaultBlockState());
				BlockState logX = Blocks.STRIPPED_PALE_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, w.f.d(Direction.EAST).getAxis());
				w.set(1, fy + 1, 2, logX);
				w.set(2, fy + 1, 2, logX);
				w.set(1, fy + 2, 2, logX);
				w.set(3, fy + 1, 2, Blocks.CAULDRON.defaultBlockState());
				for (int x = 1; x <= 3; x++) for (int z = 4; z <= 6; z++) {
					boolean corner = x != 2 && z != 5;
					for (int y = fy + 1; y <= fy + 2; y++) w.set(x, y, z, corner ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.MUD_BRICKS.defaultBlockState());
					Direction in = x == 1 ? Direction.EAST : x == 3 ? Direction.WEST : z == 4 ? Direction.SOUTH : Direction.NORTH;
					w.set(x, fy + 3, z, x == 2 && z == 5 ? Blocks.MUD_BRICKS.defaultBlockState() : corner ? W.slab(Blocks.MUD_BRICK_SLAB, false) : w.stair(Blocks.MUD_BRICK_STAIRS, in, false));
				}
				w.set(2, fy + 1, 4, Blocks.SMOKER.defaultBlockState().setValue(FurnaceBlock.FACING, w.f.d(Direction.NORTH)));
				w.set(2, fy + 4, 5, Blocks.MUD_BRICK_WALL.defaultBlockState());
				w.set(2, fy + 5, 5, Blocks.MUD_BRICK_WALL.defaultBlockState());
				for (int z = 1; z <= 6; z++) if (z != 4) w.set(4, fy + 1, z, Blocks.PALE_OAK_FENCE.defaultBlockState());
				w.set(4, fy + 2, 1, Blocks.LANTERN.defaultBlockState());
			}
		}
	}
}
