package dev.elsewhere.bossart.mournmaw;

import static dev.elsewhere.bossart.nether.ArtAnim.def;
import static dev.elsewhere.bossart.nether.ArtAnim.pos;
import static dev.elsewhere.bossart.nether.ArtAnim.rot;
import static dev.elsewhere.bossart.nether.ArtAnim.scale;

import dev.elsewhere.boss.mournmaw.Mournmaw;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Mournmaw from the concept art: weeping mask, bone crown, nine tentacles, soul chains with lanterns. */
public class MournmawArtModel extends EntityModel<BossRenderState> {
	static final AnimationDefinition IDLE = def(4.0F, true)
		.addAnimation("body", pos(0, 0, 0, 0, 2.0F, 0, 1.5F, 0, 4.0F, 0, 0, 0))
		.addAnimation("chain_r", rot(0, 0, 0, 6, 2.0F, 0, 0, -6, 4.0F, 0, 0, 6))
		.addAnimation("chain_l", rot(0, 0, 0, -6, 2.0F, 0, 0, 6, 4.0F, 0, 0, -6)).build();
	/** Wail: 1.5 s inhale (body swells, face tips up), then the scream. */
	static final AnimationDefinition WAIL = def(2.0F, false)
		.addAnimation("body", scale(0, 1, 1, 1, 1.5F, 1.12, 1.12, 1.12, 1.6F, 0.95, 0.95, 0.95, 2.0F, 1, 1, 1))
		.addAnimation("body", rot(0, 0, 0, 0, 1.5F, -14, 0, 0, 1.7F, 8, 0, 0, 2.0F, 0, 0, 0)).build();
	/** Chain Whip: chains glow 1.2 s while the tentacles coil back, then they lash out. */
	static final AnimationDefinition WHIP = def(1.6F, false)
		.addAnimation("chain_r", rot(0, 0, 0, 0, 1.2F, 0, 0, 40, 1.35F, 0, 0, -70, 1.6F, 0, 0, 0))
		.addAnimation("chain_l", rot(0, 0, 0, 0, 1.2F, 0, 0, -40, 1.35F, 0, 0, 70, 1.6F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 1.2F, 0, -25, 0, 1.35F, 0, 40, 0, 1.6F, 0, 0, 0)).build();
	static final AnimationDefinition ROAR = def(2.0F, false)
		.addAnimation("body", rot(0, 0, 0, 0, 0.5F, -20, 0, 8, 1.0F, -20, 0, -8, 1.5F, -20, 0, 8, 2.0F, 0, 0, 0)).build();

	/** 3.0 (P8) Tear Rain: 1.5 s (30-tick wind-up) of weeping, mask tipped up, chains lifted wide and swaying; then a sob that shakes the rain loose. */
	static final AnimationDefinition TEAR_RAIN = def(2.1F, false)
		.addAnimation("body", rot(0, 0, 0, 0, 0.5F, -16, 0, 6, 1.0F, -18, 0, -6, 1.45F, -20, 0, 4, 1.6F, 14, 0, 0, 1.8F, -4, 0, 0, 2.1F, 0, 0, 0))
		.addAnimation("body", pos(0, 0, 0, 0, 1.45F, 0, 2.0F, 0, 1.6F, 0, -1.5F, 0, 2.1F, 0, 0, 0))
		.addAnimation("chain_r", rot(0, 0, 0, 0, 0.6F, 0, 0, 38, 1.45F, 0, 0, 44, 1.6F, 0, 0, 8, 2.1F, 0, 0, 0))
		.addAnimation("chain_l", rot(0, 0, 0, 0, 0.6F, 0, 0, -38, 1.45F, 0, 0, -44, 1.6F, 0, 0, -8, 2.1F, 0, 0, 0)).build();
	/** 3.0 (P8) Soul Fireballs: 1 s (20 ticks) the lantern chains swing back over the crown, then fling the souls forward. */
	static final AnimationDefinition SOUL_FIREBALL = def(1.5F, false)
		.addAnimation("chain_r", rot(0, 0, 0, 0, 0.95F, 125, 0, 15, 1.08F, -55, 0, 0, 1.5F, 0, 0, 0))
		.addAnimation("chain_l", rot(0, 0, 0, 0, 0.95F, 125, 0, -15, 1.08F, -55, 0, 0, 1.5F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.95F, -12, 0, 0, 1.1F, 14, 0, 0, 1.5F, 0, 0, 0)).build();
	/** 3.0 (P8) Mourners Rise: 1.5 s rising and bowing over the ground, chains hanging straight down to call the dead up; a jolt, then a held bow. */
	static final AnimationDefinition MOURNERS = def(2.6F, false)
		.addAnimation("body", rot(0, 0, 0, 0, 1.4F, 32, 0, 0, 1.55F, 40, 0, 0, 2.2F, 34, 0, 0, 2.6F, 0, 0, 0))
		.addAnimation("body", pos(0, 0, 0, 0, 1.4F, 0, 3.5F, 0, 1.55F, 0, 1.0F, 0, 2.2F, 0, 1.5F, 0, 2.6F, 0, 0, 0))
		.addAnimation("chain_r", rot(0, 0, 0, 0, 1.4F, -32, 0, -6, 2.2F, -36, 0, -6, 2.6F, 0, 0, 0))
		.addAnimation("chain_l", rot(0, 0, 0, 0, 1.4F, -32, 0, 6, 2.2F, -36, 0, 6, 2.6F, 0, 0, 0)).build();
	/** 3.0 (P8) Grief Storm: 1 s winding up a half turn, then a full spin with the chains flung out like a flail. */
	static final AnimationDefinition GRIEF_STORM = def(2.4F, false)
		.addAnimation("body", rot(0, 0, 0, 0, 0.95F, -8, -60, 0, 1.4F, 0, 120, 0, 1.9F, 0, 300, 0, 2.4F, 0, 360, 0))
		.addAnimation("chain_r", rot(0, 0, 0, 0, 0.95F, 0, 0, 20, 1.15F, 0, 0, 85, 2.0F, 0, 0, 80, 2.4F, 0, 0, 0))
		.addAnimation("chain_l", rot(0, 0, 0, 0, 0.95F, 0, 0, -20, 1.15F, 0, 0, -85, 2.0F, 0, 0, -80, 2.4F, 0, 0, 0)).build();

	private final ModelPart body;
	private final ModelPart[] tentacles = new ModelPart[9];
	private final KeyframeAnimation idle, wail, whip, roar;
	private final KeyframeAnimation tearRain, soulFireball, mourners, griefStorm;

	/** 3.0 (P8): hurt flinch, the body recoils and sways back. */
	static final AnimationDefinition HURT = def(0.55F, false)
		.addAnimation("body", rot(0, 0, 0, 0, 0.1F, -18, 0, 0, 0.3F, 6, 0, 0, 0.55F, 0, 0, 0)).build();
	private final net.minecraft.client.animation.KeyframeAnimation hurt;

	public MournmawArtModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		for (int i = 0; i < 9; i++) tentacles[i] = body.getChild("tentacle" + i);
		this.idle = IDLE.bake(root);
		this.hurt = HURT.bake(root);
		this.wail = WAIL.bake(root);
		this.whip = WHIP.bake(root);
		this.roar = ROAR.bake(root);
		this.tearRain = TEAR_RAIN.bake(root);
		this.soulFireball = SOUL_FIREBALL.bake(root);
		this.mourners = MOURNERS.bake(root);
		this.griefStorm = GRIEF_STORM.bake(root);
	}

	@Override
	public void setupAnim(BossRenderState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		idle.apply((long) (age * 50), 1.0F);
		boolean whipping = s.flag(Mournmaw.FLAG_CHAINS_GLOW);
		for (int i = 0; i < 9; i++) {
			tentacles[i].xRot += 0.18F * Mth.sin(age * 0.09F + i * 0.7F) + (whipping ? 0.5F * Mth.sin(age * 0.9F + i) : 0);
			tentacles[i].zRot += 0.12F * Mth.cos(age * 0.07F + i * 1.3F);
		}
		if (s.dormant) body.xRot += 0.25F; // weeping, face down
		if ("wail".equals(s.attack)) wail.apply(s.attackAnim, age);
		if ("chain_whip".equals(s.attack)) whip.apply(s.attackAnim, age);
		if (dev.elsewhere.bossart.nether.HurtAnim.enabled() && s.attack != null) {
			switch (s.attack) {
				case "tear_rain" -> tearRain.apply(s.attackAnim, age);
				case "soul_fireball" -> soulFireball.apply(s.attackAnim, age);
				case "mourners" -> mourners.apply(s.attackAnim, age);
				case "grief_storm" -> griefStorm.apply(s.attackAnim, age);
				default -> { }
			}
		}
		roar.apply(s.transitionAnim, age);
		dev.elsewhere.bossart.nether.HurtAnim.apply(hurt, s);
		if (s.deathTime > 0) {
			float d = Math.min(1.0F, s.deathTime / 20.0F);
			body.xScale = body.yScale = body.zScale = 1.0F - 0.6F * d; // dissolves into soul mist
		}
	}
}
