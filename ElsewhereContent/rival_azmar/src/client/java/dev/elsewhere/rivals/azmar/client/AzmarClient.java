package dev.elsewhere.rivals.azmar.client;

import dev.elsewhere.rivals.azmar.AzmarSoldiers;
import dev.elsewhere.rivals.azmar.boss.AzmarBosses;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.EntityType;

/** Client entry (the merge calls {@link #initClient()}): duelist boss renderers plus the 3D headpiece ornaments on the bosses and elite soldiers. */
public class AzmarClient implements ClientModInitializer {
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void initClient() {
		if (done) return;
		done = true;
		dev.elsewhere.duelist.DuelistClient.initClient();
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(dev.elsewhere.rivals.azmar.AzmarFalcon.TYPE, FalconRenderer::new);
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, ctx) -> {
			// 26.3: every AvatarRenderState (bosses, soldiers, players) is drawn by the vanilla avatar renderers, so the layer goes on the player renderers
			if (type != net.minecraft.world.entity.EntityTypes.PLAYER && !ornamented(type)) return;
			if (renderer.getModel() instanceof HeadedModel) helper.register(new OrnamentLayer((RenderLayerParent) renderer));
		});
	}

	private static boolean ornamented(EntityType<?> t) {
		return t == AzmarBosses.SULTANA || t == AzmarBosses.WAZIR || t == AzmarBosses.FALCONER || t == AzmarBosses.AMIR || t == AzmarSoldiers.TYPE;
	}
}
