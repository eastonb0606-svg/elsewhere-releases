package dev.elsewhere.gear3.s3.pennant.client;

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
 * The Herdcaller's Standard: a 44-px wooden pole with a resin finial and a crossbar, and a 16 x 28 green flag with two
 * swallow tails hanging from the bar (64 x 64 texture, textures/entity/herdcaller_standard.png). The flag waves like a vanilla
 * banner (same swing formula) with a slow sideways sway, and unfurls downwards over the first 8 ticks after planting; the
 * tails flutter on their own once it is out. Ground is model y = 24, the whole thing is 47 px (2.94 blocks) tall.
 */
public class HerdcallerStandardModel extends EntityModel<HerdcallerStandardRenderer.State> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Gear3Content.id("herdcaller_standard"), "main");
	private final ModelPart flag;
	private final ModelPart tails;

	public HerdcallerStandardModel(ModelPart root) {
		super(root);
		this.flag = root.getChild("flag");
		this.tails = flag.getChild("tails");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("pole", CubeListBuilder.create().texOffs(56, 0).addBox(-1F, -44F, -1F, 2F, 44F, 2F), PartPose.offset(0F, 24F, 0F));
		root.addOrReplaceChild("finial", CubeListBuilder.create().texOffs(0, 52).addBox(-1.5F, -47F, -1.5F, 3F, 3F, 3F), PartPose.offset(0F, 24F, 0F));
		root.addOrReplaceChild("bar", CubeListBuilder.create().texOffs(0, 46).addBox(-9F, -43F, -1F, 18F, 2F, 2F), PartPose.offset(0F, 24F, 0F));
		PartDefinition flag = root.addOrReplaceChild("flag", CubeListBuilder.create().texOffs(0, 0).addBox(-8F, 0F, -2F, 16F, 28F, 1F), PartPose.offset(0F, -17F, 0F));
		flag.addOrReplaceChild("tails", CubeListBuilder.create().texOffs(0, 30).addBox(-8F, 0F, -2F, 6F, 5F, 1F).texOffs(14, 30).addBox(2F, 0F, -2F, 6F, 5F, 1F),
			PartPose.offset(0F, 28F, 0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(HerdcallerStandardRenderer.State s) {
		super.setupAnim(s);
		float t = s.ageInTicks + s.phase;
		float swing = ((t % 100F) / 100F) * Mth.TWO_PI;
		flag.xRot = (-0.0125F + 0.01F * Mth.cos(swing)) * Mth.PI;
		flag.zRot = 0.015F * Mth.sin(t * 0.09F);
		flag.yScale = 0.15F + 0.85F * s.unfurl;
		tails.visible = s.unfurl > 0.85F;
		tails.xRot = 0.10F * Mth.sin(t * 0.21F);
	}
}
