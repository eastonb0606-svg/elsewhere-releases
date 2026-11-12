package dev.elsewhere.arsenal;

/** Every arsenal number from ARSENAL.md (final, revision 4). Change values here only. */
public final class ArsenalStats {
	private ArsenalStats() {}

	// ---- Glimmer (ARSENAL §1) ----
	public static final double MAX_GLIMMER_BASE = 20, MAX_GLIMMER_MIN = 0, MAX_GLIMMER_MAX = 100;
	public static final float REGEN_PER_SECOND = 3.0F;
	public static final int REGEN_DELAY_TICKS = 20;
	public static final int REGEN_DELAY_MOONLIT_TICKS = 10;
	public static final float EXHAUSTION_PER_GLIMMER = 0.05F;
	public static final int REGEN_MIN_FOOD = 7; // no regen at hunger 6 or less
	public static final int RESERVE_PER_MINION = 4;
	public static final double COST_MULT_MIN = 0.5, COST_MULT_MAX = 2.0;
	public static final float RESPAWN_FRACTION = 0.5F;
	public static final float SYNC_STEP = 1.0F;
	public static final float COST_ROUNDING = 0.1F, COST_MIN = 1.0F; // rev 4: costs round to 0.1, minimum 1

	// ---- Attributes (ARSENAL §2): base, min, max ----
	public static final double[] GLIMMER_REGEN = {1.0, 0, 4}, SPELL_POWER = {1.0, 0, 4}, SPELL_COST = {1.0, 0.5, 2}, PROJECTILE_PIERCE = {0, 0, 5},
		MINION_SLOTS = {1, 0, 4}, MINION_DAMAGE = {1.0, 0, 4}, DODGE_CHANCE = {0, 0, 0.25}, KILL_HEAL = {0, 0, 4};
	public static final double DODGE_CAP = 0.25;
	public static final int KILL_HEAL_INTERVAL_TICKS = 20;
	public static final double BOSS_NEARBY_RADIUS = 64; // stub for "inside a sealed boss arena"

	// ---- Spells (ARSENAL §3) ----
	public static final float MOONLIT_BONUS = 1.5F;
	public static final double MOON_EYE_BONUS = 1.2;

	// ---- Calling (ARSENAL §3b, rev 3): index = pieces worn 0..4 ----
	public static final float[] CALLING_SPELL_DAMAGE = {0.80F, 0.90F, 0.95F, 0.98F, 1.00F};
	public static final float[] CALLING_SPELL_COST = {1.15F, 1.08F, 1.04F, 1.02F, 1.00F};
	public static final float[] CALLING_MINION_DAMAGE = {0.80F, 0.90F, 0.95F, 0.98F, 1.00F};
	public static final int[] CALLING_MINION_RESERVE = {6, 5, 4, 4, 4};
	public static final int STARTER_MIN_CALLING = 2;
	public static final double CASTER_PIECE_MELEE_ARROW = -0.05; // per caster piece (melee: ADD_MULTIPLIED_TOTAL; arrows: x0.85 each)
	public static final float SUMMONER_PIECE_OWN_DAMAGE = 0.80F; // per summoner piece, applied to spells here
	public static final float REGEN_PER_MINION_PENALTY = 0.10F;
	public static final float DAZED_SPELL_DAMAGE = 0.75F;
	public static final int DAZED_TICKS = 200, DAZED_MAX_TICKS = 600;

	// ---- Amethyst Wand ----
	public static final int WAND_COST = 2, WAND_DELAY_TICKS = 8, WAND_DURABILITY = 384;
	public static final float WAND_DAMAGE = 4.0F, WAND_SPEED = 2.5F, WAND_RANGE = 32.0F;

	// ---- Sunlance Staff ----
	public static final int SUNLANCE_COST = 5, SUNLANCE_COST_DAY = 4, SUNLANCE_COST_DARK = 6, SUNLANCE_DELAY_TICKS = 20, SUNLANCE_DURABILITY = 640;
	public static final float SUNLANCE_DAMAGE = dev.elsewhere.arsenal.Overhaul3.REBALANCE_ARSENAL ? 10.0F : 9.0F, SUNLANCE_RANGE = 24.0F;
	public static final int SUNLANCE_TARGETS = 3, SUNLANCE_FIRE_SECONDS = 2;

	// ---- Stormcoil Staff (rev 3) ----
	public static final int STORMCOIL_COST = 7, STORMCOIL_DELAY_TICKS = 16, STORMCOIL_DURABILITY = 640;
	public static final float STORMCOIL_DAMAGE = 8.0F, STORMCOIL_ARC_DAMAGE = dev.elsewhere.arsenal.Overhaul3.REBALANCE_ARSENAL ? 6.0F : 5.0F, STORMCOIL_RANGE = 20.0F, STORMCOIL_ARC_RADIUS = 5.0F;
	public static final int STORMCOIL_ARCS = 2, STORMCOIL_ARCS_WET = 4;
	public static final float STORMCOIL_SELF_ARC_DAMAGE = 2.0F, STORMCOIL_SELF_ARC_RADIUS = 3.0F;

	// ---- Sculk Resonator ----
	public static final int RESONATOR_COST = 10, RESONATOR_CHARGE_TICKS = 25, RESONATOR_DURABILITY = 800;
	public static final float RESONATOR_DAMAGE = 14.0F, RESONATOR_RANGE = 20.0F, RESONATOR_KNOCKBACK = 1.5F;
	public static final double RESONATOR_ARMOR_IGNORED = dev.elsewhere.arsenal.Overhaul3.REBALANCE_ARSENAL ? 0.4 : 0.5;
	public static final int CASTER_ENCHANTABILITY = 10;

	// ---- Moonsilk (per piece: helmet, chest, legs, boots) ----
	public static final int MOONSILK_DURABILITY = 33, LINED_DURABILITY = 37, MOONSILK_ENCHANTABILITY = 14;
	public static final float MOONSILK_TOUGHNESS = 2, LINED_TOUGHNESS = 3, LINED_KB = 0.05F;
	public static final double HOOD_MAX = 6, HOOD_COST = -0.10, ROBE_MAX = 6, ROBE_POWER = 0.10, LEGS_REGEN = 0.15, BOOTS_REGEN = 0.15, BOOTS_PIERCE = 1;

	// ---- Boss pieces ----
	public static final double MANTLE_MAX = 6;
	public static final float MANTLE_AIR_REGEN_MULT = 2.0F;
	public static final double DIADEM_MAX = 8, DIADEM_COST = -0.15, DIADEM_POWER = 0.10;
	public static final float DIADEM_TOUGHNESS = 3;

	// ---- Trinket and consumables ----
	public static final double FOCUS_MAX = 8, FOCUS_REGEN = 0.25;
	public static final int MAX_ACTIVE_TRINKETS = 2;
	public static final float TONIC_RESTORE = 12, STRONG_TONIC_RESTORE = 20;
	public static final int TONIC_SURGE_TICKS = 200, TONIC_COOLDOWN_SECONDS = 20, TONIC_DRINK_TICKS = 32;
	public static final float TONIC_SURGE_MULT = 2.0F;
	public static final int CLARITY_TICKS = 3 * 60 * 20, LONG_CLARITY_TICKS = 8 * 60 * 20;
	public static final double CLARITY_COST = -0.25, CLARITY_REGEN = 0.25;
}
