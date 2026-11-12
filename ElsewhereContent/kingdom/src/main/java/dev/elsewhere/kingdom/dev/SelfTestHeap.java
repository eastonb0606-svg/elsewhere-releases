package dev.elsewhere.kingdom.dev;

import dev.elsewhere.kingdom.FactionConflict;
import java.util.UUID;

/**
 * 3.0 heap pass (s-heapleak): the virtual-clash cooldown map stays bounded. Before the fix every squad that ever clashed kept an entry
 * until the server stopped. 4,000 squads clashing 30 ticks apart (100 minutes of abstract war) now leave only the ones still cooling
 * down, and a squad's cooldown still holds for {@link FactionConflict#COOLDOWN_TICKS}. No world access: runs once at self-test start.
 */
final class SelfTestHeap {
	private SelfTestHeap() {}

	static void run() {
		int before = FactionConflict.cooldownEntries();
		long t0 = 50_000_000L;
		int squads = 4000;
		for (int i = 0; i < squads; i++) FactionConflict.cooldown(UUID.randomUUID(), t0 + i * 30L);
		int left = FactionConflict.cooldownEntries() - before;
		int cooling = FactionConflict.COOLDOWN_TICKS / 30 + 1;
		SelfTest.check("heap: clash cooldowns stay bounded (" + squads + " squads leave " + left + " entries, at most max(256, " + cooling
			+ " still cooling); before the fix " + squads + ")", left <= Math.max(257, cooling + 1), null);
	}
}
