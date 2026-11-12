package dev.elsewhere.petalwind_glasswaste.client;

import dev.elsewhere.stub.petalwind_glasswaste.ClientSettingsStub;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The Glasswaste Heat thermometer bar above the hotbar. Per-player meter {@code heat} (BALANCE.md §D1, file
 * config/elsewhere-client.json {@code meters.heat}): Auto (default: in the Glasswaste, or while Heat is above 0
 * anywhere, hiding 2 s after it settles at 0), Always, Hidden (an action-bar line at Heat 75 and 100 instead).
 */
public final class HeatHud {
	private HeatHud() {}

	public static final String METER_KEY = "heat";
	private static final int FADE_TICKS = 40;
	private static int zeroTicks = FADE_TICKS, lastHeat;

	/** Client tick: Auto fade timer and the Hidden-mode action-bar warnings. */
	public static void tick(Minecraft mc) {
		if (mc.player == null) return;
		int heat = PetalwindGlasswasteClient.heat;
		zeroTicks = heat > 0 ? 0 : Math.min(FADE_TICKS, zeroTicks + 1);
		if (ClientSettingsStub.meter(METER_KEY) == ClientSettingsStub.Mode.HIDDEN) {
			if (lastHeat < 100 && heat >= 100) mc.player.sendOverlayMessage(Component.translatable("hud.elsewhere.heat.max"));
			else if (lastHeat < 75 && heat >= 75) mc.player.sendOverlayMessage(Component.translatable("hud.elsewhere.heat.high"));
		}
		lastHeat = heat;
	}

	public static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		ClientSettingsStub.Mode mode = ClientSettingsStub.meter(METER_KEY);
		if (mode == ClientSettingsStub.Mode.HIDDEN) return;
		if (mode == ClientSettingsStub.Mode.AUTO && !PetalwindGlasswasteClient.showHeat && zeroTicks >= FADE_TICKS) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.player.isSpectator()) return;
		int w = 81, h = 5;
		int x = g.guiWidth() / 2 + 10, y = g.guiHeight() - 49 - (mc.player.isCreative() ? 0 : 10);
		int heat = PetalwindGlasswasteClient.heat;
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0201010);
		int filled = Math.round(w * heat / 100.0F);
		int color = heat >= 100 ? 0xFFFF2A10 : heat >= 70 ? 0xFFFF7A1E : 0xFFFFC040;
		g.fill(x, y, x + filled, y + h, color);
		g.text(mc.font, Component.translatable("hud.elsewhere.heat").getString() + " " + heat, x, y - 9, heat >= 70 ? 0xFFFF8040 : 0xFFFFE0A0, true);
	}
}
