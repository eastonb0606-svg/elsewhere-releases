package dev.elsewhere.kingdom;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;

/**
 * Army composition: weighted rolls for squad members. Mounted units (lancers, mounted swordsmen) are about 10% of the
 * army; at most one of each support unit (standard-bearer, herald, surgeon) per squad.
 */
public final class ArmyComposer {
	private ArmyComposer() {}

	record W(Identifier unit, int weight) {}

	static final List<W> TABLE = List.of(
		new W(UnitTypes.MAN_AT_ARMS, 28), new W(UnitTypes.KNIGHT, 14), new W(UnitTypes.PIKEMAN, 10), new W(UnitTypes.ARCHER, 18),
		new W(UnitTypes.CROSSBOWMAN, 10), new W(UnitTypes.SQUIRE, 6), new W(UnitTypes.LANCER, 5), new W(UnitTypes.MOUNTED_SWORDSMAN, 5),
		new W(UnitTypes.STANDARD_BEARER, 2), new W(UnitTypes.HERALD, 1), new W(UnitTypes.SURGEON, 1));
	static final int TOTAL = TABLE.stream().mapToInt(W::weight).sum();

	public static boolean isMounted(Identifier u) {
		return u.equals(UnitTypes.LANCER) || u.equals(UnitTypes.MOUNTED_SWORDSMAN);
	}

	static boolean isSupport(Identifier u) {
		return u.equals(UnitTypes.STANDARD_BEARER) || u.equals(UnitTypes.HERALD) || u.equals(UnitTypes.SURGEON);
	}

	/** One weighted roll (mounted units about 10%). */
	public static Identifier roll(RandomSource r, boolean allowMounted) {
		for (int tries = 0; tries < 16; tries++) {
			int k = r.nextInt(TOTAL);
			for (W w : TABLE) {
				k -= w.weight();
				if (k < 0) {
					if (!allowMounted && isMounted(w.unit())) break;
					return w.unit();
				}
			}
		}
		return UnitTypes.MAN_AT_ARMS;
	}

	/** {@code n} rolled squad members (no duplicate support units; mounted only if allowed). */
	public static List<Identifier> members(RandomSource r, int n, boolean allowMounted) {
		List<Identifier> out = new ArrayList<>(n);
		while (out.size() < n) {
			Identifier u = roll(r, allowMounted);
			if (isSupport(u) && out.contains(u)) continue;
			out.add(u);
		}
		return out;
	}

	/**
	 * Members rolled from the faction's own makeup ({@code army.makeup} in its file); a faction without one (Aldermark) rolls exactly as the overload without a faction,
	 * so its 2.0 behaviour is unchanged.
	 */
	public static List<Identifier> members(RandomSource r, int n, boolean allowMounted, Identifier faction) {
		if (Multipliers.makeup(faction).isEmpty()) return members(r, n, allowMounted);
		List<Identifier> out = new ArrayList<>(n);
		int guard = 0;
		while (out.size() < n && guard++ < n * 20) {
			Identifier u = Multipliers.roll(faction, r, allowMounted);
			if (u == null) return members(r, n, allowMounted);
			if (isSupport(u) && out.contains(u)) continue;
			out.add(u);
		}
		return out;
	}
}
