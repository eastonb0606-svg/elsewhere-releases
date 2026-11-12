package dev.elsewhere.bossart.hrolf;

import static dev.elsewhere.bossart.ironpeak.Anim.ch;
import static dev.elsewhere.bossart.ironpeak.Anim.k;
import static dev.elsewhere.bossart.ironpeak.Anim.pos;
import static dev.elsewhere.bossart.ironpeak.Anim.rot;
import static dev.elsewhere.bossart.ironpeak.Anim.scl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;

/**
 * Hrolf Grimhorn and his war goat, timed to HrolfBoss / GrimhornEntity: saddle swing hits at 0.4 s (12 ticks),
 * horn blast at 1.0 s (30 ticks), overhead crack at 1.0 s (28 ticks), shield-break kick at 0.3 s (12 ticks),
 * war-cry shout at 0.5 s (20 ticks); Grimhorn paws for 1.5 s, charges, and reels when stunned.
 */
@Environment(EnvType.CLIENT)
public final class HrolfAnimations {
	private HrolfAnimations() {}

	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.5F, rot(2, 0, 0)), k(3, rot(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.2F, rot(5, 0, 2)), k(2.2F, rot(2, 0, -2)), k(3, rot(0, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-10, 0, 3)), k(1.5F, rot(-13, 0, 5)), k(3, rot(-10, 0, 3))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, -3)), k(1.5F, rot(-3, 0, -5)), k(3, rot(0, 0, -3))))
		.build();

	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.0F).looping()
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-35, 0, 0)), k(0.5F, rot(35, 0, 0)), k(1, rot(-35, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(35, 0, 0)), k(0.5F, rot(-35, 0, 0)), k(1, rot(35, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-30, 0, 0)), k(0.5F, rot(30, 0, 0)), k(1, rot(-30, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(12, 0, 0)), k(0.5F, rot(-14, 0, 0)), k(1, rot(12, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(15, 0, 0)), k(0.25F, rot(22, 0, 0)), k(0.5F, rot(15, 0, 0)), k(0.75F, rot(22, 0, 0)), k(1, rot(15, 0, 0))))
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.25F, pos(0, 0.6F, 0)), k(0.5F, pos(0, 0, 0)), k(0.75F, pos(0, 0.6F, 0)), k(1, pos(0, 0, 0))))
		.build();

	public static final AnimationDefinition SWING = AnimationDefinition.Builder.withLength(0.6F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-110, 55, 0)), k(0.42F, rot(-75, -55, 0)), k(0.6F, rot(0, 0, 0))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(70, 0, 0)), k(0.42F, rot(75, 0, 0)), k(0.6F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(0, 30, 0)), k(0.42F, rot(8, -30, 0)), k(0.6F, rot(0, 0, 0))))
		.build();

	public static final AnimationDefinition HORN = AnimationDefinition.Builder.withLength(1.5F)
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-125, 40, 0)), k(1.35F, rot(-130, 40, 0)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(-20, 0, 20)), k(1.05F, rot(-40, 0, 50)), k(1.35F, rot(-40, 0, 50)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-10, 0, 0)), k(0.9F, rot(-12, 0, 0)), k(1.05F, rot(-30, 0, 0)), k(1.35F, rot(-30, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.SCALE, k(0, scl(1, 1, 1)), k(0.9F, scl(1.1F, 1.03F, 1.2F)), k(1.1F, scl(1, 1, 1)), k(1.5F, scl(1, 1, 1))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(-6, 0, 0)), k(1.05F, rot(-14, 0, 0)), k(1.35F, rot(-12, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.05F, rot(35, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.build();

	public static final AnimationDefinition OVERHEAD = AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.6F, rot(-168, -12, 0)), k(0.8F, rot(-174, -12, 0)), k(0.95F, rot(-170, -12, 0)), k(1.05F, rot(-40, 0, 0)), k(1.2F, rot(-42, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.6F, rot(-165, 22, 0)), k(0.8F, rot(-171, 22, 0)), k(0.95F, rot(-167, 22, 0)), k(1.05F, rot(-45, 18, 0)), k(1.2F, rot(-47, 18, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.6F, rot(-30, 0, 0)), k(0.95F, rot(-35, 0, 0)), k(1.05F, rot(100, 0, 0)), k(1.2F, rot(100, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.6F, rot(-14, 0, 0)), k(0.95F, rot(-16, 0, 0)), k(1.05F, rot(28, 0, 0)), k(1.2F, rot(26, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.95F, rot(-15, 0, 0)), k(1.05F, rot(-35, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.95F, rot(10, 0, 0)), k(1.05F, rot(25, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.95F, pos(0, 0, 0)), k(1.05F, pos(0, -2, 0)), k(1.2F, pos(0, -2, 0)), k(1.4F, pos(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.95F, rot(-5, 0, 0)), k(1.05F, rot(40, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.build();

	public static final AnimationDefinition KICK = AnimationDefinition.Builder.withLength(0.6F)
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.15F, rot(40, 0, 0)), k(0.3F, rot(-95, 0, 0)), k(0.42F, rot(-90, 0, 0)), k(0.6F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.15F, rot(8, 0, 0)), k(0.3F, rot(-18, 0, 0)), k(0.42F, rot(-16, 0, 0)), k(0.6F, rot(0, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-30, 0, 35)), k(0.6F, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-30, 0, -35)), k(0.6F, rot(0, 0, 0))))
		.build();

	public static final AnimationDefinition WARCRY = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.4F, rot(-172, 0, 12)), k(0.85F, rot(-175, 0, 14)), k(1, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.4F, rot(-150, 0, -30)), k(0.85F, rot(-155, 0, -32)), k(1, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.45F, rot(-28, 0, 0)), k(0.85F, rot(-28, 0, 0)), k(1, rot(0, 0, 0))))
		.addAnimation("jaw", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.45F, rot(0, 0, 0)), k(0.5F, rot(35, 0, 0)), k(0.85F, rot(35, 0, 0)), k(1, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.SCALE, k(0, scl(1, 1, 1)), k(0.45F, scl(1.1F, 1.03F, 1.2F)), k(0.85F, scl(1.06F, 1, 1.1F)), k(1, scl(1, 1, 1))))
		.build();

	/** Phase change (2 s, invulnerable): rears up in the saddle with the axe raised and roars. */
	public static final AnimationDefinition RALLY = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-172, 0, 12)), k(1.6F, rot(-175, 0, 14)), k(2, rot(0, 0, 0))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-20, 0, 0)), k(1.6F, rot(-20, 0, 0)), k(2, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-60, 0, -30)), k(1.6F, rot(-60, 0, -32)), k(2, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-28, 0, 0)), k(1.6F, rot(-28, 0, 0)), k(2, rot(0, 0, 0))))
		.addAnimation("jaw", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(35, 0, 0)), k(1.6F, rot(35, 0, 0)), k(2, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-12, 0, 0)), k(1.6F, rot(-12, 0, 0)), k(2, rot(0, 0, 0))))
		.build();

	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.35F, pos(0, -5, 0)), k(1, pos(0, -6, 0))))
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(80, 0, 0)), k(1, rot(85, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-80, 0, 0)), k(1, rot(-85, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.4F, rot(25, 0, 0)), k(1, rot(40, 0, -6))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1, rot(35, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1, rot(-20, 0, 15))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1, rot(-20, 0, -15))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1, rot(120, 0, 0))))
		.build();

	// ---- Grimhorn ----------------------------------------------------------------------------------------
	public static final AnimationDefinition GOAT_IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.0F, rot(6, 8, 0)), k(2.0F, rot(0, 0, 0)), k(3.0F, rot(4, -10, 0)), k(4, rot(0, 0, 0))))
		.addAnimation("ear_r", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(2.8F, rot(0, 0, 0)), k(2.9F, rot(0, 0, -30)), k(3.0F, rot(0, 0, 0)), k(4, rot(0, 0, 0))))
		.addAnimation("tail", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.4F, rot(0, 0, 25)), k(0.8F, rot(0, 0, -25)), k(1.2F, rot(0, 0, 0)), k(4, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.SCALE, k(0, scl(1, 1, 1)), k(2, scl(1.02F, 1.03F, 1)), k(4, scl(1, 1, 1))))
		.build();

	public static final AnimationDefinition GOAT_WALK = AnimationDefinition.Builder.withLength(1.0F).looping()
		.addAnimation("leg_fr", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-25, 0, 0)), k(0.5F, rot(25, 0, 0)), k(1, rot(-25, 0, 0))))
		.addAnimation("leg_bl", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-25, 0, 0)), k(0.5F, rot(25, 0, 0)), k(1, rot(-25, 0, 0))))
		.addAnimation("leg_fl", ch(AnimationChannel.Targets.ROTATION, k(0, rot(25, 0, 0)), k(0.5F, rot(-25, 0, 0)), k(1, rot(25, 0, 0))))
		.addAnimation("leg_br", ch(AnimationChannel.Targets.ROTATION, k(0, rot(25, 0, 0)), k(0.5F, rot(-25, 0, 0)), k(1, rot(25, 0, 0))))
		.addAnimation("neck", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.25F, rot(4, 0, 0)), k(0.5F, rot(0, 0, 0)), k(0.75F, rot(4, 0, 0)), k(1, rot(0, 0, 0))))
		.build();

	/** Wind-up (loops through the 1.5 s telegraph): head down, horns forward, a fore hoof scraping the rock. */
	public static final AnimationDefinition GOAT_PAW = AnimationDefinition.Builder.withLength(0.75F).looping()
		.addAnimation("leg_fr", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-45, 0, 0)), k(0.3F, rot(25, 0, 0)), k(0.45F, rot(20, 0, 0)), k(0.75F, rot(-45, 0, 0))))
		.addAnimation("leg_fr", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 3, 0)), k(0.3F, pos(0, 0, 0)), k(0.45F, pos(0, 0, 0)), k(0.75F, pos(0, 3, 0))))
		.addAnimation("neck", ch(AnimationChannel.Targets.ROTATION, k(0, rot(38, 0, 0)), k(0.35F, rot(42, 0, 0)), k(0.75F, rot(38, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(10, -6, 0)), k(0.35F, rot(12, 6, 0)), k(0.75F, rot(10, -6, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-4, 0, 0)), k(0.75F, rot(-4, 0, 0))))
		.addAnimation("tail", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-30, 0, 0)), k(0.75F, rot(-30, 0, 0))))
		.build();

	/** Charge: a low, fast gallop with the horns levelled. */
	public static final AnimationDefinition GOAT_CHARGE = AnimationDefinition.Builder.withLength(0.5F).looping()
		.addAnimation("leg_fr", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-50, 0, 0)), k(0.25F, rot(45, 0, 0)), k(0.5F, rot(-50, 0, 0))))
		.addAnimation("leg_fl", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-40, 0, 0)), k(0.25F, rot(50, 0, 0)), k(0.5F, rot(-40, 0, 0))))
		.addAnimation("leg_br", ch(AnimationChannel.Targets.ROTATION, k(0, rot(45, 0, 0)), k(0.25F, rot(-50, 0, 0)), k(0.5F, rot(45, 0, 0))))
		.addAnimation("leg_bl", ch(AnimationChannel.Targets.ROTATION, k(0, rot(50, 0, 0)), k(0.25F, rot(-40, 0, 0)), k(0.5F, rot(50, 0, 0))))
		.addAnimation("neck", ch(AnimationChannel.Targets.ROTATION, k(0, rot(45, 0, 0)), k(0.25F, rot(50, 0, 0)), k(0.5F, rot(45, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(15, 0, 0)), k(0.5F, rot(15, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(3, 0, 0)), k(0.25F, rot(-3, 0, 0)), k(0.5F, rot(3, 0, 0))))
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.12F, pos(0, 1.5F, 0)), k(0.25F, pos(0, 0, 0)), k(0.37F, pos(0, 1.5F, 0)), k(0.5F, pos(0, 0, 0))))
		.addAnimation("tail", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-50, 0, 0)), k(0.5F, rot(-50, 0, 0))))
		.build();

	/** Stunned after hitting the rails: dazed head wobble, legs splayed. */
	public static final AnimationDefinition GOAT_STUNNED = AnimationDefinition.Builder.withLength(1.2F).looping()
		.addAnimation("neck", ch(AnimationChannel.Targets.ROTATION, k(0, rot(20, 0, 12)), k(0.6F, rot(24, 0, -12)), k(1.2F, rot(20, 0, 12))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(10, 18, 8)), k(0.6F, rot(14, -18, -8)), k(1.2F, rot(10, 18, 8))))
		.addAnimation("leg_fr", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-10, 0, 12)), k(1.2F, rot(-10, 0, 12))))
		.addAnimation("leg_fl", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-10, 0, -12)), k(1.2F, rot(-10, 0, -12))))
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, -1, 0)), k(0.6F, pos(0, -1.5F, 0)), k(1.2F, pos(0, -1, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 4)), k(0.6F, rot(0, 0, -4)), k(1.2F, rot(0, 0, 4))))
		.build();
}
