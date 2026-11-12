package dev.elsewhere.bossart.ironpeak;

import dev.elsewhere.boss.hrolf.HrolfEntities;
import dev.elsewhere.boss.thurgrim.ThurgrimEntities;
import dev.elsewhere.bossart.hrolf.GrimhornMesh;
import dev.elsewhere.bossart.hrolf.GrimhornRenderer;
import dev.elsewhere.bossart.hrolf.HrolfMesh;
import dev.elsewhere.bossart.hrolf.HrolfRenderer;
import dev.elsewhere.bossart.thurgrim.ThurgrimMesh;
import dev.elsewhere.bossart.thurgrim.ThurgrimRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/**
 * Boss art for Ironpeak (art group "ironpeak"): custom rigs, textures and keyframe animations for Thurgrim Ironjaw,
 * Hrolf Grimhorn and his war goat Grimhorn. Call after VerdigrisIronpeakClient.initClient(); it replaces the beta
 * vanilla-shape renderers for these three entity types.
 */
@Environment(EnvType.CLIENT)
public final class BossArtIronpeakClient {
	private BossArtIronpeakClient() {}

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(ThurgrimRenderer.LAYER, ThurgrimMesh::create);
		ModelLayerRegistry.registerModelLayer(HrolfRenderer.LAYER, HrolfMesh::create);
		ModelLayerRegistry.registerModelLayer(GrimhornRenderer.LAYER, GrimhornMesh::create);
		EntityRendererRegistry.register(ThurgrimEntities.THURGRIM, ThurgrimRenderer::new);
		EntityRendererRegistry.register(HrolfEntities.HROLF, HrolfRenderer::new);
		EntityRendererRegistry.register(HrolfEntities.GRIMHORN, GrimhornRenderer::new);
	}
}
