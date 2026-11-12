package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomConfig;
import dev.elsewhere.kingdom.KingdomPerf;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.dev.TestFactions;
import dev.elsewhere.kingdom.tactics.Behaviours;
import dev.elsewhere.kingdom.tactics.SimpleTacticsProfile;
import dev.elsewhere.kingdom.tactics.TacticsRegistry;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/**
 * Armies self-test ({@code -Delsewhere.armies.selftest=true}, add {@code -Delsewhere.armies.selftest.exit=true} to stop the
 * server after): data validation, 3.0 caps, the 15 v 15 bench (must stay at or under 2.0 ms average, p95 3.0) and the biggest
 * 3.0 battle, 32 v 32 with tactics and every soldier on full AI (added MSPT at most 5.0 ms average, p95 at most 8.0), plus
 * projectile hygiene and NPC-kill drops measured during that battle. Writes {@code selftest-armies.log}.
 */
public final class ArmiesSelfTest {
	private ArmiesSelfTest() {}

	interface Step {
		int run(MinecraftServer srv, ServerLevel l) throws Exception;
	}

	static final List<String> LOG = new ArrayList<>();
	static final List<Step> STEPS = new ArrayList<>();
	static int pass, fail, idx, wait, startDelay, y;
	static boolean started, finished, measuring;
	static long tickStart;
	static final List<Long> SAMPLES = new ArrayList<>();
	static double baseline;
	static int spawned, maxLive;
	static final Identifier A = Factions.ALDERMARK;
	static final Identifier M = TestFactions.MARCHES;

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) srv.overworld().setChunkForced(cx, cz, true);
		});
		ServerTickEvents.START_SERVER_TICK.register(s -> {
			if (measuring) tickStart = System.nanoTime();
		});
		ServerTickEvents.END_SERVER_TICK.register(ArmiesSelfTest::endTick);
	}

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[armies-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void info(String line) {
		LOG.add(line);
		System.out.println("[armies-selftest] " + line);
	}

	static void endTick(MinecraftServer srv) {
		if (measuring && tickStart != 0) SAMPLES.add(System.nanoTime() - tickStart);
		if (finished) return;
		ServerLevel l = srv.overworld();
		if (!started) {
			if (++startDelay < 600 && !l.isPositionEntityTicking(new BlockPos(0, 0, 0))) return;
			if (startDelay < 60) return;
			started = true;
			y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
			build();
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (idx >= STEPS.size()) {
			finish(srv);
			return;
		}
		Step s = STEPS.get(idx++);
		try {
			wait = s.run(srv, l);
		} catch (Throwable t) {
			check("step " + idx + " crashed", false, t);
			t.printStackTrace();
			wait = 0;
		}
	}

	static void finish(MinecraftServer srv) {
		finished = true;
		measuring = false;
		info("TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-armies.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("elsewhere.armies.selftest.exit")) srv.halt(false);
	}

	static BlockPos at(int x, int z) {
		return new BlockPos(x, y, z);
	}

	static void clean(ServerLevel l) {
		for (Entity e : l.getEntities((Entity) null, new AABB(-90, y - 20, -90, 90, y + 40, 90), e -> !(e instanceof Player))) e.discard();
		KingdomState.get(l).clearForTests();
		ArrowHygiene.reset();
	}

	static List<Identifier> members(int n, boolean mounted) {
		List<Identifier> base = List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER, UnitTypes.MAN_AT_ARMS, UnitTypes.CROSSBOWMAN, UnitTypes.KNIGHT,
			UnitTypes.ARCHER, UnitTypes.PIKEMAN, UnitTypes.MAN_AT_ARMS);
		List<Identifier> out = new ArrayList<>();
		for (int i = 0; i < n; i++) out.add(mounted && i == n - 1 ? UnitTypes.LANCER : base.get(i % base.size()));
		return out;
	}

	/** Spawns {@code perSide} soldiers per side in squads of up to 8 (leader + 7), Aldermark at z = -gap, the test faction at +gap. */
	static int spawnBattle(ServerLevel l, int perSide, int gap) {
		int made = 0;
		for (Identifier f : List.of(A, M)) {
			int side = 0, sq = 0;
			int squads = (perSide + 7) / 8;
			while (side < perSide && sq < squads + 2) {
				int n = Math.min(8, perSide - side);
				int x = (sq - squads / 2) * 12;
				List<KingdomSoldier> s = KingdomApi.spawnSquad(l, at(x, f.equals(A) ? -gap : gap), f, sq % 2 == 1 ? Squad.Formation.SHIELD_WALL : Squad.Formation.LINE,
					UnitTypes.SERGEANT, members(n - 1, sq == 0));
				side += s.size();
				sq++;
			}
			made += side;
		}
		return made;
	}

	static double[] stats() {
		long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
		double avg = Arrays.stream(s).average().orElse(0) / 1e6;
		double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
		double max = s.length == 0 ? 0 : s[s.length - 1] / 1e6;
		return new double[] {avg, p95, max, s.length};
	}

	static void build() {
		// ---- data -----------------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			check("armies overhaul is on (config/elsewhere_armies.json)", ArmiesConfig.enabled, null);
			check("army.scale parsed without errors", ArmyScale.parseErrors().isEmpty(), ArmyScale.parseErrors());
			ArmyScale ald = ArmyScale.of(A);
			check("Aldermark has army.scale and army.routine (lance of 10, patrol 8/6, capital 80-100, 5 watch changes)", ald != null && ald.squadSize() == 10
				&& ald.patrolDay() == 8 && ald.patrolNight() == 6 && ald.capital()[0] == 80 && ald.capital()[1] == 100 && ald.routine().watchChanges().size() == 5, ald);
			for (ArmyScale s : ArmyScale.all().values()) check("valid army data: " + s.faction(), s.problems().isEmpty(), s.problems().isEmpty() ? s : s.problems());
			List<Integer> squads = ArmyScale.all().values().stream().map(ArmyScale::squadSize).distinct().toList();
			check("kingdoms differ in squad size (" + ArmyScale.all().size() + " loaded)", ArmyScale.all().size() < 2 || squads.size() == ArmyScale.all().size(), squads);
			check("routine lookups: Aldermark drills at 07:00, markets at 10:00, sleeps at 23:00; watch changes at 06:00",
				ald != null && "drill".equals(ArmyScale.activityAt(A, 7).activity()) && "market".equals(ArmyScale.activityAt(A, 10).activity())
					&& "sleep".equals(ArmyScale.activityAt(A, 23).activity()) && ArmyScale.isWatchChange(A, 6) && !ArmyScale.isWatchChange(A, 7), null);
			int g = ArmyScale.garrisonSize(A, "village", l.getRandom(), 3);
			check("garrison size comes from data (Aldermark village 12-16)", g >= 12 && g <= 16, g);
			check("3.0 caps applied: " + ArmyCaps.state, KingdomConfig.maxRealSoldiersPerPlayer == ArmyCaps.PER_PLAYER && KingdomConfig.maxRealSoldiersPerArea == ArmyCaps.PER_AREA
				&& KingdomConfig.maxRealSoldiersGlobal == ArmyCaps.GLOBAL && KingdomConfig.maxSquadSize == ArmyCaps.SQUAD, null);
			TestFactions.register();
			TacticsRegistry.register(SimpleTacticsProfile.builder(M).interval(10).minOdds(1.2).retreatOdds(0.55).blunder(0.1).behaviours(Behaviours.library()).build());
			KingdomPerf.forceActive = true;
			KingdomPerf.testObservers.clear();
			clean(l);
			return 20;
		});
		// ---- idle baseline ----------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			SAMPLES.clear();
			measuring = true;
			tickStart = 0;
			return 200;
		});
		STEPS.add((srv, l) -> {
			measuring = false;
			baseline = stats()[0];
			info(String.format("BENCH idle baseline: MSPT avg %.2f ms", baseline));
			return 2;
		});
		// JIT warm-up: one unmeasured 20 v 20 skirmish, so the first measured battle isn't paying for class loading and compilation
		STEPS.add((srv, l) -> {
			clean(l);
			spawnBattle(l, 20, 10);
			return 300;
		});
		bench("15 v 15 field battle (tactics, all full AI)", 15, 9, 2.0, 3.0, false);
		bench("32 v 32 biggest 3.0 battle (tactics, all full AI)", 32, 12, 5.0, 8.0, true);
		waves();
		routine();
		beatable();
		SwarmSelfTest.build(); // swarm-as-one checks and the swarm off/on 32 v 32 bench (s-swarm1)
	}

	// ---- 150 v 150 through reinforcement waves (ARMIES.md 3.5 / 9.7) ------------------------------------------------------------
	static ArmyWaves.Battle wb;

	static void waves() {
		STEPS.add((srv, l) -> {
			clean(l);
			ArmyWaves.reset();
			ArmyWaves.intervalScale = 0.25; // waves every 10 s instead of 40 s so a short test sees several
			ArmyWaves.defaultFront = 32; // the test faction fields a full half of the area cap too
			KingdomPerf.testObservers.clear();
			KingdomPerf.testObservers.add(net.minecraft.world.phys.Vec3.atCenterOf(at(0, 0)));
			wb = ArmyWaves.start(l, at(0, 0), A, M, 150, 150, -1);
			check("a 150 v 150 wave battle starts", wb != null && !wb.a.soldiers.isEmpty() && !wb.d.soldiers.isEmpty(), wb == null ? null : wb.a.spawned + " + " + wb.d.spawned);
			info("BENCH start: 150 v 150 through waves (live " + (wb == null ? 0 : wb.a.spawned + wb.d.spawned) + ")");
			return 40;
		});
		STEPS.add((srv, l) -> {
			SAMPLES.clear();
			measuring = true;
			tickStart = 0;
			return 1600;
		});
		STEPS.add((srv, l) -> {
			measuring = false;
			double[] s = stats();
			double added = s[0] - baseline, addedP95 = s[1] - baseline;
			int total = wb == null ? 0 : wb.a.spawned + wb.d.spawned;
			info(String.format("BENCH 150 v 150 waves: MSPT avg %.2f ms (added %.2f), p95 %.2f (added %.2f), max %.2f over %.0f ticks | %d soldiers fielded so far, %d waves (%d + %d), max live %d, reserves %d + %d, lost %d + %d, wounded withdrawn %d",
				s[0], added, s[1], addedP95, s[2], s[3], total, ArmyWaves.wavesSpawned, wb == null ? 0 : wb.a.waves(), wb == null ? 0 : wb.d.waves(), ArmyWaves.maxLiveSeen,
				wb == null ? 0 : wb.a.reserve, wb == null ? 0 : wb.d.reserve, wb == null ? 0 : wb.a.lost, wb == null ? 0 : wb.d.lost, ArmyWaves.withdrawnWounded));
			check("150 v 150 waves: added MSPT average <= 5.0 ms and p95 <= 8.0 ms", s[3] >= 1500 && added <= 5.0 && addedP95 <= 8.0, String.format("avg %.2f p95 %.2f", added, addedP95));
			check("150 v 150 waves: live soldiers never exceed the area cap (" + KingdomConfig.maxRealSoldiersPerArea + ")", ArmyWaves.maxLiveSeen <= KingdomConfig.maxRealSoldiersPerArea, ArmyWaves.maxLiveSeen);
			int budget = wb == null ? 0 : wb.a.front() + wb.d.front();
			check("150 v 150 waves: reinforcement waves arrive and more soldiers fight than the live budget holds (" + budget + ")", ArmyWaves.wavesSpawned >= 2 && total > budget,
				ArmyWaves.wavesSpawned + " waves, " + total + " fielded");
			check("150 v 150 waves: a watched battle stays inside the per-player cap (" + KingdomConfig.maxRealSoldiersPerPlayer + " near one watcher)",
				ArmyWaves.maxLiveSeen <= KingdomConfig.maxRealSoldiersPerPlayer, ArmyWaves.maxLiveSeen);
			ArmyWaves.stopAll(srv, true);
			check("a finished wave battle is cleaned up", ArmyWaves.battles().isEmpty() && wb != null && wb.over, wb == null ? null : wb.result);
			ArmyWaves.intervalScale = 1.0;
			ArmyWaves.defaultFront = 15;
			KingdomPerf.testObservers.clear();
			clean(l);
			return 5;
		});
	}

	// ---- routine v1 (ARMIES.md 2.6 / 9.8) ------------------------------------------------------------------------------------------
	static final List<Squad> RS = new ArrayList<>();
	static final List<BlockPos> RP = new ArrayList<>();

	static void routine() {
		STEPS.add((srv, l) -> {
			clean(l);
			RS.clear();
			RP.clear();
			for (int i = 0; i < 11; i++) {
				BlockPos p = at(-50 + (i % 6) * 18, -50 + (i / 6) * 40);
				List<KingdomSoldier> s = KingdomApi.spawnSquad(l, p, A, Squad.Formation.LINE, UnitTypes.SERGEANT, List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER));
				if (s.isEmpty() || s.get(0).squad() == null) continue;
				s.forEach(x -> x.addTag(ArmyRoutine.OPT_IN));
				RS.add(s.get(0).squad());
				RP.add(s.get(0).blockPosition());
			}
			// the last squad already has another lane's order: routine must leave it alone
			RS.get(RS.size() - 1).setObjective(at(60, 60));
			return 10;
		});
		STEPS.add((srv, l) -> {
			ArmyRoutine.onHour(l, 1, 6); // Aldermark 06:00: watch change, drill block (share 0.5)
			int movedAway = 0;
			for (int i = 0; i < RS.size() - 1; i++) {
				Squad q = RS.get(i);
				var lead = q.leaderEntity(l);
				if (q.objective() != null && lead != null && lead.blockPosition().distManhattan(RP.get(i)) > 4) movedAway++;
			}
			check("routine v1: at Aldermark's 06:00 watch change some garrison squads go to drill (moved out of sight at once)", movedAway >= 1 && movedAway < RS.size() - 1,
				movedAway + " of " + (RS.size() - 1) + " moved, " + ArmyRoutine.teleported + " teleported");
			check("routine v1: a squad with another lane's order is left alone", at(60, 60).equals(RS.get(RS.size() - 1).objective()), RS.get(RS.size() - 1).objective());
			return 10;
		});
		STEPS.add((srv, l) -> {
			ArmyRoutine.onHour(l, 1, 22); // 22:00: watch change, sleep: everyone back to the post
			int home = 0;
			for (int i = 0; i < RS.size() - 1; i++) {
				var lead = RS.get(i).leaderEntity(l);
				if (lead != null && lead.blockPosition().distManhattan(RP.get(i)) <= 4) home++;
			}
			check("routine v1: at 22:00 every garrison squad is back at its post", home == RS.size() - 1, home + " of " + (RS.size() - 1));
			clean(l);
			return 5;
		});
	}

	// ---- squads stay beatable (RIVALS-DECISIONS 19, item 12) ----------------------------------------------------------------------
	static final java.util.Map<ArmyScale, List<List<KingdomSoldier>>> BEAT = new java.util.LinkedHashMap<>();

	static void beatable() {
		STEPS.add((srv, l) -> {
			clean(l);
			BEAT.clear();
			int k = 0;
			for (ArmyScale s : ArmyScale.all().values()) {
				List<List<KingdomSoldier>> reps = new ArrayList<>();
				for (int rep = 0; rep < 3; rep++) {
					List<KingdomSoldier> sq = KingdomApi.spawnSquad(l, at(-60 + k * 10, 60), s.faction(), Squad.Formation.LINE, UnitTypes.SERGEANT,
						dev.elsewhere.kingdom.ArmyComposer.members(l.getRandom(), s.squadSize() - 1, false, s.faction()));
					k++;
					if (!sq.isEmpty()) reps.add(sq);
				}
				BEAT.put(s, reps);
			}
			return 10; // equipment attribute modifiers (weapon damage, armor) apply on the soldiers' first ticks
		});
		STEPS.add((srv, l) -> {
			for (var e : BEAT.entrySet()) {
				ArmyScale s = e.getKey();
				ArmyBeatable.Result worst = null;
				for (List<KingdomSoldier> sq : e.getValue()) {
					List<ArmyBeatable.Unit> units = new ArrayList<>();
					for (KingdomSoldier x : sq) units.add(ArmyBeatable.Unit.of(x));
					ArmyBeatable.Result r = ArmyBeatable.duel(units, s.meleeTokens(), s.rangedTokens());
					if (worst == null || !r.playerWins() || (worst.playerWins() && r.playerHealthLeft() < worst.playerHealthLeft())) worst = r;
				}
				final ArmyBeatable.Result w = worst;
				check("a full " + s.unitName() + " of " + s.squadSize() + " (" + s.faction() + ", tokens " + s.meleeTokens() + "/" + s.rangedTokens()
					+ ") is beatable by one player in Protection I diamond with a Sharpness I sword", w != null && w.playerWins(),
					w == null ? "no squad spawned" : String.format("worst of %d: %s with %.1f of 44 health left after %.0f s, %d/%d killed", e.getValue().size(),
						w.playerWins() ? "won" : "lost", w.playerHealthLeft(), w.seconds(), w.killed(), w.squad()));
			}
			BEAT.clear();
			clean(l);
			return 5;
		});
	}

	static void bench(String name, int perSide, int gap, double maxAvg, double maxP95, boolean big) {
		STEPS.add((srv, l) -> {
			clean(l);
			return 5;
		});
		STEPS.add((srv, l) -> {
			long sup0 = NpcKillDrops.suppressed;
			spawned = spawnBattle(l, perSide, gap);
			info("BENCH start: " + name + " (spawned " + spawned + ")");
			if (big) check("the 3.0 area cap fits a 32 v 32 battle (64 spawned, 2.1 refused past 30)", spawned == perSide * 2, spawned);
			maxLive = 0;
			NpcKillDrops.suppressed = sup0;
			return 40; // settle: formations form, first contact
		});
		STEPS.add((srv, l) -> {
			SAMPLES.clear();
			dev.elsewhere.kingdom.ai.RangedNav.pathsMade = 0;
			dev.elsewhere.kingdom.ai.RangedNav.pathsSkipped = 0;
			measuring = true;
			tickStart = 0;
			return 400;
		});
		STEPS.add((srv, l) -> {
			measuring = false;
			double[] s = stats();
			double added = s[0] - baseline, addedP95 = s[1] - baseline;
			int alive = KingdomPerf.loaded(l).size();
			String line = String.format("BENCH %s: MSPT avg %.2f ms (added %.2f), p95 %.2f (added %.2f), max %.2f over %.0f ticks | spawned %d, alive %d | arrows: %d tracked, %d max live, %d retired, %d for cap, max rest %d ticks | NPC-kill drops suppressed %d",
				name, s[0], added, s[1], addedP95, s[2], s[3], spawned, alive, ArrowHygiene.tracked, ArrowHygiene.maxLive, ArrowHygiene.retired, ArrowHygiene.retiredForCap,
				ArrowHygiene.maxRestSeen, NpcKillDrops.suppressed);
			info(line + String.format(" | ranged repaths: %d made, %d kept", dev.elsewhere.kingdom.ai.RangedNav.pathsMade, dev.elsewhere.kingdom.ai.RangedNav.pathsSkipped));
			long rm = dev.elsewhere.kingdom.ai.RangedNav.pathsMade, rk = dev.elsewhere.kingdom.ai.RangedNav.pathsSkipped;
			check(name + ": ranged repaths are throttled (bow/crossbow moveTo keeps the old path; " + rm + " made, " + rk + " kept)", (!dev.elsewhere.kingdom.ai.RangedNav.throttle || (rm + rk >= 20 && rk >= rm)), rm + "/" + rk);
			check(name + ": added MSPT average <= " + maxAvg + " ms and p95 <= " + maxP95 + " ms", s[3] >= 390 && added <= maxAvg && addedP95 <= maxP95,
				String.format("avg %.2f p95 %.2f", added, addedP95));
			if (big) {
				check("live soldiers never exceed the 64 area cap", KingdomPerf.loaded(l).size() <= ArmyCaps.PER_AREA, KingdomPerf.loaded(l).size());
				check("soldier arrows are unpickable and none rests longer than " + (ArrowHygiene.STUCK_LIFE + ArrowHygiene.SWEEP) + " ticks",
					ArrowHygiene.allUnpickable() && ArrowHygiene.maxRestSeen <= ArrowHygiene.STUCK_LIFE + ArrowHygiene.SWEEP && ArrowHygiene.maxLive <= ArrowHygiene.PER_AREA + 8,
					ArrowHygiene.tracked + " tracked, max live " + ArrowHygiene.maxLive);
				long armor = l.getEntitiesOfClass(ItemEntity.class, new AABB(-90, y - 20, -90, 90, y + 40, 90), e -> e.getItem().has(DataComponents.EQUIPPABLE)).size();
				check("NPC-vs-NPC kills drop no armor (" + NpcKillDrops.suppressed + " deaths)", armor == 0, armor);
				long stray = l.getEntitiesOfClass(AbstractArrow.class, new AABB(-90, y - 20, -90, 90, y + 40, 90), a -> a.pickup != AbstractArrow.Pickup.DISALLOWED
					&& a.getOwner() instanceof KingdomSoldier).size();
				check("no pickable soldier arrow in the arena", stray == 0, stray);
			}
			clean(l);
			return 5;
		});
	}
}
