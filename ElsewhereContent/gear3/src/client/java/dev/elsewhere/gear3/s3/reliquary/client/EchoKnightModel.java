package dev.elsewhere.gear3.s3.reliquary.client;

import dev.elsewhere.gear3.Gear3Content;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Echo Knight: the hollow echo of a Mute Order knight. A hooded great-helm with a sealed visor slit (glow layer: echo-cyan
 * slit and a sigil on the tabard), plate body under a tabard, heavy pauldrons, a long cape and a two-handed echo blade held
 * point-down at rest. Vanilla-style cuboids on a 128x64 texture; the UV boxes match tools/echo_knight_art.py.
 */
public class EchoKnightModel extends EntityModel<EchoKnightRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Gear3Content.id("echo_knight"), "main");

	private final ModelPart head, body, cape, rightArm, leftArm, rightLeg, leftLeg, blade;

	public EchoKnightModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.cape = body.getChild("cape");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.blade = rightArm.getChild("blade");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F), PartPose.ZERO);
		head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(32, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F, new CubeDeformation(0.5F)), PartPose.ZERO);

		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16).addBox(-4F, 0F, -2F, 8F, 12F, 4F), PartPose.ZERO);
		body.addOrReplaceChild("tabard", CubeListBuilder.create().texOffs(0, 32).addBox(-4F, 0F, -2F, 8F, 14F, 4F, new CubeDeformation(0.3F)), PartPose.ZERO);
		body.addOrReplaceChild("cape", CubeListBuilder.create().texOffs(64, 32).addBox(-4F, 0F, 0F, 8F, 18F, 0F, new CubeDeformation(0F, 0F, 0.06F)), PartPose.offsetAndRotation(0F, 0.5F, 2.6F, 0.08F, 0F, 0F));

		PartDefinition rArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-3F, -2F, -2F, 4F, 12F, 4F), PartPose.offset(-5F, 2F, 0F));
		rArm.addOrReplaceChild("right_pauldron", CubeListBuilder.create().texOffs(80, 16).addBox(-3.5F, -3F, -2.5F, 5F, 3F, 5F), PartPose.ZERO);
		// the blade hangs from the right fist: grip, guard, then 12 px of blade (point down at rest, raised by the swing)
		PartDefinition blade = rArm.addOrReplaceChild("blade", CubeListBuilder.create()
			.texOffs(64, 20).addBox(-0.5F, -3F, -0.5F, 1F, 4F, 1F)
			.texOffs(64, 16).addBox(-2.5F, 1F, -0.5F, 5F, 1F, 1F)
			.texOffs(64, 0).addBox(-0.5F, 2F, -1F, 1F, 12F, 2F), PartPose.offsetAndRotation(-1F, 9F, -1F, -0.2F, 0F, 0F));
		PartDefinition lArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 32).addBox(-1F, -2F, -2F, 4F, 12F, 4F), PartPose.offset(5F, 2F, 0F));
		lArm.addOrReplaceChild("left_pauldron", CubeListBuilder.create().texOffs(80, 24).addBox(-1.5F, -3F, -2.5F, 5F, 3F, 5F), PartPose.ZERO);

		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offset(-1.9F, 12F, 0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(24, 32).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offset(1.9F, 12F, 0F));
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(EchoKnightRenderer.State s) {
		super.setupAnim(s);
		float w = s.walkAnimationPos, ws = Math.min(1F, s.walkAnimationSpeed);
		head.yRot = s.yRot * Mth.DEG_TO_RAD;
		head.xRot = s.xRot * Mth.DEG_TO_RAD;
		// a slow, heavy march: short strides, arms barely swing; at rest a faint drift, like an echo settling
		float stride = Mth.cos(w * 0.6662F) * 0.9F * ws;
		rightLeg.xRot = stride;
		leftLeg.xRot = -stride;
		float drift = Mth.sin(s.ageInTicks * 0.06F) * 0.03F;
		leftArm.xRot = -stride * 0.5F + drift;
		leftArm.zRot = -0.06F;
		rightArm.zRot = 0.06F;
		// the blow: the right arm lifts the blade overhead and brings it down across the body
		float a = s.attack;
		if (a > 0F) {
			float arc = Mth.sin(a * Mth.PI);
			rightArm.xRot = -2.4F * arc + stride * 0.3F;
			rightArm.yRot = -0.35F * arc;
			blade.xRot = -0.2F - 1.2F * arc;
		} else if (s.striking) {
			// between blows in a fight: both hands bring the blade up in a guard, point forward and raised
			rightArm.xRot = -1.35F + stride * 0.3F - drift;
			rightArm.yRot = -0.2F;
			blade.xRot = -0.9F;
			leftArm.xRot = -0.9F + drift;
		} else {
			rightArm.xRot = stride * 0.3F - drift;
			rightArm.yRot = 0F;
			blade.xRot = -0.2F;
		}
		body.yRot = 0F;
		cape.xRot = 0.08F + 0.35F * ws + Mth.sin(s.ageInTicks * 0.08F) * 0.04F;
	}
}
