package dev.elsewhere.gear3.s3.starpiercer.client;

import dev.elsewhere.gear3.s3.starpiercer.Starpiercer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Starpiercer client set-up: the arrow renderer. Called once from {@code Gear3Client.initClient()}. (The bow's pull models are data: items/aphelion_starpiercer.json.) */
public final class StarpiercerVisuals {
	private StarpiercerVisuals() {}

	public static void init() {
		EntityRendererRegistry.register(Starpiercer.ARROW, StarpiercerArrowRenderer::new);
		StarpiercerZoom.register();
	}
}
