package dev.elsewhere.glasswaste;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Sandswimmer: a 5-segment sand eel (Ankhara's juvenile). It swims a block under the sand leaving a falling-dust wake,
 * erupts in an arc to bite, then dives. Untouchable while submerged. HP 30, damage 7.
 */
public class Sandswimmer extends Monster implements Burrowing {
	private static final EntityDataAccessor<Boolean> DATA_SUBMERGED = SynchedEntityData.defineId(Sandswimmer.class, EntityDataSerializers.BOOLEAN);
	public static final int SEGMENTS = 5;
	private int surfacedTicks, eruptCooldown = 40;
	private Vec3 wander = Vec3.ZERO;

	public Sandswimmer(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 8;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30.0).add(Attributes.ATTACK_DAMAGE, 7.0).add(Attributes.ARMOR, 4.0)
			.add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 28.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_SUBMERGED, true);
	}

	@Override
	protected void registerGoals() {
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isBurrowed() {
		return this.entityData.get(DATA_SUBMERGED);
	}

	private void setSubmerged(boolean submerged) {
		this.entityData.set(DATA_SUBMERGED, submerged);
		this.noPhysics = submerged;
		this.setNoGravity(submerged);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isBurrowed() && !source.isCreativePlayer()) return false;
		return super.hurtServer(level, source, damage);
	}

	@Override
	public void tick() {
		this.noPhysics = isBurrowed();
		super.tick();
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		LivingEntity target = this.getTarget();
		int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(getX()), Mth.floor(getZ()));
		if (isBurrowed()) {
			Vec3 goal;
			if (target != null && target.isAlive()) goal = target.position();
			else {
				if (wander == Vec3.ZERO || this.position().distanceToSqr(wander) < 4 || this.random.nextInt(200) == 0) {
					wander = this.position().add(this.random.nextGaussian() * 12, 0, this.random.nextGaussian() * 12);
				}
				goal = wander;
			}
			Vec3 flat = new Vec3(goal.x - getX(), 0, goal.z - getZ());
			double speed = target != null ? 0.22 : 0.08;
			Vec3 move = flat.lengthSqr() > 0.01 ? flat.normalize().scale(speed) : Vec3.ZERO;
			double wantY = ground - 1.2;
			this.setDeltaMovement(move.x, (wantY - getY()) * 0.3, move.z);
			if (move.lengthSqr() > 0) this.setYRot((float) (Mth.atan2(move.z, move.x) * (180F / Math.PI)) - 90.0F);
			this.yBodyRot = this.getYRot();
			if (this.tickCount % (move.lengthSqr() > 0 ? 4 : 8) == 0) wake(level, ground); // P13: was every 2nd tick, moving or not
			if (eruptCooldown > 0) eruptCooldown--;
			if (target != null && eruptCooldown <= 0 && flat.length() < 4.5) {
				setSubmerged(false);
				Vec3 dir = flat.normalize();
				this.setPos(getX(), ground + 0.1, getZ());
				this.setDeltaMovement(dir.x * 0.5, 0.75, dir.z * 0.5);
				surfacedTicks = 40;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface(level, ground)), getX(), ground + 0.5, getZ(), 30, 0.5, 0.3, 0.5, 0.2);
				this.playSound(SoundEvents.SAND_BREAK, 1.2F, 0.6F);
			}
		} else {
			if (target != null && this.getBoundingBox().inflate(0.3).intersects(target.getBoundingBox()) && surfacedTicks > 10) {
				this.doHurtTarget(level, target);
				surfacedTicks = Math.min(surfacedTicks, 10);
			}
			if (--surfacedTicks <= 0 && (this.onGround() || getY() <= ground + 0.2)) {
				setSubmerged(true);
				eruptCooldown = 60 + this.random.nextInt(40);
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, surface(level, ground)), getX(), ground + 0.3, getZ(), 20, 0.4, 0.2, 0.4, 0.1);
			}
		}
	}

	private BlockState surface(ServerLevel level, int ground) {
		BlockState s = level.getBlockState(BlockPos.containing(getX(), ground - 1, getZ()));
		return s.isAir() ? Blocks.SAND.defaultBlockState() : s;
	}

	private void wake(ServerLevel level, int ground) {
		level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, surface(level, ground)), getX(), ground + 0.1, getZ(), 2, 0.3, 0.05, 0.3, 0);
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return !isBurrowed();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.SILVERFISH_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SILVERFISH_DEATH;
	}
}
