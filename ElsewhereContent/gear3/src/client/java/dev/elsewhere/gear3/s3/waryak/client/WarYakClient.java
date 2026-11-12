package dev.elsewhere.gear3.s3.waryak.client;

import dev.elsewhere.gear3.s3.waryak.WarYak;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** War-Yak client: model layer + renderer. Called from Gear3Client.initClient() (one line). The owner-only minion outline comes from MinionEntity. */
public final class WarYakClient {
	private WarYakClient() {}

	private static boolean done;

	public static void register() {
		if (done || WarYak.TYPE == null) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(WarYakModel.LAYER, WarYakModel::createLayer);
		EntityRendererRegistry.register(WarYak.TYPE, WarYakRenderer::new);
	}
}
