package dev.elsewhere.arsenal;

import dev.elsewhere.arsenal.spell.CasterItems;
import dev.elsewhere.arsenal.spell.GlimmerTonicItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.ChatFormatting;

/** Every arsenal item (ARSENAL.md §4). Shared ids are looked up first (registry-collision rule). */
public final class ArsenalItems {
	private ArsenalItems() {}

	public static final List<Item> ALL = new ArrayList<>();
	public static Item MIRRORGLASS_PRISM, HUSHWARD_BELL;
	public static Item AMETHYST_WAND, SUNLANCE_STAFF, STORMCOIL_STAFF, SCULK_RESONATOR;
	public static Item MOONSILK_HOOD, MOONSILK_ROBE, MOONSILK_LEGGINGS, MOONSILK_BOOTS;
	public static Item LINED_MOONSILK_HOOD, LINED_MOONSILK_ROBE, LINED_MOONSILK_LEGGINGS, LINED_MOONSILK_BOOTS;
	public static Item STORMSAGE_MANTLE, CANTORS_DIADEM, GLIMMERSTONE_FOCUS, GLIMMER_MOTE, CLOCKWORK_REPEATER, GLIMMER_TONIC, STRONG_GLIMMER_TONIC, MOONSILK_THREAD;

	public static void init() {
		MIRRORGLASS_PRISM = item("mirrorglass_prism", dev.elsewhere.arsenal.spell.GearV2.MirrorglassPrism::new,
			caster(dev.elsewhere.arsenal.spell.GearV2.PRISM_DURABILITY).repairable(tag("repairs_mirrorglass_prism")).rarity(Rarity.RARE));
		HUSHWARD_BELL = item("hushward_bell", dev.elsewhere.arsenal.spell.GearV2.HushwardBell::new,
			caster(dev.elsewhere.arsenal.spell.GearV2.BELL_DURABILITY).repairable(Items.ECHO_SHARD).rarity(Rarity.RARE));
		AMETHYST_WAND = item("amethyst_wand", CasterItems.AmethystWand::new, caster(ArsenalStats.WAND_DURABILITY).repairable(Items.AMETHYST_SHARD));
		SUNLANCE_STAFF = item("sunlance_staff", CasterItems.SunlanceStaff::new,
			caster(ArsenalStats.SUNLANCE_DURABILITY).repairable(tag("repairs_sunlance_staff")).rarity(Rarity.UNCOMMON));
		STORMCOIL_STAFF = item("stormcoil_staff", CasterItems.StormcoilStaff::new,
			caster(ArsenalStats.STORMCOIL_DURABILITY).repairable(tag("repairs_stormcoil_staff")).rarity(Rarity.UNCOMMON));
		SCULK_RESONATOR = item("sculk_resonator", CasterItems.SculkResonator::new,
			caster(ArsenalStats.RESONATOR_DURABILITY).repairable(Items.ECHO_SHARD).rarity(Rarity.RARE));

		ArmorMaterial moonsilk = new ArmorMaterial(ArsenalStats.MOONSILK_DURABILITY, defense(3, 8, 6, 3), ArsenalStats.MOONSILK_ENCHANTABILITY,
			SoundEvents.ARMOR_EQUIP_LEATHER, ArsenalStats.MOONSILK_TOUGHNESS, 0, tag("repairs_moonsilk_armor"), asset("moonsilk"));
		ArmorMaterial lined = new ArmorMaterial(ArsenalStats.LINED_DURABILITY, defense(3, 8, 6, 3), ArsenalStats.MOONSILK_ENCHANTABILITY,
			SoundEvents.ARMOR_EQUIP_NETHERITE, ArsenalStats.LINED_TOUGHNESS, ArsenalStats.LINED_KB, tag("repairs_moonsilk_armor"), asset("lined_moonsilk"));
		String[] names = {"hood", "robe", "leggings", "boots"};
		ArmorType[] types = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
		Item[] plain = new Item[4], lin = new Item[4];
		for (int i = 0; i < 4; i++) {
			plain[i] = armor("moonsilk_" + names[i], moonsilk, types[i], perks(moonsilk, types[i], "moonsilk_" + names[i]), Rarity.UNCOMMON);
		}
		for (int i = 0; i < 4; i++) {
			lin[i] = armor("lined_moonsilk_" + names[i], lined, types[i], perks(lined, types[i], "lined_moonsilk_" + names[i]), Rarity.RARE);
		}
		MOONSILK_HOOD = plain[0]; MOONSILK_ROBE = plain[1]; MOONSILK_LEGGINGS = plain[2]; MOONSILK_BOOTS = plain[3];
		LINED_MOONSILK_HOOD = lin[0]; LINED_MOONSILK_ROBE = lin[1]; LINED_MOONSILK_LEGGINGS = lin[2]; LINED_MOONSILK_BOOTS = lin[3];

		ArmorMaterial mantle = new ArmorMaterial(ArsenalStats.MOONSILK_DURABILITY, defense(3, 8, 6, 3), ArsenalStats.MOONSILK_ENCHANTABILITY,
			SoundEvents.ARMOR_EQUIP_ELYTRA, 2, 0, tag("repairs_stormsage_mantle"), asset("stormsage_mantle"));
		STORMSAGE_MANTLE = armor("stormsage_mantle", mantle, ArmorType.CHESTPLATE,
			casterPiece(mantle.createAttributes(ArmorType.CHESTPLATE), ArmorType.CHESTPLATE, "stormsage_mantle")
				.withModifierAdded(ArsenalAttributes.MAX_GLIMMER, mod("stormsage_mantle.max", ArsenalStats.MANTLE_MAX), slot(ArmorType.CHESTPLATE)),
			Rarity.EPIC);
		ArmorMaterial diadem = new ArmorMaterial(ArsenalStats.MOONSILK_DURABILITY, defense(3, 8, 6, 3), ArsenalStats.MOONSILK_ENCHANTABILITY,
			SoundEvents.ARMOR_EQUIP_GOLD, ArsenalStats.DIADEM_TOUGHNESS, 0, tag("repairs_cantors_diadem"), asset("cantors_diadem"));
		CANTORS_DIADEM = armor("cantors_diadem", diadem, ArmorType.HELMET,
			casterPiece(diadem.createAttributes(ArmorType.HELMET), ArmorType.HELMET, "cantors_diadem")
				.withModifierAdded(ArsenalAttributes.MAX_GLIMMER, mod("cantors_diadem.max", ArsenalStats.DIADEM_MAX), slot(ArmorType.HELMET))
				.withModifierAdded(ArsenalAttributes.SPELL_COST, mod("cantors_diadem.cost", ArsenalStats.DIADEM_COST), slot(ArmorType.HELMET))
				.withModifierAdded(ArsenalAttributes.SPELL_POWER, mod("cantors_diadem.power", ArsenalStats.DIADEM_POWER), slot(ArmorType.HELMET)),
			Rarity.EPIC);

		GLIMMERSTONE_FOCUS = item("glimmerstone_focus", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.LORE, new ItemLore(List.of(
				Component.translatable("item.elsewhere.glimmerstone_focus.lore1").withStyle(s -> s.withColor(ChatFormatting.GRAY).withItalic(false)),
				Component.translatable("item.elsewhere.glimmerstone_focus.lore2").withStyle(s -> s.withColor(ChatFormatting.BLUE).withItalic(false)),
				Component.translatable("item.elsewhere.glimmerstone_focus.lore3").withStyle(s -> s.withColor(ChatFormatting.BLUE).withItalic(false))))));
		GLIMMER_TONIC = item("glimmer_tonic", p -> new GlimmerTonicItem(p, ArsenalStats.TONIC_RESTORE), tonic());
		STRONG_GLIMMER_TONIC = item("strong_glimmer_tonic", p -> new GlimmerTonicItem(p, ArsenalStats.STRONG_TONIC_RESTORE), tonic());
		MOONSILK_THREAD = item("moonsilk_thread", Item::new, new Item.Properties());
		GLIMMER_MOTE = item("glimmer_mote", Item::new, new Item.Properties());
		CLOCKWORK_REPEATER = item("clockwork_repeater", dev.elsewhere.arsenal.spell.RepeaterItem::new, new Item.Properties().durability(520).rarity(net.minecraft.world.item.Rarity.UNCOMMON));

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, ArsenalContent.id("arsenal"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.arsenal")).icon(() -> new ItemStack(AMETHYST_WAND))
			.displayItems((params, out) -> {
				for (Item i : ALL) out.accept(i);
				out.accept(PotionContents.createItemStack(Items.POTION, ArsenalEffects.CLARITY_DRAUGHT));
				out.accept(PotionContents.createItemStack(Items.POTION, ArsenalEffects.LONG_CLARITY_DRAUGHT));
			}).build());
	}

	// ---------------------------------------------------------------- helpers

	private static Item.Properties caster(int durability) {
		return new Item.Properties().durability(durability).enchantable(ArsenalStats.CASTER_ENCHANTABILITY);
	}

	private static Item.Properties tonic() {
		return new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().consumeSeconds(ArsenalStats.TONIC_DRINK_TICKS / 20F).build());
	}

	public static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, ArsenalContent.id(path));
	}

	private static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, ArsenalContent.id(name));
	}

	private static Map<ArmorType, Integer> defense(int head, int chest, int legs, int feet) {
		return Map.of(ArmorType.HELMET, head, ArmorType.CHESTPLATE, chest, ArmorType.LEGGINGS, legs, ArmorType.BOOTS, feet, ArmorType.BODY, chest);
	}

	private static EquipmentSlotGroup slot(ArmorType t) {
		return EquipmentSlotGroup.bySlot(t.getSlot());
	}

	private static AttributeModifier mod(String id, double amount) {
		return new AttributeModifier(ArsenalContent.id(id), amount, AttributeModifier.Operation.ADD_VALUE);
	}

	/** Caster-piece con (ARSENAL §3b): melee −15% (arrows are scaled in AttributeHooks). */
	private static ItemAttributeModifiers casterPiece(ItemAttributeModifiers base, ArmorType t, String name) {
		if (Overhaul3.CALLINGS) return base; // 3.0: class armor no longer taxes melee/arrows (GEAR.md A1)
		return base.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(ArsenalContent.id(name + ".caster_melee"),
			ArsenalStats.CASTER_PIECE_MELEE_ARROW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot(t));
	}

	private static ItemAttributeModifiers perks(ArmorMaterial m, ArmorType t, String name) {
		ItemAttributeModifiers mods = casterPiece(m.createAttributes(t), t, name);
		EquipmentSlotGroup g = slot(t);
		switch (t) {
			case HELMET -> mods = mods.withModifierAdded(ArsenalAttributes.MAX_GLIMMER, mod(name + ".max", ArsenalStats.HOOD_MAX), g)
				.withModifierAdded(ArsenalAttributes.SPELL_COST, mod(name + ".cost", ArsenalStats.HOOD_COST), g);
			case CHESTPLATE -> mods = mods.withModifierAdded(ArsenalAttributes.MAX_GLIMMER, mod(name + ".max", ArsenalStats.ROBE_MAX), g)
				.withModifierAdded(ArsenalAttributes.SPELL_POWER, mod(name + ".power", ArsenalStats.ROBE_POWER), g);
			case LEGGINGS -> mods = mods.withModifierAdded(ArsenalAttributes.GLIMMER_REGEN, mod(name + ".regen", ArsenalStats.LEGS_REGEN), g);
			case BOOTS -> mods = mods.withModifierAdded(ArsenalAttributes.GLIMMER_REGEN, mod(name + ".regen", ArsenalStats.BOOTS_REGEN), g)
				.withModifierAdded(ArsenalAttributes.PROJECTILE_PIERCE, mod(name + ".pierce", ArsenalStats.BOOTS_PIERCE), g);
			default -> {}
		}
		return mods;
	}

	private static Item armor(String name, ArmorMaterial m, ArmorType t, ItemAttributeModifiers mods, Rarity r) {
		return item(name, Item::new, new Item.Properties().humanoidArmor(m, t).attributes(mods).rarity(r));
	}

	public static <I extends Item> Item item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
		Identifier id = ArsenalContent.id(name);
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

	static Holder<Attribute> unused() {
		return null;
	}
}
