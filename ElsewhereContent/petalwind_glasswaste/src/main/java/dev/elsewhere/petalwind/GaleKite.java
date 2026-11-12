package dev.elsewhere.petalwind;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Gale Kite: a silk-paper diamond creature with a ribbon tail. It circles an anchor in the sky, fires small wind charges,
 * then dive-bombs its target. HP 14, damage 4, flying speed 0.7. It never burns in sunlight.
 */
public class GaleKite extends Monster {
	public static final byte EVENT_DIVE = 61;
	public final AnimationState diveAnimation = new AnimationState();
	private Vec3 anchor = Vec3.ZERO;
	private int diveCooldown = 80, shotCooldown = 60, diveTicks;
	private double angle;

	public GaleKite(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 14.0).add(Attributes.ATTACK_DAMAGE, 4.0)
			.add(Attributes.FLYING_SPEED, 0.7).add(Attributes.MOVEMENT_SPEED, 0.7).add(Attributes.FOLLOW_RANGE, 32.0);
	}

	public static boolean checkKiteSpawnRules(EntityType<GaleKite> type, ServerLevelAccessor level, EntitySpawnReason reason, BlockPos pos,
		RandomSource random) {
		return level.getBlockState(pos).isAir() && random.nextInt(3) == 0;
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (anchor == Vec3.ZERO) anchor = this.position().add(0, 4, 0);
		LivingEntity target = this.getTarget();
		if (target != null && target.isAlive()) anchor = new Vec3(target.getX(), target.getY() + 9, target.getZ());
		if (diveTicks > 0) {
			diveTicks--;
			if (target != null) {
				Vec3 to = target.getEyePosition().subtract(this.position()).normalize().scale(0.9);
				this.setDeltaMovement(to);
				if (this.getBoundingBox().inflate(0.4).intersects(target.getBoundingBox())) {
					this.doHurtTarget(level, target);
					diveTicks = 0;
				}
			}
			if (diveTicks == 0) diveCooldown = 100 + this.random.nextInt(60);
		} else {
			angle += 0.06;
			Vec3 want = anchor.add(Math.cos(angle) * 7, Math.sin(angle * 0.5) * 1.5, Math.sin(angle) * 7);
			Vec3 delta = want.subtract(this.position());
			this.setDeltaMovement(this.getDeltaMovement().scale(0.85).add(delta.normalize().scale(0.06 * this.getAttributeValue(Attributes.FLYING_SPEED))));
			if (target != null && --diveCooldown <= 0 && this.hasLineOfSight(target)) {
				diveTicks = 30;
				level.broadcastEntityEvent(this, EVENT_DIVE);
				this.playSound(SoundEvents.PHANTOM_SWOOP, 1.0F, 1.4F);
			}
			if (target != null && --shotCooldown <= 0 && this.distanceTo(target) < 20 && this.hasLineOfSight(target)) {
				shotCooldown = 70 + this.random.nextInt(40);
				Vec3 dir = target.position().add(0, 0.5, 0).subtract(this.position()).normalize();
				WindCharge charge = new WindCharge(level, this.getX() + dir.x, this.getY() + dir.y, this.getZ() + dir.z, dir);
				charge.setOwner(this);
				level.addFreshEntity(charge);
				this.playSound(SoundEvents.BREEZE_SHOOT, 1.0F, 1.5F);
			}
		}
		Vec3 v = this.getDeltaMovement();
		if (v.horizontalDistanceSqr() > 1.0E-4) {
			this.setYRot((float) (Mth.atan2(v.z, v.x) * (180F / Math.PI)) - 90.0F);
			this.yBodyRot = this.getYRot();
		}
		if (this.tickCount % 4 == 0) level.sendParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY(), getZ(), 1, 0.2, 0.1, 0.2, 0);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == EVENT_DIVE) diveAnimation.start(this.tickCount);
		else super.handleEntityEvent(id);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.PHANTOM_FLAP;
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
	protected float getSoundVolume() {
		return 0.6F;
	}
}
