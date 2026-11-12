package dev.elsewhere.bossart.strawgrim;

import dev.elsewhere.overworld_bosses.OwbEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/** Registers Strawgrim's custom model layer and renderer (overrides the module's beta renderer). */
public final class StrawgrimArt {
	private StrawgrimArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/strawgrim"), "main");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, StrawgrimArtMesh::create);
		EntityRendererRegistry.register(OwbEntities.STRAWGRIM, StrawgrimArtRenderer::new);
	}
}
