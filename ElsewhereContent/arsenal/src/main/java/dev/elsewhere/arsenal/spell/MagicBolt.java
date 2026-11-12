package dev.elsewhere.arsenal.spell;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The magic projectile base: configurable damage, speed, gravity, pierce, homing and on-hit effect. Rendered as a
 * glowing camera-facing quad with a coloured dust trail. Not saved (spells are short-lived).
 */
public class MagicBolt extends ThrowableProjectile {
	public interface OnHit {
		void apply(ServerLevel level, MagicBolt bolt, LivingEntity target);
	}

	private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(MagicBolt.class, EntityDataSerializers.INT);

	public float damage = 4;
	public float gravity = 0;
	public int pierce;
	public float homing;
	public double homingRange = 8;
	public int lifetime = 40;
	public boolean starter, moonlit;
	public OnHit onHit;
	/** 3.0: power scale captured at launch (Spells.withPowerScale, e.g. a 40% recast); applied when the bolt hits. */
	public float scale = 1F;
	private final Set<Integer> hit = new HashSet<>();

	public MagicBolt(EntityType<? extends MagicBolt> type, Level level) {
		super(type, level);
	}

	public static MagicBolt create(ServerLevel level, ServerPlayer owner, int color) {
		MagicBolt b = new MagicBolt(ArsenalEntities.MAGIC_BOLT, level);
		b.setOwner(owner);
		b.setPos(owner.getX(), owner.getEyeY() - 0.15, owner.getZ());
		b.entityData.set(COLOR, color);
		b.scale = Spells.powerScale();
		return b;
	}

	public int color() {
		return this.entityData.get(COLOR);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(COLOR, 0xA77BF3);
	}

	@Override
	protected double getDefaultGravity() {
		return gravity;
	}

	@Override
	protected float getAirDrag() {
		return 1.0F;
	}

	@Override
	public boolean isNoGravity() {
		return gravity == 0;
	}

	@Override
	public void tick() {
		if (!level().isClientSide() && homing > 0) steer();
		super.tick();
		if (level().isClientSide()) {
			Vec3 v = getDeltaMovement();
			for (int i = 0; i < 3; i++) {
				double f = i / 3.0;
				level().addParticle(new DustParticleOptions(color(), 0.8F), getX() - v.x * f, getY() - v.y * f + 0.1, getZ() - v.z * f, 0, 0, 0);
			}
		} else if (tickCount > lifetime) {
			discard();
		}
	}

	private void steer() {
		if (!(getOwner() instanceof ServerPlayer owner)) return;
		LivingEntity best = null;
		double bestD = homingRange * homingRange;
		for (Entity e : level().getEntities(this, new AABB(position(), position()).inflate(homingRange))) {
			if (!(e instanceof LivingEntity le) || Spells.friendly(owner, e) || hit.contains(e.getId())) continue;
			double d = e.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = le;
			}
		}
		if (best == null) return;
		Vec3 v = getDeltaMovement();
		double speed = v.length();
		Vec3 want = best.getBoundingBox().getCenter().subtract(position()).normalize().scale(speed);
		setDeltaMovement(v.lerp(want, homing).normalize().scale(speed));
	}

	@Override
	protected boolean canHitEntity(Entity e) {
		return super.canHitEntity(e) && !hit.contains(e.getId()) && e != getOwner();
	}

	@Override
	protected void onHitEntity(EntityHitResult r) {
		if (!(level() instanceof ServerLevel sl) || !(getOwner() instanceof ServerPlayer owner) || !(r.getEntity() instanceof LivingEntity target)) {
			if (!level().isClientSide()) discard();
			return;
		}
		hit.add(target.getId());
		float dmg = Spells.damage(owner, target, damage * scale, starter, moonlit);
		Spells.hurt(owner, this, target, dmg);
		if (onHit != null) onHit.apply(sl, this, target);
		sl.sendParticles(new DustParticleOptions(color(), 1.2F), target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.2, 0.2, 0);
		if (pierce-- <= 0) discard();
	}

	@Override
	protected void onHitBlock(BlockHitResult r) {
		super.onHitBlock(r);
		if (level() instanceof ServerLevel sl) {
			sl.sendParticles(new DustParticleOptions(color(), 1.0F), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0);
			discard();
		}
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput out) {
	}

	@Override
	protected void readAdditionalSaveData(ValueInput in) {
		lifetime = 0;
	}
}
