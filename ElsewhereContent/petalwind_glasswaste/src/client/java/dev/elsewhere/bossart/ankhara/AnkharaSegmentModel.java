package dev.elsewhere.bossart.ankhara;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/** One body segment: a sand-scaled block with two amber glass dorsal spines and molten gizzard plates on its flanks. Centred on its origin. */
public class AnkharaSegmentModel extends EntityModel<AnkharaRenderState> {
	public AnkharaSegmentModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition seg = root.addOrReplaceChild("seg", CubeListBuilder.create().texOffs(0, 0).addBox(-18F, -17F, -20F, 36F, 34F, 40F), PartPose.ZERO);
		seg.addOrReplaceChild("spines", CubeListBuilder.create().texOffs(152, 0).addBox(-3F, -14F, -11F, 6F, 16F, 6F)
			.texOffs(152, 0).addBox(-3F, -12F, 5F, 6F, 14F, 6F), PartPose.offsetAndRotation(0F, -17F, 0F, -0.3F, 0F, 0F));
		seg.addOrReplaceChild("gizzard", CubeListBuilder.create().texOffs(152, 24).addBox(-20F, 1F, -8F, 4F, 12F, 16F)
			.texOffs(152, 24).mirror().addBox(16F, 1F, -8F, 4F, 12F, 16F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 256, 128);
	}
}
