package dev.elsewhere.kingdom.armies.swarm;

import dev.elsewhere.kingdom.KingdomSoldier;
import dev.elsewhere.kingdom.Squad;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Per-squad swarm state, built on demand (never by a scan): the leader's walked trail for ribbon columns (ARMIES.md 3.3: "followers trail
 * the leader's path nodes, so columns pass gates and bridges correctly") and each member's place in the column.
 */
public final class SwarmSquads {
	private SwarmSquads() {}

	/** Trail points are dropped every {@value #CRUMB} blocks of leader movement; {@value #MAX_CRUMBS} are kept (about 38 blocks). */
	public static final double CRUMB = 0.8, BREAK = 8.0;
	public static final int MAX_CRUMBS = 48;

	static final class SquadState {
		/** Newest first. */
		final List<Vec3> trail = new ArrayList<>();
		// MIN_VALUE / 2, not MIN_VALUE: "now - indexTick >= 5" must not overflow on the first call (with MIN_VALUE it went negative, the column
		// index was never built and every COLUMN member fell back to the heading slot: no ribbon, s-armies-fix 14:02)
		long trailTick = Long.MIN_VALUE / 2, indexTick = Long.MIN_VALUE / 2, used;
		final Map<UUID, Integer> column = new HashMap<>();
	}

	private static final Map<UUID, SquadState> BY_SQUAD = new HashMap<>();

	static void clear() {
		BY_SQUAD.clear();
	}

	static SquadState of(Squad sq, long now) {
		SquadState st = BY_SQUAD.get(sq.id());
		if (st == null) {
			if (BY_SQUAD.size() > 64) BY_SQUAD.values().removeIf(s -> now - s.used > 600 || now < s.used);
			st = new SquadState();
			BY_SQUAD.put(sq.id(), st);
		}
		st.used = now;
		return st;
	}

	/** Records the leader's position once per tick per squad, as a new crumb when it moved {@value #CRUMB}+ blocks; a jump resets the trail. */
	static void track(SquadState st, Vec3 lead, long now) {
		if (st.trailTick == now) return;
		st.trailTick = now;
		if (st.trail.isEmpty()) {
			st.trail.add(lead);
			return;
		}
		double d2 = st.trail.get(0).distanceToSqr(lead);
		if (d2 > BREAK * BREAK) {
			st.trail.clear(); // teleported (unseen routine move, dismount): an old trail would lead through nowhere
			st.trail.add(lead);
		} else if (d2 >= CRUMB * CRUMB) {
			st.trail.add(0, lead);
			if (st.trail.size() > MAX_CRUMBS) st.trail.remove(st.trail.size() - 1);
		}
	}

	/** Column index of a member (the order {@link Squad} uses: loaded members, riders skipped), refreshed every 5 ticks. -1 if none. */
	static int columnIndex(SquadState st, Squad sq, ServerLevel level, KingdomSoldier s, long now) {
		if (now - st.indexTick >= 5 || now < st.indexTick) {
			st.indexTick = now;
			st.column.clear();
			int k = 0;
			for (KingdomSoldier m : sq.memberEntities(level)) if (!m.isPassenger()) st.column.put(m.getUUID(), k++);
		}
		Integer k = st.column.get(s.getUUID());
		return k == null ? -1 : k;
	}

	/**
	 * The ribbon slot of column member {@code k}: {@code 1.6 + (k / 2) * 1.6} blocks back along the leader's walked trail (from the leader's
	 * current position), 0.8 blocks to the left or right of the local direction of travel, the same spacing as the heading-based column.
	 * Null when the trail is not long enough yet (then the heading slot is used).
	 */
	public static @Nullable Vec3 ribbonSlot(List<Vec3> trailNewestFirst, Vec3 head, int k) {
		double back = 1.6 + (k / 2) * 1.6, side = k % 2 == 0 ? -0.8 : 0.8;
		Vec3[] pd = pointBack(trailNewestFirst, head, back);
		if (pd == null) return null;
		Vec3 p = pd[0], dir = pd[1];
		// right of the direction of travel, as Squad: forward (fx, fz) -> right (-fz, fx)
		return new Vec3(p.x - dir.z * side, p.y, p.z + dir.x * side);
	}

	/** {point, unit forward direction} at arc length {@code dist} back from {@code head} along the trail; null when the trail is shorter. */
	public static Vec3[] pointBack(List<Vec3> trail, Vec3 head, double dist) {
		Vec3 newer = head;
		double left = dist;
		for (Vec3 older : trail) {
			double dx = newer.x - older.x, dz = newer.z - older.z;
			double len = Math.sqrt(dx * dx + dz * dz);
			if (len < 1e-4) {
				newer = older;
				continue;
			}
			if (left <= len) {
				double t = left / len;
				Vec3 p = new Vec3(newer.x - dx * t, newer.y + (older.y - newer.y) * t, newer.z - dz * t);
				return new Vec3[] {p, new Vec3(dx / len, 0, dz / len)};
			}
			left -= len;
			newer = older;
		}
		return null;
	}

	/** Distance from {@code p} to the trail polyline (head first), horizontal; for the self-test. */
	public static double distanceToTrail(List<Vec3> trail, Vec3 head, Vec3 p) {
		double best = Double.MAX_VALUE;
		Vec3 a = head;
		for (Vec3 b : trail) {
			double abx = b.x - a.x, abz = b.z - a.z, apx = p.x - a.x, apz = p.z - a.z;
			double l2 = abx * abx + abz * abz;
			double t = l2 < 1e-8 ? 0 : Math.max(0, Math.min(1, (apx * abx + apz * abz) / l2));
			double cx = a.x + abx * t - p.x, cz = a.z + abz * t - p.z;
			best = Math.min(best, Math.sqrt(cx * cx + cz * cz));
			a = b;
		}
		return best;
	}

	/** The trail of a squad as it stands (self-test). */
	public static List<Vec3> trailOf(Squad sq) {
		SquadState st = BY_SQUAD.get(sq.id());
		return st == null ? List.of() : List.copyOf(st.trail);
	}

	/** The loaded leader of a squad, or null. */
	static @Nullable Mob leader(Squad sq, ServerLevel level) {
		return sq.leaderEntity(level);
	}
}
