package dev.elsewhere.gear3.s3.harpoon.client;

import dev.elsewhere.gear3.s3.harpoon.HarpoonBolts;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Harpoon Bolt client: the entity renderer. Called once from Gear3Client.initClient() (the only gear3 client entry). */
public final class HarpoonRenderers {
	private HarpoonRenderers() {}

	public static void init() {
		if (HarpoonBolts.TYPE != null) EntityRendererRegistry.register(HarpoonBolts.TYPE, HarpoonBoltRenderer::new);
	}
}
