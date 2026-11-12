package dev.elsewhere.kingdom.armies.townsfolk;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.elsewhere.kingdom.armies.ArmiesConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Kill switch for townsfolk: key {@code "townsfolk"} (default true) in {@code config/elsewhere_armies.json}, next to the other armies
 * switches; the master {@code "enabled": false} turns townsfolk off too. System property {@code elsewhere.armies.townsfolk} overrides.
 * Off: nothing spawns and live townsfolk fold away at once (they are never saved, so nothing lingers in the world). While another
 * module's self-test runs ({@code -D<x>.selftest=true}) townsfolk stay off so they never touch other tests; the townsfolk self-test
 * ({@code elsewhere.townsfolk.selftest}) turns them on for itself.
 */
public final class TownsfolkConfig {
	private TownsfolkConfig() {}

	public static final String KEY = "townsfolk", SELFTEST = "elsewhere.townsfolk.selftest";
	public static boolean enabled = true;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	static void load() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere_armies.json");
		try {
			JsonObject o = Files.exists(p) ? GSON.fromJson(Files.readString(p), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			if (o.has(KEY)) enabled = o.get(KEY).getAsBoolean();
			else {
				o.addProperty(KEY, true);
				Files.createDirectories(p.getParent());
				Files.writeString(p, GSON.toJson(o));
			}
		} catch (Exception e) {
			dev.elsewhere.kingdom.KingdomContent.LOG.warn("elsewhere_armies.json unreadable for townsfolk ({}); townsfolk stay {}", e.toString(), enabled ? "on" : "off");
		}
		if (System.getProperty("elsewhere.armies.townsfolk") != null) enabled = Boolean.getBoolean("elsewhere.armies.townsfolk");
	}

	/** True while townsfolk may live: switch on, armies overhaul on, and no other module's self-test running. */
	public static boolean active() {
		return enabled && ArmiesConfig.enabled && !otherSelfTest();
	}

	static boolean otherSelfTest() {
		for (String k : System.getProperties().stringPropertyNames())
			if (k.endsWith(".selftest") && !k.equals(SELFTEST) && Boolean.getBoolean(k)) return true;
		return false;
	}
}
