package dev.elsewhere.bossart.vorlagh;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class VorlaghTentacleRenderState extends LivingEntityRenderState {
	public int tentacleState;
	public int prevState;
	public long stateMillis;
	public boolean severed;
	public int index;
}
