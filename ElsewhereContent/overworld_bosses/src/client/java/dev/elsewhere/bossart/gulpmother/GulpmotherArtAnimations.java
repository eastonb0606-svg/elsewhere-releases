package dev.elsewhere.bossart.gulpmother;

import static dev.elsewhere.bossart.overworld_a.ArtAnim.pos;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.rot;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.rotL;
import static dev.elsewhere.bossart.overworld_a.ArtAnim.scl;

import net.minecraft.client.animation.AnimationDefinition;

/** Keyframe animations for Old Gulpmother; timings follow GulpmotherEntity's wind-ups (ticks / 20 = seconds). */
public final class GulpmotherArtAnimations {
	private GulpmotherArtAnimations() {}

	/** Breathing, throat pulse and a lazy head bob. */
	public static final AnimationDefinition IDLE = AnimationDefinition.Builder.withLength(4.0F).looping()
		.addAnimation("body", scl(0, 1, 1, 1, 1.0F, 1.02F, 1.04F, 1.02F, 2.0F, 1, 1, 1, 3.0F, 1.02F, 1.04F, 1.02F, 4.0F, 1, 1, 1))
		.addAnimation("jaw", scl(0, 1, 1, 1, 0.6F, 1.05F, 1.25F, 1.2F, 1.2F, 1, 1, 1, 2.6F, 1, 1, 1, 3.1F, 1.05F, 1.3F, 1.25F, 3.6F, 1, 1, 1, 4.0F, 1, 1, 1))
		.addAnimation("head", rot(0, 0, 0, 0, 2.0F, -3, 0, 0, 4.0F, 0, 0, 0))
		.build();

	/** Heavy toad hops. */
	public static final AnimationDefinition WALK = AnimationDefinition.Builder.withLength(1.2F).looping()
		.addAnimation("root", pos(0, 0, 0, 0, 0.3F, 0, 3, -1, 0.6F, 0, 0, 0, 0.9F, 0, 0.5F, 0, 1.2F, 0, 0, 0))
		.addAnimation("right_haunch", rot(0, 0, 0, 0, 0.3F, -25, 0, 0, 0.6F, 10, 0, 0, 1.2F, 0, 0, 0))
		.addAnimation("left_haunch", rot(0, 0, 0, 0, 0.3F, -25, 0, 0, 0.6F, 10, 0, 0, 1.2F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.3F, -20, 0, 0, 0.6F, 15, 0, 0, 1.2F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.3F, -20, 0, 0, 0.6F, 15, 0, 0, 1.2F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.3F, -4, 0, 0, 0.6F, 3, 0, 0, 1.2F, 0, 0, 0))
		.build();

	/** Tongue Lash: 1.6 s gape wind-up (the pink line), then the strike; the tongue length itself follows the synced value. */
	public static final AnimationDefinition TONGUE = AnimationDefinition.Builder.withLength(2.6F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.6F, -30, 0, 0, 1.2F, -28, 0, 0, 1.55F, -34, 0, 0, 1.65F, -42, 0, 0, 2.1F, -40, 0, 0, 2.6F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.6F, -4, 0, 0, 1.5F, -5, 0, 0, 1.7F, 6, 0, 0, 2.2F, 4, 0, 0, 2.6F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.6F, 18, 0, 0, 2.2F, 18, 0, 0, 2.6F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 1.5F, 10, 0, 0, 1.7F, -15, 0, 0, 2.6F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 1.5F, 10, 0, 0, 1.7F, -15, 0, 0, 2.6F, 0, 0, 0))
		.build();

	/** Belly Flop: 0.8 s crouch, a 0.7 s leap (the entity itself arcs), then a squashing landing. */
	public static final AnimationDefinition FLOP = AnimationDefinition.Builder.withLength(2.2F)
		.addAnimation("root", pos(0, 0, 0, 0, 0.8F, 0, -3, 0, 0.9F, 0, 0, 0, 1.5F, 0, 0, 0, 1.6F, 0, -2, 0, 2.2F, 0, 0, 0))
		.addAnimation("body", scl(0, 1, 1, 1, 0.8F, 1.08F, 0.85F, 1.08F, 0.95F, 0.92F, 1.1F, 1.12F, 1.5F, 0.95F, 1.05F, 1.1F, 1.6F, 1.2F, 0.75F, 1.2F, 1.9F, 1.05F, 0.95F, 1.05F, 2.2F, 1, 1, 1))
		.addAnimation("body", rot(0, 0, 0, 0, 0.8F, 8, 0, 0, 1.0F, -14, 0, 0, 1.45F, 10, 0, 0, 1.6F, 0, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("right_haunch", rot(0, 0, 0, 0, 0.8F, -30, 0, 0, 1.0F, 45, 0, 0, 1.5F, 30, 0, 0, 1.6F, -10, 0, 20, 2.2F, 0, 0, 0))
		.addAnimation("left_haunch", rot(0, 0, 0, 0, 0.8F, -30, 0, 0, 1.0F, 45, 0, 0, 1.5F, 30, 0, 0, 1.6F, -10, 0, -20, 2.2F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.8F, 20, 0, 0, 1.0F, -70, 0, 0, 1.5F, -60, 0, 0, 1.6F, 10, 0, 25, 2.2F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.8F, 20, 0, 0, 1.0F, -70, 0, 0, 1.5F, -60, 0, 0, 1.6F, 10, 0, -25, 2.2F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.8F, -12, 0, 0, 1.5F, -6, 0, 0, 1.6F, -25, 0, 0, 2.2F, 0, 0, 0))
		.build();

	/** A swallowed player: mouth shut, cheeks bulging and churning. */
	public static final AnimationDefinition SWALLOW = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("body", scl(0, 1, 1, 1, 0.3F, 1.12F, 1.1F, 1.08F, 0.8F, 1.06F, 1.14F, 1.1F, 1.3F, 1.14F, 1.08F, 1.1F, 1.8F, 1.06F, 1.14F, 1.1F, 2.3F, 1.14F, 1.08F, 1.1F, 3.0F, 1.1F, 1.1F, 1.1F))
		.addAnimation("jaw", scl(0, 1, 1, 1, 0.3F, 1.2F, 1.6F, 1.4F, 3.0F, 1.2F, 1.6F, 1.4F))
		.addAnimation("head", rot(0, 0, 0, 0, 0.5F, 4, 0, 0, 1.0F, 0, 0, 0, 1.5F, 4, 0, 0, 2.0F, 0, 0, 0, 3.0F, 2, 0, 0))
		.build();

	/** Spawning Season: 1 s inflate, then frogspawn spat out in a fan. */
	public static final AnimationDefinition SPIT = AnimationDefinition.Builder.withLength(1.6F)
		.addAnimation("body", scl(0, 1, 1, 1, 0.9F, 1.12F, 1.15F, 1.1F, 1.05F, 0.95F, 0.95F, 1.0F, 1.6F, 1, 1, 1))
		.addAnimation("jaw", scl(0, 1, 1, 1, 0.9F, 1.3F, 1.8F, 1.5F, 1.05F, 1, 1, 1, 1.6F, 1, 1, 1))
		.addAnimation("head", rot(0, 0, 0, 0, 0.9F, -12, 0, 0, 1.0F, -45, 0, 0, 1.3F, -40, 0, 0, 1.6F, 0, 0, 0))
		.build();

	/** Devour: 1 s gape toward a Mire Biter, the tongue reels it in, the jaw snaps shut. */
	public static final AnimationDefinition EAT = AnimationDefinition.Builder.withLength(1.8F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.8F, -35, 0, 0, 1.35F, -35, 0, 0, 1.45F, 2, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.8F, 6, 0, 0, 1.45F, -3, 0, 0, 1.8F, 0, 0, 0))
		.addAnimation("jaw", scl(0, 1, 1, 1, 1.4F, 1, 1, 1, 1.55F, 1.2F, 1.5F, 1.4F, 1.8F, 1, 1, 1))
		.build();

	/** Fight start: she bursts up out of the pool and throws her head back. */
	public static final AnimationDefinition EMERGE = AnimationDefinition.Builder.withLength(2.2F)
		.addAnimation("root", pos(0, 0, -14, 0, 0.8F, 0, -4, 0, 1.1F, 0, 2, 0, 1.4F, 0, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1.1F, -20, 0, 0, 1.5F, -45, 0, 0, 1.9F, -40, 0, 0, 2.2F, 0, 0, 0))
		.addAnimation("jaw", scl(0, 1, 1, 1, 1.5F, 1.3F, 2.0F, 1.6F, 1.9F, 1.3F, 2.0F, 1.6F, 2.2F, 1, 1, 1))
		.build();

	/** Phase change: a bellowing croak with the throat sac blown up. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.3F, -38, 0, 0, 0.5F, -45, 0, 0, 1.5F, -42, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("jaw", scl(0, 1, 1, 1, 0.4F, 1.4F, 2.4F, 1.8F, 0.8F, 1.2F, 1.8F, 1.5F, 1.2F, 1.4F, 2.4F, 1.8F, 1.6F, 1.2F, 1.8F, 1.5F, 2.0F, 1, 1, 1))
		.addAnimation("body", scl(0, 1, 1, 1, 0.4F, 1.08F, 1.1F, 1.06F, 1.6F, 1.08F, 1.1F, 1.06F, 2.0F, 1, 1, 1))
		.addAnimation("body", rotL(0, 0, 0, 0, 0.5F, -6, 0, 0, 0.6F, -6, 0, 3, 0.7F, -6, 0, -3, 0.8F, -6, 0, 3, 0.9F, -6, 0, -3, 1.0F, -6, 0, 0, 1.6F, -6, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 0.5F, -25, 0, 15, 1.6F, -25, 0, 15, 2.0F, 0, 0, 0))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 0.5F, -25, 0, -15, 1.6F, -25, 0, -15, 2.0F, 0, 0, 0))
		.build();

	/** Death (3 s): a last gape, then she deflates and sprawls flat. */
	public static final AnimationDefinition DEATH = AnimationDefinition.Builder.withLength(3.0F)
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -40, 0, 0, 1.2F, -30, 0, 0, 1.8F, 8, 0, 6, 3.0F, 10, 0, 8))
		.addAnimation("body", scl(0, 1, 1, 1, 0.4F, 1.1F, 1.15F, 1.1F, 1.8F, 1.15F, 0.72F, 1.1F, 3.0F, 1.15F, 0.7F, 1.1F))
		.addAnimation("root", pos(0, 0, 0, 0, 1.8F, 0, -2, 0, 3.0F, 0, -3, 0))
		.addAnimation("right_haunch", rot(0, 0, 0, 0, 1.8F, -20, 0, 45, 3.0F, -20, 0, 50))
		.addAnimation("left_haunch", rot(0, 0, 0, 0, 1.8F, -20, 0, -45, 3.0F, -20, 0, -50))
		.addAnimation("right_arm", rot(0, 0, 0, 0, 1.8F, -60, 0, 30, 3.0F, -70, 0, 35))
		.addAnimation("left_arm", rot(0, 0, 0, 0, 1.8F, -60, 0, -30, 3.0F, -70, 0, -35))
		.build();
}
