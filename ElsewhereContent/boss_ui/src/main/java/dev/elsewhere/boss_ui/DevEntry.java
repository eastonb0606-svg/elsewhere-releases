package dev.elsewhere.boss_ui;

import dev.elsewhere.boss_ui.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Dev mod initializer: runs the data self-test on a dev server. Dropped at merge (the client part is {@link BossUiClient}). */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		BossUiContent.init();
		if (Boolean.getBoolean("boss_ui.selftest")) ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::run);
	}
}
