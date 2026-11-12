package dev.elsewhere.boss.neferkhet;

import dev.elsewhere.overworld_bosses.OwbSounds;
import dev.elsewhere.stub.overworld_bosses.BossDamage;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * {@code elsewhere:sun_lance}: a beam from a sun idol to a player. It tracks the player for a 2 s visible charge
 * (thin, flickering), then fires (wide, white-gold): if nothing blocks the line from the idol, the player takes the
 * hit (38 raw, a telegraphed big hit) and is set alight. Step behind a pillar to break it.
 */
public class SunLance extends Entity {
	public static final int CHARGE_TICKS = 40;
	public static final int FIRE_TICKS = 8;
	private static final EntityDataAccessor<Float> END_X = SynchedEntityData.defineId(SunLance.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> END_Y = SynchedEntityData.defineId(SunLance.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Float> END_Z = SynchedEntityData.defineId(SunLance.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SunLance.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> BLOCKED = SynchedEntityData.defineId(SunLance.class, EntityDataSerializers.BOOLEAN);

	private @Nullable NeferkhetEntity boss;
	private @Nullable LivingEntity target;
	private float damage = 38.0F;
	private boolean hitDone;

	public SunLance(EntityType<? extends SunLance> type, Level level) {
		super(type, level);
		this.noPhysics = true;
		setNoGravity(true);
	}

	public void setup(NeferkhetEntity boss, LivingEntity target, float damage) {
		this.boss = boss;
		this.target = target;
		this.damage = damage;
		Vec3 t = target.position().add(0, target.getBbHeight() * 0.6, 0);
		setEnd(t);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(END_X, 0F);
		builder.define(END_Y, 0F);
		builder.define(END_Z, 0F);
		builder.define(AGE, 0);
		builder.define(BLOCKED, false);
	}

	private void setEnd(Vec3 v) {
		this.entityData.set(END_X, (float) v.x);
		this.entityData.set(END_Y, (float) v.y);
		this.entityData.set(END_Z, (float) v.z);
	}

	public Vec3 getEnd() {
		return new Vec3(this.entityData.get(END_X), this.entityData.get(END_Y), this.entityData.get(END_Z));
	}

	public int getAge() {
		return this.entityData.get(AGE);
	}

	public boolean isFiring() {
		return getAge() >= CHARGE_TICKS;
	}

	public boolean isBlocked() {
		return this.entityData.get(BLOCKED);
	}

	@Override
	public void tick() {
		super.tick();
		if (!(level() instanceof ServerLevel sl)) return;
		int age = getAge() + 1;
		this.entityData.set(AGE, age);
		if (this.target == null || !this.target.isAlive() || this.boss == null || !this.boss.isAlive()) {
			discard();
			return;
		}
		Vec3 from = position();
		if (age <= CHARGE_TICKS) {
			Vec3 aim = this.target.position().add(0, this.target.getBbHeight() * 0.6, 0);
			BlockHitResult clip = sl.clip(new ClipContext(from, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			boolean blocked = clip.getType() != HitResult.Type.MISS;
			this.entityData.set(BLOCKED, blocked);
			setEnd(blocked ? clip.getLocation() : aim);
			if (age % 4 == 0) sl.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 2, 0.2, 0.2, 0.2, 0.01);
			// The visible beam: a thin gold line while charging (red when it is about to fire).
			if (age % 2 == 0) dev.elsewhere.stub.overworld_bosses.Telegraph.line(sl, from, getEnd(), 0.05,
				dev.elsewhere.stub.overworld_bosses.Telegraph.dust(age > CHARGE_TICKS - 12 ? 0xFF4020 : 0xFFD24A, 0.8F));
		}
		if (age == CHARGE_TICKS) {
			sl.playSound(null, from.x, from.y, from.z, OwbSounds.NEFERKHET_LANCE_FIRE, SoundSource.HOSTILE, 3.0F, 1.0F);
			if (!isBlocked() && !this.hitDone) {
				this.hitDone = true;
				BossDamage.hit(this.boss, this, this.target, this.damage, BossRegistry.BOSS_MAGIC, true);
				this.target.igniteForSeconds(3.0F);
			}
			Vec3 end = getEnd();
			dev.elsewhere.stub.overworld_bosses.Telegraph.line(sl, from, end, 0.3, dev.elsewhere.stub.overworld_bosses.Telegraph.dust(0xFFF6C0, 2.0F));
			sl.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
			sl.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 6, 0.3, 0.3, 0.3, 0);
		}
		if (age >= CHARGE_TICKS + FIRE_TICKS) discard();
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
	}

	@Override
	public boolean shouldBeSaved() {
		return false;
	}
}
