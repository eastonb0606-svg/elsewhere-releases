package dev.elsewhere.kingdom_court.client;

import dev.elsewhere.kingdom_court.ruler.Ruler;
import net.fabricmc.api.ClientModInitializer;

/** Client init: the kingdom soldier renderer (player model, trimmed armour, crown, tabard) plus a cape in each ruler's colours for the six rulers. */
public final class CourtClient implements ClientModInitializer {
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	public static void initClient() {
		if (done) return;
		done = true;
		for (Ruler r : Ruler.values()) if (r.type != null) net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(r.type, CourtRulerRenderer::new);
		CourtRulerRenderer.registerCapeLayer();
		dev.elsewhere.kingdom_court.client.standing.StandingClient.init(); // s-p7-standing: POLISH P7 trader dialogue + Standing chip
	}
}
