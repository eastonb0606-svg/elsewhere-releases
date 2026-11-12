package dev.elsewhere.bossart.vorlagh;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Copied at extract time for VorlaghModel (setupAnim runs at flush). Millis < 0 = not playing. */
public class VorlaghRenderState extends LivingEntityRenderState {
	public int action;
	public long actionMillis;
	public int follow;
	public long followMillis = -1;
	public long transMillis = -1;
}
