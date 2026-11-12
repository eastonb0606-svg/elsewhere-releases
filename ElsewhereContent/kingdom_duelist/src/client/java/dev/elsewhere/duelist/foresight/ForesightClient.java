package dev.elsewhere.duelist.foresight;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.jspecify.annotations.Nullable;

/**
 * Client side of the B5 foresight gate: the server's flag for this player (does it get boss attack hints?). null until
 * the server has sent one (a server without the gate). It drives boss_ui's attack-line gate ({@code BossBarFx.attackLineGate}).
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
		// boss_ui (s-p1-bossbar) hides every boss bar's attack/cast line while its gate says false. boss_ui is another module,
		// so the gate is set by reflection; without boss_ui there is nothing to gate.
		try {
			Class.forName("dev.elsewhere.boss_ui.BossBarFx").getField("attackLineGate").set(null, (java.util.function.BooleanSupplier) ForesightClient::showsHints);
		} catch (ReflectiveOperationException | LinkageError | ClassCastException | IllegalArgumentException e) {
			// boss_ui absent or changed: the server-side gate still holds
		}
	}

	/** The server's flag: true = this player gets attack hints, false = not, null = no flag received. */
	public static @Nullable Boolean state() {
		return state;
	}

	/** boss_ui's attack-line gate: hidden only when the server said this player has no foresight gear. No allocation (read per bar per frame). */
	public static boolean showsHints() {
		return !Boolean.FALSE.equals(state);
	}
}
