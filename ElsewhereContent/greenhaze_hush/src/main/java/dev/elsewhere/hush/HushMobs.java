package dev.elsewhere.hush;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** The Hush's mobs (DIMENSIONS.md 6). All of them hunt by sound. */
public final class HushMobs {
	private HushMobs() {}

	// ============================================================================================ Echo Stalker

	/** Blind: ignores sight, targets the latest vibration within 24, teleports toward loud events. Homes in on Resonance >= 50. */
	public static class EchoStalker extends Monster {
		public static final EntityDataAccessor<Integer> LISTENING = SynchedEntityData.defineId(EchoStalker.class, EntityDataSerializers.INT);
		private Vec3 heard;
		private int teleportCooldown, forgetTicks;

		public EchoStalker(EntityType<? extends EchoStalker> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.ATTACK_DAMAGE, 10)
				.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FOLLOW_RANGE, 32).add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder b) {
			super.defineSynchedData(b);
			b.define(LISTENING, 0);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(0, new FloatGoal(this));
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
			targetSelector.addGoal(1, new HurtByTargetGoal(this));
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			if (teleportCooldown > 0) teleportCooldown--;
			int listening = entityData.get(LISTENING);
			if (listening > 0) entityData.set(LISTENING, listening - 1);
			// Resonance >= 50 draws them in from far away
			ServerPlayer hunted = null;
			for (ServerPlayer p : level.players()) {
				if (!p.isSpectator() && !p.isCreative() && Resonance.get(p) >= GearStats.RES_STALKERS && p.distanceToSqr(this) < 64 * 64) { hunted = p; break; }
			}
			HushNoise.Noise n = HushNoise.latest(level, position(), 24, 40);
			Vec3 goal = hunted != null ? hunted.position() : n != null ? n.pos() : null;
			if (goal != null) {
				heard = goal;
				forgetTicks = 100;
				entityData.set(LISTENING, 20);
				if (n != null && n.source() instanceof LivingEntity src && src != this && !(src instanceof EchoStalker)
					&& !(src instanceof Player pl && (pl.isCreative() || pl.isSpectator())) && src.distanceToSqr(goal) < 9) setTarget(src);
				if (hunted != null) setTarget(hunted);
			} else if (forgetTicks > 0 && --forgetTicks == 0) {
				heard = null;
				if (getTarget() instanceof Player p && p.isShiftKeyDown()) setTarget(null); // sneak away and it loses you
			}
			if (heard != null && getTarget() == null && (tickCount % 10 == 0 || getNavigation().isDone())) getNavigation().moveTo(heard.x, heard.y, heard.z, 1.0);
			if (heard != null && teleportCooldown == 0 && heard.distanceToSqr(position()) > 14 * 14) teleportToward(level, heard);
		}

		private void teleportToward(ServerLevel level, Vec3 to) {
			for (int i = 0; i < 12; i++) {
				double x = to.x + (random.nextDouble() - 0.5) * 8, z = to.z + (random.nextDouble() - 0.5) * 8;
				double y = to.y + random.nextInt(5) - 2;
				Vec3 old = position();
				if (randomTeleport(x, y, z, true, (java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState>) st -> false)) {
					level.sendParticles(ParticleTypes.SCULK_SOUL, old.x, old.y + 1, old.z, 10, 0.3, 1, 0.3, 0.02);
					level.playSound(null, blockPosition(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.HOSTILE, 1.5F, 0.6F);
					teleportCooldown = 100;
					return;
				}
			}
			teleportCooldown = 40;
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.WARDEN_TENDRIL_CLICKS;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.SCULK_SHRIEKER_SHRIEK;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.SCULK_CATALYST_BLOOM;
		}
	}

	// ============================================================================================ Shrieker Swarm

	/** Bat-sized flutterers with sculk-sensor tendrils; they shriek at noise, adding +8 Resonance to nearby players. */
	public static class ShriekerSwarm extends Monster {
		private int shriekCooldown;
		private Vec3 anchor;

		public ShriekerSwarm(EntityType<? extends ShriekerSwarm> type, Level level) {
			super(type, level);
			setNoGravity(true);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 6).add(Attributes.FLYING_SPEED, 0.5)
				.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.ATTACK_DAMAGE, 0);
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			if (anchor == null) anchor = position();
			if (shriekCooldown > 0) shriekCooldown--;
			HushNoise.Noise n = HushNoise.latest(level, position(), 12, 10);
			Vec3 want = anchor.add(Math.sin(tickCount * 0.07 + getId()) * 4, Math.sin(tickCount * 0.11 + getId()) * 1.5, Math.cos(tickCount * 0.05 + getId()) * 4);
			if (n != null && !(n.source() instanceof ShriekerSwarm)) {
				want = n.pos().add(0, 2, 0);
				if (shriekCooldown == 0) shriek(level);
			}
			Vec3 d = want.subtract(position());
			if (d.lengthSqr() > 0.04) setDeltaMovement(getDeltaMovement().scale(0.7).add(d.normalize().scale(0.06)));
			if (getDeltaMovement().horizontalDistanceSqr() > 1e-4) setYRot((float) (Math.atan2(-getDeltaMovement().x, getDeltaMovement().z) * 180 / Math.PI));
		}

		void shriek(ServerLevel level) {
			shriekCooldown = 100;
			level.playSound(null, blockPosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, SoundSource.HOSTILE, 1.2F, 1.8F);
			level.sendParticles(ParticleTypes.SCULK_SOUL, getX(), getY(), getZ(), 4, 0.2, 0.2, 0.2, 0.02);
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(8))) Resonance.addRaw(p, GearStats.RES_SHRIEKER * dev.elsewhere.greenhaze_hush.s3.groups.Swarm3.weight(this)); // a swarm shrieks for the bats it holds (s-spawngroups)
		}

		@Override
		public boolean causeFallDamage(double distance, float multiplier, DamageSource source) {
			return false;
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.BAT_AMBIENT;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.BAT_HURT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.BAT_DEATH;
		}

		@Override
		public float getVoicePitch() {
			return 0.6F;
		}
	}

	// ============================================================================================ Ossuary Chorister

	/** A candle-crowned skeleton singer. Its hymn masks your noise within 16; killing it spikes +20 Resonance. */
	public static class OssuaryChorister extends Monster {
		private static final List<OssuaryChorister> SINGING = new ArrayList<>();

		public OssuaryChorister(EntityType<? extends OssuaryChorister> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 4).add(Attributes.MOVEMENT_SPEED, 0.2);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(0, new FloatGoal(this));
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, false));
			goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.6, 0.005F));
			targetSelector.addGoal(1, new HurtByTargetGoal(this));
		}

		public static boolean masks(ServerPlayer p) {
			SINGING.removeIf(c -> c.isRemoved() || !c.isAlive());
			for (OssuaryChorister c : SINGING) if (c.level() == p.level() && c.distanceToSqr(p) <= 16 * 16) return true;
			return false;
		}

		@Override
		public void tick() {
			super.tick();
			if (level() instanceof ServerLevel sl) {
				if (!SINGING.contains(this)) SINGING.add(this);
				if (tickCount % 40 == 0) {
					float pitch = (float) Math.pow(2.0, (random.nextInt(12) - 12) / 12.0);
					sl.playSound(null, blockPosition(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 0.8F, pitch);
					sl.sendParticles(ParticleTypes.NOTE, getX(), getY() + 2.2, getZ(), 1, 0.3, 0.1, 0.3, 1);
				}
			}
		}

		@Override
		public void die(DamageSource source) {
			super.die(source);
			SINGING.remove(this);
			if (level() instanceof ServerLevel sl) {
				for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(16))) Resonance.addRaw(p, GearStats.RES_CHORISTER_KILL);
				sl.playSound(null, blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 2F, 0.5F);
			}
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.SKELETON_AMBIENT;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.SKELETON_HURT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.SKELETON_DEATH;
		}
	}

	// ============================================================================================ Catalyst Mimic

	/** Looks like a sculk catalyst block. Opens near noisy players and fires Levitation bullets; deadly in shafts. */
	public static class CatalystMimic extends Monster {
		public static final EntityDataAccessor<Integer> OPEN = SynchedEntityData.defineId(CatalystMimic.class, EntityDataSerializers.INT);
		private int shotCooldown;

		public CatalystMimic(EntityType<? extends CatalystMimic> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.ARMOR, 6)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0).add(Attributes.MOVEMENT_SPEED, 0).add(Attributes.FOLLOW_RANGE, 16);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder b) {
			super.defineSynchedData(b);
			b.define(OPEN, 0);
		}

		public int openness() {
			return entityData.get(OPEN);
		}

		@Override
		public boolean isPushable() {
			return false;
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			setYRot(Math.round(getYRot() / 90F) * 90F);
			yBodyRot = getYRot();
			if (shotCooldown > 0) shotCooldown--;
			ServerPlayer target = null;
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(12))) {
				if (p.isCreative() || p.isSpectator()) continue;
				if (!p.isShiftKeyDown() || Resonance.get(p) > 20 || p.distanceToSqr(this) < 9) { target = p; break; }
			}
			int open = openness();
			if (target != null) {
				if (open < 10) entityData.set(OPEN, open + 1);
				if (open == 0) level.playSound(null, blockPosition(), SoundEvents.SHULKER_OPEN, SoundSource.HOSTILE, 1F, 0.6F);
				if (open >= 10 && shotCooldown == 0) {
					shotCooldown = 60;
					level.addFreshEntity(new ShulkerBullet(level, this, target, Direction.Axis.Y));
					level.playSound(null, blockPosition(), SoundEvents.SHULKER_SHOOT, SoundSource.HOSTILE, 2F, 0.6F);
				}
			} else if (open > 0) {
				entityData.set(OPEN, open - 1);
				if (open == 1) level.playSound(null, blockPosition(), SoundEvents.SHULKER_CLOSE, SoundSource.HOSTILE, 1F, 0.6F);
			}
		}

		@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
			if (openness() < 5 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_ARMOR)) amount *= 0.25F; // closed shell
			return super.hurtServer(level, source, amount);
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.SCULK_CATALYST_BLOOM;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.SHULKER_HURT_CLOSED;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.SHULKER_DEATH;
		}
	}

	// ============================================================================================ Mute Archivist

	/** The Silent Monastery trader: a villager in black who never speaks. */
	public static class MuteArchivist extends net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader {
		public MuteArchivist(EntityType<? extends MuteArchivist> type, Level level) {
			super(type, level);
			setDespawnDelay(0);
		}

		@Override
		protected void updateTrades(ServerLevel level) {
			HushTrades.archivist(level, blockPosition(), getOffers());
		}

		@Override
		public void aiStep() {
			super.aiStep();
			setDespawnDelay(0);
		}

		@Override
		public boolean removeWhenFarAway(double d) {
			return false;
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return null;
		}

		@Override
		protected SoundEvent getTradeUpdatedSound(boolean yes) {
			return SoundEvents.WOOL_PLACE;
		}

		@Override
		public SoundEvent getNotifyTradeSound() {
			return SoundEvents.WOOL_STEP;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.WOOL_HIT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.WOOL_BREAK;
		}
	}

	static BlockPos unused() { return BlockPos.ZERO; }
}
