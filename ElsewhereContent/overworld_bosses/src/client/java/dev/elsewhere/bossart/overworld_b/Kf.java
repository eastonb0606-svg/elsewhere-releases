package dev.elsewhere.bossart.overworld_b;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Tiny keyframe shorthands for the overworld_b boss animations (rotation in degrees, position in model px, y up). */
public final class Kf {
	private Kf() {}

	public static Keyframe r(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe rl(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	public static Keyframe p(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe pl(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), AnimationChannel.Interpolations.LINEAR);
	}

	public static Keyframe s(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), AnimationChannel.Interpolations.CATMULLROM);
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

	/** A looping sine rotation sampled into keyframes: amplitude (deg) per axis, phase in turns. */
	public static AnimationChannel wave(float length, int samples, float ax, float az, float phase, float biasX) {
		Keyframe[] k = new Keyframe[samples + 1];
		for (int i = 0; i <= samples; i++) {
			float t = length * i / samples;
			double a = Math.PI * 2 * (i / (double) samples + phase);
			k[i] = r(t, biasX + (float) (Math.sin(a) * ax), 0, (float) (Math.cos(a) * az));
		}
		return rot(k);
	}
}
