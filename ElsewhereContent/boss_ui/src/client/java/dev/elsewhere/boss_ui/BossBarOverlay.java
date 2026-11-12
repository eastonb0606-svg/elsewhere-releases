package dev.elsewhere.boss_ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;

/**
 * Groups the client's boss bars each frame (listed bosses with their sub-bars and cast bar; everything else) and draws
 * the framed ones. The lists are reused between frames, so grouping allocates nothing. Bars that are not framed are left
 * in {@link #OTHERS} for the overlay mixin to draw the vanilla way.
 */
public final class BossBarOverlay {
	private BossBarOverlay() {}

	private static final ArrayList<LerpingBossEvent> MAINS = new ArrayList<>(), SUBQ = new ArrayList<>();
	private static final ArrayList<ArrayList<BossEvent>> POOL = new ArrayList<>();
	public static final ArrayList<LerpingBossEvent> OTHERS = new ArrayList<>();

	/** Draws every framed bar from {@code y}; returns the next free y. Unframed bars are left in {@link #OTHERS}. */
	public static int drawFramed(GuiGraphicsExtractor g, Font font, int cx, int y, int limit, Collection<LerpingBossEvent> events, Map<String, BossBarData.Entry> data) {
		MAINS.clear();
		SUBQ.clear();
		OTHERS.clear();
		for (LerpingBossEvent e : events) {
			BossBarFx.Layout l = BossBarFx.layout(data, BossBarFx.titleOf(e));
			if (l.entry() != null) MAINS.add(e);
			else if (!l.owners().isEmpty()) SUBQ.add(e);
			else OTHERS.add(e);
		}
		while (POOL.size() < MAINS.size()) POOL.add(new ArrayList<>(4));
		for (int i = 0; i < MAINS.size(); i++) POOL.get(i).clear();
		for (int q = 0; q < SUBQ.size(); q++) {
			LerpingBossEvent s = SUBQ.get(q);
			List<BossBarData.Entry> owners = BossBarFx.layout(data, BossBarFx.titleOf(s)).owners();
			int at = -1;
			for (int i = 0; i < MAINS.size() && at < 0; i++) if (owners.contains(BossBarFx.layout(data, BossBarFx.titleOf(MAINS.get(i))).entry())) at = i;
			if (at >= 0) POOL.get(at).add(s); else OTHERS.add(s);
		}
		for (int i = 0; i < MAINS.size(); i++) {
			if (y >= limit) break;
			LerpingBossEvent m = MAINS.get(i);
			y += BossBarDraw.draw(g, font, cx, y, m, BossBarFx.layout(data, BossBarFx.titleOf(m)), POOL.get(i), data);
		}
		return y;
	}

	/** A framed bar for a boss that is not listed (Always mode): no icon, no invented phase notches. */
	public static int drawGeneric(GuiGraphicsExtractor g, Font font, int cx, int y, LerpingBossEvent e, Map<String, BossBarData.Entry> data) {
		return BossBarDraw.draw(g, font, cx, y, e, BossBarFx.layout(data, BossBarFx.titleOf(e)), List.of(), data);
	}
}
