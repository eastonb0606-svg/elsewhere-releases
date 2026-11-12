package dev.elsewhere.stub.petalwind_glasswaste;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/**
 * STUB of the shared per-player client settings (CONTRACT "every non-vanilla on-screen display is toggleable"). The
 * arsenal module owns the real mechanism (keybind "Toggle Elsewhere HUD", {@code /elsewhere_hud}, the settings screen);
 * at merge this class is repointed at it. Same API in every module stub:
 * {@code registerMeter / registerToggle / meter / toggle}. Reads flat keys from {@code config/elsewhere-client.json}
 * in the BALANCE.md §D4 format ({@code "meters": {"heat": "auto"|"always"|"hidden", ...}}, {@code "screenEffects": true},
 * {@code "reduceMotion": false}, {@code "hudHidden": true} hides every meter), re-read when the file's mtime changes (checked at most once a second).
 * Lives in the main source set only so the server self-test can check the parsing; it touches no client classes.
 */
public final class ClientSettingsStub {
	private ClientSettingsStub() {}

	public enum Mode { AUTO, ALWAYS, HIDDEN }

	public static final String FILE = "elsewhere-client.json";
	private static final Map<String, Mode> METERS = new LinkedHashMap<>();
	private static final Map<String, Boolean> TOGGLES = new LinkedHashMap<>();
	private static JsonObject values = new JsonObject();
	private static long lastCheck, lastMtime = Long.MIN_VALUE;

	/** Live override (tests, or any module that owns the settings): read on every call, no file polling. */
	private static volatile java.util.function.Function<String, Mode> live;
	private static boolean probed;
	private static java.lang.reflect.Method hudMode, hudAllHidden, hudRegister, hudEffects, hudReduce;

	public static void setLive(java.util.function.Function<String, Mode> f) {
		live = f;
	}

	/** The arsenal's shared HUD settings (dev.elsewhere.hud.ElsewhereHud), when present in the combined jar: live, same tick. */
	private static synchronized boolean arsenal() {
		if (!probed) {
			probed = true;
			try {
				Class<?> c = Class.forName("dev.elsewhere.hud.ElsewhereHud");
				hudMode = c.getMethod("mode", String.class);
				hudAllHidden = c.getMethod("allHidden");
				hudRegister = c.getMethod("registerMeter", String.class, String.class);
				hudEffects = c.getMethod("screenEffects");
				hudReduce = c.getMethod("reduceMotion");
			} catch (Throwable t) {
				hudMode = null;
			}
		}
		return hudMode != null;
	}

	/** Declares a meter key (default Auto) so the settings UI can list it. */
	public static synchronized void registerMeter(String key) {
		METERS.putIfAbsent(key, Mode.AUTO);
		if (arsenal()) {
			try {
				hudRegister.invoke(null, key, key);
			} catch (Throwable ignored) {
			}
		}
	}

	/** Declares an on/off key with its default. */
	public static synchronized void registerToggle(String key, boolean def) {
		TOGGLES.putIfAbsent(key, def);
	}

	public static synchronized Mode meter(String key) {
		var l = live;
		if (l != null) return l.apply(key);
		if (arsenal()) {
			try {
				if ((Boolean) hudAllHidden.invoke(null)) return Mode.HIDDEN;
				return Mode.valueOf(hudMode.invoke(null, key).toString().toUpperCase(Locale.ROOT));
			} catch (Throwable ignored) {
			}
		}
		refresh();
		return meterFrom(values, key);
	}

	public static synchronized boolean toggle(String key, boolean def) {
		if (live == null && arsenal()) {
			try {
				if (key.equals("screenEffects")) return (Boolean) hudEffects.invoke(null);
				if (key.equals("reduceMotion")) return (Boolean) hudReduce.invoke(null);
			} catch (Throwable ignored) {
			}
		}
		refresh();
		return toggleFrom(values, key, def);
	}

	/**
	 * Pure parsing (used by the self-test): {@code hudHidden} wins, then {@code meters.<key>} (BALANCE.md §D4 file format),
	 * then a flat {@code <key>Meter} / {@code <key>} entry (older stubs), then Auto.
	 */
	public static Mode meterFrom(JsonObject o, String key) {
		if (toggleFrom(o, "hudHidden", false)) return Mode.HIDDEN;
		JsonElement e = null;
		if (o.get("meters") instanceof JsonObject meters) e = meters.get(key);
		if (e == null) e = o.get(key + "Meter");
		if (e == null) e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) return Mode.AUTO;
		return switch (e.getAsString().trim().toLowerCase(Locale.ROOT)) {
			case "always" -> Mode.ALWAYS;
			case "hidden" -> Mode.HIDDEN;
			default -> Mode.AUTO;
		};
	}

	public static boolean toggleFrom(JsonObject o, String key, boolean def) {
		JsonElement e = o.get(key);
		if (e == null || !e.isJsonPrimitive()) return def;
		try {
			return e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : Boolean.parseBoolean(e.getAsString().trim());
		} catch (RuntimeException ex) {
			return def;
		}
	}

	/** Parses a settings file body; anything unreadable counts as empty (all defaults). */
	public static JsonObject parse(String json) {
		try {
			JsonElement e = JsonParser.parseString(json);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
		} catch (RuntimeException ex) {
			return new JsonObject();
		}
	}

	private static void refresh() {
		long now = System.currentTimeMillis();
		if (now - lastCheck < 1000L) return;
		lastCheck = now;
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE);
			long mtime = Files.exists(p) ? Files.getLastModifiedTime(p).toMillis() : -1L;
			if (mtime == lastMtime) return;
			lastMtime = mtime;
			values = mtime < 0 ? new JsonObject() : parse(Files.readString(p, StandardCharsets.UTF_8));
		} catch (Exception ex) {
			values = new JsonObject();
		}
	}
}
