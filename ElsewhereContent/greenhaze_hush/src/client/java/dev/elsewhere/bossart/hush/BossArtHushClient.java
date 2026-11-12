package dev.elsewhere.bossart.hush;

import dev.elsewhere.bossart.auris.AurisModel;
import dev.elsewhere.bossart.auris.AurisRenderer;
import dev.elsewhere.bossart.lithara.LitharaModel;
import dev.elsewhere.bossart.lithara.LitharaRenderer;
import dev.elsewhere.hush.HushEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art for the Hush's Tier V bosses (Auris, Lithara): custom cube models, textures, emissive layers and keyframe
 * animations. Call after GreenhazeHushClient.initClient(); it replaces the module's placeholder renderers for both bosses.
 */
public final class BossArtHushClient {
	private BossArtHushClient() {}

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(AurisModel.LAYER, AurisModel::createLayer);
		ModelLayerRegistry.registerModelLayer(LitharaModel.LAYER, LitharaModel::createLayer);
		EntityRendererRegistry.register(HushEntities.AURIS, AurisRenderer::new);
		EntityRendererRegistry.register(HushEntities.LITHARA, LitharaRenderer::new);
	}
}
