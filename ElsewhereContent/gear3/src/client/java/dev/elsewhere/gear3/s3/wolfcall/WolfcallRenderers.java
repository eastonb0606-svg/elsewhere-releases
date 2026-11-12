package dev.elsewhere.gear3.s3.wolfcall;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Wolf-Call Horn client: the Warg renderer. Called once from Gear3Client.initClient(). */
public final class WolfcallRenderers {
	private WolfcallRenderers() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		EntityRendererRegistry.register(Wolfcall.WARG, WargRenderer::new);
	}
}
