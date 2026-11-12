package dev.elsewhere.hush;

/**
 * Every Hush gear and drop number in one place, so BALANCE.md values plug straight in.
 * Values are the design docs' where they give one; the rest are placeholders marked TBD (CONTRACT.md "Design ownership").
 */
public final class GearStats {
	private GearStats() {}

	// Echo tools (DIMENSIONS.md 6: silent mining that raises no Resonance). Netherite-level placeholder, TBD.
	public static final int ECHO_DURABILITY = 1600; // BALANCE.md final
	public static final float ECHO_SPEED = 7.0F;      // N3: pickaxe speed 7 above Y 0 outside the Hush ...
	public static final double ECHO_DEEP_BONUS = 2.0; // ... and 9 in the Hush and below Y 0 (a transient mining-efficiency bonus)
	public static final float ECHO_DAMAGE_BONUS = 4.0F;
	public static final int ECHO_ENCHANT = 12;

	// Hushweave armor: Hushcloak (doc: armor 6, sneaking_speed +0.3, noise x0.5) + Hushstep Greaves (Auris, netherite leggings:
	// sneaking makes no vibrations or Resonance) + hood and boots (TBD). Full set: noise x0.25 (doc).
	public static final int HUSHWEAVE_DURABILITY = 33; // BALANCE.md final: 3/7/5/3 = 18, toughness 2.5 each, no KB
	public static final int[] HUSHWEAVE_DEFENSE = {3, 5, 7, 3}; // boots, legs, chest, helmet
	public static final float HUSHWEAVE_TOUGHNESS = 2.5F; // BALANCE.md final
	public static final int HUSHWEAVE_ENCHANT = 15;
	public static final double GREAVES_SNEAK_SPEED = 0.3; // N1: the legs carry the sneaking speed (the cloak carries the noise cut)
	public static final double HUSHCLOAK_NOISE = 0.5;
	public static final double HUSHWEAVE_SET_NOISE = 0.25;
	public static final int GREAVES_DEFENSE = 6;                 // netherite leggings
	public static final float GREAVES_TOUGHNESS = 3.0F; // BALANCE.md final 6 / 3 / 0

	// Choir Vestment (Lithara): netherite chestplate, armor 9, toughness 4; amethyst chimes on hits heal 1 heart, 5 s ICD (doc).
	public static final int VESTMENT_DEFENSE = 8; // BALANCE.md final 8 / 2 / 0
	public static final float VESTMENT_TOUGHNESS = 2.0F;
	public static final float VESTMENT_HEAL = 2.0F;
	public static final double VESTMENT_RADIUS = 8, VESTMENT_NOISE = 1.5; // allies within 8; the wearer's noise x1.5 (Resonance gain x1.5)
	public static final float VESTMENT_ABSORB = 2F;
	public static final int VESTMENT_TICKS = 120;
	public static final int VESTMENT_COOLDOWN = 240; // 12 s

	// Sonar Horn: all mobs within 48 glow 8 s, +40% Resonance, 60 s cooldown (doc).
	public static final double SONAR_RADIUS = 48;
	public static final int SONAR_GLOW_TICKS = 160;
	public static final float SONAR_RESONANCE = 40F;
	public static final float SONAR_COOLDOWN_S = 60F;

	// Sculk Censer: max_damage 600, absorbs vibrations within 4 blocks like wool, durability per event (doc).
	public static final int CENSER_DURABILITY = 600;

	// Silence Draught: Invisibility 60 s + Muted 30 s + Darkness 10 s (doc).
	public static final int DRAUGHT_INVIS = 1200, DRAUGHT_MUTED = 600, DRAUGHT_DARKNESS = 200;

	// Tuning Fork of Auris: thrown decoy, use_cooldown 5 s (doc).
	public static final float FORK_GLIMMER = 3F;          // ARSENAL.md: 3 Glimmer, 1.0 s use delay (was a 5 s cooldown)
	public static final int FORK_DELAY_TICKS = 20;
	/** ARSENAL.md: Cantor's Diadem (arsenal module's item) is a 50% Lithara drop. */
	public static final float DIADEM_CHANCE = 0.5F;
	public static final int FORK_DECOY_TICKS = 100;

	// Resonance meter (doc).
	public static final float RES_SPRINT_PER_S = 1, RES_JUMP = 2, RES_MINE = 3, RES_HIT = 4, RES_EAT = 5, RES_CHEST = 6, RES_COUGH = 25;
	public static final float RES_SNEAK_DECAY_PER_S = 2, RES_WOOL_DECAY_PER_S = 4;
	public static final float RES_STALKERS = 50, RES_ANSWER = 100;
	public static final float RES_SHRIEKER = 8, RES_CHORISTER_KILL = 20;
	/** Green Leaf: chance that a smoke use in the Hush coughs (Green Leaf's own cough chances are 10-35%). */
	public static final double GREEN_LEAF_COUGH_CHANCE = 0.35;
}
