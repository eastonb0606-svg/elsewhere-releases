package dev.elsewhere.bossart.rimeback;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Everything RimebackModel needs, copied at extract time (setupAnim runs later, at flush). Millis < 0 = not playing. */
public class RimebackRenderState extends LivingEntityRenderState {
	public int action;
	public long actionMillis;
	public int follow;
	public long followMillis = -1;
	public long transMillis = -1;
	public long shatterMillis = -1;
	public boolean holding;
	public boolean shattered;
}
