package dev.elsewhere.gear3.s3.waryak.client;

import dev.elsewhere.gear3.Gear3Content;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * War-Yak: a shaggy Ironpeak yak (low-slung head, shoulder hump, long fur skirt, tufted tail, wide horns that curve up)
 * in war barding (skyiron brow plate and horn caps, clan blanket). Vanilla-style cuboids on a 128x128 texture; the UV
 * layout matches tools/war_yak_texture.py (box name -> texOffs and size are listed there too).
 */
public class WarYakModel extends EntityModel<WarYakRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Gear3Content.id("war_yak"), "main");
	static final float HEAD_PITCH = 0.5F, CHARGE_PITCH = 0.95F;

	private final ModelPart body, head, tail, rightFront, leftFront, rightHind, leftHind;

	public WarYakModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.head = root.getChild("head");
		this.tail = root.getChild("tail");
		this.rightFront = root.getChild("right_front_leg");
		this.leftFront = root.getChild("left_front_leg");
		this.rightHind = root.getChild("right_hind_leg");
		this.leftHind = root.getChild("left_hind_leg");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7F, -6F, -10F, 14F, 12F, 20F), PartPose.offset(0F, 9F, 0F));
		body.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 32).addBox(-7.5F, 0F, -10F, 15F, 6F, 20F), PartPose.offset(0F, 4F, 0F));
		body.addOrReplaceChild("hump", CubeListBuilder.create().texOffs(70, 0).addBox(-5F, -3F, -4.5F, 10F, 5F, 9F), PartPose.offset(0F, -6F, -4F));
		body.addOrReplaceChild("blanket", CubeListBuilder.create().texOffs(0, 58).addBox(-7.5F, -0.5F, -5.5F, 15F, 7F, 11F), PartPose.offset(0F, -6F, 2.5F));

		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(70, 14).addBox(-4F, -4F, -7F, 8F, 8F, 8F),
			PartPose.offsetAndRotation(0F, 7F, -10F, HEAD_PITCH, 0F, 0F));
		head.addOrReplaceChild("muzzle", CubeListBuilder.create().texOffs(102, 14).addBox(-3F, -0.5F, -9.5F, 6F, 5F, 3F), PartPose.ZERO);
		head.addOrReplaceChild("brow_plate", CubeListBuilder.create().texOffs(102, 22).addBox(-3.5F, -3.5F, -7.6F, 7F, 4F, 1F), PartPose.ZERO);
		PartDefinition rHorn = head.addOrReplaceChild("right_horn", CubeListBuilder.create().texOffs(108, 0).addBox(-4F, -1F, -1F, 4F, 2F, 2F), PartPose.offset(-4F, -3F, -3.5F));
		rHorn.addOrReplaceChild("right_horn_tip", CubeListBuilder.create().texOffs(120, 0).addBox(-1F, -4F, -1F, 2F, 4F, 2F),
			PartPose.offsetAndRotation(-3.5F, 0F, 0F, -0.35F, 0F, -0.45F));
		PartDefinition lHorn = head.addOrReplaceChild("left_horn", CubeListBuilder.create().texOffs(108, 4).addBox(0F, -1F, -1F, 4F, 2F, 2F), PartPose.offset(4F, -3F, -3.5F));
		lHorn.addOrReplaceChild("left_horn_tip", CubeListBuilder.create().texOffs(120, 6).addBox(-1F, -4F, -1F, 2F, 4F, 2F),
			PartPose.offsetAndRotation(3.5F, 0F, 0F, -0.35F, 0F, 0.45F));

		PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(86, 43).addBox(-1F, 0F, -1F, 2F, 9F, 2F), PartPose.offsetAndRotation(0F, 5F, 10F, 0.15F, 0F, 0F));
		tail.addOrReplaceChild("tuft", CubeListBuilder.create().texOffs(94, 43).addBox(-1.5F, 0F, -1.5F, 3F, 4F, 3F), PartPose.offset(0F, 8F, 0F));

		root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(70, 30).addBox(-2F, 0F, -2F, 4F, 9F, 4F), PartPose.offset(-4.5F, 15F, -7F));
		root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(86, 30).addBox(-2F, 0F, -2F, 4F, 9F, 4F), PartPose.offset(4.5F, 15F, -7F));
		root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(102, 30).addBox(-2F, 0F, -2F, 4F, 9F, 4F), PartPose.offset(-4.5F, 15F, 7F));
		root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(70, 43).addBox(-2F, 0F, -2F, 4F, 9F, 4F), PartPose.offset(4.5F, 15F, 7F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(WarYakRenderer.State s) {
		super.setupAnim(s);
		float w = s.walkAnimationPos, ws = Math.min(1F, s.walkAnimationSpeed);
		float c = s.charge; // 0..1, eased in the renderer
		// walk: diagonal pairs; the charge is a bounding gallop (front pair together, hind pair together, longer strides)
		float walkR = Mth.cos(w * 0.6662F) * 1.2F * ws, walkL = Mth.cos(w * 0.6662F + Mth.PI) * 1.2F * ws;
		float gallop = Mth.cos(s.ageInTicks * 0.9F) * 0.9F;
		rightFront.xRot = Mth.lerp(c, walkR, gallop);
		leftFront.xRot = Mth.lerp(c, walkL, gallop);
		rightHind.xRot = Mth.lerp(c, walkL, -gallop);
		leftHind.xRot = Mth.lerp(c, walkR, -gallop);
		// head hangs low, grazes a little at rest; drops further to lead with the horns while charging
		float bob = Mth.sin(s.ageInTicks * 0.05F) * 0.04F + Mth.cos(w * 0.6662F) * 0.05F * ws;
		head.xRot = Mth.lerp(c, HEAD_PITCH + bob, CHARGE_PITCH);
		head.yRot = s.yRot * Mth.DEG_TO_RAD * (1F - c);
		head.y = 7F + c * 1.5F;
		body.xRot = c * 0.06F * Mth.sin(s.ageInTicks * 0.9F);
		tail.zRot = Mth.sin(s.ageInTicks * 0.08F) * 0.12F * (1F - c);
		tail.xRot = 0.15F + c * 0.9F + ws * 0.2F;
	}
}
