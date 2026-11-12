package dev.elsewhere.greenhaze_hush.dev;

import dev.elsewhere.greenhaze_hush.GreenhazeHushContent;
import net.fabricmc.api.ModInitializer;

/** Dev-only entrypoint. At merge the core's main entrypoint calls {@link GreenhazeHushContent#init()} instead. */
public final class DevEntrypoint implements ModInitializer {
	@Override
	public void onInitialize() {
		GreenhazeHushContent.init();
		DevCommands.register();
		SelfTest.register();
	}
}
