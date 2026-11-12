package dev.elsewhere.gear3.mixin;

import dev.elsewhere.gear3.Gear3Hooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Fowler's Bandolier: a saved arrow is shot without using up the stack. */
@Mixin(ProjectileWeaponItem.class)
public abstract class Gear3AmmoMixin {
	@Inject(method = "useAmmo", at = @At("HEAD"), cancellable = true)
	private static void gear3$useAmmo(ItemStack weapon, ItemStack ammo, LivingEntity shooter, boolean isCreative, CallbackInfoReturnable<ItemStack> cir) {
		if (!isCreative && Gear3Hooks.bandolierSaves(shooter, ammo)) cir.setReturnValue(ammo.copyWithCount(1));
	}
}
