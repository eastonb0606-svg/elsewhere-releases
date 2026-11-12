package dev.elsewhere.stub.nether_end_bosses.mixin;

import dev.elsewhere.stub.nether_end_bosses.Guards;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lethal-hit guard: no single boss hit takes more than 60% of a player's max health after armor and enchantments. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void elsewhere$lethalGuard(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		float guarded = Guards.lethal((LivingEntity) (Object) this, source, cir.getReturnValueF());
		if (guarded != cir.getReturnValueF()) cir.setReturnValue(guarded);
	}
}
