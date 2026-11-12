package dev.elsewhere.duelist.adapt;

/** What the reader decides a player is (B1). A player can carry several. */
public enum LoadoutClass {
	/** Bow or crossbow in either hand, or fired in the last 10 s. */
	RANGED,
	/** A shield raised at least once in the last 10 s. */
	SHIELD,
	/** Mace, axe or any weapon with a long recovery. */
	HEAVY,
	/** Spear, trident, pike. */
	REACH,
	/** A sword and no shield. */
	LIGHT,
	/** Speed, Jump Boost, Slow Falling, elytra worn, or 1 or more pearls thrown. */
	MOBILE,
	/** 2 or more potions or heal items used. */
	POTION_HEAVY
}
