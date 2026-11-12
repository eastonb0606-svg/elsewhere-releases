package dev.elsewhere.client.model;

import dev.elsewhere.boss.mother_creak.MotherCreak;
import dev.elsewhere.client.model.gen.MotherCreakGeometry;
import dev.elsewhere.client.render.States;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * Mother Creak: kneeling asleep, a lurching walk when unseen, a frozen reaching pose with creak twitches while watched,
 * a raised-arm branch-claw swipe, the two-arm Root Burst stomp, the heart-flare stun on one knee, and Heartwood Fury
 * without her bark plates.
 */
public class MotherCreakModel extends EntityModel<States.Mother> {
	private final ModelPart root;
	private final ModelPart body;
	private final ModelPart plates;
	private final ModelPart heart;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightClaw;
	private final ModelPart leftClaw;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public MotherCreakModel(final ModelPart model) {
		super(model);
		this.root = model.getChild("root");
		this.body = this.root.getChild("body");
		this.plates = this.body.getChild("plates");
		this.heart = this.body.getChild("heart");
		this.head = this.body.getChild("head");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightClaw = this.rightArm.getChild("right_claw");
		this.leftClaw = this.leftArm.getChild("left_claw");
		this.rightLeg = this.root.getChild("right_leg");
		this.leftLeg = this.root.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(MotherCreakGeometry.mesh(), MotherCreakGeometry.TEXTURE_WIDTH, MotherCreakGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final States.Mother s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		this.plates.visible = s.phase != MotherCreak.PHASE_FURY;
		float pulse = 1.0F + Mth.sin(t * 0.15F) * 0.08F + (s.stun > 0 ? 0.35F : 0.0F);
		this.heart.xScale = pulse;
		this.heart.yScale = pulse;
		this.heart.zScale = pulse;
		// branch arms held out like the concept painting
		this.rightArm.zRot = 0.55F;
		this.leftArm.zRot = -0.55F;
		this.rightClaw.xRot = -0.3F;
		this.leftClaw.xRot = -0.3F;
		this.head.yRot = s.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = s.xRot * Mth.DEG_TO_RAD * 0.5F;

		if (!s.awake) {
			kneel(0.8F);
			this.head.xRot = 0.6F;
			this.rightArm.zRot = 0.1F;
			this.leftArm.zRot = -0.1F;
			this.rightArm.xRot = -0.3F;
			this.leftArm.xRot = -0.3F;
			return;
		}
		if (s.stun > 0) {
			kneel(1.0F);
			this.head.xRot = 0.4F + Mth.sin(t * 0.6F) * 0.05F;
			this.rightArm.zRot = 0.15F;
			this.leftArm.zRot = -0.15F;
			this.rightArm.xRot = -0.2F;
			this.leftArm.xRot = 0.3F;
			return;
		}
		if (!s.watched || s.phase == MotherCreak.PHASE_FURY) {
			float pos = s.walkAnimationPos;
			float spd = Math.min(1.0F, s.walkAnimationSpeed);
			this.rightLeg.xRot = Mth.cos(pos * 0.45F) * 0.8F * spd;
			this.leftLeg.xRot = Mth.cos(pos * 0.45F + Mth.PI) * 0.8F * spd;
			this.rightArm.xRot = Mth.cos(pos * 0.45F + Mth.PI) * 0.5F * spd;
			this.leftArm.xRot = Mth.cos(pos * 0.45F) * 0.5F * spd;
			this.body.zRot = Mth.sin(pos * 0.225F) * 0.1F * spd;
			this.body.xRot = 0.1F * spd;
		} else {
			// frozen mid-reach, with a creak twitch now and then
			this.rightArm.xRot = -0.7F;
			this.leftArm.xRot = -0.25F;
			this.body.xRot = 0.12F;
			if ((t % 61.0F) < 3.0F) {
				this.head.zRot = Mth.sin(t * 5.0F) * 0.15F;
				this.leftClaw.xRot = -0.8F;
			}
		}
		this.head.zRot += Mth.sin(t * 0.04F) * 0.03F;

		if (s.claw > 0) {
			float elapsed = MotherCreak.CLAW_WINDUP + 8 - s.claw;
			if (elapsed < MotherCreak.CLAW_WINDUP) {
				float k = elapsed / MotherCreak.CLAW_WINDUP;
				this.rightArm.xRot = Mth.lerp(k, this.rightArm.xRot, -2.6F);
				this.rightArm.zRot = Mth.lerp(k, 0.55F, 0.2F);
				this.body.yRot = -0.35F * k;
			} else {
				float k = Math.min(1.0F, (elapsed - MotherCreak.CLAW_WINDUP) / 4.0F);
				this.rightArm.xRot = Mth.lerp(k, -2.6F, 0.6F);
				this.rightArm.zRot = 0.2F;
				this.body.yRot = Mth.lerp(k, -0.35F, 0.35F);
			}
		}
		if (s.slam > 0) {
			float elapsed = 30.0F - s.slam;
			float k = elapsed < 18.0F ? elapsed / 18.0F : Math.max(0.0F, 1.0F - (elapsed - 18.0F) / 4.0F);
			float down = elapsed >= 18.0F ? 1.0F : 0.0F;
			this.rightArm.xRot = Mth.lerp(k, this.rightArm.xRot, -2.9F + down * 3.2F);
			this.leftArm.xRot = Mth.lerp(k, this.leftArm.xRot, -2.9F + down * 3.2F);
			this.rightArm.zRot = 0.2F;
			this.leftArm.zRot = -0.2F;
			this.body.xRot = down * 0.35F;
			this.rightLeg.xRot = -0.5F * down;
		}
	}

	private void kneel(final float amount) {
		this.root.y += 5.0F * amount;
		this.body.xRot = 0.55F * amount;
		this.rightLeg.xRot = -1.4F * amount;
		this.leftLeg.xRot = 0.6F * amount;
	}
}
