package dev.elsewhere.bossart.hush;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Tiny shorthands for writing vanilla keyframe animations (degrees / pixels, catmull-rom). */
public final class Kf {
	private Kf() {}

	public static Keyframe r(float t, double x, double y, double z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec((float) x, (float) y, (float) z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe p(float t, double x, double y, double z) {
		return new Keyframe(t, KeyframeAnimations.posVec((float) x, (float) y, (float) z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe s(float t, double x, double y, double z) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	public static AnimationChannel rot(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, k);
	}

	public static AnimationChannel pos(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.POSITION, k);
	}

	public static AnimationChannel scale(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.SCALE, k);
	}
}
