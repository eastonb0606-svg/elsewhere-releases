package dev.elsewhere.arsenal.client.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * Client effects level for weapon animations (POLISH rule 7): {@code "weaponFx": "low" | "normal" | "high"} in
 * {@code config/elsewhere-client.json} (the shared Elsewhere display file; other keys are kept), default normal.
 * low = no swing trails (the swing poses and item states stay), normal = trails for you and players within 24 blocks
 * with 5 samples, high = within 32 blocks with 6. Command: {@code /elsewhere_weaponfx [low|normal|high]}.
 * Read once at start and on change; never polled.
 */
public final class UseAnimConfig {
	private UseAnimConfig() {}

	public static final String KEY = "weaponFx";
	static final String[] NAMES = {"low", "normal", "high"};
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static int level = 1;
	private static boolean commandDone;

	public static int level() {
		return level;
	}

	public static int parse(String s) {
		if (s == null) return -1;
		String k = s.toLowerCase(Locale.ROOT);
		for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(k)) return i;
		return -1;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
	}

	private static JsonObject read() {
		try {
			Path p = file();
			if (Files.exists(p)) {
				JsonElement e = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
				if (e.isJsonObject()) return e.getAsJsonObject();
			}
		} catch (Exception ignored) {
			// unreadable file: defaults
		}
		return new JsonObject();
	}

	public static void load() {
		JsonObject o = read();
		try {
			int l = o.has(KEY) ? parse(o.get(KEY).getAsString()) : -1;
			level = l < 0 ? 1 : l;
		} catch (RuntimeException e) {
			level = 1;
		}
	}

	/** Sets and saves the level (read-merge-write: only our key changes). */
	public static void set(int l) {
		level = Math.max(0, Math.min(2, l));
		try {
			JsonObject o = read();
			o.addProperty(KEY, NAMES[level]);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (Exception ignored) {
			// the level still applies for this session
		}
	}

	static void registerCommand() {
		if (commandDone) return;
		commandDone = true;
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(ClientCommands.literal("elsewhere_weaponfx")
			.executes(c -> {
				c.getSource().sendFeedback(Component.literal("Elsewhere weapon effects: " + NAMES[level] + " (low | normal | high)"));
				return 1;
			})
			.then(ClientCommands.argument("level", StringArgumentType.word())
				.suggests((c, b) -> {
					for (String n : NAMES) b.suggest(n);
					return b.buildFuture();
				})
				.executes(c -> {
					int l = parse(StringArgumentType.getString(c, "level"));
					if (l < 0) {
						c.getSource().sendError(Component.literal("Level must be low, normal or high"));
						return 0;
					}
					set(l);
					c.getSource().sendFeedback(Component.literal("Elsewhere weapon effects: " + NAMES[l]));
					return 1;
				}))));
	}
}
