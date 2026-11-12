package dev.elsewhere.kingdom_court.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.kingdom_court.CourtCombat;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server damage entry: Crown Rally, Spurs mount guard, Hunter's Mark and the Ashgrove Longbow. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float elsewhereCourt$scale(float amount, @Local(argsOnly = true) DamageSource source) {
		return CourtCombat.scaleIncoming((LivingEntity) (Object) this, source, amount);
	}
}
