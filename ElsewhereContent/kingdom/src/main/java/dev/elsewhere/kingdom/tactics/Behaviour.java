package dev.elsewhere.kingdom.tactics;

/**
 * A named tactic: pure decision logic that reads {@link Perception} and {@link SquadMemory} and returns {@link Order.Orders}; it never touches entities (FIELD-TACTICS 1.2).
 * Kingdom lanes implement their own and list them in their {@link TacticsProfile#behaviours()}; the shared library is {@link Behaviours}.
 */
public interface Behaviour {
	/** Registry id: "shield_wall", "kite_melee", ... */
	String id();

	Types.Bucket bucket();

	/** 0 = not applicable; higher wins inside the bucket. */
	double score(Perception p, SquadMemory m, SquadBrain b);

	Order.Orders plan(Perception p, SquadMemory m, SquadBrain b);

	/** Ticks before this behaviour may be chosen again by the same squad. */
	default int cooldownTicks() {
		return 0;
	}

	/** Anti-flicker: once chosen it stays this long unless a SURVIVE behaviour scores. */
	default int minCommitTicks() {
		return 20;
	}

	/** 0..1 relative decision cost; the sum evaluated in one decision is capped at 1.0. */
	default double cost() {
		return 0.1;
	}

	/** How long the orders it writes stay valid (ticks). */
	default int orderTicks() {
		return 60;
	}
}
