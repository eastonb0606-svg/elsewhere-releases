package dev.elsewhere.duelist.kit;

/**
 * The generic, player-style behaviours a boss may opt into. All are off unless the profile passes the matching
 * {@code Behaviours} record. {@link #id()} is the name used by the {@code boss_identity} "generic" list for the seven audited ones ({@link #generic()}).
 */
public enum Move {
	STRAFE("circle_strafe", true), CRIT("sprint_jump_crit", true), SHIELD("shield_block", true), SWAP("weapon_swap", false), BOW("bow", false), CROSSBOW("crossbow", false), EAT("eat", true), DRINK("drink", true), PEARL("pearl", true), BLOCKS("temp_blocks", true);

	private final String id;

	private final boolean generic;

	Move(String id, boolean generic) {
		this.id = id;
		this.generic = generic;
	}

	/** True for the seven behaviours the sameness audit counts (a card's "generic" list); weapon swap, bow and crossbow belong to the weapons slot. */
	public boolean generic() {
		return generic;
	}

	public String id() {
		return id;
	}

	/** Maps a data name ("circle-strafe", "Sprint Crit") to a move, or null. */
	public static Move byId(String name) {
		String n = name.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
		for (Move m : values()) if (m.id.equals(n) || m.name().toLowerCase(java.util.Locale.ROOT).equals(n)) return m;
		return null;
	}
}
