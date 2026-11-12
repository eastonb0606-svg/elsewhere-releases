package dev.elsewhere.summoner;

/** Every summoner number from ARSENAL.md §5 (FINAL, 2026-11-10). Change numbers here only. */
public final class GearStats {
	private GearStats() {}

	// ---- §5a framework ----
	public static final int SUMMON_GLIMMER_COST = 10;
	/** The 1.0 s call (bow-draw speed, damage interrupts it). */
	public static final int SUMMON_CALL_TICKS = 20;
	/** Battle-call rule: 60 s fervor, reset when the minion or its owner trades damage with a hostile mob. */
	public static final int FERVOR_TICKS = 60 * 20;
	/** Glimmer each live minion reserves from the owner's max (read by arsenal's Glimmer pool via MinionApi). */
	public static final int GLIMMER_RESERVE_PER_MINION = 4;
	public static final double BASE_MINION_SLOTS = 1.0;
	/** Default of the server config {@code maxMinionsPerPlayer} (see {@link SummonerConfig}, which holds the live value). */
	public static final int MAX_MINIONS_PER_PLAYER = 4;
	/** Hard global cap on total minion slots from all sources combined (the attribute is clamped to this). */
	public static final int HARD_SLOT_CAP = 4;

	// ---- Summoner Calling (ARSENAL.md rev 4 §3b), index = worn summoner pieces 0..4 ----
	public static final Double[] ATTUNE_DAMAGE = {0.80, 0.90, 0.95, 0.98, 1.00};
	public static final Integer[] ATTUNE_FERVOR_TICKS = {600, 800, 1000, 1100, 1200};
	public static final Integer[] ATTUNE_RESERVE = {6, 5, 4, 4, 4};
	/** Herd focus (rev 4): own damage (melee, arrows, spells) -10% per minion out beyond the first, additive with the piece cut. */
	public static final double HERD_FOCUS_PER_EXTRA_MINION = -0.10;
	/** Bell of the Flock (rev 4): max_glimmer +6 while a summoner piece is worn. */
	public static final double BELL_MAX_GLIMMER = 6;
	public static final Float[] ATTUNE_GLIMMER_DRAIN = {0F, 0F, 0F, 0F, 0F};
	public static final Integer[] ATTUNE_SLOT_CAP = {HARD_SLOT_CAP, HARD_SLOT_CAP, HARD_SLOT_CAP, HARD_SLOT_CAP, HARD_SLOT_CAP};
	/** Starter staff (Kitestring): its minions count as at least this Calling. */
	public static final int STARTER_MIN_CALLING = 2;
	/** Each active minion slows the owner's Glimmer regen by 10% (applied by arsenal's Glimmer from MinionApi.activeCount). */
	public static final double REGEN_SLOW_PER_MINION = 0.10;

	// ---- Herding Crook (rev 3 §5b) ----
	public static final double CROOK_DAMAGE = Overhaul3.REBALANCE_SUMMONER ? 6.0 : 5.0, CROOK_SPEED = 1.6, CROOK_REACH = 1.5;
	public static final int CROOK_DURABILITY = 1561, CROOK_ENCHANT = 10;
	public static final int HERDED_TICKS = 5 * 20;
	public static final float HERDED_BONUS = 1.0F;

	// ---- upkeep switches (0 = off) ----
	/** Per-summon lifetime in ticks; 0 = lives until dismissed. */
	public static final int SUMMON_DURATION_TICKS = 0;
	/** Glimmer drained per live minion per second (on top of the 4 reserve); 0 = off. A failed drain dismisses the newest minion. */
	public static final float DRAIN_GLIMMER_PER_SECOND = 0F;
	/** Food exhaustion added to the owner per live minion per second; 0 = off. */
	public static final float DRAIN_EXHAUSTION_PER_SECOND = 0F;
	/** Dismiss a minion after this many ticks without a target; 0 = off. */
	public static final int IDLE_DISMISS_TICKS = 0;
	public static final double FOLLOW_MIN = 3.0;
	public static final double FOLLOW_MAX = 6.0;
	public static final double TELEPORT_DISTANCE = 24.0;
	public static final double TELEPORT_DISTANCE_BOOTS = 16.0;
	/** An owner moving more than this in one tick counts as a teleport (minions follow at once). */
	public static final double OWNER_TELEPORT_JUMP = 10.0;
	public static final double FLY_HOVER_MIN = 2.0;
	public static final double FLY_HOVER_MAX = 4.0;
	public static final int TARGET_MEMORY_TICKS = 100; // 5 s
	public static final double AGGRO_SCAN_RANGE = 10.0;
	public static final double TARGET_RANGE = 16.0;
	public static final int LOST_SIGHT_TICKS = 100; // 5 s
	public static final int STAFF_DURABILITY = 128;
	public static final float STAFF_MELEE_DAMAGE = 1.0F; // the player's base 1, no bonus

	// ---- §5b minions ----
	public static final double ROOTLING_HP = 20, ROOTLING_ARMOR = 4, ROOTLING_SPEED = 0.30, ROOTLING_DAMAGE = 4;
	public static final int ROOTLING_ATTACK_TICKS = 20;
	public static final int ROOTLING_ROOT_TICKS = 20, ROOTLING_ROOT_AMPLIFIER = 1, ROOTLING_ROOT_PER_TARGET_TICKS = 80;

	public static final double KITE_HP = 12, KITE_ARMOR = 0, KITE_DAMAGE = 4, KITE_KNOCKBACK = 0.5, KITE_RANGE = 16;
	public static final int KITE_ATTACK_TICKS = Overhaul3.REBALANCE_SUMMONER ? 25 : 30;
	/** Sporebloom Staff (Phase 3): heals 2 hearts (4 HP, x Calling strength) every 6 s; reserves 6; durability 128. */
	public static final double SPORELING_HP = 16;
	public static final float SPORELING_HEAL = 4.0F;
	public static final int SPORELING_PERIOD_SECONDS = 6, SPORELING_RESERVE = 6;
	/** Gale Ribbon (Phase 3 whip): 3 damage, speed 1.5, reach 6 (+3 on the 3 base), 700 durability; tag 5 s, ends 3 s after the last swing. */
	public static final double RIBBON_DAMAGE = Overhaul3.REBALANCE_SUMMONER ? 4.0 : 3.0, RIBBON_SPEED = 1.5, RIBBON_REACH = 3.0, RIBBON_DAMAGE_BONUS = 0.30, RIBBON_RANGE_BONUS = 4.0;
	public static final int RIBBON_DURABILITY = 700, RIBBON_TAG_TICKS = 100, RIBBON_IDLE_TICKS = 60;
	/** bal-minion-armor: minion hits count the target's armor at this fraction (toughness unchanged). */
	public static final double MINION_ARMOR_FACTOR = 0.5;
	/** bal-minion-survive: one boss hit deals at most this fraction of a minion's max HP. */
	public static final double MINION_BOSS_HIT_CAP = 0.6;
	public static final double KITE_FLY_SPEED = 0.6;

	public static final double BRASSBACK_HP = 40, BRASSBACK_ARMOR = 10, BRASSBACK_SPEED = 0.25, BRASSBACK_DAMAGE = 2;
	public static final int BRASSBACK_ATTACK_TICKS = 20;
	public static final int BRASSBACK_TAUNT_EVERY_TICKS = 100, BRASSBACK_TAUNT_TICKS = 60;
	public static final double BRASSBACK_TAUNT_RANGE = 8;
	public static final float BRASSBACK_WATER_HEAL_PER_S = 1.0F;

	// ---- §5c armor ----
	public static final int[] HERDCALLER_ARMOR = {3, 8, 6, 3}; // head, chest, legs, feet
	public static final float HERDCALLER_TOUGHNESS = 2.0F;
	public static final int HERDCALLER_DURABILITY_MULT = 33, HERDCALLER_ENCHANT = 14;
	public static final int[] LINED_ARMOR = {3, 8, 6, 3};
	public static final float LINED_TOUGHNESS = 3.0F, LINED_KB = 0.05F;
	public static final int LINED_DURABILITY_MULT = 37;
	public static final double CAP_SLOTS = 1, VEST_SLOTS = 1, LEGGINGS_MINION_DAMAGE = 0.15;
	/** Every summoner armor piece: own attack_damage and spell_power -20% (add_multiplied_base), arrows -20% per piece. */
	public static final double OWN_DAMAGE_PER_PIECE = dev.elsewhere.stub.summoner.CallingStub.callingsV3() ? 0.0 : -0.10; // 3.0 Callings: class armor no longer taxes you
	public static final double BOOTS_MINION_SPEED = 0.15;
	public static final double PACK_BOND_RANGE = 8, PACK_BOND_ARMOR = 4;
	public static final float PACK_BOND_HEAL_PER_S = 1.0F, PACK_BOND_REFUND = 10.0F;

	public static final int CROWN_ARMOR = 3;
	public static final float CROWN_TOUGHNESS = 2.0F;
	public static final double CROWN_SLOTS = 1, CROWN_ROOTLING_HP = 0.5;
	public static final int HARNESS_ARMOR = 8;
	public static final float HARNESS_TOUGHNESS = 2.0F;
	public static final double HARNESS_SLOTS = 1, HARNESS_MINION_DAMAGE = 0.15;
	/** Boss pieces: diamond durability (multiplier 33), enchantability 10 like diamond. */
	public static final int BOSS_PIECE_DURABILITY_MULT = 33, BOSS_PIECE_ENCHANT = 10;

	// ---- §5d trinket and consumable ----
	public static final double BELL_SLOTS = 1;
	public static final int BISCUIT_STACK = 16, BISCUIT_NUTRITION = 2;
	public static final float BISCUIT_SATURATION = 0.4F, BISCUIT_USE_SECONDS = 1.0F;
	public static final int WELL_FED_TICKS = 3 * 60 * 20;
	public static final double WELL_FED_DAMAGE = 0.20, WELL_FED_ARMOR = 4, WELL_FED_HEAL_FRACTION = 0.5;

	// ---- §7 Moon-Eye (arsenal owns the trinket; the minion half of its bonus is applied here) ----
	public static final double MOON_EYE_BONUS = 0.20;
}
