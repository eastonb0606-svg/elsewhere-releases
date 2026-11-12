package dev.elsewhere.bossart.aphelion;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Aphelion's body segment and tail (64x64, aphelion_segment.png / aphelion_segment_rift.png): a 12 px obsidian block with
 * a glowing amethyst dorsal fin, a violet belly plate and side scales; the tail is a narrower block with a tall fin. Both
 * are centred on the origin so the renderer can lay them along the head's trail with the PoseStack alone.
 */
public final class AphelionSegmentArtModel {
	private AphelionSegmentArtModel() {}

	public static LayerDefinition createSegment() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("body", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-6.0F, -6.0F, -6.0F, 12, 12, 12)
			.texOffs(0, 24).addBox(-5.0F, 6.0F, -5.0F, 10, 2, 10)
			.texOffs(48, 0).addBox(-1.0F, -11.0F, -3.0F, 2, 5, 6)
			.texOffs(40, 24).addBox(6.0F, -3.0F, -4.0F, 1, 6, 8)
			.texOffs(40, 24).mirror().addBox(-7.0F, -3.0F, -4.0F, 1, 6, 8), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	public static LayerDefinition createTail() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("tail", CubeListBuilder.create()
			.texOffs(0, 40).addBox(-4.0F, -4.0F, -6.0F, 8, 8, 12)
			.texOffs(40, 40).addBox(-0.5F, -6.0F, 4.0F, 1, 12, 10)
			.texOffs(48, 0).addBox(-1.0F, -9.0F, -4.0F, 2, 5, 6), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}
}
