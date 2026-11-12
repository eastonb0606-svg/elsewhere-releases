package dev.elsewhere.verdigris_ironpeak.client;

import dev.elsewhere.stub.verdigris_ironpeak.ClientSettingsStub;
import dev.elsewhere.verdigris_ironpeak.Fx;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Ironpeak's Altitude gauge: a vertical bar on the right edge with the current Y, turning red in the Thin Air (Y > 1100).
 * Toggleable per player (`altitudeMeter`): Auto (default) = shown in Ironpeak; Always = shown in every dimension;
 * Hidden = never (`hudHidden` also hides it).
 */
public class AltitudeHud implements HudElement {
	public static final String KEY = "altitudeMeter";

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		ClientSettingsStub.Mode mode = ClientSettingsStub.meter(KEY);
		if (mode == ClientSettingsStub.Mode.HIDDEN || (mode == ClientSettingsStub.Mode.AUTO && mc.level.dimension() != Fx.IRONPEAK)) return;
		int w = g.guiWidth(), h = g.guiHeight();
		int barH = 100, x = w - 14, y0 = h / 2 - barH / 2;
		double alt = mc.player.getY();
		double f = Math.max(0, Math.min(1, (alt + 64) / 1536.0));
		boolean thin = alt > 1100;
		g.fill(x - 1, y0 - 1, x + 7, y0 + barH + 1, 0xA0000000);
		int fillH = (int) (barH * f);
		g.fill(x, y0 + barH - fillH, x + 6, y0 + barH, thin ? 0xFFE04040 : 0xFF80C0FF);
		int mark = y0 + barH - (int) (barH * (1100 + 64) / 1536.0);
		g.fill(x - 3, mark, x + 9, mark + 1, 0xFFFFFFFF);
		String label = (int) alt + "";
		g.text(mc.font, label, x + 3 - mc.font.width(label) / 2 - 8, y0 + barH + 4, thin ? 0xFFFF6060 : 0xFFFFFFFF);
		if (thin) {
			String t = Component.translatable("hud.elsewhere.thin_air").getString();
			g.text(mc.font, t, x - mc.font.width(t) + 6, y0 - 12, 0xFFFF6060);
		}
	}
}
