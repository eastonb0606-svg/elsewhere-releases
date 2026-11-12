package dev.elsewhere.petalwind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jspecify.annotations.Nullable;

/**
 * Cumulus: a sleepy-faced cloud blob that bounces around. Harmless; landing on top of it gives Jump Boost II, and it
 * splits into smaller puffs when it dies (HP by size, like a slime).
 */
public class Cumulus extends AbstractCubeMob {
	public Cumulus(EntityType<? extends AbstractCubeMob> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.3);
	}

	public static boolean checkCumulusSpawnRules(EntityType<Cumulus> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		return Mob.checkMobSpawnRules(type, level, reason, pos, random);
	}

	@Override
	protected void addBehaviourGoals() {
	}

	@Override
	protected void addTargetingGoals() {
	}

	@Override
	protected boolean canDealDamage() {
		return false;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (this.tickCount % 5 == 0) {
			for (Player p : level.getEntitiesOfClass(Player.class, this.getBoundingBox().move(0, this.getBbHeight() * 0.6, 0).inflate(0.1, 0.4, 0.1))) {
				if (p.getY() >= this.getY() + this.getBbHeight() * 0.7) p.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 100, 1), this);
			}
		}
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected @Nullable ParticleOptions getParticleType() {
		return ParticleTypes.CLOUD;
	}

	@Override
	protected SoundEvent getJumpSound() {
		return SoundEvents.WOOL_STEP;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.WOOL_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.WOOL_BREAK;
	}

	@Override
	protected SoundEvent getSquishSound() {
		return SoundEvents.WOOL_PLACE;
	}
}
