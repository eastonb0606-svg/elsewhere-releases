package dev.elsewhere.mixin;

import dev.elsewhere.core.gear.GearRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
	@Inject(method = "modifyDurabilityToRepairFromXp", at = @At("RETURN"), cancellable = true)
	private static void elsewhere$slowMending(final ServerLevel level, final ItemStack stack, final int xp, final CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(GearRules.mendingRepair(stack, cir.getReturnValue()));
	}
}
