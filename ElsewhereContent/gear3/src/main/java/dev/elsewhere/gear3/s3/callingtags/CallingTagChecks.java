package dev.elsewhere.gear3.s3.callingtags;

import static dev.elsewhere.gear3.dev.SelfTest.check;
import static dev.elsewhere.gear3.dev.SelfTest.info;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.elsewhere.gear3.Gear3Catalogue;
import dev.elsewhere.gear3.Gear3Content;
import dev.elsewhere.gear3.Gear3Def;
import dev.elsewhere.gear3.Gear3Items;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Self-test block (s-callingtags): every GEAR.md A5 item is in exactly the right calling tag(s) ({@link CallingSpec}), Calling reads
 * the right path from them, no vanilla item is in a calling tag, and every gear3 item has a lang name, a description and a JEI
 * how-to-get line. Runs once from {@code SelfTest.runChecks}; reads loaded tags and resources only (no world, no ticking).
 */
public final class CallingTagChecks {
	private CallingTagChecks() {}

	static final String PREFIX = "calling tags: ";

	public static void run(MinecraftServer s) {
		try {
			runChecks();
		} catch (Throwable t) {
			check(PREFIX + "block ran to the end", false, t);
		}
	}

	static TagKey<Item> tag(String path) {
		return Gear3Items.itemTag("elsewhere:" + path);
	}

	static Item registered(String id) {
		Identifier rl = Gear3Content.id(id);
		return BuiltInRegistries.ITEM.containsKey(rl) ? BuiltInRegistries.ITEM.getValue(rl) : null;
	}

	/** The calling tags (of {@link CallingSpec#TAGS}) this stack is in, in TAGS order. */
	static List<String> actual(ItemStack st) {
		List<String> out = new ArrayList<>();
		for (String t : CallingSpec.TAGS) if (st.is(tag(t))) out.add(t);
		return out;
	}

	static void runChecks() {
		Map<String, List<String>> spec = CallingSpec.SPEC;
		check(PREFIX + "spec lists the 82 GEAR.md A5 items", spec.size() == 82, spec.size());

		Set<String> unspecced = new TreeSet<>();
		for (Gear3Def d : Gear3Catalogue.DEFS) if (!spec.containsKey(d.id)) unspecced.add(d.id);
		check(PREFIX + "every catalogue item has a calling spec entry (tools/calling_tags.py + CallingSpec)", unspecced.isEmpty(), unspecced.isEmpty() ? null : unspecced);

		List<String> missing = new ArrayList<>();
		for (String id : spec.keySet()) if (registered(id) == null) missing.add(id);
		check(PREFIX + "all 82 GEAR.md A5 items are registered", missing.isEmpty(), (82 - missing.size()) + "/82" + (missing.isEmpty() ? "" : ", missing " + missing));

		// 1. exact tag membership, per item
		for (Map.Entry<String, List<String>> e : spec.entrySet()) {
			Item item = registered(e.getKey());
			if (item == null) continue;
			List<String> want = e.getValue(), got = actual(new ItemStack(item));
			boolean ok = new HashSet<>(got).equals(new HashSet<>(want)) && got.size() == want.size();
			check(PREFIX + e.getKey() + (want.isEmpty() ? " is in no calling tag" : " is in exactly " + want), ok, ok ? null : "is in " + got);
		}

		// 2. Calling reads the right path (arsenal 3.0 API; the gear3 lane's devlib may still be the 2.1 arsenal, then the merged run checks it)
		callingReads(spec);

		// 3. GEAR.md A1: vanilla items are never in a calling tag
		List<String> vanilla = new ArrayList<>();
		for (Item item : BuiltInRegistries.ITEM) {
			Identifier key = BuiltInRegistries.ITEM.getKey(item);
			if (key == null || !"minecraft".equals(key.getNamespace())) continue;
			List<String> got = actual(new ItemStack(item));
			if (!got.isEmpty()) vanilla.add(key + " in " + got);
		}
		check(PREFIX + "no vanilla item is in a calling tag", vanilla.isEmpty(), vanilla.isEmpty() ? null : vanilla);

		// 4. lang name, description and JEI how-to-get line for every registered GEAR.md item (catalogue or not)
		JsonObject lang = merged("assets/elsewhere/lang/en_us.json");
		JsonObject jei = merged("elsewhere_gear3/jei_info.json");
		JsonObject jei2 = merged("assets/elsewhere/jei/info.json"); // the merged jar's copy (modules.json snapshot_extra)
		for (String id : spec.keySet()) {
			Item item = registered(id);
			if (item == null) continue;
			List<String> why = new ArrayList<>();
			// arsenal's client tooltip puts elsewhere.tooltip.<id> under the name of every CasterItem: without the key the raw key shows
			if (item instanceof dev.elsewhere.arsenal.spell.CasterItem && str(lang, "elsewhere.tooltip." + id) == null) why.add("CasterItem without elsewhere.tooltip." + id);
			String name = str(lang, "item.elsewhere." + id);
			if (name == null || name.isBlank() || name.equals("item.elsewhere." + id)) why.add("no lang name");
			String desc = str(lang, "item.elsewhere." + id + ".lore1");
			if (desc == null) desc = str(lang, "elsewhere.tooltip." + id);
			if (desc == null || desc.isBlank()) why.add("no description (item.elsewhere." + id + ".lore1)");
			String j = jeiLine(jei, id);
			if (j == null) j = jeiLine(jei2, id);
			if (j == null) why.add("no JEI line in elsewhere_gear3/jei_info.json");
			else if (j.toLowerCase(Locale.ROOT).contains("see the elsewhere wiki")) why.add("JEI line has no source: '" + j + "'");
			check(PREFIX + id + ": lang name, description and a JEI how-to-get line", why.isEmpty(), why.isEmpty() ? null : why);
		}
	}

	/** Calling.itemPath (held items) and Calling.Path.leans (armor) must agree with the spec for every registered item. */
	static void callingReads(Map<String, List<String>> spec) {
		Method itemPath, leans, id;
		Object[] paths;
		try {
			Class<?> calling = Class.forName("dev.elsewhere.arsenal.spell.Calling");
			Class<?> path = Class.forName("dev.elsewhere.arsenal.spell.Calling$Path");
			itemPath = calling.getMethod("itemPath", ItemStack.class);
			leans = path.getMethod("leans", ItemStack.class);
			id = path.getMethod("id");
			paths = path.getEnumConstants();
		} catch (ReflectiveOperationException | LinkageError e) {
			info(PREFIX + "arsenal's 3.0 Calling API is not in this environment (" + e.getClass().getSimpleName() + "); the itemPath/leans check runs in the merged jar");
			return;
		}
		List<String> bad = new ArrayList<>();
		int n = 0;
		try {
			for (Map.Entry<String, List<String>> e : spec.entrySet()) {
				Item item = registered(e.getKey());
				if (item == null) continue;
				ItemStack st = new ItemStack(item);
				n++;
				Object p = itemPath.invoke(null, st);
				String gotHeld = p == null ? null : (String) id.invoke(p);
				String wantHeld = CallingSpec.heldPath(e.getValue());
				if (gotHeld == null ? wantHeld != null : !gotHeld.equals(wantHeld)) bad.add(e.getKey() + " itemPath " + gotHeld + " (want " + wantHeld + ")");
				Set<String> gotLean = new TreeSet<>(), wantLean = new TreeSet<>();
				for (Object q : paths) if ((Boolean) leans.invoke(q, st)) gotLean.add((String) id.invoke(q));
				for (String t : e.getValue()) if (t.startsWith("calling/")) wantLean.add(t.substring("calling/".length()));
				if (!gotLean.equals(wantLean)) bad.add(e.getKey() + " leans " + gotLean + " (want " + wantLean + ")");
			}
		} catch (ReflectiveOperationException ex) {
			check(PREFIX + "Calling reads every item's path from its tags", false, ex);
			return;
		}
		check(PREFIX + "Calling.itemPath and Path.leans read the spec path for all " + n + " registered items", bad.isEmpty(), bad.isEmpty() ? null : bad);
	}

	static String str(JsonObject o, String k) {
		if (o == null || !o.has(k) || !o.get(k).isJsonPrimitive()) return null;
		return o.get(k).getAsString();
	}

	/** First non-blank line of {@code "elsewhere:<id>": [lines]}, or null. */
	static String jeiLine(JsonObject o, String id) {
		if (o == null || !o.has("elsewhere:" + id) || !o.get("elsewhere:" + id).isJsonArray()) return null;
		for (JsonElement l : o.getAsJsonArray("elsewhere:" + id)) if (l.isJsonPrimitive() && !l.getAsString().isBlank()) return l.getAsString();
		return null;
	}

	/** Every copy of a JSON object resource on the classpath, merged with the first copy winning per key (as the game's resource stack does). */
	static JsonObject merged(String path) {
		JsonObject out = new JsonObject();
		try {
			List<java.net.URL> urls = Collections.list(Gear3Content.class.getClassLoader().getResources(path));
			for (java.net.URL u : urls) {
				try (InputStream in = u.openStream()) {
					JsonElement root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
					if (!root.isJsonObject()) continue;
					for (var e : root.getAsJsonObject().entrySet()) if (!out.has(e.getKey())) out.add(e.getKey(), e.getValue());
				} catch (RuntimeException ignored) {
					// a copy in another format (e.g. a different JEI file shape) is not ours to judge here
				}
			}
		} catch (java.io.IOException e) {
			return out;
		}
		return out;
	}
}
