package dev.elsewhere.arsenal.client.anim.mixin;

import dev.elsewhere.arsenal.client.anim.UseAnims;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** P6 third-person weapon animations: the flail arc, rapier lunge and caster point replace vanilla's attack swing. */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelUseAnimMixin {
	@Unique
	private final float[] elsewhere$anim = new float[7];

	@Inject(method = "setupAttackAnimation", at = @At("HEAD"), cancellable = true)
	private void elsewhere$attack(HumanoidRenderState state, CallbackInfo ci) {
		if (UseAnims.thirdPersonSwing((HumanoidModel<?>) (Object) this, state, elsewhere$anim)) ci.cancel();
	}
}
