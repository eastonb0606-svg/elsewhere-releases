package dev.elsewhere.core.trip;

import dev.elsewhere.ElsewhereConfig;

/**
 * The pure damping maths (no game state), shared by the live framework and the self-test simulation.
 * See CONTRACT.md "User decisions (2026-11-10)", rule 1.
 */
public final class TripRules {
	public static final long TICKS_PER_HOUR = 72000L;

	private TripRules() {
	}

	/** Everything about the player's situation the rules need. */
	public record Situation(long playTicks, long lastTripPlayTicks, double regionHours, boolean inHome, boolean budgetOk,
		boolean blocked) {
	}

	/** Familiarity multiplier for a LOCATION trigger: max(floor, 0.5^(hours / halfLife)), always the floor at home. */
	public static double familiarity(final double regionHours, final boolean inHome, final ElsewhereConfig.AccidentalTrips cfg) {
		if (inHome) {
			return cfg.familiarityFloor;
		}
		return Math.max(cfg.familiarityFloor, Math.pow(0.5, regionHours / cfg.familiarityHalfLifeHours));
	}

	public static boolean onCooldown(final long playTicks, final long lastTripPlayTicks, final ElsewhereConfig.AccidentalTrips cfg) {
		return lastTripPlayTicks >= 0 && playTicks - lastTripPlayTicks < cfg.cooldownHours * TICKS_PER_HOUR;
	}

	public static boolean pity(final long playTicks, final long lastTripPlayTicks, final boolean inHome, final ElsewhereConfig.AccidentalTrips cfg) {
		long since = playTicks - Math.max(0L, lastTripPlayTicks);
		return !inHome && since >= cfg.pityHours * TICKS_PER_HOUR;
	}

	/** Final chance of one trigger event turning into a trip. */
	public static double effectiveChance(final double base, final TripTrigger.Kind kind, final Situation s, final ElsewhereConfig.AccidentalTrips cfg) {
		if (!cfg.enabled || s.blocked() || base <= 0.0) {
			return 0.0;
		}
		if (s.playTicks() < cfg.minPlaytimeMinutes * 1200.0) {
			return 0.0;
		}
		if (onCooldown(s.playTicks(), s.lastTripPlayTicks(), cfg)) {
			return 0.0;
		}
		double chance = base * cfg.scale;
		if (kind == TripTrigger.Kind.ACTIVITY && !s.budgetOk()) {
			return 0.0;
		}
		if (kind == TripTrigger.Kind.LOCATION || cfg.dampActivityTriggers) {
			chance *= familiarity(s.regionHours(), s.inHome(), cfg);
		}
		if (pity(s.playTicks(), s.lastTripPlayTicks(), s.inHome(), cfg)) {
			chance *= cfg.pityMultiplier;
		}
		return Math.max(0.0, Math.min(1.0, chance));
	}
}
