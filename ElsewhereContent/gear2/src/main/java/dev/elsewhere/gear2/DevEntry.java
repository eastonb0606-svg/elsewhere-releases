package dev.elsewhere.gear2;

import dev.elsewhere.gear2.dev.SelfTest;
import net.fabricmc.api.ModInitializer;

/** Dev mod initializer (elsewhere_gear2): the module content plus the self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		Gear2Content.init();
		if (Boolean.getBoolean("gear2.selftest")) SelfTest.register();
	}
}
