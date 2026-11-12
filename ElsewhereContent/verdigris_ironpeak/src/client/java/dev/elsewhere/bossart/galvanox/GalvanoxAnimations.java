package dev.elsewhere.bossart.galvanox;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe animations for Galvanox, timed to GalvanoxBoss: hammer = 1.7 s with the impact at 1.2 s (tick 24),
 * arc bolt = 0.8 s firing at 0.5 s (tick 10), wax lob = 1.0 s releasing at 0.6 s (tick 12), phase roar = 2.0 s,
 * death = 1.0 s.
 */
public final class GalvanoxAnimations {
	private GalvanoxAnimations() {}

	private static Keyframe r(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	private static Keyframe l(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	private static Keyframe p(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	private static Keyframe s(float t, double x, double y, double z) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	private static AnimationChannel rot(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, k);
	}

	private static AnimationChannel pos(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.POSITION, k);
	}

	private static AnimationChannel scale(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.SCALE, k);
	}

	/** Engine idle: slow piston breathing, fists resting on the deck, the lamp throbbing. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("body", rot(r(0, 4, 0, 0), r(1.5F, 6, 0, 0), r(3, 4, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(1.5F, 0, -0.6F, 0), p(3, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, -10, 0, 4), r(1.5F, -13, 0, 6), r(3, -10, 0, 4)))
		.addAnimation("left_arm", rot(r(0, -10, 0, -4), r(1.5F, -13, 0, -6), r(3, -10, 0, -4)))
		.addAnimation("lamp", scale(s(0, 1, 1, 1), s(0.75F, 1.25, 1.25, 1.25), s(1.5F, 1, 1, 1), s(2.25F, 1.25, 1.25, 1.25), s(3, 1, 1, 1)))
		.build();

	/** Heavy stomping gait: shoulders roll, fists swing, the whole frame lurches side to side. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(2.0F).looping()
		.addAnimation("right_leg", rot(r(0, -22, 0, 0), r(1, 22, 0, 0), r(2, -22, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 22, 0, 0), r(1, -22, 0, 0), r(2, 22, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 18, 0, 0), r(1, -18, 0, 0), r(2, 18, 0, 0)))
		.addAnimation("left_arm", rot(r(0, -18, 0, 0), r(1, 18, 0, 0), r(2, -18, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 4, 4), r(1, 0, -4, -4), r(2, 0, 4, 4)))
		.addAnimation("bone", pos(p(0, 0, 0, 0), p(0.5F, 0, 1.2F, 0), p(1, 0, 0, 0), p(1.5F, 0, 1.2F, 0), p(2, 0, 0, 0)))
		.build();

	/** Overhead hammer: both fists rise and lock together (1.2 s raised-fist telegraph), then crash down in front. */
	public static final AnimationDefinition HAMMER = AnimationDefinition.Builder.withLength(1.7F)
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.5F, -150, 0, -18), r(0.95F, -170, 0, -22), l(1.05F, -168, 0, -20), l(1.12F, -172, 0, -22),
			l(1.2F, -50, 0, -10), r(1.4F, -55, 0, -10), r(1.7F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.5F, -150, 0, 18), r(0.95F, -170, 0, 22), l(1.05F, -168, 0, 20), l(1.12F, -172, 0, 22),
			l(1.2F, -50, 0, 10), r(1.4F, -55, 0, 10), r(1.7F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.5F, -10, 0, 0), r(1.1F, -14, 0, 0), l(1.2F, 26, 0, 0), r(1.4F, 22, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.5F, -20, 0, 0), r(1.1F, -22, 0, 0), l(1.2F, 15, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("bone", pos(p(0, 0, 0, 0), p(1.1F, 0, 0.8F, 0), p(1.2F, 0, -2.5F, 0), p(1.4F, 0, -2, 0), p(1.7F, 0, 0, 0)))
		.addAnimation("lamp", scale(s(0, 1, 1, 1), s(1.0F, 1.8, 1.8, 1.8), s(1.2F, 2.2, 2.2, 2.2), s(1.5F, 1, 1, 1)))
		.build();

	/** Arc bolt: right fist thrust at the target, the spire lamp swells, recoil as the bolt leaves at 0.5 s. */
	public static final AnimationDefinition BOLT = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.35F, -95, 0, -8), l(0.5F, -95, 0, -8), r(0.6F, -115, 0, -8), r(0.8F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.35F, -12, 0, 0), r(0.8F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.35F, 6, -10, 0), r(0.6F, -4, -10, 0), r(0.8F, 0, 0, 0)))
		.addAnimation("lamp", scale(s(0, 1, 1, 1), s(0.45F, 2.2, 2.2, 2.2), s(0.55F, 0.8, 0.8, 0.8), s(0.8F, 1, 1, 1)))
		.build();

	/** Wax lob: the left fist scoops back and heaves a shell straight up at 0.6 s. */
	public static final AnimationDefinition WAX = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.3F, 35, 0, -10), l(0.6F, -165, 0, -10), r(0.75F, -160, 0, -10), r(1.0F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.3F, 10, 0, 0), l(0.6F, -12, 0, 0), r(1.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.6F, -30, 0, 0), r(1.0F, 0, 0, 0)))
		.build();

	/** Phase change: the engine rears back, arms flung wide, lens, visor and lamp flaring. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -60, 0, 70), r(1.6F, -65, 0, 75), r(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, -60, 0, -70), r(1.6F, -65, 0, -75), r(2.0F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, -18, 0, 0), l(0.7F, -16, 0, 2), l(1.0F, -18, 0, -2), l(1.3F, -16, 0, 2), r(1.6F, -18, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -35, 0, 0), r(1.6F, -35, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("lamp", scale(s(0, 1, 1, 1), s(0.4F, 2.5, 2.5, 2.5), s(1.6F, 2.5, 2.5, 2.5), s(2.0F, 1, 1, 1)))
		.addAnimation("core", scale(s(0, 1, 1, 1), s(0.4F, 1.25, 1.25, 1.5), s(1.6F, 1.25, 1.25, 1.5), s(2.0F, 1, 1, 1)))
		.build();

	/** Death: the engine seizes, sags to its knees and slumps forward onto its fists, spire drooping. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("bone", pos(p(0, 0, 0, 0), p(0.3F, 0, -1, 0), p(1.0F, 0, -9, -2)))
		.addAnimation("bone", rot(r(0, 0, 0, 0), r(0.3F, 0, 0, 3), r(1.0F, 28, 0, 6)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(1.0F, -70, 0, 10)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(1.0F, -70, 0, -10)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.3F, -20, 0, 15), r(1.0F, -55, 0, 10)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.3F, -20, 0, -15), r(1.0F, -55, 0, -10)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, 10, 0, 8), r(1.0F, 35, 0, 15)))
		.addAnimation("spire", rot(r(0, 0, 0, 0), r(0.5F, 10, 0, 12), r(1.0F, 25, 0, 30)))
		.build();
}
