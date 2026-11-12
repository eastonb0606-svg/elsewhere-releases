package dev.elsewhere.hush;

import dev.elsewhere.greenhaze_hush.Reg;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Hush blocks: Hushstone set, Hushweave, Echo Crystal Ore, Sculk Lantern, Ossuary Bone bricks, and the arena blocks. */
public final class HushBlocks {
	private HushBlocks() {}

	public static Block HUSHSTONE, HUSHSTONE_STAIRS, HUSHSTONE_SLAB, HUSHSTONE_WALL, POLISHED_HUSHSTONE, HUSHSTONE_BRICKS,
		HUSHSTONE_BRICK_STAIRS, HUSHSTONE_BRICK_SLAB, HUSHSTONE_BRICK_WALL, HUSHWEAVE, HUSHWEAVE_CARPET, ECHO_CRYSTAL_ORE, ECHO_CRYSTAL_BLOCK,
		SCULK_LANTERN, OSSUARY_BONE_BRICKS, OSSUARY_BONE_BRICK_STAIRS, OSSUARY_BONE_BRICK_SLAB, OSSUARY_BONE_BRICK_WALL,
		TYMPANUM_MEMBRANE, CHOIR_PILLAR;

	static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).instrument(NoteBlockInstrument.BASEDRUM)
			.requiresCorrectToolForDrops().strength(3.0F, 6.0F).sound(SoundType.DEEPSLATE);
	}

	static BlockBehaviour.Properties bone() {
		return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).instrument(NoteBlockInstrument.XYLOPHONE)
			.requiresCorrectToolForDrops().strength(2.0F, 6.0F).sound(SoundType.BONE_BLOCK);
	}

	static void init() {
		HUSHSTONE = Reg.block("hushstone", Block::new, stone());
		HUSHSTONE_STAIRS = Reg.block("hushstone_stairs", p -> new StairBlock(HUSHSTONE.defaultBlockState(), p), stone());
		HUSHSTONE_SLAB = Reg.block("hushstone_slab", SlabBlock::new, stone());
		HUSHSTONE_WALL = Reg.block("hushstone_wall", WallBlock::new, stone().forceSolidOn());
		POLISHED_HUSHSTONE = Reg.block("polished_hushstone", Block::new, stone());
		HUSHSTONE_BRICKS = Reg.block("hushstone_bricks", Block::new, stone());
		HUSHSTONE_BRICK_STAIRS = Reg.block("hushstone_brick_stairs", p -> new StairBlock(HUSHSTONE_BRICKS.defaultBlockState(), p), stone());
		HUSHSTONE_BRICK_SLAB = Reg.block("hushstone_brick_slab", SlabBlock::new, stone());
		HUSHSTONE_BRICK_WALL = Reg.block("hushstone_brick_wall", WallBlock::new, stone().forceSolidOn());
		HUSHWEAVE = Reg.block("hushweave", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.instrument(NoteBlockInstrument.GUITAR).strength(0.8F).sound(SoundType.WOOL).ignitedByLava());
		HUSHWEAVE_CARPET = Reg.block("hushweave_carpet", CarpetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.strength(0.1F).sound(SoundType.WOOL).ignitedByLava());
		ECHO_CRYSTAL_ORE = Reg.block("echo_crystal_ore", EchoCrystalOreBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE)
			.instrument(NoteBlockInstrument.BASEDRUM).requiresCorrectToolForDrops().strength(4.5F, 3.0F).sound(SoundType.DEEPSLATE).lightLevel(s -> 3));
		ECHO_CRYSTAL_BLOCK = Reg.block("echo_crystal_block", Block::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.requiresCorrectToolForDrops().strength(3.0F).sound(SoundType.AMETHYST).lightLevel(s -> 6));
		SCULK_LANTERN = Reg.block("sculk_lantern", SculkLanternBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.strength(1.5F).sound(SoundType.SCULK_CATALYST).lightLevel(s -> 3 + 4 * s.getValue(SculkLanternBlock.PULSE)));
		OSSUARY_BONE_BRICKS = Reg.block("ossuary_bone_bricks", Block::new, bone());
		OSSUARY_BONE_BRICK_STAIRS = Reg.block("ossuary_bone_brick_stairs", p -> new StairBlock(OSSUARY_BONE_BRICKS.defaultBlockState(), p), bone());
		OSSUARY_BONE_BRICK_SLAB = Reg.block("ossuary_bone_brick_slab", SlabBlock::new, bone());
		OSSUARY_BONE_BRICK_WALL = Reg.block("ossuary_bone_brick_wall", WallBlock::new, bone().forceSolidOn());
		TYMPANUM_MEMBRANE = Reg.block("tympanum_membrane", TympanumBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN)
			.strength(-1.0F, 3600000.0F).sound(SoundType.SCULK).lightLevel(s -> s.getValue(TympanumBlock.EXPOSED) ? 12 : 4).noLootTable());
		CHOIR_PILLAR = Reg.block("choir_pillar", ChoirPillarBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
			.strength(-1.0F, 3600000.0F).sound(SoundType.AMETHYST).lightLevel(s -> s.getValue(ChoirPillarBlock.LIT) ? 15 : 5).noLootTable());
	}

	/** Cyan crystal that chimes when mined. */
	public static class EchoCrystalOreBlock extends DropExperienceBlock {
		public EchoCrystalOreBlock(BlockBehaviour.Properties p) {
			super(UniformInt.of(3, 7), p);
		}

		@Override
		public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 2F, 0.6F + level.getRandom().nextFloat() * 0.6F);
			return super.playerWillDestroy(level, pos, state, player);
		}
	}

	/** Pulses brighter with nearby sound (checks the Hush noise bus every half second). */
	public static class SculkLanternBlock extends Block {
		public static final IntegerProperty PULSE = IntegerProperty.create("pulse", 0, 3);

		public SculkLanternBlock(BlockBehaviour.Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(PULSE, 0));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
			b.add(PULSE);
		}

		@Override
		protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
			if (!level.isClientSide()) level.scheduleTick(pos, this, 10);
		}

		@Override
		protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
			int loud = HushNoise.loudnessNear(level, net.minecraft.world.phys.Vec3.atCenterOf(pos), 10, 20);
			int target = Math.max(loud, state.getValue(PULSE) - 1);
			if (target != state.getValue(PULSE)) level.setBlock(pos, state.setValue(PULSE, Math.min(3, target)), 2);
			level.scheduleTick(pos, this, 10);
		}

		@Override
		protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
			return Block.column(10.0, 0.0, 12.0);
		}
	}

	/** One of Auris's four tympanum membranes (weak points). Strike it when exposed. */
	public static class TympanumBlock extends Block {
		public static final BooleanProperty EXPOSED = BooleanProperty.create("exposed");

		public TympanumBlock(BlockBehaviour.Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(EXPOSED, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
			b.add(EXPOSED);
		}

		@Override
		protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
			if (level instanceof ServerLevel sl) dev.elsewhere.boss.auris.Auris.onMembraneStruck(sl, pos, player);
		}

		@Override
		public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
			if (state.getValue(EXPOSED) && random.nextInt(2) == 0)
				level.addParticle(ParticleTypes.SCULK_SOUL, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.03, 0);
		}
	}

	/** One of Lithara's 8 resonating crystal pillars; note 0-7 is its pitch. Strike it (attack) to sound it. */
	public static class ChoirPillarBlock extends Block {
		public static final IntegerProperty NOTE = IntegerProperty.create("note", 0, 7);
		public static final BooleanProperty LIT = BooleanProperty.create("lit");

		public ChoirPillarBlock(BlockBehaviour.Properties p) {
			super(p);
			registerDefaultState(stateDefinition.any().setValue(NOTE, 0).setValue(LIT, false));
		}

		@Override
		protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
			b.add(NOTE, LIT);
		}

		@Override
		protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
			if (level instanceof ServerLevel sl) dev.elsewhere.boss.lithara.Lithara.onPillarStruck(sl, pos, state.getValue(NOTE), player);
		}

		public static float pitch(int note) {
			return (float) Math.pow(2.0, (new int[]{0, 2, 4, 5, 7, 9, 11, 12}[note] - 6) / 12.0);
		}
	}
}
