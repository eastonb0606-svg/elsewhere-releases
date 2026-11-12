package dev.elsewhere.greenhaze_hush.mixin;

import dev.elsewhere.hush.HushTrips;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Accidental trip trigger: a Warden targeting you (post-dragon only). */
@Mixin(Warden.class)
public class WardenTargetMixin {
	@Inject(method = "setAttackTarget", at = @At("HEAD"))
	private void elsewhere$targeted(LivingEntity target, CallbackInfo ci) {
		if (target instanceof ServerPlayer p) HushTrips.onWardenTarget(p);
	}
}
