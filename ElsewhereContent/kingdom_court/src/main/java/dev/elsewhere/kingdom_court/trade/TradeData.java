package dev.elsewhere.kingdom_court.trade;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.kingdom.FactionDef;
import dev.elsewhere.kingdom.Factions;
import dev.elsewhere.kingdom_court.CourtContent;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The per-faction trade stock (RIVALS-DECISIONS 12). The court owns the mechanics, each kingdom lane owns its stock. Lookup order for a faction:
 * <ol>
 * <li>a stock registered in code ({@link #put});</li>
 * <li>the {@code trade} object of the faction file {@code data/<ns>/kingdom_faction/<path>.json};</li>
 * <li>a data file {@code data/<ns>/kingdom_trade/<path>.json} (same schema; for a lane that keeps its stock apart from the faction file);</li>
 * <li>the court's shipped default {@code data/elsewhere/kingdom_trade_default/<path>.json};</li>
 * <li>derived from the faction file's {@code economy.shop} (every line one tier, no extras), mode by faction id.</li>
 * </ol>
 * Schema (all optional except {@code lines[].item} and {@code price}; see API.md section 13):
 * <pre>
 * { "mode": "thing" | "posted" | "floating",
 *   "goods": { "minecraft:iron_ingot": 30, ... },                                  // Hrafnholt barter values in tenths of one coin
 *   "lines": [ { "item": "ns:id", "count": 1, "price": 30, "tier": "...", "rings": 4, "weekly_limit": 2,
 *               "barter": [ {"item": "ns:id", "count": 10} ], "extra": [ {"item": "ns:id", "count": 20} ],
 *               "commission": false, "sell": 12 } ],
 *   "caravan": [ [ lines... ], ... ],                                              // Azmar: one list per rotation step
 *   "auction": [ { "item": "ns:id", "count": 1, "start": 40, "max": 120 } ] }      // Mirewick lot pool
 * </pre>
 * Tiers: Hrafnholt {@code smith | thing | top} (top costs {@code rings} Arm-Rings only); Azmar {@code common | workshop | royal} (Neutral, Friendly, Allied);
 * Mirewick {@code market}. A line whose item is not registered is skipped.
 */
public final class TradeData {
	private TradeData() {}

	public enum Mode {
		NONE, THING, POSTED, FLOATING;

		public static Mode parse(String s, Mode def) {
			try {
				return valueOf(s.toUpperCase(Locale.ROOT));
			} catch (RuntimeException e) {
				return def;
			}
		}
	}

	public record Good(Identifier item, int count) {
		public @Nullable ItemStack stack() {
			return BuiltInRegistries.ITEM.containsKey(item) ? new ItemStack(BuiltInRegistries.ITEM.getValue(item), Math.max(1, count)) : null;
		}
	}

	/** One line of stock. {@code index} is its position in {@link Stock#lines()} (what /diplomacy buy takes). */
	public record Line(int index, Identifier item, int count, int price, String tier, int rings, List<Good> barter, int weeklyLimit, List<Good> extra, boolean commission, int sell) {
		public @Nullable Item itemOrNull() {
			return BuiltInRegistries.ITEM.containsKey(item) ? BuiltInRegistries.ITEM.getValue(item) : null;
		}

		public @Nullable ItemStack stack() {
			Item i = itemOrNull();
			return i == null ? null : new ItemStack(i, Math.max(1, count));
		}

		public String key() {
			return item.toString();
		}

		public boolean top() {
			return "top".equals(tier);
		}
	}

	public record Lot(Identifier item, int count, int start, int max) {
		public @Nullable ItemStack stack() {
			return BuiltInRegistries.ITEM.containsKey(item) ? new ItemStack(BuiltInRegistries.ITEM.getValue(item), Math.max(1, count)) : null;
		}
	}

	public record Stock(Mode mode, List<Line> lines, List<List<Line>> caravan, List<Lot> auction, Map<Identifier, Integer> goods) {
		public static Stock empty(Mode m) {
			return new Stock(m, List.of(), List.of(), List.of(), defaultGoods());
		}
	}

	/** Hrafnholt barter values in tenths of one coin: 1 iron ingot = 3, leather 2, raw cod 1, raw salmon 1, wheat 0.5. */
	public static Map<Identifier, Integer> defaultGoods() {
		Map<Identifier, Integer> m = new LinkedHashMap<>();
		m.put(Identifier.withDefaultNamespace("iron_ingot"), 30);
		m.put(Identifier.withDefaultNamespace("leather"), 20);
		m.put(Identifier.withDefaultNamespace("cod"), 10);
		m.put(Identifier.withDefaultNamespace("salmon"), 10);
		m.put(Identifier.withDefaultNamespace("wheat"), 5);
		return m;
	}

	private static final Map<Identifier, Stock> CODE = new ConcurrentHashMap<>();
	private static final Map<Identifier, Stock> LOADED = new ConcurrentHashMap<>();

	/** Registers a stock in code; it wins over every data file. */
	public static void put(Identifier faction, Stock s) {
		CODE.put(faction, s);
	}

	public static void remove(Identifier faction) {
		CODE.remove(faction);
		LOADED.remove(faction);
	}

	/** Forgets the data-loaded stocks (server start and /reload). */
	public static void reload() {
		LOADED.clear();
	}

	public static Mode defaultMode(Identifier faction) {
		return switch (faction.getPath()) {
			case "hrafnholt" -> Mode.THING;
			case "azmar" -> Mode.POSTED;
			case "mirewick" -> Mode.FLOATING;
			default -> Mode.NONE;
		};
	}

	public static Stock stock(MinecraftServer server, Identifier faction) {
		Stock c = CODE.get(faction);
		if (c != null) return c;
		Stock s = LOADED.get(faction);
		if (s == null) {
			s = load(server, faction);
			LOADED.put(faction, s);
		}
		return s;
	}

	static Stock load(MinecraftServer server, Identifier faction) {
		String ns = faction.getNamespace(), path = faction.getPath();
		var rm = server.getResourceManager();
		try {
			var fres = rm.getResource(Identifier.fromNamespaceAndPath(ns, "kingdom_faction/" + path + ".json"));
			if (fres.isPresent()) {
				try (Reader r = fres.get().openAsReader()) {
					JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
					if (o.has("trade") && o.get("trade").isJsonObject()) return parse(faction, o.getAsJsonObject("trade"));
				}
			}
			for (String dir : new String[] {"kingdom_trade", "kingdom_trade_default"}) {
				var res = rm.getResource(Identifier.fromNamespaceAndPath(dir.equals("kingdom_trade") ? ns : "elsewhere", dir + "/" + path + ".json"));
				if (res.isPresent()) {
					try (Reader r = res.get().openAsReader()) {
						return parse(faction, JsonParser.parseReader(r).getAsJsonObject());
					}
				}
			}
		} catch (Exception e) {
			CourtContent.LOG.warn("trade stock of {} is unreadable: {}", faction, e.toString());
		}
		return derive(faction);
	}

	/** The fallback: the faction file's {@code economy.shop}, every line one tier (Hrafnholt: arrows at the Smith, the rest at the Thing; Azmar: common; Mirewick: market). */
	static Stock derive(Identifier faction) {
		Mode mode = defaultMode(faction);
		FactionDef d = Factions.def(faction);
		List<Line> lines = new ArrayList<>();
		if (d != null) {
			int i = 0;
			for (FactionDef.ShopEntry e : d.economy().shop()) {
				List<Good> extra = new ArrayList<>();
				for (FactionDef.Drop x : e.extra()) extra.add(new Good(x.item(), Math.max(1, x.min())));
				String tier = switch (mode) {
					case THING -> e.price() >= 200 ? "top" : e.item().getPath().contains("bolt") ? "smith" : "thing";
					case POSTED -> e.price() >= 200 ? "workshop" : "common";
					default -> "market";
				};
				lines.add(new Line(i++, e.item(), Math.max(1, e.count()), e.price(), tier, "top".equals(tier) ? Math.max(1, Math.round(e.price() / 50.0F)) : 0, List.of(), e.weeklyLimit(), extra, false, Math.max(1, e.price() / 2)));
			}
		}
		return new Stock(mode, lines, List.of(), List.of(), defaultGoods());
	}

	static int i(JsonObject o, String k, int def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
	}

	static String s(JsonObject o, String k, String def) {
		return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
	}

	static List<Good> goods(JsonObject o, String k) {
		List<Good> out = new ArrayList<>();
		if (o.has(k) && o.get(k).isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray(k)) {
				JsonObject g = e.getAsJsonObject();
				out.add(new Good(Identifier.parse(s(g, "item", "minecraft:air")), Math.max(1, i(g, "count", i(g, "min", 1)))));
			}
		}
		return out;
	}

	static List<Line> lines(JsonArray a, String defaultTier) {
		List<Line> out = new ArrayList<>();
		int idx = 0;
		for (JsonElement e : a) {
			JsonObject o = e.getAsJsonObject();
			Identifier item = Identifier.parse(s(o, "item", "minecraft:air"));
			int price = Math.max(1, i(o, "price", 1));
			out.add(new Line(idx++, item, Math.max(1, i(o, "count", 1)), price, s(o, "tier", defaultTier), Math.max(0, i(o, "rings", 0)), goods(o, "barter"), Math.max(0, i(o, "weekly_limit", 0)),
				goods(o, "extra"), o.has("commission") && o.get("commission").getAsBoolean(), Math.max(1, i(o, "sell", Math.max(1, price / 2)))));
		}
		return out;
	}

	public static Stock parse(Identifier faction, JsonObject o) {
		Mode mode = Mode.parse(s(o, "mode", ""), defaultMode(faction));
		String tier = switch (mode) {
			case THING -> "thing";
			case POSTED -> "common";
			default -> "market";
		};
		List<Line> lines = o.has("lines") ? lines(o.getAsJsonArray("lines"), tier) : List.of();
		List<List<Line>> caravan = new ArrayList<>();
		if (o.has("caravan")) for (JsonElement set : o.getAsJsonArray("caravan")) caravan.add(lines(set.getAsJsonArray(), "common"));
		List<Lot> lots = new ArrayList<>();
		if (o.has("auction")) {
			for (JsonElement e : o.getAsJsonArray("auction")) {
				JsonObject l = e.getAsJsonObject();
				int start = Math.max(1, i(l, "start", 20));
				lots.add(new Lot(Identifier.parse(s(l, "item", "minecraft:air")), Math.max(1, i(l, "count", 1)), start, Math.max(start + 1, i(l, "max", start * 3))));
			}
		}
		Map<Identifier, Integer> goods = defaultGoods();
		if (o.has("goods") && o.get("goods").isJsonObject()) {
			goods = new LinkedHashMap<>();
			for (var e : o.getAsJsonObject("goods").entrySet()) goods.put(Identifier.parse(e.getKey()), Math.max(1, e.getValue().getAsInt()));
		}
		return new Stock(mode, lines, caravan, lots, goods);
	}
}
