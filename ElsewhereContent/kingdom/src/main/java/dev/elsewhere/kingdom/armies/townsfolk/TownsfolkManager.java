package dev.elsewhere.kingdom.armies.townsfolk;

import dev.elsewhere.kingdom.Serfs;
import dev.elsewhere.kingdom.Standing;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Townsfolk level of detail (ARMIES.md 2.5, 3.10): every town keeps a logical resident count ({@link TownsfolkState}; capitals
 * 160/180/120/220, towns 50/60/36/80 for Aldermark/Hrafnholt/Azmar/Mirewick); only a few of them are live entities, near a player.
 * <ul>
 * <li><b>Event-driven.</b> Work happens when (a) a player crosses a chunk border, joins or leaves (the player list is compared once a
 * second; no entity or block scans), (b) the in-game hour changes (one long compare per tick, as ArmyRoutine), (c) a townsperson is
 * hurt, dies or is removed. Spawns and street-graph builds drain small queues that exist only while they have entries.</li>
 * <li><b>Live counts.</b> Within {@link #SPAWN_RADIUS} of a player a town shows {@code min(cap, logical x share outdoors this hour)}:
 * the share comes from the role schedules and the routine engine's assignments (events bring people out). Caps: the kingdom's live
 * townsfolk near one player (40/40/32/48 at a capital, 18/18/14/20 in a town), {@link #AREA_CAP} per town area, {@link #SERVER_CAP}
 * per server. At most {@link #SPAWNS_PER_TICK} spawn per tick (no mass pop), at doors or out of the player's near view.</li>
 * <li><b>Folding.</b> Beyond {@link #FOLD_RADIUS} of every player (out of their tracking range) a townsperson folds back into the count
 * at once; surplus people (evening, siesta) walk indoors and vanish at a door. Deaths lower the count (Standing loss as for a Serf);
 * it refills by 1 per in-game day.</li>
 * </ul>
 */
public final class TownsfolkManager {
	private TownsfolkManager() {}

	public static final int SPAWN_RADIUS = 48, FOLD_RADIUS = 72, AREA_CAP = 48, SERVER_CAP = 144, SPAWNS_PER_TICK = 2;
	/** Graph-build budget per tick (the whole build of a town costs about 1-2 ms, spread over several ticks). */
	static final long BUILD_BUDGET_NANOS = 400_000L;
	/** Test observers (positions treated like players), as {@code KingdomPerf.testObservers}. */
	public static final List<Vec3> testObservers = new ArrayList<>();
	public static @Nullable EntityType<Townsfolk> TYPE;

	// counters (self-test, /kingdom townsfolk)
	public static long evaluations, spawned, foldedFar, foldedHome, foldedOther, replans, deaths, graphsBuilt, chunkEvents, hourEvents;
	public static double lastBuildMs, lastBuildMaxStepMs;

	private static long lastHour = Long.MIN_VALUE;
	private static boolean dirty;
	private static final Map<UUID, Long> PLAYER_CHUNKS = new HashMap<>();
	private static final Map<String, Integer> PENDING = new LinkedHashMap<>();
	private static final Map<String, StreetGraph.Builder> BUILDS = new LinkedHashMap<>();

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(TownsfolkManager::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(srv -> reset());
	}

	static void reset() {
		lastHour = Long.MIN_VALUE;
		dirty = false;
		PLAYER_CHUNKS.clear();
		PENDING.clear();
		BUILDS.clear();
		testObservers.clear();
		testHour = -1;
		testDay = -1;
		TownsfolkRoutineLink.clearCache();
		Towns.reset();
	}

	/** Self-test clock override (-1 = the world clock). */
	static int testHour = -1;
	static long testDay = -1;

	public static long day(ServerLevel lv) {
		return testDay >= 0 ? testDay : Math.floorDiv(lv.getOverworldClockTime(), 24000L);
	}

	/** In-game hour 0-23 (tick 0 is 06:00). */
	public static int hour(ServerLevel lv) {
		return testHour >= 0 ? testHour : (int) Math.floorMod(Math.floorDiv(lv.getOverworldClockTime(), 1000L) + 6, 24L);
	}

	/** A number that changes exactly when the in-game hour does. */
	static long hourIndex(ServerLevel lv) {
		return testHour >= 0 ? testDay * 24 + testHour : Math.floorDiv(lv.getOverworldClockTime(), 1000L);
	}

	private static boolean failed;

	static void tick(MinecraftServer srv) {
		try {
			tickInner(srv);
		} catch (RuntimeException e) {
			// never take the server down for townsfolk: fold them and log once; the next event tries again
			if (!failed) dev.elsewhere.kingdom.KingdomContent.LOG.error("Townsfolk tick failed; townsfolk folded", e);
			failed = true;
			foldAll("error");
		}
	}

	static void tickInner(MinecraftServer srv) {
		ServerLevel lv = srv.overworld();
		if (!TownsfolkConfig.active()) {
			if (liveTotal() > 0 || !PENDING.isEmpty()) foldAll("switched off");
			return;
		}
		long h = hourIndex(lv);
		if (h != lastHour) {
			boolean first = lastHour == Long.MIN_VALUE;
			lastHour = h;
			if (!first) onHour(lv);
			dirty = true;
		}
		if (srv.getTickCount() % 20 == 13 && playersMoved(lv)) dirty = true;
		if (dirty && srv.getTickCount() % 20 == 13) evaluate(lv);
		if (!BUILDS.isEmpty()) runBuilds(lv);
		if (!PENDING.isEmpty()) runSpawns(lv);
	}

	/** True when a player crossed a chunk border, joined or left since the last look (the "player moved" event; one compare per player). */
	static boolean playersMoved(ServerLevel lv) {
		boolean moved = false;
		Set<UUID> now = new HashSet<>();
		for (ServerPlayer p : lv.players()) {
			if (p.isSpectator()) continue;
			now.add(p.getUUID());
			long c = ChunkPos.pack(p.getBlockX() >> 4, p.getBlockZ() >> 4);
			Long old = PLAYER_CHUNKS.put(p.getUUID(), c);
			if (old == null || old != c) moved = true;
		}
		if (PLAYER_CHUNKS.keySet().retainAll(now)) moved = true;
		if (moved) chunkEvents++;
		return moved;
	}

	static List<Vec3> observers(ServerLevel lv) {
		List<Vec3> obs = new ArrayList<>(testObservers);
		for (ServerPlayer p : lv.players()) if (!p.isSpectator()) obs.add(p.position());
		return obs;
	}

	static double nearestSq(List<Vec3> obs, double x, double z) {
		double best = Double.MAX_VALUE;
		for (Vec3 o : obs) {
			double dx = o.x - x, dz = o.z - z;
			best = Math.min(best, dx * dx + dz * dz);
		}
		return best;
	}

	public static int liveTotal() {
		int n = 0;
		for (Towns.Town t : Towns.all()) n += t.live.size();
		return n;
	}

	static int pendingTotal() {
		int n = 0;
		for (int v : PENDING.values()) n += v;
		return n;
	}

	/** Live townsfolk of {@code faction} within 64 blocks of {@code o} (the per-player cap counts one kingdom's people). */
	static int liveNear(Vec3 o, Identifier faction) {
		int n = 0;
		for (Towns.Town t : Towns.all()) {
			if (!t.faction.equals(faction)) continue;
			for (Townsfolk f : t.live) if (f.distanceToSqr(o.x, f.getY(), o.z) < 64 * 64) n++;
		}
		return n;
	}

	/** The kingdom's live cap near one player for this town (capital or town). */
	public static int cap(Towns.Town t) {
		TownsfolkRoles.Kingdom k = TownsfolkRoles.of(t.faction);
		int c = k == null ? 0 : t.capital ? k.liveCapital() : k.liveTown();
		return Math.min(c, AREA_CAP);
	}

	/** Live townsfolk this town wants now: {@code min(cap, logical x outdoor share)} (events bring people out). */
	public static int desired(ServerLevel lv, Towns.Town t, int logical) {
		TownsfolkRoles.Kingdom k = TownsfolkRoles.of(t.faction);
		if (k == null || logical <= 0) return 0;
		int hour = hour(lv);
		double share = k.outdoorShare(hour);
		TownsfolkRoutineLink.Plan plan = TownsfolkRoutineLink.plan(t.faction, day(lv), hour);
		if (plan.event()) share = Math.max(share, plan.totalShare());
		return Math.min(cap(t), (int) Math.round(logical * share));
	}

	/** One evaluation pass over the towns (on a player-moved or hour event; also called by the self-test). */
	public static void evaluate(ServerLevel lv) {
		dirty = false;
		if (!TownsfolkConfig.active()) return;
		evaluations++;
		List<Vec3> obs = observers(lv);
		Towns.refresh(lv);
		TownsfolkState st = TownsfolkState.get(lv);
		long day = day(lv);
		int server = liveTotal() + pendingTotal();
		for (Towns.Town t : new ArrayList<>(Towns.all())) {
			// fold anyone beyond every player's reach (out of their tracking range: nobody sees it go)
			for (Townsfolk f : new ArrayList<>(t.live))
				if (nearestSq(obs, f.getX(), f.getZ()) > (double) FOLD_RADIUS * FOLD_RADIUS) {
					foldedFar++;
					fold(f);
				}
			double d = Math.sqrt(nearestSq(obs, t.center.getX(), t.center.getZ()));
			if (d > t.radius + SPAWN_RADIUS) {
				server -= PENDING.getOrDefault(t.key, 0);
				PENDING.remove(t.key);
				continue;
			}
			StreetGraph g = graph(lv, t, st, day);
			if (g == null) continue; // being built; its completion triggers a new evaluation
			int logical = st.logical(t.key, t.maxResidents(), day);
			int want = Math.min(desired(lv, t, logical), logical);
			int have = t.live.size() + PENDING.getOrDefault(t.key, 0);
			if (want > have) {
				Vec3 o = nearestObserver(obs, t.center);
				int room = want - have;
				room = Math.min(room, AREA_CAP - have);
				room = Math.min(room, SERVER_CAP - server);
				if (o != null) room = Math.min(room, cap(t) - liveNear(o, t.faction) - PENDING.getOrDefault(t.key, 0));
				if (room > 0) {
					PENDING.merge(t.key, room, Integer::sum);
					server += room;
				}
			} else if (want < t.live.size()) {
				int surplus = t.live.size() - want;
				// the ones farthest from any player go home first
				List<Townsfolk> order = new ArrayList<>(t.live);
				order.removeIf(f -> f.goingHome);
				order.sort((a, b) -> Double.compare(nearestSq(obs, b.getX(), b.getZ()), nearestSq(obs, a.getX(), a.getZ())));
				for (int i = 0; i < order.size() && surplus > 0; i++, surplus--) sendHome(lv, t, order.get(i), obs);
			}
		}
	}

	static @Nullable Vec3 nearestObserver(List<Vec3> obs, BlockPos p) {
		Vec3 best = null;
		double bd = Double.MAX_VALUE;
		for (Vec3 o : obs) {
			double dx = o.x - p.getX(), dz = o.z - p.getZ(), d = dx * dx + dz * dz;
			if (d < bd) {
				bd = d;
				best = o;
			}
		}
		return best;
	}

	/** The town's street graph: from the save, else a build is queued (null until it finishes). Old graphs are rebuilt lazily. */
	static @Nullable StreetGraph graph(ServerLevel lv, Towns.Town t, TownsfolkState st, long day) {
		if (t.graph == null) t.graph = st.graph(t.key);
		if (t.graph != null && t.graph.size() > 0 && day - t.graph.builtDay <= StreetGraph.MAX_AGE_DAYS) return t.graph;
		if (!BUILDS.containsKey(t.key)) {
			int y = lv.isLoaded(t.center) ? lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, t.center.getX(), t.center.getZ()) : t.center.getY();
			BUILDS.put(t.key, new StreetGraph.Builder(t.key, new BlockPos(t.center.getX(), y, t.center.getZ()), t.radius, day));
		}
		return t.graph != null && t.graph.size() > 0 ? t.graph : null; // an old graph serves until the new one is ready
	}

	static void runBuilds(ServerLevel lv) {
		Iterator<Map.Entry<String, StreetGraph.Builder>> it = BUILDS.entrySet().iterator();
		if (!it.hasNext()) return;
		var e = it.next();
		StreetGraph.Builder b = e.getValue();
		Towns.Town t = Towns.get(e.getKey());
		if (t == null) {
			it.remove();
			return;
		}
		StreetGraph g = b.step(lv, BUILD_BUDGET_NANOS);
		if (g == null) return;
		it.remove();
		lastBuildMs = b.nanos / 1e6;
		lastBuildMaxStepMs = b.maxStepNanos / 1e6;
		if (g.size() < 3) {
			// mostly unloaded or no streets: try again on a later evaluation (the old graph, if any, stays)
			if (b.unloaded > 0) dirty = true;
			return;
		}
		graphsBuilt++;
		t.graph = g;
		TownsfolkState.get(lv).putGraph(t.key, g);
		dirty = true;
	}

	static void runSpawns(ServerLevel lv) {
		Iterator<Map.Entry<String, Integer>> it = PENDING.entrySet().iterator();
		int budget = SPAWNS_PER_TICK;
		List<Vec3> obs = observers(lv);
		while (it.hasNext() && budget > 0) {
			var e = it.next();
			Towns.Town t = Towns.get(e.getKey());
			if (t == null || t.graph == null) {
				it.remove();
				continue;
			}
			int left = e.getValue();
			while (left > 0 && budget > 0) {
				left--;
				budget--;
				if (spawnOne(lv, t, obs, null) == null) {
					left = 0; // no place now (chunks still loading after a teleport or a time set): ask again at the next evaluation (about 1 s)
					dirty = true;
				}
			}
			if (left <= 0) it.remove();
			else e.setValue(left);
		}
	}

	/** Spawns one resident of town {@code t}: at {@code at}, or at a street node near a player but out of their near view (doors first). */
	static @Nullable Townsfolk spawnOne(ServerLevel lv, Towns.Town t, List<Vec3> obs, @Nullable BlockPos at) {
		TownsfolkRoles.Kingdom k = TownsfolkRoles.of(t.faction);
		StreetGraph g = t.graph;
		if (k == null || TYPE == null) return null;
		BlockPos pos = at;
		if (pos == null) {
			if (g == null) return null;
			pos = spawnNode(lv, g, obs);
			if (pos == null) return null;
		}
		if (!lv.isLoaded(pos) || !lv.isPositionEntityTicking(pos)) return null;
		Townsfolk f = TYPE.create(lv, EntitySpawnReason.MOB_SUMMONED);
		if (f == null) return null;
		var rnd = lv.getRandom();
		int hour = hour(lv);
		f.setup(t.key, k.faction(), k.pickOutdoors(rnd.nextDouble(), hour), rnd.nextInt(TownsfolkRoles.VARIANTS)); // the kingdom's skins, also for a sub-faction
		f.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360F, 0F);
		if (!lv.addFreshEntity(f)) return null;
		t.live.add(f);
		spawned++;
		plan(lv, t, f, day(lv), hour);
		return f;
	}

	/** A usable node within {@link #SPAWN_RADIUS} of a player and at least 16 blocks from all (doors preferred), else at least 8. */
	static @Nullable BlockPos spawnNode(ServerLevel lv, StreetGraph g, List<Vec3> obs) {
		List<Integer> doors = new ArrayList<>(), far = new ArrayList<>(), near = new ArrayList<>();
		for (int i = 0; i < g.size(); i++) {
			if (!g.usable(i)) continue;
			double d = nearestSq(obs, g.x[i], g.z[i]);
			if (d > (double) SPAWN_RADIUS * SPAWN_RADIUS) continue;
			if (d >= 16 * 16) (g.door(i) ? doors : far).add(i);
			else if (d >= 8 * 8) near.add(i);
		}
		List<Integer> pick = !doors.isEmpty() ? doors : !far.isEmpty() ? far : near;
		if (pick.isEmpty()) return null;
		return g.pos(pick.get(lv.getRandom().nextInt(pick.size())));
	}

	// ---- plans ------------------------------------------------------------------------------------------------------------------

	/** At an hour change: every live townsperson gets its plan for the new hour (the routine engine's caches are per hour). */
	static void onHour(ServerLevel lv) {
		hourEvents++;
		TownsfolkRoutineLink.clearCache();
		long day = day(lv);
		int hour = hour(lv);
		for (Towns.Town t : Towns.all()) {
			t.anchors.clear();
			for (Townsfolk f : new ArrayList<>(t.live)) if (!f.goingHome) plan(lv, t, f, day, hour);
		}
	}

	/** Gives {@code f} its activity for this hour: a kingdom-wide assignment (by a stable roll), else its role's schedule, else home. */
	public static void plan(ServerLevel lv, Towns.Town t, Townsfolk f, long day, int hour) {
		TownsfolkRoles.Kingdom k = TownsfolkRoles.of(t.faction);
		if (k == null) return;
		TownsfolkRoles.Role role = k.role(f.role);
		TownsfolkRoles.Slot slot = role == null ? null : role.at(hour);
		TownsfolkRoutineLink.Plan plan = TownsfolkRoutineLink.plan(t.faction, day, hour);
		String act = null, where = null, pose = "NONE";
		if (slot != null || plan.event()) {
			double r = roll(f.getUUID(), day, hour), acc = 0;
			for (TownsfolkRoutineLink.Assignment a : plan.assignments()) {
				acc += a.share();
				if (r < acc) {
					act = a.activity();
					where = a.where();
					pose = a.pose();
					break;
				}
			}
		}
		if (act == null && slot != null) {
			act = slot.activity();
			where = slot.where();
			pose = slot.pose();
		}
		if (act != null && "peat_cutter".equals(f.role) && Serfs.isOnStrike(lv, t.faction)) {
			act = "strike"; // Mirewick's council-paid diggers picket the Counting-House while the strike lasts
			where = "counting_house";
			pose = "CHEER";
		}
		replans++;
		if (act == null) {
			sendHome(lv, t, f, observers(lv));
			return;
		}
		BlockPos anchor = anchor(lv, t, where, act);
		StreetGraph g = t.graph;
		BlockPos spot = anchor;
		BlockPos[] path = null;
		if (g != null) {
			int from = g.nearest(f.blockPosition(), -1);
			int to = from < 0 ? -1 : g.nearest(anchor, g.comp[from]);
			if (to >= 0) {
				path = g.path(from, to);
				BlockPos node = g.pos(to);
				spot = spread(lv, node, f.getUUID());
			}
		}
		f.plan(spot, path, act, pose, false);
	}

	/** Walks {@code f} to the nearest door (or the node farthest from players); with nobody within 24 blocks it folds at once. */
	static void sendHome(ServerLevel lv, Towns.Town t, Townsfolk f, List<Vec3> obs) {
		if (nearestSq(obs, f.getX(), f.getZ()) > 24 * 24) {
			foldedHome++;
			fold(f);
			return;
		}
		StreetGraph g = t.graph;
		if (g == null) {
			foldedHome++;
			fold(f);
			return;
		}
		int from = g.nearest(f.blockPosition(), -1);
		int best = -1;
		double bestScore = -Double.MAX_VALUE;
		for (int i = 0; i < g.size(); i++) {
			if (from < 0 || g.comp[i] != g.comp[from]) continue;
			double walk = Math.sqrt(f.distanceToSqr(g.x[i] + 0.5, g.y[i], g.z[i] + 0.5));
			double away = Math.sqrt(nearestSq(obs, g.x[i], g.z[i]));
			double score = (g.door(i) ? 40 : 0) + Math.min(away, 32) - walk * 0.5;
			if (score > bestScore) {
				bestScore = score;
				best = i;
			}
		}
		if (best < 0) {
			foldedHome++;
			fold(f);
			return;
		}
		f.plan(g.pos(best), g.path(from, best), "home", "NONE", true);
	}

	/** Where an activity happens: the routine engine's place for the where id, else a fixed direction per where id from the centre. */
	static BlockPos anchor(ServerLevel lv, Towns.Town t, String where, String act) {
		return t.anchors.computeIfAbsent(where + "|" + act, k -> {
			BlockPos p = TownsfolkRoutineLink.resolve(lv, t.faction, t.center, where, act);
			if (p != null) return p;
			double ang = Math.floorMod(where.hashCode(), 360) * Math.PI / 180;
			double dist = t.radius * (0.25 + Math.floorMod(where.hashCode() >> 9, 40) / 100.0);
			int x = t.center.getX() + (int) Math.round(Math.cos(ang) * dist), z = t.center.getZ() + (int) Math.round(Math.sin(ang) * dist);
			int y = lv.isLoaded(new BlockPos(x, t.center.getY(), z)) ? lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : t.center.getY();
			return new BlockPos(x, y, z);
		});
	}

	/** A spot within 3 blocks of a street node, stable per person, on the same level and dry; else the node itself. */
	static BlockPos spread(ServerLevel lv, BlockPos node, UUID id) {
		long h = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
		int dx = (int) Math.floorMod(h, 7L) - 3, dz = (int) Math.floorMod(h >> 8, 7L) - 3;
		int x = node.getX() + dx, z = node.getZ() + dz;
		BlockPos probe = new BlockPos(x, node.getY(), z);
		if (!lv.isLoaded(probe)) return node;
		int y = lv.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (Math.abs(y - node.getY()) > 1 || !lv.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) return node;
		return new BlockPos(x, y, z);
	}

	static double roll(UUID id, long day, int hour) {
		long h = id.getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L) ^ (hour * 0xC2B2AE3D27D4EB4FL);
		h ^= h >>> 33;
		h *= 0xFF51AFD7ED558CCDL;
		h ^= h >>> 33;
		return (h >>> 11) / (double) (1L << 53);
	}

	// ---- events from the entity -------------------------------------------------------------------------------------------------

	/** Hurt: neighbours within 12 blocks who saw it flee too (Serf-style). Walks the town's live list, not the world. */
	static void alertNeighbours(Townsfolk f, Vec3 from) {
		Towns.Town t = Towns.get(f.townKey);
		if (t == null) return;
		for (Townsfolk o : t.live)
			if (o != f && o.isAlive() && o.distanceToSqr(f) < 144 && (o.distanceToSqr(f) < 25 || o.hasLineOfSight(f))) o.startPanic(from);
	}

	/** Died: one fewer resident until the daily refill; a player (or their pet) who killed them loses Standing as for a Serf. */
	static void onDeath(ServerLevel lv, Townsfolk f, DamageSource src) {
		deaths++;
		Towns.Town t = Towns.get(f.townKey);
		if (t != null) {
			t.live.remove(f);
			TownsfolkState.get(lv).kill(t.key);
		}
		ServerPlayer killer = null;
		Entity e = src.getEntity();
		if (e instanceof ServerPlayer sp) killer = sp;
		else if (e instanceof OwnableEntity o && o.getOwner() instanceof ServerPlayer sp) killer = sp;
		if (killer != null) Standing.onKilled(killer, f.faction, Standing.Victim.SERF);
		dirty = true;
	}

	static void onRemoved(Townsfolk f) {
		Towns.Town t = Towns.get(f.townKey);
		if (t != null) t.live.remove(f);
	}

	/** Goes indoors: the entity vanishes, the town's logical count is unchanged. */
	static void foldIn(Townsfolk f, String why) {
		if ("home".equals(why)) foldedHome++;
		else foldedOther++;
		fold(f);
	}

	private static void fold(Townsfolk f) {
		Towns.Town t = Towns.get(f.townKey);
		if (t != null) t.live.remove(f);
		if (!f.isRemoved()) f.discard();
	}

	/** Folds a whole town (kill switch, owner change, town gone). */
	static void foldTown(Towns.Town t, String why) {
		PENDING.remove(t.key);
		for (Townsfolk f : new ArrayList<>(t.live)) {
			foldedOther++;
			fold(f);
		}
	}

	static void foldAll(String why) {
		PENDING.clear();
		for (Towns.Town t : new ArrayList<>(Towns.all())) foldTown(t, why);
	}

	/**
	 * Crowd clusters (ARMIES.md 2.5): a crowd figure within 12 blocks of a player breaks out into a real townsperson of
	 * {@code faction} at {@code at}, if the town there has a resident to spare and the caps leave room. Null otherwise.
	 */
	public static @Nullable Townsfolk breakOut(ServerLevel lv, BlockPos at, Identifier faction) {
		if (!TownsfolkConfig.active()) return null;
		Towns.Town t = Towns.near(at, faction, 16);
		if (t == null || t.graph == null) return null;
		int logical = TownsfolkState.get(lv).logical(t.key, t.maxResidents(), day(lv));
		if (t.live.size() >= Math.min(logical, cap(t)) || t.live.size() >= AREA_CAP || liveTotal() >= SERVER_CAP) return null;
		return spawnOne(lv, t, observers(lv), at);
	}

	/** Pending spawns of a town (self-test and command). */
	public static int pending(String key) {
		return PENDING.getOrDefault(key, 0);
	}

	public static boolean building(String key) {
		return BUILDS.containsKey(key);
	}

	/** Self-test: drain the spawn and build queues now (as the ticks would). */
	static void drain(ServerLevel lv, int maxTicks) {
		for (int i = 0; i < maxTicks && (!BUILDS.isEmpty() || !PENDING.isEmpty()); i++) {
			if (!BUILDS.isEmpty()) runBuilds(lv);
			if (!PENDING.isEmpty()) runSpawns(lv);
			if (dirty && BUILDS.isEmpty()) evaluate(lv);
		}
	}
}
