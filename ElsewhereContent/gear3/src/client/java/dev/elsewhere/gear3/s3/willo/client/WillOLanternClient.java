package dev.elsewhere.gear3.s3.willo.client;

import dev.elsewhere.gear3.s3.willo.WillOLantern;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Will-o'-Lantern client: the Marsh Wisp model layer and renderer. Called once from Gear3Client.initClient(). */
public final class WillOLanternClient {
	private WillOLanternClient() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(MarshWispModel.LAYER, MarshWispModel::createLayer);
		EntityRendererRegistry.register(WillOLantern.WISP, MarshWispRenderer::new);
	}
}
