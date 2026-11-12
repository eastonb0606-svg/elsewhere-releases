package dev.elsewhere.lore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.ItemLore;

/** Every K-D item. Ids are looked up first (registry-collision rule: an existing id is reused, never re-registered). */
public final class LoreItems {
	private LoreItems() {}

	public static final List<Item> ALL = new ArrayList<>();
	/** lord key -> seal item, in rank order. */
	public static final Map<String, Item> SEALS = new LinkedHashMap<>();
	public static final String[] LORDS = {"marshal", "castellan", "master_at_arms", "huntsman", "chancellor"};

	public static Item ROYAL_PROCLAMATION, SURVEY_FRAGMENT, ROYAL_SURVEY_MAP, AZMAR_WELL_STONE;

	public static final TagKey<Item> SEAL_TAG = TagKey.create(Registries.ITEM, LoreContent.id("lore_seals"));

	public static void init() {
		ROYAL_PROCLAMATION = item("lore_royal_proclamation", WrittenBookItem::new,
			new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(DataComponents.WRITTEN_BOOK_CONTENT, Proclamation.content()));
		SURVEY_FRAGMENT = item("lore_survey_fragment", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), "lore_survey_fragment", 2));
		ROYAL_SURVEY_MAP = item("lore_royal_survey_map", SurveyMapItem::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "lore_royal_survey_map", 4));
		for (String lord : LORDS) {
			Item seal = item("lore_seal_" + lord, Item::new,
				lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), "lore_seal_" + lord, 2));
			SEALS.put(lord, seal);
		}
		LoreFactions.Lore ald = LoreFactions.of(LoreFactions.ALDERMARK);
		ald.proclamation = ROYAL_PROCLAMATION;
		ald.fragment = SURVEY_FRAGMENT;
		ald.map = ROYAL_SURVEY_MAP;
		for (LoreFactions.Lore f : LoreFactions.ALL.values()) {
			if (!f.rival()) continue;
			String book = f.key.equals("mirewick") ? "mirewick_council_proclamation" : f.key + "_royal_proclamation";
			f.proclamation = item(book, WrittenBookItem::new,
				new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(DataComponents.WRITTEN_BOOK_CONTENT, RivalProclamations.content(f.key)));
			f.fragment = item(f.key + "_survey_fragment", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), f.key + "_survey_fragment", 2));
			Identifier fid = f.id;
			f.map = item(f.key + "_survey_map", p -> new SurveyMapItem(p, fid), lore(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), f.key + "_survey_map", 4));
		}
		AZMAR_WELL_STONE = item("azmar_well_stone", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), "azmar_well_stone", 2));
		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.kingdom_lore"))
			.icon(() -> new ItemStack(ROYAL_PROCLAMATION))
			.displayItems((params, out) -> ALL.forEach(out::accept))
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, LoreContent.id("kingdom_lore"), tab);
	}

	/** Grey/blue tooltip lines {@code item.elsewhere.<key>.lore1..n} (lore1 grey, the rest blue). */
	static Item.Properties lore(Item.Properties p, String key, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			ChatFormatting c = i == 1 ? ChatFormatting.GRAY : ChatFormatting.BLUE;
			l.add(Component.translatable("item.elsewhere." + key + ".lore" + i).withStyle(s -> s.withColor(c).withItalic(false)));
		}
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	public static <I extends Item> Item item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
		Identifier id = LoreContent.id(name);
		if (BuiltInRegistries.ITEM.containsKey(id)) {
			Item existing = BuiltInRegistries.ITEM.getValue(id);
			ALL.add(existing);
			return existing;
		}
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	public static Item proclamationFor(Identifier faction) {
		Item i = LoreFactions.of(faction).proclamation;
		return i != null ? i : ROYAL_PROCLAMATION;
	}

	public static ItemStack proclamation() {
		return new ItemStack(ROYAL_PROCLAMATION);
	}
}
