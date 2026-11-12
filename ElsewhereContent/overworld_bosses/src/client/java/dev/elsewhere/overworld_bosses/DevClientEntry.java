package dev.elsewhere.overworld_bosses;

import net.fabricmc.api.ClientModInitializer;

/** Dev client initializer: calls the module's client entry point. */
public class DevClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		OverworldBossesClient.initClient();
		dev.elsewhere.bossart.overworld_a.BossArtOverworldAClient.initClient();
		dev.elsewhere.bossart.overworld_b.BossArtOverworldBClient.initClient();
	}
}
