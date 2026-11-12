package dev.elsewhere.petalwind;

import static dev.elsewhere.petalwind_glasswaste.Reg.item;

import dev.elsewhere.petalwind_glasswaste.GearStats;
import dev.elsewhere.petalwind_glasswaste.Reg;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;

/** Petalwind items: material, Zephyrite tools, Galeweave armor, signature items and Kazehana/Tempestra drops. */
public final class PetalwindItems {
	private PetalwindItems() {}

	public static Item ZEPHYR_FEATHER, ZEPHYRITE, PETAL_GLIDER, SKYHOOK, CLOUDBREAD, ZEPHYR_KEY, BLOSSOM_SEAL;
	public static Item ZEPHYRITE_SWORD, ZEPHYRITE_PICKAXE, ZEPHYRITE_AXE, ZEPHYRITE_SHOVEL, ZEPHYRITE_HOE;
	public static Item GALEWEAVE_HOOD, GALEWEAVE_ROBE, GALEWEAVE_LEGGINGS, GALEWEAVE_BOOTS;
	public static Item GALE_TRIM_TEMPLATE;
	public static Item PETALSTEP_SANDALS, KAZE_KATANA, ZEPHYR_WINGS, GALE_HEART, MUSIC_DISC_UPDRAFT;
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.JukeboxSong> UPDRAFT = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.JUKEBOX_SONG, Reg.id("updraft"));

	public static void init() {
		Reg.currentTab = "petalwind";
		ZEPHYR_FEATHER = item("zephyr_feather", new Item.Properties().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
		ZEPHYRITE = item("zephyrite");
		GALE_TRIM_TEMPLATE = item("gale_armor_trim_smithing_template", p -> net.minecraft.world.item.SmithingTemplateItem.createArmorTrimTemplate(p), new Item.Properties());
		BLOSSOM_SEAL = item("blossom_seal", new Item.Properties().rarity(Rarity.UNCOMMON).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
		ZEPHYR_KEY = item("zephyr_key");
		MUSIC_DISC_UPDRAFT = item("music_disc_updraft", new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(UPDRAFT));
		CLOUDBREAD = item("cloudbread", new Item.Properties().food(
			new FoodProperties.Builder().nutrition(GearStats.CLOUDBREAD_NUTRITION).saturationModifier(GearStats.CLOUDBREAD_SATURATION).build(),
			Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
				new MobEffectInstance(MobEffects.SLOW_FALLING, GearStats.CLOUDBREAD_EFFECT_TICKS, 0),
				new MobEffectInstance(MobEffects.JUMP_BOOST, GearStats.CLOUDBREAD_EFFECT_TICKS, 0)))).build()));
		PETAL_GLIDER = item("petal_glider", new Item.Properties().durability(GearStats.PETAL_GLIDER_DURABILITY).rarity(Rarity.UNCOMMON)
			.component(DataComponents.GLIDER, Unit.INSTANCE)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
				.setAsset(GearStats.asset("petal_glider")).setDamageOnHurt(false).build())
			.repairable(Items.FEATHER));
		SKYHOOK = item("skyhook", SkyhookItem::new, new Item.Properties().durability(GearStats.SKYHOOK_DURABILITY));

		ZEPHYRITE_SWORD = item("zephyrite_sword", new Item.Properties().sword(GearStats.ZEPHYRITE, 3.0F, -2.2F)
			.attributes(knockback(GearStats.weapon(GearStats.ZEPHYRITE_SWORD_DAMAGE, GearStats.ZEPHYRITE_SWORD_SPEED))));
		ZEPHYRITE_PICKAXE = item("zephyrite_pickaxe", new Item.Properties().pickaxe(GearStats.ZEPHYRITE, 1.0F, -2.6F));
		ZEPHYRITE_AXE = item("zephyrite_axe", new Item.Properties().axe(GearStats.ZEPHYRITE, 5.0F, -2.9F));
		ZEPHYRITE_SHOVEL = item("zephyrite_shovel", new Item.Properties().shovel(GearStats.ZEPHYRITE, 1.5F, -2.9F));
		ZEPHYRITE_HOE = item("zephyrite_hoe", new Item.Properties().hoe(GearStats.ZEPHYRITE, -3.0F, 0.0F));

		ArmorMaterial galeweave = GearStats.armor(GearStats.GALEWEAVE_DURABILITY, GearStats.GALEWEAVE_DEFENSE, GearStats.GALEWEAVE_TOUGHNESS, 0.0F,
			SoundEvents.ARMOR_EQUIP_LEATHER, "repairs_galeweave_armor", "galeweave");
		GALEWEAVE_HOOD = item("galeweave_hood", galeweave(galeweave, ArmorType.HELMET));
		GALEWEAVE_ROBE = item("galeweave_robe", galeweave(galeweave, ArmorType.CHESTPLATE));
		GALEWEAVE_LEGGINGS = item("galeweave_leggings", galeweave(galeweave, ArmorType.LEGGINGS));
		GALEWEAVE_BOOTS = item("galeweave_boots", galeweave(galeweave, ArmorType.BOOTS));

		// Boss drops (Kazehana: Tier I; Tempestra: Tier III).
		PETALSTEP_SANDALS = item("petalstep_sandals", new Item.Properties().rarity(Rarity.RARE).humanoidArmor(
			GearStats.armor(GearStats.GALEWEAVE_DURABILITY, GearStats.PETALSTEP_DEFENSE, GearStats.PETALSTEP_TOUGHNESS, 0.0F, SoundEvents.ARMOR_EQUIP_LEATHER,
				"repairs_galeweave_armor", "petalstep"), ArmorType.BOOTS));
		KAZE_KATANA = item("kaze_katana", KazeKatanaItem::new, new Item.Properties().rarity(Rarity.RARE).sword(GearStats.ZEPHYRITE, 3.0F, -2.4F)
			.attributes(GearStats.weapon(GearStats.KAZE_KATANA_DAMAGE, GearStats.KAZE_KATANA_SPEED)));
		ZEPHYR_WINGS = item("zephyr_wings", new Item.Properties().durability(GearStats.ZEPHYR_WINGS_DURABILITY).rarity(Rarity.EPIC)
			.component(DataComponents.GLIDER, Unit.INSTANCE)
			.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.CHEST).setEquipSound(SoundEvents.ARMOR_EQUIP_ELYTRA)
				.setAsset(GearStats.asset("zephyr_wings")).setDamageOnHurt(false).build())
			.attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
				new AttributeModifier(Reg.id("zephyr_wings_armor"), GearStats.ZEPHYR_WINGS_ARMOR, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.CHEST).build())
			.repairable(ZEPHYR_FEATHER));
		GALE_HEART = item("gale_heart", GaleHeartItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));
	}

	/** C33: every Galeweave piece takes 10% less fall damage (fall_damage_multiplier -0.1); the set adds the mid-air jump. */
	private static Item.Properties galeweave(ArmorMaterial m, ArmorType type) {
		return new Item.Properties().humanoidArmor(m, type).attributes(GearStats.armorAttrs(m, type, "galeweave_fall", Attributes.FALL_DAMAGE_MULTIPLIER,
			GearStats.GALEWEAVE_FALL_PER_PIECE));
	}

	private static ItemAttributeModifiers knockback(ItemAttributeModifiers base) {
		return GearStats.withExtra(base, Attributes.ATTACK_KNOCKBACK, "zephyrite_knockback", GearStats.ZEPHYRITE_KNOCKBACK, EquipmentSlotGroup.MAINHAND);
	}

	public static boolean isGaleweaveSet(LivingEntity entity) {
		return entity.getItemBySlot(EquipmentSlot.HEAD).is(GALEWEAVE_HOOD) && entity.getItemBySlot(EquipmentSlot.CHEST).is(GALEWEAVE_ROBE)
			&& entity.getItemBySlot(EquipmentSlot.LEGS).is(GALEWEAVE_LEGGINGS) && entity.getItemBySlot(EquipmentSlot.FEET).is(GALEWEAVE_BOOTS);
	}
}
