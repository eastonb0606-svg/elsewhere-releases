package dev.elsewhere.kingdom.armies.routine;

import dev.elsewhere.kingdom.KingdomLod;
import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.KingdomState;
import dev.elsewhere.kingdom.Squad;
import dev.elsewhere.kingdom.UnitType;
import dev.elsewhere.kingdom.armies.ArmiesConfig;
import dev.elsewhere.kingdom.armies.ArmyRoutine;
import dev.elsewhere.kingdom.armies.ArmyScale;
import dev.elsewhere.kingdom.armies.routine.RoutineEvents.Assignment;
import dev.elsewhere.kingdom.armies.routine.RoutinePlan.Plan;
import dev.elsewhere.kingdom.armies.routine.RoutinePlan.Step;
import dev.elsewhere.kingdom.armies.routine.RoutinePoses.Pose;
import dev.elsewhere.kingdom.war.WarApi;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The routine engine (ARMIES.md 2.4 / 2.6, key {@code routine}): every kingdom's day as data ({@link RoutinePlan}), carried out by
 * its garrison squads and announced to the townsfolk ({@link RoutineEvents}).
 * <ul>
 * <li><b>Event-driven.</b> {@link ArmyRoutine} calls {@link #onHour} when the overworld clock's hour index changes. Per kingdom it
 * fires the hour to the listeners and plays that hour's cues (bells, horns, lights, festival lights); then one walk over the squads
 * gives orders where something changes (watch change, block or event start, end or phase). Everything after that is a small timer
 * queue run every 20 ticks only while it has entries: arrivals (poses and activity spots are set when a squad gets there), the
 * moving activities (drill legs, rank-block turns, tilting and jousting passes, processions, patrols, the mock charge,
 * lamplighters), salutes, lantern waves, and the sweep that drops an activity the moment its squad fights or another order
 * takes it.</li>
 * <li><b>Mirewick's wards</b> change watch off-time: each squad has a stable ward offset of up to 30 in-game minutes before or
 * after the hour ({@link RoutinePlan#wardOffsetMinutes}).</li>
 * <li><b>Out of sight</b> (no player within 64 blocks of the squad or its target) a squad is moved at once, with no pathfinding.</li>
 * </ul>
 */
public final class RoutineDirector {
	private RoutineDirector() {}

	public static long passes, applied, arrived, cycles, lightsToggled, fxSent, cues, soundsPlayed, cleared, wardsEarly, wardsLate, salutes, slept;

	static final int APPLY = 0, ARRIVE = 1, CYCLE = 2, UNSALUTE = 3;

	record Job(long due, UUID squad, int kind, long day, int hour, int n, long token) {}

	record LightJob(long due, List<BlockPos> batch, boolean on) {}

	/** A squad's activity in progress. */
	static final class Active {
		final Assignment a;
		final @Nullable Step step;
		final BlockPos post, anchor;
		final long token, day;
		final int hour;
		BlockPos goal;
		float yaw;
		@Nullable List<RoutineSlots.Spot> spots;

		Active(Assignment a, @Nullable Step step, BlockPos post, BlockPos anchor, long token, long day, int hour) {
			this.a = a;
			this.step = step;
			this.post = post;
			this.anchor = anchor;
			this.goal = anchor;
			this.token = token;
			this.day = day;
			this.hour = hour;
		}
	}

	static final List<Job> JOBS = new ArrayList<>();
	static final List<LightJob> LIGHTS = new ArrayList<>();
	static final Map<UUID, Active> ACTIVE = new HashMap<>();
	private static long tokens;

	/** Called once from {@link ArmyRoutine#init}. */
	public static void init() {
		RoutinePlan.init();
		PayloadTypeRegistry.clientboundPlay().register(RoutineFxPayload.TYPE, RoutineFxPayload.CODEC);
		// a soldier loaded with a routine pose whose squad has no activity now (restart, chunk reload) stands normally again
		ServerEntityEvents.ENTITY_LOAD.register((e, lv) -> {
			if (e instanceof KingdomSoldier ks && RoutinePoses.get(ks) != Pose.NONE) {
				UUID q = ks.squadId();
				if (q == null || !ACTIVE.containsKey(q)) RoutinePoses.clear(ks);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> reset());
		// Mirewick's Pay Day brawl: the war model's treasury when it runs, else a stable roll (RoutineEvents)
		RoutineEvents.registerCondition("treasury_low", (lv, f, day) -> {
			var srv = lv.getServer();
			if (WarApi.heads(srv, f) <= 0) return RoutinePlan.roll(f.hashCode(), day, 77) < 1.0 / 3;
			return WarApi.treasury(srv, f) < 400;
		});
		// Hrafnholt's raid crews board at dusk while the kingdom is at war with anyone
		RoutineEvents.registerCondition("raid_pending", (lv, f, day) -> {
			for (Identifier o : ArmyScale.all().keySet()) if (!o.equals(f) && WarApi.relation(lv.getServer(), f, o) == 2) return true;
			return false;
		});
	}

	public static void reset() {
		JOBS.clear();
		LIGHTS.clear();
		ACTIVE.clear();
		RoutineAnchors.clear();
	}

	/** True while the timer queue or the sweep has work (the tick hook runs {@link #tick20} only then). */
	public static boolean busy() {
		return !JOBS.isEmpty() || !LIGHTS.isEmpty() || !ACTIVE.isEmpty();
	}

	// ---- the hour ---------------------------------------------------------------------------------------------------------------

	/** One pass at an hour change: cues per kingdom, then orders for the squads whose schedule changes now. */
	public static int onHour(ServerLevel lv, long day, int hour) {
		if (!ArmiesConfig.routine || ArmiesConfig.otherSelfTest()) return 0;
		passes++;
		ArmyRoutine.passes++;
		for (Plan p : RoutinePlan.all().values()) cues(lv, p, day, hour);
		if (hour == 0) prune(lv);
		int n = 0;
		long now = lv.getGameTime();
		Map<BlockPos, Integer> crowd = new HashMap<>();
		for (Squad q : new ArrayList<>(KingdomState.get(lv).squads())) {
			Plan p = RoutinePlan.of(q.faction());
			if (p == null || p.blocks().isEmpty()) continue;
			int off = RoutinePlan.wardOffsetMinutes(p, q.id());
			if (off > 0) { // a late ward: changes up to 30 minutes after the hour
				if (RoutinePlan.trigger(p, day, hour)) {
					JOBS.add(new Job(now + off * 1000L / 60, q.id(), APPLY, day, hour, 0, 0));
					wardsLate++;
				}
				continue;
			}
			if (off < 0) { // an early ward: takes the next hour's change up to 30 minutes before it
				long d2 = hour == 23 ? day + 1 : day;
				int h2 = (hour + 1) % 24;
				if (RoutinePlan.trigger(p, d2, h2)) {
					JOBS.add(new Job(now + (60 + off) * 1000L / 60, q.id(), APPLY, d2, h2, 0, 0));
					wardsEarly++;
				}
				continue;
			}
			if (!RoutinePlan.trigger(p, day, hour)) continue;
			if (apply(lv, q, p, day, hour, crowd)) n++;
		}
		return n;
	}

	/** The hour's cues for one kingdom: listeners, the watch-change call, block/event/phase sounds, lights and festival lights. */
	static void cues(ServerLevel lv, Plan p, long day, int hour) {
		RoutineEvents.fire(lv, RoutinePlan.hour(p, lv, day, hour));
		if (p.watch().contains(hour) && p.watchSound() != null) sound(lv, p.faction(), "gate", p.watchSound());
		List<Step> steps = new ArrayList<>(3);
		Step b = RoutinePlan.block(p, hour);
		if (b != null) steps.add(b);
		steps.addAll(RoutinePlan.events(p, lv, day, hour));
		for (Step s : steps) {
			String snd = s.from() == hour || s.hourlySound() ? s.sound() : null;
			for (RoutinePlan.Phase ph : s.phases()) if (ph.at() == hour && ph.sound() != null) snd = ph.sound();
			if (snd != null) sound(lv, p.faction(), s.a().where(), snd);
			if (s.from() == hour) for (RoutinePlan.Light l : s.lights()) lights(lv, p.faction(), l);
			if (s.fx() != null) fx(lv, p.faction(), s.a().where(), s.fx(), s.to() - hour);
		}
		// lights switched at the end of a block or event (e.g. lanterns doused at dawn) are listed with "on": false on the next step
	}

	static void sound(ServerLevel lv, Identifier f, String where, String key) {
		cues++;
		BlockPos at = RoutineAnchors.layout(lv, f, null, where);
		if (at == null) at = RoutineAnchors.layout(lv, f, null, "gate");
		if (at == null || !KingdomLod.observerNear(lv, at.getX(), at.getZ(), 96)) return;
		SoundEvent ev;
		float vol = 2.0F, pitch = 1.0F;
		switch (key) {
			case "bell" -> ev = SoundEvents.BELL_BLOCK;
			case "bellman" -> {
				ev = SoundEvents.BELL_BLOCK;
				vol = 1.0F;
				pitch = 1.4F;
			}
			case "horn" -> {
				ev = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value();
				vol = 4.0F;
			}
			case "war_horn" -> {
				ev = SoundEvents.RAID_HORN.value();
				vol = 4.0F;
			}
			case "chant" -> {
				ev = SoundEvents.AMETHYST_BLOCK_CHIME;
				pitch = 0.6F;
			}
			case "brass" -> ev = SoundEvents.BEACON_ACTIVATE;
			case "cheer" -> {
				ev = SoundEvents.VINDICATOR_CELEBRATE;
				pitch = 1.2F;
			}
			case "shout" -> {
				ev = SoundEvents.VINDICATOR_CELEBRATE;
				pitch = 0.75F;
			}
			case "anvil" -> {
				ev = SoundEvents.ANVIL_LAND;
				vol = 0.6F;
				pitch = 0.8F;
			}
			case "drum" -> ev = SoundEvents.NOTE_BLOCK_BASEDRUM.value();
			default -> {
				return;
			}
		}
		lv.playSound(null, at, ev, SoundSource.NEUTRAL, vol, pitch);
		soundsPlayed++;
	}

	/** Queues a wave of light changes: 4 places every second, nearest the first one first (a lamplighter's round). */
	static void lights(ServerLevel lv, Identifier f, RoutinePlan.Light l) {
		List<BlockPos> all = new ArrayList<>(RoutineAnchors.all(lv, f, l.id()));
		if (all.isEmpty()) return;
		BlockPos first = all.get(0);
		all.sort(Comparator.comparingDouble(p -> p.distSqr(first)));
		long now = lv.getGameTime();
		for (int i = 0; i < all.size(); i += 4) LIGHTS.add(new LightJob(now + 20L * (i / 4), List.copyOf(all.subList(i, Math.min(all.size(), i + 4))), l.on()));
	}

	static void fx(ServerLevel lv, Identifier f, String where, String fx, int hoursLeft) {
		BlockPos at = RoutineAnchors.layout(lv, f, null, where);
		if (at == null) return;
		RoutineFxPayload pl = new RoutineFxPayload(RoutineFxPayload.kindOf(fx), at.getX(), at.getY(), at.getZ(), 40, Math.max(1, Math.min(hoursLeft, 1)) * 1000 + 100);
		for (ServerPlayer sp : lv.players()) {
			if (sp.distanceToSqr(Vec3.atCenterOf(at)) > 160 * 160 || !ServerPlayNetworking.canSend(sp, RoutineFxPayload.TYPE)) continue;
			ServerPlayNetworking.send(sp, pl);
			fxSent++;
		}
	}

	static void prune(ServerLevel lv) {
		RoutineState st = RoutineState.get(lv);
		KingdomState ks = KingdomState.get(lv);
		for (UUID id : new ArrayList<>(st.posts.keySet())) if (ks.squad(id) == null) st.forget(id);
	}

	// ---- one squad --------------------------------------------------------------------------------------------------------------

	static boolean busy(Squad q) {
		return q.isVirtual() || q.isDismissed() || !q.enemies().isEmpty() || q.huntTarget() != null || q.commander() != null;
	}

	/** Mounted, ranged or melee, by the squad's majority (riders count first). */
	static String kind(@Nullable Mob lead, List<KingdomSoldier> mem) {
		int mounted = 0, ranged = 0, melee = 0;
		for (Mob m : ArmyRoutine.all(lead, mem)) {
			if (m.isPassenger() || m instanceof KingdomSoldier ks && ks.role() == UnitType.Role.LANCER) mounted++;
			else if (m instanceof KingdomSoldier ks && ks.role().ranged()) ranged++;
			else melee++;
		}
		if (mounted * 2 >= mounted + ranged + melee && mounted > 0) return "mounted";
		return ranged > melee ? "ranged" : "melee";
	}

	/** Gives one squad its order for {@code hour} of {@code day}. True when it was given one. */
	public static boolean apply(ServerLevel lv, Squad q, Plan p, long day, int hour, Map<BlockPos, Integer> crowd) {
		if (busy(q)) {
			ArmyRoutine.skippedBusy++;
			if (ACTIVE.containsKey(q.id())) clearActivity(lv, q);
			return false;
		}
		Mob lead = q.leaderEntity(lv);
		List<KingdomSoldier> mem = q.memberEntities(lv);
		Mob tagged = lead != null ? lead : mem.isEmpty() ? null : mem.get(0);
		if (tagged == null || !(ArmyRoutine.hasTag(lead, mem, ArmyRoutine.COURT) || ArmyRoutine.hasTag(lead, mem, ArmyRoutine.OPT_IN))) return false;
		RoutineState st = RoutineState.get(lv);
		BlockPos post = ArmyRoutine.readPos(lead, mem);
		if (post == null) post = st.post(q.id());
		BlockPos obj = q.objective();
		if (post == null) {
			if (obj != null) return false; // someone else is giving this squad orders
			post = tagged.blockPosition();
		} else if (obj != null && !obj.equals(post) && !obj.equals(st.goal(q.id())) && !ArmyRoutine.isOurAnchor(post, obj)) {
			return false; // another order (the court's drill loop, a hunt) owns the squad now
		}
		String postTag = ArmyRoutine.POST + post.getX() + "," + post.getY() + "," + post.getZ();
		for (Mob m : ArmyRoutine.all(lead, mem)) if (!m.entityTags().contains(postTag)) m.addTag(postTag);
		RoutinePlan.Choice c = RoutinePlan.choose(p, lv, q.id(), kind(lead, mem), day, hour);
		Assignment a = c.a();
		BlockPos target;
		if (a.activity().equals("watch")) target = post;
		else if (a.activity().equals("sleep")) {
			BlockPos l = RoutineAnchors.layout(lv, q.faction(), post, a.where());
			BlockPos s = l == null ? null : RoutineAnchors.settle(lv, l, 4);
			target = s != null ? s : post;
		} else {
			target = RoutineAnchors.resolve(lv, q.faction(), post, a.where(), a.activity());
			if (!target.equals(post)) {
				int k = crowd.merge(target, 1, Integer::sum) - 1;
				BlockPos spread = RoutineSlots.spread(target, q.id(), k);
				BlockPos s = k == 0 ? target : RoutineAnchors.settle(lv, spread, 3);
				if (s != null) target = s;
			}
		}
		// leave the previous activity: stand up, wake, drop the spots and poses
		if (ACTIVE.containsKey(q.id())) clearActivity(lv, q);
		boolean anyResting = false;
		for (KingdomSoldier s : mem) anyResting |= s.isResting();
		if (lead instanceof KingdomSoldier lk && lk.isResting()) anyResting = true;
		if (anyResting && !a.activity().equals("sleep")) q.wake(lv);
		formation(q, lead, mem, a);
		q.setObjective(target);
		st.set(q.id(), post, target);
		if (target.equals(post)) ArmyRoutine.returned++;
		else ArmyRoutine.moved++;
		applied++;
		boolean jumped = false;
		if (!KingdomLod.observerNear(lv, tagged.getX(), tagged.getZ(), 64) && !KingdomLod.observerNear(lv, target.getX(), target.getZ(), 64)
			&& tagged.blockPosition().distManhattan(target) > 3) {
			int i = 0;
			for (Mob m : ArmyRoutine.all(lead, mem)) {
				m.getNavigation().stop();
				m.teleportTo(target.getX() + 0.5 + (i % 4) - 1.5, target.getY(), target.getZ() + 0.5 + (i / 4) - 1.0);
				i++;
			}
			ArmyRoutine.teleported++;
			jumped = true;
		}
		long token = ++tokens;
		Active ac = new Active(a, c.step(), post, target, token, day, hour);
		ac.yaw = facing(lv, q.faction(), ac);
		ACTIVE.put(q.id(), ac);
		double dist = Math.sqrt(tagged.blockPosition().distSqr(target));
		long due = lv.getGameTime() + (jumped ? 10 : 40 + (long) (dist * 6));
		JOBS.add(new Job(due, q.id(), ARRIVE, day, hour, 0, token));
		return true;
	}

	/** Drill flips LINE / SHIELD_WALL for the block (the v1 rule), marching activities walk in COLUMN; both are restored afterwards. */
	static void formation(Squad q, @Nullable Mob lead, List<KingdomSoldier> mem, Assignment a) {
		String form = ArmyRoutine.readTag(lead, mem, ArmyRoutine.FORM);
		boolean drill = a.activity().equals("drill") && !a.style().equals("rank_blocks") && !a.style().equals("half_hearted");
		boolean column = a.activity().equals("procession") || a.activity().equals("patrol") || a.activity().equals("caravan");
		if ((drill || column) && form == null) {
			for (Mob m : ArmyRoutine.all(lead, mem)) m.addTag(ArmyRoutine.FORM + q.formation().name());
			if (column) q.setFormation(Squad.Formation.COLUMN);
			else q.setFormation(q.formation() == Squad.Formation.SHIELD_WALL ? Squad.Formation.LINE : Squad.Formation.SHIELD_WALL);
		} else if (form != null && !(drill || column)) {
			try {
				q.setFormation(Squad.Formation.valueOf(form));
			} catch (IllegalArgumentException ignored) {
			}
			for (Mob m : ArmyRoutine.all(lead, mem)) m.removeTag(ArmyRoutine.FORM + form);
		}
	}

	/** Which way the activity faces: towards the layout's focus ({@code <where>_focus}) if it has one, else the way the squad came. */
	static float facing(ServerLevel lv, Identifier f, Active ac) {
		BlockPos focus = RoutineAnchors.layout(lv, f, ac.post, ac.a.where() + "_focus");
		if (focus != null && focus.distSqr(ac.anchor) > 4) return RoutineSlots.yawTo(ac.anchor.getX(), ac.anchor.getZ(), focus.getX(), focus.getZ());
		if (ac.post.distSqr(ac.anchor) > 4) return RoutineSlots.yawTo(ac.post.getX(), ac.post.getZ(), ac.anchor.getX(), ac.anchor.getZ());
		return 0;
	}

	static void clearActivity(ServerLevel lv, Squad q) {
		Active ac = ACTIVE.remove(q.id());
		q.activitySpots = null;
		q.activityYaws = null;
		if (q.isVirtual()) return;
		for (Mob m : ArmyRoutine.all(q.leaderEntity(lv), q.memberEntities(lv))) RoutinePoses.clear(m);
		if (ac != null) cleared++;
	}

	static void order(ServerLevel lv, Squad q, Active ac, BlockPos goal) {
		ac.goal = goal;
		q.setObjective(goal);
		RoutineState.get(lv).set(q.id(), ac.post, goal);
	}

	// ---- the timer queue (every 20 ticks, only while it has entries) ----------------------------------------------------------

	public static void tick20(ServerLevel lv) {
		long now = lv.getGameTime();
		if (!JOBS.isEmpty()) {
			List<Job> due = new ArrayList<>();
			for (Iterator<Job> it = JOBS.iterator(); it.hasNext();) {
				Job j = it.next();
				if (j.due() <= now) {
					due.add(j);
					it.remove();
				}
			}
			for (Job j : due) run(lv, j);
		}
		if (!LIGHTS.isEmpty()) for (Iterator<LightJob> it = LIGHTS.iterator(); it.hasNext();) {
			LightJob l = it.next();
			if (l.due() > now) continue;
			it.remove();
			for (BlockPos p : l.batch()) {
				if (!lv.isLoaded(p)) continue;
				BlockState s = lv.getBlockState(p);
				if (s.hasProperty(BlockStateProperties.LIT) && s.getValue(BlockStateProperties.LIT) != l.on()) {
					lv.setBlock(p, s.setValue(BlockStateProperties.LIT, l.on()), Block.UPDATE_ALL);
					lightsToggled++;
				}
			}
		}
		if (!ACTIVE.isEmpty()) sweep(lv);
	}

	/** Drops activities whose squad fights, went virtual, was disbanded or got another order. */
	static void sweep(ServerLevel lv) {
		KingdomState ks = KingdomState.get(lv);
		for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
			Squad q = ks.squad(id);
			Active ac = ACTIVE.get(id);
			if (q == null) {
				ACTIVE.remove(id);
				continue;
			}
			if (busy(q) || q.objective() == null || !q.objective().equals(ac.goal)) clearActivity(lv, q);
		}
	}

	static void run(ServerLevel lv, Job j) {
		KingdomState ks = KingdomState.get(lv);
		Squad q = ks.squad(j.squad());
		if (q == null) {
			ACTIVE.remove(j.squad());
			return;
		}
		if (j.kind() == APPLY) {
			Plan p = RoutinePlan.of(q.faction());
			if (p != null && ArmiesConfig.routine) apply(lv, q, p, j.day(), j.hour(), new HashMap<>());
			return;
		}
		Active ac = ACTIVE.get(q.id());
		if (ac == null || ac.token != j.token()) return;
		if (busy(q)) {
			clearActivity(lv, q);
			return;
		}
		switch (j.kind()) {
			case ARRIVE -> arrive(lv, q, ac, j);
			case CYCLE -> cycle(lv, q, ac, j);
			case UNSALUTE -> {
				for (Mob m : ArmyRoutine.all(q.leaderEntity(lv), q.memberEntities(lv))) RoutinePoses.set(m, ac.a.pose());
			}
			default -> {
			}
		}
	}

	static void arrive(ServerLevel lv, Squad q, Active ac, Job j) {
		Mob lead = q.leaderEntity(lv);
		List<KingdomSoldier> mem = q.memberEntities(lv);
		Mob ref = lead != null ? lead : mem.isEmpty() ? null : mem.get(0);
		if (ref == null) return;
		long now = lv.getGameTime();
		if (ref.blockPosition().distManhattan(ac.goal) > 5 && j.n() < 6) {
			JOBS.add(new Job(now + 100, q.id(), ARRIVE, j.day(), j.hour(), j.n() + 1, ac.token));
			return;
		}
		arrived++;
		Assignment a = ac.a;
		List<Mob> all = ArmyRoutine.all(lead, mem);
		switch (a.activity()) {
			case "watch" -> {
				Plan p = RoutinePlan.of(q.faction());
				boolean change = p != null && p.watch().contains(ac.hour);
				for (Mob m : all) RoutinePoses.set(m, change ? Pose.SALUTE : a.pose());
				if (change) {
					salutes++;
					JOBS.add(new Job(now + 100, q.id(), UNSALUTE, j.day(), j.hour(), 0, ac.token));
				}
				return;
			}
			case "sleep" -> {
				for (Mob m : all) if (m instanceof KingdomSoldier ks) ks.setResting(true);
				slept++;
				return;
			}
			default -> {
			}
		}
		if (moving(a)) {
			for (Mob m : all) RoutinePoses.set(m, a.pose());
		} else place(lv, q, ac, lead, mem);
		int period = period(a);
		if (period > 0) JOBS.add(new Job(now + period, q.id(), CYCLE, j.day(), j.hour(), 1, ac.token));
	}

	/** Activities done on the move (formation, no fixed spots). */
	static boolean moving(Assignment a) {
		String s = a.style(), act = a.activity();
		if (act.equals("drill")) return !(s.equals("rank_blocks") || s.equals("half_hearted") || s.equals("butts") || s.equals("quintain") || s.equals("inspection"));
		return act.equals("procession") || act.equals("patrol") || s.equals("mock_charge") || s.equals("linkboys") || s.equals("depart");
	}

	static int period(Assignment a) {
		return switch (a.style()) {
			case "rank_blocks" -> 200;
			case "quintain", "joust" -> 160;
			case "mock_charge" -> 300;
			case "linkboys" -> 120;
			case "half_hearted" -> 400;
			default -> switch (a.activity()) {
				case "drill" -> 300;
				case "procession" -> 240;
				case "patrol" -> 600;
				default -> 0;
			};
		};
	}

	/** Puts the squad into its activity's shape: leader at spot 0, members on the rest, each with its pose. */
	static void place(ServerLevel lv, Squad q, Active ac, @Nullable Mob lead, List<KingdomSoldier> mem) {
		int n = mem.size() + 1;
		List<RoutineSlots.Spot> spots = new ArrayList<>(RoutineSlots.layout(ac.a.activity(), ac.a.style(), ac.a.pose(), ac.anchor, ac.yaw, n, q.id()));
		for (int i = 0; i < spots.size(); i++) {
			RoutineSlots.Spot s = spots.get(i);
			BlockPos b = RoutineAnchors.settle(lv, BlockPos.containing(s.pos()), 2);
			if (b == null) spots.set(i, new RoutineSlots.Spot(Vec3.atBottomCenterOf(ac.anchor), s.yaw(), s.pose()));
			else if (b.getY() != (int) Math.floor(s.pos().y)) spots.set(i, new RoutineSlots.Spot(new Vec3(s.pos().x, b.getY(), s.pos().z), s.yaw(), s.pose()));
		}
		ac.spots = spots;
		order(lv, q, ac, BlockPos.containing(spots.get(0).pos()));
		List<Vec3> ps = new ArrayList<>(mem.size());
		float[] ys = new float[mem.size()];
		for (int k = 0; k < mem.size(); k++) {
			RoutineSlots.Spot s = spots.get(k + 1);
			ps.add(s.pos());
			ys[k] = s.yaw();
			RoutinePoses.set(mem.get(k), s.pose());
		}
		q.activitySpots = ps;
		q.activityYaws = ys;
		if (lead != null) {
			lead.setYRot(spots.get(0).yaw());
			lead.setYHeadRot(spots.get(0).yaw());
			RoutinePoses.set(lead, spots.get(0).pose());
		}
	}

	/** The moving activities: one step per period while the activity lasts. */
	static void cycle(ServerLevel lv, Squad q, Active ac, Job j) {
		cycles++;
		int leg = j.n();
		Assignment a = ac.a;
		double rad = Math.toRadians(ac.yaw);
		int fx = (int) Math.round(-Math.sin(rad)), fz = (int) Math.round(Math.cos(rad));
		int rx = -fz, rz = fx;
		Mob lead = q.leaderEntity(lv);
		List<KingdomSoldier> mem = q.memberEntities(lv);
		switch (a.style()) {
			case "rank_blocks" -> { // the blocks turn in unison
				ac.yaw += 90;
				place(lv, q, ac, lead, mem);
			}
			case "quintain", "joust" -> { // riders pass each other between the ends of the lists; the rest watch
				if (ac.spots != null && q.activitySpots != null) {
					List<Vec3> ps = new ArrayList<>(q.activitySpots);
					int rider = 0;
					for (int k = 0; k < mem.size() && k < ps.size(); k++) {
						if (!mem.get(k).isPassenger()) continue;
						int end = ((leg + rider) & 1) == 0 ? 10 : -10;
						ps.set(k, Vec3.atBottomCenterOf(ac.anchor.offset(fx * end + rx * (rider * 2 - 1), 0, fz * end + rz * (rider * 2 - 1))));
						rider++;
					}
					q.activitySpots = ps;
				}
				if (KingdomLod.observerNear(lv, ac.anchor.getX(), ac.anchor.getZ(), 48)) sound(lv, q.faction(), a.where(), "cheer");
			}
			case "half_hearted" -> sound(lv, q.faction(), a.where(), "shout"); // the reeve shouts
			case "mock_charge" -> {
				BlockPos far = RoutineAnchors.settle(lv, ac.anchor.offset(fx * 18, 0, fz * 18), 4);
				order(lv, q, ac, (leg & 1) == 1 && far != null ? far : ac.anchor);
				q.setFormation(Squad.Formation.LINE);
			}
			case "linkboys" -> { // walk the lantern round
				List<BlockPos> posts = RoutineAnchors.all(lv, q.faction(), "lantern_posts");
				BlockPos next = posts.isEmpty() ? ac.anchor.offset(rx * ((leg % 3) - 1) * 8, 0, rz * ((leg % 3) - 1) * 8) : posts.get((leg * 4) % posts.size());
				BlockPos s = RoutineAnchors.settle(lv, next, 4);
				if (s != null) order(lv, q, ac, s);
			}
			default -> {
				BlockPos next;
				if (a.activity().equals("procession")) { // up the route and back
					int[] k = {0, 12, 24, 12};
					int d = k[leg % 4];
					next = ac.anchor.offset(fx * d, 0, fz * d);
				} else if (a.activity().equals("patrol")) { // out along the road and back
					BlockPos far = RoutineAnchors.layout(lv, q.faction(), ac.post, a.where() + "_far");
					next = (leg & 1) == 1 ? far != null ? far : ac.anchor.offset(fx * 24, 0, fz * 24) : ac.anchor;
				} else { // drill: a square of 6 (march, wheel, march), the formation flipping on every leg
					int[][] c = {{0, 0}, {6, 0}, {6, 6}, {0, 6}};
					int[] o = c[leg % 4];
					next = ac.anchor.offset(fx * o[0] + rx * o[1], 0, fz * o[0] + rz * o[1]);
					q.setFormation(q.formation() == Squad.Formation.SHIELD_WALL ? Squad.Formation.LINE : Squad.Formation.SHIELD_WALL);
				}
				BlockPos s = RoutineAnchors.settle(lv, next, 4);
				if (s != null) order(lv, q, ac, s);
			}
		}
		int period = period(a);
		if (period > 0) JOBS.add(new Job(lv.getGameTime() + period, q.id(), CYCLE, j.day(), j.hour(), leg + 1, ac.token));
	}
}
