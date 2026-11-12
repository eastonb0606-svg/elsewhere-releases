package dev.elsewhere.ironpeak;

import static dev.elsewhere.verdigris_ironpeak.Reg.item;

import dev.elsewhere.verdigris_ironpeak.GearStats;
import dev.elsewhere.verdigris_ironpeak.GearStats.Extra;
import dev.elsewhere.verdigris_ironpeak.HornItem;
import dev.elsewhere.verdigris_ironpeak.Reg;
import dev.elsewhere.verdigris_ironpeak.VerdigrisIronpeakSounds;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorType;

/** Ironpeak items: Skyiron and its tools, Climber armor, the climbing kit, yak food, and the drops of Hrolf and Thurgrim. */
public final class IronpeakItems {
	public static final ResourceKey<JukeboxSong> THIN_AIR_SONG = ResourceKey.create(Registries.JUKEBOX_SONG, Reg.id("thin_air"));

	public static final Item SKYIRON = item("skyiron", new Item.Properties().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).rarity(Rarity.UNCOMMON));
	public static final Item ROC_FEATHER = item("roc_feather", new Item.Properties());
	public static final Item YAK_MEAT = item("yak_meat", new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()));
	public static final Item COOKED_YAK_MEAT = item("cooked_yak_meat", new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.75F).build()));
	public static final Item YAK_MILK = item("yak_milk", new Item.Properties().craftRemainder(Items.BUCKET)
		.component(DataComponents.CONSUMABLE, Consumables.MILK_BUCKET).usingConvertsTo(Items.BUCKET).stacksTo(1));
	public static final Item YAK_BUTTER_TEA = item("yak_butter_tea", ButterTeaItem::new, new Item.Properties().stacksTo(1).usingConvertsTo(Items.BOWL).food(
		new FoodProperties.Builder().nutrition(6).saturationModifier(8F / 12F).build(),
		Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.RESISTANCE, GearStats.TEA_RESISTANCE, 0))).build()));
	public static final Item IRONJAW_GLAND = item("ironjaw_gland", IronjawGlandItem::new, new Item.Properties().rarity(Rarity.EPIC)
		.food(new FoodProperties.Builder().nutrition(2).saturationModifier(0.5F).alwaysEdible().build()));
	public static final Item MUSIC_DISC_THIN_AIR = item("music_disc_thin_air", new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(THIN_AIR_SONG));

	public static final Item ICE_AXE = item("ice_axe", IceAxeItem::new, new Item.Properties().axe(ToolMaterial.DIAMOND, 5.0F, -3.0F)
		.attributes(ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, 5.0F + ToolMaterial.DIAMOND.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -3.0F, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.GRAVITY, new AttributeModifier(Reg.id("ice_axe_gravity"), GearStats.ICE_AXE_GRAVITY, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.SAFE_FALL_DISTANCE, new AttributeModifier(Reg.id("ice_axe_safe_fall"), GearStats.ICE_AXE_SAFE_FALL, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.STEP_HEIGHT, new AttributeModifier(Reg.id("ice_axe_step"), GearStats.ICE_AXE_STEP, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build()));
	public static final Item CRAMPONS = item("crampons", new Item.Properties().humanoidArmor(GearStats.CRAMPONS, ArmorType.BOOTS)
		.attributes(GearStats.armor(GearStats.CRAMPONS, ArmorType.BOOTS, Extra.add(Attributes.STEP_HEIGHT, "crampons_step", GearStats.CRAMPONS_STEP))));
	public static final Item AVALANCHE_HORN = item("avalanche_horn", p -> new HornItem(VerdigrisIronpeakSounds.AVALANCHE_HORN, GearStats.AVALANCHE_COOLDOWN, AvalancheHorn::blow, p),
		new Item.Properties().rarity(Rarity.RARE));
	public static final Item CHAMPIONS_BELT = item("champions_belt", new Item.Properties().rarity(Rarity.EPIC).humanoidArmor(GearStats.CHAMPIONS_BELT, ArmorType.LEGGINGS)
		.attributes(GearStats.armor(GearStats.CHAMPIONS_BELT, ArmorType.LEGGINGS, Extra.add(Attributes.ATTACK_DAMAGE, "belt_attack", GearStats.BELT_ATTACK))));
	public static final Item SUMMIT_CROWN = item("summit_crown", new Item.Properties().rarity(Rarity.EPIC).humanoidArmor(GearStats.SUMMIT_CROWN, ArmorType.HELMET)
		.attributes(GearStats.armor(GearStats.SUMMIT_CROWN, ArmorType.HELMET, Extra.add(Attributes.KNOCKBACK_RESISTANCE, "crown_kb", 0.05))));
	public static final Item KINGSPLITTER = item("kingsplitter", KingsplitterItem::new, new Item.Properties().rarity(Rarity.EPIC).durability(GearStats.KINGSPLITTER_DURABILITY)
		.axe(ToolMaterial.NETHERITE, GearStats.KINGSPLITTER_DAMAGE - 1 - ToolMaterial.NETHERITE.attackDamageBonus(), GearStats.KINGSPLITTER_SPEED - 4.0F));

	/** ARSENAL.md: non-magic archer bow (arrow speed x1.4, 1.4 s draw, projectile_pierce +1 by id), durability 768. */
	public static final Item ROC_FEATHER_LONGBOW = item("roc_feather_longbow", RocFeatherLongbowItem::new, new Item.Properties().durability(dev.elsewhere.verdigris_ironpeak.GearStats.LONGBOW_DURABILITY).enchantable(1));

	public static final Item SKYIRON_SWORD = item("skyiron_sword", new Item.Properties().sword(GearStats.SKYIRON, 3.0F, -2.4F));
	public static final Item SKYIRON_PICKAXE = item("skyiron_pickaxe", new Item.Properties().pickaxe(GearStats.SKYIRON, 1.0F, -2.8F));
	public static final Item SKYIRON_AXE = item("skyiron_axe", new Item.Properties().axe(GearStats.SKYIRON, 5.0F, -3.0F));
	public static final Item SKYIRON_SHOVEL = item("skyiron_shovel", new Item.Properties().shovel(GearStats.SKYIRON, 1.5F, -3.0F));
	public static final Item SKYIRON_HOE = item("skyiron_hoe", new Item.Properties().hoe(GearStats.SKYIRON, -3.5F, 0.0F));

	/** Sold by the Clan Quartermaster (2.0); the smithing recipes for the Climber armor line come with the endgame-sets work. */
	public static final Item CLIMBER_UPGRADE_TEMPLATE = item("climber_upgrade_smithing_template", new Item.Properties());
	public static final Item SUMMIT_TRIM_TEMPLATE = item("summit_armor_trim_smithing_template", p -> net.minecraft.world.item.SmithingTemplateItem.createArmorTrimTemplate(p), new Item.Properties());
	public static final Item SKYIRON_INGOT = item("skyiron_ingot", new Item.Properties().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true).rarity(Rarity.UNCOMMON));
	public static final Item CLIMBER_HELMET = item("climber_helmet", climber(ArmorType.HELMET));
	public static final Item CLIMBER_CHESTPLATE = item("climber_chestplate", climber(ArmorType.CHESTPLATE));
	public static final Item CLIMBER_LEGGINGS = item("climber_leggings", climber(ArmorType.LEGGINGS));
	public static final Item CLIMBER_BOOTS = item("climber_boots", climber(ArmorType.BOOTS));

	private IronpeakItems() {}

	/** BALANCE N1: helm none (Thin Air immunity is code), chest and boots safe_fall +3, legs step_height +0.5; every piece burns 25% longer (fur). */
	private static Item.Properties climber(ArmorType type) {
		Extra fur = Extra.add(Attributes.BURNING_TIME, "climber_fur", GearStats.CLIMBER_BURN_PER_PIECE);
		Extra perk = switch (type) {
			case CHESTPLATE, BOOTS -> Extra.add(Attributes.SAFE_FALL_DISTANCE, "climber_safe_fall", GearStats.CLIMBER_SAFE_FALL);
			case LEGGINGS -> Extra.add(Attributes.STEP_HEIGHT, "climber_step", GearStats.CLIMBER_STEP);
			default -> null;
		};
		return new Item.Properties().humanoidArmor(GearStats.CLIMBER, type)
			.attributes(perk == null ? GearStats.armor(GearStats.CLIMBER, type, fur) : GearStats.armor(GearStats.CLIMBER, type, fur, perk));
	}

	/** True for the helmets that lift the Thin Air penalties (hunger, Mining Fatigue): Climber Helmet and Summit Crown. */
	public static boolean thinAirProof(net.minecraft.world.item.ItemStack helmet) {
		return helmet.is(CLIMBER_HELMET) || helmet.is(SUMMIT_CROWN);
	}

	public static boolean isSkyironTool(Item item) {
		return item == SKYIRON_PICKAXE || item == SKYIRON_AXE || item == SKYIRON_SHOVEL || item == SKYIRON_HOE || item == SKYIRON_SWORD;
	}

	public static void init() {}
}
