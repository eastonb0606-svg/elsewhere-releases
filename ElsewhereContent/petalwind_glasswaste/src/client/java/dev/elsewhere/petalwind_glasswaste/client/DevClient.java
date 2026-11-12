package dev.elsewhere.petalwind_glasswaste.client;

import net.fabricmc.api.ClientModInitializer;

/** Dev-only client entry point (dropped at merge). */
public final class DevClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		PetalwindGlasswasteClient.initClient();
		dev.elsewhere.bossart.petalwind.BossArtPetalwindClient.initClient();
	}
}
