package dev.elsewhere.bossart.neferkhet;

import dev.elsewhere.overworld_bosses.OwbEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/** Registers Neferkhet's custom model layer and renderer (overrides the module's beta renderer). */
public final class NeferkhetArt {
	private NeferkhetArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/neferkhet"), "main");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, NeferkhetArtMesh::create);
		EntityRendererRegistry.register(OwbEntities.NEFERKHET, NeferkhetArtRenderer::new);
	}
}
