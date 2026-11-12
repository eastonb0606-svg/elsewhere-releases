package dev.elsewhere.bossart.heliodor;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Client copy of what Heliodor's model needs each frame. */
public class HeliodorRenderState extends LivingEntityRenderState {
	public final AnimationState lance = new AnimationState();
	public final AnimationState slam = new AnimationState();
	public final AnimationState crack = new AnimationState();
	public final AnimationState vent = new AnimationState();
	public final AnimationState sweep = new AnimationState();
	/** Seconds into the phase-transition roar, or -1. */
	public float roarTime = -1;
	public int phase = 1, cracks;
	public boolean stunned;
}
