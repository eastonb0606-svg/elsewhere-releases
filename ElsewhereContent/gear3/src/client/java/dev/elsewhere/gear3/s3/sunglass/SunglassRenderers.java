package dev.elsewhere.gear3.s3.sunglass;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client registration for the Sunglass Arrow, called once from {@code Gear3Client.initClient()} (never name this *Client: see Gear3Client). */
public final class SunglassRenderers {
	private SunglassRenderers() {}

	public static void init() {
		if (SunglassArrows.TYPE != null) EntityRendererRegistry.register(SunglassArrows.TYPE, SunglassArrowRenderer::new);
	}
}
