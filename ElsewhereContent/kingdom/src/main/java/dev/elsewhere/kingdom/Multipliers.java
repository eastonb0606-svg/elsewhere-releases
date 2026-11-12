package dev.elsewhere.kingdom;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Per-faction stat, size and makeup multipliers (RIVALS-DECISIONS 3). HP, damage and speed are already applied to every unit by {@link Factions#unit}; this class gives the
 * rest: army size (garrisons and patrols), patrol size by day or night, and the unit makeup (unit id to percent weight) used to roll squad members.
 * Built-in values: Hrafnholt size 1.2, patrols 7/8; Azmar size 0.75; Mirewick size 0.9, patrols 6; the faction file's {@code army} block overrides them.
 */
public final class Multipliers {
	private Multipliers() {}

	public record Stats(double health, double damage, double speed, double size) {}

	public static Stats stats(Identifier faction) {
		FactionDef d = Factions.def(Factions.ledgerOf(faction));
		if (d == null) return new Stats(1, 1, 1, 1);
		FactionDef.Army a = d.army();
		return new Stats(a.healthMultiplier(), a.damageMultiplier(), a.speedMultiplier(), size(faction));
	}

	/** Army size multiplier: the file's {@code size_multiplier} if set, else the built-in for the kingdom, else 1. */
	public static double size(Identifier faction) {
		FactionDef d = Factions.def(Factions.ledgerOf(faction));
		if (d != null && d.army().sizeMultiplier() != 1.0) return d.army().sizeMultiplier();
		return switch (faction.getPath()) {
			case "hrafnholt" -> 1.2;
			case "azmar" -> 0.75;
			case "mirewick" -> 0.9;
			default -> 1.0;
		};
	}

	/** Scales a garrison or patrol size for the faction (at least 1 when {@code base} is positive). */
	public static int scale(Identifier faction, int base) {
		return base <= 0 ? 0 : Math.max(1, (int) Math.round(base * size(faction)));
	}

	/** Patrol size: the file's {@code patrol_day}/{@code patrol_night}, else the built-in (Hrafnholt 7/8, Mirewick 6/6, Azmar a caravan of 5, Aldermark 6/6). */
	public static int patrolSize(Identifier faction, boolean night) {
		FactionDef d = Factions.def(Factions.ledgerOf(faction));
		if (d != null && (d.army().patrolDay() != 6 || d.army().patrolNight() != 6)) return night ? d.army().patrolNight() : d.army().patrolDay();
		return switch (faction.getPath()) {
			case "hrafnholt" -> night ? 8 : 7;
			case "azmar" -> 5;
			default -> 6;
		};
	}

	/** The makeup table (unit id to weight) from the faction file; empty means "use Aldermark's table" (so Aldermark is unchanged). */
	public static Map<Identifier, Float> makeup(Identifier faction) {
		FactionDef d = Factions.def(Factions.ledgerOf(faction));
		return d == null ? Map.of() : d.army().makeup();
	}

	/** One weighted roll from the faction's makeup, or null when the faction has none. {@code allowMounted} excludes cavalry roles' units by {@link ArmyComposer#isMounted} or a unit whose role is cavalry or lancer. */
	public static Identifier roll(Identifier faction, RandomSource r, boolean allowMounted) {
		Map<Identifier, Float> m = makeup(faction);
		if (m.isEmpty()) return null;
		Map<Identifier, Float> use = new LinkedHashMap<>();
		for (var e : m.entrySet()) {
			UnitType u = Factions.unit(faction, e.getKey());
			if (!allowMounted && (u.mounted() || ArmyComposer.isMounted(e.getKey()))) continue;
			if (u.officer()) continue;
			use.put(e.getKey(), e.getValue());
		}
		if (use.isEmpty()) return null;
		double total = 0;
		for (float w : use.values()) total += w;
		double k = r.nextDouble() * total;
		Identifier last = null;
		for (var e : use.entrySet()) {
			last = e.getKey();
			k -= e.getValue();
			if (k < 0) return last;
		}
		return last;
	}
}
