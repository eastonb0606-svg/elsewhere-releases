package dev.elsewhere.overworld_bosses.anim3mixin;

import dev.elsewhere.overworld_bosses.anim3.Hurt3Holder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** 3.0 (P8): carries the boss flinch clock from extraction to drawing. */
@Mixin(LivingEntityRenderState.class)
public abstract class RenderStateHurt3Mixin implements Hurt3Holder {
	@Unique
	private float elsewhere$owbhurt3 = -1F;

	@Override
	public float elsewhere$owbHurt3() {
		return elsewhere$owbhurt3;
	}

	@Override
	public void elsewhere$owbSetHurt3(float clock) {
		elsewhere$owbhurt3 = clock;
	}
}
