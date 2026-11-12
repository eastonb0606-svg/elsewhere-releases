package dev.elsewhere.kingdom_court.client.standing;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * POLISH P7: the Standing bar, -100 at the left to +100 at the right, in the five bands (Hostile -100..-50, Wary -49..-10, Neutral -9..29,
 * Friendly 30..69, Allied 70..100). The band you are in is lit, the others dimmed, with a tick at each boundary and the kingdom's marker at
 * your value. Plain fills only (a dozen per bar, no textures, no allocation).
 */
public final class StandingBar {
	private StandingBar() {}

	/** Band edges: the lowest value of each band, and 101 as the end. */
	private static final int[] LOW = {-100, -49, -9, 30, 70, 101};
	/** Hostile red, Wary amber, Neutral grey, Friendly green, Allied blue-gold: the same meaning in every kingdom. */
	private static final int[] COLOR = {0xFFB0362E, 0xFFC08A32, 0xFF8C8C84, 0xFF4E9E55, 0xFF4F86D6};

	static int band(int v) {
		for (int i = 4; i >= 0; i--) if (v >= LOW[i]) return i;
		return 0;
	}

	static int xOf(int x, int w, int v) {
		return x + Math.round(w * (Math.clamp(v, -100, 100) + 100) / 201F);
	}

	static int dark(int argb) {
		int r = ((argb >> 16) & 0xFF) * 2 / 5, g = ((argb >> 8) & 0xFF) * 2 / 5, b = (argb & 0xFF) * 2 / 5;
		return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
	}

	/** Draws the bar at (x, y), {@code w} by {@code h}, faded by {@code a} (0..1). */
	public static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, int standing, KingdomStyle s, float a) {
		int cur = band(standing);
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, KingdomStyle.fade(s.barEdge, a));
		for (int i = 0; i < 5; i++) {
			int x0 = xOf(x, w, LOW[i]), x1 = i == 4 ? x + w : xOf(x, w, LOW[i + 1]);
			int c = i == cur ? COLOR[i] : dark(COLOR[i]);
			g.fill(x0, y, x1, y + h, KingdomStyle.fade(c, a));
			if (i == cur && h >= 4) g.fill(x0, y, x1, y + 1, KingdomStyle.fade(0x40FFFFFF, a)); // a lit top edge on the current band
			if (i > 0) g.fill(x0, y, x0 + 1, y + h, KingdomStyle.fade(s.barEdge, a));
		}
		int mx = Math.min(x + w - 2, Math.max(x, xOf(x, w, standing) - 1));
		g.fill(mx - 1, y - 2, mx + 3, y + h + 2, KingdomStyle.fade(s.barEdge, a));
		g.fill(mx, y - 1, mx + 2, y + h + 1, KingdomStyle.fade(s.marker, a));
	}
}
