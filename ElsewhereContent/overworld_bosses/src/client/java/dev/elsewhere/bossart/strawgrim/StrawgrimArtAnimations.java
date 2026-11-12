package dev.elsewhere.bossart.strawgrim;

import static dev.elsewhere.bossart.overworld_a.ArtAnim.pos;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.rot;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.rotL;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.scl;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Strawgrim; timings follow StrawgrimEntity's wind-ups (ticks / 20 = seconds). */
public final class StrawgrimArtAnimations {
	private StrawgrimArtAnimations() {}

	/** Creaking sway on the post, the scythe bobbing, the head lolling. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", rot(0, 0, 0, 0, 1.0F, 0, 4, 3, 2.0F, 0, 0, 0, 3.0F, 0, -4, -3, 4.0F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1.0F, 4, 0, -6, 2.0F, 0, 0, 0, 3.0F, 4, 0, 8, 4.0F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 2.0F, 0, 0, 4, 4.0F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 2.0F, 0, 0, -4, 4.0F, 0, 0, 0))
		.addAnimation("scythe", rot(0, 0, 0, 0, 2.0F, 6, 0, 0, 4.0F, 0, 0, 0))
		.build();

	/** Ground movement: small hops on the single post. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(0.8F).looping()
		.addAnimation("root", pos(0, 0, 0, 0, 0.2F, 0, 3, 0, 0.4F, 0, 0, 0, 0.6F, 0, 3, 0, 0.8F, 0, 0, 0))
		.addAnimation("post", scl(0, 1, 0.9F, 1, 0.2F, 1, 1.05F, 1, 0.4F, 1, 0.9F, 1, 0.6F, 1, 1.05F, 1, 0.8F, 1, 0.9F, 1))
		.addAnimation("body", rot(0, 6, 0, 0, 0.4F, 8, 0, 0, 0.8F, 6, 0, 0))
		.build();

	/** Reap: 1.0 s coil with the scythe drawn back, then a 270-degree sweep around the post. */
	public static final AnimationDefinition REAP = AnimationDefinition.Builder.withLength(2.1F)
		.addAnimation("body", rotL(0, 0, 0, 0, 0.9F, -6, 70, 0, 1.0F, -6, 75, 0, 1.3F, 6, -200, 0, 1.45F, 6, -255, 0, 1.8F, 0, -340, 0, 2.1F, 0, -360, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.9F, 0, 25, 45, 1.0F, 0, 25, 45, 1.15F, 0, -10, 5, 1.6F, 0, -10, 5, 2.1F, 0, 0, 0))
		.addAnimation("scythe", rot(0, 0, 0, 0, 0.9F, -30, 0, 0, 1.0F, -30, 0, 0, 1.15F, 75, 0, 0, 1.6F, 70, 0, 0, 2.1F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.9F, 10, -30, 0, 1.2F, 0, 0, 0, 2.1F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.9F, 0, 0, -30, 1.3F, 0, 0, 10, 2.1F, 0, 0, 0))
		.build();

	/** Furrow: 0.8 s scythe raised high, then slammed into the soil to rip the fang lines. */
	public static final AnimationDefinition FURROW = AnimationDefinition.Builder.withLength(1.8F)
		.addAnimation("body", rot(0, 0, 0, 0, 0.75F, -14, 0, 0, 0.85F, 28, 0, 0, 1.3F, 26, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.75F, 0, 30, 80, 0.85F, 0, -20, -5, 1.3F, 0, -20, -5, 1.8F, 0, 0, 0))
		.addAnimation("scythe", rot(0, 0, 0, 0, 0.75F, -50, 0, 0, 0.85F, 110, 0, 0, 1.3F, 105, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.75F, -20, 0, 0, 0.85F, 20, 0, 0, 1.8F, 0, 0, 0))
		.build();

	/** Murder of Crows: arms flung wide and up, head thrown back, summoning the flock. */
	public static final AnimationDefinition CROWS = AnimationDefinition.Builder.withLength(1.9F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.6F, 0, 0, 35, 1.0F, 0, 0, 40, 1.5F, 0, 0, 38, 1.9F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.6F, 0, 0, -35, 1.0F, 0, 0, -40, 1.5F, 0, 0, -38, 1.9F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.6F, -30, 0, 0, 1.0F, -35, 0, 0, 1.5F, -32, 0, 0, 1.9F, 0, 0, 0))
		.addAnimation("body", rotL(0, 0, 0, 0, 0.7F, -8, 0, 0, 0.8F, -8, 0, 4, 0.9F, -8, 0, -4, 1.0F, -8, 0, 4, 1.1F, -8, 0, 0, 1.9F, 0, 0, 0))
		.build();

	/** Seed volley: a quick double-armed fling forward. */
	public static final AnimationDefinition VOLLEY = AnimationDefinition.Builder.withLength(0.9F)
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.3F, 0, 25, 20, 0.45F, 0, -55, 0, 0.9F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.3F, 0, -25, -20, 0.45F, 0, 55, 0, 0.9F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.3F, -10, 0, 0, 0.45F, 15, 0, 0, 0.9F, 0, 0, 0))
		.build();

	/** The big hop between furrows (0.6 s arc): tuck, flail, land. */
	public static final AnimationDefinition HOP = AnimationDefinition.Builder.withLength(0.8F)
		.addAnimation("post", rot(0, 0, 0, 0, 0.15F, -25, 0, 0, 0.45F, 15, 0, 0, 0.6F, 0, 0, 0))
		.addAnimation("post", scl(0, 1, 0.8F, 1, 0.15F, 1, 1.1F, 1, 0.6F, 1, 0.85F, 1, 0.8F, 1, 1, 1))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.2F, 0, 0, 30, 0.45F, 0, 0, -10, 0.8F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.2F, 0, 0, -30, 0.45F, 0, 0, 10, 0.8F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.2F, -15, 0, 0, 0.6F, 10, 0, 0, 0.8F, 0, 0, 0))
		.build();

	/** Phase 2 ignition / phase transitions: a shuddering, arms-up howl as the effigy catches fire. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("body", rotL(0, 0, 0, 0, 0.2F, -12, 0, 6, 0.3F, -12, 0, -6, 0.4F, -12, 0, 6, 0.5F, -12, 0, -6, 0.6F, -12, 0, 6, 0.7F, -12, 0, -6,
			0.8F, -12, 0, 6, 0.9F, -12, 0, -6, 1.0F, -12, 0, 6, 1.1F, -12, 0, -6, 1.2F, -12, 0, 0, 1.6F, -10, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.3F, -40, 0, 0, 1.4F, -38, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.3F, 0, 0, 55, 1.4F, 0, 0, 50, 2.0F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.3F, 0, 0, -55, 1.4F, 0, 0, -50, 2.0F, 0, 0, 0))
		.addAnimation("scythe", rot(0, 0, 0, 0, 0.3F, -20, 0, 0, 1.4F, -20, 0, 0, 2.0F, 0, 0, 0))
		.build();

	/** Death (3 s): the arms droop, the effigy slumps and the post topples backward. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("root", rot(0, 0, 0, 0, 0.6F, -6, 0, 0, 1.6F, 10, 0, 0, 2.3F, 82, 0, 0, 2.45F, 76, 0, 0, 2.6F, 85, 0, 0, 3.0F, 84, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.6F, 0, 0, -40, 3.0F, 0, 0, -45))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.6F, 0, 0, 40, 3.0F, 0, 0, 45))
		.addAnimation("head", rot(0, 0, 0, 0, 0.6F, 30, 0, 20, 3.0F, 35, 0, 25))
		.addAnimation("body", rot(0, 0, 0, 0, 0.6F, 15, 0, 0, 3.0F, 18, 0, 0))
		.addAnimation("scythe", rot(0, 0, 0, 0, 0.6F, 0, 0, 0, 1.2F, 0, 0, -60, 3.0F, 0, 0, -70))
		.build();
}
