package dev.elsewhere.gear3;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One catalogue entry ({@code items[]} in catalogue.json). Stats are a plain name -> number map (see the schema in tools/README-gear3.txt). */
public final class Gear3Def {
	/** One attribute modifier: attr id, amount, op (add | mul_base | mul_total), slot (armor | mainhand | offhand | any). */
	public record Attr(String attr, double amount, String op, String slot) {}

	public record Drop(String table, float chance, int min, int max) {}

	public final String id, name, kind, rarity, mechanic, set, slot;
	public final Map<String, Double> stats = new LinkedHashMap<>();
	public final Map<String, String> sstats = new LinkedHashMap<>();
	public final List<String> lore = new ArrayList<>();
	public final List<Drop> drops = new ArrayList<>();
	public final List<String> tags = new ArrayList<>();
	public final List<Attr> attrs = new ArrayList<>();
	/** Recipe ids (file names without .json): "recipe" is one object or a list; a recipe's "file" overrides the name (default: the item id, then id_2, id_3 ...). */
	public final List<String> recipeFiles = new ArrayList<>();

	Gear3Def(JsonObject o) {
		id = o.get("id").getAsString();
		name = o.get("name").getAsString();
		kind = str(o, "kind", "plain");
		rarity = str(o, "rarity", "common");
		mechanic = str(o, "mechanic", "");
		set = str(o, "set", "");
		slot = str(o, "slot", "");
		if (o.has("recipe")) {
			JsonElement r = o.get("recipe");
			List<JsonObject> rs = new ArrayList<>();
			if (r.isJsonArray()) for (JsonElement e : r.getAsJsonArray()) rs.add(e.getAsJsonObject());
			else rs.add(r.getAsJsonObject());
			for (int i = 0; i < rs.size(); i++) recipeFiles.add(rs.get(i).has("file") ? rs.get(i).get("file").getAsString() : (i == 0 ? id : id + "_" + (i + 1)));
		}
		if (o.has("stats")) for (var e : o.getAsJsonObject("stats").entrySet()) {
			JsonElement v = e.getValue();
			if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) stats.put(e.getKey(), v.getAsDouble());
			else if (v.isJsonPrimitive()) sstats.put(e.getKey(), v.getAsString());
		}
		if (o.has("lore")) for (JsonElement l : o.getAsJsonArray("lore")) lore.add(l.getAsString());
		if (o.has("attrs")) for (JsonElement a : o.getAsJsonArray("attrs")) {
			JsonObject x = a.getAsJsonObject();
			attrs.add(new Attr(x.get("attr").getAsString(), x.get("amount").getAsDouble(), str(x, "op", "add"), str(x, "slot", "armor")));
		}
		if (o.has("calling")) for (JsonElement c : o.getAsJsonArray("calling")) tags.add("elsewhere:" + (kind.equals("armor") ? "calling/" : "calling_weapon/") + c.getAsString());
		if (o.has("tags")) for (JsonElement t : o.getAsJsonArray("tags")) tags.add(t.getAsString());
		if (o.has("loot")) for (JsonElement l : o.getAsJsonArray("loot")) {
			JsonObject d = l.getAsJsonObject();
			drops.add(new Drop(d.get("table").getAsString(), d.get("chance").getAsFloat(), d.has("min") ? d.get("min").getAsInt() : 1, d.has("max") ? d.get("max").getAsInt() : 1));
		}
	}

	private static String str(JsonObject o, String k, String def) {
		return o.has(k) ? o.get(k).getAsString() : def;
	}

	public double stat(String key, double def) {
		return stats.getOrDefault(key, def);
	}

	public double stat(String key) {
		Double d = stats.get(key);
		if (d == null) throw new IllegalStateException("catalogue item " + id + " has no stat '" + key + "'");
		return d;
	}

	public String sstat(String key, String def) {
		return sstats.getOrDefault(key, def);
	}

	/** Lore keys are {@code item.elsewhere.<id>.loreN}, N from 1. */
	public int loreLines() {
		return lore.size();
	}

	static JsonArray arr(JsonObject o, String k) {
		return o.has(k) ? o.getAsJsonArray(k) : new JsonArray();
	}
}
