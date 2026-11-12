package dev.elsewhere.rivals.azmar;

import java.util.ArrayList;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
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
 * Azmar items. All ids start with {@code azmar_}. Headpieces are real equipment (equipment assets
 * {@code azmar_<name>}) with vanilla-equivalent armor values; the nemes and the ibis crest are boss drops.
 */
public final class AzmarItems {
	private AzmarItems() {}

	public static final List<Item> ALL = new ArrayList<>();
	public static final TagKey<Item> GILDED = TagKey.create(Registries.ITEM, AzmarContent.id("azmar_repair_gilded"));
	public static final TagKey<Item> LINEN = TagKey.create(Registries.ITEM, AzmarContent.id("azmar_repair_linen"));
	public static final TagKey<Item> COPPER = TagKey.create(Registries.ITEM, AzmarContent.id("azmar_repair_copper"));
	public static final TagKey<Item> KHOPESH_TAG = TagKey.create(Registries.ITEM, AzmarContent.id("azmar_khopesh"));
	public static final TagKey<Item> LORE_SEALS = TagKey.create(Registries.ITEM, AzmarContent.id("lore_seals"));

	static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, AzmarContent.id("azmar_" + name));
	}

	// vanilla-equivalent materials (helmet durability = base x 11, chestplate x 16, boots x 13)
	public static final ArmorMaterial NEMES = new ArmorMaterial(36, Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
		25, SoundEvents.ARMOR_EQUIP_GOLD, 2.0F, 0.1F, GILDED, asset("nemes"));
	public static final ArmorMaterial IBIS = new ArmorMaterial(25, Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 5, ArmorType.LEGGINGS, 4, ArmorType.BOOTS, 1, ArmorType.BODY, 5),
		25, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F, LINEN, asset("ibis"));
	public static final ArmorMaterial FALCON = new ArmorMaterial(5, Map.of(ArmorType.HELMET, 1, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BOOTS, 1, ArmorType.BODY, 3),
		15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, LINEN, asset("falcon"));
	public static final ArmorMaterial JACKAL = new ArmorMaterial(15, Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 5, ArmorType.LEGGINGS, 4, ArmorType.BOOTS, 1, ArmorType.BODY, 5),
		12, SoundEvents.ARMOR_EQUIP_CHAIN, 0.0F, 0.0F, GILDED, asset("jackal"));
	public static final ArmorMaterial SCARAB = new ArmorMaterial(15, Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2, ArmorType.BODY, 5),
		9, SoundEvents.ARMOR_EQUIP_IRON, 0.0F, 0.0F, COPPER, asset("scarab"));
	public static final ArmorMaterial SUNPLATE = new ArmorMaterial(15, Map.of(ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 6, ArmorType.LEGGINGS, 5, ArmorType.BOOTS, 2, ArmorType.BODY, 5),
		12, SoundEvents.ARMOR_EQUIP_GOLD, 0.0F, 0.0F, GILDED, asset("sunplate"));
	public static final ArmorMaterial SANDRUNNER = new ArmorMaterial(36, Map.of(ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BOOTS, 3, ArmorType.BODY, 11),
		15, SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, LINEN, asset("sandrunner"));

	public static Item DINAR, SEAL_SULTANA, SEAL_WAZIR, SEAL_FALCONER, SEAL_AMIR;
	public static Item NEMES_OF_THE_URAEUS, IBIS_CREST, FALCONERS_GAUNTLET, SANDRUNNER_SANDALS, WINGED_SCARAB_STANDARD;
	public static Item COPPER_KHOPESH, GILDED_KHOPESH, TWIN_KHOPESH, SCARAB_SPEAR, COMPOSITE_BOW, FIRE_POT;
	public static Item JACKAL_HELM, FALCON_HELM, HORNED_SCARAB_CROWN, SUNPLATE_CUIRASS, TRIM_TEMPLATE, BANNER_PATTERN;

	static Item.Properties lore(Item.Properties p, String id, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) l.add(Component.translatable("item.elsewhere." + id + ".desc" + i));
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	static <T extends Item> T reg(String id, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AzmarContent.id(id));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	static void init() {
		if (DINAR != null) return;
		DINAR = reg("azmar_dinar", Item::new, lore(new Item.Properties(), "azmar_dinar", 2));
		SEAL_SULTANA = reg("azmar_seal_sultana", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "azmar_seal_sultana", 1));
		SEAL_WAZIR = reg("azmar_seal_wazir", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "azmar_seal_wazir", 1));
		SEAL_FALCONER = reg("azmar_seal_falconer", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "azmar_seal_falconer", 1));
		SEAL_AMIR = reg("azmar_seal_amir", Item::new, lore(new Item.Properties().stacksTo(16).rarity(Rarity.RARE), "azmar_seal_amir", 1));

		// Nemes of the Uraeus: helmet 3 / 2 / 0.1, 396 durability (36 x 11), enchantability 25
		NEMES_OF_THE_URAEUS = reg("azmar_nemes_of_the_uraeus", Item::new,
			lore(new Item.Properties().humanoidArmor(NEMES, ArmorType.HELMET).rarity(Rarity.EPIC), "azmar_nemes_of_the_uraeus", 3));
		// Ibis Crest: helmet 2 / 1 / 0, 275 durability (25 x 11), enchantability 25
		IBIS_CREST = reg("azmar_ibis_crest", Item::new, lore(new Item.Properties().humanoidArmor(IBIS, ArmorType.HELMET).rarity(Rarity.EPIC), "azmar_ibis_crest", 3));
		FALCONERS_GAUNTLET = reg("azmar_falconers_gauntlet", GauntletItem::new,
			lore(new Item.Properties().stacksTo(1).durability(400).rarity(Rarity.EPIC), "azmar_falconers_gauntlet", 2));
		// Sandrunner Sandals: boots 3 / 2 / 0, 468 durability (36 x 13), enchantability 15, fall damage -2 (safe fall +2)
		ItemAttributeModifiers sandals = SANDRUNNER.createAttributes(ArmorType.BOOTS).withModifierAdded(Attributes.SAFE_FALL_DISTANCE,
			new AttributeModifier(AzmarContent.id("sandrunner_fall"), 2.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET);
		SANDRUNNER_SANDALS = reg("azmar_sandrunner_sandals", Item::new,
			lore(new Item.Properties().humanoidArmor(SANDRUNNER, ArmorType.BOOTS).attributes(sandals).rarity(Rarity.EPIC), "azmar_sandrunner_sandals", 2));
		WINGED_SCARAB_STANDARD = reg("azmar_winged_scarab_standard", StandardItem::new,
			lore(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "azmar_winged_scarab_standard", 2));

		COPPER_KHOPESH = reg("azmar_copper_khopesh", Item::new, lore(new Item.Properties().sword(ToolMaterial.COPPER, 3.0F, -2.4F), "azmar_copper_khopesh", 2));
		GILDED_KHOPESH = reg("azmar_gilded_khopesh", Item::new, lore(new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.4F).rarity(Rarity.UNCOMMON), "azmar_gilded_khopesh", 2));
		TWIN_KHOPESH = reg("azmar_twin_khopesh", Item::new, lore(new Item.Properties().sword(ToolMaterial.IRON, 3.0F, -2.2F).rarity(Rarity.RARE), "azmar_twin_khopesh", 2));
		SCARAB_SPEAR = reg("azmar_scarab_spear", Item::new, lore(new Item.Properties().spear(ToolMaterial.DIAMOND, 1.05F, 1.075F, 0.5F, 3.0F, 10.0F, 6.5F, 5.1F, 10.0F, 4.6F), "azmar_scarab_spear", 1));
		COMPOSITE_BOW = reg("azmar_composite_bow", BowItem::new, lore(new Item.Properties().durability(384).enchantable(1), "azmar_composite_bow", 1));
		FIRE_POT = reg("azmar_fire_pot", FirePotItem::new, lore(new Item.Properties().stacksTo(16), "azmar_fire_pot", 2));

		JACKAL_HELM = reg("azmar_jackal_helm", Item::new, lore(new Item.Properties().humanoidArmor(JACKAL, ArmorType.HELMET), "azmar_jackal_helm", 2));
		FALCON_HELM = reg("azmar_falcon_helm", Item::new, lore(new Item.Properties().humanoidArmor(FALCON, ArmorType.HELMET), "azmar_falcon_helm", 2));
		HORNED_SCARAB_CROWN = reg("azmar_horned_scarab_crown", Item::new, lore(new Item.Properties().humanoidArmor(SCARAB, ArmorType.HELMET).rarity(Rarity.UNCOMMON), "azmar_horned_scarab_crown", 2));
		SUNPLATE_CUIRASS = reg("azmar_sunplate_cuirass", Item::new, lore(new Item.Properties().humanoidArmor(SUNPLATE, ArmorType.CHESTPLATE).rarity(Rarity.RARE), "azmar_sunplate_cuirass", 2));
		TRIM_TEMPLATE = reg("azmar_armor_trim_smithing_template", SmithingTemplateItem::createArmorTrimTemplate, new Item.Properties().rarity(Rarity.UNCOMMON));
		BANNER_PATTERN = reg("azmar_banner_pattern", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.delayedComponent(DataComponents.PROVIDES_BANNER_PATTERNS, ctx -> ctx.lookupOrThrow(Registries.BANNER_PATTERN)
				.getOrThrow(TagKey.create(Registries.BANNER_PATTERN, AzmarContent.id("pattern_item/azmar")))));

		CreativeModeTab tab = FabricCreativeModeTab.builder().title(Component.translatable("itemGroup.elsewhere.azmar"))
			.icon(() -> new ItemStack(COPPER_KHOPESH)).displayItems((params, out) -> ALL.forEach(out::accept)).build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, AzmarContent.id("azmar"), tab);
	}
}
