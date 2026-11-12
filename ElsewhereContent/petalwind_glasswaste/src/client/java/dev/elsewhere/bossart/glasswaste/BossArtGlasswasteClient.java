package dev.elsewhere.bossart.glasswaste;

import dev.elsewhere.bossart.ankhara.AnkharaModel;
import dev.elsewhere.bossart.ankhara.AnkharaRenderer;
import dev.elsewhere.bossart.ankhara.AnkharaSegmentModel;
import dev.elsewhere.bossart.heliodor.HeliodorModel;
import dev.elsewhere.bossart.heliodor.HeliodorRenderer;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Boss art for Heliodor and Ankhara: registers their model layers and overrides the module's placeholder renderers. Call after the module's client init. */
public final class BossArtGlasswasteClient {
	private static boolean done;

	private BossArtGlasswasteClient() {}

	/** Safe to call more than once. */
	public static void initClient() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(HeliodorRenderer.LAYER, HeliodorModel::createLayer);
		ModelLayerRegistry.registerModelLayer(AnkharaRenderer.HEAD_LAYER, AnkharaModel::createLayer);
		ModelLayerRegistry.registerModelLayer(AnkharaRenderer.SEGMENT_LAYER, AnkharaSegmentModel::createLayer);
		EntityRendererRegistry.register(ModEntities.HELIODOR, HeliodorRenderer::new);
		EntityRendererRegistry.register(ModEntities.ANKHARA, AnkharaRenderer::new);
	}
}
