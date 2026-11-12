package dev.elsewhere.gear3.mixin.client.starpiercer;

import dev.elsewhere.gear3.s3.starpiercer.client.StarpiercerZoom;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Starpiercer longbow zoom (same maths as the Roc-Feather's mixin). Vanilla returns lerp(effectScale, 1, modifier); we
 * recover the modifier, multiply in the zoom and re-apply the same lerp, so the FOV Effects slider scales it like vanilla.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class StarpiercerZoomMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void elsewhere$starpiercerZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		float zoom = StarpiercerZoom.factor((AbstractClientPlayer) (Object) this, firstPerson);
		if (zoom >= 0.9999F || effectScale <= 0.0F) return;
		float result = cir.getReturnValueF();
		float modifier = 1.0F + (result - 1.0F) / effectScale;
		cir.setReturnValue(1.0F + effectScale * (modifier * zoom - 1.0F));
	}
}
