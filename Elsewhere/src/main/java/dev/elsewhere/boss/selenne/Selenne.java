package dev.elsewhere.boss.selenne;

import dev.elsewhere.core.boss.ElsewhereBoss;
import dev.elsewhere.palehollow.GearStats;
import dev.elsewhere.palehollow.entity.EyeblossomWisp;
import dev.elsewhere.palehollow.entity.Gaze;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModParticles;
import dev.elsewhere.registry.ModSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Selenne, the Unblinking (BOSSES.md #7, Tier III). A floating eye over the Moonring.
 * <ol>
 * <li>Gaze (100-60%): open 6 s / closed 5 s. Looking at the open eye (25 degree cone, line of sight) costs 2 HP/s and
 * brings Darkness; she can only be hurt while closed (she sinks low enough to reach). Moonbeam every 9 s: a 1 s
 * end-rod line, then a 44-raw beam that stops at the first solid block.</li>
 * <li>Petals (60-30%): shorter cycle, Eyeblossom Wisps, monoliths crack.</li>
 * <li>Unblinking (30-0%): lids torn, permanent gaze. Only hits from players standing at a monolith's water cauldron
 * (its "reflection") count, and they are safe from the gaze there. Under 10% a Full Moon charges for 10 s; anyone with
 * line of sight to her when it ends is struck.</li>
 * </ol>
 */
public class Selenne extends ElsewhereBoss {
	public static final int PHASE_GAZE = 1;
	public static final int PHASE_PETALS = 2;
	public static final int PHASE_UNBLINKING = 3;
	public static final int BEAM_WINDUP = 20;
	public static final int FULL_MOON_TICKS = 200;
	public static final double GAZE_COS = Math.cos(Math.toRadians(25.0));

	private static final EntityDataAccessor<Boolean> DATA_OPEN = SynchedEntityData.defineId(Selenne.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_TORN = SynchedEntityData.defineId(Selenne.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_BEAM = SynchedEntityData.defineId(Selenne.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FULL_MOON = SynchedEntityData.defineId(Selenne.class, EntityDataSerializers.INT);

	private int cycle;
	private int beamTimer = 80;
	private @Nullable Vec3 beamTarget;
	private int wispTimer = 160;
	private int crackTimer = 300;
	private int fullMoonCooldown;
	private int gazeTick;
	private @Nullable UUID lastWatcher;
	private final Set<Integer> cracked = new HashSet<>();
	/** Client-only lid animation state (eased toward the synced open flag by the renderer). */
	public float lidOpenness;

	public Selenne(final EntityType<? extends Monster> type, final Level level) {
		super(type, level, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_10);
		this.setNoGravity(true);
		this.xpReward = 0; // XP comes from the boss kit (always, 400)
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, GearStats.SELENNE_HEALTH)
			.add(Attributes.ARMOR, GearStats.SELENNE_ARMOR)
			.add(Attributes.ARMOR_TOUGHNESS, GearStats.SELENNE_TOUGHNESS)
			.add(Attributes.ATTACK_DAMAGE, 8.0)
			.add(Attributes.FLYING_SPEED, 0.4)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 64.0);
	}

	@Override
	protected double baseMaxHealth() {
		return GearStats.SELENNE_HEALTH;
	}

	@Override
	protected void registerGoals() {
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_OPEN, false);
		builder.define(DATA_TORN, false);
		builder.define(DATA_BEAM, 0);
		builder.define(DATA_FULL_MOON, 0);
	}

	public boolean isOpen() {
		return this.entityData.get(DATA_OPEN);
	}

	public boolean lidsTorn() {
		return this.entityData.get(DATA_TORN);
	}

	public int beamCharge() {
		return this.entityData.get(DATA_BEAM);
	}

	public int fullMoon() {
		return this.entityData.get(DATA_FULL_MOON);
	}

	@Override
	public boolean isNoGravity() {
		return true;
	}

	@Override
	public boolean causeFallDamage(final double fallDistance, final float damageModifier, final DamageSource source) {
		return false;
	}

	@Override
	protected SoundEvent roarSound() {
		return ModSounds.SELENNE_OPEN.value();
	}

	private int openTicks() {
		return this.phase() >= PHASE_PETALS ? 100 : 120;
	}

	private int closedTicks() {
		return this.phase() >= PHASE_PETALS ? 80 : 100;
	}

	// ------------------------------------------------------------------ AI
	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.isAwake()) {
			this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
		}
	}

	private int lastHintTick = -100;

	/** Shows where the "reflections" are: glittering rings over the Moonring's filled cauldrons. */
	private void sparkCauldrons(final ServerLevel level) {
		if (this.home == null) {
			return;
		}
		for (BlockPos c : MoonringArena.cauldrons(this.home)) {
			if (level.getBlockState(c).is(Blocks.WATER_CAULDRON)) {
				level.sendParticles(ParticleTypes.END_ROD, c.getX() + 0.5, c.getY() + 1.2, c.getZ() + 0.5, 6, 0.3, 0.4, 0.3, 0.02);
			}
		}
	}

	@Override
	protected void onPhaseChange(final ServerLevel level, final int from, final int to) {
		if (to == PHASE_UNBLINKING) {
			for (ServerPlayer sp : this.playersInArena(level)) {
				sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.elsewhere.selenne.reflection_hint")
					.withStyle(net.minecraft.ChatFormatting.AQUA), false);
			}
			this.sparkCauldrons(level);
			this.entityData.set(DATA_TORN, true);
			this.setOpen(level, true);
			level.sendParticles(ParticleTypes.CRIT, this.getX(), this.getY() + 1.75, this.getZ(), 60, 1.2, 1.2, 1.2, 0.3);
		}
	}

	@Override
	protected void onReset(final ServerLevel level) {
		this.entityData.set(DATA_TORN, false);
		this.entityData.set(DATA_OPEN, false);
		this.entityData.set(DATA_BEAM, 0);
		this.entityData.set(DATA_FULL_MOON, 0);
		this.cracked.clear();
	}

	@Override
	protected void bossTick(final ServerLevel level) {
		float hp = this.getHealth() / this.getMaxHealth();
		if (this.phase() == PHASE_GAZE && hp <= 0.6F) {
			this.setPhase(PHASE_PETALS);
			return;
		}
		if (this.phase() == PHASE_PETALS && hp <= 0.3F) {
			this.setPhase(PHASE_UNBLINKING);
			return;
		}
		if (this.phase() == PHASE_UNBLINKING && this.tickCount % 20 == 0) {
			this.sparkCauldrons(level);
		}
		this.tickEyeCycle(level);
		this.tickMovement(level);
		this.tickGaze(level);
		this.tickBeam(level);
		if (this.phase() >= PHASE_PETALS) {
			this.tickPetals(level);
		}
		if (this.phase() == PHASE_UNBLINKING) {
			this.tickFullMoon(level, hp);
		}
	}

	private void setOpen(final ServerLevel level, final boolean open) {
		if (open == this.isOpen()) {
			return;
		}
		this.entityData.set(DATA_OPEN, open);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), open ? ModSounds.SELENNE_OPEN.value() : ModSounds.SELENNE_CLOSE.value(),
			SoundSource.HOSTILE, 3.0F, 1.0F);
	}

	private void tickEyeCycle(final ServerLevel level) {
		if (this.lidsTorn()) {
			this.setOpen(level, true);
			return;
		}
		this.cycle++;
		int period = this.openTicks() + this.closedTicks();
		boolean open = (this.cycle % period) < this.openTicks();
		// do not close in the middle of a Moonbeam charge
		if (!open && this.beamCharge() > 0) {
			open = true;
		}
		this.setOpen(level, open);
	}

	private void tickMovement(final ServerLevel level) {
		BlockPos c = this.home != null ? this.home : this.blockPosition();
		double t = this.tickCount * 0.012;
		double orbit = this.isOpen() ? 5.0 : 2.5;
		double height = this.isOpen() ? 6.5 : 1.6;
		Vec3 goal = new Vec3(c.getX() + 0.5 + Math.cos(t) * orbit, c.getY() + height, c.getZ() + 0.5 + Math.sin(t) * orbit);
		Vec3 d = goal.subtract(this.position());
		this.setDeltaMovement(this.getDeltaMovement().scale(0.7).add(d.scale(0.04)));
		ServerPlayer target = this.focus(level);
		if (target != null) {
			this.getLookControl().setLookAt(target, 20.0F, 20.0F);
			Vec3 to = target.getEyePosition().subtract(this.getEyePosition());
			float yaw = (float)(Mth.atan2(to.z, to.x) * (180.0 / Math.PI)) - 90.0F;
			this.setYRot(Mth.approachDegrees(this.getYRot(), yaw, 6.0F));
			this.yBodyRot = this.getYRot();
			this.yHeadRot = this.getYRot();
		}
	}

	private @Nullable ServerPlayer focus(final ServerLevel level) {
		if (this.lastWatcher != null) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(this.lastWatcher);
			if (p != null && p.isAlive() && this.arena != null && this.arena.contains(p.position())) {
				return p;
			}
		}
		return this.nearestFighter(level);
	}

	/** True if {@code p} looks at her open eye: within 25 degrees of their view, clear line of sight. */
	public boolean isGazing(final Player p) {
		Vec3 to = this.position().add(0.0, this.getBbHeight() * 0.5, 0.0).subtract(p.getEyePosition());
		double len = to.length();
		if (len < 0.001 || len > 48.0) {
			return false;
		}
		double dot = p.getViewVector(1.0F).dot(to.scale(1.0 / len));
		return dot > GAZE_COS && p.hasLineOfSight(this);
	}

	/** Phase 3 "reflection": standing within 2.5 blocks of one of the Moonring's water cauldrons. */
	public boolean atReflection(final ServerLevel level, final Player p) {
		if (this.home == null) {
			return false;
		}
		for (BlockPos c : MoonringArena.cauldrons(this.home)) {
			if (c.distToCenterSqr(p.getX(), p.getY() + 0.5, p.getZ()) < 6.25 && level.getBlockState(c).is(Blocks.WATER_CAULDRON)) {
				return true;
			}
		}
		return false;
	}

	private void tickGaze(final ServerLevel level) {
		if (!this.isOpen() || ++this.gazeTick % 10 != 0) {
			return;
		}
		for (ServerPlayer p : this.playersInArena(level)) {
			if (p.isCreative() || Gaze.gazeImmune(p)) {
				continue;
			}
			if (this.isGazing(p)) {
				this.lastWatcher = p.getUUID();
				if (this.lidsTorn() && this.atReflection(level, p)) {
					continue;
				}
				p.hurtServer(level, this.damageSources().indirectMagic(this, this), GearStats.SELENNE_GAZE_PER_SECOND / 2.0F);
				p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), this);
			}
		}
	}

	private void tickBeam(final ServerLevel level) {
		int charge = this.beamCharge();
		if (charge > 0) {
			this.entityData.set(DATA_BEAM, charge - 1);
			Vec3 eye = this.eyeCenter();
			if (this.beamTarget != null) {
				Vec3 end = this.beamEnd(level, eye, this.beamTarget);
				if (charge % 3 == 0) {
					Vec3 d = end.subtract(eye);
					int n = Math.max(1, (int)d.length()); // P13: one end rod per block (they linger 3 s), was 1.5
					for (int i = 0; i < n; i++) {
						Vec3 p = eye.add(d.scale(i / (double)n));
						level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
					}
				}
				if (charge == 1) {
					this.fireBeam(level, eye, end);
				}
			}
			return;
		}
		if (--this.beamTimer > 0 || !this.isOpen()) {
			return;
		}
		ServerPlayer target = this.focus(level);
		if (target == null || target.isCreative()) {
			return;
		}
		this.beamTimer = this.phase() >= PHASE_PETALS ? GearStats.SELENNE_BEAM_INTERVAL - 40 : GearStats.SELENNE_BEAM_INTERVAL;
		this.beamTarget = target.position().add(0.0, 1.0, 0.0);
		this.entityData.set(DATA_BEAM, BEAM_WINDUP);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.5F, 1.6F);
	}

	public Vec3 eyeCenter() {
		return this.position().add(0.0, this.getBbHeight() * 0.5, 0.0);
	}

	/** The beam runs from her eye through the locked target point and stops at the first solid block (monoliths are cover). */
	private Vec3 beamEnd(final ServerLevel level, final Vec3 eye, final Vec3 target) {
		Vec3 dir = target.subtract(eye).normalize();
		Vec3 far = eye.add(dir.scale(40.0));
		BlockHitResult hit = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		return hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
	}

	private void fireBeam(final ServerLevel level, final Vec3 eye, final Vec3 end) {
		level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SELENNE_BEAM.value(), SoundSource.HOSTILE, 3.0F, 1.0F);
		Vec3 d = end.subtract(eye);
		double len = d.length();
		Vec3 u = d.scale(1.0 / Math.max(0.001, len));
		for (int i = 0; i < (int)(len * 2); i++) {
			Vec3 p = eye.add(u.scale(i * 0.5));
			level.sendParticles(ModParticles.MOON_MOTE, p.x, p.y, p.z, 3, 0.25, 0.25, 0.25, 0.01);
		}
		for (ServerPlayer p : this.playersInArena(level)) {
			Vec3 rel = p.position().add(0.0, 0.9, 0.0).subtract(eye);
			double along = rel.dot(u);
			if (along < 0.0 || along > len + 0.5) {
				continue;
			}
			double dist = rel.subtract(u.scale(along)).length();
			if (dist < 1.6) {
				this.hitGuarded(level, p, GearStats.SELENNE_MOONBEAM, this.damageSources().mobAttack(this));
			}
		}
	}

	private void tickPetals(final ServerLevel level) {
		if (--this.wispTimer <= 0) {
			this.wispTimer = 240;
			int cap = 4 + 2 * (this.partySize() - 1);
			int alive = level.getEntitiesOfClass(EyeblossomWisp.class, this.getBoundingBox().inflate(24.0)).size();
			for (int i = 0; i < 2 && alive + i < cap; i++) {
				EyeblossomWisp wisp = ModEntities.EYEBLOSSOM_WISP.create(level, EntitySpawnReason.MOB_SUMMONED);
				if (wisp != null) {
					wisp.snapTo(this.getX() + this.random.nextGaussian() * 2.0, this.getY() + 1.0, this.getZ() + this.random.nextGaussian() * 2.0, 0.0F, 0.0F);
					wisp.setPersistenceRequired();
					level.addFreshEntity(wisp);
				}
			}
			level.sendParticles(ParticleTypes.CHERRY_LEAVES, this.getX(), this.getY() + 1.5, this.getZ(), 30, 1.5, 1.0, 1.5, 0.05);
		}
		if (--this.crackTimer <= 0 && this.home != null) {
			this.crackTimer = 300;
			this.crackMonolith(level);
		}
	}

	/** Breaks the top of one intact monolith: less cover each time. */
	public boolean crackMonolith(final ServerLevel level) {
		if (this.home == null || this.cracked.size() >= MoonringArena.MONOLITHS) {
			return false;
		}
		int i;
		do {
			i = this.random.nextInt(MoonringArena.MONOLITHS);
		} while (this.cracked.contains(i));
		this.cracked.add(i);
		BlockPos base = MoonringArena.monolithBase(this.home, i);
		for (int ox = 0; ox <= 1; ox++) {
			for (int oz = 0; oz <= 1; oz++) {
				for (int y = 3; y < 10; y++) {
					BlockPos p = base.offset(ox, y, oz);
					if (!level.getBlockState(p).isAir()) {
						level.setBlock(p, y == 3 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
					}
				}
			}
		}
		level.playSound(null, base.getX(), base.getY() + 3, base.getZ(), SoundEvents.DRIPSTONE_BLOCK_BREAK, SoundSource.HOSTILE, 3.0F, 0.5F);
		level.sendParticles(ParticleTypes.EXPLOSION, base.getX() + 1.0, base.getY() + 5.0, base.getZ() + 1.0, 3, 0.5, 1.0, 0.5, 0.0);
		return true;
	}

	public int crackedCount() {
		return this.cracked.size();
	}

	private void tickFullMoon(final ServerLevel level, final float hp) {
		int fm = this.fullMoon();
		if (fm > 0) {
			this.entityData.set(DATA_FULL_MOON, fm - 1);
			if (fm % 10 == 0 && this.home != null) {
				for (int i = 0; i < MoonringArena.MONOLITHS; i++) {
					BlockPos b = MoonringArena.monolithBase(this.home, i);
					level.sendParticles(ModParticles.MOON_MOTE, b.getX() + 1.0, b.getY() + 4.0, b.getZ() + 1.0, 4, 0.6, 2.0, 0.6, 0.02);
				}
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.HOSTILE, 2.0F,
					0.5F + (FULL_MOON_TICKS - fm) / (float)FULL_MOON_TICKS);
			}
			if (fm == 1) {
				Vec3 eye = this.eyeCenter();
				for (ServerPlayer p : this.playersInArena(level)) {
					if (p.isCreative() || Gaze.gazeImmune(p)) {
						continue;
					}
					BlockHitResult hit = level.clip(new ClipContext(p.getEyePosition(), eye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
					if (hit.getType() == HitResult.Type.MISS) {
						this.hitGuarded(level, p, GearStats.SELENNE_FULL_MOON, this.damageSources().indirectMagic(this, this));
					}
				}
				level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0F, 1.4F);
				this.fullMoonCooldown = 500;
			}
			return;
		}
		if (this.fullMoonCooldown > 0) {
			this.fullMoonCooldown--;
			return;
		}
		if (hp <= 0.1F) {
			this.startFullMoon(level);
		}
	}

	public void startFullMoon(final ServerLevel level) {
		this.entityData.set(DATA_FULL_MOON, FULL_MOON_TICKS);
		level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
	}

	// ------------------------------------------------------------------ damage
	@Override
	protected boolean canBeHurtNow(final ServerLevel level, final DamageSource source) {
		if (this.phase() == PHASE_UNBLINKING) {
			boolean ok = source.getEntity() instanceof Player p && this.atReflection(level, p);
			if (!ok && source.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && this.tickCount - this.lastHintTick >= 40) {
				this.lastHintTick = this.tickCount;
				sp.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.elsewhere.selenne.reflection_hint")
					.withStyle(net.minecraft.ChatFormatting.AQUA), true);
				this.sparkCauldrons(level);
			}
			return ok;
		}
		return !this.isOpen();
	}

	@Override
	protected java.util.List<net.minecraft.world.item.ItemStack> extraLoot(final ServerLevel level) {
		return dev.elsewhere.palehollow.ArsenalLinks.selenneExtras(this.random);
	}

	/** Boss-kit drop rules: Eyeblossom Circlet signature, Moon-Eye and Rootcall Staff (summoner) others. */
	@Override
	protected java.util.List<dev.elsewhere.boss.kit.DropRule.Unique> kitUniques() {
		java.util.List<dev.elsewhere.boss.kit.DropRule.Unique> u = new java.util.ArrayList<>();
		u.add(dev.elsewhere.boss.kit.DropRule.signature("eyeblossom_circlet", () -> new net.minecraft.world.item.ItemStack(dev.elsewhere.registry.ModItems.EYEBLOSSOM_CIRCLET)));
		u.add(dev.elsewhere.boss.kit.DropRule.other("moon_eye", () -> new net.minecraft.world.item.ItemStack(dev.elsewhere.registry.ModItems.MOON_EYE)));
		dev.elsewhere.palehollow.ArsenalLinks.item(dev.elsewhere.palehollow.ArsenalLinks.ROOTCALL_STAFF)
			.ifPresent(i -> u.add(dev.elsewhere.boss.kit.DropRule.other("rootcall_staff", () -> new net.minecraft.world.item.ItemStack(i))));
		return u;
	}

	/** Tier III signature: Phasewalker Greaves. */
	@Override
	protected net.minecraft.world.item.Item tierItem() {
		return dev.elsewhere.tiergear.TierGear.PHASEWALKER_GREAVES;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.AMETHYST_BLOCK_RESONATE;
	}

	@Override
	protected SoundEvent getHurtSound(final DamageSource source) {
		return SoundEvents.SLIME_SQUISH;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ELDER_GUARDIAN_DEATH;
	}

	@Override
	protected float getSoundVolume() {
		return 3.0F;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("LidsTorn", this.lidsTorn());
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_TORN, input.getBooleanOr("LidsTorn", false));
	}
}
