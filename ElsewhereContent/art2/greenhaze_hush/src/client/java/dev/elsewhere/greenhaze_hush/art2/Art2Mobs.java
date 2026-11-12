package dev.elsewhere.greenhaze_hush.art2;

import dev.elsewhere.hush.HushEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** art2 mob models for this module. Called once from GreenhazeHushClient.initClient(). */
@Environment(EnvType.CLIENT)
public final class Art2Mobs {
	private Art2Mobs() {}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(EchoStalkerArt.LAYER, EchoStalkerArt::createBodyLayer);
		EntityRendererRegistry.register(HushEntities.ECHO_STALKER, EchoStalkerArt.Renderer::new);
	}
}
