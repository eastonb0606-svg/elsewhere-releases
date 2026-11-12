package dev.elsewhere.rivals.hrafnholt.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.elsewhere.rivals.hrafnholt.HrafnholtGear;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Wolfskin Boots ice-grip: the block friction used by ground movement (client and server) is plain-ground friction on ice for the wearer. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityIceMixin {
	@ModifyExpressionValue(method = "travelInAir", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/Block;getFriction()F"))
	private float elsewhereHrafnholt$iceGrip(float friction) {
		return HrafnholtGear.iceGrip((LivingEntity) (Object) this, friction);
	}
}
