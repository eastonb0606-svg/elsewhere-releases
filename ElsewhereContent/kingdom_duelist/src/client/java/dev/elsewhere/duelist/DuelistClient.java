package dev.elsewhere.duelist;

import dev.elsewhere.duelist.boss.HumanBoss;
import dev.elsewhere.duelist.client.DuelistRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.EntityType;

/** Client init: a player-model renderer for every boss type built on HumanBoss, and the thrown-gear renderer. */
public final class DuelistClient {
	private DuelistClient() {}

	private static boolean done;

	public static void initClient() {
		if (done) return;
		done = true;
		DuelistContent.init();
		for (EntityType<? extends HumanBoss> t : DuelistContent.BOSS_TYPES) registerRenderer(t);
		EntityRendererRegistry.register(DuelistContent.PROJECTILE, ThrownItemRenderer::new);
		// 3.0 B4 phase aura: 26.3 draws every boss's AvatarRenderState on the vanilla avatar renderers, so the layer goes there (bosses only, see PhaseAuraLayer)
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			if (type == net.minecraft.world.entity.EntityTypes.PLAYER && renderer.getModel() instanceof net.minecraft.client.model.player.PlayerModel)
				helper.register(new dev.elsewhere.duelist.client.anim.PhaseAuraLayer(aura(renderer)));
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static net.minecraft.client.renderer.entity.RenderLayerParent<net.minecraft.client.renderer.entity.state.AvatarRenderState, net.minecraft.client.model.player.PlayerModel> aura(Object renderer) {
		return (net.minecraft.client.renderer.entity.RenderLayerParent) renderer;
	}

	/** Registers the duelist renderer (player model, armor, held items, wind-up poses) for a boss type registered after init. */
	public static <T extends HumanBoss> void registerRenderer(EntityType<T> type) {
		EntityRendererRegistry.register(type, DuelistRenderer<T>::new);
	}
}
