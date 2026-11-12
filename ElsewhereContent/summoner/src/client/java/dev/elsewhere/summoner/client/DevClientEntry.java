package dev.elsewhere.summoner.client;

import dev.elsewhere.summoner.SummonerClient;
import net.fabricmc.api.ClientModInitializer;

/** Dev client initializer: calls the module's client entry point. */
public class DevClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		SummonerClient.initClient();
	}
}
