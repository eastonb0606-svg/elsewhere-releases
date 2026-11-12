package dev.elsewhere.verdigris_ironpeak;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

/**
 * Every gear number in this module, in one place, so the balance pass (ElsewhereContent/BALANCE.md) is a one-file change.
 * Netherite for reference: sword 8 @1.6, axe 10 @1.0, armor 20 + 12 toughness + 0.4 knockback resistance.
 * Design is owned by the mod-wide design agent (CONTRACT.md "Design ownership"); this class only holds the numbers.
 */
public final class GearStats {
	private GearStats() {}

	public static TagKey<Item> itemTag(String name) {
		return TagKey.create(Registries.ITEM, Reg.id(name));
	}

	public static ResourceKey<EquipmentAsset> asset(String name) {
		return ResourceKey.create(EquipmentAssets.ROOT_ID, Reg.id(name));
	}

	// BALANCE.md (FINAL) numbers applied where marked; the rest: DIMENSIONS.md / BOSSES.md, or a PLACEHOLDER (see ElsewhereContent/QUESTIONS.md)
	// until BALANCE.md is final. Do not tune here without BALANCE.md.

	// Tools ------------------------------------------------------------------------------------------
	/** Tidesteel (Verdigris Sea), doc: "full speed underwater". PLACEHOLDER stats = diamond. */
	public static final ToolMaterial TIDESTEEL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1700, 9.0F, 3.0F, 12, itemTag("tidesteel_tool_materials"));
	public static final double TIDESTEEL_SUBMERGED_MINING = 0.8; // +0.8 on the 0.2 base = full speed underwater
	/** Skyiron (Ironpeak), doc: "between diamond and netherite", "mine faster the higher you are". PLACEHOLDER stats. */
	public static final ToolMaterial SKYIRON = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 1800, 8.0F, 3.0F, 12, itemTag("skyiron_tool_materials"));
	/** Skyiron mining multiplier: 1.0 at or below {@link #SKYIRON_LOW_Y}, rising linearly to {@link #SKYIRON_MAX} at {@link #SKYIRON_HIGH_Y}. PLACEHOLDER. */
	public static final double SKYIRON_LOW_Y = 64, SKYIRON_HIGH_Y = 1400, SKYIRON_MAX = 1.0; // 2.0: altitude speed removed (Quarry replaces it)

	// Armor ------------------------------------------------------------------------------------------
	/** Diving Suit, doc: "full set gives water breathing and walking on the sea floor". PLACEHOLDER stats = diamond. */
	public static final ArmorMaterial DIVING_SUIT = new ArmorMaterial(33, defense(3, 6, 8, 3), 10, SoundEvents.ARMOR_EQUIP_IRON, 3.0F, 0.1F,
		itemTag("repairs_diving_suit"), asset("diving_suit"));
	/** Climber armor, doc: "immune to cold, with a safe-fall bonus". PLACEHOLDER stats = diamond; cold-proof per piece; safe fall +1 per piece. */
	public static final ArmorMaterial CLIMBER = new ArmorMaterial(37, defense(3, 6, 7, 3), 12, SoundEvents.ARMOR_EQUIP_LEATHER, 3.0F, 0.05F,
		itemTag("repairs_climber_armor"), asset("climber"));
	public static final double CLIMBER_SAFE_FALL = 3.0, CLIMBER_STEP = 0.5, CLIMBER_BURN_PER_PIECE = 0.25;
	/** Mountain Goat: fall damage -50%, wall cling 3 s, 8 s cooldown. */
	public static final double MOUNTAIN_GOAT_FALL = -0.5;
	public static final int GOAT_CLING_TICKS = 60, GOAT_CLING_COOLDOWN = 160;
	/** Tidebrass: -5% land speed per piece (underwater none); oxygen +2 on helm and chest; guardians and drowned -15% on the chest. */
	public static final double TIDEBRASS_OXYGEN = 2, TIDEBRASS_WATER_EFF = 0.33, TIDEBRASS_LAND_SLOW = -0.05, TIDEBRASS_SET_WATER_EFF = 0.34, TIDEBRASS_GUARD_REDUCTION = 0.15;
	/** Tidesteel: +2 damage on sword and axe when you are in water or rain. */
	public static final double TIDESTEEL_WET_BONUS = 2.0;
	/** Skyiron Quarry: 3x3 face above Y 63, felling up to 24 connected logs. */
	public static final int QUARRY_MIN_Y = 63, FELL_MAX_LOGS = 24;
	public static final float QUARRY_MAX_HARDNESS = 3.0F;

	// Single pieces (doc values) -----------------------------------------------------------------------
	/** Diving Helm: armor 2, oxygen_bonus +3, submerged_mining_speed 1.0. */
	public static final ArmorMaterial DIVING_HELM = single(275, ArmorType.HELMET, 3, 2, SoundEvents.ARMOR_EQUIP_TURTLE, "diving_helm", "repairs_diving_suit");
	public static final double DIVING_HELM_OXYGEN = 3, DIVING_HELM_SUBMERGED_MINING = 0.8;
	/** Verdigris Mitre: Respiration III (as oxygen_bonus +3) + Conduit Power within 8 blocks of copper. Armor: copper/turtle helmet (2). */
	public static final ArmorMaterial VERDIGRIS_MITRE = Overhaul3.REBALANCE_BOSSDROPS // 3.0: 3 / 3 / KB 0.1 (BALANCE value)
		? new ArmorMaterial(275 / 11 + 1, Map.of(ArmorType.HELMET, 3), 12, SoundEvents.ARMOR_EQUIP_COPPER, 3.0F, 0.1F, itemTag("repairs_diving_suit"), asset("verdigris_mitre"))
		: single(275, ArmorType.HELMET, 2, 0, SoundEvents.ARMOR_EQUIP_COPPER, "verdigris_mitre", "repairs_diving_suit");
	public static final double MITRE_OXYGEN = 3;
	public static final int MITRE_COPPER_RADIUS = 8;
	/** Crampons: armor 3, powder-snow walking, step_height +0.6. */
	public static final ArmorMaterial CRAMPONS = single(429, ArmorType.BOOTS, 3, 2, SoundEvents.ARMOR_EQUIP_LEATHER, "crampons", "repairs_climber_armor");
	public static final double CRAMPONS_STEP = 0.5;
	/** Champion's Belt: armor 6, attack_damage +1; Test Meter +60 ng/dL lifestyle while worn. */
	public static final ArmorMaterial CHAMPIONS_BELT = single(495, ArmorType.LEGGINGS, 6, 2, SoundEvents.ARMOR_EQUIP_LEATHER, "champions_belt", "repairs_climber_armor");
	public static final double BELT_ATTACK = 1, BELT_TEST_METER_NGDL = 60;
	/** Summit Crown: armor 4, toughness 3; Test Meter: natural T held 10% higher, damage-stress T loss halved. */
	public static final ArmorMaterial SUMMIT_CROWN = single(363, ArmorType.HELMET, 4, 3, SoundEvents.ARMOR_EQUIP_GOLD, "summit_crown", "repairs_climber_armor");
	public static final double CROWN_NATURAL_BONUS = 0.10;

	// Weapons and tools with abilities ---------------------------------------------------------------
	/** Stormcaller Harpoon: trident + attack_damage +2; lightning on every hit in the Verdigris Sea; reels 4 blocks. */
	public static final double HARPOON_BONUS_DAMAGE = 0; // BALANCE.md final: no bonus damage
	public static final int HARPOON_COOLDOWN = 160, HARPOON_REEL_COOLDOWN = 80; // BALANCE.md: thrown lightning 8 s, reel 4 s
	/** Galvanic Trident: lightning on hit in any weather, anywhere. use_cooldown 6 s. */
	public static final int GALVANIC_COOLDOWN = 160; // BALANCE.md: 8 s
	/** Kingsplitter (netherite_axe): sneak-attack slam launches enemies up. Slam cooldown PLACEHOLDER 8 s. */
	public static final float KINGSPLITTER_DAMAGE = 12.0F, KINGSPLITTER_SPEED = Overhaul3.REBALANCE_BOSSDROPS ? 0.9F : 0.8F; // BALANCE.md final
	public static final int KINGSPLITTER_SLAM_COOLDOWN = 200, KINGSPLITTER_DURABILITY = 1900;
	public static final double KINGSPLITTER_LAUNCH = 1.1;
	/** ARSENAL.md §2: Kingsplitter long haft, + entity_interaction_range (MAINHAND). */
	public static final double KINGSPLITTER_REACH = 0.5;

	/** ARSENAL.md §6 Roc-Feather Longbow: arrow speed x1.4, full draw 1.4 s (28 ticks), durability 768, pierce +1. */
	public static final float LONGBOW_ARROW_SPEED = 1.4F, LONGBOW_DRAW_SECONDS = 1.4F;
	public static final int LONGBOW_DURABILITY = 768, LONGBOW_PIERCE = 1;
	/** Longbow zoom (user decision): 1.5x at full draw, 2x sneaking at full draw (the cap); eases in with draw^2. */
	public static final float LONGBOW_ZOOM = 1.5F, LONGBOW_ZOOM_SNEAK = 2.0F, LONGBOW_ZOOM_SNEAK_BLEND_SECONDS = 0.25F;
	/** Quartermaster rank-3 price: bow + 4 Roc Feathers + 2 Skyiron. */
	public static final int LONGBOW_PRICE_FEATHERS = 4, LONGBOW_PRICE_SKYIRON = 2, LONGBOW_TRADE_RANK = 3;

	/** ARSENAL.md §5b/§5c drops by id lookup (summoner module items): 50% on a player's first kill, 20% after. */
	public static final float ARSENAL_DROP_FIRST = 0.5F, ARSENAL_DROP_REPEAT = 0.2F;
	/** Tidewright prices (ARSENAL.md): Brassback Key = 3 Scraped Patina + 16 Gears; Stormcoil Staff = Amethyst Wand + 1 Storm Coil + 12 Gears + 2 Pearlite. */
	public static final int BRASSBACK_KEY_PATINA = 3, BRASSBACK_KEY_GEARS = 16;
	public static final int STORMCOIL_COILS = 1, STORMCOIL_GEARS = 12, STORMCOIL_PEARLITE = 2;
	/** Ice Axe: mainhand gravity -25%, safe_fall_distance +4, step_height +0.5; cling 3 s. Base stats PLACEHOLDER = diamond axe. */
	public static final double ICE_AXE_GRAVITY = -0.25, ICE_AXE_SAFE_FALL = 4, ICE_AXE_STEP = 0.5;
	public static final int ICE_AXE_COOLDOWN = 120; // BALANCE.md: 6 s cling cooldown
	/** Hymnal Horn: blows mobs back 6 blocks, use_cooldown 30 s. Avalanche Horn: 7-wide snow wave, 120 s. */
	public static final int HYMNAL_COOLDOWN = 600, AVALANCHE_COOLDOWN = 2400;
	public static final double HYMNAL_RADIUS = 6;
	public static final int AVALANCHE_WIDTH = 7;

	// Food --------------------------------------------------------------------------------------------
	public static final int BRINE_LUNG_BREATHING = 180 * 20, BRINE_LUNG_CONDUIT = 45 * 20;
	public static final int TEA_RESISTANCE = 60 * 20;

	// Drops (per BOSSES.md / DIMENSIONS.md) ------------------------------------------------------------
	public static final float GEAR_FROM_CLOCKWORK = 0.40F, TIDE_KEY_FROM_GUARDIAN = 0.25F, SKYIRON_FROM_RAIDER = 0.10F, SKYIRON_FROM_HIGH_IRON = 0.25F;
	public static final float SIGNATURE_REPEAT_CHANCE = 0.10F;
	/** Boss drops. BOSSES.md lists them without chances: first kill gives every listed drop; repeat chances are PLACEHOLDERS. */
	public static final float VERDIGRIM_MITRE_REPEAT = 0.5F, VERDIGRIM_HORN_REPEAT = 0.5F; // Tide Key: guaranteed
	public static final float GALVANOX_TRIDENT_REPEAT = 0.35F, GALVANOX_LANTERN_REPEAT = 0.5F;
	public static final int GALVANOX_PATINA_MIN = 3, GALVANOX_PATINA_MAX = 6;
	public static final float THURGRIM_CROWN_REPEAT = 0.35F, THURGRIM_AXE_REPEAT = 0.25F, THURGRIM_GLAND_REPEAT = 1.0F;
	public static final float HROLF_BELT_REPEAT = 0.35F; // Avalanche Horn: guaranteed

	private static Map<ArmorType, Integer> defense(int boots, int legs, int chest, int helm) {
		return Map.of(ArmorType.BOOTS, boots, ArmorType.LEGGINGS, legs, ArmorType.CHESTPLATE, chest, ArmorType.HELMET, helm, ArmorType.BODY, chest);
	}

	private static ArmorMaterial single(int durability, ArmorType type, int armor, float toughness, Holder<SoundEvent> sound, String asset, String repair) {
		return new ArmorMaterial(durability / 11 + 1, Map.of(type, armor), 12, sound, toughness, 0.0F, itemTag(repair), asset(asset));
	}

	/** Armor attributes for a piece, plus extra modifiers. */
	public static ItemAttributeModifiers armor(ArmorMaterial material, ArmorType type, Extra... extras) {
		ItemAttributeModifiers mods = material.createAttributes(type);
		EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(type.getSlot());
		for (Extra e : extras) {
			mods = mods.withModifierAdded(e.attribute, new AttributeModifier(Reg.id(e.name + "." + type.getName()), e.amount, e.op), group);
		}
		return mods;
	}

	public record Extra(Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
		public static Extra add(Holder<Attribute> a, String name, double v) {
			return new Extra(a, name, v, AttributeModifier.Operation.ADD_VALUE);
		}
	}
}
