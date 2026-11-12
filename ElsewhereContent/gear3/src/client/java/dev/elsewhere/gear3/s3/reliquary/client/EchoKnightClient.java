package dev.elsewhere.gear3.s3.reliquary.client;

import dev.elsewhere.gear3.s3.reliquary.EchoReliquary;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Echo Knight client: model layer + renderer. Called from Gear3Client.initClient() (one line). The owner-only minion outline comes from MinionEntity. */
public final class EchoKnightClient {
	private EchoKnightClient() {}

	private static boolean done;

	public static void register() {
		if (done || EchoReliquary.ECHO_KNIGHT == null) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(EchoKnightModel.LAYER, EchoKnightModel::createLayer);
		EntityRendererRegistry.register(EchoReliquary.ECHO_KNIGHT, EchoKnightRenderer::new);
	}
}
