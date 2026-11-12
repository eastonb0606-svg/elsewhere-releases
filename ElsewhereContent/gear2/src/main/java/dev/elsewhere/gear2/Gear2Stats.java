package dev.elsewhere.gear2;

/** Every gear2 number, straight from Ideas/v2gear.json ({@code what} / {@code tradeoff}). */
public final class Gear2Stats {
	private Gear2Stats() {}

	// gear-armor-b Galvanic Coilmail: 3/7/5/3 (18), toughness 3 each (12), no KB res, slow Mending.
	public static final int[] COILMAIL_ARMOR = Overhaul3.REBALANCE_GEAR2 ? new int[] {3, 8, 6, 3} : new int[] {3, 7, 5, 3};
	public static final float COILMAIL_TOUGHNESS = 3.0F;
	public static final float COILMAIL_KB = Overhaul3.REBALANCE_GEAR2 ? 0.05F : 0.0F; // 3.0: 0.05 per piece
	public static final int COILMAIL_DURABILITY = 35; // multiplier: diamond 33 < this < netherite 37
	public static final int COILMAIL_ENCHANTABILITY = 15;
	// Capacitor: max 5 charges, each fades after 8 s; next melee hit discharges into up to 3 other mobs within 4 blocks, 2 per charge, ignoring armor.
	public static final int CAPACITOR_MAX = 5;
	public static final int CAPACITOR_FADE_TICKS = 160;
	public static final int CAPACITOR_TARGETS = 3;
	public static final double CAPACITOR_RADIUS = 4.0;
	public static final float CAPACITOR_DAMAGE_PER_CHARGE = 2.0F;
	// Con: lightning hurts 50% more with the full set (+12.5% per piece, BALANCE's per-piece rule).
	public static final float COILMAIL_LIGHTNING_PER_PIECE = 0.125F;

	// gear-armor-a Amberstill Plate: 3/8/6/3 (20), toughness 2 each (8), KB 0.1 each (0.4), slow Mending, not fire-proof.
	public static final int[] AMBERSTILL_ARMOR = {3, 8, 6, 3};
	public static final float AMBERSTILL_TOUGHNESS = 2.0F, AMBERSTILL_KB = 0.1F;
	public static final int AMBERSTILL_DURABILITY = 35;
	// Stillness: after 2 s without sprinting or attacking, +4 toughness (12 total, matching netherite).
	public static final int STILLNESS_TICKS = 40;
	public static final double STILLNESS_TOUGHNESS = 4.0;

	// gear-weapon1-b Ironclad Flail: 7 damage, speed 1.2, +10% per chained hit within 2 s, max +50%, durability 1700, Skyiron.
	public static final float FLAIL_DAMAGE = 7.0F, FLAIL_SPEED = Overhaul3.REBALANCE_GEAR2 ? 1.25F : 1.2F;
	public static final int FLAIL_DURABILITY = 1700;
	public static final float FLAIL_CHAIN_STEP = Overhaul3.REBALANCE_GEAR2 ? 0.12F : 0.10F;
	public static final int FLAIL_CHAIN_MAX = 5, FLAIL_CHAIN_WINDOW = 40;
	public static final double FLAIL_BACKWARD_SLOW = -0.20; // con: 20% slower while moving backward

	// gear-weapon2-a Gale Rapier: 5 damage, speed 2.4, sprint hit +3 lunge + dash, -25% standing still, durability 1100, Zephyrite.
	public static final float RAPIER_DAMAGE = 5.0F, RAPIER_SPEED = 2.4F;
	public static final int RAPIER_DURABILITY = 1100;
	public static final float RAPIER_LUNGE = 3.0F, RAPIER_STILL_MULT = Overhaul3.REBALANCE_GEAR2 ? 0.85F : 0.75F;
	public static final double RAPIER_DASH = 0.6;

	// gear-ranged-a Mirrorbolt Arbalest: 14 damage, pierce 3 mobs, 60-block range, 2.5 s reload, normal arrows, durability 700.
	public static final float ARBALEST_DAMAGE = 14.0F;
	public static final float ARBALEST_POWER = 3.15F; // vanilla crossbow arrow speed
	public static final int ARBALEST_PIERCE = 3;
	public static final float ARBALEST_RELOAD_SECONDS = Overhaul3.REBALANCE_GEAR2 ? 2.2F : 2.5F;
	public static final int ARBALEST_DURABILITY = 700;
	public static final int ARBALEST_RANGE = 60;

	// gear-drill-a Steam Auger: reach 2 (pickaxe 4.5), 2.5x netherite pickaxe speed on stone/ores/deepslate, Silk Touch only,
	// 1 coal per 100 blocks, durability 1200, Skyiron, no Mending.
	public static final double AUGER_REACH_DELTA = -2.5;
	public static final float NETHERITE_PICK_SPEED = 9.0F;
	public static final float AUGER_SPEED = NETHERITE_PICK_SPEED * 2.5F;
	public static final int AUGER_BLOCKS_PER_COAL = 100;
	public static final int AUGER_DURABILITY = 1200;

	// gear-areaminer-b Termite Hive Lantern: 3x3x1 face spread over 2 s, 1/3 netherite pickaxe speed, dirt/wood/mud/stone only,
	// durability 800, Amber.
	public static final float TERMITE_SPEED = NETHERITE_PICK_SPEED / 3.0F;
	public static final int TERMITE_SPREAD_TICKS = 40;
	public static final int TERMITE_DURABILITY = 800;

	// gear-trinket1-a Seer's Hourglass: boss hints + warnings 0.15 s earlier.
	public static final int HOURGLASS_LEAD_TICKS = 3;

	// gear-trinket2-b Prospector's Pendulum: nearest ore within 24 blocks, up to 2 types, no diamond/ancient debris, durability 300.
	public static final int PENDULUM_RANGE = 24;
	public static final int PENDULUM_TYPES = 2;
	public static final int PENDULUM_DURABILITY = 300;
	public static final int PENDULUM_PING_TICKS = 100;

	// gear-trinket3-b Void Tether Spool: anchor, snap back within 64 blocks, 3 min cooldown, 4 hunger points, same dimension.
	public static final double TETHER_RANGE = 64.0;
	public static final int TETHER_COOLDOWN = 3 * 60 * 20;
	public static final int TETHER_HUNGER = 4;

	// Boss/chest drop chances (loot-table modify event; BALANCE: boss parts ~50%, finished uniques ~30%).
	public static final float PART_CHANCE = 0.50F;
	public static final float RAPIER_CHANCE = 0.30F;
	public static final float HOURGLASS_CHANCE = 0.35F;
	public static final float SPOOL_APHELION_CHANCE = 0.35F, SPOOL_OSSYRA_CHANCE = 0.10F;
	public static final float PENDULUM_THURGRIM_CHANCE = 0.20F, PENDULUM_CHEST_CHANCE = 0.12F;
}
