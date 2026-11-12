package dev.elsewhere.verdigris_ironpeak;

import com.mojang.serialization.MapCodec;
import dev.elsewhere.verdigris_sea.VerdigrisBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/** 2.0: a giant kelp stalk of Kelpwood (a log column rooted on the sea floor, a tuft of kelp on top): the Kelpwood Reaches' survival source of Kelpwood. */
public record KelpwoodFeature() implements Feature {
	public static final MapCodec<KelpwoodFeature> CODEC = MapCodec.unit(new KelpwoodFeature());

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Reg.id("kelpwood_stalk"), CODEC);
	}

	@Override
	public MapCodec<KelpwoodFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (!level.getFluidState(origin).is(FluidTags.WATER) || !level.getBlockState(origin.below()).isSolidRender()) return false;
		int h = 6 + random.nextInt(8);
		for (int i = 0; i < h; i++) if (!level.getBlockState(origin.above(i)).is(Blocks.WATER)) {
			h = i;
			break;
		}
		if (h < 4) return false;
		BlockState log = VerdigrisBlocks.KELPWOOD_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		for (int i = 0; i < h - 1; i++) level.setBlock(origin.above(i), log, Block.UPDATE_CLIENTS);
		level.setBlock(origin.above(h - 1), Blocks.KELP.defaultBlockState(), Block.UPDATE_CLIENTS);
		return true;
	}
}
