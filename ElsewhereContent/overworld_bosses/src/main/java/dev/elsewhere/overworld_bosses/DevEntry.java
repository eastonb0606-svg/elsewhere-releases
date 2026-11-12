package dev.elsewhere.overworld_bosses;

import dev.elsewhere.overworld_bosses.dev.DevCommands;
import dev.elsewhere.overworld_bosses.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Dev mod initializer (elsewhere_overworld_bosses): the module content, the dev command and the self-test. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		OverworldBossesContent.init();
		DevCommands.init();
		if (Boolean.getBoolean("overworld_bosses.selftest")) ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
	}
}
