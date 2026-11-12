package dev.elsewhere.kingdom.client.lod.mixin;

import dev.elsewhere.kingdom.client.lod.BattleFx;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Battle particle limits (ARMIES.md 3.7): every world particle passes {@code createParticle}; {@link BattleFx#allowParticle} decides. */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineLodMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void elsewhere$armyLod(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
		if (!BattleFx.allowParticle(options, x, y, z)) cir.setReturnValue(null);
	}
}
