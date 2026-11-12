package dev.elsewhere.boss_ui;

import com.google.gson.JsonParser;
import dev.elsewhere.hud.ElsewhereHud;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.BossEvent;

/**
 * Boss bar v2 state (3.0 polish P1): damage trail, phase-crossing flash and the phase-transition timer, kept per bar UUID,
 * plus the per-title layout cache (matched boss, display name, attack label, state chip) that keeps the per-frame path
 * allocation free. Kill switch: {@code "bossBarFx": false} in config/elsewhere-client.json (read once per launch; off = the
 * 2.x flat bar). No packets, no ticking: everything is derived from the bar's progress and title.
 */
public final class BossBarFx {
	private BossBarFx() {}

	public static final float[] DEFAULT_PHASES = BossBarData.DEFAULT_PHASES;
	private static final long TRAIL_DELAY_MS = 450, FLASH_MS = 520;
	private static Boolean enabled;

	public static boolean enabled() {
		if (enabled == null) {
			boolean on = true;
			try {
				if (Files.exists(ElsewhereHud.file())) {
					var o = JsonParser.parseString(Files.readString(ElsewhereHud.file())).getAsJsonObject();
					if (o.has("bossBarFx")) on = o.get("bossBarFx").getAsBoolean();
				}
			} catch (Exception ignored) {}
			enabled = on;
		}
		return enabled;
	}

	/** Per-bar animation state. */
	static final class Trail {
		float shown = -1F, last = -1F;
		long hitAt;
		long flashAt = -100000L;
		long transitionUntil;
		int phase;
		long seen;
	}

	private static final Map<UUID, Trail> TRAILS = new HashMap<>();
	private static long frame;

	/** Called once per HUD frame before drawing; drops state for bars that are gone. */
	public static void beginFrame() {
		frame++;
		if (frame % 120 == 0) TRAILS.values().removeIf(t -> frame - t.seen > 120);
	}

	static Trail trail(UUID id, float progress, float[] phases, int transitionTicks, long now) {
		Trail t = TRAILS.computeIfAbsent(id, k -> new Trail());
		t.seen = frame;
		if (t.shown < 0F) { t.shown = progress; t.last = progress; t.phase = BossBarData.phaseOf(progress, phases); return t; }
		if (progress < t.last - 0.0005F) t.hitAt = now;
		if (progress > t.shown) t.shown = progress;
		else if (now - t.hitAt > TRAIL_DELAY_MS) t.shown = Math.max(progress, t.shown - 0.0035F - (t.shown - progress) * 0.04F);
		int ph = BossBarData.phaseOf(progress, phases);
		if (ph > t.phase) {
			t.flashAt = now;
			// every boss family is invulnerable for its phase transition (40 ticks): the Invulnerable chip shows for that long
			t.transitionUntil = now + transitionTicks * 50L;
		}
		t.phase = ph;
		t.last = progress;
		return t;
	}

	static int phaseOf(float progress, float[] phases) {
		return BossBarData.phaseOf(progress, phases);
	}

	static float flash(Trail t, long now) {
		long d = now - t.flashAt;
		return d < 0 || d > FLASH_MS ? 0F : 1F - d / (float) FLASH_MS;
	}

	static boolean inTransition(Trail t, long now) {
		return t != null && now < t.transitionUntil;
	}

	// ---- caches (cleared on resource reload by BossUiClient.data) ----

	/**
	 * Everything the draw path needs from a title, computed once per distinct title: the matched boss (null = unlisted),
	 * the name and attack line with raw ids replaced by lang names, the state chip (null = none), the cast-bar flag and,
	 * for the vanilla path, the component to draw instead of the server's (null = draw the server's unchanged).
	 */
	public record Layout(BossBarData.Entry entry, String name, String attack, String state, boolean cast, Component vanillaName, List<BossBarData.Entry> owners, Component nameOnly) {}

	/**
	 * Gate for the attack/cast line (B5: wind-up readouts only with foresight gear). Another module sets it once from its
	 * client init; checked per bar per frame, so it must be cheap (no allocation). Default: always shown.
	 */
	public static java.util.function.BooleanSupplier attackLineGate = () -> true;

	public static boolean attackLine() {
		return attackLineGate.getAsBoolean();
	}

	private static final Map<String, String> NAMES = new HashMap<>();
	private static final Map<String, Layout> LAYOUT = new HashMap<>();
	private static final Map<String, String> STATE_TEXT = new HashMap<>();
	private static final Map<String, Identifier> ICONS = new HashMap<>();
	private static final String[] PHASE_TEXT = new String[64];
	private static Map<String, BossBarData.Entry> layoutData;

	static void clearCaches() { NAMES.clear(); LAYOUT.clear(); STATE_TEXT.clear(); ICONS.clear(); TITLES.clear(); java.util.Arrays.fill(PHASE_TEXT, null); }

	/** The bar title as a string, rebuilt only when the bar's name component is replaced (an update packet), not per frame. */
	private static final class Title { Component src; String text; long seen; }
	private static final Map<UUID, Title> TITLES = new HashMap<>();

	public static String titleOf(BossEvent e) {
		Title t = TITLES.get(e.getId());
		if (t == null) {
			if (TITLES.size() > 64) TITLES.values().removeIf(x -> frame - x.seen > 120);
			TITLES.put(e.getId(), t = new Title());
		}
		t.seen = frame;
		Component c = e.getName();
		if (t.src != c) { t.src = c; t.text = c.getString(); }
		return t.text;
	}

	static Identifier icon(String id) {
		return ICONS.computeIfAbsent(id, Identifier::parse);
	}

	static String nameOf(String id) { return NAMES.computeIfAbsent(id, BossUiClient::nameOfRaw); }

	/** Client lang lookup: null when the key is missing (so the caller falls back). */
	static String lang(String key) {
		Language l = Language.getInstance();
		return l.has(key) ? l.getOrDefault(key) : null;
	}

	public static Layout layout(Map<String, BossBarData.Entry> data, String title) {
		if (data != layoutData) { LAYOUT.clear(); layoutData = data; }
		Layout l = LAYOUT.get(title);
		if (l == null) {
			if (LAYOUT.size() > 256) LAYOUT.clear();
			l = build(data, title);
			LAYOUT.put(title, l);
		}
		return l;
	}

	static Layout build(Map<String, BossBarData.Entry> data, String title) {
		boolean cast = BossBarData.isCastBar(title);
		if (cast) {
			// "⚠ Reaping Sweep": the whole text is the attack name; it is drawn on its owner's info line
			String body = title.substring(1).trim();
			String name = BossBarData.label(body, BossBarFx::lang);
			Component vanilla = name.equals(body) ? null : Component.literal("⚠ " + name);
			return new Layout(null, name, "", BossBarData.stateOf(body, null), true, vanilla, BossBarData.subOwners(data, title), null);
		}
		BossBarData.Entry e = BossBarData.match(data, title, BossBarFx::nameOf);
		BossBarData.Split s = e != null ? BossBarData.splitFor(title, nameOf(e.id())) : BossBarData.split(title);
		String name = BossBarData.label(s.name(), BossBarFx::lang);
		String attack = BossBarData.label(s.attack(), BossBarFx::lang);
		String state = BossBarData.stateOf(s.attack(), e);
		Component vanilla = null;
		if (!name.equals(s.name()) || !attack.equals(s.attack())) vanilla = Component.literal(name + (attack.isEmpty() ? "" : " · " + attack));
		Component nameOnly = attack.isEmpty() ? null : Component.literal(name);
		return new Layout(e, name, attack, state, false, vanilla, e == null ? BossBarData.subOwners(data, title) : List.of(), nameOnly);
	}

	/** Matched boss for a title (cached), or null. */
	public static BossBarData.Entry match(Map<String, BossBarData.Entry> data, String title) {
		return layout(data, title).entry();
	}

	static String stateText(String state) {
		return STATE_TEXT.computeIfAbsent(state, st -> {
			String v = lang("elsewhere.bossbar.state." + st);
			return v != null ? v : BossBarData.humanize(st);
		});
	}

	static String phaseText(int phase, int count) {
		int i = Math.min(7, phase) * 8 + Math.min(7, count);
		String s = PHASE_TEXT[i];
		if (s == null) PHASE_TEXT[i] = s = "❖ " + phase + "/" + count;
		return s;
	}
}
