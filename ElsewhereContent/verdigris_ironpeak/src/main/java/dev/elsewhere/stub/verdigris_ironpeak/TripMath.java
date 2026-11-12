package dev.elsewhere.stub.verdigris_ironpeak;

import java.util.Map;

/**
 * STUB of the core's trip damping rules (CONTRACT.md "User decisions" rule 1), as pure math so the self-test can
 * simulate hundreds of hours. The live service ({@link Trips}) feeds it the player's state.
 */
public final class TripMath {
	public static final double MIN_PLAYTIME_H = 1.0, COOLDOWN_H = 2.0, PITY_AFTER_H = 8.0, HOME_RADIUS = 128, FAMILIARITY_HALF_H = 0.5, FAMILIARITY_FLOOR = 0.03;
	public static final int REGION = 512;
	/** Hourly budgets for ACTIVITY triggers (events that may roll per hour of playtime). */
	public static final Map<String, Integer> BUDGET = Map.of("petalwind.long_fall", 3, "petalwind.wind_launch", 4, "greenhaze.green_leaf_use", 12, "the_hush.warden", 2);

	private TripMath() {}

	public static double familiarity(double hoursInRegion, boolean nearHome) {
		if (nearHome) return FAMILIARITY_FLOOR;
		return Math.max(FAMILIARITY_FLOOR, Math.pow(0.5, hoursInRegion / FAMILIARITY_HALF_H));
	}

	/**
	 * Final chance for one trigger event.
	 * @param hoursSinceTrip playtime hours since the last accidental trip (infinite if none)
	 * @param eventsThisHour how many events of this trigger already rolled this playtime hour (ACTIVITY only)
	 */
	public static double chance(TripTrigger.Kind kind, String triggerId, double base, double scale, double playtimeH, double hoursSinceTrip,
		double hoursInRegion, boolean nearHome, int eventsThisHour, boolean blocked) {
		if (blocked || playtimeH < MIN_PLAYTIME_H || hoursSinceTrip < COOLDOWN_H) return 0;
		double c = base * scale;
		if (kind == TripTrigger.Kind.LOCATION) c *= familiarity(hoursInRegion, nearHome);
		else if (eventsThisHour >= BUDGET.getOrDefault(triggerId, 6)) return 0;
		if (Math.min(hoursSinceTrip, playtimeH) >= PITY_AFTER_H && !nearHome) c *= 2;
		return Math.min(1, c);
	}
}
