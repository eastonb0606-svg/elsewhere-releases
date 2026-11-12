package dev.elsewhere.gear3.s3.echoarrow.client;

import dev.elsewhere.gear3.s3.echoarrow.EchoArrows;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client registration for the Echo Arrow, called once from {@code Gear3Client.initClient()} (never name this *Client: see Gear3Client). */
public final class EchoArrowRenderers {
	private EchoArrowRenderers() {}

	public static void register() {
		if (EchoArrows.TYPE != null) EntityRendererRegistry.register(EchoArrows.TYPE, EchoArrowRenderer::new);
	}
}
