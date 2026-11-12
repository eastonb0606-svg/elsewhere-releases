package dev.elsewhere.rivals.mirewick.boss;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Pure rules of the Lantern Council (RIVALS.md 1C, RIVAL-BOSSES Council rules), free of the world so they can be tested
 * directly. The runtime ({@link MagnateBoss}) calls these; when the court lane's oligarchy API lands it supersedes the
 * Chair and vote selection, and these stay the reference for the squabble rules (payment, flight, lantern).
 */
public final class CouncilRules {
	private CouncilRules() {}

	public enum Decision { WAR, PEACE, HIRE }

	/** Vault score: 100, plus 10 per village in the ward, plus 5 per outpost, plus a d20 weekly draw (ties go to Marrow). */
	public static int vault(Magnate m, long week, int villages, int outposts) {
		Random r = new Random(week * 31L + m.ordinal() * 7919L + 17L);
		return 100 + 10 * villages + 5 * outposts + 1 + r.nextInt(20);
	}

	/** The Chair: the living Magnate with the largest vault (a tie goes to Marrow, then seat order). Null when none lives. */
	public static @Nullable Magnate chair(Map<Magnate, Integer> livingVaults) {
		Magnate best = null;
		int bv = Integer.MIN_VALUE;
		for (Magnate m : Magnate.values()) {
			Integer v = livingVaults.get(m);
			if (v == null) continue;
			if (v > bv) {
				best = m;
				bv = v;
			}
		}
		return best;
	}

	/** Chair max HP: base plus 12 per other living Magnate (the court's {@code hp_per_other_magnate} bonus). */
	public static double chairHealth(Magnate m, int livingOthers) {
		return m.hp + Magnate.CHAIR_HP_PER_OTHER * livingOthers;
	}

	/** One step in the order hawk, miser, dove, wrapping (a bribe moves one Magnate's leaning one step for the week). */
	public static Magnate.Leaning step(Magnate.Leaning l) {
		return switch (l) {
			case HAWK -> Magnate.Leaning.MISER;
			case MISER -> Magnate.Leaning.DOVE;
			case DOVE -> Magnate.Leaning.HAWK;
		};
	}

	/** The weekly vote: hawk votes war, dove peace, miser hire; plurality wins; a tie goes to hiring, then peace. A dead Magnate has no vote. */
	public static Decision vote(List<Magnate.Leaning> leanings) {
		int war = 0, peace = 0, hire = 0;
		for (Magnate.Leaning l : leanings) {
			switch (l) {
				case HAWK -> war++;
				case DOVE -> peace++;
				case MISER -> hire++;
			}
		}
		int max = Math.max(war, Math.max(peace, hire));
		if (hire == max) return Decision.HIRE;
		if (peace == max) return Decision.PEACE;
		return Decision.WAR;
	}

	/** Every decision takes 1 to 2 in-game days to act on. */
	public static int decisionDelayDays(Random r) {
		return 1 + r.nextInt(2);
	}

	// ---- the Almoner (who paid last) -------------------------------------------------------------------------------------

	/** Payment steps: the first time a Magnate falls under 70% HP and again at each further 20 percentage points (50, 30, 10). */
	public static int paymentStep(double hpFraction) {
		if (hpFraction >= 0.70) return 0;
		return 1 + (int) Math.floor((0.70 - hpFraction) / 0.20 + 1e-9);
	}

	/**
	 * The Almoner's pay ledger: {@code pay} records that a Magnate paid; {@code healTarget} is only the latest payer (even if
	 * another is nearly dead), or the Almoner himself (8%) when he is alone.
	 */
	public static final class Ledger {
		private @Nullable UUID last;
		private final Map<UUID, Integer> paidSteps = new java.util.HashMap<>();

		/** Called as a Magnate's HP fraction changes; returns true when this Magnate pays this call. */
		public boolean offer(UUID magnate, double hpFraction) {
			int step = paymentStep(hpFraction);
			int done = paidSteps.getOrDefault(magnate, 0);
			if (step > done) {
				paidSteps.put(magnate, step);
				last = magnate;
				return true;
			}
			return false;
		}

		public @Nullable UUID lastPayer() {
			return last;
		}

		public void clearIfGone(java.util.function.Predicate<UUID> alive) {
			if (last != null && !alive.test(last)) last = null;
		}

		public void reset() {
			last = null;
			paidSteps.clear();
		}
	}

	/** Heal 12% of the payee's max HP, three casts per fight; alone he heals himself for 8%. */
	public static double healFraction(boolean alone) {
		return alone ? 0.08 : 0.12;
	}

	// ---- the richest flees -----------------------------------------------------------------------------------------------

	/** The Magnate with the largest vault among those in the fight (null for fewer than two: a lone Magnate stands). */
	public static @Nullable Magnate richest(Map<Magnate, Integer> inFightVaults) {
		if (inFightVaults.size() < 2) return null;
		return chair(inFightVaults);
	}

	public static final double FLEE_AT = 0.40, FLEE_RETURN_HEAL = 0.10;
	public static final int FLEE_TICKS = 1200;

	// ---- Dark Lanterns -----------------------------------------------------------------------------------------------------

	/**
	 * True when every seat has been killed within 7 in-game days of the first death and none was refilled first. Inputs are the
	 * in-game days of each death (null for a living or refilled seat).
	 */
	public static boolean darkLanterns(Map<Magnate, Long> deathDay, long refillDays) {
		long first = Long.MAX_VALUE, last = Long.MIN_VALUE;
		for (Magnate m : Magnate.values()) {
			Long d = deathDay.get(m);
			if (d == null) return false;
			first = Math.min(first, d);
			last = Math.max(last, d);
		}
		return last - first < refillDays;
	}

	public static Map<Magnate, Long> emptyDeaths() {
		return new EnumMap<>(Magnate.class);
	}
}
