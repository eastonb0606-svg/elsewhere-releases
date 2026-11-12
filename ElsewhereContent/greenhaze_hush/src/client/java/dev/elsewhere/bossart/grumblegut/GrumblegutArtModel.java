package dev.elsewhere.bossart.grumblegut;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Grumblegut, the Endless Munchies: a pot-bellied moss boar-beast with a fern mane, a huge underbite jaw with fangs and a lolling
 * tongue, swept-back tusks and glowing red eyes. Boxes follow the hero boxes of grumblegut() in Ideas/art/src/bosses_art.py at 6 px per art unit.
 */
public class GrumblegutArtModel extends EntityModel<GrumblegutArtState> {
	private final ModelPart head;
	private final KeyframeAnimation idle, walk, paw, charge, munch, sniff, chew, slam, yawn, nap, puff, roar, death;

	public GrumblegutArtModel(ModelPart root) {
		super(root);
		this.head = root.getChild("whole").getChild("body").getChild("head");
		this.idle = GrumblegutArtAnimations.IDLE.bake(root);
		this.walk = GrumblegutArtAnimations.WALK.bake(root);
		this.paw = GrumblegutArtAnimations.PAW.bake(root);
		this.charge = GrumblegutArtAnimations.CHARGE.bake(root);
		this.munch = GrumblegutArtAnimations.MUNCH.bake(root);
		this.sniff = GrumblegutArtAnimations.SNIFF.bake(root);
		this.chew = GrumblegutArtAnimations.CHEW.bake(root);
		this.slam = GrumblegutArtAnimations.SLAM.bake(root);
		this.yawn = GrumblegutArtAnimations.YAWN.bake(root);
		this.nap = GrumblegutArtAnimations.NAP.bake(root);
		this.puff = GrumblegutArtAnimations.PUFF.bake(root);
		this.roar = GrumblegutArtAnimations.ROAR.bake(root);
		this.death = GrumblegutArtAnimations.DEATH.bake(root);
	}

	@Override
	public void setupAnim(GrumblegutArtState state) {
		super.setupAnim(state);
		if (!state.napping) {
			this.head.yRot = Mth.clamp(state.yRot, -30F, 30F) * Mth.DEG_TO_RAD;
			this.head.xRot += Mth.clamp(state.xRot, -20F, 20F) * Mth.DEG_TO_RAD;
		}
		this.walk.applyWalk(state.walkAnimationPos, state.walkAnimationSpeed, 2.0F, 2.5F);
		this.idle.apply(state.idle, state.ageInTicks);
		this.paw.apply(state.paw, state.ageInTicks);
		this.charge.apply(state.charge, state.ageInTicks);
		this.munch.apply(state.munch, state.ageInTicks);
		this.sniff.apply(state.sniff, state.ageInTicks);
		this.chew.apply(state.chew, state.ageInTicks);
		this.slam.apply(state.slam, state.ageInTicks);
		this.yawn.apply(state.yawn, state.ageInTicks);
		this.nap.apply(state.nap, state.ageInTicks);
		this.puff.apply(state.puff, state.ageInTicks);
		this.roar.apply(state.roar, state.ageInTicks);
		if (state.deathTime > 0) this.death.apply((long) (state.deathTime * 50F), 1F);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition whole = root.addOrReplaceChild("whole", CubeListBuilder.create(), PartPose.offsetAndRotation(0F, 24F, 0F, 0.0F, 0.0F, 0.0F));
		PartDefinition right_front_leg = whole.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(98, 129).addBox(-5.5F, 0F, -5.5F, 11F, 15F, 11F), PartPose.offsetAndRotation(-12F, -15F, -14.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition left_front_leg = whole.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(98, 129).addBox(-5.5F, 0F, -5.5F, 11F, 15F, 11F), PartPose.offsetAndRotation(12F, -15F, -14.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition right_hind_leg = whole.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(98, 129).addBox(-5.5F, 0F, -5.5F, 11F, 15F, 11F), PartPose.offsetAndRotation(-12F, -15F, 20.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition left_hind_leg = whole.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(98, 129).addBox(-5.5F, 0F, -5.5F, 11F, 15F, 11F), PartPose.offsetAndRotation(12F, -15F, 20.5F, 0.0F, 0.0F, 0.0F));
		PartDefinition body = whole.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-17F, -14F, -24F, 34F, 28F, 48F), PartPose.offsetAndRotation(0F, -23F, 4F, 0.0F, 0.0F, 0.0F));
		PartDefinition belly = body.addOrReplaceChild("belly", CubeListBuilder.create().texOffs(0, 76).addBox(-18F, -4F, -18F, 36F, 15F, 38F), PartPose.offsetAndRotation(0F, 7F, -5F, 0.0F, 0.0F, 0.0F));
		PartDefinition mane_0 = body.addOrReplaceChild("mane_0", CubeListBuilder.create().texOffs(214, 129).addBox(-1.5F, -17F, -0.5F, 3F, 18F, 1F), PartPose.offsetAndRotation(-15F, -14F, -8F, -0.5236F, 0.5236F, 0.0F));
		PartDefinition mane_1 = body.addOrReplaceChild("mane_1", CubeListBuilder.create().texOffs(222, 129).addBox(-1.5F, -17F, -0.5F, 3F, 18F, 1F), PartPose.offsetAndRotation(-9F, -14F, -4F, -0.5236F, 0.31416F, 0.0F));
		PartDefinition mane_2 = body.addOrReplaceChild("mane_2", CubeListBuilder.create().texOffs(214, 129).addBox(-1.5F, -17F, -0.5F, 3F, 18F, 1F), PartPose.offsetAndRotation(-3F, -14F, -8F, -0.5236F, 0.10472F, 0.0F));
		PartDefinition mane_3 = body.addOrReplaceChild("mane_3", CubeListBuilder.create().texOffs(222, 129).addBox(-1.5F, -17F, -0.5F, 3F, 18F, 1F), PartPose.offsetAndRotation(3F, -14F, -4F, -0.5236F, -0.10472F, 0.0F));
		PartDefinition mane_4 = body.addOrReplaceChild("mane_4", CubeListBuilder.create().texOffs(214, 129).addBox(-1.5F, -17F, -0.5F, 3F, 18F, 1F), PartPose.offsetAndRotation(9F, -14F, -8F, -0.5236F, -0.31416F, 0.0F));
		PartDefinition mane_5 = body.addOrReplaceChild("mane_5", CubeListBuilder.create().texOffs(222, 129).addBox(-1.5F, -17F, -0.5F, 3F, 18F, 1F), PartPose.offsetAndRotation(15F, -14F, -4F, -0.5236F, -0.5236F, 0.0F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(148, 76).addBox(-14F, -12.4F, -21F, 28F, 14F, 22F).texOffs(0, 159).addBox(-11.5F, -3.8F, -21.4F, 23F, 4F, 6F).texOffs(58, 159).addBox(-10F, -2F, -21.2F, 2F, 4F, 2F).texOffs(58, 159).addBox(-4F, -2F, -21.2F, 2F, 4F, 2F).texOffs(58, 159).addBox(2F, -2F, -21.2F, 2F, 4F, 2F).texOffs(58, 159).addBox(8F, -2F, -21.2F, 2F, 4F, 2F), PartPose.offsetAndRotation(0F, -2.2F, -25.6F, -0.10472F, 0.0F, 0.0F));
		PartDefinition eyes = head.addOrReplaceChild("eyes", CubeListBuilder.create().texOffs(78, 159).addBox(-11.4F, -2F, 0F, 6F, 4F, 1F).texOffs(78, 159).addBox(5.4F, -2F, 0F, 6F, 4F, 1F), PartPose.offsetAndRotation(0F, -10F, -21.6F, 0.0F, 0.0F, 0.0F));
		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 129).addBox(-13F, 1F, -22F, 26F, 7F, 23F).texOffs(66, 159).addBox(-11.5F, -1F, -21.5F, 3F, 3F, 3F).texOffs(66, 159).addBox(8.5F, -1F, -21.5F, 3F, 3F, 3F), PartPose.offsetAndRotation(0F, 2.4F, -1.2F, 0.34907F, 0.0F, 0.0F));
		PartDefinition tongue = jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(142, 129).addBox(-8F, -1F, -24F, 16F, 1F, 20F), PartPose.offsetAndRotation(0F, 1F, -4F, 0.20944F, 0.0F, 0.0F));
		PartDefinition right_tusk = head.addOrReplaceChild("right_tusk", CubeListBuilder.create().texOffs(230, 129).addBox(-1.5F, -13F, -1.5F, 3F, 14F, 3F), PartPose.offsetAndRotation(-14.4F, -12F, -2.4F, -0.69813F, 0.0F, -0.43633F));
		PartDefinition left_tusk = head.addOrReplaceChild("left_tusk", CubeListBuilder.create().texOffs(230, 129).addBox(-1.5F, -13F, -1.5F, 3F, 14F, 3F), PartPose.offsetAndRotation(14.4F, -12F, -2.4F, -0.69813F, 0.0F, 0.43633F));
		return LayerDefinition.create(mesh, 256, 256);
	}
}
