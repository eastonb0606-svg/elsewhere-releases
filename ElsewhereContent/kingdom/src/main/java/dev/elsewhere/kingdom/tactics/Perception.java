package dev.elsewhere.kingdom.tactics;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a squad's brain knows at a decision (FIELD-TACTICS 1.3): only what its soldiers can see or hear, plus intel from scouts. No wall-hacks: a contact that is not
 * {@code visibleNow} is a ghost (old) and is never targeted. Built by {@link Perceiver}; this class imports no Standing or inventory API (test 6.24).
 */
public record Perception(long tick, Vec3 squadCentre, List<Contact> contacts, List<Friendly> friends, OddsEstimate odds, TerrainRead terrain, Environment env, List<ThreatEvent> events,
	List<IntelSource> sources) {

	/** One thing the squad has seen. {@code type} is the entity type path (creeper, enderman, warden, skeleton, player...); extras after the spec's fields. */
	public record Contact(UUID id, Types.ContactKind kind, Vec3 lastSeen, int ageTicks, boolean visibleNow, @Nullable LoadoutSummary loadout, double estThreat, boolean inWater, boolean onHighGround,
		String type, boolean primed, double hpFraction, boolean ranged, boolean shielded, boolean commander, boolean banner, boolean inBoat) {}

	public record Friendly(UUID id, Vec3 pos, double hpFraction, Types.TacticalRole role, boolean shield, boolean ranged, boolean leader) {}

	/** {@code mine} and {@code theirs} in soldier-equivalents; {@code confidence} 0..1 (1 with true intel). */
	public record OddsEstimate(double mine, double theirs, double ratio, double confidence) {}

	/** A short scan of the ground: the best ledge for archers, water or lava near the squad. */
	public record TerrainRead(@Nullable BlockPos ledge, boolean water, boolean lava, boolean confinedContact) {
		public static final TerrainRead EMPTY = new TerrainRead(null, false, false, false);
	}

	public record Environment(boolean night, boolean raining, boolean thunder) {}

	public record ThreatEvent(long tick, String kind, Vec3 pos, @Nullable UUID source) {}

	/** Extra intel from a scout, falcon or allied squad, reported with its own age. */
	public interface IntelSource {
		List<Contact> contacts(long now);

		String id();
	}

	/** Which extra intel sources a faction may use (falcon, scouts); the default allows allied squads only. */
	public interface Filter {
		boolean allows(IntelSource s);

		Filter ALLIES_ONLY = s -> !s.id().startsWith("falcon");
		Filter ALL = s -> true;
	}

	/** Visible gear of a player or soldier (FIELD-TACTICS 1.3). Unknown fields are {@code UNKNOWN}/false, never guessed from hidden state. */
	public record LoadoutSummary(int armorTier, String weapon, boolean shield, boolean ranged, boolean mounted, boolean elytra, boolean pearlInHand) {
		public static final LoadoutSummary UNKNOWN = new LoadoutSummary(-1, "UNKNOWN", false, false, false, false, false);

		/** Player combat value in soldier-equivalents (1.3.1). */
		public double pcv(double healthFraction) {
			double w = switch (weapon) {
				case "sword" -> 7;
				case "axe" -> 9;
				case "mace" -> 8;
				case "spear", "trident" -> 6;
				default -> 3;
			};
			return Math.max(0.3, (1.0 + 0.35 * Math.max(0, armorTier) + w / 8.0 + (shield ? 0.3 : 0) + (ranged ? 0.25 : 0)) * healthFraction);
		}
	}
}
