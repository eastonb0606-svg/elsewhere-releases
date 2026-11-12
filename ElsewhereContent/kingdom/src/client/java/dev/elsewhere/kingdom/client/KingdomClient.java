package dev.elsewhere.kingdom.client;

import dev.elsewhere.kingdom.KingdomEntities;
import dev.elsewhere.kingdom.KingdomSoldier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.EntityType;

/** Client init: the tabard model layer and a {@link KingdomSoldierRenderer} for every registered soldier type. */
public final class KingdomClient implements ClientModInitializer {
	public static final ModelLayerLocation TABARD = new ModelLayerLocation(dev.elsewhere.kingdom.KingdomContent.id("kingdom_tabard"), "main");
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	public static void initClient() {
		if (done) return;
		done = true;
		KingdomClientConfig.load();
		ModelLayerRegistry.registerModelLayer(TABARD, () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1.08F), 0.0F), 64, 32));
		// 26.3 draws every AvatarRenderState with vanilla's AvatarRenderer, so our layers go onto it (see AvatarLayers)
		AvatarLayers.add((parent, models) -> new TabardLayer(parent, models));
		for (EntityType<? extends KingdomSoldier> t : KingdomEntities.TYPES.keySet()) registerSoldierRenderer(t);
		EntityRendererRegistry.register(KingdomEntities.LONGSHIP, HrafnholtLongshipRenderer::new);
	}

	/** Registers the soldier renderer (player model, armor, held items, tinted tabard) for a soldier type, e.g. a Lord. */
	public static <T extends KingdomSoldier> void registerSoldierRenderer(EntityType<T> type) {
		EntityRendererRegistry.register(type, KingdomSoldierRenderer::new);
	}
}
