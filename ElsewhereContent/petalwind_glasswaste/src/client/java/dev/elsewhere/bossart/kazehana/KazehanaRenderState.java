package dev.elsewhere.bossart.kazehana;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Client copy of Kazehana's (or a Petal Mirage's) duel state and animation timers. */
public class KazehanaRenderState extends LivingEntityRenderState {
	public int duelState;
	public boolean dormant, mirage;
	public final AnimationState bow = new AnimationState();
	public final AnimationState iaido = new AnimationState();
	public final AnimationState dash = new AnimationState();
	public final AnimationState combo = new AnimationState();
	public final AnimationState parry = new AnimationState();
	public final AnimationState stun = new AnimationState();
	public final AnimationState phase = new AnimationState();
	public final AnimationState gale = new AnimationState();
}
