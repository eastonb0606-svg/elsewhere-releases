package dev.elsewhere.boss_ui.mixin;

import dev.elsewhere.boss_ui.BossBarData;
import dev.elsewhere.boss_ui.BossUiClient;
import dev.elsewhere.hud.ElsewhereHud;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla bar for listed bosses (Auto) or all bosses (Always); Hidden and unknown bosses keep the vanilla drawing. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
	@Shadow @Final private Map<UUID, LerpingBossEvent> events;

	@Shadow
	private void extractBar(GuiGraphicsExtractor g, int x, int y, BossEvent event) {}

	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void elsewhere$custom(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (events.isEmpty() || ElsewhereHud.invisible("bossbars")) return;
		Map<String, BossBarData.Entry> data = BossUiClient.data();
		dev.elsewhere.boss_ui.BossBarFx.beginFrame();
		boolean always = ElsewhereHud.mode("bossbars") == ElsewhereHud.Mode.ALWAYS;
		if (data.isEmpty() && !always) return;
		Minecraft mc = Minecraft.getInstance();
		var font = mc.font;
		int cx = mc.getWindow().getGuiScaledWidth() / 2, limit = mc.getWindow().getGuiScaledHeight() / 2;
		// group: main bars (matched by name) with their sub-bars; unmatched, non-sub bars are vanilla (or generic framed in Always)
		Map<LerpingBossEvent, List<BossEvent>> mains = new LinkedHashMap<>();
		Map<LerpingBossEvent, BossBarData.Entry> entries = new LinkedHashMap<>();
		List<LerpingBossEvent> others = new ArrayList<>();
		Map<LerpingBossEvent, BossBarData.Entry> subOf = new LinkedHashMap<>();
		for (LerpingBossEvent e : events.values()) {
			String title = e.getName().getString();
			BossBarData.Entry m = dev.elsewhere.boss_ui.BossBarFx.match(data, title);
			if (m != null) { mains.put(e, new ArrayList<>()); entries.put(e, m); }
			else {
				BossBarData.Entry s = BossBarData.subOwner(data, title);
				if (s != null) subOf.put(e, s); else others.add(e);
			}
		}
		List<LerpingBossEvent> orphanSubs = new ArrayList<>();
		for (var s : subOf.entrySet()) {
			boolean placed = false;
			for (var m : entries.entrySet()) if (m.getValue() == s.getValue()) { mains.get(m.getKey()).add(s.getKey()); placed = true; break; }
			if (!placed) orphanSubs.add(s.getKey());
		}
		others.addAll(orphanSubs);
		int y = 12;
		for (var m : mains.entrySet()) {
			if (y >= limit) break;
			y += dev_draw(g, font, cx, y, m.getKey(), entries.get(m.getKey()), m.getValue());
		}
		for (LerpingBossEvent e : others) {
			if (y >= limit) break;
			if (always) {
				y += dev_draw(g, font, cx, y, e, null, List.of());
			} else {
				int x = cx - 91;
				this.extractBar(g, x, y, e);
				var name = e.getName();
				g.text(font, name, cx - font.width(name) / 2, y - 9, 0xFFFFFFFF);
				y += 10 + font.lineHeight;
			}
		}
		ci.cancel();
	}

	private static int dev_draw(GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int cx, int y, BossEvent e, BossBarData.Entry entry, List<BossEvent> subs) {
		return dev.elsewhere.boss_ui.BossBarDraw.draw(g, font, cx, y, e, entry, dev.elsewhere.boss_ui.BossBarFx.split(e.getName().getString()), subs);
	}
}
