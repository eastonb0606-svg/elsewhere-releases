package dev.elsewhere.duelist.boss;

/**
 * Damage scaling by HP lost (X13). Multiplier rises from 1.0 at full HP to {@code 1 + maxBonus} at 0 HP. With
 * {@code permanentBelow} the rage stays once reached (Winter Rage). The flags {@code noRetreat} and {@code noHeal}
 * tell HumanBoss to switch Eat and Drink off (and the faction lane to skip fleeing).
 */
public final class FrenzyRage {
	public final double maxBonus, permanentBelow;
	public final boolean noRetreat, noHeal;
	private boolean locked;
	private double best = 1.0;

	public FrenzyRage(double maxBonus, double permanentBelow, boolean noRetreat, boolean noHeal) {
		this.maxBonus = maxBonus;
		this.permanentBelow = permanentBelow;
		this.noRetreat = noRetreat;
		this.noHeal = noHeal;
	}

	public double multiplier(double hpFrac) {
		if (hpFrac <= permanentBelow) locked = true;
		double m = 1.0 + maxBonus * (1.0 - hpFrac);
		if (locked) {
			best = Math.max(best, m);
			return best; // permanent: never falls back below the level reached
		}
		return m;
	}

	public boolean locked() {
		return locked;
	}
}
