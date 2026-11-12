package dev.elsewhere.palehollow.entity;

import dev.elsewhere.palehollow.GearStats;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Eyeblossom Wisp: a floating flower-eye, the reverse of Stillness. Stare at it for 1 s and it shrieks (Darkness 5 s
 * for everyone near) and attacks; ignore it and it drifts close and nibbles your XP.
 */
public class EyeblossomWisp extends Monster {
	private static final EntityDataAccessor<Integer> DATA_SHRIEK = SynchedEntityData.defineId(EyeblossomWisp.class, EntityDataSerializers.INT);
	private final Map<UUID, Integer> stare = new HashMap<>();
	private @Nullable BlockPos anchor;
	private int attackCooldown;
	private int nibbleCooldown;

	public EyeblossomWisp(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
		this.setNoGravity(true);
		this.xpReward = 4;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.WISP_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, GearStats.WISP_DAMAGE)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
			.add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SHRIEK, 0);
	}

	public int shriekTicks() {
		return this.entityData.get(DATA_SHRIEK);
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean causeFallDamage(final double fallDistance, final float damageModifier, final DamageSource source) {
		return false;
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.anchor == null) {
			this.anchor = this.blockPosition();
		}
		int sh = this.shriekTicks();
		if (sh > 0) {
			this.entityData.set(DATA_SHRIEK, sh - 1);
		}
		if (this.attackCooldown > 0) {
			this.attackCooldown--;
		}
		if (this.nibbleCooldown > 0) {
			this.nibbleCooldown--;
		}
		// staring
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(16.0), pl -> !pl.isSpectator() && !pl.isCreative())) {
			boolean looking = this.isLookingAtMe(p, 0.06, true, false, this.getEyeY());
			int s = looking ? this.stare.merge(p.getUUID(), 1, Integer::sum) : 0;
			if (!looking) {
				this.stare.remove(p.getUUID());
			}
			if (s >= 20) {
				this.shriek(level, p);
				this.stare.clear();
				break;
			}
		}
		LivingEntity target = this.getTarget();
		Vec3 goal;
		if (target != null && target.isAlive()) {
			goal = target.getEyePosition().add(0.0, -0.3, 0.0);
			if (this.distanceToSqr(target) < 2.2 && this.attackCooldown <= 0) {
				this.doHurtTarget(level, target);
				this.attackCooldown = 20;
			}
		} else {
			Player near = level.getNearestPlayer(this, 10.0);
			if (near != null && !near.isCreative() && !near.isSpectator()) {
				goal = near.getEyePosition().add(near.getViewVector(1.0F).scale(-1.5));
				if (this.distanceToSqr(near) < 9.0 && this.nibbleCooldown <= 0 && near.totalExperience > 0) {
					near.giveExperiencePoints(-1);
					this.nibbleCooldown = 40;
					level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.HOSTILE, 0.4F, 0.5F);
				}
			} else {
				double t = this.tickCount * 0.03 + this.getId();
				goal = Vec3.atCenterOf(this.anchor).add(Math.cos(t) * 4.0, 2.0 + Math.sin(t * 1.7), Math.sin(t) * 4.0);
			}
		}
		Vec3 d = goal.subtract(this.position());
		double len = d.length();
		double speed = target != null ? 0.11 : 0.05;
		Vec3 v = this.getDeltaMovement().scale(0.85);
		if (len > 0.3) {
			v = v.add(d.scale(speed / len));
		}
		this.setDeltaMovement(v);
		if (len > 0.1) {
			this.setYRot((float)(Math.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F);
			this.yBodyRot = this.getYRot();
		}
	}

	private void shriek(final ServerLevel level, final ServerPlayer starer) {
		this.entityData.set(DATA_SHRIEK, 30);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.4F, 1.6F);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(12.0))) {
			if (!Gaze.gazeImmune(p)) {
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0), this);
			}
		}
		this.setTarget(starer);
	}

	@Override
	protected void dropCustomDeathLoot(final ServerLevel level, final DamageSource source, final boolean killedByPlayer) {
		super.dropCustomDeathLoot(level, source, killedByPlayer);
		for (net.minecraft.world.item.ItemStack extra : dev.elsewhere.palehollow.ArsenalLinks.wispExtras(this.random)) {
			this.spawnAtLocation(level, extra);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.ALLAY_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ALLAY_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.6F;
	}
}
