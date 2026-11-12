package dev.elsewhere.nether_end_bosses.fit3;

import dev.elsewhere.nether_end_bosses.Build;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jspecify.annotations.Nullable;

/**
 * 3.0 arena writer (STRUCTURES.md 2.13, "FitWriter"). Every read and write is clipped to one box: the chunk being generated
 * for a structure piece (so a v2 arena is written chunk by chunk, never from its centre), or nothing for the dev command and
 * fight rebuilds on a loaded level. Variation is a stable hash of position and piece seed (no RandomSource order, no map order).
 * Fences, bars, walls and stairs are marked for post-processing in world generation (they connect like vanilla pieces) and
 * re-shaped once at {@link #finish()} on a live level.
 */
public final class FitW {
	public final WorldGenLevel level;
	private final @Nullable BoundingBox clip;
	private final long seed;
	private final boolean worldgen;
	private final List<BlockPos> shaped = new ArrayList<>();
	private final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
	/** Self-test hooks: writes made, writes dropped by the clip, and writes outside {@link #watch} (should stay 0). */
	public int writes, clipped, outsideWatch;
	public @Nullable BoundingBox watch;

	public FitW(WorldGenLevel level, @Nullable BoundingBox clip, long seed) {
		this.level = level;
		this.clip = clip;
		this.seed = seed;
		this.worldgen = level instanceof WorldGenRegion;
	}

	public boolean in(int x, int y, int z) {
		return clip == null || clip.isInside(x, y, z);
	}

	public boolean inColumn(int x, int z) {
		return clip == null || (x >= clip.minX() && x <= clip.maxX() && z >= clip.minZ() && z <= clip.maxZ());
	}

	public void set(int x, int y, int z, BlockState s) {
		if (watch != null && !watch.isInside(x, y, z)) outsideWatch++;
		if (!in(x, y, z) || y <= level.getMinY() || y >= level.getMaxY()) {
			clipped++;
			return;
		}
		writes++;
		p.set(x, y, z);
		level.setBlock(p, s, Build.FLAGS);
		Block b = s.getBlock();
		if (b instanceof CrossCollisionBlock || b instanceof WallBlock || b instanceof StairBlock) {
			if (worldgen) level.getChunk(p).markPosForPostProcessing(p);
			else shaped.add(p.immutable());
		}
	}

	public void set(BlockPos at, BlockState s) {
		set(at.getX(), at.getY(), at.getZ(), s);
	}

	/** Reads one block, only inside the clip (outside it answers air, and nothing outside is ever touched). */
	public BlockState get(int x, int y, int z) {
		if (!in(x, y, z)) return Blocks.AIR.defaultBlockState();
		return level.getBlockState(p.set(x, y, z));
	}

	/** Natural ground for footings: not air, not fluid, not replaceable (plants, fire, snow). */
	public static boolean ground(BlockState st) {
		return !st.isAir() && st.getFluidState().isEmpty() && !st.canBeReplaced();
	}

	/** Writes only where the world is open (air, fluid or replaceable): roofs and façades that must not cut natural rock. */
	public void soft(int x, int y, int z, BlockState s) {
		if (!in(x, y, z)) {
			clipped++;
			return;
		}
		if (!ground(get(x, y, z))) set(x, y, z, s);
	}

	public void box(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
		for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
			for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++)
				for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) set(x, y, z, s);
	}

	public void disc(BlockPos c, int y, double r, BlockState s) {
		int ir = (int) Math.ceil(r);
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) if (dx * dx + dz * dz <= r * r) set(c.getX() + dx, y, c.getZ() + dz, s);
	}

	public void ring(BlockPos c, int y, double r0, double r1, BlockState s) {
		int ir = (int) Math.ceil(r1);
		for (int dz = -ir; dz <= ir; dz++) for (int dx = -ir; dx <= ir; dx++) {
			double d = dx * dx + dz * dz;
			if (d >= r0 * r0 && d <= r1 * r1) set(c.getX() + dx, y, c.getZ() + dz, s);
		}
	}

	/** Stable 0..99 per position (mossy, cracked, heights); identical in every chunk and on every thread. */
	public int hash(int x, int y, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (int) Math.floorMod(h, 100L);
	}

	/**
	 * A footing below a floor (STRUCTURES.md 2.9): fills air, fluid and replaceables down from {@code topY} with the material
	 * ladder (plinth 2, course 1, then rubble) until natural ground or {@code cap} blocks. Reads only this column.
	 * @return blocks filled, or -1 when the cap was reached without ground
	 */
	public int footing(int x, int z, int topY, Ladder ladder, int cap) {
		if (!inColumn(x, z)) return 0;
		int n = 0;
		for (int y = topY; y > level.getMinY() + 1; y--) {
			if (ground(get(x, y, z))) return n;
			if (n >= cap) return -1;
			set(x, y, z, ladder.at(n, hash(x, y, z)));
			n++;
		}
		return n;
	}

	/** A footing only when natural ground is within {@code max} below {@code topY} (otherwise nothing is written). */
	public int footingIfNear(int x, int z, int topY, Ladder ladder, int max) {
		if (!inColumn(x, z)) return 0;
		for (int k = 0; k <= max; k++) {
			if (ground(get(x, topY - k, z))) return k == 0 ? 0 : footing(x, z, topY, ladder, k);
		}
		return -1;
	}

	/**
	 * Solid footing to {@code solidCap}; past it only the pier lattice (2x2 posts every 6 blocks, local to the arena centre)
	 * carries on down to the ground (up to {@code pierCap}), so a deep gap gets posts and not a solid pillar.
	 * @return true when this column is planned to reach the ground (solid footing or a pier)
	 */
	public boolean footingOrPier(int x, int z, int topY, Ladder ladder, int solidCap, int pierCap, BlockPos centre) {
		int r = footing(x, z, topY, ladder, solidCap);
		if (r >= 0 || !isPier(x - centre.getX(), z - centre.getZ())) return r >= 0;
		return footing(x, z, topY - solidCap, ladder.pier(), pierCap - solidCap) >= 0;
	}

	/** The pier lattice: 2x2 posts every 6 blocks, symmetric about the arena centre. */
	public static boolean isPier(int lx, int lz) {
		return Math.floorMod(lx + 1, 6) < 2 && Math.floorMod(lz + 1, 6) < 2;
	}

	/** Re-shapes fences, bars, walls and stairs on a live level (world generation marks them for post-processing instead). */
	public void finish() {
		for (BlockPos at : shaped) {
			BlockState st = level.getBlockState(at);
			BlockState fixed = Block.updateFromNeighbourShapes(st, level, at);
			if (fixed != st) level.setBlock(at, fixed, Build.FLAGS);
		}
		shaped.clear();
	}

	/** Foundation material ladder of one palette: plinth (2), course (1), rubble (varied), and the pier block. */
	public record Ladder(BlockState plinth, BlockState course, BlockState rubble, BlockState rubbleAlt, BlockState pierBlock) {
		public BlockState at(int depth, int hash) {
			if (depth < 2) return plinth;
			if (depth == 2) return course;
			return hash < 30 ? rubbleAlt : rubble;
		}

		Ladder pier() {
			return new Ladder(pierBlock, pierBlock, pierBlock, pierBlock, pierBlock);
		}
	}
}
