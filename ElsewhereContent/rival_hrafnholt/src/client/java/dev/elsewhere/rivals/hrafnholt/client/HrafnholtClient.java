package dev.elsewhere.rivals.hrafnholt.client;

import dev.elsewhere.kingdom.client.KingdomClient;
import dev.elsewhere.rivals.hrafnholt.HrafnholtUnits;
import net.fabricmc.api.ClientModInitializer;

/** Client init: soldier renderers for the two unique units (player model, trimmed armor, tabard; skin from the unit file). */
public final class HrafnholtClient implements ClientModInitializer {
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	public static void initClient() {
		if (done) return;
		done = true;
		KingdomClient.registerSoldierRenderer(HrafnholtUnits.BERSERKER);
		KingdomClient.registerSoldierRenderer(HrafnholtUnits.WOLF_HANDLER);
		dev.elsewhere.duelist.DuelistClient.initClient();
		for (var t : dev.elsewhere.rivals.hrafnholt.boss.HrafnholtBosses.types()) net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(t, HrafnholtBossRenderer::new);
	}
}
