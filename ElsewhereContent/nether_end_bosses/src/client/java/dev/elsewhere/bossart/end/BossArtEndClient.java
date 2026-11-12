package dev.elsewhere.bossart.end;

import dev.elsewhere.boss.aphelion.AphelionContent;
import dev.elsewhere.boss.ossyra.OssyraContent;
import dev.elsewhere.bossart.aphelion.AphelionArtModel;
import dev.elsewhere.bossart.aphelion.AphelionArtRenderer;
import dev.elsewhere.bossart.aphelion.AphelionSegmentArtModel;
import dev.elsewhere.bossart.ossyra.OssyraArtModel;
import dev.elsewhere.bossart.ossyra.OssyraArtRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art (End group): custom models, textures and keyframe animations for Ossyra (queen shulker) and Aphelion (void
 * wyrm, head plus 21 trailing body pieces). Call after {@code NetherEndBossesClient.initClient()}; it replaces both renderers.
 */
public final class BossArtEndClient {
	private static boolean done;

	private BossArtEndClient() {}

	public static void initClient() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(OssyraArtRenderer.LAYER, OssyraArtModel::createLayer);
		ModelLayerRegistry.registerModelLayer(AphelionArtRenderer.LAYER, AphelionArtModel::createLayer);
		ModelLayerRegistry.registerModelLayer(AphelionArtRenderer.SEGMENT_LAYER, AphelionSegmentArtModel::createSegment);
		ModelLayerRegistry.registerModelLayer(AphelionArtRenderer.TAIL_LAYER, AphelionSegmentArtModel::createTail);
		EntityRendererRegistry.register(OssyraContent.OSSYRA, OssyraArtRenderer::new);
		EntityRendererRegistry.register(AphelionContent.APHELION, AphelionArtRenderer::new);
	}
}
