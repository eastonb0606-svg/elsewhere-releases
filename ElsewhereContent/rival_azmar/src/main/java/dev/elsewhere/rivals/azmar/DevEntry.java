package dev.elsewhere.rivals.azmar;

import dev.elsewhere.rivals.azmar.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Dev mod initializer (elsewhere_rival_azmar): the module content plus the tick-driven self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		AzmarContent.init();
		if (Boolean.getBoolean("azmar.selftest")) {
			ServerLifecycleEvents.SERVER_STARTED.register(srv -> {
				for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) srv.overworld().setChunkForced(cx, cz, true);
			});
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::startTick);
			ServerTickEvents.END_SERVER_TICK.register(SelfTest::endTick);
		}
	}
}
