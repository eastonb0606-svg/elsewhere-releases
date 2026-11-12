package dev.elsewhere.gear3.mixin.reliquary;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.s3.reliquary.EchoKnight;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.gameevent.GameEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Echo Reliquary: the Echo Knight's blows, and hits it takes, make no ENTITY_DAMAGE vibration (sculk sensors, shriekers,
 * Wardens and the Hush's noise bus never hear its fights). Wraps the single gameEvent call in actuallyHurt; one instanceof
 * pair per hit, nothing ticks. Every other hit is unchanged.
 */
@Mixin(LivingEntity.class)
public abstract class EchoSilenceMixin {
	@WrapOperation(method = "actuallyHurt", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;gameEvent(Lnet/minecraft/core/Holder;)V"))
	private void gear3$echoSilentHit(LivingEntity self, Holder<GameEvent> event, Operation<Void> original, @Local(argsOnly = true) DamageSource source) {
		if (EchoKnight.vibrates(self, source)) original.call(self, event);
	}
}
