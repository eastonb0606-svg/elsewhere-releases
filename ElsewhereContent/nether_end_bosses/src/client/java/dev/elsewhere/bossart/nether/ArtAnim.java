package dev.elsewhere.bossart.nether;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/** Small builder helpers for the boss keyframe animations (rotation channels in degrees, times in seconds). */
public final class ArtAnim {
	private ArtAnim() {}

	/** {t0, x0, y0, z0, t1, x1, y1, z1, ...} rotation keyframes with Catmull-Rom smoothing. */
	public static AnimationChannel rot(float... k) {
		Keyframe[] frames = new Keyframe[k.length / 4];
		for (int i = 0; i < frames.length; i++) {
			frames[i] = new Keyframe(k[i * 4], KeyframeAnimations.degreeVec(k[i * 4 + 1], k[i * 4 + 2], k[i * 4 + 3]), AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, frames);
	}

	public static AnimationChannel pos(float... k) {
		Keyframe[] frames = new Keyframe[k.length / 4];
		for (int i = 0; i < frames.length; i++) {
			frames[i] = new Keyframe(k[i * 4], KeyframeAnimations.posVec(k[i * 4 + 1], k[i * 4 + 2], k[i * 4 + 3]), AnimationChannel.Interpolations.CATMULLROM);
		}
		return new AnimationChannel(AnimationChannel.Targets.POSITION, frames);
	}

	public static AnimationChannel scale(double... k) {
		Keyframe[] frames = new Keyframe[k.length / 4];
		for (int i = 0; i < frames.length; i++) {
			frames[i] = new Keyframe((float) k[i * 4], KeyframeAnimations.scaleVec(k[i * 4 + 1], k[i * 4 + 2], k[i * 4 + 3]), AnimationChannel.Interpolations.LINEAR);
		}
		return new AnimationChannel(AnimationChannel.Targets.SCALE, frames);
	}

	public static AnimationDefinition.Builder def(float seconds, boolean loop) {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(seconds);
		return loop ? b.looping() : b;
	}
}
