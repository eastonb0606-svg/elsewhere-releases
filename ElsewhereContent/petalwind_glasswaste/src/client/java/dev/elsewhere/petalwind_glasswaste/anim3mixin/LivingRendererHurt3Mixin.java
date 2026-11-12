package dev.elsewhere.petalwind_glasswaste.anim3mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.petalwind_glasswaste.anim3.Flinch3;
import dev.elsewhere.petalwind_glasswaste.anim3.Hurt3Holder;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 3.0 (P8): the boss hurt flinch, on every renderer of this module's bosses (they all extend LivingEntityRenderer). */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingRendererHurt3Mixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
	private void elsewhere$pgwextractHurt3(LivingEntity e, LivingEntityRenderState s, float pt, CallbackInfo ci) {
		((Hurt3Holder) s).elsewhere$pgwSetHurt3(Flinch3.clock(e, pt, s.distanceToCameraSq));
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
	private void elsewhere$pgwflinch3(LivingEntityRenderState s, PoseStack pose, float bodyRot, float scale, CallbackInfo ci) {
		Flinch3.apply(pose, ((Hurt3Holder) s).elsewhere$pgwHurt3());
	}
}
