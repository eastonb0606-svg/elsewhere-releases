package dev.elsewhere.glasswaste;

import static dev.elsewhere.petalwind_glasswaste.Reg.item;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.Reg;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;

/** Glasswaste items: material, Sunstone tools, Sunwright armor, signature items and Heliodor/Ankhara drops. */
public final class GlasswasteItems {
	private GlasswasteItems() {}

	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.JukeboxSong> ZENITH = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.JUKEBOX_SONG, Reg.id("zenith"));
	public static Item MUSIC_DISC_ZENITH, SUNGLASS_SHARD, SUNSTONE, BURNING_GLASS, CACTUS_WATER, SUNWRIGHT_VISOR, EEL_TOOTH, EEL_TOOTH_SPEAR;
	public static Item SUNWRIGHT_INGOT, SUNWRIGHT_TEMPLATE, SUNBURST_TRIM_TEMPLATE;
	public static Item SUNSTONE_SWORD, SUNSTONE_PICKAXE, SUNSTONE_AXE, SUNSTONE_SHOVEL, SUNSTONE_HOE;
	public static Item SUNWRIGHT_HELMET, SUNWRIGHT_CHESTPLATE, SUNWRIGHT_LEGGINGS, SUNWRIGHT_BOOTS;
	public static Item HELIOGRAPH_LENS, SUNWRIGHT_PLATE, GLASSFANG, SANDSWIMMER_SCALE, GIZZARD_STONE;

	public static void init() {
		Reg.currentTab = "glasswaste";
		MUSIC_DISC_ZENITH = item("music_disc_zenith", new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(ZENITH));
		SUNGLASS_SHARD = item("sunglass_shard", new Item.Properties().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
		SUNSTONE = item("sunstone");
		EEL_TOOTH = item("eel_tooth");
		SANDSWIMMER_SCALE = item("sandswimmer_scale", new Item.Properties().rarity(Rarity.UNCOMMON));
		BURNING_GLASS = item("burning_glass", BurningGlassItem::new, new Item.Properties().durability(GearStats.BURNING_GLASS_DURABILITY));
		CACTUS_WATER = item("cactus_water", CactusWaterItem::new, new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
			.component(DataComponents.CONSUMABLE, Consumables.defaultDrink().onConsume(new ApplyStatusEffectsConsumeEffect(
				new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GearStats.CACTUS_WATER_FIRE_RES_TICKS, 0))).build()));
		SUNWRIGHT_VISOR = item("sunwright_visor", new Item.Properties().rarity(Rarity.UNCOMMON).humanoidArmor(
			GearStats.armor(33, new int[] {0, 0, 0, GearStats.SUNWRIGHT_VISOR_ARMOR}, 2.0F, 0.0F, SoundEvents.ARMOR_EQUIP_GOLD, "repairs_sunwright_armor",
				"sunwright_visor"), ArmorType.HELMET));
		EEL_TOOTH_SPEAR = item("eel_tooth_spear", EelToothSpearItem::new,
			new Item.Properties().spear(ToolMaterial.IRON, 0.95F, 0.95F, 0.6F, 2.5F, 11.0F, 6.75F, 5.1F, 11.25F, 4.6F));

		SUNSTONE_SWORD = item("sunstone_sword", SunstoneToolItem::new, new Item.Properties().fireResistant().sword(GearStats.SUNSTONE, 3.0F, -2.4F));
		SUNSTONE_PICKAXE = item("sunstone_pickaxe", SunstoneToolItem::new, new Item.Properties().fireResistant().pickaxe(GearStats.SUNSTONE, 1.0F, -2.8F));
		SUNSTONE_AXE = item("sunstone_axe", SunstoneToolItem::new, new Item.Properties().fireResistant().axe(GearStats.SUNSTONE, 5.0F, -3.0F));
		SUNSTONE_SHOVEL = item("sunstone_shovel", SunstoneToolItem::new, new Item.Properties().fireResistant().shovel(GearStats.SUNSTONE, 1.5F, -3.0F));
		SUNSTONE_HOE = item("sunstone_hoe", SunstoneToolItem::new, new Item.Properties().fireResistant().hoe(GearStats.SUNSTONE, -3.0F, 0.0F));

		SUNWRIGHT_INGOT = item("sunwright_ingot", new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
		SUNWRIGHT_TEMPLATE = item("sunwright_upgrade_smithing_template", new Item.Properties().fireResistant().rarity(Rarity.UNCOMMON));
		SUNBURST_TRIM_TEMPLATE = item("sunburst_armor_trim_smithing_template", p -> net.minecraft.world.item.SmithingTemplateItem.createArmorTrimTemplate(p), new Item.Properties());

		ArmorMaterial sunwright = GearStats.armor(GearStats.SUNWRIGHT_DURABILITY, GearStats.SUNWRIGHT_DEFENSE, GearStats.SUNWRIGHT_TOUGHNESS, 0.0F,
			SoundEvents.ARMOR_EQUIP_GOLD, "repairs_sunwright_armor", "sunwright", GearStats.SUNWRIGHT_ENCHANTABILITY);
		SUNWRIGHT_HELMET = item("sunwright_helmet", new Item.Properties().fireResistant().humanoidArmor(sunwright, ArmorType.HELMET));
		SUNWRIGHT_CHESTPLATE = item("sunwright_chestplate", new Item.Properties().fireResistant().humanoidArmor(sunwright, ArmorType.CHESTPLATE)
			.attributes(GearStats.armorAttrs(sunwright, ArmorType.CHESTPLATE, "sunwright_burn", Attributes.BURNING_TIME, GearStats.SUNWRIGHT_CHEST_BURN)));
		SUNWRIGHT_LEGGINGS = item("sunwright_leggings", new Item.Properties().fireResistant().humanoidArmor(sunwright, ArmorType.LEGGINGS));
		SUNWRIGHT_BOOTS = item("sunwright_boots", new Item.Properties().fireResistant().humanoidArmor(sunwright, ArmorType.BOOTS));

		// Boss drops (Heliodor and Ankhara: Tier IV).
		HELIOGRAPH_LENS = item("heliograph_lens", HeliographLensItem::new, new Item.Properties().rarity(Rarity.EPIC).durability(256));
		ArmorMaterial plate = GearStats.armor(37, GearStats.SUNWRIGHT_PLATE_DEFENSE, GearStats.SUNWRIGHT_PLATE_TOUGHNESS, GearStats.SUNWRIGHT_PLATE_KNOCKBACK_RES,
			SoundEvents.ARMOR_EQUIP_GOLD, "repairs_sunwright_armor", "sunwright_plate", GearStats.SUNWRIGHT_ENCHANTABILITY);
		SUNWRIGHT_PLATE = item("sunwright_plate", new Item.Properties().rarity(Rarity.EPIC).fireResistant().humanoidArmor(plate, ArmorType.CHESTPLATE)
			.attributes(GearStats.armorAttrs(plate, ArmorType.CHESTPLATE, "sunwright_burn", Attributes.BURNING_TIME, GearStats.SUNWRIGHT_CHEST_BURN)));
		GLASSFANG = item("glassfang", GlassfangItem::new, new Item.Properties().rarity(Rarity.EPIC).sword(ToolMaterial.DIAMOND, 3.0F, -2.4F).durability(1000)
			.attributes(GearStats.weapon(GearStats.GLASSFANG_DAMAGE, GearStats.GLASSFANG_SPEED)));
		GIZZARD_STONE = item("gizzard_stone", new Item.Properties().rarity(Rarity.RARE).food(
			new FoodProperties.Builder().nutrition(GearStats.GIZZARD_STONE_NUTRITION).saturationModifier(0.5F).alwaysEdible().build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.SATURATION, 200, 0))).build()));
	}

	/** Number of Sunwright armor pieces worn (the Plate and the Visor count too). */
	public static int sunwrightPieces(LivingEntity entity) {
		int n = 0;
		Item head = entity.getItemBySlot(EquipmentSlot.HEAD).getItem();
		Item chest = entity.getItemBySlot(EquipmentSlot.CHEST).getItem();
		if (head == SUNWRIGHT_HELMET || head == SUNWRIGHT_VISOR) n++;
		if (chest == SUNWRIGHT_CHESTPLATE || chest == SUNWRIGHT_PLATE) n++;
		if (entity.getItemBySlot(EquipmentSlot.LEGS).is(SUNWRIGHT_LEGGINGS)) n++;
		if (entity.getItemBySlot(EquipmentSlot.FEET).is(SUNWRIGHT_BOOTS)) n++;
		return n;
	}
}
