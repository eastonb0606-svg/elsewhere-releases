package dev.elsewhere.gear3.s3.pennant;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The planted Herdcaller's Standard: a 3-block pole with a green swallow-tailed flag (client: HerdcallerStandardRenderer). It is
 * scenery for {@link Pennant.Standard}, which owns the aura and takes it down; the entity itself does nothing on the server but
 * one safety check a second (an orphan, e.g. after a record was dropped, removes itself). It cannot be hit, pushed, picked,
 * ridden, burnt or blown up, never uses portals, and is never saved. Synced: the aura radius (for the planting ring) and the
 * game time it was planted (the flag unfurls over the first 8 ticks).
 */
public class HerdcallerStandard extends Entity {
	private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(HerdcallerStandard.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Long> DATA_PLANTED = SynchedEntityData.defineId(HerdcallerStandard.class, EntityDataSerializers.LONG);
	/** Flag unfurl time (ticks). */
	public static final float UNFURL_TICKS = 8F;

	public HerdcallerStandard(EntityType<? extends HerdcallerStandard> type, Level level) {
		super(type, level);
		this.noPhysics = true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder b) {
		b.define(DATA_RADIUS, (float) Pennant.BASE_RADIUS);
		b.define(DATA_PLANTED, 0L);
	}

	void setup(float radius, long gameTime) {
		entityData.set(DATA_RADIUS, radius);
		entityData.set(DATA_PLANTED, gameTime);
	}

	void setRadius(float radius) {
		entityData.set(DATA_RADIUS, radius);
	}

	public float radius() {
		return entityData.get(DATA_RADIUS);
	}

	/** Ticks since planting (partial ticks included), from the synced planting time. */
	public float age(float partialTick) {
		long planted = entityData.get(DATA_PLANTED);
		return planted <= 0L ? tickCount + partialTick : Math.max(0F, (float) (level().getGameTime() - planted) + partialTick);
	}

	/** 0 (furled) to 1 (fully out). */
	public float unfurl(float partialTick) {
		return Mth.clamp(age(partialTick) / UNFURL_TICKS, 0F, 1F);
	}

	@Override
	public void tick() {
		// no baseTick: nothing to burn, drown, push or fall
		if (level() instanceof ServerLevel) {
			if (tickCount > Pennant.LIFETIME_TICKS + 40 || (tickCount % 20 == 10 && !Pennant.isLive(this))) discard();
			return;
		}
		clientTick();
	}

	private void clientTick() {
		Level l = level();
		if (tickCount == 2 && age(0F) < 40F) ring(l);
		if (tickCount % 20 == 5) {
			double a = random.nextDouble() * Math.PI * 2;
			l.addParticle(ParticleTypes.HAPPY_VILLAGER, getX() + Math.cos(a) * 0.35, getY() + 1.4 + random.nextDouble() * 1.2, getZ() + Math.sin(a) * 0.35, 0.0, 0.02, 0.0);
		}
	}

	/** Once, when planted: a ring of motes on the ground at the aura radius (snapped to the surface within 2 blocks). */
	private void ring(Level l) {
		double r = radius();
		int n = Mth.clamp((int) (r * Math.PI * 2 / 1.25), 16, 120);
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		int y0 = Mth.floor(getY());
		for (int i = 0; i < n; i++) {
			double a = Math.PI * 2 * i / n;
			double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
			int bx = Mth.floor(x), bz = Mth.floor(z);
			double y = getY();
			for (int dy = 2; dy >= -2; dy--) {
				mp.set(bx, y0 + dy - 1, bz);
				boolean solidBelow = !l.getBlockState(mp).getCollisionShape(l, mp).isEmpty();
				mp.set(bx, y0 + dy, bz);
				if (solidBelow && l.getBlockState(mp).getCollisionShape(l, mp).isEmpty()) {
					y = y0 + dy;
					break;
				}
			}
			l.addParticle(ParticleTypes.HAPPY_VILLAGER, x, y + 0.15, z, 0.0, 0.0, 0.0);
		}
	}

	// ---------------------------------------------------------------- inert scenery

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public PushReaction getPistonPushReaction() {
		return PushReaction.IGNORE_ENTITY;
	}

	@Override
	public boolean isIgnoringBlockTriggers() {
		return true;
	}

	@Override
	public boolean ignoreExplosion(Explosion explosion) {
		return true;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return false;
	}

	@Override
	protected boolean couldAcceptPassenger() {
		return false;
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}

	@Override
	public boolean displayFireAnimation() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {}
}
