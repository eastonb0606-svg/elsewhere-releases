package dev.elsewhere.petalwind_glasswaste.client;

import dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Glasswaste screen effects, drawn under the HUD: a warm heat vignette with rising shimmer bands from Heat 60 up, and a
 * white glare wash while the server reports glare (bright glass underfoot without a helmet; the Blindness pulses are
 * gameplay and stay). Per-player settings in config/elsewhere-client.json: {@code screenEffects} (default on) turns all
 * of it off; {@code reduceMotion} (default off) drops the shimmer and vignette (BALANCE §D2: shimmer is pure motion) and
 * keeps the glare wash as a static tint.
 */
public final class HeatEffects {
	private HeatEffects() {}

	public static final String EFFECTS_KEY = "screenEffects", REDUCE_MOTION_KEY = "reduceMotion";
	private static final int SHIMMER_FROM = 60;

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!ClientSettingsStub.toggle(EFFECTS_KEY, true)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator() || !PetalwindGlasswasteClient.showHeat) return;
		boolean still = ClientSettingsStub.toggle(REDUCE_MOTION_KEY, false);
		float t = (mc.player.tickCount + delta.getGameTimeDeltaPartialTick(false)) / 20.0F;
		int w = g.guiWidth(), h = g.guiHeight();
		int heat = PetalwindGlasswasteClient.heat;
		if (heat >= SHIMMER_FROM && !still) { // BALANCE §D2: heat shimmer is pure motion, so reduce motion removes it
			float k = Mth.clamp((heat - SHIMMER_FROM) / (100.0F - SHIMMER_FROM), 0.0F, 1.0F);
			float pulse = 0.8F + 0.2F * Mth.sin(t * 2.4F);
			vignette(g, w, h, Math.round(110 * k * pulse), 0xFF6A1E);
			shimmer(g, w, h, t, k);
		}
		if (PetalwindGlasswasteClient.glare) {
			float pulse = still ? 0.6F : 0.45F + 0.35F * Math.abs(Mth.sin(t * 1.7F));
			g.fill(0, 0, w, h, argb(Math.round(70 * pulse), 0xFFF8E0));
		}
	}

	private static void vignette(GuiGraphicsExtractor g, int w, int h, int alpha, int rgb) {
		if (alpha <= 0) return;
		int band = Math.max(12, h / 5);
		g.fillGradient(0, 0, w, band, argb(alpha, rgb), argb(0, rgb));
		g.fillGradient(0, h - band, w, h, argb(0, rgb), argb(alpha, rgb));
		int steps = 8, side = Math.max(12, w / 8);
		for (int i = 0; i < steps; i++) {
			int a = alpha * (steps - i) / (steps * 2);
			int x0 = side * i / steps, x1 = side * (i + 1) / steps;
			g.fill(x0, 0, x1, h, argb(a, rgb));
			g.fill(w - x1, 0, w - x0, h, argb(a, rgb));
		}
	}

	/** Thin translucent bands drifting upward: a cheap heat-haze cue. */
	private static void shimmer(GuiGraphicsExtractor g, int w, int h, float t, float k) {
		int bands = 5;
		for (int i = 0; i < bands; i++) {
			float phase = (t * 0.35F + i / (float) bands) % 1.0F;
			int y = Math.round(h * (1.0F - phase));
			int a = Math.round(26 * k * Mth.sin(phase * Mth.PI));
			if (a <= 0) continue;
			int wob = Math.round(6 * Mth.sin(t * 3.0F + i));
			g.fill(wob, y, w + wob, y + 2, argb(a, 0xFFD9A0));
		}
	}

	private static int argb(int a, int rgb) {
		return (Mth.clamp(a, 0, 255) << 24) | (rgb & 0xFFFFFF);
	}
}
