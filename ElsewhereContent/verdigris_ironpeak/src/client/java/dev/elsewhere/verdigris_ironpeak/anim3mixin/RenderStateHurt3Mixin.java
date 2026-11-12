package dev.elsewhere.verdigris_ironpeak.anim3mixin;

import dev.elsewhere.verdigris_ironpeak.anim3.Hurt3Holder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 3.0 (P8): carries the boss flinch clock from extraction to drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class RenderStateHurt3Mixin implements Hurt3Holder {
	@Unique
	private float elsewhere$viphurt3 = -1F;

	@Override
	public float elsewhere$vipHurt3() {
		return elsewhere$viphurt3;
	}

	@Override
	public void elsewhere$vipSetHurt3(float clock) {
		elsewhere$viphurt3 = clock;
	}
}
