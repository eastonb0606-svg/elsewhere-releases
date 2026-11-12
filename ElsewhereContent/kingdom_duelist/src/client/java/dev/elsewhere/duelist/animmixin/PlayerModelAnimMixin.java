package dev.elsewhere.duelist.animmixin;

import dev.elsewhere.duelist.client.DuelistRenderer;
import dev.elsewhere.duelist.client.anim.DuelAnim;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 3.0 (P4): 26.3 draws every {@link AvatarRenderState} with the vanilla avatar renderer, so the human bosses' move
 * poses go on at the end of {@link PlayerModel#setupAnim}. Real players and every other avatar return at once.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelAnimMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void elsewhere$bossMovePose(AvatarRenderState state, CallbackInfo ci) {
		if (state instanceof DuelistRenderer.BossRenderState s && s.animOn) DuelAnim.apply((PlayerModel) (Object) this, s);
	}
}
