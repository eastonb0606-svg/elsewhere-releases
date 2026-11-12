package dev.elsewhere.greenhaze;

import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** The Greenhaze's mobs (DIMENSIONS.md 7) plus Percolax's Bud Bomb minion. */
public final class GreenhazeMobs {
	private GreenhazeMobs() {}

	static boolean hasFood(Player p) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).has(DataComponents.FOOD)) return true;
		}
		return false;
	}

	static boolean hasMunchies(Player p) {
		return p.hasEffect(MobEffects.HUNGER) || (p instanceof ServerPlayer sp && Haze.get(sp) >= 10);
	}

	// ============================================================================================ Lazy Gorger

	/** A tubby green panda that naps on paths and steals food from players with the munchies, then naps again. */
	public static class LazyGorger extends PathfinderMob {
		public static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(LazyGorger.class, EntityDataSerializers.BOOLEAN);
		public static final EntityDataAccessor<Integer> CHEWING = SynchedEntityData.defineId(LazyGorger.class, EntityDataSerializers.INT);
		private int napTicks;
		public int stolen;

		public LazyGorger(EntityType<? extends LazyGorger> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, 0.15)
				.add(Attributes.ATTACK_DAMAGE, 5).add(Attributes.FOLLOW_RANGE, 16);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder b) {
			super.defineSynchedData(b);
			b.define(SLEEPING, false);
			b.define(CHEWING, 0);
		}

		public boolean isNapping() {
			return entityData.get(SLEEPING);
		}

		public void nap(int ticks) {
			napTicks = ticks;
			entityData.set(SLEEPING, true);
			getNavigation().stop();
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(0, new FloatGoal(this));
			goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.1, true));
			goalSelector.addGoal(2, new EatDroppedFoodGoal(this));
			goalSelector.addGoal(3, new StealFoodGoal(this));
			goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8, 0.002F));
			goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
			targetSelector.addGoal(1, new HurtByTargetGoal(this));
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			if (napTicks > 0) {
				napTicks--;
				getNavigation().stop();
				if (napTicks == 0 || getLastHurtByMob() != null) { napTicks = 0; entityData.set(SLEEPING, false); }
				if (tickCount % 60 == 0) level.sendParticles(ParticleTypes.NOTE, getX(), getY() + 1.6, getZ(), 1, 0.2, 0.1, 0.2, 0);
			} else if (getTarget() == null && getNavigation().isDone() && random.nextInt(900) == 0) {
				nap(400 + random.nextInt(600));
			}
			int chew = entityData.get(CHEWING);
			if (chew > 0) entityData.set(CHEWING, chew - 1);
		}

		@Override
		protected void dropCustomDeathLoot(ServerLevel level, net.minecraft.world.damagesource.DamageSource source, boolean killedByPlayer) {
			super.dropCustomDeathLoot(level, source, killedByPlayer);
			net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(dev.elsewhere.greenhaze_hush.Reg.id("gorger_fluff"))
				.filter(i -> random.nextFloat() < GearStats.GORGER_FLUFF_CHANCE).ifPresent(i -> spawnAtLocation(level, new ItemStack(i)));
		}

		void chew(ServerLevel level) {
			entityData.set(CHEWING, 40);
			level.playSound(null, blockPosition(), SoundEvents.PANDA_EAT, SoundSource.NEUTRAL, 1F, 0.8F);
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return isNapping() ? SoundEvents.PANDA_SNEEZE : SoundEvents.PANDA_AMBIENT;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.PANDA_HURT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.PANDA_DEATH;
		}
	}

	/** Steal one food item from a nearby player with the munchies, then nap. */
	static class StealFoodGoal extends Goal {
		private final LazyGorger mob;
		private Player victim;
		private int cooldown;

		StealFoodGoal(LazyGorger mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (mob.isNapping() || --cooldown > 0) return false;
			cooldown = 40;
			victim = mob.level().getNearestPlayer(mob, 10);
			return victim != null && !victim.isCreative() && !victim.isSpectator() && hasMunchies(victim) && hasFood(victim);
		}

		@Override
		public boolean canContinueToUse() {
			return victim != null && victim.isAlive() && !mob.isNapping() && mob.distanceToSqr(victim) < 256 && hasFood(victim);
		}

		@Override
		public void tick() {
			mob.getLookControl().setLookAt(victim);
			mob.getNavigation().moveTo(victim, 1.2);
			if (mob.distanceToSqr(victim) < 4.5 && mob.level() instanceof ServerLevel sl) {
				for (int i = 0; i < victim.getInventory().getContainerSize(); i++) {
					ItemStack s = victim.getInventory().getItem(i);
					if (s.has(DataComponents.FOOD)) { s.shrink(1); break; }
				}
				mob.stolen++;
				mob.chew(sl);
				mob.nap(600);
				victim = null;
			}
		}
	}

	/** Walk to dropped food and eat it (they steal Grumblegut's snacks). */
	static class EatDroppedFoodGoal extends Goal {
		private final LazyGorger mob;
		private ItemEntity food;

		EatDroppedFoodGoal(LazyGorger mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			if (mob.isNapping() || mob.tickCount % 10 != 0) return false;
			List<ItemEntity> items = mob.level().getEntitiesOfClass(ItemEntity.class, mob.getBoundingBox().inflate(12), e -> e.getItem().has(DataComponents.FOOD));
			food = items.isEmpty() ? null : items.get(0);
			return food != null;
		}

		@Override
		public boolean canContinueToUse() {
			return food != null && food.isAlive() && !mob.isNapping();
		}

		@Override
		public void tick() {
			mob.getNavigation().moveTo(food, 1.3);
			if (mob.distanceToSqr(food) < 3 && mob.level() instanceof ServerLevel sl) {
				food.getItem().shrink(1);
				if (food.getItem().isEmpty()) food.discard();
				mob.stolen++;
				mob.chew(sl);
			}
		}
	}

	// ============================================================================================ Munchie Boar

	/** Smells food in your inventory and charges, knocking food out of your hand. */
	public static class MunchieBoar extends Monster {
		public static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(MunchieBoar.class, EntityDataSerializers.BOOLEAN);
		private int chargeCooldown;

		public MunchieBoar(EntityType<? extends MunchieBoar> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.ATTACK_DAMAGE, 6)
				.add(Attributes.MOVEMENT_SPEED, 0.28).add(Attributes.FOLLOW_RANGE, 20).add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder b) {
			super.defineSynchedData(b);
			b.define(CHARGING, false);
		}

		public boolean isCharging() {
			return entityData.get(CHARGING);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(0, new FloatGoal(this));
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0, true));
			goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
			goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8));
			goalSelector.addGoal(7, new RandomLookAroundGoal(this));
			targetSelector.addGoal(1, new HurtByTargetGoal(this));
			targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, (target, level) -> target instanceof Player p && hasFood(p)));
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			LivingEntity t = getTarget();
			if (chargeCooldown > 0) chargeCooldown--;
			boolean charging = t != null && chargeCooldown == 0 && distanceToSqr(t) < 100 && distanceToSqr(t) > 9;
			if (charging && !isCharging()) {
				entityData.set(CHARGING, true);
				level.playSound(null, blockPosition(), SoundEvents.HOGLIN_ANGRY, SoundSource.HOSTILE, 1F, 1.3F);
			}
			if (isCharging()) {
				if (t != null) getNavigation().moveTo(t, 1.9);
				if (t == null || distanceToSqr(t) < 4 || tickCount % 60 == 0) { entityData.set(CHARGING, false); chargeCooldown = 80; }
			}
		}

		@Override
		public boolean doHurtTarget(ServerLevel level, Entity target) {
			boolean hit = super.doHurtTarget(level, target);
			if (hit && target instanceof Player p) {
				ItemStack held = p.getMainHandItem();
				if (held.has(DataComponents.FOOD)) {
					ItemStack knocked = held.split(1);
					ItemEntity e = new ItemEntity(level, p.getX(), p.getEyeY() - 0.3, p.getZ(), knocked);
					e.setDeltaMovement(new Vec3(random.nextGaussian() * 0.2, 0.3, random.nextGaussian() * 0.2));
					e.setPickUpDelay(40);
					level.addFreshEntity(e);
				}
			}
			return hit;
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.HOGLIN_AMBIENT;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.HOGLIN_HURT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.HOGLIN_DEATH;
		}
	}

	// ============================================================================================ Toke Toad

	/** Eats small mobs with its tongue. Lick it with an empty hand: a random strain hit, or 10% Nausea. */
	public static class TokeToad extends PathfinderMob {
		public static final EntityDataAccessor<Integer> TONGUE = SynchedEntityData.defineId(TokeToad.class, EntityDataSerializers.INT);
		private int lickCooldown;

		public TokeToad(EntityType<? extends TokeToad> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20).add(Attributes.MOVEMENT_SPEED, 0.16);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder b) {
			super.defineSynchedData(b);
			b.define(TONGUE, 0);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(0, new FloatGoal(this));
			goalSelector.addGoal(1, new PanicGoal(this, 1.3));
			goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.7, 0.01F));
			goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6));
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			int t = entityData.get(TONGUE);
			if (t > 0) entityData.set(TONGUE, t - 1);
			if (lickCooldown > 0) lickCooldown--;
			if (tickCount % 20 == 0 && t == 0) {
				for (Mob prey : level.getEntitiesOfClass(Mob.class, getBoundingBox().inflate(5), m -> m != this && m.isAlive()
					&& m.getBbWidth() <= 0.7F && m.getBbHeight() <= 0.9F && !(m instanceof TokeToad))) {
					getLookControl().setLookAt(prey);
					entityData.set(TONGUE, 10);
					prey.hurtServer(level, damageSources().mobAttack(this), 50F);
					level.playSound(null, blockPosition(), SoundEvents.FROG_EAT, SoundSource.NEUTRAL, 1F, 0.7F);
					break;
				}
			}
		}

		@Override
		protected InteractionResult mobInteract(Player player, InteractionHand hand) {
			if (!player.getItemInHand(hand).isEmpty() || hand != InteractionHand.MAIN_HAND) return super.mobInteract(player, hand);
			if (!(level() instanceof ServerLevel sl) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
			if (lickCooldown > 0) return InteractionResult.PASS;
			lickCooldown = 600;
			sl.playSound(null, blockPosition(), SoundEvents.FROG_TONGUE, SoundSource.NEUTRAL, 1F, 0.6F);
			if (random.nextFloat() < 0.1F) {
				if (dev.elsewhere.greenhaze_hush.FxPrefs.motion(sp)) sp.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0));
			} else {
				Haze.add(sp, 10, true);
				switch (random.nextInt(3)) {
					case 0 -> { sp.addEffect(new MobEffectInstance(MobEffects.SPEED, 600, 0)); sp.addEffect(new MobEffectInstance(MobEffects.HASTE, 600, 0)); }
					case 1 -> sp.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 600, 0));
					default -> sp.addEffect(new MobEffectInstance(MobEffects.HASTE, 600, 0));
				}
			}
			return InteractionResult.SUCCESS;
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.FROG_AMBIENT;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.FROG_HURT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.FROG_DEATH;
		}

		@Override
		public float getVoicePitch() {
			return 0.6F;
		}
	}

	// ============================================================================================ Hotbox Smog

	/** A rolling smoke cloud with a lazy grin: engulfs you (fast contact high plus Slowness) and splits when hit. HP 16/8/4. */
	public static class HotboxSmog extends Slime {
		public HotboxSmog(EntityType<? extends HotboxSmog> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 16).add(Attributes.MOVEMENT_SPEED, 0.2);
		}

		@Override
		protected void setCubeMobHealth(int size) {
			getAttribute(Attributes.MAX_HEALTH).setBaseValue(4.0 * size);
		}

		@Override
		protected ParticleOptions getParticleType() {
			return ParticleTypes.CAMPFIRE_COSY_SMOKE;
		}

		@Override
		public void playerTouch(Player player) {
			if (player instanceof ServerPlayer sp && isAlive() && tickCount % 10 == 0 && !sp.isCreative()) {
				Haze.add(sp, GearStats.HAZE_SMOG_ENGULF * getSize() / 4.0, false);
				sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, getSize() >= 4 ? 1 : 0));
				sp.level().sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, sp.getX(), sp.getEyeY(), sp.getZ(), 4, 0.3, 0.3, 0.3, 0.01);
			}
		}

		@Override
		protected SoundEvent getJumpSound() {
			return SoundEvents.WOOL_STEP;
		}

		@Override
		protected SoundEvent getSquishSound() {
			return SoundEvents.WOOL_STEP;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource source) {
			return SoundEvents.FIRE_EXTINGUISH;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.FIRE_EXTINGUISH;
		}
	}

	// ============================================================================================ Paranoia Shade

	/** A flickering shadow with too many eyes. Only Baked+ players can see it (client renderer); it only hunts them. */
	public static class ParanoiaShade extends Monster {
		public ParanoiaShade(EntityType<? extends ParanoiaShade> type, Level level) {
			super(type, level);
			setNoGravity(true);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 8).add(Attributes.ATTACK_DAMAGE, 1)
				.add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.FLYING_SPEED, 0.4).add(Attributes.FOLLOW_RANGE, 24);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 12));
			targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false,
				(t, level) -> t instanceof ServerPlayer p && Haze.get(p) >= 60));
		}

		@Override
		public void tick() {
			noPhysics = true;
			super.tick();
			noPhysics = false;
			setNoGravity(true);
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			LivingEntity t = getTarget();
			Vec3 want;
			if (t != null) {
				want = t.getEyePosition().add(Math.sin(tickCount * 0.15) * 1.2, -0.3, Math.cos(tickCount * 0.15) * 1.2);
				if (distanceToSqr(t) < 2.2 && tickCount % 20 == 0) doHurtTarget(level, t);
				if (tickCount % 80 == 0) level.playSound(null, t.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.HOSTILE, 0.5F, 1.8F);
			} else {
				want = position().add(Math.sin(tickCount * 0.03) * 2, Math.sin(tickCount * 0.05) * 0.3, Math.cos(tickCount * 0.03) * 2);
			}
			Vec3 d = want.subtract(position());
			if (d.lengthSqr() > 0.01) setDeltaMovement(getDeltaMovement().scale(0.6).add(d.normalize().scale(0.08)));
			if (t != null) getLookControl().setLookAt(t);
		}

		@Override
		protected SoundEvent getAmbientSound() {
			return SoundEvents.VEX_AMBIENT;
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.VEX_HURT;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.VEX_DEATH;
		}

		@Override
		public float getVoicePitch() {
			return 0.5F;
		}
	}

	// ============================================================================================ Bud Bomb

	/** Percolax's minion: a puffball that swells and bursts into smoke (no block damage). */
	public static class BudBomb extends Monster {
		public static final EntityDataAccessor<Integer> SWELL = SynchedEntityData.defineId(BudBomb.class, EntityDataSerializers.INT);
		public static final int FUSE = 30;

		public BudBomb(EntityType<? extends BudBomb> type, Level level) {
			super(type, level);
		}

		public static AttributeSupplier.Builder attributes() {
			return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 10).add(Attributes.MOVEMENT_SPEED, 0.27);
		}

		@Override
		protected void defineSynchedData(SynchedEntityData.Builder b) {
			super.defineSynchedData(b);
			b.define(SWELL, 0);
		}

		public int swell() {
			return entityData.get(SWELL);
		}

		@Override
		protected void registerGoals() {
			goalSelector.addGoal(0, new FloatGoal(this));
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, false));
			goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
			targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
		}

		@Override
		public boolean doHurtTarget(ServerLevel level, Entity target) {
			return false; // it bursts instead of biting
		}

		@Override
		protected void customServerAiStep(ServerLevel level) {
			super.customServerAiStep(level);
			LivingEntity t = getTarget();
			int s = swell();
			if (t != null && distanceToSqr(t) < 9) {
				if (s == 0) level.playSound(null, blockPosition(), SoundEvents.CREEPER_PRIMED, SoundSource.HOSTILE, 1F, 1.6F);
				entityData.set(SWELL, s + 1);
			} else if (s > 0) {
				entityData.set(SWELL, s - 1);
			}
			if (swell() >= FUSE) burst(level);
		}

		public void burst(ServerLevel level) {
			level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.5, getZ(), 50, 1.5, 0.8, 1.5, 0.02);
			level.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.5, getZ(), 20, 0.6, 0.6, 0.6, 0.05);
			level.playSound(null, blockPosition(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.HOSTILE, 1.5F, 0.6F);
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(4))) {
				Haze.add(p, 8, false);
				if (!GreenhazeItems.immuneToContactHigh(p)) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
			}
			discard();
		}

		@Override
		protected SoundEvent getHurtSound(DamageSource s) {
			return SoundEvents.PUFFER_FISH_STING;
		}

		@Override
		protected SoundEvent getDeathSound() {
			return SoundEvents.PUFFER_FISH_DEATH;
		}
	}

	// ============================================================================================ Budtender (trader)

	/** The Sesh Hut trader: seeds, strains, Lucid Water. A wandering trader that never wanders off. */
	public static class Budtender extends net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader {
		public Budtender(EntityType<? extends Budtender> type, Level level) {
			super(type, level);
			setDespawnDelay(0);
		}

		@Override
		protected void updateTrades(ServerLevel level) {
			GreenhazeTrades.budtender(this, getOffers());
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
		protected void addAdditionalSaveData(ValueOutput output) {
			super.addAdditionalSaveData(output);
		}

		@Override
		protected void readAdditionalSaveData(ValueInput input) {
			super.readAdditionalSaveData(input);
		}
	}
}
