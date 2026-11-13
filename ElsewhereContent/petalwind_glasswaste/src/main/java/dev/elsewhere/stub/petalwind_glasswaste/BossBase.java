package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * STUB of the core's shared boss framework (BOSSES.md "Shared boss framework"): boss bar, HP-threshold phase machine with
 * 2 s invulnerable transitions, arena seal (particle ring) with snapshot/restore and reset, 30 s drop-out, party HP
 * scaling, per-player loot and first-kill tracking, attack-name flashes. The overpower and lethal-hit guards live in
 * {@code BossGuardMixin} and apply to every {@link BossBase}. Boss-specific logic goes in the subclasses.
 */
public abstract class BossBase extends Monster {
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_DORMANT = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_TRANSITION = SynchedEntityData.defineId(BossBase.class, EntityDataSerializers.INT);
	public static final int TRANSITION_TICKS = 40;
	public static final int DROP_OUT_TICKS = 600;
	public static final int JOIN_WINDOW_TICKS = 600;

	protected final ServerBossEvent bossBar;
	protected @Nullable BlockPos arenaCenter;
	protected int arenaRadius = 16;
	protected int arenaDown = 6, arenaUp = 24;
	protected @Nullable ArenaSnapshot snapshot;
	protected int fightTicks;
	protected boolean fighting;
	private int announceTicks;
	private final Map<UUID, Participant> participants = new HashMap<>();
	private int partySize = 1;

	public static final class Participant {
		public float damage;
		public int ticksInside;
		public int ticksOutside;
		public boolean droppedOut;
	}

	protected BossBase(EntityType<? extends Monster> type, Level level, BossEvent.BossBarColor color) {
		super(type, level);
		this.bossBar = new ServerBossEvent(UUID.randomUUID(), this.getDisplayName(), color, BossEvent.BossBarOverlay.NOTCHED_10);
		this.bossBar.setDarkenScreen(false);
		this.setPersistenceRequired();
		this.xpReward = 0; // XP is awarded by BossRewards.awardXp (always, 2.0 drop rules)
	}

	// ---- per-boss hooks -------------------------------------------------------------------------------------------
	/** Health fractions at which phases 2, 3, ... begin (e.g. {0.6, 0.3}). */
	protected abstract float[] phaseThresholds();

	/** Boss-specific AI each server tick while the fight runs (not dormant, not in a phase transition). */
	protected abstract void fightTick(ServerLevel level);

	protected void onPhaseStart(ServerLevel level, int phase) {}

	/** Arena restored and boss healed: put boss-specific state back. */
	protected void onReset(ServerLevel level) {}

	/** Drops for one eligible player (firstKill = this player's first kill of this boss). */
	protected abstract List<ItemStack> rollDrops(ServerPlayer player, boolean firstKill);

	/** The item consumed to summon this boss (refunded 30 s after a wipe, bal-boss-retry). Empty = none. */
	protected ItemStack summonItem() {
		return ItemStack.EMPTY;
	}

	/** XP the kill drops (at least BossRewards.XP_FLOOR). */
	public int bossXp() {
		return 400 + 100 * tier();
	}

	public abstract String bossId();

	public abstract int tier();

	protected SoundEvent roarSound() {
		return SoundEvents.RAVAGER_ROAR;
	}

	/** Dormant bosses (waiting to be challenged) can override to start the fight on their own condition. */
	protected boolean startsDormant() {
		return false;
	}

	// ---- synced state ---------------------------------------------------------------------------------------------
	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PHASE, 1);
		builder.define(DATA_DORMANT, false);
		builder.define(DATA_TRANSITION, 0);
	}

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	public boolean isDormant() {
		return this.entityData.get(DATA_DORMANT);
	}

	public void setDormant(boolean dormant) {
		this.entityData.set(DATA_DORMANT, dormant);
	}

	public int transitionTicks() {
		return this.entityData.get(DATA_TRANSITION);
	}

	public boolean inTransition() {
		return transitionTicks() > 0;
	}

	public boolean isFighting() {
		return fighting;
	}

	public @Nullable BlockPos arenaCenter() {
		return arenaCenter;
	}

	public int arenaRadius() {
		return arenaRadius;
	}

	public void setArena(BlockPos center, int radius, int down, int up) {
		this.arenaCenter = center.immutable();
		this.arenaRadius = radius;
		this.arenaDown = down;
		this.arenaUp = up;
	}

	public boolean isParticipant(UUID id) {
		Participant p = participants.get(id);
		return p != null && !p.droppedOut;
	}

	public Map<UUID, Participant> participants() {
		return participants;
	}

	public int partySize() {
		return partySize;
	}

	// ---- lifecycle --------------------------------------------------------------------------------------------------
	@Override
	public void tick() {
		super.tick();
		if (!(this.level() instanceof ServerLevel level)) return;
		if (!homeChecked) {
			// A bare /summon has no home: home is the summon point. A home that is far from where the boss actually is (stale or
			// default coordinates, another arena) is never trusted: the boss is re-homed where it stands.
			homeChecked = true;
			if (arenaCenter == null || farFromHome()) arenaCenter = this.blockPosition();
		}
		if (arenaCenter == null) arenaCenter = this.blockPosition();
		if (this.tickCount % 20 == 0 && fighting && !insideArena(this)) leashHome(level);
		bossBar.setProgress(this.getHealth() / this.getMaxHealth());
		if (announceTicks > 0 && --announceTicks == 0) bossBar.setName(this.getDisplayName());
		if (isDormant()) {
			idleTick(level);
			return;
		}
		if (!fighting) {
			if (!level.getEntitiesOfClass(ServerPlayer.class, arenaBox(), this::eligible).isEmpty()) startFight(level);
			else {
				idleTick(level);
				return;
			}
		}
		fightTicks++;
		trackParticipants(level);
		if (!fighting) return; // reset during tracking
		if (this.tickCount % 10 == 0) drawSeal(level);
		int t = transitionTicks();
		if (t > 0) {
			this.entityData.set(DATA_TRANSITION, t - 1);
			this.getNavigation().stop();
			this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 1, 0.5));
			if (t % 5 == 0) level.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + getBbHeight() * 0.6, getZ(), 1, 0.5, 0.5, 0.5, 0);
			return;
		}
		checkPhase(level);
		if (transitionTicks() == 0) fightTick(level);
	}

	private boolean homeChecked;

	private boolean farFromHome() {
		if (arenaCenter == null) return true;
		double dx = getX() - (arenaCenter.getX() + 0.5), dz = getZ() - (arenaCenter.getZ() + 0.5);
		return dx * dx + dz * dz > (arenaRadius + 24.0) * (arenaRadius + 24.0) || Math.abs(getY() - arenaCenter.getY()) > arenaDown + arenaUp + 24;
	}

	/** Outside the arena mid-fight: back home, unless home is far away (then home was wrong: re-home here, never teleport to a far arena). */
	private void leashHome(ServerLevel level) {
		if (arenaCenter == null) return;
		if (farFromHome()) {
			arenaCenter = this.blockPosition();
			return;
		}
		this.teleportTo(arenaCenter.getX() + 0.5, Math.max(arenaCenter.getY(), level.getMinY() + 3), arenaCenter.getZ() + 0.5);
		this.setDeltaMovement(Vec3.ZERO);
	}

	/** Behaviour while waiting for a challenger (dormant or nobody in the arena). */
	protected void idleTick(ServerLevel level) {
		this.getNavigation().stop();
	}

	protected boolean eligible(ServerPlayer p) {
		return p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	public AABB arenaBox() {
		BlockPos c = arenaCenter != null ? arenaCenter : blockPosition();
		return new AABB(c.getX() - arenaRadius, c.getY() - arenaDown, c.getZ() - arenaRadius, c.getX() + arenaRadius + 1, c.getY() + arenaUp,
			c.getZ() + arenaRadius + 1);
	}

	public boolean insideArena(Entity e) {
		BlockPos c = arenaCenter != null ? arenaCenter : blockPosition();
		double dx = e.getX() - (c.getX() + 0.5), dz = e.getZ() - (c.getZ() + 0.5);
		return dx * dx + dz * dz <= (arenaRadius + 1.5) * (arenaRadius + 1.5) && e.getY() > c.getY() - arenaDown - 2 && e.getY() < c.getY() + arenaUp;
	}

	public void startFight(ServerLevel level) {
		if (fighting) return;
		fighting = true;
		fightTicks = 0;
		setDormant(false);
		participants.clear();
		if (snapshot == null) snapshot = ArenaSnapshot.capture(level, arenaBox());
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, arenaBox(), this::eligible)) join(p);
		bossBar.setVisible(true);
		BossFightRegistry.active(this, true);
		level.playSound(null, blockPosition(), roarSound(), SoundSource.HOSTILE, 3.0F, 0.8F);
		if (level.getServer() != null) {
			// fix-arena-restart / bal-boss-retry: the fight is on disk with whoever summoned it; a wipe or restart refunds the summon item.
			Player near = level.getNearestPlayer(getX(), getY(), getZ(), 48.0, false);
			ItemStack item = near == null || near.isCreative() ? ItemStack.EMPTY : summonItem();
			dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightStart(getUUID(), bossId(), near == null ? null : near.getUUID(), item,
				level.dimension().identifier().toString(), blockPosition().getX(), blockPosition().getY(), blockPosition().getZ());
		}
	}

	private void join(ServerPlayer p) {
		participants.computeIfAbsent(p.getUUID(), u -> new Participant());
		bossBar.addPlayer(p);
		if (fightTicks <= JOIN_WINDOW_TICKS) rescaleForParty();
	}

	/** Boss HP x(1 + 0.5 per extra player) for players inside at the start or joining in the first 30 s. */
	private void rescaleForParty() {
		int n = (int) participants.values().stream().filter(p -> !p.droppedOut).count();
		if (n <= partySize) return;
		float fraction = this.getHealth() / this.getMaxHealth();
		partySize = n;
		var inst = this.getAttribute(Attributes.MAX_HEALTH);
		if (inst == null) return;
		inst.removeModifier(PARTY_ID);
		inst.addTransientModifier(new AttributeModifier(PARTY_ID, 0.5 * (n - 1), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		this.setHealth(this.getMaxHealth() * fraction);
	}

	private static final net.minecraft.resources.Identifier PARTY_ID = net.minecraft.resources.Identifier.fromNamespaceAndPath("elsewhere", "boss_party_scaling");

	private void trackParticipants(ServerLevel level) {
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, arenaBox(), this::eligible)) {
			if (!participants.containsKey(p.getUUID())) join(p);
		}
		boolean anyone = false;
		for (var e : participants.entrySet()) {
			Participant part = e.getValue();
			if (part.droppedOut) continue;
			ServerPlayer p = resolve(level, e.getKey());
			if (p == null || !p.isAlive() || p.level() != level) {
				part.droppedOut = true;
				if (p != null) bossBar.removePlayer(p);
				continue;
			}
			if (insideArena(p)) {
				part.ticksInside++;
				part.ticksOutside = 0;
			} else if (++part.ticksOutside >= DROP_OUT_TICKS) {
				part.droppedOut = true;
				bossBar.removePlayer(p);
				p.sendSystemMessage(Component.literal("You left the arena and dropped out of the fight.").withStyle(ChatFormatting.GRAY));
				continue;
			}
			anyone = true;
		}
		if (!anyone) resetFight(level);
	}

	public void resetFight(ServerLevel level) {
		if (fighting && level.getServer() != null) dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWiped(getUUID(), "wipe");
		fighting = false;
		fightTicks = 0;
		BossFightRegistry.active(this, false);
		bossBar.removeAllPlayers();
		bossBar.setVisible(false);
		participants.clear();
		var inst = this.getAttribute(Attributes.MAX_HEALTH);
		if (inst != null) inst.removeModifier(PARTY_ID);
		partySize = 1;
		this.setHealth(this.getMaxHealth());
		this.entityData.set(DATA_PHASE, 1);
		this.entityData.set(DATA_TRANSITION, 0);
		Telegraph.clear(this);
		BossSummons.clear(this);
		if (snapshot != null) {
			snapshot.restore(level);
			snapshot = null;
		}
		if (arenaCenter != null) this.teleportTo(arenaCenter.getX() + 0.5, arenaCenter.getY(), arenaCenter.getZ() + 0.5);
		this.setTarget(null);
		this.getNavigation().stop();
		this.setDeltaMovement(Vec3.ZERO);
		if (startsDormant()) setDormant(true);
		onReset(level);
	}

	private void checkPhase(ServerLevel level) {
		float[] th = phaseThresholds();
		int phase = phase();
		float frac = this.getHealth() / this.getMaxHealth();
		if (phase - 1 < th.length && frac <= th[phase - 1]) {
			int next = phase + 1;
			this.entityData.set(DATA_PHASE, next);
			this.entityData.set(DATA_TRANSITION, TRANSITION_TICKS);
			level.playSound(null, blockPosition(), roarSound(), SoundSource.HOSTILE, 3.0F, 1.0F);
			announce("Phase " + next, 60);
			Telegraph.clear(this);
			onPhaseStart(level, next);
		}
	}

	/** Flashes the attack name under the boss bar. */
	public void announce(String attackName, int ticks) {
		bossBar.setName(Component.empty().append(this.getDisplayName()).append(Component.literal(" — " + attackName).withStyle(ChatFormatting.YELLOW)));
		announceTicks = ticks;
	}

	/** Radius of the particle seal ring; arenas whose rim is solid at the edge pull it inside so the wall is not buried. */
	protected double sealRingRadius() {
		return arenaRadius + 1.5;
	}

	private void drawSeal(ServerLevel level) {
		BlockPos c = arenaCenter;
		if (c == null) return;
		// P13: one packet per column (3 dust spread over the same 0.5-3.5 band) and 3 columns per radius block, was 5 x 3 packets
		int points = Math.max(48, arenaRadius * 6); // ~1.05 block spacing so the wall reads as continuous
		double phaseOffset = (this.tickCount % 40) / 40.0;
		DustParticleOptions dust = new DustParticleOptions(0x9FE8FF, 1.4F);
		// forced delivery (a plain sendParticles only reaches players within 32 blocks, so the far side of the ring never showed);
		// 3 stacked columns (0.5 / 1.5 / 2.5 up) keep the wall continuous with the same packet count as before per column
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2 * (i + phaseOffset) / points;
			double sr = sealRingRadius();
			double x = c.getX() + 0.5 + Math.cos(a) * sr, z = c.getZ() + 0.5 + Math.sin(a) * sr;
			for (int k = 0; k < 3; k++) forceParticles(level, dust, x, c.getY() + 0.5 + k, z, 1, 0, 0.3, 0, 0);
		}
	}

	/** Sends a particle to every player within 128 blocks, past the 32-block limit of the plain overload (alwaysShow set). */
	public static void forceParticles(ServerLevel level, net.minecraft.core.particles.ParticleOptions p, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		for (ServerPlayer pl : level.players()) {
			if (pl.distanceToSqr(x, y, z) <= 128.0 * 128.0) level.sendParticles(pl, p, true, true, x, y, z, count, dx, dy, dz, speed);
		}
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
		if (isDormant() || inTransition()) return !source.isCreativePlayer();
		return super.isInvulnerableTo(level, source);
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof ServerPlayer p) {
			Participant part = participants.get(p.getUUID());
			if (part == null && fighting) {
				join(p);
				part = participants.get(p.getUUID());
			}
			if (part != null) part.damage += Math.max(0, before - this.getHealth());
		}
		return hurt;
	}

	@Override
	public void die(DamageSource source) {
		if (this.level() instanceof ServerLevel level && !this.isRemoved() && !this.dead) {
			distributeLoot(level);
			BossFightRegistry.active(this, false);
			BossSummons.clear(this);
		}
		super.die(source);
	}

	/** Discard, unload or kill: nothing this boss spawned outlives it (QA 0.3.0 issue 9). */
	@Override
	public void remove(Entity.RemovalReason reason) {
		if (this.level() instanceof ServerLevel) BossSummons.clear(this);
		super.remove(reason);
	}

	@Override
	protected void tickDeath() {
		super.tickDeath();
		if (this.isRemoved() || this.deathTime >= 20) {
			bossBar.removeAllPlayers();
			if (this.level() instanceof ServerLevel level && snapshot != null) {
				snapshot.restore(level);
				snapshot = null;
			}
		}
	}

	private void distributeLoot(ServerLevel level) {
		float total = Math.max(1, participants.values().stream().map(p -> p.damage).reduce(0F, Float::sum));
		List<ServerPlayer> winners = new ArrayList<>();
		for (var e : participants.entrySet()) {
			ServerPlayer p = resolve(level, e.getKey());
			if (p == null) continue;
			Participant part = e.getValue();
			if (part.damage / total >= 0.05F || part.ticksInside >= 1200) winners.add(p);
		}
		var store = dev.elsewhere.boss.kit.KitStore.of(level.getServer());
		List<ItemStack> all = new ArrayList<>();
		for (ServerPlayer p : winners) {
			boolean first = !store.hasKilled(p.getUUID(), bossId()) && !p.entityTags().contains("elsewhere.killed." + bossId());
			all.addAll(rollDrops(p, first)); // boss rollDrops use BossRewards.rollUniques, which records the kill and the pity
			all.addAll(SignatureGearStub.roll(tier(), p, first));
			Component who = this.getDisplayName();
			p.sendSystemMessage(Component.literal("You defeated ").append(who).append(". The spoils lie where it fell.").withStyle(ChatFormatting.GOLD));
		}
		all.removeIf(ItemStack::isEmpty);
		Vec3 at = arenaCenter != null ? Vec3.atBottomCenterOf(arenaCenter).add(0, 1, 0) : position();
		dev.elsewhere.boss.kit.BossRewards.dropLoot(level, at, all);
		dev.elsewhere.boss.kit.BossRewards.awardXp(level, at, bossXp());
		store.fightWon(getUUID());
		lastLoot = all;
	}

	/** What the last kill dropped (self-test hook). */
	public List<ItemStack> lastLoot = List.of();

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		if (fighting && isParticipant(player.getUUID())) bossBar.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossBar.removePlayer(player);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
	}

	@Override
	public boolean canUsePortal(boolean ignorePassenger) {
		return false;
	}

	@Override
	protected boolean canRide(Entity vehicle) {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		if (arenaCenter != null) output.putLong("ArenaCenter", arenaCenter.asLong());
		output.putInt("ArenaRadius", arenaRadius);
		output.putInt("ArenaDown", arenaDown);
		output.putInt("ArenaUp", arenaUp);
		output.putBoolean("Dormant", isDormant());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		long c = input.getLongOr("ArenaCenter", Long.MIN_VALUE);
		if (c != Long.MIN_VALUE) arenaCenter = BlockPos.of(c);
		arenaRadius = input.getIntOr("ArenaRadius", arenaRadius);
		arenaDown = input.getIntOr("ArenaDown", arenaDown);
		arenaUp = input.getIntOr("ArenaUp", arenaUp);
		setDormant(input.getBooleanOr("Dormant", startsDormant()));
	}

	/** Real players come from the player list; FakePlayers (self-test, automation) from the level. */
	protected static @Nullable ServerPlayer resolve(ServerLevel level, UUID id) {
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
		if (p == null && level.getEntity(id) instanceof ServerPlayer fake) p = fake;
		return p;
	}

	/** Living players taking part right now. */
	public List<ServerPlayer> fighters(ServerLevel level) {
		List<ServerPlayer> out = new ArrayList<>();
		for (var e : participants.entrySet()) {
			if (e.getValue().droppedOut) continue;
			ServerPlayer p = resolve(level, e.getKey());
			if (p != null && p.isAlive() && p.level() == level) out.add(p);
		}
		return out;
	}

	/** Spreads attacks across players: the fighter attacked least recently (round robin). */
	private int roundRobin;

	public @Nullable ServerPlayer pickTarget(ServerLevel level) {
		List<ServerPlayer> f = fighters(level);
		if (f.isEmpty()) return null;
		f.removeIf(p -> !insideArena(p));
		if (f.isEmpty()) return null;
		return f.get(Math.floorMod(roundRobin++, f.size()));
	}

	public static boolean isBossPlayerHit(Player player, DamageSource source) {
		return source.getEntity() instanceof BossBase;
	}
}
