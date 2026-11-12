package dev.elsewhere.kingdom_court.dev;

import static dev.elsewhere.kingdom_court.dev.SelfTest.STEPS;

import dev.elsewhere.kingdom_court.world.RealmFit;

/**
 * 3.0 heap pass (s-heapleak): the realm column memo ({@code RealmFit.MEMO}, keyed by seed and site) is dropped when a server stops, so a
 * single-player client that opens many worlds no longer keeps every world's realms. Runs after the multi-seed layout checks, which fill
 * the memo for several seeds in this one JVM, and reports what they left.
 */
final class SelfTestHeap {
	private SelfTestHeap() {}

	/** ConcurrentHashMap node + boxed Long key + table slot; ground heights are cached Integers. */
	static final int BYTES_PER_COLUMN = 56;

	static void add() {
		STEPS.add((srv, l) -> {
			int realms = RealmFit.memoRealms(), cols = RealmFit.memoColumns();
			RealmFit.clearMemo(); // what CourtWorld's SERVER_STOPPED hook does
			SelfTest.check("heap: realm column memo dropped on server stop (" + realms + " realms, " + cols + " columns, about "
				+ (cols * (long) BYTES_PER_COLUMN >> 10) + " KB, before the fix kept for the whole game session)",
				RealmFit.memoRealms() == 0 && RealmFit.memoColumns() == 0, null);
			return 0;
		});
	}
}
