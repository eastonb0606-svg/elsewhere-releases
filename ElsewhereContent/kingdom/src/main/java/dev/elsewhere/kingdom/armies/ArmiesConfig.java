package dev.elsewhere.kingdom.armies;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Kill switches for the 3.0 armies overhaul, {@code config/elsewhere_armies.json}, one key per feature, all default on:
 * {@code enabled} (the whole overhaul: caps, rings, hygiene; off = exactly 2.1), {@code caps} ({@code epic} 32/64/192 or {@code fallback}
 * 24/48/160), {@code attack_tokens} (at most N soldiers of a kingdom fight one player at once), {@code waves} (watched war-layer assaults become
 * reinforcement-wave battles) and {@code routine} (garrisons move between activity anchors at watch changes). Missing keys are added
 * with their default when the file is read.
 */
public final class ArmiesConfig {
	private ArmiesConfig() {}

	public static boolean enabled = true, waves = true, routine = true, tokens = true, rangedRepath = true;
	/** {@code "caps": "epic"} (32 per player / 64 per area / 192 per server, default) or {@code "fallback"} (24 / 48 / 160). */
	public static boolean fallbackCaps;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/**
	 * True when another module's self-test runs ({@code -D<x>.selftest=true}, other than the armies one). Those tests assert 2.1 numbers
	 * (caps of 15/30, the built-in war hand-off, garrisons at their posts), so caps, waves and routine stay at 2.1 behaviour for them.
	 */
	public static boolean otherSelfTest() {
		for (String k : System.getProperties().stringPropertyNames())
			if (k.endsWith(".selftest") && !k.equals("elsewhere.armies.selftest") && Boolean.getBoolean(k)) return true;
		return false;
	}

	static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("elsewhere_armies.json");
	}

	static void load() {
		Path p = path();
		try {
			JsonObject o = Files.exists(p) ? GSON.fromJson(Files.readString(p), JsonObject.class) : null;
			if (o == null) o = new JsonObject();
			boolean write = false;
			if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean(); else { o.addProperty("enabled", true); write = true; }
			if (o.has("waves")) waves = o.get("waves").getAsBoolean(); else { o.addProperty("waves", true); write = true; }
			if (o.has("ranged_repath")) rangedRepath = o.get("ranged_repath").getAsBoolean(); else { o.addProperty("ranged_repath", true); write = true; }
			if (o.has("routine")) routine = o.get("routine").getAsBoolean(); else { o.addProperty("routine", true); write = true; }
			if (o.has("attack_tokens")) tokens = o.get("attack_tokens").getAsBoolean(); else { o.addProperty("attack_tokens", true); write = true; }
			if (o.has("caps")) fallbackCaps = "fallback".equalsIgnoreCase(o.get("caps").getAsString()); else { o.addProperty("caps", "epic"); write = true; }
			if (write) {
				Files.createDirectories(p.getParent());
				Files.writeString(p, GSON.toJson(o));
			}
		} catch (Exception e) {
			dev.elsewhere.kingdom.KingdomContent.LOG.warn("elsewhere_armies.json unreadable ({}); armies overhaul stays {}", e.toString(), enabled ? "on" : "off");
		}
		if (System.getProperty("elsewhere.armies.enabled") != null) enabled = Boolean.getBoolean("elsewhere.armies.enabled");
		if (System.getProperty("elsewhere.armies.waves") != null) waves = Boolean.getBoolean("elsewhere.armies.waves");
		if (System.getProperty("elsewhere.armies.rangedrepath") != null) rangedRepath = Boolean.getBoolean("elsewhere.armies.rangedrepath");
		if (System.getProperty("elsewhere.armies.routine") != null) routine = Boolean.getBoolean("elsewhere.armies.routine");
		if (System.getProperty("elsewhere.armies.caps") != null) fallbackCaps = "fallback".equalsIgnoreCase(System.getProperty("elsewhere.armies.caps"));
	}
}
