package dev.elsewhere.ironpeak;

import static dev.elsewhere.verdigris_ironpeak.Reg.block;

import dev.elsewhere.verdigris_ironpeak.SummonFocusBlock;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Ironpeak blocks: the Frostgranite set, Skyiron Ore, Hearthstone, Prayer Flags, Glacier Ice and arena blocks. */
public final class IronpeakBlocks {
	private static Properties granite() {
		return Properties.of().mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.8F, 6.0F).sound(SoundType.STONE);
	}

	public static final Block FROSTGRANITE = block("frostgranite", Block::new, granite());
	public static final Block FROSTGRANITE_STAIRS = block("frostgranite_stairs", p -> new StairBlock(FROSTGRANITE.defaultBlockState(), p), granite());
	public static final Block FROSTGRANITE_SLAB = block("frostgranite_slab", SlabBlock::new, granite());
	public static final Block FROSTGRANITE_WALL = block("frostgranite_wall", WallBlock::new, granite().forceSolidOn());
	public static final Block POLISHED_FROSTGRANITE = block("polished_frostgranite", Block::new, granite());
	public static final Block POLISHED_FROSTGRANITE_STAIRS = block("polished_frostgranite_stairs", p -> new StairBlock(POLISHED_FROSTGRANITE.defaultBlockState(), p), granite());
	public static final Block POLISHED_FROSTGRANITE_SLAB = block("polished_frostgranite_slab", SlabBlock::new, granite());
	public static final Block FROSTGRANITE_BRICKS = block("frostgranite_bricks", Block::new, granite());
	public static final Block FROSTGRANITE_BRICK_STAIRS = block("frostgranite_brick_stairs", p -> new StairBlock(FROSTGRANITE_BRICKS.defaultBlockState(), p), granite());
	public static final Block FROSTGRANITE_BRICK_SLAB = block("frostgranite_brick_slab", SlabBlock::new, granite());
	public static final Block FROSTGRANITE_BRICK_WALL = block("frostgranite_brick_wall", WallBlock::new, granite().forceSolidOn());

	public static final Block SKYIRON_ORE = block("skyiron_ore", p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
		Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(4.5F, 6.0F).sound(SoundType.DEEPSLATE));
	public static final Block HEARTHSTONE = block("hearthstone", HearthstoneBlock::new,
		Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(2.5F, 6.0F).sound(SoundType.STONE).lightLevel(s -> 13));
	public static final Block PRAYER_FLAGS = block("prayer_flags", PrayerFlagsBlock::new,
		Properties.of().mapColor(MapColor.COLOR_RED).strength(0.3F).sound(SoundType.WOOL).noOcclusion().noCollision().pushReaction(PushReaction.POPPED).ignitedByLava());
	public static final Block GLACIER_ICE = block("glacier_ice", Block::new,
		Properties.of().mapColor(MapColor.ICE).strength(2.8F).friction(0.6F).sound(SoundType.GLASS));

	// Arena focus blocks (boss summons).
	public static final Block SUMMIT_WAR_DRUM = block("summit_war_drum", p -> new SummonFocusBlock("summit_war_drum", Block.box(1, 0, 1, 15, 14, 15), p),
		Properties.of().mapColor(MapColor.COLOR_BROWN).strength(6.0F, 6.0F).sound(SoundType.WOOD).noOcclusion());
	public static final Block PROVING_CHALLENGE_HORN = block("proving_challenge_horn", p -> new SummonFocusBlock("proving_challenge_horn", Block.box(3, 0, 3, 13, 16, 13), p),
		Properties.of().mapColor(MapColor.COLOR_GRAY).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).noOcclusion().noLootTable());

	private IronpeakBlocks() {}

	public static void init() {}
}
