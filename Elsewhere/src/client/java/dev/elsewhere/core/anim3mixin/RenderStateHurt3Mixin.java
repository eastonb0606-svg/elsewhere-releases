package dev.elsewhere.core.anim3mixin;

import dev.elsewhere.core.anim3.Hurt3Holder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 3.0 (P8): carries the boss flinch clock from extraction to drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class RenderStateHurt3Mixin implements Hurt3Holder {
	@Unique
	private float elsewhere$corehurt3 = -1F;

	@Override
	public float elsewhere$coreHurt3() {
		return elsewhere$corehurt3;
	}

	@Override
	public void elsewhere$coreSetHurt3(float clock) {
		elsewhere$corehurt3 = clock;
	}
}
