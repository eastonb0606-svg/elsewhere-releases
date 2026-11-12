package dev.elsewhere.kingdom.idlemixin;

import dev.elsewhere.kingdom.client.idle.IdleAnim;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3.0 polish P9 (s-p9-idle): idle/work poses go on at the end of {@link HumanoidModel#setupAnim}. Hooking the humanoid base
 * (not only {@code PlayerModel}) makes the armor and tabard models, which are set up separately from the same render state,
 * take the same pose as the body. Every state that is not an {@link IdleAnim.Holder} with an active track returns at once.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelIdleMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void elsewhere$idlePose(HumanoidRenderState state, CallbackInfo ci) {
		if (state instanceof IdleAnim.Holder h) {
			IdleAnim.Track t = h.idleTrack();
			if (t != null && t.isOn()) IdleAnim.apply((HumanoidModel<?>) (Object) this, t, state);
		}
	}
}
