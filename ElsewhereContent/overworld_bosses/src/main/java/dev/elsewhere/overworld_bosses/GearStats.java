package dev.elsewhere.overworld_bosses;

/**
 * Every gear and drop number of this module in one place, so BALANCE.md values plug in quickly (CONTRACT.md "Design
 * ownership"). Values are the BOSSES.md doc values; where the doc gives none, the value is a PLACEHOLDER marked as
 * such, waiting for BALANCE.md. Boss stats and attack numbers live in {@code data/elsewhere/boss_tuning/*.json}.
 */
public final class GearStats {
	private GearStats() {}

	// ---- Old Gulpmother ----------------------------------------------------------------------------------------
	/** Lilycrown (lily_pad + equippable head): oxygen_bonus +2, water_movement_efficiency +0.5, jump_strength +0.1. */
	public static final double LILYCROWN_OXYGEN = 2.0;
	public static final double LILYCROWN_WATER_MOVE = 0.5;
	public static final double LILYCROWN_JUMP = 0.1;
	public static final double LILYCROWN_ARMOR = 3.0; // BALANCE.md final
	public static final double LILYCROWN_TOUGHNESS = 2.0;
	/** Gulp Tongue (fishing_rod): grapple, use_cooldown 3 s. Pull speed is a PLACEHOLDER. */
	public static final float GULP_TONGUE_COOLDOWN_S = 3.0F;
	public static final double GULP_TONGUE_PULL = 1.6;
	public static final int GULP_TONGUE_DURABILITY = 256; // BALANCE.md final
	/** Frog Legs food. PLACEHOLDER values (lean protein: cooked chicken-like). */
	public static final int FROG_LEGS_NUTRITION = 5;
	public static final float FROG_LEGS_SATURATION = 0.6F;
	/** Bufo Gland (Green Leaf tie-in): 10 s of Nausea "swamp vision". */
	public static final int BUFO_GLAND_NAUSEA_TICKS = 200;

	// ---- Strawgrim -----------------------------------------------------------------------------------------------
	/** Harvest Scythe (iron_hoe): attack_damage +7, attack_speed -3.0, harvest+replant 5x5, use_cooldown 1 s. */
	public static final double SCYTHE_DAMAGE = 8.0; // BALANCE.md final: 9 damage total
	public static final double SCYTHE_SPEED = -3.0; // 1.0 attacks/s
	public static final int SCYTHE_DURABILITY = 1561;
	public static final double GRIN_ARMOR = 3.0;
	public static final double GRIN_TOUGHNESS = 2.0;
	public static final float SCYTHE_COOLDOWN_S = 1.0F;
	public static final int SCYTHE_RADIUS = 2; // 5x5
	/** Harvest Moon Seeds: crops grow 2x. */
	public static final int HARVEST_MOON_GROWTH_MULT = 2;
	public static final int HARVEST_LOAF_NUTRITION = 8; // PLACEHOLDER
	public static final float HARVEST_LOAF_SATURATION = 0.8F; // PLACEHOLDER

	// ---- Neferkhet ------------------------------------------------------------------------------------------------
	/** Ankh of Neferkhet: death_protection with Regeneration II 10 s + 20 s where undead ignore you. */
	public static final int ANKH_REGEN_TICKS = 200;
	public static final int ANKH_REGEN_AMP = 1;
	public static final int ANKH_UNDEAD_IGNORE_TICKS = 400;
	/** Sunfire Khopesh (golden_sword with diamond-level attribute_modifiers, enchantable 22): sets undead on fire. */
	public static final double KHOPESH_DAMAGE = 6.0; // BALANCE.md final: 7 / 1.6
	public static final double KHOPESH_SPEED = -2.4;
	public static final int KHOPESH_DURABILITY = 1561;
	public static final int KHOPESH_ENCHANTABLE = 22;
	public static final int KHOPESH_IGNITE_SECONDS = 3;
	/** Ankh drop: first kill 100%, repeat 5%; 30 s death-protection lockout (BALANCE G4). */
	public static final float ANKH_REPEAT_CHANCE = 0.05F;
	/** Pharaoh's Nemes (golden helmet, armor 3). */
	public static final double NEMES_ARMOR = 3.0;
	public static final double NEMES_TOUGHNESS = 2.0;
	public static final int ROYAL_HONEY_NUTRITION = 6; // PLACEHOLDER
	public static final float ROYAL_HONEY_SATURATION = 0.4F; // PLACEHOLDER

	// ---- Rimeback -------------------------------------------------------------------------------------------------
	/** Rimeheart Maul (mace): a smash attack freezes the target and adds Slowness III for 3 s. */
	public static final int MAUL_FREEZE_TICKS = 140;
	public static final int MAUL_SLOW_TICKS = 60;
	public static final int MAUL_SLOW_AMP = 1; // BALANCE.md final: Slowness II
	public static final int MAUL_COOLDOWN_TICKS = 160;
	/** Rimewalker Boots (leather boots dyed ice blue): armor 3, toughness 2, freeze immunity, Frost Walker. */
	public static final double BOOTS_ARMOR = 3.0;
	public static final double BOOTS_TOUGHNESS = 2.0;
	public static final int BOOTS_DYE = 0x9AD8F5;
	public static final int BOOTS_FROST_WALKER = 2;
	/** Glacier Water: the Test Meter "cold plunge" quick effect; without Test Meter, PLACEHOLDER: Fire Resistance 30 s. */
	public static final int GLACIER_WATER_FIRE_RES_TICKS = 600;

	// ---- Vorlagh --------------------------------------------------------------------------------------------------
	/** Undertow (trident, Loyalty III): hits pull the target 3 blocks toward you; works as Riptide in water. */
	public static final double UNDERTOW_PULL_BLOCKS = 3.0;
	public static final int UNDERTOW_LOYALTY = 3;
	public static final float UNDERTOW_RIPTIDE_STRENGTH = 3.0F; // Riptide III equivalent, PLACEHOLDER
	public static final int UNDERTOW_DURABILITY = 500;
	public static final int UNDERTOW_PULL_COOLDOWN_TICKS = 80;
	/** Kraken Ink Bomb: blindness cloud, use_cooldown 10 s. Cloud size PLACEHOLDER. */
	public static final float INK_BOMB_COOLDOWN_S = 10.0F;
	public static final float INK_CLOUD_RADIUS = 3.0F;
	public static final int INK_CLOUD_TICKS = 120;
	public static final int INK_BLIND_TICKS = 100;
	/** Abyssal Eye (heart_of_the_sea, offhand): Conduit Power + Night Vision while submerged. */
	public static final int ABYSSAL_EYE_EFFECT_TICKS = 260;
	public static final int CALAMARI_NUTRITION = 8; // PLACEHOLDER
	public static final float CALAMARI_SATURATION = 0.9F; // PLACEHOLDER
	/** Siren Conch drop chance from Tidewrack Drowned (PLACEHOLDER), +2% per Looting level. */
	public static final float SIREN_CONCH_CHANCE = 0.08F;

	// ---- Boss drop rolls (PLACEHOLDER: BOSSES.md lists the drops, not the chances) --------------------------------
	/** Chance per kill for each of a boss's unique drops after the first kill (first kill: one unique guaranteed). */
	public static final float UNIQUE_REPEAT_CHANCE = 0.35F;
	public static final int FOOD_DROP_MIN = 2;
	public static final int FOOD_DROP_MAX = 4;
}
