package dev.elsewhere.boss.solaxis;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** {@code elsewhere:pyre_cradle_core}: the dormant core at the centre of the Pyre Cradle. Break the 4 spawners to wake Solaxis. */
public class PyreCradleCoreBlock extends Block {

	public PyreCradleCoreBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6, pos.getY() + 1.0, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6, 0, 0.04, 0);
		}
		if (random.nextInt(6) == 0) level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0, 0);
	}
}
