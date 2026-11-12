package dev.elsewhere.bossart.overworld_a;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Compact keyframe helpers: each call takes (time, x, y, z) quadruples, smoothed with Catmull-Rom. */
public final class ArtAnim {
	private ArtAnim() {}

	public static AnimationChannel rot(float... k) {
		return ch(AnimationChannel.Targets.ROTATION, 0, k);
	}

	public static AnimationChannel pos(float... k) {
		return ch(AnimationChannel.Targets.POSITION, 1, k);
	}

	public static AnimationChannel scl(float... k) {
		return ch(AnimationChannel.Targets.SCALE, 2, k);
	}

	/** Same as {@link #rot} but with linear interpolation (for snappy strikes). */
	public static AnimationChannel rotL(float... k) {
		return ch(AnimationChannel.Targets.ROTATION, 3, k);
	}

	private static AnimationChannel ch(AnimationChannel.Target target, int kind, float[] k) {
		Keyframe[] frames = new Keyframe[k.length / 4];
		for (int i = 0; i < frames.length; i++) {
			float t = k[i * 4], x = k[i * 4 + 1], y = k[i * 4 + 2], z = k[i * 4 + 3];
			frames[i] = new Keyframe(t,
				switch (kind) {
					case 1 -> KeyframeAnimations.posVec(x, y, z);
					case 2 -> KeyframeAnimations.scaleVec(x, y, z);
					default -> KeyframeAnimations.degreeVec(x, y, z);
				},
				kind == 3 ? AnimationChannel.Interpolations.LINEAR : AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(target, frames);
	}
}
