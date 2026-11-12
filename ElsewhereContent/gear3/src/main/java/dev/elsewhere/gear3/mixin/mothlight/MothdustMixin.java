package dev.elsewhere.gear3.mixin.mothlight;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.s3.mothlight.Mothlight;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Mothlight Staff: a mob carrying Mothdust deals 10% less damage (server damage entry point; one effect lookup per hit, no ticking). */
@Mixin(LivingEntity.class)
public abstract class MothdustMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float gear3$mothdustOutgoing(float amount, @Local(argsOnly = true) DamageSource source) {
		return Mothlight.outgoing((LivingEntity) (Object) this, source, amount);
	}
}
