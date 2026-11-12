package dev.elsewhere.bossart.percolax;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

public class PercolaxArtState extends LivingEntityRenderState {
	public final AnimationState idle = new AnimationState();
	public final AnimationState bigRip = new AnimationState();
	public final AnimationState exhale = new AnimationState();
	public final AnimationState stomp = new AnimationState();
	public final AnimationState flick = new AnimationState();
	public final AnimationState cough = new AnimationState();
	public final AnimationState close = new AnimationState();
	public final AnimationState roar = new AnimationState();
	/** 0 Sativa, 1 Indica, 2 Hybrid (synced from the boss). */
	public int strain;
	public boolean strainShown;
	public boolean hidden;
	public boolean ember;
}
