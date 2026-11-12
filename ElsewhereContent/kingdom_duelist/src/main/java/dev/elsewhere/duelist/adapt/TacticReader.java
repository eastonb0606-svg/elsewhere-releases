package dev.elsewhere.duelist.adapt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * X11 {@code TacticReader}: reads a player's loadout (B1), keeps the 7-tactic ledger (B2) and queues counters.
 * One reader per brain boss (the Sultana, the Wazir); each has its own ledger. The boss calls {@link #tick} every
 * AI tick with its current player target (and {@link #hitBoss}/{@link #hitGuard} are fed by the damage hook through
 * {@link PlayerTrace}), then acts on {@link #pollCounter()} when idle.
 *
 * <p>Rules (exact): the read takes {@link #READ_DELAY} ticks from first sight (0 while {@link #setMarked marked} by a
 * falcon); it is lost after {@link #LOS_LOST} ticks without line of sight. Events go into a ring of 12 with the time.
 * Two events are the same tactic when class and side match (a combo also needs the same hit count); the window is 800
 * ticks. The first and second events are free; the THIRD inside the window queues the counter once, resets that tactic
 * and blocks it for 400 ticks. At most 2 counters are pending (oldest dropped); 6 per fight, then the reader stops.
 */
public final class TacticReader {
	public static final int READ_DELAY = 200, LOS_LOST = 600, RING = 12, WINDOW = 800, COUNTER_COOLDOWN = 400, MAX_PENDING = 2, MAX_COUNTERS = 6;
	public static final int REREAD = 200, COMBO_HITS = 3, COMBO_WINDOW = 50, HIGH_GROUND_BLOCKS = 3, HIGH_GROUND_TICKS = 60, TURTLE_TICKS = 200;
	public static final double TURTLE_FRACTION = 0.60, POKE_MIN_DIST = 8.0, PEARL_NEAR = 12.0, JUMP_NEAR = 5.0, HEAL_RETREAT = 6.0;
	/** Percent of mirage steps that are fakes (no gold flash), so a flash alone cannot bait her. */
	public static final int FEINT_PERCENT = 20;

	/** One recorded event. */
	public record Event(Tactic tactic, Side side, int count, long tick) {}

	/** A queued counter. */
	public record Counter(Tactic tactic, UUID player, Side side, long tick) {}

	private final LivingEntity boss;
	private final ArrayDeque<Event> ring = new ArrayDeque<>();
	private final ArrayDeque<Counter> pending = new ArrayDeque<>();
	private final EnumMap<Tactic, Long> blockedUntil = new EnumMap<>(Tactic.class);
	private final EnumSet<LoadoutClass> classes = EnumSet.noneOf(LoadoutClass.class);
	private long firstSight = -1, lastSeen = -1, lastClassRead = -100000;
	private boolean marked, done;
	private int countersFired;
	private final Map<Tactic, Long> lastDetect = new EnumMap<>(Tactic.class);
	private long lastGroundJumpSeen = -1, lastBowSeen = -1, lastPearlSeen = -1, lastHealSeen = -1, lastHighGround = -100000;
	private final ArrayDeque<Long> hitTimes = new ArrayDeque<>();
	private int lastHealUses;
	private boolean highGroundFlag;

	private static final List<TacticReader> ACTIVE = new ArrayList<>();
	private final java.util.Set<UUID> watched = new java.util.HashSet<>();

	public TacticReader(LivingEntity boss) {
		this.boss = boss;
		watched.add(boss.getUUID());
		ACTIVE.add(this);
	}

	/** Hits on {@code e} (a guard, the boss) count towards the combo tactic. */
	public void watch(LivingEntity e) {
		watched.add(e.getUUID());
	}

	/** Stops listening (boss death). */
	public void close() {
		ACTIVE.remove(this);
	}

	static void onPlayerHit(LivingEntity victim, Player p, long now) {
		for (TacticReader r : ACTIVE) if (r.watched.contains(victim.getUUID())) r.hitBoss(p, now);
	}

	public EnumSet<LoadoutClass> classes() {
		return classes.clone();
	}

	public boolean has(LoadoutClass c) {
		return classes.contains(c);
	}

	/** True once the read delay is over (counters are active). */
	public boolean readDone(long now) {
		return !done && firstSight >= 0 && (marked || now - firstSight >= READ_DELAY);
	}

	public boolean satisfied() {
		return done;
	}

	public int countersFired() {
		return countersFired;
	}

	public List<Event> events() {
		return new ArrayList<>(ring);
	}

	/** A falcon of the Falconer has marked the player: the read delay is 0 while this is true. */
	public void setMarked(boolean m) {
		this.marked = m;
	}

	/** Next counter to execute, or null. Call when the boss is free. */
	public @Nullable Counter pollCounter() {
		return pending.pollFirst();
	}

	public int pendingCount() {
		return pending.size();
	}

	/** True for 20 percent of mirage steps: a fake with no gold flash. */
	public boolean rollFeint(RandomSource r) {
		return r.nextInt(100) < FEINT_PERCENT;
	}

	/** Feed events (hits on the boss or its guards) from the damage hook. */
	public void hitBoss(Player p, long now) {
		hitTimes.addLast(now);
		while (hitTimes.size() > 8) hitTimes.pollFirst();
	}

	/** Call every AI tick with the player the boss is fighting. */
	public void tick(ServerLevel level, @Nullable Player target) {
		long now = level.getGameTime();
		if (done || target == null) return;
		PlayerTrace.sample(target, now);
		PlayerTrace.Trace tr = PlayerTrace.of(target);
		boolean los = boss.hasLineOfSight(target);
		if (firstSight < 0) {
			firstSight = now;
			lastClassRead = -100000;
		}
		if (los) lastSeen = now;
		else if (lastSeen >= 0 && now - lastSeen > LOS_LOST && !marked) {
			firstSight = -1; // the read is lost
			classes.clear();
			return;
		}
		if (now - lastClassRead >= REREAD) {
			classify(target, tr, now);
			lastClassRead = now;
		}
		detect(level, target, tr, now);
	}

	private void classify(Player p, PlayerTrace.Trace tr, long now) {
		classes.clear();
		ItemStack main = p.getMainHandItem();
		if (PlayerTrace.holdsRanged(p) || now - tr.lastBowShot <= 200) classes.add(LoadoutClass.RANGED);
		if (now - tr.lastShieldUp <= 200 || p.isBlocking()) classes.add(LoadoutClass.SHIELD);
		if (main.is(ItemTags.AXES) || main.is(Items.MACE)) classes.add(LoadoutClass.HEAVY);
		if (main.is(ItemTags.SPEARS) || main.is(Items.TRIDENT)) classes.add(LoadoutClass.REACH);
		if (main.is(ItemTags.SWORDS) && !(p.getOffhandItem().is(Items.SHIELD) || now - tr.lastShieldUp <= 200)) classes.add(LoadoutClass.LIGHT);
		if (p.hasEffect(MobEffects.SPEED) || p.hasEffect(MobEffects.JUMP_BOOST) || p.hasEffect(MobEffects.SLOW_FALLING) || PlayerTrace.elytra(p) || tr.pearlsThrown >= 1) classes.add(LoadoutClass.MOBILE);
		if (tr.healUses >= 2 || tr.potionsUsed >= 2) classes.add(LoadoutClass.POTION_HEAVY);
	}

	private void detect(ServerLevel level, Player p, PlayerTrace.Trace tr, long now) {
		double dist = boss.distanceTo(p);
		Side side = Side.of(boss, p);
		// T1 jump-crit opener: took off sprinting within 5 blocks
		if (tr.jumpTakeoffTick > lastGroundJumpSeen && now - tr.jumpTakeoffTick < 10) {
			lastGroundJumpSeen = tr.jumpTakeoffTick;
			if (dist <= JUMP_NEAR) record(Tactic.JUMP_CRIT_OPENER, side, 0, now, p);
		}
		// T2 combo: 3 or more hits within 50 ticks
		while (!hitTimes.isEmpty() && now - hitTimes.peekFirst() > COMBO_WINDOW) hitTimes.pollFirst();
		if (hitTimes.size() >= COMBO_HITS) {
			int n = hitTimes.size();
			hitTimes.clear();
			record(Tactic.COMBO, side, n, now, p);
		}
		// T3 ranged poke: an arrow from more than 8 blocks
		if (tr.lastArrowTick > lastBowSeen) {
			lastBowSeen = tr.lastArrowTick;
			if (dist > POKE_MIN_DIST) record(Tactic.RANGED_POKE, side, 0, now, p);
		}
		// T4 pearl in: landed within 12 blocks of the boss
		if (tr.lastPearlLanded > lastPearlSeen) {
			lastPearlSeen = tr.lastPearlLanded;
			double dx = tr.lastPearlLandX - boss.getX(), dy = tr.lastPearlLandY - boss.getY(), dz = tr.lastPearlLandZ - boss.getZ();
			if (dx * dx + dy * dy + dz * dz <= PEARL_NEAR * PEARL_NEAR) record(Tactic.PEARL_IN, side, 0, now, p);
		}
		// T5 high ground: 3 or more blocks above for 60 ticks
		if (p.getY() - boss.getY() >= HIGH_GROUND_BLOCKS) {
			if (tr.highGroundSince < 0) tr.highGroundSince = now;
			if (now - tr.highGroundSince >= HIGH_GROUND_TICKS && !highGroundFlag) {
				highGroundFlag = true;
				record(Tactic.HIGH_GROUND, side, 0, now, p);
			}
		} else {
			tr.highGroundSince = -1;
			highGroundFlag = false;
		}
		// T6 shield turtle: shield up for 60 percent of the last 200 ticks
		if (tr.shieldFraction() >= TURTLE_FRACTION && now - lastDetect.getOrDefault(Tactic.SHIELD_TURTLE, -100000L) > TURTLE_TICKS) {
			lastDetect.put(Tactic.SHIELD_TURTLE, now);
			record(Tactic.SHIELD_TURTLE, side, 0, now, p);
		}
		// T7 heal run: started a heal item or potion 6 or more blocks away
		if (tr.healStartTick > lastHealSeen) {
			lastHealSeen = tr.healStartTick;
			if (dist >= HEAL_RETREAT) record(Tactic.HEAL_RUN, side, 0, now, p);
		}
	}

	/** Records an event and, if it is the third of the same tactic inside the window, queues its counter. Public so a faction can feed its own events. */
	public void record(Tactic t, Side side, int count, long now, Player p) {
		if (done || now < blockedUntil.getOrDefault(t, 0L)) return; // a blocked tactic is not counted
		ring.addLast(new Event(t, side, count, now));
		while (ring.size() > RING) ring.pollFirst();
		if (!readDone(now)) return;
		int same = 0;
		for (Event e : ring) if (e.tactic() == t && e.side() == side && e.count() == count && now - e.tick() <= WINDOW) same++;
		if (same < 3) return;
		// the counter fires once; the tactic resets and is blocked for 400 ticks
		ring.removeIf(e -> e.tactic() == t);
		blockedUntil.put(t, now + COUNTER_COOLDOWN);
		pending.addLast(new Counter(t, p.getUUID(), side, now));
		while (pending.size() > MAX_PENDING) pending.pollFirst();
		if (++countersFired >= MAX_COUNTERS) done = true;
	}
}
