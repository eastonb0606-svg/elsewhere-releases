package dev.elsewhere.stub.verdigris_ironpeak;

import static dev.elsewhere.verdigris_ironpeak.Reg.item;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.ArmorMaterials;
import dev.elsewhere.verdigris_ironpeak.GearStats;
import org.jspecify.annotations.Nullable;

/**
 * STUB of the core's tier signature gear (BOSSES.md "Signature gear"), only the tiers this module's bosses use:
 * II Aegis of the Ladder (Verdigrim), III Phasewalker Greaves (Hrolf), IV Stormcrest Helm (Galvanox, Thurgrim).
 * The core registers the real ones; drop this class at merge. Abilities are implemented in {@link SignatureAbilities}.
 */
public final class SignatureGear {
	private static final ArmorMaterial PHASEWALKER = new ArmorMaterial(37, java.util.Map.of(ArmorType.LEGGINGS, 6), 15, SoundEvents.ARMOR_EQUIP_NETHERITE,
		4.0F, 0.1F, ItemTags.REPAIRS_NETHERITE_ARMOR, GearStats.asset("phasewalker"));
	private static final ArmorMaterial STORMCREST = new ArmorMaterial(37, java.util.Map.of(ArmorType.HELMET, 3), 15, SoundEvents.ARMOR_EQUIP_NETHERITE,
		3.0F, 0.2F, ItemTags.REPAIRS_NETHERITE_ARMOR, GearStats.asset("stormcrest"));

	public static final Item AEGIS_OF_THE_LADDER = dev.elsewhere.verdigris_ironpeak.Reg.sharedItem("aegis_of_the_ladder", ShieldItem::new, new Item.Properties().rarity(Rarity.EPIC)
		.durability(900).equippableUnswappable(EquipmentSlot.OFFHAND).repairable(ItemTags.REPAIRS_NETHERITE_ARMOR)
		.delayedComponent(DataComponents.BLOCKS_ATTACKS, context -> new BlocksAttacks(0.25F, 1.0F,
			List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
			new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
			Optional.of(context.getOrThrow(DamageTypeTags.BYPASSES_SHIELD)), Optional.of(SoundEvents.SHIELD_BLOCK), Optional.of(SoundEvents.SHIELD_BREAK)))
		.component(DataComponents.BREAK_SOUND, SoundEvents.SHIELD_BREAK));
	public static final Item PHASEWALKER_GREAVES = dev.elsewhere.verdigris_ironpeak.Reg.sharedItem("phasewalker_greaves", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
		.humanoidArmor(PHASEWALKER, ArmorType.LEGGINGS));
	public static final Item STORMCREST_HELM = dev.elsewhere.verdigris_ironpeak.Reg.sharedItem("stormcrest_helm", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant()
		.humanoidArmor(STORMCREST, ArmorType.HELMET));

	private SignatureGear() {}

	public static @Nullable Item forTier(int tier) {
		return switch (tier) {
			case 2 -> AEGIS_OF_THE_LADDER;
			case 3 -> PHASEWALKER_GREAVES;
			case 4 -> STORMCREST_HELM;
			default -> null;
		};
	}

	public static void init() {}
}
