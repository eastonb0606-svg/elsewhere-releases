package dev.elsewhere.bossart.grumblegut;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class GrumblegutArtState extends LivingEntityRenderState {
	public final AnimationState idle = new AnimationState();
	public final AnimationState paw = new AnimationState();
	public final AnimationState charge = new AnimationState();
	public final AnimationState munch = new AnimationState();
	public final AnimationState sniff = new AnimationState();
	public final AnimationState chew = new AnimationState();
	public final AnimationState slam = new AnimationState();
	public final AnimationState yawn = new AnimationState();
	public final AnimationState nap = new AnimationState();
	public final AnimationState puff = new AnimationState();
	public final AnimationState roar = new AnimationState();
	public boolean napping;
}
