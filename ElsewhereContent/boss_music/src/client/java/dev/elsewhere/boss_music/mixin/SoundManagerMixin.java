package dev.elsewhere.boss_music.mixin;

import dev.elsewhere.boss_music.RealmGuard;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 3.0 realm guard (see {@link RealmGuard}): while a boss theme plays, a kingdom realm track ({@code *.realm}, MUSIC) is not
 * started on top of it, and the court's "stop all music" before each 240 s realm resend does not cut the theme.
 */
@Mixin(SoundManager.class)
abstract class SoundManagerMixin {
	@Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)Lnet/minecraft/client/sounds/SoundEngine$PlayResult;", at = @At("HEAD"), cancellable = true)
	private void elsewhere$realmGuard(SoundInstance s, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (RealmGuard.blockPlay(s)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}

	@Inject(method = "stop(Lnet/minecraft/resources/Identifier;Lnet/minecraft/sounds/SoundSource;)V", at = @At("HEAD"), cancellable = true)
	private void elsewhere$keepTheme(Identifier id, SoundSource source, CallbackInfo ci) {
		if (RealmGuard.blockStopAll(id, source)) ci.cancel();
	}
}
