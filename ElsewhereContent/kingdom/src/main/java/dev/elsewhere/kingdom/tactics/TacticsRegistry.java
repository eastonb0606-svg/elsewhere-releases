package dev.elsewhere.kingdom.tactics;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Per-faction profile registry (FIELD-TACTICS 1.7). {@link #of} returns the faction's profile, or Aldermark's baseline (a pass-through) when it has none.
 * Register from a mod initialiser through {@link dev.elsewhere.kingdom.KingdomApi#registerTactics}.
 */
public final class TacticsRegistry {
	private TacticsRegistry() {}

	private static final Map<Identifier, TacticsProfile> PROFILES = new LinkedHashMap<>();
	/** Master switch ({@code tactics.enabled} in config): off = every faction runs the 2.0 goals. */
	public static boolean enabled = true;

	public static void register(TacticsProfile p) {
		PROFILES.put(p.faction(), p);
	}

	public static @Nullable TacticsProfile get(Identifier faction) {
		return PROFILES.get(faction);
	}

	/** The profile in force: the faction's own, else Aldermark's baseline. */
	public static TacticsProfile of(Identifier faction) {
		TacticsProfile p = PROFILES.get(faction);
		return p != null ? p : Baseline.PROFILE;
	}

	public static Map<Identifier, TacticsProfile> all() {
		return java.util.Collections.unmodifiableMap(PROFILES);
	}

	/** Tests: forget a profile. */
	public static void unregister(Identifier faction) {
		PROFILES.remove(faction);
	}

	/**
	 * Aldermark's baseline profile (FIELD-TACTICS 2): a pass-through that reproduces 2.0 exactly. Interval 20, min odds 1.2, retreat odds 0.55, blunder 0.08, reaction 8-20,
	 * behaviours: {@code engage_default} only (and the existing retreat goal as {@code fall_back_regroup}); the brain never writes an Order for it.
	 */
	public static final class Baseline {
		private Baseline() {}

		public static final TacticsProfile PROFILE = SimpleTacticsProfile.builder(dev.elsewhere.kingdom.Factions.ALDERMARK).interval(20).minOdds(1.2).retreatOdds(0.55)
			.reaction(8, 20).blunder(0.08).passThrough(true).behaviour(Behaviours.ENGAGE_DEFAULT).signals(TacticsProfile.SignalSet.NONE).build();
	}
}
