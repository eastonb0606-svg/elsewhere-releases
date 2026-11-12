package dev.elsewhere.stub.nether_end_bosses;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;
import org.jspecify.annotations.Nullable;

/** Render state shared by boss renderers: phase, flags, the running attack and its age, and the keyframe animation clocks. */
public class BossRenderState extends LivingEntityRenderState {
	public int phase = 1;
	public boolean transition;
	public boolean dormant;
	public int flags;
	public @Nullable String attack;
	public float attackAge;
	public float partialTick;
	public final AnimationState transitionAnim = new AnimationState();
	public final AnimationState attackAnim = new AnimationState();

	public boolean flag(int f) {
		return (flags & f) != 0;
	}
}
