package dev.elsewhere.rivals.mirewick.dev;

import dev.elsewhere.rivals.mirewick.tactics.MirewickTactics;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 3.0 heap pass (s-heapleak): the split-command state ({@link MirewickTactics#GROUPS}) is bounded over a long session. Before the fix
 * every squad that ever made a decision kept its entry until the server stopped; 3,000 squads formed 2 s apart (100 minutes of play)
 * now leave at most the cap, and a squad that is still deciding keeps its state.
 */
final class SelfTestHeap {
	private SelfTestHeap() {}

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			Map<UUID, MirewickTactics.Group> saved = new HashMap<>(MirewickTactics.GROUPS);
			MirewickTactics.GROUPS.clear();
			try {
				long t0 = 5_000_000L;
				UUID keep = UUID.randomUUID();
				MirewickTactics.Group kept = null;
				int squads = 3000;
				for (int i = 0; i < squads; i++) {
					long now = t0 + i * 40L;
					MirewickTactics.track(UUID.randomUUID(), now);
					kept = MirewickTactics.track(keep, now);   // one squad that is still in the field
					kept.pending = MirewickTactics.Strategic.ADVANCE;
				}
				int size = MirewickTactics.GROUPS.size();
				SelfTest.check("heap: Mirewick squad command state stays bounded (" + squads + " squads over 100 min leave " + size + " entries, cap "
					+ MirewickTactics.GROUP_CAP + "; before the fix " + (squads + 1) + ")", size <= MirewickTactics.GROUP_CAP + 1, null);
				MirewickTactics.Group again = MirewickTactics.GROUPS.get(keep);
				SelfTest.check("heap: a squad still deciding keeps its command state (pending order kept)",
					again != null && again == kept && again.pending == MirewickTactics.Strategic.ADVANCE, null);
			} finally {
				MirewickTactics.GROUPS.clear();
				MirewickTactics.GROUPS.putAll(saved);
			}
			return 0;
		});
	}
}
