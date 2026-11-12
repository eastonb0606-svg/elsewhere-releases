package dev.elsewhere.verdigris_sea;

import static dev.elsewhere.verdigris_ironpeak.Reg.block;
import static dev.elsewhere.verdigris_ironpeak.Reg.id;

import dev.elsewhere.verdigris_ironpeak.SummonFocusBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WaterloggedTransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Verdigris Sea blocks: the Verdigris Plate family, Storm Coil, Kelpwood set, Barnacle Cluster, Pearlite Ore and arena blocks. */
public final class VerdigrisBlocks {
	public static final BlockSetType KELPWOOD_SET = BlockSetTypeBuilder.copyOf(BlockSetType.MANGROVE).register(id("kelpwood"));
	public static final WoodType KELPWOOD_WOOD = WoodTypeBuilder.copyOf(WoodType.MANGROVE).register(id("kelpwood"), KELPWOOD_SET);

	private static Properties plate() {
		return Properties.of().mapColor(MapColor.WARPED_WART_BLOCK).requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.COPPER);
	}

	public static final Block VERDIGRIS_PLATE = block("verdigris_plate", Block::new, plate());
	public static final Block RIVETED_VERDIGRIS_PLATE = block("riveted_verdigris_plate", Block::new, plate());
	public static final Block VERDIGRIS_PLATE_STAIRS = block("verdigris_plate_stairs", p -> new StairBlock(VERDIGRIS_PLATE.defaultBlockState(), p), plate());
	public static final Block VERDIGRIS_PLATE_SLAB = block("verdigris_plate_slab", SlabBlock::new, plate());
	public static final Block VERDIGRIS_PLATE_WALL = block("verdigris_plate_wall", WallBlock::new, plate().forceSolidOn());
	public static final Block VERDIGRIS_GRATING = block("verdigris_grating", WaterloggedTransparentBlock::new,
		Properties.of().mapColor(MapColor.WARPED_WART_BLOCK).strength(3.0F, 6.0F).sound(SoundType.COPPER_GRATE).noOcclusion()
			.requiresCorrectToolForDrops());
	public static final Block VERDIGRIS_PIPE = block("verdigris_pipe", PipeBlock::new, plate().noOcclusion());
	public static final Block VERDIGRIS_PIPE_JUNCTION = block("verdigris_pipe_junction", Block::new, plate());
	public static final Block STORM_COIL = block("storm_coil", StormCoilBlock::new,
		Properties.of().mapColor(MapColor.COLOR_ORANGE).requiresCorrectToolForDrops().strength(3.5F, 6.0F).sound(SoundType.COPPER)
			.lightLevel(s -> s.getValue(StormCoilBlock.CHARGE) > 0 ? 7 : 0));

	private static Properties wood(MapColor color) {
		return Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava();
	}

	public static final Block KELPWOOD_LOG = block("kelpwood_log", RotatedPillarBlock::new, wood(MapColor.COLOR_GREEN).strength(2.0F));
	public static final Block STRIPPED_KELPWOOD_LOG = block("stripped_kelpwood_log", RotatedPillarBlock::new, wood(MapColor.TERRACOTTA_GREEN).strength(2.0F));
	public static final Block KELPWOOD_WOOD_BLOCK = block("kelpwood_wood", RotatedPillarBlock::new, wood(MapColor.COLOR_GREEN).strength(2.0F));
	public static final Block STRIPPED_KELPWOOD_WOOD = block("stripped_kelpwood_wood", RotatedPillarBlock::new, wood(MapColor.TERRACOTTA_GREEN).strength(2.0F));
	public static final Block KELPWOOD_PLANKS = block("kelpwood_planks", Block::new, wood(MapColor.TERRACOTTA_GREEN));
	public static final Block KELPWOOD_STAIRS = block("kelpwood_stairs", p -> new StairBlock(KELPWOOD_PLANKS.defaultBlockState(), p), wood(MapColor.TERRACOTTA_GREEN));
	public static final Block KELPWOOD_SLAB = block("kelpwood_slab", SlabBlock::new, wood(MapColor.TERRACOTTA_GREEN));
	public static final Block KELPWOOD_FENCE = block("kelpwood_fence", FenceBlock::new, wood(MapColor.TERRACOTTA_GREEN));
	public static final Block KELPWOOD_FENCE_GATE = block("kelpwood_fence_gate", p -> new FenceGateBlock(KELPWOOD_WOOD, p), wood(MapColor.TERRACOTTA_GREEN).forceSolidOn());
	public static final Block KELPWOOD_DOOR = block("kelpwood_door", p -> new DoorBlock(KELPWOOD_SET, p),
		wood(MapColor.TERRACOTTA_GREEN).strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED));
	public static final Block KELPWOOD_TRAPDOOR = block("kelpwood_trapdoor", p -> new TrapDoorBlock(KELPWOOD_SET, p),
		wood(MapColor.TERRACOTTA_GREEN).strength(3.0F).noOcclusion().isValidSpawn(Blocks::never));
	public static final Block KELPWOOD_BUTTON = block("kelpwood_button", p -> new ButtonBlock(KELPWOOD_SET, 30, p),
		Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
	public static final Block KELPWOOD_PRESSURE_PLATE = block("kelpwood_pressure_plate", p -> new PressurePlateBlock(KELPWOOD_SET, p),
		wood(MapColor.TERRACOTTA_GREEN).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));

	public static final Block BARNACLE_CLUSTER = block("barnacle_cluster", BarnacleClusterBlock::new,
		Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.6F).sound(SoundType.CORAL_BLOCK).noOcclusion().pushReaction(PushReaction.POPPED));
	public static final Block PEARLITE_ORE = block("pearlite_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p),
		Properties.of().mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops().strength(3.5F, 3.0F).sound(SoundType.CALCITE));

	// Arena focus blocks (boss summons).
	public static final Block DROWNED_ORGAN = block("drowned_organ", p -> new SummonFocusBlock("drowned_organ", Block.box(0, 0, 0, 16, 16, 16), p),
		Properties.of().mapColor(MapColor.WARPED_WART_BLOCK).strength(-1.0F, 3600000.0F).sound(SoundType.COPPER).lightLevel(s -> 6).noLootTable());
	public static final Block LIGHTHOUSE_LANTERN = block("lighthouse_lantern", p -> new SummonFocusBlock("lighthouse_lantern", Block.box(0, 0, 0, 16, 16, 16), p),
		Properties.of().mapColor(MapColor.QUARTZ).strength(-1.0F, 3600000.0F).sound(SoundType.GLASS).lightLevel(s -> 15).noLootTable());

	private VerdigrisBlocks() {}

	public static void init() {
		FlammableBlockRegistry fire = FlammableBlockRegistry.getDefaultInstance();
		for (Block b : new Block[] {KELPWOOD_LOG, STRIPPED_KELPWOOD_LOG, KELPWOOD_WOOD_BLOCK, STRIPPED_KELPWOOD_WOOD}) fire.add(b, 5, 5);
		for (Block b : new Block[] {KELPWOOD_PLANKS, KELPWOOD_STAIRS, KELPWOOD_SLAB, KELPWOOD_FENCE, KELPWOOD_FENCE_GATE}) fire.add(b, 5, 20);
	}
}
