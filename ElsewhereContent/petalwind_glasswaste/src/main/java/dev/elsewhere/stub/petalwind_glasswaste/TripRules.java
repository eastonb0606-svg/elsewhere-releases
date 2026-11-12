package dev.elsewhere.stub.petalwind_glasswaste;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * STUB: the accidental-trip damping rules from CONTRACT.md "User decisions (2026-11-10)", as a pure per-player state
 * object so the self-test can simulate hundreds of hours. All times are in seconds of playtime.
 */
public final class TripRules {
	public static final double MIN_PLAYTIME = 60 * 60;          // no trips in the first 60 minutes
	public static final double COOLDOWN = 2 * 60 * 60;          // 2 h after any trip
	public static final double PITY_AFTER = 8 * 60 * 60;        // x2 after 8 h without a trip (never at home)
	public static final double HOME_RADIUS = 128;
	public static final double FAMILIARITY_FLOOR = 0.03;
	public static final double FAMILIARITY_HALF_LIFE_HOURS = 0.5;
	public static final Map<String, Integer> HOURLY_BUDGET = Map.of(
		"petalwind.long_fall", 3, "petalwind.wind_launch", 4, "greenhaze.green_leaf_use", 12, "the_hush.warden", 2);

	public double playtime;
	public double lastTrip = Double.NEGATIVE_INFINITY;
	public final Map<String, Double> regionHours = new HashMap<>();
	private final Map<String, Deque<Double>> activityEvents = new HashMap<>();
	public double scale = 1.0;

	public static String regionKey(String dimension, double x, double z) {
		return dimension + "@" + Math.floorDiv((long) Math.floor(x), 512) + "," + Math.floorDiv((long) Math.floor(z), 512);
	}

	/** Advance playtime while standing in a region. */
	public void tick(double seconds, String region) {
		playtime += seconds;
		regionHours.merge(region, seconds / 3600.0, Double::sum);
	}

	public double familiarity(String region, boolean atHome) {
		if (atHome) return FAMILIARITY_FLOOR;
		double h = regionHours.getOrDefault(region, 0.0);
		return Math.max(FAMILIARITY_FLOOR, Math.pow(0.5, h / FAMILIARITY_HALF_LIFE_HOURS));
	}

	/**
	 * Final chance for one event, or 0 when a global rule blocks it. ACTIVITY events count against the hourly budget
	 * whether or not they then succeed (only the first N per hour roll at all).
	 */
	public double chance(String triggerId, TripTrigger.Kind kind, double base, String region, boolean atHome) {
		if (playtime < MIN_PLAYTIME) return 0;
		if (playtime - lastTrip < COOLDOWN) return 0;
		double c = base * scale;
		if (kind == TripTrigger.Kind.LOCATION) {
			c *= familiarity(region, atHome);
		} else {
			Deque<Double> events = activityEvents.computeIfAbsent(triggerId, k -> new ArrayDeque<>());
			while (!events.isEmpty() && playtime - events.peekFirst() > 3600) events.pollFirst();
			int budget = HOURLY_BUDGET.getOrDefault(triggerId, Integer.MAX_VALUE);
			if (events.size() >= budget) return 0;
			events.addLast(playtime);
		}
		double sinceTrip = playtime - Math.max(lastTrip, 0);
		if (sinceTrip >= PITY_AFTER && !atHome) c *= 2;
		return Math.min(1.0, c);
	}

	public void recordTrip() {
		lastTrip = playtime;
	}
}
