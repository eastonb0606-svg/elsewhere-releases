package dev.elsewhere.bossart.mother_creak;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Render state for Mother Creak's art model. */
public class MotherCreakArtState extends LivingEntityRenderState {
	public boolean awake;
	public boolean watched;
	public boolean fury;
	public final AnimationState twitch = new AnimationState();
	public final AnimationState claw = new AnimationState();
	public final AnimationState burst = new AnimationState();
	public final AnimationState stun = new AnimationState();
	public final AnimationState roar = new AnimationState();
}
