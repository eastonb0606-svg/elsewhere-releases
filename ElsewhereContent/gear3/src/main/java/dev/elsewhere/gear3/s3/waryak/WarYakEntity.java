package dev.elsewhere.gear3.s3.waryak;

import dev.elsewhere.minion.MinionApi;
import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.minion.MinionTargeting;
import dev.elsewhere.stub.summoner.BossCheck;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The War-Yak: a shaggy Ironpeak war yak in skyiron barding. A 2-slot minion: 80 HP, armor 12. Every 1.5 s, when its
 * target is within 7 blocks, it charges: a straight run through the target and 2.5 blocks past it, trampling up to 3
 * mobs in the lane for 9 damage each (the target first). Hits go through {@link MinionEntity#hitTarget}, so the owner's
 * minion_damage, Summoner Calling, Test Meter, Ribbon / Herded bonuses and the protected-entity rules all apply; non-boss
 * mobs are tossed aside. The lane is read once per charge (one entity query every 1.5 s at most); during the run only
 * the up to 3 chosen victims are tested against the yak's box. Visuals (hoof dust while charging) are client-side.
 */
public class WarYakEntity extends MinionEntity {
	private static final EntityDataAccessor<Boolean> DATA_CHARGING = SynchedEntityData.defineId(WarYakEntity.class, EntityDataSerializers.BOOLEAN);

	private int chargeTicks, chargeTotal;
	private Vec3 chargeDir = Vec3.ZERO;
	private final List<LivingEntity> lane = new ArrayList<>(WarYak.MAX_TRAMPLE);
	private final boolean[] trampled = new boolean[WarYak.MAX_TRAMPLE];
	private int hits;
	/** Self-test: mobs hit by the last finished charge, and charges started. */
	public int lastTrampleCount, charges;

	public WarYakEntity(EntityType<? extends WarYakEntity> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, WarYak.HP)
			.add(Attributes.ARMOR, WarYak.ARMOR)
			.add(Attributes.MOVEMENT_SPEED, WarYak.SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
			.add(Attributes.STEP_HEIGHT, 1.0)
			.add(Attributes.ATTACK_DAMAGE, WarYak.DAMAGE);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		super.defineSynchedData(b);
		b.define(DATA_CHARGING, false);
	}

	public boolean isCharging() {
		return this.entityData.get(DATA_CHARGING);
	}

	// ---------------------------------------------------------------- MinionEntity

	@Override
	public boolean isFlyingMinion() {
		return false;
	}

	@Override
	public double baseDamage() {
		return WarYak.DAMAGE;
	}

	@Override
	public int attackIntervalTicks() {
		return WarYak.ATTACK_TICKS;
	}

	@Override
	public double attackReach() {
		return WarYak.CHARGE_RANGE;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		if (chargeTicks > 0) return; // a charge is never restarted mid-run (fast attack-speed buffs can't reset its lane)
		startCharge(level, owner, target);
	}

	/**
	 * Lands beside the owner, never on them: the yak's 1.3 x 1.7 box almost never fits the base class's 1-2 block random
	 * search, which then fell back to the owner's own position. Searches a ring 2.5 to 4.5 blocks out (the owner's front
	 * and sides first, then behind) for a free standing spot, and as a last resort takes the clear column 2.5 blocks out.
	 */
	@Override
	public void teleportToOwner(ServerPlayer owner) {
		Vec3 op = owner.position();
		double base = Math.toRadians(owner.getYRot());
		double[] turns = {0, 0.5, -0.5, 1.0, -1.0, 1.5, -1.5, 2.0, -2.0, Math.PI - 0.5, Math.PI + 0.5, Math.PI}; // radians off the facing
		for (double r = 2.5; r <= 4.5; r += 1.0) {
			for (double t : turns) {
				double a = base + t;
				double x = op.x - Math.sin(a) * r, z = op.z + Math.cos(a) * r;
				for (int dy : new int[] {0, 1, -1, 2, -2}) {
					BlockPos p = BlockPos.containing(x, op.y + dy, z);
					BlockPos below = p.below();
					if (!level().getBlockState(below).isFaceSturdy(level(), below, net.minecraft.core.Direction.UP)) continue;
					Vec3 at = new Vec3(x, p.getY(), z);
					if (!level().noCollision(this, getType().getDimensions().makeBoundingBox(at))) continue;
					snapTo(at.x, at.y, at.z, getYRot(), 0);
					return;
				}
			}
		}
		// no free ground nearby: still not on the owner; stand 2.5 blocks out on the facing side
		snapTo(op.x - Math.sin(base) * 2.5, op.y, op.z + Math.cos(base) * 2.5, getYRot(), 0);
	}

	/** While charging the run steers itself; no pathing toward the target or the owner. */
	@Override
	protected void moveTowardTarget(ServerPlayer owner, LivingEntity target) {
		if (chargeTicks <= 0) super.moveTowardTarget(owner, target);
	}

	@Override
	protected void follow(ServerPlayer owner) {
		if (chargeTicks <= 0) super.follow(owner);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (chargeTicks > 0 && !this.isRemoved()) chargeStep(level);
	}

	// ---------------------------------------------------------------- the charge

	/** Starts a charge at {@code target}: picks the lane's victims once (the target first, then the nearest along the run). */
	public void startCharge(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		Vec3 d = new Vec3(target.getX() - getX(), 0, target.getZ() - getZ());
		double len = d.length();
		Vec3 dir = len < 1.0E-3 ? Vec3.directionFromRotation(0F, getYRot()) : d.scale(1.0 / len);
		dir = new Vec3(dir.x, 0, dir.z).normalize();
		double run = Math.min(WarYak.CHARGE_MAX_RUN, len + WarYak.CHARGE_OVERRUN);
		chargeDir = dir;
		chargeTicks = chargeTotal = Math.max(4, Mth.ceil(run / WarYak.CHARGE_SPEED));
		hits = 0;
		lane.clear();
		java.util.Arrays.fill(trampled, false);
		if (MinionTargeting.isValidTarget(owner, this, target, MinionTargeting.Reason.KEEP)) lane.add(target);
		AABB sweep = getBoundingBox().expandTowards(dir.scale(run)).inflate(0.4, 0.25, 0.4);
		final Vec3 start = position(), fdir = dir;
		List<LivingEntity> others = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, sweep,
			e -> e != target && e != this && MinionTargeting.isValidTarget(owner, this, e, MinionTargeting.Reason.KEEP)));
		others.sort(Comparator.comparingDouble((LivingEntity e) -> e.position().subtract(start).dot(fdir)));
		for (LivingEntity e : others) {
			if (lane.size() >= WarYak.MAX_TRAMPLE) break;
			if (e.position().subtract(start).dot(fdir) < -0.5) continue; // behind the yak
			lane.add(e);
		}
		this.entityData.set(DATA_CHARGING, true);
		charges++;
		faceRun();
		level.playSound(null, getX(), getY(), getZ(), WarYak.CHARGE, SoundSource.NEUTRAL, 1.2F, 0.9F + this.random.nextFloat() * 0.2F);
	}

	private void faceRun() {
		float yaw = (float) (Mth.atan2(chargeDir.z, chargeDir.x) * Mth.RAD_TO_DEG) - 90F;
		setYRot(yaw);
		this.yBodyRot = yaw;
		this.yHeadRot = yaw;
	}

	private void chargeStep(ServerLevel level) {
		ServerPlayer owner = ownerPlayer();
		if (owner == null) {
			endCharge(level, null);
			return;
		}
		getNavigation().stop();
		Vec3 v = getDeltaMovement();
		setDeltaMovement(chargeDir.x * WarYak.CHARGE_SPEED, v.y, chargeDir.z * WarYak.CHARGE_SPEED);
		faceRun();
		AABB box = getBoundingBox().expandTowards(chargeDir.scale(WarYak.CHARGE_SPEED)).inflate(0.5, 0.25, 0.5);
		for (int i = 0; i < lane.size(); i++) {
			if (trampled[i]) continue;
			LivingEntity t = lane.get(i);
			if (t.isAlive() && !t.isRemoved() && t.level() == level && box.intersects(t.getBoundingBox())) {
				trampled[i] = true;
				trample(level, owner, t);
			}
		}
		boolean blocked = this.horizontalCollision && chargeTotal - chargeTicks >= 2; // ran into a wall (not on the first ticks)
		if (--chargeTicks <= 0 || blocked) endCharge(level, owner);
	}

	/** One trample hit: 9 damage as the owner's minion hit; non-boss mobs are thrown aside and up. */
	public boolean trample(ServerLevel level, ServerPlayer owner, LivingEntity t) {
		if (hits >= WarYak.MAX_TRAMPLE) return false;
		boolean landed = hitTarget(level, owner, t, baseDamage());
		if (!landed) return false;
		hits++;
		if (!BossCheck.isBoss(t)) {
			Vec3 side = new Vec3(-chargeDir.z, 0, chargeDir.x);
			double s = t.position().subtract(position()).dot(side) >= 0 ? 1 : -1;
			Vec3 push = side.scale(0.7 * s).add(chargeDir.scale(0.7));
			DamageSource src = MinionApi.damageSource(level, this, owner);
			t.knockback(WarYak.TRAMPLE_KNOCKBACK, -push.x, -push.z, src, (float) baseDamage());
		}
		level.playSound(null, t.getX(), t.getY(), t.getZ(), WarYak.TRAMPLE, SoundSource.NEUTRAL, 1.0F, 0.85F + this.random.nextFloat() * 0.3F);
		level.sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 6, 0.25, 0.25, 0.25, 0.15);
		return true;
	}

	private void endCharge(ServerLevel level, ServerPlayer owner) {
		// the target is always met: a short or blocked run still hits it if it is within reach at the end
		if (owner != null && !lane.isEmpty() && !trampled[0] && hits < WarYak.MAX_TRAMPLE) {
			LivingEntity t = lane.get(0);
			if (t.isAlive() && !t.isRemoved() && t.level() == level && this.distanceTo(t) <= 2.0 + t.getBbWidth() * 0.5 + getBbWidth() * 0.5) {
				trampled[0] = true;
				trample(level, owner, t);
			}
		}
		lastTrampleCount = hits;
		chargeTicks = 0;
		lane.clear();
		this.entityData.set(DATA_CHARGING, false);
		Vec3 v = getDeltaMovement();
		setDeltaMovement(v.x * 0.3, v.y, v.z * 0.3);
	}

	/** Self-test: ticks left in the current charge (0 = not charging). */
	public int chargeTicksLeft() {
		return chargeTicks;
	}

	/** Self-test: victims picked for the current charge, in trample order. */
	public List<LivingEntity> lane() {
		return List.copyOf(lane);
	}

	// ---------------------------------------------------------------- client: hoof dust while charging (client-side particles only)

	/** Client: charge pose blend 0..1 (and last tick's), eased so the head drops and lifts smoothly. */
	public float chargeAnim, chargeAnimO;

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) return;
		chargeAnimO = chargeAnim;
		chargeAnim = Mth.clamp(chargeAnim + (isCharging() ? 0.34F : -0.2F), 0F, 1F);
		if (isCharging() && (this.tickCount & 1) == 0) {
			BlockState under = level().getBlockState(BlockPos.containing(getX(), getY() - 0.2, getZ()));
			if (!under.isAir()) {
				for (int i = 0; i < 3; i++) {
					level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, under), getX() + (this.random.nextDouble() - 0.5) * getBbWidth(), getY() + 0.1,
						getZ() + (this.random.nextDouble() - 0.5) * getBbWidth(), -getDeltaMovement().x * 2.0, 0.6, -getDeltaMovement().z * 2.0);
				}
			}
		}
	}

	// ---------------------------------------------------------------- sounds

	@Override
	protected SoundEvent getAmbientSound() {
		return WarYak.AMBIENT;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return WarYak.HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return WarYak.DEATH;
	}

	@Override
	protected void playStepSound(BlockPos pos, BlockState state) {
		this.playSound(WarYak.STEP, 0.35F, 1.0F);
	}
}
