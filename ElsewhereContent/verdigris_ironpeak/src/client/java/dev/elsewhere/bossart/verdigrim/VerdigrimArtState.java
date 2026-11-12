package dev.elsewhere.bossart.verdigrim;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Render state of Archdeacon Verdigrim: one animation state per telegraphed attack plus the phase roar. */
public class VerdigrimArtState extends LivingEntityRenderState {
	public final AnimationState slam = new AnimationState();
	public final AnimationState note = new AnimationState();
	public final AnimationState flood = new AnimationState();
	public final AnimationState arpeggio = new AnimationState();
	public final AnimationState chord = new AnimationState();
	public final AnimationState roar = new AnimationState();
	public int attack;
	public int phase;
	public boolean stunned;
}
