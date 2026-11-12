package dev.elsewhere.summoner.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * BALANCE.md §D3 `minionOutline` (default on): the owner-only outline on your minions. Read from the shared
 * {@code config/elsewhere-client.json} (written by the Elsewhere HUD settings); re-checked at most every 2 s, only
 * re-parsed when the file changes. A missing file or key means on.
 */
public final class MinionOutlineSetting {
	private MinionOutlineSetting() {}

	private static boolean enabled = true;
	private static long lastCheck, lastModified = Long.MIN_VALUE;

	public static boolean enabled() {
		long now = System.currentTimeMillis();
		if (now - lastCheck < 2000) return enabled;
		lastCheck = now;
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("elsewhere-client.json");
			if (!Files.exists(f)) {
				enabled = true;
				return enabled;
			}
			long m = Files.getLastModifiedTime(f).toMillis();
			if (m == lastModified) return enabled;
			lastModified = m;
			try (Reader r = Files.newBufferedReader(f)) {
				JsonElement e = JsonParser.parseReader(r);
				JsonObject o = e.isJsonObject() ? e.getAsJsonObject() : null;
				enabled = o == null || !o.has("minionOutline") || o.get("minionOutline").getAsBoolean();
			}
		} catch (Exception ex) {
			enabled = true;
		}
		return enabled;
	}
}
