package dev.elsewhere.gear3.s3.totems.client;

import dev.elsewhere.gear3.s3.totems.Totems;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** Totem client: the three model layers and renderers. Called once from Gear3Client.initClient() (one line). */
public final class TotemRenderers {
	private TotemRenderers() {}

	private static boolean done;

	public static void init() {
		if (done || Totems.BRAMBLE == null) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(TotemModel.BRAMBLE, TotemModel::bramble);
		ModelLayerRegistry.registerModelLayer(TotemModel.SENTRY, TotemModel::sentry);
		ModelLayerRegistry.registerModelLayer(TotemModel.OBELISK, TotemModel::obelisk);
		EntityRendererRegistry.register(Totems.BRAMBLE, ctx -> new TotemRenderer<>(ctx, TotemModel.Kind.BRAMBLE));
		EntityRendererRegistry.register(Totems.SENTRY, ctx -> new TotemRenderer<>(ctx, TotemModel.Kind.SENTRY));
		EntityRendererRegistry.register(Totems.OBELISK, ctx -> new TotemRenderer<>(ctx, TotemModel.Kind.OBELISK));
	}
}
