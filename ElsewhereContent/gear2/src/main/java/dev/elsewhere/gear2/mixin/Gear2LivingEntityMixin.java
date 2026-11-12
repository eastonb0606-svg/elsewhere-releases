package dev.elsewhere.gear2.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear2.Gear2Hooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server damage entry point: gear2 weapon bonuses/cons, Capacitor discharge and the Coilmail lightning con. */
@Mixin(LivingEntity.class)
public abstract class Gear2LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float gear2$modifyDamage(float amount, @Local(argsOnly = true) DamageSource source) {
		return Gear2Hooks.modifyIncoming((LivingEntity) (Object) this, source, amount);
	}
}
