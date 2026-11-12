package dev.elsewhere.gear3.s3.wolfcall;

import dev.elsewhere.minion.MinionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Warg (Wolf-Call Horn): a big Hrafnholt war-wolf minion. 26 HP, armor 6, bites for 5 every 1 s (the owner's minion damage:
 * minion_damage, Summoner Calling, whip tags and so on, through {@link #hitTarget}). One minion slot. Once per second (the
 * framework's per-minion hook) it refreshes its owner's howl bonus; it marks itself aggressive while it has a target so the
 * client raises its hackles (wolf "angry" pose). Everything else (follow, teleport, targets, timers, no saving, no drops) is
 * the summoner module's {@link MinionEntity}.
 */
public class WargEntity extends MinionEntity {
	public WargEntity(EntityType<? extends WargEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, Wolfcall.WARG_HP)
			.add(Attributes.ARMOR, Wolfcall.WARG_ARMOR)
			.add(Attributes.MOVEMENT_SPEED, Wolfcall.WARG_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.3)
			.add(Attributes.ATTACK_DAMAGE, Wolfcall.WARG_DAMAGE);
	}

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	@Override
	public double baseDamage() {
		return Wolfcall.WARG_DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return Wolfcall.WARG_ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return Wolfcall.WARG_REACH;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		bite(level, owner, target);
	}

	/** One bite (the attack; public for the self-test). True if it landed. */
	public boolean bite(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		this.swingForAttack(InteractionHand.MAIN_HAND);
		boolean hit = hitTarget(level, owner, target, baseDamage());
		if (hit) level.playSound(null, getX(), getY(), getZ(), Wolfcall.WARG_GROWL, SoundSource.NEUTRAL, 0.9F, 0.9F + getRandom().nextFloat() * 0.2F);
		return hit;
	}

	@Override
	protected void minionSecond(ServerLevel level, ServerPlayer owner) {
		if (Wolfcall.refreshHowl(owner, null)) Wolfcall.howl(level, owner, this, false);
	}

	/** Hackles up while hunting: the aggressive flag is synced, the renderer reads it (angry pose, tail raised). */
	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(target);
		if (!level().isClientSide()) setAggressive(target != null);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		if (!level().isClientSide()) {
			ServerPlayer owner = ownerPlayer();
			if (owner != null) Wolfcall.refreshHowl(owner, this);
		}
	}

	// ---------------------------------------------------------------- sounds (deep "big wolf" voice, own events in sounds.json)

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		if (isAggressive()) return Wolfcall.WARG_GROWL;
		return getRandom().nextInt(3) == 0 ? Wolfcall.WARG_AMBIENT : Wolfcall.WARG_PANT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return Wolfcall.WARG_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return Wolfcall.WARG_DEATH;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 160;
	}
}
