package dev.elsewhere.bossart.auris;

import static dev.elsewhere.bossart.hush.Kf.p;
import static dev.elsewhere.bossart.hush.Kf.pos;
import static dev.elsewhere.bossart.hush.Kf.r;
import static dev.elsewhere.bossart.hush.Kf.rot;
import static dev.elsewhere.bossart.hush.Kf.s;
import static dev.elsewhere.bossart.hush.Kf.scale;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Auris, the Listening Engine. Timings match Auris.java (sonic charge 34 ticks, smash wind-up 10 ticks). */
public final class AurisAnimations {
	private AurisAnimations() {}

	/** Breathing, ear flexing and a pulsing chest core. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(3.2F).looping()
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(1.6F, 3, 0, 0), r(3.2F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(0.8F, -3, 4, 2), r(2.4F, -2, -4, -2), r(3.2F, 0, 0, 0)))
		.addAnimation("right_ear", rot(r(0F, 0, 0, 0), r(0.5F, 0, 0, -8), r(0.9F, 0, 0, 0), r(2.0F, 0, 0, -4), r(3.2F, 0, 0, 0)))
		.addAnimation("left_ear", rot(r(0F, 0, 0, 0), r(1.3F, 0, 0, 8), r(1.8F, 0, 0, 0), r(2.6F, 0, 0, 4), r(3.2F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.6F, -4, 0, 4), r(3.2F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.6F, -4, 0, -4), r(3.2F, 0, 0, 0)))
		.addAnimation("core", scale(s(0F, 1, 1, 1), s(0.4F, 1.12, 1.12, 1), s(0.8F, 1, 1, 1), s(1.2F, 1.08, 1.08, 1), s(1.6F, 1, 1, 1), s(3.2F, 1, 1, 1)))
		.build();

	/** Heavy stomping gait. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_leg", rot(r(0F, 28, 0, 0), r(0.8F, -28, 0, 0), r(1.6F, 28, 0, 0)))
		.addAnimation("left_leg", rot(r(0F, -28, 0, 0), r(0.8F, 28, 0, 0), r(1.6F, -28, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, -18, 0, 0), r(0.8F, 18, 0, 0), r(1.6F, -18, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 18, 0, 0), r(0.8F, -18, 0, 0), r(1.6F, 18, 0, 0)))
		.addAnimation("body", rot(r(0F, 4, 0, -4), r(0.4F, 6, 0, 0), r(0.8F, 4, 0, 4), r(1.2F, 6, 0, 0), r(1.6F, 4, 0, -4)))
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(0.4F, 0, -1, 0), p(0.8F, 0, 0, 0), p(1.2F, 0, -1, 0), p(1.6F, 0, 0, 0)))
		.build();

	/** It heard something: head cocks, ears swivel forward and flare. */
	public static final AnimationDefinition LISTEN = AnimationDefinition.Builder.withLength(2.0F).looping()
		.addAnimation("head", rot(r(0F, 8, 0, 16), r(0.7F, 10, 12, 18), r(1.3F, 8, -12, 12), r(2.0F, 8, 0, 16)))
		.addAnimation("body", rot(r(0F, 8, 0, 0), r(1.0F, 10, 0, 0), r(2.0F, 8, 0, 0)))
		.addAnimation("right_ear", rot(r(0F, -20, 0, -14), r(0.2F, -24, 0, -20), r(0.4F, -20, 0, -14), r(2.0F, -20, 0, -14)))
		.addAnimation("left_ear", rot(r(0F, -20, 0, 14), r(1.0F, -24, 0, 20), r(1.2F, -20, 0, 14), r(2.0F, -20, 0, 14)))
		.addAnimation("right_arm", rot(r(0F, -10, 0, 10), r(2.0F, -10, 0, 10)))
		.addAnimation("left_arm", rot(r(0F, -10, 0, -10), r(2.0F, -10, 0, -10)))
		.build();

	/** Sonic Boom: 1.7 s charge (rears back, chest core swells), release at 1.7 s, recover by 2.2 s. */
	public static final AnimationDefinition SONIC_CHARGE = AnimationDefinition.Builder.withLength(2.2F)
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.4F, -12, 0, 0), r(1.2F, -20, 0, 0), r(1.6F, -24, 0, 0), r(1.72F, 20, 0, 0), r(1.95F, 12, 0, 0), r(2.2F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(1.2F, -24, 0, 0), r(1.6F, -30, 0, 0), r(1.72F, 22, 0, 0), r(2.2F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.2F, 32, 0, 28), r(1.6F, 38, 0, 34), r(1.72F, -75, 0, -15), r(2.0F, -40, 0, -5), r(2.2F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.2F, 32, 0, -28), r(1.6F, 38, 0, -34), r(1.72F, -75, 0, 15), r(2.0F, -40, 0, 5), r(2.2F, 0, 0, 0)))
		.addAnimation("right_ear", rot(r(0F, 0, 0, 0), r(1.2F, 0, 0, -26), r(1.6F, 0, 0, -30), r(1.72F, 10, 0, 12), r(2.2F, 0, 0, 0)))
		.addAnimation("left_ear", rot(r(0F, 0, 0, 0), r(1.2F, 0, 0, 26), r(1.6F, 0, 0, 30), r(1.72F, 10, 0, -12), r(2.2F, 0, 0, 0)))
		.addAnimation("core", scale(s(0F, 1, 1, 1), s(1.0F, 1.25, 1.25, 1), s(1.6F, 1.5, 1.5, 1.4), s(1.72F, 1.9, 1.9, 2), s(2.0F, 1, 1, 1), s(2.2F, 1, 1, 1)))
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(1.6F, 0, 0, 2), p(1.72F, 0, 0, -3), p(2.2F, 0, 0, 0)))
		.build();

	/** Melee / impact smash: 0.5 s overhead wind-up, double-fist slam, recover by 1.2 s. */
	public static final AnimationDefinition SMASH = AnimationDefinition.Builder.withLength(1.2F)
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.35F, -160, 0, -25), r(0.47F, -168, 0, -28), r(0.56F, -40, 0, -12), r(0.8F, -35, 0, -10), r(1.2F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.35F, -160, 0, 25), r(0.47F, -168, 0, 28), r(0.56F, -40, 0, 12), r(0.8F, -35, 0, 10), r(1.2F, 0, 0, 0)))
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.4F, -16, 0, 0), r(0.56F, 26, 0, 0), r(0.8F, 20, 0, 0), r(1.2F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(0.4F, -12, 0, 0), r(0.56F, 16, 0, 0), r(1.2F, 0, 0, 0)))
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(0.4F, 0, 1, 0), p(0.56F, 0, -2.5F, -1), p(0.8F, 0, -2, -1), p(1.2F, 0, 0, 0)))
		.build();

	/** Deafened (8 s): hunched, clutching its ears, head shaking. Loops while deafened. */
	public static final AnimationDefinition DEAF = AnimationDefinition.Builder.withLength(2.0F).looping()
		.addAnimation("body", rot(r(0F, 24, 0, -5), r(1.0F, 26, 0, 5), r(2.0F, 24, 0, -5)))
		.addAnimation("head", rot(r(0F, 12, 0, -14), r(0.5F, 12, 0, 14), r(1.0F, 12, 0, -14), r(1.5F, 12, 0, 14), r(2.0F, 12, 0, -14)))
		.addAnimation("right_arm", rot(r(0F, -150, 0, -22), r(0.25F, -146, 0, -18), r(0.5F, -150, 0, -22), r(1.0F, -146, 0, -18), r(2.0F, -150, 0, -22)))
		.addAnimation("left_arm", rot(r(0F, -150, 0, 22), r(0.25F, -146, 0, 18), r(0.5F, -150, 0, 22), r(1.0F, -146, 0, 18), r(2.0F, -150, 0, 22)))
		.addAnimation("right_ear", rot(r(0F, 0, 0, -30), r(2.0F, 0, 0, -30)))
		.addAnimation("left_ear", rot(r(0F, 0, 0, 30), r(2.0F, 0, 0, 30)))
		.addAnimation("right_leg", rot(r(0F, -14, 0, 6), r(2.0F, -14, 0, 6)))
		.addAnimation("left_leg", rot(r(0F, -14, 0, -6), r(2.0F, -14, 0, -6)))
		.addAnimation("bone", pos(p(0F, 0, -2, 0), p(1.0F, 0, -2.5F, 0), p(2.0F, 0, -2, 0)))
		.build();

	/** Phase transition (2 s): rears up, arms flung wide, ears flared and quivering, core blazing. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.3F, -20, 0, 0), r(1.6F, -22, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(0.3F, -36, 0, 0), r(0.6F, -32, 6, 0), r(0.9F, -36, -6, 0), r(1.2F, -32, 6, 0), r(1.5F, -36, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.3F, -25, 0, 55), r(1.6F, -30, 0, 60), r(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, -25, 0, -55), r(1.6F, -30, 0, -60), r(2.0F, 0, 0, 0)))
		.addAnimation("right_ear", rot(r(0F, 0, 0, 0), r(0.3F, 0, -10, -30), r(0.45F, 0, 10, -30), r(0.6F, 0, -10, -30), r(0.75F, 0, 10, -30), r(0.9F, 0, -10, -30), r(1.6F, 0, 0, -30), r(2.0F, 0, 0, 0)))
		.addAnimation("left_ear", rot(r(0F, 0, 0, 0), r(0.3F, 0, 10, 30), r(0.45F, 0, -10, 30), r(0.6F, 0, 10, 30), r(0.75F, 0, -10, 30), r(0.9F, 0, 10, 30), r(1.6F, 0, 0, 30), r(2.0F, 0, 0, 0)))
		.addAnimation("core", scale(s(0F, 1, 1, 1), s(0.3F, 1.7, 1.7, 1.6), s(1.6F, 1.6, 1.6, 1.5), s(2.0F, 1, 1, 1)))
		.build();

	/** Death (driven by deathTime, 1 s): knees buckle, it folds forward, ears droop, the core dims. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(0.5F, 0, -5, 0), p(1.0F, 0, -9, 0)))
		.addAnimation("right_leg", rot(r(0F, 0, 0, 0), r(0.5F, -50, 0, 8), r(1.0F, -85, 0, 12)))
		.addAnimation("left_leg", rot(r(0F, 0, 0, 0), r(0.5F, -50, 0, -8), r(1.0F, -85, 0, -12)))
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.6F, 25, 0, 0), r(1.0F, 42, 0, 8)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(1.0F, 35, 0, 16)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.0F, -10, 0, -30)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.0F, -10, 0, 30)))
		.addAnimation("right_ear", rot(r(0F, 0, 0, 0), r(1.0F, 0, 0, -42)))
		.addAnimation("left_ear", rot(r(0F, 0, 0, 0), r(1.0F, 0, 0, 42)))
		.addAnimation("core", scale(s(0F, 1, 1, 1), s(1.0F, 0.6, 0.6, 1)))
		.build();
}
