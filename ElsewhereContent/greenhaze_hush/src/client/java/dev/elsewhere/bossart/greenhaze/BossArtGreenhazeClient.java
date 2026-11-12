package dev.elsewhere.bossart.greenhaze;

import dev.elsewhere.bossart.grumblegut.GrumblegutArtModel;
import dev.elsewhere.bossart.grumblegut.GrumblegutArtRenderer;
import dev.elsewhere.bossart.percolax.PercolaxArtModel;
import dev.elsewhere.bossart.percolax.PercolaxArtRenderer;
import dev.elsewhere.greenhaze.GreenhazeEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Boss art for Percolax and Grumblegut: custom models, textures and keyframe animations. Call after the module's client init. */
public final class BossArtGreenhazeClient {
	private BossArtGreenhazeClient() {}

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(PercolaxArtRenderer.LAYER, PercolaxArtModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(GrumblegutArtRenderer.LAYER, GrumblegutArtModel::createBodyLayer);
		EntityRendererRegistry.register(GreenhazeEntities.PERCOLAX, PercolaxArtRenderer::new);
		EntityRendererRegistry.register(GreenhazeEntities.GRUMBLEGUT, GrumblegutArtRenderer::new);
	}
}
