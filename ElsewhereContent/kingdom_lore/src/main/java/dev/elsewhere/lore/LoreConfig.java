package dev.elsewhere.lore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** {@code config/elsewhere-kingdom-lore.json}, written with defaults on first start. Only what a server owner may want. */
public final class LoreConfig {
	private LoreConfig() {}

	/** Royal patrols on or off. */
	public static boolean patrolsEnabled = true;
	/** Multiplier on the patrol chance (1.0 = the tuned rate; see {@link PatrolRate}). */
	public static double patrolChanceScale = 1.0;
	/** Liberated cartographers sell the Survey Map. */
	public static boolean cartographerSellsMap = true;

	public static void load() {
		Path p = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-kingdom-lore.json");
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		try {
			if (Files.exists(p)) {
				JsonObject o = gson.fromJson(Files.readString(p), JsonObject.class);
				if (o.has("patrolsEnabled")) patrolsEnabled = o.get("patrolsEnabled").getAsBoolean();
				if (o.has("patrolChanceScale")) patrolChanceScale = Math.max(0, Math.min(4, o.get("patrolChanceScale").getAsDouble()));
				if (o.has("cartographerSellsMap")) cartographerSellsMap = o.get("cartographerSellsMap").getAsBoolean();
			} else {
				JsonObject o = new JsonObject();
				o.addProperty("patrolsEnabled", patrolsEnabled);
				o.addProperty("patrolChanceScale", patrolChanceScale);
				o.addProperty("cartographerSellsMap", cartographerSellsMap);
				Files.createDirectories(p.getParent());
				Files.writeString(p, gson.toJson(o));
			}
		} catch (Exception e) {
			LoreContent.LOG.warn("elsewhere-kingdom-lore.json unreadable, using defaults: {}", e.toString());
		}
	}
}
