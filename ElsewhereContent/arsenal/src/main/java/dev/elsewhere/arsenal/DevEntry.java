package dev.elsewhere.arsenal;

import dev.elsewhere.arsenal.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Dev mod initializer (elsewhere_arsenal): the module content plus the self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		ArsenalContent.init();
		// Same hook as the merged jar's BetaInit: SelfTest.run forces the platform chunks and waits until they entity-tick.
		if (Boolean.getBoolean("arsenal.selftest")) ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
	}
}
