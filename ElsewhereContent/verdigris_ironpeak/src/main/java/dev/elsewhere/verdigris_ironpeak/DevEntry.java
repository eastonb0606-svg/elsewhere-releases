package dev.elsewhere.verdigris_ironpeak;

import net.fabricmc.api.ModInitializer;

/** Dev-only entry point. At merge, the core's entry point calls {@link VerdigrisIronpeakContent#init()} instead. */
public final class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		VerdigrisIronpeakContent.init();
		dev.elsewhere.verdigris_ironpeak.dev.DevCommands.init();
		dev.elsewhere.verdigris_ironpeak.dev.SelfTest.init();
	}
}
