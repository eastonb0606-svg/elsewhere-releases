package dev.elsewhere.bossart.ankhara;

import static dev.elsewhere.bossart.glasswaste.Kf.*;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;

/**
 * Ankhara's head animations, timed to the server (Ankhara.fightTick): Erupt breach (0.75 s until the wind-up starts),
 * 1.0 s Lunge wind-up (EV_WINDUP at surfaced tick 15, EV_LUNGE at tick 35), Lunge strike (up to 0.7 s of travel),
 * 5 s stun, 2 s phase roar, 1 s death. Poses are deliberately big so they read from fight distance (12+ blocks).
 * Six sunglass fangs ring the maw: 0/5 top, 1/4 sides, 2/3 bottom (the "jaw"). Fang X rotation: negative = flower open,
 * positive = clamp shut. Neck X rotation: negative = rear back, positive = lean forward. Position y is up, z is back.
 */
public final class AnkharaAnimations {
	private AnkharaAnimations() {}

	private static AnimationDefinition.Builder fangs(AnimationDefinition.Builder b, Keyframe... k) {
		for (int i = 0; i < 6; i++) b.addAnimation("fang" + i, new AnimationChannel(AnimationChannel.Targets.ROTATION, k));
		return b;
	}

	/** Top fangs (0, 5), side fangs (1, 4), bottom "jaw" fangs (2, 3) on separate curves. */
	private static AnimationDefinition.Builder fangs(AnimationDefinition.Builder b, Keyframe[] top, Keyframe[] side, Keyframe[] jaw) {
		Keyframe[][] by = {top, side, jaw, jaw, side, top};
		for (int i = 0; i < 6; i++) b.addAnimation("fang" + i, new AnimationChannel(AnimationChannel.Targets.ROTATION, by[i]));
		return b;
	}

	private static Keyframe[] k(Keyframe... k) {
		return k;
	}

	/** Surfaced: the neck sways, the maw breathes open and shut. */
	public static final AnimationDefinition IDLE = fangs(AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("neck", rot(r(0, 0, 0, 0), r(1.5F, -6, 4, 0), r(3.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 3), r(1.5F, 6, 0, -3), r(3.0F, 0, 0, 3))),
		r(0, 0, 0, 0), r(1.5F, -18, 0, 0), r(3.0F, 0, 0, 0)).build();

	/** Burrowed: fast side-to-side swimming through the sand. */
	public static final AnimationDefinition SWIM = AnimationDefinition.Builder.withLength(1.2F).looping()
		.addAnimation("neck", rot(r(0, 0, 0, 0), r(0.3F, 0, 14, 0), r(0.9F, 0, -14, 0), r(1.2F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, 0, -10, 6), r(0.9F, 0, 10, -6), r(1.2F, 0, 0, 0)))
		.build();

	/**
	 * Erupt (0.75 s, then the wind-up takes over): rockets up from 4 blocks under the sand to 2 blocks above its rest
	 * height, rears back with the maw to the sky and the fangs flowered wide open, shakes the sand off, settles.
	 */
	public static final AnimationDefinition ERUPT = fangs(AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("neck", pos(p(0, 0, -64, 0), p(0.15F, 0, 34, 4), p(0.4F, 0, 26, 4), p(0.8F, 0, 0, 0)))
		.addAnimation("neck", rot(r(0, 10, 0, 0), r(0.15F, -38, 0, 0), r(0.4F, -42, 0, 0), r(0.8F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.15F, -38, 0, 0), r(0.3F, -40, 6, 12), r(0.45F, -36, -6, -12), r(0.6F, -20, 0, 4),
			r(0.8F, 0, 0, 0)))
		.addAnimation("head", scale(s(0, 1.0F), s(0.15F, 1.15F), s(0.4F, 1.1F), s(0.8F, 1.0F)))
		.addAnimation("crest", rot(r(0, 0, 0, 0), r(0.15F, 30, 0, 0), r(0.5F, 20, 0, 0), r(0.8F, 0, 0, 0))),
		r(0, 0, 0, 0), r(0.12F, -80, 0, 0), r(0.5F, -76, 0, 0), r(0.8F, 0, 0, 0)).build();

	/** Lunge wind-up (1.0 s, then EV_LUNGE): rears and pulls far back like a cobra, maw aimed down at the target, fangs splayed, trembling. */
	public static final AnimationDefinition WINDUP = fangs(AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("neck", rot(r(0, 0, 0, 0), r(0.35F, -40, 0, 0), r(0.9F, -48, 0, 0), r(1.0F, -48, 0, 0), r(1.4F, 0, 0, 0)))
		.addAnimation("neck", pos(p(0, 0, 0, 0), p(0.35F, 0, 4, 14), p(0.9F, 0, 6, 18), p(1.0F, 0, 6, 18), p(1.4F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.35F, 26, 0, 0), r(0.6F, 32, 0, 6), r(0.75F, 32, 0, -6), r(0.9F, 34, 0, 5),
			r(1.0F, 34, 0, 0), r(1.4F, 0, 0, 0)))
		.addAnimation("crest", rot(r(0, 0, 0, 0), r(0.35F, -25, 0, 0), r(1.0F, -30, 0, 0), r(1.4F, 0, 0, 0))),
		r(0, 0, 0, 0), r(0.5F, -55, 0, 0), r(0.9F, -72, 0, 0), r(1.0F, -72, 0, 0), r(1.4F, 0, 0, 0)).build();

	/**
	 * Lunge strike (server: up to 0.7 s of travel at 18 blocks/s): snaps from the coil into a straight, near-level spear
	 * nearly 3 blocks forward, fangs flared wide, then bites shut at 0.5 s and recovers.
	 */
	public static final AnimationDefinition LUNGE = fangs(AnimationDefinition.Builder.withLength(0.9F)
		.addAnimation("neck", rot(r(0, -48, 0, 0), r(0.08F, 36, 0, 0), r(0.55F, 32, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("neck", pos(p(0, 0, 6, 18), p(0.08F, 0, -2, -28), p(0.55F, 0, -2, -26), p(0.9F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 34, 0, 0), r(0.08F, -30, 0, 0), r(0.55F, -28, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("head", scale(s(0, 1.0F), s(0.08F, 1.12F), s(0.5F, 1.08F), s(0.9F, 1.0F)))
		.addAnimation("crest", rot(r(0, -30, 0, 0), r(0.08F, -45, 0, 0), r(0.55F, -40, 0, 0), r(0.9F, 0, 0, 0))),
		r(0, -72, 0, 0), r(0.06F, -84, 0, 0), r(0.45F, -84, 0, 0), r(0.52F, 18, 0, 0), r(0.7F, 12, 0, 0), r(0.9F, 0, 0, 0)).build();

	/**
	 * Glass-pillar stun (5 s): whiplash back on impact, then the head topples sideways and slumps onto the sand, dazed,
	 * the bottom fangs hang open like a slack jaw, the top fangs droop, the crest flops; slow groggy sway; recovers at 4.6 s.
	 */
	public static final AnimationDefinition STUN = fangs(AnimationDefinition.Builder.withLength(5.0F)
		.addAnimation("neck", rot(r(0, 0, 0, 0), r(0.15F, -28, 0, -10), r(0.6F, 22, 0, 36), r(1.8F, 18, 5, 30), r(3.0F, 22, -5, 36),
			r(4.2F, 18, 5, 30), r(4.6F, 16, 0, 26), r(5.0F, 0, 0, 0)))
		.addAnimation("neck", pos(p(0, 0, 0, 0), p(0.15F, 0, 4, 6), p(0.6F, 0, -4, 0), p(4.6F, 0, -4, 0), p(5.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.15F, -20, 0, -10), r(0.6F, 18, 0, 24), r(1.8F, 14, 0, 16), r(3.0F, 18, 0, 24),
			r(4.2F, 14, 0, 16), r(4.6F, 12, 0, 10), r(5.0F, 0, 0, 0)))
		.addAnimation("crest", rot(r(0, 0, 0, 0), r(0.6F, 60, 0, 25), r(4.6F, 60, 0, 25), r(5.0F, 0, 0, 0))),
		k(r(0, 0, 0, 0), r(0.15F, -60, 0, 0), r(0.6F, 30, 0, 0), r(4.6F, 30, 0, 0), r(5.0F, 0, 0, 0)),
		k(r(0, 0, 0, 0), r(0.15F, -60, 0, 0), r(0.6F, 10, 0, 0), r(4.6F, 10, 0, 0), r(5.0F, 0, 0, 0)),
		k(r(0, 0, 0, 0), r(0.15F, -60, 0, 0), r(0.6F, -78, 0, 0), r(2.4F, -70, 0, 0), r(4.6F, -78, 0, 0), r(5.0F, 0, 0, 0))).build();

	/** Phase roar (2 s, synced transition timer): rears up, maw wide, head thrashing. */
	public static final AnimationDefinition ROAR = fangs(AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("neck", rot(r(0, 0, 0, 0), r(0.4F, -34, 0, 0), r(1.6F, -30, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("neck", pos(p(0, 0, 0, 0), p(0.4F, 0, 10, 4), p(1.6F, 0, 10, 4), p(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -30, 0, 0), r(0.6F, -30, 8, 6), r(0.8F, -30, -8, -6), r(1.0F, -30, 8, 6), r(1.2F, -30, -8, -6),
			r(1.4F, -30, 0, 0), r(2.0F, 0, 0, 0))),
		r(0, 0, 0, 0), r(0.4F, -75, 0, 0), r(1.6F, -70, 0, 0), r(2.0F, 0, 0, 0)).build();

	/** Death (1 s, deathTime): the head crashes down and sinks into the sand. */
	public static final AnimationDefinition DEATH = fangs(AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("neck", rot(r(0, 0, 0, 0), r(0.25F, -30, 0, 0), r(1.0F, 70, 0, 12)))
		.addAnimation("neck", pos(p(0, 0, 0, 0), p(0.25F, 0, 4, 0), p(1.0F, 0, -16, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.25F, -30, 0, 0), r(1.0F, 20, 0, 28))),
		r(0, 0, 0, 0), r(0.25F, -70, 0, 0), r(1.0F, 28, 0, 0)).build();
}
