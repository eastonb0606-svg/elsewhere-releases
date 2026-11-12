package dev.elsewhere.kingdom_villages.dev;

import dev.elsewhere.kingdom_villages.BlockWriter;
import dev.elsewhere.kingdom_villages.Plans;
import dev.elsewhere.kingdom_villages.VillageRecord;
import dev.elsewhere.kingdom_villages.s3.ald.AldFort;
import dev.elsewhere.kingdom_villages.s3.ald.AldHamlet;
import dev.elsewhere.kingdom_villages.s3.ald.AldLook;
import dev.elsewhere.kingdom_villages.s3.ald.AldRebuild;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * [ald-look] scenes for the 3.0 Aldermark looks (s-vlook-ald), shared by {@link AldLookTest} and the gametest screenshots: sculpted
 * hills on the flat test world (smooth, grassy, with tall grass to be cleared properly), a small Aldermark-held village on a hill
 * flank (two gabled oak houses, a bell, two dirt paths out), the motte-and-bailey outpost on a hilltop and the burnt farmstead on a
 * gentle slope. Every build goes through {@link BlockWriter} with the manager's {@code canSurvive} rule, so it can be undone.
 */
public final class AldLookScenes {
	private AldLookScenes() {}

	/** Default scene origins (the flat test world, far from everything else). */
	public static final BlockPos VILLAGE_HILL = new BlockPos(3000, 0, 3000), FORT_HILL = new BlockPos(3000, 0, 3100), HAMLET_HILL = new BlockPos(3100, 0, 3000),
		WATER = new BlockPos(3200, 0, 3000), CASTLE = new BlockPos(3000, -60, 2500);

	public static void load(ServerLevel level, BlockPos c, int radius, boolean force) {
		for (int cx = (c.getX() - radius) >> 4; cx <= (c.getX() + radius) >> 4; cx++) for (int cz = (c.getZ() - radius) >> 4; cz <= (c.getZ() + radius) >> 4; cz++) {
			if (force) level.setChunkForced(cx, cz, true);
			level.getChunk(cx, cz);
		}
	}

	public static void unforce(ServerLevel level, BlockPos c, int radius) {
		for (int cx = (c.getX() - radius) >> 4; cx <= (c.getX() + radius) >> 4; cx++) for (int cz = (c.getZ() - radius) >> 4; cz <= (c.getZ() + radius) >> 4; cz++)
			level.setChunkForced(cx, cz, false);
	}

	static int lift(int d, int radius, int height) {
		if (d >= radius) return 0;
		double t = 1.0 - (double) d / radius;
		return (int) Math.round(height * t * t * (3 - 2 * t));
	}

	/** A smooth grassy hill (smoothstep profile) on the flat ground at {@code c}; returns the old ground y. */
	public static int hill(ServerLevel level, BlockPos c, int radius, int height) {
		load(level, c, radius + 8, true);
		int base = AldLook.ground(level, c.getX(), c.getZ());
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
			int d = (int) Math.round(Math.hypot(dx, dz)), h = lift(d, radius, height);
			int x = c.getX() + dx, z = c.getZ() + dz;
			for (int y = base; y < base + h; y++) level.setBlock(new BlockPos(x, y, z), Blocks.DIRT.defaultBlockState(), 2);
			BlockPos top = new BlockPos(x, base + h, z);
			level.setBlock(top, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
			int hs = AldLook.hash(x, 7, z);
			if (hs < 12) level.setBlock(top.above(), Blocks.SHORT_GRASS.defaultBlockState(), 2);
			else if (hs < 15) {
				level.setBlock(top.above(), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), 2);
				level.setBlock(top.above(2), Blocks.TALL_GRASS.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), 2);
			}
		}
		return base;
	}

	/** Puts the flat ground back (air above {@code base}, grass at it). */
	public static void flatten(ServerLevel level, BlockPos c, int radius, int base, int up) {
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
			int x = c.getX() + dx, z = c.getZ() + dz;
			for (int y = base + up; y > base; y--) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
			level.setBlock(new BlockPos(x, base, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
		}
	}

	/** Writes ops like {@code VillageManager.runOp}: unsurvivable ops are skipped. Returns {written, unsurvivable, refused}. */
	public static int[] write(ServerLevel level, VillageRecord r, List<VillageRecord.Op> ops) {
		int[] n = new int[3];
		for (VillageRecord.Op op : ops) {
			BlockPos p = BlockPos.of(op.pos());
			if (!op.state().isAir() && !op.state().canSurvive(level, p)) {
				n[1]++;
				continue;
			}
			if (BlockWriter.write(level, r, p, op.state())) n[0]++;
			else n[2]++;
		}
		return n;
	}

	// ---- the hill village ---------------------------------------------------------------------------------------------

	public static final int V_HALF = 18;

	/** A village on the hill flank: centre {@code v}, the hill's peak at v + (8, 8). Returns its record (not in the ledger). */
	public static VillageRecord village(ServerLevel level, BlockPos v) {
		VillageRecord r = new VillageRecord();
		r.id = -4242;
		r.kind = VillageRecord.Kind.VILLAGE;
		r.owner = "elsewhere:aldermark";
		r.castle = CASTLE;
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int dx = -V_HALF; dx <= V_HALF; dx++) for (int dz = -V_HALF; dz <= V_HALF; dz++) {
			int g = AldLook.ground(level, v.getX() + dx, v.getZ() + dz);
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
		}
		house(level, r, v.getX() - 13, v.getZ() - 12);
		house(level, r, v.getX() - 13, v.getZ() + 4);
		house(level, r, v.getX() + 1, v.getZ() - 16);
		// the bell on the green and the two paths out (east, north)
		int bx = v.getX() - 4, bz = v.getZ(), bg = AldLook.ground(level, bx, bz);
		level.setBlock(new BlockPos(bx, bg + 1, bz), Blocks.BELL.defaultBlockState(), 2);
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(bx - 1, bg, bz - 1, bx + 1, bg + 2, bz + 1), VillageRecord.CENTER));
		for (int x = bx + 2; x <= v.getX() + V_HALF; x++) for (int z = bz - 1; z <= bz + 1; z++) path(level, x, z);
		for (int z = bz - 2; z >= v.getZ() - V_HALF; z--) for (int x = bx - 1; x <= bx + 1; x++) path(level, x, z);
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(v.getX() - V_HALF, lo - 1, v.getZ() - V_HALF, v.getX() + V_HALF, hi + 3, v.getZ() + V_HALF), VillageRecord.OTHER));
		r.center = new BlockPos(bx, bg + 1, bz);
		r.bounds = new BoundingBox(v.getX() - V_HALF, lo - 1, v.getZ() - V_HALF, v.getX() + V_HALF, hi + 12, v.getZ() + V_HALF);
		return r;
	}

	static void path(ServerLevel level, int x, int z) {
		int g = AldLook.ground(level, x, z);
		BlockPos p = new BlockPos(x, g, z);
		if (!level.getBlockState(p).is(Blocks.GRASS_BLOCK)) return;
		BlockState up = level.getBlockState(p.above());
		if (up.getBlock() instanceof DoublePlantBlock) level.setBlock(p.above(2), Blocks.AIR.defaultBlockState(), 2);
		if (!up.isAir()) level.setBlock(p.above(), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(p, Blocks.DIRT_PATH.defaultBlockState(), 2);
	}

	/** A plain village house (the kind Aldermark restyles): 5x7, cobble foundation to each column's ground, oak walls, oak gable roof, a door. */
	static void house(ServerLevel level, VillageRecord r, int x0, int z0) {
		int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
		for (int x = x0 - 1; x <= x0 + 5; x++) for (int z = z0 - 1; z <= z0 + 7; z++) {
			int g = AldLook.ground(level, x, z);
			lo = Math.min(lo, g);
			hi = Math.max(hi, g);
		}
		int y0 = hi + 1;
		for (int x = x0 - 1; x <= x0 + 5; x++) for (int z = z0 - 1; z <= z0 + 7; z++) for (int y = lo + 1; y <= y0 + 10; y++)
			if (!level.getBlockState(new BlockPos(x, y, z)).isAir() && !level.getBlockState(new BlockPos(x, y, z)).isSolid()) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
		for (int x = x0; x <= x0 + 4; x++) for (int z = z0; z <= z0 + 6; z++) {
			int g = AldLook.ground(level, x, z);
			for (int y = g + 1; y <= y0; y++) level.setBlock(new BlockPos(x, y, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
			boolean ex = x == x0 || x == x0 + 4, ez = z == z0 || z == z0 + 6;
			if (!ex && !ez) continue;
			for (int y = y0 + 1; y <= y0 + 3; y++)
				level.setBlock(new BlockPos(x, y, z), ex && ez ? Blocks.OAK_LOG.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState(), 2);
		}
		BlockPos door = new BlockPos(x0 + 2, y0 + 1, z0);
		BlockState d = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
		level.setBlock(door, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 2);
		level.setBlock(door.above(), d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
		for (int x = x0 - 1; x <= x0 + 5; x++) for (int z = z0 - 1; z <= z0 + 7; z++) {
			int k = Math.min(z - (z0 - 1), (z0 + 7) - z), ry = y0 + 4 + k;
			BlockState s = k == 4 ? Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)
				: Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, z <= z0 + 3 ? Direction.SOUTH : Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM);
			level.setBlock(new BlockPos(x, ry, z), s, 2);
			if ((x == x0 || x == x0 + 4) && z >= z0 && z <= z0 + 6) for (int y = y0 + 4; y < ry; y++) level.setBlock(new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState(), 2);
		}
		r.pieces.add(new VillageRecord.Piece(new BoundingBox(x0 - 1, lo, z0 - 1, x0 + 5, y0 + 9, z0 + 7), VillageRecord.HOUSE));
	}

	/** Builds the village scene: hill, village, the Aldermark rebuild written. Returns the record; {@code res} gets the write counts. */
	public static VillageRecord villageScene(ServerLevel level, BlockPos v, int[] res, Plans.Fortify[] plan) {
		hill(level, v.offset(8, 0, 8), 30, 10);
		load(level, v, V_HALF + 40, true);
		VillageRecord r = village(level, v);
		Plans.Fortify f = AldRebuild.fortify(level, r, RandomSource.create(4242));
		if (plan != null) plan[0] = f;
		int[] n = write(level, r, f.ops());
		System.arraycopy(n, 0, res, 0, 3);
		return r;
	}

	/** Builds the fort scene: hill, site, plan, written into a fresh OUTPOST record. Null when no site was found. */
	public static @Nullable VillageRecord fortScene(ServerLevel level, BlockPos c, int[] res, AldFort.Plan[] plan) {
		hill(level, c, 24, 9);
		load(level, c, 50, true);
		BlockPos m = AldFort.site(level, c.offset(12, 0, 7), CASTLE);
		if (m == null) return null;
		AldFort.Plan p = AldFort.plan(level, m, CASTLE);
		if (p == null) return null;
		if (plan != null) plan[0] = p;
		VillageRecord r = new VillageRecord();
		r.id = -4243;
		r.kind = VillageRecord.Kind.OUTPOST;
		r.center = p.yard();
		r.bounds = p.box();
		r.pieces.add(new VillageRecord.Piece(p.box(), VillageRecord.OTHER));
		r.state = VillageRecord.State.OCCUPIED;
		r.hint = p.flag();
		int[] n = write(level, r, p.ops());
		System.arraycopy(n, 0, res, 0, 3);
		return r;
	}

	/** Builds the hamlet scene: a gentle slope, site, plan, written into a fresh HAMLET record. */
	public static @Nullable VillageRecord hamletScene(ServerLevel level, BlockPos c, int[] res, AldHamlet.Plan[] plan) {
		hill(level, c, 26, 4);
		load(level, c, 40, true);
		BlockPos s = AldHamlet.site(level, c.offset(10, 0, 0));
		if (s == null) return null;
		AldHamlet.Plan p = AldHamlet.plan(level, s, RandomSource.create(77));
		if (plan != null) plan[0] = p;
		VillageRecord r = new VillageRecord();
		r.id = -4244;
		r.kind = VillageRecord.Kind.HAMLET;
		r.center = p.refuge();
		r.bounds = p.box();
		r.pieces.add(new VillageRecord.Piece(p.box(), VillageRecord.OTHER));
		int[] n = write(level, r, p.ops());
		System.arraycopy(n, 0, res, 0, 3);
		return r;
	}
}
