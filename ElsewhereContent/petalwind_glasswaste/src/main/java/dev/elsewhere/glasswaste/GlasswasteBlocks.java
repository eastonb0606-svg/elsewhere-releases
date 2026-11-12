package dev.elsewhere.glasswaste;

import static dev.elsewhere.petalwind_glasswaste.Reg.block;

import dev.elsewhere.petalwind_glasswaste.Reg;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;

/** Glasswaste blocks: Sunglass, Scorched Sand, Quicksand, Mirror Block, the Sunwright Brick set, Sunstone ore and the Sun Mirror. */
public final class GlasswasteBlocks {
	private GlasswasteBlocks() {}

	public static Block SUNGLASS, SCORCHED_SAND, QUICKSAND, MIRROR_BLOCK, SUNWRIGHT_BRICKS, SUNWRIGHT_BRICK_STAIRS, SUNWRIGHT_BRICK_SLAB,
		SUNWRIGHT_BRICK_WALL, CHISELED_SUNWRIGHT_BRICKS, SUNSTONE_ORE, SUN_MIRROR;

	public static void init() {
		Reg.currentTab = "glasswaste";
		SUNGLASS = block("sunglass", SunglassBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.4F)
			.sound(SoundType.GLASS).noOcclusion().isValidSpawn((s, l, p, t) -> false).isRedstoneConductor((s, l, p) -> false)
			.isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, a) -> false).lightLevel(s -> s.getValue(SunglassBlock.LIT) ? 12 : 0));
		SCORCHED_SAND = block("scorched_sand", ScorchedSandBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.strength(0.6F).sound(SoundType.SAND).lightLevel(s -> 2));
		QUICKSAND = block("quicksand", QuicksandBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.5F)
			.sound(SoundType.SAND).dynamicShape().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, a) -> true));
		MIRROR_BLOCK = block("mirror_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).requiresCorrectToolForDrops()
			.strength(3.0F, 6.0F).sound(SoundType.COPPER));
		SUNWRIGHT_BRICKS = block("sunwright_bricks", Block::new, bricks());
		SUNWRIGHT_BRICK_STAIRS = block("sunwright_brick_stairs", p -> new StairBlock(SUNWRIGHT_BRICKS.defaultBlockState(), p), bricks());
		SUNWRIGHT_BRICK_SLAB = block("sunwright_brick_slab", SlabBlock::new, bricks());
		SUNWRIGHT_BRICK_WALL = block("sunwright_brick_wall", WallBlock::new, bricks().forceSolidOn());
		CHISELED_SUNWRIGHT_BRICKS = block("chiseled_sunwright_bricks", Block::new, bricks().lightLevel(s -> 4));
		SUNSTONE_ORE = block("sunstone_ore", p -> new DropExperienceBlock(UniformInt.of(3, 7), p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F)
			.lightLevel(s -> 7));
		SUN_MIRROR = block("sun_mirror", SunMirrorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(-1.0F, 3600000.0F)
			.sound(SoundType.COPPER).noOcclusion());
	}

	private static BlockBehaviour.Properties bricks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops()
			.strength(1.5F, 6.0F);
	}
}
