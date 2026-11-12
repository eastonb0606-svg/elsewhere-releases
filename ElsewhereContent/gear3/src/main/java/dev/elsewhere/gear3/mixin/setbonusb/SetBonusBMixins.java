package dev.elsewhere.gear3.mixin.setbonusb;

import com.llamalad7.mixinextras.sugar.Local;
import dev.elsewhere.gear3.setbonus.b.RidgewatchSteadyAim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hooks of the (B) set bonuses (s-setbonus-b). Each handler is one early-out unless the shooter wears the set. */
public final class SetBonusBMixins {
	private SetBonusBMixins() {}

	/** Ridgewatch "Steady Aim": a steady full-draw bow shot has no spread (inaccuracy 0). Server only (shoot takes a ServerLevel). */
	@Mixin(ProjectileWeaponItem.class)
	public abstract static class Shoot {
		@ModifyVariable(method = "shoot", at = @At("HEAD"), argsOnly = true, ordinal = 1)
		private float gear3sbb$steadyAim(float inaccuracy, @Local(argsOnly = true, ordinal = 0) LivingEntity shooter, @Local(argsOnly = true) boolean fullDraw) {
			return RidgewatchSteadyAim.inaccuracy((ProjectileWeaponItem) (Object) this, shooter, fullDraw, inaccuracy);
		}
	}
}
