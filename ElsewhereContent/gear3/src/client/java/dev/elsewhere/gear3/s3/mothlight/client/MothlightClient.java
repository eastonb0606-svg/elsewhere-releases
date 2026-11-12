package dev.elsewhere.gear3.s3.mothlight.client;

import dev.elsewhere.gear3.s3.mothlight.Mothlight;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Mothlight Staff client: the Mothlight Swarm model layer and renderer. Called once from Gear3Client.initClient(). */
public final class MothlightClient {
	private MothlightClient() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(MothlightSwarmModel.LAYER, MothlightSwarmModel::createLayer);
		EntityRendererRegistry.register(Mothlight.SWARM, MothlightSwarmRenderer::new);
	}
}
