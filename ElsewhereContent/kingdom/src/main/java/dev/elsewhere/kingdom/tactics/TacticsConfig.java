package dev.elsewhere.kingdom.tactics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/**
 * The {@code tactics} block of a faction file ({@code data/<ns>/kingdom_faction/<name>.json}, FIELD-TACTICS 1.7), read next to the faction definition and reloaded with it:
 * {@code decision_interval}, {@code min_local_odds}, {@code retreat_odds}, {@code reaction_ticks [min,max]}, {@code blunder_rate}, {@code mob_policy}, {@code behaviours
 * {id: {weight, min_ratio}}}, {@code roles {ROLE: {standoff, strafe}}}, {@code signals {horn, banner, shout_chance}}. Values here override the registered profile's.
 */
public final class TacticsConfig {
	private TacticsConfig() {}

	public static final class Cfg {
		public Integer interval, reactMin, reactMax;
		public Double minOdds, retreatOdds, blunder, shoutChance;
		public String mobPolicy;
		public Boolean horn, banner;
		public final Map<String, Double> weight = new HashMap<>(), minRatio = new HashMap<>();
		public final Map<String, double[]> roles = new HashMap<>();
	}

	private static Map<Identifier, Cfg> cfgs = Map.of();

	public static @Nullable Cfg of(Identifier faction) {
		return cfgs.get(faction);
	}

	static Double dbl(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : null;
	}

	static Cfg parse(JsonObject t) {
		Cfg c = new Cfg();
		Double d;
		if ((d = dbl(t, "decision_interval")) != null) c.interval = d.intValue();
		c.minOdds = dbl(t, "min_local_odds");
		c.retreatOdds = dbl(t, "retreat_odds");
		c.blunder = dbl(t, "blunder_rate");
		if (t.has("reaction_ticks") && t.get("reaction_ticks").isJsonArray() && t.getAsJsonArray("reaction_ticks").size() == 2) {
			c.reactMin = t.getAsJsonArray("reaction_ticks").get(0).getAsInt();
			c.reactMax = t.getAsJsonArray("reaction_ticks").get(1).getAsInt();
		}
		if (t.has("mob_policy")) c.mobPolicy = t.get("mob_policy").getAsString();
		if (t.has("behaviours") && t.get("behaviours").isJsonObject()) {
			for (var e : t.getAsJsonObject("behaviours").entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject b = e.getValue().getAsJsonObject();
				Double w = dbl(b, "weight"), r = dbl(b, "min_ratio");
				if (w != null) c.weight.put(e.getKey(), w);
				if (r != null) c.minRatio.put(e.getKey(), r);
			}
		}
		if (t.has("roles") && t.get("roles").isJsonObject()) {
			for (var e : t.getAsJsonObject("roles").entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				JsonObject b = e.getValue().getAsJsonObject();
				Double s = dbl(b, "standoff");
				c.roles.put(e.getKey().toUpperCase(java.util.Locale.ROOT), new double[] {s == null ? 0 : s, b.has("strafe") && b.get("strafe").getAsBoolean() ? 1 : 0});
			}
		}
		if (t.has("signals") && t.get("signals").isJsonObject()) {
			JsonObject s = t.getAsJsonObject("signals");
			if (s.has("horn")) c.horn = s.get("horn").getAsBoolean();
			if (s.has("banner")) c.banner = s.get("banner").getAsBoolean();
			c.shoutChance = dbl(s, "shout_chance");
		}
		return c;
	}

	public static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_tactics"),
			new SimpleJsonResourceReloadListener<JsonElement>(ExtraCodecs.JSON, FileToIdConverter.json("kingdom_faction")) {
				@Override
				protected void apply(final Map<Identifier, JsonElement> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					Map<Identifier, Cfg> out = new HashMap<>();
					for (var e : prepared.entrySet()) {
						try {
							if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("tactics") && e.getValue().getAsJsonObject().get("tactics").isJsonObject()) {
								out.put(e.getKey(), parse(e.getValue().getAsJsonObject().getAsJsonObject("tactics")));
							}
						} catch (RuntimeException ex) {
							dev.elsewhere.kingdom.KingdomContent.LOG.warn("bad tactics block in {}: {}", e.getKey(), ex.toString());
						}
					}
					cfgs = out;
				}
			});
	}
}
