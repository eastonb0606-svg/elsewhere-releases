package dev.elsewhere.greenhaze_hush.mixin;

import dev.elsewhere.hush.HushItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Unheard (full Hushweave): mobs that haven't spotted you must come within half their usual range to notice you. Mob
 * targeting scales its follow range by the target's visibility percent, so halving it halves the notice distance.
 */
@Mixin(LivingEntity.class)
public class LivingEntityVisibilityMixin {
	@Inject(method = "getVisibilityPercent", at = @At("RETURN"), cancellable = true)
	private void elsewhere$unheard(ServerLevel level, Entity looker, CallbackInfoReturnable<Double> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (HushItems.unheard(self)) cir.setReturnValue(cir.getReturnValueD() * 0.5);
	}
}
