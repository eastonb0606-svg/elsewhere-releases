package dev.elsewhere.gear3.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.elsewhere.gear3.s3.dredge.TidebrassDredgeItem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tidebrass Dredge (s-dredge): "full speed underwater". While the dredge is in the main hand, {@code Player.getDestroySpeed} skips its
 * two water penalties: eyes in water (x submerged_mining_speed, 0.2) and not on the ground while in water (/5). Common code, so the
 * client's dig progress and the server's check agree. Runs only on the mining path (no tick).
 */
@Mixin(Player.class)
public abstract class TidebrassDredgeMixin {
	@ModifyExpressionValue(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
	private boolean gear3$dredgeSubmerged(boolean eyeInWater) {
		return eyeInWater && !TidebrassDredgeItem.ignoresSubmergedPenalty((Player) (Object) this);
	}

	@ModifyExpressionValue(method = "getDestroySpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
	private boolean gear3$dredgeAfloat(boolean onGround) {
		return onGround || TidebrassDredgeItem.ignoresAfloatPenalty((Player) (Object) this);
	}
}
