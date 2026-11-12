package dev.elsewhere.arsenal.client.anim.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.arsenal.client.anim.UseAnims;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P6 first-person weapon animations: our own swing curve (and trail) instead of the vanilla whack for the flail,
 * rapier and instant casters, and a small held-in-use motion for the channelled casters.
 */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonUseAnimMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void elsewhere$beginArm(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light,
		CallbackInfo ci) {
		UseAnims.beginArm(itemStack, collector);
	}

	@Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
	private void elsewhere$swing(float animation, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
		if (UseAnims.firstPersonSwing(animation, poseStack, invert)) ci.cancel();
	}

	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void elsewhere$heldUse(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks, float xRot,
		InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector collector, int light,
		CallbackInfo ci) {
		var avatar = playerState.avatarRenderState;
		if (avatar == null || !avatar.isUsingItem || avatar.useItemHand != hand || state.useItemRemainingTicks <= 0) return;
		boolean main = hand == InteractionHand.MAIN_HAND;
		int useDuration = main ? state.mainHandUseDuration : state.offHandUseDuration;
		float ticksUsed = useDuration - (state.useItemRemainingTicks - partialTicks + 1.0F);
		HumanoidArm armSide = main ? avatar.mainArm : avatar.mainArm.getOpposite();
		UseAnims.firstPersonUse(poseStack, itemStack, true, Math.max(0F, ticksUsed), armSide == HumanoidArm.RIGHT ? 1 : -1);
	}
}
