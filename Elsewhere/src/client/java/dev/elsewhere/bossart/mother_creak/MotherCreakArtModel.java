package dev.elsewhere.bossart.mother_creak;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * Mother Creak, the Elder Heartwood: a towering dark-bark creaking with a branch crown, pale-oak bark plates, a
 * glowing resin heart and three-branch claws, posed like the concept painting. All motion is keyframed
 * (see {@link MotherCreakAnimations}); only the head look is procedural.
 */
public class MotherCreakArtModel extends EntityModel<MotherCreakArtState> {
	private final ModelPart head;
	private final ModelPart platesR;
	private final ModelPart platesL;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation twitch;
	private final KeyframeAnimation walk;
	private final KeyframeAnimation dormant;
	private final KeyframeAnimation claw;
	private final KeyframeAnimation burst;
	private final KeyframeAnimation stun;
	private final KeyframeAnimation roar;
	private final KeyframeAnimation death;

	public MotherCreakArtModel(final ModelPart root) {
		super(root);
		ModelPart torso = root.getChild("root").getChild("torso");
		this.head = torso.getChild("head");
		this.platesR = torso.getChild("body_r").getChild("plates_r");
		this.platesL = torso.getChild("body_l").getChild("plates_l");
		this.idle = MotherCreakAnimations.IDLE.bake(root);
		this.twitch = MotherCreakAnimations.TWITCH.bake(root);
		this.walk = MotherCreakAnimations.WALK.bake(root);
		this.dormant = MotherCreakAnimations.DORMANT.bake(root);
		this.claw = MotherCreakAnimations.CLAW.bake(root);
		this.burst = MotherCreakAnimations.ROOT_BURST.bake(root);
		this.stun = MotherCreakAnimations.STUN.bake(root);
		this.roar = MotherCreakAnimations.ROAR.bake(root);
		this.death = MotherCreakAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(MotherCreakArtGeometry.mesh(), MotherCreakArtGeometry.TEXTURE_WIDTH, MotherCreakArtGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final MotherCreakArtState s) {
		super.setupAnim(s);
		boolean plates = !s.fury;
		this.platesR.visible = plates;
		this.platesL.visible = plates;
		if (s.deathTime > 0.0F) {
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		if (!s.awake) {
			this.dormant.apply((long) (s.ageInTicks * 50.0F), 1.0F);
			return;
		}
		if (s.stun.isStarted()) {
			this.stun.apply(s.stun, s.ageInTicks);
		} else {
			this.idle.apply((long) (s.ageInTicks * 50.0F), 1.0F);
			this.walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.2F, 3.0F);
			float yaw = Mth.clamp(s.yRot, -45.0F, 45.0F);
			this.head.yRot += yaw * Mth.DEG_TO_RAD * 0.6F;
			this.head.xRot += Mth.clamp(s.xRot, -30.0F, 30.0F) * Mth.DEG_TO_RAD * 0.5F;
		}
		this.twitch.apply(s.twitch, s.ageInTicks);
		this.claw.apply(s.claw, s.ageInTicks);
		this.burst.apply(s.burst, s.ageInTicks);
		this.roar.apply(s.roar, s.ageInTicks);
	}
}
