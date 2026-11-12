package dev.elsewhere.summoner;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server config for minions (0.4.0): {@code maxMinionsPerPlayer} (default 4, range 0..4). It can only lower the
 * limit: a player's minion slots are min(gear slots, hard cap 4, this value); 0 turns summoning off.
 * <p>
 * Source, in order: a public int field {@code maxMinionsPerPlayer} on the core's {@code dev.elsewhere.ElsewhereConfig}
 * (if the core adds one at merge, it wins), else {@code config/elsewhere-summoner.json}
 * ({@code {"maxMinionsPerPlayer": 4}}, written with the default when missing). Read on server start.
 */
public final class SummonerConfig {
	private SummonerConfig() {}

	private static final Logger LOG = LoggerFactory.getLogger("elsewhere_summoner");
	public static final String FILE = "elsewhere-summoner.json";
	public static final int DEFAULT_MAX = GearStats.HARD_SLOT_CAP;

	private static int maxMinionsPerPlayer = DEFAULT_MAX;

	/** The server's minion limit per player (0..4). */
	public static int maxMinionsPerPlayer() {
		return maxMinionsPerPlayer;
	}

	/** Self-test / command use: sets the limit (clamped 0..4) without touching the file. */
	public static void setMaxMinionsPerPlayer(int v) {
		maxMinionsPerPlayer = clamp(v);
	}

	private static int clamp(int v) {
		return Math.max(0, Math.min(GearStats.HARD_SLOT_CAP, v));
	}

	public static void load() {
		Integer core = fromCore();
		if (core != null) {
			maxMinionsPerPlayer = clamp(core);
			LOG.info("maxMinionsPerPlayer = {} (from config/elsewhere.json)", maxMinionsPerPlayer);
			return;
		}
		Path p = FabricLoader.getInstance().getConfigDir().resolve(FILE);
		int v = DEFAULT_MAX;
		JsonObject o = new JsonObject();
		try {
			if (Files.exists(p)) {
				o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
				if (o.has("maxMinionsPerPlayer")) v = o.get("maxMinionsPerPlayer").getAsInt();
			}
		} catch (Exception e) {
			LOG.warn("Could not read config/{} ({}); using maxMinionsPerPlayer = {}", FILE, e.getMessage(), DEFAULT_MAX);
			v = DEFAULT_MAX;
		}
		maxMinionsPerPlayer = clamp(v);
		if (v != maxMinionsPerPlayer) LOG.warn("maxMinionsPerPlayer {} is outside 0..{}; using {}", v, GearStats.HARD_SLOT_CAP, maxMinionsPerPlayer);
		try {
			o.addProperty("maxMinionsPerPlayer", maxMinionsPerPlayer);
			if (!o.has("_comment")) o.addProperty("_comment", "Minions per player, 0-4 (default 4). Can only lower the limit from gear; 0 turns summoning off.");
			Files.createDirectories(p.getParent());
			Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(o));
		} catch (Exception e) {
			LOG.warn("Could not write config/{}: {}", FILE, e.getMessage());
		}
	}

	private static Integer fromCore() {
		try {
			Class<?> c = Class.forName("dev.elsewhere.ElsewhereConfig");
			Object cfg = c.getMethod("get").invoke(null);
			var f = c.getField("maxMinionsPerPlayer");
			return ((Number) f.get(cfg)).intValue();
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			return null;
		}
	}
}
