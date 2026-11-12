package dev.elsewhere.bossart.core;

import dev.elsewhere.bossart.mother_creak.MotherCreakArtModel;
import dev.elsewhere.bossart.mother_creak.MotherCreakArtRenderer;
import dev.elsewhere.bossart.selenne.SelenneArtModel;
import dev.elsewhere.bossart.selenne.SelenneArtRenderer;
import dev.elsewhere.registry.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art for the core bosses (Mother Creak, Selenne): custom cube models, textures, emissive layers and keyframe
 * animations. Overrides the core's placeholder renderers for those entity types. Safe to call more than once.
 */
public final class BossArtCoreClient {
	private static boolean done;

	private BossArtCoreClient() {
	}

	public static void initClient() {
		if (done) {
			return;
		}
		done = true;
		ModelLayerRegistry.registerModelLayer(MotherCreakArtRenderer.LAYER, MotherCreakArtModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(SelenneArtRenderer.LAYER, SelenneArtModel::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.MOTHER_CREAK, MotherCreakArtRenderer::new);
		EntityRendererRegistry.register(ModEntities.SELENNE, SelenneArtRenderer::new);
	}
}
