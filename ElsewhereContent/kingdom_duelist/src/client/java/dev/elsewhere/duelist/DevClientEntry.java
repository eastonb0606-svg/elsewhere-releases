package dev.elsewhere.duelist;

import net.fabricmc.api.ClientModInitializer;

/** Dev client initializer: calls the module's client entry point. */
public class DevClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		DuelistClient.initClient();
	}
}
