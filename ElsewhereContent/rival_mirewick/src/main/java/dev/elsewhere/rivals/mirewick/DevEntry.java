package dev.elsewhere.rivals.mirewick;

import dev.elsewhere.kingdom.KingdomContent;
import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.rivals.mirewick.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Dev mod initializer (elsewhere_rival_mirewick): the module content plus the tick-driven self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		KingdomContent.init();
		CourtContent.init();
		MirewickContent.init();
		if (Boolean.getBoolean("mirewick.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
				for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) srv.overworld().setChunkForced(cx, cz, true);
			});
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::startTick);
			ServerTickEvents.END_SERVER_TICK.register(SelfTest::endTick);
		}
	}
}
