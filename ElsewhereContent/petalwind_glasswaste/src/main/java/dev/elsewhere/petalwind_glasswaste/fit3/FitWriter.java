package dev.elsewhere.petalwind_glasswaste.fit3;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The clipped writer of the 3.0 Petalwind/Glasswaste sites (STRUCTURES.md 2.13). At worldgen the clip is the piece box inside the chunk being
 * generated, so every write and every read stays in that chunk ("worldgen within +-1 chunk", strictly: within 0). Runtime rebuilds clip to the piece box.
 * Writes use UPDATE_CLIENTS only. Weathering variation comes from a stable hash of the position and the piece seed, never Math.random.
 */
public final class FitWriter {
	public final WorldGenLevel level;
	public final long seed;
	public final int x0, x1, y0, y1, z0, z1;
	private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

	public FitWriter(WorldGenLevel level, BoundingBox clip, long seed) {
		this.level = level;
		this.seed = seed;
		this.x0 = clip.minX();
		this.x1 = clip.maxX();
		this.y0 = clip.minY();
		this.y1 = clip.maxY();
		this.z0 = clip.minZ();
		this.z1 = clip.maxZ();
	}

	public boolean in(int x, int y, int z) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1 && y >= y0 && y <= y1;
	}

	public boolean col(int x, int z) {
		return x >= x0 && x <= x1 && z >= z0 && z <= z1;
	}

	public void set(int x, int y, int z, BlockState s) {
		if (!in(x, y, z)) return;
		level.setBlock(m.set(x, y, z), s, Block.UPDATE_CLIENTS);
	}

	public BlockState get(int x, int y, int z) {
		if (!in(x, y, z)) return Blocks.AIR.defaultBlockState();
		return level.getBlockState(m.set(x, y, z));
	}

	/** Air, fluids, plants, snow, leaves: what a foundation may replace and a soft clear removes. */
	public static boolean soft(BlockState s) {
		return s.isAir() || s.canBeReplaced() || !s.getFluidState().isEmpty() || s.is(BlockTags.LEAVES);
	}

	/** Natural solid ground (not soft). */
	public boolean solid(int x, int y, int z) {
		return !soft(get(x, y, z));
	}

	/** Stable 0..65535 hash of a position for this piece. */
	public int h(int x, int y, int z) {
		long v = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		v = (v ^ (v >>> 31)) * 0xBF58476D1CE4E5B9L;
		v = (v ^ (v >>> 29)) * 0x94D049BB133111EBL;
		return (int) ((v ^ (v >>> 32)) & 0xFFFF);
	}

	/** Top natural solid y in the column between {@code top} and {@code bottom} (inclusive), or {@code bottom - 1} when none. Reads only inside the clip. */
	public int surface(int x, int z, int top, int bottom) {
		for (int y = Math.min(top, y1); y >= Math.max(bottom, y0); y--) if (solid(x, y, z)) return y;
		return bottom - 1;
	}

	/**
	 * Foundation in a material ladder (STRUCTURES.md 2.9): fills the column under {@code floorY} down to real ground through air, fluid and
	 * replaceables only. Ladder: {@code plinth} for the first 2, {@code course} for the 3rd, {@code rubble} below. Capped at {@code cap}; a column with
	 * no ground within the cap gets nothing (the planner keeps footprints over ground), except pier columns, which are allowed {@code cap * 2}.
	 * Returns the depth filled, or -1 when no ground was found (nothing written).
	 */
	public int foundation(int x, int z, int floorY, BlockState plinth, BlockState course, BlockState rubble, int cap) {
		if (!col(x, z)) return 0;
		int d = 0;
		for (int y = floorY - 1; y >= floorY - cap && y >= y0; y--) {
			if (solid(x, y, z)) {
				d = floorY - 1 - y;
				for (int k = 1; k <= d; k++) set(x, floorY - k, z, k <= 2 ? plinth : k == 3 ? course : rubble);
				return d;
			}
		}
		return -1;
	}

	/** Removes natural blocks (and soft ones) in a column from {@code fromY} to {@code toY} inclusive: a local cut, never a box clear. */
	public void clear(int x, int z, int fromY, int toY) {
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int y = Math.max(fromY, y0); y <= Math.min(toY, y1); y++) if (!get(x, y, z).isAir()) set(x, y, z, air);
	}

	// ------------------------------------------------------------------ shape helpers (baked states: no neighbour updates at worldgen)
	public static BlockState stair(Block b, Direction facing, boolean top) {
		return b.defaultBlockState().setValue(StairBlock.FACING, facing).setValue(StairBlock.HALF, top ? Half.TOP : Half.BOTTOM);
	}

	public static BlockState slab(Block b, boolean top) {
		return b.defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
	}

	public static BlockState hangingLantern() {
		return Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
	}

	/** Direction from (dx, dz) toward the centre on the dominant axis (stairs face up-slope toward the centre). */
	public static Direction inward(int dx, int dz) {
		if (Math.abs(dx) >= Math.abs(dz)) return dx > 0 ? Direction.WEST : Direction.EAST;
		return dz > 0 ? Direction.NORTH : Direction.SOUTH;
	}
}
