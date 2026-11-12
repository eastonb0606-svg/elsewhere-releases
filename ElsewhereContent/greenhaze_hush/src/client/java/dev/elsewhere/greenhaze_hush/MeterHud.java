package dev.elsewhere.greenhaze_hush;

import dev.elsewhere.greenhaze.Haze;
import dev.elsewhere.hush.Resonance;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import dev.elsewhere.stub.greenhaze_hush.HudStub;

/**
 * HUD bars: the Haze bar (Greenhaze, can host Green Leaf's high) and the Resonance waveform bar (Hush).
 * Each is toggleable per player (Auto / Always / Hidden, meters {@code haze} and {@code resonance}, shared Elsewhere client
 * settings); Auto = in its dimension or the value is above 0.5. Reduce motion draws the waveform without its animation.
 */
public final class MeterHud {
	private MeterHud() {}

	private static int hazeBand, resBand;

	/** Danger band: 0 below the first threshold, then 1, 2. */
	private static int band(float v, float a, float b) {
		return v >= b ? 2 : v >= a ? 1 : 0;
	}

	/** Hidden meters still get one action-bar line when the value climbs into a danger band. */
	private static void dangerLines(Minecraft mc, float haze, float res) {
		int hb = band(haze, 60F, 85F), rb = band(res, 50F, 90F);
		if (hb > hazeBand && !HudStub.shouldShow("haze", true)) {
			mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("Haze " + Math.round(haze) + "% (" + dev.elsewhere.greenhaze.Haze.label(haze) + ")")
				.withStyle(net.minecraft.ChatFormatting.GREEN), false);
		}
		if (rb > resBand && !HudStub.shouldShow("resonance", true)) {
			mc.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal(rb == 2 ? "Resonance " + Math.round(res) + "%: something below is waking" : "Resonance " + Math.round(res) + "%: the stalkers can hear you")
				.withStyle(net.minecraft.ChatFormatting.RED), false);
		}
		hazeBand = hb;
		resBand = rb;
	}

	/** Vertical pitch of one stacked meter: its label (10) + the bar (7) + a gap. The old 14 made the Haze label overlap the Resonance bar (QA #2). */
	public static final int PITCH = dev.elsewhere.greenhaze_hush.HudLayout.PITCH;

	/** The Resonance meter is readable anywhere while the Hushweave Hood is worn (N1 hood perk). */
	private static boolean hoodWorn(Minecraft mc) {
		return mc.player != null && mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(dev.elsewhere.hush.HushItems.HUSHWEAVE_HOOD);
	}

	/** Which of the two meters is drawn now, in stack order (bottom first): Haze, then Resonance. Public for the self-test of the layout. */
	public static boolean[] visible(Minecraft mc, float haze, float res) {
		return new boolean[] {
			HudStub.shouldShow("haze", GreenhazeHushClient.inGreenhaze() || haze > 0.5F),
			HudStub.shouldShow("resonance", GreenhazeHushClient.inHush() || res > 0.5F || hoodWorn(mc))};
	}

	/** y of the meter at stack slot {@code slot} (0 = lowest); slots never overlap because {@link #PITCH} covers label + bar. */
	public static int slotY(int guiHeight, int slot) {
		return dev.elsewhere.greenhaze_hush.HudLayout.slotY(guiHeight, slot);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		float haze = mc.player.getAttachedOrElse(Haze.HAZE, 0F);
		float res = mc.player.getAttachedOrElse(Resonance.RESONANCE, 0F);
		dangerLines(mc, haze, res);
		int w = g.guiWidth(), h = g.guiHeight();
		int x = w / 2 + 96;
		boolean[] vis = visible(mc, haze, res);
		int slot = 0;
		if (vis[0]) {
			bar(g, x, slotY(h, slot++), haze / 100F, 0xFF7CC24A, 0xFFB58BE8, "Haze " + Math.round(haze) + "% " + Haze.label(haze), mc);
		}
		if (vis[1]) {
			int x0 = x, y0 = slotY(h, slot++);
			g.fill(x0 - 1, y0 - 1, x0 + 81, y0 + 7, 0xC0000000);
			int filled = Math.round(80 * Math.min(1F, res / 100F));
			long t = mc.level == null || HudStub.reduceMotion() ? 0 : mc.level.getGameTime();
			for (int i = 0; i < 80; i++) {
				double amp = 1 + 2.5 * res / 100.0;
				int hgt = (int) Math.round(3 + Math.sin(i * 0.5 + t * 0.3) * amp * (i < filled ? 1 : 0.3));
				int col = i < filled ? (res >= 50 ? 0xFFFF5A5A : 0xFF3AE8FF) : 0xFF1B3A40;
				g.fill(x0 + i, y0 + 3 - Math.max(0, hgt / 2), x0 + i + 1, y0 + 3 + Math.max(1, (hgt + 1) / 2), col);
			}
			g.text(mc.font, "Resonance " + Math.round(res) + "%", x0, y0 - 10, res >= 50 ? 0xFFFF7070 : 0xFF8FF4FF, true);
		}
	}

	private static void bar(GuiGraphicsExtractor g, int x, int y, float frac, int c1, int c2, String label, Minecraft mc) {
		g.fill(x - 1, y - 1, x + 81, y + 6, 0xC0000000);
		g.fillGradient(x, y, x + Math.round(80 * Math.max(0, Math.min(1, frac))), y + 5, c2, c1);
		g.text(mc.font, label, x, y - 10, 0xFFD8F5B0, true);
	}
}
