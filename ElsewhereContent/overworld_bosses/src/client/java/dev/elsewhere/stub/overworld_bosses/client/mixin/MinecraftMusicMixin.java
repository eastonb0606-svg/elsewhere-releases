package dev.elsewhere.stub.overworld_bosses.client.mixin;

import dev.elsewhere.stub.overworld_bosses.client.BossMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boss music hook: while an Elsewhere boss bar is on screen, its track replaces the situational music. STUB. */
@Mixin(Minecraft.class)
abstract class MinecraftMusicMixin {
	@Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void elsewhere$bossMusic(CallbackInfoReturnable<Music> cir) {
		Music music = BossMusic.current((Minecraft) (Object) this);
		if (music != null) cir.setReturnValue(music);
	}
}
