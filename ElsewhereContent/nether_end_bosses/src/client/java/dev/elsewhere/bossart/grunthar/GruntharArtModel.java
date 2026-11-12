package dev.elsewhere.bossart.grunthar;

import static dev.elsewhere.bossart.nether.ArtAnim.def;
import static dev.elsewhere.bossart.nether.ArtAnim.rot;

import dev.elsewhere.boss.grunthar.Grunthar;
import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Grunthar: piglin warlord with a gold crown and a giant golden axe; rides seated, berserks on foot. */
public class GruntharArtModel extends EntityModel<GruntharArtModel.State> {
	public static class State extends dev.elsewhere.bossart.nether.HurtAnim.State {
		public boolean riding;
	}

	static final AnimationDefinition IDLE = def(3.0F, true)
		.addAnimation("head", rot(0, 0, 0, 0, 1.5F, 4, 6, 0, 3.0F, 0, 0, 0))
		.addAnimation("arm_l", rot(0, 0, 0, -4, 1.5F, 6, 0, -8, 3.0F, 0, 0, -4)).build();
	static final AnimationDefinition WALK = def(1.0F, true)
		.addAnimation("leg_r", rot(0, 30, 0, 0, 0.5F, -30, 0, 0, 1.0F, 30, 0, 0))
		.addAnimation("leg_l", rot(0, -30, 0, 0, 0.5F, 30, 0, 0, 1.0F, -30, 0, 0))
		.addAnimation("arm_l", rot(0, -25, 0, 0, 0.5F, 25, 0, 0, 1.0F, -25, 0, 0)).build();
	/** Golden Axe: 0.8 s overhead wind-up (matches the 16-tick telegraph), then the cleave. */
	static final AnimationDefinition AXE = def(1.1F, false)
		.addAnimation("arm_r", rot(0, 0, 0, 0, 0.8F, -165, 10, 0, 0.9F, -20, 0, 0, 1.1F, -10, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.8F, -8, 0, 0, 0.9F, 12, 0, 0, 1.1F, 0, 0, 0)).build();
	/** Gold Bombs: 0.7 s wind-up (14 ticks), then the throw. */
	static final AnimationDefinition THROW = def(0.95F, false)
		.addAnimation("arm_l", rot(0, 0, 0, 0, 0.7F, -160, 0, -20, 0.8F, -40, 0, 0, 0.95F, 0, 0, 0)).build();
	static final AnimationDefinition GREED = def(1.0F, true)
		.addAnimation("arm_l", rot(0, -70, -20, 0, 0.5F, -80, -30, 0, 1.0F, -70, -20, 0))
		.addAnimation("head", rot(0, 20, -20, 0, 0.5F, 25, -10, 0, 1.0F, 20, -20, 0)).build();
	static final AnimationDefinition ROAR = def(2.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -35, 0, 0, 1.6F, -30, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("arm_r", rot(0, 0, 0, 0, 0.4F, -40, 0, 50, 1.6F, -40, 0, 50, 2.0F, 0, 0, 0))
		.addAnimation("arm_l", rot(0, 0, 0, 0, 0.4F, -40, 0, -50, 1.6F, -40, 0, -50, 2.0F, 0, 0, 0)).build();
	static final AnimationDefinition DEATH = def(1.0F, false)
		.addAnimation("arm_r", rot(0, 0, 0, 0, 1.0F, -100, 0, 40, 1.0F, -100, 0, 40))
		.addAnimation("arm_l", rot(0, 0, 0, 0, 1.0F, -100, 0, -40, 1.0F, -100, 0, -40))
		.addAnimation("head", rot(0, 0, 0, 0, 1.0F, 30, 0, 0, 1.0F, 30, 0, 0)).build();

	/** 3.0 (P8) Call the Enforcers: 1.5 s (30-tick wind-up) fist raised high and a bellow, head thrown back; then two fist pumps. */
	static final AnimationDefinition CALL = def(2.4F, false)
		.addAnimation("arm_l", rot(0, 0, 0, 0, 0.6F, -170, 0, -12, 1.45F, -175, 0, -10, 1.65F, -120, 0, -10, 1.85F, -175, 0, -10, 2.05F, -120, 0, -10, 2.4F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 0.6F, -30, 0, 0, 1.45F, -34, 0, 0, 2.05F, -26, 0, 0, 2.4F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.6F, -6, 0, 0, 1.45F, -8, 0, 0, 2.4F, 0, 0, 0))
		.addAnimation("arm_r", rot(0, 0, 0, 0, 0.6F, 20, 0, 25, 2.05F, 20, 0, 25, 2.4F, 0, 0, 0)).build();
	/** 3.0 (P8) Molten Hoard: 1.5 s both fists raised over the crown, then he hammers the ground and the gold pours; crouched follow-through. */
	static final AnimationDefinition HOARD = def(2.3F, false)
		.addAnimation("arm_r", rot(0, 0, 0, 0, 1.35F, -175, 0, 12, 1.5F, -30, 0, 0, 1.9F, -25, 0, 0, 2.3F, 0, 0, 0))
		.addAnimation("arm_l", rot(0, 0, 0, 0, 1.35F, -175, 0, -12, 1.5F, -30, 0, 0, 1.9F, -25, 0, 0, 2.3F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 1.35F, -10, 0, 0, 1.5F, 26, 0, 0, 1.9F, 22, 0, 0, 2.3F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 1.35F, -24, 0, 0, 1.5F, 18, 0, 0, 1.9F, 14, 0, 0, 2.3F, 0, 0, 0)).build();

	private final ModelPart legR, legL, armR;
	private final KeyframeAnimation idle, walk, axe, toss, greed, roar, death;
	private final KeyframeAnimation call, hoard;

	/** 3.0 (P8): hurt flinch, head snaps back, arms fly out, torso rocks. */
	static final AnimationDefinition HURT = def(0.5F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.08F, -22, 0, 10, 0.3F, 4, 0, -3, 0.5F, 0, 0, 0))
		.addAnimation("body", rot(0, 0, 0, 0, 0.1F, -8, 0, 0, 0.5F, 0, 0, 0))
		.addAnimation("arm_r", rot(0, 0, 0, 0, 0.1F, -30, 0, 20, 0.5F, 0, 0, 0))
		.addAnimation("arm_l", rot(0, 0, 0, 0, 0.1F, -20, 0, -20, 0.5F, 0, 0, 0)).build();
	private final net.minecraft.client.animation.KeyframeAnimation hurt;

	public GruntharArtModel(ModelPart root) {
		super(root);
		this.legR = root.getChild("leg_r");
		this.legL = root.getChild("leg_l");
		this.armR = root.getChild("arm_r");
		this.idle = IDLE.bake(root);
		this.walk = WALK.bake(root);
		this.axe = AXE.bake(root);
		this.toss = THROW.bake(root);
		this.greed = GREED.bake(root);
		this.roar = ROAR.bake(root);
		this.death = DEATH.bake(root);
		this.hurt = HURT.bake(root);
		this.call = CALL.bake(root);
		this.hoard = HOARD.bake(root);
	}

	@Override
	public void setupAnim(State s) {
		super.setupAnim(s);
		root().getChild("head").yRot = Mth.clamp(s.yRot, -50, 50) * Mth.DEG_TO_RAD;
		root().getChild("head").xRot = s.xRot * Mth.DEG_TO_RAD;
		idle.apply((long) (s.ageInTicks * 50), 1.0F);
		if (s.riding) {
			legR.xRot = -1.35F;
			legR.yRot = 0.3F;
			legL.xRot = -1.35F;
			legL.yRot = -0.3F;
		} else {
			walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 2.0F, 2.5F);
		}
		armR.xRot += -0.35F; // carries the axe forward
		if ("axe_cleave".equals(s.attack)) axe.apply(s.attackAnim, s.ageInTicks);
		if ("gold_bombs".equals(s.attack)) toss.apply(s.attackAnim, s.ageInTicks);
		if (dev.elsewhere.bossart.nether.HurtAnim.enabled()) {
			if ("call_enforcers".equals(s.attack)) call.apply(s.attackAnim, s.ageInTicks);
			if ("molten_hoard".equals(s.attack)) hoard.apply(s.attackAnim, s.ageInTicks);
		}
		if (s.flag(Grunthar.FLAG_GREED)) greed.apply((long) (s.ageInTicks * 50), 1.0F);
		roar.apply(s.transitionAnim, s.ageInTicks);
		dev.elsewhere.bossart.nether.HurtAnim.apply(hurt, s);
		if (s.deathTime > 0) death.apply((long) (s.deathTime * 50), 1.0F);
	}
}
