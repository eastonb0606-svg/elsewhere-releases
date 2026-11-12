package dev.elsewhere.core.boss;

import dev.elsewhere.ElsewhereConfig;
import dev.elsewhere.registry.ModSounds;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shared boss framework (BOSSES.md "Shared boss framework"). Every Elsewhere boss extends this. It provides:
 * <ul>
 * <li>dormant/awake lifecycle with party scaling at wake (x(1 + 0.5 per extra player in the arena));</li>
 * <li>a boss bar shown to players near the arena;</li>
 * <li>an {@link ArenaSeal}: snapshot, seal ring, 30 s leave timer, reset to full health + arena restore when everyone
 * has left or died;</li>
 * <li>a phase machine: {@link #setPhase} starts a 2 s invulnerable transition with a roar;</li>
 * <li>the overpower guard on damage taken and {@link #hitGuarded} applying the lethal-hit guard on damage dealt;</li>
 * <li>per-player loot: each participant (5% of the damage or 60 s in the arena) gets their own roll of the loot table,
 * as items only they can pick up.</li>
 * </ul>
 * Subclasses implement {@link #bossTick}, {@link #canBeHurtNow}, {@link #onPhaseChange}, and set {@link #baseMaxHealth()}.
 */
public abstract class ElsewhereBoss extends Monster {
	private static final EntityDataAccessor<Boolean> DATA_AWAKE = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_TRANSITION = SynchedEntityData.defineId(ElsewhereBoss.class, EntityDataSerializers.INT);
	public static final int TRANSITION_TICKS = 40;

	protected final ServerBossEvent bossEvent;
	protected @Nullable ArenaSeal arena;
	protected @Nullable BlockPos home;
	protected int arenaRadius = 16;
	protected final Map<UUID, Float> damageBy = new HashMap<>();
	protected final Map<UUID, Integer> ticksInArena = new HashMap<>();
	protected final Map<UUID, Integer> ticksOutside = new HashMap<>();
	private int emptyTicks;
	private int partySize = 1;

	protected ElsewhereBoss(final EntityType<? extends Monster> type, final Level level, final BossEvent.BossBarColor color,
		final BossEvent.BossBarOverlay overlay) {
		super(type, level);
		this.bossEvent = new ServerBossEvent(this.getUUID(), this.getDisplayName(), color, overlay);
		this.bossEvent.setVisible(false);
		this.setPersistenceRequired();
		this.xpReward = 250;
	}

	/** Base (pre-Test-Meter, solo) max health. */
	protected abstract double baseMaxHealth();

	/** Boss AI, called every server tick while awake and not mid-transition. */
	protected abstract void bossTick(ServerLevel level);

	/** Mechanic gating (hearts, eye open...). Return false to make the hit bounce off. */
	protected abstract boolean canBeHurtNow(ServerLevel level, DamageSource source);

	protected void onPhaseChange(final ServerLevel level, final int from, final int to) {
	}

	/** Called once when the boss wakes; build the seal, spawn effects. */
	protected void onWake(final ServerLevel level) {
	}

	/** Called on reset after everyone left or died. */
	protected void onReset(final ServerLevel level) {
	}

	@Override
	protected void defineSynchedData(final SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_AWAKE, false);
		builder.define(DATA_PHASE, 0);
		builder.define(DATA_TRANSITION, 0);
	}

	// ------------------------------------------------------------------ state
	public boolean isAwake() {
		return this.entityData.get(DATA_AWAKE);
	}

	public int phase() {
		return this.entityData.get(DATA_PHASE);
	}

	public int transitionTicks() {
		return this.entityData.get(DATA_TRANSITION);
	}

	public int partySize() {
		return this.partySize;
	}

	public @Nullable ArenaSeal arena() {
		return this.arena;
	}

	/** Sets the arena centre (usually the boss spawn point) and radius. Call right after spawning. */
	public void setArena(final BlockPos center, final int radius, final int below, final int above) {
		this.home = center;
		this.arenaRadius = radius;
		this.arena = new ArenaSeal(center, radius, below, above);
	}

	/** Starts a phase transition (2 s invulnerable, roar). */
	public void setPhase(final int phase) {
		int old = this.phase();
		if (old == phase) {
			return;
		}
		this.entityData.set(DATA_PHASE, phase);
		if (this.level() instanceof ServerLevel server) {
			this.entityData.set(DATA_TRANSITION, TRANSITION_TICKS);
			server.playSound(null, this.getX(), this.getY(), this.getZ(), this.roarSound(), SoundSource.HOSTILE, 3.0F, 0.8F);
			this.onPhaseChange(server, old, phase);
		}
	}

	protected net.minecraft.sounds.SoundEvent roarSound() {
		return ModSounds.MOTHER_CREAK_ROAR.value();
	}

	// ------------------------------------------------------------------ lifecycle
	/** Wakes the boss: party scaling, boss bar, arena snapshot and seal. */
	public void wake(final ServerLevel level) {
		this.wake(level, this.playersInArena(level).size());
	}

	/** Wakes with an explicit party size (players inside the arena when the fight starts). */
	public void wake(final ServerLevel level, final int players) {
		if (this.isAwake()) {
			return;
		}
		this.partySize = Math.max(1, players);
		double hp = this.baseMaxHealth() * (1.0 + ElsewhereConfig.get().bosses.partyHealthPerPlayer * (this.partySize - 1));
		AttributeInstance max = this.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(hp);
		}
		this.setHealth(this.getMaxHealth());
		this.entityData.set(DATA_AWAKE, true);
		if (this.arena != null) {
			this.arena.snapshot(level);
			this.arena.raise(level);
			level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.BOSS_SEAL.value(), SoundSource.HOSTILE, 2.0F, 1.0F);
		}
		this.bossEvent.setVisible(true);
		this.bossEvent.setName(this.getDisplayName());
		level.playSound(null, this.getX(), this.getY(), this.getZ(), this.roarSound(), SoundSource.HOSTILE, 4.0F, 1.0F);
		this.setPhase(1);
		this.onWake(level);
	}

	/** Everyone left or died: full heal, restore the arena, back to sleep. */
	public void resetFight(final ServerLevel level) {
		this.entityData.set(DATA_AWAKE, false);
		this.entityData.set(DATA_PHASE, 0);
		this.entityData.set(DATA_TRANSITION, 0);
		AttributeInstance max = this.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(this.baseMaxHealth());
		}
		this.setHealth(this.getMaxHealth());
		this.damageBy.clear();
		this.ticksInArena.clear();
		this.ticksOutside.clear();
		this.bossEvent.setVisible(false);
		this.bossEvent.removeAllPlayers();
		if (this.arena != null) {
			this.arena.lower(level);
			this.arena.restore(level);
		}
		if (this.home != null) {
			this.snapTo(this.home.getX() + 0.5, this.home.getY(), this.home.getZ() + 0.5, this.getYRot(), 0.0F);
		}
		this.setTarget(null);
		this.getNavigation().stop();
		this.onReset(level);
	}

	public List<ServerPlayer> playersInArena(final ServerLevel level) {
		if (this.arena == null) {
			return level.getEntitiesOfClass(ServerPlayer.class, this.getBoundingBox().inflate(32.0), p -> p.isAlive() && !p.isSpectator());
		}
		return level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(this.arena.center).inflate(this.arena.radius + 2, 48, this.arena.radius + 2),
			p -> p.isAlive() && !p.isSpectator() && this.arena.contains(p.position()));
	}

	@Override
	protected void customServerAiStep(final ServerLevel level) {
		super.customServerAiStep(level);
		if (!this.isAwake()) {
			return;
		}
		int t = this.transitionTicks();
		if (t > 0) {
			this.entityData.set(DATA_TRANSITION, t - 1);
		}
		this.trackParticipants(level);
		this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
		if (this.isAwake() && t <= 0) {
			this.bossTick(level);
		}
	}

	private void trackParticipants(final ServerLevel level) {
		List<ServerPlayer> inside = this.playersInArena(level);
		Set<UUID> insideIds = new HashSet<>();
		for (ServerPlayer p : inside) {
			insideIds.add(p.getUUID());
			this.ticksInArena.merge(p.getUUID(), 1, Integer::sum);
			this.ticksOutside.remove(p.getUUID());
			this.bossEvent.addPlayer(p);
		}
		int leave = ElsewhereConfig.get().bosses.arenaLeaveSeconds * 20;
		for (UUID id : Set.copyOf(this.ticksInArena.keySet())) {
			if (!insideIds.contains(id)) {
				int out = this.ticksOutside.merge(id, 1, Integer::sum);
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
				if (out > leave || p == null || !p.isAlive()) {
					if (p != null) {
						this.bossEvent.removePlayer(p);
					}
				}
			}
		}
		if (inside.isEmpty()) {
			this.emptyTicks++;
			if (this.emptyTicks > leave) {
				this.emptyTicks = 0;
				this.resetFight(level);
			}
		} else {
			this.emptyTicks = 0;
		}
	}

	// ------------------------------------------------------------------ damage
	@Override
	public boolean hurtServer(final ServerLevel level, final DamageSource source, float damage) {
		boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!bypass) {
			if (!this.isAwake()) {
				if (source.getEntity() instanceof ServerPlayer && this.isAlive()) {
					this.wake(level);
				}
				return false;
			}
			if (this.transitionTicks() > 0 || !this.canBeHurtNow(level, source)) {
				return false;
			}
			damage = BossGuards.overpower(damage);
		}
		float before = this.getHealth();
		boolean hurt = super.hurtServer(level, source, damage);
		if (hurt && source.getEntity() instanceof Player p) {
			this.damageBy.merge(p.getUUID(), Math.max(0.0F, before - this.getHealth()), Float::sum);
		}
		return hurt;
	}

	/** Deals boss damage to a target with the lethal-hit guard applied. */
	public boolean hitGuarded(final ServerLevel level, final LivingEntity target, final float raw, final DamageSource source) {
		float dmg = target instanceof Player ? BossGuards.lethalCap(target, raw, source) : raw;
		return target.hurtServer(level, source, dmg);
	}

	// ------------------------------------------------------------------ loot
	/** Extra per-participant drops computed in code (e.g. items owned by other modules, looked up by id). */
	protected java.util.List<net.minecraft.world.item.ItemStack> extraLoot(final ServerLevel level) {
		return java.util.List.of();
	}

	/**
	 * True while the player stands inside the arena of an awake Elsewhere boss in their level (core bosses and every boss
	 * built on this framework). Used to switch off kill heal inside boss arenas (ARSENAL.md kill_heal).
	 */
	public static boolean inActiveArena(final ServerPlayer player) {
		if (!(player.level() instanceof ServerLevel level)) {
			return false;
		}
		net.minecraft.world.phys.AABB box = player.getBoundingBox().inflate(96.0);
		for (ElsewhereBoss boss : level.getEntitiesOfClass(ElsewhereBoss.class, box, b -> b.isAlive() && b.isAwake())) {
			if (boss.arena != null ? boss.arena.contains(player.position()) : boss.distanceToSqr(player) < (double)boss.arenaRadius * boss.arenaRadius) {
				return true;
			}
		}
		return false;
	}

	/** Participants: dealt at least 5% of max health, or spent 60 s in the arena. */
	public List<ServerPlayer> participants(final ServerLevel level) {
		float need = this.getMaxHealth() * 0.05F;
		return level.getServer().getPlayerList().getPlayers().stream()
			.filter(p -> this.damageBy.getOrDefault(p.getUUID(), 0.0F) >= need || this.ticksInArena.getOrDefault(p.getUUID(), 0) >= 1200)
			.toList();
	}

	/** 2.0 boss-kit drop rules: non-null = DropRule uniques, ground drops, always XP (see {@link #dropWithKit}). */
	protected java.util.@org.jspecify.annotations.Nullable List<dev.elsewhere.boss.kit.DropRule.Unique> kitUniques() {
		return null;
	}

	/** Tier signature item (100% first kill, 10% repeat), or null. */
	protected net.minecraft.world.item.@org.jspecify.annotations.Nullable Item tierItem() {
		return null;
	}

	public static final float TIER_REPEAT = 0.10F;
	/** XP awarded by the last kit kill (self-test hook). */
	public int lastKitXp = -1;

	public String kitId() {
		return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.getType()).getPath();
	}

	/**
	 * DECISIONS-2.0 drop rules: each credited player (participants, else the killer) rolls the uniques with
	 * {@link dev.elsewhere.boss.kit.BossRewards#rollUniques} (first kill: signature 100%, others 50%; later 67% / 33% + pity;
	 * extra-life 100% / 5%) and the tier item (100% / 10%); the loot table (non-unique drops) rolls once per credited player.
	 * Everything drops on the ground like any mob; XP always drops ({@link dev.elsewhere.boss.kit.BossRewards#awardXp}).
	 */
	protected void dropWithKit(final ServerLevel level, final DamageSource source, final List<dev.elsewhere.boss.kit.DropRule.Unique> uniques) {
		List<ServerPlayer> party = new java.util.ArrayList<>(this.participants(level));
		if (party.isEmpty() && source.getEntity() instanceof ServerPlayer killer) {
			party.add(killer);
		}
		List<net.minecraft.world.item.ItemStack> all = new java.util.ArrayList<>();
		Optional<ResourceKey<LootTable>> table = this.getLootTable();
		for (int i = 0; i < Math.max(1, party.size()); i++) {
			table.ifPresent(t -> this.dropFromLootTable(level, source, true, t, all::add));
			all.addAll(this.extraLoot(level));
		}
		for (ServerPlayer p : party) {
			boolean first = !dev.elsewhere.boss.kit.KitStore.of(level.getServer()).hasKilled(p.getUUID(), this.kitId());
			all.addAll(dev.elsewhere.boss.kit.BossRewards.rollUniques(p, this.kitId(), first, uniques));
			net.minecraft.world.item.Item tier = this.tierItem();
			if (tier != null && (first || this.random.nextFloat() < TIER_REPEAT)) {
				all.add(new net.minecraft.world.item.ItemStack(tier));
			}
		}
		dev.elsewhere.boss.kit.BossRewards.dropLoot(level, this.position().add(0.0, 1.0, 0.0), all);
		this.lastKitXp = dev.elsewhere.boss.kit.BossRewards.awardXp(level, this.position(), dev.elsewhere.boss.kit.BossRewards.XP_FLOOR);
	}

	@Override
	protected void dropFromLootTable(final ServerLevel level, final DamageSource source, final boolean playerKilled) {
		List<dev.elsewhere.boss.kit.DropRule.Unique> kit = this.kitUniques();
		if (kit != null) {
			this.dropWithKit(level, source, kit);
			return;
		}
		Optional<ResourceKey<LootTable>> table = this.getLootTable();
		if (table.isEmpty()) {
			return;
		}
		List<ServerPlayer> party = this.participants(level);
		if (party.isEmpty()) {
			super.dropFromLootTable(level, source, playerKilled);
			for (net.minecraft.world.item.ItemStack extra : this.extraLoot(level)) {
				this.spawnAtLocation(level, extra);
			}
			for (net.minecraft.world.item.ItemStack extra : BossDrops.roll(this.getType(), null, this.random)) {
				this.spawnAtLocation(level, extra);
			}
			return;
		}
		for (ServerPlayer p : party) {
			java.util.List<net.minecraft.world.item.ItemStack> extras = new java.util.ArrayList<>(this.extraLoot(level));
			extras.addAll(BossDrops.roll(this.getType(), p, this.random));
			BossDrops.recordKill(p, this.getType());
			for (net.minecraft.world.item.ItemStack extra : extras) {
				ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 1.0, this.getZ(), extra);
				item.setTarget(p.getUUID());
				item.setExtendedLifetime();
				level.addFreshEntity(item);
			}
			this.dropFromLootTable(level, source, true, table.get(), stack -> {
				ItemEntity item = new ItemEntity(level, this.getX(), this.getY() + 1.0, this.getZ(), stack);
				item.setTarget(p.getUUID());
				item.setExtendedLifetime();
				item.setGlowingTag(true);
				level.addFreshEntity(item);
			});
		}
	}

	@Override
	public void die(final DamageSource source) {
		super.die(source);
		if (this.level() instanceof ServerLevel server && this.arena != null) {
			this.arena.lower(server);
		}
		this.bossEvent.removeAllPlayers();
	}

	@Override
	public void remove(final Entity.RemovalReason reason) {
		if (this.level() instanceof ServerLevel server && this.arena != null && reason.shouldDestroy()) {
			this.arena.lower(server);
		}
		this.bossEvent.removeAllPlayers();
		super.remove(reason);
	}

	// ------------------------------------------------------------------ misc
	@Override
	public void startSeenByPlayer(final ServerPlayer player) {
		super.startSeenByPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(final ServerPlayer player) {
		super.stopSeenByPlayer(player);
		this.bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(final @Nullable Component name) {
		super.setCustomName(name);
		this.bossEvent.setName(this.getDisplayName());
	}

	@Override
	public boolean removeWhenFarAway(final double distSqr) {
		return false;
	}

	@Override
	public void checkDespawn() {
		if (this.level().getDifficulty() == Difficulty.PEACEFUL) {
			this.discard();
		} else {
			this.noActionTime = 0;
		}
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	protected boolean canRide(final Entity vehicle) {
		return false;
	}

	@Override
	public boolean canUsePortal(final boolean ignorePassenger) {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(final ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putBoolean("Awake", this.isAwake());
		output.putInt("Phase", this.phase());
		output.putInt("PartySize", this.partySize);
		if (this.arena != null) {
			output.store("ArenaCenter", BlockPos.CODEC, this.arena.center);
			output.putInt("ArenaRadius", this.arena.radius);
			output.putInt("ArenaBelow", this.arena.center.getY() - this.arena.minY);
			output.putInt("ArenaAbove", this.arena.maxY - this.arena.center.getY());
		}
	}

	@Override
	protected void readAdditionalSaveData(final ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(DATA_AWAKE, input.getBooleanOr("Awake", false));
		this.entityData.set(DATA_PHASE, input.getIntOr("Phase", 0));
		this.partySize = input.getIntOr("PartySize", 1);
		input.read("ArenaCenter", BlockPos.CODEC).ifPresent(c -> this.setArena(c, input.getIntOr("ArenaRadius", 16),
			input.getIntOr("ArenaBelow", 2), input.getIntOr("ArenaAbove", 12)));
		this.bossEvent.setVisible(this.isAwake());
		this.bossEvent.setName(this.getDisplayName());
	}

	/** Small helper for subclasses: nearest living, non-spectator, non-creative player in the arena. */
	protected @Nullable ServerPlayer nearestFighter(final ServerLevel level) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer p : this.playersInArena(level)) {
			if (p.isCreative()) {
				continue;
			}
			double d = p.distanceToSqr(this);
			if (d < bestD) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	protected static Vec3 flat(final Vec3 v) {
		return new Vec3(v.x, 0.0, v.z);
	}
}
