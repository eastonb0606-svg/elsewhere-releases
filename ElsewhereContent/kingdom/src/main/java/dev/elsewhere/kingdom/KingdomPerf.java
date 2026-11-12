package dev.elsewhere.kingdom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Performance guard rails.
 * <ul>
 * <li><b>Active set:</b> every 10 ticks, for each player (non-spectator) the nearest {@code perf.maxRealSoldiersPerPlayer}
 * soldiers within {@code perf.activeRadius} are marked active, up to {@code perf.maxRealSoldiersGlobal} in total.
 * Inactive soldiers skip their expensive goals (targeting scans, strafing, formation, look-around); physics still runs.</li>
 * <li><b>Path budget:</b> at most {@code perf.pathSearchesPerTick} soldier path searches start per tick ({@link #tryPath}).</li>
 * <li><b>Spawn caps:</b> {@link #canSpawn} checks the global, per-area, mounted and squad caps; {@link KingdomApi} spawns
 * respect it (spawn eggs and /summon don't, they are op tools).</li>
 * </ul>
 */
public final class KingdomPerf {
	private KingdomPerf() {}

	/** Tests/benchmarks: treat every soldier as active. */
	public static boolean forceActive;
	/** Tests: extra "player" positions for the active-set computation (FakePlayers are not in level.players()). */
	public static final List<Vec3> testObservers = new ArrayList<>();
	/** Path searches started (counter for tests/benchmarks). */
	public static long pathsStarted, pathsDenied;

	private static final Set<KingdomSoldier> LOADED = new HashSet<>();
	private static long budgetTick = -1;
	private static int budgetUsed;

	static void onLoad(KingdomSoldier s) {
		LOADED.add(s);
	}

	static void onUnload(KingdomSoldier s) {
		LOADED.remove(s);
	}

	static void reset() {
		LOADED.clear();
		testObservers.clear();
		forceActive = false;
	}

	/** Loaded soldiers on the server (all levels). */
	public static int loadedCount() {
		LOADED.removeIf(s -> s.isRemoved());
		return LOADED.size();
	}

	public static List<KingdomSoldier> loaded(ServerLevel level) {
		List<KingdomSoldier> out = new ArrayList<>();
		for (KingdomSoldier s : LOADED) if (s.level() == level && !s.isRemoved()) out.add(s);
		return out;
	}

	/** Asks for one path search this tick; false when the per-tick budget is used up (try again next tick). */
	public static boolean tryPath(ServerLevel level) {
		long t = level.getServer().getTickCount();
		if (t != budgetTick) {
			budgetTick = t;
			budgetUsed = 0;
		}
		if (budgetUsed >= KingdomConfig.pathSearchesPerTick) {
			pathsDenied++;
			return false;
		}
		budgetUsed++;
		pathsStarted++;
		return true;
	}

	/**
	 * Null if a soldier may be spawned at {@code pos}, else the reason ("global cap", "area cap", "player cap",
	 * "mounted cap"). Squad size is checked by {@link Squad#hasRoom()}.
	 */
	public static String canSpawn(ServerLevel level, Vec3 pos, boolean mounted) {
		int total = 0, mountedN = 0;
		for (KingdomSoldier s : LOADED) {
			if (s.isRemoved()) continue;
			total++;
			if (s.isPassenger()) mountedN++;
		}
		if (total >= KingdomConfig.maxRealSoldiersGlobal) return "global cap";
		if (areaCount(level, pos) >= KingdomConfig.maxRealSoldiersPerArea) return "area cap";
		if (playerRoom(level, pos) <= 0) return "player cap";
		if (mounted && mountedN >= KingdomConfig.maxMounted) return "mounted cap";
		return null;
	}

	static int areaCount(ServerLevel level, Vec3 pos) {
		int area = 0;
		for (KingdomSoldier s : LOADED) if (!s.isRemoved() && s.level() == level && s.position().distanceToSqr(pos) < 48 * 48) area++;
		return area;
	}

	/**
	 * Per-player cap: for every player (or test observer) within {@code perf.activeRadius} of {@code pos}, soldiers near
	 * that player may not exceed {@code maxRealSoldiersPerPlayer} x the players there. Integer.MAX_VALUE when no player is near.
	 */
	static int playerRoom(ServerLevel level, Vec3 pos) {
		List<Vec3> obs = KingdomLod.observers(level);
		double r2 = KingdomConfig.activeRadius * KingdomConfig.activeRadius;
		int room = Integer.MAX_VALUE;
		for (Vec3 o : obs) {
			if (o.distanceToSqr(pos) >= r2) continue;
			int players = 0, near = 0;
			for (Vec3 o2 : obs) if (o2.distanceToSqr(o) < r2) players++;
			for (KingdomSoldier s : LOADED) if (!s.isRemoved() && s.level() == level && s.position().distanceToSqr(o) < r2) near++;
			room = Math.min(room, KingdomConfig.maxRealSoldiersPerPlayer * Math.max(1, players) - near);
		}
		return room;
	}

	/** How many more soldiers the caps allow at {@code pos} (global, area and per-player; mounted not counted). */
	public static int room(ServerLevel level, Vec3 pos) {
		int total = loadedCount();
		int r = Math.min(KingdomConfig.maxRealSoldiersGlobal - total, KingdomConfig.maxRealSoldiersPerArea - areaCount(level, pos));
		return Math.max(0, Math.min(r, playerRoom(level, pos)));
	}

	/** Number of soldiers currently active in a level. */
	public static int activeCount(ServerLevel level) {
		int n = 0;
		for (KingdomSoldier s : LOADED) if (s.level() == level && !s.isRemoved() && s.isActive()) n++;
		return n;
	}

	static void tickLevel(ServerLevel level) {
		long now = level.getGameTime();
		if (now % 10 != 0 || LOADED.isEmpty()) return;
		List<Vec3> obs = new ArrayList<>(testObservers);
		for (ServerPlayer p : level.players()) if (!p.isSpectator()) obs.add(p.position());
		if (obs.isEmpty()) return;
		List<KingdomSoldier> here = loaded(level);
		double r2 = KingdomConfig.activeRadius * KingdomConfig.activeRadius;
		Set<KingdomSoldier> active = new HashSet<>();
		for (Vec3 o : obs) {
			List<KingdomSoldier> near = new ArrayList<>();
			for (KingdomSoldier s : here) if (s.position().distanceToSqr(o) < r2) near.add(s);
			near.sort((a, b) -> Double.compare(a.position().distanceToSqr(o), b.position().distanceToSqr(o)));
			int players = 0;
			for (Vec3 o2 : obs) if (o2.distanceToSqr(o) < r2) players++;
			int limit = KingdomConfig.maxRealSoldiersPerPlayer * Math.max(1, players);
			for (int i = 0; i < near.size() && i < limit; i++) {
				if (active.size() >= KingdomConfig.maxRealSoldiersGlobal) break;
				active.add(near.get(i));
			}
		}
		for (KingdomSoldier s : here) s.setActiveUntil(active.contains(s) ? now + 25 : 0);
	}

	public static Set<KingdomSoldier> unmodifiableLoaded() {
		return Collections.unmodifiableSet(LOADED);
	}
}
