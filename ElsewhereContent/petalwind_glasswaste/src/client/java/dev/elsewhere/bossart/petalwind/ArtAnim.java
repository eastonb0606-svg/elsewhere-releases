package dev.elsewhere.bossart.petalwind;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.world.entity.AnimationState;
import org.joml.Vector3f;

/** Small helpers for writing keyframe animations compactly (Kazehana and Tempestra). Keys are (time, x, y, z) quadruples. */
public final class ArtAnim {
	private ArtAnim() {}

	/** Rotation keys in degrees, smooth. */
	public static AnimationChannel rot(float... k) {
		return channel(AnimationChannel.Targets.ROTATION, 0, AnimationChannel.Interpolations.CATMULLROM, k);
	}

	/** Rotation keys in degrees, linear (spins). */
	public static AnimationChannel rotLinear(float... k) {
		return channel(AnimationChannel.Targets.ROTATION, 0, AnimationChannel.Interpolations.LINEAR, k);
	}

	/** Position keys in pixels, y up. */
	public static AnimationChannel pos(float... k) {
		return channel(AnimationChannel.Targets.POSITION, 1, AnimationChannel.Interpolations.CATMULLROM, k);
	}

	/** Scale keys (1 = unchanged). */
	public static AnimationChannel scale(float... k) {
		return channel(AnimationChannel.Targets.SCALE, 2, AnimationChannel.Interpolations.CATMULLROM, k);
	}

	private static AnimationChannel channel(AnimationChannel.Target target, int kind, AnimationChannel.Interpolation interp, float[] k) {
		Keyframe[] frames = new Keyframe[k.length / 4];
		for (int i = 0; i < frames.length; i++) {
			float t = k[i * 4], x = k[i * 4 + 1], y = k[i * 4 + 2], z = k[i * 4 + 3];
			Vector3f v = switch (kind) {
				case 0 -> KeyframeAnimations.degreeVec(x, y, z);
				case 1 -> KeyframeAnimations.posVec(x, y, z);
				default -> KeyframeAnimations.scaleVec(x, y, z);
			};
			frames[i] = new Keyframe(t, v, interp);
		}
		return new AnimationChannel(target, frames);
	}

	/** Plays a one-shot animation while it is within its length (so finished ones never pin the pose). */
	public static void play(KeyframeAnimation anim, AnimationState state, float ageInTicks, float lengthSeconds) {
		if (!state.isStarted()) return;
		long ms = state.getTimeInMillis(ageInTicks);
		if (ms >= 0 && ms <= (long) (lengthSeconds * 1000)) anim.apply(ms, 1.0F);
	}

	/** Holds an animation (its last frame stays) for as long as the caller says the pose applies. */
	public static void hold(KeyframeAnimation anim, AnimationState state, float ageInTicks) {
		if (state.isStarted()) anim.apply(Math.max(0, state.getTimeInMillis(ageInTicks)), 1.0F);
	}
}
