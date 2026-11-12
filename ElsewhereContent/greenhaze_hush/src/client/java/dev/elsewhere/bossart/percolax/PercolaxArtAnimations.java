package dev.elsewhere.bossart.percolax;

import static dev.elsewhere.bossart.greenhaze.Kf.*;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Percolax, timed to the server's attack ticks (Big Rip 30 t inhale + 15 t exhale, Stomp 25 t, Flick 15 t, Cough 60 t). */
public final class PercolaxArtAnimations {
	private PercolaxArtAnimations() {}

	/** Bubbling water chamber, swaying smoke neck, drifting arms. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("chamber", scale(s(0F, 1F, 1F, 1F), s(0.4F, 1.03F, 0.96F, 1.03F), s(0.8F, 0.98F, 1.04F, 0.98F), s(1.5F, 1.03F, 0.97F, 1.03F),
			s(2.1F, 0.99F, 1.03F, 0.99F), s(3.0F, 1F, 1F, 1F)))
		.addAnimation("neck", rot(r(0F, 0F, 0F, 0F), r(1.5F, 2F, 0F, 2.5F), r(3.0F, 0F, 0F, 0F)))
		.addAnimation("mouthpiece", rot(r(0F, 0F, 0F, 0F), r(1.5F, 0F, 6F, 0F), r(3.0F, 0F, 0F, 0F)))
		.addAnimation("right_arm", rot(r(0F, 0F, 0F, 0F), r(1.5F, -4F, 0F, 3F), r(3.0F, 0F, 0F, 0F)))
		.addAnimation("left_arm", rot(r(0F, 0F, 0F, 0F), r(1.5F, 4F, 0F, -3F), r(3.0F, 0F, 0F, 0F)))
		.addAnimation("bowl", scale(s(0F, 1F, 1F, 1F), s(0.75F, 1.08F, 1.08F, 1.08F), s(1.5F, 1F, 1F, 1F), s(2.25F, 1.08F, 1.08F, 1.08F), s(3.0F, 1F, 1F, 1F)))
		.build();

	/** Heavy two-legged stomp walk with a body roll and counter-swinging arms (driven by walk position). */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_leg", rot(r(0F, 24F, 0F, 0F), r(0.8F, -24F, 0F, 0F), r(1.6F, 24F, 0F, 0F)))
		.addAnimation("left_leg", rot(r(0F, -24F, 0F, 0F), r(0.8F, 24F, 0F, 0F), r(1.6F, -24F, 0F, 0F)))
		.addAnimation("right_arm", rot(r(0F, -18F, 0F, 0F), r(0.8F, 18F, 0F, 0F), r(1.6F, -18F, 0F, 0F)))
		.addAnimation("left_arm", rot(r(0F, 18F, 0F, 0F), r(0.8F, -18F, 0F, 0F), r(1.6F, 18F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 0F, 0F, -4F), r(0.4F, 0F, 0F, 0F), r(0.8F, 0F, 0F, 4F), r(1.2F, 0F, 0F, 0F), r(1.6F, 0F, 0F, -4F)))
		.addAnimation("body", pos(p(0F, 0F, 0F, 0F), p(0.4F, 0F, 1.5F, 0F), p(0.8F, 0F, 0F, 0F), p(1.2F, 0F, 1.5F, 0F), p(1.6F, 0F, 0F, 0F)))
		.addAnimation("neck", rot(r(0F, 0F, 0F, 3F), r(0.8F, 0F, 0F, -3F), r(1.6F, 0F, 0F, 3F)))
		.build();

	/** Big Rip inhale (1.5 s): leans back, neck tilts up, both hands grip the neck, the chamber swells and churns. Holds at the end until the exhale. */
	public static final AnimationDefinition BIG_RIP = AnimationDefinition.Builder.withLength(1.5F)
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(0.5F, -10F, 0F, 0F), r(1.5F, -16F, 0F, 0F)))
		.addAnimation("neck", rot(r(0F, 0F, 0F, 0F), r(0.6F, -14F, 0F, 0F), r(1.5F, -22F, 0F, 0F)))
		.addAnimation("right_arm", rot(r(0F, 0F, 0F, 0F), r(0.5F, -95F, 0F, -25F), r(1.5F, -110F, 0F, -30F)))
		.addAnimation("left_arm", rot(r(0F, 0F, 0F, 0F), r(0.5F, -110F, 0F, 25F), r(1.5F, -125F, 0F, 30F)))
		.addAnimation("chamber", scale(s(0F, 1F, 1F, 1F), s(0.3F, 1.1F, 0.95F, 1.1F), s(0.5F, 1.02F, 1.08F, 1.02F), s(0.7F, 1.14F, 0.98F, 1.14F),
			s(0.9F, 1.06F, 1.12F, 1.06F), s(1.1F, 1.18F, 1.02F, 1.18F), s(1.3F, 1.12F, 1.16F, 1.12F), s(1.5F, 1.2F, 1.12F, 1.2F)))
		.addAnimation("neck", scale(s(0F, 1F, 1F, 1F), s(1.5F, 1.12F, 1.06F, 1.12F)))
		.addAnimation("mouthpiece", scale(s(0F, 1F, 1F, 1F), s(1.5F, 1.15F, 1.3F, 1.15F)))
		.build();

	/** Exhale (0.75 s): the neck whips forward and blasts the cone, the chamber deflates back to rest. */
	public static final AnimationDefinition EXHALE = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("body", rot(r(0F, -16F, 0F, 0F), r(0.15F, 14F, 0F, 0F), r(0.6F, 10F, 0F, 0F), r(1.0F, 0F, 0F, 0F)))
		.addAnimation("neck", rot(r(0F, -22F, 0F, 0F), r(0.12F, 28F, 0F, 0F), r(0.6F, 22F, 0F, 0F), r(1.0F, 0F, 0F, 0F)))
		.addAnimation("right_arm", rot(r(0F, -110F, 0F, -30F), r(0.15F, -30F, 0F, 40F), r(0.6F, -25F, 0F, 35F), r(1.0F, 0F, 0F, 0F)))
		.addAnimation("left_arm", rot(r(0F, -125F, 0F, 30F), r(0.15F, -20F, 0F, -40F), r(0.6F, -15F, 0F, -35F), r(1.0F, 0F, 0F, 0F)))
		.addAnimation("chamber", scale(s(0F, 1.2F, 1.12F, 1.2F), s(0.15F, 0.9F, 0.9F, 0.9F), s(0.5F, 0.95F, 0.95F, 0.95F), s(1.0F, 1F, 1F, 1F)))
		.addAnimation("mouthpiece", scale(s(0F, 1.15F, 1.3F, 1.15F), s(0.12F, 1.35F, 0.8F, 1.35F), s(0.6F, 1.1F, 1F, 1.1F), s(1.0F, 1F, 1F, 1F)))
		.build();

	/** Bubble Stomp: 1.25 s wind-up with both fists raised over its head, slam into the floor, 0.5 s recovery. */
	public static final AnimationDefinition STOMP = AnimationDefinition.Builder.withLength(1.75F)
		.addAnimation("right_arm", rot(r(0F, 0F, 0F, 0F), r(0.9F, -165F, 0F, 10F), r(1.15F, -170F, 0F, 12F), r(1.3F, -40F, 0F, 0F), r(1.75F, 0F, 0F, 0F)))
		.addAnimation("left_arm", rot(r(0F, 0F, 0F, 0F), r(0.9F, -165F, 0F, -10F), r(1.15F, -170F, 0F, -12F), r(1.3F, -40F, 0F, 0F), r(1.75F, 0F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(1.15F, -12F, 0F, 0F), r(1.3F, 18F, 0F, 0F), r(1.75F, 0F, 0F, 0F)))
		.addAnimation("body", pos(p(0F, 0F, 0F, 0F), p(1.15F, 0F, 3F, 0F), p(1.3F, 0F, -3F, 0F), p(1.75F, 0F, 0F, 0F)))
		.addAnimation("right_leg", rot(r(0F, 0F, 0F, 0F), r(1.15F, -10F, 0F, 6F), r(1.3F, 0F, 0F, 10F), r(1.75F, 0F, 0F, 0F)))
		.addAnimation("left_leg", rot(r(0F, 0F, 0F, 0F), r(1.15F, -10F, 0F, -6F), r(1.3F, 0F, 0F, -10F), r(1.75F, 0F, 0F, 0F)))
		.addAnimation("chamber", scale(s(0F, 1F, 1F, 1F), s(1.25F, 1F, 1F, 1F), s(1.32F, 1.2F, 0.8F, 1.2F), s(1.5F, 0.95F, 1.08F, 0.95F), s(1.75F, 1F, 1F, 1F)))
		.build();

	/** Ember Flick (0.75 s): the side bowl tips and flicks three embers, the left hand swats. */
	public static final AnimationDefinition FLICK = AnimationDefinition.Builder.withLength(0.9F)
		.addAnimation("downstem", rot(r(0F, 0F, 0F, 0F), r(0.15F, 0F, 0F, -20F), r(0.3F, -30F, 0F, 25F), r(0.45F, -10F, 0F, -15F), r(0.6F, -30F, 0F, 25F), r(0.9F, 0F, 0F, 0F)))
		.addAnimation("left_arm", rot(r(0F, 0F, 0F, 0F), r(0.2F, -60F, -30F, 20F), r(0.35F, -80F, 30F, -10F), r(0.9F, 0F, 0F, 0F)))
		.addAnimation("bowl", scale(s(0F, 1F, 1F, 1F), s(0.3F, 1.3F, 1.3F, 1.3F), s(0.6F, 1.3F, 1.3F, 1.3F), s(0.9F, 1F, 1F, 1F)))
		.build();

	/** Coughing fit (looped while the 3 s stagger lasts): doubled over, hacking, glass core exposed. */
	public static final AnimationDefinition COUGH = AnimationDefinition.Builder.withLength(0.6F).looping()
		.addAnimation("body", rot(r(0F, 22F, 0F, 0F), r(0.1F, 32F, 0F, 0F), r(0.3F, 24F, 0F, 0F), r(0.6F, 22F, 0F, 0F)))
		.addAnimation("neck", rot(r(0F, 18F, 0F, 0F), r(0.1F, 34F, 0F, 0F), r(0.3F, 14F, 0F, 0F), r(0.6F, 18F, 0F, 0F)))
		.addAnimation("right_arm", rot(r(0F, -70F, 0F, -35F), r(0.6F, -70F, 0F, -35F)))
		.addAnimation("left_arm", rot(r(0F, 20F, 0F, 10F), r(0.1F, 30F, 0F, 15F), r(0.6F, 20F, 0F, 10F)))
		.addAnimation("chamber", scale(s(0F, 1F, 1F, 1F), s(0.1F, 0.85F, 1.12F, 0.85F), s(0.3F, 1.05F, 0.95F, 1.05F), s(0.6F, 1F, 1F, 1F)))
		.addAnimation("right_leg", rot(r(0F, -20F, 0F, 0F), r(0.6F, -20F, 0F, 0F)))
		.addAnimation("left_leg", rot(r(0F, -20F, 0F, 0F), r(0.6F, -20F, 0F, 0F)))
		.addAnimation("whole", pos(p(0F, 0F, -4F, 0F), p(0.6F, 0F, -4F, 0F)))
		.build();

	/** Closing a vent (looped): both hands shove the shutter down. */
	public static final AnimationDefinition CLOSE = AnimationDefinition.Builder.withLength(1.0F).looping()
		.addAnimation("right_arm", rot(r(0F, -80F, 0F, -10F), r(0.5F, -45F, 0F, -10F), r(1.0F, -80F, 0F, -10F)))
		.addAnimation("left_arm", rot(r(0F, -95F, 0F, 10F), r(0.5F, -50F, 0F, 10F), r(1.0F, -95F, 0F, 10F)))
		.addAnimation("body", rot(r(0F, 12F, 0F, 0F), r(0.5F, 22F, 0F, 0F), r(1.0F, 12F, 0F, 0F)))
		.build();

	/** Phase transition (2 s, the invulnerable window): rears back, arms flung wide, smoke boils up the neck, then a forward bellow. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(0.5F, -18F, 0F, 0F), r(1.2F, -20F, 0F, 0F), r(1.45F, 12F, 0F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("neck", rot(r(0F, 0F, 0F, 0F), r(0.5F, -20F, 0F, 0F), r(1.0F, -24F, 8F, 0F), r(1.2F, -24F, -8F, 0F), r(1.45F, 22F, 0F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("right_arm", rot(r(0F, 0F, 0F, 0F), r(0.5F, -40F, 0F, 70F), r(1.2F, -50F, 0F, 80F), r(1.45F, -30F, 0F, 30F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("left_arm", rot(r(0F, 0F, 0F, 0F), r(0.5F, -40F, 0F, -70F), r(1.2F, -50F, 0F, -80F), r(1.45F, -30F, 0F, -30F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("chamber", scale(s(0F, 1F, 1F, 1F), s(0.4F, 1.15F, 1.1F, 1.15F), s(0.8F, 1.05F, 1.2F, 1.05F), s(1.2F, 1.2F, 1.15F, 1.2F), s(1.5F, 0.92F, 0.92F, 0.92F), s(2.0F, 1F, 1F, 1F)))
		.addAnimation("neck", scale(s(0F, 1F, 1F, 1F), s(1.2F, 1.12F, 1.08F, 1.12F), s(1.5F, 1F, 1F, 1F)))
		.build();

	/** Death (1 s, from deathTime): shudders, the chamber cracks and drains, the neck topples and the colossus slumps. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("body", rot(rl(0F, 0F, 0F, 0F), rl(0.1F, 0F, 0F, 4F), rl(0.2F, 0F, 0F, -4F), rl(0.3F, 0F, 0F, 4F), r(0.6F, 18F, 0F, -6F), r(1.0F, 30F, 0F, -10F)))
		.addAnimation("whole", pos(p(0F, 0F, 0F, 0F), p(0.4F, 0F, 0F, 0F), p(1.0F, 0F, -12F, 0F)))
		.addAnimation("chamber", scale(s(0F, 1F, 1F, 1F), s(0.3F, 1.05F, 1F, 1.05F), s(1.0F, 1F, 0.15F, 1F)))
		.addAnimation("chamber", pos(p(0F, 0F, 0F, 0F), p(0.3F, 0F, 0F, 0F), p(1.0F, 0F, -6F, 0F)))
		.addAnimation("neck", rot(r(0F, 0F, 0F, 0F), r(0.4F, 6F, 0F, 10F), r(1.0F, 40F, 0F, 35F)))
		.addAnimation("right_arm", rot(r(0F, 0F, 0F, 0F), r(1.0F, 20F, 0F, -25F)))
		.addAnimation("left_arm", rot(r(0F, 0F, 0F, 0F), r(1.0F, 15F, 0F, 30F)))
		.addAnimation("right_leg", rot(r(0F, 0F, 0F, 0F), r(1.0F, -70F, 0F, 10F)))
		.addAnimation("left_leg", rot(r(0F, 0F, 0F, 0F), r(1.0F, -70F, 0F, -10F)))
		.build();
}
