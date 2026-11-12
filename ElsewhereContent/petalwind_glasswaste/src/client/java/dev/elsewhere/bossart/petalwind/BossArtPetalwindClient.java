package dev.elsewhere.bossart.petalwind;

import dev.elsewhere.bossart.kazehana.KazehanaGeometry;
import dev.elsewhere.bossart.kazehana.KazehanaRenderer;
import dev.elsewhere.bossart.tempestra.TempestraGeometry;
import dev.elsewhere.bossart.tempestra.TempestraRenderer;
import dev.elsewhere.petalwind_glasswaste.ModEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/**
 * Boss art for Kazehana (and his Petal Mirages) and Tempestra. Call after the module's client init: it replaces the
 * module's generic renderers for these entity types with the custom models.
 */
public final class BossArtPetalwindClient {
	private BossArtPetalwindClient() {}

	public static final ModelLayerLocation KAZEHANA = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss_kazehana"), "main");
	public static final ModelLayerLocation TEMPESTRA = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss_tempestra"), "main");

	public static void initClient() {
		ModelLayerRegistry.registerModelLayer(KAZEHANA, KazehanaGeometry::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(TEMPESTRA, TempestraGeometry::createBodyLayer);
		EntityRendererRegistry.register(ModEntities.KAZEHANA, ctx -> new KazehanaRenderer<>(ctx, false));
		EntityRendererRegistry.register(ModEntities.PETAL_MIRAGE, ctx -> new KazehanaRenderer<>(ctx, true));
		EntityRendererRegistry.register(ModEntities.TEMPESTRA, TempestraRenderer::new);
	}
}
