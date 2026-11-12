package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.ArmyComposer;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * The 15 v 15 bench for EVERY kingdom pairing (ARMIES.md 9.12): Aldermark, Hrafnholt, Azmar and Mirewick, each against each and
 * mirrored (12 ordered pairings, the first named at z -9, the second at z +9). Each side fields 15 of its own soldiers in two squads
 * (a sergeant plus members rolled from the kingdom's makeup with a fixed seed per kingdom, so a kingdom brings the same 15 to every
 * pairing; no cavalry, as war battles field them), tactics profiles on, every soldier on full AI, the pair at war for the run.
 * Measured like the armies 15 v 15 bench: 40 ticks to close, then 400 measured ticks, added MSPT = minus an idle baseline taken in the same
 * clean arena at the start of the round. {@link #ROUNDS} rounds, interleaved; the median run (by average) counts (ARMIES.md 4.1). Budget
 * per pairing: added average at most 2.0 ms, p95 at most 3.0. Only in the merged build (all four kingdoms loaded); a build with
 * Aldermark alone logs a skip. Writes {@code selftest-armies-pairings.tsv}. Switch: {@code -Delsewhere.armies.pairings.rounds=0} skips it.
 */
final class ArmiesPairings {
	private ArmiesPairings() {}

	static final int PER_SIDE = 15, GAP = 9, SETTLE = 40, TICKS = 400, IDLE = 200;
	static final double MAX_AVG = 2.0, MAX_P95 = 3.0;
	static final int ROUNDS = Integer.getInteger("elsewhere.armies.pairings.rounds", 3);
	static final List<Identifier> KINGDOMS = List.of(Factions.ALDERMARK, KingdomContent.id("hrafnholt"), KingdomContent.id("azmar"), KingdomContent.id("mirewick"));

	/** One measured run: added average, added p95, max, ticks, swings, deaths, live at the end, idle baseline. */
	record Run(double added, double addedP95, double max, int ticks, long swings, int deaths, int alive, double idle) {}

	static final Map<String, List<Run>> RUNS = new LinkedHashMap<>();
	static double idle;
	static long swings0;
	static int spawned;
	static Factions.Stance before;

	static void build() {
		if (ROUNDS <= 0) {
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.info("PAIRINGS skipped (-Delsewhere.armies.pairings.rounds=0)");
				return 1;
			});
			return;
		}
		List<Identifier> missing = KINGDOMS.stream().filter(k -> !Factions.exists(k) || ArmyScale.of(k) == null).toList();
		if (missing.size() == KINGDOMS.size() - 1) {
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.info("PAIRINGS skipped: only " + Factions.ALDERMARK + " is loaded; the per-pairing 15 v 15 bench runs in the merged build (" + missing + " missing)");
				return 1;
			});
			return;
		}
		if (!missing.isEmpty()) {
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.check("all four kingdoms are loaded for the per-pairing 15 v 15 bench", false, missing + " missing");
				return 1;
			});
			return;
		}
		List<Identifier[]> pairs = new ArrayList<>();
		for (Identifier a : KINGDOMS) for (Identifier b : KINGDOMS) if (!a.equals(b)) pairs.add(new Identifier[] {a, b});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			RUNS.clear();
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			ArmiesSelfTest.info("PAIRINGS " + pairs.size() + " ordered pairings x " + ROUNDS + " rounds, 15 v 15, budget added avg <= " + MAX_AVG + " ms, p95 <= " + MAX_P95);
			return 1;
		});
		for (int round = 0; round < ROUNDS; round++) {
			final int rd = round;
			// idle baseline for this round, in the same clean arena
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.clean(l);
				ArmiesSelfTest.SAMPLES.clear();
				ArmiesSelfTest.measuring = true;
				ArmiesSelfTest.tickStart = 0;
				return IDLE;
			});
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.measuring = false;
				idle = ArmiesSelfTest.stats()[0];
				ArmiesSelfTest.info(String.format("PAIRINGS round %d idle baseline: MSPT avg %.2f ms", rd + 1, idle));
				return 1;
			});
			for (Identifier[] p : pairs) bench(p[0], p[1], rd);
		}
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			summary(pairs);
			return 1;
		});
	}

	static String name(Identifier a, Identifier b) {
		return a.getPath() + " v " + b.getPath();
	}

	/** One side: 15 soldiers in squads of 8 and 7 (a sergeant + members of the kingdom's own makeup, seeded per kingdom). */
	static int side(ServerLevel l, Identifier f, int z) {
		RandomSource r = RandomSource.create(4400L + f.hashCode());
		int made = 0, sq = 0, squads = (PER_SIDE + 7) / 8;
		while (made < PER_SIDE && sq < squads + 2) {
			int n = Math.min(8, PER_SIDE - made);
			int x = (sq - squads / 2) * 12;
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, ArmiesSelfTest.at(x, z), f, sq % 2 == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE, UnitTypes.SERGEANT,
				ArmyComposer.members(r, n - 1, false, f));
			made += s.size();
			sq++;
		}
		return made;
	}

	static void bench(Identifier a, Identifier b, int round) {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			return 5;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			before = Factions.stance(a, b);
			Factions.setStance(a, b, Factions.Stance.WAR);
			spawned = side(l, a, -GAP) + side(l, b, GAP);
			if (round == 0) ArmiesSelfTest.info("PAIRINGS start " + name(a, b) + " (spawned " + spawned + ", stance was " + before + ")");
			return SETTLE;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.SAMPLES.clear();
			swings0 = KingdomSoldier.totalAttacks;
			ArmiesSelfTest.measuring = true;
			ArmiesSelfTest.tickStart = 0;
			return TICKS;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.measuring = false;
			double[] s = ArmiesSelfTest.stats();
			int alive = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) if (k.isAlive() && (k.factionId().equals(a) || k.factionId().equals(b))) alive++;
			Run run = new Run(s[0] - idle, s[1] - idle, s[2], (int) s[3], KingdomSoldier.totalAttacks - swings0, spawned - alive, alive, idle);
			RUNS.computeIfAbsent(name(a, b), k -> new ArrayList<>()).add(run);
			ArmiesSelfTest.info(String.format("PAIRINGS %s round %d: MSPT avg %.2f ms (added %.2f), p95 %.2f (added %.2f), max %.2f over %d ticks | spawned %d, %d swings, %d fell, %d standing",
				name(a, b), round + 1, s[0], run.added(), s[1], run.addedP95(), s[2], run.ticks(), spawned, run.swings(), run.deaths(), alive));
			Factions.setStance(a, b, null);
			if (Factions.stance(a, b) != before) Factions.setStance(a, b, before);
			ArmiesSelfTest.clean(l);
			return 5;
		});
	}

	static void summary(List<Identifier[]> pairs) {
		List<String> tsv = new ArrayList<>();
		tsv.add("pairing\tmedian_added_avg_ms\tmedian_run_added_p95_ms\tmax_ms\truns_added_avg\truns_added_p95\tswings\tfell\tpass");
		for (Identifier[] p : pairs) {
			String n = name(p[0], p[1]);
			List<Run> runs = new ArrayList<>(RUNS.getOrDefault(n, List.of()));
			if (runs.isEmpty()) {
				ArmiesSelfTest.check("15 v 15 " + n + ": measured", false, "no run");
				continue;
			}
			List<Run> sorted = new ArrayList<>(runs);
			sorted.sort(Comparator.comparingDouble(Run::added));
			Run med = sorted.get(sorted.size() / 2);
			boolean fought = med.swings() > 0;
			boolean ok = med.ticks() >= TICKS - 10 && med.added() <= MAX_AVG && med.addedP95() <= MAX_P95;
			String all = runs.stream().map(r -> String.format("%.2f", r.added())).toList().toString(), allP = runs.stream().map(r -> String.format("%.2f", r.addedP95())).toList().toString();
			ArmiesSelfTest.check(String.format("15 v 15 %s: added MSPT average <= %.1f ms and p95 <= %.1f ms (median of %d runs)", n, MAX_AVG, MAX_P95, runs.size()), ok,
				String.format("median avg %.2f p95 %.2f max %.2f; runs avg %s p95 %s", med.added(), med.addedP95(), med.max(), all, allP));
			ArmiesSelfTest.check("15 v 15 " + n + ": the sides fought (swings in the window)", fought, med.swings() + " swings, " + med.deaths() + " fell");
			tsv.add(String.format("%s\t%.2f\t%.2f\t%.2f\t%s\t%s\t%d\t%d\t%s", n, med.added(), med.addedP95(), med.max(), all, allP, med.swings(), med.deaths(), ok && fought ? "PASS" : "FAIL"));
		}
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-armies-pairings.tsv")))) {
			tsv.forEach(w::println);
		} catch (Exception ignored) {
		}
	}
}
