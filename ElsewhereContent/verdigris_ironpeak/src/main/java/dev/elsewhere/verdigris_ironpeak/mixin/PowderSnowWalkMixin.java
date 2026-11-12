package dev.elsewhere.verdigris_ironpeak.mixin;

import dev.elsewhere.ironpeak.IronpeakItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Crampons and Climber Boots walk on powder snow (vanilla only allows leather boots). */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowWalkMixin {
	@Inject(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"), cancellable = true)
	private static void elsewhere$crampons(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ() && entity instanceof LivingEntity l) {
			var boots = l.getItemBySlot(EquipmentSlot.FEET);
			if (boots.is(IronpeakItems.CRAMPONS) || boots.is(IronpeakItems.CLIMBER_BOOTS)) cir.setReturnValue(true);
		}
	}
}
