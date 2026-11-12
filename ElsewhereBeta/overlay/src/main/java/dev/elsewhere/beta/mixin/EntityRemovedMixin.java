package dev.elsewhere.beta.mixin;

import dev.elsewhere.beta.BossBarSweep;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Clears an Elsewhere boss's bars on every removal path (see {@link BossBarSweep}). */
@Mixin(Entity.class)
public abstract class EntityRemovedMixin {
	@Inject(method = "setRemoved", at = @At("TAIL"))
	private void elsewhere_beta$clearBossBars(Entity.RemovalReason reason, CallbackInfo ci) {
		BossBarSweep.onRemoved((Entity) (Object) this);
	}
}
