package dev.elsewhere.stub.nether_end_bosses;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * One scheduled boss attack. Defaults live in code; {@code data/elsewhere/boss_tuning/<boss>.json} overrides any field,
 * so tuning is a JSON change. Times are in ticks; damage is the raw Normal-difficulty value from BOSSES.md.
 */
public record AttackSpec(String id, String name, int windup, int cooldown, int phasesMask, int weight, float damage) {
	/** Big attacks (heavy damage or a long wind-up) get a 25% longer wind-up, like the Overworld bosses' BIG attacks. */
	public static final float BIG_WINDUP_SCALE = 1.25F;

	public boolean isBig() {
		return windup >= 30 || damage >= 28.0F;
	}

	public AttackSpec withBigWindup() {
		if (windup <= 0 || !isBig()) return this;
		return new AttackSpec(id, name, Math.round(windup * BIG_WINDUP_SCALE), cooldown, phasesMask, weight, damage);
	}

	public boolean inPhase(int phase) {
		return (phasesMask & (1 << phase)) != 0;
	}

	public static int phases(int... phases) {
		int mask = 0;
		for (int p : phases) mask |= 1 << p;
		return mask;
	}

	/** Applies the boss's tuning file (if any) on top of the code defaults. */
	public static List<AttackSpec> tuned(String bossId, List<AttackSpec> defaults) {
		JsonObject attacks = null;
		try (InputStream in = AttackSpec.class.getResourceAsStream("/data/elsewhere/boss_tuning/" + bossId + ".json")) {
			if (in != null) {
				JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				attacks = root.has("attacks") ? root.getAsJsonObject("attacks") : null;
			}
		} catch (Exception e) {
			StubFramework.LOGGER.warn("Bad boss tuning file for {}: {}", bossId, e.toString());
		}
		if (attacks == null) return defaults;
		List<AttackSpec> out = new ArrayList<>();
		for (AttackSpec d : defaults) {
			JsonObject o = attacks.has(d.id) ? attacks.getAsJsonObject(d.id) : null;
			if (o == null) {
				out.add(d);
				continue;
			}
			int mask = d.phasesMask;
			if (o.has("phases")) {
				mask = 0;
				JsonArray arr = o.getAsJsonArray("phases");
				for (JsonElement e : arr) mask |= 1 << e.getAsInt();
			}
			out.add(new AttackSpec(d.id, o.has("name") ? o.get("name").getAsString() : d.name,
				o.has("windup") ? o.get("windup").getAsInt() : d.windup,
				o.has("cooldown") ? o.get("cooldown").getAsInt() : d.cooldown,
				mask,
				o.has("weight") ? o.get("weight").getAsInt() : d.weight,
				o.has("damage") ? o.get("damage").getAsFloat() : d.damage));
		}
		return out;
	}
}
