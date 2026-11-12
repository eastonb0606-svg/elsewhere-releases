package dev.elsewhere.greenhaze;

/**
 * Every Greenhaze gear and drop number in one place, so BALANCE.md values plug straight in.
 * Values are the design docs' where they give one; the rest are placeholders marked TBD (CONTRACT.md "Design ownership":
 * build agents do not tune gear; floor = diamond level + one perk for the Greenhaze).
 */
public final class GearStats {
	private GearStats() {}

	// Trichome tools (DIMENSIONS.md 7: "mobs you hit are Slowed"). Diamond-level per the revised floor. TBD.
	public static final int TRICHOME_DURABILITY = 1561;
	public static final float TRICHOME_SPEED = 8.0F;
	public static final float TRICHOME_DAMAGE_BONUS = 3.0F;
	public static final int TRICHOME_ENCHANT = 14; // BALANCE.md final
	public static final int TRICHOME_SLOW_TICKS = 40;       // TBD
	public static final int TRICHOME_SLOW_AMPLIFIER = 0;    // Slowness I, TBD

	// Frond armor (leaf-shingle set; Frond Cloak is the chestpiece). Diamond-level defense, TBD.
	public static final int FROND_DURABILITY = 33;
	public static final int[] FROND_DEFENSE = {3, 6, 8, 3}; // boots, legs, chest, helmet
	public static final float FROND_TOUGHNESS = 2.0F;
	public static final int FROND_ENCHANT = 14; // BALANCE.md final
	/** BALANCE.md final: per Frond piece, mobs' follow range -12.5% while you stand in ferns, leaves or tall grass. */
	public static final double FROND_CAMO_PER_PIECE = 0.125;

	// Hotbox Visor (Percolax): helmet, diamond-level (doc). Immune to Blindness and contact highs, sees through smoke.
	public static final int VISOR_DEFENSE = 3;
	public static final float VISOR_TOUGHNESS = 2.0F;

	// Couchlock Club (Grumblegut): weapon, diamond-level (doc). Slowness II 2 s on hit; sneak-attack 3-block shockwave.
	public static final float CLUB_DAMAGE = 9.0F;         // BALANCE.md final: 9 / 1.0
	public static final float CLUB_SPEED = 1.0F;
	public static final int CLUB_SLOW_TICKS = 40, CLUB_SLOW_AMPLIFIER = 1;
	public static final double CLUB_SHOCKWAVE_RADIUS = 3.0;
	public static final float CLUB_SHOCKWAVE_DAMAGE = 4.0F; // TBD
	public static final int CLUB_SHOCKWAVE_COOLDOWN = 160;  // BALANCE.md final: 8 s

	// Everflow Bong (Percolax): no water, never breaks; rips still burn buds.
	public static final double BONG_RIP_HIGH = 22.0;        // Green Leaf bong rip size
	public static final double BONG_HAZEBORN_FACTOR = 1.5;  // Hazeborn: highs last +50% (approximated as a bigger dose)
	public static final int BONG_COOLDOWN = 40;

	// Cloud Jar (Percolax): thrown smoke screen; allies inside get Invisibility 5 s; 30 s cooldown (doc).
	public static final int CLOUD_JAR_INVIS_TICKS = 100;
	public static final float CLOUD_JAR_GLIMMER = 8F;     // ARSENAL.md: 8 Glimmer, 2.0 s use delay (was a 30 s cooldown)
	public static final int CLOUD_JAR_DELAY_TICKS = 40;
	/** ARSENAL.md: gorger_fluff (summoner module's item) drops from Lazy Gorgers 50%. */
	public static final float GORGER_FLUFF_CHANCE = 0.5F;
	public static final float CLOUD_JAR_RADIUS = 3.5F;

	// Endless Snack Pouch (Grumblegut): restores 6 hunger, refills itself, 60 s cooldown (doc).
	public static final int POUCH_FOOD = 6;
	public static final float POUCH_SATURATION = 2F / (2 * 6); // BALANCE.md final: 6 hunger + 2 saturation
	public static final float POUCH_COOLDOWN_S = 90F; // BALANCE.md final

	// Hotbox Lantern: 3-block smoke bubble (doc).
	public static final double LANTERN_RADIUS = 3.0;

	// Food and drinks (doc).
	public static final int BACON_FOOD = 10;
	public static final float BACON_SATURATION_MOD = 0.6F;  // 10 * 0.6 * 2 = 12 saturation
	public static final int MUNCHFRUIT_FOOD = 3;
	public static final float MUNCHFRUIT_SATURATION_MOD = 0.3F;
	public static final double LUCID_WATER_HAZE = -40.0;
	public static final int LUCID_WATER_NIGHT_VISION = 600;

	// Haze meter (doc): +1% per 20 s, capped at High (60%) unless you smoke.
	public static final double HAZE_PASSIVE_PER_SECOND = 1.0 / 20.0;
	public static final double HAZE_PASSIVE_CAP = 60.0;
	public static final double HAZE_VENT_PER_SECOND = 0.5;
	public static final double HAZE_LILY_PER_SECOND = -1.0;
	public static final double HAZE_SMOG_ENGULF = 4.0;
}
