package dev.elsewhere.rivals.hrafnholt.mixin;

import dev.elsewhere.rivals.hrafnholt.boss.HrafnholtStandards;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Raven Standard (Hersir's last stand) is an armor stand with its own 60-point health: vanilla would break it in two hits. */
@Mixin(ArmorStand.class)
public abstract class ArmorStandStandardMixin {
	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void elsewhereHrafnholt$standard(ServerLevel level, DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
		if (HrafnholtStandards.onHit((ArmorStand) (Object) this, level, source, amount)) cir.setReturnValue(true);
	}
}
