package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomTargeting;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.ai.SoldierMeleeGoal;
import dev.elsewhere.kingdom.armies.swarm.SlotSteer;
import dev.elsewhere.kingdom.armies.swarm.Swarm;
import dev.elsewhere.kingdom.armies.swarm.SwarmCommands;
import dev.elsewhere.kingdom.armies.swarm.SwarmPaths;
import dev.elsewhere.kingdom.armies.swarm.SwarmPush;
import dev.elsewhere.kingdom.armies.swarm.SwarmSquads;
import dev.elsewhere.kingdom.armies.swarm.TargetAssigner;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Swarm-as-one checks, run at the end of the armies self-test ({@link ArmiesSelfTest#build}): the kill switch, the ring rule, shared march
 * and approach paths (hit, independent copy, TTL), ribbon columns, squad target selection (cap, legality, ray budget, R0 untouched), the ally
 * push filter (R1+ no push, R0 vanilla) and the swarm off/on 32 v 32 bench (swarm off must run no swarm code; swarm on must stay inside the
 * 5.0 / 8.0 ms budget and not cost more than off).
 */
final class SwarmSelfTest {
	private SwarmSelfTest() {}

	static final Identifier A = ArmiesSelfTest.A, M = ArmiesSelfTest.M;
	static final List<Squad> SQ = new ArrayList<>();
	static final List<KingdomSoldier> S = new ArrayList<>();
	static final List<BlockPos> GOAL = new ArrayList<>();
	static final double[] DIST0 = new double[3];
	static long hits0, stored0;
	static boolean wasOn;

	static void build() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			wasOn = Swarm.on();
			KingdomPerf.forceActive = true; // full AI, no players: soldiers sit in R1 unless a test observer is near
			KingdomPerf.testObservers.clear();
			ArmiesSelfTest.check("swarm: kill switch \"swarm\" is in config/elsewhere_armies.json and on", Swarm.keyPresent && Swarm.configured && Swarm.on(),
				"key " + Swarm.keyPresent + ", configured " + Swarm.configured + ", on " + Swarm.on());
			boolean rings = Swarm.ringFor(10, -1) == 0 && Swarm.ringFor(30, -1) == 1 && Swarm.ringFor(60, -1) == 2 && Swarm.ringFor(120, -1) == 3
				&& Swarm.ringFor(20, 1) == 1 && Swarm.ringFor(15, 1) == 0 && Swarm.ringFor(30, 0) == 0 && Swarm.ringFor(33, 0) == 1 && Swarm.ringFor(105, 2) == 3
				&& Swarm.ringFor(100, 2) == 2;
			ArmiesSelfTest.check("swarm: rings 24/48/96 with 8-block hysteresis (promote 8 inside, demote 8 outside)", rings, null);
			// ribbon geometry on an L-shaped trail: walked north 10 blocks, then turned east 5
			List<Vec3> trail = new ArrayList<>();
			for (int i = 0; i < 6; i++) trail.add(new Vec3(4.2 - 0.8 * i, 0, 0)); // 4.2 .. 0.2
			trail.add(new Vec3(0, 0, 0)); // the corner
			for (int i = 1; i <= 12; i++) trail.add(new Vec3(0, 0, -0.8 * i)); // -0.8 .. -9.6
			Vec3 head = new Vec3(5, 0, 0);
			Vec3[] p3 = SwarmSquads.pointBack(trail, head, 3), p7 = SwarmSquads.pointBack(trail, head, 7);
			Vec3 r1 = SwarmSquads.ribbonSlot(trail, head, 1);
			boolean ribbon = p3 != null && p3[0].distanceTo(new Vec3(2, 0, 0)) < 0.05 && p3[1].x > 0.99 && p7 != null && p7[0].distanceTo(new Vec3(0, 0, -2)) < 0.05
				&& p7[1].z > 0.99 && r1 != null && Math.abs(SwarmSquads.distanceToTrail(trail, head, r1) - 0.8) < 0.05 && SwarmSquads.pointBack(trail, head, 40) == null;
			ArmiesSelfTest.check("swarm: ribbon slots lie on the leader's walked trail (around a 90-degree turn), 0.8 to the side", ribbon,
				p3 == null || p7 == null ? "short" : p3[0] + " / " + p7[0] + " / " + r1);
			return 2;
		});
		march();
		approach();
		column();
		targets();
		push();
		bench();
	}

	// ---- shared march paths ------------------------------------------------------------------------------------------------------
	static void march() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			SQ.clear();
			S.clear();
			GOAL.clear();
			int[] xs = {40, 43, 46};
			for (int i = 0; i < 3; i++) {
				List<KingdomSoldier> s = KingdomApi.spawnSquad(l, ArmiesSelfTest.at(xs[i], -40), A, Squad.Formation.LINE, UnitTypes.SERGEANT,
					List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS));
				if (s.isEmpty() || s.get(0).squad() == null) continue;
				Squad q = s.get(0).squad();
				BlockPos g = ArmiesSelfTest.at(40 + i, -10); // one 4x4x4 goal cell, one start section
				q.setObjective(g);
				SQ.add(q);
				S.add(s.get(0));
				GOAL.add(g);
				DIST0[SQ.size() - 1] = Math.sqrt(g.distToCenterSqr(s.get(0).position()));
			}
			hits0 = SwarmPaths.marchHits;
			stored0 = SwarmPaths.marchStored;
			return 80;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			long hits = SwarmPaths.marchHits - hits0, stored = SwarmPaths.marchStored - stored0;
			int closer = 0;
			for (int i = 0; i < S.size(); i++) if (S.get(i).isAlive() && Math.sqrt(GOAL.get(i).distToCenterSqr(S.get(i).position())) < DIST0[i] - 6) closer++;
			ArmiesSelfTest.check("swarm: 3 squads marching from one section to one goal cell share leader paths (joins > 0) and all march on", S.size() == 3 && hits > 0
				&& closer == 3, hits + " joins, " + stored + " searched+stored, " + closer + "/3 leaders 6+ blocks closer");
			Path a = S.size() > 1 ? S.get(0).getNavigation().getPath() : null, b = S.size() > 1 ? S.get(1).getNavigation().getPath() : null;
			ArmiesSelfTest.check("swarm: a joined path is the leader's own copy (separate object and progress)", a == null || b == null || a != b, a + " / " + b);
			// TTL: an entry older than 100 ticks is never joined
			boolean aged = false;
			if (S.size() == 3) {
				KingdomSoldier c = S.get(2);
				SwarmPaths.ageForTests(SwarmPaths.MARCH_TTL + 1);
				aged = !SwarmPaths.joinMarch(c, GOAL.get(2), 0.8);
			}
			ArmiesSelfTest.check("swarm: a shared march path older than " + SwarmPaths.MARCH_TTL + " ticks is not reused", aged, SwarmPaths.storedMarch() + " stored");
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			return 5;
		});
	}

	// ---- shared approach paths ---------------------------------------------------------------------------------------------------
	static void approach() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			KingdomSoldier enemy = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, M, Vec3.atBottomCenterOf(ArmiesSelfTest.at(-48, -60)), null);
			KingdomSoldier a = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, A, Vec3.atBottomCenterOf(ArmiesSelfTest.at(-58, -40)), null);
			KingdomSoldier b = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, A, Vec3.atBottomCenterOf(ArmiesSelfTest.at(-58, -40)).add(0.3, 0, 0), null); // same start cell
			boolean ok = false;
			String detail = "spawn failed";
			if (enemy != null && a != null && b != null) {
				enemy.setNoAi(true);
				a.getNavigation().moveTo(enemy, 1.0);
				SwarmPaths.offerApproach(a, enemy);
				long h0 = SwarmPaths.approachHits;
				boolean joined = SwarmPaths.joinApproach(b, enemy, 1.0);
				Path pa = a.getNavigation().getPath(), pb = b.getNavigation().getPath();
				ok = joined && SwarmPaths.approachHits == h0 + 1 && pa != null && pb != null && pa != pb && pb.getNextNodeIndex() < pb.getNodeCount();
				detail = "joined " + joined + ", paths " + (pa == null ? 0 : pa.getNodeCount()) + "/" + (pb == null ? 0 : pb.getNodeCount());
			}
			ArmiesSelfTest.check("swarm: a squad mate closing on the same enemy joins the fresh approach path (no second search)", ok, detail);
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			return 5;
		});
	}

	// ---- ribbon column -----------------------------------------------------------------------------------------------------------
	static void column() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			SQ.clear();
			S.clear();
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, ArmiesSelfTest.at(-40, -40), A, Squad.Formation.COLUMN, UnitTypes.SERGEANT,
				List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.MAN_AT_ARMS));
			if (!s.isEmpty() && s.get(0).squad() != null) {
				SQ.add(s.get(0).squad());
				S.addAll(s);
				SQ.get(0).setObjective(ArmiesSelfTest.at(-40, -12)); // march 28 blocks, then turn
			}
			hits0 = SlotSteer.ribbonSlots;
			return 120;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			if (!SQ.isEmpty()) SQ.get(0).setObjective(ArmiesSelfTest.at(-12, -12)); // 90-degree turn
			return 100;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			boolean ok = false;
			String detail = "no squad";
			if (!SQ.isEmpty()) {
				Squad q = SQ.get(0);
				Mob lead = q.leaderEntity(l);
				List<Vec3> trail = SwarmSquads.trailOf(q);
				int near = 0, n = 0;
				double worst = 0;
				if (lead != null) for (KingdomSoldier m : q.memberEntities(l)) {
					n++;
					double d = SwarmSquads.distanceToTrail(trail, lead.position(), m.position());
					worst = Math.max(worst, d);
					if (d <= 2.5) near++;
				}
				ok = lead != null && n >= 5 && near == n && SlotSteer.ribbonSlots > hits0 && trail.size() > 8;
				detail = near + "/" + n + " members within 2.5 of the trail (worst " + String.format("%.2f", worst) + "), trail " + trail.size() + " points, ribbon slots "
					+ (SlotSteer.ribbonSlots - hits0);
			}
			ArmiesSelfTest.check("swarm: a column follows its leader's walked trail through a 90-degree turn (ribbon)", ok, detail);
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			return 5;
		});
	}

	// ---- squad target selection --------------------------------------------------------------------------------------------------
	static final List<LivingEntity> ENEMIES = new ArrayList<>();
	static Vec3 OBS = Vec3.ZERO;

	static void targets() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			SQ.clear();
			S.clear();
			ENEMIES.clear();
			KingdomPerf.testObservers.clear();
			List<Identifier> men = new ArrayList<>();
			for (int i = 0; i < 8; i++) men.add(UnitTypes.MAN_AT_ARMS);
			List<KingdomSoldier> s = KingdomApi.spawnSquad(l, ArmiesSelfTest.at(0, 40), A, Squad.Formation.LINE, UnitTypes.SERGEANT, men);
			if (!s.isEmpty() && s.get(0).squad() != null) {
				SQ.add(s.get(0).squad());
				S.addAll(s);
			}
			// two still enemy soldiers 18 blocks out: inside the squad's 24-block scan, beyond the 16-block engage distance
			for (int x : new int[] {-2, 2}) {
				KingdomSoldier e = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, M, Vec3.atBottomCenterOf(ArmiesSelfTest.at(x, 58)), null);
				if (e != null) {
					e.setNoAi(true);
					ENEMIES.add(e);
				}
			}
			return 6; // the squad's shared enemy scan runs on its next 5-tick slot
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			boolean ok = false, legal = true, budget = false;
			String detail = "no squad";
			if (!SQ.isEmpty() && ENEMIES.size() == 2) {
				Squad q = SQ.get(0);
				TargetAssigner.Plan p = TargetAssigner.planNow(q, l); // fresh: a soldier may have cached a plan before the squad's first enemy scan
				int melee = 0, meleeAssigned = 0;
				for (KingdomSoldier b : S) {
					if (!b.isAlive()) continue;
					LivingEntity t = p.targetOf(b.getUUID());
					if (t != null && !KingdomTargeting.canAttack(b, t)) legal = false;
					if (!b.role().ranged()) {
						melee++;
						if (t != null) meleeAssigned++;
					}
				}
				ok = p.maxMeleeOnOneSoldier() <= TargetAssigner.MELEE_PER_ENEMY && meleeAssigned == Math.min(melee, 2 * TargetAssigner.MELEE_PER_ENEMY);
				budget = p.reusedPerception() || p.raysUsed() <= TargetAssigner.RAYS_R1;
				detail = meleeAssigned + " of " + melee + " melee assigned, at most " + p.maxMeleeOnOneSoldier() + " on one enemy, " + p.raysUsed() + " rays"
					+ (p.reusedPerception() ? " (brain perception reused)" : "");
			}
			ArmiesSelfTest.check("swarm: the squad picks targets: at most 3 melee per enemy soldier, the rest hold formation", ok, detail);
			ArmiesSelfTest.check("swarm: every squad-assigned target is legal (KingdomTargeting.canAttack)", legal && ok, null);
			ArmiesSelfTest.check("swarm: one squad decision casts at most " + TargetAssigner.RAYS_R1 + " sight rays (shared perception)", budget, detail);
			boolean managed = !S.isEmpty() && TargetAssigner.managed(S.get(S.size() - 1));
			ArmiesSelfTest.check("swarm: away from players (R1) soldiers take the squad's targets", managed, null);
			OBS = S.isEmpty() ? Vec3.ZERO : S.get(S.size() - 1).position();
			if (!S.isEmpty()) KingdomPerf.testObservers.add(OBS); // a player beside the squad
			return 11; // rings re-check every 10 ticks
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			int own = 0, near = 0;
			for (KingdomSoldier b : S) {
				if (!b.isAlive() || horiz(b.position(), OBS) > 12) continue;
				near++;
				if (!TargetAssigner.managed(b)) own++;
			}
			ArmiesSelfTest.check("swarm: within 24 blocks of a player (R0) soldiers keep their own targeting and sight", near > 0 && own == near, own + "/" + near);
			KingdomPerf.testObservers.clear();
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			return 5;
		});
	}

	// ---- ally push filter --------------------------------------------------------------------------------------------------------
	static void push() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			S.clear();
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(Vec3.atBottomCenterOf(ArmiesSelfTest.at(70, 60)));
			Vec3 far = Vec3.atBottomCenterOf(ArmiesSelfTest.at(-60, 60)), near = Vec3.atBottomCenterOf(ArmiesSelfTest.at(70, 62));
			for (Vec3 p : List.of(far, far.add(0.1, 0, 0), near, near.add(0.1, 0, 0))) {
				KingdomSoldier s = KingdomApi.spawnSoldier(l, UnitTypes.MAN_AT_ARMS, A, p, null);
				if (s != null) S.add(s);
			}
			hits0 = SwarmPush.alliesSkipped;
			return 30;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			boolean ok = S.size() == 4;
			double farGap = ok ? horiz(S.get(0).position(), S.get(1).position()) : -1, nearGap = ok ? horiz(S.get(2).position(), S.get(3).position()) : -1;
			ArmiesSelfTest.check("swarm: allied soldiers away from players don't push each other (R1+)", ok && farGap < 0.35 && SwarmPush.alliesSkipped > hits0,
				String.format("gap %.2f, %d ally pushes skipped", farGap, SwarmPush.alliesSkipped - hits0));
			ArmiesSelfTest.check("swarm: beside a player (R0) allies push apart as in vanilla", ok && nearGap > 0.35, String.format("gap %.2f", nearGap));
			KingdomPerf.testObservers.clear();
			ArmiesSelfTest.clean(l);
			Swarm.resetCaches();
			return 5;
		});
	}

	static double horiz(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- bench: 32 v 32 with the swarm off, then on ------------------------------------------------------------------------------
	static final double[][] RES = new double[2][];
	static final long[][] CNT = new long[2][];
	static final int[] DEATHS = new int[2];
	static long[] before;

	static long[] counters() {
		return new long[] {KingdomPerf.pathsStarted, KingdomPerf.pathsDenied, SoldierMeleeGoal.pathsMade, SlotSteer.decisions + TargetAssigner.plans
			+ SwarmPaths.marchHits + SwarmPaths.marchMisses + SwarmPaths.approachHits + SwarmPaths.approachMisses + SwarmPush.alliesSkipped + SwarmPush.queriesSkipped,
			TargetAssigner.rays, SwarmPaths.approachHits, SwarmPush.alliesSkipped};
	}

	static void bench() {
		for (int run = 0; run < 2; run++) {
			final int r = run;
			final boolean on = run == 1;
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.clean(l);
				Swarm.setActive(on);
				KingdomPerf.testObservers.clear();
				ArmiesSelfTest.spawned = ArmiesSelfTest.spawnBattle(l, 32, 12);
				ArmiesSelfTest.info("BENCH start: swarm " + (on ? "ON" : "OFF") + " 32 v 32 (spawned " + ArmiesSelfTest.spawned + ")");
				return 40;
			});
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				before = counters();
				ArmiesSelfTest.SAMPLES.clear();
				ArmiesSelfTest.measuring = true;
				ArmiesSelfTest.tickStart = 0;
				return 400;
			});
			ArmiesSelfTest.STEPS.add((srv, l) -> {
				ArmiesSelfTest.measuring = false;
				RES[r] = ArmiesSelfTest.stats();
				long[] after = counters();
				long[] d = new long[after.length];
				for (int i = 0; i < d.length; i++) d[i] = after[i] - before[i];
				CNT[r] = d;
				DEATHS[r] = ArmiesSelfTest.spawned - KingdomPerf.loaded(l).size();
				double[] s = RES[r];
				ArmiesSelfTest.info(String.format("BENCH swarm %s 32 v 32: MSPT avg %.2f ms (added %.2f), p95 %.2f (added %.2f), max %.2f over %.0f ticks | path searches %d (denied %d), melee approach searches %d, shared approach paths %d, squad sight rays %d, ally pushes skipped %d | fallen %d",
					on ? "ON" : "OFF", s[0], s[0] - ArmiesSelfTest.baseline, s[1], s[1] - ArmiesSelfTest.baseline, s[2], s[3], d[0], d[1], d[2], d[5], d[4], d[6], DEATHS[r]));
				if (!on) ArmiesSelfTest.check("swarm off (kill switch): no swarm code runs in a 32 v 32 (all swarm counters unchanged)", d[3] == 0, d[3]);
				else {
					double addedAvg = s[0] - ArmiesSelfTest.baseline, addedP95 = s[1] - ArmiesSelfTest.baseline;
					ArmiesSelfTest.check("swarm on 32 v 32: added MSPT average <= 5.0 ms and p95 <= 8.0 ms", s[3] >= 390 && addedAvg <= 5.0 && addedP95 <= 8.0,
						String.format("avg %.2f p95 %.2f", addedAvg, addedP95));
					double offAvg = RES[0][0];
					ArmiesSelfTest.check("swarm on costs no more than swarm off (average within 0.5 ms of off or below)", s[0] <= offAvg + 0.5,
						String.format("on %.2f, off %.2f ms", s[0], offAvg));
					ArmiesSelfTest.check("swarm on: the battle is fought (soldiers fall within 400 ticks)", DEATHS[1] >= 4, DEATHS[1] + " fallen (off: " + DEATHS[0] + ")");
					long offPaths = CNT[0][0], onPaths = d[0];
					ArmiesSelfTest.info(String.format("BENCH swarm A/B: MSPT avg %.2f -> %.2f ms (%+.0f%%), p95 %.2f -> %.2f; path searches %d -> %d (%+.0f%%); melee approach searches %d -> %d",
						offAvg, s[0], offAvg <= 0 ? 0 : (s[0] - offAvg) * 100 / offAvg, RES[0][1], s[1], offPaths, onPaths, offPaths == 0 ? 0 : (onPaths - offPaths) * 100.0 / offPaths,
						CNT[0][2], d[2]));
					for (String line : SwarmCommands.report()) ArmiesSelfTest.info("SWARM " + line);
				}
				ArmiesSelfTest.clean(l);
				Swarm.setActive(wasOn);
				return 5;
			});
		}
	}
}
