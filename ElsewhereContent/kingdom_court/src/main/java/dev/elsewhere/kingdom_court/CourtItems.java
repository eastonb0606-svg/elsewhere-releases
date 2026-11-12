package dev.elsewhere.kingdom_court;

import dev.elsewhere.kingdom_court.item.AshgroveLongbowItem;
import dev.elsewhere.kingdom_court.item.DuelistsSabreItem;
import dev.elsewhere.kingdom_court.item.SovereignStandardItem;
import dev.elsewhere.kingdom_court.ruler.Ruler;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * One new item per Lord plus the King's drops (the Lord Seals are K-D's {@code lore_seal_*}). Every number is compared with netherite in
 * NOTES.md / STATUS (sidegrade or creative downgrade, per the user's note).
 */
public final class CourtItems {
	private CourtItems() {}

	public static final TagKey<Item> GILDED_REPAIR = TagKey.create(Registries.ITEM, CourtContent.id("court_gilded_repair"));
	public static final TagKey<Item> CLOTH_REPAIR = TagKey.create(Registries.ITEM, CourtContent.id("court_cloth_repair"));
	public static final TagKey<Item> STEEL_REPAIR = TagKey.create(Registries.ITEM, CourtContent.id("court_steel_repair"));

	static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, CourtContent.id(name));
	}

	// netherite for reference: durability base 37, boots 3 / chest 8 / helmet 3, toughness 3, knockback 0.1, enchant 15
	public static final ArmorMaterial WARMARSHAL = new ArmorMaterial(37, Map.of(ArmorType.BOOTS, 3, ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BODY, 11),
		15, SoundEvents.ARMOR_EQUIP_IRON, 2.0F, 0.0F, GILDED_REPAIR, asset("court_warmarshal"));
	public static final ArmorMaterial GATEWARDEN = new ArmorMaterial(37, Map.of(ArmorType.BOOTS, 3, ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BODY, 11),
		10, SoundEvents.ARMOR_EQUIP_NETHERITE, 4.0F, 0.2F, STEEL_REPAIR, asset("court_gatewarden"));
	public static final ArmorMaterial CHAPERON = new ArmorMaterial(25, Map.of(ArmorType.BOOTS, 1, ArmorType.HELMET, 2, ArmorType.CHESTPLATE, 3, ArmorType.LEGGINGS, 2, ArmorType.BODY, 3),
		25, SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 0.0F, CLOTH_REPAIR, asset("court_chaperon"));
	public static final ArmorMaterial CROWN = new ArmorMaterial(37, Map.of(ArmorType.BOOTS, 3, ArmorType.HELMET, 3, ArmorType.CHESTPLATE, 8, ArmorType.LEGGINGS, 6, ArmorType.BODY, 11),
		25, SoundEvents.ARMOR_EQUIP_GOLD, 3.0F, 0.0F, GILDED_REPAIR, asset("court_crown"));
	/** Duelist's Sabre: diamond durability, damage bonus 3 (netherite 4), enchantability 15. */
	public static final ToolMaterial DUELIST = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 8.0F, 3.0F, 15, STEEL_REPAIR);

	public static Item WARMARSHAL_SPURS, GATEWARDEN_PLATE, DUELISTS_SABRE, ASHGROVE_LONGBOW, CHANCELLORS_CHAPERON;
	public static Item CROWN_OF_ALDERMARK, SOVEREIGNS_STANDARD;
	/** K-D's Lord Seals ({@code elsewhere:lore_seal_<lord>}, tag {@code #elsewhere:lore_seals}); never registered here. */
	public static final TagKey<Item> LORE_SEALS = TagKey.create(Registries.ITEM, CourtContent.id("lore_seals"));
	public static final Map<Ruler, Item> SIGNATURE = new EnumMap<>(Ruler.class);
	public static final List<Item> ALL = new ArrayList<>();

	static Item.Properties lore(Item.Properties p, String id, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) l.add(Component.translatable("item.elsewhere." + id + ".desc" + i));
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	static <T extends Item> T reg(String id, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CourtContent.id(id));
		T item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
		ALL.add(item);
		return item;
	}

	static void init() {
		WARMARSHAL_SPURS = reg("court_warmarshal_spurs", Item::new,
			lore(new Item.Properties().humanoidArmor(WARMARSHAL, ArmorType.BOOTS).rarity(Rarity.EPIC), "court_warmarshal_spurs", 3));
		ItemAttributeModifiers plate = GATEWARDEN.createAttributes(ArmorType.CHESTPLATE).withModifierAdded(Attributes.MOVEMENT_SPEED,
			new AttributeModifier(CourtContent.id("gatewarden_weight"), -0.07, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.CHEST);
		GATEWARDEN_PLATE = reg("court_gatewarden_plate", Item::new,
			lore(new Item.Properties().humanoidArmor(GATEWARDEN, ArmorType.CHESTPLATE).attributes(plate).rarity(Rarity.EPIC), "court_gatewarden_plate", 3));
		DUELISTS_SABRE = reg("court_duelists_sabre", DuelistsSabreItem::new,
			lore(new Item.Properties().sword(DUELIST, 3.0F, -2.2F).rarity(Rarity.EPIC), "court_duelists_sabre", 3));
		ASHGROVE_LONGBOW = reg("court_ashgrove_longbow", AshgroveLongbowItem::new,
			lore(new Item.Properties().durability(576).enchantable(1).repairable(STEEL_REPAIR).rarity(Rarity.EPIC), "court_ashgrove_longbow", 3));
		CHANCELLORS_CHAPERON = reg("court_chancellors_chaperon", Item::new,
			lore(new Item.Properties().humanoidArmor(CHAPERON, ArmorType.HELMET).rarity(Rarity.EPIC), "court_chancellors_chaperon", 3));
		ItemAttributeModifiers crown = CROWN.createAttributes(ArmorType.HELMET).withModifierAdded(Attributes.ATTACK_SPEED,
			new AttributeModifier(CourtContent.id("crown_burden"), -0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.HEAD);
		CROWN_OF_ALDERMARK = reg("court_crown_of_aldermark", Item::new,
			lore(new Item.Properties().humanoidArmor(CROWN, ArmorType.HELMET).attributes(crown).rarity(Rarity.EPIC), "court_crown_of_aldermark", 3));
		SOVEREIGNS_STANDARD = reg("court_sovereigns_standard", SovereignStandardItem::new,
			lore(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "court_sovereigns_standard", 2));
		SIGNATURE.put(Ruler.MARSHAL, WARMARSHAL_SPURS);
		SIGNATURE.put(Ruler.CASTELLAN, GATEWARDEN_PLATE);
		SIGNATURE.put(Ruler.MASTER_AT_ARMS, DUELISTS_SABRE);
		SIGNATURE.put(Ruler.HUNTSMAN, ASHGROVE_LONGBOW);
		SIGNATURE.put(Ruler.CHANCELLOR, CHANCELLORS_CHAPERON);
		SIGNATURE.put(Ruler.KING, CROWN_OF_ALDERMARK);

		CreativeModeTab tab = FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.kingdom_court"))
			.icon(() -> new ItemStack(CROWN_OF_ALDERMARK))
			.displayItems((params, out) -> ALL.forEach(out::accept))
			.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CourtContent.id("kingdom_court"), tab);
	}

	/** An item registered by another lane (K-A's Aldermark Crowns, Oathkeeper's Seal), or null while it is absent. */
	public static Item optional(String path) {
		Identifier id = CourtContent.id(path);
		return BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.getValue(id) : null;
	}
}
