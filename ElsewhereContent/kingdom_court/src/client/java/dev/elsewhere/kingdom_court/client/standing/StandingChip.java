package dev.elsewhere.kingdom_court.client.standing;

import dev.elsewhere.kingdom_court.CourtContent;
import dev.elsewhere.kingdom_court.ruler.CourtRuler;
import dev.elsewhere.kingdom_court.standing.StandingNet;
import dev.elsewhere.kingdom_court.standing.StandingText;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * POLISH P7: the Standing chip. Look at a ruler (any kingdom's King or Lord, up to {@link #RANGE} blocks, not through walls) and a small
 * chip in that kingdom's style fades in under the crosshair: the kingdom crest, the ruler's name, your Standing in that kingdom's own words
 * ("Shield-friend"), the band and number, a bar, and a red line when its people attack you on sight.
 * <p>Cost: rulers are tracked by the client's entity load/unload events, so with no ruler loaded (almost always) the tick hook returns at
 * once. With rulers near, one ray test per loaded ruler (at most six) per tick and one block ray when the crosshair is on one. Standing comes
 * from {@link StandingCache}: one query when the crosshair lands on a ruler whose kingdom has no fresh copy, never a poll. The text is
 * built when the target or its Standing changes, not per frame. Off: kill switch {@code standing_chip}, or {@code /elsewhere_hud standing hidden}.
 */
public final class StandingChip {
	private StandingChip() {}

	public static final double RANGE = 24.0;
	static final long FADE_IN_MS = 150L, FADE_OUT_MS = 220L;
	private static final List<CourtRuler> RULERS = new ArrayList<>();
	private static CourtRuler target, shown;
	private static boolean fadingIn;
	private static long fadeStart;
	private static boolean done;

	// cached layout of the shown chip, rebuilt only when the ruler, its name object or the Standing entry object changes
	private static int builtId = Integer.MIN_VALUE;
	private static Object builtName, builtEntry;
	private static String builtFaction;
	private static FormattedCharSequence nameLine, valueLine, warnLine;
	private static int chipW, valueStanding;
	private static boolean hasValue;
	private static KingdomStyle style = KingdomStyle.ALDERMARK;

	static void init() {
		if (done) return;
		done = true;
		HudBridge.init();
		ClientEntityEvents.ENTITY_LOAD.register((e, level) -> {
			if (e instanceof CourtRuler r && !RULERS.contains(r)) RULERS.add(r);
		});
		ClientEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
			if (e instanceof CourtRuler r) {
				RULERS.remove(r);
				if (r == target) lose();
				if (r == shown) shown = null;
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			RULERS.clear();
			target = shown = null;
			StandingCache.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(StandingChip::tick);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, CourtContent.id("standing_chip"), StandingChip::render);
	}

	/** Number of rulers the chip knows are loaded (self-checks and the shot run). */
	public static int tracked() {
		return RULERS.size();
	}

	static void tick(Minecraft mc) {
		if (RULERS.isEmpty()) {
			if (target != null) lose();
			return;
		}
		if (mc.player == null || mc.level == null) return;
		CourtRuler best = pick(mc);
		if (best == target) return;
		if (best == null) lose();
		else acquire(best);
	}

	static CourtRuler pick(Minecraft mc) {
		Entity cam = mc.getCameraEntity();
		if (cam == null) return null;
		if (mc.crosshairPickEntity instanceof CourtRuler r && !r.isRemoved() && !r.isInvisible()) return r; // vanilla's own pick, already paid for
		Vec3 eye = cam.getEyePosition();
		Vec3 end = eye.add(cam.getViewVector(1.0F).scale(RANGE));
		CourtRuler best = null;
		Vec3 bestHit = null;
		double bestD = RANGE * RANGE;
		for (CourtRuler r : RULERS) {
			if (r.isRemoved() || r.isInvisible() || r.level() != mc.level || r.distanceToSqr(eye) > (RANGE + 3) * (RANGE + 3)) continue;
			Optional<Vec3> hit = r.getBoundingBox().inflate(0.2).clip(eye, end);
			if (hit.isEmpty()) continue;
			double d = eye.distanceToSqr(hit.get());
			if (d < bestD) {
				bestD = d;
				best = r;
				bestHit = hit.get();
			}
		}
		if (best == null) return null;
		BlockHitResult wall = mc.level.clip(new ClipContext(eye, bestHit, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, cam));
		if (wall.getType() != HitResult.Type.MISS && eye.distanceToSqr(wall.getLocation()) < bestD - 0.01) return null;
		return best;
	}

	static void acquire(CourtRuler r) {
		long now = Util.getMillis();
		if (shown != r || !fadingIn) fadeStart = now - (shown == r ? Math.round(FADE_IN_MS * alpha(now)) : 0L);
		target = shown = r;
		fadingIn = true;
		String f = r.syncedFaction();
		if (!f.isEmpty()) StandingCache.request(r.getId(), f);
	}

	static void lose() {
		if (target == null) return;
		long now = Util.getMillis();
		fadeStart = now - Math.round(FADE_OUT_MS * (1F - alpha(now)));
		fadingIn = false;
		target = null;
	}

	static float alpha(long now) {
		if (shown == null) return 0F;
		float t = (now - fadeStart) / (float) (fadingIn ? FADE_IN_MS : FADE_OUT_MS);
		return fadingIn ? Math.min(1F, t) : Math.max(0F, 1F - t);
	}

	static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		CourtRuler r = shown;
		if (r == null) return;
		Minecraft mc = Minecraft.getInstance();
		float a = alpha(Util.getMillis());
		if (a <= 0.04F) {
			if (!fadingIn) shown = null;
			return;
		}
		if (mc.gui.screen() != null || mc.player == null || !HudBridge.show()) return;
		String f = r.syncedFaction();
		if (f.isEmpty()) return;
		StandingCache.Entry e = StandingCache.get(f);
		build(mc.font, r, f, e);
		int h = warnLine != null ? 42 : 32;
		int x = (g.guiWidth() - chipW) / 2, y = g.guiHeight() / 2 + 20;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, style.chip, x, y, chipW, h, KingdomStyle.fade(0xFFFFFFFF, a));
		g.blitSprite(RenderPipelines.GUI_TEXTURED, style.crest, x + 6, y + 8, 16, 16, KingdomStyle.fade(0xFFFFFFFF, a));
		g.text(mc.font, nameLine, x + 27, y + 5, KingdomStyle.fade(style.title, a), false);
		g.text(mc.font, valueLine, x + 27, y + 15, KingdomStyle.fade(hasValue ? style.text : style.dim, a), false);
		if (hasValue) StandingBar.draw(g, x + 27, y + 26, chipW - 34, 2, valueStanding, style, a);
		if (warnLine != null) g.text(mc.font, warnLine, x + 27, y + 31, KingdomStyle.fade(0xFFF07A6A, a), false);
	}

	/** Rebuilds the chip's lines when the ruler, its name or the Standing copy changed; otherwise it returns at once without allocating. */
	static void build(Font font, CourtRuler r, String faction, StandingCache.Entry e) {
		Component custom = r.getCustomName();
		if (r.getId() == builtId && custom == builtName && e == builtEntry && faction.equals(builtFaction)) return;
		builtId = r.getId();
		builtName = custom;
		builtEntry = e;
		builtFaction = faction;
		Component name = custom != null ? custom : r.getDisplayName();
		style = KingdomStyle.of(faction);
		nameLine = clip(font, name, 170);
		Component value;
		if (e == null) {
			hasValue = false;
			value = Component.translatable(StandingText.chipKey("asking"));
			warnLine = null;
		} else {
			hasValue = true;
			valueStanding = e.standing();
			String band = StandingText.band(e.standing());
			value = Component.translatable(StandingText.VALUE, style.bandTitle(band).withColor(style.accent & 0xFFFFFF),
				Component.translatable(StandingText.bandKey(band)).withColor(style.dim & 0xFFFFFF), signed(e.standing()));
			String warn = (e.flags() & StandingNet.R_ATTACKS) != 0 ? "attacks" : (e.flags() & StandingNet.R_OATH) != 0 ? "oath" : (e.flags() & StandingNet.R_CAPPED) != 0 ? "capped" : null;
			warnLine = warn == null ? null : clip(font, Component.translatable(StandingText.chipKey(warn)), 170);
		}
		valueLine = clip(font, value, 170);
		int w = Math.max(font.width(nameLine), font.width(valueLine));
		if (warnLine != null) w = Math.max(w, font.width(warnLine));
		chipW = Math.max(110, w + 34);
	}

	static String signed(int v) {
		return v > 0 ? "+" + v : Integer.toString(v);
	}

	static FormattedCharSequence clip(Font font, Component c, int w) {
		List<FormattedCharSequence> lines = font.split(c, w);
		return lines.isEmpty() ? FormattedCharSequence.EMPTY : lines.get(0);
	}
}
