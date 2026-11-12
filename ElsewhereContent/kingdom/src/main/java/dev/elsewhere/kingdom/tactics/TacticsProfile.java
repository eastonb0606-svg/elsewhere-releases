package dev.elsewhere.kingdom.tactics;

import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/**
 * A faction's field-tactics profile (FIELD-TACTICS 1.2). Only {@link #faction()} is required; every other accessor has a default so a lane overrides just what it
 * changes (or builds one with {@link SimpleTacticsProfile#builder}). Register with {@link dev.elsewhere.kingdom.KingdomApi#registerTactics}. Values may be overridden by
 * the faction JSON {@code tactics} block ({@link TacticsConfig}).
 */
public interface TacticsProfile {
	Identifier faction();

	/** Ticks between decisions, 10..20. */
	default int decisionInterval() {
		return 20;
	}

	/** Behaviours in no particular order; the brain sorts by bucket and score. */
	default List<Behaviour> behaviours() {
		return List.of();
	}

	/** Role specs by unit role (standoff distance, strafe, aggression). */
	default Map<dev.elsewhere.kingdom.UnitType.Role, RoleSpec> roles() {
		return Map.of();
	}

	/** Refuse-fight threshold (Azmar 1.5, Aldermark 1.2). */
	default double minLocalOdds() {
		return 1.2;
	}

	/** Break contact below this odds (Azmar 0.85, Aldermark 0.55, Hrafnholt 0 = never). */
	default double retreatOdds() {
		return 0.55;
	}

	default int reactionTicksMin() {
		return 8;
	}

	default int reactionTicksMax() {
		return 20;
	}

	/** Per decision chance to hold the previous order or pick the second-best behaviour (Azmar 0.03, Aldermark 0.08, Hrafnholt 0.10, Mirewick 0.20). */
	default double blunderRate() {
		return 0.08;
	}

	default SignalSet signals() {
		return SignalSet.DEFAULT;
	}

	/** Which extra intel (falcon, scouts) the faction may use. */
	default Perception.Filter intelFilter() {
		return Perception.Filter.ALLIES_ONLY;
	}

	/** The WAR-AI intelligence noise (sigma of ln error) used for odds; null = take the faction's brain tuning. */
	default Double intelNoise() {
		return null;
	}

	/** "default" or "careful": the mob policy (FIELD-TACTICS 3.3). The shared library enables creeper and warden avoidance for "careful". */
	default String mobPolicy() {
		return "default";
	}

	/** Cheese counters this profile implements, answered on the third use (1.5.2). */
	default List<CheeseCounter> cheese() {
		return List.of();
	}

	/** A pass-through profile never creates a brain: the 2.0 goals run unchanged (Aldermark). */
	default boolean passThrough() {
		return false;
	}

	default void onSquadCreated(SquadBrain brain) {}

	default String name() {
		return faction().getPath() + "_profile";
	}

	/** Spec for one unit role. {@code standoff} is the distance ranged units keep from a melee contact. */
	record RoleSpec(double standoff, boolean strafe, double aggression) {}

	/** A named answer to a player's cheese tactic: {@code detect} reads the perception; {@code answer} is used from the third sighting (the first gets a sane default). */
	interface CheeseCounter {
		String id();

		boolean detect(Perception p);

		Order.Orders answer(Perception p, SquadMemory m, SquadBrain b, int uses);
	}

	/** Which signals a faction uses and how often it shouts. */
	record SignalSet(boolean horn, boolean banner, double shoutChance) {
		public static final SignalSet DEFAULT = new SignalSet(true, true, 0.5);
		public static final SignalSet NONE = new SignalSet(false, false, 0);
	}
}
