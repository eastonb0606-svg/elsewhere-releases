package dev.elsewhere.bossart.ossyra;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimations;

/**
 * Ossyra's keyframe animations. Timings follow the boss's real attack table (Ossyra.defaultAttacks): Bullet Storm 1 s
 * wind-up then a shot every 0.3 s for 2.4 s, Shell Slam 1.5 s close then the shockwave, Gravity Flip 3 s chime, Shulklings
 * 1 s, Void Bloom 2 s, Surface Swap 1 s, phase roar 2 s (TRANSITION_TICKS), death 1 s (vanilla deathTime 20).
 * The lid's open/close itself is driven from the synced open flag; these add on top.
 */
public final class OssyraAnimations {
	private OssyraAnimations() {}

	private static final AnimationChannel.Interpolation C = AnimationChannel.Interpolations.CATMULLROM;
	private static final AnimationChannel.Interpolation L = AnimationChannel.Interpolations.LINEAR;

	private static Keyframe rot(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), C);
	}

	private static Keyframe rotL(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.degreeVec(x, y, z), L);
	}

	private static Keyframe pos(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.posVec(x, y, z), C);
	}

	private static Keyframe scl(float t, float x, float y, float z) {
		return new Keyframe(t, KeyframeAnimations.scaleVec(x, y, z), C);
	}

	private static AnimationChannel r(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.ROTATION, k);
	}

	private static AnimationChannel p(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.POSITION, k);
	}

	private static AnimationChannel s(Keyframe... k) {
		return new AnimationChannel(AnimationChannel.Targets.SCALE, k);
	}

	/** Idle: she hovers and breathes; the lid peeks open a crack. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", p(pos(0, 0, 0, 0), pos(2, 0, 1.2F, 0), pos(4, 0, 0, 0)))
		.addAnimation("body", r(rot(0, 0, 0, 0), rot(1, 0, 0, 1.2F), rot(3, 0, 0, -1.2F), rot(4, 0, 0, 0)))
		.addAnimation("lid", r(rot(0, 0, 0, 0), rot(1.6F, -5, 0, 0), rot(2.2F, -5, 0, 0), rot(3.0F, 0, 0, 0), rot(4, 0, 0, 0)))
		.build();

	/** Bullet Storm: the shell yawns open over the 1 s wind-up, rods extend; the pearl kicks with every shot (0.3 s). */
	public static final AnimationDefinition BULLET_STORM;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(3.6F)
			.addAnimation("lid", r(rot(0, 0, 0, 0), rot(0.8F, -14, 0, 0), rot(1.0F, -8, 0, 0), rot(3.4F, -8, 0, 0), rot(3.6F, 0, 0, 0)))
			.addAnimation("body", p(pos(0, 0, 0, 0), pos(0.9F, 0, 2.5F, 0), pos(3.4F, 0, 2.5F, 0), pos(3.6F, 0, 0, 0)));
		for (int i = 0; i < 4; i++) {
			b.addAnimation("rod" + i, s(scl(0, 1, 1, 1), scl(0.9F, 1, 1.4F, 1), scl(3.4F, 1, 1.4F, 1), scl(3.6F, 1, 1, 1)));
		}
		Keyframe[] kick = new Keyframe[18];
		kick[0] = scl(0, 1, 1, 1);
		kick[1] = scl(1.0F, 1.1F, 1.1F, 1.1F);
		for (int i = 0; i < 8; i++) {
			float t = 1.0F + i * 0.3F;
			kick[2 + i * 2] = new Keyframe(t + 0.05F, KeyframeAnimations.scaleVec(1.28, 1.28, 1.28), L);
			kick[3 + i * 2] = new Keyframe(t + 0.25F, KeyframeAnimations.scaleVec(1.08, 1.08, 1.08), L);
		}
		BULLET_STORM = b.addAnimation("pearl", s(kick)).build();
	}

	/** Shell Slam: 1.5 s of clamping shut and shuddering down (armor up), then she bounces up with the shockwave. */
	public static final AnimationDefinition SHELL_SLAM = AnimationDefinition.Builder.withLength(2.4F)
		.addAnimation("body", s(scl(0, 1, 1, 1), scl(1.2F, 1.08F, 0.84F, 1.08F), scl(1.45F, 1.12F, 0.8F, 1.12F),
			scl(1.6F, 0.94F, 1.18F, 0.94F), scl(1.9F, 1.03F, 0.97F, 1.03F), scl(2.4F, 1, 1, 1)))
		.addAnimation("body", p(pos(0, 0, 0, 0), pos(1.0F, 0, 4, 0), pos(1.45F, 0, 6, 0), pos(1.55F, 0, -1, 0), pos(1.9F, 0, 1.5F, 0), pos(2.4F, 0, 0, 0)))
		.addAnimation("body", r(rotL(0, 0, 0, 0), rotL(0.3F, 0, 0, 2), rotL(0.4F, 0, 0, -2), rotL(0.6F, 0, 0, 2.5F), rotL(0.7F, 0, 0, -2.5F),
			rotL(0.9F, 0, 0, 3), rotL(1.0F, 0, 0, -3), rotL(1.2F, 0, 0, 3.5F), rotL(1.3F, 0, 0, -3.5F), rotL(1.5F, 0, 0, 0), rotL(2.4F, 0, 0, 0)))
		.addAnimation("lid", r(rot(0, 0, 0, 0), rot(1.4F, 0, 0, 0), rot(1.55F, -30, 0, 0), rot(1.8F, -10, 0, 0), rot(2.4F, 0, 0, 0)))
		.build();

	/** Gravity Flip: the chime builds for 3 s (she rises and spins up, rods flicker), then she rolls over as gravity inverts. */
	public static final AnimationDefinition GRAVITY_FLIP = AnimationDefinition.Builder.withLength(4.0F)
		.addAnimation("body", p(pos(0, 0, 0, 0), pos(2.8F, 0, 5, 0), pos(3.4F, 0, 5, 0), pos(4.0F, 0, 0, 0)))
		.addAnimation("body", r(rotL(0, 0, 0, 0), rotL(1.5F, 0, 90, 0), rotL(2.5F, 0, 270, 0), rotL(3.0F, 0, 360, 0), rotL(3.5F, 0, 360, 180),
			rotL(4.0F, 0, 360, 360)))
		.addAnimation("pearl", s(scl(0, 1, 1, 1), scl(2.9F, 1.25F, 1.25F, 1.25F), scl(3.1F, 0.9F, 0.9F, 0.9F), scl(4.0F, 1, 1, 1)))
		.build();

	/** Shulklings: the lid pops and the shell heaves as she births them. */
	public static final AnimationDefinition SHULKLINGS = AnimationDefinition.Builder.withLength(1.6F)
		.addAnimation("lid", r(rot(0, 0, 0, 0), rot(0.7F, -12, 0, 0), rot(1.0F, -45, 0, 0), rot(1.6F, 0, 0, 0)))
		.addAnimation("body", s(scl(0, 1, 1, 1), scl(0.8F, 1.1F, 0.9F, 1.1F), scl(1.05F, 0.95F, 1.1F, 0.95F), scl(1.6F, 1, 1, 1)))
		.build();

	/** Void Bloom: she sinks and the pearl swells and throbs for the 2 s warning while the panels dissolve. */
	public static final AnimationDefinition VOID_BLOOM = AnimationDefinition.Builder.withLength(2.6F)
		.addAnimation("body", p(pos(0, 0, 0, 0), pos(1.0F, 0, -3, 0), pos(2.0F, 0, -3, 0), pos(2.6F, 0, 0, 0)))
		.addAnimation("pearl", s(scl(0, 1, 1, 1), scl(0.5F, 1.2F, 1.2F, 1.2F), scl(0.8F, 1.05F, 1.05F, 1.05F), scl(1.1F, 1.3F, 1.3F, 1.3F),
			scl(1.4F, 1.1F, 1.1F, 1.1F), scl(1.8F, 1.4F, 1.4F, 1.4F), scl(2.05F, 0.9F, 0.9F, 0.9F), scl(2.6F, 1, 1, 1)))
		.addAnimation("lid", r(rot(0, 0, 0, 0), rot(1.0F, -10, 0, 0), rot(2.0F, -18, 0, 0), rot(2.6F, 0, 0, 0)))
		.build();

	/** Surface Swap: she spins up and implodes to a point, then reappears on the other surface. */
	public static final AnimationDefinition SURFACE_SWAP = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("body", r(rotL(0, 0, 0, 0), rotL(1.0F, 0, 720, 0)))
		.addAnimation("body", s(scl(0, 1, 1, 1), scl(0.6F, 0.85F, 1.1F, 0.85F), scl(1.0F, 0.15F, 0.15F, 0.15F)))
		.build();

	/** Phase-change roar (2 s): the shell gapes, the crown flares, the pearl blazes. */
	public static final AnimationDefinition ROAR;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(2.0F)
			.addAnimation("lid", r(rot(0, 0, 0, 0), rot(0.35F, -60, 0, 0), rot(1.5F, -55, 0, 0), rot(2.0F, 0, 0, 0)))
			.addAnimation("pearl", p(pos(0, 0, 0, 0), pos(0.4F, 0, 8, 0), pos(1.5F, 0, 7, 0), pos(2.0F, 0, 0, 0)))
			.addAnimation("pearl", s(scl(0, 1, 1, 1), scl(0.4F, 1.45F, 1.45F, 1.45F), scl(1.5F, 1.35F, 1.35F, 1.35F), scl(2.0F, 1, 1, 1)))
			.addAnimation("body", r(rotL(0, 0, 0, 0), rotL(0.4F, 0, 0, 3), rotL(0.5F, 0, 0, -3), rotL(0.6F, 0, 0, 3), rotL(0.7F, 0, 0, -3),
				rotL(0.8F, 0, 0, 2), rotL(0.9F, 0, 0, -2), rotL(1.0F, 0, 0, 0), rotL(2.0F, 0, 0, 0)));
		for (int i = 0; i < 4; i++) b.addAnimation("rod" + i, s(scl(0, 1, 1, 1), scl(0.4F, 1.3F, 1.8F, 1.3F), scl(1.6F, 1.3F, 1.7F, 1.3F), scl(2.0F, 1, 1, 1)));
		ROAR = b.build();
	}

	/** Death (1 s): the shell bursts apart (lid thrown back, base crumpling) and the pearl rises out of it. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("lid", r(rot(0, 0, 0, 0), rot(0.3F, -100, 0, 25), rot(1.0F, -140, 0, 40)))
		.addAnimation("lid", p(pos(0, 0, 0, 0), pos(0.3F, 0, 10, 4), pos(1.0F, -4, 18, 10)))
		.addAnimation("base", s(scl(0, 1, 1, 1), scl(0.3F, 1.15F, 0.8F, 1.15F), scl(1.0F, 0.85F, 0.35F, 0.85F)))
		.addAnimation("base", r(rot(0, 0, 0, 0), rot(1.0F, 8, 0, -6)))
		.addAnimation("pearl", p(pos(0, 0, 0, 0), pos(0.4F, 0, 10, 0), pos(1.0F, 0, 26, 0)))
		.addAnimation("pearl", s(scl(0, 1, 1, 1), scl(0.6F, 1.4F, 1.4F, 1.4F), scl(1.0F, 1.7F, 1.7F, 1.7F)))
		.addAnimation("pearl", r(rotL(0, 0, 0, 0), rotL(1.0F, 0, 180, 0)))
		.build();
}
