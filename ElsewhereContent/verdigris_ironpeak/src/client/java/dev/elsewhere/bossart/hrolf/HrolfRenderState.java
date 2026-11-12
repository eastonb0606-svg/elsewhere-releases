package dev.elsewhere.bossart.hrolf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

@Environment(EnvType.CLIENT)
public class HrolfRenderState extends LivingEntityRenderState {
	public final AnimationState swing = new AnimationState();
	public final AnimationState overhead = new AnimationState();
	public final AnimationState kick = new AnimationState();
	public final AnimationState warcry = new AnimationState();
	public final AnimationState rally = new AnimationState();
	/** Horn blast time in ms (-1 when not blowing); shifted past the 2 s phase transition it starts in. */
	public long hornMillis = -1;
	public int attack;
	public boolean transition;
	public boolean riding;
}
