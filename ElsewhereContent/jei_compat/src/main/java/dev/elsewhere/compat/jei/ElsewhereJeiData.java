package dev.elsewhere.compat.jei;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything the Elsewhere JEI pages show, read straight from the Elsewhere jar (so it also works on a dedicated server, whose data
 * packs the client never sees): attunement files, info pages ({@code assets/elsewhere/jei/*.json}), structure-chest and creature loot
 * tables, the four kingdoms' shops (faction economy plus {@code kingdom_trade}) and the Calling tags.
 *
 * <p>No JEI classes here, so the server-side coverage self-test ({@code dev.JeiSelfTest}) reads exactly what the plugin shows. Built
 * lazily: the first call to {@link #get()} (JEI's recipe registration, which only runs when JEI is installed, or the self-test) parses
 * the files once and keeps the result for the session. Nothing ticks.
 */
public final class ElsewhereJeiData {
	public static final Logger LOG = LoggerFactory.getLogger("elsewhere-jei");
	/**
	 * Technical blocks/items that players never hold: hidden from JEI's item list (and so need no page). Besides the arena seal and
	 * return rift, these are the boss projectiles' render items (an entity's thrown-item sprite, registered only so the projectile can
	 * draw itself; no recipe, loot or trade makes one).
	 */
	public static final List<String> HIDDEN = List.of("elsewhere:arena_seal", "elsewhere:return_rift", "elsewhere:skyiron_sword",
		"elsewhere:gold_bomb", "elsewhere:void_breath", "elsewhere:soul_tear", "elsewhere:sun_orb", "elsewhere:soul_fireball",
		"elsewhere:frogspawn_glob", "elsewhere:seed_fireball", "elsewhere:frost_shard", "elsewhere:spit_seed", "elsewhere:smoke_ring",
		"elsewhere:ember_flick");

	/** One possible drop of a loot page: {@code chance} is per roll of its pool (weight share times any random_chance condition). */
	public record LootEntry(Item item, float chance, int min, int max) {}

	/** A structure chest ({@code kind = "chest"}) or a creature ({@code kind = "creature"}) loot table with at least one item. */
	public record LootPage(Identifier id, String kind, String source, String rolls, List<LootEntry> entries) {}

	/** One shop line. {@code auction} lines are lots of the Mirewick weekly auction (price = opening bid, {@code max} = top bid). */
	public record TradePage(Identifier id, String faction, Item currency, int price, List<Map.Entry<Item, Integer>> extra, Item result,
		int count, String tier, int weeklyLimit, int sell, boolean auction, int max) {}

	/** What the pages show. {@code info} = item id to lines (hand-written pages plus the generated Calling and spawn-egg lines). */
	public record Snapshot(Map<String, List<String>> info, List<AttunementPage> attunements, List<LootPage> loot, List<TradePage> trades) {}

	private static volatile Snapshot cached;

	private ElsewhereJeiData() {
	}

	public static Optional<Item> item(final String id) {
		Identifier key = Identifier.tryParse(id);
		return key == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(key);
	}

	/** The pages, parsed once on first use. */
	public static Snapshot get() {
		Snapshot s = cached;
		if (s == null) {
			synchronized (ElsewhereJeiData.class) {
				s = cached;
				if (s == null) {
					long t0 = System.nanoTime();
					Map<String, List<String>> info = info();
					callingLines(info);
					spawnEggLines(info);
					s = new Snapshot(info, attunements(), loot(), trades());
					cached = s;
					LOG.info("Elsewhere JEI data built in {} ms: {} info pages, {} attunements, {} loot pages, {} shop lines",
						(System.nanoTime() - t0) / 1_000_000, s.info().size(), s.attunements().size(), s.loot().size(), s.trades().size());
				}
			}
		}
		return s;
	}

	static List<Path> roots() {
		List<Path> roots = new ArrayList<>();
		for (String mod : List.of("elsewhere", "elsewhere_jei_compat")) {
			FabricLoader.getInstance().getModContainer(mod).map(ModContainer::getRootPaths).ifPresent(roots::addAll);
		}
		return roots;
	}

	/** Every {@code *.json} directly in {@code dir} under every root, first root first, sorted by name. */
	private static List<Path> files(final String dir) {
		List<Path> out = new ArrayList<>();
		for (Path root : roots()) {
			Path d = root.resolve(dir);
			if (!Files.isDirectory(d)) {
				continue;
			}
			try (Stream<Path> files = Files.list(d)) {
				files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().forEach(out::add);
			} catch (Exception e) {
				LOG.warn("Could not list {}", d, e);
			}
		}
		return out;
	}

	private static JsonObject read(final Path f) {
		try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
			JsonElement e = JsonParser.parseReader(r);
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Exception e) {
			LOG.warn("Could not read {}", f, e);
			return null;
		}
	}

	private static String name(final Path f) {
		String n = f.getFileName().toString();
		return n.substring(0, n.length() - ".json".length());
	}

	static List<AttunementPage> attunements() {
		Map<Identifier, AttunementPage> pages = new LinkedHashMap<>();
		Optional<Item> wayfinder = item("elsewhere:wayfinder");
		if (wayfinder.isEmpty()) {
			return List.of();
		}
		for (Path f : files("data/elsewhere/attunement")) {
			JsonObject o = read(f);
			if (o == null || !o.has("dimension") || !o.has("ingredients")) {
				continue;
			}
			Identifier dim = Identifier.parse(o.get("dimension").getAsString());
			List<Item> ings = new ArrayList<>();
			for (JsonElement e : o.getAsJsonArray("ingredients")) {
				item(e.getAsString()).ifPresent(ings::add);
			}
			Optional<Item> result = item("elsewhere:wayfinder_" + dim.getPath());
			String requires = o.has("requires") ? o.get("requires").getAsString() : "none";
			if (result.isPresent() && !ings.isEmpty()) {
				Identifier id = Identifier.fromNamespaceAndPath("elsewhere", "attunement/" + name(f));
				pages.putIfAbsent(id, new AttunementPage(id, dim, ings, wayfinder.get(), result.get(), requires));
			}
		}
		return new ArrayList<>(pages.values());
	}

	/**
	 * item id -> info lines, from every {@code assets/elsewhere/jei/*.json} (the merged {@code info.json} plus the per-module files
	 * this module ships, e.g. {@code info_rivals.json}); lines of one item from several files are joined without duplicates.
	 */
	static Map<String, List<String>> info() {
		Map<String, List<String>> out = new TreeMap<>();
		for (Path f : files("assets/elsewhere/jei")) {
			JsonObject o = read(f);
			if (o == null) {
				continue;
			}
			for (Map.Entry<String, JsonElement> e : o.entrySet()) {
				if (!e.getValue().isJsonArray() || !e.getKey().contains(":")) {
					continue;
				}
				List<String> lines = out.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
				for (JsonElement x : e.getValue().getAsJsonArray()) {
					String line = x.getAsString().strip();
					if (!line.isEmpty() && !lines.contains(line)) {
						lines.add(line);
					}
				}
			}
		}
		out.values().removeIf(List::isEmpty);
		return out;
	}

	/** Tag file values (plain ids only; nested {@code #tags} are followed one level, inside the same namespace folder). */
	private static Set<String> tagValues(final String tagPath) {
		Set<String> out = new LinkedHashSet<>();
		for (Path root : roots()) {
			Path f = root.resolve("data/elsewhere/tags/item/" + tagPath + ".json");
			if (!Files.isRegularFile(f)) {
				continue;
			}
			JsonObject o = read(f);
			if (o == null || !o.has("values")) {
				continue;
			}
			for (JsonElement e : o.getAsJsonArray("values")) {
				String v = e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString();
				if (v.startsWith("#elsewhere:")) {
					out.addAll(tagValues(v.substring("#elsewhere:".length())));
				} else if (!v.startsWith("#")) {
					out.add(v);
				}
			}
		}
		return out;
	}

	private static final String[][] CALLINGS = {
		{"vanguard", "Vanguard", "melee weapons"},
		{"marksman", "Marksman", "bows, crossbows and throwables"},
		{"caster", "Caster", "spells and Glimmer items"},
		{"summoner", "Summoner", "minions, whips, crooks, totems and standards"}};

	/** GEAR.md A1: one generated line per Calling armor piece and Calling weapon, from the same tags the Calling code reads. */
	static void callingLines(final Map<String, List<String>> info) {
		for (String[] c : CALLINGS) {
			Set<String> armor = new LinkedHashSet<>(tagValues("calling/" + c[0]));
			if (c[0].equals("caster")) {
				armor.addAll(tagValues("caster_armor"));
			} else if (c[0].equals("summoner")) {
				armor.addAll(tagValues("summoner_armor"));
			}
			String armorLine = "Calling: leans " + c[1] + ". Wear 2 or more " + c[1] + " pieces and " + c[1]
				+ " becomes your Primary Calling (full mastery): " + c[1] + " " + c[2] + " deal x1.15 damage (never against players)"
				+ switch (c[0]) {
					case "caster" -> ", spells cost 10% less Glimmer, +12 max Glimmer and 30% faster Glimmer regen.";
					case "summoner" -> ", up to 4 minion slots and 60 s minion battle timers.";
					default -> ".";
				};
			for (String id : armor) {
				addLine(info, id, armorLine);
			}
			String weaponLine = c[1] + " Calling item: its damage scales with your " + c[1]
				+ " mastery, from x0.96 (Open) through x1.00 (Unattuned) to x1.15 (Primary). Against players it always deals its base damage.";
			for (String id : tagValues("calling_weapon/" + c[0])) {
				addLine(info, id, weaponLine);
			}
		}
	}

	/** Spawn eggs only exist in creative mode: say so instead of leaving the page empty. */
	static void spawnEggLines(final Map<String, List<String>> info) {
		for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
			if (key.getNamespace().equals("elsewhere") && key.getPath().endsWith("_spawn_egg")) {
				addLine(info, key.toString(), "Creative mode only: places the creature named on the egg. Not obtainable in survival; "
					+ "find the creature itself in its home dimension or kingdom.");
			}
		}
	}

	private static void addLine(final Map<String, List<String>> info, final String id, final String line) {
		List<String> lines = info.computeIfAbsent(id, k -> new ArrayList<>());
		if (!lines.contains(line)) {
			lines.add(line);
		}
	}

	// ------------------------------------------------------------------------------------------------------------------ loot tables

	static List<LootPage> loot() {
		List<LootPage> out = new ArrayList<>();
		Set<Identifier> seen = new LinkedHashSet<>();
		for (String kind : List.of("chests", "entities")) {
			for (Path f : files("data/elsewhere/loot_table/" + kind)) {
				Identifier id = Identifier.fromNamespaceAndPath("elsewhere", kind + "/" + name(f));
				if (!seen.add(id)) {
					continue;
				}
				JsonObject o = read(f);
				if (o == null || !o.has("pools")) {
					continue;
				}
				Map<Item, LootEntry> entries = new LinkedHashMap<>();
				List<String> rolls = new ArrayList<>();
				for (JsonElement pe : o.getAsJsonArray("pools")) {
					JsonObject pool = pe.getAsJsonObject();
					rolls.add(range(pool.get("rolls")));
					List<JsonObject> flat = new ArrayList<>();
					if (pool.has("entries")) {
						flatten(pool.getAsJsonArray("entries"), flat);
					}
					int total = 0;
					for (JsonObject e : flat) {
						total += e.has("weight") ? Math.max(0, e.get("weight").getAsInt()) : 1;
					}
					for (JsonObject e : flat) {
						if (!e.has("name") || !"minecraft:item".equals(type(e))) {
							continue;
						}
						Optional<Item> it = item(e.get("name").getAsString());
						if (it.isEmpty()) {
							continue;
						}
						int w = e.has("weight") ? Math.max(0, e.get("weight").getAsInt()) : 1;
						float chance = total <= 0 ? 0F : (float) w / total;
						chance *= randomChance(e) * randomChance(pool);
						int[] count = count(e);
						LootEntry prev = entries.get(it.get());
						if (prev == null || prev.chance() < chance) {
							entries.put(it.get(), new LootEntry(it.get(), chance, count[0], count[1]));
						}
					}
				}
				if (!entries.isEmpty()) {
					String kindName = kind.equals("chests") ? "chest" : "creature";
					out.add(new LootPage(id, kindName, name(f), String.join(" + ", rolls), new ArrayList<>(entries.values())));
				}
			}
		}
		return out;
	}

	private static String type(final JsonObject e) {
		String t = e.has("type") ? e.get("type").getAsString() : "minecraft:item";
		return t.contains(":") ? t : "minecraft:" + t;
	}

	/** Alternatives / group / sequence entries are opened up; their children keep their own weights. */
	private static void flatten(final JsonArray entries, final List<JsonObject> out) {
		for (JsonElement el : entries) {
			if (!el.isJsonObject()) {
				continue;
			}
			JsonObject e = el.getAsJsonObject();
			String t = type(e);
			if ((t.equals("minecraft:alternatives") || t.equals("minecraft:group") || t.equals("minecraft:sequence")) && e.has("children")) {
				flatten(e.getAsJsonArray("children"), out);
			} else {
				out.add(e);
			}
		}
	}

	/** 26.3 uses {@code condition} (one object or a list); older files still say {@code conditions}. */
	private static float randomChance(final JsonObject o) {
		float c = 1F;
		for (String key : List.of("condition", "conditions")) {
			if (!o.has(key)) {
				continue;
			}
			JsonElement v = o.get(key);
			List<JsonElement> list = new ArrayList<>();
			if (v.isJsonArray()) {
				v.getAsJsonArray().forEach(list::add);
			} else {
				list.add(v);
			}
			for (JsonElement x : list) {
				if (x.isJsonObject()) {
					JsonObject cond = x.getAsJsonObject();
					String t = cond.has("type") ? cond.get("type").getAsString() : cond.has("condition") ? cond.get("condition").getAsString() : "";
					if (t.endsWith("random_chance") && cond.has("chance") && cond.get("chance").isJsonPrimitive()) {
						c *= cond.get("chance").getAsFloat();
					}
				}
			}
		}
		return c;
	}

	/** set_count from {@code modifier} (26.3) or {@code functions} (older files). */
	private static int[] count(final JsonObject e) {
		for (String key : List.of("modifier", "modifiers", "functions")) {
			if (!e.has(key)) {
				continue;
			}
			JsonElement v = e.get(key);
			List<JsonElement> list = new ArrayList<>();
			if (v.isJsonArray()) {
				v.getAsJsonArray().forEach(list::add);
			} else {
				list.add(v);
			}
			for (JsonElement x : list) {
				if (!x.isJsonObject()) {
					continue;
				}
				JsonObject f = x.getAsJsonObject();
				String t = f.has("type") ? f.get("type").getAsString() : f.has("function") ? f.get("function").getAsString() : "";
				if (t.endsWith("set_count") && f.has("count")) {
					return minMax(f.get("count"));
				}
			}
		}
		return new int[] {1, 1};
	}

	private static int[] minMax(final JsonElement v) {
		if (v == null) {
			return new int[] {1, 1};
		}
		if (v.isJsonPrimitive()) {
			int n = Math.round(v.getAsFloat());
			return new int[] {n, n};
		}
		JsonObject o = v.getAsJsonObject();
		if (o.has("min") || o.has("max")) {
			int lo = o.has("min") && o.get("min").isJsonPrimitive() ? Math.round(o.get("min").getAsFloat()) : 1;
			int hi = o.has("max") && o.get("max").isJsonPrimitive() ? Math.round(o.get("max").getAsFloat()) : lo;
			return new int[] {lo, Math.max(lo, hi)};
		}
		if (o.has("value") && o.get("value").isJsonPrimitive()) {
			int n = Math.round(o.get("value").getAsFloat());
			return new int[] {n, n};
		}
		return new int[] {1, 1};
	}

	private static String range(final JsonElement v) {
		int[] r = minMax(v);
		return r[0] == r[1] ? Integer.toString(r[0]) : r[0] + "-" + r[1];
	}

	// ------------------------------------------------------------------------------------------------------------------- shops

	/**
	 * The four kingdoms' shops: {@code kingdom_trade/<faction>.json} (else {@code kingdom_trade_default}) where a kingdom has one,
	 * else the faction file's {@code economy.shop} (Aldermark's Royal Steward). Prices are in the faction's {@code economy.currency}.
	 */
	static List<TradePage> trades() {
		List<TradePage> out = new ArrayList<>();
		Map<String, JsonObject> factions = new TreeMap<>();
		for (Path f : files("data/elsewhere/kingdom_faction")) {
			JsonObject o = read(f);
			if (o != null) {
				factions.putIfAbsent(name(f), o);
			}
		}
		Map<String, JsonObject> stock = new TreeMap<>();
		for (String dir : List.of("data/elsewhere/kingdom_trade", "data/elsewhere/kingdom_trade_default")) {
			for (Path f : files(dir)) {
				JsonObject o = read(f);
				if (o != null) {
					stock.putIfAbsent(name(f), o);
				}
			}
		}
		for (Map.Entry<String, JsonObject> fe : factions.entrySet()) {
			String faction = fe.getKey();
			JsonObject eco = fe.getValue().has("economy") && fe.getValue().get("economy").isJsonObject()
				? fe.getValue().getAsJsonObject("economy") : null;
			if (eco == null || !eco.has("currency")) {
				continue;
			}
			Optional<Item> currency = item(eco.get("currency").getAsString());
			if (currency.isEmpty()) {
				continue;
			}
			JsonObject st = stock.get(faction);
			JsonArray lines = st != null && st.has("lines") ? st.getAsJsonArray("lines") : eco.has("shop") ? eco.getAsJsonArray("shop") : new JsonArray();
			int n = 0;
			for (JsonElement le : lines) {
				TradePage p = trade(faction, currency.get(), le.getAsJsonObject(), n++, false);
				if (p != null) {
					out.add(p);
				}
			}
			if (st != null && st.has("auction")) {
				for (JsonElement le : st.getAsJsonArray("auction")) {
					TradePage p = trade(faction, currency.get(), le.getAsJsonObject(), n++, true);
					if (p != null) {
						out.add(p);
					}
				}
			}
		}
		return out;
	}

	private static TradePage trade(final String faction, final Item currency, final JsonObject o, final int n, final boolean auction) {
		if (!o.has("item")) {
			return null;
		}
		Optional<Item> result = item(o.get("item").getAsString());
		if (result.isEmpty()) {
			return null;
		}
		int price = auction ? intOf(o, "start", 0) : intOf(o, "price", 0);
		List<Map.Entry<Item, Integer>> extra = new ArrayList<>();
		if (o.has("extra")) {
			for (JsonElement x : o.getAsJsonArray("extra")) {
				JsonObject xo = x.getAsJsonObject();
				item(xo.get("item").getAsString()).ifPresent(i -> extra.add(Map.entry(i, Math.max(intOf(xo, "count", 0), intOf(xo, "min", 1)))));
			}
		}
		Identifier id = Identifier.fromNamespaceAndPath("elsewhere",
			"shop/" + faction + "/" + (auction ? "auction_" : "") + n + "_" + BuiltInRegistries.ITEM.getKey(result.get()).getPath());
		return new TradePage(id, faction.toLowerCase(Locale.ROOT), currency, price, extra, result.get(), Math.max(1, intOf(o, "count", 1)),
			o.has("tier") ? o.get("tier").getAsString() : "", intOf(o, "weekly_limit", 0), intOf(o, "sell", 0), auction, intOf(o, "max", 0));
	}

	private static int intOf(final JsonObject o, final String key, final int def) {
		return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsInt() : def;
	}
}
