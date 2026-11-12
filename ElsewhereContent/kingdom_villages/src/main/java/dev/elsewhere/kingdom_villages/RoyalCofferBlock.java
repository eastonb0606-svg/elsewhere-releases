package dev.elsewhere.kingdom_villages;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.core.Direction;

/**
 * {@code elsewhere:village_royal_coffer}: the Royal Steward's job site, an iron-bound coffer. Uncraftable, drops nothing.
 * Serfs build it SEALED into the tax office of a fortified town; liberation unseals it, and only an unsealed coffer is a
 * point of interest (so Stewards exist only in liberated towns, at most one each).
 */
public class RoyalCofferBlock extends Block {
	public static final BooleanProperty SEALED = BooleanProperty.create("sealed");
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

	public RoyalCofferBlock(BlockBehaviour.Properties p) {
		super(p);
		registerDefaultState(stateDefinition.any().setValue(SEALED, false).setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
		b.add(SEALED, FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext ctx) {
		return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
	}
}
