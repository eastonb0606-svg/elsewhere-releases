package dev.elsewhere.bossart.gulpmother;

import dev.elsewhere.overworld_bosses.OwbEntities;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.Identifier;

/** Registers Old Gulpmother's custom model layer and renderer (overrides the module's beta renderer). */
public final class GulpmotherArt {
	private GulpmotherArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/gulpmother"), "main");

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, GulpmotherArtMesh::create);
		EntityRendererRegistry.register(OwbEntities.GULPMOTHER, GulpmotherArtRenderer::new);
	}
}
