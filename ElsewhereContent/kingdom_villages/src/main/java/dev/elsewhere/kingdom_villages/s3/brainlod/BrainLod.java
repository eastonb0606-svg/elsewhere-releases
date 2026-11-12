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

	/** Start-pass interval for a villager at squared distance {@code d2} from the nearest player (MAX_VALUE: none). */
	public static int interval(double d2, boolean captured) {
		if (captured) return d2 <= NEAR * NEAR ? 2 : 4;
		if (d2 <= MID * MID) return 1;
		return d2 <= FAR * FAR ? 2 : 4;
	}

	/** Never delay a reaction: hurt, trading, panicking, raid and hide states run the start pass every tick. */
	public static boolean urgent(Villager v, Brain<?> brain) {
		return v.hurtTime > 0 || v.isTrading() || brain.isActive(Activity.PANIC) || brain.isActive(Activity.RAID)
			|| brain.isActive(Activity.PRE_RAID) || brain.isActive(Activity.HIDE);
	}

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
