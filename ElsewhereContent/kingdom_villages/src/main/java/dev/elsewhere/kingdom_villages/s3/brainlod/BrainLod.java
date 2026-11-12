package dev.elsewhere.kingdom_villages.s3.brainlod;

import dev.elsewhere.kingdom_villages.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/**
 * Villager brain LOD and stagger (3.0 siege performance, s-siegeperf). Profiling the full siege bench showed vanilla
 * villager brains at about 42% of the server tick (soldier AI about 10%): almost all of it in
 * {@code Brain.startEachNonRunningBehavior}, the per-tick "may a new behaviour start?" pass, where
 * {@code MoveToTargetSink} and {@code AcquirePoi} run path searches to beds, job sites and walk targets that the
 * siege burned or blocked.
 *
 * <p>Only that start pass is spread out. Memory expiry, sensors and every running behaviour (walking, working,
 * sleeping, trading, breeding, panicking) still tick every tick, exactly as vanilla, from {@code Brain.tick}. A villager
 * runs the start pass:
 * <ul>
 * <li>every tick when it was hurt in the last 10 ticks, is trading, or its brain is in PANIC, RAID, PRE_RAID or HIDE
 * (so a hit, a raid bell or a zombie never waits);</li>
 * <li>captured (its village is besieged or occupied, so it can't trade anyway): every 2nd tick within {@link #NEAR}
 * blocks of a player, every 4th tick farther out;</li>
 * <li>free villagers: every tick within {@link #MID} (vanilla), every 2nd tick within {@link #FAR}, every 4th beyond (or
 * with no player in the level).</li>
 * </ul>
 * The phase is the villager's entity id, so on any tick only a share of the villagers evaluate new behaviours (the
 * path-search spikes no longer pile onto the same ticks). The tier is re-read every 20 ticks from the player list (no
 * entity scans).
 *
 * <p>Kill switch (BUILD-3.0): JVM flag {@code elsewhere.overhaul3.villager_brain_lod}, default on; off = vanilla.
 */
public final class BrainLod {
	private BrainLod() {}

	public static final boolean ON = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.villager_brain_lod", "true"));
	/** Tests can switch it off for a same-world A/B comparison. */
	public static boolean enabled = ON;
	public static final double NEAR = 16, MID = 48, FAR = 96;
	/** How long a computed tier is kept, in ticks. */
	public static final int RETIER = 20;
	/** Counters for the self-test and the bench report. */
	public static long startsRun, startsSkipped;

	/**
	 * Walk path searches (villager {@code MoveToTargetSink} start checks). A search whose result is futile (no path, or a
	 * partial path that ends where the villager already stands: a burned or walled-off bed, job site or stroll spot) is
	 * not repeated from the same block to the same target for {@link #FUTILE_MEMO} ticks: vanilla would search again
	 * every pass and walk nowhere; the give-up timer (CANT_REACH_WALK_TARGET_SINCE) is game time and keeps running, so the
	 * villager forgets an unreachable POI exactly when vanilla would. At most {@link #WALK_SEARCHES_PER_TICK} villager walk
	 * searches start per server tick; a villager over the budget keeps its walk target and searches on a later pass.
	 */
	public static final int FUTILE_MEMO = 40, WALK_SEARCHES_PER_TICK = 3;
	public static long walkSearches, walkSearchesSaved, walkSearchesDeferred;
	public static int maxWalkSearchesInTick;
	private static long budgetTick = Long.MIN_VALUE;
	private static int budgetUsed;

	/** One villager walk search this tick, or false when the per-tick budget is used up (try again on a later pass). */
	public static boolean tryWalkSearch(long gameTime) {
		roll(gameTime);
		if (budgetUsed >= WALK_SEARCHES_PER_TICK || searchesUsed >= SEARCHES_PER_TICK) {
			walkSearchesDeferred++;
			return false;
		}
		budgetUsed++;
		searchesUsed++;
		walkSearches++;
		if (budgetUsed > maxWalkSearchesInTick) maxWalkSearchesInTick = budgetUsed;
		return true;
	}

	/**
	 * fx-siegeperf: villager POI searches (AcquirePoi) per server tick, counted only inside a villager's brain start pass
	 * ({@link #startPassTick} is that pass's game time, MIN_VALUE outside it). Over the budget the search waits for the
	 * villager's next start pass, its own schedule untouched.
	 */
	public static final int POI_SEARCHES_PER_TICK = 3;
	public static long poiSearches, poiSearchesDeferred;
	public static int maxPoiSearchesInTick;
	public static long startPassTick = Long.MIN_VALUE;
	private static int poiUsed;

	/** May a villager POI search start now? True outside a villager start pass (no budget there). */
	public static boolean mayPoiSearch() {
		if (!enabled || startPassTick == Long.MIN_VALUE) return true;
		if (shelteredNow) {
			poiSuspended++;
			return false; // siege shelter: no POI acquisition until the siege ends
		}
		roll(startPassTick);
		if (poiUsed >= POI_SEARCHES_PER_TICK || searchesUsed >= SEARCHES_PER_TICK) {
			poiSearchesDeferred++;
			return false;
		}
		return true;
	}

	/** A villager POI search ran (counts against this tick's budget). */
	public static void poiSearched() {
		if (startPassTick == Long.MIN_VALUE) return;
		roll(startPassTick);
		poiUsed++;
		searchesUsed++;
		poiSearches++;
		if (poiUsed > maxPoiSearchesInTick) maxPoiSearchesInTick = poiUsed;
	}

	/** Self-test: a fresh walk-search budget for this tick. */
	public static void resetWalkBudget() {
		budgetTick = Long.MIN_VALUE;
		budgetUsed = 0;
		poiUsed = 0;
		searchesUsed = 0;
	}

	/**
	 * fx-siegeperf: walk and POI path searches share one per-tick cap ({@link #SEARCHES_PER_TICK}) on top of their own caps,
	 * so one tick never stacks both bursts (3 + 3 path searches were the siege bench's slowest ticks). Bench demand is about
	 * 2.3 searches a tick, so a deferred search runs on the villager's next start pass.
	 */
	public static final int SEARCHES_PER_TICK = Integer.MAX_VALUE; // fx-siegeperf: shared cap measured no gain (8.59 ms vs 7.75 without); off, own caps only
	private static int searchesUsed;

	private static void roll(long tick) {
		if (tick != budgetTick) {
			budgetTick = tick;
			budgetUsed = 0;
			poiUsed = 0;
			searchesUsed = 0;
		}
	}

	/** Start-pass interval for a villager at squared distance {@code d2} from the nearest player (MAX_VALUE: none). */
	public static int interval(double d2, boolean captured) {
		if (captured) return d2 <= NEAR * NEAR ? 2 : 4;
		if (d2 <= MID * MID) return 1;
		return d2 <= FAR * FAR ? 2 : 4;
	}

	/** Never delay a reaction: hurt, trading, panicking, raid and hide states run the start pass every tick. */
	public static boolean urgent(Villager v, Brain<?> brain) {
		return urgent(v, brain, false);
	}

	/** As {@link #urgent(Villager, Brain)}; a siege-sheltered villager's own (siege) hide is not an urgent state, a hit or panic still is. */
	public static boolean urgent(Villager v, Brain<?> brain, boolean sheltered) {
		return v.hurtTime > 0 || v.isTrading() || brain.isActive(Activity.PANIC) || brain.isActive(Activity.RAID)
			|| brain.isActive(Activity.PRE_RAID) || !sheltered && brain.isActive(Activity.HIDE);
	}

	/**
	 * fx-siegeperf, siege shelter (gameplay, lead-approved 16:02): while its village is under siege a villager takes shelter
	 * the vanilla raid-bell way. It hears a bell (HEARD_BELL_TIME, refreshed every {@link #SHELTER_BELL_EVERY} ticks from its
	 * own brain tick, no entity scans), so vanilla ReactToBell puts it in HIDE: it goes to its home or hiding place once and
	 * stays, with no strolls. Its POI acquisition (job site, bed, meeting point) is suspended until the siege ends; within
	 * {@link #RETIER} ticks of the end both lift (the last bell times out the vanilla way). Kill switch: JVM flag
	 * {@code elsewhere.overhaul3.siege_shelter}, default on.
	 */
	public static final boolean SHELTER = !"false".equalsIgnoreCase(System.getProperty("elsewhere.overhaul3.siege_shelter", "true"));
	public static final int SHELTER_BELL_EVERY = 200;
	/** True while a siege-sheltered villager's start pass runs. */
	public static boolean shelteredNow;
	public static long poiSuspended, shelterBells;

	/** Squared distance to the nearest non-spectator player in the level (or self-test observer); MAX_VALUE if none. */
	public static double nearestObserver2(ServerLevel level, Vec3 pos) {
		double best = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) if (!p.isSpectator()) best = Math.min(best, p.position().distanceToSqr(pos));
		for (Vec3 o : VillageManager.testObservers) best = Math.min(best, o.distanceToSqr(pos));
		return best;
	}

	/** Recomputes the interval for a villager (called every {@link #RETIER} ticks per villager). */
	public static int computeInterval(ServerLevel level, Villager v) {
		return interval(nearestObserver2(level, v.position()), VillageManager.isCaptured(level, v));
	}

	/** True when this villager's start pass runs on this tick (its phase), given its cached interval. */
	public static boolean onPhase(Villager v, int every) {
		return every <= 1 || Math.floorMod(v.tickCount + v.getId(), every) == 0;
	}
}
