package dev.elsewhere.bossart.tempestra;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Client copy of Tempestra's shield, phase and attack timers. */
public class TempestraRenderState extends LivingEntityRenderState {
	/** 0 = rings closed around her (shielded), 1 = rings opened wide. Smoothed on the client. */
	public float ringsOpen;
	public int phase;
	public final AnimationState volley = new AnimationState();
	public final AnimationState downburst = new AnimationState();
	public final AnimationState phaseRoar = new AnimationState();
	public final AnimationState bolt = new AnimationState();
	public final AnimationState strike = new AnimationState();
}
