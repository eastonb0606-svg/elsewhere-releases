package dev.elsewhere.bossart.kazehana;

import static dev.elsewhere.bossart.petalwind.ArtAnim.pos;
import static dev.elsewhere.bossart.petalwind.ArtAnim.rot;
import static dev.elsewhere.bossart.petalwind.ArtAnim.rotLinear;

import net.minecraft.client.animation.AnimationDefinition;

/**
 * Kazehana's keyframes. Timings follow the boss code: Draw Cut wind-up 20 ticks (1.0 s) then a 6-8 tick dash, combo hits
 * every 10 ticks, Petal Parry 20 ticks, bell stun 60 ticks, Gale 15 ticks, phase roar 40 ticks, death 20 ticks.
 */
public final class KazehanaAnimations {
	private KazehanaAnimations() {}

	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("body", rot(0, 0, 0, 0, 1.5F, 2, 0, 0, 3, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1.5F, -2, 0, 0, 3, 0, 0, 0))
		.addAnimation("haori", rot(0, 0, 0, 0, 1.0F, 6, 0, 0, 2.0F, -2, 0, 0, 3, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 1.5F, -4, 0, 0, 3, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 1.5F, 3, 0, 0, 3, 0, 0, 0))
		.addAnimation("waist", pos(0, 0, 0, 0, 1.5F, 0, -0.4F, 0, 3, 0, 0, 0))
		.build();

	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.0F).looping()
		.addAnimation("right_leg", rot(0, -32, 0, 0, 0.5F, 32, 0, 0, 1, -32, 0, 0))
		.addAnimation("left_leg", rot(0, 32, 0, 0, 0.5F, -32, 0, 0, 1, 32, 0, 0))
		.addAnimation("left_arm", rot(0, -18, 0, 0, 0.5F, 18, 0, 0, 1, -18, 0, 0))
		.addAnimation("right_arm", rot(0, 6, 0, 0, 0.5F, -6, 0, 0, 1, 6, 0, 0))
		.addAnimation("waist", pos(0, 0, 0, 0, 0.25F, 0, 0.7F, 0, 0.5F, 0, 0, 0, 0.75F, 0, 0.7F, 0, 1, 0, 0, 0))
		.addAnimation("haori", rot(0, 12, 0, 0, 0.5F, 18, 0, 0, 1, 12, 0, 0))
		.build();

	/** Waiting on the roof: a slow formal bow, sword lowered (loops while dormant). */
	public static final AnimationDefinition BOW = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("waist", rot(0, 4, 0, 0, 0.7F, 38, 0, 0, 1.9F, 38, 0, 0, 2.6F, 4, 0, 0, 3, 4, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.7F, 14, 0, 0, 1.9F, 14, 0, 0, 2.6F, 0, 0, 0, 3, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 35, 0, 0, 3, 35, 0, 0))
		.addAnimation("katana", rot(0, 80, 0, 0, 3, 80, 0, 0))
		.addAnimation("left_arm", rot(0, 30, -25, 0, 3, 30, -25, 0))
		.addAnimation("haori", rot(0, 4, 0, 0, 0.7F, 0, 0, 0, 1.9F, 0, 0, 0, 3, 4, 0, 0))
		.build();

	/** Draw Cut wind-up (1.0 s): sinks low, twists, hand on the hilt at the hip. Held until the dash. */
	public static final AnimationDefinition IAIDO = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("waist", pos(0, 0, 0, 0, 0.3F, 0, -2.5F, 0, 1, 0, -2.5F, 0))
		.addAnimation("waist", rot(0, 0, 0, 0, 0.3F, 15, -35, 0, 1, 15, -35, 0))
		.addAnimation("right_leg", pos(0, 0, 0, 0, 0.3F, 0, -2.5F, 0, 1, 0, -2.5F, 0))
		.addAnimation("left_leg", pos(0, 0, 0, 0, 0.3F, 0, -2.5F, 0, 1, 0, -2.5F, 0))
		.addAnimation("right_leg", rot(0, 0, 0, 0, 0.3F, -40, 0, 0, 1, -40, 0, 0))
		.addAnimation("left_leg", rot(0, 0, 0, 0, 0.3F, 35, 0, 0, 1, 35, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.3F, 20, 25, -40, 1, 20, 25, -40))
		.addAnimation("katana", rot(0, 0, 0, 0, 0.3F, 150, 20, 0, 1, 150, 20, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.3F, 15, -40, 0, 1, 15, -40, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.3F, -15, 35, 0, 1, -15, 35, 0))
		.addAnimation("haori", rot(0, 0, 0, 0, 0.3F, 22, 0, 0, 1, 26, 0, 0))
		.build();

	/** The cut itself: an explosive lunge from the iaido crouch with the blade swept out wide, then recovery. */
	public static final AnimationDefinition DASH = AnimationDefinition.Builder.withLength(0.6F)
		.addAnimation("waist", pos(0, 0, -2.5F, 0, 0.1F, 0, -3, 0, 0.4F, 0, -1, 0, 0.6F, 0, 0, 0))
		.addAnimation("waist", rot(0, 15, -35, 0, 0.1F, 25, 40, 0, 0.4F, 10, 20, 0, 0.6F, 0, 0, 0))
		.addAnimation("right_leg", pos(0, 0, -2.5F, 0, 0.1F, 0, -3, 0, 0.4F, 0, -1, 0, 0.6F, 0, 0, 0))
		.addAnimation("left_leg", pos(0, 0, -2.5F, 0, 0.1F, 0, -3, 0, 0.4F, 0, -1, 0, 0.6F, 0, 0, 0))
		.addAnimation("right_leg", rot(0, -40, 0, 0, 0.1F, -60, 0, 0, 0.4F, -30, 0, 0, 0.6F, 0, 0, 0))
		.addAnimation("left_leg", rot(0, 35, 0, 0, 0.1F, 55, 0, 0, 0.4F, 25, 0, 0, 0.6F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 20, 25, -40, 0.1F, -55, -10, 75, 0.4F, -40, 0, 55, 0.6F, 0, 0, 0))
		.addAnimation("katana", rot(0, 150, 20, 0, 0.1F, 0, 0, 0, 0.6F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 15, -40, 0, 0.1F, 30, 0, -35, 0.4F, 20, 0, -20, 0.6F, 0, 0, 0))
		.addAnimation("head", rot(0, -15, 35, 0, 0.1F, -20, -40, 0, 0.4F, -10, -20, 0, 0.6F, 0, 0, 0))
		.addAnimation("haori", rot(0, 26, 0, 0, 0.1F, 55, 0, 0, 0.4F, 35, 0, 0, 0.6F, 0, 0, 0))
		.build();

	/** One combo slash (restarted for each of the 3 hits). */
	public static final AnimationDefinition COMBO = AnimationDefinition.Builder.withLength(0.5F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.12F, -110, 0, 10, 0.25F, 30, -20, -30, 0.5F, 0, 0, 0))
		.addAnimation("katana", rot(0, 0, 0, 0, 0.12F, -10, 0, 0, 0.25F, 20, 0, 0, 0.5F, 0, 0, 0))
		.addAnimation("waist", rot(0, 0, 0, 0, 0.12F, -8, 15, 0, 0.25F, 15, -25, 0, 0.5F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.12F, -40, 0, 0, 0.25F, 10, 0, 0, 0.5F, 0, 0, 0))
		.addAnimation("haori", rot(0, 0, 0, 0, 0.25F, 20, 0, 0, 0.5F, 0, 0, 0))
		.build();

	/** Petal Parry (1.0 s): the glowing blade held upright in front, braced. */
	public static final AnimationDefinition PARRY = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.1F, -55, 30, 0, 0.8F, -55, 30, 0, 1, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.1F, -55, -10, 0, 0.8F, -55, -10, 0, 1, 0, 0, 0))
		.addAnimation("waist", rot(0, 0, 0, 0, 0.1F, -8, 0, 0, 0.8F, -8, 0, 0, 1, 0, 0, 0))
		.addAnimation("right_leg", rot(0, 0, 0, 0, 0.1F, -15, 0, 0, 0.8F, -15, 0, 0, 1, 0, 0, 0))
		.addAnimation("left_leg", rot(0, 0, 0, 0, 0.1F, 15, 0, 0, 0.8F, 15, 0, 0, 1, 0, 0, 0))
		.build();

	/** Wind-bell stun (3 s): slumped and swaying, blade drooping. */
	public static final AnimationDefinition STUN = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("waist", rot(0, 0, 0, 0, 0.2F, 25, 0, 0, 0.8F, 22, 0, 8, 1.4F, 25, 0, -8, 2.0F, 22, 0, 8, 2.6F, 20, 0, 0, 3, 0, 0, 0))
		.addAnimation("waist", pos(0, 0, 0, 0, 0.2F, 0, -1.5F, 0, 2.6F, 0, -1.5F, 0, 3, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.2F, 30, 0, 0, 1.0F, 25, 0, 15, 1.8F, 28, 0, -15, 2.6F, 20, 0, 0, 3, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.2F, 50, 0, 10, 2.6F, 50, 0, 10, 3, 0, 0, 0))
		.addAnimation("katana", rot(0, 0, 0, 0, 0.2F, 70, 0, 0, 2.6F, 70, 0, 0, 3, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.2F, 40, -25, -10, 2.6F, 40, -25, -10, 3, 0, 0, 0))
		.build();

	/** Phase change (2 s): leans back with the sword raised to the sky, haori flaring. */
	public static final AnimationDefinition PHASE = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("waist", rot(0, 0, 0, 0, 0.3F, -18, 0, 0, 1.6F, -18, 0, 0, 2, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.3F, -30, 0, 0, 0.6F, -25, 0, 5, 0.9F, -30, 0, -5, 1.6F, -28, 0, 0, 2, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.3F, -130, 0, 25, 1.6F, -130, 0, 25, 2, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.3F, 20, -25, -70, 1.6F, 20, -25, -70, 2, 0, 0, 0))
		.addAnimation("haori", rot(0, 0, 0, 0, 0.3F, 45, 0, 0, 1.0F, 55, 0, 0, 1.6F, 45, 0, 0, 2, 0, 0, 0))
		.addAnimation("hat", rot(0, 0, 0, 0, 0.3F, -10, 0, 0, 1.6F, -10, 0, 0, 2, 0, 0, 0))
		.build();

	/** Gale (0.75 s): a full spinning sweep that throws the wind outward. */
	public static final AnimationDefinition GALE = AnimationDefinition.Builder.withLength(0.75F)
		.addAnimation("waist", rotLinear(0, 0, 0, 0, 0.1F, 0, 30, 0, 0.55F, 0, -330, 0, 0.75F, 0, -360, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.1F, 30, 0, 80, 0.6F, 30, 0, 80, 0.75F, 0, 0, 0))
		.addAnimation("haori", rot(0, 0, 0, 0, 0.2F, 50, 0, 0, 0.6F, 50, 0, 0, 0.75F, 0, 0, 0))
		.build();

	/** Death (driven by deathTime, 1 s): drops to a knee, plants the blade, falls forward; the hat slides off. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("waist", pos(0, 0, 0, 0, 0.35F, 0, -4, 0, 1, 0, -9, 0))
		.addAnimation("waist", rot(0, 0, 0, 0, 0.35F, 15, 0, 0, 1, 80, 0, 0))
		.addAnimation("right_leg", pos(0, 0, 0, 0, 0.35F, 0, -4, 0, 1, 0, -4, 0))
		.addAnimation("left_leg", pos(0, 0, 0, 0, 0.35F, 0, -4, 0, 1, 0, -4, 0))
		.addAnimation("right_leg", rot(0, 0, 0, 0, 0.35F, -80, 0, 0, 1, -80, 0, 0))
		.addAnimation("left_leg", rot(0, 0, 0, 0, 0.35F, 60, 0, 0, 1, 60, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.35F, 60, 0, 0, 1, 70, 0, 20))
		.addAnimation("katana", rot(0, 0, 0, 0, 0.35F, 80, 0, 0, 1, 80, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.35F, 25, 0, 0, 1, 35, 0, 0))
		.addAnimation("hat", rot(0, 0, 0, 0, 0.6F, 0, 0, 0, 1, -30, 0, 20))
		.addAnimation("hat", pos(0, 0, 0, 0, 0.6F, 0, 0, 0, 1, 0, 2, -3))
		.build();
}
