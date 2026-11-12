package dev.elsewhere.palehollow.block;

import dev.elsewhere.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One of Mother Creak's four wall hearts. Breaking it stuns her; while any heart stands she cannot be hurt. */
public class ResinHeartBlock extends Block {
	public ResinHeartBlock(final Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(final BlockState state, final Level level, final BlockPos pos, final RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ModParticles.RESIN_DUST, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(),
				0.0, -0.02, 0.0);
		}
	}
}
