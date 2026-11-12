package dev.elsewhere.bossart.tempestra;

import static dev.elsewhere.bossart.petalwind.ArtAnim.pos;
import static dev.elsewhere.bossart.petalwind.ArtAnim.rot;
import static dev.elsewhere.bossart.petalwind.ArtAnim.scale;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Tempestra's keyframes: the hovering idle, Gust Volley (5 charges fired at once), Downburst (30-tick red-circle
 * wind-up, then the slam), the phase roar (40 ticks) and the death (20 ticks). Ring and tail spin is procedural.
 */
public final class TempestraAnimations {
	private TempestraAnimations() {}

	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", pos(0, 0, 0, 0, 2, 0, 1.5F, 0, 4, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1, 0, 0, 3, 2, -3, 0, 0, 3, 0, 0, -3, 4, 0, 0, 0))
		.addAnimation("head", pos(0, 0, 0, 0, 1.5F, 0, 0.8F, 0, 3, 0, -0.3F, 0, 4, 0, 0, 0))
		.addAnimation("tail1", rot(0, 0, 0, 0, 1, 6, 0, 4, 2, 0, 0, 8, 3, -6, 0, 4, 4, 0, 0, 0))
		.addAnimation("tail2", rot(0, 4, 0, 0, 1, 0, 0, 6, 2, -4, 0, 0, 3, 0, 0, -6, 4, 4, 0, 0))
		.addAnimation("tail3", rot(0, 0, 0, 6, 1, -6, 0, 0, 2, 0, 0, -6, 3, 6, 0, 0, 4, 0, 0, 6))
		.addAnimation("tail4", rot(0, -8, 0, 0, 1, 0, 0, -8, 2, 8, 0, 0, 3, 0, 0, 8, 4, -8, 0, 0))
		.addAnimation("funnel", scale(0, 1, 1, 1, 2, 1.12F, 1, 1.12F, 4, 1, 1, 1))
		.addAnimation("debris", pos(0, 0, 0, 0, 2, 0, -1.2F, 0, 4, 0, 0, 0))
		.build();

	/** Gust Volley (1 s): rears back with the rings flaring, then snaps forward as the five charges fly. */
	public static final AnimationDefinition VOLLEY = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.3F, -25, 0, 0, 0.45F, 18, 0, 0, 0.8F, 5, 0, 0, 1, 0, 0, 0))
		.addAnimation("head", pos(0, 0, 0, 0, 0.3F, 0, 1, 2, 0.45F, 0, -1, -3, 1, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.3F, -8, 0, 0, 0.45F, 10, 0, 0, 1, 0, 0, 0))
		.addAnimation("ring1", scale(0, 1, 1, 1, 0.3F, 1.3F, 1, 1.3F, 0.45F, 0.85F, 1, 0.85F, 1, 1, 1, 1))
		.addAnimation("ring2", scale(0, 1, 1, 1, 0.3F, 1.35F, 1, 1.35F, 0.45F, 0.85F, 1, 0.85F, 1, 1, 1, 1))
		.addAnimation("ring3", scale(0, 1, 1, 1, 0.3F, 1.4F, 1, 1.4F, 0.45F, 0.85F, 1, 0.85F, 1, 1, 1, 1))
		.build();

	/** Downburst (2 s): rises and draws the rings in tight during the 1.5 s telegraph, then slams the air down. */
	public static final AnimationDefinition DOWNBURST = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", pos(0, 0, 0, 0, 1.2F, 0, 6, 0, 1.45F, 0, 7, 0, 1.6F, 0, -3, 0, 2, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1.2F, 25, 0, 0, 1.45F, 30, 0, 0, 1.6F, -10, 0, 0, 2, 0, 0, 0))
		.addAnimation("ring1", scale(0, 1, 1, 1, 1.2F, 0.6F, 1, 0.6F, 1.45F, 0.55F, 1, 0.55F, 1.6F, 1.6F, 1, 1.6F, 2, 1, 1, 1))
		.addAnimation("ring2", scale(0, 1, 1, 1, 1.2F, 0.6F, 1, 0.6F, 1.45F, 0.55F, 1, 0.55F, 1.6F, 1.7F, 1, 1.7F, 2, 1, 1, 1))
		.addAnimation("ring3", scale(0, 1, 1, 1, 1.2F, 0.6F, 1, 0.6F, 1.45F, 0.55F, 1, 0.55F, 1.6F, 1.8F, 1, 1.8F, 2, 1, 1, 1))
		.addAnimation("debris", pos(0, 0, 0, 0, 1.2F, 0, 4, 0, 1.6F, 0, -3, 0, 2, 0, 0, 0))
		.addAnimation("tail1", scale(0, 1, 1, 1, 1.2F, 1.3F, 0.8F, 1.3F, 1.6F, 0.8F, 1.2F, 0.8F, 2, 1, 1, 1))
		.build();

	/** Phase change (2 s): a howling head-shake with the rings and debris thrown wide. */
	public static final AnimationDefinition PHASE = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.2F, -30, 0, 0, 0.4F, -25, 0, 12, 0.6F, -30, 0, -12, 0.8F, -25, 0, 12, 1.0F, -30, 0, -12, 1.4F, -28, 0, 0,
			2, 0, 0, 0))
		.addAnimation("head", scale(0, 1, 1, 1, 0.2F, 1.15F, 1.15F, 1.15F, 1.4F, 1.15F, 1.15F, 1.15F, 2, 1, 1, 1))
		.addAnimation("ring1", scale(0, 1, 1, 1, 0.3F, 1.8F, 1, 1.8F, 1.4F, 1.8F, 1, 1.8F, 2, 1, 1, 1))
		.addAnimation("ring2", scale(0, 1, 1, 1, 0.3F, 1.8F, 1, 1.8F, 1.4F, 1.8F, 1, 1.8F, 2, 1, 1, 1))
		.addAnimation("ring3", scale(0, 1, 1, 1, 0.3F, 1.8F, 1, 1.8F, 1.4F, 1.8F, 1, 1.8F, 2, 1, 1, 1))
		.addAnimation("debris", scale(0, 1, 1, 1, 0.3F, 1.5F, 1, 1.5F, 1.4F, 1.5F, 1, 1.5F, 2, 1, 1, 1))
		.addAnimation("body", pos(0, 0, 0, 0, 0.3F, 0, 3, 0, 1.4F, 0, 3, 0, 2, 0, 0, 0))
		.build();

	/** Cyclone bolt (0.8 s): a sharp forward jab of the head, the top ring flicking out with the bolt. */
	public static final AnimationDefinition BOLT = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.15F, -12, 0, 0, 0.3F, 20, 0, 0, 0.8F, 0, 0, 0))
		.addAnimation("head", pos(0, 0, 0, 0, 0.15F, 0, 0, 1.5F, 0.3F, 0, 0, -3, 0.8F, 0, 0, 0))
		.addAnimation("ring1", scale(0, 1, 1, 1, 0.3F, 1.5F, 1, 1.5F, 0.8F, 1, 1, 1))
		.build();

	/** Stormrim (1.5 s): looks up and calls the lightning down onto the marked spot (30-tick telegraph). */
	public static final AnimationDefinition STRIKE = AnimationDefinition.Builder.withLength(1.8F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -35, 0, 0, 1.3F, -40, 0, 0, 1.5F, 25, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("body", pos(0, 0, 0, 0, 0.4F, 0, 3, 0, 1.3F, 0, 4, 0, 1.5F, 0, -1, 0, 1.8F, 0, 0, 0))
		.addAnimation("ring3", pos(0, 0, 0, 0, 0.4F, 0, 10, 0, 1.3F, 0, 12, 0, 1.5F, 0, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("ring2", pos(0, 0, 0, 0, 0.4F, 0, 5, 0, 1.3F, 0, 6, 0, 1.5F, 0, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("ring3", scale(0, 1, 1, 1, 0.4F, 0.7F, 1, 0.7F, 1.3F, 0.6F, 1, 0.6F, 1.5F, 1.5F, 1, 1.5F, 1.8F, 1, 1, 1))
		.build();

	/** Death (driven by deathTime, 1 s): the rings fly apart, the tail unravels and the head sinks and tips over. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("head", rot(0, 0, 0, 0, 1, 40, 0, 25))
		.addAnimation("head", pos(0, 0, 0, 0, 1, 0, -10, 0))
		.addAnimation("ring1", scale(0, 1, 1, 1, 1, 2.5F, 1, 2.5F))
		.addAnimation("ring2", scale(0, 1, 1, 1, 1, 2.7F, 1, 2.7F))
		.addAnimation("ring3", scale(0, 1, 1, 1, 1, 3.0F, 1, 3.0F))
		.addAnimation("debris", scale(0, 1, 1, 1, 1, 2, 1, 2))
		.addAnimation("debris", pos(0, 0, 0, 0, 1, 0, -8, 0))
		.addAnimation("tail1", scale(0, 1, 1, 1, 1, 0.2F, 0.2F, 0.2F))
		.addAnimation("funnel", scale(0, 1, 1, 1, 1, 0.2F, 1, 0.2F))
		.build();
}
