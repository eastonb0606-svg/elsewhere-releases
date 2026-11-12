package dev.elsewhere.boss.mother_creak;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.core.boss.ElsewhereBoss;
import dev.elsewhere.palehollow.GearStats;
import dev.elsewhere.palehollow.StillnessMob;
import dev.elsewhere.palehollow.entity.CreakingHusk;
import dev.elsewhere.palehollow.entity.Gaze;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModParticles;
import dev.elsewhere.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Mother Creak, the Elder Heartwood (BOSSES.md #6, Tier I). Moves only while nobody watches her. While any of the four
 * wall hearts stands she cannot be hurt; breaking one stuns her for 6 s (the damage window) and advances the phase:
 * 4 hearts Stillness, 3 Root Burst, 2-1 Leaf Litter, 0 Heartwood Fury (always hittable, moves even when watched at half
 * speed, bark plates fall: armor -8).
 */
public class MotherCreak extends ElsewhereBoss implements StillnessMob {
	public static final int PHASE_STILLNESS = 1;
	public static final int PHASE_ROOT_BURST = 2;
	public static final int PHASE_LEAF_LITTER = 3;
	public static final int PHASE_FURY = 4;
	public static final int CLAW_WINDUP = 16;
	public static final int BURST_WINDUP = 20;

	private static final EntityDataAccessor<Boolean> DATA_WATCHED = SynchedEntityData.defineId(MotherCreak.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_STUN = SynchedEntityData.defineId(MotherCreak.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_CLAW = SynchedEntityData.defineId(MotherCreak.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SLAM = SynchedEntityData.defineId(MotherCreak.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_HEARTS = SynchedEntityData.defineId(MotherCreak.class, EntityDataSerializers.INT);
	private static final net.minecraft.resources.Identifier FURY_ARMOR = Elsewhere.id("mother_creak_fury");

	private final List<BlockPos> hearts = new ArrayList<>();
	private int freezeTicks;
	private int clawCooldown = 40;
	private int burstTimer = GearStats.MOTHER_CREAK_ROOT_BURST_INTERVAL;
	private int litterTimer = 240;
	private int minionTimer = 300;
	private @Nullable Vec3 burstFrom;
	private @Nullable Vec3 burstDir;
	private int burstLen;
	private int burstStep = -1;

	public MotherCreak(final EntityType<? extends Monster> type, final Level level) {
		super(type, level, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_6);
		this.xpReward = 0; // XP comes from the boss kit (always, 400)
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.MOTHER_CREAK_HEALTH)
			.add(Attributes.ARMOR, GearStats.MOTHER_CREAK_ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, GearStats.MOTHER_CREAK_TOUGHNESS)
			.add(Attributes.ATTACK_DAMAGE, GearStats.MOTHER_CREAK_CLAW)
			.add(Attributes.MOVEMENT_SPEED, 0.32)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 48.0)
			.add(Attributes.STEP_HEIGHT, 2.0);
	}

	@Override
	protected double baseMaxHealth() {
		return GearStats.MOTHER_CREAK_HEALTH;
	}

	@Override
	protected void registerGoals() {
		// Movement and attacks are scripted in bossTick.
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_WATCHED, false);
		builder.define(DATA_STUN, 0);
		builder.define(DATA_CLAW, 0);
		builder.define(DATA_SLAM, 0);
		builder.define(DATA_HEARTS, 4);
	}

	// ------------------------------------------------------------------ synced state (renderer reads these)
	public boolean isWatched() {
		return this.entityData.get(DATA_WATCHED);
	}

	public int stunTicks() {
		return this.entityData.get(DATA_STUN);
	}

	public int clawTicks() {
		return this.entityData.get(DATA_CLAW);
	}

	public int slamTicks() {
		return this.entityData.get(DATA_SLAM);
	}

	public int heartsLeft() {
		return this.entityData.get(DATA_HEARTS);
	}

	public List<BlockPos> heartPositions() {
		return this.hearts;
	}

	public void setHearts(final List<BlockPos> positions) {
		this.hearts.clear();
		this.hearts.addAll(positions);
		this.entityData.set(DATA_HEARTS, positions.size());
	}

	@Override
	public void stillnessFreeze(final int ticks) {
		this.freezeTicks = Math.max(this.freezeTicks, ticks);
	}

	@Override
	protected SoundEvent roarSound() {
		return ModSounds.MOTHER_CREAK_ROAR.value();
	}

	// ------------------------------------------------------------------ lifecycle
	@Override
	protected void onWake(final ServerLevel level) {
		if (this.hearts.isEmpty() && this.arena != null) {
			// find the wall hearts of the arena
			BlockPos c = this.arena.center;
			int r = this.arena.radius + 3;
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, 12, r))) {
				if (level.getBlockState(p).is(ModBlocks.RESIN_HEART)) {
					this.hearts.add(p.immutable());
				}
			}
		}
		this.entityData.set(DATA_HEARTS, this.countHearts(level));
		this.updatePhaseFromHearts(level, false);
		this.updateBarName();
	}

	@Override
	protected void onReset(final ServerLevel level) {
		this.entityData.set(DATA_STUN, 0);
		this.entityData.set(DATA_HEARTS, this.countHearts(level));
		AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
		if (armor != null) {
			armor.removeModifier(FURY_ARMOR);
		}
	}

	private int countHearts(final ServerLevel level) {
		int n = 0;
		for (BlockPos p : this.hearts) {
			if (level.getBlockState(p).is(ModBlocks.RESIN_HEART)) {
				n++;
			}
		}
		return n;
	}

	private void updateBarName() {
		StringBuilder hearts = new StringBuilder();
		for (int i = 0; i < this.heartsLeft(); i++) {
			hearts.append('❤');
		}
		this.bossEvent.setName(this.getDisplayName().copy().append(hearts.isEmpty() ? Component.empty() : Component.literal("  " + hearts)));
	}

	private void updatePhaseFromHearts(final ServerLevel level, final boolean stun) {
		int left = this.heartsLeft();
		int phase = left >= 4 ? PHASE_STILLNESS : left == 3 ? PHASE_ROOT_BURST : left >= 1 ? PHASE_LEAF_LITTER : PHASE_FURY;
		if (stun) {
			this.entityData.set(DATA_STUN, GearStats.MOTHER_CREAK_STUN_TICKS);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.MOTHER_CREAK_STUN.value(), SoundSource.HOSTILE, 3.0F, 0.7F);
			level.sendParticles(ModParticles.RESIN_DUST, this.getX(), this.getY() + 5.0, this.getZ(), 80, 0.6, 1.5, 0.6, 0.15);
		}
		this.setPhase(phase);
		if (phase == PHASE_FURY) {
			AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
			if (armor != null && armor.getModifier(FURY_ARMOR) == null) {
				armor.addTransientModifier(new AttributeModifier(FURY_ARMOR, -GearStats.MOTHER_CREAK_ARMOR, AttributeModifier.Operation.ADD_VALUE));
			}
		}
		this.updateBarName();
	}

	// ------------------------------------------------------------------ AI
	@Override
	protected void customServerAiStep(final ServerLevel level) {
		if (!this.isAwake()) {
			this.getNavigation().stop();
			if (this.tickCount % 10 == 0 && this.arena != null) {
				for (ServerPlayer p : this.playersInArena(level)) {
					if (!p.isCreative() && p.distanceToSqr(this) < 64.0 && Math.abs(p.getY() - this.getY()) < 5.0) {
						this.wake(level);
						break;
					}
				}
			}
			return;
		}
		super.customServerAiStep(level);
	}

	@Override
	protected void bossTick(final ServerLevel level) {
		int left = this.countHearts(level);
		if (left < this.heartsLeft()) {
			this.entityData.set(DATA_HEARTS, left);
			this.updatePhaseFromHearts(level, true);
			return;
		}
		int stun = this.stunTicks();
		if (stun > 0) {
			this.entityData.set(DATA_STUN, stun - 1);
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
			if (stun % 10 == 0) {
				level.sendParticles(ModParticles.RESIN_DUST, this.getX(), this.getY() + 4.5, this.getZ(), 6, 0.5, 0.5, 0.5, 0.05);
			}
			return;
		}
		if (this.freezeTicks > 0) {
			this.freezeTicks--;
		}
		boolean watched = this.freezeTicks > 0 || Gaze.isWatched(level, this, 48.0);
		if (watched != this.isWatched()) {
			level.playSound(null, this.getX(), this.getY(), this.getZ(), watched ? SoundEvents.CREAKING_FREEZE : SoundEvents.CREAKING_UNFREEZE,
				SoundSource.HOSTILE, 2.5F, 0.5F);
		}
		this.entityData.set(DATA_WATCHED, watched);

		ServerPlayer target = this.nearestFighter(level);
		this.setTarget(target);
		boolean fury = this.phase() == PHASE_FURY;
		boolean canMove = !watched || fury;
		if (target != null && canMove && this.clawTicks() == 0) {
			double speed = fury && watched ? 0.5 : 1.0;
			if (this.heartBeingMined(level)) {
				speed *= HEART_MINING_SPEED;
			}
			this.lastMoveSpeed = speed;
			this.getNavigation().moveTo(target, speed);
			this.getLookControl().setLookAt(target, 30.0F, 30.0F);
		} else {
			this.getNavigation().stop();
			this.setDeltaMovement(0.0, this.getDeltaMovement().y, 0.0);
		}

		this.tickClaw(level, target);
		this.tickRootBurst(level);
		if (this.phase() >= PHASE_LEAF_LITTER) {
			this.tickLeafLitter(level);
		}
		if (this.phase() >= PHASE_ROOT_BURST) {
			this.tickMinions(level);
		}
	}

	private void tickClaw(final ServerLevel level, final @Nullable ServerPlayer target) {
		int claw = this.clawTicks();
		if (claw > 0) {
			this.entityData.set(DATA_CLAW, claw - 1);
			int elapsed = CLAW_WINDUP + 8 - claw;
			if (elapsed < CLAW_WINDUP && elapsed % 4 == 0) {
				level.sendParticles(ModParticles.RESIN_DUST, this.getX(), this.getY() + 0.1, this.getZ(), 14, 1.6, 0.05, 1.6, 0.0);
			}
			if (elapsed == CLAW_WINDUP) {
				Vec3 fwd = Vec3.directionFromRotation(0.0F, this.getYRot());
				for (ServerPlayer p : this.playersInArena(level)) {
					Vec3 to = flat(p.position().subtract(this.position()));
					double d = to.length();
					if (d < 5.5 && (d < 1.5 || to.normalize().dot(fwd) > 0.3)) {
						this.hitGuarded(level, p, GearStats.MOTHER_CREAK_CLAW, this.damageSources().mobAttack(this));
						p.push(fwd.x * 1.2, 0.4, fwd.z * 1.2);
						p.needsSync = true;
					}
				}
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CREAKING_ATTACK, SoundSource.HOSTILE, 2.5F, 0.6F);
			}
			return;
		}
		if (this.clawCooldown > 0) {
			this.clawCooldown--;
		}
		if (target != null && this.clawCooldown <= 0 && flat(target.position().subtract(this.position())).length() < 5.0) {
			this.entityData.set(DATA_CLAW, CLAW_WINDUP + 8);
			this.clawCooldown = 50;
			this.getLookControl().setLookAt(target, 60.0F, 30.0F);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CREAKING_ACTIVATE, SoundSource.HOSTILE, 2.5F, 0.5F);
		}
	}

	private void tickRootBurst(final ServerLevel level) {
		int slam = this.slamTicks();
		if (slam > 0) {
			this.entityData.set(DATA_SLAM, slam - 1);
		}
		if (this.burstStep >= 0 && this.burstFrom != null && this.burstDir != null) {
			int elapsed = this.burstStep++;
			if (elapsed < BURST_WINDUP) {
				// the 1 s resin-dust line telegraph
				if (elapsed % 3 == 0) {
					for (int i = 1; i <= this.burstLen; i++) {
						Vec3 p = this.burstFrom.add(this.burstDir.scale(i));
						level.sendParticles(ModParticles.RESIN_DUST, p.x, p.y + 0.15, p.z, 2, 0.15, 0.02, 0.15, 0.0);
					}
				}
			} else {
				int i = elapsed - BURST_WINDUP + 1;
				if (i <= this.burstLen) {
					Vec3 p = this.burstFrom.add(this.burstDir.scale(i));
					RootSpike spike = ModEntities.ROOT_SPIKE.create(level, EntitySpawnReason.MOB_SUMMONED);
					if (spike != null) {
						spike.setup(p.x, Math.floor(p.y), p.z, this.getYRot(), this, GearStats.MOTHER_CREAK_ROOT_BURST);
						level.addFreshEntity(spike);
					}
				} else {
					this.burstStep = -1;
				}
			}
			return;
		}
		if (this.phase() < PHASE_ROOT_BURST) {
			return;
		}
		if (--this.burstTimer <= 0) {
			this.burstTimer = GearStats.MOTHER_CREAK_ROOT_BURST_INTERVAL;
			List<ServerPlayer> fighters = this.playersInArena(level).stream().filter(p -> !p.isCreative()).toList();
			if (!fighters.isEmpty()) {
				this.startRootBurst(level, fighters.get(this.random.nextInt(fighters.size())));
			}
		}
	}

	/** Starts a Root Burst line toward {@code target}: 1 s dust telegraph, then spikes erupt from her to past the target. */
	public void startRootBurst(final ServerLevel level, final ServerPlayer target) {
		Vec3 from = new Vec3(this.getX(), this.getY(), this.getZ());
		Vec3 dir = flat(target.position().subtract(from));
		double len = dir.length();
		if (len < 0.5) {
			dir = Vec3.directionFromRotation(0.0F, this.getYRot());
			len = 1.0;
		}
		this.burstDir = dir.normalize();
		this.burstFrom = from;
		this.burstLen = (int)Math.min(28.0, len + 4.0);
		this.burstStep = 0;
		this.entityData.set(DATA_SLAM, 30);
		this.getLookControl().setLookAt(target, 60.0F, 30.0F);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CREAKING_HEART_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
	}

	private void tickLeafLitter(final ServerLevel level) {
		if (--this.litterTimer > 0) {
			return;
		}
		this.litterTimer = 240;
		for (ServerPlayer p : this.playersInArena(level)) {
			p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0), this);
		}
		if (this.arena != null) {
			BlockPos c = this.arena.center;
			level.sendParticles(ParticleTypes.PALE_OAK_LEAVES, c.getX() + 0.5, c.getY() + 10.0, c.getZ() + 0.5, 160, 8.0, 3.0, 8.0, 0.02);
		}
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CREAKING_HEART_IDLE, SoundSource.HOSTILE, 3.0F, 0.6F);
	}

	private void tickMinions(final ServerLevel level) {
		if (--this.minionTimer > 0 || this.arena == null) {
			return;
		}
		this.minionTimer = 400;
		int cap = 2 + this.partySize();
		int alive = level.getEntitiesOfClass(CreakingHusk.class, this.getBoundingBox().inflate(this.arena.radius + 4)).size();
		if (alive >= cap) {
			return;
		}
		BlockPos c = this.arena.center;
		double a = this.random.nextDouble() * Math.PI * 2.0;
		double r = this.arena.radius - 2.0;
		CreakingHusk husk = ModEntities.CREAKING_HUSK.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (husk != null) {
			husk.snapTo(c.getX() + 0.5 + Math.cos(a) * r, c.getY(), c.getZ() + 0.5 + Math.sin(a) * r, (float)Math.toDegrees(a) + 90.0F, 0.0F);
			husk.setPersistenceRequired();
			level.addFreshEntity(husk);
			level.sendParticles(ParticleTypes.PALE_OAK_LEAVES, husk.getX(), husk.getY() + 1.5, husk.getZ(), 30, 0.5, 1.0, 0.5, 0.02);
			level.playSound(null, husk.getX(), husk.getY(), husk.getZ(), SoundEvents.CREAKING_SPAWN, SoundSource.HOSTILE, 1.5F, 0.8F);
		}
	}

	// ------------------------------------------------------------------ damage
	@Override
	protected boolean canBeHurtNow(final ServerLevel level, final DamageSource source) {
		boolean open = this.heartsLeft() == 0 || this.stunTicks() > 0;
		if (!open) {
			level.playSound(null, this.getX(), this.getY() + 4.0, this.getZ(), SoundEvents.CREAKING_HEART_HURT, SoundSource.HOSTILE, 1.5F, 0.6F);
			level.sendParticles(ModParticles.RESIN_DUST, this.getX(), this.getY() + 4.0, this.getZ(), 8, 0.6, 1.0, 0.6, 0.05);
		}
		return open;
	}

	@Override
	protected List<net.minecraft.world.item.ItemStack> extraLoot(final ServerLevel level) {
		return dev.elsewhere.palehollow.ArsenalLinks.motherCreakExtras(this.random);
	}

	/** Boss-kit drop rules: Heartwood Greatbow signature, Resin Heart Locket extra-life, Elder Bark Crown (summoner) other. */
	@Override
	protected List<dev.elsewhere.boss.kit.DropRule.Unique> kitUniques() {
		List<dev.elsewhere.boss.kit.DropRule.Unique> u = new java.util.ArrayList<>();
		u.add(dev.elsewhere.boss.kit.DropRule.signature("heartwood_greatbow", () -> new net.minecraft.world.item.ItemStack(dev.elsewhere.registry.ModItems.HEARTWOOD_GREATBOW)));
		u.add(dev.elsewhere.boss.kit.DropRule.extraLife("resin_heart_locket", () -> new net.minecraft.world.item.ItemStack(dev.elsewhere.registry.ModItems.RESIN_HEART_LOCKET)));
		dev.elsewhere.palehollow.ArsenalLinks.item(dev.elsewhere.palehollow.ArsenalLinks.ELDER_BARK_CROWN)
			.ifPresent(i -> u.add(dev.elsewhere.boss.kit.DropRule.other("elder_bark_crown", () -> new net.minecraft.world.item.ItemStack(i))));
		return u;
	}

	/** Tier I signature: Challenger's Cleaver. */
	@Override
	protected net.minecraft.world.item.Item tierItem() {
		return dev.elsewhere.tiergear.TierGear.CHALLENGERS_CLEAVER;
	}

	/** fun-mother-creak-solo: her speed while any player is mining one of her wall hearts (always, any party size). */
	public static final double HEART_MINING_SPEED = 0.575;
	/** Self-test hook: forces "a heart is being mined". */
	public static java.util.function.@org.jspecify.annotations.Nullable BooleanSupplier heartMiningOverride;

	public boolean heartBeingMined(final ServerLevel level) {
		if (heartMiningOverride != null) {
			return heartMiningOverride.getAsBoolean();
		}
		for (ServerPlayer p : level.players()) {
			if (p.distanceToSqr(this) < 40 * 40) {
				dev.elsewhere.mixin.ServerPlayerGameModeAccessor gm = (dev.elsewhere.mixin.ServerPlayerGameModeAccessor)p.gameMode;
				if (gm.elsewhere$isDestroyingBlock() && this.hearts.contains(gm.elsewhere$destroyPos())) {
					return true;
				}
			}
		}
		return false;
	}

	/** Current navigation speed multiplier (self-test hook). */
	public double lastMoveSpeed = 1.0;

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return this.isWatched() ? null : SoundEvents.CREAKING_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.CREAKING_SWAY;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.CREAKING_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 0.45F;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.store("Hearts", BlockPos.CODEC.listOf(), List.copyOf(this.hearts));
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		input.read("Hearts", BlockPos.CODEC.listOf()).ifPresent(this::setHearts);
	}
}
