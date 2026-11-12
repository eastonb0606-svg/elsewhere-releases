package dev.elsewhere.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Shared client settings for every non-vanilla Elsewhere on-screen display (CONTRACT "RESTART" user decision).
 * <ul>
 * <li>Each meter registers once: {@code ElsewhereHud.registerMeter("heat", "heatMeter")}; it then reads
 * {@code ElsewhereHud.shouldShow("heat", autoCondition)} every frame (Auto = your own condition, Always, Hidden).</li>
 * <li>One unbound keybind "Toggle Elsewhere HUD" hides/shows all meters at once ({@link #allHidden()}).</li>
 * <li>Client command {@code /elsewhere_hud <meter|all> auto|always|hidden}, plus
 * {@code /elsewhere_hud effects on|off} and {@code /elsewhere_hud reduce_motion on|off} for screen effects.</li>
 * <li>Saved in {@code config/elsewhere-client.json}; keys written by other code (e.g. {@code longbowZoom}) are kept.</li>
 * </ul>
 * At merge this class moves to the core's client package unchanged (package {@code dev.elsewhere.hud}).
 */
public final class ElsewhereHud {
	private ElsewhereHud() {}

	public enum Mode {
		AUTO, ALWAYS, HIDDEN;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Mode next() {
			return values()[(ordinal() + 1) % values().length];
		}

		public static Mode parse(String s, Mode fallback) {
			for (Mode m : values()) if (m.key().equalsIgnoreCase(s)) return m;
			return fallback;
		}
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, String> CONFIG_KEYS = new LinkedHashMap<>();
	private static final Map<String, Mode> MODES = new LinkedHashMap<>();
	private static boolean allHidden, screenEffects = true, reduceMotion, loaded, initDone;
	private static final Map<String, Boolean> FLAGS = new LinkedHashMap<>();
	static {
		for (String m : new String[]{"glimmer", "heat", "altitude", "haze", "resonance"}) CONFIG_KEYS.put(m, m);
		for (String f : new String[]{"trinketMarks", "minionOutline", "rankTitles", "longbowZoom"}) FLAGS.put(f, true);
	}
	public static KeyMapping TOGGLE_HUD;
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("elsewhere", "elsewhere"));

	/** On/off display switch from BALANCE.md §D3 (trinketMarks, minionOutline, rankTitles, longbowZoom; default on). */
	public static boolean flag(String key) {
		load();
		return FLAGS.getOrDefault(key, true);
	}

	public static void setFlag(String key, boolean v) {
		FLAGS.put(key, v);
		save();
	}

	/** Registers a meter (id = command name and key under "meters" in elsewhere-client.json, e.g. "glimmer"); configKey is kept for API compatibility. */
	public static synchronized void registerMeter(String id, String configKey) {
		CONFIG_KEYS.put(id, id);
		load();
		MODES.putIfAbsent(id, Mode.AUTO);
	}

	public static Mode mode(String id) {
		return MODES.getOrDefault(id, Mode.AUTO);
	}

	public static void setMode(String id, Mode m) {
		MODES.put(id, m);
		save();
	}

	/** True if the meter should draw now: false while all meters are toggled off or the meter is Hidden; Auto uses autoCondition. */
	public static boolean shouldShow(String id, boolean autoCondition) {
		if (allHidden) return false;
		return switch (mode(id)) {
			case ALWAYS -> true;
			case HIDDEN -> false;
			case AUTO -> autoCondition;
		};
	}

	/** True if the player can't see this meter at all right now (used for fallback messages such as "Not enough Glimmer"). */
	public static boolean invisible(String id) {
		return allHidden || mode(id) == Mode.HIDDEN;
	}

	public static boolean allHidden() {
		return allHidden;
	}

	public static void setAllHidden(boolean v) {
		allHidden = v;
		save();
	}

	/** Elsewhere screen effects (vignettes, fog pulses, heat shimmer): on/off. */
	public static boolean screenEffects() {
		return screenEffects;
	}

	/** Reduce-motion option for screen effects (no pulsing/shimmer, static tints only). */
	public static boolean reduceMotion() {
		return reduceMotion;
	}

	public static void init() {
		if (initDone) return;
		initDone = true;
		load();
		TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.elsewhere.toggle_hud", InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (TOGGLE_HUD.consumeClick()) {
				setAllHidden(!allHidden);
				if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable(allHidden ? "elsewhere.hud.all_hidden" : "elsewhere.hud.all_shown"));
			}
		});
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(ClientCommands.literal("elsewhere_hud")
			.executes(c -> {
				c.getSource().sendFeedback(Component.literal("Elsewhere HUD: " + MODES + " hudHidden=" + allHidden + " screenEffects=" + screenEffects + " reduceMotion=" + reduceMotion + " " + FLAGS));
				return 1;
			})
			.then(ClientCommands.argument("meter", StringArgumentType.word())
				.suggests((c, b) -> {
					b.suggest("all");
					b.suggest("effects");
					b.suggest("reduce_motion");
					FLAGS.keySet().forEach(b::suggest);
					CONFIG_KEYS.keySet().forEach(b::suggest);
					return b.buildFuture();
				})
				.then(ClientCommands.argument("mode", StringArgumentType.word())
					.suggests((c, b) -> {
						String m = StringArgumentType.getString(c, "meter");
						if (m.equals("effects") || m.equals("reduce_motion") || FLAGS.containsKey(m)) {
							b.suggest("on");
							b.suggest("off");
							if (m.equals("effects")) b.suggest("reduced");
						} else for (Mode x : Mode.values()) b.suggest(x.key());
						return b.buildFuture();
					})
					.executes(c -> run(c.getSource(), StringArgumentType.getString(c, "meter"), StringArgumentType.getString(c, "mode")))))));
	}

	private static int run(net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource src, String meter, String value) {
		if (FLAGS.containsKey(meter)) {
			boolean on = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
			setFlag(meter, on);
			src.sendFeedback(Component.literal("Elsewhere " + meter + ": " + (on ? "on" : "off")));
			return 1;
		}
		if (meter.equals("effects") || meter.equals("reduce_motion")) {
			boolean reduced = value.equalsIgnoreCase("reduced");
			boolean on = reduced || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
			if (meter.equals("effects")) {
				screenEffects = on;
				reduceMotion = reduced;
			} else reduceMotion = on;
			save();
			src.sendFeedback(Component.literal("Elsewhere " + meter + ": " + (on ? "on" : "off")));
			return 1;
		}
		Mode m = Mode.parse(value, null);
		if (m == null) {
			src.sendError(Component.literal("Mode must be auto, always or hidden"));
			return 0;
		}
		if (meter.equals("all")) {
			for (String id : CONFIG_KEYS.keySet()) MODES.put(id, m);
			allHidden = false;
			save();
			src.sendFeedback(Component.translatable("elsewhere.hud.mode", "all", Component.translatable("elsewhere.hud.mode." + m.key())));
			return 1;
		}
		if (!CONFIG_KEYS.containsKey(meter)) {
			src.sendError(Component.translatable("elsewhere.hud.unknown", meter, String.join(", ", CONFIG_KEYS.keySet())));
			return 0;
		}
		setMode(meter, m);
		src.sendFeedback(Component.translatable("elsewhere.hud.mode", meter, Component.translatable("elsewhere.hud.mode." + m.key())));
		return 1;
	}

	// ---------------------------------------------------------------- config file

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
	}

	private static JsonObject read() {
		try {
			if (Files.exists(file())) {
				JsonElement e = JsonParser.parseString(Files.readString(file()));
				if (e.isJsonObject()) return e.getAsJsonObject();
			}
		} catch (IOException | RuntimeException ignored) {
		}
		return new JsonObject();
	}

	private static synchronized void load() {
		JsonObject o = read();
		JsonObject meters = o.has("meters") && o.get("meters").isJsonObject() ? o.getAsJsonObject("meters") : new JsonObject();
		for (String id : CONFIG_KEYS.keySet()) {
			if (meters.has(id)) MODES.put(id, Mode.parse(meters.get(id).getAsString(), Mode.AUTO));
		}
		for (String f : FLAGS.keySet()) if (o.has(f) && o.get(f).isJsonPrimitive()) FLAGS.put(f, o.get(f).getAsBoolean());
		if (!loaded) {
			loaded = true;
			if (o.has("hudHidden")) allHidden = o.get("hudHidden").getAsBoolean();
			if (o.has("screenEffects")) screenEffects = o.get("screenEffects").getAsBoolean();
			if (o.has("reduceMotion")) reduceMotion = o.get("reduceMotion").getAsBoolean();
		}
	}

	/** Read-merge-write: only our keys change, every other key in the file is kept. */
	public static synchronized void save() {
		JsonObject o = read();
		JsonObject meters = o.has("meters") && o.get("meters").isJsonObject() ? o.getAsJsonObject("meters") : new JsonObject();
		for (String id : CONFIG_KEYS.keySet()) meters.addProperty(id, mode(id).key());
		o.add("meters", meters);
		for (Map.Entry<String, Boolean> f : FLAGS.entrySet()) if (!o.has(f.getKey())) o.addProperty(f.getKey(), f.getValue()); else if (!f.getKey().equals("longbowZoom")) o.addProperty(f.getKey(), f.getValue());
		o.addProperty("hudHidden", allHidden);
		o.addProperty("screenEffects", screenEffects);
		o.addProperty("reduceMotion", reduceMotion);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(o));
		} catch (IOException ignored) {
		}
	}

	/** For tests/screenshots. */
	public static void setForTest(String id, Mode m) {
		MODES.put(id, m);
	}

	static Minecraft mc() {
		return Minecraft.getInstance();
	}
}
