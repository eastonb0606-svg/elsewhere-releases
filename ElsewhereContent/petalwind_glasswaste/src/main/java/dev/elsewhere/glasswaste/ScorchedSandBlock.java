package dev.elsewhere.glasswaste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** Sun-baked sand: it burns anything walking on it without boots (sneaking still saves you, like magma). */
public class ScorchedSandBlock extends Block {
	public ScorchedSandBlock(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide() && entity instanceof LivingEntity living && !entity.isSteppingCarefully() && !entity.fireImmune()
			&& living.getItemBySlot(EquipmentSlot.FEET).isEmpty() && level.getGameTime() % 10 == 0) {
			entity.hurt(level.damageSources().hotFloor(), 1.0F);
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (random.nextInt(12) == 0 && level.getBlockState(pos.above()).isAir()) {
			level.addParticle(ParticleTypes.WHITE_ASH, pos.getX() + random.nextDouble(), pos.getY() + 1.05, pos.getZ() + random.nextDouble(), 0, 0.02, 0);
		}
	}
}
