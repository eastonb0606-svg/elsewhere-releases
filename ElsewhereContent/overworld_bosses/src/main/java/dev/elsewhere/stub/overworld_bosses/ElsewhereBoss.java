package dev.elsewhere.stub.overworld_bosses;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Base class for an Elsewhere boss (BOSSES.md "Shared boss framework"): a custom {@link Monster} that implements
 * {@link Enemy} (so Test Meter's x1.53 applies), with
 * <ul>
 * <li>an HP-threshold phase machine with 2 s invulnerable transitions and a roar,</li>
 * <li>a scheduler for {@link BossAttack}s (cooldown, wind-up telegraph, fire, active time; the attack name flashes
 * in a second bar under the boss bar),</li>
 * <li>the arena lifecycle: seal on start, participants (inside the seal), 30 s drop-out, reset to full with the
 * arena restored when everyone leaves or dies, and a dormant state until players come back,</li>
 * <li>party HP scaling (x(1 + 0.5 per extra player) for players present in the first 30 s),</li>
 * <li>the overpower guard on incoming hits, the Challenger's Cleaver bonus, per-player loot on death.</li>
 * </ul>
 * Synced data drives client animations: {@link #actionAnim} is started when the server sets an action id.
 * STUB: the core owns the real framework; boss-specific logic lives in the subclasses only.
 */
public abstract class ElsewhereBoss extends Monster implements Enemy {
	public static final int MAX_ACTIONS = 16;
	public static final int TRANSITION_TICKS = 40;
	public static final int DEATH_TICKS = 60;
	public static final int DROP_OUT_TICKS = 30 * 20;
	public static final int JOIN_WINDOW_TICKS = 30 * 20;
	/** Self-test only: FakePlayers are not in the player list, so the empty-arena reset is skipped. */
	public static boolean testNoReset;

	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_TRANSITION = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.BOOLEAN);

	// Client animation states (render states copy them).
	public final AnimationState[] actionAnim = new AnimationState[MAX_ACTIONS];
	public final AnimationState transitionAnim = new AnimationState();
	public final AnimationState idleAnim = new AnimationState();

	protected final ServerBossEvent bossEvent;
	protected final ServerBossEvent castEvent;
	protected final BossTuning tuning;
	protected final List<BossAttack<?>> attacks = new ArrayList<>();

	protected @Nullable BossArena arena;
	protected @Nullable BlockPos home;
	private int transitionTicks;
	private int fightTicks;
	private int gapTicks = 30;
	private @Nullable BossAttack<?> current;
	private int attackTick;
	private boolean fired;
	private int allGoneTicks;
	private boolean resetPending;
	private final Map<UUID, Float> damageBy = new HashMap<>();
	private final Map<UUID, Integer> sealTicks = new HashMap<>();
	private final Map<UUID, Integer> outTicks = new HashMap<>();
	private final Set<UUID> participants = new HashSet<>();
	private final Map<UUID, Integer> targetedCount = new HashMap<>();
	private int partySize = 1;
	private final List<Entity> minions = new ArrayList<>();
	private float totalDamage;

	protected ElsewhereBoss(EntityType<? extends ElsewhereBoss> type, Level level) {
		super(type, level);
		for (int i = 0; i < MAX_ACTIONS; i++) this.actionAnim[i] = new AnimationState();
		this.tuning = BossTuning.get(bossId());
		this.bossEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), type.getDescription(), barColor(), barOverlay());
		this.castEvent = new ServerBossEvent(Mth.createInsecureUUID(this.random), Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
		this.castEvent.setVisible(false);
		this.xpReward = 60;
		this.setPersistenceRequired();
		registerAttacks();
		for (BossAttack<?> a : this.attacks) a.tune(this.tuning);
	}

	// ---- what a boss provides ----------------------------------------------------------------------------------

	public abstract String bossId();

	/** HP fractions where a new phase starts, highest first, e.g. {0.6, 0.3}. */
	protected abstract float[] phaseThresholds();

	protected abstract BossEvent.BossBarColor barColor();

	protected BossEvent.BossBarOverlay barOverlay() {
		return BossEvent.BossBarOverlay.NOTCHED_10;
	}

	/** Fill {@link #attacks}. */
	protected abstract void registerAttacks();

	/** Per-tick behaviour while the fight runs and no transition plays (movement, passives). */
	protected void bossTick(ServerLevel level) {}

	protected void onFightStart(ServerLevel level) {}

	protected void onPhaseChange(ServerLevel level, int phase) {}

	/** Clean up boss-specific state (held players, detached parts). Minions and blocks are handled by the base. */
	protected void onFightReset(ServerLevel level) {}

	protected void onFightWon(ServerLevel level) {}

	protected @Nullable SoundEvent roarSound() {
		return null;
	}

	/** What a wipe gives back to the summoner (bal-boss-retry); empty = nothing to give back. */
	public ItemStack defaultSummonItem() {
		return ItemStack.EMPTY;
	}

	/** XP the kill drops (always, see BossRewards.XP_FLOOR); vanilla XP is switched off so it is not doubled. */
	public int bossXp() {
		return this.xpReward;
	}

	@Override
	public boolean shouldDropExperience() {
		return false;
	}

	/** The boss's own drops for one eligible player (the tier signature item is added by {@link BossLoot}). */
	protected List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill) {
		return List.of();
	}

	protected @Nullable Item tierSignature() {
		return null;
	}

	/** Arena used when the boss was spawned without one (e.g. /summon): a cylinder around where it stands. */
	protected BossArena defaultArena() {
		BlockPos p = blockPosition();
		return new BossArena(p, 16, 4, 16, p.getY() - 1, 6);
	}

	/** Mechanics gate: return true to make this hit do nothing (e.g. body invulnerable until tentacles are cut). */
	protected boolean isDamageGated(ServerLevel level, DamageSource source) {
		return false;
	}

	/** Boss-specific multipliers (fire x1.5, channeling x2...). Applied before the overpower guard. */
	protected float modifyIncomingDamage(ServerLevel level, DamageSource source, float amount) {
		return amount;
	}

	// ---- synced state --------------------------------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_ACTION, 0);
		builder.define(DATA_TRANSITION, false);
		builder.define(DATA_DORMANT, false);
	}

	public int getPhase() {
		return this.entityData.get(DATA_PHASE);
	}

	public int getAction() {
		return this.entityData.get(DATA_ACTION);
	}

	public void setAction(int action) {
		this.entityData.set(DATA_ACTION, action);
	}

	public boolean isTransitioning() {
		return this.entityData.get(DATA_TRANSITION);
	}

	public boolean isDormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	@Override
	public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
		super.onSyncedDataUpdated(accessor);
		if (DATA_ACTION.equals(accessor)) {
			int a = getAction();
			for (int i = 0; i < MAX_ACTIONS; i++) if (i != a) this.actionAnim[i].stop();
			if (a > 0 && a < MAX_ACTIONS) this.actionAnim[a].start(this.tickCount);
		} else if (DATA_TRANSITION.equals(accessor)) {
			if (isTransitioning()) this.transitionAnim.start(this.tickCount);
			else this.transitionAnim.stop();
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide()) this.idleAnim.startIfStopped(this.tickCount);
	}

	// ---- fight lifecycle -----------------------------------------------------------------------------------------

	public @Nullable BossArena getArena() {
		return this.arena;
	}

	/** Called by the summon path: snapshot the arena (if not yet), seal it and start. */
	public void beginFight(ServerLevel level, BossArena arena) {
		this.arena = arena;
		arena.setOwner(this);
		if (this.home == null) this.home = blockPosition();
		if (!arena.hasSnapshot()) arena.capture(level);
		{
			// fix-arena-restart / bal-boss-retry: the fight is recorded on disk with whoever summoned it (nearest player)
			net.minecraft.world.entity.player.Player near = level.getNearestPlayer(getX(), getY(), getZ(), 40.0, false);
			dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightStart(getUUID(), bossId(), near == null ? null : near.getUUID(),
				defaultSummonItem(), level.dimension().identifier().toString(), blockPosition().getX(), blockPosition().getY(), blockPosition().getZ());
		}
		arena.seal(level);
		level.playSound(null, arena.center, BossRegistry.SEAL_CLOSE, SoundSource.HOSTILE, 2.0F, 1.0F);
		this.entityData.set(DATA_DORMANT, false);
		this.fightTicks = 0;
		this.gapTicks = 40;
		this.participants.clear();
		this.damageBy.clear();
		this.sealTicks.clear();
		this.outTicks.clear();
		this.totalDamage = 0;
		this.partySize = 1;
		this.allGoneTicks = 0;
		for (BossAttack<?> a : this.attacks) a.readyAt = 40 + this.random.nextInt(Math.max(1, a.cooldown / 2));
		setPhase(1);
		this.bossEvent.setVisible(true);
		updateParticipants(level);
		applyPartyScaling(Math.max(1, this.participants.size()));
		setHealth(getMaxHealth());
		SoundEvent roar = dev.elsewhere.overworld_bosses.Sfx3.pick(bossId(), "groan", roarSound());
		if (roar != null) level.playSound(null, getX(), getY(), getZ(), roar, SoundSource.HOSTILE, 4.0F, 1.0F);
		onFightStart(level);
	}

	/** Everyone left or died: heal to full, restore the arena, remove minions, go dormant at home. */
	public void resetFight(ServerLevel level) {
		if (level.getServer() != null) dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWiped(getUUID(), "wipe");
		abortAttack();
		onFightReset(level);
		cleanupMinions(level);
		if (this.arena != null) this.arena.restore(level);
		removePartyScaling();
		setHealth(getMaxHealth());
		removeAllEffects();
		clearFire();
		if (this.home != null) {
			Vec3 h = Vec3.atBottomCenterOf(this.home);
			teleportTo(h.x, h.y, h.z);
		}
		setDeltaMovement(Vec3.ZERO);
		setTarget(null);
		this.entityData.set(DATA_DORMANT, true);
		this.entityData.set(DATA_TRANSITION, false);
		this.transitionTicks = 0;
		setPhase(1);
		this.participants.clear();
		this.bossEvent.removeAllPlayers();
		this.castEvent.removeAllPlayers();
		// The snapshot is retaken when the fight starts again.
		if (this.arena != null) this.arena = new BossArena(this.arena.center, this.arena.radius, this.arena.down, this.arena.up, this.arena.floorY, this.arena.sealHeight);
	}

	private void setPhase(int phase) {
		this.entityData.set(DATA_PHASE, phase);
	}

	private int phaseForHealth() {
		float frac = getHealth() / getMaxHealth();
		int p = 1;
		for (float t : phaseThresholds()) if (frac <= t) p++;
		return p;
	}

	@Override
	protected void customServerAiStep(ServerLevel level) {
		this.opening.tick(level, this);
		super.customServerAiStep(level);
		if (this.resetPending) {
			this.resetPending = false;
			if (this.arena != null) {
				resetFight(level);
			}
		}
		if (this.arena == null) {
			this.arena = defaultArena();
			if (this.home == null) this.home = blockPosition();
			beginFight(level, this.arena);
		}
		if (isDormant()) {
			if (this.tickCount % 20 == 0 && anyPlayerInArena(level)) beginFight(level, this.arena);
			return;
		}
		this.fightTicks++;
		if (this.tickCount % 10 == 0) updateParticipants(level);
		if (!isAlive()) return;
		this.bossEvent.setProgress(getHealth() / getMaxHealth());
		if (this.transitionTicks > 0) {
			if (--this.transitionTicks == 0) {
				this.entityData.set(DATA_TRANSITION, false);
				this.castEvent.setVisible(false);
			}
			return;
		}
		int want = phaseForHealth();
		if (want > getPhase()) {
			abortAttack();
			setPhase(want);
			this.transitionTicks = TRANSITION_TICKS;
			this.entityData.set(DATA_TRANSITION, true);
			SoundEvent roar = dev.elsewhere.overworld_bosses.Sfx3.pick(bossId(), "groan", roarSound());
			if (roar != null) level.playSound(null, getX(), getY(), getZ(), roar, SoundSource.HOSTILE, 4.0F, 0.9F);
			dev.elsewhere.overworld_bosses.BossSfx.play(this, bossId(), "roar");
			showCast(Component.translatable("phase.elsewhere." + bossId() + "." + want), BossAttack.Danger.BIG, 1.0F);
			onPhaseChange(level, want);
			return;
		}
		if (this.tickCount % 40 == 0 || getTarget() == null || !getTarget().isAlive()) chooseTarget(level);
		bossTick(level);
		runAttacks(level);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void runAttacks(ServerLevel level) {
		if (this.current != null) {
			BossAttack a = this.current;
			this.attackTick++;
			if (this.attackTick <= a.windup) {
				a.onWindup.accept(this, this.attackTick);
				if (a.windup > 0 && a.showName) this.castEvent.setProgress(1.0F - this.attackTick / (float) a.windup);
			}
			if (!this.fired && this.attackTick >= a.windup) {
				this.fired = true;
				this.castEvent.setVisible(false);
				a.onFire.accept(this);
			} else if (this.fired && this.attackTick > a.windup) {
				a.onActive.accept(this, this.attackTick - a.windup);
			}
			if (this.current == a && this.attackTick >= a.windup + a.active) {
				finishAttack();
			}
			return;
		}
		if (this.gapTicks > 0) {
			this.gapTicks--;
			return;
		}
		int phase = getPhase();
		List<BossAttack> ready = new ArrayList<>();
		int total = 0;
		for (BossAttack a : this.attacks) {
			if (a.inPhase(phase) && a.readyAt <= this.fightTicks && a.canStart.test(this)) {
				ready.add(a);
				total += a.weight;
			}
		}
		if (ready.isEmpty()) return;
		int r = this.random.nextInt(total);
		for (BossAttack a : ready) {
			r -= a.weight;
			if (r < 0) {
				startAttack(a);
				return;
			}
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private final dev.elsewhere.boss.kit.Openings.Tracker opening = new dev.elsewhere.boss.kit.Openings.Tracker();

	private void startAttack(BossAttack a) {
		if (level() instanceof ServerLevel sl && !dev.elsewhere.overworld_bosses.Sfx3.windup(sl, this, bossId(), a.id, a.danger == BossAttack.Danger.BIG))
			dev.elsewhere.boss.kit.Openings.windupTell(sl, this, a.danger == BossAttack.Danger.BIG);
		if (a.danger == BossAttack.Danger.BIG) dev.elsewhere.overworld_bosses.BossSfx.play(this, bossId(), "big");
		this.current = a;
		this.attackTick = 0;
		this.fired = false;
		a.readyAt = this.fightTicks + a.cooldown;
		if (a.showName && a.windup > 0) showCast(a.name, a.danger, 1.0F);
		a.onStart.accept(this);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private void finishAttack() {
		BossAttack a = this.current;
		if (a != null && level() instanceof ServerLevel sl) this.opening.attackFinished(sl, this, bossId(), a.id);
		this.current = null;
		this.castEvent.setVisible(false);
		if (a != null) a.onEnd.accept(this);
		setAction(0);
		this.gapTicks = 10;
	}

	/** Stops the running attack early (phase change, reset, stun). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void abortAttack() {
		if (this.current != null) {
			BossAttack a = this.current;
			this.current = null;
			a.onEnd.accept(this);
		}
		this.castEvent.setVisible(false);
		setAction(0);
	}

	public @Nullable String currentAttackId() {
		return this.current == null ? null : this.current.id;
	}

	/** Forces an attack to start now (self-test and scripted sequences). Returns false if unknown. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public boolean forceAttack(String id) {
		for (BossAttack a : this.attacks) {
			if (a.id.equals(id)) {
				abortAttack();
				startAttack(a);
				return true;
			}
		}
		return false;
	}

	public void readyAttack(String id) {
		for (BossAttack<?> a : this.attacks) if (a.id.equals(id)) a.readyAt = 0;
	}

	public int getFightTicks() {
		return this.fightTicks;
	}

	public void showCast(Component name, BossAttack.Danger danger, float progress) {
		Component text = Component.literal("⚠ ").append(name).withStyle(switch (danger) {
			case BIG -> ChatFormatting.RED;
			case INTERACT -> ChatFormatting.AQUA;
			default -> ChatFormatting.YELLOW;
		});
		this.castEvent.setName(text);
		this.castEvent.setColor(switch (danger) {
			case BIG -> BossEvent.BossBarColor.RED;
			case INTERACT -> BossEvent.BossBarColor.BLUE;
			default -> BossEvent.BossBarColor.YELLOW;
		});
		this.castEvent.setProgress(progress);
		this.castEvent.setVisible(true);
	}

	// ---- participants -------------------------------------------------------------------------------------------

	private boolean anyPlayerInArena(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && this.arena != null && this.arena.contains(p)) return true;
		}
		return false;
	}

	private void updateParticipants(ServerLevel level) {
		if (this.arena == null) return;
		for (ServerPlayer p : level.players()) {
			boolean inside = p.isAlive() && !p.isSpectator() && !p.isCreative() && this.arena.contains(p);
			UUID id = p.getUUID();
			if (inside) {
				if (this.participants.add(id) && this.fightTicks <= JOIN_WINDOW_TICKS && this.participants.size() > this.partySize) {
					applyPartyScaling(this.participants.size());
				}
				this.sealTicks.merge(id, 10, Integer::sum);
				this.outTicks.put(id, 0);
			} else if (this.participants.contains(id)) {
				int out = this.outTicks.merge(id, 10, Integer::sum);
				if (out >= DROP_OUT_TICKS || !p.isAlive()) {
					if (out >= DROP_OUT_TICKS) this.participants.remove(id);
				}
			}
			boolean show = this.participants.contains(id) || (p.isAlive() && this.arena.contains(p)) || p.distanceToSqr(this) < 48 * 48;
			if (show && !this.bossEvent.getPlayers().contains(p)) {
				this.bossEvent.addPlayer(p);
				this.castEvent.addPlayer(p);
			} else if (!show && this.bossEvent.getPlayers().contains(p)) {
				this.bossEvent.removePlayer(p);
				this.castEvent.removePlayer(p);
			}
		}
		if (testNoReset) return;
		this.participants.removeIf(id -> level.getServer().getPlayerList().getPlayer(id) == null);
		boolean anyAliveInside = false;
		for (UUID id : this.participants) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && p.isAlive() && p.level() == level && this.arena.contains(p)) anyAliveInside = true;
		}
		if (this.fightTicks > 20 && (this.participants.isEmpty() || !anyAliveInside)) {
			this.allGoneTicks += 10;
			// Everyone dead or outside: reset once all have dropped out (30 s), or 5 s after the last one died.
			boolean allDead = !this.participants.isEmpty() && this.participants.stream().allMatch(id -> {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				return p == null || !p.isAlive();
			});
			if (this.participants.isEmpty() || (allDead && this.allGoneTicks >= 100)) resetFight(level);
		} else {
			this.allGoneTicks = 0;
		}
	}

	public List<ServerPlayer> participantPlayers(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (UUID id : this.participants) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p != null && p.isAlive() && p.level() == level) out.add(p);
		}
		return out;
	}

	/** Living players inside the arena (participants or not). */
	public List<ServerPlayer> playersInArena(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && this.arena != null && this.arena.contains(p)) out.add(p);
		}
		return out;
	}

	public int participantCount() {
		return Math.max(1, this.participants.size());
	}

	/** Spreads attacks: picks the participant targeted least often. */
	protected void chooseTarget(ServerLevel level) {
		LivingEntity best = null;
		int bestCount = Integer.MAX_VALUE;
		double bestDist = Double.MAX_VALUE;
		for (ServerPlayer p : playersInArena(level)) {
			int c = this.targetedCount.getOrDefault(p.getUUID(), 0);
			double d = p.distanceToSqr(this);
			if (c < bestCount || (c == bestCount && d < bestDist)) {
				best = p;
				bestCount = c;
				bestDist = d;
			}
		}
		if (best != null) this.targetedCount.merge(best.getUUID(), 1, Integer::sum);
		setTarget(best);
	}

	/** Max minions for the party: base for one player, +50% per extra player. */
	public int minionCap(int base) {
		return base + (participantCount() - 1) * Math.max(1, base / 2);
	}

	// ---- HP scaling -----------------------------------------------------------------------------------------------

	private static final net.minecraft.resources.Identifier PARTY_ID = BossRegistry.id("party_scaling");

	public void applyPartyScaling(int players) {
		this.partySize = players;
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp == null) return;
		float frac = getHealth() / getMaxHealth();
		hp.removeModifier(PARTY_ID);
		if (players > 1) hp.addPermanentModifier(new AttributeModifier(PARTY_ID, 0.5 * (players - 1), AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		setHealth(getMaxHealth() * frac);
	}

	private void removePartyScaling() {
		AttributeInstance hp = getAttribute(Attributes.MAX_HEALTH);
		if (hp != null) hp.removeModifier(PARTY_ID);
		this.partySize = 1;
	}

	// ---- minions --------------------------------------------------------------------------------------------------

	public <T extends Entity> T addMinion(ServerLevel level, T e) {
		e.addTag(BossDamage.MINION_TAG);
		e.addTag("elsewhere_minion_of_" + getUUID());
		if (e instanceof net.minecraft.world.entity.Mob m) m.setPersistenceRequired();
		level.addFreshEntity(e);
		this.minions.add(e);
		return e;
	}

	public int liveMinions() {
		this.minions.removeIf(e -> !e.isAlive());
		return this.minions.size();
	}

	public List<Entity> minions() {
		liveMinions();
		return this.minions;
	}

	public void cleanupMinions(ServerLevel level) {
		for (Entity e : this.minions) if (e.isAlive()) e.discard();
		this.minions.clear();
		if (this.arena != null) {
			String tag = "elsewhere_minion_of_" + getUUID();
			for (Entity e : level.getEntities((Entity) null, this.arena.box().inflate(16), x -> x.entityTags().contains(tag))) e.discard();
		}
	}

	// ---- damage ---------------------------------------------------------------------------------------------------

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, amount);
		if (source.getEntity() instanceof ElsewhereBoss || source.getEntity() instanceof BossPart) return false;
		if (source.getEntity() != null && source.getEntity().entityTags().contains(BossDamage.MINION_TAG) && !(source.getEntity() instanceof Player)) return false;
		if (isDormant()) {
			if (source.getEntity() instanceof ServerPlayer && this.arena != null) beginFight(level, this.arena);
			else return false;
		}
		if (this.transitionTicks > 0) return false;
		if (isDamageGated(level, source)) {
			level.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
			return false;
		}
		amount = modifyIncomingDamage(level, source, amount);
		if (source.getEntity() instanceof LivingEntity attacker && SignatureGear.isCleaver(attacker.getMainHandItem())) {
			amount *= 1.0F + SignatureGear.CLEAVER_BOSS_BONUS;
		}
		float before = getHealth();
		boolean hurt = super.hurtServer(level, source, amount);
		if (hurt && source.getEntity() instanceof ServerPlayer p) {
			float dealt = Math.max(0, before - getHealth());
			this.damageBy.merge(p.getUUID(), dealt, Float::sum);
			this.totalDamage += dealt;
			if (this.arena != null && this.participants.add(p.getUUID()) && this.fightTicks <= JOIN_WINDOW_TICKS) applyPartyScaling(this.participants.size());
		}
		return hurt;
	}

	/** Overpower guard: applied to the final pre-armor amount (after Test Meter's player scaling). */
	@Override
	protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
		if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) dmg *= this.opening.multiplier(level.getGameTime());
		super.actuallyHurt(level, source, source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) ? dmg : BossDamage.overpowerGuard(dmg));
	}

	/** Damage taken by a part (head, tentacle) counted as damage to the boss for participation. */
	public void creditPartDamage(ServerPlayer p, float dealt) {
		this.damageBy.merge(p.getUUID(), dealt, Float::sum);
		this.totalDamage += dealt;
		this.participants.add(p.getUUID());
	}

	@Override
	public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
		// Bosses do not get knocked around.
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
		if (level().getDifficulty() == Difficulty.PEACEFUL && !getType().isAllowedInPeaceful()) discard();
		else this.noActionTime = 0;
	}

	// ---- death and loot -------------------------------------------------------------------------------------------

	private List<List<ItemStack>> lastLoot = List.of();

	public List<List<ItemStack>> lastLoot() {
		return this.lastLoot;
	}

	@Override
	public void die(DamageSource source) {
		boolean wasAlive = !isRemoved() && !this.dead;
		super.die(source);
		if (!wasAlive || !(level() instanceof ServerLevel level)) return;
		dev.elsewhere.overworld_bosses.BossSfx.play(this, bossId(), "death");
		abortAttack();
		onFightWon(level);
		cleanupMinions(level);
		this.bossEvent.setProgress(0.0F);
		List<ServerPlayer> eligible = new ArrayList<>();
		float need = this.totalDamage * 0.05F;
		for (UUID id : this.participants) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null && source.getEntity() instanceof ServerPlayer sp && sp.getUUID().equals(id)) p = sp;
			if (p == null) continue;
			if (this.damageBy.getOrDefault(id, 0F) >= need || this.sealTicks.getOrDefault(id, 0) >= 60 * 20) eligible.add(p);
		}
		if (eligible.isEmpty() && source.getEntity() instanceof ServerPlayer killer) eligible.add(killer);
		if (this.arena != null) this.arena.restore(level);
		Vec3 at = this.arena != null ? this.arena.centerVec() : position();
		this.lastLoot = BossLoot.distribute(level, at, eligible, bossId(), tierSignature(), this::rollLoot);
		dev.elsewhere.boss.kit.BossRewards.awardXp(level, at, bossXp());
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWon(getUUID());
	}

	@Override
	protected void tickDeath() {
		this.deathTime++;
		if (this.deathTime >= DEATH_TICKS && !level().isClientSide() && !isRemoved()) {
			level().broadcastEntityEvent(this, (byte) 60);
			this.remove(Entity.RemovalReason.KILLED);
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		this.bossEvent.removeAllPlayers();
		this.castEvent.removeAllPlayers();
		// Discarded (/kill-less removal, despawn, dimension change) without the death/reset path: lift the arena.
		// Chunk unloads keep it: the boss reloads, resets and restores its snapshot then (ArenaSeals sweeps the rest).
		if (reason != Entity.RemovalReason.UNLOADED_TO_CHUNK && reason != Entity.RemovalReason.UNLOADED_WITH_PLAYER
			&& this.arena != null && !this.arena.isReleased() && level() instanceof ServerLevel level) {
			this.arena.restore(level);
		}
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
		this.castEvent.removePlayer(player);
	}

	// ---- save ------------------------------------------------------------------------------------------------------

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (this.arena != null) this.arena.save(output.child("Arena"));
		if (this.home != null) {
			output.putInt("HomeX", this.home.getX());
			output.putInt("HomeY", this.home.getY());
			output.putInt("HomeZ", this.home.getZ());
		}
		output.putBoolean("Dormant", isDormant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		input.child("Arena").ifPresent(a -> this.arena = BossArena.load(a));
		if (this.arena != null) this.arena.setOwner(this);
		if (input.getInt("HomeX").isPresent()) this.home = new BlockPos(input.getIntOr("HomeX", 0), input.getIntOr("HomeY", 0), input.getIntOr("HomeZ", 0));
		// A fight interrupted by a restart resets (arena restored) and waits for players.
		if (!input.getBooleanOr("Dormant", false) && this.arena != null) this.resetPending = true;
		else this.entityData.set(DATA_DORMANT, input.getBooleanOr("Dormant", false));
	}

	public BossTuning tuning() {
		return this.tuning;
	}

	public float stat(String key, float fallback) {
		return (float) this.tuning.stat(key, fallback);
	}

	public float num(String attack, String field, float fallback) {
		return this.tuning.attackFloat(attack, field, fallback);
	}

	public ServerBossEvent bossEvent() {
		return this.bossEvent;
	}
}
