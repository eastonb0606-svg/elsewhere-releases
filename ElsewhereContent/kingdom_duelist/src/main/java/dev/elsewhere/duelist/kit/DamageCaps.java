package dev.elsewhere.duelist.kit;

/** Kinds of hit for {@code HumanBoss.hurtRaw}: which of the boss's caps applies (see {@code DuelistProfile.Builder#caps}). */
public enum DamageCaps {
	/** Ordinary swing, bash, arrow, thrown gear. */
	MINOR,
	/** A signature move or a crit. */
	SIGNATURE,
	/** Armour-ignoring magic (splash, ground patch): raw is converted with {@link Kit#MAGIC_RAW_FACTOR}; cap as SIGNATURE. */
	MAGIC;

	public float cap(dev.elsewhere.duelist.boss.DuelistProfile p) {
		return this == MINOR ? p.capMinor : p.capSignature;
	}
}
