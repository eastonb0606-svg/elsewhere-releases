package dev.elsewhere.kingdom.armies.townsfolk;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Townsfolk self-test ({@code -Delsewhere.townsfolk.selftest=true}, plus {@code -Delsewhere.townsfolk.selftest.exit=true} to stop the
 * server after; run it without {@code kingdom.selftest}). Superflat world. Checks the data (roles per kingdom, residents, caps), the
 * street graph and its cached Dijkstra, LOD spawning from the logical count with every cap, the hour replan, folding (far and home),
 * deaths and the daily refill, panic, the not-a-villager and never-saved rules, the event-driven rule (no evaluation while nothing
 * changes), the kill switch, crowd break-out and the B6 cost (48 live townsfolk at market: added MSPT average at most 2.0 ms).
 * Lines {@code [townsfolk-selftest] PASS|FAIL ...}, then {@code TOTAL}; also {@code selftest-townsfolk.log}.
 */
final class TownsfolkSelfTest {
	private TownsfolkSelfTest() {}

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
	static final Identifier ALD = id("aldermark"), HRAF = id("hrafnholt"), AZ = id("azmar"), MIRE = id("mirewick");
	static final int[] SITES = {0, 160, 320, 480};

	static Identifier id(String p) {
		return Identifier.fromNamespaceAndPath("elsewhere", p);
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
			for (int cx = -5; cx <= 35; cx++) for (int cz = -5; cz <= 5; cz++) srv.overworld().setChunkForced(cx, cz, true);
		});
		ServerTickEvents.START_SERVER_TICK.register(s -> {
			if (measuring) tickStart = System.nanoTime();
		});
		ServerTickEvents.END_SERVER_TICK.register(TownsfolkSelfTest::endTick);
	}

	static void check(String name, boolean ok, Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (detail == null ? "" : " (" + detail + ")");
		LOG.add(line);
		System.out.println("[townsfolk-selftest] " + line);
		if (ok) pass++;
		else fail++;
	}

	static void info(String line) {
		LOG.add(line);
		System.out.println("[townsfolk-selftest] " + line);
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
		TownsfolkManager.foldAll("test end");
		TownsfolkManager.testObservers.clear();
		info("TOTAL " + pass + "/" + (pass + fail) + (fail == 0 ? " PASS" : " FAIL"));
		try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Path.of("selftest-townsfolk.log")))) {
			LOG.forEach(w::println);
		} catch (Exception ignored) {
		}
		if (Boolean.getBoolean("elsewhere.townsfolk.selftest.exit")) srv.halt(false);
	}

	static Towns.Town town(String key, Identifier f, int x) {
		return Towns.register(key, f, true, new BlockPos(x, y, 0));
	}

	static void observe(int... xs) {
		TownsfolkManager.testObservers.clear();
		for (int x : xs) TownsfolkManager.testObservers.add(new Vec3(x + 0.5, y, 0.5));
	}

	static double[] stats() {
		long[] s = SAMPLES.stream().mapToLong(Long::longValue).sorted().toArray();
		double avg = Arrays.stream(s).average().orElse(0) / 1e6;
		double p95 = s.length == 0 ? 0 : s[(int) (s.length * 0.95)] / 1e6;
		return new double[] {avg, p95, s.length};
	}

	static void build() {
		// ---- data ----------------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			check("townsfolk switch on and active for this test", TownsfolkConfig.active(), "enabled " + TownsfolkConfig.enabled);
			check("elsewhere:townsfolk is registered", TownsfolkManager.TYPE != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id("townsfolk")), null);
			Map<Identifier, List<String>> want = Map.of(ALD, List.of("peasant", "craftsman"), HRAF, List.of("thrall", "fisherfolk"),
				AZ, List.of("priest", "scribe", "porter", "merchant"), MIRE, List.of("clerk", "bargeman", "lamplighter", "peat_cutter"));
			for (var e : want.entrySet()) {
				TownsfolkRoles.Kingdom k = TownsfolkRoles.of(e.getKey());
				List<String> got = k == null ? List.of() : k.roles().stream().map(TownsfolkRoles.Role::id).toList();
				check("townsfolk-roles " + e.getKey().getPath() + " has its own roles " + e.getValue(), got.equals(e.getValue()), got);
				check("townsfolk-data " + e.getKey().getPath() + " is valid", k != null && TownsfolkRoles.problems(k).isEmpty(), k == null ? "missing" : TownsfolkRoles.problems(k));
			}
			check("townsfolk-data no parse errors", TownsfolkRoles.parseErrors().isEmpty(), TownsfolkRoles.parseErrors());
			int[][] res = {{160, 50}, {180, 60}, {120, 36}, {220, 80}};
			Identifier[] fs = {ALD, HRAF, AZ, MIRE};
			for (int i = 0; i < 4; i++) {
				int c = TownsfolkRoles.residents(fs[i], true), t = TownsfolkRoles.residents(fs[i], false);
				check("townsfolk-residents " + fs[i].getPath() + " capital " + res[i][0] + " / town " + res[i][1], c == res[i][0] && t == res[i][1], c + " / " + t);
			}
			int[] live = {40, 40, 32, 48};
			for (int i = 0; i < 4; i++) {
				TownsfolkRoles.Kingdom k = TownsfolkRoles.of(fs[i]);
				check("townsfolk-caps " + fs[i].getPath() + " live near a player " + live[i], k != null && k.liveCapital() == live[i] && k.liveTown() < k.liveCapital(),
					k == null ? "missing" : k.liveCapital() + " capital, " + k.liveTown() + " town");
			}
			TownsfolkRoles.Kingdom az = TownsfolkRoles.of(AZ);
			boolean smallest = az != null;
			for (Identifier f : fs) {
				TownsfolkRoles.Kingdom k = TownsfolkRoles.of(f);
				if (f != AZ && k != null && az != null) smallest &= az.liveCapital() < k.liveCapital() && az.liveTown() < k.liveTown()
					&& TownsfolkRoles.residents(AZ, true) < TownsfolkRoles.residents(f, true) && TownsfolkRoles.residents(AZ, false) < TownsfolkRoles.residents(f, false);
			}
			check("townsfolk-azmar-smallest in every row", smallest, null);
			// day schedules: lore checks
			TownsfolkRoles.Kingdom mire = TownsfolkRoles.of(MIRE), hraf = TownsfolkRoles.of(HRAF), ald = TownsfolkRoles.of(ALD);
			check("townsfolk-schedule mirewick lamplighters out at 18:00 lighting lanterns", mire != null && mire.role("lamplighter").at(18) != null
				&& "lamplight".equals(mire.role("lamplighter").at(18).activity()), null);
			check("townsfolk-schedule hrafnholt fisherfolk at the jetty at 05:00", hraf != null && hraf.role("fisherfolk").at(5) != null
				&& "jetty".equals(hraf.role("fisherfolk").at(5).where()), null);
			check("townsfolk-schedule azmar streets empty in the siesta (12:00)", az != null && az.outdoorShare(12) == 0.0, az == null ? null : az.outdoorShare(12));
			check("townsfolk-schedule aldermark everyone at market at 10:00, nobody out at 02:00", ald != null && ald.outdoorShare(10) == 1.0 && ald.outdoorShare(2) == 0.0, null);
			return 0;
		});

		// ---- street graph ----------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			TownsfolkState.get(l).clearForTests();
			TownsfolkManager.testDay = 7;
			TownsfolkManager.testHour = 10;
			town("test:aldermark", ALD, SITES[0]);
			observe(SITES[0]);
			TownsfolkManager.evaluate(l);
			check("townsfolk-graph build is queued lazily on first approach", TownsfolkManager.building("test:aldermark"), null);
			TownsfolkManager.drain(l, 400);
			Towns.Town t = Towns.get("test:aldermark");
			StreetGraph g = t == null ? null : t.graph;
			check("townsfolk-graph built: 8-64 nodes with edges", g != null && g.size() >= 8 && g.size() <= StreetGraph.MAX_NODES && g.edges() >= g.size() - 1,
				g == null ? "none" : g.size() + " nodes, " + g.edges() + " edges");
			check("townsfolk-graph build is spread over ticks: at most 1 ms in any one tick", TownsfolkManager.lastBuildMaxStepMs <= 1.0,
				String.format("max %.2f ms per tick, %.2f ms in all", TownsfolkManager.lastBuildMaxStepMs, TownsfolkManager.lastBuildMs));
			if (g != null) {
				boolean ground = true;
				for (int i = 0; i < g.size(); i++) ground &= g.y[i] == y;
				check("townsfolk-graph nodes stand on the ground (none floating or buried)", ground, null);
				BlockPos[] p1 = g.path(0, g.size() - 1);
				int runs = g.dijkstraRuns;
				BlockPos[] p2 = g.path(0, g.size() / 2);
				check("townsfolk-graph Dijkstra path found and cached per source", p1 != null && p1.length >= 2 && p2 != null && g.dijkstraRuns == runs && g.cacheHits >= 1,
					(p1 == null ? "no path" : p1.length + " hops") + ", runs " + g.dijkstraRuns + ", hits " + g.cacheHits);
				check("townsfolk-graph is saved with the town", TownsfolkState.get(l).graph("test:aldermark") != null, null);
			}
			return 2;
		});

		// ---- LOD spawn from the logical count --------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Towns.Town t = Towns.get("test:aldermark");
			TownsfolkManager.evaluate(l);
			TownsfolkManager.drain(l, 60);
			int live = t.live.size();
			check("townsfolk-lod Aldermark capital at market shows its cap of 40 (of 160 residents)", live == 40, live);
			Vec3 o = TownsfolkManager.testObservers.get(0);
			boolean inRange = true, roles = true, held = true;
			for (Townsfolk f : t.live) {
				inRange &= f.distanceToSqr(o.x, f.getY(), o.z) <= 50 * 50; // spawned within 48, then 2 ticks of walking
				roles &= f.look().startsWith("aldermark/peasant/") || f.look().startsWith("aldermark/craftsman/");
				held &= !f.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty();
			}
			check("townsfolk-lod every live resident spawned within 48 blocks of the player", inRange, null);
			check("townsfolk-look Aldermark residents are peasants and craftsmen with their tools", roles && held, t.live.isEmpty() ? null : t.live.get(0).look());
			check("townsfolk-lod logical count unchanged by spawning", TownsfolkState.get(l).data.towns.get("test:aldermark").logical == 160, null);
			Townsfolk f0 = t.live.isEmpty() ? null : t.live.get(0);
			check("townsfolk-not-villager (no trades, not a Merchant) and never saved", f0 != null && !(f0 instanceof net.minecraft.world.item.trading.Merchant)
				&& !net.minecraft.world.entity.npc.villager.AbstractVillager.class.isInstance(f0) && !f0.shouldBeSaved(), null);
			check("townsfolk-plan every resident has an activity on spawn", t.live.stream().allMatch(f -> f.target != null && f.plans >= 1), null);
			return 100; // let them walk
		});

		// ---- event-driven: nothing changes, nothing runs ---------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			long ev = TownsfolkManager.evaluations;
			STEPS.add(idx, (s2, l2) -> {
				check("townsfolk-events no evaluation in 200 ticks without a chunk crossing or an hour change", TownsfolkManager.evaluations == ev, (TownsfolkManager.evaluations - ev) + " evaluations");
				return 0;
			});
			return 200;
		});

		// ---- hour change: replan ----------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Towns.Town t = Towns.get("test:aldermark");
			int before = t.live.stream().mapToInt(f -> f.plans).sum();
			long hev = TownsfolkManager.hourEvents;
			TownsfolkManager.testHour = 14; // fields and workshops
			TownsfolkManager.tick(srv);
			int after = t.live.stream().mapToInt(f -> f.plans).sum();
			check("townsfolk-hour an hour change replans every live resident once", TownsfolkManager.hourEvents == hev + 1 && after - before == t.live.size(), (after - before) + " plans for " + t.live.size());
			long work = t.live.stream().filter(f -> "work".equals(f.activity)).count();
			check("townsfolk-hour at 14:00 Aldermark residents go to the fields and workshops", work * 2 >= t.live.size() && t.live.stream().allMatch(f -> f.target != null),
				work + " of " + t.live.size() + " at work");
			return 40;
		});

		// ---- night: home, then fold when the player leaves --------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Towns.Town t = Towns.get("test:aldermark");
			TownsfolkManager.testHour = 2;
			TownsfolkManager.tick(srv);
			TownsfolkManager.evaluate(l);
			boolean home = t.live.stream().allMatch(f -> f.goingHome);
			check("townsfolk-home at 02:00 everyone still out walks indoors", home, t.live.size() + " live");
			observe(SITES[0] + 400);
			TownsfolkManager.evaluate(l);
			check("townsfolk-fold with the player 400 blocks away nobody stays live", t.live.isEmpty(), t.live.size());
			check("townsfolk-fold folding keeps the logical count", TownsfolkState.get(l).data.towns.get("test:aldermark").logical == 160, null);
			return 2;
		});

		// ---- deaths, Standing rule and the daily refill -----------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Towns.Town t = Towns.get("test:aldermark");
			TownsfolkManager.testHour = 10;
			TownsfolkManager.tick(srv);
			observe(SITES[0]);
			TownsfolkManager.evaluate(l);
			TownsfolkManager.drain(l, 60);
			int live = t.live.size();
			Townsfolk victim = t.live.isEmpty() ? null : t.live.get(0);
			long deaths = TownsfolkManager.deaths;
			if (victim != null) victim.hurtServer(l, l.damageSources().generic(), 100F);
			TownsfolkState.Rec r = TownsfolkState.get(l).data.towns.get("test:aldermark");
			check("townsfolk-death a death lowers the logical count by one", victim != null && !victim.isAlive() && r.logical == 159 && TownsfolkManager.deaths == deaths + 1
				&& t.live.size() == live - 1, r.logical + " residents, " + t.live.size() + " live");
			r.logical = 150;
			r.lastDay = 4;
			int now = TownsfolkState.get(l).logical("test:aldermark", 160, 7);
			check("townsfolk-refill +1 per in-game day (3 days: 150 -> 153)", now == 153, now);
			int full = TownsfolkState.get(l).logical("test:aldermark", 160, 300);
			check("townsfolk-refill never above the town's residents", full == 160, full);
			return 2;
		});

		// ---- panic ----------------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			Towns.Town t = Towns.get("test:aldermark");
			if (t.live.size() < 2) {
				check("townsfolk-panic needs two residents", false, t.live.size());
				return 0;
			}
			Townsfolk a = t.live.get(0), b = t.live.get(1);
			b.snapTo(a.getX() + 1.5, a.getY(), a.getZ(), 0F, 0F);
			a.damageCooldownTime = 0;
			a.hurtServer(l, l.damageSources().generic(), 1F);
			check("townsfolk-panic a hurt resident panics and a neighbour runs too", a.panicking() && b.panicking(), a.panicking() + " / " + b.panicking());
			return 20;
		});

		// ---- caps: four capitals at once ------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			town("test:hrafnholt", HRAF, SITES[1]);
			town("test:azmar", AZ, SITES[2]);
			town("test:mirewick", MIRE, SITES[3]);
			observe(SITES);
			TownsfolkManager.testHour = 9; // markets and work everywhere but Azmar (diwan) and Hrafnholt (weapon play)
			TownsfolkManager.tick(srv);
			for (int i = 0; i < 6; i++) {
				TownsfolkManager.evaluate(l);
				TownsfolkManager.drain(l, 400);
			}
			int total = TownsfolkManager.liveTotal();
			boolean perTown = true;
			StringBuilder sb = new StringBuilder();
			for (Towns.Town t : Towns.all()) {
				perTown &= t.live.size() <= TownsfolkManager.cap(t) && t.live.size() <= TownsfolkManager.AREA_CAP;
				sb.append(t.faction.getPath()).append(' ').append(t.live.size()).append('/').append(TownsfolkManager.cap(t)).append("; ");
			}
			check("townsfolk-caps server cap 144 holds with four capitals in view", total <= TownsfolkManager.SERVER_CAP && total >= 100, total + " live: " + sb);
			check("townsfolk-caps no town above its kingdom cap or the area cap 48", perTown, sb);
			Towns.Town az = Towns.get("test:azmar");
			check("townsfolk-look Azmar residents are priests, scribes, porters and merchants", !az.live.isEmpty() && az.live.stream().allMatch(f -> f.look().startsWith("azmar/")
				&& List.of("priest", "scribe", "porter", "merchant").contains(f.role)), az.live.size());
			Townsfolk extra = TownsfolkManager.breakOut(l, new BlockPos(SITES[3] + 4, y, 4), MIRE);
			check("townsfolk-breakout crowd break-out refused when the caps are full", extra == null || TownsfolkManager.liveTotal() <= TownsfolkManager.SERVER_CAP, null);
			return 2;
		});

		// ---- kill switch --------------------------------------------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			TownsfolkConfig.enabled = false;
			TownsfolkManager.tick(srv);
			int live = TownsfolkManager.liveTotal();
			TownsfolkManager.evaluate(l); // a stray call must not spawn while off: the queues drain only from tick()
			TownsfolkManager.tick(srv);
			check("townsfolk-killswitch off folds everyone and spawns nobody", live == 0 && TownsfolkManager.liveTotal() == 0, live);
			TownsfolkConfig.enabled = true;
			return 2;
		});

		// ---- B6 cost: 48 Mirewick townsfolk at the canal market -------------------------------------------------------------------
		STEPS.add((srv, l) -> {
			for (String k : List.of("test:aldermark", "test:hrafnholt", "test:azmar", "test:mirewick")) Towns.unregister(k);
			observe(SITES[3]);
			SAMPLES.clear();
			measuring = true;
			STEPS.add(idx, (s2, l2) -> {
				measuring = false;
				baseline = stats()[0];
				town("test:mirewick", MIRE, SITES[3]); // its street graph comes back from the save
				TownsfolkManager.testHour = 9;
				TownsfolkManager.tick(s2);
				for (int i = 0; i < 4; i++) {
					TownsfolkManager.evaluate(l2);
					TownsfolkManager.drain(l2, 200);
				}
				Towns.Town m = Towns.get("test:mirewick");
				info("B6: " + m.live.size() + " Mirewick townsfolk live; baseline " + String.format("%.3f", baseline) + " ms");
				check("townsfolk-b6 Mirewick capital at market shows 48 live", m.live.size() == 48, m.live.size());
				SAMPLES.clear();
				measuring = true;
				STEPS.add(idx, (s3, l3) -> {
					measuring = false;
					double[] st = stats();
					double added = st[0] - baseline;
					check("townsfolk-b6 48 live townsfolk add at most 2.0 ms average MSPT", added <= 2.0, String.format("avg %.3f ms (added %.3f), p95 %.3f, %d ticks", st[0], added, st[1], (int) st[2]));
					Towns.Town mm = Towns.get("test:mirewick");
					boolean walking = mm.live.stream().anyMatch(f -> f.arrived || f.nextWaypoint > 0);
					check("townsfolk-walk residents walk the street graph to their places", walking, null);
					return 0;
				});
				return 400;
			});
			return 100;
		});
	}
}
