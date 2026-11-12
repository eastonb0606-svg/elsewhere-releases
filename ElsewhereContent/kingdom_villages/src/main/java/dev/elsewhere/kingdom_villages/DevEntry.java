package dev.elsewhere.kingdom_villages;

import dev.elsewhere.kingdom_villages.dev.SelfTest;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/** Dev mod initializer (elsewhere_kingdom_villages): the module content plus the tick-driven self-test. Dropped at merge. */
public class DevEntry implements ModInitializer {
	@Override
	public void onInitialize() {
		VillagesContent.init();
		if (Boolean.getBoolean("villages.selftest")) {
			ServerTickEvents.START_SERVER_TICK.register(SelfTest::startTick);
			ServerTickEvents.END_SERVER_TICK.register(SelfTest::tick);
			dev.elsewhere.kingdom_villages.dev.SerfSkinCheck.run(); // serf variant skins distinct (s-townsfolk-art)
			ServerTickEvents.END_SERVER_TICK.register(dev.elsewhere.kingdom_villages.dev.SelfTestSerfWork::tick); // 3.0 P9 serf work poses (s-p9-idle)
		}
		if (Boolean.getBoolean("villages.worldgen")) ServerTickEvents.END_SERVER_TICK.register(dev.elsewhere.kingdom_villages.dev.WorldgenCheck::endTick);
	}
}
