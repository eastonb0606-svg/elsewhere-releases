package dev.elsewhere.boss.hrolf;

import dev.elsewhere.stub.verdigris_ironpeak.BossGuards;
import dev.elsewhere.stub.verdigris_ironpeak.Telegraph;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Grimhorn, Hrolf's war goat (goat x3, HP 180, armor 10, toughness 2, knockback resistance 1). It paws the ground for
 * 1.5 s (yellow line), then rams straight across the arena: 44 raw and a launch for anyone in the way. A wall impact
 * stuns it for 3 s. It stops at the open cliff edge; players it hits there may not.
 */
public class GrimhornEntity extends Monster {
	public static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(GrimhornEntity.class, EntityDataSerializers.INT);
	public static final int IDLE = 0, WINDUP = 1, CHARGE = 2, STUNNED = 3;
	public static final float RAM_RAW = 44;
	private int stateTicks, cooldown = 60;
	private Vec3 dir = Vec3.ZERO;
	private final Set<UUID> hit = new HashSet<>();
	public @Nullable HrolfBoss rider;
	public int chargeEvery = 120;
	private @Nullable UUID riderId;

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput out) {
		super.addAdditionalSaveData(out);
		if (rider != null) out.putString("RiderUUID", rider.getUUID().toString());
		else if (riderId != null) out.putString("RiderUUID", riderId.toString());
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput in) {
		super.readAdditionalSaveData(in);
		String id = in.getStringOr("RiderUUID", "");
		try {
			riderId = id.isEmpty() ? null : UUID.fromString(id);
		} catch (IllegalArgumentException e) {
			riderId = null;
		}
	}

	public GrimhornEntity(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		setPersistenceRequired();
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 180).add(Attributes.ARMOR, 10).add(Attributes.ARMOR_TOUGHNESS, 2)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 10).add(Attributes.STEP_HEIGHT, 1.1)
			.add(Attributes.FOLLOW_RANGE, 40);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_STATE, IDLE);
	}

	public int state() {
		return entityData.get(DATA_STATE);
	}

	private void setState(int s) {
		entityData.set(DATA_STATE, s);
		stateTicks = 0;
	}

	@Override
	protected void registerGoals() {}

	@Override
	public boolean removeWhenFarAway(double d) {
		return false;
	}

	@Override
	protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
		super.actuallyHurt(level, source, BossGuards.overpower(dmg));
		if (rider != null && rider.isAlive()) rider.recheckPhase(level);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (rider != null && source.getEntity() == rider) return false;
		return super.hurtServer(level, source, amount);
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (rider == null && riderId != null && tickCount % 10 == 0 && level.getEntity(riderId) instanceof HrolfBoss h && h.isAlive()) h.setMount(this);
		if (rider == null || !rider.isAlive()) {
			if (tickCount > 40 && rider == null) return;
		}
		stateTicks++;
		switch (state()) {
			case WINDUP -> {
				getNavigation().stop();
				setDeltaMovement(0, getDeltaMovement().y, 0);
				if (stateTicks % 3 == 0) Telegraph.line(level, position(), position().add(dir.scale(28)), 1.0, Telegraph.YELLOW);
				if (stateTicks % 6 == 0) level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2, getZ(), 4, 0.6, 0.1, 0.6, 0.02);
				if (stateTicks >= 30) {
					setState(CHARGE);
					hit.clear();
					level.playSound(null, blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.HOSTILE, 2.0F, 0.6F);
				}
			}
			case CHARGE -> {
				setDeltaMovement(dir.x * 0.75, getDeltaMovement().y, dir.z * 0.75);
				setYRot((float) (Math.atan2(dir.z, dir.x) * 180 / Math.PI) - 90);
				yBodyRot = getYRot();
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(0.8))) {
					if (p.isCreative() || p.isSpectator() || !hit.add(p.getUUID())) continue;
					p.hurtServer(level, damageSources().mobAttack(rider != null ? rider : this), BossGuards.scaled(level, RAM_RAW));
					p.setDeltaMovement(p.getDeltaMovement().add(dir.x * 2.2, 0.7, dir.z * 2.2));
					p.needsSync = true;
				}
				boolean atEdge = rider != null && edgeAhead();
				if (horizontalCollision && stateTicks > 3) {
					setState(STUNNED);
					level.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.HOSTILE, 1.5F, 0.6F);
					level.sendParticles(ParticleTypes.CRIT, getX(), getY() + 2, getZ(), 30, 1, 1, 1, 0.3);
				} else if (atEdge || stateTicks > 60) {
					setDeltaMovement(Vec3.ZERO);
					setState(IDLE);
					cooldown = chargeEvery;
				}
			}
			case STUNNED -> {
				getNavigation().stop();
				if (stateTicks % 10 == 0) level.sendParticles(ParticleTypes.CRIT, getX(), getY() + getBbHeight(), getZ(), 6, 0.4, 0.2, 0.4, 0.1);
				if (stateTicks >= 60) {
					setState(IDLE);
					cooldown = chargeEvery / 2;
				}
			}
			default -> {
				ServerPlayer target = rider != null ? rider.nearestFighter(this) : null;
				if (target == null) return;
				getLookControl().setLookAt(target, 30, 30);
				if (--cooldown <= 0) {
					dir = target.position().subtract(position()).multiply(1, 0, 1).normalize();
					setState(WINDUP);
				}
			}
		}
	}

	private boolean edgeAhead() {
		Vec3 ahead = position().add(dir.scale(2.0));
		double dx = ahead.x - (rider.arenaCenter().getX() + 0.5), dz = ahead.z - (rider.arenaCenter().getZ() + 0.5);
		return dx * dx + dz * dz > (ProvingGroundsArena.RADIUS - 0.5) * (ProvingGroundsArena.RADIUS - 0.5);
	}

	public boolean isStunned() {
		return state() == STUNNED;
	}

	@Override
	public Vec3 getPassengerRidingPosition(net.minecraft.world.entity.Entity passenger) {
		return position().add(0, getBbHeight() * 0.85, 0);
	}
}
