package dev.elsewhere.overworld_bosses;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * The server switch {@code overworldContent} (user decision "vanilla if you so choose", default true). When false, no
 * Overworld boss sites generate (Croaking Shrine, Sun Chamber, Glacier Shelf, Wreck Ring), so their site-only mobs
 * (Tidewrack Drowned) never spawn, and Strawgrim's farm ritual does nothing. Boss minions such as Murder Crows only
 * come from the bosses themselves.
 * Source, in order: the core's {@code dev.elsewhere.ElsewhereConfig.get().overworldContent} (read reflectively, when the
 * core has that field), else the top-level key {@code "overworldContent"} in {@code config/elsewhere.json}, else true.
 * Re-read at every server start.
 */
public final class OwbConfig {
	private OwbConfig() {}

	private static @Nullable Boolean cached;
	/** Self-test hook: forces the value (null = read the config). */
	public static @Nullable Boolean override;
	private static boolean hooked;

	public static void init() {
		if (hooked) return;
		hooked = true;
		ServerLifecycleEvents.SERVER_STARTING.register(s -> cached = null);
	}

	public static boolean overworldContent() {
		if (override != null) return override;
		Boolean c = cached;
		if (c == null) cached = c = read();
		return c;
	}

	private static boolean read() {
		try {
			Class<?> core = Class.forName("dev.elsewhere.ElsewhereConfig");
			Object cfg = core.getMethod("get").invoke(null);
			Object v = core.getField("overworldContent").get(cfg);
			if (v instanceof Boolean b) return b;
		} catch (Throwable ignored) {
			// core absent or without the field: fall back to the file
		}
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("elsewhere.json");
			if (Files.exists(f)) {
				JsonElement e = JsonParser.parseString(Files.readString(f));
				if (e.isJsonObject() && e.getAsJsonObject().has("overworldContent")) return e.getAsJsonObject().get("overworldContent").getAsBoolean();
			}
		} catch (Throwable t) {
			OverworldBossesContent.LOGGER.warn("[elsewhere/overworld_bosses] could not read overworldContent from config/elsewhere.json: {}", t.toString());
		}
		return true;
	}
}
