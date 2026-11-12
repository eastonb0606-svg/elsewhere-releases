package dev.elsewhere.petalwind;

import static dev.elsewhere.petalwind_glasswaste.Reg.block;

import dev.elsewhere.petalwind_glasswaste.Reg;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.util.random.WeightedList;

/** Petalwind Isles blocks: the Skyblossom wood set, clouds, updraft vents, windchimes and Zephyrite ore. */
public final class PetalwindBlocks {
	private PetalwindBlocks() {}

	public static final BlockSetType SKYBLOSSOM_SET = BlockSetTypeBuilder.copyOf(BlockSetType.CHERRY).register(Reg.id("skyblossom"));
	public static final WoodType SKYBLOSSOM_WOOD = WoodTypeBuilder.copyOf(WoodType.CHERRY).register(Reg.id("skyblossom"), SKYBLOSSOM_SET);
	public static final ResourceKey<Feature> SKYBLOSSOM_TREE = ResourceKey.create(Registries.FEATURE, Reg.id("skyblossom_tree"));
	public static final TreeGrower SKYBLOSSOM_GROWER = new TreeGrower("elsewhere_skyblossom", WeightedList.of(SKYBLOSSOM_TREE), WeightedList.of(), WeightedList.of(), null);

	public static Block SKYBLOSSOM_LOG, STRIPPED_SKYBLOSSOM_LOG, SKYBLOSSOM_WOOD_BLOCK, STRIPPED_SKYBLOSSOM_WOOD, SKYBLOSSOM_PLANKS, SKYBLOSSOM_STAIRS,
		SKYBLOSSOM_SLAB, SKYBLOSSOM_FENCE, SKYBLOSSOM_FENCE_GATE, SKYBLOSSOM_DOOR, SKYBLOSSOM_TRAPDOOR, SKYBLOSSOM_BUTTON, SKYBLOSSOM_PRESSURE_PLATE,
		SKYBLOSSOM_LEAVES, SKYBLOSSOM_SAPLING, CLOUD_BLOCK, CUMULUS_PUFF, UPDRAFT_VENT, WINDCHIME, ZEPHYRITE_ORE;

	public static void init() {
		Reg.currentTab = "petalwind";
		SKYBLOSSOM_LOG = block("skyblossom_log", RotatedPillarBlock::new, log(MapColor.TERRACOTTA_WHITE));
		STRIPPED_SKYBLOSSOM_LOG = block("stripped_skyblossom_log", RotatedPillarBlock::new, log(MapColor.TERRACOTTA_PINK));
		SKYBLOSSOM_WOOD_BLOCK = block("skyblossom_wood", RotatedPillarBlock::new, log(MapColor.TERRACOTTA_WHITE));
		STRIPPED_SKYBLOSSOM_WOOD = block("stripped_skyblossom_wood", RotatedPillarBlock::new, log(MapColor.TERRACOTTA_PINK));
		SKYBLOSSOM_PLANKS = block("skyblossom_planks", Block::new, planks());
		SKYBLOSSOM_STAIRS = block("skyblossom_stairs", p -> new StairBlock(SKYBLOSSOM_PLANKS.defaultBlockState(), p), planks());
		SKYBLOSSOM_SLAB = block("skyblossom_slab", SlabBlock::new, planks());
		SKYBLOSSOM_FENCE = block("skyblossom_fence", FenceBlock::new, planks());
		SKYBLOSSOM_FENCE_GATE = block("skyblossom_fence_gate", p -> new FenceGateBlock(SKYBLOSSOM_WOOD, p), planks().forceSolidOn());
		SKYBLOSSOM_DOOR = block("skyblossom_door", p -> new DoorBlock(SKYBLOSSOM_SET, p), planks().noOcclusion().pushReaction(PushReaction.POPPED));
		SKYBLOSSOM_TRAPDOOR = block("skyblossom_trapdoor", p -> new TrapDoorBlock(SKYBLOSSOM_SET, p), planks().noOcclusion().isValidSpawn((s, l, p2, t) -> false));
		SKYBLOSSOM_BUTTON = block("skyblossom_button", p -> new ButtonBlock(SKYBLOSSOM_SET, 30, p),
			BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
		SKYBLOSSOM_PRESSURE_PLATE = block("skyblossom_pressure_plate", p -> new PressurePlateBlock(SKYBLOSSOM_SET, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
		SKYBLOSSOM_LEAVES = block("skyblossom_leaves",
			p -> new UntintedParticleLeavesBlock(0.12F, ParticleTypes.CHERRY_LEAVES, AmbientLeavesBlockSoundPlayer.noAmbientSound(), p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(0.2F).randomTicks().sound(SoundType.CHERRY_LEAVES).noOcclusion()
				.isValidSpawn((s, l, p2, t) -> false).isSuffocating((s, l, p2) -> false).isViewBlocking((s, l, p2, a) -> false)
				.ignitedByLava().pushReaction(PushReaction.POPPED).isRedstoneConductor((s, l, p2) -> false));
		SKYBLOSSOM_SAPLING = block("skyblossom_sapling", p -> new SaplingBlock(SKYBLOSSOM_GROWER, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).noCollision().randomTicks().instabreak().sound(SoundType.CHERRY_SAPLING)
				.pushReaction(PushReaction.POPPED));

		CLOUD_BLOCK = block("cloud_block", CloudBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F)
			.sound(SoundType.WOOL).speedFactor(0.7F).jumpFactor(0.85F).fallDistanceReduction(1.0F).noOcclusion()
			.isViewBlocking((s, l, p, a) -> false).isSuffocating((s, l, p) -> false));
		CUMULUS_PUFF = block("cumulus_puff", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.4F)
			.sound(SoundType.WOOL).bounceRestitution(1.0F).fallDistanceReduction(1.0F));
		UPDRAFT_VENT = block("updraft_vent", UpdraftVentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(1.0F).sound(SoundType.CALCITE).noOcclusion());
		WINDCHIME = block("windchime", WindchimeBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE)
			.strength(0.5F).sound(SoundType.AMETHYST_CLUSTER).noCollision().noOcclusion().randomTicks().pushReaction(PushReaction.POPPED));
		ZEPHYRITE_ORE = block("zephyrite_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p), BlockBehaviour.Properties.of()
			.mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(2.0F, 3.0F)
			.sound(SoundType.CALCITE).lightLevel(s -> 3));
	}

	private static BlockBehaviour.Properties log(MapColor color) {
		return BlockBehaviour.Properties.of().mapColor(color).instrument(NoteBlockInstrument.BASS).strength(2.0F).sound(SoundType.CHERRY_WOOD).ignitedByLava();
	}

	private static BlockBehaviour.Properties planks() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASS).strength(2.0F, 3.0F)
			.sound(SoundType.CHERRY_WOOD).ignitedByLava();
	}

}
