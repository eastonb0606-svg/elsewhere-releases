package dev.elsewhere.verdigris_ironpeak.client.art2;

import dev.elsewhere.ironpeak.IronpeakEntities;
import dev.elsewhere.verdigris_sea.VerdigrisEntities;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

/** art2 mob models for this module. Called once from VerdigrisIronpeakClient.initClient(). */
@Environment(EnvType.CLIENT)
public final class Art2Mobs {
	private Art2Mobs() {}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(IronclanRaiderArt.LAYER, IronclanRaiderArt::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(ClockworkDrownedArt.LAYER, ClockworkDrownedArt::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(BarnacleGolemArt.LAYER, BarnacleGolemArt::createBodyLayer);
		EntityRendererRegistry.register(IronpeakEntities.IRONCLAN_RAIDER, IronclanRaiderArt.Renderer::new);
		EntityRendererRegistry.register(VerdigrisEntities.CLOCKWORK_DROWNED, ClockworkDrownedArt.Renderer::new);
		EntityRendererRegistry.register(VerdigrisEntities.BARNACLE_GOLEM, BarnacleGolemArt.Renderer::new);
	}
}
