package dev.elsewhere.verdigris_ironpeak.mixin;

import dev.elsewhere.verdigris_sea.StormcallerHarpoonItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Thrown Stormcaller Harpoon / Galvanic Trident: lightning on hit (rules in StormcallerHarpoonItem). */
@Mixin(ThrownTrident.class)
public abstract class ThrownTridentMixin {
	@Inject(method = "onHitEntity", at = @At("TAIL"))
	private void elsewhere$stormcaller(EntityHitResult hit, CallbackInfo ci) {
		ThrownTrident self = (ThrownTrident) (Object) this;
		ItemStack weapon = self.getWeaponItem();
		if (weapon != null && weapon.getItem() instanceof StormcallerHarpoonItem harpoon && self.level() instanceof ServerLevel level
			&& hit.getEntity() instanceof LivingEntity target) {
			harpoon.thrownHit(level, target, self.getOwner());
		}
	}
}
