package dev.elsewhere.nether_end_bosses;

import dev.elsewhere.boss.aphelion.AphelionContent;
import dev.elsewhere.boss.aphelion.client.AphelionModelLayer;
import dev.elsewhere.boss.aphelion.client.AphelionRenderer;
import dev.elsewhere.boss.aphelion.client.AphelionSegmentModelLayer;
import dev.elsewhere.boss.grunthar.GruntharContent;
import dev.elsewhere.boss.grunthar.client.AshtuskRenderer;
import dev.elsewhere.boss.grunthar.client.GruntharRenderer;
import dev.elsewhere.boss.mournmaw.MournmawContent;
import dev.elsewhere.boss.mournmaw.client.MournmawModelLayer;
import dev.elsewhere.boss.mournmaw.client.MournmawRenderer;
import dev.elsewhere.boss.ossyra.OssyraContent;
import dev.elsewhere.boss.ossyra.client.OssyraModelLayer;
import dev.elsewhere.boss.ossyra.client.OssyraRenderer;
import dev.elsewhere.boss.solaxis.SolaxisContent;
import dev.elsewhere.boss.solaxis.client.SolaxisModelLayer;
import dev.elsewhere.boss.solaxis.client.SolaxisRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/** Client entrypoint of the nether_end_bosses module: renderers and model layers. The core's client entrypoint calls {@link #initClient()}. */
public final class NetherEndBossesClient {
	private static boolean done;

	private NetherEndBossesClient() {}

	public static void initClient() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(SolaxisRenderer.LAYER, SolaxisModelLayer::create);
		ModelLayerRegistry.registerModelLayer(MournmawRenderer.LAYER, MournmawModelLayer::create);
		ModelLayerRegistry.registerModelLayer(OssyraRenderer.LAYER, OssyraModelLayer::create);
		ModelLayerRegistry.registerModelLayer(AphelionRenderer.LAYER, AphelionModelLayer::create);
		ModelLayerRegistry.registerModelLayer(AphelionRenderer.SEGMENT_LAYER, AphelionSegmentModelLayer::create);

		EntityRendererRegistry.register(GruntharContent.GRUNTHAR, GruntharRenderer::new);
		EntityRendererRegistry.register(GruntharContent.ASHTUSK, AshtuskRenderer::new);
		EntityRendererRegistry.register(GruntharContent.GOLD_BOMB, ctx -> new ThrownItemRenderer<>(ctx, 1.6F, true));
		EntityRendererRegistry.register(SolaxisContent.SOLAXIS, SolaxisRenderer::new);
		EntityRendererRegistry.register(SolaxisContent.PYRE_BOLT, ctx -> new ThrownItemRenderer<>(ctx, 0.75F, true));
		EntityRendererRegistry.register(SolaxisContent.SUN_ORB, ctx -> new ThrownItemRenderer<>(ctx, 3.0F, true));
		EntityRendererRegistry.register(MournmawContent.MOURNMAW, MournmawRenderer::new);
		EntityRendererRegistry.register(MournmawContent.SOUL_TEAR, ctx -> new ThrownItemRenderer<>(ctx, 1.0F, true));
		EntityRendererRegistry.register(MournmawContent.SOUL_FIREBALL, ctx -> new ThrownItemRenderer<>(ctx, 2.5F, true));
		EntityRendererRegistry.register(OssyraContent.OSSYRA, OssyraRenderer::new);
		EntityRendererRegistry.register(AphelionContent.APHELION, AphelionRenderer::new);
		EntityRendererRegistry.register(AphelionContent.VOID_BREATH, ctx -> new ThrownItemRenderer<>(ctx, 2.0F, true));
	}
}
