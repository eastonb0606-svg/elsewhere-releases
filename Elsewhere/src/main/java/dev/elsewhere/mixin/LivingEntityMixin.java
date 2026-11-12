package dev.elsewhere.mixin;

import dev.elsewhere.core.gear.GearRules;
import dev.elsewhere.palehollow.PaleHollowGear;
import dev.elsewhere.tiergear.TierGear;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float elsewhere$tierDamage(final float amount, final ServerLevel level, final DamageSource source) {
		return TierGear.modifyDamage((LivingEntity)(Object)this, source, amount);
	}

	@Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
	private void elsewhere$lockout(final DamageSource source, final CallbackInfoReturnable<Boolean> cir) {
		if (GearRules.blocks((LivingEntity)(Object)this)) {
			cir.setReturnValue(false);
		}
	}

	@Inject(method = "checkTotemDeathProtection", at = @At("RETURN"))
	private void elsewhere$protected(final DamageSource source, final CallbackInfoReturnable<Boolean> cir) {
		if (Boolean.TRUE.equals(cir.getReturnValue())) {
			GearRules.triggered((LivingEntity)(Object)this);
		}
	}

	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void elsewhere$paleweave(final ServerLevel level, final Entity looker, final CallbackInfoReturnable<Double> cir) {
		double f = PaleHollowGear.noticeFactor((LivingEntity)(Object)this, looker);
		if (f < 1.0) {
			cir.setReturnValue(cir.getReturnValue() * f);
		}
	}
}
