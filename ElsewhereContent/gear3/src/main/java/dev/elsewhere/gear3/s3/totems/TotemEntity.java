package dev.elsewhere.gear3.s3.totems;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionManager;
import dev.elsewhere.minion.MinionTargeting;
import dev.elsewhere.minion.MinionUpkeep;
import dev.elsewhere.summoner.SummonerPerks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A placed minion that never moves. Built on the summoner's {@link MinionEntity} (owner, Glimmer upkeep timer, Summoner
 * Calling damage through {@link #hitTarget}, owner / minion / boss-cap hurt guards, no drops, owner-only outline), but
 * with its own server step: it pays upkeep like any minion, then runs its own timer ({@link #totemTick}); it never
 * follows, never teleports to the owner (recall leaves it standing) and never walks (speed 0, full knockback
 * resistance, not pushed by water). It crumbles when the owner is more than {@link Totems#LEASH} blocks away, leaves
 * the dimension or logs off.
 * <p>Client sync: the aimed target id (sentry head / obelisk beam), a pulse counter (one per shot, for the bolt streak
 * and the lash flash), "lit" (obelisk lens) and "ramped" (obelisk beam at full power). Rendering reads these; the server
 * never ticks anything per-frame for visuals.
 */
public abstract class TotemEntity extends MinionEntity {
	static final EntityDataAccessor<Integer> DATA_TARGET = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Integer> DATA_PULSE = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.INT);
	static final EntityDataAccessor<Boolean> DATA_LIT = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.BOOLEAN);
	static final EntityDataAccessor<Boolean> DATA_RAMPED = SynchedEntityData.defineId(TotemEntity.class, EntityDataSerializers.BOOLEAN);

	/** Where the totem being summoned right now stands (set by {@link TotemItem#place} around MinionManager.summon, which
	 *  calls {@link #teleportToOwner} once to position a new minion). Server thread only. */
	static @Nullable Vec3 pendingPos;
	static float pendingYaw;

	/** Client: tickCount at the last pulse. */
	public int clientPulseTick = -1000;
	/** Self-test: strikes that landed. */
	public int strikes;

	protected TotemEntity(EntityType<? extends TotemEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder attributes(double hp, double armor) {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, hp)
			.add(Attributes.ARMOR, armor)
			.add(Attributes.MOVEMENT_SPEED, 0.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_TARGET, -1);
		b.define(DATA_PULSE, 0);
		b.define(DATA_LIT, true);
		b.define(DATA_RAMPED, false);
	}

	// ---------------------------------------------------------------- what each totem does

	/** Called every server tick after upkeep; implementations count down their own timer and do nothing in between. */
	protected abstract void totemTick(ServerLevel level, ServerPlayer owner);

	/** Material sound (hurt, death, crumble, placement). */
	public abstract SoundEvent materialSound();

	// ---------------------------------------------------------------- MinionEntity

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		// Not reached: the totem's own step (below) replaces MinionEntity's chase-and-attack loop.
		if (hitTarget(level, owner, target, baseDamage())) strikes++;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		// Not super: MinionEntity's step follows, chases and teleports to the owner, and a totem does none of that.
		// The upkeep part is the same as the summoner's (owner gone -> discard; timer / Glimmer per second -> dismiss).
		ServerPlayer owner = ownerPlayer();
		if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level || owner.isSpectator()) {
			this.discard();
			return;
		}
		lifeTicks++;
		if (fervorTicks > 0) fervorTicks--;
		if (MinionUpkeep.expired(this)) {
			MinionManager.dismiss(this);
			return;
		}
		if (this.tickCount % 20 == 0) {
			if (!MinionUpkeep.paySecond(this, owner)) {
				MinionManager.dismiss(this);
				return;
			}
			if (this.distanceToSqr(owner) > Totems.LEASH * Totems.LEASH) {
				crumble(level);
				return;
			}
			SummonerPerks.minionSecond(this, owner);
			minionSecond(level, owner);
		}
		if (!this.isRemoved()) totemTick(level, owner);
	}

	/** A totem stays where it was put: only the placement itself moves it (MinionManager.summon calls this once). */
	@Override
	public void teleportToOwner(ServerPlayer owner) {
		Vec3 p = pendingPos;
		if (p == null) return;
		pendingPos = null;
		this.snapTo(p.x, p.y, p.z, pendingYaw, 0F);
		this.setYBodyRot(pendingYaw);
		this.setYHeadRot(pendingYaw);
		this.setDeltaMovement(Vec3.ZERO);
	}

	/** The body never turns (the head may, client-side from the aimed target). */
	@Override
	protected void tickHeadTurn(float yBodyRotTarget) {}

	@Override
	public boolean isPushedByFluid() {
		return false;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return materialSound();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return materialSound();
	}

	/** Breaks apart and leaves (leash, cap, sneak-use). */
	public void crumble(ServerLevel level) {
		level.sendParticles(ParticleTypes.POOF, getX(), getY() + getBbHeight() * 0.5, getZ(), 10, 0.25, 0.4, 0.25, 0.02);
		level.playSound(null, getX(), getY(), getZ(), materialSound(), SoundSource.NEUTRAL, 0.9F, 0.8F);
		MinionManager.dismiss(this);
	}

	// ---------------------------------------------------------------- targets (each totem's own timer calls these; no polling)

	/** Mobs this totem may hit within {@code range} (centre distance), nearest first, line of sight checked. */
	protected List<LivingEntity> targetsInRange(ServerLevel level, ServerPlayer owner, double range, int max) {
		List<LivingEntity> found = new ArrayList<>();
		double r2 = range * range;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range),
			e -> e != this && e != owner && !(e instanceof MinionEntity) && !(e instanceof Player) && this.distanceToSqr(e) <= r2)) {
			if (MinionTargeting.isValidTarget(owner, this, e, MinionTargeting.Reason.AGGRO)) found.add(e);
		}
		found.sort(Comparator.comparingDouble(this::distanceToSqr));
		List<LivingEntity> out = new ArrayList<>(Math.min(max, found.size()));
		for (LivingEntity e : found) {
			if (out.size() >= max) break;
			if (this.hasLineOfSight(e)) out.add(e);
		}
		return out;
	}

	/** One target for a sentry: keeps the current one while it stays valid, in range and in sight; else the owner's
	 *  focus (summoner pick: Herd order, what the owner hit or was hit by) when in range; else the nearest hostile. */
	protected @Nullable LivingEntity acquire(ServerLevel level, ServerPlayer owner, double range) {
		LivingEntity cur = getTarget();
		if (cur != null && reach(owner, cur, range, MinionTargeting.Reason.KEEP)) return cur;
		LivingEntity pick = MinionTargeting.pick(owner, this);
		LivingEntity t = pick != null && reach(owner, pick, range, MinionTargeting.Reason.OWNER_HIT) ? pick : null;
		if (t == null) {
			List<LivingEntity> near = targetsInRange(level, owner, range, 1);
			t = near.isEmpty() ? null : near.get(0);
		}
		setTarget(t);
		return t;
	}

	private boolean reach(ServerPlayer owner, LivingEntity e, double range, MinionTargeting.Reason why) {
		return this.distanceToSqr(e) <= range * range && MinionTargeting.isValidTarget(owner, this, e, why) && this.hasLineOfSight(e);
	}

	// ---------------------------------------------------------------- synced state

	protected void aimAt(@Nullable LivingEntity t) {
		int id = t == null ? -1 : t.getId();
		if (this.entityData.get(DATA_TARGET) != id) this.entityData.set(DATA_TARGET, id);
	}

	protected void pulse() {
		this.entityData.set(DATA_PULSE, this.entityData.get(DATA_PULSE) + 1);
	}

	protected void setLit(boolean lit) {
		if (this.entityData.get(DATA_LIT) != lit) this.entityData.set(DATA_LIT, lit);
	}

	protected void setRamped(boolean ramped) {
		if (this.entityData.get(DATA_RAMPED) != ramped) this.entityData.set(DATA_RAMPED, ramped);
	}

	public int aimTargetId() {
		return this.entityData.get(DATA_TARGET);
	}

	public int pulses() {
		return this.entityData.get(DATA_PULSE);
	}

	public boolean isLit() {
		return this.entityData.get(DATA_LIT);
	}

	public boolean isRamped() {
		return this.entityData.get(DATA_RAMPED);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
		super.onSyncedDataUpdated(key);
		if (DATA_PULSE.equals(key) && this.level().isClientSide()) clientPulseTick = this.tickCount;
	}
}
