package dev.elsewhere.gear3;

import dev.elsewhere.gear3.dev.SelfTest;
import net.fabricmc.api.ModInitializer;

/** Dev mod initializer (elsewhere_gear3): the module content plus the self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		Gear3Content.init();
		if (Boolean.getBoolean("gear3.selftest")) SelfTest.register();
	}
}
