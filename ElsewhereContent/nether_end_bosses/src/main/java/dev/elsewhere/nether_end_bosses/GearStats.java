package dev.elsewhere.nether_end_bosses;

/**
 * Every gear and drop number for this module's bosses, in one place so BALANCE.md values plug in quickly.
 * Values are from BOSSES.md (2026-11-10). Where the doc gives no number, the placeholder is marked
 * "PLACEHOLDER" and a question is filed in ROOT/ElsewhereContent/QUESTIONS.md. Not tuned here on purpose.
 */
public final class GearStats {
	private GearStats() {}

	// ---- Grunthar & Ashtusk (Tier I) ----
	/** Tyrant's Tusk: golden axe "with netherite-level attributes" (netherite axe: 10 damage, 1.0 speed). */
	public static final float TUSK_DAMAGE = 9.0F; // BALANCE.md final: diamond axe 9 / 1.0
	public static final float TUSK_SPEED = 1.0F;
	public static final int TUSK_DURABILITY = 1561;
	/** Piglins stay neutral while it is held (radius they re-check anger in). */
	public static final double TUSK_PIGLIN_CALM_RADIUS = 16;
	/** Ashtusk Harness: ridden hoglin speed multiplier while steering with the crimson fungus on a stick. PLACEHOLDER. */
	public static final float HARNESS_HOGLIN_SPEED = 1.0F;
	public static final int HARNESS_BOOST_COOLDOWN_TICKS = 100;
	/** Ashtusk Gland: Test Meter +T booster, ng/dL. PLACEHOLDER (doc gives no amount). */
	public static final double GLAND_TESTOSTERONE = 150;

	// ---- Solaxis (Tier I) ----
	public static final int NINEFOLD_ROD_FIREBALLS = 3;
	/** ARSENAL.md (beta 2): spends Glimmer instead of a cooldown; counts as a spell. */
	public static final float NINEFOLD_ROD_GLIMMER = 6.0F;
	public static final float NINEFOLD_ROD_USE_DELAY_S = 1.0F;
	public static final int NINEFOLD_ROD_USES = 250;
	/** Pyre Crown armor/toughness: doc gives none; diamond-helmet floor. PLACEHOLDER. */
	public static final double PYRE_CROWN_ARMOR = 3, PYRE_CROWN_TOUGHNESS = 2;
	public static final double PYRE_CROWN_ON_FIRE_DAMAGE = 1.0;
	public static final int SOLAXIS_EMBER_LIGHT = 15;

	// ---- Mournmaw (Tier II) ----
	public static final double LAMENT_HARNESS_FLYING_SPEED = 0.25;
	public static final double SOULWICK_REVEAL_RADIUS = 16;
	/** Mournmaw's Blessing second wind: Regeneration III + Resistance II. Durations PLACEHOLDER. */
	public static final int BLESSING_REGEN_TICKS = 200, BLESSING_RESISTANCE_TICKS = 100;
	public static final int BLESSING_REGEN_LEVEL = 1, BLESSING_RESISTANCE_LEVEL = 0; // Regeneration II, Resistance I
	/** Mournmaw's Tear: clears Test Meter damage-stress T loss. */

	// ---- Ossyra (Tier VI) ----
	/** Inverted Crown: gravity -30%, safe fall +8, Levitation immunity. Armor PLACEHOLDER (diamond floor). */
	public static final double INVERTED_CROWN_GRAVITY = -0.25, INVERTED_CROWN_SAFE_FALL = 6;
	public static final double INVERTED_CROWN_KNOCKBACK_TAKEN = 1.25;
	public static final double INVERTED_CROWN_ARMOR = 3, INVERTED_CROWN_TOUGHNESS = 2;
	/** Gravity Pearl: toggles gravity to 0.02 for 20 s, 30 s cooldown, not consumed. */
	public static final double GRAVITY_PEARL_GRAVITY_FACTOR = 0.25;
	public static final int GRAVITY_PEARL_TICKS = 160;
	public static final float GRAVITY_PEARL_GLIMMER = 12.0F;
	public static final float GRAVITY_PEARL_USE_DELAY_S = 2.0F;

	// ---- Aphelion (Tier VI) ----
	public static final double VOID_ELYTRA_ARMOR = 6, VOID_ELYTRA_TOUGHNESS = 2;
	public static final int VOID_ELYTRA_DURABILITY = 864;
	/** Wyrmfang: netherite spear; lunge ignores 6 armor; +30% on airborne targets. */
	public static final double WYRMFANG_ARMOR_PIERCE = 2;
	public static final int WYRMFANG_DURABILITY = 1600;
	public static final float WYRMFANG_AIRBORNE_DAMAGE = 2.0F;
	/** ARSENAL.md (beta 2): spear reach, vanilla entity_interaction_range. */
	public static final double WYRMFANG_REACH = 1.0;

	// ---- per-player drop rolls (doc lists drops, not chances: PLACEHOLDER) ----
	/** G7: first kill: signature 100%, other uniques 50%; repeat kills: each unique 20%; death protection 5% on repeats. */
	public static final float DROP_FIRST_KILL_OTHER = 0.5F;
	public static final float DROP_REPEAT_UNIQUE = 0.2F;
	public static final float DROP_REPEAT_DEATH_PROTECTION = 0.05F;
	/** Kept for the first-listed drops: the signature item. */
	public static boolean signatureDrop(boolean firstKill, net.minecraft.util.RandomSource r) {
		return firstKill || r.nextFloat() < DROP_REPEAT_UNIQUE;
	}

	public static boolean otherDrop(boolean firstKill, net.minecraft.util.RandomSource r) {
		return r.nextFloat() < (firstKill ? DROP_FIRST_KILL_OTHER : DROP_REPEAT_UNIQUE);
	}
}
