package dev.elsewhere.bossart.overworld_a;

import dev.elsewhere.stub.overworld_bosses.ElsewhereBoss;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Render state shared by the overworld_a boss art: the boss's synced animation states plus death time. */
public class OwaRenderState extends LivingEntityRenderState {
	public final AnimationState[] actions = new AnimationState[ElsewhereBoss.MAX_ACTIONS];
	public final AnimationState idle = new AnimationState();
	public final AnimationState transition = new AnimationState();
	public float deathTicks;
	public int phase = 1;
	public int action;

	public OwaRenderState() {
		for (int i = 0; i < this.actions.length; i++) this.actions[i] = new AnimationState();
	}
}
