package dev.elsewhere.client.v2;

import dev.elsewhere.core.atlas.AtlasPayload;
import dev.elsewhere.registry.ModBlocks;
import dev.elsewhere.registry.ModEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Client registrations for the 2.0 core content: companion and Bog Wight renderers, trophy statues, the Atlas screen. */
public final class V2Client {
	private V2Client() {
	}

	public static void init() {
		EntityRendererRegistry.register(ModEntities.HOLLOW_STAG, V2Renderers.Stag::new);
		EntityRendererRegistry.register(ModEntities.DOCILE_SCARAB, V2Renderers.Scarab::new);
		EntityRendererRegistry.register(ModEntities.HAZE_FERRET, V2Renderers.Ferret::new);
		EntityRendererRegistry.register(ModEntities.BOG_WIGHT, V2Renderers.Wight::new);
		BlockEntityRendererRegistry.register(ModBlocks.BOSS_TROPHY_ENTITY, TrophyRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(AtlasPayload.TYPE, (payload, ctx) -> ctx.client().gui.setScreen(new AtlasScreen(payload)));
	}
}
