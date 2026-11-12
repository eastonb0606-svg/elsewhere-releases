package dev.elsewhere.boss_music.mixin;

import dev.elsewhere.boss_music.BossMusicService;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pauses vanilla (and the older per-module boss) music while a boss theme from this module is playing. */
@Mixin(MusicManager.class)
abstract class MusicManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void elsewhere$pauseForBossTheme(CallbackInfo ci) {
		if (BossMusicService.suppressVanilla()) ci.cancel();
	}
}
