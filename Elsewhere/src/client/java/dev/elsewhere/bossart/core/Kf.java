package dev.elsewhere.bossart.core;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Short keyframe builders for the core bosses' animation definitions (Blockbench conventions: degrees, +y up). */
public final class Kf {
	private Kf() {
	}

	public static Keyframe r(final float t, final float x, final float y, final float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe rl(final float t, final float x, final float y, final float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	public static Keyframe p(final float t, final float x, final float y, final float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe pl(final float t, final float x, final float y, final float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	public static Keyframe s(final float t, final double x, final double y, final double z) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe sl(final float t, final double x, final double y, final double z) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	public static AnimationChannel rot(final Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, k);
	}

	public static AnimationChannel pos(final Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.POSITION, k);
	}

	public static AnimationChannel scl(final Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.SCALE, k);
	}
}
