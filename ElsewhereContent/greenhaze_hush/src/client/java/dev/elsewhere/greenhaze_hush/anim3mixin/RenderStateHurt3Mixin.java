package dev.elsewhere.greenhaze_hush.anim3mixin;

import dev.elsewhere.greenhaze_hush.anim3.Hurt3Holder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 3.0 (P8): carries the boss flinch clock from extraction to drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class RenderStateHurt3Mixin implements Hurt3Holder {
	@Unique
	private float elsewhere$ghhhurt3 = -1F;

	@Override
	public float elsewhere$ghhHurt3() {
		return elsewhere$ghhhurt3;
	}

	@Override
	public void elsewhere$ghhSetHurt3(float clock) {
		elsewhere$ghhhurt3 = clock;
	}
}
