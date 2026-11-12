package dev.elsewhere.gear3.s3.miresnare;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The placed Mire Snare (see {@link MireSnare}). States: {@code sprung} (armed: jaws open flat on a reed mat; sprung: jaws shut
 * upright) and {@code waterlogged} (a mire snare may sit in shallow water). No collision; mobs path straight over it. Needs a floor
 * (like a pressure plate) and pops off, dropping itself, when the floor goes. Block entity: the placer's UUID (kill credit).
 */
public class MireSnareBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
	public static final BooleanProperty SPRUNG = BooleanProperty.create("sprung");
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	/** Outline: the mat and the open jaws. */
	private static final VoxelShape ARMED = Block.box(1, 0, 1, 15, 2.5, 15);
	/** Outline: the shut jaws over the mat. */
	private static final VoxelShape CLOSED = Block.box(2, 0, 3, 14, 6.5, 13);
	/** Where an entity must be to spring it: the bottom quarter, so mobs flying or jumping over the block don't. */
	private static final VoxelShape TRIGGER = Block.box(1, 0, 1, 15, 4, 15);

	public MireSnareBlock(Properties props) {
		super(props);
		registerDefaultState(stateDefinition.any().setValue(SPRUNG, false).setValue(WATERLOGGED, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(SPRUNG, WATERLOGGED);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
		return state.getValue(SPRUNG) ? CLOSED : ARMED;
	}

	@Override
	protected VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity) {
		return TRIGGER;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		FluidState f = ctx.getLevel().getFluidState(ctx.getClickedPos());
		return defaultBlockState().setValue(WATERLOGGED, f.getType() == Fluids.WATER);
	}

	@Override
	protected FluidState getFluidState(BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	/** A floor like a pressure plate's: a sturdy top face, or one that holds a block's centre (fences, walls). */
	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		return canSupportRigidBlock(level, below) || canSupportCenter(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction dir, BlockPos nPos, BlockState nState,
		RandomSource random) {
		if (state.getValue(WATERLOGGED)) ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
		// air here makes vanilla destroy the block with drops (an armed snare drops itself) and keep any water
		if (dir == Direction.DOWN && !state.canSurvive(level, pos)) return Blocks.AIR.defaultBlockState();
		return super.updateShape(state, level, ticks, pos, dir, nPos, nState, random);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!level.isClientSide() && placer instanceof Player p && level.getBlockEntity(pos) instanceof MireSnareBlockEntity be) be.setOwner(p.getUUID());
	}

	/** Vanilla's block-collision callback (only for entities touching this block, never a scan): the first mob in springs it. */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
		if (!(level instanceof ServerLevel server) || state.getValue(SPRUNG) || !MireSnare.ENABLED) return;
		if (!(entity instanceof LivingEntity victim) || !MireSnare.springsFor(victim)) return;
		MireSnare.spring(server, pos, state, victim, MireSnare.owner(server, pos));
	}

	/** The one scheduled tick after a spring: the snare falls apart. */
	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (state.getValue(SPRUNG)) MireSnare.fallApart(level, pos);
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MireSnareBlockEntity(pos, state);
	}
}
