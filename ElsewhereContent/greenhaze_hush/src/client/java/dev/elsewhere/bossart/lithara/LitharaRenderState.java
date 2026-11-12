package dev.elsewhere.bossart.lithara;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class LitharaRenderState extends LivingEntityRenderState {
	public final AnimationState idleState = new AnimationState();
	public final AnimationState singState = new AnimationState();
	public final AnimationState conductState = new AnimationState();
	public final AnimationState shatteredState = new AnimationState();
	public final AnimationState psalmState = new AnimationState();
	public final AnimationState rainState = new AnimationState();
	public final AnimationState shardsState = new AnimationState();
	public final AnimationState vexState = new AnimationState();
	public final AnimationState roarState = new AnimationState();
	public boolean armored = true;
	public int anim;
	/** Alpha of the emissive layer (eyes, hand crystals, spires). */
	public float glow = 1F;
}
