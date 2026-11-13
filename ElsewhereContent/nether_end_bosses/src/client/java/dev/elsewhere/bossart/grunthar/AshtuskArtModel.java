package dev.elsewhere.bossart.grunthar;

import static dev.elsewhere.bossart.nether.ArtAnim.def;
import static dev.elsewhere.bossart.nether.ArtAnim.pos;
import static dev.elsewhere.bossart.nether.ArtAnim.rot;
import static dev.elsewhere.bossart.nether.ArtAnim.scale;

import dev.elsewhere.boss.grunthar.Ashtusk;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;

/** Ashtusk: armored war-hoglin. Paws for 1.2 s before the charge, flicks the tusks up for the toss. */
public class AshtuskArtModel extends EntityModel<BossRenderState> {
	static final AnimationDefinition IDLE = def(2.4F, true)
		.addAnimation("body", scale(0, 1, 1, 1, 1.2F, 1.0, 1.04, 1.02, 2.4F, 1, 1, 1))
		.addAnimation("head", rot(0, 0, 0, 0, 1.2F, 4, 4, 0, 2.4F, 0, 0, 0)).build();
	static final AnimationDefinition WALK = def(1.0F, true)
		.addAnimation("leg_fr", rot(0, 25, 0, 0, 0.5F, -25, 0, 0, 1.0F, 25, 0, 0))
		.addAnimation("leg_bl", rot(0, 25, 0, 0, 0.5F, -25, 0, 0, 1.0F, 25, 0, 0))
		.addAnimation("leg_fl", rot(0, -25, 0, 0, 0.5F, 25, 0, 0, 1.0F, -25, 0, 0))
		.addAnimation("leg_br", rot(0, -25, 0, 0, 0.5F, 25, 0, 0, 1.0F, -25, 0, 0)).build();
	/** Pawing (the 1.2 s charge telegraph): front leg scrapes, head lowers. */
	static final AnimationDefinition PAW = def(0.6F, true)
		.addAnimation("leg_fr", rot(0, 0, 0, 0, 0.2F, -45, 0, 0, 0.4F, 20, 0, 0, 0.6F, 0, 0, 0))
		.addAnimation("head", rot(0, 14, 0, 0, 0.3F, 20, 0, 6, 0.6F, 14, 0, 0)).build();
	static final AnimationDefinition CHARGE = def(0.4F, true)
		.addAnimation("head", rot(0, 22, 0, 0, 0.2F, 18, 0, 0, 0.4F, 22, 0, 0)).build();
	/** Tusk Toss: 0.5 s wind-up then an upward head flick. */
	static final AnimationDefinition TOSS = def(0.8F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.45F, 27, 0, 0, 0.55F, -53, 0, 0, 0.8F, 0, 0, 0)).build();
	static final AnimationDefinition ROAR = def(2.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -43, 0, 0, 1.6F, -38, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.4F, -8, 0, 0, 1.6F, -8, 0, 0, 2.0F, 0, 0, 0)).build();

	/** 3.0 (P8): hurt flinch (head jerks up, body rocks) and death (legs splay, head sags while vanilla tips it over). */
	static final AnimationDefinition HURT = def(0.5F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.08F, -28, 0, 0, 0.3F, 6, 0, 0, 0.5F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.1F, -6, 0, 5, 0.5F, 0, 0, 0)).build();
	// 3.0 (f-anim): the head sag was 30 degrees, which on the rolled-over boar drove the snout below the grass plane
	// (judge R-ground, scene 51); a 10 degree sag plus a 3 px lift keeps the snout resting on the ground
	static final AnimationDefinition DEATH = def(1.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 1.0F, 10, 0, 0))
		.addAnimation("head", pos(0, 0, 0, 0, 1.0F, 0, -3, 0))
		.addAnimation("leg_fr", rot(0, 0, 0, 0, 1.0F, -55, 0, 0))
		.addAnimation("leg_fl", rot(0, 0, 0, 0, 1.0F, -55, 0, 0))
		.addAnimation("leg_br", rot(0, 0, 0, 0, 1.0F, 55, 0, 0))
		.addAnimation("leg_bl", rot(0, 0, 0, 0, 1.0F, 55, 0, 0)).build();

	private final KeyframeAnimation idle, walk, paw, charge, toss, roar, hurt, death;

	public AshtuskArtModel(ModelPart root) {
		super(root);
		this.idle = IDLE.bake(root);
		this.walk = WALK.bake(root);
		this.paw = PAW.bake(root);
		this.charge = CHARGE.bake(root);
		this.toss = TOSS.bake(root);
		this.roar = ROAR.bake(root);
		this.hurt = HURT.bake(root);
		this.death = DEATH.bake(root);
	}

	@Override
	public void setupAnim(BossRenderState s) {
		super.setupAnim(s);
		idle.apply((long) (s.ageInTicks * 50), 1.0F);
		walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 2.0F, 2.5F);
		if (s.flag(Ashtusk.FLAG_PAWING)) paw.apply((long) (s.ageInTicks * 50), 1.0F);
		if (s.flag(Ashtusk.FLAG_CHARGING)) {
			charge.apply((long) (s.ageInTicks * 50), 1.0F);
			walk.applyWalk(s.ageInTicks * 0.9F, 1.0F, 2.0F, 2.5F);
		}
		if ("tusk_toss".equals(s.attack)) toss.apply(s.attackAnim, s.ageInTicks);
		if (s.flag(Ashtusk.FLAG_STUNNED)) root().getChild("body").getChild("head").zRot = 0.3F * (float) Math.sin(s.ageInTicks * 0.8F);
		roar.apply(s.transitionAnim, s.ageInTicks);
		dev.elsewhere.bossart.nether.HurtAnim.apply(hurt, s);
		if (s.deathTime > 0) death.apply((long) (s.deathTime * 50), 1.0F);
	}
}
