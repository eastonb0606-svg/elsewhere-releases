package dev.elsewhere.bossart.neferkhet;

import static dev.elsewhere.bossart.overworld_a.ArtAnim.pos;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.rot;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.rotL;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Keyframe animations for Neferkhet; timings follow NeferkhetEntity's wind-ups (ticks / 20 = seconds).
 * The staff is a child of the right arm: staff angle = arm angle + staff angle (0 = along the arm, "up" when it hangs).
 */
public final class NeferkhetArtAnimations {
	private NeferkhetArtAnimations() {}

	/** Slow regal breathing, the staff swaying in the right hand. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", pos(0, 0, 0, 0, 2.0F, 0, -0.4F, 0, 4.0F, 0, 0, 0))
		.addAnimation("head", rot(0, 6, 0, 0, 2.0F, 8, 4, 0, 4.0F, 6, 0, 0))
		.addAnimation("right_arm", rot(0, -12, 0, 0, 2.0F, -16, 0, 2, 4.0F, -12, 0, 0))
		.addAnimation("staff", rot(0, 12, 0, 0, 2.0F, 16, 0, -2, 4.0F, 12, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, -4, 2.0F, -4, 0, -7, 4.0F, 0, 0, -4))
		.build();

	/** A stiff mummy stride. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.6F).looping()
		.addAnimation("right_leg", rot(0, 25, 0, 0, 0.8F, -25, 0, 0, 1.6F, 25, 0, 0))
		.addAnimation("left_leg", rot(0, -25, 0, 0, 0.8F, 25, 0, 0, 1.6F, -25, 0, 0))
		.addAnimation("left_arm", rot(0, 18, 0, 0, 0.8F, -18, 0, 0, 1.6F, 18, 0, 0))
		.addAnimation("right_arm", rot(0, -6, 0, 0, 0.8F, 4, 0, 0, 1.6F, -6, 0, 0))
		.addAnimation("body", rot(0, 0, 4, 0, 0.8F, 0, -4, 0, 1.6F, 0, 4, 0))
		.addAnimation("root", pos(0, 0, 0, 0, 0.4F, 0, 0.6F, 0, 0.8F, 0, 0, 0, 1.2F, 0, 0.6F, 0, 1.6F, 0, 0, 0))
		.build();

	/** Sun Lance: 0.5 s staff raised high overhead, the sun pointed at the target, held while the beam fires. */
	public static final AnimationDefinition LANCE = AnimationDefinition.Builder.withLength(1.6F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.4F, -165, 0, 10, 0.5F, -168, 0, 10, 0.6F, -100, 0, 0, 1.1F, -100, 0, 0, 1.6F, 0, 0, 0))
		.addAnimation("staff", rot(0, 0, 0, 0, 0.4F, 160, 0, 0, 0.5F, 165, 0, 0, 0.6F, 190, 0, 0, 1.1F, 190, 0, 0, 1.6F, 360, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -25, 0, 0, 0.6F, 5, 0, 0, 1.6F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.4F, -8, -10, 0, 0.6F, 8, 5, 0, 1.6F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.4F, -20, 0, -30, 0.6F, 10, 0, -15, 1.6F, 0, 0, 0))
		.build();

	/** Sinking Sands: 1.5 s both hands on the staff, driving it down into the floor. */
	public static final AnimationDefinition SINK = AnimationDefinition.Builder.withLength(2.2F)
		.addAnimation("root", pos(0, 0, 0, 0, 1.2F, 0, -2, 0, 1.45F, 0, -4, 0, 1.8F, 0, -4, 0, 2.2F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 1.2F, -95, 15, 0, 1.45F, -55, 15, 0, 1.8F, -55, 15, 0, 2.2F, 0, 0, 0))
		.addAnimation("staff", rot(0, 0, 0, 0, 1.2F, 95, 0, 0, 1.45F, 55, 0, 0, 1.8F, 55, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 1.2F, -95, -30, 0, 1.45F, -55, -30, 0, 1.8F, -55, -30, 0, 2.2F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 1.2F, 10, 0, 0, 1.45F, 22, 0, 0, 1.8F, 22, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("right_leg", rot(0, 0, 0, 0, 1.45F, -20, 0, 0, 1.8F, -20, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("left_leg", rot(0, 0, 0, 0, 1.45F, 10, 0, 0, 1.8F, 10, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1.45F, 20, 0, 0, 2.2F, 0, 0, 0))
		.build();

	/** Pharaoh's backhand: 0.8 s wrapped arm drawn back, then a heavy sweep. */
	public static final AnimationDefinition SWING = AnimationDefinition.Builder.withLength(1.4F)
		.addAnimation("left_arm", rotL(0, 0, 0, 0, 0.75F, -70, -60, -40, 0.8F, -75, -65, -40, 0.95F, -85, 70, 0, 1.1F, -80, 75, 0, 1.4F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.75F, 0, -30, 0, 0.8F, 0, -32, 0, 0.95F, 5, 35, 0, 1.1F, 5, 35, 0, 1.4F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.75F, 0, 20, 0, 0.95F, 0, -20, 0, 1.4F, 0, 0, 0))
		.build();

	/** Waking the sarcophagi: 1.5 s both arms raised in command. */
	public static final AnimationDefinition SARCO = AnimationDefinition.Builder.withLength(2.2F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.8F, -150, 0, 25, 1.6F, -155, 0, 25, 2.2F, 0, 0, 0))
		.addAnimation("staff", rot(0, 0, 0, 0, 0.8F, 150, 0, 0, 1.6F, 155, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.8F, -150, 0, -25, 1.6F, -155, 0, -25, 2.2F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.8F, -25, 0, 0, 1.6F, -25, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.8F, -8, 0, 0, 1.6F, -8, 0, 0, 2.2F, 0, 0, 0))
		.build();

	/** Curse of the Ankh: 1 s left palm thrust at the marked player. */
	public static final AnimationDefinition CURSE = AnimationDefinition.Builder.withLength(1.8F)
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.6F, -70, 0, -20, 0.9F, -75, 0, -20, 1.0F, -100, 0, 0, 1.4F, -100, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.9F, -5, -20, 0, 1.0F, 6, 15, 0, 1.4F, 6, 15, 0, 1.8F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.9F, -10, 0, 0, 1.0F, 8, 0, 0, 1.8F, 0, 0, 0))
		.build();

	/** Phase transitions / the eclipse: staff thrust to the sky, head thrown back. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.4F, -170, 0, 15, 1.5F, -170, 0, 15, 2.0F, 0, 0, 0))
		.addAnimation("staff", rot(0, 0, 0, 0, 0.4F, 170, 0, 0, 1.5F, 170, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.4F, -40, 0, -60, 1.5F, -40, 0, -60, 2.0F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -40, 0, 0, 1.5F, -40, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("body", rotL(0, 0, 0, 0, 0.4F, -10, 0, 0, 0.5F, -10, 0, 3, 0.6F, -10, 0, -3, 0.7F, -10, 0, 3, 0.8F, -10, 0, -3, 0.9F, -10, 0, 0, 1.5F, -10, 0, 0, 2.0F, 0, 0, 0))
		.build();

	/** Death (3 s): sinks to its knees, the staff falls, then the king topples forward. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("root", pos(0, 0, 0, 0, 0.8F, 0, -11, 0, 3.0F, 0, -11, 0))
		.addAnimation("root", rot(0, 0, 0, 0, 1.4F, 0, 0, 0, 2.2F, 78, 0, 0, 2.35F, 70, 0, 0, 2.5F, 80, 0, 0, 3.0F, 80, 0, 0))
		.addAnimation("right_leg", rot(0, 0, 0, 0, 0.8F, 88, 0, 0, 3.0F, 88, 0, 0))
		.addAnimation("left_leg", rot(0, 0, 0, 0, 0.8F, 88, 0, 0, 3.0F, 88, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.8F, 30, 0, 0, 3.0F, 35, 0, 10))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.8F, -20, 0, 20, 3.0F, -150, 0, 20))
		.addAnimation("staff", rot(0, 0, 0, 0, 0.8F, 0, 0, -60, 3.0F, 0, 0, -80))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.8F, -10, 0, -10, 3.0F, -150, 0, -20))
		.build();
}
