package dev.elsewhere.bossart.selenne;

import static dev.elsewhere.bossart.core.Kf.p;
import static dev.elsewhere.bossart.core.Kf.pl;
import static dev.elsewhere.bossart.core.Kf.pos;
import static dev.elsewhere.bossart.core.Kf.r;
import static dev.elsewhere.bossart.core.Kf.rl;
import static dev.elsewhere.bossart.core.Kf.rot;
import static dev.elsewhere.bossart.core.Kf.s;
import static dev.elsewhere.bossart.core.Kf.sl;
import static dev.elsewhere.bossart.core.Kf.scl;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.util.Mth;

/**
 * Selenne keyframes. Base pose: lids shut (lid plates full height), lashes spread like the concept painting.
 * Moonbeam fires at ~0.95 s (BEAM_WINDUP 20 ticks).
 */
public final class SelenneAnimations {
	private SelenneAnimations() {
	}

	/** Hovering bob with the ten eyeblossom lashes swaying out of step. */
	public static final AnimationDefinition IDLE = idle();

	/** The lids roll up into the brows (chime), lashes flare, the iris dilates. Holds open. */
	public static final AnimationDefinition OPEN = AnimationDefinition.Builder.withLength(0.6F)
		.addAnimation("lid_top", scl(s(0.0F, 1, 1, 1), s(0.45F, 1, 0.04, 1), s(0.6F, 1, 0.04, 1)))
		.addAnimation("lid_bottom", scl(s(0.0F, 1, 1, 1), s(0.45F, 1, 0.04, 1), s(0.6F, 1, 0.04, 1)))
		.addAnimation("iris", scl(s(0.0F, 0.7, 0.7, 1), s(0.35F, 1.15, 1.15, 1), s(0.6F, 1, 1, 1)))
		.addAnimation("eye", pos(p(0.0F, 0, 0, 0), p(0.2F, 0, 0, -1.0F), p(0.6F, 0, 0, 0)))
		.build();

	/** Blink tell, then the lids slam shut (tock). Holds closed with the lashes cupped forward. */
	public static final AnimationDefinition CLOSE = AnimationDefinition.Builder.withLength(0.7F)
		.addAnimation("lid_top", scl(s(0.0F, 1, 0.04, 1), sl(0.08F, 1, 0.6, 1), sl(0.18F, 1, 0.08, 1), s(0.5F, 1, 1, 1), s(0.7F, 1, 1, 1)))
		.addAnimation("lid_bottom", scl(s(0.0F, 1, 0.04, 1), sl(0.08F, 1, 0.6, 1), sl(0.18F, 1, 0.08, 1), s(0.5F, 1, 1, 1), s(0.7F, 1, 1, 1)))
		.addAnimation("eye", pos(p(0.0F, 0, 0, 0), p(0.5F, 0, -0.6F, 0.8F), p(0.7F, 0, 0, 0)))
		.build();

	/** Moonbeam: the eye draws back and trembles while the iris contracts, then lunges as the beam fires. */
	public static final AnimationDefinition BEAM = lashes(AnimationDefinition.Builder.withLength(1.4F), 0.9F, 32.0F, 1.0F, -22.0F, 1.4F)
		.addAnimation("iris", scl(s(0.0F, 1, 1, 1), s(0.9F, 0.5, 0.5, 1), sl(1.0F, 1.35, 1.35, 1), s(1.4F, 1, 1, 1)))
		.addAnimation("pupil", scl(s(0.0F, 1, 1, 1), s(0.9F, 0.6, 0.6, 1), sl(1.0F, 1.25, 1.25, 1), s(1.4F, 1, 1, 1)))
		.addAnimation("eye", pos(p(0.0F, 0, 0, 0), p(0.6F, 0.3F, 0, 2.6F), p(0.7F, -0.3F, 0, 2.8F), p(0.8F, 0.3F, 0, 3.0F),
			p(0.9F, -0.3F, 0, 3.0F), pl(1.0F, 0, 0, -2.5F), p(1.4F, 0, 0, 0)))
		.build();

	/** Phase 2 (Petals): she shudders and the lashes burst wide, blooms swelling. */
	public static final AnimationDefinition PETALS = petals();

	/** Phase 3 (Unblinking): the lids are torn away and fly off. */
	public static final AnimationDefinition TEAR = AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("eye", rot(rl(0.0F, 0, 0, 0), rl(0.1F, 0, 0, 8.0F), rl(0.2F, 0, 0, -8.0F), rl(0.3F, 0, 0, 8.0F), rl(0.4F, 0, 0, -8.0F), r(0.6F, 0, 0, 0)))
		.addAnimation("brow_top", pos(p(0.0F, 0, 0, 0), p(0.45F, 0, 1.0F, 0), pl(1.4F, 3.0F, 18.0F, -12.0F)))
		.addAnimation("brow_top", rot(r(0.0F, 0, 0, 0), r(0.45F, -10.0F, 0, 0), rl(1.4F, -80.0F, 20.0F, 15.0F)))
		.addAnimation("brow_top", scl(s(0.0F, 1, 1, 1), s(0.9F, 1, 1, 1), sl(1.4F, 0, 0, 0)))
		.addAnimation("brow_bottom", pos(p(0.0F, 0, 0, 0), p(0.45F, 0, -1.0F, 0), pl(1.4F, -3.0F, -18.0F, -12.0F)))
		.addAnimation("brow_bottom", rot(r(0.0F, 0, 0, 0), r(0.45F, 10.0F, 0, 0), rl(1.4F, 80.0F, -20.0F, -15.0F)))
		.addAnimation("brow_bottom", scl(s(0.0F, 1, 1, 1), s(0.9F, 1, 1, 1), sl(1.4F, 0, 0, 0)))
		.build();

	/** Full Moon (final 10 s): trembling, lashes flared, blooms throbbing. */
	public static final AnimationDefinition FULL_MOON = fullMoon();

	/** Death: the eye closes, sinks and droops while the blooms fall away. Driven by deathTime (1 s). */
	public static final AnimationDefinition DEATH = death();

	private static AnimationDefinition idle() {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(4.0F).looping()
			.addAnimation("eye", pos(p(0.0F, 0, 0, 0), p(1.0F, 0, 0.8F, 0), p(2.0F, 0, 1.4F, 0), p(3.0F, 0, 0.6F, 0), p(4.0F, 0, 0, 0)));
		for (int i = 0; i < 10; i++) {
			float ph = i / 10.0F * Mth.TWO_PI;
			b.addAnimation("lash" + i, rot(r(0.0F, 7.0F * Mth.sin(ph), 0, 0), r(1.0F, 7.0F * Mth.sin(ph + Mth.HALF_PI), 0, 0),
				r(2.0F, 7.0F * Mth.sin(ph + Mth.PI), 0, 0), r(3.0F, 7.0F * Mth.sin(ph + 1.5F * Mth.PI), 0, 0), r(4.0F, 7.0F * Mth.sin(ph), 0, 0)));
		}
		return b.build();
	}

	private static AnimationDefinition.Builder lashes(final AnimationDefinition.Builder b, final float t1, final float a1, final float t2,
		final float a2, final float end) {
		for (int i = 0; i < 10; i++) {
			b.addAnimation("lash" + i, rot(r(0.0F, 0, 0, 0), r(t1, a1, 0, 0), rl(t2, a2, 0, 0), r(end, 0, 0, 0)));
			b.addAnimation("bloom" + i, scl(s(0.0F, 1, 1, 1), s(t1, 1.4, 1.4, 1.4), sl(t2, 0.9, 0.9, 0.9), s(end, 1, 1, 1)));
		}
		return b;
	}

	private static AnimationDefinition petals() {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(1.6F)
			.addAnimation("eye", rot(rl(0.0F, 0, 0, 0), rl(0.1F, 0, 0, 7.0F), rl(0.2F, 0, 0, -7.0F), rl(0.3F, 0, 0, 7.0F), rl(0.4F, 0, 0, -7.0F),
				rl(0.5F, 0, 0, 6.0F), rl(0.6F, 0, 0, -6.0F), rl(0.7F, 0, 0, 5.0F), rl(0.8F, 0, 0, -5.0F), rl(0.9F, 0, 0, 3.0F), r(1.2F, 0, 0, 0)))
			.addAnimation("eye", pos(p(0.0F, 0, 0, 0), p(0.4F, 0, 2.0F, 0), p(1.2F, 0, 2.0F, 0), p(1.6F, 0, 0, 0)))
			.addAnimation("iris", scl(s(0.0F, 1, 1, 1), s(0.4F, 1.3, 1.3, 1), s(1.6F, 1, 1, 1)));
		for (int i = 0; i < 10; i++) {
			b.addAnimation("lash" + i, rot(r(0.0F, 0, 0, 0), r(0.4F, -38.0F, 0, 0), r(1.2F, -34.0F, 0, 0), r(1.6F, 0, 0, 0)));
			b.addAnimation("bloom" + i, scl(s(0.0F, 1, 1, 1), s(0.4F, 1.8, 1.8, 1.8), s(1.2F, 1.5, 1.5, 1.5), s(1.6F, 1, 1, 1)));
		}
		return b.build();
	}

	private static AnimationDefinition fullMoon() {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(0.6F).looping()
			.addAnimation("eye", pos(pl(0.0F, 0.4F, 0, 0), pl(0.15F, -0.4F, 0.2F, 0), pl(0.3F, 0.4F, 0, 0), pl(0.45F, -0.4F, 0.2F, 0), pl(0.6F, 0.4F, 0, 0)));
		for (int i = 0; i < 10; i++) {
			b.addAnimation("lash" + i, rot(r(0.0F, -24.0F, 0, 0), r(0.3F, -30.0F, 0, 0), r(0.6F, -24.0F, 0, 0)));
			b.addAnimation("bloom" + i, scl(s(0.0F, 1.1, 1.1, 1.1), s(0.3F, 1.45, 1.45, 1.45), s(0.6F, 1.1, 1.1, 1.1)));
		}
		return b.build();
	}

	private static AnimationDefinition death() {
		AnimationDefinition.Builder b = AnimationDefinition.Builder.withLength(1.0F)
			.addAnimation("lid_top", scl(s(0.0F, 1, 0.04, 1), s(0.3F, 1, 1, 1), s(1.0F, 1, 1, 1)))
			.addAnimation("lid_bottom", scl(s(0.0F, 1, 0.04, 1), s(0.3F, 1, 1, 1), s(1.0F, 1, 1, 1)))
			.addAnimation("eye", pos(p(0.0F, 0, 0, 0), p(0.3F, 0, 1.0F, 0), p(1.0F, 0, -12.0F, 0)))
			.addAnimation("eye", rot(r(0.0F, 0, 0, 0), r(1.0F, 35.0F, 0, 12.0F)));
		for (int i = 0; i < 10; i++) {
			b.addAnimation("lash" + i, rot(r(0.0F, 0, 0, 0), r(1.0F, 55.0F, 0, 0)));
			b.addAnimation("bloom" + i, pos(p(0.0F, 0, 0, 0), p(0.3F, 0, 0, 0), p(1.0F, 0, 7.0F, 3.0F)));
			b.addAnimation("bloom" + i, rot(r(0.0F, 0, 0, 0), r(1.0F, 40.0F * (i % 2 == 0 ? 1 : -1), 0, 90.0F)));
			b.addAnimation("bloom" + i, scl(s(0.0F, 1, 1, 1), s(0.4F, 1.2, 1.2, 1.2), s(1.0F, 0, 0, 0)));
		}
		return b.build();
	}
}
