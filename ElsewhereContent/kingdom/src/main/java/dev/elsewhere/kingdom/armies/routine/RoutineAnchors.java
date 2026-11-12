package dev.elsewhere.kingdom.armies.routine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Layout anchors for the routines (ARMIES.md 2.6): where a {@code where} id (yard, lists, bazaar, quays, thing_stone...) is for a
 * kingdom. Sources in order: explicit anchors registered at runtime with {@link #put} (village and castle pieces), then
 * {@link Provider}s (the court maps ids onto its castle layout), then a fixed direction per id from the squad's post (the 3.0 v1
 * rule). Every anchor is checked once when it is used: loaded, dry and standable (a short vertical search), else the next source,
 * else the post. Nothing here runs per tick.
 */
public final class RoutineAnchors {
	private RoutineAnchors() {}

	/** Maps a where id to a position for a kingdom; {@code near} is the squad's post (null: the realm's own place). */
	@FunctionalInterface
	public interface Provider {
		@Nullable BlockPos anchor(ServerLevel level, Identifier faction, @Nullable BlockPos near, String where);

		/** Every place of a multi-place id (lantern posts, braziers) for the lamplighters; empty when unknown. */
		default List<BlockPos> anchors(ServerLevel level, Identifier faction, String where) {
			return List.of();
		}
	}

	private static final List<Provider> PROVIDERS = new ArrayList<>();
	private static final Map<String, List<BlockPos>> EXPLICIT = new HashMap<>();
	/** Distance from the post per activity for the fallback direction (the 3.0 v1 values). */
	static final Map<String, Integer> DIST = Map.ofEntries(Map.entry("drill", 9), Map.entry("market", 12), Map.entry("court", 8),
		Map.entry("temple", 10), Map.entry("feast", 8), Map.entry("spar", 6), Map.entry("tourney", 12), Map.entry("muster", 10),
		Map.entry("procession", 14), Map.entry("work", 10), Map.entry("siesta", 4), Map.entry("lamplight", 8), Map.entry("caravan", 14),
		Map.entry("patrol", 14));
	public static long fromLayout, fromFallback, toPost;

	public static synchronized void register(Provider p) {
		if (!PROVIDERS.contains(p)) PROVIDERS.add(p);
	}

	static String key(ServerLevel lv, Identifier faction, String where) {
		return lv.dimension().identifier() + "|" + faction + "|" + where;
	}

	/** Registers an anchor (server thread). Several per id are fine: the nearest to the squad is used. */
	public static synchronized void put(ServerLevel lv, Identifier faction, String where, BlockPos pos) {
		List<BlockPos> l = EXPLICIT.computeIfAbsent(key(lv, faction, where), k -> new ArrayList<>());
		BlockPos p = pos.immutable();
		if (!l.contains(p)) l.add(p);
	}

	public static synchronized void putAll(ServerLevel lv, Identifier faction, String where, List<BlockPos> pos) {
		for (BlockPos p : pos) put(lv, faction, where, p);
	}

	/** Every registered place of {@code where} (explicit ones plus the providers'), for lights. */
	public static List<BlockPos> all(ServerLevel lv, Identifier faction, String where) {
		List<BlockPos> out;
		List<Provider> ps;
		synchronized (RoutineAnchors.class) {
			out = new ArrayList<>(EXPLICIT.getOrDefault(key(lv, faction, where), List.of()));
			ps = List.copyOf(PROVIDERS);
		}
		for (Provider p : ps) {
			try {
				for (BlockPos b : p.anchors(lv, faction, where)) if (!out.contains(b)) out.add(b);
			} catch (RuntimeException e) {
				dev.elsewhere.kingdom.KingdomContent.LOG.warn("routine anchor provider failed for {} {}: {}", faction, where, e.toString());
			}
		}
		return out;
	}

	public static synchronized void clear() {
		EXPLICIT.clear();
	}

	/** The layout's own place for {@code where} (explicit, then providers), unchecked; null when no layout knows it. */
	public static @Nullable BlockPos layout(ServerLevel lv, Identifier faction, @Nullable BlockPos near, String where) {
		List<BlockPos> ex;
		List<Provider> ps;
		synchronized (RoutineAnchors.class) {
			ex = EXPLICIT.getOrDefault(key(lv, faction, where), List.of());
			ps = List.copyOf(PROVIDERS);
		}
		BlockPos best = null;
		double bd = 192 * 192;
		for (BlockPos p : ex) {
			double d = near == null ? 0 : p.distSqr(near);
			if (d < bd || near == null && best == null) {
				best = p;
				bd = d;
			}
		}
		if (best != null) return best;
		for (Provider p : ps) {
			try {
				BlockPos b = p.anchor(lv, faction, near, where);
				if (b != null) return b;
			} catch (RuntimeException e) {
				dev.elsewhere.kingdom.KingdomContent.LOG.warn("routine anchor provider failed for {} {}: {}", faction, where, e.toString());
			}
		}
		return null;
	}

	/**
	 * Where a squad posted at {@code post} goes for {@code activity} at {@code where}. Layout anchors first (checked), then the fixed
	 * direction from the post (checked, within 4 blocks of the post's height), else the post itself.
	 */
	public static BlockPos resolve(ServerLevel lv, Identifier faction, BlockPos post, String where, String activity) {
		BlockPos l = layout(lv, faction, post, where);
		if (l != null) {
			BlockPos s = settle(lv, l, 4);
			if (s != null) {
				fromLayout++;
				return s;
			}
		}
		if (activity.equals("sleep") || activity.equals("watch")) {
			toPost++;
			return post;
		}
		int dist = DIST.getOrDefault(activity, 8);
		double ang = Math.floorMod(where.hashCode(), 360) * Math.PI / 180;
		BlockPos p = new BlockPos(post.getX() + (int) Math.round(Math.cos(ang) * dist), post.getY(), post.getZ() + (int) Math.round(Math.sin(ang) * dist));
		if (lv.isLoaded(p)) {
			BlockPos g = lv.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
			if (Math.abs(g.getY() - post.getY()) <= 4 && dry(lv, g) && standable(lv, g)) {
				fromFallback++;
				return g;
			}
		}
		toPost++;
		return post;
	}

	/** A dry, standable spot at {@code p} or up to {@code dy} blocks above or below it (loaded chunks only); null if none. */
	public static @Nullable BlockPos settle(ServerLevel lv, BlockPos p, int dy) {
		if (!lv.isLoaded(p)) return null;
		for (int k = 0; k <= dy; k++) {
			BlockPos a = p.above(k);
			if (standable(lv, a) && dry(lv, a)) return a;
			if (k > 0) {
				BlockPos b = p.below(k);
				if (standable(lv, b) && dry(lv, b)) return b;
			}
		}
		return null;
	}

	public static boolean standable(ServerLevel lv, BlockPos p) {
		BlockState at = lv.getBlockState(p), head = lv.getBlockState(p.above()), below = lv.getBlockState(p.below());
		return at.getCollisionShape(lv, p).isEmpty() && head.getCollisionShape(lv, p.above()).isEmpty()
			&& below.isFaceSturdy(lv, p.below(), Direction.UP);
	}

	public static boolean dry(ServerLevel lv, BlockPos p) {
		return lv.getFluidState(p).isEmpty() && lv.getFluidState(p.below()).isEmpty();
	}
}
