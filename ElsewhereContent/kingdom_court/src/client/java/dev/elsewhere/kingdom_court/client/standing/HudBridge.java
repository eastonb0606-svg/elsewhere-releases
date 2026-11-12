package dev.elsewhere.kingdom_court.client.standing;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * POLISH rule 7: the Standing chip is a meter of the shared client settings ({@code dev.elsewhere.hud.ElsewhereHud}, which this module does not
 * compile against): {@code /elsewhere_hud standing auto|always|hidden}, the "Toggle Elsewhere HUD" key and {@code config/elsewhere-client.json}
 * key {@code standingChip}. Looked up once; without the class (this module alone in dev) the chip simply shows.
 */
final class HudBridge {
	private HudBridge() {}

	private static MethodHandle shouldShow;
	private static boolean tried;

	static void init() {
		if (tried) return;
		tried = true;
		try {
			Class<?> hud = Class.forName("dev.elsewhere.hud.ElsewhereHud");
			MethodHandles.Lookup lk = MethodHandles.publicLookup();
			lk.findStatic(hud, "registerMeter", MethodType.methodType(void.class, String.class, String.class)).invoke("standing", "standingChip");
			shouldShow = lk.findStatic(hud, "shouldShow", MethodType.methodType(boolean.class, String.class, boolean.class));
		} catch (Throwable t) {
			shouldShow = null;
		}
	}

	/** True unless the player hid the chip (or every Elsewhere meter). Auto means "when looking at a ruler", which the caller already knows. */
	static boolean show() {
		if (shouldShow == null) return true;
		try {
			return (boolean) shouldShow.invokeExact("standing", true);
		} catch (Throwable t) {
			shouldShow = null;
			return true;
		}
	}
}
