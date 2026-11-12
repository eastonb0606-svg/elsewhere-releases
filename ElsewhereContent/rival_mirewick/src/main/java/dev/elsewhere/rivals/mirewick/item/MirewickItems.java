package dev.elsewhere.rivals.mirewick.item;

import dev.elsewhere.rivals.mirewick.MirewickContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SmithingTemplateItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.block.entity.BannerPattern;

/**
 * Mirewick items (ids start {@code mirewick_}): Lantern Scrip, five Lord Seals, the five Magnate drops (each a sidegrade or a
 * creative downgrade to netherite, never stronger), the Chair table (Lantern Coronet, Marsh-Lantern Standard), Dunstan Reeve's
 * Reedcutter Pike, the trim template and banner pattern, and two cosmetics sold at the Scrip-Desk.
 * Netherite for reference: boots 3/3/0.1, chest 8/3/0.1, helmet 3/3/0.1, leggings 6/3/0.1.
 */
public final class MirewickItems {
	private MirewickItems() {}

	public static final TagKey<Item> CLOTH_REPAIR = TagKey.create(Registries.ITEM, MirewickContent.id("mirewick_cloth_repair"));
	public static final TagKey<Item> IRON_REPAIR = TagKey.create(Registries.ITEM, MirewickContent.id("mirewick_iron_repair"));
	public static final TagKey<Item> WOOD_REPAIR = TagKey.create(Registries.ITEM, MirewickContent.id("mirewick_wood_repair"));
	public static final TagKey<Item> LORE_SEALS = TagKey.create(Registries.ITEM, MirewickContent.id("lore_seals"));
	public static final ResourceKey<TrimPattern> MIREWICK_TRIM = ResourceKey.create(Registries.TRIM_PATTERN, MirewickContent.id("mirewick"));
	public static final ResourceKey<BannerPattern> MIREWICK_PATTERN = ResourceKey.create(Registries.BANNER_PATTERN, MirewickContent.id("mirewick"));
	public static final TagKey<BannerPattern> PATTERN_ITEM_TAG = TagKey.create(Registries.BANNER_PATTERN, MirewickContent.id("pattern_item/mirewick"));

	static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, MirewickContent.id(name));
	}

	private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helmet) {
		return Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest, ArmorType.HELMET, helmet, ArmorType.BODY, chest + legs);
	}

	// durability base x slot factor (helmet 11, chest 16, legs 15, boots 13): legs 540, boots 468, chest 576, cowl 275, coronet 396
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

	public static final ArmorMaterial DEBT = new ArmorMaterial(36, defense(2, REBALANCE_RIVALS ? 6 : 5, 6, 2), 15, SoundEvents.ARMOR_EQUIP_LEATHER, REBALANCE_RIVALS ? 2.5F : 2.0F, 0.05F, CLOTH_REPAIR, asset("mirewick_debt_collector"));
	public static final ArmorMaterial WADERS = new ArmorMaterial(36, defense(3, 5, 6, 2), 15, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, CLOTH_REPAIR, asset("mirewick_fen_marshal"));
	public static final ArmorMaterial BRIGANDINE = new ArmorMaterial(36, defense(2, 5, REBALANCE_RIVALS ? 7 : 6, 2), 10, SoundEvents.ARMOR_EQUIP_CHAIN, REBALANCE_RIVALS ? 2.5F : 2.0F, 0.1F, IRON_REPAIR, asset("mirewick_dikewarden"));
	public static final ArmorMaterial COWL = new ArmorMaterial(25, defense(1, 2, 3, 2), 25, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F, CLOTH_REPAIR, asset("mirewick_almoner"));
	public static final ArmorMaterial CORONET = new ArmorMaterial(36, defense(3, 6, 8, 3), 25, SoundEvents.ARMOR_EQUIP_GOLD, 2.0F, 0.1F, IRON_REPAIR, asset("mirewick_lantern_coronet"));

	public static Item SCRIP, DEBT_COLLECTORS_LEGGINGS, FEN_MARSHALS_WADERS, DIKEWARDENS_BRIGANDINE, FOWLERS_MARSHBOW, ALMONERS_COWL;
	public static Item LANTERN_CORONET, MARSH_LANTERN_STANDARD, REEDCUTTER_PIKE, TRIM_TEMPLATE, BANNER_PATTERN, LANTERN_SHIELD, OILCLOTH_DYE;
	/** Lord Seals by Magnate slot ({@code marrow, thwaite, sorrel, tolliver, ames}). */
	public static final java.util.LinkedHashMap<String, Item> SEALS = new java.util.LinkedHashMap<>();
	/** The unique drop of each Magnate slot. */
	public static final java.util.LinkedHashMap<String, Item> UNIQUE = new java.util.LinkedHashMap<>();
	public static final List<Item> ALL = new ArrayList<>();

	static Item.Properties lore(Item.Properties p, String id, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) l.add(Component.translatable("item.elsewhere." + id + ".desc" + i));
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	static <T extends Item> T reg(String id, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, MirewickContent.id(id));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	public static void init() {
		if (SCRIP != null) return;
		SCRIP = reg("mirewick_scrip", Item::new, new Item.Properties());
		for (String k : new String[] {"marrow", "thwaite", "sorrel", "tolliver", "ames"})
			SEALS.put(k, reg("mirewick_seal_" + k, Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), "mirewick_seal_" + k, 1)));

		// Marrow: leggings 5/2/0.05; the walk is in the EntityStuckMixin (cobweb, mud) and MirewickPassives (+5% on planks and mud-brick)
		DEBT_COLLECTORS_LEGGINGS = reg("mirewick_debt_collectors_leggings", Item::new,
			lore(new Item.Properties().humanoidArmor(DEBT, ArmorType.LEGGINGS).rarity(Rarity.EPIC), "mirewick_debt_collectors_leggings", 3));
		// Thwaite: boots 3/2/0, Depth Strider II equivalent (2/3 of full water speed) and no soul-sand slowdown
		ItemAttributeModifiers wad = WADERS.createAttributes(ArmorType.BOOTS)
			.withModifierAdded(Attributes.WATER_MOVEMENT_EFFICIENCY, new AttributeModifier(MirewickContent.id("waders_strider"), 2.0 / 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
			.withModifierAdded(Attributes.MOVEMENT_EFFICIENCY, new AttributeModifier(MirewickContent.id("waders_soul"), 1.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET);
		FEN_MARSHALS_WADERS = reg("mirewick_fen_marshals_waders", Item::new,
			lore(new Item.Properties().humanoidArmor(WADERS, ArmorType.BOOTS).attributes(wad).rarity(Rarity.EPIC), "mirewick_fen_marshals_waders", 3));
		// Sorrel: chest 6/2/0.1 dry; MirewickPassives adds +2 armor and +1 toughness while wet (8/3/0.1)
		DIKEWARDENS_BRIGANDINE = reg("mirewick_dikewardens_brigandine", Item::new,
			lore(new Item.Properties().humanoidArmor(BRIGANDINE, ArmorType.CHESTPLATE).rarity(Rarity.EPIC), "mirewick_dikewardens_brigandine", 3));
		// Tolliver: bow, 560 durability, enchantability 15
		FOWLERS_MARSHBOW = reg("mirewick_fowlers_marshbow", MarshbowItem::new,
			lore(new Item.Properties().durability(560).enchantable(15).repairable(WOOD_REPAIR).rarity(Rarity.EPIC), "mirewick_fowlers_marshbow", 2));
		// Ames: helmet 2/1/0, calling bonus (+10% potion duration, MirewickPassives)
		ALMONERS_COWL = reg("mirewick_almoners_cowl", Item::new,
			lore(new Item.Properties().humanoidArmor(COWL, ArmorType.HELMET).rarity(Rarity.EPIC), "mirewick_almoners_cowl", 2));
		UNIQUE.put("marrow", DEBT_COLLECTORS_LEGGINGS);
		UNIQUE.put("thwaite", FEN_MARSHALS_WADERS);
		UNIQUE.put("sorrel", DIKEWARDENS_BRIGANDINE);
		UNIQUE.put("tolliver", FOWLERS_MARSHBOW);
		UNIQUE.put("ames", ALMONERS_COWL);
		// the Chair table
		LANTERN_CORONET = reg("mirewick_lantern_coronet", Item::new,
			lore(new Item.Properties().humanoidArmor(CORONET, ArmorType.HELMET).rarity(Rarity.EPIC), "mirewick_lantern_coronet", 3));
		MARSH_LANTERN_STANDARD = reg("mirewick_marsh_lantern_standard", MarshStandardItem::new,
			lore(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "mirewick_marsh_lantern_standard", 1));
		// Dunstan Reeve's pike (20%): 6.5 damage, 1.5 speed, 1,400 durability, enchantability 15, +0.75 reach
		ItemAttributeModifiers pike = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, REBALANCE_RIVALS ? 6.5 : 5.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.5, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(MirewickContent.id("pike_reach"), 0.75, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
		REEDCUTTER_PIKE = reg("mirewick_reedcutter_pike", Item::new,
			lore(new Item.Properties().durability(1400).enchantable(15).repairable(IRON_REPAIR).attributes(pike).rarity(Rarity.RARE), "mirewick_reedcutter_pike", 1));
		TRIM_TEMPLATE = reg("mirewick_armor_trim_smithing_template", SmithingTemplateItem::createArmorTrimTemplate, new Item.Properties().rarity(Rarity.UNCOMMON));
		BANNER_PATTERN = reg("mirewick_banner_pattern", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, ctx -> ctx.lookupOrThrow(Registries.BANNER_PATTERN).getOrThrow(PATTERN_ITEM_TAG)));
		// the Lantern Shield blocks exactly like the vanilla shield: BLOCKS_ATTACKS (0.25 s raise, 90 percent from the front, 3 + damage durability,
		// axes disable it, bypassed by the same damage types), 336 durability, offhand, the same break sound. The use animation is the component's BLOCK pose.
		LANTERN_SHIELD = reg("mirewick_lantern_shield", Item::new, lore(new Item.Properties().stacksTo(1).durability(336).rarity(Rarity.UNCOMMON)
			.repairable(WOOD_REPAIR).equippableUnswappable(EquipmentSlot.OFFHAND)
			.delayedComponent(DataComponents.BLOCKS_ATTACKS, ctx -> new BlocksAttacks(0.25F, 1.0F,
				List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)), new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
				Optional.of(ctx.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)), Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
			.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK), "mirewick_lantern_shield", 1));
		OILCLOTH_DYE = reg("mirewick_oilcloth_dye", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "mirewick_oilcloth_dye", 1));

		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.rival_mirewick"))
			.icon(() -> new ItemStack(SCRIP))
			.displayItems((params, out) -> {
				ALL.forEach(out::accept);
				dev.elsewhere.rivals.mirewick.MirewickUnits.EGGS.forEach(out::accept);
			})
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MirewickContent.id("rival_mirewick"), tab);
	}

	/** A registered Mirewick item by path ({@code mirewick_scrip}). */
	public static Item byPath(String path) {
		return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("elsewhere", path));
	}
}
