package dev.elsewhere.core.riftruin;

import com.mojang.serialization.MapCodec;
import dev.elsewhere.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Builds the Return Rift ruin in the one chunk of each 600x600 cell that holds the cell's anchor.
 * Add {@code "elsewhere:return_rift"} (placed feature with no placement modifiers) to every biome of a dimension,
 * in the same step and order, and register the dimension with {@code returnRifts = true}.
 */
public record ReturnRiftFeature() implements Feature {
	public static final MapCodec<ReturnRiftFeature> CODEC = MapCodec.unit(ReturnRiftFeature::new);

	@Override
	public MapCodec<ReturnRiftFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		ResourceKey<Level> dim = level.getLevel().dimension();
		if (!ReturnRifts.enabledIn(dim)) {
			return false;
		}
		int chunkX = origin.getX() >> 4;
		int chunkZ = origin.getZ() >> 4;
		BlockPos anchor = ReturnRifts.anchor(level.getSeed(), dim, Mth.floorDiv(origin.getX(), ReturnRifts.CELL), Mth.floorDiv(origin.getZ(), ReturnRifts.CELL));
		if (anchor.getX() >> 4 != chunkX || anchor.getZ() >> 4 != chunkZ) {
			return false;
		}
		int y = Math.max(level.getSeaLevel() + 1, level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, anchor.getX(), anchor.getZ()));
		build(level, new BlockPos(anchor.getX(), y, anchor.getZ()), random);
		return true;
	}

	/**
	 * 3.0: a 5x5 dais and a ring of standing stones, each on its own ground ({@code dev.elsewhere.palehollow.fit3.RiftRing}); with the core_fit
	 * kill switch off, the 2.x look below.
	 */
	public static void build(final WorldGenLevel level, final BlockPos c, final RandomSource random) {
		if (dev.elsewhere.palehollow.fit3.CoreFit.ENABLED) {
			dev.elsewhere.palehollow.fit3.RiftRing.build(level, c, random);
			return;
		}
		legacy(level, c, random);
	}

	/** A 9x9 broken tuff-brick dais, four leaning pillars, and the rift standing in the middle (2.x). */
	public static void legacy(final WorldGenLevel level, final BlockPos c, final RandomSource random) {
		BlockState bricks = Blocks.TUFF_BRICKS.defaultBlockState();
		BlockState polished = Blocks.POLISHED_TUFF.defaultBlockState();
		BlockState chiseled = Blocks.CHISELED_TUFF_BRICKS.defaultBlockState();
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 4; dz++) {
				int d = Math.max(Math.abs(dx), Math.abs(dz));
				if (d == 4 && random.nextInt(3) == 0) {
					continue;
				}
				BlockPos p = c.offset(dx, -1, dz);
				level.setBlock(p, d <= 1 ? chiseled : (random.nextInt(5) == 0 ? polished : bricks), 2);
				for (int dy = 2; dy <= 5; dy++) {
					if (level.getBlockState(p.below(dy - 1)).isAir() || !level.getBlockState(p.below(dy - 1)).getFluidState().isEmpty()) {
						level.setBlock(p.below(dy - 1), bricks, 2);
					}
				}
				for (int dy = 0; dy < 5; dy++) {
					level.setBlock(c.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
		for (int[] k : corners) {
			int h = 2 + random.nextInt(3);
			for (int dy = 0; dy < h; dy++) {
				level.setBlock(c.offset(k[0], dy, k[1]), dy == h - 1 ? chiseled : bricks, 2);
			}
		}
		level.setBlock(c, ModBlocks.RETURN_RIFT.defaultBlockState(), 2);
		level.setBlock(c.above(), ModBlocks.RETURN_RIFT.defaultBlockState(), 2);
	}
}
