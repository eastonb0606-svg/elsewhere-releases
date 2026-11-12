package dev.elsewhere.stub.overworld_bosses.mixin;

import dev.elsewhere.stub.overworld_bosses.BossDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lethal-hit guard: a boss hit never takes more than 60% of a player's max health after armor. STUB. */
@Mixin(LivingEntity.class)
abstract class LethalHitGuardMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void elsewhere$lethalHitGuard(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		float capped = BossDamage.lethalGuard((LivingEntity) (Object) this, source, cir.getReturnValueF());
		if (capped != cir.getReturnValueF()) cir.setReturnValue(capped);
	}
}
