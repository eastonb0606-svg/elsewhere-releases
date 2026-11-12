package dev.elsewhere.stub.greenhaze_hush;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * STUB of the shared boss framework (BOSSES.md "Shared boss framework" + "Difficulty framework"). Boss-specific logic
 * lives in subclasses; this class only provides what the core will own:
 * HP-threshold phase machine with 2 s invulnerable transitions, boss bar with an attack-name flash, overpower guard,
 * arena seal/reset, 30 s drop-out, multiplayer HP scaling, per-player loot with a guaranteed first-kill signature drop,
 * and a small action scheduler for wind-ups.
 * Bosses extend {@link Monster} so they implement {@code Enemy} and Test Meter's x1.53 applies on top of the base HP.
 */
public abstract class BossEntity extends Monster {
	public static final int TRANSITION_TICKS = 40;
	public static final int DROP_OUT_TICKS = 600;
	public static final int JOIN_WINDOW_TICKS = 600;
	/** Self-test only: fake players are not in the player list, so don't auto-reset. */
	public static boolean selfTestNoReset;

	/** Per-player set of boss ids already killed (first kill guarantees the signature drop). */
	public static final AttachmentType<List<String>> KILLS = AttachmentRegistry.create(
		Identifier.fromNamespaceAndPath("elsewhere", "greenhaze_hush_boss_kills"),
		b -> b.persistent(Codec.STRING.listOf()).initializer(List::of).copyOnDeath());

	public final ServerBossEvent bossBar;
	protected int phase;
	protected int transitionTicks;
	protected @Nullable ArenaSeal arena;
	protected int fightTicks;
	protected final Map<UUID, Float> damageDealt = new HashMap<>();
	protected final Map<UUID, Integer> ticksInside = new HashMap<>();
	protected final Map<UUID, Integer> ticksOutside = new HashMap<>();
	protected final Set<UUID> participants = new HashSet<>();
	/** Players seen damaging the boss (also covers FakePlayers, which are not in the player list). */
	private final Map<UUID, ServerPlayer> known = new HashMap<>();
	private final List<Scheduled> scheduled = new ArrayList<>();
	private final Map<String, Integer> cooldowns = new HashMap<>();
	private @Nullable Component flash;
	private int flashTicks;
	private int scaledFor = 1;
	/** Reset count, for the self-test. */
	public int resets;
	public float lastDamageTaken;
	private int @Nullable [] pendingArena;
	private long pendingCenter;

	private record Scheduled(int at, Runnable action) {}

	/** Live bosses, so a player who changes dimension is dropped from every boss bar at once. */
	private static final Set<BossEntity> LIVE = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
	private static boolean hooked;

	private static void hook() {
		if (hooked) return;
		hooked = true;
		net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, from, to) -> {
			for (BossEntity b : new ArrayList<>(LIVE)) b.bossBar.removePlayer(player);
		});
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			for (BossEntity b : new ArrayList<>(LIVE)) b.bossBar.removePlayer(handler.getPlayer());
		});
	}

	protected BossEntity(EntityType<? extends BossEntity> type, Level level, BossEvent.BossBarColor color) {
		super(type, level);
		this.bossBar = new ServerBossEvent(UUID.randomUUID(), type.getDescription(), color, BossEvent.BossBarOverlay.NOTCHED_10);
		this.bossBar.setDarkenScreen(false);
		this.setPersistenceRequired();
		this.xpReward = 0; // XP comes from BossRewards.awardXp (always, 400+), so vanilla XP is switched off to avoid doubling
	}

	/**
	 * Bosses call moveTo every tick; re-pathing a big mob every tick in a built arena cost Auris ~20 ms/tick in QA.
	 * Keep the current path unless 10 ticks passed or the goal moved more than 2 blocks.
	 */
	static final class ThrottledNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
		private double gx = Double.NaN, gy, gz;
		private int lastTick = -100;

		ThrottledNavigation(net.minecraft.world.entity.Mob mob, Level level) {
			super(mob, level);
		}

		private boolean keep(double x, double y, double z) {
			int now = mob.tickCount;
			boolean close = !Double.isNaN(gx) && (gx - x) * (gx - x) + (gy - y) * (gy - y) + (gz - z) * (gz - z) < 4;
			if (!isDone() && close && now - lastTick < 10) return true;
			gx = x; gy = y; gz = z;
			lastTick = now;
			return false;
		}

		@Override
		public boolean moveTo(double x, double y, double z, double speed) {
			if (keep(x, y, z)) { setSpeedModifier(speed); return true; }
			return super.moveTo(x, y, z, speed);
		}

		@Override
		public boolean moveTo(net.minecraft.world.entity.Entity target, double speed) {
			if (keep(target.getX(), target.getY(), target.getZ())) { setSpeedModifier(speed); return true; }
			return super.moveTo(target, speed);
		}
	}

	@Override
	protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
		return new ThrottledNavigation(this, level);
	}

	// ---------------------------------------------------------------------------------------------- hooks

	public abstract String bossId();

	/** Base HP before Test Meter (registered max health). */
	public abstract double baseHealth();

	/** Health fractions where phases 2, 3, ... begin, descending (e.g. {0.65, 0.30}). */
	protected abstract float[] phaseThresholds();

	protected void onPhaseStart(ServerLevel level, int phase) {}

	/** Runs every tick outside phase transitions. */
	protected abstract void bossTick(ServerLevel level);

	/** Damage multiplier for weak points / exposed windows (x2 from behind while closing a vent, ...). */
	protected float damageMultiplier(ServerLevel level, DamageSource source, float amount) {
		return 1F;
	}

	/** Mechanic gating: false means the hit glances off. */
	protected boolean acceptsDamage(ServerLevel level, DamageSource source) {
		return true;
	}

	/** Loot for one participant. firstKill means this player's first kill of this boss. */
	public abstract List<ItemStack> rollLoot(ServerPlayer player, boolean firstKill);

	/** Called when the fight resets (everyone left or died) before the arena is restored. */
	protected void onReset(ServerLevel level) {}

	// ---------------------------------------------------------------------------------------------- arena

	public void setArena(ArenaSeal arena) {
		this.arena = arena;
	}

	public @Nullable ArenaSeal arena() {
		return arena;
	}

	public int phase() {
		return phase;
	}

	/** Health fraction where the next phase starts (0 in the last phase). */
	public float nextThreshold() {
		float[] t = phaseThresholds();
		return phase < t.length ? t[phase] : 0F;
	}

	public boolean inTransition() {
		return transitionTicks > 0;
	}

	public List<ServerPlayer> fighters() {
		if (!(level() instanceof ServerLevel sl)) return List.of();
		if (arena != null) return arena.playersInside();
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : sl.players()) if (p.isAlive() && !p.isSpectator() && p.distanceToSqr(this) < 48 * 48) out.add(p);
		return out;
	}

	// ---------------------------------------------------------------------------------------------- helpers for subclasses

	protected void schedule(int delayTicks, Runnable action) {
		scheduled.add(new Scheduled(tickCount + delayTicks, action));
	}

	protected boolean ready(String attack) {
		return cooldowns.getOrDefault(attack, 0) <= tickCount;
	}

	protected void cooldown(String attack, int ticks) {
		cooldowns.put(attack, tickCount + ticks);
	}

	/** Flash an attack name under the boss bar (shared framework: "its name flashes under the boss bar"). */
	protected void announce(String attackName, ChatFormatting color) {
		flash = Component.literal(attackName).withStyle(color, ChatFormatting.BOLD);
		flashTicks = 50;
		// sfx lane 3.0: each named ATTACK (not openings or state banners) gets this boss's telegraph wind-up at its own fixed pitch
		if (transitionTicks <= 0 && dev.elsewhere.greenhaze_hush.Sfx3.isAttack(attackName)) dev.elsewhere.greenhaze_hush.Sfx3.tele(this, bossId(), attackName.split(":", 2)[0], color == ChatFormatting.RED);
	}

	protected void playBoss(SoundEvent sound, float volume, float pitch) {
		level().playSound(null, getX(), getY(), getZ(), sound, getSoundSource(), volume, pitch);
	}

	protected boolean hitScripted(ServerLevel level, LivingEntity target, float raw) {
		return BossDamage.hit(level, this, target, raw);
	}

	public float healthFraction() {
		return getHealth() / getMaxHealth();
	}

	// ---------------------------------------------------------------------------------------------- core loop

	@Override
	protected void customServerAiStep(ServerLevel level) {
		super.customServerAiStep(level);
		if (LIVE.add(this)) hook();
		if (arena == null && pendingArena != null) {
			arena = ArenaSeal.relink(level, BlockPos.of(pendingCenter), pendingArena[1], pendingArena[2], pendingArena[3]);
			pendingArena = null;
		}
		fightTicks++;
		// scheduled wind-up actions
		if (!scheduled.isEmpty()) {
			List<Scheduled> due = new ArrayList<>();
			for (Iterator<Scheduled> it = scheduled.iterator(); it.hasNext(); ) {
				Scheduled s = it.next();
				if (s.at() <= tickCount) { due.add(s); it.remove(); }
			}
			for (Scheduled s : due) if (isAlive()) s.action().run();
		}
		updateParticipants(level);
		if (!isAlive()) return;
		openings.tick(level, this);
		// phase machine
		float[] t = phaseThresholds();
		if (transitionTicks > 0) {
			transitionTicks--;
			getNavigation().stop();
		} else if (phase < t.length && healthFraction() <= t[phase]) {
			phase++;
			transitionTicks = TRANSITION_TICKS;
			scheduled.clear();
			if (!dev.elsewhere.greenhaze_hush.Sfx3.roar(this, bossId())) playBoss(SoundEvents.ENDER_DRAGON_GROWL, 2.5F, 0.7F + 0.1F * phase);
			announce("Phase " + (phase + 1), ChatFormatting.GOLD);
			onPhaseStart(level, phase);
		} else {
			bossTick(level);
		}
		updateBar();
	}

	private void updateBar() {
		bossBar.setProgress(Math.max(0, healthFraction()));
		Component base = getType().getDescription();
		if (flashTicks > 0 && flash != null) {
			flashTicks--;
			bossBar.setName(Component.empty().append(base).append(Component.literal("  »  ").withStyle(ChatFormatting.GRAY)).append(flash));
		} else {
			bossBar.setName(base);
		}
	}

	private void updateParticipants(ServerLevel level) {
		List<ServerPlayer> inside = fighters();
		Set<UUID> insideIds = new HashSet<>();
		for (ServerPlayer p : inside) {
			insideIds.add(p.getUUID());
			ticksInside.merge(p.getUUID(), 1, Integer::sum);
			ticksOutside.remove(p.getUUID());
			if (participants.add(p.getUUID()) && fightTicks <= JOIN_WINDOW_TICKS) rescaleHealth();
			ensureKitFight(level, p);
		}
		for (Iterator<UUID> it = participants.iterator(); it.hasNext(); ) {
			UUID id = it.next();
			if (insideIds.contains(id)) continue;
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			int out = ticksOutside.merge(id, 1, Integer::sum);
			if (p == null || !p.isAlive() || out > DROP_OUT_TICKS) it.remove();
		}
		// boss bar viewers: everyone near the arena (and nobody who left the dimension)
		for (ServerPlayer p : new ArrayList<>(bossBar.getPlayers())) {
			if (p.level() != level || p.isRemoved() || p.distanceToSqr(this) >= 64 * 64) bossBar.removePlayer(p);
		}
		for (ServerPlayer p : level.players()) {
			boolean near = p.distanceToSqr(this) < 64 * 64;
			if (near && !bossBar.getPlayers().contains(p)) bossBar.addPlayer(p);
			else if (!near && bossBar.getPlayers().contains(p)) bossBar.removePlayer(p);
		}
		if (!selfTestNoReset && fightTicks > 100 && participants.isEmpty() && arena != null && arena.isSealed()) resetFight(level);
	}

	/** Shared framework: HP x(1 + 0.5 per extra player) for players inside in the first 30 s. */
	private void rescaleHealth() {
		int n = Math.max(1, participants.size());
		if (n <= scaledFor) return;
		float frac = healthFraction();
		var attr = getAttribute(Attributes.MAX_HEALTH);
		if (attr != null) {
			attr.setBaseValue(baseHealth() * (1 + 0.5 * (n - 1)));
			setHealth(getMaxHealth() * frac);
		}
		scaledFor = n;
	}

	/** Everyone left or died: full heal, arena restored, boss gone (re-summon at the altar). */
	public void resetFight(ServerLevel level) {
		resets++;
		if (level.getServer() != null) dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWiped(getUUID(), "wipe");
		onReset(level);
		scheduled.clear();
		Telegraph.clear(level);
		bossBar.removeAllPlayers();
		if (arena != null) {
			arena.restore();
			arena.release();
		}
		discard();
	}

	// ---------------------------------------------------------------------------------------------- damage

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		if (source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurtServer(level, source, amount);
		if (transitionTicks > 0) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK.value(), getSoundSource(), 1F, 0.6F);
			return false;
		}
		if (!acceptsDamage(level, source)) {
			level.playSound(null, getX(), getY(), getZ(), SoundEvents.SHIELD_BLOCK.value(), getSoundSource(), 0.8F, 1.4F);
			return false;
		}
		if (source.getEntity() instanceof ServerPlayer sp && source.getDirectEntity() == sp) {
			boolean crit = sp.fallDistance > 0 && !sp.onGround() && !sp.isSprinting();
			amount = SignatureGear.modifyOutgoing(sp, this, amount, crit);
		}
		float dmg = BossDamage.overpowerGuard(amount) * damageMultiplier(level, source, amount) * openings.multiplier(level.getGameTime());
		lastDamageTaken = dmg;
		Entity attacker = source.getEntity();
		if (attacker instanceof ServerPlayer p) {
			damageDealt.merge(p.getUUID(), dmg, Float::sum);
			ensureKitFight(level, p);
			known.put(p.getUUID(), p);
			participants.add(p.getUUID());
		}
		return super.hurtServer(level, source, dmg);
	}

	@Override
	public boolean removeWhenFarAway(double distSqr) {
		return false;
	}

	@Override
	public void die(DamageSource source) {
		super.die(source);
		if (level() instanceof ServerLevel level) {
			deliverLoot(level);
			bossBar.removeAllPlayers();
			Telegraph.clear(level);
			if (arena != null) {
				arena.restore();
				arena.release();
			}
		}
	}

	@Override
	public void remove(RemovalReason reason) {
		super.remove(reason);
		LIVE.remove(this);
		bossBar.removeAllPlayers();
	}

	/** bal-boss-punish-window: this boss's own openings (see dev.elsewhere.boss.kit.Openings for its table). */
	protected dev.elsewhere.boss.kit.Openings.Tracker openings = new dev.elsewhere.boss.kit.Openings.Tracker();

	/** Opens this boss's window for {@code attackId} (if the table has one) {@code delayTicks} from now, i.e. when the attack ends. */
	public void attackDone(String attackId, int delayTicks) {
		schedule(delayTicks, () -> { if (level() instanceof ServerLevel sl) openings.attackFinished(sl, this, bossId(), attackId); });
	}

	public float openingMultiplier() {
		return openings.multiplier(level().getGameTime());
	}

	/** What the summoner spent (refunded 30 s after a wipe by the boss kit); null when nothing is consumed. */
	protected @Nullable ItemStack summonItem;
	private boolean kitFightStarted;

	/** fix-arena-restart / bal-boss-retry: the fight is recorded on disk with the first player in it (the summoner). */
	protected void ensureKitFight(ServerLevel level, ServerPlayer first) {
		if (kitFightStarted || level.getServer() == null) return;
		kitFightStarted = true;
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightStart(getUUID(), bossId(), first.getUUID(),
			summonItem == null ? ItemStack.EMPTY : summonItem, level.dimension().identifier().toString(),
			blockPosition().getX(), blockPosition().getY(), blockPosition().getZ());
	}

	public void setSummonItem(ItemStack stack) {
		this.summonItem = stack.copy();
	}

	/** Per-player loot: >=5% of the damage or 60 s inside the seal. Uniques follow the kit's drop rule and land on the ground. */
	protected void deliverLoot(ServerLevel level) {
		float total = 0;
		for (float f : damageDealt.values()) total += f;
		Set<UUID> eligible = new HashSet<>();
		for (Map.Entry<UUID, Float> e : damageDealt.entrySet()) if (total > 0 && e.getValue() / total >= 0.05F) eligible.add(e.getKey());
		for (Map.Entry<UUID, Integer> e : ticksInside.entrySet()) if (e.getValue() >= 1200) eligible.add(e.getKey());
		for (UUID id : eligible) {
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (p == null) p = known.get(id);
			if (p == null) continue;
			List<String> kills = new ArrayList<>(p.getAttachedOrCreate(KILLS));
			// first kill = this module's own record (KILLS): in the combined jar the arsenal/summoner loot hooks (Sporebloom Staff,
			// Hushward Bell) mark the shared kit record "killed" first, which would turn a first kill into a repeat here.
			boolean first = !kills.contains(bossId());
			if (first) { kills.add(bossId()); p.setAttached(KILLS, kills); }
			List<ItemStack> loot = rollLoot(p, first);
			dev.elsewhere.boss.kit.BossRewards.dropLoot(level, arenaCenter(), loot);
			p.sendSystemMessage(Component.literal("Your share of the spoils from ").append(getType().getDescription())
				.append(" (" + loot.size() + " item" + (loot.size() == 1 ? "" : "s") + ")").withStyle(ChatFormatting.GOLD));
		}
		lastLootRecipients = eligible.size();
		dev.elsewhere.boss.kit.BossRewards.awardXp(level, arenaCenter(), 0);
		dev.elsewhere.boss.kit.KitStore.of(level.getServer()).fightWon(getUUID());
	}

	public int lastLootRecipients;

	/** Kept for callers that still hand items straight to a player (polish-loot-chest = No: bosses drop on the ground). */
	protected static void giveLoot(ServerPlayer p, ItemStack stack) {
		p.getInventory().placeItemBackInInventory(stack.copy(), net.minecraft.util.Prediction.SERVER_ONLY);
	}

	// ---------------------------------------------------------------------------------------------- save

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putInt("boss_phase", phase);
		if (arena != null) {
			output.putLong("arena_center", arena.center.asLong());
			output.putInt("arena_radius", arena.radius);
			output.putInt("arena_below", arena.below);
			output.putInt("arena_above", arena.above);
		}
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		phase = input.getIntOr("boss_phase", 0);
		// relinked on the first tick (scanning blocks while the entity is being loaded is not safe)
		input.getLong("arena_center").ifPresent(c -> pendingArena = new int[]{0, input.getIntOr("arena_radius", 20),
			input.getIntOr("arena_below", 4), input.getIntOr("arena_above", 20)});
		input.getLong("arena_center").ifPresent(c -> pendingCenter = c);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
	}

	public Vec3 arenaCenter() {
		return arena != null ? Vec3.atBottomCenterOf(arena.center) : position();
	}
}
