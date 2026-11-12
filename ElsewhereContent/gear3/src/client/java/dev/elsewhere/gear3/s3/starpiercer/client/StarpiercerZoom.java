package dev.elsewhere.gear3.s3.starpiercer.client;

import dev.elsewhere.gear3.s3.starpiercer.Starpiercer;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Starpiercer longbow zoom, the same feel as the Roc-Feather Longbow (user decision there): while drawing in first person
 * the zoom eases in with the square of the draw over 1.6 s, to 1.5x at full draw, or 2x while also sneaking at full draw
 * (blended over 0.25 s). Returned as an FOV modifier (1 / zoom) that {@code StarpiercerZoomMixin} multiplies into vanilla's,
 * so the "FOV Effects" slider scales it. Off with the shared client toggle {@code longbowZoom} (the Roc-Feather's key, in
 * config/elsewhere-client.json), read through verdigris's ClientSettingsStub when that module is present.
 */
public final class StarpiercerZoom {
	private StarpiercerZoom() {}

	public static final String KEY = "longbowZoom";
	private static float sneakBlend;
	private static long lastNanos;
	private static boolean settingsMissing;

	public static void register() {
		try {
			dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.registerToggle(KEY, true);
		} catch (LinkageError e) {
			settingsMissing = true;
		}
	}

	public static boolean enabled() {
		if (settingsMissing) return true;
		try {
			return dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub.toggle(KEY, true);
		} catch (LinkageError e) {
			settingsMissing = true;
			return true;
		}
	}

	/** The FOV modifier to multiply in (1 = none). */
	public static float factor(AbstractClientPlayer player, boolean firstPerson) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
		lastNanos = now;
		if (!firstPerson || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof dev.elsewhere.gear3.s3.starpiercer.StarpiercerItem) || !enabled()) {
			sneakBlend = 0;
			return 1.0F;
		}
		float progress = Starpiercer.drawProgress(player.getTicksUsingItem());
		boolean sneakFull = player.isShiftKeyDown() && progress >= 1.0F;
		float step = dt / Starpiercer.ZOOM_SNEAK_BLEND_SECONDS;
		sneakBlend = sneakFull ? Math.min(1, sneakBlend + step) : Math.max(0, sneakBlend - step);
		return 1.0F / Starpiercer.zoom(progress, sneakBlend);
	}
}
