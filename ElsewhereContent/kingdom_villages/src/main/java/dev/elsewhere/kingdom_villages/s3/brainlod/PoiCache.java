package dev.elsewhere.kingdom_villages.s3.brainlod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * fx-siegeperf: remembers villager POI searches (AcquirePoi: bed, job site, meeting point) that can find nothing, so the
 * same empty search is not repeated. The siege bench sampler put AcquirePoi at 10-13% of the full-siege tick: the burn
 * takes beds and job sites, and every homeless or jobless villager re-searches its 48-block radius every 20-40 ticks.
 *
 * <p>Exact, not approximate. An entry says "no POI of this type with free space lies within {@code r} blocks of this
 * section's centre", proven by a probe query when a real search came back empty with no candidates at all (so not
 * because of the villager's own retry back-off). {@code r} is the search radius plus 14 (the farthest any block of the
 * section is from its centre), so it covers every search from inside that section; a hit returns the same empty result
 * vanilla would compute, and vanilla's empty search has no side effects. Entries are dropped by events only: any POI
 * record created (a block placed, a section refreshed or loaded) or any ticket released (a bed or job site gets free
 * space) within {@code r + 1} of an entry's centre removes it. No scans, no timers. Kill switch: the brain LOD flag.
 */
public final class PoiCache {
	private PoiCache() {}

	public static long hits, stored, dropped;

	record Key(Object type, long section, int r) {}

	private static final Map<PoiManager, Set<Key>> EMPTY = new WeakHashMap<>();
	/** Probes that found a POI just outside the search reach: not probed again for {@link #NO_PROBE} ticks (work saved only; the real search always runs). */
	private static final Map<PoiManager, Map<Key, Long>> NO_PROBE = new WeakHashMap<>();
	static final int NO_PROBE_TICKS = 200;
	private static final int MAX = 8192;

	static long sectionOf(BlockPos at) {
		return SectionPos.asLong(at.getX() >> 4, at.getY() >> 4, at.getZ() >> 4);
	}

	/** True when a search of {@code type} from {@code at} is known to find nothing (no change since it was proven). */
	public static synchronized boolean knownEmpty(PoiManager pm, Predicate<Holder<PoiType>> type, BlockPos at, int radius) {
		Set<Key> s = EMPTY.get(pm);
		if (s == null || !s.contains(new Key(type, sectionOf(at), radius + 14))) return false;
		hits++;
		return true;
	}

	/** A real search from {@code at} saw no candidate at all: probe the whole section's reach and remember it if empty. */
	public static void searchedEmpty(PoiManager pm, Predicate<Holder<PoiType>> type, BlockPos at, int radius, PoiManager.Occupancy occ) {
		int r = radius + 14;
		Key key = new Key(type, sectionOf(at), r);
		long now = BrainLod.startPassTick;
		synchronized (PoiCache.class) {
			Map<Key, Long> np = NO_PROBE.get(pm);
			Long until = np == null ? null : np.get(key);
			if (until != null && now < until && now > until - 2 * NO_PROBE_TICKS) return;
		}
		BlockPos c = new BlockPos(((at.getX() >> 4) << 4) + 8, ((at.getY() >> 4) << 4) + 8, ((at.getZ() >> 4) << 4) + 8);
		if (pm.getInRange(type, c, r, occ).findAny().isPresent()) {
			synchronized (PoiCache.class) {
				Map<Key, Long> np = NO_PROBE.computeIfAbsent(pm, k -> new java.util.HashMap<>());
				if (np.size() >= MAX) np.clear();
				np.put(key, now + NO_PROBE_TICKS);
			}
			return;
		}
		synchronized (PoiCache.class) {
			Set<Key> s = EMPTY.computeIfAbsent(pm, k -> new HashSet<>());
			if (s.size() >= MAX) s.clear();
			if (s.add(key)) stored++;
		}
	}

	/** A POI record was created or freed a ticket at {@code pos}: drop every entry whose reach covers it. */
	public static synchronized void changed(BlockPos pos) {
		if (EMPTY.isEmpty()) return;
		for (Set<Key> s : EMPTY.values()) {
			if (s.isEmpty()) continue;
			s.removeIf(k -> {
				long sec = k.section();
				double dx = SectionPos.x(sec) * 16 + 8 - pos.getX(), dy = SectionPos.y(sec) * 16 + 8 - pos.getY(), dz = SectionPos.z(sec) * 16 + 8 - pos.getZ();
				boolean hit = dx * dx + dy * dy + dz * dz <= (double) (k.r() + 1) * (k.r() + 1);
				if (hit) dropped++;
				return hit;
			});
		}
	}

	/** Self-test / kill switch: forget everything. */
	public static synchronized void clear() {
		EMPTY.clear();
		NO_PROBE.clear();
	}
}
