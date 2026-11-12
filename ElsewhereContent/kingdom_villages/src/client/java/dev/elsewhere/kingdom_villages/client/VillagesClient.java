package dev.elsewhere.kingdom_villages.client;

import dev.elsewhere.kingdom_villages.VillagesContent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client init: the Serf renderer. (Siege fire, ash and coffer render from their block models.) */
public final class VillagesClient implements ClientModInitializer {
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	public static void initClient() {
		if (done) return;
		done = true;
		EntityRendererRegistry.register(VillagesContent.SERF, SerfRenderer::new);
	}
}
