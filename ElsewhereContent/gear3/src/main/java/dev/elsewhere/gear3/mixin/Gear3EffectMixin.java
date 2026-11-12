package dev.elsewhere.gear3.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.Gear3Hooks;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Binding Sigil: effects a player applies to a mob (addEffect with a source). */
@Mixin(LivingEntity.class)
public abstract class Gear3EffectMixin {
	@ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private MobEffectInstance gear3$sigil(MobEffectInstance effect, @Local(argsOnly = true) Entity source) {
		return Gear3Hooks.sigil((LivingEntity) (Object) this, effect, source);
	}
}
