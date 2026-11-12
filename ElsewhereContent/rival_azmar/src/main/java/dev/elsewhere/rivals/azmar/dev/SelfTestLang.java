package dev.elsewhere.rivals.azmar.dev;

import static dev.elsewhere.rivals.azmar.dev.SelfTest.check;
import static dev.elsewhere.rivals.azmar.dev.SelfTest.info;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * s-langfix: no registered Elsewhere id is missing its English name (the 3.0.0 release server suite found 9 raw keys at 12:18:
 * {@code entity.elsewhere.azmar_soldier}, its spawn egg and 7 court trader/diplomat spawn eggs).
 * <p>
 * Walks the live registries, so in the merged jar it covers every module and in the lane run it covers Azmar plus its devlibs. Each
 * name is the real displayed component (item hover name, block name, entity type description, effect display name, potion item
 * names, attribute key, creative tab title), and every translatable key inside it must be in the server's {@link Language}, which
 * holds every mod's {@code en_us.json} (Fabric). A key whose text is blank counts as missing too. Read-only, runs once.
 */
final class SelfTestLang {
	private SelfTestLang() {}

	/** The raw keys the release server suite reported (results.tsv, 12:18). */
	static final List<String> RELEASE_RAW = List.of("entity.elsewhere.azmar_soldier", "item.elsewhere.azmar_soldier_spawn_egg",
		"item.elsewhere.kingdom_auctioneer_spawn_egg", "item.elsewhere.kingdom_caravan_master_spawn_egg", "item.elsewhere.kingdom_clerk_spawn_egg",
		"item.elsewhere.kingdom_lawspeaker_spawn_egg", "item.elsewhere.kingdom_market_inspector_spawn_egg", "item.elsewhere.kingdom_royal_envoy_spawn_egg",
		"item.elsewhere.kingdom_smith_spawn_egg");

	/** Vanilla potion-type items: each Elsewhere potion needs a name on all four. */
	static final List<Item> POTION_ITEMS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION, Items.TIPPED_ARROW);

	static void add() {
		SelfTest.STEPS.add((srv, l) -> {
			Language lang = Language.getInstance();
			int[] total = new int[1];
			List<String> all = new ArrayList<>();

			List<String> items = sweep(BuiltInRegistries.ITEM, i -> new ItemStack(i).getHoverName(), lang, total);
			int itemCount = total[0];
			report("every Elsewhere item has an English name (spawn eggs included)", items, itemCount, true, all);

			total[0] = 0;
			List<String> blocks = sweep(BuiltInRegistries.BLOCK, b -> b.getName(), lang, total);
			report("every Elsewhere block has an English name (with or without an item)", blocks, total[0], false, all);

			total[0] = 0;
			List<String> ents = sweep(BuiltInRegistries.ENTITY_TYPE, t -> t.getDescription(), lang, total);
			report("every Elsewhere entity type has an English name", ents, total[0], true, all);

			total[0] = 0;
			List<String> effects = sweep(BuiltInRegistries.MOB_EFFECT, e -> e.getDisplayName(), lang, total);
			report("every Elsewhere mob effect has an English name", effects, total[0], false, all);

			// potions: the potion, splash, lingering and tipped-arrow item names (vanilla's creative tabs list all four)
			Set<String> potions = new TreeSet<>();
			int potionCount = 0;
			for (Identifier id : BuiltInRegistries.POTION.keySet()) {
				if (!ours(id)) continue;
				var holder = BuiltInRegistries.POTION.get(id).orElse(null);
				if (holder == null) continue;
				potionCount++;
				for (Item it : POTION_ITEMS) missing(PotionContents.createItemStack(it, holder).getHoverName(), lang, potions);
			}
			report("every Elsewhere potion is named as a potion, splash, lingering potion and tipped arrow", new ArrayList<>(potions), potionCount, false, all);

			Set<String> attrs = new TreeSet<>();
			int attrCount = 0;
			for (Identifier id : BuiltInRegistries.ATTRIBUTE.keySet()) {
				if (!ours(id)) continue;
				attrCount++;
				String key = BuiltInRegistries.ATTRIBUTE.getValue(id).getDescriptionId();
				if (!named(lang, key)) attrs.add(key);
			}
			report("every Elsewhere attribute has an English name", new ArrayList<>(attrs), attrCount, false, all);

			total[0] = 0;
			List<String> tabs = sweep(BuiltInRegistries.CREATIVE_MODE_TAB, t -> t.getDisplayName(), lang, total);
			report("every Elsewhere creative tab has an English title", tabs, total[0], true, all);

			// the exact keys the release suite flagged, each with a real name (the court eggs come from kingdom_court, the soldier from Azmar)
			List<String> stillRaw = new ArrayList<>();
			for (String k : RELEASE_RAW) {
				String id = k.substring(k.lastIndexOf('.') + 1);
				boolean registered = k.startsWith("entity.") ? BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.fromNamespaceAndPath("elsewhere", id))
					: BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("elsewhere", id));
				if (registered && !(named(lang, k) && (k.startsWith("entity.") || lang.getOrDefault(k).endsWith("Spawn Egg")))) stillRaw.add(k);
			}
			check("the 9 raw keys from the release server suite (Azmar Elite, its egg, 7 court trader and diplomat eggs) have real names",
				stillRaw.isEmpty(), stillRaw.isEmpty() ? lang.getOrDefault("entity.elsewhere.azmar_soldier") + " / " + lang.getOrDefault("item.elsewhere.azmar_soldier_spawn_egg")
					+ " / " + lang.getOrDefault("item.elsewhere.kingdom_clerk_spawn_egg") : stillRaw);
			if (!all.isEmpty()) info("lang: " + all.size() + " missing keys: " + all);
			return 0;
		});
	}

	/** True for an Elsewhere namespace ({@code elsewhere} or a module's own {@code elsewhere_*}). */
	static boolean ours(Identifier id) {
		return id.getNamespace().equals("elsewhere") || id.getNamespace().startsWith("elsewhere_");
	}

	static boolean named(Language lang, String key) {
		return lang.has(key) && !lang.getOrDefault(key).isBlank();
	}

	/** Every missing key in the names of all Elsewhere entries of {@code reg}; {@code count[0]} gets the number of entries looked at. */
	static <T> List<String> sweep(Registry<T> reg, Function<T, Component> name, Language lang, int[] count) {
		Set<String> out = new TreeSet<>();
		for (Identifier id : reg.keySet()) {
			if (!ours(id)) continue;
			count[0]++;
			Component c;
			try {
				c = name.apply(reg.getValue(id));
			} catch (RuntimeException e) {
				out.add(id + " (name threw " + e.getClass().getSimpleName() + ")");
				continue;
			}
			if (c == null) {
				out.add(id + " (no name)");
				continue;
			}
			missing(c, lang, out);
		}
		return new ArrayList<>(out);
	}

	/** Adds each translatable key in {@code c} (and its arguments and siblings) that has no text and no fallback. */
	static void missing(Component c, Language lang, Set<String> out) {
		if (c.getContents() instanceof TranslatableContents t) {
			if (t.getFallback() == null && !named(lang, t.getKey())) out.add(t.getKey());
			for (Object a : t.getArgs()) if (a instanceof Component ac) missing(ac, lang, out);
		}
		for (Component s : c.getSiblings()) missing(s, lang, out);
	}

	/** One check line; {@code mustHaveSome}: Azmar itself registers entries of this kind, so an empty sweep means the sweep is broken. */
	static void report(String name, List<String> missing, int looked, boolean mustHaveSome, List<String> all) {
		all.addAll(missing);
		String detail = missing.isEmpty() ? looked + " checked" : missing.size() + " of " + looked + " missing: "
			+ (missing.size() > 40 ? missing.subList(0, 40) + " ..." : missing);
		check(name, missing.isEmpty() && (looked > 0 || !mustHaveSome), detail);
	}
}
