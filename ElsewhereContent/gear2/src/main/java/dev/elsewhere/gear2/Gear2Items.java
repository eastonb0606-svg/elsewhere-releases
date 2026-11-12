package dev.elsewhere.gear2;

import com.mojang.serialization.Codec;
import dev.elsewhere.gear2.item.MirrorboltArbalest;
import dev.elsewhere.gear2.item.ProspectorsPendulum;
import dev.elsewhere.gear2.item.SteamAuger;
import dev.elsewhere.gear2.item.TermiteHiveLantern;
import dev.elsewhere.gear2.item.VoidTetherSpool;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.level.block.Block;

/** Every gear2 item. Ids are looked up first (registry-collision rule, as in arsenal). */
public final class Gear2Items {
	private Gear2Items() {}

	public static final List<Item> ALL = new ArrayList<>();
	public static final String COILMAIL_SET = "galvanic_coilmail", AMBERSTILL_SET = "amberstill_plate";

	public static Item COILMAIL_HELMET, COILMAIL_CHESTPLATE, COILMAIL_LEGGINGS, COILMAIL_BOOTS, GALVANIC_TEMPLATE;
	public static Item IRONCLAD_FLAIL, IRONCLAD_CHAIN, GALE_RAPIER, MIRRORBOLT_ARBALEST, HELIODOR_MIRROR_CORE;
	public static Item STEAM_AUGER, STEAM_AUGER_BIT, TERMITE_HIVE_LANTERN, TERMITE_QUEEN;
	public static Item SEERS_HOURGLASS, PROSPECTORS_PENDULUM, VOID_TETHER_SPOOL;
	public static Item AMBERSTILL_HELMET, AMBERSTILL_CHESTPLATE, AMBERSTILL_LEGGINGS, AMBERSTILL_BOOTS, AMBERSTILL_TEMPLATE;

	public static DataComponentType<Integer> AUGER_FUEL;
	public static DataComponentType<GlobalPos> TETHER_ANCHOR;

	public static final TagKey<Block> AUGER_FAST = blockTag("steam_auger_fast");
	public static final TagKey<Block> TERMITE_EDIBLE = blockTag("termite_edible");

	public static void init() {
		AUGER_FUEL = component("auger_fuel", Codec.INT, ByteBufCodecs.VAR_INT);
		TETHER_ANCHOR = component("tether_anchor", GlobalPos.CODEC, GlobalPos.STREAM_CODEC);
		HolderGetter<Block> blocks = BuiltInRegistries.acquireBootstrapRegistrationLookup(BuiltInRegistries.BLOCK);

		// ---- gear-armor-b Galvanic Coilmail
		ArmorMaterial coil = new ArmorMaterial(Gear2Stats.COILMAIL_DURABILITY, defense(Gear2Stats.COILMAIL_ARMOR), Gear2Stats.COILMAIL_ENCHANTABILITY,
			SoundEvents.ARMOR_EQUIP_CHAIN, Gear2Stats.COILMAIL_TOUGHNESS, Gear2Stats.COILMAIL_KB, tag("repairs_galvanic_coilmail"), asset(COILMAIL_SET));
		COILMAIL_HELMET = armor("galvanic_coilmail_helmet", coil, ArmorType.HELMET, COILMAIL_SET);
		COILMAIL_CHESTPLATE = armor("galvanic_coilmail_chestplate", coil, ArmorType.CHESTPLATE, COILMAIL_SET);
		COILMAIL_LEGGINGS = armor("galvanic_coilmail_leggings", coil, ArmorType.LEGGINGS, COILMAIL_SET);
		COILMAIL_BOOTS = armor("galvanic_coilmail_boots", coil, ArmorType.BOOTS, COILMAIL_SET);
		GALVANIC_TEMPLATE = item("galvanic_upgrade_smithing_template", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "galvanic_upgrade_smithing_template", 2));

		// ---- gear-weapon1-b Ironclad Flail (no Sweeping Edge: not in #swords / #enchantable/sweeping)
		ToolMaterial flailMat = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, Gear2Stats.FLAIL_DURABILITY, 8.0F, 0.0F, 10, tag("repairs_ironclad_flail"));
		IRONCLAD_FLAIL = item("ironclad_flail", Item::new, lore(new Item.Properties().sword(flailMat, Gear2Stats.FLAIL_DAMAGE - 1.0F, Gear2Stats.FLAIL_SPEED - 4.0F)
			.rarity(Rarity.RARE), "ironclad_flail", 3));
		IRONCLAD_CHAIN = item("ironclad_chain", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "ironclad_chain", 1));

		// ---- gear-weapon2-a Gale Rapier
		ToolMaterial rapierMat = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, Gear2Stats.RAPIER_DURABILITY, 8.0F, 0.0F, 12, tag("repairs_gale_rapier"));
		GALE_RAPIER = item("gale_rapier", Item::new, lore(new Item.Properties().sword(rapierMat, Gear2Stats.RAPIER_DAMAGE - 1.0F, Gear2Stats.RAPIER_SPEED - 4.0F)
			.rarity(Rarity.RARE), "gale_rapier", 3));

		// ---- gear-ranged-a Mirrorbolt Arbalest
		MIRRORBOLT_ARBALEST = item("mirrorbolt_arbalest", MirrorboltArbalest::new, lore(new Item.Properties().stacksTo(1).durability(Gear2Stats.ARBALEST_DURABILITY)
			.component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).enchantable(1).rarity(Rarity.RARE), "mirrorbolt_arbalest", 3));
		HELIODOR_MIRROR_CORE = item("heliodor_mirror_core", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "heliodor_mirror_core", 1));

		// ---- gear-drill-a Steam Auger: fast rule on stone/ores/deepslate, netherite pickaxe speed on other pickaxe blocks, diamond tier
		ToolMaterial augerMat = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, Gear2Stats.AUGER_DURABILITY, Gear2Stats.NETHERITE_PICK_SPEED, 0.0F, 10, tag("repairs_steam_auger"));
		Tool augerTool = new Tool(List.of(
			Tool.Rule.deniesDrops(blocks.getOrThrow(BlockTags.INCORRECT_FOR_DIAMOND_TOOL)),
			Tool.Rule.minesAndDrops(blocks.getOrThrow(AUGER_FAST), Gear2Stats.AUGER_SPEED),
			Tool.Rule.minesAndDrops(blocks.getOrThrow(BlockTags.MINEABLE_WITH_PICKAXE), Gear2Stats.NETHERITE_PICK_SPEED)), 1.0F, 1, true);
		STEAM_AUGER = item("steam_auger", SteamAuger::new, lore(new Item.Properties().pickaxe(augerMat, 4.0F, -2.8F)
			.component(DataComponents.TOOL, augerTool).component(AUGER_FUEL, 0)
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 4.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.BLOCK_INTERACTION_RANGE, new AttributeModifier(Gear2Content.id("steam_auger.reach"), Gear2Stats.AUGER_REACH_DELTA,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build())
			.rarity(Rarity.RARE), "steam_auger", 3));
		STEAM_AUGER_BIT = item("steam_auger_bit", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "steam_auger_bit", 1));

		// ---- gear-areaminer-b Termite Hive Lantern: only termite-edible blocks, at 1/3 netherite pickaxe speed
		Tool termiteTool = new Tool(List.of(Tool.Rule.minesAndDrops(blocks.getOrThrow(TERMITE_EDIBLE), Gear2Stats.TERMITE_SPEED)), 1.0F, 1, false);
		TERMITE_HIVE_LANTERN = item("termite_hive_lantern", TermiteHiveLantern::new, lore(new Item.Properties().stacksTo(1).durability(Gear2Stats.TERMITE_DURABILITY)
			.repairable(tag("repairs_termite_hive_lantern")).enchantable(10).component(DataComponents.TOOL, termiteTool).rarity(Rarity.RARE), "termite_hive_lantern", 3));
		TERMITE_QUEEN = item("termite_queen", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "termite_queen", 1));

		// ---- trinkets / utility
		SEERS_HOURGLASS = item("seers_hourglass", Item::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.RARE), "seers_hourglass", 3));
		PROSPECTORS_PENDULUM = item("prospectors_pendulum", ProspectorsPendulum::new, lore(new Item.Properties().durability(Gear2Stats.PENDULUM_DURABILITY)
			.rarity(Rarity.UNCOMMON), "prospectors_pendulum", 3));
		VOID_TETHER_SPOOL = item("void_tether_spool", VoidTetherSpool::new, lore(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "void_tether_spool", 3));

		// ---- gear-armor-a Amberstill Plate (phase 5 extra)
		ArmorMaterial amber = new ArmorMaterial(Gear2Stats.AMBERSTILL_DURABILITY, defense(Gear2Stats.AMBERSTILL_ARMOR), 15,
			SoundEvents.ARMOR_EQUIP_GOLD, Gear2Stats.AMBERSTILL_TOUGHNESS, Gear2Stats.AMBERSTILL_KB, tag("repairs_amberstill_plate"), asset("amberstill"));
		AMBERSTILL_HELMET = armor("amberstill_helmet", amber, ArmorType.HELMET, AMBERSTILL_SET);
		AMBERSTILL_CHESTPLATE = armor("amberstill_chestplate", amber, ArmorType.CHESTPLATE, AMBERSTILL_SET);
		AMBERSTILL_LEGGINGS = armor("amberstill_leggings", amber, ArmorType.LEGGINGS, AMBERSTILL_SET);
		AMBERSTILL_BOOTS = armor("amberstill_boots", amber, ArmorType.BOOTS, AMBERSTILL_SET);
		AMBERSTILL_TEMPLATE = item("amberstill_upgrade_smithing_template", Item::new, lore(new Item.Properties().rarity(Rarity.UNCOMMON), "amberstill_upgrade_smithing_template", 2));

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Gear2Content.id("gear2"), FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.elsewhere.gear2")).icon(() -> new ItemStack(IRONCLAD_FLAIL))
			.displayItems((params, out) -> {
				for (Item i : ALL) out.accept(i);
			}).build());
	}

	// ---------------------------------------------------------------- helpers

	public static TagKey<Item> tag(String path) {
		return TagKey.create(Registries.ITEM, Gear2Content.id(path));
	}

	static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, Gear2Content.id(path));
	}

	private static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Gear2Content.id(name));
	}

	private static Map<ArmorType, Integer> defense(int[] d) {
		return Map.of(ArmorType.HELMET, d[0], ArmorType.CHESTPLATE, d[1], ArmorType.LEGGINGS, d[2], ArmorType.BOOTS, d[3], ArmorType.BODY, d[1]);
	}

	private static Item armor(String name, ArmorMaterial m, ArmorType t, String set) {
		return item(name, Item::new, lore(new Item.Properties().humanoidArmor(m, t).rarity(Rarity.RARE), set, 3));
	}

	/** Grey/blue tooltip lines {@code item.elsewhere.<key>.lore1..n} (lore1 grey, the rest blue; last is the con in red). */
	static Item.Properties lore(Item.Properties p, String key, int lines) {
		List<Component> l = new ArrayList<>();
		for (int i = 1; i <= lines; i++) {
			ChatFormatting c = i == 1 ? ChatFormatting.GRAY : (i == lines && lines >= 3 ? ChatFormatting.RED : ChatFormatting.BLUE);
			l.add(Component.translatable("item.elsewhere." + key + ".lore" + i).withStyle(s -> s.withColor(c).withItalic(false)));
		}
		return p.component(DataComponents.LORE, new ItemLore(l));
	}

	private static <T> DataComponentType<T> component(String name, Codec<T> codec, net.minecraft.network.codec.StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, T> stream) {
		Identifier id = Gear2Content.id(name);
		@SuppressWarnings("unchecked")
		DataComponentType<T> existing = (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id);
		if (existing != null) return existing;
		return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, DataComponentType.<T>builder().persistent(codec).networkSynchronized(stream).build());
	}

	public static <I extends Item> Item item(String name, Function<Item.Properties, I> factory, Item.Properties props) {
		Identifier id = Gear2Content.id(name);
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
}
