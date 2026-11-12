package dev.elsewhere.bossart.verdigrim;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Keyframe animations for Archdeacon Verdigrim, timed to VerdigrimBoss: slam = 1.7 s with the hit at 1.2 s (tick 24),
 * note/arpeggio/chord/flood = 1.0 s (20 ticks), phase roar = 2.0 s (the 40-tick transition), death = 1.0 s.
 */
public final class VerdigrimAnimations {
	private VerdigrimAnimations() {}

	private static Keyframe r(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	private static Keyframe l(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	private static Keyframe p(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	private static AnimationChannel rot(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, k);
	}

	private static AnimationChannel pos(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.POSITION, k);
	}

	/** Slow breathing sway, staff swaying with it, robe stirring as if underwater. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", rot(r(0, 0, 0, 0), r(1, 2, 0, 1), r(2, 0, 0, 0), r(3, 2, 0, -1), r(4, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(1, -3, 0, 2), r(2, 0, 0, 0), r(3, -3, 0, -2), r(4, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, -4), r(2, -6, 0, -7), r(4, 0, 0, -4)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 3), r(2, 3, 0, 5), r(4, 0, 0, 3)))
		.addAnimation("robe", rot(r(0, 0, 0, 0), r(1.3F, 4, 0, 0), r(2.6F, -3, 0, 0), r(4, 0, 0, 0)))
		.addAnimation("mitre_glow", new AnimationChannel(AnimationChannel.Targets.SCALE,
			new Keyframe(0, KeyframeAnimations.scaleVec(1, 1, 1), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2, KeyframeAnimations.scaleVec(1.15, 1.05, 1.3), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(4, KeyframeAnimations.scaleVec(1, 1, 1), AnimationChannel.Interpolations.CATMULLROM)))
		.build();

	/** Heavy wading stride: legs under the robe, free arm swinging, staff planted forward. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_leg", rot(r(0, -25, 0, 0), r(0.8F, 25, 0, 0), r(1.6F, -25, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 25, 0, 0), r(0.8F, -25, 0, 0), r(1.6F, 25, 0, 0)))
		.addAnimation("left_arm", rot(r(0, -20, 0, 0), r(0.8F, 20, 0, 0), r(1.6F, -20, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 6, 0, 0), r(0.8F, -10, 0, 0), r(1.6F, 6, 0, 0)))
		.addAnimation("robe", rot(r(0, 8, 0, 0), r(0.4F, 0, 0, 0), r(0.8F, -8, 0, 0), r(1.2F, 0, 0, 0), r(1.6F, 8, 0, 0)))
		.addAnimation("body", rot(r(0, 4, 0, 3), r(0.8F, 4, 0, -3), r(1.6F, 4, 0, 3)))
		.addAnimation("bone", pos(p(0, 0, 0, 0), p(0.4F, 0, 0.8F, 0), p(0.8F, 0, 0, 0), p(1.2F, 0, 0.8F, 0), p(1.6F, 0, 0, 0)))
		.build();

	/** Crozier slam: both hands lift the trident-crozier overhead (the concept-art pose), strike at 1.2 s, recover. */
	public static final AnimationDefinition SLAM = AnimationDefinition.Builder.withLength(1.7F)
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.3F, -140, 0, 20), r(1.0F, -165, 0, 12), l(1.1F, -168, 0, 10),
			l(1.2F, -45, 0, 0), r(1.35F, -50, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.3F, -140, 0, -20), r(1.0F, -165, 0, -12), l(1.1F, -168, 0, -10),
			l(1.2F, -45, 0, -20), r(1.35F, -50, 0, -20), r(1.7F, 0, 0, 0)))
		.addAnimation("staff", rot(r(0, 0, 0, 0), r(0.3F, -10, 0, 0), r(1.0F, -15, 0, 0), l(1.2F, 30, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.3F, -10, 0, 0), r(1.0F, -16, 0, 0), l(1.1F, -16, 0, 0), l(1.2F, 28, 0, 0),
			r(1.35F, 24, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, -20, 0, 0), r(1.0F, -25, 0, 0), l(1.2F, 10, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("bone", pos(p(0, 0, 0, 0), p(1.0F, 0, 0.5F, 0), p(1.1F, 0, 0.6F, 0), p(1.2F, 0, -2.5F, -1), p(1.4F, 0, -2, -1), p(1.7F, 0, 0, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(1.1F, 0, 0, 0), l(1.2F, -25, 0, 0), r(1.45F, -22, 0, 0), r(1.7F, 0, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(1.1F, 0, 0, 0), l(1.2F, 15, 0, 0), r(1.45F, 12, 0, 0), r(1.7F, 0, 0, 0)))
		.build();

	/** Conducting a note: the free hand sweeps up and down on the beat, the crozier lifts. */
	public static final AnimationDefinition NOTE = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.3F, -120, 0, -25), r(0.55F, -50, 0, -15), r(1.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -35, 0, 10), r(1.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, -15, 0, 0), r(0.55F, 12, 0, 0), r(1.0F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.3F, -6, 0, 0), r(0.55F, 6, 0, 0), r(1.0F, 0, 0, 0)))
		.build();

	/** Arpeggio: both hands play rapid runs on an invisible manual. */
	public static final AnimationDefinition ARPEGGIO = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.15F, -80, 0, -10), r(0.3F, -60, 0, -20), r(0.45F, -85, 0, -5),
			r(0.6F, -60, 0, -20), r(0.75F, -85, 0, -10), r(1.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.15F, -60, 0, 15), r(0.3F, -85, 0, 5), r(0.45F, -60, 0, 20),
			r(0.6F, -85, 0, 5), r(0.75F, -60, 0, 15), r(1.0F, 0, 0, 0)))
		.addAnimation("staff", rot(r(0, 0, 0, 0), r(0.15F, 50, 0, 0), r(0.85F, 50, 0, 0), r(1.0F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.2F, 8, 0, 3), r(0.5F, 8, 0, -3), r(0.8F, 8, 0, 3), r(1.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.25F, 10, -10, 0), r(0.5F, 10, 10, 0), r(0.75F, 10, -10, 0), r(1.0F, 0, 0, 0)))
		.build();

	/** The great chord: arms flung wide and high, head thrown back. */
	public static final AnimationDefinition CHORD = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.25F, -30, 0, -110), r(0.8F, -35, 0, -115), r(1.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.25F, -30, 0, 110), r(0.8F, -35, 0, 115), r(1.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.25F, -30, 0, 0), r(0.8F, -30, 0, 0), r(1.0F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.25F, -10, 0, 0), r(0.8F, -10, 0, 0), r(1.0F, 0, 0, 0)))
		.build();

	/** Calling the flood: arms rise from low to high, palms up, as the water climbs. */
	public static final AnimationDefinition FLOOD = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.2F, -20, 0, -40), r(0.7F, -170, 0, -30), r(1.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.2F, -20, 0, 40), r(0.7F, -170, 0, 30), r(1.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.7F, -25, 0, 0), r(1.0F, 0, 0, 0)))
		.build();

	/** Phase change: a silent, shuddering roar with arms spread and the mitre seam flaring. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, -45, 0, -95), r(1.6F, -50, 0, -100), r(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -45, 0, 95), r(1.6F, -50, 0, 100), r(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -40, 0, 0), l(0.6F, -38, 0, 4), l(0.8F, -40, 0, -4), l(1.0F, -38, 0, 4),
			l(1.2F, -40, 0, -4), l(1.4F, -38, 0, 4), r(1.6F, -40, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, -18, 0, 0), r(1.6F, -18, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("mitre_glow", new AnimationChannel(AnimationChannel.Targets.SCALE,
			new Keyframe(0, KeyframeAnimations.scaleVec(1, 1, 1), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.4F, KeyframeAnimations.scaleVec(1.6, 1.3, 2), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.6F, KeyframeAnimations.scaleVec(1.6, 1.3, 2), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.0F, KeyframeAnimations.scaleVec(1, 1, 1), AnimationChannel.Interpolations.CATMULLROM)))
		.build();

	/** Death: drops to his knees, the crozier slips, and he topples forward into the water. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("bone", rot(r(0, 0, 0, 0), r(0.35F, 8, 0, 0), r(1.0F, 80, 0, 4)))
		.addAnimation("bone", pos(p(0, 0, 0, 0), p(0.35F, 0, -5, 0), p(1.0F, 0, -6, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.35F, -80, 10, 0), r(1.0F, -85, 10, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(0.35F, -80, -10, 0), r(1.0F, -85, -10, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.35F, 15, 0, 0), r(1.0F, 10, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.35F, 25, 0, 10), r(1.0F, 30, 0, 15)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.35F, -10, 0, 25), r(1.0F, -60, 0, 30)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.35F, -10, 0, -25), r(1.0F, -60, 0, -30)))
		.addAnimation("staff", rot(r(0, 0, 0, 0), r(0.35F, 30, 0, 40), r(1.0F, 80, 0, 70)))
		.build();
}
