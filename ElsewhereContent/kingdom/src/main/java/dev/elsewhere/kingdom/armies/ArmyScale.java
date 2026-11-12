package dev.elsewhere.kingdom.armies;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jspecify.annotations.Nullable;

/**
 * Per-kingdom army scale and day routine (ARMIES.md section 9.2), read from the {@code army.scale} and {@code army.routine}
 * blocks of every {@code data/<ns>/kingdom_faction/<id>.json}. Data only: it never spawns anything itself. Other lanes read it
 * through {@link #of}, {@link #garrisonSize}, {@link #activityAt} and {@link #isWatchChange}. Reloaded with {@code /reload}.
 */
public record ArmyScale(Identifier faction, int patrolDay, int patrolNight, int squadSize, String unitName, int companySize,
	int[] village, int[] town, int[] capital, int[] fieldArmy, int battleSideMax, int liveFront, Wave wave, int meleeTokens,
	int rangedTokens, Formation formation, int[] hiredCompany, int residentsCapital, int residentsTown, Map<String, Double> composition,
	Routine routine) {

	public record Wave(String style, int size, int intervalSeconds, int intervalJitterSeconds, double triggerFront) {}

	public record Formation(int width, int depth, double spacing, double jitter) {}

	public record Block(int from, int to, String activity, double share, String where) {}

	public record Event(String id, int everyDays, int at, int hours, String activity, String where) {}

	public record Routine(List<Integer> watchChanges, int watchJitterMinutes, List<Block> blocks, List<Event> events) {}

	public static final Set<String> ACTIVITIES = Set.of("sleep", "watch", "drill", "patrol", "market", "work", "court", "temple", "feast", "spar",
		"tourney", "caravan", "lamplight", "muster", "procession", "siesta");
	public static final Set<String> WAVE_STYLES = Set.of("sergeant", "surge", "relief", "ragged");
	public static final List<String> ROLES = List.of("line", "heavy", "ranged", "mounted", "special", "officers");

	private static Map<Identifier, ArmyScale> loaded = Map.of();
	/** Parse problems from the last reload (shown by /kingdom armies and checked by the self-test). */
	private static List<String> parseErrors = List.of();

	static void init() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(Identifier.fromNamespaceAndPath("elsewhere", "kingdom_army_scale"),
			new SimpleJsonResourceReloadListener<JsonElement>(ExtraCodecs.JSON, FileToIdConverter.json("kingdom_faction")) {
				@Override
				protected void apply(final Map<Identifier, JsonElement> prepared, final ResourceManager manager, final ProfilerFiller profiler) {
					Map<Identifier, ArmyScale> out = new LinkedHashMap<>();
					List<String> errs = new ArrayList<>();
					for (var e : prepared.entrySet()) {
						try {
							if (!e.getValue().isJsonObject()) continue;
							JsonObject f = e.getValue().getAsJsonObject();
							if (!f.has("army") || !f.get("army").isJsonObject()) continue;
							JsonObject army = f.getAsJsonObject("army");
							if (!army.has("scale") || !army.get("scale").isJsonObject()) continue;
							out.put(e.getKey(), parse(e.getKey(), army));
						} catch (RuntimeException ex) {
							errs.add(e.getKey() + ": " + ex);
							dev.elsewhere.kingdom.KingdomContent.LOG.warn("bad army.scale/routine block in {}: {}", e.getKey(), ex.toString());
						}
					}
					loaded = Collections.unmodifiableMap(out);
					parseErrors = List.copyOf(errs);
					dev.elsewhere.kingdom.KingdomContent.LOG.info("Army scale loaded for {} kingdoms: {}", out.size(), out.keySet());
				}
			});
	}

	public static @Nullable ArmyScale of(Identifier faction) {
		return loaded.get(faction);
	}

	public static Map<Identifier, ArmyScale> all() {
		return loaded;
	}

	public static List<String> parseErrors() {
		return parseErrors;
	}

	/** A garrison size for {@code kind} (village, town or capital), uniform in the file's [min, max]; {@code fallback} when unknown. */
	public static int garrisonSize(Identifier faction, String kind, RandomSource random, int fallback) {
		ArmyScale s = of(faction);
		if (s == null) return fallback;
		int[] r = switch (kind) {
			case "village" -> s.village;
			case "town" -> s.town;
			case "capital" -> s.capital;
			default -> null;
		};
		if (r == null) return fallback;
		return r[0] + (r[1] > r[0] ? random.nextInt(r[1] - r[0] + 1) : 0);
	}

	/** The routine block active at {@code hour} (0-23), or null. */
	public static @Nullable Block activityAt(Identifier faction, int hour) {
		ArmyScale s = of(faction);
		if (s == null) return null;
		for (Block b : s.routine.blocks) if (hour >= b.from && hour < b.to) return b;
		return null;
	}

	public static boolean isWatchChange(Identifier faction, int hour) {
		ArmyScale s = of(faction);
		return s != null && s.routine.watchChanges.contains(hour);
	}

	/** Problems with this kingdom's data (empty when valid). Used by the self-test and /kingdom armies. */
	public List<String> problems() {
		List<String> p = new ArrayList<>();
		if (squadSize < 4 || squadSize > 20) p.add("squad_size " + squadSize + " outside 4-20");
		if (patrolDay < 1 || patrolNight < 1) p.add("patrol sizes must be at least 1");
		for (int[] r : new int[][] {village, town, capital, fieldArmy})
			if (r[0] < 0 || r[0] > r[1]) p.add("range [" + r[0] + ", " + r[1] + "] has min > max");
		if (!(village[1] <= town[1] && town[1] <= capital[1])) p.add("garrisons must grow village <= town <= capital");
		if (battleSideMax < 1 || battleSideMax > 150) p.add("battle_side_max outside 1-150");
		if (!WAVE_STYLES.contains(wave.style)) p.add("unknown wave style " + wave.style);
		double sum = 0;
		for (String r : ROLES) sum += composition.getOrDefault(r, 0.0);
		if (Math.abs(sum - 1.0) > 0.01) p.add("composition sums to " + sum);
		for (String k : composition.keySet()) if (!ROLES.contains(k)) p.add("unknown composition role " + k);
		int lastTo = 0;
		for (Block b : routine.blocks) {
			if (b.from < 0 || b.to > 24 || b.from >= b.to) p.add("block " + b.from + "-" + b.to + " outside 0-24");
			if (b.from < lastTo) p.add("block " + b.from + "-" + b.to + " overlaps the previous one");
			if (!ACTIVITIES.contains(b.activity)) p.add("unknown activity " + b.activity);
			if (b.share < 0 || b.share > 1) p.add("share " + b.share + " outside 0-1");
			lastTo = Math.max(lastTo, b.to);
		}
		for (int h : routine.watchChanges) if (h < 0 || h > 23) p.add("watch change hour " + h + " outside 0-23");
		for (Event e : routine.events) {
			if (e.everyDays < 1 || e.at < 0 || e.at > 23 || e.hours < 1) p.add("event " + e.id + " has bad timing");
			if (!ACTIVITIES.contains(e.activity)) p.add("event " + e.id + " has unknown activity " + e.activity);
		}
		return p;
	}

	// ---- parsing ------------------------------------------------------------------------------------------------------------

	static ArmyScale parse(Identifier id, JsonObject army) {
		JsonObject s = army.getAsJsonObject("scale");
		JsonObject patrol = obj(s, "patrol");
		int legacyDay = i(army, "patrol_day", 6), legacyNight = i(army, "patrol_night", 6);
		JsonObject g = obj(s, "garrison");
		JsonObject w = obj(s, "wave");
		JsonObject t = obj(s, "attack_tokens");
		JsonObject f = obj(s, "formation");
		JsonObject res = obj(s, "residents");
		Map<String, Double> comp = new LinkedHashMap<>();
		JsonObject c = obj(s, "composition");
		for (var e : c.entrySet()) comp.put(e.getKey(), e.getValue().getAsDouble());
		JsonObject r = army.has("routine") && army.get("routine").isJsonObject() ? army.getAsJsonObject("routine") : new JsonObject();
		List<Integer> watch = new ArrayList<>();
		if (r.has("watch_changes")) for (JsonElement e : r.getAsJsonArray("watch_changes")) watch.add(e.getAsInt());
		List<Block> blocks = new ArrayList<>();
		if (r.has("blocks")) for (JsonElement e : r.getAsJsonArray("blocks")) {
			JsonObject b = e.getAsJsonObject();
			blocks.add(new Block(i(b, "from", 0), i(b, "to", 0), str(b, "activity", "work"), d(b, "share", 0.5), str(b, "where", "centre")));
		}
		List<Event> events = new ArrayList<>();
		if (r.has("events")) for (JsonElement e : r.getAsJsonArray("events")) {
			JsonObject b = e.getAsJsonObject();
			events.add(new Event(str(b, "id", "event"), i(b, "every_days", 7), i(b, "at", 12), i(b, "hours", 1), str(b, "activity", "muster"), str(b, "where", "centre")));
		}
		return new ArmyScale(id, i(patrol, "day", legacyDay), i(patrol, "night", legacyNight), i(s, "squad_size", 8), str(s, "unit_name", "squad"),
			i(s, "company_size", 16), range(g, "village", 3, 5), range(g, "town", 8, 12), range(g, "capital", 20, 20), range(s, "field_army", 30, 30),
			i(s, "battle_side_max", 15), i(s, "live_front", 15),
			new Wave(str(w, "style", "sergeant"), i(w, "size", 8), i(w, "interval_s", 40), i(w, "interval_jitter_s", 0), d(w, "trigger_front", 0.7)),
			i(t, "melee", 4), i(t, "ranged", 3), new Formation(i(f, "width", 5), i(f, "depth", 2), d(f, "spacing", 1.25), d(f, "jitter", 0)),
			range(obj(s, "hired"), "company", 0, 0), i(res, "capital", 0), i(res, "town", 0), Collections.unmodifiableMap(comp),
			new Routine(List.copyOf(watch), i(r, "watch_jitter_min", 0), List.copyOf(blocks), List.copyOf(events)));
	}

	private static JsonObject obj(JsonObject o, String k) {
		return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
	}

	private static int i(JsonObject o, String k, int def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
	}

	private static double d(JsonObject o, String k, double def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
	}

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}

	private static int[] range(JsonObject o, String k, int a, int b) {
		if (!o.has(k) || !o.get(k).isJsonArray()) return new int[] {a, b};
		JsonArray arr = o.getAsJsonArray(k);
		return new int[] {arr.get(0).getAsInt(), arr.get(arr.size() > 1 ? 1 : 0).getAsInt()};
	}

	@Override
	public String toString() {
		return faction + "{" + unitName + " " + squadSize + ", patrol " + patrolDay + "/" + patrolNight + ", village " + village[0] + "-" + village[1]
			+ ", town " + town[0] + "-" + town[1] + ", capital " + capital[0] + "-" + capital[1] + ", wave " + wave.style + " " + wave.size + "}";
	}
}
