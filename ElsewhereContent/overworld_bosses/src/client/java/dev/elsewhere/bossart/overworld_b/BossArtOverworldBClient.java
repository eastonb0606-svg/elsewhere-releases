package dev.elsewhere.bossart.overworld_b;

import dev.elsewhere.bossart.rimeback.RimebackModel;
import dev.elsewhere.bossart.rimeback.RimebackRenderer;
import dev.elsewhere.bossart.vorlagh.VorlaghModel;
import dev.elsewhere.bossart.vorlagh.VorlaghRenderer;
import dev.elsewhere.bossart.vorlagh.VorlaghTentacleModel;
import dev.elsewhere.bossart.vorlagh.VorlaghTentacleRenderer;
import dev.elsewhere.overworld_bosses.OwbEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art group overworld_b: custom models, textures and keyframe animations for Rimeback and Vorlagh (body and its
 * five tentacles). Call after OverworldBossesClient.initClient(); the registrations here override the module's
 * beta renderers for those entity types.
 */
public final class BossArtOverworldBClient {
	private BossArtOverworldBClient() {}

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(RimebackRenderer.LAYER, RimebackModel::createLayer);
		ModelLayerRegistry.registerModelLayer(VorlaghRenderer.LAYER, VorlaghModel::createLayer);
		ModelLayerRegistry.registerModelLayer(VorlaghTentacleRenderer.LAYER, VorlaghTentacleModel::createLayer);
		EntityRendererRegistry.register(OwbEntities.RIMEBACK, RimebackRenderer::new);
		EntityRendererRegistry.register(OwbEntities.VORLAGH, VorlaghRenderer::new);
		EntityRendererRegistry.register(OwbEntities.VORLAGH_TENTACLE, VorlaghTentacleRenderer::new);
	}
}
