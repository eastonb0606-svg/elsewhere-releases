package dev.elsewhere.boss_ui.mixin;

import dev.elsewhere.boss_ui.BossBarData;
import dev.elsewhere.boss_ui.BossBarFx;
import dev.elsewhere.boss_ui.BossBarOverlay;
import dev.elsewhere.boss_ui.BossUiClient;
import dev.elsewhere.hud.ElsewhereHud;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla bar for listed bosses (Auto) or all bosses (Always); Hidden and unknown bosses keep the vanilla
 * drawing. Every bar's text, framed or vanilla, goes through the label cache, so a raw move id or an untranslated key is
 * shown as its lang name.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Shadow @Final private Map<UUID, LerpingBossEvent> events;

	@Shadow
	private void extractBar(GuiGraphicsExtractor g, int x, int y, BossEvent event) {}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void elsewhere$custom(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (events.isEmpty() || ElsewhereHud.invisible("bossbars")) return;
		Map<String, BossBarData.Entry> data = BossUiClient.data();
		BossBarFx.beginFrame();
		boolean always = ElsewhereHud.mode("bossbars") == ElsewhereHud.Mode.ALWAYS;
		Minecraft mc = Minecraft.getInstance();
		var font = mc.font;
		int cx = mc.getWindow().getGuiScaledWidth() / 2, limit = mc.getWindow().getGuiScaledHeight() / 2;
		int y = BossBarOverlay.drawFramed(g, font, cx, 12, limit, events.values(), data);
		for (int i = 0; i < BossBarOverlay.OTHERS.size(); i++) {
			LerpingBossEvent e = BossBarOverlay.OTHERS.get(i);
			if (y >= limit) break;
			BossBarFx.Layout lay = BossBarFx.layout(data, BossBarFx.titleOf(e));
			if (lay.cast() && !BossBarFx.attackLine()) continue; // a cast bar is a wind-up readout (B5 gate)
			if (always) {
				y += BossBarOverlay.drawGeneric(g, font, cx, y, e, data);
			} else {
				int x = cx - 91;
				this.extractBar(g, x, y, e);
				Component name = !BossBarFx.attackLine() && lay.nameOnly() != null ? lay.nameOnly() : lay.vanillaName() != null ? lay.vanillaName() : e.getName();
				g.text(font, name, cx - font.width(name) / 2, y - 9, 0xFFFFFFFF);
				y += 10 + font.lineHeight;
			}
		}
		ci.cancel();
	}
}
