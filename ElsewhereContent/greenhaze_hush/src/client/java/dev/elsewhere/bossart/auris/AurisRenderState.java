package dev.elsewhere.bossart.auris;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class AurisRenderState extends LivingEntityRenderState {
	public final AnimationState idleState = new AnimationState();
	public final AnimationState listenState = new AnimationState();
	public final AnimationState chargeState = new AnimationState();
	public final AnimationState smashState = new AnimationState();
	public final AnimationState deafState = new AnimationState();
	public final AnimationState roarState = new AnimationState();
	public boolean deaf;
	public int anim;
	/** Alpha of the emissive layer (membranes, chest core). */
	public float glow = 1F;
}
