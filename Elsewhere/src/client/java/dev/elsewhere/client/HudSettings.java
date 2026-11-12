package dev.elsewhere.client;

import java.lang.reflect.Method;

/**
 * The shared per-player display switches from the arsenal module's {@code dev.elsewhere.hud.ElsewhereHud}
 * ({@code screenEffects()}, {@code reduceMotion()}; BALANCE.md §D), read by reflection so the core builds and runs
 * without it (defaults: effects on, full motion).
 */
public final class HudSettings {
	private HudSettings() {
	}

	private static boolean looked;
	private static Method screenEffects;
	private static Method reduceMotion;

	private static void look() {
		if (looked) {
			return;
		}
		looked = true;
		try {
			Class<?> hud = Class.forName("dev.elsewhere.hud.ElsewhereHud");
			screenEffects = hud.getMethod("screenEffects");
			reduceMotion = hud.getMethod("reduceMotion");
		} catch (ReflectiveOperationException | LinkageError e) {
			screenEffects = null;
			reduceMotion = null;
		}
	}

	private static boolean call(final Method m, final boolean fallback) {
		if (m == null) {
			return fallback;
		}
		try {
			return (Boolean)m.invoke(null);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return fallback;
		}
	}

	/** Elsewhere screen effects (vignettes, fog pulses) on? */
	public static boolean screenEffects() {
		look();
		return call(screenEffects, true);
	}

	/** Reduce motion: draw effects static instead of growing/pulsing. */
	public static boolean reduceMotion() {
		look();
		return call(reduceMotion, false);
	}
}
