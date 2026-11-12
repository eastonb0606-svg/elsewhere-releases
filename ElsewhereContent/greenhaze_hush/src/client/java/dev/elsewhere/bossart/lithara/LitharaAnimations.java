package dev.elsewhere.bossart.lithara;

import static dev.elsewhere.bossart.hush.Kf.p;
import static dev.elsewhere.bossart.hush.Kf.pos;
import static dev.elsewhere.bossart.hush.Kf.r;
import static dev.elsewhere.bossart.hush.Kf.rot;
import static dev.elsewhere.bossart.hush.Kf.s;
import static dev.elsewhere.bossart.hush.Kf.scale;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Lithara, the Geode Cantor. Timings follow Lithara.java (16 ticks per sung note, rain lands after 30 ticks, fangs after 20+). */
public final class LitharaAnimations {
	private LitharaAnimations() {}

	/** Hovering: slow bob, swaying robe and arms, the hand crystals turning. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(2.0F, 0, 1.5F, 0), p(4.0F, 0, 0, 0)))
		.addAnimation("robe", rot(r(0F, 4, 0, 0), r(1.0F, 6, 0, 2), r(2.0F, 4, 0, 0), r(3.0F, 6, 0, -2), r(4.0F, 4, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(2.0F, -6, 0, 4), r(4.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(2.0F, -6, 0, -4), r(4.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(1.3F, -4, 6, 0), r(2.7F, -4, -6, 0), r(4.0F, 0, 0, 0)))
		.addAnimation("right_hand", rot(r(0F, 0, 0, 0), r(1.0F, 0, 90, 0), r(2.0F, 0, 180, 0), r(3.0F, 0, 270, 0), r(4.0F, 0, 360, 0)))
		.addAnimation("left_hand", rot(r(0F, 0, 0, 0), r(1.0F, 0, -90, 0), r(2.0F, 0, -180, 0), r(3.0F, 0, -270, 0), r(4.0F, 0, -360, 0)))
		.addAnimation("crystal_4", rot(r(0F, 0, 0, 0), r(2.0F, -3, 0, 0), r(4.0F, 0, 0, 0)))
		.build();

	/** Singing (one 0.8 s beat per note): the concept pose, arms raised in a V, head up, crystals pulsing on each note. */
	public static final AnimationDefinition SING = AnimationDefinition.Builder.withLength(0.8F).looping()
		.addAnimation("right_arm", rot(r(0F, -130, 0, -50), r(0.15F, -142, 0, -56), r(0.5F, -130, 0, -50), r(0.8F, -130, 0, -50)))
		.addAnimation("left_arm", rot(r(0F, -130, 0, 50), r(0.15F, -142, 0, 56), r(0.5F, -130, 0, 50), r(0.8F, -130, 0, 50)))
		.addAnimation("head", rot(r(0F, -14, 0, 0), r(0.15F, -22, 0, 0), r(0.5F, -14, 0, 0), r(0.8F, -14, 0, 0)))
		.addAnimation("body", rot(r(0F, -4, 0, 0), r(0.15F, -8, 0, 0), r(0.8F, -4, 0, 0)))
		.addAnimation("crystal_0", scale(s(0F, 1, 1, 1), s(0.12F, 1.15, 1.18, 1.15), s(0.5F, 1, 1, 1), s(0.8F, 1, 1, 1)))
		.addAnimation("crystal_1", scale(s(0F, 1, 1, 1), s(0.12F, 1.15, 1.18, 1.15), s(0.5F, 1, 1, 1), s(0.8F, 1, 1, 1)))
		.addAnimation("crystal_2", scale(s(0F, 1, 1, 1), s(0.12F, 1.15, 1.18, 1.15), s(0.5F, 1, 1, 1), s(0.8F, 1, 1, 1)))
		.addAnimation("crystal_3", scale(s(0F, 1, 1, 1), s(0.12F, 1.15, 1.18, 1.15), s(0.5F, 1, 1, 1), s(0.8F, 1, 1, 1)))
		.addAnimation("crystal_4", scale(s(0F, 1, 1, 1), s(0.12F, 1.15, 1.18, 1.15), s(0.5F, 1, 1, 1), s(0.8F, 1, 1, 1)))
		.build();

	/** Conducting the answer window: alternating sweeps of the arms, head and torso following the beat. */
	public static final AnimationDefinition CONDUCT = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_arm", rot(r(0F, -95, 0, -10), r(0.4F, -60, 20, 10), r(0.8F, -105, 0, -25), r(1.2F, -70, -15, 0), r(1.6F, -95, 0, -10)))
		.addAnimation("left_arm", rot(r(0F, -70, 15, 0), r(0.4F, -105, 0, 25), r(0.8F, -60, -20, -10), r(1.2F, -95, 0, 10), r(1.6F, -70, 15, 0)))
		.addAnimation("body", rot(r(0F, 0, 8, 0), r(0.8F, 0, -8, 0), r(1.6F, 0, 8, 0)))
		.addAnimation("head", rot(r(0F, -6, 10, 0), r(0.8F, -6, -10, 0), r(1.6F, -6, 10, 0)))
		.build();

	/** Crystal armor shattered (10 s): pitched forward, arms hanging, trembling. */
	public static final AnimationDefinition SHATTERED = AnimationDefinition.Builder.withLength(2.0F).looping()
		.addAnimation("body", rot(r(0F, 18, 0, -3), r(1.0F, 20, 0, 3), r(2.0F, 18, 0, -3)))
		.addAnimation("head", rot(r(0F, 26, 0, 4), r(0.5F, 24, 0, -4), r(1.0F, 26, 0, 4), r(1.5F, 24, 0, -4), r(2.0F, 26, 0, 4)))
		.addAnimation("right_arm", rot(r(0F, 25, 0, -12), r(1.0F, 22, 0, -10), r(2.0F, 25, 0, -12)))
		.addAnimation("left_arm", rot(r(0F, 25, 0, 12), r(1.0F, 22, 0, 10), r(2.0F, 25, 0, 12)))
		.addAnimation("bone", pos(p(0F, 0, -2, 0), p(1.0F, 0, -3, 0), p(2.0F, 0, -2, 0)))
		.build();

	/** Fang Psalm: arms rise (0.5 s), then sweep down and point along the fang line (fangs erupt from 1.0 s). */
	public static final AnimationDefinition PSALM = AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.5F, -150, 0, -20), r(0.65F, -75, 0, 8), r(1.0F, -72, 0, 8), r(1.4F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.5F, -150, 0, 20), r(0.65F, -75, 0, -8), r(1.0F, -72, 0, -8), r(1.4F, 0, 0, 0)))
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.5F, -10, 0, 0), r(0.65F, 16, 0, 0), r(1.0F, 14, 0, 0), r(1.4F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(0.5F, -14, 0, 0), r(0.65F, 10, 0, 0), r(1.4F, 0, 0, 0)))
		.build();

	/** Crystal Rain: arms climb for 1.5 s while the crystals swell, then are flung outward as the rain lands. */
	public static final AnimationDefinition RAIN = AnimationDefinition.Builder.withLength(2.1F)
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.3F, -165, 0, -15), r(1.45F, -170, 0, -18), r(1.6F, -40, 0, -60), r(2.1F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.3F, -165, 0, 15), r(1.45F, -170, 0, 18), r(1.6F, -40, 0, 60), r(2.1F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(1.3F, -30, 0, 0), r(1.6F, 8, 0, 0), r(2.1F, 0, 0, 0)))
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(1.4F, 0, 3, 0), p(1.6F, 0, -1, 0), p(2.1F, 0, 0, 0)))
		.addAnimation("crystal_0", rot(r(0F, 0, 0, 0), r(1.4F, 0, 0, -14), r(1.6F, 0, 0, 0)))
		.addAnimation("crystal_3", rot(r(0F, 0, 0, 0), r(1.4F, 0, 0, 14), r(1.6F, 0, 0, 0)))
		.addAnimation("crystal_4", scale(s(0F, 1, 1, 1), s(1.4F, 1.25, 1.3, 1.25), s(1.6F, 1, 1, 1)))
		.build();

	/** Shard volley: a quick torso wind-up and a two-handed fling. */
	public static final AnimationDefinition SHARDS = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.2F, -8, 20, 0), r(0.35F, 10, -14, 0), r(0.8F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.2F, -40, 0, -30), r(0.35F, -100, 0, 10), r(0.8F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.2F, -30, 0, 20), r(0.35F, -90, 0, -10), r(0.8F, 0, 0, 0)))
		.addAnimation("crystal_4", rot(r(0F, 0, 0, 0), r(0.35F, 12, 0, 0), r(0.8F, 0, 0, 0)))
		.build();

	/** Summoning the chime vexes: arms spread wide palms-up, head thrown back, rising. */
	public static final AnimationDefinition VEX = AnimationDefinition.Builder.withLength(1.3F)
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.4F, -60, 0, -70), r(1.0F, -60, 0, -72), r(1.3F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.4F, -60, 0, 70), r(1.0F, -60, 0, 72), r(1.3F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(0.4F, -25, 0, 0), r(1.0F, -25, 0, 0), r(1.3F, 0, 0, 0)))
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(0.5F, 0, 2, 0), p(1.3F, 0, 0, 0)))
		.build();

	/** Phase transition (2 s): she arches back, arms flung out, the crystal crown flares open. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(0.3F, -18, 0, 0), r(1.6F, -20, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(0.3F, -38, 0, 0), r(1.6F, -40, 0, 0), r(2.0F, 0, 0, 0)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(0.3F, -80, 0, -80), r(1.6F, -85, 0, -82), r(2.0F, 0, 0, 0)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(0.3F, -80, 0, 80), r(1.6F, -85, 0, 82), r(2.0F, 0, 0, 0)))
		.addAnimation("crystal_0", rot(r(0F, 0, 0, 0), r(0.3F, 0, 0, -18), r(1.6F, 0, 0, -18), r(2.0F, 0, 0, 0)))
		.addAnimation("crystal_1", rot(r(0F, 0, 0, 0), r(0.3F, 0, 0, -10), r(1.6F, 0, 0, -10), r(2.0F, 0, 0, 0)))
		.addAnimation("crystal_2", rot(r(0F, 0, 0, 0), r(0.3F, 0, 0, 10), r(1.6F, 0, 0, 10), r(2.0F, 0, 0, 0)))
		.addAnimation("crystal_3", rot(r(0F, 0, 0, 0), r(0.3F, 0, 0, 18), r(1.6F, 0, 0, 18), r(2.0F, 0, 0, 0)))
		.addAnimation("crystal_4", scale(s(0F, 1, 1, 1), s(0.3F, 1.3, 1.35, 1.3), s(1.6F, 1.3, 1.35, 1.3), s(2.0F, 1, 1, 1)))
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(0.3F, 0, 3, 0), p(1.6F, 0, 3, 0), p(2.0F, 0, 0, 0)))
		.build();

	/** Death (driven by deathTime, 1 s): she sinks, folds forward and her crystal crown topples apart. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("bone", pos(p(0F, 0, 0, 0), p(1.0F, 0, -10, 0)))
		.addAnimation("body", rot(r(0F, 0, 0, 0), r(1.0F, 35, 0, 0)))
		.addAnimation("head", rot(r(0F, 0, 0, 0), r(1.0F, 40, 0, 10)))
		.addAnimation("right_arm", rot(r(0F, 0, 0, 0), r(1.0F, 30, 0, -20)))
		.addAnimation("left_arm", rot(r(0F, 0, 0, 0), r(1.0F, 30, 0, 20)))
		.addAnimation("crystal_0", rot(r(0F, 0, 0, 0), r(1.0F, -30, 0, -45)))
		.addAnimation("crystal_1", rot(r(0F, 0, 0, 0), r(1.0F, -40, 0, -25)))
		.addAnimation("crystal_2", rot(r(0F, 0, 0, 0), r(1.0F, -40, 0, 25)))
		.addAnimation("crystal_3", rot(r(0F, 0, 0, 0), r(1.0F, -30, 0, 45)))
		.addAnimation("crystal_4", rot(r(0F, 0, 0, 0), r(1.0F, -50, 0, 0)))
		.addAnimation("robe", rot(r(0F, 0, 0, 0), r(1.0F, 12, 0, 0)))
		.build();
}
