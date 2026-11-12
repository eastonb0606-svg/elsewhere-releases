package dev.elsewhere.boss_ui;

import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;

/**
 * Draws one framed boss bar: name above, icon at the left, notched bar with damage trail and phase flash, then one info
 * line (state chip at the left, attack or cast name in the middle with its wind-up fill, phase counter at the right) and
 * thin sub-bars underneath. All text comes from the cached {@link BossBarFx.Layout}, so nothing is built per frame.
 */
public final class BossBarDraw {
	private BossBarDraw() {}

	static final int BAR_W = 182, BAR_H = 7;

	static int color(BossEvent e, BossBarData.Entry entry) {
		if (entry != null) return 0xFF000000 | entry.color();
		return 0xFF000000 | switch (e.getColor()) {
			case PINK -> 0xE96BA0; case BLUE -> 0x4F8FE0; case RED -> 0xD84040; case GREEN -> 0x5BBE4A;
			case YELLOW -> 0xE8C840; case PURPLE -> 0x9A5AD8; case WHITE -> 0xF0F0F0;
		};
	}

	private static int shade(int argb, float f) {
		int r = Math.min(255, (int) (((argb >> 16) & 255) * f)), g = Math.min(255, (int) (((argb >> 8) & 255) * f)), b = Math.min(255, (int) ((argb & 255) * f));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Chip and frame colour per state. */
	static int stateColor(String state) {
		return switch (state) {
			case BossBarData.INVULNERABLE -> 0xFF9DB4CC;
			case BossBarData.WARDED -> 0xFF7FD0C8;
			case BossBarData.ENRAGED -> 0xFFE85A3A;
			case BossBarData.VULNERABLE -> 0xFFF2C744;
			default -> 0xFFC0C0C0;
		};
	}

	/** Cast bar text colour from the bar colour the server picked for its danger (red big, blue interact, else yellow). */
	private static int castColor(BossEvent c) {
		return switch (c.getColor()) {
			case RED -> 0xFFFF6A5A;
			case BLUE -> 0xFF7FE0F0;
			default -> 0xFFF2D25A;
		};
	}

	/** Returns the height used. */
	public static int draw(GuiGraphicsExtractor g, Font font, int centerX, int y, BossEvent e, BossBarFx.Layout lay, List<BossEvent> subs, Map<String, BossBarData.Entry> data) {
		BossBarData.Entry entry = lay.entry();
		int x = centerX - BAR_W / 2, y0 = y;
		int col = color(e, entry);
		g.centeredText(font, lay.name(), centerX, y, 0xFFFFFFFF);
		y += 10;
		boolean fx = BossBarFx.enabled();
		float prog = Math.max(0F, Math.min(1F, e.getProgress()));
		int filled = Math.round(BAR_W * prog);
		float[] phases = BossBarData.phasesFor(entry);
		long now = System.currentTimeMillis();
		BossBarFx.Trail tr = fx ? BossBarFx.trail(e.getId(), prog, phases, entry != null ? entry.transition() : 0, now) : null;
		// the cast bar (overworld bosses) becomes this bar's attack line; its state words count too
		BossEvent cast = null;
		BossBarFx.Layout castLay = null;
		for (int i = 0; i < subs.size(); i++) {
			BossEvent s = subs.get(i);
			BossBarFx.Layout sl = BossBarFx.layout(data, BossBarFx.titleOf(s));
			if (sl.cast()) { cast = s; castLay = sl; break; }
		}
		String state = null;
		if (fx) {
			state = lay.state();
			if (state == null && castLay != null) state = castLay.state();
			if (state == null && BossBarFx.inTransition(tr, now)) state = BossBarData.INVULNERABLE;
		}
		int sc = state != null ? stateColor(state) : 0;
		boolean pulse = state != null && BossBarData.ENRAGED.equals(state) && !dev.elsewhere.hud.ElsewhereHud.reduceMotion() && (now / 250L) % 2L == 0L;
		// frame: dark border, then the boss colour (or the state colour while a state is up)
		g.fill(x - 2, y - 2, x + BAR_W + 2, y + BAR_H + 2, 0xFF14101A);
		g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, state != null ? (pulse ? shade(sc, 1.25F) : shade(sc, 0.85F)) : shade(col, 0.55F));
		g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF1E1A24);
		if (tr != null && tr.shown > prog + 0.002F) {
			int tw = Math.round(BAR_W * Math.min(1F, tr.shown));
			g.fill(x + filled, y, x + tw, y + BAR_H, 0xFFE8E0C8);
			g.fill(x + filled, y + BAR_H - 2, x + tw, y + BAR_H, 0xFFB8A878);
		}
		if (filled > 0) {
			g.fill(x, y, x + filled, y + BAR_H, col);
			g.fill(x, y, x + filled, y + 2, shade(col, 1.35F));
			g.fill(x, y + BAR_H - 1, x + filled, y + BAR_H, shade(col, 0.7F));
			// invulnerable / warded: a cold steel sheen over the fill (no damage gets through)
			if (state != null && (BossBarData.INVULNERABLE.equals(state) || BossBarData.WARDED.equals(state))) g.fill(x, y, x + filled, y + BAR_H, (sc & 0x00FFFFFF) | 0x66000000);
		}
		if (tr != null && !dev.elsewhere.hud.ElsewhereHud.reduceMotion()) {
			float fl = BossBarFx.flash(tr, now);
			if (fl > 0F) {
				int a = (int) (fl * 170F) << 24;
				g.fill(x, y, x + BAR_W, y + BAR_H, a | 0xFFFFFF);
				g.fill(x - 2, y - 2, x + BAR_W + 2, y - 1, a | 0xD8B050);
				g.fill(x - 2, y + BAR_H + 1, x + BAR_W + 2, y + BAR_H + 2, a | 0xD8B050);
			}
		}
		if (fx) {
			for (float p : phases) {
				int px = x + Math.round(BAR_W * p);
				g.fill(px, y - 2, px + 1, y + BAR_H + 2, prog <= p ? 0xFF6A5A38 : 0xFFE8C860);
			}
		} else {
			for (int i = 1; i < 10; i++) g.fill(x + BAR_W * i / 10, y, x + BAR_W * i / 10 + 1, y + BAR_H, 0x60000000);
		}
		g.fill(x - 3, y - 3, x - 1, y - 1, 0xFFD8B050);
		g.fill(x + BAR_W + 1, y - 3, x + BAR_W + 3, y - 1, 0xFFD8B050);
		g.fill(x - 3, y + BAR_H + 1, x - 1, y + BAR_H + 3, 0xFFD8B050);
		g.fill(x + BAR_W + 1, y + BAR_H + 1, x + BAR_W + 3, y + BAR_H + 3, 0xFFD8B050);
		if (entry != null && !entry.icon().isEmpty()) {
			Identifier icon = BossBarFx.icon(entry.icon());
			g.fill(x - 22, y - 5, x - 4, y + 13, 0xFF14101A);
			g.fill(x - 21, y - 4, x - 5, y + 12, shade(col, 0.5F));
			g.blit(RenderPipelines.GUI_TEXTURED, icon, x - 21, y - 4, 0F, 0F, 16, 16, 16, 16);
		}
		y += BAR_H + 4;

		// info line: [state chip]   attack / cast   [phase counter]
		String chip = state != null ? BossBarFx.stateText(state) : null;
		String counter = fx && phases.length > 0 && tr != null && entry != null && entry.counter() ?BossBarFx.phaseText(tr.phase + 1, phases.length + 1) : null;
		boolean showAttack = BossBarFx.attackLine();
		String attack = !showAttack ? "" : !lay.attack().isEmpty() ? lay.attack() : castLay != null ? castLay.name() : "";
		int attackCol = lay.attack().isEmpty() && cast != null ? castColor(cast) : 0xFFE8D8A8;
		int chipW = chip != null ? font.width(chip) + 6 : 0, counterW = counter != null ? font.width(counter) : 0;
		if (chip != null || counter != null || !attack.isEmpty()) {
			if (chip != null) {
				g.fill(x, y - 1, x + chipW, y + 9, shade(sc, 0.6F));
				g.fill(x + 1, y, x + chipW - 1, y + 8, 0xF014101A);
				g.text(font, chip, x + 3, y, sc, false);
			}
			if (counter != null) g.text(font, counter, x + BAR_W - counterW, y, 0xFF9A8E70, true);
			if (!attack.isEmpty()) {
				int aw = font.width(attack);
				boolean fits = centerX - aw / 2 >= x + chipW + 4 && centerX + aw / 2 <= x + BAR_W - counterW - 4;
				if (!fits && (chip != null || counter != null)) y += 10;
				g.centeredText(font, attack, centerX, y, attackCol);
				if (lay.attack().isEmpty() && cast != null && showAttack) {
					// wind-up: a thin fill under the cast name, draining as the cast bar's progress does
					int ww = aw + 8, wx = centerX - ww / 2;
					g.fill(wx, y + 9, wx + ww, y + 10, 0xFF2A2430);
					g.fill(wx, y + 9, wx + Math.round(ww * Math.max(0F, Math.min(1F, cast.getProgress()))), y + 10, attackCol);
					y += 2;
				}
			}
			y += 10;
		}
		for (int i = 0; i < subs.size(); i++) {
			BossEvent s = subs.get(i);
			if (s == cast) continue;
			String sn = BossBarFx.layout(data, BossBarFx.titleOf(s)).name();
			int sw = 100, sx = centerX - sw / 2;
			g.fill(sx - 1, y, sx + sw + 1, y + 5, 0xFF14101A);
			g.fill(sx, y + 1, sx + sw, y + 4, 0xFF2A2430);
			g.fill(sx, y + 1, sx + Math.round(sw * Math.max(0F, Math.min(1F, s.getProgress()))), y + 4, color(s, entry));
			g.text(font, sn, sx - 4 - font.width(sn), y - 2, 0xFFB8B0C0, true);
			y += 7;
		}
		return y - y0 + 6;
	}
}
