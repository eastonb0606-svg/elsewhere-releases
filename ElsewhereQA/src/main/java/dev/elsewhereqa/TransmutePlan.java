package dev.elsewhereqa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * s-compat-transmute (3.0.0): every Elsewhere item that was planned to work with Test Meter's Transmutation Table does.
 * Reads {@code /transmute-plan.json} (generated with ElsewhereQA/reports/transmute-3.0.md) plus every Test Meter data file the
 * jar ships, and checks against the live value map:
 * <ul>
 *   <li>transmute entries: every id in every {@code testmeter:transmutation_excluded} tag file and every {@code testmeter/values}
 *       file is a registered item (tag entries are {@code required:false}, so a typo would silently do nothing);</li>
 *   <li>transmute plan: every planned id resolves; planned exclusions are excluded, planned 5x-netherite items are at least 5x,
 *       planned automatic items have a value, capped items stay under their cap;</li>
 *   <li>transmute coverage: every Elsewhere item is either excluded or has a value (no missing item);</li>
 *   <li>transmute sanity: no Elsewhere item takes an inferred value (that is how a stone block once cost 18 netherite ingots), no
 *       data override lets a recipe make Quanta, and no non-gear Elsewhere item gets a heuristic price above 5x netherite;</li>
 *   <li>transmute survival: the Table and Tablet recipes still load and craft with Elsewhere present, and a fake player burns,
 *       learns and withdraws an Elsewhere item, while an excluded one is refused.</li>
 * </ul>
 * Test Meter is optional: every check reports SKIP when it is absent.
 */
final class TransmutePlan implements QAServer.Step {
	static final String AREA = "transmute plan";

	@Override
	public boolean tick(final MinecraftServer server) throws Exception {
		Class<?> tv = ReleaseSuite.cls("dev.testmeter.TransmuteValues");
		Class<?> tm = ReleaseSuite.cls("dev.testmeter.Transmutation");
		if (tv == null || tm == null) {
			ReleaseSuite.skip(AREA, "Test Meter is not loaded in this run");
			return true;
		}
		Class<?> valueCls = ReleaseSuite.cls("dev.testmeter.TransmuteValues$Value");
		double net = ReleaseSuite.Transmute.val(tv, valueCls, new ItemStack(Items.NETHERITE_INGOT));
		JsonObject plan = readPlan();
		if (plan == null) {
			QAServer.record("FAIL", AREA, "transmute-plan.json is missing from the QA jar");
			return true;
		}

		// 1. Every Test Meter data entry the jar ships names a real item.
		TreeSet<String> tagIds = new TreeSet<>(), valueIds = new TreeSet<>(), missing = new TreeSet<>();
		Identifier tagFile = Identifier.fromNamespaceAndPath("testmeter", "tags/item/transmutation_excluded.json");
		int tagFiles = 0;
		for (Resource r : server.getResourceManager().getResourceStack(tagFile)) {
			tagFiles++;
			try (Reader in = r.openAsReader()) {
				for (JsonElement e : JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("values")) {
					tagIds.add(e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString());
				}
			}
		}
		int valueFiles = 0;
		for (Map.Entry<Identifier, List<Resource>> e : server.getResourceManager().listResourceStacks("testmeter/values", id -> id.getPath().endsWith(".json")).entrySet()) {
			for (Resource r : e.getValue()) {
				valueFiles++;
				try (Reader in = r.openAsReader()) {
					for (String k : JsonParser.parseReader(in).getAsJsonObject().getAsJsonObject("values").keySet()) {
						valueIds.add(k);
					}
				}
			}
		}
		for (String id : tagIds) {
			if (!id.startsWith("#") && !registered(id)) {
				missing.add(id);
			}
		}
		for (String id : valueIds) {
			if (!registered(id)) {
				missing.add(id);
			}
		}
		QAServer.check(tagFiles > 0 && missing.isEmpty(), AREA, QAServer.f("transmute entries: all %d exclusion-tag ids (%d tag files) and %d value-file ids (%d files) are registered items; unknown: %s",
			tagIds.size(), tagFiles, valueIds.size(), valueFiles, missing));

		// 2. The plan.
		TreeSet<String> unresolved = new TreeSet<>(), notExcluded = new TreeSet<>(), under5x = new TreeSet<>(), unvalued = new TreeSet<>(), overCap = new TreeSet<>();
		for (String path : strings(plan, "planned_gear3")) {
			if (!registered("elsewhere:" + path)) {
				unresolved.add(path);
			}
		}
		for (String path : strings(plan, "exclude")) {
			Item item = item(path, unresolved);
			if (item != null && (!excluded(tm, new ItemStack(item)) || lookup(tv, item) != null)) {
				notExcluded.add(path);
			}
		}
		for (String path : strings(plan, "min5x")) {
			Item item = item(path, unresolved);
			double q = item == null ? -1 : value(tv, valueCls, item);
			if (item != null && q < 5 * net) {
				under5x.add(path + "=" + QAServer.f("%.2fx", q / net));
			}
		}
		for (String path : strings(plan, "auto")) {
			Item item = item(path, unresolved);
			if (item != null && !(value(tv, valueCls, item) > 0)) {
				unvalued.add(path);
			}
		}
		JsonObject caps = plan.getAsJsonObject("caps");
		for (String path : caps.keySet()) {
			Item item = item(path, unresolved);
			double q = item == null ? 0 : value(tv, valueCls, item);
			if (q > caps.get(path).getAsDouble()) {
				overCap.add(path + "=" + Math.round(q));
			}
		}
		QAServer.check(unresolved.isEmpty(), AREA, QAServer.f("every planned id resolves (%d exclusions, %d at 5x netherite, %d automatic, %d gear3 items incl. the 21 swarm items); unresolved: %s",
			strings(plan, "exclude").size(), strings(plan, "min5x").size(), strings(plan, "auto").size(), strings(plan, "planned_gear3").size(), unresolved));
		QAServer.check(notExcluded.isEmpty(), AREA, "planned exclusions (uniques, kingdom and rival items, currencies, boss parts, smithing templates) are refused by the table; valued anyway: " + notExcluded);
		QAServer.check(under5x.isEmpty(), AREA, QAServer.f("planned rare materials and endgame gear are priced at least 5x a netherite ingot (%.0f Quanta); cheaper: %s", 5 * net, under5x));
		QAServer.check(unvalued.isEmpty(), AREA, "planned automatic items have a Quanta value; none: " + unvalued);
		QAServer.check(overCap.isEmpty(), AREA, "capped items stay at or under their cap (blocks that once inherited inflated prices); over: " + overCap);

		// 3. Coverage and 4. sanity over every Elsewhere item.
		TreeSet<String> noValue = new TreeSet<>(), inferred = new TreeSet<>(), wildFallback = new TreeSet<>();
		int valued = 0, excludedCount = 0;
		for (Identifier id : QAServer.ids(BuiltInRegistries.ITEM)) {
			Item item = BuiltInRegistries.ITEM.getValue(id);
			ItemStack stack = new ItemStack(item);
			if (excluded(tm, stack)) {
				excludedCount++;
				continue;
			}
			Object v = lookup(tv, item);
			if (v == null) {
				noValue.add(id.getPath());
				continue;
			}
			valued++;
			double q = (double)ReleaseSuite.call(v, valueCls, "value");
			String src = String.valueOf(ReleaseSuite.call(v, valueCls, "source"));
			if (src.equals("INFERRED")) {
				inferred.add(id.getPath() + "=" + Math.round(q));
			}
			if (src.equals("FALLBACK") && q > 5 * net && !ReleaseSuite.Transmute.isGear(item)) {
				wildFallback.add(id.getPath() + "=" + Math.round(q));
			}
		}
		QAServer.check(noValue.isEmpty(), AREA, QAServer.f("coverage: every Elsewhere item is excluded (%d) or valued (%d); neither: %s", excludedCount, valued, noValue));
		QAServer.check(inferred.isEmpty(), AREA, "no Elsewhere item takes an inferred (back-propagated) value; inferred: " + inferred);
		QAServer.check(wildFallback.isEmpty(), AREA, "no non-gear Elsewhere item gets a heuristic price above 5x netherite; heuristic: " + wildFallback);
		List<String> profits = new ArrayList<>();
		Object snap = ReleaseSuite.call(null, tv, "snapshot");
		Field offenders = snap.getClass().getDeclaredField("offenders");
		offenders.setAccessible(true);
		for (Object o : (Collection<?>)offenders.get(snap)) {
			if (String.valueOf(o).contains("elsewhere:")) {
				profits.add(String.valueOf(o));
			}
		}
		QAServer.check(profits.isEmpty(), AREA, "no Elsewhere data override lets a recipe make Quanta; offenders: " + profits);

		// 5. Survival use.
		survival(server, tm, tv, valueCls);
		return true;
	}

	private static void survival(final MinecraftServer server, final Class<?> tm, final Class<?> tv, final Class<?> valueCls) throws Exception {
		ServerLevel level = server.overworld();
		Class<?> items = ReleaseSuite.cls("dev.testmeter.TestItems");
		Object[][] recipes = {
			{"transmutation_table", new Item[] {Items.OBSIDIAN, Items.AMETHYST_SHARD, Items.OBSIDIAN, Items.DIAMOND, Items.ENDER_EYE, Items.DIAMOND, Items.OBSIDIAN,
				Items.AMETHYST_SHARD, Items.OBSIDIAN}},
			{"transmutation_tablet", new Item[] {Items.ENDER_EYE, Items.DIAMOND, Items.ENDER_EYE, Items.NETHERITE_SCRAP, Items.RECOVERY_COMPASS, Items.NETHERITE_SCRAP,
				Items.ENDER_EYE, Items.DIAMOND, Items.ENDER_EYE}},
		};
		for (Object[] r : recipes) {
			String kind = (String)r[0];
			List<ItemStack> grid = new ArrayList<>();
			for (Item i : (Item[])r[1]) {
				grid.add(new ItemStack(i));
			}
			CraftingInput input = CraftingInput.of(3, 3, grid);
			Optional<RecipeHolder<CraftingRecipe>> found = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
			boolean ok = found.isPresent() && found.get().id().identifier().equals(Identifier.fromNamespaceAndPath("testmeter", kind));
			String made = "";
			if (ok) {
				ItemStack crafted = found.get().value().assemble(input);
				made = items == null ? "?" : String.valueOf(ReleaseSuite.call(null, items, "kind", crafted));
				ok = kind.equals(made);
			}
			QAServer.check(ok, AREA, QAServer.f("survival: the %s recipe loads with Elsewhere present and crafts a working %s (%s)", kind.replace('_', ' '), kind, found.map(h -> h.id().identifier().toString()).orElse("no recipe")));
		}

		// Burn, learn and withdraw a valued Elsewhere item; an excluded one is refused.
		FakePlayer p = QAServer.fake(level, "QATransmute");
		Item probe = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("elsewhere", "skyiron_ingot"));
		Item refused = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("elsewhere", "boss_token"));
		if (probe == Items.AIR || refused == Items.AIR) {
			QAServer.record("FAIL", AREA, "survival: probe items elsewhere:skyiron_ingot / elsewhere:boss_token are not registered");
			return;
		}
		double unit = value(tv, valueCls, probe);
		ItemStack stack = new ItemStack(probe, 2);
		p.getInventory().clearContent();
		long gain = (long)ReleaseSuite.call(null, tm, "burn", p, stack, 2);
		String key = (String)ReleaseSuite.call(null, tv, "key", new ItemStack(probe));
		p.getInventory().clearContent();
		int back = (int)ReleaseSuite.call(null, tm, "withdraw", p, key, 1);
		boolean inInv = p.getInventory().contains(new ItemStack(probe));
		QAServer.check(gain == (long)Math.floor(unit * 2 + 1e-9) && stack.isEmpty() && back == 1 && inInv, AREA,
			QAServer.f("survival: burning 2 Skyiron Ingots gives %d Quanta (value %.0f each), teaches the item, and 1 can be withdrawn again (%d)", gain, unit, back));
		ItemStack token = new ItemStack(refused);
		long refusedGain = (long)ReleaseSuite.call(null, tm, "burn", p, token, 1);
		QAServer.check(refusedGain < 0 && token.getCount() == 1, AREA, "survival: an excluded Elsewhere item (Boss Token) is refused and kept");
		p.getInventory().clearContent();
	}

	private static boolean registered(final String id) {
		Identifier i = Identifier.tryParse(id);
		return i != null && BuiltInRegistries.ITEM.containsKey(i);
	}

	private static Item item(final String path, final TreeSet<String> unresolved) {
		Identifier id = Identifier.fromNamespaceAndPath("elsewhere", path);
		if (!BuiltInRegistries.ITEM.containsKey(id)) {
			unresolved.add(path);
			return null;
		}
		return BuiltInRegistries.ITEM.getValue(id);
	}

	private static boolean excluded(final Class<?> tm, final ItemStack stack) throws Exception {
		return stack.getItem() instanceof SpawnEggItem || (boolean)ReleaseSuite.call(null, tm, "isExcluded", stack);
	}

	private static Object lookup(final Class<?> tv, final Item item) throws Exception {
		return ReleaseSuite.call(null, tv, "lookup", new ItemStack(item));
	}

	private static double value(final Class<?> tv, final Class<?> valueCls, final Item item) throws Exception {
		return ReleaseSuite.Transmute.val(tv, valueCls, new ItemStack(item));
	}

	private static List<String> strings(final JsonObject plan, final String key) {
		List<String> out = new ArrayList<>();
		JsonArray a = plan.getAsJsonArray(key);
		if (a != null) {
			for (JsonElement e : a) {
				out.add(e.getAsString());
			}
		}
		return out;
	}

	static JsonObject readPlan() {
		try (InputStream in = TransmutePlan.class.getResourceAsStream("/transmute-plan.json")) {
			return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return null;
		}
	}
}
