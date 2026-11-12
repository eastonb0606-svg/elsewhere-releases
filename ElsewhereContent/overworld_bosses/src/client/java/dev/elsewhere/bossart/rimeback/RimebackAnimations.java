package dev.elsewhere.bossart.rimeback;

import static dev.elsewhere.bossart.overworld_b.Kf.*;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Rimeback keyframe animations, timed to RimebackEntity's real wind-ups: Glacier Hurl 40 ticks (0.5 s rip, 1.5 s
 * overhead hold) then throw; fist slam 20 ticks; frost breath 16 + 30 ticks; avalanche roar 60 + 50 ticks;
 * campfire stomp 14 ticks; 40-tick phase transition (phase 3 = Shatter, the rime plates fly off); 60-tick death.
 */
public final class RimebackAnimations {
	private RimebackAnimations() {}

	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("chest", rot(r(0, 0, 0, 0), r(2.0F, 2.5F, 0, 0), r(4.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(1.0F, -3, 0, 2), r(2.0F, 0, 0, 0), r(3.0F, -2, 0, -2), r(4.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.8F, 0, 0, 0), r(1.1F, 9, 0, 0), r(1.6F, 0, 0, 0), r(4.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(2.0F, -3, 0, 3), r(4.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(2.0F, -3, 0, -3), r(4.0F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(2.0F, 0, -0.6F, 0), p(4.0F, 0, 0, 0)))
		.build();

	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(2.0F).looping()
		.addAnimation("right_leg", rot(r(0, 24, 0, 0), r(1.0F, -24, 0, 0), r(2.0F, 24, 0, 0)))
		.addAnimation("left_leg", rot(r(0, -24, 0, 0), r(1.0F, 24, 0, 0), r(2.0F, -24, 0, 0)))
		.addAnimation("right_arm", rot(r(0, -16, 0, 0), r(1.0F, 16, 0, 0), r(2.0F, -16, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 16, 0, 0), r(1.0F, -16, 0, 0), r(2.0F, 16, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 3), r(1.0F, 0, 0, -3), r(2.0F, 0, 0, 3)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.5F, 0, -1.2F, 0), p(1.0F, 0, 0, 0), p(1.5F, 0, -1.2F, 0), p(2.0F, 0, 0, 0)))
		.build();

	/** Glacier Hurl wind-up, 2.0 s: crouch and rip the ice (0-0.5 s), heave it overhead, hold it there to 2.0 s. */
	public static final AnimationDefinition HURL = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.35F, 26, 0, 0), r(0.5F, 22, 0, 0), r(0.8F, -8, 0, 0), r(2.0F, -8, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(0.35F, 0, -3, 0), p(0.5F, 0, -2.5F, 0), p(0.8F, 0, 0, 0), p(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.35F, -45, 0, 0), r(0.5F, -60, 0, 0), r(0.8F, -168, 0, -6), r(1.3F, -171, 0, -6),
			r(1.6F, -166, 0, -6), r(2.0F, -170, 0, -6)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.35F, -45, 0, 0), r(0.5F, -60, 0, 0), r(0.8F, -168, 0, 6), r(1.3F, -171, 0, 6),
			r(1.6F, -166, 0, 6), r(2.0F, -170, 0, 6)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.35F, 10, 0, 0), r(0.8F, -18, 0, 0), r(2.0F, -15, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.5F, 22, 0, 0), r(0.8F, 10, 0, 0), r(2.0F, 6, 0, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.8F, 0, 0, 7), r(2.0F, 0, 0, 7)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(0.8F, 0, 0, -7), r(2.0F, 0, 0, -7)))
		.build();

	/** Follows HURL when the boulder leaves the hands. */
	public static final AnimationDefinition THROW = AnimationDefinition.Builder.withLength(0.9F)
		.addAnimation("body", rot(r(0, -8, 0, 0), r(0.2F, 26, 0, 0), r(0.45F, 14, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, -170, 0, -6), r(0.2F, -40, 0, 0), r(0.45F, -25, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, -170, 0, 6), r(0.2F, -40, 0, 0), r(0.45F, -25, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, -15, 0, 0), r(0.2F, 12, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 6, 0, 0), r(0.2F, 30, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 7), r(0.9F, 0, 0, 0)))
		.addAnimation("left_leg", rot(r(0, 0, 0, -7), r(0.9F, 0, 0, 0)))
		.build();

	/** Fist slam wind-up, 1.0 s: both fists joined high overhead, leaning back. */
	public static final AnimationDefinition SLAM_UP = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.6F, -12, 0, 0), r(1.0F, -16, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.6F, -165, 0, -14), r(1.0F, -182, 0, -16)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.6F, -165, 0, 14), r(1.0F, -182, 0, 16)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.6F, -12, 0, 0), r(1.0F, -14, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.6F, 18, 0, 0), r(1.0F, 22, 0, 0)))
		.build();

	public static final AnimationDefinition SLAM_DOWN = AnimationDefinition.Builder.withLength(0.9F)
		.addAnimation("body", rot(r(0, -16, 0, 0), rl(0.12F, 34, 0, 0), r(0.35F, 32, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), pl(0.12F, 0, -4, 0), p(0.35F, 0, -4, 0), p(0.9F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, -182, 0, -16), rl(0.12F, -40, 0, -10), r(0.35F, -38, 0, -10), r(0.9F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, -182, 0, 16), rl(0.12F, -40, 0, 10), r(0.35F, -38, 0, 10), r(0.9F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, -14, 0, 0), r(0.12F, -20, 0, 0), r(0.9F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 22, 0, 0), r(0.12F, 35, 0, 0), r(0.9F, 0, 0, 0)))
		.build();

	/** Frost Breath, 2.3 s: inhale (0.8 s wind-up), then a long exhale over the cone. */
	public static final AnimationDefinition BREATH = AnimationDefinition.Builder.withLength(2.3F)
		.addAnimation("chest", rot(r(0, 0, 0, 0), r(0.7F, -9, 0, 0), r(0.9F, 10, 0, 0), r(2.1F, 8, 0, 0), r(2.3F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.7F, -26, 0, 0), r(0.9F, 14, 0, 0), r(1.3F, 16, 0, 2), r(1.7F, 14, 0, -2), r(2.1F, 15, 0, 0),
			r(2.3F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.7F, 4, 0, 0), r(0.9F, 38, 0, 0), r(2.1F, 34, 0, 0), r(2.3F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.7F, 0, 0, 14), r(0.9F, -10, 0, 8), r(2.1F, -10, 0, 8), r(2.3F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.7F, 0, 0, -14), r(0.9F, -10, 0, -8), r(2.1F, -10, 0, -8), r(2.3F, 0, 0, 0)))
		.build();

	/** Avalanche / revenant roar, 5.5 s: rear up roaring, pound the ground twice, roar again. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(5.5F)
		.addAnimation("chest", rot(r(0, 0, 0, 0), r(0.6F, -18, 0, 0), r(1.4F, -16, 0, 0), rl(1.75F, 28, 0, 0), r(2.1F, 24, 0, 0), r(2.6F, -12, 0, 0),
			r(3.2F, -14, 0, 0), rl(3.5F, 28, 0, 0), r(3.9F, 22, 0, 0), r(4.6F, -10, 0, 0), r(5.5F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.6F, -30, 0, 0), r(1.4F, -28, 0, 0), r(1.75F, 5, 0, 0), r(2.6F, -24, 0, 0), r(3.5F, 5, 0, 0),
			r(4.6F, -20, 0, 0), r(5.5F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.6F, 40, 0, 0), r(1.4F, 38, 0, 0), r(1.75F, 10, 0, 0), r(2.6F, 34, 0, 0), r(3.5F, 10, 0, 0),
			r(4.6F, 30, 0, 0), r(5.5F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.6F, -100, 0, 48), r(1.4F, -105, 0, 50), rl(1.75F, -30, 0, 0), r(2.1F, -30, 0, 0),
			r(2.6F, -110, 0, 40), r(3.2F, -112, 0, 40), rl(3.5F, -30, 0, 0), r(3.9F, -30, 0, 0), r(4.6F, -95, 0, 45), r(5.5F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.6F, -100, 0, -48), r(1.4F, -105, 0, -50), rl(1.75F, -30, 0, 0), r(2.1F, -30, 0, 0),
			r(2.6F, -110, 0, -40), r(3.2F, -112, 0, -40), rl(3.5F, -30, 0, 0), r(3.9F, -30, 0, 0), r(4.6F, -95, 0, -45), r(5.5F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(1.4F, 0, 0, 0), pl(1.75F, 0, -3, 0), p(2.1F, 0, -3, 0), p(2.6F, 0, 0, 0), p(3.2F, 0, 0, 0),
			pl(3.5F, 0, -3, 0), p(3.9F, 0, -3, 0), p(4.6F, 0, 0, 0), p(5.5F, 0, 0, 0)))
		.build();

	/** Campfire stomp wind-up, 0.7 s: lifts the right foot high. */
	public static final AnimationDefinition STOMP_UP = AnimationDefinition.Builder.withLength(0.7F)
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(0.6F, -55, 0, 0), r(0.7F, -58, 0, 0)))
		.addAnimation("body", rot(r(0, 0, 0, 0), r(0.6F, -6, 0, 6), r(0.7F, -7, 0, 6)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.6F, -20, 0, 16), r(0.7F, -20, 0, 16)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.6F, -20, 0, -16), r(0.7F, -20, 0, -16)))
		.build();

	public static final AnimationDefinition STOMP_DOWN = AnimationDefinition.Builder.withLength(0.6F)
		.addAnimation("right_leg", rot(r(0, -58, 0, 0), rl(0.1F, 4, 0, 0), r(0.6F, 0, 0, 0)))
		.addAnimation("body", rot(r(0, -7, 0, 6), rl(0.1F, 10, 0, 0), r(0.6F, 0, 0, 0)))
		.addAnimation("body", pos(p(0, 0, 0, 0), pl(0.1F, 0, -2, 0), p(0.6F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, -20, 0, 16), r(0.1F, -10, 0, 8), r(0.6F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, -20, 0, -16), r(0.1F, -10, 0, -8), r(0.6F, 0, 0, 0)))
		.build();

	/** Phase 2 transition roar, 2.0 s (the boss is invulnerable for 40 ticks). */
	public static final AnimationDefinition PHASE_ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("chest", rot(r(0, 0, 0, 0), r(0.4F, -16, 0, 0), r(0.6F, -15, 0, 2), r(0.8F, -16, 0, -2), r(1.0F, -15, 0, 2),
			r(1.2F, -16, 0, -2), r(1.4F, -15, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -28, 0, 0), r(1.5F, -26, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.4F, 42, 0, 0), r(1.5F, 40, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -70, 0, 42), r(1.5F, -75, 0, 45), r(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, -70, 0, -42), r(1.5F, -75, 0, -45), r(2.0F, 0, 0, 0)))
		.build();

	/** Phase 3 Shatter, 2.0 s: hunches, then bursts; the rime plates fly off and vanish. */
	public static final AnimationDefinition SHATTER = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("chest", rot(r(0, 0, 0, 0), r(0.3F, 22, 0, 0), rl(0.45F, -22, 0, 0), r(1.2F, -18, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.3F, 15, 0, 0), rl(0.45F, -32, 0, 0), r(1.2F, -28, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.3F, 5, 0, 0), rl(0.45F, 45, 0, 0), r(1.2F, 40, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.3F, -60, 0, -30), rl(0.45F, -35, 0, 80), r(1.2F, -40, 0, 70), r(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.3F, -60, 0, 30), rl(0.45F, -35, 0, -80), r(1.2F, -40, 0, -70), r(2.0F, 0, 0, 0)))
		.addAnimation("right_plate", pos(p(0, 0, 0, 0), p(0.45F, 0, 0, 0), p(1.1F, -14, 18, 4)))
		.addAnimation("right_plate", rot(r(0, 0, 0, 0), r(0.45F, 0, 0, 0), r(1.1F, 140, 40, 200)))
		.addAnimation("right_plate", scale(s(0, 1, 1, 1), s(0.6F, 1, 1, 1), s(1.2F, 0.01F, 0.01F, 0.01F)))
		.addAnimation("left_plate", pos(p(0, 0, 0, 0), p(0.45F, 0, 0, 0), p(1.1F, 14, 18, 4)))
		.addAnimation("left_plate", rot(r(0, 0, 0, 0), r(0.45F, 0, 0, 0), r(1.1F, 120, -40, -200)))
		.addAnimation("left_plate", scale(s(0, 1, 1, 1), s(0.6F, 1, 1, 1), s(1.2F, 0.01F, 0.01F, 0.01F)))
		.addAnimation("chest_plate", pos(p(0, 0, 0, 0), p(0.45F, 0, 0, 0), p(1.1F, 0, 10, -22)))
		.addAnimation("chest_plate", rot(r(0, 0, 0, 0), r(0.45F, 0, 0, 0), r(1.1F, -160, 0, 30)))
		.addAnimation("chest_plate", scale(s(0, 1, 1, 1), s(0.6F, 1, 1, 1), s(1.2F, 0.01F, 0.01F, 0.01F)))
		.build();

	/** Death, 3.0 s: staggers, freezes stiff, sinks to its knees and cracks apart. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("chest", rot(r(0, 0, 0, 0), r(0.4F, -12, 0, 0), r(1.1F, -12, 0, 0), r(1.6F, 25, 0, 0), r(2.3F, 38, 0, 6), r(3.0F, 60, 0, 10)))
		.addAnimation("head", rot(r(0, 0, 0, 0), r(0.4F, -22, 0, 0), r(1.1F, -22, 0, 0), r(1.6F, 25, 0, 0), r(2.3F, 50, 0, 15), r(3.0F, 70, 0, 25)))
		.addAnimation("head", pos(p(0, 0, 0, 0), p(1.6F, 0, 0, 0), p(2.3F, 1, -1, -3), p(3.0F, 3, -3, -6)))
		.addAnimation("jaw", rot(r(0, 0, 0, 0), r(0.4F, 32, 0, 0), r(1.1F, 32, 0, 0), r(1.6F, 15, 0, 0), r(3.0F, 25, 0, 0)))
		.addAnimation("right_arm", rot(r(0, 0, 0, 0), r(0.4F, -10, 0, 30), r(1.1F, -10, 0, 30), r(1.6F, -5, 0, 12), r(2.3F, 0, 0, 40),
			r(3.0F, 10, 0, 70)))
		.addAnimation("right_arm", pos(p(0, 0, 0, 0), p(1.6F, 0, 0, 0), p(3.0F, -5, -3, 0)))
		.addAnimation("left_arm", rot(r(0, 0, 0, 0), r(0.4F, -10, 0, -30), r(1.1F, -10, 0, -30), r(1.6F, -5, 0, -12), r(2.3F, 0, 0, -40),
			r(3.0F, 10, 0, -70)))
		.addAnimation("left_arm", pos(p(0, 0, 0, 0), p(1.6F, 0, 0, 0), p(3.0F, 5, -3, 0)))
		.addAnimation("right_leg", rot(r(0, 0, 0, 0), r(1.1F, 0, 0, 0), r(1.6F, 70, 0, 10), r(3.0F, 80, 0, 25)))
		.addAnimation("left_leg", rot(r(0, 0, 0, 0), r(1.1F, 0, 0, 0), r(1.6F, 70, 0, -10), r(3.0F, 80, 0, -25)))
		.addAnimation("body", pos(p(0, 0, 0, 0), p(1.1F, 0, 0, 0), p(1.6F, 0, -9, 0), p(3.0F, 0, -13, 0)))
		.build();
}
