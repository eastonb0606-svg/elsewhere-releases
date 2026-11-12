package dev.elsewhere.kingdom.client.lod.mixin;

import dev.elsewhere.kingdom.client.lod.BattleFx;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Battle sound limits (ARMIES.md 3.7): every sound, delayed ones included, starts through {@code SoundEngine.play};
 * {@link BattleFx#allowSound} decides (and may play the battle din instead).
 */
@Mixin(SoundEngine.class)
abstract class SoundEngineLodMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void elsewhere$armyLod(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (!BattleFx.allowSound((SoundEngine) (Object) this, instance)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}
}
