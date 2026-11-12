package dev.elsewhere.petalwind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Skywhale: a gentle blossom-backed sky whale (replaces the happy-ghast Cloud Whale). It behaves like a happy ghast:
 * feed it snowballs, put a harness on it and ride it.
 */
public class Skywhale extends HappyGhast {
	public Skywhale(EntityType<? extends HappyGhast> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return HappyGhast.createAttributes().add(Attributes.MAX_HEALTH, 30.0);
	}

	public static boolean checkWhaleSpawnRules(EntityType<Skywhale> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		return pos.getY() > 100 && level.getBlockState(pos).isAir() && level.getBlockState(pos.above(3)).isAir() && random.nextInt(4) == 0;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && this.tickCount % 6 == 0) {
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY() + getBbHeight(), getZ(), 1, 1.2, 0.2, 1.2, 0);
		}
	}
}
