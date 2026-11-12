package dev.elsewhere.bossart.grumblegut;

import static dev.elsewhere.bossart.greenhaze.Kf.*;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Grumblegut, timed to the server (paw 24 t before the charge, munch bite at 10 t, slam at 25 t, yawn chomp at 30 t, puff fire at 30 t). */
public final class GrumblegutArtAnimations {
	private GrumblegutArtAnimations() {}

	/** Breathing pot belly, lolling jaw and tongue, swaying fern mane. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(2.4F).looping()
		.addAnimation("belly", scale(s(0F, 1F, 1F, 1F), s(1.2F, 1.06F, 1.1F, 1.03F), s(2.4F, 1F, 1F, 1F)))
		.addAnimation("body", scale(s(0F, 1F, 1F, 1F), s(1.2F, 1.02F, 1.03F, 1F), s(2.4F, 1F, 1F, 1F)))
		.addAnimation("head", rot(r(0F, 0F, 0F, 0F), r(1.2F, -3F, 0F, 0F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(0.6F, 4F, 0F, 0F), r(1.2F, 0F, 0F, 0F), r(1.8F, 5F, 0F, 0F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("tongue", rot(r(0F, 0F, 0F, 0F), r(1.2F, 8F, 0F, 0F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("mane_0", rot(r(0F, 0F, 0F, 0F), r(1.2F, -6F, 0F, -4F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("mane_1", rot(r(0F, 0F, 0F, 0F), r(1.2F, 6F, 0F, -3F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("mane_2", rot(r(0F, 0F, 0F, 0F), r(1.2F, -5F, 0F, 0F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("mane_3", rot(r(0F, 0F, 0F, 0F), r(1.2F, 5F, 0F, 0F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("mane_4", rot(r(0F, 0F, 0F, 0F), r(1.2F, -6F, 0F, 3F), r(2.4F, 0F, 0F, 0F)))
		.addAnimation("mane_5", rot(r(0F, 0F, 0F, 0F), r(1.2F, 6F, 0F, 4F), r(2.4F, 0F, 0F, 0F)))
		.build();

	/** Lumbering four-legged gait with a belly wobble. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.2F).looping()
		.addAnimation("right_front_leg", rot(r(0F, 28F, 0F, 0F), r(0.6F, -28F, 0F, 0F), r(1.2F, 28F, 0F, 0F)))
		.addAnimation("left_front_leg", rot(r(0F, -28F, 0F, 0F), r(0.6F, 28F, 0F, 0F), r(1.2F, -28F, 0F, 0F)))
		.addAnimation("right_hind_leg", rot(r(0F, -28F, 0F, 0F), r(0.6F, 28F, 0F, 0F), r(1.2F, -28F, 0F, 0F)))
		.addAnimation("left_hind_leg", rot(r(0F, 28F, 0F, 0F), r(0.6F, -28F, 0F, 0F), r(1.2F, 28F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 0F, 0F, 3F), r(0.6F, 0F, 0F, -3F), r(1.2F, 0F, 0F, 3F)))
		.addAnimation("body", pos(p(0F, 0F, 0F, 0F), p(0.3F, 0F, 1F, 0F), p(0.6F, 0F, 0F, 0F), p(0.9F, 0F, 1F, 0F), p(1.2F, 0F, 0F, 0F)))
		.addAnimation("belly", rot(r(0F, 0F, 0F, -4F), r(0.6F, 0F, 0F, 4F), r(1.2F, 0F, 0F, -4F)))
		.addAnimation("head", rot(r(0F, 4F, 0F, 0F), r(0.3F, -2F, 0F, 0F), r(0.6F, 4F, 0F, 0F), r(0.9F, -2F, 0F, 0F), r(1.2F, 4F, 0F, 0F)))
		.build();

	/** Gulp Charge wind-up (looped through the 1.2 s paw): head down, the front-right hoof scrapes the ground. */
	public static final AnimationDefinition PAW = AnimationDefinition.Builder.withLength(0.6F).looping()
		.addAnimation("right_front_leg", rot(r(0F, 0F, 0F, 0F), r(0.15F, -55F, 0F, 0F), r(0.4F, 30F, 0F, 0F), r(0.6F, 0F, 0F, 0F)))
		.addAnimation("right_front_leg", pos(p(0F, 0F, 0F, 0F), p(0.15F, 0F, 3F, 0F), p(0.4F, 0F, 0F, 0F), p(0.6F, 0F, 0F, 0F)))
		.addAnimation("head", rot(r(0F, 22F, 0F, 0F), r(0.3F, 26F, 0F, 0F), r(0.6F, 22F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 8F, 0F, 0F), r(0.6F, 8F, 0F, 0F)))
		.addAnimation("body", pos(p(0F, 0F, -2F, 0F), p(0.6F, 0F, -2F, 0F)))
		.addAnimation("jaw", rot(r(0F, -12F, 0F, 0F), r(0.6F, -12F, 0F, 0F)))
		.build();

	/** The charge itself (looped): a fast gallop, head low and tusks forward. */
	public static final AnimationDefinition CHARGE = AnimationDefinition.Builder.withLength(0.5F).looping()
		.addAnimation("right_front_leg", rot(r(0F, 45F, 0F, 0F), r(0.25F, -45F, 0F, 0F), r(0.5F, 45F, 0F, 0F)))
		.addAnimation("left_front_leg", rot(r(0F, 35F, 0F, 0F), r(0.25F, -50F, 0F, 0F), r(0.5F, 35F, 0F, 0F)))
		.addAnimation("right_hind_leg", rot(r(0F, -45F, 0F, 0F), r(0.25F, 45F, 0F, 0F), r(0.5F, -45F, 0F, 0F)))
		.addAnimation("left_hind_leg", rot(r(0F, -35F, 0F, 0F), r(0.25F, 50F, 0F, 0F), r(0.5F, -35F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 6F, 0F, 0F), r(0.125F, 2F, 0F, 0F), r(0.25F, 6F, 0F, 0F), r(0.375F, 2F, 0F, 0F), r(0.5F, 6F, 0F, 0F)))
		.addAnimation("body", pos(p(0F, 0F, 0F, 0F), p(0.125F, 0F, 3F, 0F), p(0.25F, 0F, 0F, 0F), p(0.375F, 0F, 3F, 0F), p(0.5F, 0F, 0F, 0F)))
		.addAnimation("head", rot(r(0F, 25F, 0F, 0F), r(0.5F, 25F, 0F, 0F)))
		.addAnimation("jaw", rot(r(0F, 15F, 0F, 0F), r(0.5F, 15F, 0F, 0F)))
		.addAnimation("mane_0", rot(r(0F, -30F, 0F, 0F), r(0.5F, -30F, 0F, 0F)))
		.addAnimation("mane_1", rot(r(0F, -30F, 0F, 0F), r(0.5F, -30F, 0F, 0F)))
		.addAnimation("mane_2", rot(r(0F, -30F, 0F, 0F), r(0.5F, -30F, 0F, 0F)))
		.addAnimation("mane_3", rot(r(0F, -30F, 0F, 0F), r(0.5F, -30F, 0F, 0F)))
		.addAnimation("mane_4", rot(r(0F, -30F, 0F, 0F), r(0.5F, -30F, 0F, 0F)))
		.addAnimation("mane_5", rot(r(0F, -30F, 0F, 0F), r(0.5F, -30F, 0F, 0F)))
		.build();

	/** Munch: the jaw gapes, the head lunges, the bite lands at 0.5 s. */
	public static final AnimationDefinition MUNCH = AnimationDefinition.Builder.withLength(0.9F)
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(0.35F, 40F, 0F, 0F), rl(0.5F, -18F, 0F, 0F), r(0.65F, -10F, 0F, 0F), r(0.9F, 0F, 0F, 0F)))
		.addAnimation("head", rot(r(0F, 0F, 0F, 0F), r(0.35F, -18F, 0F, 0F), rl(0.5F, 14F, 0F, 0F), r(0.9F, 0F, 0F, 0F)))
		.addAnimation("head", pos(p(0F, 0F, 0F, 0F), p(0.35F, 0F, 1F, 2F), p(0.5F, 0F, -1F, -5F), p(0.9F, 0F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(0.35F, -4F, 0F, 0F), r(0.5F, 6F, 0F, 0F), r(0.9F, 0F, 0F, 0F)))
		.build();

	/** Snack Sniff (looped): head up, snout twitching toward the food. */
	public static final AnimationDefinition SNIFF = AnimationDefinition.Builder.withLength(0.5F).looping()
		.addAnimation("head", rot(r(0F, -18F, 0F, 0F), r(0.1F, -22F, 6F, 0F), r(0.2F, -18F, 0F, 0F), r(0.3F, -22F, -6F, 0F), r(0.5F, -18F, 0F, 0F)))
		.addAnimation("head", pos(p(0F, 0F, 0F, 0F), p(0.1F, 0F, 0F, -0.8F), p(0.2F, 0F, 0F, 0F), p(0.3F, 0F, 0F, -0.8F), p(0.5F, 0F, 0F, 0F)))
		.addAnimation("jaw", rot(r(0F, -15F, 0F, 0F), r(0.5F, -15F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, -5F, 0F, 0F), r(0.5F, -5F, 0F, 0F)))
		.build();

	/** Chewing (looped): fast jaw, bobbing head, belly bouncing. */
	public static final AnimationDefinition CHEW = AnimationDefinition.Builder.withLength(0.4F).looping()
		.addAnimation("jaw", rot(r(0F, -18F, 0F, 0F), r(0.2F, 14F, 3F, 0F), r(0.4F, -18F, 0F, 0F)))
		.addAnimation("head", rot(r(0F, 6F, 0F, 0F), r(0.2F, 0F, 0F, 3F), r(0.4F, 6F, 0F, 0F)))
		.addAnimation("belly", scale(s(0F, 1.08F, 1.12F, 1.04F), s(0.2F, 1.12F, 1.06F, 1.04F), s(0.4F, 1.08F, 1.12F, 1.04F)))
		.build();

	/** Couch Slam: rears up for 1.25 s, crashes down onto its belly, settles. */
	public static final AnimationDefinition SLAM = AnimationDefinition.Builder.withLength(1.7F)
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(0.9F, -38F, 0F, 0F), r(1.15F, -42F, 0F, 0F), rl(1.3F, 4F, 0F, 0F), r(1.7F, 0F, 0F, 0F)))
		.addAnimation("body", pos(p(0F, 0F, 0F, 0F), p(1.15F, 0F, 6F, 0F), p(1.3F, 0F, -6F, 0F), p(1.7F, 0F, 0F, 0F)))
		.addAnimation("right_front_leg", rot(r(0F, 0F, 0F, 0F), r(1.15F, -70F, 0F, 10F), r(1.3F, -80F, 0F, 30F), r(1.7F, 0F, 0F, 0F)))
		.addAnimation("left_front_leg", rot(r(0F, 0F, 0F, 0F), r(1.15F, -70F, 0F, -10F), r(1.3F, -80F, 0F, -30F), r(1.7F, 0F, 0F, 0F)))
		.addAnimation("right_hind_leg", rot(r(0F, 0F, 0F, 0F), r(1.3F, 60F, 0F, 20F), r(1.7F, 0F, 0F, 0F)))
		.addAnimation("left_hind_leg", rot(r(0F, 0F, 0F, 0F), r(1.3F, 60F, 0F, -20F), r(1.7F, 0F, 0F, 0F)))
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(1.0F, 30F, 0F, 0F), r(1.3F, -10F, 0F, 0F), r(1.7F, 0F, 0F, 0F)))
		.addAnimation("belly", scale(s(0F, 1F, 1F, 1F), s(1.3F, 1.25F, 0.8F, 1.15F), s(1.5F, 0.95F, 1.08F, 0.95F), s(1.7F, 1F, 1F, 1F)))
		.build();

	/** Yawn: 1.5 s vacuum with the jaw hanging enormously wide and the head tipped up, then the 38-raw chomp. */
	public static final AnimationDefinition YAWN = AnimationDefinition.Builder.withLength(1.9F)
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(0.4F, 45F, 0F, 0F), r(1.45F, 52F, 0F, 0F), rl(1.55F, -18F, 0F, 0F), r(1.9F, 0F, 0F, 0F)))
		.addAnimation("head", rot(r(0F, 0F, 0F, 0F), r(0.4F, -24F, 0F, 0F), r(1.45F, -28F, 0F, 0F), rl(1.55F, 14F, 0F, 0F), r(1.9F, 0F, 0F, 0F)))
		.addAnimation("tongue", rot(r(0F, 0F, 0F, 0F), r(0.4F, 18F, 0F, 0F), r(1.45F, 22F, 0F, 0F), r(1.55F, -8F, 0F, 0F), r(1.9F, 0F, 0F, 0F)))
		.addAnimation("belly", scale(s(0F, 1F, 1F, 1F), s(1.45F, 1.15F, 1.18F, 1.1F), s(1.6F, 1F, 1F, 1F)))
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(1.45F, -8F, 0F, 0F), r(1.6F, 6F, 0F, 0F), r(1.9F, 0F, 0F, 0F)))
		.build();

	/** Food-coma nap (looped): slumped on its belly, legs splayed, head down, slow snoring breaths. */
	public static final AnimationDefinition NAP = AnimationDefinition.Builder.withLength(3.0F).looping()
		.addAnimation("whole", pos(p(0F, 0F, -10F, 0F), p(3.0F, 0F, -10F, 0F)))
		.addAnimation("right_front_leg", rot(r(0F, -80F, 0F, 10F), r(3.0F, -80F, 0F, 10F)))
		.addAnimation("left_front_leg", rot(r(0F, -80F, 0F, -10F), r(3.0F, -80F, 0F, -10F)))
		.addAnimation("right_hind_leg", rot(r(0F, 80F, 0F, 10F), r(3.0F, 80F, 0F, 10F)))
		.addAnimation("left_hind_leg", rot(r(0F, 80F, 0F, -10F), r(3.0F, 80F, 0F, -10F)))
		.addAnimation("head", rot(r(0F, 24F, 0F, 8F), r(1.5F, 20F, 0F, 8F), r(3.0F, 24F, 0F, 8F)))
		.addAnimation("jaw", rot(r(0F, -12F, 0F, 0F), r(1.5F, 6F, 0F, 0F), r(3.0F, -12F, 0F, 0F)))
		.addAnimation("belly", scale(s(0F, 1.1F, 1F, 1.05F), s(1.5F, 1.2F, 1.15F, 1.1F), s(3.0F, 1.1F, 1F, 1.05F)))
		.build();

	/** Puff-Puff-Pass: 1.5 s inhale (puffs up, head tipped back), then the smoke ring is spat out. */
	public static final AnimationDefinition PUFF = AnimationDefinition.Builder.withLength(1.9F)
		.addAnimation("body", scale(s(0F, 1F, 1F, 1F), s(1.45F, 1.1F, 1.12F, 1.04F), s(1.55F, 0.96F, 0.96F, 1F), s(1.9F, 1F, 1F, 1F)))
		.addAnimation("belly", scale(s(0F, 1F, 1F, 1F), s(1.45F, 1.2F, 1.25F, 1.1F), s(1.55F, 0.95F, 0.95F, 1F), s(1.9F, 1F, 1F, 1F)))
		.addAnimation("head", rot(r(0F, 0F, 0F, 0F), r(1.45F, -26F, 0F, 0F), rl(1.55F, 12F, 0F, 0F), r(1.9F, 0F, 0F, 0F)))
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(1.4F, -16F, 0F, 0F), rl(1.55F, 36F, 0F, 0F), r(1.9F, 0F, 0F, 0F)))
		.build();

	/** Phase transition (2 s): rears its head with a gaping roar and shakes the mane. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("head", rot(r(0F, 0F, 0F, 0F), r(0.4F, -32F, 0F, 0F), r(0.8F, -34F, 10F, 0F), r(1.1F, -34F, -10F, 0F), r(1.4F, -32F, 8F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(0.4F, 48F, 0F, 0F), r(1.5F, 50F, 0F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("body", rot(r(0F, 0F, 0F, 0F), r(0.4F, -14F, 0F, 0F), r(1.5F, -14F, 0F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("right_front_leg", rot(r(0F, 0F, 0F, 0F), r(0.4F, -30F, 0F, 0F), r(1.5F, -30F, 0F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("left_front_leg", rot(r(0F, 0F, 0F, 0F), r(0.4F, -30F, 0F, 0F), r(1.5F, -30F, 0F, 0F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("mane_0", rot(r(0F, 0F, 0F, 0F), r(0.6F, 0F, 0F, -25F), r(0.9F, 0F, 0F, 20F), r(1.2F, 0F, 0F, -25F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("mane_2", rot(r(0F, 0F, 0F, 0F), r(0.6F, 0F, 0F, 25F), r(0.9F, 0F, 0F, -20F), r(1.2F, 0F, 0F, 25F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("mane_3", rot(r(0F, 0F, 0F, 0F), r(0.6F, 0F, 0F, -25F), r(0.9F, 0F, 0F, 20F), r(1.2F, 0F, 0F, -25F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("mane_5", rot(r(0F, 0F, 0F, 0F), r(0.6F, 0F, 0F, 25F), r(0.9F, 0F, 0F, -20F), r(1.2F, 0F, 0F, 25F), r(2.0F, 0F, 0F, 0F)))
		.addAnimation("belly", scale(s(0F, 1F, 1F, 1F), s(0.4F, 1.1F, 1.12F, 1.05F), s(1.5F, 1.1F, 1.12F, 1.05F), s(2.0F, 1F, 1F, 1F)))
		.build();

	/** Death (1 s, with the vanilla roll-over): legs kick up, the jaw drops open, the belly deflates. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("belly", scale(s(0F, 1.15F, 1.15F, 1.1F), s(0.3F, 1.2F, 1.2F, 1.1F), s(1.0F, 0.85F, 0.55F, 0.95F)))
		.addAnimation("right_front_leg", rot(r(0F, 0F, 0F, 0F), r(1.0F, -60F, 0F, 20F)))
		.addAnimation("left_front_leg", rot(r(0F, 0F, 0F, 0F), r(1.0F, -50F, 0F, -15F)))
		.addAnimation("right_hind_leg", rot(r(0F, 0F, 0F, 0F), r(1.0F, 55F, 0F, 20F)))
		.addAnimation("left_hind_leg", rot(r(0F, 0F, 0F, 0F), r(1.0F, 60F, 0F, -15F)))
		.addAnimation("jaw", rot(r(0F, 0F, 0F, 0F), r(1.0F, 30F, 0F, 0F)))
		.addAnimation("tongue", rot(r(0F, 0F, 0F, 0F), r(1.0F, 35F, 0F, 0F)))
		.addAnimation("head", rot(r(0F, 0F, 0F, 0F), r(1.0F, 10F, 0F, 0F)))
		.build();
}
