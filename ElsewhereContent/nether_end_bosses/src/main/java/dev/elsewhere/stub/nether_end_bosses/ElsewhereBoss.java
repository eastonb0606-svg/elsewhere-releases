package dev.elsewhere.stub.nether_end_bosses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Stub of the core's shared boss base. Everything boss-specific lives in the subclasses.
 * <ul>
 * <li>Phase machine on HP thresholds (or a subclass rule), 2 s invulnerable transitions with a roar.</li>
 * <li>Attack scheduler: per-attack cooldowns and wind-ups from {@link AttackSpec} (JSON-tunable); the attack name
 *     flashes under the boss bar.</li>
 * <li>Overpower guard on incoming hits, lethal-hit guard on outgoing hits (mixin), difficulty factors on scripted damage.</li>
 * <li>A {@link BossFight} for the arena seal/snapshot/reset, participants, party scaling, void returns and per-player loot.</li>
 * </ul>
 */
public abstract class ElsewhereBoss extends Monster implements Enemy {
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ATTACK = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	public static final int FLAG_TRANSITION = 1;
	public static final int FLAG_DORMANT = 2;
	/** Bits from 8 up are free for subclasses (shell closed, rods glowing, ...). */
	public static final int FLAG_CUSTOM = 1 << 8;

	public static final int TRANSITION_TICKS = 40;

	private List<AttackSpec> attacks;
	protected @Nullable AttackSpec current;
	protected int attackTick;
	private final Map<String, Integer> cooldowns = new HashMap<>();
	protected int globalCooldown = 40;
	protected int transitionTicks;
	protected @Nullable BossFight fight;
	protected @Nullable BlockPos home;
	/** Attacks that ran to completion, for the self-test. */
	public final Set<String> completedAttacks = new HashSet<>();
	private int targetCursor;
	// client side animation bookkeeping
	public int clientAttackStart;
	public int clientPhaseStart;
	public final net.minecraft.world.entity.AnimationState transitionAnim = new net.minecraft.world.entity.AnimationState();
	public final net.minecraft.world.entity.AnimationState attackAnim = new net.minecraft.world.entity.AnimationState();

	protected ElsewhereBoss(EntityType<? extends ElsewhereBoss> type, Level level) {
		super(type, level);
		this.setPersistenceRequired();
		this.xpReward = 250;
	}

	// ---- identity, implemented per boss -------------------------------------------------------------------

	public abstract String bossId();

	/** Difficulty tier (I..VI = 1..6), picks the signature drop. */
	public abstract int tier();

	public abstract BossEvent.BossBarColor barColor();

	public abstract BossEvent.BossBarOverlay barOverlay();

	/** This boss's music track (see {@code NetherEndSounds}); the client plays it while the boss is active nearby. */
	public abstract Holder<SoundEvent> music();

	/** Code defaults for the attack table; the tuning JSON overrides. */
	protected abstract List<AttackSpec> defaultAttacks();

	/** Runs one tick of an attack. {@code t} counts from 0; the first {@code spec.windup()} ticks are the telegraph. Return true when done. */
	protected abstract boolean tickAttack(ServerLevel level, AttackSpec spec, int t);

	/** HP-ratio thresholds that start phases 2, 3, ... (descending). */
	protected float[] phaseThresholds() {
		return new float[0];
	}

	/** Builds the arena description for a fight centred on {@code origin}. */
	public abstract BossArena createArena(BlockPos origin);

	/** Rebuilds the arena from its generator when no snapshot exists (after a restart mid-fight). */
	public void rebuildArena(ServerLevel level, BlockPos origin) {
	}

	/** This boss's own drops for one participant's roll (the signature tier item is added by the framework). */
	public abstract List<ItemStack> rollDrops(ServerPlayer player, RandomSource random, boolean firstKill);

	/** Alternative endings (Mournmaw's mercy) can swap the drop table. */
	public List<ItemStack> rollVariantDrops(String variant, ServerPlayer player, RandomSource random, boolean firstKill, List<ItemStack> normal) {
		return normal;
	}

	/** Everyone left or died. Default: the boss leaves; the anchor can be used again. */
	/** What a wipe gives back to the summoner when the fight itself holds no return item (bal-boss-retry). */
	public ItemStack defaultSummonItem() {
		return ItemStack.EMPTY;
	}

	/** XP the kill drops (always; see BossRewards.XP_FLOOR). Vanilla XP is switched off so it is not doubled. */
	public int bossXp() {
		return this.xpReward;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	public void onFightReset(ServerLevel level) {
		for (ElsewhereBoss body : fight == null ? List.<ElsewhereBoss>of(this) : fight.bodies()) body.discard();
	}

	/** Per-tick boss behaviour outside attacks (movement, facing, hazards). Not called while dormant or transitioning. */
	protected void bossTick(ServerLevel level) {
	}

	protected void onPhaseStart(ServerLevel level, int phase) {
	}

	/** Boss-specific damage rules (shell closed, rift scales, snowballs ...). Runs after the overpower guard. */
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float damage, @Nullable BossPart part) {
		return damage;
	}

	/** The fight owner; linked bodies (Ashtusk) return their rider. */
	public ElsewhereBoss primary() {
		return this;
	}

	// ---- synced state -------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_ATTACK, -1);
		builder.define(DATA_FLAGS, 0);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ATTACK.equals(accessor)) {
			clientAttackStart = this.tickCount;
			if (currentAttackIndex() >= 0) attackAnim.start(this.tickCount);
			else attackAnim.stop();
		}
		if (DATA_PHASE.equals(accessor)) clientPhaseStart = this.tickCount;
		if (DATA_FLAGS.equals(accessor)) {
			if (inTransition()) transitionAnim.startIfStopped(this.tickCount);
			else transitionAnim.stop();
		}
	}

	public int getPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	protected void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, phase);
	}

	public boolean hasFlag(int flag) {
		return (this.entityData.get(DATA_FLAGS) & flag) != 0;
	}

	public void setFlag(int flag, boolean on) {
		int f = this.entityData.get(DATA_FLAGS);
		this.entityData.set(DATA_FLAGS, on ? f | flag : f & ~flag);
	}

	public boolean isDormant() {
		return hasFlag(FLAG_DORMANT);
	}

	public boolean inTransition() {
		return hasFlag(FLAG_TRANSITION);
	}

	/** Index of the running attack in {@link #attacks()}, or -1 (synced, for client animations). */
	public int currentAttackIndex() {
		return this.entityData.get(DATA_ATTACK);
	}

	public @Nullable String currentAttackId() {
		int i = currentAttackIndex();
		List<AttackSpec> list = attacks();
		return i >= 0 && i < list.size() ? list.get(i).id() : null;
	}

	/** Ticks since the current attack started, on either side. */
	public int attackAge() {
		return this.level().isClientSide() ? this.tickCount - clientAttackStart : attackTick;
	}

	public List<AttackSpec> attacks() {
		if (attacks == null) attacks = AttackSpec.tuned(bossId(), defaultAttacks()).stream().map(AttackSpec::withBigWindup).toList();
		return attacks;
	}

	public @Nullable AttackSpec attack(String id) {
		for (AttackSpec a : attacks()) if (a.id().equals(id)) return a;
		return null;
	}

	public @Nullable BossFight fight() {
		return primary().fight;
	}

	public @Nullable BlockPos home() {
		return primary().home;
	}

	public void setHome(BlockPos home) {
		this.home = home;
	}

	// ---- lifecycle ----------------------------------------------------------------------------------------

	/** Starts the fight: arena snapshot + seal, bars, participants. Call after the boss is positioned and added. */
	public BossFight startFight(ServerLevel level, BlockPos origin, @Nullable ServerPlayer summoner, ItemStack returnItem) {
		this.home = origin.immutable();
		setFlag(FLAG_DORMANT, false);
		this.fight = new BossFight(this, createArena(origin), summoner, returnItem);
		this.fight.begin(level);
		return this.fight;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level() instanceof ServerLevel level && primary() == this && !this.isDeadOrDying()) {
			if (fight == null && home != null && !isDormant()) {
				// reloaded mid-fight: resume with a fresh fight object; the arena is rebuilt from its generator on reset
				fight = new BossFight(this, createArena(home), null, ItemStack.EMPTY);
				fight.resume(level);
			}
			if (fight == null && home == null && tickCount >= 2) {
				// summoned by /summon or a spawn egg (no summoner ritual): the summon point becomes home and the fight starts here,
				// so the arena, leash and boss bars all exist (QA defects: drifting bosses, no bars)
				ServerPlayer nearest = null;
				for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(this) < 64 * 64 && (nearest == null || p.distanceToSqr(this) < nearest.distanceToSqr(this))) nearest = p;
				startFight(level, this.blockPosition(), nearest, ItemStack.EMPTY);
			}
			if (fight != null) {
				fight.tick(level);
				// leash: a boss that leaves its arena (fell, was knocked or pushed away) returns to its home point
				if (tickCount % 20 == 0 && home != null && !fight.arena.contains(this)) {
					Vec3 h = homeCenter();
					this.teleportTo(h.x, h.y, h.z);
					this.setDeltaMovement(Vec3.ZERO);
					this.fallDistance = 0;
				}
			}
		}
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		this.opening.tick(level, this);
		super.customServerAiStep(level);
		if (isDormant() || this.isDeadOrDying()) return;
		if (transitionTicks > 0) {
			transitionTicks--;
			if (transitionTicks == 0) setFlag(FLAG_TRANSITION, false);
			return;
		}
		int newPhase = computePhase();
		if (newPhase > getPhase()) {
			beginPhase(level, newPhase);
			return;
		}
		bossTick(level);
		runAttacks(level);
	}

	protected int computePhase() {
		float ratio = this.getHealth() / this.getMaxHealth();
		float[] t = phaseThresholds();
		int phase = 1;
		for (float threshold : t) if (ratio <= threshold) phase++;
		return Math.max(phase, getPhase());
	}

	protected void beginPhase(ServerLevel level, int phase) {
		setPhase(phase);
		dev.elsewhere.nether_end_bosses.BossSfx.play(this, bossId(), "roar");
		cancelAttack();
		transitionTicks = TRANSITION_TICKS;
		setFlag(FLAG_TRANSITION, true);
		if (!(dev.elsewhere.nether_end_bosses.Sfx3.enabled() && dev.elsewhere.nether_end_bosses.Sfx3.has(bossId(), "roar"))) this.playSound(roarSound(), 4.0F, 0.8F);
		if (fight() != null) fight().flash(Component.translatable("boss.elsewhere.phase", phase), 60);
		onPhaseStart(level, phase);
	}

	protected SoundEvent roarSound() {
		return SoundEvents.RAVAGER_ROAR;
	}

	private void runAttacks(ServerLevel level) {
		cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
		if (current != null) {
			if (tickAttack(level, current, attackTick)) {
				completedAttacks.add(current.id());
				this.opening.attackFinished(level, this, bossId(), current.id());
				cooldowns.put(current.id(), current.cooldown());
				globalCooldown = 20 + this.random.nextInt(20);
				cancelAttack();
			} else {
				attackTick++;
			}
			return;
		}
		if (globalCooldown > 0) {
			globalCooldown--;
			return;
		}
		if (fight() == null || fight().activePlayers().isEmpty()) return;
		AttackSpec next = chooseAttack();
		if (next != null) startAttack(next);
	}

	protected @Nullable AttackSpec chooseAttack() {
		int phase = getPhase();
		List<AttackSpec> ready = new ArrayList<>();
		int total = 0;
		for (AttackSpec a : attacks()) {
			if (a.inPhase(phase) && cooldowns.getOrDefault(a.id(), 0) <= 0 && canStart(a)) {
				ready.add(a);
				total += a.weight();
			}
		}
		if (total <= 0) return null;
		int roll = this.random.nextInt(total);
		for (AttackSpec a : ready) {
			roll -= a.weight();
			if (roll < 0) return a;
		}
		return null;
	}

	/** Extra per-attack conditions (range, line of sight, minion caps). */
	protected boolean canStart(AttackSpec spec) {
		return true;
	}

	private final dev.elsewhere.boss.kit.Openings.Tracker opening = new dev.elsewhere.boss.kit.Openings.Tracker();

	public void startAttack(AttackSpec spec) {
		if (level() instanceof ServerLevel sl && !dev.elsewhere.nether_end_bosses.Sfx3.windup(sl, this, bossId(), spec.id(), spec.isBig() || spec.damage() >= 16 || spec.windup() >= 30))
			dev.elsewhere.boss.kit.Openings.windupTell(sl, this, spec.damage() >= 16 || spec.windup() >= 30);
		if (spec.isBig()) dev.elsewhere.nether_end_bosses.BossSfx.play(this, bossId(), "big");
		current = spec;
		attackTick = 0;
		this.entityData.set(DATA_ATTACK, attacks().indexOf(spec));
		if (fight() != null) fight().flash(Component.translatable("attack.elsewhere." + bossId() + "." + spec.id()), 50);
	}

	/** Forces an attack now (dev command and self-test). */
	public boolean forceAttack(String id) {
		AttackSpec spec = attack(id);
		if (spec == null) return false;
		cancelAttack();
		transitionTicks = 0;
		setFlag(FLAG_TRANSITION, false);
		startAttack(spec);
		return true;
	}

	protected void cancelAttack() {
		current = null;
		attackTick = 0;
		this.entityData.set(DATA_ATTACK, -1);
	}

	// ---- targets and damage -------------------------------------------------------------------------------

	/** Spreads attacks across participants (round robin over living players in the arena). */
	public @Nullable ServerPlayer nextTarget() {
		BossFight f = fight();
		if (f == null) return null;
		List<ServerPlayer> players = f.activePlayers();
		if (players.isEmpty()) return null;
		targetCursor = (targetCursor + 1) % players.size();
		return players.get(targetCursor);
	}

	public @Nullable ServerPlayer nearestTarget() {
		BossFight f = fight();
		if (f == null) return null;
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : f.activePlayers()) {
			double d = p.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	public List<ServerPlayer> targets() {
		BossFight f = fight();
		return f == null ? List.of() : f.activePlayers();
	}

	/** Scripted attack damage: ×1.25 on Hard, ×0.6 on Easy (BOSSES.md difficulty framework). */
	public static float scaled(Level level, float raw) {
		Difficulty d = level.getDifficulty();
		return switch (d) {
			case PEACEFUL -> 0.0F;
			case EASY -> raw * 0.6F;
			case NORMAL -> raw;
			case HARD -> raw * 1.25F;
		};
	}

	public DamageSource source(ResourceKey<DamageType> type, @Nullable Entity direct) {
		Holder<DamageType> holder = this.level().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type);
		return new DamageSource(holder, direct == null ? this : direct, primary());
	}

	/** Deals a scripted hit (raw Normal value) to a living target. */
	public boolean hit(ServerLevel level, LivingEntity target, float raw, ResourceKey<DamageType> type, @Nullable Entity direct) {
		return target.hurtServer(level, source(type, direct), scaled(level, raw));
	}

	public boolean hit(ServerLevel level, LivingEntity target, float raw) {
		return hit(level, target, raw, StubFramework.BOSS_MELEE, null);
	}

	// ---- incoming damage ----------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		return hurtPart(level, null, source, damage);
	}

	/** Shared entry for the body and its {@link BossPart}s. */
	public boolean hurtPart(ServerLevel level, @Nullable BossPart part, DamageSource source, float damage) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, damage);
		if (isDormant() || inTransition()) return false;
		if (source.getEntity() instanceof ElsewhereBoss || source.getEntity() != null && source.getEntity().entityTags().contains(Guards.MINION_TAG)) {
			return false; // no friendly fire from the boss side
		}
		if (source.getEntity() instanceof Player attacker) damage = StubFramework.cleaverBonus(attacker, damage);
		float guarded = Guards.overpower(damage);
		guarded = modifyIncomingDamage(level, source, guarded, part);
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) guarded *= this.opening.multiplier(level.getGameTime());
		if (guarded <= 0) return false;
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, guarded);
		float dealt = before - this.getHealth();
		if (dealt > 0 && fight() != null) fight().recordDamage(source.getEntity(), dealt);
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		dev.elsewhere.nether_end_bosses.BossSfx.play(this, bossId(), "death");
		if (this.level() instanceof ServerLevel level) onBossDeath(level);
	}

	/** Called once when this body dies. Single-body bosses end the fight here. */
	protected void onBossDeath(ServerLevel level) {
		if (fight != null) fight.victory(level, null);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	// ---- save data ----------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("ElsewherePhase", getPhase());
		output.putBoolean("ElsewhereDormant", isDormant());
		if (home != null) output.store("ElsewhereHome", BlockPos.CODEC, home);
		if (fight != null && !fight.returnItem().isEmpty()) output.store("ElsewhereReturnItem", ItemStack.CODEC, fight.returnItem());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		setPhase(input.getIntOr("ElsewherePhase", 1));
		setFlag(FLAG_DORMANT, input.getBooleanOr("ElsewhereDormant", false));
		this.home = input.read("ElsewhereHome", BlockPos.CODEC).orElse(null);
		pendingReturnItem = input.read("ElsewhereReturnItem", ItemStack.CODEC).orElse(ItemStack.EMPTY);
	}

	ItemStack pendingReturnItem = ItemStack.EMPTY;

	// ---- small helpers ------------------------------------------------------------------------------------

	public Vec3 homeCenter() {
		BlockPos h = home();
		return h == null ? this.position() : Vec3.atBottomCenterOf(h);
	}

	public boolean isPlayerTarget(Entity e) {
		return e instanceof Player p && !p.isSpectator() && !p.isCreative() && p.isAlive();
	}
}
