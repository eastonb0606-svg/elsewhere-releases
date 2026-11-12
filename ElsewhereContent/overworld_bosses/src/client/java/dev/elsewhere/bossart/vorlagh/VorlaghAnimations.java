package dev.elsewhere.bossart.vorlagh;

import static dev.elsewhere.bossart.overworld_b.Kf.*;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Vorlagh keyframe animations. Body: timed to VorlaghEntity (ink cloud 14-tick wind-up then the jet; whirlpool roar
 * 30 + 100 ticks; beak bite at the end of a grab; 40-tick phase roar; 60-tick death: sinks, arms go limp).
 * Tentacles: timed to VorlaghTentacle states (raise 24 ticks, slam 10, glow 20, grab 80).
 */
public final class VorlaghAnimations {
	private VorlaghAnimations() {}

	private static AnimationDefinition.Builder arms(AnimationDefinition.Builder b, float len, float ax, float az) {
		for (int i = 0; i < 6; i++) {
			float ph = i / 6.0F;
			b.addAnimation("arm" + i, wave(len, 8, ax, az, ph, 0));
			b.addAnimation("arm" + i + "_1", wave(len, 8, ax * 1.4F, az * 1.2F, ph - 0.12F, 4));
			b.addAnimation("arm" + i + "_2", wave(len, 8, ax * 1.8F, az * 1.4F, ph - 0.24F, 8));
		}
		return b;
	}

	public static final AnimationDefinition IDLE = arms(AnimationDefinition.Builder.withLength(4.0F).looping(), 4.0F, 9, 7)
		.addAnimation("body", pos(p(0, 0, 0, 0), p(2.0F, 0, 1.2F, 0), p(4.0F, 0, 0, 0)))
		.addAnimation("mantle1", rot(r(0, 0, 0, 0), r(2.0F, -3, 0, 1.5F), r(4.0F, 0, 0, 0)))
		.addAnimation("mantle1", scale(s(0, 1, 1, 1), s(2.0F, 1.05F, 1.03F, 1.05F), s(4.0F, 1, 1, 1)))
		.addAnimation("mantle2", rot(r(0, 0, 0, 0), r(2.4F, -4, 0, -2), r(4.0F, 0, 0, 0)))
		.addAnimation("mantle3", rot(r(0, 0, 0, 0), r(2.8F, -5, 0, 2), r(4.0F, 0, 0, 0)))
		.addAnimation("fin_l", rot(r(0, 0, 0, 0), r(1.0F, 0, 0, -16), r(2.0F, 0, 0, 0), r(3.0F, 0, 0, -16), r(4.0F, 0, 0, 0)))
		.addAnimation("fin_r", rot(r(0, 0, 0, 0), r(1.0F, 0, 0, 16), r(2.0F, 0, 0, 0), r(3.0F, 0, 0, 16), r(4.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(2.6F, 0, 0, 0), r(3.0F, 8, 0, 0), r(3.4F, 0, 0, 0), r(4.0F, 0, 0, 0)))
		.build();

	/** Ink cloud wind-up, 0.7 s: the mantle swells and the head draws back. */
	public static final AnimationDefinition INK_CHARGE = AnimationDefinition.Builder.withLength(0.7F)
		.addAnimation("mantle1", scale(s(0, 1, 1, 1), s(0.6F, 1.14F, 1.1F, 1.14F), s(0.7F, 1.15F, 1.1F, 1.15F)))
		.addAnimation("mantle2", scale(s(0, 1, 1, 1), s(0.6F, 1.12F, 1.08F, 1.12F), s(0.7F, 1.12F, 1.08F, 1.12F)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.6F, -9, 0, 0), r(0.7F, -10, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.7F, 12, 0, 0)))
		.build();

	/** Follows INK_CHARGE: the mantle squeezes, beak gapes as the ink glob spurts out. */
	public static final AnimationDefinition INK_JET = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("mantle1", scale(s(0, 1.15F, 1.1F, 1.15F), s(0.12F, 0.9F, 0.94F, 0.9F), s(0.8F, 1, 1, 1)))
		.addAnimation("mantle2", scale(s(0, 1.12F, 1.08F, 1.12F), s(0.15F, 0.9F, 0.95F, 0.9F), s(0.8F, 1, 1, 1)))
		.addAnimation("head", rot(r(0, -10, 0, 0), r(0.12F, 14, 0, 0), r(0.8F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 12, 0, 0), r(0.12F, 40, 0, 0), r(0.8F, 0, 0, 0)))
		.build();

	/** Whirlpool call, 6.5 s: rears up roaring, then churns the water, swaying and rolling. */
	public static final AnimationDefinition WHIRL = arms(AnimationDefinition.Builder.withLength(6.5F), 6.5F, 22, 18)
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.6F, 0, 5, 0), p(1.5F, 0, 5, 0), p(2.5F, 0, 2, 0), p(6.0F, 0, 2, 0), p(6.5F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(1.5F, 0, 0, 0), r(2.75F, 0, 14, 4), r(4.0F, 0, -14, -4), r(5.25F, 0, 12, 4), r(6.5F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.6F, -16, 0, 0), r(1.5F, -14, 0, 0), r(2.2F, 0, 0, 0), r(6.5F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.6F, 45, 0, 0), r(1.5F, 42, 0, 0), r(2.0F, 8, 0, 0), r(6.5F, 0, 0, 0)))
		.addAnimation("mantle1", rot(r(0, 0, 0, 0), r(0.6F, -10, 0, 0), r(1.5F, -8, 0, 0), r(2.2F, 0, 0, 0), r(6.5F, 0, 0, 0)))
		.build();

	/** Beak bite at the end of a grab, 0.8 s. */
	public static final AnimationDefinition BITE = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.15F, 18, 0, 0), r(0.35F, 10, 0, 0), r(0.8F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.15F, 0, 0, -4), p(0.8F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.15F, 50, 0, 0), rl(0.28F, 0, 0, 0), r(0.8F, 0, 0, 0)))
		.build();

	/** Phase transition roar, 2.0 s. */
	public static final AnimationDefinition PHASE_ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.5F, 0, 5, 0), p(1.6F, 0, 5, 0), p(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.5F, -20, 0, 0), r(0.7F, -19, 0, 3), r(0.9F, -20, 0, -3), r(1.1F, -19, 0, 3),
			r(1.3F, -20, 0, -3), r(1.6F, -18, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.5F, 55, 0, 0), r(1.6F, 50, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("mantle1", scale(s(0, 1, 1, 1), s(0.5F, 1.1F, 1.08F, 1.1F), s(1.6F, 1.1F, 1.08F, 1.1F), s(2.0F, 1, 1, 1)))
		.addAnimation("brow_l", rot(r(0, 0, 0, 0), r(0.5F, 0, 0, -12), r(1.6F, 0, 0, -12), r(2.0F, 0, 0, 0)))
		.addAnimation("brow_r", rot(r(0, 0, 0, 0), r(0.5F, 0, 0, 12), r(1.6F, 0, 0, 12), r(2.0F, 0, 0, 0)))
		.build();

	/** Death, 3.0 s: a last spasm, then it goes limp and sinks below the deck, rolling over. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.4F, 0, 3, 0), p(1.0F, 0, 0, 0), p(2.0F, 0, -14, 0), p(3.0F, 0, -34, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.4F, 0, 0, 0), r(3.0F, 10, 0, 30)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -16, 0, 0), r(1.0F, 10, 0, 0), r(3.0F, 22, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.4F, 45, 0, 0), r(1.0F, 20, 0, 0), r(3.0F, 26, 0, 0)))
		.addAnimation("mantle1", rot(r(0, 0, 0, 0), r(1.0F, 6, 0, 0), r(3.0F, 14, 0, 6)))
		.addAnimation("mantle2", rot(r(0, 0, 0, 0), r(1.0F, 8, 0, 0), r(3.0F, 16, 0, 8)))
		.addAnimation("arm0", rot(r(0, 0, 0, 0), r(0.4F, -40, 0, 30), r(1.2F, 0, 0, 0)))
		.addAnimation("arm1", rot(r(0, 0, 0, 0), r(0.4F, -40, 0, -30), r(1.2F, 0, 0, 0)))
		.addAnimation("arm2", rot(r(0, 0, 0, 0), r(0.4F, 0, 0, 45), r(1.2F, 0, 0, 0)))
		.addAnimation("arm3", rot(r(0, 0, 0, 0), r(0.4F, 0, 0, -45), r(1.2F, 0, 0, 0)))
		.build();

	// ---- tentacles: seg0..seg6 ---------------------------------------------------------------------------------------

	public static final AnimationDefinition T_IDLE;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(3.0F).looping();
		for (int i = 0; i < 7; i++) b.addAnimation("seg" + i, wave(3.0F, 8, 5 + i * 2.2F, 3 + i * 1.2F, -i * 0.09F, i == 0 ? 0 : 3 + i));
		T_IDLE = b.build();
	}

	private static final float[] RAISE = {-26, -20, -16, -22, -28, -32, -36};

	/** Slam wind-up, 1.2 s: rears back and curls the tip over like a whip, trembling. */
	public static final AnimationDefinition T_RAISE;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(1.2F);
		for (int i = 0; i < 7; i++) {
			float a = RAISE[i];
			b.addAnimation("seg" + i, rot(r(0, 0, 0, 0), r(0.9F, a, 0, 0), r(1.0F, a - 2, 0, 3), r(1.1F, a, 0, -3), r(1.2F, a - 2, 0, 0)));
		}
		T_RAISE = b.build();
	}

	private static final float[] SLAM = {82, 6, 3, 2, 0, -4, -8};

	/** The slam, 0.5 s: whips down flat along the deck toward the target. */
	public static final AnimationDefinition T_SLAM;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(0.5F);
		for (int i = 0; i < 7; i++) {
			b.addAnimation("seg" + i, rot(r(0, RAISE[i] - 2, 0, 0), rl(0.12F, SLAM[i] + (i == 0 ? 6 : 0), 0, 0), r(0.22F, SLAM[i], 0, 0),
				r(0.5F, SLAM[i], 0, 0)));
		}
		T_SLAM = b.build();
	}

	/** After a slam: lifts back up off the deck, 0.8 s. */
	public static final AnimationDefinition T_RECOVER;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(0.8F);
		for (int i = 0; i < 7; i++) b.addAnimation("seg" + i, rot(r(0, SLAM[i], 0, 0), r(0.8F, 0, 0, 0)));
		T_RECOVER = b.build();
	}

	private static final float[] GLOW = {16, 12, 10, -6, -16, -22, -24};

	/** Grab wind-up, 1.0 s: leans in, tip raised like a cobra and quivering (the tip glows cyan). */
	public static final AnimationDefinition T_GLOW;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(1.0F);
		for (int i = 0; i < 7; i++) {
			float a = GLOW[i];
			float q = i >= 4 ? 5 : 0;
			b.addAnimation("seg" + i, rot(r(0, 0, 0, 0), r(0.5F, a, 0, 0), r(0.65F, a, 0, q), r(0.8F, a, 0, -q), r(1.0F, a, 0, 0)));
		}
		T_GLOW = b.build();
	}

	private static final float[] COIL = {40, 18, 14, 48, 62, 72, 80};

	/** The grab, 4.0 s: lashes out and coils the tip around the player, then hauls back toward the beak. */
	public static final AnimationDefinition T_GRAB;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(4.0F);
		for (int i = 0; i < 7; i++) {
			float c = COIL[i];
			float end = i == 0 ? -18 : (i < 3 ? c * 0.6F : c);
			b.addAnimation("seg" + i, rot(r(0, GLOW[i], 0, 0), rl(0.2F, c, 0, 0), r(1.0F, c * 0.95F, 0, 0), r(4.0F, end, 0, 0)));
		}
		T_GRAB = b.build();
	}

	/** After a grab or bite: uncoils back to upright, 0.8 s. */
	public static final AnimationDefinition T_RELEASE;

	static {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(0.8F);
		for (int i = 0; i < 7; i++) {
			float c = i == 0 ? -18 : (i < 3 ? COIL[i] * 0.6F : COIL[i]);
			b.addAnimation("seg" + i, rot(r(0, c * 0.5F, 0, 0), r(0.8F, 0, 0, 0)));
		}
		T_RELEASE = b.build();
	}

	/** Severed stump twitching (looping). */
	public static final AnimationDefinition T_STUMP = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("seg0", rot(r(0, 0, 0, 0), r(0.2F, 6, 0, -4), r(0.4F, -3, 0, 5), r(0.6F, 0, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("seg1", rot(r(0, 0, 0, 0), r(0.3F, 10, 0, 6), r(0.5F, -6, 0, -4), r(0.8F, 0, 0, 0), r(1.6F, 0, 0, 0)))
		.build();
}
