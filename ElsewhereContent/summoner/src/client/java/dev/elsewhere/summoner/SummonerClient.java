package dev.elsewhere.summoner;

import dev.elsewhere.minion.MinionEntity;
import dev.elsewhere.summoner.client.MinionModels;
import dev.elsewhere.summoner.client.MinionRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;

/** Client entry: minion models/renderers and the owner-only amethyst outline. */
public final class SummonerClient {
	private SummonerClient() {}

	private static boolean done;

	public static void initClient() {
		if (done) return;
		done = true;
		ModelLayerRegistry.registerModelLayer(MinionModels.ROOTLING, MinionModels::rootling);
		ModelLayerRegistry.registerModelLayer(MinionModels.PAPER_KITE, MinionModels::paperKite);
		ModelLayerRegistry.registerModelLayer(MinionModels.BRASSBACK, MinionModels::brassback);
		EntityRendererRegistry.register(SummonerEntities.ROOTLING, c -> new MinionRenderer<>(c, new MinionModels.RootlingModel(c.bakeLayer(MinionModels.ROOTLING)), "rootling", 0.3F, 1F));
		EntityRendererRegistry.register(SummonerEntities.PAPER_KITE, c -> new MinionRenderer<>(c, new MinionModels.PaperKiteModel(c.bakeLayer(MinionModels.PAPER_KITE)), "paper_kite", 0F, 1F));
		EntityRendererRegistry.register(SummonerEntities.BRASSBACK, c -> new MinionRenderer<>(c, new MinionModels.BrassbackModel(c.bakeLayer(MinionModels.BRASSBACK)), "brassback", 0.4F, 1F));
		// placeholder: the Sporeling reuses the Rootling model and texture (art phase)
		EntityRendererRegistry.register(SummonerEntities.SPORELING, c -> new MinionRenderer<>(c, new MinionModels.RootlingModel(c.bakeLayer(MinionModels.ROOTLING)), "sporeling", 0.3F, 0.85F));
		MinionEntity.clientIsLocalPlayer = u -> {
			var p = Minecraft.getInstance().player;
			return p != null && p.getUUID().equals(u) && dev.elsewhere.summoner.client.MinionOutlineSetting.enabled();
		};
	}
}
