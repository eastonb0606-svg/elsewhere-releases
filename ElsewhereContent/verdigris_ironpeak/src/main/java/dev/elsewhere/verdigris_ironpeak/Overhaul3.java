package dev.elsewhere.verdigris_ironpeak;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 3.0 overhaul kill switches for boss-drop numbers (GEAR.md A3, BUILD-3.0 "Kill switches"). Every key defaults on.
 * Turn one off with {@code "key": false} in {@code config/elsewhere-overhaul3.json} or the JVM flag
 * {@code -Delsewhere.overhaul3.<key>=false}. Read once, at class load; nothing polls.
 */
public final class Overhaul3 {
	private Overhaul3() {}

	private static final JsonObject FILE = read();

	/** {@code rebalance_bossdrops}: 3.0 numbers for this module's boss-drop gear (GEAR.md A4). */
	public static final boolean REBALANCE_BOSSDROPS = on("rebalance_bossdrops");

	public static boolean on(String key) {
		String prop = System.getProperty("elsewhere.overhaul3." + key);
		if (prop != null) return !"false".equalsIgnoreCase(prop);
		try {
			if (FILE != null && FILE.has(key)) return FILE.get(key).getAsBoolean();
		} catch (RuntimeException e) {
			// a malformed value keeps the default
		}
		return true;
	}

	private static JsonObject read() {
		try {
			Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (!Files.isRegularFile(p)) return null;
			return JsonParser.parseString(Files.readString(p)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
