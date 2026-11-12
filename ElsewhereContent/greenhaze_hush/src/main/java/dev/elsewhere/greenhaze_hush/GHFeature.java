package dev.elsewhere.greenhaze_hush;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.elsewhere.greenhaze.GreenhazeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * 2.0 survival sources for the Greenhaze plants, placed by one feature type; the JSON picks the {@code kind}:
 * {@code giant_fern} (a 3-5 block fern on dirt or moss), {@code haze_vent} (a cracked-earth vent in the ground), {@code lucid_lily}
 * (on still water), {@code munchfruit} (a ripe bush on grass or dirt), {@code kush_tree} (a small kushwood tree).
 */
public record GHFeature(String kind) implements Feature {
	public static String why = "";
	public static final MapCodec<GHFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
		Codec.STRING.fieldOf("kind").forGetter(GHFeature::kind)).apply(i, GHFeature::new));

	public static void init() {
		Registry.register(BuiltInRegistries.FEATURE_TYPE, Reg.id("greenhaze_plant"), CODEC);
	}

	@Override
	public MapCodec<GHFeature> codec() {
		return CODEC;
	}

	@Override
	public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
		if (kind.equals("giant_fern") || kind.equals("munchfruit") || kind.equals("kush_tree")) {
			origin = ground(level, origin); // the surface heightmap sits on top of the jungle canopy: look for the forest floor
			if (origin == null) { why = "no ground"; return false; }
		}
		return switch (kind) {
			case "giant_fern" -> fern(level, random, origin);
			case "haze_vent" -> vent(level, origin);
			case "lucid_lily" -> lily(level, origin);
			case "munchfruit" -> bush(level, origin);
			case "kush_tree" -> tree(level, random, origin);
			default -> false;
		};
	}

	private static boolean soil(BlockState s) {
		return s.is(BlockTags.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.PODZOL) || s.is(Blocks.MOSS_BLOCK) || s.is(Blocks.MUD);
	}

	private static boolean free(BlockState s) {
		return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || s.is(Blocks.VINE);
	}

	/** The first free spot above dirt, searching down from {@code from} through canopy and air; null if none within 40 blocks. */
	private static BlockPos ground(WorldGenLevel level, BlockPos from) {
		for (int dy = 0; dy < 40; dy++) {
			BlockPos p = from.below(dy);
			if (soil(level.getBlockState(p.below())) && free(level.getBlockState(p)) && !level.getBlockState(p).is(BlockTags.LEAVES)) return p;
		}
		return null;
	}

	private static boolean fern(WorldGenLevel level, RandomSource random, BlockPos p) {
		BlockState below = level.getBlockState(p.below());
		why = "fern ground " + below.getBlock().getDescriptionId() + " here " + level.getBlockState(p).getBlock().getDescriptionId();
		if (!(soil(below) || below.is(Blocks.MUD) || below.is(Blocks.MOSS_BLOCK)) || !free(level.getBlockState(p))) return false;
		int h = 4; // the block's part chain (0,1,2,3) is only valid at height 4
		for (int i = 0; i < h; i++) if (!free(level.getBlockState(p.above(i)))) { why = "fern blocked by " + level.getBlockState(p.above(i)).getBlock().getDescriptionId() + " at +" + i; return false; }
		GreenhazeBlocks.GiantFernBlock.place(level, p, h, Block.UPDATE_CLIENTS);
		return true;
	}

	private static boolean vent(WorldGenLevel level, BlockPos p) {
		BlockPos g = p.below();
		BlockState s = level.getBlockState(g);
		if (!(soil(s) || s.is(Blocks.MUD) || s.is(Blocks.CLAY)) || !(level.getBlockState(p).isAir() || level.getBlockState(p).is(Blocks.WATER))) return false;
		level.setBlock(g, GreenhazeBlocks.HAZE_VENT.defaultBlockState(), Block.UPDATE_CLIENTS);
		return true;
	}

	private static boolean lily(WorldGenLevel level, BlockPos p) {
		if (!level.getBlockState(p).isAir() || !level.getFluidState(p.below()).is(FluidTags.WATER) || !level.getBlockState(p.below()).is(Blocks.WATER)) return false;
		level.setBlock(p, GreenhazeBlocks.LUCID_LILY.defaultBlockState(), Block.UPDATE_CLIENTS);
		return true;
	}

	private static boolean bush(WorldGenLevel level, BlockPos p) {
		BlockState below = level.getBlockState(p.below());
		if (!soil(below) || !free(level.getBlockState(p))) return false;
		level.setBlock(p, GreenhazeBlocks.MUNCHFRUIT_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3), Block.UPDATE_CLIENTS);
		return true;
	}

	private static boolean tree(WorldGenLevel level, RandomSource random, BlockPos p) {
		BlockState below = level.getBlockState(p.below());
		if (!soil(below)) return false;
		int h = 5 + random.nextInt(4);
		for (int i = 0; i < h + 2; i++) if (!free(level.getBlockState(p.above(i)))) return false;
		BlockState log = GreenhazeBlocks.KUSHWOOD_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
		BlockState leaves = GreenhazeBlocks.KUSHWOOD_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
		for (int dy = h - 3; dy <= h + 1; dy++) {
			int r = dy >= h ? 1 : 2;
			for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
				if (Math.abs(dx) == r && Math.abs(dz) == r && (dy == h + 1 || random.nextBoolean())) continue;
				BlockPos q = p.offset(dx, dy, dz);
				if (free(level.getBlockState(q))) level.setBlock(q, leaves, Block.UPDATE_CLIENTS);
			}
		}
		for (int i = 0; i < h; i++) level.setBlock(p.above(i), log, Block.UPDATE_CLIENTS);
		return true;
	}
}
