package dev.elsewhere.greenhaze;

import dev.elsewhere.greenhaze_hush.Reg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PlaceOnWaterBlockItem;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Greenhaze blocks: the Kushwood set, Giant Fern, Haze Vent, Lucid Lily, Munchfruit Bush, Trichome Quartz, boss altars. */
public final class GreenhazeBlocks {
	private GreenhazeBlocks() {}

	public static Block KUSHWOOD_LOG, STRIPPED_KUSHWOOD_LOG, KUSHWOOD_WOOD, STRIPPED_KUSHWOOD_WOOD, KUSHWOOD_PLANKS, KUSHWOOD_STAIRS,
		KUSHWOOD_SLAB, KUSHWOOD_FENCE, KUSHWOOD_FENCE_GATE, KUSHWOOD_DOOR, KUSHWOOD_TRAPDOOR, KUSHWOOD_BUTTON, KUSHWOOD_PRESSURE_PLATE,
		KUSHWOOD_LEAVES, GIANT_FERN, HAZE_VENT, LUCID_LILY, MUNCHFRUIT_BUSH, TRICHOME_QUARTZ_ORE, TRICHOME_QUARTZ_BLOCK, HAZE_RESIN_BLOCK,
		HOTBOX_ALTAR, SNACK_ALTAR, HOTBOX_VENT, VENT_LEVER;

	static void init() {
		KUSHWOOD_LOG = Reg.block("kushwood_log", RotatedPillarBlock::new, wood());
		STRIPPED_KUSHWOOD_LOG = Reg.block("stripped_kushwood_log", RotatedPillarBlock::new, wood());
		KUSHWOOD_WOOD = Reg.block("kushwood_wood", RotatedPillarBlock::new, wood());
		STRIPPED_KUSHWOOD_WOOD = Reg.block("stripped_kushwood_wood", RotatedPillarBlock::new, wood());
		KUSHWOOD_PLANKS = Reg.block("kushwood_planks", Block::new, wood());
		KUSHWOOD_STAIRS = Reg.block("kushwood_stairs", p -> new StairBlock(KUSHWOOD_PLANKS.defaultBlockState(), p), wood());
		KUSHWOOD_SLAB = Reg.block("kushwood_slab", SlabBlock::new, wood());
		KUSHWOOD_FENCE = Reg.block("kushwood_fence", FenceBlock::new, wood());
		KUSHWOOD_FENCE_GATE = Reg.block("kushwood_fence_gate", p -> new FenceGateBlock(WoodType.JUNGLE, p), wood().forceSolidOn());
		KUSHWOOD_DOOR = Reg.block("kushwood_door", p -> new DoorBlock(BlockSetType.JUNGLE, p), wood().strength(3.0F).noOcclusion().pushReaction(PushReaction.POPPED));
		KUSHWOOD_TRAPDOOR = Reg.block("kushwood_trapdoor", p -> new TrapDoorBlock(BlockSetType.JUNGLE, p), wood().strength(3.0F).noOcclusion());
		KUSHWOOD_BUTTON = Reg.block("kushwood_button", p -> new ButtonBlock(BlockSetType.JUNGLE, 30, p),
			BlockBehaviour.Properties.of().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
		KUSHWOOD_PRESSURE_PLATE = Reg.block("kushwood_pressure_plate", p -> new PressurePlateBlock(BlockSetType.JUNGLE, p),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).forceSolidOn().noCollision().strength(0.5F).pushReaction(PushReaction.POPPED));
		KUSHWOOD_LEAVES = Reg.block("kushwood_leaves", p -> new TintedParticleLeavesBlock(0.01F, p), Blocks.leavesProperties(SoundType.AZALEA_LEAVES));

		GIANT_FERN = Reg.block("giant_fern", GiantFernBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
			.noCollision().strength(0.3F).sound(SoundType.BIG_DRIPLEAF).noOcclusion().ignitedByLava().pushReaction(PushReaction.POPPED)
			.offsetType(BlockBehaviour.OffsetType.XZ), false);
		Reg.item("giant_fern", p -> new BlockItem(GIANT_FERN, p), new Item.Properties().useBlockDescriptionPrefix());
		HAZE_VENT = Reg.block("haze_vent", HazeVentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_PURPLE)
			.strength(0.8F).sound(SoundType.MUD).lightLevel(s -> 4));
		LUCID_LILY = Reg.block("lucid_lily", LucidLilyBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND).instabreak()
			.sound(SoundType.LILY_PAD).noOcclusion().pushReaction(PushReaction.POPPED).lightLevel(s -> 10), false);
		Reg.item("lucid_lily", p -> new PlaceOnWaterBlockItem(LUCID_LILY, p), new Item.Properties().useBlockDescriptionPrefix());
		MUNCHFRUIT_BUSH = Reg.block("munchfruit_bush", MunchfruitBushBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.PLANT)
			.randomTicks().noCollision().sound(SoundType.SWEET_BERRY_BUSH).pushReaction(PushReaction.POPPED), false);
		TRICHOME_QUARTZ_ORE = Reg.block("trichome_quartz_ore", p -> new DropExperienceBlock(UniformInt.of(2, 5), p),
			BlockBehaviour.Properties.of().mapColor(MapColor.STONE).instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(3.0F, 3.0F));
		TRICHOME_QUARTZ_BLOCK = Reg.block("trichome_quartz_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ)
			.requiresCorrectToolForDrops().strength(1.5F).sound(SoundType.AMETHYST));
		HAZE_RESIN_BLOCK = Reg.block("haze_resin_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE)
			.strength(0.6F).sound(SoundType.HONEY_BLOCK).speedFactor(0.6F).jumpFactor(0.6F).noOcclusion());
		HOTBOX_ALTAR = Reg.block("hotbox_altar", HotboxAltarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_GREEN)
			.strength(-1.0F, 3600000.0F).sound(SoundType.MUD_BRICKS).lightLevel(s -> 9));
		SNACK_ALTAR = Reg.block("snack_altar", SnackAltarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(-1.0F, 3600000.0F).sound(SoundType.WOOD).lightLevel(s -> 7));
		HOTBOX_VENT = Reg.block("hotbox_vent", HotboxVentBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY)
			.strength(-1.0F, 3600000.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion());
		VENT_LEVER = Reg.block("vent_lever", VentLeverBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
			.strength(-1.0F, 3600000.0F).sound(SoundType.BAMBOO_WOOD).noOcclusion());
	}

	private static BlockBehaviour.Properties wood() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).instrument(NoteBlockInstrument.BASS)
			.strength(2.0F, 3.0F).sound(SoundType.WOOD).ignitedByLava();
	}

	// ------------------------------------------------------------------------------------------------ blocks

	/** A 4-block-tall fern (part 0 = base .. 3 = crown). Breaking any part fells the whole plant. */
	public static class GiantFernBlock extends Block {
		public static final net.minecraft.world.level.block.state.properties.IntegerProperty PART =
			net.minecraft.world.level.block.state.properties.IntegerProperty.create("part", 0, 3);

		public GiantFernBlock(BlockBehaviour.Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(PART, 0));
		}

		@Override
		protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) {
			b.add(PART);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			return Block.column(14.0, 0.0, 16.0);
		}

		@Override
		protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
			int part = state.getValue(PART);
			if (part == 0) {
				BlockState below = level.getBlockState(pos.below());
				return below.is(net.minecraft.tags.BlockTags.DIRT) || below.is(Blocks.MUD) || below.is(Blocks.MOSS_BLOCK);
			}
			BlockState below = level.getBlockState(pos.below());
			return below.is(this) && below.getValue(PART) == part - 1;
		}

		@Override
		protected BlockState updateShape(BlockState state, LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos,
										 net.minecraft.core.Direction dir, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
			return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
		}

		@Override
		public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity by, ItemStack stack) {
			for (int i = 1; i <= 3; i++) {
				if (!level.getBlockState(pos.above(i)).canBeReplaced()) break;
				level.setBlock(pos.above(i), state.setValue(PART, i), 3);
			}
		}

		@Override
		public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
			return ctx.getLevel().getBlockState(ctx.getClickedPos().above()).canBeReplaced() ? defaultBlockState() : null;
		}

		/** Worldgen / blueprint helper. */
		public static void place(net.minecraft.world.level.LevelAccessor level, BlockPos base, int height, int flags) {
			for (int i = 0; i < height; i++) level.setBlock(base.above(i), GIANT_FERN.defaultBlockState().setValue(PART, i == height - 1 ? 3 : Math.min(i, 2)), flags);
		}
	}

	/** Cracked earth that puffs violet smoke. Standing near one pushes the haze up (see {@link Haze}). */
	public static class HazeVentBlock extends Block {
		public HazeVentBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(3) == 0) {
				level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5 + random.nextGaussian() * 0.2, pos.getY() + 1.05,
					pos.getZ() + 0.5 + random.nextGaussian() * 0.2, 0, 0.03 + random.nextDouble() * 0.03, 0);
			}
			if (random.nextInt(4) == 0) {
				level.addParticle(ParticleTypes.WITCH, pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0.05, 0);
			}
		}
	}

	/** A glowing cyan lily on water that cleans the haze around it. */
	public static class LucidLilyBlock extends LilyPadBlock {
		public LucidLilyBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (random.nextInt(5) == 0) level.addParticle(ParticleTypes.GLOW, pos.getX() + random.nextDouble(), pos.getY() + 0.2,
				pos.getZ() + random.nextDouble(), 0, 0.02, 0);
		}
	}

	/** Berry bush with plump snack fruit (Munchfruit). */
	public static class MunchfruitBushBlock extends net.minecraft.world.level.block.SweetBerryBushBlock {
		public MunchfruitBushBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
			return new ItemStack(GreenhazeItems.MUNCHFRUIT);
		}

		@Override
		protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
			// no thorns: it's a snack bush
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			int age = state.getValue(AGE);
			if (age > 1) {
				if (level instanceof ServerLevel sl) {
					Block.popResource(sl, pos, new ItemStack(GreenhazeItems.MUNCHFRUIT, 1 + sl.getRandom().nextInt(2) + (age == 3 ? 1 : 0)));
					sl.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.9F + sl.getRandom().nextFloat() * 0.3F);
					sl.setBlock(pos, state.setValue(AGE, 1), 2);
				}
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
	}

	/** Centre of the Great Hotbox: sneak-use with a lit Green Leaf joint/blunt/vape (or 4 Haze Resin) to summon Percolax. */
	public static class HotboxAltarBlock extends Block {
		public HotboxAltarBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
			if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
			return dev.elsewhere.boss.percolax.Percolax.tryAltar(sl, pos, player, stack) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.04, 0);
		}
	}

	/** The Bonfire Terrace's snack altar: leave a full stack of food to summon Grumblegut. */
	public static class SnackAltarBlock extends Block {
		public SnackAltarBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
			if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
			return dev.elsewhere.boss.grumblegut.Grumblegut.tryAltar(sl, pos, player, stack) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
	}

	/** A bamboo vent shutter in the Great Hotbox (open lets the smoke out). */
	public static class HotboxVentBlock extends Block {
		public static final net.minecraft.world.level.block.state.properties.BooleanProperty OPEN =
			net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN;

		public HotboxVentBlock(BlockBehaviour.Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(OPEN, true));
		}

		@Override
		protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> b) {
			b.add(OPEN);
		}

		@Override
		protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			return state.getValue(OPEN) ? net.minecraft.world.phys.shapes.Shapes.empty() : net.minecraft.world.phys.shapes.Shapes.block();
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (state.getValue(OPEN) && random.nextInt(2) == 0)
				level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + random.nextDouble(), pos.getY() + 0.5, pos.getZ() + random.nextDouble(), 0, 0.07, 0);
		}
	}

	/** Hold use on this lever for 2 s to open its vent (Percolax phase 2). */
	public static class VentLeverBlock extends Block {
		public VentLeverBlock(BlockBehaviour.Properties p) {
			super(p);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			return Block.column(6.0, 0.0, 12.0);
		}

		@Override
		protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
			if (level instanceof ServerLevel sl) dev.elsewhere.boss.percolax.Percolax.channelLever(sl, pos, player);
			return InteractionResult.SUCCESS;
		}

		@Override
		protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
			return useWithoutItem(state, level, pos, player, hit);
		}
	}
}
