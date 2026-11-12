package dev.elsewhere.rivals.hrafnholt.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server damage entry: Wolf-Rally (Iron Crown), Hearth-Ward fire resistance. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float elsewhereHrafnholt$scale(float amount, @Local(argsOnly = true) DamageSource source) {
		return HrafnholtGear.scaleIncoming((LivingEntity) (Object) this, source, amount);
	}
}
