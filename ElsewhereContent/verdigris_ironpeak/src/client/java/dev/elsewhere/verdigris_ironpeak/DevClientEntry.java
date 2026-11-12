package dev.elsewhere.verdigris_ironpeak;

import net.fabricmc.api.ClientModInitializer;

/** Dev-only client entry point. At merge, the core's client entry point calls {@link VerdigrisIronpeakClient#initClient()}. */
public final class DevClientEntry implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VerdigrisIronpeakClient.initClient();
		dev.elsewhere.bossart.verdigris.BossArtVerdigrisClient.initClient();
		dev.elsewhere.bossart.ironpeak.BossArtIronpeakClient.initClient();
	}
}
