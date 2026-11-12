package dev.elsewhere.verdigris_ironpeak.client;

import dev.elsewhere.ironpeak.IronpeakItems;
import dev.elsewhere.ironpeak.RocFeatherLongbowItem;
import dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import net.minecraft.client.player.AbstractClientPlayer;

/**
 * Roc-Feather Longbow zoom (user decision, ARSENAL.md §6): while drawing it in first person the zoom eases in with the
 * square of the draw progress over the 1.4 s draw, to 1.5x at full draw, or 2x while also sneaking at full draw
 * (blending over ~0.25 s; never more). Returned as an FOV modifier (1 / zoom) that the mixin multiplies into vanilla's,
 * so the "FOV Effects" slider scales it. Vanilla's own bow zoom only applies to {@code minecraft:bow}, so this replaces
 * it. Toggle: `longbowZoom` (default on) through the shared client settings (stub until the merge).
 */
public final class LongbowZoom {
	public static final String KEY = "longbowZoom";
	private static float sneakBlend;
	private static long lastNanos;

	private LongbowZoom() {}

	public static void register() {
		ClientSettingsStub.registerToggle(KEY, true);
	}

	public static boolean enabled() {
		return ClientSettingsStub.toggle(KEY, true);
	}

	/** The FOV modifier to multiply in (1 = none). */
	public static float factor(AbstractClientPlayer player, boolean firstPerson) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.1F, (now - lastNanos) / 1.0E9F);
		lastNanos = now;
		if (!firstPerson || !player.isUsingItem() || !player.getUseItem().is(IronpeakItems.ROC_FEATHER_LONGBOW) || !enabled()) {
			sneakBlend = 0;
			return 1.0F;
		}
		float progress = RocFeatherLongbowItem.drawProgress(player.getTicksUsingItem());
		boolean sneakFull = player.isShiftKeyDown() && progress >= 1.0F;
		float step = dt / GearStats.LONGBOW_ZOOM_SNEAK_BLEND_SECONDS;
		sneakBlend = sneakFull ? Math.min(1, sneakBlend + step) : Math.max(0, sneakBlend - step);
		return 1.0F / RocFeatherLongbowItem.zoom(progress, sneakBlend);
	}
}
