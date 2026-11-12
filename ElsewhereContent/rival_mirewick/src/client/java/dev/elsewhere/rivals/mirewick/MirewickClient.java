package dev.elsewhere.rivals.mirewick;

import dev.elsewhere.duelist.DuelistClient;
import dev.elsewhere.kingdom.client.KingdomClient;
import dev.elsewhere.rivals.mirewick.boss.Magnate;
import net.fabricmc.api.ClientModInitializer;

/** Client init: the player-model soldier renderer for the two unique soldiers, the duelist renderer for the five Magnates. */
public final class MirewickClient implements ClientModInitializer {
	private static boolean done;

	@Override
	public void onInitializeClient() {
		initClient();
	}

	public static void initClient() {
		if (done) return;
		done = true;
		KingdomClient.registerSoldierRenderer(MirewickUnits.BOG_WARDEN);
		KingdomClient.registerSoldierRenderer(MirewickUnits.MIRE_TRAPPER);
		for (Magnate m : Magnate.values()) DuelistClient.registerRenderer(m.type);
	}
}
