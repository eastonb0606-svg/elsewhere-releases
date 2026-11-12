package dev.elsewhere.petalwind_glasswaste;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Dev-only entry point (dropped at merge): registers the content plus the dev command and self-test. */
public final class DevMain implements ModInitializer {
	@Override
	public void onInitialize() {
		PetalwindGlasswasteContent.init();
		CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> DevCommands.register(d));
		if (Boolean.getBoolean("petalwind_glasswaste.selftest")) ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::start);
	}
}
