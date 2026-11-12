package dev.elsewhere.stub.nether_end_bosses.mixin.client;

import dev.elsewhere.stub.nether_end_bosses.BossMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Boss tracks override ambient music during a fight and stop on win or loss. */
@Mixin(Minecraft.class)
public abstract class MinecraftMusicMixin {
	@Inject(method = "getSituationalMusic", at = @At("HEAD"), cancellable = true)
	private void elsewhere$bossMusic(CallbackInfoReturnable<Music> cir) {
		Minecraft mc = (Minecraft) (Object) this;
		if (mc.gui.screen() != null) return;
		Music music = BossMusic.current(mc);
		if (music != null) cir.setReturnValue(music);
	}
}
