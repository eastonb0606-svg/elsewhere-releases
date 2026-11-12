package dev.elsewhere.registry;

import dev.elsewhere.Elsewhere;
import dev.elsewhere.ElsewhereConfig;
import dev.elsewhere.core.wayfinder.AttunedWayfinderItem;
import dev.elsewhere.core.wayfinder.FrayedWayfinderItem;
import dev.elsewhere.core.wayfinder.WayfinderItem;
import dev.elsewhere.palehollow.PaleHollowDimension;
import dev.elsewhere.palehollow.item.HeartwoodEffigyItem;
import dev.elsewhere.palehollow.item.HeartwoodGreatbowItem;
import dev.elsewhere.palehollow.item.MoonEyeItem;
import dev.elsewhere.palehollow.item.MoonringSeedItem;
import dev.elsewhere.palehollow.item.RootAttackersEffect;
import dev.elsewhere.palehollow.item.UnblinkingEyeItem;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;

public final class ModItems {
	/** Every Wayfinder shares one cooldown group, so you cannot chain different attunements. */
	public static final UseCooldown WAYFINDER_COOLDOWN = new UseCooldown(
		(float)ElsewhereConfig.get().wayfinders.cooldownSeconds, Optional.of(Elsewhere.id("wayfinder")));

	// ---- core
	public static final Item WAYFINDER = register("wayfinder", WayfinderItem::new, new Item.Properties().stacksTo(1));
	public static final Item WAYFINDER_PALE_HOLLOW = register("wayfinder_pale_hollow",
		p -> new AttunedWayfinderItem(p, PaleHollowDimension.LEVEL, PaleHollowDimension.COLOR),
		new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON).component(DataComponents.USE_COOLDOWN, WAYFINDER_COOLDOWN));
	public static final Item FRAYED_WAYFINDER = register("frayed_wayfinder", FrayedWayfinderItem::new,
		new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// ---- Pale Hollow materials and food
	public static final Item HEARTWOOD_SHARD = register("heartwood_shard", Item::new,
		new Item.Properties().rarity(Rarity.UNCOMMON).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true));
	public static final Item PALE_BARK = register("pale_bark", Item::new, new Item.Properties());
	public static final Item PALE_SAP = register("pale_sap", Item::new, new Item.Properties().stacksTo(16).usingConvertsTo(Items.GLASS_BOTTLE)
		.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.33F).alwaysEdible().build(),
			Consumable.builder().consumeSeconds(1.6F).animation(ItemUseAnimation.DRINK).sound(SoundEvents.HONEY_DRINK).hasConsumeParticles(false)
				.onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
					new MobEffectInstance(MobEffects.NIGHT_VISION, 90 * 20, 0),
					new MobEffectInstance(MobEffects.SLOWNESS, 10 * 20, 0))))
				.build()));
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.JukeboxSong> PALE_LIGHT = net.minecraft.resources.ResourceKey.create(
		net.minecraft.core.registries.Registries.JUKEBOX_SONG, Elsewhere.id("pale_light"));
	public static final Item MUSIC_DISC_PALE_LIGHT = register("music_disc_pale_light", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(PALE_LIGHT));
	public static final Item UNBLINKING_EYE = register("unblinking_eye", UnblinkingEyeItem::new,
		new Item.Properties().durability(16).rarity(Rarity.UNCOMMON).useCooldown(30.0F));

	// ---- boss drops
	/** Mother Creak. Weaker than a totem (no absorption or fire resistance) but roots whoever was attacking you. */
	public static final Item RESIN_HEART_LOCKET = register("resin_heart_locket", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
		.component(DataComponents.DEATH_PROTECTION, new DeathProtection(List.of(
			new ClearAllStatusEffectsConsumeEffect(),
			new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 1))),
			new RootAttackersEffect(dev.elsewhere.palehollow.GearStats.LOCKET_ROOT_RADIUS, dev.elsewhere.palehollow.GearStats.LOCKET_ROOT_TICKS)))));
	/** Mother Creak. A bow (iron-tier durability) whose arrows root what they hit for 1 s. */
	public static final Item HEARTWOOD_GREATBOW = register("heartwood_greatbow", HeartwoodGreatbowItem::new,
		new Item.Properties().durability(dev.elsewhere.palehollow.GearStats.GREATBOW_DURABILITY).enchantable(1).rarity(Rarity.RARE).repairable(PALE_BARK));
	/** Selenne. Diamond-helmet stats (3 armor, 2 toughness) plus a perk: endermen, creakings and gaze attacks ignore your look. */
	public static final Item EYEBLOSSOM_CIRCLET = register("eyeblossom_circlet", Item::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)
		.component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.HEAD).setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).build())
		.attributes(ItemAttributeModifiers.builder()
			.add(Attributes.ARMOR, new AttributeModifier(Elsewhere.id("circlet_armor"), 3.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
			.add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Elsewhere.id("circlet_toughness"), 2.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HEAD)
			.build()));
	/** Selenne. Marks the mob you look at with Glowing for 10 s. */
	public static final Item MOON_EYE = register("moon_eye", MoonEyeItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.RARE).useCooldown(dev.elsewhere.palehollow.GearStats.MOON_EYE_COOLDOWN_SECONDS));

	// ---- summons
	public static final Item HEARTWOOD_EFFIGY = register("heartwood_effigy", HeartwoodEffigyItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));
	public static final Item MOONRING_SEED = register("moonring_seed", MoonringSeedItem::new, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

	// ---- spawn eggs
	public static final Item CREAKING_HUSK_SPAWN_EGG = register("creaking_husk_spawn_egg", SpawnEggItem::new,
		new Item.Properties().spawnEgg(ModEntities.CREAKING_HUSK));
	public static final Item EYEBLOSSOM_WISP_SPAWN_EGG = register("eyeblossom_wisp_spawn_egg", SpawnEggItem::new,
		new Item.Properties().spawnEgg(ModEntities.EYEBLOSSOM_WISP));


	// ---- 2.0 Pale Hollow (polish-pale-hollow)
	public static final net.minecraft.tags.TagKey<Item> AMBER_REPAIR = net.minecraft.tags.TagKey.create(Registries.ITEM, Elsewhere.id("repairs_amber"));
	public static final Item AMBER = register("amber", Item::new, new Item.Properties());
	public static final Item STAG_ANTLER = register("stag_antler", Item::new, new Item.Properties());
	public static final net.minecraft.world.item.ToolMaterial HEARTWOOD = new net.minecraft.world.item.ToolMaterial(
		net.minecraft.tags.BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1561, 8.0F, 3.0F, 14, AMBER_REPAIR);
	public static final Item HEARTWOOD_SWORD = register("heartwood_sword", Item::new, new Item.Properties().sword(HEARTWOOD, 3.0F, -2.4F));
	public static final Item HEARTWOOD_PICKAXE = register("heartwood_pickaxe", Item::new, new Item.Properties().pickaxe(HEARTWOOD, 1.0F, -2.8F));
	public static final Item HEARTWOOD_AXE = register("heartwood_axe", Item::new, new Item.Properties().axe(HEARTWOOD, 5.0F, -3.0F));
	public static final Item HEARTWOOD_SHOVEL = register("heartwood_shovel", Item::new, new Item.Properties().shovel(HEARTWOOD, 1.5F, -3.0F));
	public static final Item HEARTWOOD_HOE = register("heartwood_hoe", Item::new, new Item.Properties().hoe(HEARTWOOD, -3.0F, 0.0F));
	public static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> PALEWEAVE_ASSET = net.minecraft.resources.ResourceKey.create(
		net.minecraft.world.item.equipment.EquipmentAssets.ROOT_ID, Elsewhere.id("paleweave"));
	private static final java.util.Map<net.minecraft.world.item.equipment.ArmorType, Integer> PALEWEAVE_DEFENSE = java.util.Map.of(
		net.minecraft.world.item.equipment.ArmorType.HELMET, 3, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE, 8,
		net.minecraft.world.item.equipment.ArmorType.LEGGINGS, 6, net.minecraft.world.item.equipment.ArmorType.BOOTS, 3,
		net.minecraft.world.item.equipment.ArmorType.BODY, 11);
	public static final net.minecraft.world.item.equipment.ArmorMaterial PALEWEAVE = new net.minecraft.world.item.equipment.ArmorMaterial(33, PALEWEAVE_DEFENSE, 14,
		SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.0F, AMBER_REPAIR, PALEWEAVE_ASSET);
	/** The Mantle (chest) alone has knockback resistance 0.1. */
	public static final net.minecraft.world.item.equipment.ArmorMaterial PALEWEAVE_MANTLE_MAT = new net.minecraft.world.item.equipment.ArmorMaterial(33, PALEWEAVE_DEFENSE, 14,
		SoundEvents.ARMOR_EQUIP_LEATHER, 2.0F, 0.1F, AMBER_REPAIR, PALEWEAVE_ASSET);
	public static final Item PALEWEAVE_HELMET = register("paleweave_helmet", Item::new, new Item.Properties().humanoidArmor(PALEWEAVE, net.minecraft.world.item.equipment.ArmorType.HELMET));
	public static final Item PALEWEAVE_MANTLE = register("paleweave_mantle", Item::new, new Item.Properties().rarity(Rarity.UNCOMMON)
		.humanoidArmor(PALEWEAVE_MANTLE_MAT, net.minecraft.world.item.equipment.ArmorType.CHESTPLATE));
	public static final Item PALEWEAVE_LEGGINGS = register("paleweave_leggings", Item::new, new Item.Properties().humanoidArmor(PALEWEAVE, net.minecraft.world.item.equipment.ArmorType.LEGGINGS));
	public static final Item PALEWEAVE_BOOTS = register("paleweave_boots", Item::new, new Item.Properties().humanoidArmor(PALEWEAVE, net.minecraft.world.item.equipment.ArmorType.BOOTS));
	public static final Item ANTLER_BOW = register("antler_bow", dev.elsewhere.palehollow.item.AntlerBowItem::new,
		new Item.Properties().durability(640).enchantable(1).repairable(STAG_ANTLER));

	// ---- 2.0 spawn eggs
	public static final Item HOLLOW_STAG_SPAWN_EGG = register("hollow_stag_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.HOLLOW_STAG));
	public static final Item BOG_WIGHT_SPAWN_EGG = register("bog_wight_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.BOG_WIGHT));
	public static final Item DOCILE_SCARAB_SPAWN_EGG = register("docile_scarab_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.DOCILE_SCARAB));
	public static final Item HAZE_FERRET_SPAWN_EGG = register("haze_ferret_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.HAZE_FERRET));

	// ---- 2.0 fishing (new-dimension-fishing): one catch per dimension
	public static final Item GHOSTFIN = fish("ghostfin", 2, MobEffects.NIGHT_VISION, 30);
	public static final Item PETALSCALE_KOI = fish("petalscale_koi", 2, MobEffects.SLOW_FALLING, 20);
	public static final Item GLASSFIN_DARTER = fish("glassfin_darter", 2, MobEffects.FIRE_RESISTANCE, 15);
	public static final Item VERDIGRIS_EEL = fish("verdigris_eel", 3, MobEffects.WATER_BREATHING, 30);
	public static final Item FROSTJAW_TROUT = fish("frostjaw_trout", 3, MobEffects.RESISTANCE, 10);
	public static final Item HAZE_GUPPY = fish("haze_guppy", 2, MobEffects.REGENERATION, 5);
	public static final Item HUSH_LANTERNFISH = fish("hush_lanternfish", 2, MobEffects.INVISIBILITY, 10);

	private static Item fish(final String name, final int food, final net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, final int seconds) {
		return register(name, Item::new, new Item.Properties().food(new FoodProperties.Builder().nutrition(food).saturationModifier(0.3F).build(),
			Consumable.builder().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(new MobEffectInstance(effect, seconds * 20, 0)))).build()));
	}

	// ---- tier signature gear (fix-tier-gear-owner): registered here first so every module's stub gets this copy
	static {
		dev.elsewhere.tiergear.TierGear.register();
	}

	private ModItems() {
	}

	public static Item register(final String name, final Function<Item.Properties, Item> factory, final Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Elsewhere.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(props.setId(key)));
	}

	public static void init() {
	}
}
