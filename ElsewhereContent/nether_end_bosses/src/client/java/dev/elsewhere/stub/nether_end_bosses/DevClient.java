package dev.elsewhere.stub.nether_end_bosses;

import dev.elsewhere.nether_end_bosses.NetherEndBossesClient;
import net.fabricmc.api.ClientModInitializer;

/** Dev-only client entrypoint. At merge the core's client entrypoint calls {@link NetherEndBossesClient#initClient()}. */
public final class DevClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		NetherEndBossesClient.initClient();
		dev.elsewhere.bossart.nether.BossArtNetherClient.initClient(); // at merge the integration calls this after the module clients
		dev.elsewhere.bossart.end.BossArtEndClient.initClient();
	}
}
