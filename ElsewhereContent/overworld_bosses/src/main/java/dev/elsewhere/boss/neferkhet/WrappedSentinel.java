package dev.elsewhere.boss.neferkhet;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Wrapped Sentinel (BOSSES.md supporting mobs): a husk in linen wraps that guards the pyramid. Its first hit
 * inflicts Hunger II; it rises again once unless killed with fire. Drops Linen Wrap.
 */
public class WrappedSentinel extends Husk {
	private boolean risen;
	private boolean hitOnce;

	public WrappedSentinel(EntityType<? extends WrappedSentinel> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ARMOR, 4.0).add(Attributes.ATTACK_DAMAGE, 5.0);
	}

	@Override
	public boolean doHurtTarget(ServerLevel level, Entity target) {
		boolean hit = super.doHurtTarget(level, target);
		if (hit && !this.hitOnce && target instanceof LivingEntity le) {
			this.hitOnce = true;
			le.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 1), this);
		}
		return hit;
	}

	@Override
	public void die(DamageSource source) {
		boolean fire = source.is(DamageTypeTags.IS_FIRE) || isOnFire();
		if (!this.risen && !fire && level() instanceof ServerLevel sl) {
			this.risen = true;
			setHealth(getMaxHealth() * 0.5F);
			sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()), getX(), getY() + 1, getZ(), 30, 0.3, 0.6, 0.3, 0.1);
			playSound(SoundEvents.HUSK_CONVERTED_TO_ZOMBIE, 1.0F, 0.6F);
			return;
		}
		super.die(source);
	}

	public boolean hasRisen() {
		return this.risen;
	}

	@Override
	protected boolean convertsInWater() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Risen", this.risen);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.risen = input.getBooleanOr("Risen", false);
	}
}
