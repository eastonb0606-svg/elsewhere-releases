package dev.elsewhere.lore;

import net.minecraft.util.RandomSource;

/**
 * The royal patrol spawn rate and the math behind the user's target: "a player exploring 500-600+ blocks from spawn has
 * a 50%+ chance to meet a patrol within 5+ hours".
 *
 * <p>Model. A player accrues <i>explore time</i> only while in the Overworld, on or near the surface (sky above or at
 * most 6 blocks under the surface height), not in creative or spectator. Once per explore minute (1,200 ticks) the mod
 * rolls {@code p(d) = P0 * ramp(d) * scale}, where d is the distance from world spawn: ramp is 0 below 400 blocks, rises
 * linearly to 1 at 500 and stays 1 beyond. {@code P0 = 0.0042}. A successful roll spawns a patrol 56-80 blocks ahead
 * of the player (in view range), so a spawn is a meeting; a roll whose placement fails is kept and retried each second,
 * so failed placements do not lower the rate. After a patrol a player has a 20-minute cooldown.
 *
 * <p>Chance of at least one patrol in M explore minutes: {@code 1 - (1 - P0)^M}.
 * <ul>
 * <li>5 h of pure exploring (M = 300): 1 - 0.9958^300 = 71.7 %.</li>
 * <li>5 h of play where only 70 % counts as explore time (night, caves, water; M = 210): 58.7 %. Still above 50 %.</li>
 * <li>10 h of play at 70 % (M = 420): 83 %. Expected rate when exploring: one patrol per ~3.9 h (about 0.25 per hour).</li>
 * </ul>
 * Not overly common: a 1-hour trip gives about 22 %. The self-test checks these numbers and runs a Monte Carlo.
 */
public final class PatrolRate {
	private PatrolRate() {}

	public static final double P0 = 0.0042;
	public static final int ATTEMPT_TICKS = 1200;
	public static final int RAMP_START = 400, RAMP_FULL = 500;
	public static final int COOLDOWN_TICKS = 20 * 60 * 20;

	public static double ramp(double distFromSpawn) {
		if (distFromSpawn <= RAMP_START) return 0;
		if (distFromSpawn >= RAMP_FULL) return 1;
		return (distFromSpawn - RAMP_START) / (RAMP_FULL - RAMP_START);
	}

	/** Chance per explore minute at distance {@code d} from spawn. */
	public static double perMinute(double d) {
		return Math.min(1.0, P0 * ramp(d) * LoreConfig.patrolChanceScale);
	}

	/** Chance of at least one patrol after {@code exploreMinutes} of exploring at distance {@code d}. */
	public static double chanceWithin(double d, double exploreMinutes) {
		return 1.0 - Math.pow(1.0 - perMinute(d), exploreMinutes);
	}

	public static boolean roll(RandomSource r, double d) {
		return r.nextDouble() < perMinute(d);
	}
}
