package dev.elsewhere.bossart.mother_creak;

import static dev.elsewhere.bossart.core.Kf.p;
import static dev.elsewhere.bossart.core.Kf.pl;
import static dev.elsewhere.bossart.core.Kf.pos;
import static dev.elsewhere.bossart.core.Kf.r;
import static dev.elsewhere.bossart.core.Kf.rl;
import static dev.elsewhere.bossart.core.Kf.rot;
import static dev.elsewhere.bossart.core.Kf.s;
import static dev.elsewhere.bossart.core.Kf.scl;
import static dev.elsewhere.bossart.core.Kf.sl;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Mother Creak keyframes. The base pose is the concept painting's frozen reach (arms out, leaning in); every
 * channel is an offset from it. Timings match the server: branch claw strikes at 0.8 s (CLAW_WINDUP 16 ticks),
 * Root Burst spikes erupt at 1.0 s (BURST_WINDUP 20 ticks), the stun lasts 6 s.
 */
public final class MotherCreakAnimations {
	private MotherCreakAnimations() {
	}

	/** Watched: almost still, a slow creaking breath. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("torso", rot(r(0.0F, 0, 0, 0), r(2.0F, 2.0F, 0, 1.0F), r(4.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 0, 0, 0), r(2.0F, -3.0F, 4.0F, 0), r(4.0F, 0, 0, 0)))
		.addAnimation("hand_r", rot(r(0.0F, 0, 0, 0), r(2.0F, 8.0F, 0, 0), r(4.0F, 0, 0, 0)))
		.addAnimation("hand_l", rot(r(0.0F, 0, 0, 0), r(2.0F, -6.0F, 0, 0), r(4.0F, 0, 0, 0)))
		.addAnimation("heart", scl(s(0.0F, 1, 1, 1), s(0.4F, 1.25, 1.25, 1.1), s(0.8F, 1, 1, 1), s(4.0F, 1, 1, 1)))
		.build();

	/** The creak twitch: a sudden head snap and claw flex while someone is watching her. */
	public static final AnimationDefinition TWITCH = AnimationDefinition.Builder.withLength(0.6F)
		.addAnimation("head", rot(rl(0.0F, 0, 0, 0), rl(0.05F, -8.0F, 25.0F, 18.0F), rl(0.15F, -4.0F, 18.0F, 12.0F), r(0.6F, 0, 0, 0)))
		.addAnimation("hand_l", rot(rl(0.0F, 0, 0, 0), rl(0.05F, 35.0F, 0, 0), r(0.6F, 0, 0, 0)))
		.addAnimation("claw_l0", rot(rl(0.0F, 0, 0, 0), rl(0.05F, 0, 0, -20.0F), r(0.6F, 0, 0, 0)))
		.addAnimation("claw_l2", rot(rl(0.0F, 0, 0, 0), rl(0.05F, 0, 0, 20.0F), r(0.6F, 0, 0, 0)))
		.addAnimation("arm_r", rot(rl(0.0F, 0, 0, 0), rl(0.08F, -6.0F, 0, 5.0F), r(0.6F, 0, 0, 0)))
		.build();

	/** Unseen: a lurching, uneven stride with the arms swinging low. Played through applyWalk. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(2.0F).looping()
		.addAnimation("leg_r", rot(r(0.0F, -28.0F, 0, 0), r(1.0F, 24.0F, 0, 0), r(2.0F, -28.0F, 0, 0)))
		.addAnimation("leg_l", rot(r(0.0F, 24.0F, 0, 0), r(1.0F, -28.0F, 0, 0), r(2.0F, 24.0F, 0, 0)))
		.addAnimation("root", pos(p(0.0F, 0, 0, 0), p(0.5F, 0, 1.2F, 0), p(1.0F, 0, 0, 0), p(1.5F, 0, 1.2F, 0), p(2.0F, 0, 0, 0)))
		.addAnimation("torso", rot(r(0.0F, 8.0F, 6.0F, -7.0F), r(0.5F, 12.0F, 0, 0), r(1.0F, 8.0F, -6.0F, 7.0F), r(1.5F, 12.0F, 0, 0), r(2.0F, 8.0F, 6.0F, -7.0F)))
		.addAnimation("arm_r", rot(r(0.0F, 75.0F, 0, -12.0F), r(1.0F, 40.0F, 0, -18.0F), r(2.0F, 75.0F, 0, -12.0F)))
		.addAnimation("arm_l", rot(r(0.0F, 10.0F, 0, 22.0F), r(1.0F, 40.0F, 0, 28.0F), r(2.0F, 10.0F, 0, 22.0F)))
		.addAnimation("head", rot(r(0.0F, -6.0F, 0, 6.0F), r(0.5F, 2.0F, 0, 0), r(1.0F, -6.0F, 0, -6.0F), r(1.5F, 2.0F, 0, 0), r(2.0F, -6.0F, 0, 6.0F)))
		.build();

	/** Dormant at the bottom of the Elder Trunk: knelt, head bowed, arms hanging. */
	public static final AnimationDefinition DORMANT = AnimationDefinition.Builder.withLength(6.0F).looping()
		.addAnimation("root", pos(p(0.0F, 0, -5.0F, 0), p(6.0F, 0, -5.0F, 0)))
		.addAnimation("leg_r", rot(r(0.0F, -35.0F, 0, 0), r(6.0F, -35.0F, 0, 0)))
		.addAnimation("leg_l", rot(r(0.0F, 62.0F, 0, 0), r(6.0F, 62.0F, 0, 0)))
		.addAnimation("torso", rot(r(0.0F, 28.0F, 0, 0), r(3.0F, 31.0F, 0, 0), r(6.0F, 28.0F, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 25.0F, -8.0F, 0), r(6.0F, 25.0F, -8.0F, 0)))
		.addAnimation("arm_r", rot(r(0.0F, 100.0F, 0, -24.0F), r(6.0F, 100.0F, 0, -24.0F)))
		.addAnimation("arm_l", rot(r(0.0F, 55.0F, 0, 42.0F), r(6.0F, 55.0F, 0, 42.0F)))
		.addAnimation("heart", scl(s(0.0F, 0.7, 0.7, 0.7), s(3.0F, 0.85, 0.85, 0.85), s(6.0F, 0.7, 0.7, 0.7)))
		.build();

	/** Branch claw: 0.8 s wind-up (arm drawn back overhead, claws splayed), swipe at 0.8 s, recover. */
	public static final AnimationDefinition CLAW = AnimationDefinition.Builder.withLength(1.3F)
		.addAnimation("arm_r", rot(r(0.0F, 0, 0, 0), r(0.55F, -65.0F, 20.0F, -15.0F), r(0.78F, -72.0F, 22.0F, -18.0F),
			rl(0.92F, 105.0F, -20.0F, 10.0F), r(1.3F, 0, 0, 0)))
		.addAnimation("torso", rot(r(0.0F, 0, 0, 0), r(0.6F, -6.0F, -24.0F, 0), r(0.78F, -8.0F, -28.0F, 0),
			rl(0.92F, 12.0F, 26.0F, 0), r(1.3F, 0, 0, 0)))
		.addAnimation("claw_r0", rot(r(0.0F, 0, 0, 0), r(0.6F, 0, 0, -25.0F), rl(0.92F, 0, 0, 10.0F), r(1.3F, 0, 0, 0)))
		.addAnimation("claw_r2", rot(r(0.0F, 0, 0, 0), r(0.6F, 0, 0, 25.0F), rl(0.92F, 0, 0, -10.0F), r(1.3F, 0, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 0, 0, 0), r(0.6F, -10.0F, -15.0F, 0), rl(0.92F, 8.0F, 20.0F, 0), r(1.3F, 0, 0, 0)))
		.addAnimation("leg_l", rot(r(0.0F, 0, 0, 0), r(0.6F, 12.0F, 0, 0), rl(0.92F, -16.0F, 0, 0), r(1.3F, 0, 0, 0)))
		.build();

	/** Root Burst: both arms rise for 1 s while the resin line shows, then slam into the floor at 1.0 s with a stomp. */
	public static final AnimationDefinition ROOT_BURST = AnimationDefinition.Builder.withLength(1.6F)
		.addAnimation("arm_r", rot(r(0.0F, 0, 0, 0), r(0.75F, -62.0F, 0, -26.0F), r(0.92F, -66.0F, 0, -28.0F),
			rl(1.02F, 95.0F, 0, -20.0F), r(1.3F, 92.0F, 0, -20.0F), r(1.6F, 0, 0, 0)))
		.addAnimation("arm_l", rot(r(0.0F, 0, 0, 0), r(0.75F, -112.0F, 0, 48.0F), r(0.92F, -116.0F, 0, 50.0F),
			rl(1.02F, 45.0F, 0, 40.0F), r(1.3F, 42.0F, 0, 40.0F), r(1.6F, 0, 0, 0)))
		.addAnimation("torso", rot(r(0.0F, 0, 0, 0), r(0.75F, -22.0F, 0, 0), r(0.92F, -24.0F, 0, 0),
			rl(1.02F, 30.0F, 0, 0), r(1.3F, 26.0F, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 0, 0, 0), r(0.75F, -25.0F, 0, 0), rl(1.02F, 10.0F, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("leg_r", rot(r(0.0F, 0, 0, 0), r(0.6F, -40.0F, 0, 0), r(0.9F, -48.0F, 0, 0), rl(1.0F, 4.0F, 0, 0), r(1.6F, 0, 0, 0)))
		.addAnimation("root", pos(p(0.0F, 0, 0, 0), p(0.9F, 0, 1.0F, 0), pl(1.02F, 0, -3.0F, 0), p(1.3F, 0, -2.5F, 0), p(1.6F, 0, 0, 0)))
		.addAnimation("heart", scl(s(0.0F, 1, 1, 1), s(0.9F, 1.3, 1.3, 1.2), sl(1.02F, 1.8, 1.8, 1.4), s(1.6F, 1, 1, 1)))
		.build();

	/** Stunned by a broken wall heart: she drops to one knee, the heart flares and throbs, then she begins to rise. */
	public static final AnimationDefinition STUN = AnimationDefinition.Builder.withLength(6.0F)
		.addAnimation("root", pos(p(0.0F, 0, 0, 0), pl(0.35F, 0, -5.0F, 0), p(5.5F, 0, -5.0F, 0), p(6.0F, 0, -2.0F, 0)))
		.addAnimation("leg_r", rot(r(0.0F, 0, 0, 0), rl(0.35F, -35.0F, 0, 0), r(5.5F, -35.0F, 0, 0), r(6.0F, -15.0F, 0, 0)))
		.addAnimation("leg_l", rot(r(0.0F, 0, 0, 0), rl(0.35F, 62.0F, 0, 0), r(5.5F, 62.0F, 0, 0), r(6.0F, 25.0F, 0, 0)))
		.addAnimation("torso", rot(r(0.0F, 0, 0, 0), rl(0.35F, 34.0F, 0, -6.0F), r(0.5F, 30.0F, 0, -6.0F), r(5.5F, 30.0F, 0, -6.0F), r(6.0F, 12.0F, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 0, 0, 0), r(0.4F, 30.0F, 0, 10.0F), r(1.5F, 26.0F, 0, -6.0F), r(3.0F, 30.0F, 0, 8.0F),
			r(4.5F, 26.0F, 0, -6.0F), r(6.0F, 10.0F, 0, 0)))
		.addAnimation("arm_r", rot(r(0.0F, 0, 0, 0), r(0.4F, 112.0F, 0, -26.0F), r(5.5F, 112.0F, 0, -26.0F), r(6.0F, 60.0F, 0, -10.0F)))
		.addAnimation("arm_l", rot(r(0.0F, 0, 0, 0), r(0.4F, 40.0F, 0, 46.0F), r(5.5F, 40.0F, 0, 46.0F), r(6.0F, 20.0F, 0, 20.0F)))
		.addAnimation("heart", scl(s(0.0F, 1, 1, 1), sl(0.2F, 2.2, 2.2, 1.6), s(0.7F, 1.6, 1.6, 1.3), s(1.2F, 2.0, 2.0, 1.5),
			s(1.7F, 1.6, 1.6, 1.3), s(2.2F, 2.0, 2.0, 1.5), s(2.7F, 1.6, 1.6, 1.3), s(3.2F, 2.0, 2.0, 1.5), s(3.7F, 1.6, 1.6, 1.3),
			s(4.2F, 2.0, 2.0, 1.5), s(4.7F, 1.6, 1.6, 1.3), s(5.2F, 2.0, 2.0, 1.5), s(6.0F, 1.2, 1.2, 1.1)))
		.build();

	/** Waking, and rising after each stun into the next phase: she straightens and roars with her branches flung wide. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.2F)
		.addAnimation("root", pos(p(0.0F, 0, -3.0F, 0), p(0.4F, 0, 0, 0), p(2.2F, 0, 0, 0)))
		.addAnimation("torso", rot(r(0.0F, 20.0F, 0, 0), r(0.5F, -16.0F, 0, 0), r(0.7F, -24.0F, 0, 0), r(1.7F, -22.0F, 0, 0), r(2.2F, 0, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 20.0F, 0, 0), r(0.6F, -38.0F, 0, 0), r(0.8F, -40.0F, 0, 6.0F), r(1.0F, -40.0F, 0, -6.0F),
			r(1.2F, -40.0F, 0, 6.0F), r(1.4F, -40.0F, 0, -6.0F), r(1.7F, -36.0F, 0, 0), r(2.2F, 0, 0, 0)))
		.addAnimation("arm_r", rot(r(0.0F, 90.0F, 0, -20.0F), r(0.6F, 40.0F, 0, 45.0F), r(1.7F, 40.0F, 0, 50.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("arm_l", rot(r(0.0F, 40.0F, 0, 30.0F), r(0.6F, -10.0F, 0, -30.0F), r(1.7F, -10.0F, 0, -34.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("claw_r0", rot(r(0.0F, 0, 0, 0), r(0.6F, 0, 0, -25.0F), r(1.7F, 0, 0, -25.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("claw_r2", rot(r(0.0F, 0, 0, 0), r(0.6F, 0, 0, 25.0F), r(1.7F, 0, 0, 25.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("claw_l0", rot(r(0.0F, 0, 0, 0), r(0.6F, 0, 0, -25.0F), r(1.7F, 0, 0, -25.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("claw_l2", rot(r(0.0F, 0, 0, 0), r(0.6F, 0, 0, 25.0F), r(1.7F, 0, 0, 25.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("branch0", rot(r(0.0F, 0, 0, 0), r(0.7F, -10.0F, 0, -12.0F), r(1.7F, -10.0F, 0, -12.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("branch4", rot(r(0.0F, 0, 0, 0), r(0.7F, -10.0F, 0, 12.0F), r(1.7F, -10.0F, 0, 12.0F), r(2.2F, 0, 0, 0)))
		.addAnimation("heart", scl(s(0.0F, 1, 1, 1), s(0.7F, 1.7, 1.7, 1.4), s(1.7F, 1.6, 1.6, 1.3), s(2.2F, 1, 1, 1)))
		.build();

	/** Death: she splits down the middle like a felled tree and topples back. Driven by deathTime (1 s). */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("body_r", rot(r(0.0F, 0, 0, 0), r(0.25F, 0, 0, 0), r(0.6F, 0, -8.0F, 32.0F), r(1.0F, 0, -10.0F, 48.0F)))
		.addAnimation("body_l", rot(r(0.0F, 0, 0, 0), r(0.25F, 0, 0, 0), r(0.6F, 0, 8.0F, -32.0F), r(1.0F, 0, 10.0F, -48.0F)))
		.addAnimation("torso", rot(r(0.0F, 0, 0, 0), r(0.25F, -10.0F, 0, 0), r(1.0F, -55.0F, 0, 0)))
		.addAnimation("head", rot(r(0.0F, 0, 0, 0), r(0.5F, 30.0F, 0, 20.0F), r(1.0F, 60.0F, 0, 35.0F)))
		.addAnimation("arm_r", rot(r(0.0F, 0, 0, 0), r(1.0F, 100.0F, 0, -40.0F)))
		.addAnimation("arm_l", rot(r(0.0F, 0, 0, 0), r(1.0F, 50.0F, 0, 40.0F)))
		.addAnimation("root", pos(p(0.0F, 0, 0, 0), p(1.0F, 0, -4.0F, 0)))
		.addAnimation("heart", scl(s(0.0F, 1.6, 1.6, 1.4), s(0.5F, 2.2, 2.2, 1.6), s(1.0F, 0.2, 0.2, 0.2)))
		.build();
}
