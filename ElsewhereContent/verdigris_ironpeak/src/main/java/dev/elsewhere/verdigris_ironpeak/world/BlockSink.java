package dev.elsewhere.verdigris_ironpeak.world;

import dev.elsewhere.verdigris_ironpeak.world.fit.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.loot.LootTable;
import org.jspecify.annotations.Nullable;

/** Where a {@link Blueprint} writes: relative coordinates, optional clip box (one chunk during world generation). */
public final class BlockSink {
	public final WorldGenLevel level;
	public final BlockPos origin;
	private final @Nullable BoundingBox clip;
	public final RandomSource random;
	/** 3.0 fit pieces: the ground model saved with the piece (null for 2.x pieces and the legacy blueprints). */
	public final @Nullable Terrain terrain;

	public BlockSink(WorldGenLevel level, BlockPos origin, @Nullable BoundingBox clip, RandomSource random) {
		this(level, origin, clip, random, null);
	}

	public BlockSink(WorldGenLevel level, BlockPos origin, @Nullable BoundingBox clip, RandomSource random, @Nullable Terrain terrain) {
		this.level = level;
		this.origin = origin;
		this.clip = clip;
		this.random = random;
		this.terrain = terrain;
	}

	public BlockPos at(int dx, int dy, int dz) {
		return origin.offset(dx, dy, dz);
	}

	public boolean inside(BlockPos p) {
		return (clip == null || clip.isInside(p)) && !level.isOutsideBuildHeight(p);
	}

	public void set(int dx, int dy, int dz, BlockState state) {
		BlockPos p = at(dx, dy, dz);
		if (inside(p)) level.setBlock(p, state, Block.UPDATE_CLIENTS);
	}

	public void set(int dx, int dy, int dz, Block block) {
		set(dx, dy, dz, block.defaultBlockState());
	}

	/** Only replaces air, water and plants (keeps terrain). */
	public void soft(int dx, int dy, int dz, BlockState state) {
		BlockPos p = at(dx, dy, dz);
		if (!inside(p)) return;
		BlockState old = level.getBlockState(p);
		if (old.isAir() || old.canBeReplaced() || old.is(Blocks.WATER)) level.setBlock(p, state, Block.UPDATE_CLIENTS);
	}

	/** The block there, or air when it lies outside the clip box (never reads chunks we may not touch). */
	public BlockState get(int dx, int dy, int dz) {
		BlockPos p = at(dx, dy, dz);
		return inside(p) ? level.getBlockState(p) : Blocks.AIR.defaultBlockState();
	}

	/** True if the block there is terrain (not air, water or replaceable). False outside the clip box. */
	public boolean solid(int dx, int dy, int dz) {
		BlockPos p = at(dx, dy, dz);
		if (!inside(p)) return false;
		BlockState old = level.getBlockState(p);
		return !old.isAir() && !old.canBeReplaced() && !old.is(Blocks.WATER);
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
		for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
			for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++)
				for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) set(x, y, z, state);
	}

	public void fill(int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		fill(x0, y0, z0, x1, y1, z1, block.defaultBlockState());
	}

	/** Disc of radius r at height dy (inclusive). */
	public void disc(int dy, double r, BlockState state) {
		int ri = (int) Math.ceil(r);
		for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) if (x * x + z * z <= r * r) set(x, dy, z, state);
	}

	public void ring(int dy, double r0, double r1, BlockState state) {
		int ri = (int) Math.ceil(r1);
		for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
			double d = x * x + z * z;
			if (d <= r1 * r1 && d >= r0 * r0) set(x, dy, z, state);
		}
	}

	/** Solid pillar from dy down until it meets terrain (max 64 blocks). */
	public void foundation(int dx, int dy, int dz, BlockState state) {
		for (int y = dy; y > dy - 128; y--) {
			BlockPos p = at(dx, y, dz);
			if (!inside(p)) return;
			if (y < dy && solid(dx, y, dz)) return;
			level.setBlock(p, state, Block.UPDATE_CLIENTS);
		}
	}

	public void chest(int dx, int dy, int dz, Direction facing, ResourceKey<LootTable> loot) {
		BlockPos p = at(dx, dy, dz);
		if (!inside(p)) return;
		level.setBlock(p, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), Block.UPDATE_CLIENTS);
		RandomizableContainer.setBlockEntityLootTable(level, random, p, loot);
	}

	/** A vanilla vault opened by {@code key} (once per player), paying out {@code loot}. */
	public void vault(int dx, int dy, int dz, Direction facing, net.minecraft.world.item.Item key, ResourceKey<LootTable> loot) {
		BlockPos p = at(dx, dy, dz);
		if (!inside(p)) return;
		level.setBlock(p, Blocks.VAULT.defaultBlockState().setValue(net.minecraft.world.level.block.VaultBlock.FACING, facing), Block.UPDATE_CLIENTS);
		if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.vault.VaultBlockEntity v) {
			v.setConfig(new net.minecraft.world.level.block.entity.vault.VaultConfig(loot, 4.0, 4.5, new net.minecraft.world.item.ItemStack(key), java.util.Optional.empty()));
		}
	}

	public int surface(int dx, int dz) {
		BlockPos p = at(dx, 0, dz);
		return level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, p.getX(), p.getZ()) - origin.getY();
	}

	// ---- 3.0 terrain fit (STRUCTURES.md 2.6, 2.9): per-column ground from the saved ground model, matching-block foundations ----

	/** Top ground block at relative (dx, dz), relative to the origin's y (from the piece's ground model, never the live world). */
	public int ground(int dx, int dz) {
		if (terrain == null) return surface(dx, dz) - 1;
		return terrain.ground(origin.getX() + dx, origin.getZ() + dz) - origin.getY();
	}

	/** Top water block (the ground on dry land), relative to the origin's y. */
	public int water(int dx, int dz) {
		if (terrain == null) return ground(dx, dz);
		return terrain.water(origin.getX() + dx, origin.getZ() + dz) - origin.getY();
	}

	public boolean wet(int dx, int dz) {
		return water(dx, dz) > ground(dx, dz);
	}

	/** Natural ground a foundation may rest on: not air, fluid, replaceable, leaves, logs or another structure's woodwork (planks, stairs...). */
	public static boolean bearing(BlockState s) {
		return !s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(net.minecraft.tags.BlockTags.LEAVES) && !s.is(net.minecraft.tags.BlockTags.LOGS)
			&& !s.is(net.minecraft.tags.BlockTags.PLANKS) && !s.is(net.minecraft.tags.BlockTags.WOODEN_STAIRS) && !s.is(net.minecraft.tags.BlockTags.WOODEN_SLABS)
			&& !s.is(net.minecraft.tags.BlockTags.WOODEN_FENCES) && !s.is(net.minecraft.tags.BlockTags.WOODEN_TRAPDOORS);
	}

	/**
	 * Foundation in matching blocks (STRUCTURES.md 2.9): from {@code dy} down through air, fluid, plants, leaves and logs until it meets
	 * bearing ground, as a ladder: {@code plinth} for 2 blocks, {@code course} for 1, then {@code rubble}. Capped at {@code cap}; past the cap
	 * only every 4th column continues (a pier, never a solid block), down to 64. Writes only inside this chunk.
	 * @return blocks placed in this column, or -1 when the drop is deeper than the cap off the pier grid (nothing placed)
	 */
	public int underpin(int dx, int dy, int dz, BlockState plinth, BlockState course, BlockState rubble, int cap) {
		if (!inside(at(dx, dy, dz))) return 0;
		// measure the drop to bearing ground first: a column deeper than the cap is filled only on the pier grid, never left hanging
		int depth = 64;
		for (int k = 0; k < 64; k++) {
			BlockPos p = at(dx, dy - k, dz);
			if (!inside(p) || bearing(level.getBlockState(p))) {
				depth = k;
				break;
			}
		}
		if (depth > cap && (Math.floorMod(dx, 4) != 0 || Math.floorMod(dz, 4) != 0)) return -1;
		for (int k = 0; k < depth; k++) level.setBlock(at(dx, dy - k, dz), k < 2 ? plinth : k == 2 ? course : rubble, Block.UPDATE_CLIENTS);
		return depth;
	}

	/** Air only where something stands (a room interior); never touches blocks outside this chunk. Underwater it leaves water. */
	public void hollow(int dx, int dy, int dz) {
		BlockPos p = at(dx, dy, dz);
		if (!inside(p)) return;
		BlockState old = level.getBlockState(p);
		if (old.isAir()) return;
		level.setBlock(p, dy <= water(dx, dz) && wet(dx, dz) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	/**
	 * A soft bank instead of a box cut: removes natural blocks in this column above relative height {@code top} (up to {@code limit} above it)
	 * and re-tops the column with the surface block it had (grass, snow, sand...). Water is never drained: cut blocks under the water line
	 * become water.
	 */
	public void trim(int dx, int dz, int top, int limit) {
		BlockState surface = null;
		int waterTop = water(dx, dz);
		boolean wetCol = wet(dx, dz);
		for (int y = top + limit; y > top; y--) {
			BlockPos p = at(dx, y, dz);
			if (!inside(p)) return;
			BlockState s = level.getBlockState(p);
			if (s.isAir() || !s.getFluidState().isEmpty()) continue;
			if (surface == null && !s.canBeReplaced()) surface = s;
			level.setBlock(p, wetCol && y <= waterTop ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		BlockPos t = at(dx, top, dz);
		if (surface != null && inside(t) && bearing(level.getBlockState(t)) && topsoil(surface)) level.setBlock(t, surface, Block.UPDATE_CLIENTS);
	}

	static boolean topsoil(BlockState s) {
		return s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.SAND) || s.is(Blocks.GRAVEL) || s.is(Blocks.COARSE_DIRT)
			|| s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.CLAY);
	}

	/** Earth berm on dry ground: raises this column with its own soil up to relative height {@code top} (fills air and plants only). */
	public void berm(int dx, int dz, int top) {
		if (wet(dx, dz)) return;
		int g = ground(dx, dz);
		// the model is interpolated: start from the real top of this column (read inside this chunk only)
		// (below the berm's own top: an eave or wall over the column is not the ground)
		for (int y = Math.min(g + 4, top - 1); y >= g - 8; y--) {
			BlockPos q = at(dx, y, dz);
			if (inside(q) && bearing(level.getBlockState(q))) {
				g = y;
				break;
			}
		}
		BlockPos gp = at(dx, g, dz);
		BlockState soil = inside(gp) && bearing(level.getBlockState(gp)) ? level.getBlockState(gp) : Blocks.DIRT.defaultBlockState();
		BlockState fill = soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.PODZOL) || soil.is(Blocks.MOSS_BLOCK) ? Blocks.DIRT.defaultBlockState() : soil;
		for (int y = g + 1; y <= top; y++) {
			BlockPos p = at(dx, y, dz);
			if (!inside(p)) return;
			BlockState old = level.getBlockState(p);
			if (!old.isAir() && !old.canBeReplaced()) continue;
			level.setBlock(p, y == top ? soil : fill, Block.UPDATE_CLIENTS);
		}
		BlockPos below = at(dx, top - 1, dz);
		if (top > g + 1 && inside(below) && level.getBlockState(below).is(Blocks.GRASS_BLOCK)) level.setBlock(below, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
	}

	public static ResourceKey<LootTable> loot(String path) {
		return ResourceKey.create(Registries.LOOT_TABLE, dev.elsewhere.verdigris_ironpeak.Reg.id(path));
	}
}
