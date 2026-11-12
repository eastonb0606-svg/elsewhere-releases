package dev.elsewhere.palehollow;

/**
 * Every Pale Hollow gear, drop and boss number in one place (CONTRACT.md "Design ownership").
 * Values are the DIMENSIONS.md / BOSSES.md doc values until ROOT/ElsewhereContent/BALANCE.md is marked final.
 */
public final class GearStats {
	private GearStats() {
	}

	// ---- items
	public static final int PALE_SAP_NUTRITION = 3;
	public static final float PALE_SAP_SATURATION = 2.0F;
	public static final int PALE_SAP_NIGHT_VISION_SECONDS = 90;
	public static final int PALE_SAP_SLOWNESS_SECONDS = 10;
	public static final int UNBLINKING_EYE_DURABILITY = 16;
	public static final float UNBLINKING_EYE_COOLDOWN_SECONDS = 30.0F;
	public static final double UNBLINKING_EYE_RADIUS = 24.0;
	public static final int UNBLINKING_EYE_FREEZE_TICKS = 8 * 20;
	/** Bosses that move only when unwatched are frozen for less (doc gives no number; see QUESTIONS.md). */
	public static final int UNBLINKING_EYE_BOSS_FREEZE_TICKS = 2 * 20;
	public static final float HEARTWOOD_SHARD_DROP_CHANCE = 0.35F;

	// ---- Mother Creak drops
	public static final int LOCKET_REGENERATION_SECONDS = 10;
	public static final int LOCKET_REGENERATION_LEVEL = 2;
	public static final float LOCKET_ROOT_RADIUS = 4.0F;
	public static final int LOCKET_ROOT_TICKS = 3 * 20;
	public static final int GREATBOW_DURABILITY = 768;
	/** Arrows root targets: Slowness III for 1 s, at most once per 4 s per target (BALANCE.md M-core). */
	public static final int GREATBOW_ROOT_TICKS = 20;
	public static final int GREATBOW_ROOT_AMPLIFIER = 2;
	public static final int GREATBOW_ROOT_COOLDOWN_TICKS = 80;
	/** G5: player crowd control on bosses is capped at Slowness I for 1 s, once per 10 s per boss. */
	public static final int BOSS_CC_TICKS = 20;
	public static final int BOSS_CC_COOLDOWN_TICKS = 200;
	public static final int PALE_BARK_DROP = 10;

	// ---- Selenne drops
	/** Doc says "helmet" with no stats; contract floor is diamond level (3 armor, 2 toughness). Placeholder pending BALANCE.md. */
	public static final double CIRCLET_ARMOR = 3.0;
	public static final double CIRCLET_TOUGHNESS = 2.0;
	public static final float MOON_EYE_COOLDOWN_SECONDS = 1.0F;
	/** ARSENAL.md: the Moon-Eye costs 3 Glimmer with a 1.0 s use delay (was a 20 s cooldown). */
	public static final int MOON_EYE_GLIMMER = 3;
	public static final int MOON_EYE_GLOW_TICKS = 10 * 20;
	public static final double MOON_EYE_RANGE = 32.0;

	// ---- mobs (DIMENSIONS.md)
	public static final double HUSK_HEALTH = 40.0;
	public static final double HUSK_DAMAGE = 5.0;
	public static final double HUSK_ARMOR = 4.0;
	public static final double WISP_HEALTH = 8.0;
	public static final double WISP_DAMAGE = 3.0;

	// ---- bosses (BOSSES.md difficulty ladder)
	public static final double MOTHER_CREAK_HEALTH = 360.0;
	public static final double MOTHER_CREAK_ARMOR = 8.0;
	public static final double MOTHER_CREAK_TOUGHNESS = 2.0;
	public static final float MOTHER_CREAK_ROOT_BURST = 38.0F;
	public static final float MOTHER_CREAK_CLAW = 30.0F;
	public static final int MOTHER_CREAK_STUN_TICKS = 6 * 20;
	public static final int MOTHER_CREAK_ROOT_BURST_INTERVAL = 7 * 20;

	public static final double SELENNE_HEALTH = 460.0;
	public static final double SELENNE_ARMOR = 10.0;
	public static final double SELENNE_TOUGHNESS = 4.0;
	public static final float SELENNE_MOONBEAM = 44.0F;
	public static final float SELENNE_GAZE_PER_SECOND = 2.0F;
	public static final float SELENNE_FULL_MOON = 44.0F;
	public static final int SELENNE_BEAM_INTERVAL = 9 * 20;

	// ---- 0.4.0 (ARSENAL.md §2 / §5 / BALANCE G7), applied by id in ArsenalLinks
	/** Heartwood Greatbow: elsewhere:projectile_pierce +1 (main hand). */
	public static final double GREATBOW_PIERCE = 1.0;
	/** Echo-Forged Blade (greenhaze_hush item): elsewhere:kill_heal +2 (main hand); never inside boss arenas. */
	public static final double ECHO_FORGED_KILL_HEAL = 2.0;
	/** Boss uniques (Elder Bark Crown from Mother Creak, Rootcall Staff from Selenne): first kill / repeat chance per participant. */
	public static final float UNIQUE_FIRST_KILL = 0.5F;
	public static final float UNIQUE_REPEAT = 0.2F;
}
