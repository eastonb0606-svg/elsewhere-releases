package dev.elsewhere.greenhaze_hush.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Placeholder until the full self-test lands. */
public final class SelfTest {
	private SelfTest() {}

	public static void register() {
		if (!Boolean.getBoolean("greenhaze_hush.selftest")) return;
		ServerLifecycleEvents.SERVER_STARTED.register(SelfTestRun::run);
	}
}
