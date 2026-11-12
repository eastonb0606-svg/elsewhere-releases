package dev.elsewhere.arsenal.client;

import dev.elsewhere.arsenal.ArsenalContent;
import dev.elsewhere.arsenal.glimmer.Glimmer;
import dev.elsewhere.arsenal.glimmer.GlimmerPayload;
import dev.elsewhere.arsenal.trinket.Trinkets;
import dev.elsewhere.hud.ElsewhereHud;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * The Glimmer bar (ARSENAL §1): 81 × 3 px above the hunger row, amethyst on a dark track, hatched minion reserve at the
 * right end, a "cur/max" number while a Glimmer item is held, a red flash on a failed cast. Modes via {@link ElsewhereHud}.
 */
public final class GlimmerHud {
	private GlimmerHud() {}

	public static final String METER = "glimmer";
	public static final TagKey<Item> GLIMMER_ITEM = TagKey.create(Registries.ITEM, ArsenalContent.id("glimmer_item"));
	static final int W = 81, H = 3, FILL = 0xFFA77BF3, FILL_LIGHT = 0xFFC9A8FF, TRACK = 0xFF2A1F3D, RESERVE = 0xFF1A1326, HATCH = 0xFF4A3A66;
	static final int FADE_TICKS = 40, FADE_LEN = 10;

	static long lastActive = -1000, failAt = -1000;
	static int failNeed;
	static float shownCur = -1;

	public static void receive(GlimmerPayload p) {
		Glimmer.ClientMirror.current = p.current();
		Glimmer.ClientMirror.max = p.max();
		Glimmer.ClientMirror.reserved = p.reserved();
		Glimmer.ClientMirror.minions = p.minions();
		if (p.failNeed() > 0) {
			failAt = now();
			failNeed = p.failNeed();
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null && ElsewhereHud.invisible(METER))
				mc.player.sendOverlayMessage(Component.translatable("elsewhere.glimmer.not_enough", p.failNeed()));
		}
	}

	static long now() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.getGameTime() : 0;
	}

	static boolean holdingGlimmerItem(Player p) {
		return p.getMainHandItem().is(GLIMMER_ITEM) || p.getOffhandItem().is(GLIMMER_ITEM);
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || mc.gameMode == null || !mc.gameMode.canHurtPlayer() && !p.isCreative() || p.isSpectator()) return;
		float max = Glimmer.ClientMirror.max, cur = Glimmer.ClientMirror.current;
		int reserved = Glimmer.ClientMirror.reserved;
		if (max <= 0) return;
		float usable = Math.max(0, max - reserved);
		boolean holding = holdingGlimmerItem(p);
		long t = now();
		boolean active = holding || Glimmer.ClientMirror.minions > 0 || cur < usable - 0.01F || t - failAt < 40;
		if (active) lastActive = t;
		long since = t - lastActive;
		boolean auto = since < FADE_TICKS + FADE_LEN;
		if (!ElsewhereHud.shouldShow(METER, auto)) return;
		float alpha = ElsewhereHud.mode(METER) == ElsewhereHud.Mode.ALWAYS || since <= FADE_TICKS ? 1F : 1F - (since - FADE_TICKS) / (float) FADE_LEN;
		if (alpha <= 0.02F) return;
		int x = g.guiWidth() / 2 + 91 - W;
		int y = g.guiHeight() - 39 - H - 2;
		if (p.getAirSupply() < p.getMaxAirSupply() || p.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)) y -= 10; // above the air bubbles
		if (p.getVehicle() instanceof net.minecraft.world.entity.LivingEntity) y -= 10; // mount hearts replace the food row
		boolean flash = t - failAt < 10 && (t - failAt) % 4 < 2;
		g.fill(x - 1, y - 1, x + W + 1, y + H + 1, a(0xFF000000, alpha * 0.8F));
		g.fill(x, y, x + W, y + H, a(TRACK, alpha));
		int resW = Math.round(W * Math.min(1, reserved / max));
		int filled = Math.round(W * Math.min(1, cur / max));
		filled = Math.min(filled, W - resW);
		if (filled > 0) {
			g.fill(x, y, x + filled, y + H, a(flash ? 0xFFE05A6A : FILL, alpha));
			g.fill(x, y, x + filled, y + 1, a(flash ? 0xFFFF9AA6 : FILL_LIGHT, alpha));
		}
		if (resW > 0) {
			int rx = x + W - resW;
			g.fill(rx, y, x + W, y + H, a(RESERVE, alpha));
			for (int i = 0; i < resW; i++) {
				for (int j = 0; j < H; j++) if (((i + j) & 1) == 0) g.fill(rx + i, y + j, rx + i + 1, y + j + 1, a(HATCH, alpha));
			}
		}
		if (flash) g.fill(x - 1, y - 1, x + W + 1, y, a(0xFFE05A6A, alpha));
		if (holding || ElsewhereHud.mode(METER) == ElsewhereHud.Mode.ALWAYS && cur < usable - 0.01F) {
			Font font = mc.font;
			String s = (int) Math.floor(cur) + "/" + (int) usable;
			g.text(font, s, x + W - font.width(s), y - 9, a(0xFFD9C6FF, alpha), true);
		}
	}

	/** Small amethyst corner marks on hotbar slots holding an active trinket (ARSENAL §7). */
	public static void trinketMarks(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (p == null || p.isSpectator() || ElsewhereHud.allHidden() || !ElsewhereHud.flag("trinketMarks")) return;
		List<Integer> slots = Trinkets.activeSlots(p);
		if (slots.isEmpty()) return;
		int cx = g.guiWidth() / 2, by = g.guiHeight() - 22;
		for (int s : slots) {
			int sx = s < 0 ? cx - 91 - 29 + 3 : cx - 91 + 3 + s * 20;
			int sy = s < 0 ? by + 1 + 3 : by + 3;
			g.fill(sx + 12, sy, sx + 16, sy + 1, 0xFFA77BF3);
			g.fill(sx + 15, sy, sx + 16, sy + 4, 0xFFA77BF3);
			g.fill(sx + 13, sy + 1, sx + 15, sy + 2, 0xFFE2D0FF);
		}
	}

	static int a(int argb, float alpha) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, alpha)));
		return (al << 24) | (argb & 0xFFFFFF);
	}
}
