package dev.elsewhere.rivals.hrafnholt;

import net.fabricmc.api.ModInitializer;

/** Dev mod initializer (elsewhere_rival_hrafnholt): the module content plus the tick-driven self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		HrafnholtContent.init();
		if (Boolean.getBoolean("hrafnholt.selftest")) dev.elsewhere.rivals.hrafnholt.dev.SelfTest.register();
	}
}
