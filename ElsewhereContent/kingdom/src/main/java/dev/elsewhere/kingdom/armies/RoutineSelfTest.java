package dev.elsewhere.kingdom.armies;

import dev.elsewhere.kingdom.KingdomApi;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitTypes;
import dev.elsewhere.kingdom.armies.routine.RoutineAnchors;
import dev.elsewhere.kingdom.armies.routine.RoutineDirector;
import dev.elsewhere.kingdom.armies.routine.RoutineEvents;
import dev.elsewhere.kingdom.armies.routine.RoutinePlan;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;

/**
 * Self-test checks for the per-kingdom routines (s-routines), run inside the armies self-test ({@code -Delsewhere.armies.selftest=true}):
 * the four day schedules as data (every activity, watch changes, events, wards), then Aldermark squads carrying out a muster, the
 * work block, a watch-change salute, the night (rest or torch), the lantern wave, the townsfolk hour hook, the combat sweep and the
 * kill switch. The rival kingdoms' data checks run where their modules are loaded (the merged self-test).
 */
final class RoutineSelfTest {
	private RoutineSelfTest() {}

	static final Identifier A = dev.elsewhere.kingdom.Factions.ALDERMARK;
	static final Identifier H = Identifier.fromNamespaceAndPath("elsewhere", "hrafnholt"), Z = Identifier.fromNamespaceAndPath("elsewhere", "azmar"),
		M = Identifier.fromNamespaceAndPath("elsewhere", "mirewick");
	static final List<Squad> SQ = new ArrayList<>();
	static final List<BlockPos> POSTS = new ArrayList<>();
	static final List<RoutineEvents.Hour> HEARD = new ArrayList<>();
	static final RoutineEvents.Listener EAR = (lv, h) -> HEARD.add(h);
	static BlockPos field, fields;
	static final List<BlockPos> LANTERNS = new ArrayList<>();

	static void check(String name, boolean ok, Object detail) {
		ArmiesSelfTest.check("routines: " + name, ok, detail);
	}

	static void build() {
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			data();
			return 1;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			ArmiesSelfTest.clean(l);
			RoutineDirector.resetForTests();
			SQ.clear();
			POSTS.clear();
			for (int i = 0; i < 6; i++) {
				BlockPos p = ArmiesSelfTest.at(-40 + i * 14, -30);
				List<KingdomSoldier> s = KingdomApi.spawnSquad(l, p, A, Squad.Formation.LINE, UnitTypes.SERGEANT, List.of(UnitTypes.MAN_AT_ARMS, UnitTypes.MAN_AT_ARMS, UnitTypes.ARCHER));
				if (s.isEmpty() || s.get(0).squad() == null) continue;
				s.forEach(x -> x.addTag(ArmyRoutine.OPT_IN));
				SQ.add(s.get(0).squad());
				POSTS.add(s.get(0).blockPosition());
			}
			field = ArmiesSelfTest.at(0, 20);
			fields = ArmiesSelfTest.at(30, 0);
			RoutineAnchors.put(l, A, "tilting_field", field);
			RoutineAnchors.put(l, A, "fields", fields);
			HEARD.clear();
			RoutineEvents.register(EAR);
			ArmyRoutine.onHour(l, 7, 10); // Muster Day (every 7 days) at 10:00: the whole levy forms ranks on the tilting field
			return 50;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			int ranked = 0, attention = 0, near = 0;
			Set<BlockPos> goals = new HashSet<>();
			for (Squad q : SQ) {
				if (q.activitySpots != null) ranked++;
				Mob lead = q.leaderEntity(l);
				if (lead != null && lead.blockPosition().distManhattan(field) <= 16) near++;
				if (q.objective() != null) goals.add(q.objective());
				for (KingdomSoldier s : q.memberEntities(l)) if (RoutinePoses.get(s) == Pose.ATTENTION) attention++;
			}
			check("Muster Day: every squad forms ranks at the tilting field anchor with spots and the attention pose", ranked == SQ.size() && near == SQ.size()
				&& attention >= SQ.size() * 2, ranked + " ranked, " + near + " near, " + attention + " at attention of " + SQ.size() + " squads");
			check("squads sent to one anchor spread out (no two share a spot)", goals.size() == SQ.size(), goals.size() + " goals");
			RoutineEvents.Hour h = HEARD.stream().filter(x -> x.faction().equals(A)).findFirst().orElse(null);
			check("townsfolk hear the hour: Aldermark 10:00 on Muster Day is muster_day with a crowd at the tilting field", h != null
				&& "muster_day".equals(h.eventId()) && h.townsfolk().stream().anyMatch(a -> a.where().equals("tilting_field")), h);
			// the combat sweep: a squad that gets another order stops its activity at once
			Squad busy = SQ.get(0);
			busy.setCommander(SQ.get(1).leaderEntity(l));
			return 30;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			Squad busy = SQ.get(0);
			boolean posesOff = true;
			for (KingdomSoldier s : busy.memberEntities(l)) posesOff &= RoutinePoses.get(s) == Pose.NONE;
			check("a squad given another order leaves its activity (spots dropped, poses cleared)", busy.activitySpots == null && posesOff, busy.activitySpots);
			busy.setCommander(null);
			busy.setObjective(null);
			ArmyRoutine.onHour(l, 1, 13); // an ordinary day at 13:00: work in the fields (share 0.5); the rest stand watch at their posts
			return 90;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			int working = 0, posted = 0, other = 0, bad = 0;
			RoutinePlan.Plan ap = RoutinePlan.of(A);
			for (int i = 0; i < SQ.size(); i++) {
				Squad q = SQ.get(i);
				Mob lead = q.leaderEntity(l);
				if (lead == null || ap == null) continue;
				String want = RoutinePlan.choose(ap, l, q.id(), "melee", 1, 13).a().activity();
				if (want.equals("work")) {
					if (lead.blockPosition().distManhattan(fields) <= 16) working++;
					else bad++;
					for (KingdomSoldier s : q.memberEntities(l)) if (RoutinePoses.get(s) != Pose.WORK) bad++;
				} else if (want.equals("watch")) {
					if (lead.blockPosition().distManhattan(POSTS.get(i)) <= 5) posted++;
					else bad++;
				} else if (q.objective() != null && !q.objective().equals(POSTS.get(i))) other++; // the daily road patrol (8-16)
				else bad++;
			}
			check("13:00 work block: each squad works the fields (work pose), stands at its post or is out on the road patrol, as its roll says",
				working + posted + other == SQ.size() && bad == 0, working + " working, " + posted + " posted, " + other + " on patrol, " + bad + " wrong");
			ArmyRoutine.onHour(l, 1, 12); // 12:00 watch change (the formal changing of the guard): the posted squads salute
			return 85;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			int salute = 0, posted = 0;
			for (Squad q : SQ) {
				Mob lead = q.leaderEntity(l);
				if (lead == null || q.objective() == null || !RoutineDirectorAccess.atPost(q, l)) continue;
				posted++;
				if (RoutinePoses.get(lead) == Pose.SALUTE) salute++;
			}
			check("12:00 changing of the guard: every squad at its post salutes", posted > 0 && salute == posted, salute + " of " + posted);
			ArmyRoutine.onHour(l, 1, 22); // 22:00: sleep (share 0.67), the rest keep the night watch with torches
			return 90;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			int ok = 0, all = 0;
			for (Squad q : SQ) for (Mob m : ArmyRoutine.all(q.leaderEntity(l), q.memberEntities(l))) {
				all++;
				Pose p = RoutinePoses.get(m);
				if (m instanceof KingdomSoldier ks && ks.isResting() || p == Pose.TORCH || p == Pose.SALUTE) ok++;
			}
			check("22:00: every soldier either sleeps (resting) or keeps the night watch (torch, after the watch-change salute)", all > 0 && ok == all, ok + " of " + all);
			// the lamplighters' wave: lantern posts registered by a layout are lit 4 a second
			LANTERNS.clear();
			for (int i = 0; i < 6; i++) {
				BlockPos p = ArmiesSelfTest.at(-20 + i * 3, 40).above();
				l.setBlock(p, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
				LANTERNS.add(p);
			}
			RoutineAnchors.putAll(l, A, "lantern_posts", LANTERNS);
			RoutineDirector.lights(l, A, new RoutinePlan.Light("lantern_posts", true));
			return 60;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			int lit = 0;
			for (BlockPos p : LANTERNS) if (l.getBlockState(p).hasProperty(CampfireBlock.LIT) && l.getBlockState(p).getValue(CampfireBlock.LIT)) lit++;
			check("lamplight: the registered lantern posts are lit in a wave (batched block changes)", lit == LANTERNS.size(), lit + " of " + LANTERNS.size());
			for (BlockPos p : LANTERNS) l.removeBlock(p, false);
			// kill switch: with routine off an hour change gives no orders
			ArmiesConfig.routine = false;
			long before = ArmyRoutine.moved + ArmyRoutine.returned;
			int n = ArmyRoutine.onHour(l, 7, 10);
			ArmiesConfig.routine = true;
			check("kill switch routine=false: an hour change gives no orders", n == 0 && ArmyRoutine.moved + ArmyRoutine.returned == before, n);
			RoutineEvents.unregister(EAR);
			ArmiesSelfTest.clean(l);
			RoutineDirector.resetForTests();
			return 25;
		});
		ArmiesSelfTest.STEPS.add((srv, l) -> {
			check("nothing left queued once the squads are gone (no per-tick work when idle)", !RoutineDirector.busy(), null);
			return 1;
		});
	}

	/** Pure data checks of the four day schedules. */
	static void data() {
		check("routine detail parsed without errors", RoutinePlan.parseErrors().isEmpty(), RoutinePlan.parseErrors());
		for (RoutinePlan.Plan p : RoutinePlan.all().values()) check("valid day schedule: " + p.faction(), RoutinePlan.problems(p).isEmpty(), RoutinePlan.problems(p));
		RoutinePlan.Plan a = RoutinePlan.of(A), h = RoutinePlan.of(H), z = RoutinePlan.of(Z), m = RoutinePlan.of(M);
		check("Aldermark: bell kingdom watches at 06, 12, 18, 22 and 02; drill at 07, market at 10, sleep at 23", a != null
			&& a.watch().equals(List.of(6, 12, 18, 22, 2)) && act(a, 7).equals("drill") && act(a, 10).equals("market") && act(a, 23).equals("sleep"), a == null ? null : a.watch());
		check("Aldermark: triggers only at changes (06:00 yes, 07:00 no on an ordinary day)", a != null && RoutinePlan.trigger(a, 1, 6) && !RoutinePlan.trigger(a, 1, 7), null);
		check("Aldermark calendar: Muster Day every 7 days at 10 (mock charge at 11), Harvest Fair and Tourney every 8 at 9", a != null
			&& ids(a, 7, 10).contains("muster_day") && ids(a, 8, 9).contains("harvest_fair") && !ids(a, 1, 10).contains("muster_day")
			&& RoutinePlan.assignment(event(a, "muster_day"), "melee", 11).style().equals("mock_charge"), null);
		if (h == null || z == null || m == null) {
			ArmiesSelfTest.info("routines: rival kingdoms not loaded in this run (" + RoutinePlan.all().keySet() + "); their schedules are checked in the merged self-test");
			return;
		}
		check("Hrafnholt: two watches, at 06 and 18; spar at 09, feast at 18, holmgang at 15", h.watch().equals(List.of(6, 18)) && act(h, 9).equals("spar")
			&& act(h, 18).equals("feast") && act(h, 15).equals("spar"), h.watch());
		check("Azmar: watches every 3 hours; grand drill at 07, siesta 11-15, Grand Bazaar 15-20", z.watch().equals(List.of(0, 3, 6, 9, 12, 15, 18, 21))
			&& act(z, 7).equals("drill") && act(z, 12).equals("siesta") && act(z, 16).equals("market") && !act(z, 15).equals("siesta"), z.watch());
		check("Mirewick: wards change off-time around 07 and 19 (jitter 30); linkboys light the canals at 18", m.watch().equals(List.of(7, 19))
			&& m.jitter() == 30 && act(m, 18).equals("lamplight"), m.watch());
		int lo = 0, hi = 0, out = 0, ald = 0;
		for (int i = 0; i < 64; i++) {
			UUID id = new UUID(i * 0x9E3779B97F4A7C15L, i * 31L + 7);
			int off = RoutinePlan.wardOffsetMinutes(m, id);
			if (off < -10) lo++;
			if (off > 10) hi++;
			if (Math.abs(off) > 30) out++;
			if (RoutinePlan.wardOffsetMinutes(a, id) != 0) ald++;
		}
		check("Mirewick's wards: some change up to 30 minutes early, some late, none beyond; Aldermark's change on the hour", lo > 0 && hi > 0 && out == 0 && ald == 0,
			lo + " early, " + hi + " late, " + out + " out of range, " + ald + " Aldermark off-time");
		Set<String> acts = new HashSet<>();
		for (RoutinePlan.Plan p : List.of(a, h, z, m)) {
			List<RoutinePlan.Step> steps = new ArrayList<>(p.blocks());
			steps.addAll(p.events());
			for (RoutinePlan.Step s : steps) {
				acts.add(s.a().activity());
				s.groups().forEach(g -> acts.add(g.a().activity()));
				s.phases().forEach(ph -> acts.add(ph.a().activity()));
				if (s.rest() != null) acts.add(s.rest().activity());
			}
		}
		List<String> need = List.of("drill", "market", "work", "court", "temple", "feast", "spar", "tourney", "caravan", "lamplight", "muster", "procession", "siesta");
		List<String> missing = need.stream().filter(x -> !acts.contains(x)).toList();
		check("all 13 activities are scheduled across the four kingdoms", missing.isEmpty(), missing);
		List<String> days = new ArrayList<>();
		for (RoutinePlan.Plan p : List.of(a, h, z, m)) {
			StringBuilder b = new StringBuilder();
			for (int hr = 0; hr < 24; hr++) b.append(act(p, hr)).append(',');
			days.add(b.toString());
		}
		check("each kingdom's day is its own (the four hour-by-hour schedules differ)", new HashSet<>(days).size() == 4, null);
		check("calendars: Thing (6 days) and Blót (9), Sun Procession (5), Review of the Ranks (7), Festival of the Flood (10), Pay Day (7), Lantern Fair (8)",
			ids(h, 6, 14).contains("thing") && ids(h, 9, 19).contains("blot") && ids(z, 5, 12).contains("sun_procession") && ids(z, 7, 8).contains("review_of_the_ranks")
				&& ids(z, 10, 19).contains("flood_festival") && ids(m, 7, 9).contains("pay_day") && ids(m, 8, 19).contains("lantern_fair"), null);
		check("Azmar caravans arrive at 16:00 and leave at 19:00", ids(z, 1, 16).contains("caravan")
			&& RoutinePlan.assignment(event(z, "caravan"), "melee", 19).style().equals("depart"), null);
		check("festival lights go to clients: Lantern Fair, Festival of the Flood and the Blót carry fx", event(m, "lantern_fair").fx() != null
			&& event(z, "flood_festival").fx() != null && event(h, "blot").fx() != null, null);
		check("Hrafnholt's raid crews board at dusk only while a raid is pending", "raid_pending".equals(event(h, "night_raid").cond()), null);
	}

	static String act(RoutinePlan.Plan p, int hour) {
		RoutinePlan.Step b = RoutinePlan.block(p, hour);
		return b == null ? "watch" : b.a().activity();
	}

	static List<String> ids(RoutinePlan.Plan p, long day, int hour) {
		return RoutinePlan.events(p, null, day, hour).stream().map(RoutinePlan.Step::id).toList();
	}

	static RoutinePlan.Step event(RoutinePlan.Plan p, String id) {
		for (RoutinePlan.Step e : p.events()) if (e.id().equals(id)) return e;
		throw new IllegalStateException("no event " + id + " in " + p.faction());
	}

	/** Small read helper so the checks above stay readable. */
	static final class RoutineDirectorAccess {
		static boolean atPost(Squad q, ServerLevel l) {
			BlockPos post = ArmyRoutine.readPos(q.leaderEntity(l), q.memberEntities(l));
			return post != null && post.equals(q.objective());
		}
	}
}
