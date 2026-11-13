package dev.elsewhere.client.v2;

import dev.elsewhere.Elsewhere;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.GoatRenderState;

/**
 * Hollow Stag model: the goat's quadruped skeleton (so the goat walk and head animation apply), reshaped as a deer -
 * long legs, a tail, no goat horns or beard - with branching, multi-tine antlers built from model cubes.
 * Texture 64x64, layout in tools gen: antler beams at (12,55), tines at (30,53), tail at (23,53).
 */
public class StagModel extends QuadrupedModel<GoatRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Elsewhere.id("hollow_stag"), "main");

	public StagModel(final ModelPart root) {
		super(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild(
			"head",
			CubeListBuilder.create()
				.texOffs(2, 61)
				.addBox(-6.0F, -11.0F, -10.0F, 3.0F, 2.0F, 1.0F)
				.texOffs(2, 61)
				.mirror()
				.addBox(2.0F, -11.0F, -10.0F, 3.0F, 2.0F, 1.0F),
			PartPose.offset(1.0F, 12.0F, 0.0F)
		);
		head.addOrReplaceChild(
			"nose",
			CubeListBuilder.create().texOffs(34, 46).addBox(-3.0F, -4.0F, -8.0F, 5.0F, 7.0F, 10.0F),
			PartPose.offsetAndRotation(0.0F, -8.0F, -8.0F, 0.9599F, 0.0F, 0.0F)
		);
		antler(head, "left", 1.0F);
		antler(head, "right", -1.0F);
		PartDefinition body = root.addOrReplaceChild(
			"body",
			CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, -17.0F, -7.0F, 9.0F, 11.0F, 16.0F).texOffs(0, 28).addBox(-5.0F, -18.0F, -8.0F, 11.0F, 14.0F, 11.0F),
			PartPose.offset(0.0F, 22.0F, 0.0F)
		);
		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(23, 53).addBox(-1.0F, -17.0F, 9.0F, 2.0F, 3.0F, 1.0F), PartPose.ZERO);
		root.addOrReplaceChild("left_hind_leg", CubeListBuilder.create().texOffs(36, 31).addBox(0.0F, 4.0F, 0.0F, 3.0F, 8.0F, 3.0F), PartPose.offset(1.0F, 12.0F, 4.0F));
		root.addOrReplaceChild("right_hind_leg", CubeListBuilder.create().texOffs(49, 31).addBox(0.0F, 4.0F, 0.0F, 3.0F, 8.0F, 3.0F), PartPose.offset(-3.0F, 12.0F, 4.0F));
		root.addOrReplaceChild("left_front_leg", CubeListBuilder.create().texOffs(51, 0).addBox(0.0F, 0.0F, 0.0F, 3.0F, 12.0F, 3.0F), PartPose.offset(1.0F, 12.0F, -6.0F));
		root.addOrReplaceChild("right_front_leg", CubeListBuilder.create().texOffs(51, 15).addBox(0.0F, 0.0F, 0.0F, 3.0F, 12.0F, 3.0F), PartPose.offset(-3.0F, 12.0F, -6.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	private static CubeListBuilder beam() {
		return CubeListBuilder.create().texOffs(12, 55).addBox(-1.0F, -5.0F, -1.0F, 2.0F, 5.0F, 2.0F);
	}

	private static CubeListBuilder tine(final float len) {
		return CubeListBuilder.create().texOffs(30, 53).addBox(-0.5F, -len, -0.5F, 1.0F, len, 1.0F);
	}

	/** One antler: main beam sweeping up and back, brow and bez tines forward, a three-point crown. s = +1 left, -1 right. */
	private static void antler(final PartDefinition head, final String side, final float s) {
		PartDefinition lower = head.addOrReplaceChild(side + "_antler", beam(), PartPose.offsetAndRotation(s * 1.5F, -9.0F, -9.0F, 0.0F, 0.0F, s * 0.35F));
		lower.addOrReplaceChild("brow_tine", tine(4.0F), PartPose.offsetAndRotation(0.0F, -1.0F, -0.5F, 1.1F, 0.0F, s * 0.2F));
		PartDefinition upper = lower.addOrReplaceChild("upper_beam", beam(), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, -0.3F, 0.0F, -s * 0.6F));
		upper.addOrReplaceChild("bez_tine", tine(4.0F), PartPose.offsetAndRotation(0.0F, -1.5F, -0.5F, 0.9F, 0.0F, s * 0.3F));
		upper.addOrReplaceChild("trez_tine", tine(3.0F), PartPose.offsetAndRotation(0.0F, -3.5F, 0.5F, -0.7F, 0.0F, s * 0.3F));
		PartDefinition crown = upper.addOrReplaceChild("crown_beam", beam(), PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, -0.15F, 0.0F, -s * 0.1F));
		crown.addOrReplaceChild("crown_out", tine(4.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.0F, 0.0F, s * 0.55F));
		crown.addOrReplaceChild("crown_in", tine(3.0F), PartPose.offsetAndRotation(0.0F, -3.0F, 0.0F, 0.0F, 0.0F, -s * 0.5F));
		crown.addOrReplaceChild("crown_fwd", tine(3.0F), PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.7F, 0.0F, 0.0F));
	}
}
