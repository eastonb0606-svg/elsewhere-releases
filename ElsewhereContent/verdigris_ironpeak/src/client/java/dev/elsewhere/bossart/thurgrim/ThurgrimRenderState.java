package dev.elsewhere.bossart.thurgrim;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

@Environment(EnvType.CLIENT)
public class ThurgrimRenderState extends LivingEntityRenderState {
	public final AnimationState split = new AnimationState();
	public final AnimationState roar = new AnimationState();
	public final AnimationState combo = new AnimationState();
	public final AnimationState boomerang = new AnimationState();
	public final AnimationState command = new AnimationState();
	public int attack;
	public boolean transition;
}
