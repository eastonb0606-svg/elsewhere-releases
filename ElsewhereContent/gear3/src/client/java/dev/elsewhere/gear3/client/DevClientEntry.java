package dev.elsewhere.gear3.client;

import net.fabricmc.api.ClientModInitializer;

/** Dev client initializer (elsewhere_gear3): calls the module's client entry point. Dropped at merge (the merged BetaClientInit calls Gear3Client.initClient()). */
public class DevClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Gear3Client.initClient();
	}
}
