package dev.elsewhere.stub.nether_end_bosses;

import dev.elsewhere.nether_end_bosses.NetherEndBossesContent;
import net.fabricmc.api.ModInitializer;

/** Dev-only entrypoint. At merge the core's entrypoint calls {@link NetherEndBossesContent#init()} instead. */
public final class DevMain implements ModInitializer {
	@Override
	public void onInitialize() {
		StubFramework.init();
		NetherEndBossesContent.init();
	}
}
