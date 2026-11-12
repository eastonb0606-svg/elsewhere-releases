package dev.elsewhere.ironpeak;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Hearthstone: a glowing heater stone. Players within {@link #RADIUS} blocks are spared Thin Air's cold (see ThinAir). */
public class HearthstoneBlock extends Block {
	public static final int RADIUS = 6;

	public HearthstoneBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(3) == 0) {
			level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.02, pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0.01, 0);
		}
		if (random.nextInt(6) == 0) {
			level.addParticle(ParticleTypes.WHITE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0, 0.03, 0);
		}
	}
}
