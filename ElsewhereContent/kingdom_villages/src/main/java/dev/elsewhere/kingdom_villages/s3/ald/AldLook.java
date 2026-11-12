package dev.elsewhere.kingdom_villages.s3.ald;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * 3.0 Aldermark village looks (STRUCTURES.md 1.3, 2.6-2.9, 3.1 and section 4 "kingdom_villages"): kill switches plus the shared
 * terrain helpers the runtime builders use. Runtime builds run on loaded chunks, so the ground is read from the live heightmap once
 * per column at plan time; every piece stands on its own column's ground (no planes, no box clears), foundations go down in the
 * Aldermark material ladder (stone bricks, cobblestone, andesite) and are capped at {@value #FOUNDATION_CAP}.
 *
 * <p>Kill switches (BUILD-3.0), each default on, off with {@code -Delsewhere.overhaul3.<key>=false}: {@code vlook_aldermark_rebuild}
 * (the fortified rebuild of an Aldermark-held village and Aldermark's cosmetic swap signature), {@code vlook_aldermark_fort}
 * (the motte-and-bailey outpost and its pad-only site test) and {@code vlook_hamlet} (the per-column burnt farmstead). Off = the
 * 2.1 plans in {@code Plans}, byte for byte.
 */
public final class AldLook {
	private AldLook() {}

	/** {@code vlook_aldermark_rebuild}: high-point spired watchtower, contour log palisade with timber gatehouses, deepslate roofs, camp. */
	public static final boolean REBUILD = on("vlook_aldermark_rebuild");
	/** {@code vlook_aldermark_fort}: the motte-and-bailey outpost on the highest point, ring stepping with the slope. */
	public static final boolean FORT = on("vlook_aldermark_fort");
	/** {@code vlook_hamlet}: burnt farmsteads placed per column (every faction's outposts use the same refugee hamlet). */
	public static final boolean HAMLET = on("vlook_hamlet");

	public static final int FOUNDATION_CAP = 12;

	public static boolean on(String key) {
		return !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3." + key, "true"));
	}

	// ---- ground ------------------------------------------------------------------------------------------------------

	/** y of the top block of a column that blocks motion (leaves ignored: trunks, ground or water), or {@link Integer#MIN_VALUE} when unloaded. */
	public static int ground(ServerLevel level, int x, int z) {
		if (!level.isLoaded(new BlockPos(x, level.getMinY(), z))) return Integer.MIN_VALUE;
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
	}

	/** Natural ground a runtime piece may stand on (never paths, fields, logs, water or anything built). */
	public static boolean natural(BlockState s) {
		return s.is(Blocks.GRASS_BLOCK) || s.is(BlockTags.DIRT) && !s.is(Blocks.MUD) || s.is(Blocks.SAND) || s.is(Blocks.RED_SAND) || s.is(Blocks.GRAVEL)
			|| s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.STONE) || s.is(Blocks.ANDESITE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.CLAY);
	}

	/** Air, or a soft block (grass, flowers, snow layer) the writer may replace; never a fluid. */
	public static boolean soft(BlockState s) {
		return s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty();
	}

	/** True when the column at x/z is clear (air or soft) from {@code y0} to {@code y1}. */
	public static boolean clear(ServerLevel level, int x, int y0, int y1, int z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int y = y0; y <= y1; y++) if (!soft(level.getBlockState(m.set(x, y, z)))) return false;
		return true;
	}

	/** True when x/z is natural ground with a clear column above (the usual "may build here" test of a runtime piece). */
	public static boolean buildable(ServerLevel level, int x, int z, int height) {
		int g = ground(level, x, z);
		if (g == Integer.MIN_VALUE) return false;
		BlockPos gp = new BlockPos(x, g, z);
		BlockState gs = level.getBlockState(gp);
		return natural(gs) && gs.isFaceSturdy(level, gp, Direction.UP) && clear(level, x, g + 1, g + height, z);
	}

	/** A door within one block of the column (at the two heights above {@code g}): a palisade or post there would block it. */
	public static boolean nearDoor(ServerLevel level, int x, int g, int z) {
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
			if (!level.isLoaded(m.set(x + dx, g, z + dz))) return true; // never load a neighbour chunk at plan time; unknown counts as a door (s-review-kingdom)
			for (int dy = 0; dy <= 2; dy++) if (level.getBlockState(m.set(x + dx, g + dy, z + dz)).is(BlockTags.DOORS)) return true;
		}
		return false;
	}

	/** The other half of a double plant (tall grass, large fern, tall flowers) a piece replaces must go too: writes send no shape updates. */
	public static void clearPlantTop(ServerLevel level, AldOps ops, int x, int y, int z) {
		BlockPos p = new BlockPos(x, y, z);
		BlockState s = level.getBlockState(p);
		if (!(s.getBlock() instanceof DoublePlantBlock)) return;
		ops.set(s.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER ? p.above() : p.below(), Blocks.AIR.defaultBlockState());
	}

	// ---- materials ---------------------------------------------------------------------------------------------------

	/** Deterministic per-block hash in 0..99 (weathering must not depend on chunk order or the random source). */
	public static int hash(int x, int y, int z) {
		long h = x * 3129871L ^ z * 116129781L ^ y * 0x5DEECE66DL;
		h = h * h * 42317861L + h * 11L;
		return (int) Math.floorMod(h >> 16, 100L);
	}

	/** Stone bricks with hashed weathering (about 12% mossy, 6% cracked): STRUCTURES.md 7 rule 4. */
	public static BlockState bricks(int x, int y, int z) {
		int h = hash(x, y, z);
		return (h < 12 ? Blocks.MOSSY_STONE_BRICKS : h < 18 ? Blocks.CRACKED_STONE_BRICKS : Blocks.STONE_BRICKS).defaultBlockState();
	}

	/** The Aldermark foundation ladder (STRUCTURES.md 2.9): 2 plinth courses of stone bricks, 2 of cobblestone, then andesite. */
	public static BlockState ladder(int depth, int x, int y, int z) {
		if (depth < 2) return bricks(x, y, z);
		if (depth < 4) return (hash(x, y, z) < 20 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE).defaultBlockState();
		return Blocks.ANDESITE.defaultBlockState();
	}

	/** Underpins column x/z from {@code top} (inclusive, the course right under the floor) down to just above its ground. */
	public static void underpin(ServerLevel level, AldOps ops, int x, int top, int z) {
		int g = ground(level, x, z);
		if (g == Integer.MIN_VALUE) return;
		for (int y = top, d = 0; y > g && d < FOUNDATION_CAP; y--, d++) ops.set(new BlockPos(x, y, z), ladder(d, x, y, z));
	}

	public static BlockState stair(Block b, Direction facing, Half half) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, half);
	}

	/** Standing-banner rotation (0..15) that faces from {@code from} toward {@code to}. */
	public static int rotationToward(BlockPos from, BlockPos to) {
		return Math.floorMod(Math.round((float) Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ()))) / 22.5F) + 8, 16);
	}
}
