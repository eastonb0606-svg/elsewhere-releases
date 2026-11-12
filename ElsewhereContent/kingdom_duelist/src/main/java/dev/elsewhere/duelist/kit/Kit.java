package dev.elsewhere.duelist.kit;

/**
 * Module-wide rails only: the telegraph minimums, the highest damage caps a boss may choose and the duel right. These are safety rules
 * (BOSS-BALANCE-2.0, RIVALS 4.3), not fighting numbers. Every fighting number lives in the boss's own profile
 * ({@code Behaviours}); a boss may also set its own caps with {@code DuelistProfile.Builder#caps}.
 */
public final class Kit {
	private Kit() {}

	/** Telegraph rule: attacks of raw 16 or more need a wind-up of {@link #WINDUP_NORMAL}; signature moves {@link #WINDUP_SIGNATURE}; area and heavy moves {@link #WINDUP_HEAVY}. */
	public static final float TELEGRAPH_RAW = 16F;
	public static final int WINDUP_NORMAL = 8, WINDUP_SIGNATURE = 14, WINDUP_HEAVY = 20;

	/** The highest caps (raw, before the victim's armor) a boss may choose: there is no default, every profile calls {@code caps(minor, signature)} and {@code build()} rejects anything above these. */
	public static final float CAP_MINOR_MAX = 18F, CAP_SIGNATURE_MAX_RULER = 40F, CAP_SIGNATURE_MAX_LORD = 38F;

	/** Armour-ignoring magic (splash, patches) is converted from raw at this factor (raw x0.20 is roughly netherite + Prot IV). */
	public static final float MAGIC_RAW_FACTOR = 0.21F;

	/** Duel right (RIVALS 4.3): a boss in duel mode yields at this fraction of its HP. */
	public static final float YIELD_HP = 0.10F;
}
