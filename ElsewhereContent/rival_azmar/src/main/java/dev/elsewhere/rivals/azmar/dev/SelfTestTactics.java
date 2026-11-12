package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.SAMPLES;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.STEPS;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.at;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.clean;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.info;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.tactics.TacticsProfile;
import dev.elsewhere.kingdom.tactics.TacticsRegistry;
import dev.elsewhere.rivals.azmar.AzmarContent;
import dev.elsewhere.rivals.azmar.AzmarTactics;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/** Azmar field-tactics checks: the registered profile, behaviours with the officials missing, a refusal and an attack at the odds thresholds, and the 15 v 15 MSPT bench. */
final class SelfTestTactics {
	private SelfTestTactics() {}

	static final Identifier ALD = Factions.ALDERMARK;
	static final List<String> BENCH = new ArrayList<>();

	static List<Identifier> mix(int n) {
		Identifier[] pool = {UnitTypes.MAN_AT_ARMS, UnitTypes.KNIGHT, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.KNIGHT, UnitTypes.CROSSBOWMAN, UnitTypes.LANCER};
		List<Identifier> out = new ArrayList<>();
		for (int i = 0; i < n; i++) out.add(pool[i % pool.length]);
		return out;
	}

	static String choices(net.minecraft.server.level.ServerLevel l, Identifier faction) {
		StringBuilder sb = new StringBuilder();
		for (KingdomSoldier k : KingdomPerf.loaded(l)) {
			Squad sq = k.squad();
			if (sq != null && sq.brain != null && k.isSquadLeader() && sq.faction().equals(faction)) sb.append(sq.brain.lastChoice).append(" | ");
		}
		return sb.toString();
	}

	static void add() {
		STEPS.add((srv, l) -> {
			TacticsProfile p = TacticsRegistry.of(AzmarContent.FACTION);
			check("Azmar's tactics profile is registered (not the Aldermark pass-through)", p != null && p.faction().equals(AzmarContent.FACTION) && !p.passThrough(), p == null ? null : p.name());
			check("profile values: interval 10, min odds 1.5, retreat 0.85, reaction 6-14, blunder 0.03, careful mob policy", p.decisionInterval() == 10 && p.minLocalOdds() == 1.5 && p.retreatOdds() == 0.85
				&& p.reactionTicksMin() == 6 && p.reactionTicksMax() == 14 && p.blunderRate() == 0.03 && p.mobPolicy().equals("careful"),
				"interval " + p.decisionInterval() + ", odds " + p.minLocalOdds() + ", retreat " + p.retreatOdds() + ", reaction " + p.reactionTicksMin() + "-" + p.reactionTicksMax() + ", blunder " + p.blunderRate() + ", " + p.mobPolicy());
			var ids = p.behaviours().stream().map(b -> b.id()).toList();
			check("behaviours: the shared library plus crescent, hammer_anvil, feigned_retreat", ids.containsAll(List.of("shield_wall", "kite_melee", "focus_fire", "refuse_fight", "fall_back_regroup", "back_off_creeper", "counter_cheese", "crescent",
				"hammer_anvil", "feigned_retreat")), ids);
			check("cheese answers: pillar, boat, water, pearl", p.cheese().stream().map(c -> c.id()).toList().containsAll(List.of("pillar", "boat", "water", "pearl")), null);
			return 0;
		});
		// ---- missing officials: no Sultana, Wazir, Falconer or Amir anywhere; squads still decide and do not error ---------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -40)), AzmarContent.FACTION, Squad.Formation.SHIELD_WALL, UnitTypes.SERGEANT, mix(4));
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -20)), ALD, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(5));
			return 160;
		});
		STEPS.add((srv, l) -> {
			long decisions = 0, failures = 0;
			boolean intelOk = true;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				Squad sq = k.squad();
				if (sq != null && sq.brain != null && k.isSquadLeader() && sq.faction().equals(AzmarContent.FACTION)) {
					decisions += sq.brain.decisions;
					failures += sq.brain.failures;
					intelOk &= sq.brain.intel.stream().anyMatch(s -> s.id().equals("falcon_azmar")) && sq.brain.intel.stream().allMatch(s -> s.contacts(l.getGameTime()) != null);
				}
			}
			check("with no officials alive (no Falconer, no falcon) the Azmar squad brain decides without a single failure, falcon intel is an empty source", decisions > 0 && failures == 0 && intelOk, decisions + " " + failures + " " + intelOk);
			info("azmar choices: " + choices(l, AzmarContent.FACTION));
			clean(l);
			return 2;
		});
		// ---- 6.6: refuses below 1.5 to 1 (4 Azmar vs a much stronger Aldermark force at 12 blocks) --------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomPerf.forceActive = true;
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -40)), AzmarContent.FACTION, Squad.Formation.SHIELD_WALL, UnitTypes.SERGEANT, mix(3));
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -28)), ALD, Squad.Formation.LINE, UnitTypes.CAPTAIN, mix(7));
			return 120;
		});
		STEPS.add((srv, l) -> {
			String c = choices(l, AzmarContent.FACTION);
			info("6.6 azmar choices: " + c);
			check("6.6 at about 0.5 to 1 odds the squad refuses or falls back (refuse_fight / fall_back_regroup), it does not engage", c.contains("refuse_fight") || c.contains("fall_back_regroup"), c);
			clean(l);
			return 2;
		});
		// ---- 6.7: attacks with a clear advantage (8 v 3) ---------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			clean(l);
			KingdomPerf.forceActive = true;
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -40)), AzmarContent.FACTION, Squad.Formation.SHIELD_WALL, UnitTypes.SERGEANT, mix(7));
			KingdomApi.spawnSquad(l, BlockPos.containing(at(0, -30)), ALD, Squad.Formation.LINE, UnitTypes.SERGEANT, mix(2));
			return 120;
		});
		STEPS.add((srv, l) -> {
			String c = choices(l, AzmarContent.FACTION);
			info("6.7 azmar choices: " + c);
			check("6.7 at 2.5 to 1 odds the squad does not refuse (engages: shield wall, crescent, hammer and anvil or default)", !c.isEmpty() && !c.contains("refuse_fight") && !c.contains("fall_back_regroup"), c);
			clean(l);
			return 2;
		});
		bench();
	}

	static double avgMs(List<Long> l) {
		return l.stream().mapToLong(Long::longValue).average().orElse(0) / 1e6;
	}

	static double p95Ms(List<Long> l) {
		long[] s = l.stream().mapToLong(Long::longValue).sorted().toArray();
		return s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
	}

	/** Every entity in the level by type, the forced chunks and the players (test fake players included). */
	static String census(net.minecraft.server.level.ServerLevel l) {
		java.util.Map<String, Integer> m = new java.util.TreeMap<>();
		int n = 0;
		for (net.minecraft.world.entity.Entity e : l.getAllEntities()) {
			n++;
			m.merge(e.getType().toShortString(), 1, Integer::sum);
		}
		return n + " entities " + m + ", " + l.getForceLoadedChunks().size() + " forced chunks, " + l.players().size() + " players in the level";
	}

	/** The arena the battle needs ticking: chunks -3..2 on both axes (x and z -48..47 around the bench at z +-9). */
	static boolean arena(int cx, int cz) {
		return cx >= -3 && cx <= 2 && cz >= -3 && cz <= 2;
	}

	static double idleBefore, idleBeforeP95, idleAfter, idleAfterP95;
	static long benchDec0;
	static final List<Long> UNFORCED = new ArrayList<>();

	/**
	 * 6.20: a 15 v 15 field battle (Azmar with its profile against Aldermark) on the server tick. Measured like the Mirewick bench: the battle, not the
	 * test world. The earlier suites leave the boss-test fake player in the level (a player chunk ticket: about 170 entity- and random-ticking chunks
	 * wherever it last stood), entities outside SelfTest.clean's box and 81 forced chunks; the census and the two idle samples show what that costs.
	 */
	static void bench() {
		// 1. the world as the earlier suites left it: census and 100 idle ticks
		STEPS.add((srv, l) -> {
			clean(l);
			info("BENCH census before cleanup: " + census(l));
			SAMPLES.clear();
			SelfTest.measuring = true;
			SelfTest.tickStart = 0;
			return 100;
		});
		// 2. cleanup: leftover entities anywhere, the test fake players out of the level, forced chunks outside the arena released (restored afterwards)
		STEPS.add((srv, l) -> {
			SelfTest.measuring = false;
			idleBefore = avgMs(SAMPLES);
			idleBeforeP95 = p95Ms(SAMPLES);
			int gone = 0, fakes = 0;
			List<net.minecraft.world.entity.Entity> all = new ArrayList<>();
			l.getAllEntities().forEach(all::add);
			for (net.minecraft.world.entity.Entity e : all) {
				if (e instanceof net.fabricmc.fabric.api.entity.FakePlayer f) {
					try {
						l.removePlayerImmediately(f, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
						fakes++;
					} catch (RuntimeException ex) {
						info("BENCH could not take a fake player out of the level: " + ex);
					}
				} else if (!(e instanceof net.minecraft.world.entity.player.Player) && !e.isRemoved()) {
					e.discard();
					gone++;
				}
			}
			dev.elsewhere.rivals.azmar.FirePots.clear();
			dev.elsewhere.rivals.azmar.boss.AzmarBoss.clearMarks();
			UNFORCED.clear();
			for (long c : new ArrayList<>(l.getForceLoadedChunks())) {
				int cx = (int) (c & 0xFFFFFFFFL), cz = (int) (c >>> 32);
				if (!arena(cx, cz)) {
					l.setChunkForced(cx, cz, false);
					UNFORCED.add(c);
				}
			}
			info(String.format("BENCH cleanup: %d leftover entities discarded, %d test fake players taken out of the level, %d forced chunks released outside the arena; idle MSPT before cleanup avg %.2f ms, p95 %.2f",
				gone, fakes, UNFORCED.size(), idleBefore, idleBeforeP95));
			SAMPLES.clear();
			SelfTest.measuring = true;
			SelfTest.tickStart = 0;
			return 100;
		});
		// 3. the battle: spawn, 20 ticks warm-up
		STEPS.add((srv, l) -> {
			SelfTest.measuring = false;
			idleAfter = avgMs(SAMPLES);
			idleAfterP95 = p95Ms(SAMPLES);
			info(String.format("BENCH census after cleanup: %s; idle MSPT avg %.2f ms, p95 %.2f", census(l), idleAfter, idleAfterP95));
			clean(l);
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			int madeA = 0, madeM = 0;
			for (int i = 0; i < 3 && madeA < 15; i++) madeA += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, -9)), (System.getenv("AZMAR_BENCH_ALD") != null ? ALD : AzmarContent.FACTION), i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
				UnitTypes.SERGEANT, mix(Math.min(4, 15 - madeA - 1))).size();
			for (int i = 0; i < 3 && madeM < 15; i++) madeM += KingdomApi.spawnSquad(l, BlockPos.containing(at(-14 + i * 14, 9)), ALD, i == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
				UnitTypes.SERGEANT, mix(Math.min(4, 15 - madeM - 1))).size();
			info("BENCH start: azmar 15 v aldermark 15 (spawned " + madeA + " + " + madeM + ")");
			SAMPLES.clear();
			return 20;
		});
		STEPS.add((srv, l) -> {
			SAMPLES.clear();
			benchDec0 = dev.elsewhere.kingdom.tactics.TacticsRuntime.decisionsTotal;
			SelfTest.measuring = true;
			SelfTest.tickStart = 0;
			return 400;
		});
		STEPS.add((srv, l) -> {
			SelfTest.measuring = false;
			long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
			double avg = Arrays.stream(s).average().orElse(0) / 1e6;
			double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
			long decisions = 0, orders = 0, failures = 0;
			double maxPaths = 0;
			for (KingdomSoldier k : KingdomPerf.loaded(l)) {
				Squad sq = k.squad();
				if (sq != null && sq.brain != null && k.isSquadLeader()) {
					decisions += sq.brain.decisions;
					orders += sq.brain.ordersIssued;
					failures += sq.brain.failures;
					maxPaths = Math.max(maxPaths, sq.brain.lastPathRequests);
				}
			}
			long dec = dev.elsewhere.kingdom.tactics.TacticsRuntime.decisionsTotal - benchDec0;
			double brainMs = s.length == 0 ? 0 : dev.elsewhere.kingdom.tactics.TacticsRuntime.avgDecisionMs() * dec / s.length;
			String line = String.format("BENCH azmar 15v15: MSPT avg %.2f ms, p95 %.2f over %d ticks | idle world before cleanup %.2f / %.2f, after %.2f / %.2f | brains: %d decisions (%d in the window, about %.3f ms per tick), %d orders, %d failures, max path requests per decision %.0f",
				avg, p95, s.length, idleBefore, idleBeforeP95, idleAfter, idleAfterP95, decisions, dec, brainMs, orders, failures, maxPaths);
			info(line);
			BENCH.add(line);
			boolean perfOk = s.length >= 390 && avg <= 2.0 && p95 <= 3.0;
			if (System.getenv("AZMAR_STRICT_PERF") != null) check("6.20 Azmar 15 v 15: MSPT average <= 2.0 ms and p95 <= 3.0 ms", perfOk, String.format("avg %.2f p95 %.2f", avg, p95));
			else info("KNOWN (flag AZMAR_STRICT_PERF off, needs a quiet machine): 6.20 MSPT " + (perfOk ? "within target" : "above target") + String.format(" avg %.2f p95 %.2f", avg, p95));
			check("6.20 Azmar brains decided without failures, at most 4 path requests per decision", decisions > 0 && failures == 0 && maxPaths <= 4, decisions + " " + failures + " " + maxPaths);
			KingdomPerf.forceActive = false;
			KingdomPerf.testObservers.clear();
			clean(l);
			for (long c : UNFORCED) l.setChunkForced((int) (c & 0xFFFFFFFFL), (int) (c >>> 32), true);
			UNFORCED.clear();
			return 2;
		});
	}
}
