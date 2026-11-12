package dev.elsewhere.client;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.client.model.CreakingHuskModel;
import dev.elsewhere.client.model.EyeblossomWispModel;
import dev.elsewhere.client.model.MotherCreakModel;
import dev.elsewhere.client.model.RiftModel;
import dev.elsewhere.client.model.RootSpikeModel;
import dev.elsewhere.client.model.SelenneModel;
import dev.elsewhere.client.particle.ElsewhereParticles;
import dev.elsewhere.client.render.Renderers;
import dev.elsewhere.client.render.ReturnRiftRenderer;
import dev.elsewhere.core.gear.ArmorSets;
import dev.elsewhere.core.trip.PullPayload;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import dev.elsewhere.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** Client entrypoint: models, renderers, particles, the Return Rift beam, the trip "pull" vignette, set-bonus tooltips. */
public final class ElsewhereClient implements ClientModInitializer {
	private static int pullTicks;
	private static int pullTotal = 1;
	private static int pullColor;

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(Renderers.HUSK, CreakingHuskModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(Renderers.WISP, EyeblossomWispModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(Renderers.MOTHER, MotherCreakModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(Renderers.SELENNE, SelenneModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(Renderers.SPIKE, RootSpikeModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(Renderers.RIFT, RiftModel::createBodyLayer);

		EntityRendererRegistry.register(ModEntities.CREAKING_HUSK, Renderers.Husk::new);
		EntityRendererRegistry.register(ModEntities.EYEBLOSSOM_WISP, dev.elsewhere.client.render.WispSwarmRenderer::new); // swarm-as-one (s-spawngroups; a plain wisp draws as 2.1)
		EntityRendererRegistry.register(ModEntities.MOTHER_CREAK, Renderers.Mother::new);
		EntityRendererRegistry.register(ModEntities.SELENNE, Renderers.SelenneR::new);
		EntityRendererRegistry.register(ModEntities.ROOT_SPIKE, Renderers.Spike::new);
		EntityRendererRegistry.register(ModEntities.RIFT, Renderers.Rift::new);
		EntityRendererRegistry.register(ModEntities.SAP_TAPPER, net.minecraft.client.renderer.entity.WanderingTraderRenderer::new);
		BlockEntityRendererRegistry.register(ModBlocks.RETURN_RIFT_ENTITY, ctx -> new ReturnRiftRenderer());

		ParticleProviderRegistry.getInstance().register(ModParticles.RIFT_SPARK, ElsewhereParticles.SparkProvider::new);
		ParticleProviderRegistry.getInstance().register(ModParticles.RESIN_DUST, ElsewhereParticles.ResinProvider::new);
		ParticleProviderRegistry.getInstance().register(ModParticles.MOON_MOTE, ElsewhereParticles.MoonProvider::new);

		ClientPlayNetworking.registerGlobalReceiver(PullPayload.TYPE, (payload, ctx) -> {
			pullTicks = payload.ticks();
			pullTotal = Math.max(1, payload.ticks());
			pullColor = payload.color();
		});
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (pullTicks > 0) {
				pullTicks--;
			}
		});
		HudElementRegistry.addLast(Elsewhere.id("pull_vignette"), (graphics, delta) -> {
			if (pullTicks <= 0) {
				return;
			}
			// BALANCE.md §D: the vignette follows the shared screen-effects switch; the warning itself (overlay text,
			// whispers, particles, and the 3 s pull) always plays. Reduce motion draws it static instead of closing in.
			if (!HudSettings.screenEffects()) {
				return;
			}
			float k = HudSettings.reduceMotion() ? 0.35F : 1.0F - pullTicks / (float)pullTotal;
			int w = graphics.guiWidth();
			int h = graphics.guiHeight();
			int alpha = (int)(Mth.clamp(0.25F + k * 0.6F, 0.0F, 0.85F) * 255.0F);
			int edge = (alpha << 24) | (pullColor & 0xFFFFFF);
			int clear = pullColor & 0xFFFFFF;
			int band = (int)(h * (0.18F + 0.22F * k));
			graphics.fillGradient(0, 0, w, band, edge, clear);
			graphics.fillGradient(0, h - band, w, h, clear, edge);
		});
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> lines.addAll(ArmorSets.tooltip(stack, Minecraft.getInstance().player)));
		dev.elsewhere.client.v2.V2Client.init(); // 2.0: companions, Bog Wight, trophies, Atlas
	}
}
