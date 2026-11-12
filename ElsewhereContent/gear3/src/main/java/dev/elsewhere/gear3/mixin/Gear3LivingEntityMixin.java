package dev.elsewhere.gear3.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.Gear3Hooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server damage entry point: gear3 trinket bonuses on incoming damage (no per-tick work). */
@Mixin(LivingEntity.class)
public abstract class Gear3LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float gear3$modifyDamage(float amount, @Local(argsOnly = true) DamageSource source) {
		return Gear3Hooks.modifyIncoming((LivingEntity) (Object) this, source, amount);
	}
}
