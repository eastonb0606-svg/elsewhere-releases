package dev.elsewhere.gear3.s3.galesling;

import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;

/**
 * Gale Sling client (one line in Gear3Client.initClient). The slung stone uses the vanilla ThrownItemRenderer, which draws the
 * stone's item stack; that stack's {@code minecraft:item_model} is the pebble sprite (assets/elsewhere/items/gale_sling_stone.json,
 * gale_sling_gravel.json), so no custom model class is needed. The sling's whirl while charging is pure item-model data
 * (assets/elsewhere/items/gale_sling.json: using_item -> use_cycle over 4 whirl frames).
 */
public final class GaleSlingRenderers {
	private GaleSlingRenderers() {}

	public static void init() {
		EntityRendererRegistry.register(GaleSling.STONE, ThrownItemRenderer::new);
	}
}
