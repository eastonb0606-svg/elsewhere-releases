package dev.elsewhere.verdigris_ironpeak.mixin.client;

import dev.elsewhere.verdigris_ironpeak.client.LongbowZoom;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Roc-Feather Longbow zoom. Vanilla returns lerp(effectScale, 1, modifier); we recover the modifier, multiply in the
 * zoom and re-apply the same lerp, so the FOV Effects slider scales the zoom exactly like vanilla's bow FOV change.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerZoomMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void elsewhere$longbowZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
		float zoom = LongbowZoom.factor((AbstractClientPlayer) (Object) this, firstPerson);
		if (zoom >= 0.9999F || effectScale <= 0.0F) return;
		float result = cir.getReturnValueF();
		float modifier = 1.0F + (result - 1.0F) / effectScale;
		cir.setReturnValue(1.0F + effectScale * (modifier * zoom - 1.0F));
	}
}
