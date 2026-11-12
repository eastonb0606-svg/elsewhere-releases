package dev.elsewhere.bossart.glasswaste;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Short keyframe helpers for the Glasswaste boss animations (Catmull-Rom everywhere, like the Warden's). */
public final class Kf {
	private Kf() {}

	public static Keyframe r(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe p(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe s(float t, float k) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(k, k, k), AnimationChannel.Interpolations.CATMULLROM);
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
