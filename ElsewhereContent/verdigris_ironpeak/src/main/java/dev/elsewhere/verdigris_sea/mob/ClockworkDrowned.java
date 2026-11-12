package dev.elsewhere.verdigris_sea.mob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Clockwork Drowned: half drowned, half clockwork, with a steaming boiler on its back. Always carries a trident.
 * Winds up a speed burst; a hit on the boiler (from behind) makes it explode for 3.
 */
public class ClockworkDrowned extends Drowned {
	private static final EntityDataAccessor<Integer> WINDUP = SynchedEntityData.defineId(ClockworkDrowned.class, EntityDataSerializers.INT);
	public final AnimationState windupAnim = new AnimationState();
	private int burstCooldown = 160;

	public ClockworkDrowned(EntityType<? extends Drowned> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Drowned.createAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ARMOR, 6).add(Attributes.ATTACK_DAMAGE, 5);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(WINDUP, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (accessor == WINDUP && level().isClientSide()) {
			if (entityData.get(WINDUP) > 0) windupAnim.startIfStopped(tickCount);
			else windupAnim.stop();
		}
	}

	@Override
	protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		setDropChance(EquipmentSlot.MAINHAND, 0.02F);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (getMainHandItem().isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
		int w = entityData.get(WINDUP);
		if (w > 0) {
			entityData.set(WINDUP, w - 1);
			level.sendParticles(ParticleTypes.WHITE_SMOKE, getX(), getY() + 1.6, getZ(), 2, 0.2, 0.1, 0.2, 0.02);
			if (w == 1) {
				addEffect(new MobEffectInstance(MobEffects.SPEED, 50, 2));
				level.playSound(null, blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.HOSTILE, 0.8F, 1.4F);
			}
		} else if (getTarget() != null && --burstCooldown <= 0) {
			burstCooldown = 140 + random.nextInt(80);
			entityData.set(WINDUP, 20);
		}
		if (tickCount % 10 == 0) level.sendParticles(ParticleTypes.WHITE_SMOKE, getX() - Math.sin(Math.toRadians(-yBodyRot)) * -0.4, getY() + 1.9, getZ() - Math.cos(Math.toRadians(-yBodyRot)) * -0.4, 1, 0.05, 0, 0.05, 0.01);
	}

	/** True if the attacker stands behind the boiler (within 70 degrees of straight behind). */
	public boolean hitsBoiler(Entity attacker) {
		Vec3 facing = Vec3.directionFromRotation(0, yBodyRot);
		Vec3 to = attacker.position().subtract(position()).multiply(1, 0, 1);
		if (to.lengthSqr() < 1.0E-4) return false;
		return facing.dot(to.normalize()) < -0.34;
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		Entity attacker = source.getEntity();
		if (attacker != null && source.getDirectEntity() == attacker && hitsBoiler(attacker) && isAlive()) {
			level.explode(this, getX(), getY() + 1.2, getZ(), 1.2F, Level.ExplosionInteraction.NONE);
			return super.hurtServer(level, source, Math.max(amount, getHealth() + 10));
		}
		return super.hurtServer(level, source, amount);
	}

	@Override
	public boolean isSunSensitive() {
		return false;
	}

	public int windup() {
		return entityData.get(WINDUP);
	}
}
