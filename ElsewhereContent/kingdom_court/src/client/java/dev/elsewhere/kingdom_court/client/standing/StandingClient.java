package dev.elsewhere.kingdom_court.client.standing;

import dev.elsewhere.kingdom_court.Overhaul3;
import dev.elsewhere.kingdom_court.standing.StandingNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * POLISH P7 client entry (one line in {@code CourtClient.initClient()}): the trader dialogue and the Standing chip. The payload types are
 * registered by {@code CourtContent.init()} on both sides; here are the receivers, the chip's HUD element and its entity hooks.
 * Kill switches: {@code -Delsewhere.overhaul3.trader_screen=false} and {@code -Delsewhere.overhaul3.standing_chip=false}.
 */
public final class StandingClient {
	private StandingClient() {}

	private static boolean done;

	public static void init() {
		if (done) return;
		done = true;
		ClientPlayNetworking.registerGlobalReceiver(StandingNet.TraderOpen.TYPE, (payload, ctx) -> {
			StandingCache.accept(payload);
			if (Overhaul3.TRADER_SCREEN) ctx.client().gui.setScreen(new TraderScreen(payload));
		});
		ClientPlayNetworking.registerGlobalReceiver(StandingNet.Reply.TYPE, (payload, ctx) -> StandingCache.accept(payload));
		if (Overhaul3.STANDING_CHIP) StandingChip.init();
	}
}
