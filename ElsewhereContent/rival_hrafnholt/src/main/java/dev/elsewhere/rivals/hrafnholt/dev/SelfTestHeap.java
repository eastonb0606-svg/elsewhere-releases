package dev.elsewhere.rivals.hrafnholt.dev;

import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import java.util.UUID;

/**
 * 3.0 heap pass (s-heapleak): the Skald's Rune-Axe combo map stays bounded over a long session, and the combo itself is unchanged.
 * Before the fix every mob a Rune-Axe ever hit kept an entry (attacker -> target -> hit times) until the server stopped.
 */
final class SelfTestHeap {
	private SelfTestHeap() {}

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			HrafnholtGear.runeReset();
			try {
				// the combo: third hit on one target within the window fires, a slow third hit does not
				UUID a = UUID.randomUUID(), t = UUID.randomUUID(), t2 = UUID.randomUUID();
				long now = 9_000_000L;
				boolean fast = !HrafnholtGear.runeHit(a, t, now) && !HrafnholtGear.runeHit(a, t, now + 20) && HrafnholtGear.runeHit(a, t, now + 40);
				boolean slow = !HrafnholtGear.runeHit(a, t2, now) && !HrafnholtGear.runeHit(a, t2, now + 20)
					&& !HrafnholtGear.runeHit(a, t2, now + 40 + HrafnholtGear.RUNE_WINDOW);
				SelfTest.check("heap: Rune-Axe combo unchanged (3 hits within the window fire, a late third hit does not)", fast && slow, null);
				// a long session: 20 players' worth of attackers each hitting 400 different mobs, 1 s apart
				HrafnholtGear.runeReset();
				int attackers = 20, perAttacker = 400;
				long t0 = 10_000_000L;
				for (int i = 0; i < perAttacker; i++)
					for (int k = 0; k < attackers; k++) HrafnholtGear.runeHit(new UUID(k, 1), UUID.randomUUID(), t0 + i * 20L);
				int[] n = HrafnholtGear.runeTracked();
				SelfTest.check("heap: Rune-Axe combo state stays bounded (" + attackers * perAttacker + " mobs hit leave " + n[1] + " target entries for "
					+ n[0] + " attackers; before the fix " + attackers * perAttacker + ")", n[1] <= attackers * 17, null);
			} finally {
				HrafnholtGear.runeReset();
			}
			return 0;
		});
	}
}
