package dev.elsewhere.boss.solaxis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Solaxis Ember: an everlasting light (light 15, never burns out). Green Leaf counts it as a lighter (soft tie-in). */
public class SolaxisEmberBlock extends Block {
	private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 6, 11);

	public SolaxisEmberBlock(Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.45, pos.getZ() + 0.5, 0, 0.01, 0);
	}
}
