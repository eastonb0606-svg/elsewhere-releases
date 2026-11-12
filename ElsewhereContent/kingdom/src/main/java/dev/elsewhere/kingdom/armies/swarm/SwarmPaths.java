package dev.elsewhere.kingdom.armies.swarm;

import dev.elsewhere.kingdom.KingdomSoldier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jspecify.annotations.Nullable;

/**
 * Shared paths (ARMIES.md 3.3): an A* result is reused instead of searched again.
 * <ul>
 * <li><b>March:</b> a squad leader's path to its objective is stored under (level, entity type, start chunk section, 4x4x4 goal cell) for
 * {@value #MARCH_TTL} ticks. Any leader of the same type starting in that section and heading into that cell joins it at its nearest node
 * (within {@value #JOIN_MAX} blocks), so squads converging on one battle anchor search once.</li>
 * <li><b>Approach:</b> a melee soldier's path to an enemy is stored under (level, type, enemy, 8x4x8 start cell, enemy's 2-block cell) for
 * {@value #APPROACH_TTL} ticks; squad mates closing on the same enemy from there join it.</li>
 * </ul>
 * A joined path is a copy with its own node list and progress, so nobody's walk changes another's. A path is only installed when the
 * navigation's own re-path target (used by vanilla when a block changes near the path) is unset or close to the shared target, so a block
 * update never sends the soldier to an old goal. A leader that made no progress on a shared march path evicts it and searches for real.
 * Riders never share (their mounts move them). Cache hits cost no path search from the per-tick budget.
 */
public final class SwarmPaths {
	private SwarmPaths() {}

	public static final int MARCH_TTL = 100, APPROACH_TTL = 10;
	public static final double JOIN_MAX = 4.0, APPROACH_JOIN_MAX = 3.5;
	/** Joined paths, misses, stored paths and evictions (self-test, bench and {@code /kingdom armies swarm}). */
	public static long marchHits, marchMisses, marchStored, approachHits, approachMisses, approachStored, evicted;

	record MarchKey(ResourceKey<Level> dim, EntityType<?> type, long section, long goalCell) {}

	record ApproachKey(ResourceKey<Level> dim, EntityType<?> type, int target, long startCell, long targetCell) {}

	/** One stored path: its nodes (never mutated after storing), target, whether it reaches it, and when it was made. */
	record Entry(List<Node> nodes, BlockPos target, boolean reached, long made) {}

	static final Map<MarchKey, Entry> MARCH = new HashMap<>();
	static final Map<ApproachKey, Entry> APPROACH = new HashMap<>();

	/** Per leader: the shared march path it last joined, where it stood, and when (no-progress eviction). */
	static final class Join {
		MarchKey key;
		long tick;
		double dist;
	}

	private static final Map<KingdomSoldier, Join> JOINS = new WeakHashMap<>();

	static void clear() {
		MARCH.clear();
		APPROACH.clear();
		JOINS.clear();
	}

	/** Self-test: makes every stored path {@code ticks} older (TTL check without waiting). */
	public static void ageForTests(long ticks) {
		MARCH.replaceAll((k, e) -> new Entry(e.nodes(), e.target(), e.reached(), e.made() - ticks));
		APPROACH.replaceAll((k, e) -> new Entry(e.nodes(), e.target(), e.reached(), e.made() - ticks));
	}

	public static int storedMarch() {
		return MARCH.size();
	}

	static long cell(int x, int y, int z) {
		return ((long) x & 0x3FFFFFL) << 42 | ((long) z & 0x3FFFFFL) << 20 | ((long) y & 0xFFFFFL);
	}

	static MarchKey marchKey(Entity mob, BlockPos goal) {
		BlockPos p = mob.blockPosition();
		return new MarchKey(mob.level().dimension(), mob.getType(), cell(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4),
			cell(goal.getX() >> 2, goal.getY() >> 2, goal.getZ() >> 2));
	}

	static ApproachKey approachKey(Entity mob, Entity target) {
		BlockPos p = mob.blockPosition(), t = target.blockPosition();
		return new ApproachKey(mob.level().dimension(), mob.getType(), target.getId(), cell(p.getX() >> 3, p.getY() >> 2, p.getZ() >> 3),
			cell(t.getX() >> 1, t.getY() >> 1, t.getZ() >> 1));
	}

	static List<Node> nodesOf(Path p) {
		List<Node> out = new ArrayList<>(p.getNodeCount());
		for (int i = 0; i < p.getNodeCount(); i++) out.add(p.getNode(i));
		return out;
	}

	private static void prune(Map<?, Entry> map, long now, int ttl) {
		if (map.size() < 128) return;
		map.values().removeIf(e -> now - e.made() > ttl || now < e.made());
	}

	/**
	 * A copy of {@code e}'s path for {@code mob}, joined at the node nearest to it (within {@code max} blocks horizontally, 2 vertically); the
	 * node after it when the mob already stands between the two. Null when no node is near or only the last node is.
	 */
	static @Nullable Path join(Entity mob, Entry e, double max) {
		List<Node> nodes = e.nodes();
		int best = -1;
		double bd = max * max;
		for (int i = 0; i < nodes.size(); i++) {
			Node n = nodes.get(i);
			if (Math.abs(n.y - mob.getY()) > 2.0) continue;
			double dx = n.x + 0.5 - mob.getX(), dz = n.z + 0.5 - mob.getZ();
			double d = dx * dx + dz * dz;
			if (d < bd) {
				bd = d;
				best = i;
			}
		}
		if (best < 0 || best >= nodes.size() - 1) return null;
		Node a = nodes.get(best), b = nodes.get(best + 1);
		double ab = a.distanceToXZ(b);
		double bx = b.x + 0.5 - mob.getX(), bz = b.z + 0.5 - mob.getZ();
		if (bx * bx + bz * bz < ab * ab) best++; // already past node a towards b
		Path p = new Path(new ArrayList<>(nodes), e.target(), e.reached());
		p.setNextNodeIndex(best);
		return p;
	}

	/** True when the navigation's re-path target is unset or within {@code r} blocks of {@code target}. */
	static boolean recomputeSafe(PathNavigation nav, BlockPos target, int r) {
		BlockPos tp = nav.getTargetPos();
		return tp == null || tp.distManhattan(target) <= r;
	}

	// ---- march -------------------------------------------------------------------------------------------------------------------

	/** Repaths skipped because the leader still walked a live path into its goal cell. */
	public static long marchKept;

	/**
	 * True when the leader already walks a live path (its own search or a joined one) into {@code goal}'s 4x4x4 cell: keep it, no join and no
	 * search, as vanilla keeps a path to an unchanged target. Without this every repath re-joined whichever neighbouring squad had stored last,
	 * at that path's nearest node (often its start, to the side), so leaders hopped between paths and zig-zagged on the spot (s-armies-fix,
	 * 14:15: 0 of 3 leaders 6 blocks closer in 80 ticks). A joined path that brought no progress for 20+ ticks is evicted and not kept.
	 */
	public static boolean keepMarch(KingdomSoldier mob, BlockPos goal) {
		if (!Swarm.on() || mob.isPassenger()) return false;
		PathNavigation nav = mob.getNavigation();
		Path p = nav.getPath();
		if (p == null || p.isDone() || nav.isStuck()) return false;
		BlockPos t = p.getTarget();
		if (t == null || (t.getX() >> 2) != (goal.getX() >> 2) || (t.getY() >> 2) != (goal.getY() >> 2) || (t.getZ() >> 2) != (goal.getZ() >> 2)) return false;
		Join j = JOINS.get(mob);
		if (j != null) {
			long now = mob.level().getGameTime();
			double dist = Math.sqrt(goal.distToCenterSqr(mob.position()));
			if (dist <= j.dist - 1.0) {
				j.tick = now;
				j.dist = dist;
			} else if (now - j.tick >= 20) {
				// no progress on the shared path: it is bad for us (blocked, changed terrain); drop it and search for real
				MARCH.remove(j.key);
				JOINS.remove(mob);
				evicted++;
				nav.stop();
				return false;
			}
		}
		marchKept++;
		return true;
	}

	/** Joins a stored march path to {@code goal}; false = search for real (then call {@link #offerMarch}). Never uses the path budget. */
	public static boolean joinMarch(KingdomSoldier mob, BlockPos goal, double speed) {
		if (!Swarm.on() || mob.isPassenger()) return false;
		long now = mob.level().getGameTime();
		MarchKey k = marchKey(mob, goal);
		Entry e = MARCH.get(k);
		if (e == null || now - e.made() > MARCH_TTL || now < e.made()) {
			if (e != null) MARCH.remove(k);
			marchMisses++;
			return false;
		}
		double dist = Math.sqrt(goal.distToCenterSqr(mob.position()));
		Join j = JOINS.get(mob);
		if (j != null && k.equals(j.key)) {
			if (dist <= j.dist - 1.0) {
				j.tick = now; // progress since the last check
				j.dist = dist;
			} else if (now - j.tick >= 20) {
				// no progress on the shared path for 20+ ticks: it is bad for us (blocked, changed terrain); search for real
				MARCH.remove(k);
				JOINS.remove(mob);
				evicted++;
				marchMisses++;
				return false;
			}
		}
		if (!recomputeSafe(mob.getNavigation(), e.target(), 6)) {
			marchMisses++;
			return false;
		}
		Path p = join(mob, e, JOIN_MAX);
		if (p == null || !mob.getNavigation().moveTo(p, speed)) {
			marchMisses++;
			return false;
		}
		if (j == null || !k.equals(j.key)) {
			j = new Join(); // first join of this path: progress is measured from here
			j.key = k;
			j.tick = now;
			j.dist = dist;
			JOINS.put(mob, j);
		}
		marchHits++;
		return true;
	}

	/** Stores the leader's freshly searched path to {@code goal} (call right after a real {@code moveTo}). */
	public static void offerMarch(KingdomSoldier mob, BlockPos goal) {
		if (!Swarm.on() || mob.isPassenger()) return;
		Path p = mob.getNavigation().getPath();
		JOINS.remove(mob);
		if (p == null || p.getNodeCount() < 2) return;
		long now = mob.level().getGameTime();
		prune(MARCH, now, MARCH_TTL);
		MARCH.put(marchKey(mob, goal), new Entry(nodesOf(p), p.getTarget(), p.canReach(), now));
		marchStored++;
	}

	// ---- melee approach ----------------------------------------------------------------------------------------------------------

	/** Joins a squad mate's fresh path to {@code target}; false = search for real (then call {@link #offerApproach}). */
	public static boolean joinApproach(KingdomSoldier mob, LivingEntity target, double speed) {
		if (!Swarm.on() || mob.isPassenger() || Swarm.ring(mob) == 0) return false;
		long now = mob.level().getGameTime();
		ApproachKey k = approachKey(mob, target);
		Entry e = APPROACH.get(k);
		if (e == null || now - e.made() > APPROACH_TTL || now < e.made()) {
			if (e != null) APPROACH.remove(k);
			approachMisses++;
			return false;
		}
		if (!recomputeSafe(mob.getNavigation(), e.target(), 4)) {
			approachMisses++;
			return false;
		}
		Path p = join(mob, e, APPROACH_JOIN_MAX);
		if (p == null || !mob.getNavigation().moveTo(p, speed)) {
			approachMisses++;
			return false;
		}
		approachHits++;
		return true;
	}

	/** Stores a freshly searched path to {@code target} (call right after a real {@code moveTo(target)}). */
	public static void offerApproach(KingdomSoldier mob, LivingEntity target) {
		if (!Swarm.on() || mob.isPassenger() || Swarm.ring(mob) == 0) return;
		Path p = mob.getNavigation().getPath();
		if (p == null || p.getNodeCount() < 2) return;
		long now = mob.level().getGameTime();
		prune(APPROACH, now, APPROACH_TTL);
		APPROACH.put(approachKey(mob, target), new Entry(nodesOf(p), p.getTarget(), p.canReach(), now));
		approachStored++;
	}
}
