package dev.elsewhere.summoner;

import dev.elsewhere.summoner.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Dev mod initializer (elsewhere_summoner): the module content and the self-test. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		SummonerContent.init();
		if (Boolean.getBoolean("summoner.selftest")) ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
	}
}
