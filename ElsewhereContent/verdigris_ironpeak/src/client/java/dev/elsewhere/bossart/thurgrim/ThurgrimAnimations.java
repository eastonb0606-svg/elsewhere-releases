package dev.elsewhere.bossart.thurgrim;

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
 * Thurgrim's keyframe animations, timed to ThurgrimBoss: Overhead Split 1.2 s wind-up then the slam (34 ticks),
 * Kingly Roar 1 s inhale then the roar (30 ticks), axe combo hits at 0.3/0.7/1.1 s (28 ticks), Avalanche Split
 * 1.5 s wind-up, throw, catch at 3.5 s (70 ticks), phase roar = war-drum command (2 s), death = drops to one knee
 * while the crown rolls away (1 s).
 */
@Environment(EnvType.CLIENT)
public final class ThurgrimAnimations {
	private ThurgrimAnimations() {}

	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.5F, rot(2, 0, 0)), k(3, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.5F, rot(-3, 4, 0)), k(3, rot(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.0F, rot(5, 0, 2)), k(2.0F, rot(2, 0, -2)), k(3, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, -4)), k(1.5F, rot(-3, 0, -7)), k(3, rot(0, 0, -4))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-15, 0, 4)), k(1.5F, rot(-18, 0, 6)), k(3, rot(-15, 0, 4))))
		.build();

	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-28, 0, 0)), k(0.8F, rot(28, 0, 0)), k(1.6F, rot(-28, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(28, 0, 0)), k(0.8F, rot(-28, 0, 0)), k(1.6F, rot(28, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(-22, 0, 0)), k(0.8F, rot(22, 0, 0)), k(1.6F, rot(-22, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(8, 0, 0)), k(0.8F, rot(-10, 0, 0)), k(1.6F, rot(8, 0, 0))))
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.4F, pos(0, 0.8F, 0)), k(0.8F, pos(0, 0, 0)), k(1.2F, pos(0, 0.8F, 0)), k(1.6F, pos(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(4, -5, 0)), k(0.8F, rot(4, 5, 0)), k(1.6F, rot(4, -5, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(14, 0, 0)), k(0.4F, rot(20, 0, 0)), k(0.8F, rot(14, 0, 0)), k(1.2F, rot(20, 0, 0)), k(1.6F, rot(14, 0, 0))))
		.build();

	/** Overhead Split: 1.2 s raise (red line), slam at 1.2 s, hold, recover by 1.7 s. */
	public static final AnimationDefinition SPLIT = AnimationDefinition.Builder.withLength(1.7F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-165, -15, 0)), k(1.1F, rot(-175, -15, 0)), k(1.25F, rot(-40, 0, 0)), k(1.45F, rot(-45, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-160, 25, 0)), k(1.1F, rot(-170, 25, 0)), k(1.25F, rot(-45, 20, 0)), k(1.45F, rot(-50, 20, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-40, 0, 0)), k(1.1F, rot(-50, 0, 0)), k(1.25F, rot(95, 0, 0)), k(1.45F, rot(95, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-12, 0, 0)), k(1.1F, rot(-16, 0, 0)), k(1.25F, rot(28, 0, 0)), k(1.45F, rot(26, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.1F, rot(-10, 0, 0)), k(1.25F, rot(-15, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.1F, rot(10, 0, 0)), k(1.25F, rot(25, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.1F, rot(-10, 0, 0)), k(1.25F, rot(-35, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(1.1F, pos(0, 0, 0)), k(1.25F, pos(0, -2.5F, 0)), k(1.45F, pos(0, -2.5F, 0)), k(1.7F, pos(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.1F, rot(-6, 0, 0)), k(1.25F, rot(40, 0, 0)), k(1.7F, rot(0, 0, 0))))
		.build();

	/** Kingly Roar: 1 s inhale (chest swells), roar at 1.0 s, settle by 1.5 s. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(1.5F)
		.addAnimation("body", ch(AnimationChannel.Targets.SCALE, k(0, scl(1, 1, 1)), k(0.9F, scl(1.12F, 1.04F, 1.25F)), k(1.05F, scl(1.05F, 1, 1.05F)), k(1.5F, scl(1, 1, 1))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(-14, 0, 0)), k(1.05F, rot(12, 0, 0)), k(1.3F, rot(10, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(-25, 0, 0)), k(1.05F, rot(10, 0, 0)), k(1.3F, rot(8, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("jaw", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(0, 0, 0)), k(1.0F, rot(40, 0, 0)), k(1.35F, rot(40, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(-10, 0, 20)), k(1.05F, rot(-20, 0, 55)), k(1.3F, rot(-20, 0, 55)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.9F, rot(-10, 0, -20)), k(1.05F, rot(-20, 0, -55)), k(1.3F, rot(-20, 0, -55)), k(1.5F, rot(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.05F, rot(35, 0, 0)), k(1.5F, rot(0, 0, 0))))
		.build();

	/** Axe combo: forehand at 0.3 s, backhand at 0.7 s, overhead chop at 1.1 s. */
	public static final AnimationDefinition COMBO = AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION,
			k(0, rot(0, 0, 0)), k(0.2F, rot(-80, 60, 0)), k(0.32F, rot(-80, -50, 0)),
			k(0.55F, rot(-85, -60, 0)), k(0.72F, rot(-80, 55, 0)),
			k(0.95F, rot(-170, 0, 0)), k(1.12F, rot(-50, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION,
			k(0, rot(0, 0, 0)), k(0.2F, rot(60, 0, 0)), k(0.32F, rot(60, 0, 0)), k(0.72F, rot(60, 0, 0)),
			k(0.95F, rot(-40, 0, 0)), k(1.12F, rot(80, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION,
			k(0, rot(0, 0, 0)), k(0.2F, rot(0, 35, 0)), k(0.32F, rot(5, -35, 0)), k(0.55F, rot(5, -40, 0)), k(0.72F, rot(5, 35, 0)),
			k(0.95F, rot(-12, 0, 0)), k(1.12F, rot(22, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-25, 0, 0)), k(1.1F, rot(-30, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.32F, rot(15, 0, 15)), k(0.72F, rot(15, 0, -15)), k(1.12F, rot(30, 0, 0)), k(1.4F, rot(0, 0, 0))))
		.build();

	/** Avalanche Split: 1.5 s wind-up behind the head, throw at 1.5 s (axe leaves the hand), catch at 3.5 s. */
	public static final AnimationDefinition BOOMERANG = AnimationDefinition.Builder.withLength(3.5F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION,
			k(0, rot(0, 0, 0)), k(1.0F, rot(-150, 40, 30)), k(1.45F, rot(-160, 45, 35)), k(1.6F, rot(-70, -40, 0)),
			k(3.0F, rot(-60, -10, 0)), k(3.3F, rot(-80, 20, 0)), k(3.5F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION,
			k(0, rot(0, 0, 0)), k(1.0F, rot(-8, 40, 0)), k(1.45F, rot(-10, 45, 0)), k(1.6F, rot(15, -35, 0)), k(2.2F, rot(5, -10, 0)), k(3.5F, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.0F, rot(-70, -30, 0)), k(1.6F, rot(20, 0, -20)), k(3.0F, rot(-60, 0, -10)), k(3.5F, rot(0, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.0F, rot(-30, 0, 0)), k(1.6F, rot(-40, 0, 0)), k(2.4F, rot(0, 0, 0))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.6F, rot(35, 0, 10)), k(3.5F, rot(0, 0, 0))))
		.build();

	/** Phase change: the war-drum command, axe raised to the sky, roar (2 s, the boss is invulnerable meanwhile). */
	public static final AnimationDefinition COMMAND = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-175, 0, 10)), k(1.6F, rot(-178, 0, 12)), k(2.0F, rot(0, 0, 0))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-30, 0, 0)), k(1.6F, rot(-30, 0, 0)), k(2.0F, rot(0, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION,
			k(0, rot(0, 0, 0)), k(0.4F, rot(-70, 0, -25)), k(0.6F, rot(-40, 0, -10)), k(0.8F, rot(-70, 0, -25)), k(1.0F, rot(-40, 0, -10)), k(1.2F, rot(-70, 0, -25)), k(2.0F, rot(0, 0, 0))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(-30, 0, 0)), k(1.6F, rot(-30, 0, 0)), k(2.0F, rot(0, 0, 0))))
		.addAnimation("jaw", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(40, 0, 0)), k(1.6F, rot(40, 0, 0)), k(2.0F, rot(0, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.SCALE, k(0, scl(1, 1, 1)), k(0.5F, scl(1.1F, 1.03F, 1.2F)), k(1.6F, scl(1.1F, 1.03F, 1.2F)), k(2.0F, scl(1, 1, 1))))
		.addAnimation("cape", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.6F, rot(30, 0, 8)), k(1.2F, rot(20, 0, -8)), k(2.0F, rot(0, 0, 0))))
		.build();

	/** Death (1 s): drops to one knee, the axe slips, the crown rolls away. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("root", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.35F, pos(0, -5, 0)), k(1.0F, pos(0, -5.5F, 0))))
		.addAnimation("right_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(80, 0, 0)), k(1.0F, rot(85, 0, 0))))
		.addAnimation("left_leg", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-80, 0, 0)), k(1.0F, rot(-85, 0, 0))))
		.addAnimation("body", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(18, 0, 0)), k(1.0F, rot(30, 0, 5))))
		.addAnimation("head", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(25, 0, 0)), k(1.0F, rot(35, 0, 0))))
		.addAnimation("right_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-30, 0, 0)), k(1.0F, rot(-10, 0, 10))))
		.addAnimation("axe", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(60, 0, 0)), k(1.0F, rot(130, 0, 0))))
		.addAnimation("left_arm", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-40, 0, -10)), k(1.0F, rot(-35, 0, -10))))
		.addAnimation("crown", ch(AnimationChannel.Targets.POSITION, k(0, pos(0, 0, 0)), k(0.3F, pos(0, 3, -4)), k(0.6F, pos(2, -10, -12)), k(1.0F, pos(8, -18, -16))))
		.addAnimation("crown", ch(AnimationChannel.Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-40, 0, 0)), k(0.6F, rot(-200, 0, 20)), k(1.0F, rot(-360, 0, 80))))
		.build();
}
