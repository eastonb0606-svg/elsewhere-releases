package dev.elsewhere.duelist.adapt;

/** The 7 tactics the ledger counts (B2, T1 to T7). */
public enum Tactic {
	JUMP_CRIT_OPENER, COMBO, RANGED_POKE, PEARL_IN, HIGH_GROUND, SHIELD_TURTLE, HEAL_RUN;

	/** "T1".."T7". */
	public String code() {
		return "T" + (ordinal() + 1);
	}
}
