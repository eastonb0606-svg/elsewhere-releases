package dev.elsewhere.arsenal.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.arsenal.spell.Calling;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Server damage entry: Vanguard and Marksman multiply the full damage (calling_weapons), never against players. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityCallingMixin {
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private float elsewhereArsenal$calling(float amount, @Local(argsOnly = true) DamageSource source) {
		return Calling.scaleHurt((LivingEntity) (Object) this, source, amount);
	}
}
