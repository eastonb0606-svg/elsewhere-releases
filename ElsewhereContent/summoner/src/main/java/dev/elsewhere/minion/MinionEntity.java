package dev.elsewhere.minion;

import dev.elsewhere.summoner.GearStats;
import dev.elsewhere.summoner.SummonerPerks;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base of every Elsewhere minion (ARSENAL.md §5a). Owned by a player UUID; follows, teleports, picks targets from what
 * the owner hit / what hurt the owner / the nearest Enemy targeting the owner; never targets protected entities; never
 * griefs; despawns with the owner; not saved; no drops or XP; its hits are the owner's damage.
 * Subclasses give stats and the attack ({@link #performAttack}).
 */
public abstract class MinionEntity extends PathfinderMob implements OwnableEntity {
	private static final EntityDataAccessor<Optional<EntityReference<LivingEntity>>> DATA_OWNER =
		SynchedEntityData.defineId(MinionEntity.class, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE);

	/** Client hook (set by the client init): is this UUID the local player? Drives the owner-only outline. */
	public static Predicate<UUID> clientIsLocalPlayer = u -> false;
	/** Dev self-test hook: resolves fake-player owners that are not in the player list. */
	public static java.util.function.Function<UUID, ServerPlayer> testOwnerLookup = u -> null;
	public static final int OUTLINE_COLOR = 0xA77BF3;

	long summonOrder;
	private int attackCooldown;
	private int lostSightTicks;
	private @Nullable Vec3 lastOwnerPos;
	private int stuckTicks;
	private double lastDistToOwner;
	/** Ticks alive and ticks without a target (for the optional lifetime and idle-dismiss rules). */
	public int lifeTicks, idleTicks;
	/** Battle-call timer: the minion goes home when it reaches 0 (see MinionUpkeep / MinionManager.onCombat). */
	public int fervorTicks = GearStats.FERVOR_TICKS;

	protected MinionEntity(EntityType<? extends MinionEntity> type, Level level) {
		super(type, level);
		this.xpReward = 0;
		this.setPersistenceRequired();
	}

	// ---------------------------------------------------------------- subclass API

	public abstract boolean isFlyingMinion();

	/** Minions of a starter staff count as at least Calling 2 (Paper Kite). */
	public boolean isStarterMinion() {
		return false;
	}

	/** Base hit damage before minion_damage, Test Meter, Well Fed and the Moon-Eye. */
	public abstract double baseDamage();

	public abstract int attackIntervalTicks();

	/** Max distance (blocks, centre to centre) from which {@link #performAttack} can be used. */
	public abstract double attackReach();

	/** Hit the target once; use {@link #hitTarget} for the damage. */
	protected abstract void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target);

	/** Extra per-second behaviour (taunts, water healing...). */
	/** A support minion (Sporeling) never picks targets or attacks. */
	public boolean isSupport() {
		return false;
	}

	protected void minionSecond(ServerLevel level, ServerPlayer owner) {}

	// ---------------------------------------------------------------- owner

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_OWNER, Optional.empty());
	}

	public void setOwner(ServerPlayer owner) {
		this.entityData.set(DATA_OWNER, Optional.of(EntityReference.of(owner)));
	}

	@Override
	public @Nullable EntityReference<LivingEntity> getOwnerReference() {
		return this.entityData.get(DATA_OWNER).orElse(null);
	}

	public @Nullable UUID getOwnerUUID() {
		EntityReference<LivingEntity> r = getOwnerReference();
		return r == null ? null : r.getUUID();
	}

	public @Nullable ServerPlayer ownerPlayer() {
		UUID u = getOwnerUUID();
		if (u == null || !(level() instanceof ServerLevel sl)) return null;
		ServerPlayer p = sl.getServer().getPlayerList().getPlayer(u);
		return p != null ? p : testOwnerLookup.apply(u);
	}

	public boolean isOwnedBy(Entity e) {
		UUID u = getOwnerUUID();
		return u != null && e != null && u.equals(e.getUUID());
	}

	// ---------------------------------------------------------------- tick / AI

	@Override
	public void tick() {
		boolean fly = isFlyingMinion();
		if (fly) this.noPhysics = true;
		super.tick();
		if (fly) {
			this.noPhysics = false;
			this.setNoGravity(true);
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		ServerPlayer owner = ownerPlayer();
		if (owner == null || !owner.isAlive() || owner.isRemoved() || owner.level() != level || owner.isSpectator()) {
			this.discard();
			return;
		}
		if (attackCooldown > 0) attackCooldown--;
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
			SummonerPerks.minionSecond(this, owner);
			minionSecond(level, owner);
		}

		// teleport: far away, or the owner teleported inside the dimension
		double tp = SummonerPerks.teleportDistance(owner);
		Vec3 op = owner.position();
		boolean ownerJumped = lastOwnerPos != null && lastOwnerPos.distanceToSqr(op) > GearStats.OWNER_TELEPORT_JUMP * GearStats.OWNER_TELEPORT_JUMP;
		lastOwnerPos = op;
		double dist = this.distanceTo(owner);
		if (dist > tp || ownerJumped) {
			teleportToOwner(owner);
			setTarget(null);
			return;
		}

		LivingEntity target = isSupport() ? null : updateTarget(level, owner);
		idleTicks = target == null ? idleTicks + 1 : 0;
		if (target != null) {
			moveTowardTarget(owner, target);
			double reach = attackReach() + (attackReach() > 3 && dev.elsewhere.summoner.GaleRibbonItem.isTagged(owner, target) ? dev.elsewhere.summoner.GearStats.RIBBON_RANGE_BONUS : 0);
			if (attackCooldown <= 0 && this.distanceTo(target) <= reach + target.getBbWidth() * 0.5 && this.hasLineOfSight(target)) {
				attackCooldown = attackIntervalTicks();
				this.getLookControl().setLookAt(target, 30F, 30F);
				performAttack(level, owner, target);
			}
		} else {
			follow(owner);
		}

		// ground minions that make no progress toward a far owner hop next to them
		if (!isFlyingMinion() && target == null && dist > GearStats.FOLLOW_MAX * 1.5) {
			if (dist >= lastDistToOwner - 0.05) stuckTicks++;
			else stuckTicks = 0;
			if (stuckTicks > 60) {
				teleportToOwner(owner);
				stuckTicks = 0;
			}
		} else stuckTicks = 0;
		lastDistToOwner = dist;
	}

	private @Nullable LivingEntity updateTarget(ServerLevel level, ServerPlayer owner) {
		LivingEntity cur = getTarget();
		if (cur != null) {
			if (!MinionTargeting.isValidTarget(owner, this, cur, MinionTargeting.Reason.KEEP) || cur.distanceTo(owner) > GearStats.TARGET_RANGE + 4) {
				cur = null;
			} else if (!this.hasLineOfSight(cur)) {
				if (++lostSightTicks > GearStats.LOST_SIGHT_TICKS) cur = null;
			} else lostSightTicks = 0;
		}
		if (cur == null || this.tickCount % 10 == 0) {
			LivingEntity pick = MinionTargeting.pick(owner, this);
			if (pick != null && pick != cur) {
				cur = pick;
				lostSightTicks = 0;
			}
		}
		if (cur != getTarget()) setTarget(cur);
		return cur;
	}

	protected void moveTowardTarget(ServerPlayer owner, LivingEntity target) {
		double speed = SummonerPerks.speedMultiplier(owner);
		if (isFlyingMinion()) {
			// keep a ranged hover: above and a little away from the target
			double keep = Math.max(2.0, attackReach() * 0.5);
			Vec3 to = position().subtract(target.position());
			Vec3 flat = new Vec3(to.x, 0, to.z);
			Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : flat.normalize();
			Vec3 want = target.position().add(dir.scale(keep)).add(0, 2.5, 0);
			flyTo(want, flyingSpeed() * speed);
			faceToward(target.position());
		} else {
			this.getNavigation().moveTo(target, 1.2 * speed);
		}
	}

	protected void follow(ServerPlayer owner) {
		double speed = SummonerPerks.speedMultiplier(owner);
		int idx = MinionManager.indexOf(owner, this);
		int n = Math.max(1, MinionManager.count(owner));
		double ang = owner.getYRot() * Mth.DEG_TO_RAD + Math.PI + (idx - (n - 1) / 2.0) * 0.9;
		if (isFlyingMinion()) {
			double h = GearStats.FLY_HOVER_MIN + (GearStats.FLY_HOVER_MAX - GearStats.FLY_HOVER_MIN) * (0.5 + 0.5 * Math.sin(this.tickCount * 0.05 + idx));
			Vec3 want = owner.position().add(-Math.sin(ang) * 1.6, owner.getBbHeight() + h, Math.cos(ang) * 1.6);
			flyTo(want, flyingSpeed() * speed);
			faceToward(owner.position());
		} else {
			double d = this.distanceTo(owner);
			if (d > GearStats.FOLLOW_MAX) {
				Vec3 want = owner.position().add(-Math.sin(ang) * GearStats.FOLLOW_MIN, 0, Math.cos(ang) * GearStats.FOLLOW_MIN);
				this.getNavigation().moveTo(want.x, want.y, want.z, 1.1 * speed);
			} else if (d < GearStats.FOLLOW_MIN) {
				this.getNavigation().stop();
			}
			if (d < GearStats.FOLLOW_MAX) this.getLookControl().setLookAt(owner, 10F, 10F);
		}
	}

	protected double flyingSpeed() {
		return GearStats.KITE_FLY_SPEED;
	}

	protected void flyTo(Vec3 want, double speed) {
		Vec3 delta = want.subtract(position());
		double len = delta.length();
		if (len < 0.3) {
			setDeltaMovement(getDeltaMovement().scale(0.6));
			return;
		}
		Vec3 v = getDeltaMovement().scale(0.8).add(delta.scale(Math.min(1.0, len) * 0.06 * speed / Math.max(len, 1.0E-3) * Math.min(len, 4.0)));
		double max = 0.25 + 0.35 * speed;
		if (v.length() > max) v = v.normalize().scale(max);
		setDeltaMovement(v);
	}

	protected void faceToward(Vec3 at) {
		Vec3 m = getDeltaMovement();
		double dx = at.x - getX(), dz = at.z - getZ();
		if (getTarget() == null && m.horizontalDistanceSqr() > 0.004) {
			dx = m.x;
			dz = m.z;
		}
		float yaw = -((float) Mth.atan2(dx, dz)) * Mth.RAD_TO_DEG;
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	/** Teleports next to the owner (flyers above their head); safe spot search, falls back to the owner's feet. */
	public void teleportToOwner(ServerPlayer owner) {
		Vec3 op = owner.position();
		if (isFlyingMinion()) {
			moveTo(op.x, op.y + owner.getBbHeight() + GearStats.FLY_HOVER_MIN, op.z);
			return;
		}
		BlockPos base = owner.blockPosition();
		for (int i = 0; i < 16; i++) {
			int dx = this.random.nextInt(5) - 2, dz = this.random.nextInt(5) - 2, dy = this.random.nextInt(3) - 1;
			if (Math.abs(dx) < 1 && Math.abs(dz) < 1) continue;
			BlockPos p = base.offset(dx, dy, dz);
			if (!level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)) continue;
			AABB box = this.getType().getDimensions().makeBoundingBox(Vec3.atBottomCenterOf(p));
			if (!level().noCollision(this, box)) continue;
			moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
			return;
		}
		moveTo(op.x, op.y, op.z);
	}

	private void moveTo(double x, double y, double z) {
		this.teleportTo(x, y, z);
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
		this.resetFallDistance();
	}

	// ---------------------------------------------------------------- damage out

	/** Damages the target as the owner's minion hit; returns true if it landed. */
	public boolean hitTarget(ServerLevel level, ServerPlayer owner, LivingEntity target, double base) {
		if (!MinionTargeting.isValidTarget(owner, this, target, MinionTargeting.Reason.KEEP)) return false;
		float dmg = (float) MinionApi.minionDamage(owner, this, target, base);
		DamageSource src = MinionApi.damageSource(level, this, owner);
		net.minecraft.world.entity.ai.attributes.AttributeInstance armor = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
		net.minecraft.resources.Identifier armorId = net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "minion_armor_pierce");
		if (armor != null) armor.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(armorId,
			-(1 - dev.elsewhere.summoner.GearStats.MINION_ARMOR_FACTOR), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		boolean hurt;
		try {
			hurt = target.hurtServer(level, src, dmg);
		} finally {
			if (armor != null) armor.removeModifier(armorId);
		}
		if (hurt) MinionApi.LAST_HIT_DAMAGE = dmg;
		return hurt;
	}

	// ---------------------------------------------------------------- damage in

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		Entity by = source.getEntity();
		Entity direct = source.getDirectEntity();
		if (by != null && isOwnedBy(by) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
		if (by instanceof MinionEntity || direct instanceof MinionEntity) return false;
		ServerPlayer owner = ownerPlayer();
		if (owner != null && SummonerPerks.fireAndLightningImmune(owner)
			&& (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_LIGHTNING))) {
			this.clearFire();
			return false;
		}
		if (by != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && dev.elsewhere.stub.summoner.BossCheck.isBoss(by))
			damage = Math.min(damage, (float) (getMaxHealth() * dev.elsewhere.summoner.GearStats.MINION_BOSS_HIT_CAP));
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void die(DamageSource source) {
		ServerPlayer owner = ownerPlayer();
		if (owner != null && !this.isRemoved()) SummonerPerks.onMinionDied(this, owner);
		super.die(source);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		if (!level().isClientSide()) MinionManager.unregister(this);
	}

	@Override
	protected boolean shouldDropLoot(ServerLevel level) {
		return false;
	}

	@Override
	protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {}

	@Override
	protected int getBaseExperienceReward(ServerLevel level) {
		return 0;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean canBreatheUnderwater() {
		return true;
	}

	// ---------------------------------------------------------------- no griefing / no interaction

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	public boolean startRiding(Entity entity, boolean force, boolean sendEventAndTriggers) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	/** Not pickable: the owner's swings and arrows pass through, and nobody can rename, leash or mount it. */
	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean canBeCollidedWith(@Nullable Entity other) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	// ---------------------------------------------------------------- owner-only outline

	@Override
	public boolean isCurrentlyGlowing() {
		if (level().isClientSide()) {
			UUID u = getOwnerUUID();
			return u != null && clientIsLocalPlayer.test(u);
		}
		return super.isCurrentlyGlowing();
	}

	@Override
	public int getTeamColor() {
		return getTeam() == null ? OUTLINE_COLOR : super.getTeamColor();
	}
}
