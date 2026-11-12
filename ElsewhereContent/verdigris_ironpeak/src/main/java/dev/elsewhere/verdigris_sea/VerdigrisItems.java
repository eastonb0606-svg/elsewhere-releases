package dev.elsewhere.verdigris_sea;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.AABB;

/** Verdigris Sea items: materials, signature items, Tidesteel tools, Diving Suit, and the drops of Verdigrim and Galvanox. */
public final class VerdigrisItems {
	public static final ResourceKey<JukeboxSong> LOW_TIDE_ENGINE = ResourceKey.create(Registries.JUKEBOX_SONG, Reg.id("low_tide_engine"));

	public static final Item VERDIGRIS_GEAR = item("verdigris_gear", new Item.Properties());
	public static final Item PEARLITE = item("pearlite", new Item.Properties());
	public static final Item RAY_LEATHER = item("ray_leather", new Item.Properties());
	public static final Item SCRAPED_PATINA = item("scraped_patina", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item TIDE_KEY = item("tide_key", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item BRINE_LUNG = item("brine_lung", new Item.Properties().food(
		new FoodProperties.Builder().nutrition(1).saturationModifier(0.5F).alwaysEdible().build(),
		Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
			new MobEffectInstance(MobEffects.WATER_BREATHING, GearStats.BRINE_LUNG_BREATHING),
			new MobEffectInstance(MobEffects.CONDUIT_POWER, GearStats.BRINE_LUNG_CONDUIT)))).build()));
	public static final Item MUSIC_DISC_LOW_TIDE_ENGINE = item("music_disc_low_tide_engine",
		new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(LOW_TIDE_ENGINE));

	public static final Item STORMCALLER_HARPOON = item("stormcaller_harpoon", p -> new StormcallerHarpoonItem(false, GearStats.HARPOON_COOLDOWN, p),
		new Item.Properties().rarity(Rarity.RARE).durability(400).enchantable(1)
			.attributes(TridentItem.createAttributes())
			.component(DataComponents.TOOL, TridentItem.createToolProperties()));
	public static final Item GALVANIC_TRIDENT = item("galvanic_trident", p -> new StormcallerHarpoonItem(true, GearStats.GALVANIC_COOLDOWN, p),
		new Item.Properties().rarity(Rarity.EPIC).durability(500).enchantable(15)
			.attributes(TridentItem.createAttributes())
			.component(DataComponents.TOOL, TridentItem.createToolProperties()));
	public static final Item ENGINEERS_LANTERN = item("engineers_lantern", new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
	public static final Item HYMNAL_HORN = item("hymnal_horn", p -> new HornItem(VerdigrisIronpeakSounds.HYMNAL_HORN, GearStats.HYMNAL_COOLDOWN, (level, player, stack) -> {
		AABB box = player.getBoundingBox().inflate(GearStats.HYMNAL_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && !(e instanceof Player) && e.isAlive())) {
			dev.elsewhere.verdigris_ironpeak.Fx.push(e, player.position(), 1.6, 0.35);
			if (e instanceof Mob mob) mob.setTarget(null);
		}
		return true;
	}, p), new Item.Properties().rarity(Rarity.RARE));

	public static final Item TIDESTEEL_SWORD = item("tidesteel_sword", new Item.Properties().sword(GearStats.TIDESTEEL, 3.0F, -2.4F));
	public static final Item TIDESTEEL_PICKAXE = item("tidesteel_pickaxe", tidesteel(new Item.Properties().pickaxe(GearStats.TIDESTEEL, 1.0F, -2.8F), 1.0F, -2.8F, false));
	public static final Item TIDESTEEL_AXE = item("tidesteel_axe", tidesteel(new Item.Properties().axe(GearStats.TIDESTEEL, 5.0F, -3.0F), 5.0F, -3.0F, false));
	public static final Item TIDESTEEL_SHOVEL = item("tidesteel_shovel", tidesteel(new Item.Properties().shovel(GearStats.TIDESTEEL, 1.5F, -3.0F), 1.5F, -3.0F, false));
	public static final Item TIDESTEEL_HOE = item("tidesteel_hoe", tidesteel(new Item.Properties().hoe(GearStats.TIDESTEEL, -3.0F, 0.0F), -3.0F, 0.0F, false));

	public static final Item TIDAL_TRIM_TEMPLATE = item("tidal_armor_trim_smithing_template", p -> net.minecraft.world.item.SmithingTemplateItem.createArmorTrimTemplate(p), new Item.Properties());
	public static final Item TIDEBRASS_INGOT = item("tidebrass_ingot", new Item.Properties().rarity(Rarity.UNCOMMON));
	public static final Item TIDEBRASS_UPGRADE_TEMPLATE = item("tidebrass_upgrade_smithing_template", new Item.Properties());
	/** The Tidebrass Diving Suit (ids keep the old diving_suit_* names; only the display name changed). Perks per BALANCE N1. */
	public static final Item DIVING_SUIT_HELMET = item("diving_suit_helmet", new Item.Properties().humanoidArmor(GearStats.DIVING_SUIT, ArmorType.HELMET)
		.attributes(GearStats.armor(GearStats.DIVING_SUIT, ArmorType.HELMET, Extra.add(Attributes.OXYGEN_BONUS, "tidebrass_oxygen", GearStats.TIDEBRASS_OXYGEN))));
	public static final Item DIVING_SUIT_CHESTPLATE = item("diving_suit_chestplate", new Item.Properties().humanoidArmor(GearStats.DIVING_SUIT, ArmorType.CHESTPLATE)
		.attributes(GearStats.armor(GearStats.DIVING_SUIT, ArmorType.CHESTPLATE, Extra.add(Attributes.OXYGEN_BONUS, "tidebrass_oxygen", GearStats.TIDEBRASS_OXYGEN))));
	public static final Item DIVING_SUIT_LEGGINGS = item("diving_suit_leggings", new Item.Properties().humanoidArmor(GearStats.DIVING_SUIT, ArmorType.LEGGINGS)
		.attributes(GearStats.armor(GearStats.DIVING_SUIT, ArmorType.LEGGINGS, Extra.add(Attributes.WATER_MOVEMENT_EFFICIENCY, "tidebrass_water", GearStats.TIDEBRASS_WATER_EFF))));
	public static final Item DIVING_SUIT_BOOTS = item("diving_suit_boots", new Item.Properties().humanoidArmor(GearStats.DIVING_SUIT, ArmorType.BOOTS)
		.attributes(GearStats.armor(GearStats.DIVING_SUIT, ArmorType.BOOTS, Extra.add(Attributes.WATER_MOVEMENT_EFFICIENCY, "tidebrass_water", GearStats.TIDEBRASS_WATER_EFF))));

	public static final Item DIVING_HELM = item("diving_helm", new Item.Properties().humanoidArmor(GearStats.DIVING_HELM, ArmorType.HELMET)
		.attributes(GearStats.armor(GearStats.DIVING_HELM, ArmorType.HELMET,
			Extra.add(Attributes.OXYGEN_BONUS, "diving_helm_oxygen", GearStats.DIVING_HELM_OXYGEN),
			Extra.add(Attributes.SUBMERGED_MINING_SPEED, "diving_helm_mining", GearStats.DIVING_HELM_SUBMERGED_MINING))));
	public static final Item VERDIGRIS_MITRE = item("verdigris_mitre", new Item.Properties().rarity(Rarity.EPIC).humanoidArmor(GearStats.VERDIGRIS_MITRE, ArmorType.HELMET)
		.attributes(GearStats.armor(GearStats.VERDIGRIS_MITRE, ArmorType.HELMET, Extra.add(Attributes.OXYGEN_BONUS, "mitre_oxygen", GearStats.MITRE_OXYGEN))));

	private VerdigrisItems() {}

	/** Tidesteel mining tools: full mining speed underwater (submerged_mining_speed while held). */
	private static Item.Properties tidesteel(Item.Properties p, float dmg, float speed, boolean unused) {
		ItemAttributeModifiers base = ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, dmg + GearStats.TIDESTEEL.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, speed, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.add(Attributes.SUBMERGED_MINING_SPEED, new AttributeModifier(Reg.id("tidesteel_underwater"), GearStats.TIDESTEEL_SUBMERGED_MINING, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
			.build();
		return p.attributes(base);
	}

	private static ItemAttributeModifiers withBonusDamage(ItemAttributeModifiers mods, double bonus) {
		return mods.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(Reg.id("harpoon_bonus"), bonus, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
	}

	public static void init() {}
}
