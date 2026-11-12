package dev.elsewhere.client.model;

import dev.elsewhere.client.model.gen.CreakingHuskGeometry;
import dev.elsewhere.client.render.States;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/** Creaking Husk: lurching walk when unwatched, a stiff frozen pose with the odd twitch when watched. */
public class CreakingHuskModel extends EntityModel<States.Husk> {
	private final ModelPart body;
	private final ModelPart head;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public CreakingHuskModel(final ModelPart root) {
		super(root);
		ModelPart r = root.getChild("root");
		this.body = r.getChild("body");
		this.head = this.body.getChild("head");
		this.rightArm = this.body.getChild("right_arm");
		this.leftArm = this.body.getChild("left_arm");
		this.rightLeg = r.getChild("right_leg");
		this.leftLeg = r.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(CreakingHuskGeometry.mesh(), CreakingHuskGeometry.TEXTURE_WIDTH, CreakingHuskGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final States.Husk s) {
		super.setupAnim(s);
		this.head.yRot = s.yRot * Mth.DEG_TO_RAD;
		this.head.xRot = s.xRot * Mth.DEG_TO_RAD;
		this.rightArm.zRot = 0.08F;
		this.leftArm.zRot = -0.08F;
		if (s.frozen) {
			// caught mid-step: one arm reaching, then a twitch every couple of seconds
			this.rightArm.xRot = -0.55F;
			this.leftArm.xRot = 0.2F;
			this.body.xRot = 0.12F;
			float twitch = (s.ageInTicks % 47.0F) < 3.0F ? Mth.sin(s.ageInTicks * 4.0F) * 0.12F : 0.0F;
			this.head.zRot = twitch;
			return;
		}
		float pos = s.walkAnimationPos;
		float spd = Math.min(1.0F, s.walkAnimationSpeed);
		this.rightLeg.xRot = Mth.cos(pos * 0.55F) * 1.1F * spd;
		this.leftLeg.xRot = Mth.cos(pos * 0.55F + Mth.PI) * 1.1F * spd;
		this.rightArm.xRot = Mth.cos(pos * 0.55F + Mth.PI) * 0.9F * spd - 0.2F;
		this.leftArm.xRot = Mth.cos(pos * 0.55F) * 0.9F * spd - 0.2F;
		this.body.zRot = Mth.sin(pos * 0.275F) * 0.08F * spd;
		this.body.xRot = 0.15F * spd + Mth.sin(s.ageInTicks * 0.05F) * 0.02F;
		this.head.zRot = Mth.sin(s.ageInTicks * 0.09F) * 0.05F;
	}
}
