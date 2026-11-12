package dev.elsewhere.palehollow.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * A world-trunk: a pale-oak column several blocks wide rising far above the forest, veined with glowing heartwood,
 * flaring into roots at the base and capped with a broad pale canopy dripping hanging moss and resin.
 */
public record WorldTrunkFeature(int minHeight, int maxHeight, int minRadius, int maxRadius) implements Feature {
	public static final MapCodec<WorldTrunkFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.INT.fieldOf("min_height").forGetter(WorldTrunkFeature::minHeight),
		Codec.INT.fieldOf("max_height").forGetter(WorldTrunkFeature::maxHeight),
		Codec.INT.fieldOf("min_radius").forGetter(WorldTrunkFeature::minRadius),
		Codec.INT.fieldOf("max_radius").forGetter(WorldTrunkFeature::maxRadius)
	).apply(i, WorldTrunkFeature::new));

	@Override
	public MapCodec<WorldTrunkFeature> codec() {
		return CODEC;
	}

	/** True if the trunk's base (column, flare and root buttresses) would cut through blocks of a generated site. */
	private static boolean overlapsSite(final WorldGenLevel level, final BlockPos origin, final int radius) {
		int rr = radius * 2 + 8;
		// worldgen may only read chunks within 1 of the chunk being decorated (vanilla logs "unsafe terrain read" otherwise)
		final net.minecraft.world.level.ChunkPos centre = level instanceof net.minecraft.server.level.WorldGenRegion region ? region.getCenter() : null;
		for (int dx = -rr; dx <= rr; dx++) {
			for (int dz = -rr; dz <= rr; dz++) {
				if (dx * dx + dz * dz > rr * rr) {
					continue;
				}
				for (int dy = -4; dy <= 7; dy += 1) {
					BlockPos p = origin.offset(dx, dy, dz);
					// read-only probe: hasChunkAt is a silent bounds check (ensureCanWrite logs a "far chunk" ERROR per call)
					if (centre != null ? Math.abs((p.getX() >> 4) - centre.x()) > 1 || Math.abs((p.getZ() >> 4) - centre.z()) > 1 : !level.hasChunkAt(p)) {
						continue;
					}
					BlockState st = level.getBlockState(p);
					if (st.is(Blocks.CHEST) || st.is(ModBlocks.RETURN_RIFT) || st.is(Blocks.STONE_BRICKS) || st.is(Blocks.MOSSY_STONE_BRICKS)
						|| st.is(Blocks.PALE_OAK_PLANKS) || st.is(Blocks.CHISELED_STONE_BRICKS) || st.is(Blocks.TUFF_BRICKS) || st.is(Blocks.CHISELED_TUFF_BRICKS)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean place(final WorldGenLevel level, final ChunkGenerator chunkGenerator, final RandomSource random, final BlockPos origin) {
		int height = this.minHeight + random.nextInt(Math.max(1, this.maxHeight - this.minHeight + 1));
		int radius = this.minRadius + random.nextInt(Math.max(1, this.maxRadius - this.minRadius + 1));
		int top = Math.min(origin.getY() + height, level.getMaxY() - 12);
		if (top - origin.getY() < 24) {
			return false;
		}
		if (overlapsSite(level, origin, radius)) {
			return false; // never swallow a Hollow Shrine, Drowned Chapel, Lantern Road or Return Rift ruin
		}
		BlockState wood = Blocks.PALE_OAK_WOOD.defaultBlockState();
		BlockState vein = ModBlocks.HEARTWOOD_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		double veinAngle = random.nextDouble() * Math.PI * 2.0;
		for (int y = origin.getY() - 4; y <= top; y++) {
			double flare = y < origin.getY() + 6 ? (origin.getY() + 6 - y) * 0.45 : 0.0;
			double r = radius + flare - Math.max(0, y - top + 10) * 0.25;
			int ri = (int)Math.ceil(r);
			double va = veinAngle + (y - origin.getY()) * 0.045;
			for (int dx = -ri; dx <= ri; dx++) {
				for (int dz = -ri; dz <= ri; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > r) {
						continue;
					}
					BlockPos p = new BlockPos(origin.getX() + dx, y, origin.getZ() + dz);
					boolean shell = d > r - 1.5;
					double ang = Math.atan2(dz, dx);
					boolean isVein = shell && Math.abs(Math.sin((ang - va) * 1.5)) < 0.12;
					level.setBlock(p, isVein ? vein : wood, 2);
				}
			}
		}
		// root buttresses
		for (int k = 0; k < 6; k++) {
			double a = k * Math.PI / 3.0 + random.nextDouble() * 0.4;
			for (int s = 0; s < radius + 6; s++) {
				int x = origin.getX() + (int)Math.round(Math.cos(a) * (radius + s * 0.8));
				int z = origin.getZ() + (int)Math.round(Math.sin(a) * (radius + s * 0.8));
				int y = origin.getY() + 2 - s / 3;
				for (int dy = -2; dy <= 0; dy++) {
					level.setBlock(new BlockPos(x, y + dy, z), wood, 2);
				}
			}
		}
		// canopy
		int cr = radius + 9 + random.nextInt(3);
		BlockState leaves = Blocks.PALE_OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dx = -cr; dx <= cr; dx++) {
			for (int dz = -cr; dz <= cr; dz++) {
				for (int dy = -4; dy <= 5; dy++) {
					double e = (dx * dx + dz * dz) / (double)(cr * cr) + (dy * dy) / 20.0;
					if (e > 1.0 || (e > 0.8 && random.nextInt(3) == 0)) {
						continue;
					}
					BlockPos p = new BlockPos(origin.getX() + dx, top + dy, origin.getZ() + dz);
					if (level.getBlockState(p).isAir()) {
						level.setBlock(p, random.nextInt(60) == 0 ? Blocks.RESIN_BLOCK.defaultBlockState() : leaves, 2);
					}
				}
			}
		}
		// hanging moss under the canopy
		for (int i = 0; i < cr * 6; i++) {
			int x = origin.getX() + random.nextInt(cr * 2 + 1) - cr;
			int z = origin.getZ() + random.nextInt(cr * 2 + 1) - cr;
			BlockPos p = new BlockPos(x, top - 1, z);
			for (int dy = 0; dy < 6 && level.getBlockState(p).isAir(); dy++) {
				p = p.above();
			}
			p = p.below();
			int len = 1 + random.nextInt(4);
			for (int j = 0; j < len; j++) {
				BlockPos q = p.below(j);
				if (!level.getBlockState(q).isAir()) {
					break;
				}
				level.setBlock(q, Blocks.PALE_HANGING_MOSS.defaultBlockState(), 2);
			}
		}
		return true;
	}
}
