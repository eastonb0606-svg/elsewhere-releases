package dev.elsewhere.bossart.nether;

import dev.elsewhere.boss.grunthar.GruntharContent;
import dev.elsewhere.boss.mournmaw.MournmawContent;
import dev.elsewhere.boss.solaxis.SolaxisContent;
import dev.elsewhere.bossart.grunthar.AshtuskArtRenderer;
import dev.elsewhere.bossart.grunthar.AshtuskModelLayer;
import dev.elsewhere.bossart.grunthar.GruntharArtRenderer;
import dev.elsewhere.bossart.grunthar.GruntharModelLayer;
import dev.elsewhere.bossart.mournmaw.MournmawArtModelLayer;
import dev.elsewhere.bossart.mournmaw.MournmawArtRenderer;
import dev.elsewhere.bossart.solaxis.SolaxisArtModelLayer;
import dev.elsewhere.bossart.solaxis.SolaxisArtRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art (Nether group): custom models, textures and keyframe animations for Grunthar, Ashtusk, Solaxis and Mournmaw.
 * Call after {@code NetherEndBossesClient.initClient()}; it replaces those four entity renderers.
 */
public final class BossArtNetherClient {
	private static boolean done;

	private BossArtNetherClient() {}

	public static void initClient() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(GruntharArtRenderer.LAYER, GruntharModelLayer::create);
		ModelLayerRegistry.registerModelLayer(AshtuskArtRenderer.LAYER, AshtuskModelLayer::create);
		ModelLayerRegistry.registerModelLayer(SolaxisArtRenderer.LAYER, SolaxisArtModelLayer::create);
		ModelLayerRegistry.registerModelLayer(MournmawArtRenderer.LAYER, MournmawArtModelLayer::create);
		EntityRendererRegistry.register(GruntharContent.GRUNTHAR, GruntharArtRenderer::new);
		EntityRendererRegistry.register(GruntharContent.ASHTUSK, AshtuskArtRenderer::new);
		EntityRendererRegistry.register(SolaxisContent.SOLAXIS, SolaxisArtRenderer::new);
		EntityRendererRegistry.register(MournmawContent.MOURNMAW, MournmawArtRenderer::new);
	}
}
