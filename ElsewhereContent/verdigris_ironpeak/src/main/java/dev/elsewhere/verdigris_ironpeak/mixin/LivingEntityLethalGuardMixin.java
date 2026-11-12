package dev.elsewhere.verdigris_ironpeak.mixin;

import dev.elsewhere.stub.verdigris_ironpeak.BossGuards;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lethal-hit guard (Difficulty framework): no single boss hit takes more than 60% of a player's max health after armor. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLethalGuardMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void elsewhere$lethalGuard(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self instanceof Player && BossGuards.fromBoss(source)) {
			cir.setReturnValue(BossGuards.lethalCap(cir.getReturnValueF(), self.getMaxHealth()));
		}
	}
}
