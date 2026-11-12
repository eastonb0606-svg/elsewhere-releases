package dev.elsewhere.gear3.s3.willo;

import dev.elsewhere.minion.MinionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
 * Marsh Wisp: the Will-o'-Lantern's minion (1 slot). A flying mote of marsh-light that keeps to its owner's shoulder instead of
 * closing in, looks at the minion target, and every 4 s recasts the owner's last damage spell at it ({@link WillOLantern#recast}).
 * It never hits anything itself. While it is out it lights the ground around it ({@link WispLights}). Everything else (follow,
 * teleport, battle timer, slots, Glimmer reserve, never saved, owner-only outline) is the summoner {@link MinionEntity} base.
 */
public class MarshWisp extends MinionEntity {
	public static final double HP = 10, FLY_SPEED = 0.6;
	private static final DustParticleOptions MIREWICK_DUST = new DustParticleOptions(0xC27FC4, 0.6F);

	/** Server: the light block this wisp keeps lit, or null. Owned by {@link WispLights}. */
	@Nullable BlockPos lightPos;

	public MarshWisp(EntityType<? extends MarshWisp> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
			.add(Attributes.MAX_HEALTH, HP)
			.add(Attributes.ARMOR, 0.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, FLY_SPEED)
			.add(Attributes.FOLLOW_RANGE, 32.0);
	}

	public @Nullable BlockPos lightPos() {
		return lightPos;
	}

	@Override
	public boolean isFlyingMinion() {
		return true;
	}

	/** The wisp deals no damage of its own; its recast is the owner's spell. */
	@Override
	public double baseDamage() {
		return 0.0;
	}

	@Override
	public int attackIntervalTicks() {
		return WillOLantern.RECAST_TICKS;
	}

	@Override
	public double attackReach() {
		return WillOLantern.RECAST_RANGE;
	}

	@Override
	protected void performAttack(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		WillOLantern.recast(level, owner, this, target);
	}

	/** Stays at its owner's shoulder and turns to the target: the echo flies along the owner's line of sight. */
	@Override
	protected void moveTowardTarget(ServerPlayer owner, LivingEntity target) {
		follow(owner);
		faceToward(target.position());
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (!isRemoved() && this.tickCount % WispLights.INTERVAL == 0) WispLights.follow(this, level);
	}

	/** Client only: a few mint glow motes and Mirewick-purple dust drift off it (spawned by each watching client, distance-culled by the particle engine). */
	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide()) return;
		if (this.random.nextInt(3) == 0)
			level().addParticle(ParticleTypes.GLOW, getRandomX(0.6), getY() + getBbHeight() * (0.2 + 0.6 * random.nextDouble()), getRandomZ(0.6), 0, 0.01, 0);
		if (this.random.nextInt(7) == 0)
			level().addParticle(MIREWICK_DUST, getRandomX(0.8), getY() + getBbHeight() * random.nextDouble(), getRandomZ(0.8), 0, 0, 0);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		if (level() instanceof ServerLevel sl) WispLights.release(this, sl, reason);
		super.remove(reason);
	}

	@Override
	protected @Nullable SoundEvent getAmbientSound() {
		return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
	}

	@Override
	public int getAmbientSoundInterval() {
		return 240;
	}

	@Override
	protected @Nullable SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ALLAY_HURT;
	}

	@Override
	protected @Nullable SoundEvent getDeathSound() {
		return SoundEvents.ALLAY_DEATH;
	}

	/** Higher and breathier than an allay, so it reads as its own thing. */
	@Override
	public float getVoicePitch() {
		return 1.45F + (this.random.nextFloat() - 0.5F) * 0.1F;
	}

	@Override
	protected float getSoundVolume() {
		return 0.45F;
	}
}
