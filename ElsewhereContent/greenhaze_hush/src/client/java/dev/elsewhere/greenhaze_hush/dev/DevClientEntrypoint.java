package dev.elsewhere.greenhaze_hush.dev;

import dev.elsewhere.greenhaze_hush.GreenhazeHushClient;
import net.fabricmc.api.ClientModInitializer;

/** Dev-only client entrypoint. At merge the core's client entrypoint calls {@link GreenhazeHushClient#initClient()}. */
public final class DevClientEntrypoint implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		GreenhazeHushClient.initClient();
		dev.elsewhere.bossart.hush.BossArtHushClient.initClient();
		dev.elsewhere.bossart.greenhaze.BossArtGreenhazeClient.initClient();
	}
}
