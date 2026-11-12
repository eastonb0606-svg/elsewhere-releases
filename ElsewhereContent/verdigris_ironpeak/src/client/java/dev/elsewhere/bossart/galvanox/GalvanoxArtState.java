package dev.elsewhere.bossart.galvanox;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.AnimationState;

/** Render state of Galvanox: attack animation states, phase roar, plate count and waxed/stunned flags. */
public class GalvanoxArtState extends LivingEntityRenderState {
	public final AnimationState hammer = new AnimationState();
	public final AnimationState bolt = new AnimationState();
	public final AnimationState wax = new AnimationState();
	public final AnimationState roar = new AnimationState();
	public int attack;
	public int phase;
	public int plates = 8;
	public boolean waxed;
	public boolean stunned;
}
