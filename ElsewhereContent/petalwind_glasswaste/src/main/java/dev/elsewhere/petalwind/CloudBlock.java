package dev.elsewhere.petalwind;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Soft walkable cloud: you stand a little inside it, move slowly, and never take fall damage on it. */
public class CloudBlock extends Block {
	private static final VoxelShape SOFT = Block.box(0, 0, 0, 16, 12, 16);

	public CloudBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SOFT;
	}
}
