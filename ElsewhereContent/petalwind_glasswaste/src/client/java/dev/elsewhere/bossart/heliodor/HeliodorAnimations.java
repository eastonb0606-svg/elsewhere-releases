package dev.elsewhere.bossart.heliodor;

import static dev.elsewhere.bossart.glasswaste.Kf.*;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Heliodor, timed to the server: lance 2 s charge, slam 1 s wind-up, 3 s stun, 2 s phase roar, 1 s death. */
public final class HeliodorAnimations {
	private HeliodorAnimations() {}

	/** Engine breathing: the chassis rises and settles, arms drift, the lens bobs. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", pos(p(0, 0, 0, 0), p(2, 0, 0.7F, 0), p(4, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 3), r(2, -4, 0, 5), r(4, 0, 0, 3)))
		.addAnimation("left_arm", rot(r(0, 0, 0, -3), r(2, -4, 0, -5), r(4, 0, 0, -3)))
		.addAnimation("halo", scale(s(0, 1.0F), s(2, 1.06F), s(4, 1.0F)))
		.build();

	/** Heavy golem stride: legs swing, arms counter-swing, the chassis rolls and dips. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.4F, 24, 0, 0), r(0.8F, 0, 0, 0), r(1.2F, -24, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(0.4F, -24, 0, 0), r(0.8F, 0, 0, 0), r(1.2F, 24, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -16, 0, 0), r(0.8F, 0, 0, 0), r(1.2F, 16, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, 16, 0, 0), r(0.8F, 0, 0, 0), r(1.2F, -16, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, 0, 0, 3), r(0.8F, 0, 0, 0), r(1.2F, 0, 0, -3), r(1.6F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.4F, 0, -1.2F, 0), p(0.8F, 0, 0, 0), p(1.2F, 0, -1.2F, 0), p(1.6F, 0, 0, 0)))
		.build();

	/** Solar Lance: 2 s charge (arms raise to frame the sun-face, the chassis leans back, the face swells), fire at 2.0 s, recoil. */
	public static final AnimationDefinition LANCE = AnimationDefinition.Builder.withLength(2.7F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.5F, -8, 0, 0), r(1.9F, -12, 0, 0), r(2.05F, -22, 0, 0), r(2.7F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(1.9F, 0, 0, 0), p(2.05F, 0, 0, 4), p(2.7F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.6F, -75, 0, 30), r(1.9F, -85, 0, 38), r(2.05F, -60, 0, 55), r(2.7F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.6F, -75, 0, -30), r(1.9F, -85, 0, -38), r(2.05F, -60, 0, -55), r(2.7F, 0, 0, 0)))
		.addAnimation("face", scale(s(0, 1.0F), s(1.6F, 1.12F), s(1.95F, 1.2F), s(2.05F, 0.9F), s(2.7F, 1.0F)))
		.addAnimation("pupil", scale(s(0, 1.0F), s(1.9F, 1.8F), s(2.05F, 0.6F), s(2.7F, 1.0F)))
		.addAnimation("halo", scale(s(0, 1.0F), s(1.9F, 1.25F), s(2.05F, 1.45F), s(2.7F, 1.0F)))
		.build();

	/** Fist slam: 1 s wind-up with both fists overhead, strike at 1.0 s, recover. */
	public static final AnimationDefinition SLAM = AnimationDefinition.Builder.withLength(1.7F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.8F, -14, 0, 0), r(1.0F, 26, 0, 0), r(1.3F, 22, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.8F, 0, 2, 0), p(1.0F, 0, -5, -2), p(1.3F, 0, -5, -2), p(1.7F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.8F, -170, 0, -12), r(1.0F, -40, 0, -6), r(1.3F, -40, 0, -6), r(1.7F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.8F, -170, 0, 12), r(1.0F, -40, 0, 6), r(1.3F, -40, 0, 6), r(1.7F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.8F, -12, 0, 0), r(1.0F, 14, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(1.0F, -12, 0, 0), r(1.3F, -12, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(1.0F, -12, 0, 0), r(1.3F, -12, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("right_leg", pos(p(0, 0, 0, 0), p(1.0F, 0, -3, 0), p(1.3F, 0, -3, 0), p(1.7F, 0, 0, 0)))
		.addAnimation("left_leg", pos(p(0, 0, 0, 0), p(1.0F, 0, -3, 0), p(1.3F, 0, -3, 0), p(1.7F, 0, 0, 0)))
		.build();

	/** Mirror crack: jolt back, then sag with lolling head and dangling arms for the 3 s stun. */
	public static final AnimationDefinition CRACK = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.15F, -16, 0, 4), r(0.6F, 18, 0, -3), r(1.6F, 15, 0, 3), r(2.6F, 18, 0, -3), r(3.0F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.15F, 0, 0, 3), p(0.6F, 0, -5, 0), p(2.6F, 0, -5, 0), p(3.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.15F, -10, 0, 20), r(0.6F, 25, 0, 12), r(1.6F, 22, 0, -10), r(2.6F, 25, 0, 12), r(3.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.15F, -20, 0, 35), r(0.6F, 12, 0, 4), r(2.6F, 12, 0, 4), r(3.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.15F, -20, 0, -35), r(0.6F, 12, 0, -4), r(2.6F, 12, 0, -4), r(3.0F, 0, 0, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.6F, -14, 0, 0), r(2.6F, -14, 0, 0), r(3.0F, 0, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(0.6F, -14, 0, 0), r(2.6F, -14, 0, 0), r(3.0F, 0, 0, 0)))
		.addAnimation("right_leg", pos(p(0, 0, 0, 0), p(0.6F, 0, -5, 0), p(2.6F, 0, -5, 0), p(3.0F, 0, 0, 0)))
		.addAnimation("left_leg", pos(p(0, 0, 0, 0), p(0.6F, 0, -5, 0), p(2.6F, 0, -5, 0), p(3.0F, 0, 0, 0)))
		.addAnimation("halo", scale(s(0, 1.0F), s(0.3F, 0.75F), s(2.6F, 0.75F), s(3.0F, 1.0F)))
		.build();

	/** Overheat vent: arms fling wide, head thrown back, chest out, halo flares. */
	public static final AnimationDefinition VENT = AnimationDefinition.Builder.withLength(1.6F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, -10, 0, 0), r(1.1F, -10, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -25, 0, 0), r(1.1F, -25, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -20, 0, 75), r(1.1F, -20, 0, 75), r(1.6F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, -20, 0, -75), r(1.1F, -20, 0, -75), r(1.6F, 0, 0, 0)))
		.addAnimation("halo", scale(s(0, 1.0F), s(0.4F, 1.4F), s(1.1F, 1.4F), s(1.6F, 1.0F)))
		.build();

	/** Noon Eternal: 1.5 s turn-and-aim telegraph, then the face sweeps across the scorched path for 0.6 s. */
	public static final AnimationDefinition SWEEP = AnimationDefinition.Builder.withLength(2.4F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(1.4F, -6, -28, 0), r(1.5F, -6, -28, 0), r(2.1F, -6, 28, 0), r(2.4F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(1.0F, -150, 0, 20), r(2.1F, -150, 0, 20), r(2.4F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(1.0F, -150, 0, -20), r(2.1F, -150, 0, -20), r(2.4F, 0, 0, 0)))
		.addAnimation("face", scale(s(0, 1.0F), s(1.4F, 1.25F), s(2.1F, 1.25F), s(2.4F, 1.0F)))
		.addAnimation("halo", scale(s(0, 1.0F), s(1.4F, 1.5F), s(2.1F, 1.5F), s(2.4F, 1.0F)))
		.build();

	/** Phase transition (2 s, driven by the synced transition timer): crouch, then burst open with arms high and halo blazing. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, 16, 0, 0), r(0.7F, -20, 0, 0), r(1.6F, -18, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.4F, 0, -4, 0), p(0.7F, 0, 1, 0), p(1.6F, 0, 1, 0), p(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, 20, 0, 0), r(0.7F, -35, 0, 4), r(0.9F, -35, 0, -4), r(1.1F, -35, 0, 4), r(1.3F, -35, 0, -4),
			r(1.6F, -30, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -30, 0, -8), r(0.7F, -40, 0, 100), r(1.6F, -40, 0, 95), r(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, -30, 0, 8), r(0.7F, -40, 0, -100), r(1.6F, -40, 0, -95), r(2.0F, 0, 0, 0)))
		.addAnimation("halo", scale(s(0, 1.0F), s(0.4F, 0.8F), s(0.7F, 1.6F), s(1.6F, 1.5F), s(2.0F, 1.0F)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.4F, -10, 0, 0), r(0.7F, 0, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(0.4F, -10, 0, 0), r(0.7F, 0, 0, 0)))
		.build();

	/** Death (1 s, driven by deathTime): the engine kneels and folds forward as its light dies. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.3F, -10, 0, 0), r(1.0F, 32, 0, 6)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.3F, 0, 0, 0), p(1.0F, 0, -10, -2)))
		.addAnimation("right_leg", pos(p(0, 0, 0, 0), p(0.3F, 0, 0, 0), p(1.0F, 0, -10, 0)))
		.addAnimation("left_leg", pos(p(0, 0, 0, 0), p(0.3F, 0, 0, 0), p(1.0F, 0, -10, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.3F, 0, 0, 0), r(1.0F, -50, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(0.3F, 0, 0, 0), r(1.0F, -50, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, -20, 0, 0), r(1.0F, 38, 0, 16)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.3F, -30, 0, 40), r(1.0F, -15, 0, 10)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.3F, -30, 0, -40), r(1.0F, -15, 0, -10)))
		.addAnimation("halo", scale(s(0, 1.0F), s(0.3F, 1.3F), s(1.0F, 0.5F)))
		.build();
}
