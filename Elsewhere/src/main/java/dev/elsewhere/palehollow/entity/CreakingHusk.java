package dev.elsewhere.palehollow.entity;

import dev.elsewhere.palehollow.GearStats;
import dev.elsewhere.palehollow.StillnessMob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Creaking Husk: a tall bark husk with a moss cape and glowing resin knots. Freezes while any player watches it;
 * unwatched and idle for 5 s it roots and spreads pale moss.
 */
public class CreakingHusk extends Monster implements StillnessMob {
	private static final EntityDataAccessor<Boolean> DATA_FROZEN = SynchedEntityData.defineId(CreakingHusk.class, EntityDataSerializers.BOOLEAN);
	private int freezeTicks;
	private int idleUnwatched;

	public CreakingHusk(final EntityType<? extends Monster> type, final Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.HUSK_HEALTH)
			.add(Attributes.ATTACK_DAMAGE, GearStats.HUSK_DAMAGE)
			.add(Attributes.ARMOR, GearStats.HUSK_ARMOR)
			.add(Attributes.MOVEMENT_SPEED, 0.34)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.5)
			.add(Attributes.STEP_HEIGHT, 1.0);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(0, new FloatGoal(this));
		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.6));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 12.0F));
		this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FROZEN, false);
	}

	public boolean isFrozen() {
		return this.entityData.get(DATA_FROZEN);
	}

	@Override
	public void stillnessFreeze(final int ticks) {
		this.freezeTicks = Math.max(this.freezeTicks, ticks);
	}

	@Override
	public boolean isImmobile() {
		return super.isImmobile() || this.isFrozen();
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (this.freezeTicks > 0) {
			this.freezeTicks--;
		}
		if (this.tickCount % 2 == 0) {
			boolean frozen = this.freezeTicks > 0 || Gaze.isWatched(level, this, 32.0);
			if (frozen != this.isFrozen()) {
				this.playSound(frozen ? SoundEvents.CREAKING_FREEZE : SoundEvents.CREAKING_UNFREEZE, 1.0F, 0.75F);
				if (frozen) {
					this.getNavigation().stop();
				}
			}
			this.entityData.set(DATA_FROZEN, frozen);
			if (!frozen && this.getTarget() == null) {
				this.idleUnwatched += 2;
				if (this.idleUnwatched >= 100 && this.random.nextInt(20) == 0) {
					BlockPos below = this.blockPosition().below();
					BlockState s = level.getBlockState(below);
					if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.MUD) || s.is(Blocks.PODZOL)) {
						level.setBlockAndUpdate(below, Blocks.PALE_MOSS_BLOCK.defaultBlockState());
					}
				}
			} else {
				this.idleUnwatched = 0;
			}
		}
		if (this.isFrozen()) {
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
		}
	}

	@Override
	public boolean doHurtTarget(final ServerLevel level, final net.minecraft.world.entity.Entity target) {
		if (this.isFrozen()) {
			return false;
		}
		this.playSound(SoundEvents.CREAKING_ATTACK, 1.0F, 0.8F);
		return super.doHurtTarget(level, target);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isFrozen() ? null : SoundEvents.CREAKING_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.CREAKING_SWAY;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CREAKING_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.8F;
	}
}
