package dev.elsewhere.gear3.s3.pennant.client;

import dev.elsewhere.gear3.s3.pennant.Pennant;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Herdcaller's Standard client: model layer + renderer. Called from Gear3Client.initClient() (one line). */
public final class PennantClient {
	private PennantClient() {}

	private static boolean done;

	public static void init() {
		if (done || Pennant.STANDARD == null) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(HerdcallerStandardModel.LAYER, HerdcallerStandardModel::createLayer);
		EntityRendererRegistry.register(Pennant.STANDARD, HerdcallerStandardRenderer::new);
	}
}
