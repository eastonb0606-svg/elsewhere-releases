package dev.elsewhere.compat.jei.dev;

import dev.elsewhere.compat.jei.ElsewhereJeiData;
import dev.elsewhere.compat.jei.ElsewhereJeiData.LootEntry;
import dev.elsewhere.compat.jei.ElsewhereJeiData.LootPage;
import dev.elsewhere.compat.jei.ElsewhereJeiData.Snapshot;
import dev.elsewhere.compat.jei.ElsewhereJeiData.TradePage;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * JEI coverage self-test (s-compat-jei, 3.0; {@code -Djei.selftest=true}, {@code -Djei.selftest.exit=true} halts after). Runs on a
 * server with every module loaded (the merged jar) once the recipes are loaded, and checks that what the JEI plugin would show covers
 * every registered {@code elsewhere:} item: a recipe that makes it (any recipe type JEI reads from the recipe manager), an attunement,
 * a loot page, a kingdom-shop line or an info page. Reads the same {@link ElsewhereJeiData#get()} the plugin reads. No JEI needed.
 */
public final class JeiSelfTest {
	private static final List<String> LOG = new ArrayList<>();
	private static int pass;
	private static int fail;

	private JeiSelfTest() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(JeiSelfTest::run);
	}

	private static void check(final String name, final boolean ok, final Object detail) {
		String line = (ok ? "PASS " : "FAIL ") + name + (ok || detail == null ? "" : ": " + detail);
		if (ok) {
			pass++;
		} else {
			fail++;
		}
		LOG.add(line);
		System.out.println("[jei-selftest] " + line);
	}

	private static void info(final String s) {
		System.out.println("[jei-selftest] INFO " + s);
	}

	/** Every item some recipe in the recipe manager makes (JEI shows these recipes itself). */
	static Set<Identifier> recipeOutputs(final MinecraftServer server) {
		Set<Identifier> out = new TreeSet<>();
		ContextMap ctx = SlotDisplayContext.fromLevel(server.overworld());
		for (RecipeHolder<?> h : server.getRecipeManager().getRecipes()) {
			try {
				for (RecipeDisplay d : h.value().display()) {
					for (ItemStack s : d.result().resolveForStacks(ctx)) {
						if (!s.isEmpty()) {
							out.add(BuiltInRegistries.ITEM.getKey(s.getItem()));
						}
					}
				}
			} catch (RuntimeException e) {
				info("recipe " + h.id() + " has no display: " + e);
			}
		}
		return out;
	}

	/** The items the Elsewhere pages give a source for (info lines, attunement results, loot, shop goods). */
	static Set<Identifier> pageSources(final Snapshot data) {
		Set<Identifier> out = new TreeSet<>();
		for (var e : data.info().entrySet()) {
			Identifier id = Identifier.tryParse(e.getKey());
			if (id != null && !e.getValue().isEmpty()) {
				out.add(id);
			}
		}
		data.attunements().forEach(a -> out.add(BuiltInRegistries.ITEM.getKey(a.result())));
		for (LootPage p : data.loot()) {
			for (LootEntry e : p.entries()) {
				out.add(BuiltInRegistries.ITEM.getKey(e.item()));
			}
		}
		for (TradePage t : data.trades()) {
			out.add(BuiltInRegistries.ITEM.getKey(t.result()));
		}
		return out;
	}

	public static void run(final MinecraftServer server) {
		try {
			runChecks(server);
		} catch (Throwable t) {
			t.printStackTrace(System.out);
			check("self-test ran to the end", false, t);
		}
		System.out.println("[jei-selftest] TOTAL " + pass + " passed, " + fail + " failed");
		if (Boolean.getBoolean("jei.selftest.exit")) {
			server.halt(false);
		}
	}

	static void runChecks(final MinecraftServer server) {
		long t0 = System.nanoTime();
		Snapshot data = ElsewhereJeiData.get();
		info("data built in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
		check("JEI data built: info pages, attunements, loot and shop pages", !data.info().isEmpty() && !data.attunements().isEmpty()
			&& !data.loot().isEmpty() && !data.trades().isEmpty(),
			data.info().size() + " info, " + data.attunements().size() + " attunements, " + data.loot().size() + " loot, " + data.trades().size() + " shop");

		Set<Identifier> recipes = recipeOutputs(server);
		Set<Identifier> pages = pageSources(data);
		Set<String> hidden = Set.copyOf(ElsewhereJeiData.HIDDEN);
		List<String> missing = new ArrayList<>();
		int total = 0;
		int byRecipe = 0;
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (!id.getNamespace().equals("elsewhere") || hidden.contains(id.toString())) {
				continue;
			}
			total++;
			boolean r = recipes.contains(id);
			if (r) {
				byRecipe++;
			}
			if (!r && !pages.contains(id)) {
				missing.add(id.getPath());
			}
		}
		info(total + " elsewhere items: " + byRecipe + " made by a recipe, " + (total - missing.size()) + " covered, " + missing.size() + " missing");
		check("every registered Elsewhere item has a JEI recipe or page (" + total + " items, " + missing.size() + " missing)",
			missing.isEmpty(), missing);

		List<String> orphans = new ArrayList<>();
		for (var e : data.info().entrySet()) {
			if (e.getKey().startsWith("elsewhere:") && ElsewhereJeiData.item(e.getKey()).isEmpty()) {
				orphans.add(e.getKey());
			}
		}
		check("every info page names a registered item", orphans.isEmpty(), orphans);

		List<String> empty = new ArrayList<>();
		for (var e : data.info().entrySet()) {
			if (e.getValue().stream().anyMatch(l -> l.isBlank())) {
				empty.add(e.getKey());
			}
		}
		check("no blank info line", empty.isEmpty(), empty);

		check("Wayfinder attunement pages: all 7 dimensions", data.attunements().size() == 7, data.attunements().size());

		Set<String> factions = new TreeSet<>();
		Set<String> coins = new TreeSet<>();
		for (TradePage t : data.trades()) {
			factions.add(t.faction());
			coins.add(BuiltInRegistries.ITEM.getKey(t.currency()).getPath());
		}
		check("kingdom shops: Aldermark, Hrafnholt, Azmar and Mirewick each have lines",
			factions.containsAll(List.of("aldermark", "hrafnholt", "azmar", "mirewick")), factions);
		check("kingdom shops are priced in each kingdom's own coin (Crown, Hacksilver, Dinar, Scrip)",
			coins.containsAll(List.of("aldermark_crown", "hacksilver", "azmar_dinar", "mirewick_scrip")), coins);
		check("Mirewick Scrip-Desk: weekly auction lots shown", data.trades().stream().anyMatch(t -> t.faction().equals("mirewick") && t.auction()), null);
		check("every shop line has a positive price", data.trades().stream().allMatch(t -> t.price() > 0),
			data.trades().stream().filter(t -> t.price() <= 0).map(t -> t.id().toString()).toList());

		Set<String> chests = new TreeSet<>();
		Set<String> creatures = new TreeSet<>();
		for (LootPage p : data.loot()) {
			(p.kind().equals("chest") ? chests : creatures).add(p.source());
		}
		info(chests.size() + " chest loot pages, " + creatures.size() + " creature loot pages");
		check("structure-chest loot pages (kingdom caches, couriers, dimension structures)", chests.size() >= 20
			&& chests.containsAll(List.of("kingdom_cache", "mirewick_courier", "azmar_courier", "hrafnholt_courier")), chests);
		check("no loot page lists an item at 0% chance", data.loot().stream().allMatch(p -> p.entries().stream().allMatch(e -> e.chance() > 0F)),
			data.loot().stream().filter(p -> p.entries().stream().anyMatch(e -> e.chance() <= 0F)).map(p -> p.id().toString()).toList());

		List<String> noCalling = new ArrayList<>();
		for (String path : List.of("vanguard", "marksman", "caster", "summoner")) {
			TagKey<Item> armor = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "calling/" + path));
			TagKey<Item> weapon = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("elsewhere", "calling_weapon/" + path));
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack s = new ItemStack(item);
				if (s.is(armor) || s.is(weapon)) {
					String id = BuiltInRegistries.ITEM.getKey(item).toString();
					List<String> lines = data.info().getOrDefault(id, List.of());
					if (lines.stream().noneMatch(l -> l.contains("Calling"))) {
						noCalling.add(id);
					}
				}
			}
		}
		check("every Calling armor piece and Calling weapon has its Calling line", noCalling.isEmpty(), noCalling);

		List<String> eggs = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ITEM.keySet()) {
			if (id.getNamespace().equals("elsewhere") && id.getPath().endsWith("_spawn_egg")
				&& data.info().getOrDefault(id.toString(), List.of()).stream().noneMatch(l -> l.contains("Creative"))) {
				eggs.add(id.getPath());
			}
		}
		check("every spawn egg says it is creative-only", eggs.isEmpty(), eggs);

		check("data is built once and reused (lazy cache)", ElsewhereJeiData.get() == data, null);
	}
}
