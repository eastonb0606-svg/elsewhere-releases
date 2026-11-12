package dev.elsewhere.verdigris_ironpeak;

import dev.elsewhere.ironpeak.IronpeakItems;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.item.v1.FabricComponentMapBuilder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * Beta-2 ARSENAL.md attribute hooks, applied to default item components after every mod has registered, so attributes
 * owned by other modules are looked up by id and skipped cleanly when absent:
 * Roc-Feather Longbow `elsewhere:projectile_pierce` +1 (either hand); Kingsplitter `entity_interaction_range` +0.5 (long
 * haft) and BALANCE.md's -10% movement speed while held. Existing modifiers are kept (read from the builder, not from
 * {@code item.components()}, which isn't bound yet here).
 */
public final class ArsenalHooks {
	private ArsenalHooks() {}

	public static void init() {
		DefaultItemComponentEvents.MODIFY.register(ctx -> {
			ctx.modify(IronpeakItems.ROC_FEATHER_LONGBOW, b -> BuiltInRegistries.ATTRIBUTE.get(Reg.id("projectile_pierce")).ifPresent(pierce ->
				b.set(DataComponents.ATTRIBUTE_MODIFIERS, current(b).withModifierAdded(pierce,
					new AttributeModifier(Reg.id("longbow_pierce"), GearStats.LONGBOW_PIERCE, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.HAND))));
			ctx.modify(IronpeakItems.KINGSPLITTER, b -> b.set(DataComponents.ATTRIBUTE_MODIFIERS, current(b)
				.withModifierAdded(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(Reg.id("kingsplitter_reach"), GearStats.KINGSPLITTER_REACH, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(Reg.id("kingsplitter_heavy"), -0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND)));
		});
	}

	private static ItemAttributeModifiers current(DataComponentMap.Builder b) {
		return ((FabricComponentMapBuilder) (Object) b).getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
	}
}
