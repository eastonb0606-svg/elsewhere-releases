package dev.elsewhere.rivals.hrafnholt;

import dev.elsewhere.rivals.hrafnholt.item.HornbowItem;
import dev.elsewhere.rivals.hrafnholt.item.RavenBannerItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Hrafnholt items: Hacksilver, the six Lord Seals, the six unique boss drops (every one a sidegrade or downgrade to netherite),
 * the Raven Banner, the trim template, the banner pattern and the shop cosmetics. Netherite reference: durability base 37,
 * helmet 3 / chest 8 / boots 3, toughness 3, knockback 0.1, enchantability 15; netherite axe 10 damage, speed 1.0, 2031 uses.
 */
public final class HrafnholtItems {
	private HrafnholtItems() {}

	public static final TagKey<Item> IRON_REPAIR = TagKey.create(Registries.ITEM, HrafnholtContent.id("hrafnholt_iron_repair"));
	public static final TagKey<Item> FUR_REPAIR = TagKey.create(Registries.ITEM, HrafnholtContent.id("hrafnholt_fur_repair"));
	public static final TagKey<Item> LORE_SEALS = TagKey.create(Registries.ITEM, HrafnholtContent.id("lore_seals"));
	public static final String[] SLOTS = {"jarl", "hersir", "hallwarden", "skald", "chief", "huntmaster"};

	static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, HrafnholtContent.id(name));
	}

	private static Map<ArmorType, Integer> defense(int boots, int helmet, int chest, int legs) {
		return Map.of(ArmorType.BOOTS, boots, ArmorType.HELMET, helmet, ArmorType.CHESTPLATE, chest, ArmorType.LEGGINGS, legs, ArmorType.BODY, chest + 3);
	}

	// Iron Crown: helmet 3 / toughness 2 / knockback 0.1 (netherite: 3 / 3 / 0.1), 420 uses (407), enchantability 25
	/** 3.0 kill switch {@code rebalance_rivals} (GEAR.md A4): JVM flag -Delsewhere.overhaul3.rebalance_rivals=false or config/elsewhere-overhaul3.json. */
	public static final boolean REBALANCE_RIVALS = rivalsOn();

	private static boolean rivalsOn() {
		String p = System.getProperty("elsewhere.overhaul3.rebalance_rivals");
		if (p != null) return !"false".equalsIgnoreCase(p);
		try {
			var f = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("elsewhere-overhaul3.json");
			if (java.nio.file.Files.isRegularFile(f)) {
				var o = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(f)).getAsJsonObject();
				if (o.has("rebalance_rivals")) return o.get("rebalance_rivals").getAsBoolean();
			}
		} catch (Exception e) {
			// default on
		}
		return true;
	}

	public static final ArmorMaterial CROWN = new ArmorMaterial(38, defense(3, 3, 8, 6), 25, SoundEvents.ARMOR_EQUIP_IRON, REBALANCE_RIVALS ? 3.0F : 2.0F, 0.1F, IRON_REPAIR, asset("hrafnholt_crown"));
	// Wolfskin Boots: boots 3 / toughness 2 / knockback 0 (netherite: 3 / 3 / 0.1), 481 uses (481), enchantability 15
	public static final ArmorMaterial WOLFSKIN = new ArmorMaterial(37, defense(3, 3, 8, 6), 15, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, FUR_REPAIR, asset("hrafnholt_wolfskin"));
	// Hearth-Ward Mail: chestplate 7 / 3 / 0.1 (netherite: 8 / 3 / 0.1), 560 uses (592), enchantability 10
	public static final ArmorMaterial HEARTH_WARD = new ArmorMaterial(35, defense(3, 3, REBALANCE_RIVALS ? 8 : 7, 6), 10, SoundEvents.ARMOR_EQUIP_CHAIN, 3.0F, 0.1F, IRON_REPAIR, asset("hrafnholt_hearth_ward"));
	// Ulfhednar Pelt: chestplate 6 / 1 / 0.05 (netherite: 8 / 3 / 0.1), 480 uses (592), enchantability 15
	public static final ArmorMaterial ULFHEDNAR = new ArmorMaterial(30, defense(2, 2, REBALANCE_RIVALS ? 8 : 6, 5), 15, SoundEvents.ARMOR_EQUIP_LEATHER, REBALANCE_RIVALS ? 3.0F : 1.0F, REBALANCE_RIVALS ? 0.0F : 0.05F, FUR_REPAIR, asset("hrafnholt_ulfhednar"));
	/** Skald's Rune-Axe: 3 + axe 5 + base 1 = 9 damage (netherite 10), 1,700 uses (2,031), enchantability 15. */
	public static final ToolMaterial RUNE = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1700, 8.0F, 3.0F, 15, IRON_REPAIR);

	public static Item HACKSILVER, IRON_CROWN, RAVEN_BANNER, WOLFSKIN_BOOTS, HEARTH_WARD_MAIL, RUNE_AXE, ULFHEDNAR_PELT, HORNBOW;
	public static Item TRIM_TEMPLATE, BANNER_PATTERN, RAVEN_SHIELD, WOLFPELT_BLANKET, FUR_COLLAR_DYE;
	public static final Map<String, Item> SEALS = new LinkedHashMap<>();
	public static final List<Item> ALL = new ArrayList<>();

	static Item.Properties lore(Item.Properties p, String id, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) l.add(Component.translatable("item.elsewhere." + id + ".desc" + i));
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	static <T extends Item> T reg(String id, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, HrafnholtContent.id(id));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	static ItemAttributeModifiers withModifier(ItemAttributeModifiers base, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, String id, double amount, EquipmentSlotGroup slot) {
		return base.withModifierAdded(attr, new AttributeModifier(HrafnholtContent.id(id), amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
	}

	public static void init() {
		if (HACKSILVER != null) return;
		HACKSILVER = reg("hacksilver", Item::new, lore(new Item.Properties(), "hacksilver", 2));
		for (String s : SLOTS)
			SEALS.put(s, reg("hrafnholt_seal_" + s, Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON), "hrafnholt_seal_" + s, 1)));
		IRON_CROWN = reg("hrafnholt_iron_crown", Item::new, lore(new Item.Properties().humanoidArmor(CROWN, ArmorType.HELMET)
			.component(DataComponents.MAX_DAMAGE, 420).rarity(Rarity.EPIC), "hrafnholt_iron_crown", 3));
		WOLFSKIN_BOOTS = reg("hrafnholt_wolfskin_boots", Item::new, lore(new Item.Properties().humanoidArmor(WOLFSKIN, ArmorType.BOOTS)
			.component(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x6E6A66)).rarity(Rarity.EPIC), "hrafnholt_wolfskin_boots", 2));
		ItemAttributeModifiers mail = withModifier(HEARTH_WARD.createAttributes(ArmorType.CHESTPLATE), Attributes.MOVEMENT_SPEED, "hearth_ward_weight", -0.05, EquipmentSlotGroup.CHEST);
		HEARTH_WARD_MAIL = reg("hrafnholt_hearth_ward_mail", Item::new, lore(new Item.Properties().humanoidArmor(HEARTH_WARD, ArmorType.CHESTPLATE)
			.attributes(mail).rarity(Rarity.EPIC), "hrafnholt_hearth_ward_mail", 2));
		ULFHEDNAR_PELT = reg("hrafnholt_ulfhednar_pelt", Item::new, lore(new Item.Properties().humanoidArmor(ULFHEDNAR, ArmorType.CHESTPLATE)
			.component(DataComponents.DYED_COLOR, new net.minecraft.world.item.component.DyedItemColor(0x6E6A66)).rarity(Rarity.EPIC), "hrafnholt_ulfhednar_pelt", 2));
		RUNE_AXE = reg("hrafnholt_skalds_rune_axe", Item::new, lore(new Item.Properties().axe(RUNE, REBALANCE_RIVALS ? 6.0F : 5.0F, -3.0F).rarity(Rarity.EPIC), "hrafnholt_skalds_rune_axe", 2));
		HORNBOW = reg("hrafnholt_hornbow", HornbowItem::new, lore(new Item.Properties().durability(480).enchantable(15).repairable(IRON_REPAIR).rarity(Rarity.EPIC), "hrafnholt_hornbow", 2));
		RAVEN_BANNER = reg("hrafnholt_raven_banner", RavenBannerItem::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "hrafnholt_raven_banner", 2));
		TRIM_TEMPLATE = reg("hrafnholt_armor_trim_smithing_template", SmithingTemplateItem::createArmorTrimTemplate, new Item.Properties().rarity(Rarity.UNCOMMON));
		BANNER_PATTERN = reg("hrafnholt_banner_pattern", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, ctx -> ctx.lookupOrThrow(Registries.BANNER_PATTERN)
				.getOrThrow(TagKey.create(Registries.BANNER_PATTERN, HrafnholtContent.id("pattern_item/hrafnholt")))));
		RAVEN_SHIELD = reg("hrafnholt_raven_shield", Item::new, lore(new Item.Properties().stacksTo(1), "hrafnholt_raven_shield", 2));
		WOLFPELT_BLANKET = reg("hrafnholt_wolfpelt_blanket", Item::new, lore(new Item.Properties().stacksTo(1), "hrafnholt_wolfpelt_blanket", 2));
		FUR_COLLAR_DYE = reg("hrafnholt_fur_collar_dye", Item::new, lore(new Item.Properties(), "hrafnholt_fur_collar_dye", 2));

		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.rival_hrafnholt"))
			.icon(() -> new ItemStack(IRON_CROWN))
			.displayItems((params, out) -> ALL.forEach(out::accept))
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, HrafnholtContent.id("rival_hrafnholt"), tab);
	}
}
