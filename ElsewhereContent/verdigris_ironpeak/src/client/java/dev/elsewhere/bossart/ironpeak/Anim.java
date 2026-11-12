package dev.elsewhere.bossart.ironpeak;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;
import org.joml.Vector3f;

/** Short helpers for writing keyframe animations by hand (Blockbench conventions: degrees, +y up for positions). */
@Environment(EnvType.CLIENT)
public final class Anim {
	private Anim() {}

	public static AnimationChannel ch(AnimationChannel.Target target, Keyframe... frames) {
		return new AnimationChannel(target, frames);
	}

	public static Keyframe k(float t, Vector3f v) {
		return new Keyframe(t, v, AnimationChannel.Interpolations.CATMULLROM);
	}

	public static Keyframe kl(float t, Vector3f v) {
		return new Keyframe(t, v, AnimationChannel.Interpolations.LINEAR);
	}

	public static Vector3f rot(float x, float y, float z) {
		return KeyframeAnimations.degreeVec(x, y, z);
	}

	public static Vector3f pos(float x, float y, float z) {
		return KeyframeAnimations.posVec(x, y, z);
	}

	public static Vector3f scl(float x, float y, float z) {
		return KeyframeAnimations.scaleVec(x, y, z);
	}
}
