package dev.elsewhere.petalwind_glasswaste.anim3mixin;

import dev.elsewhere.petalwind_glasswaste.anim3.Hurt3Holder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 3.0 (P8): carries the boss flinch clock from extraction to drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class RenderStateHurt3Mixin implements Hurt3Holder {
	@Unique
	private float elsewhere$pgwhurt3 = -1F;

	@Override
	public float elsewhere$pgwHurt3() {
		return elsewhere$pgwhurt3;
	}

	@Override
	public void elsewhere$pgwSetHurt3(float clock) {
		elsewhere$pgwhurt3 = clock;
	}
}
