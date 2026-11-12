package dev.elsewhere.boss_music;

import net.fabricmc.api.ClientModInitializer;

/** Dev client initializer: calls the module's client entry point. Dropped at merge (post_client calls it instead). */
public class DevClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BossMusicClient.initClient();
	}
}
