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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Glass Scarab: a beetle with a stained-glass shell. Swarms; when hurt it burrows (invisible, sand spray) and
 * resurfaces behind you. HP 16, damage 4, armor 6.
 */
public class GlassScarab extends Monster implements Burrowing {
	private static final EntityDataAccessor<Boolean> DATA_BURROWED = SynchedEntityData.defineId(GlassScarab.class, EntityDataSerializers.BOOLEAN);
	public static final int BURROW_TICKS = 40;
	private int burrowTicks, burrowCooldown;

	public GlassScarab(EntityType<? extends Monster> type, Level level) {
		super(type, level);
		this.xpReward = 5;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16.0).add(Attributes.ATTACK_DAMAGE, 4.0).add(Attributes.ARMOR, 6.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 24.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_BURROWED, false);
	}

	@Override
	protected void registerGoals() {
		this.goalSelector.addGoal(1, new FloatGoal(this));
		this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
		this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers(GlassScarab.class));
		this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isBurrowed() {
		return this.entityData.get(DATA_BURROWED);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		if (isBurrowed()) return false;
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && this.isAlive() && burrowCooldown <= 0 && this.random.nextFloat() < 0.6F) {
			this.entityData.set(DATA_BURROWED, true);
			this.setInvisible(true);
			burrowTicks = BURROW_TICKS;
			burrowCooldown = 120;
			spray(level);
			this.playSound(SoundEvents.SAND_BREAK, 1.0F, 0.8F);
		}
		return hurt;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (burrowCooldown > 0) burrowCooldown--;
		if (!isBurrowed()) return;
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
		if (this.tickCount % 4 == 0) spray(level);
		if (--burrowTicks <= 0) {
			LivingEntity target = this.getTarget();
			if (target != null) {
				Vec3 behind = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(2.0));
				int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(behind.x), (int) Math.floor(behind.z));
				if (Math.abs(y - target.getY()) < 4) this.teleportTo(behind.x, y, behind.z);
			}
			this.entityData.set(DATA_BURROWED, false);
			this.setInvisible(false);
			spray(level);
			this.playSound(SoundEvents.SAND_PLACE, 1.0F, 1.2F);
		}
	}

	private void spray(ServerLevel level) {
		BlockPos below = this.blockPosition().below();
		var state = level.getBlockState(below);
		if (state.isAir()) state = Blocks.SAND.defaultBlockState();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), getX(), getY() + 0.2, getZ(), 12, 0.3, 0.2, 0.3, 0.1);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.SILVERFISH_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.GLASS_HIT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.GLASS_BREAK;
	}
}
