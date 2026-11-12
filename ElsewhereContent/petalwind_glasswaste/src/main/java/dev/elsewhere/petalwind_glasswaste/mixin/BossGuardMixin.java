package dev.elsewhere.petalwind_glasswaste.mixin;

import dev.elsewhere.petalwind_glasswaste.BossGuards;
import dev.elsewhere.stub.petalwind_glasswaste.BossBase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Difficulty framework guards (STUB until the core's boss framework owns them):
 * overpower guard: damage to a boss above 24 per hit is halved past 24, before armor;
 * lethal-hit guard: no single boss hit takes more than 60% of a player's max health after armor and enchantments.
 */
@Mixin(LivingEntity.class)
abstract class BossGuardMixin {
	@ModifyVariable(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), argsOnly = true)
	private float elsewhere_pg$overpowerGuard(float damage, DamageSource source) {
		if ((Object) this instanceof BossBase && damage > BossGuards.OVERPOWER_THRESHOLD) return BossGuards.overpower(damage);
		return damage;
	}

	@Inject(method = "getDamageAfterMagicAbsorb", at = @At("RETURN"), cancellable = true)
	private void elsewhere_pg$lethalHitGuard(DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof Player player && BossGuards.fromBoss(source)) {
			float cap = player.getMaxHealth() * BossGuards.LETHAL_CAP;
			if (cir.getReturnValue() > cap) cir.setReturnValue(cap);
		}
	}
}
