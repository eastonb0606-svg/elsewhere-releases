package dev.elsewhere.duelist.foresight;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the B5 foresight gate: the server's flag for this player (does it get boss attack hints?). null until
 * the server has sent one (a server without the gate). boss_ui reads {@link #state()} to draw a marked move-name line
 * only when it is true.
 */
public final class ForesightClient {
	private ForesightClient() {}

	private static @Nullable Boolean state;
	private static boolean inited;

	public static synchronized void init() {
		if (inited) return;
		inited = true;
		ClientPlayNetworking.registerGlobalReceiver(ForesightPayload.TYPE, (payload, ctx) -> state = payload.sees());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> state = null);
	}

	/** The server's flag: true = this player gets attack hints, false = not, null = no flag received. */
	public static @Nullable Boolean state() {
		return state;
	}
}
