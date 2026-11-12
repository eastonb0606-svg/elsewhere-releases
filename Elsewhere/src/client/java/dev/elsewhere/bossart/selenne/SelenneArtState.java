package dev.elsewhere.bossart.selenne;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Render state for Selenne's art model. */
public class SelenneArtState extends LivingEntityRenderState {
	public boolean awake;
	public boolean lidsVisible = true;
	public boolean fullMoon;
	public final AnimationState open = new AnimationState();
	public final AnimationState close = new AnimationState();
	public final AnimationState beam = new AnimationState();
	public final AnimationState petals = new AnimationState();
	public final AnimationState tear = new AnimationState();
}
