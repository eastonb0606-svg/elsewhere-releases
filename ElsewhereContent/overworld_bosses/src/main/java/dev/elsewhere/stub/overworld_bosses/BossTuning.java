package dev.elsewhere.stub.overworld_bosses;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-boss tuning numbers read from {@code data/elsewhere/boss_tuning/<boss>.json} on the mod's classpath, with the
 * values in code as defaults. Format: {@code {"stats": {"hp": 340, ...}, "attacks": {"tongue_lash": {"cooldown": 80,
 * "damage": 16}}}}. STUB: the core will load these through a datapack reload listener so tuning is a JSON change
 * without a rebuild.
 */
public final class BossTuning {
	private static final Map<String, BossTuning> CACHE = new ConcurrentHashMap<>();
	private final JsonObject root;

	private BossTuning(JsonObject root) {
		this.root = root;
	}

	public static BossTuning get(String bossId) {
		return CACHE.computeIfAbsent(bossId, BossTuning::load);
	}

	private static BossTuning load(String bossId) {
		String path = "/data/elsewhere/boss_tuning/" + bossId + ".json";
		try (InputStream in = BossTuning.class.getResourceAsStream(path)) {
			if (in != null) {
				JsonElement e = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				if (e.isJsonObject()) return new BossTuning(e.getAsJsonObject());
			}
		} catch (Exception ignored) {
		}
		return new BossTuning(new JsonObject());
	}

	public double stat(String key, double fallback) {
		JsonObject stats = this.root.has("stats") ? this.root.getAsJsonObject("stats") : null;
		return stats != null && stats.has(key) ? stats.get(key).getAsDouble() : fallback;
	}

	public int attackInt(String attack, String field, int fallback) {
		JsonObject a = attackObj(attack);
		return a != null && a.has(field) ? a.get(field).getAsInt() : fallback;
	}

	public float attackFloat(String attack, String field, float fallback) {
		JsonObject a = attackObj(attack);
		return a != null && a.has(field) ? a.get(field).getAsFloat() : fallback;
	}

	private JsonObject attackObj(String attack) {
		JsonObject attacks = this.root.has("attacks") ? this.root.getAsJsonObject("attacks") : null;
		return attacks != null && attacks.has(attack) ? attacks.getAsJsonObject(attack) : null;
	}
}
