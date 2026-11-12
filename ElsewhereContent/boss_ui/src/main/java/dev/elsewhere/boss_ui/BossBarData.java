package dev.elsewhere.boss_ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed {@code assets/elsewhere/boss_bars.json}: pure data and matching, no client classes (so the self-test can run on a server). */
public final class BossBarData {
	private BossBarData() {}

	/**
	 * One boss: entity id ({@code elsewhere:strawgrim}), icon texture id, bar colour (RGB), sub-bar title keywords, the HP
	 * fractions where its phases start (copied from the boss's own {@code phaseThresholds()}), title tokens that name a
	 * state chip ({@code states}: lowercase token to state) and the invulnerable phase-transition length in ticks.
	 */
	public record Entry(String id, String icon, int color, List<String> sub, float[] phases, Map<String, String> states, int transition) {
		public String path() { return id.substring(id.indexOf(':') + 1); }
	}

	/** Title split into the boss name and the text shown after it ("Name  ·  Attack"). */
	public record Split(String name, String attack) {}

	public static final String SEP = "·";
	/** Every separator a boss family puts between its name and the attack/flash text (core and nether_end ·, greenhaze », petalwind —). */
	public static final String[] SEPS = {"·", "»", "—"};
	public static final float[] DEFAULT_PHASES = {0.66F, 0.33F};
	public static final int DEFAULT_TRANSITION = 40;

	/** The four state chips (lang {@code elsewhere.bossbar.state.<state>}). */
	public static final String INVULNERABLE = "invulnerable", ENRAGED = "enraged", VULNERABLE = "vulnerable", WARDED = "warded";
	public static final List<String> STATES = List.of(INVULNERABLE, ENRAGED, VULNERABLE, WARDED);

	public static Map<String, Entry> parse(JsonObject root) {
		Map<String, Entry> out = new LinkedHashMap<>();
		if (root == null || !root.has("bosses")) return out;
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("bosses").entrySet()) {
			JsonObject o = e.getValue().getAsJsonObject();
			int color = 0xC0C0C0;
			try { color = Integer.parseInt(o.get("color").getAsString().replace("#", ""), 16); } catch (Exception ignored) {}
			List<String> sub = new ArrayList<>();
			if (o.has("sub")) o.getAsJsonArray("sub").forEach(s -> sub.add(s.getAsString().toLowerCase(Locale.ROOT)));
			float[] phases = DEFAULT_PHASES.clone();
			if (o.has("phases")) { var a = o.getAsJsonArray("phases"); phases = new float[a.size()]; for (int i = 0; i < phases.length; i++) phases[i] = a.get(i).getAsFloat(); }
			Map<String, String> states = new LinkedHashMap<>();
			if (o.has("states")) for (var s : o.getAsJsonObject("states").entrySet()) {
				String st = s.getValue().getAsString().toLowerCase(Locale.ROOT);
				if (STATES.contains(st)) states.put(s.getKey().toLowerCase(Locale.ROOT), st);
			}
			int transition = o.has("transition") ? o.get("transition").getAsInt() : DEFAULT_TRANSITION;
			out.put(e.getKey(), new Entry(e.getKey(), o.has("icon") ? o.get("icon").getAsString() : "", color, List.copyOf(sub), phases, Map.copyOf(states), transition));
		}
		return out;
	}

	/** Phase notches to draw: the boss's own thresholds, none for bars that are not listed (vanilla and unknown bosses get no invented phases). */
	public static float[] phasesFor(Entry e) {
		return e == null ? new float[0] : e.phases();
	}

	/** Splits at the first separator of any boss family; a plain title has no attack. */
	public static Split split(String title) {
		int best = -1, len = 0;
		for (String s : SEPS) {
			int i = title.indexOf(s);
			if (i >= 0 && (best < 0 || i < best)) { best = i; len = s.length(); }
		}
		if (best < 0) return new Split(title.trim(), "");
		return new Split(title.substring(0, best).trim(), title.substring(best + len).trim());
	}

	/**
	 * Splits a title whose boss is known: the name is the boss's display name and everything after it (any separator,
	 * suffix such as Mother Creak's hearts) is the attack/flash text. Falls back to {@link #split} when the title does not start with the name.
	 */
	public static Split splitFor(String title, String name) {
		String t = title.trim();
		if (name == null || name.isBlank() || !t.startsWith(name)) return split(title);
		String rest = t.substring(name.length());
		int i = 0;
		while (i < rest.length() && (Character.isWhitespace(rest.charAt(i)) || isSep(rest.charAt(i)))) i++;
		return new Split(name, rest.substring(i).trim());
	}

	private static boolean isSep(char c) {
		for (String s : SEPS) if (s.charAt(0) == c) return true;
		return c == '-' || c == ':';
	}

	/** The boss whose display name starts the title (longest name wins), or null (then the bar stays vanilla). {@code nameOf} maps an entity id to its display name. */
	public static Entry match(Map<String, Entry> map, String title, Function<String, String> nameOf) {
		Entry best = null;
		int bestLen = 0;
		String t = title.trim();
		for (Entry e : map.values()) {
			String n = nameOf.apply(e.id());
			if (n == null || n.isBlank() || !t.startsWith(n)) continue;
			if (n.length() > bestLen) { best = e; bestLen = n.length(); }
		}
		return best;
	}

	/** The first boss that owns a sub-bar with this title (rider/mount, tentacle, plate, timer, cast bar), or null. */
	public static Entry subOwner(Map<String, Entry> map, String title) {
		List<Entry> all = subOwners(map, title);
		return all.isEmpty() ? null : all.get(0);
	}

	/** Every boss that may own a sub-bar with this title; the overlay attaches it to whichever of them has a bar on screen. */
	public static List<Entry> subOwners(Map<String, Entry> map, String title) {
		String t = title.toLowerCase(Locale.ROOT);
		List<Entry> out = new ArrayList<>(2);
		for (Entry e : map.values()) for (String s : e.sub()) if (t.contains(s)) { out.add(e); break; }
		return out;
	}

	/** A cast bar (overworld bosses' separate "⚠ Attack" bar): drawn as the attack line with a wind-up fill. */
	public static boolean isCastBar(String title) {
		return title.startsWith("⚠");
	}

	// ---- labels: never show a raw id ----------------------------------------------------------------------------

	/** A snake_case id ("glyph_flare"), a lone lowercase word ("crescent") or an untranslated lang key ("entity.elsewhere.test_duelist"). */
	private static final Pattern RAW = Pattern.compile("[a-z][a-z0-9_]*|[a-z0-9_]+(?:\\.[a-z0-9_]+)+");

	public static boolean isRaw(String s) {
		return s != null && !s.isEmpty() && RAW.matcher(s).matches();
	}

	/** "glyph_flare" to "Glyph Flare"; "entity.elsewhere.test_duelist" to "Test Duelist" (last key segment). */
	public static String humanize(String raw) {
		String s = raw;
		int dot = s.lastIndexOf('.');
		if (dot >= 0 && dot < s.length() - 1) s = s.substring(dot + 1);
		StringBuilder b = new StringBuilder(s.length());
		boolean up = true;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '_') { if (b.length() > 0 && b.charAt(b.length() - 1) != ' ') b.append(' '); up = true; continue; }
			b.append(up ? Character.toUpperCase(c) : c);
			up = false;
		}
		return b.toString().trim();
	}

	/** Lang key of a move's display name. */
	public static String moveKey(String id) {
		return "elsewhere.move." + id;
	}

	/**
	 * The text to show for a bar label: a proper label passes through unchanged (char for char); a raw move id is looked up
	 * as {@code elsewhere.move.<id>} through {@code lang} (null when the key is missing) and otherwise humanized; an
	 * untranslated lang key is humanized from its last segment.
	 */
	public static String label(String text, Function<String, String> lang) {
		if (text == null || text.isEmpty() || !isRaw(text)) return text == null ? "" : text;
		if (text.indexOf('.') < 0) {
			String named = lang.apply(moveKey(text));
			if (named != null && !named.isBlank()) return named;
		}
		return humanize(text);
	}

	// ---- state chips ------------------------------------------------------------------------------------------

	/** Built-in title words for the state chips, matched at word starts in the text after the boss name only. */
	private static final Pattern VULN = Pattern.compile("\\b(exposed|stunned|shattered|cracked|vulnerable|staggered|dazed)");
	private static final Pattern RAGE = Pattern.compile("\\b(enraged?|berserks?|fury|frenzy|wrath|last stand)\\b");
	private static final Pattern INVULN = Pattern.compile("\\b(invulnerable|immune)\\b");
	private static final Pattern WARD = Pattern.compile("\\b(warded|shielded)\\b");

	/** The state named by the text after the boss name ({@code rest}), or null. Boss-specific tokens from the JSON win. */
	public static String stateOf(String rest, Entry e) {
		if (rest == null || rest.isEmpty()) return null;
		String t = rest.toLowerCase(Locale.ROOT);
		if (e != null) for (var s : e.states().entrySet()) if (t.contains(s.getKey())) return s.getValue();
		if (INVULN.matcher(t).find()) return INVULNERABLE;
		if (WARD.matcher(t).find()) return WARDED;
		if (VULN.matcher(t).find()) return VULNERABLE;
		Matcher m = RAGE.matcher(t);
		if (m.find()) return ENRAGED;
		return null;
	}

	/** 0-based phase index: how many thresholds the bar has dropped below. */
	public static int phaseOf(float progress, float[] phases) {
		int n = 0;
		for (float p : phases) if (progress <= p) n++;
		return n;
	}
}
