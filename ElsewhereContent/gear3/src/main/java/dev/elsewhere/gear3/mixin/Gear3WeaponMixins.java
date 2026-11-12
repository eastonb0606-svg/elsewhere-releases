package dev.elsewhere.gear3.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.Gear3Hooks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Quickdraw Quiver: bow release and crossbow charge time. */
public final class Gear3WeaponMixins {
	private Gear3WeaponMixins() {}

	@Mixin(BowItem.class)
	public abstract static class Bow {
		@ModifyVariable(method = "releaseUsing", at = @At("HEAD"), argsOnly = true, ordinal = 0)
		private int gear3$timeLeft(int timeLeft, @Local(argsOnly = true) ItemStack stack, @Local(argsOnly = true) LivingEntity user) {
			return Gear3Hooks.bowTimeLeft(timeLeft, ((BowItem) (Object) this).getUseDuration(stack, user), user);
		}
	}

	@Mixin(CrossbowItem.class)
	public abstract static class Crossbow {
		@ModifyReturnValue(method = "getChargeDuration", at = @At("RETURN"))
		private static int gear3$charge(int original, ItemStack stack, LivingEntity user) {
			return Gear3Hooks.crossbowDuration(original, user);
		}
	}
}
