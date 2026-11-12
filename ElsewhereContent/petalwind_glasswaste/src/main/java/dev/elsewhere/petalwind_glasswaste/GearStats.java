package dev.elsewhere.petalwind_glasswaste;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Every gear, ability and drop number for this module, in one place (CONTRACT.md "Design ownership").
 * Values are the doc values, or placeholders where the docs give none (listed in QUESTIONS.md), until BALANCE.md is final.
 */
public final class GearStats {
	private GearStats() {}

	// ---- Petalwind -------------------------------------------------------------------------------------------------
	/** Placeholder: diamond tool stats, "light and fast with extra knockback". */
	public static final ToolMaterial ZEPHYRITE = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1300, 9.0F, 3.0F, 14,
		itemTag("zephyrite_tool_materials"));
	public static final float ZEPHYRITE_SWORD_DAMAGE = 7.0F, ZEPHYRITE_SWORD_SPEED = 1.7F; // BALANCE.md M (final)
	public static final double ZEPHYRITE_KNOCKBACK = 0.5;
	/** Placeholder: diamond armor; full set = one mid-air jump per airtime and fall damage x0.75. */
	public static final int[] GALEWEAVE_DEFENSE = {3, 6, 8, 3}; // boots, legs, chest, helm
	public static final float GALEWEAVE_TOUGHNESS = 2.0F;
	public static final int GALEWEAVE_DURABILITY = 33;
	public static final float GALEWEAVE_FALL_MULTIPLIER = 0.6F; // BALANCE: -0.1 per piece (C33), i.e. 0.6 with all four
	public static final double GALEWEAVE_FALL_PER_PIECE = -0.10;
	public static final double AIR_JUMP_VELOCITY = 0.62;
	/** Doc: glider, max_damage 216, repairable with feathers; contract: shorter, slower glide, no rocket boost. */
	public static final int PETAL_GLIDER_DURABILITY = 216;
	public static final double PETAL_GLIDER_DRAG = 0.985; // extra horizontal drag per tick while gliding
	public static final double PETAL_GLIDER_SINK = 0.012; // extra sink per tick
	public static final int SKYHOOK_DURABILITY = 128, SKYHOOK_COOLDOWN = 30;
	public static final double SKYHOOK_RANGE = 28.0;
	public static final int CLOUDBREAD_NUTRITION = 5, CLOUDBREAD_EFFECT_TICKS = 600;
	public static final float CLOUDBREAD_SATURATION = 0.6F;
	// Kazehana drops (Tier I)
	public static final float KAZE_KATANA_DAMAGE = 7.0F, KAZE_KATANA_SPEED = 1.6F; // "iron_sword with diamond-level attributes"
	public static final int KAZE_KATANA_COOLDOWN = 120; // BALANCE: 6 s
	public static final double KAZE_KATANA_DASH = 1.6;  // ~5 blocks
	public static final int[] PETALSTEP_DEFENSE = {3, 0, 0, 0}; // placeholder: diamond boots
	public static final float PETALSTEP_TOUGHNESS = 2.0F;
	// Tempestra drops (Tier III)
	public static final int ZEPHYR_WINGS_DURABILITY = 640, ZEPHYR_WINGS_BOOST_COOLDOWN = 30; // BALANCE N3
	public static final double ZEPHYR_WINGS_ARMOR = 0.0, ZEPHYR_WINGS_BOOST = 1.1;
	public static final int GALE_HEART_DURATION = 200, GALE_HEART_COOLDOWN = 40, GALE_HEART_GLIMMER = 10; // ARSENAL: 10 Glimmer, 2 s use delay
	// Boss-specific drop chances (placeholders: BOSSES.md gives none; first kill always gives both).
	public static final float KAZEHANA_KATANA_CHANCE = 0.5F, KAZEHANA_SANDALS_CHANCE = 0.5F;
	public static final float TEMPESTRA_WINGS_CHANCE = 0.4F, TEMPESTRA_HEART_CHANCE = 0.6F;
	public static final float HELIODOR_LENS_CHANCE = 0.5F, HELIODOR_PLATE_CHANCE = 0.35F;
	public static final float ANKHARA_GLASSFANG_CHANCE = 0.35F, ANKHARA_GIZZARD_CHANCE = 0.6F;

	// ---- Glasswaste ------------------------------------------------------------------------------------------------
	/** Placeholder: diamond tool stats; hits ignite targets while you fight under the open sun. */
	public static final ToolMaterial SUNSTONE = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1800, 9.0F, 4.0F, 12,
		itemTag("sunstone_tool_materials"));
	public static final int SUNSTONE_IGNITE_SECONDS = 3; // BALANCE: ignite 3 s in daylight under open sky
	/** Placeholder: diamond armor; each piece -25% Heat gain, full set Glare immunity. */
	public static final int[] SUNWRIGHT_DEFENSE = {3, 6, 8, 3};
	public static final float SUNWRIGHT_TOUGHNESS = 2.0F;
	public static final int SUNWRIGHT_DURABILITY = 35;
	public static final double SUNWRIGHT_HEAT_REDUCTION_PER_PIECE = 0.25;
	// BALANCE N1 Sunwright line (polish-endgame-sets): netherite-equal defence, -4 toughness / no KB res, per-piece perks, Noonfire.
	public static final int SUNWRIGHT_ENCHANTABILITY = 12;
	public static final double SUNWRIGHT_CHEST_BURN = -0.30;           // chest and Plate: burning_time -0.3
	public static final double SUNWRIGHT_SAND_SPEED = 0.15;            // boots: +15% on sand, red sand, soul sand, soul soil
	public static final int NOONFIRE_IGNITE_SECONDS = 3;                // melee attackers catch fire
	public static final double SUNWRIGHT_FREEZE_DAMAGE_PER_PIECE = 0.125; // con: freeze damage +12.5% per piece
	public static final double SUNWRIGHT_FREEZE_BUILDUP_SET = 2.0;      // con: with the set freezing builds 2x faster
	public static final double SUNSTONE_SUN_MINING = 0.15;              // pro: +15% mining speed in daylight under open sky
	public static final float SUNSTONE_DARK_PENALTY = Overhaul3.REBALANCE_BOSSDROPS ? 0.0F : 1.0F; // 3.0: no dark penalty             // con: -1 attack damage underground, at night or in rain
	public static final double SUNSTONE_DARK_SPEED = 8.0 / 9.0;         // con: pick speed 8 instead of 9
	/** Doc: armor 3, attack speed +10% in sunlight, Glare immunity. */
	public static final int SUNWRIGHT_VISOR_ARMOR = 3;
	public static final double SUNWRIGHT_VISOR_SUN_ATTACK_SPEED = 0.10;
	/** Doc: max_damage 128, 2 -> 6 fire damage per second while held under open sky. */
	public static final int BURNING_GLASS_DURABILITY = 128;
	public static final float BURNING_GLASS_MIN_DPS = 2.0F, BURNING_GLASS_MAX_DPS = 6.0F;
	public static final double BURNING_GLASS_RANGE = 32.0;
	public static final int CACTUS_WATER_FIRE_RES_TICKS = 600;
	/** Doc: iron spear + 3 attack damage, bonus vs burrowed mobs. */
	public static final float EEL_SPEAR_BONUS_DAMAGE = 3.0F, EEL_SPEAR_BURROW_BONUS = 4.0F;
	// Heliodor drops (Tier IV)
	public static final int HELIOGRAPH_LENS_COOLDOWN = 20, HELIOGRAPH_LENS_GLIMMER = 6; // ARSENAL: 6 Glimmer, 1 s use delay
	public static final float HELIOGRAPH_LENS_DAMAGE = 6.0F;
	// ARSENAL.md beta 2 (attributes by id, trades for arsenal/summoner items by id; skipped when absent)
	public static final double GALEWEAVE_DODGE_PER_PIECE = 0.03, KAZE_KATANA_DODGE = 0.10; // elsewhere:dodge_chance
	public static final int KITESTRING_FEATHERS = 12, KITESTRING_ZEPHYRITE = 2;   // Windwright: Kitestring Staff
	public static final int RAPIER_FEATHERS = 16, RAPIER_ZEPHYRITE = 4;           // Windwright: Gale Rapier (LANES gear2 trade)
	public static final int RIBBON_FEATHERS = 10, RIBBON_SEALS = 3;               // Windwright: Gale Ribbon
	public static final int BELL_SEALS = 2;                                       // Windwright: Bell of the Flock (+ 1 bell)
	public static final int SUNLANCE_SHARDS = 12;                                 // Dune Factor: Amethyst Wand + 12 Sunglass Shards
	public static final int FOCUS_SHARDS = 10, FOCUS_AMETHYST = 4;                // Dune Factor: Glimmerstone Focus
	public static final int[] SUNWRIGHT_PLATE_DEFENSE = {0, 0, 8, 0}; // "golden_chestplate with netherite-level attributes"
	public static final float SUNWRIGHT_PLATE_TOUGHNESS = 3.0F, SUNWRIGHT_PLATE_KNOCKBACK_RES = 0.1F;
	// Ankhara drops (Tier IV)
	public static final float GLASSFANG_DAMAGE = Overhaul3.REBALANCE_BOSSDROPS ? 9.0F : 8.0F, GLASSFANG_SPEED = 1.5F; // BALANCE: 8 / 1.5, pierce 3, no +4
	public static final float GLASSFANG_BONUS_VS_BURROWING = 0.0F, GLASSFANG_ARMOR_PIERCE = Overhaul3.REBALANCE_BOSSDROPS ? 4.0F : 3.0F;
	public static final int GIZZARD_STONE_NUTRITION = 2;

	// ---- Signature gear (one per tier; BOSSES.md "Signature gear") ------------------------------------------------
	public static final float SIGNATURE_DROP_CHANCE_REPEAT = 0.10F; // guaranteed on first kill
	public static final float CHALLENGERS_CLEAVER_DAMAGE = Overhaul3.REBALANCE_BOSSDROPS ? 7.0F : 8.0F, CHALLENGERS_CLEAVER_SPEED = 1.6F, CHALLENGERS_CLEAVER_BOSS_BONUS = Overhaul3.REBALANCE_BOSSDROPS ? 0.20F : 0.15F; // 3.0: same as the core copy (7 / +20%)

	// ---- helpers ---------------------------------------------------------------------------------------------------
	public static TagKey<Item> itemTag(String path) {
		return TagKey.create(Registries.ITEM, Reg.id(path));
	}

	public static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id(name));
	}

	public static Map<ArmorType, Integer> defense(int[] d) {
		return Map.of(ArmorType.BOOTS, d[0], ArmorType.LEGGINGS, d[1], ArmorType.CHESTPLATE, d[2], ArmorType.HELMET, d[3], ArmorType.BODY, d[2]);
	}

	public static ArmorMaterial armor(int durability, int[] defense, float toughness, float knockbackRes, Holder<SoundEvent> sound, String repairTag,
		String asset) {
		return armor(durability, defense, toughness, knockbackRes, sound, repairTag, asset, 14);
	}

	public static ArmorMaterial armor(int durability, int[] defense, float toughness, float knockbackRes, Holder<SoundEvent> sound, String repairTag,
		String asset, int enchantability) {
		return new ArmorMaterial(durability, defense(defense), enchantability, sound, toughness, knockbackRes, itemTag(repairTag), asset(asset));
	}

	/** Armor attributes for one piece plus extra ADD_VALUE modifiers (id = name.slot) in that piece's slot. */
	public static ItemAttributeModifiers armorAttrs(ArmorMaterial material, ArmorType type, Object... nameAttrAmount) {
		ItemAttributeModifiers mods = material.createAttributes(type);
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
		for (int i = 0; i + 2 < nameAttrAmount.length + 0; i += 3) {
			@SuppressWarnings("unchecked") Holder<Attribute> a = (Holder<Attribute>) nameAttrAmount[i + 1];
			mods = mods.withModifierAdded(a, new AttributeModifier(Reg.id(nameAttrAmount[i] + "." + type.getName()), ((Number) nameAttrAmount[i + 2]).doubleValue(),
				AttributeModifier.Operation.ADD_VALUE), group);
		}
		return mods;
	}

	/** Full attribute set for a melee item: damage and speed are the totals shown in the tooltip. */
	public static ItemAttributeModifiers weapon(float totalDamage, float attacksPerSecond) {
		return ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, totalDamage - 1.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, attacksPerSecond - 4.0, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.build();
	}

	public static ItemAttributeModifiers withExtra(ItemAttributeModifiers base, Holder<Attribute> attribute, String id, double amount, EquipmentSlotGroup slot) {
		return base.withModifierAdded(attribute, new AttributeModifier(Reg.id(id), amount, AttributeModifier.Operation.ADD_VALUE), slot);
	}

	public static Item.Properties withAttributes(Item.Properties props, ItemAttributeModifiers mods) {
		return props.component(DataComponents.ATTRIBUTE_MODIFIERS, mods);
	}
}
