package dev.elsewhere.kingdom.tactics;

/** Small enums of the squad-tactics framework (FIELD-TACTICS 1.2). */
public final class Types {
	private Types() {}

	/** Decision buckets in priority order: the first bucket with an applicable behaviour wins. */
	public enum Bucket {
		SURVIVE, REACT, ENGAGE, POSITION, IDLE
	}

	/** Tactical role inside a squad (assigned once per squad, reassigned when a member dies). */
	public enum TacticalRole {
		ANVIL, SHIELD, HAMMER, SKIRMISHER, SCREEN, SCOUT, LEADER, BANNER
	}

	public enum OrderKind {
		HOLD, MOVE_TO_SLOT, ADVANCE, FALL_BACK, KEEP_RANGE, FOCUS, SHIELD_UP, CHARGE, FLANK, LURE, RALLY, WITHDRAW, SKIRMISH, GUARD_ALLY, PICKET
	}

	public enum SignalKind {
		HORN_LONG, HORN_SHORT2, HORN_SHORT3, BANNER_RAISE, FOCUS_MARK, SHOUT, FALCON_DIVE
	}

	public enum ContactKind {
		PLAYER, KINGDOM_SOLDIER, HOSTILE_MOB, VILLAGE_RAIDER, PASSIVE, PET, UNKNOWN
	}
}
