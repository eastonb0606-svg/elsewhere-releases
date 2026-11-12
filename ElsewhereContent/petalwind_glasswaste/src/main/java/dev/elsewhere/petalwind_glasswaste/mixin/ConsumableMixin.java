package dev.elsewhere.petalwind_glasswaste.mixin;

import dev.elsewhere.glasswaste.Heat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drinking a water bottle cools you down in the Glasswaste. */
@Mixin(Consumable.class)
abstract class ConsumableMixin {
	@Inject(method = "onConsume", at = @At("HEAD"))
	private void elsewhere_pg$coolDown(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
		if (!(user instanceof ServerPlayer player) || !stack.is(Items.POTION)) return;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents != null && contents.is(Potions.WATER)) Heat.add(player, -Heat.WATER_BOTTLE_COOLING);
	}
}
