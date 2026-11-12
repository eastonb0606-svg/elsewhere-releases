package dev.elsewhere.glasswaste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Amber glass that glows only while it can see the open sky. */
public class SunglassBlock extends TransparentBlock {
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	public SunglassBlock(BlockBehaviour.Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return this.defaultBlockState().setValue(LIT, context.getLevel().canSeeSky(context.getClickedPos().above()));
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		boolean sky = level.canSeeSky(pos.above());
		if (sky != state.getValue(LIT)) level.setBlock(pos, state.setValue(LIT, sky), Block.UPDATE_CLIENTS);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighborPos,
		BlockState neighborState, RandomSource random) {
		if (direction == Direction.UP && level instanceof net.minecraft.world.level.Level l) {
			return state.setValue(LIT, l.canSeeSky(pos.above()));
		}
		return state;
	}
}
