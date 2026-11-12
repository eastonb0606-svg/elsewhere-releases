package dev.elsewhere.gear3.s3.willo.client;

import dev.elsewhere.gear3.Gear3Content;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Marsh Wisp model (32x32, translucent): a mint-white core inside a slowly tumbling Mirewick-purple glass shell, a flickering
 * gold flame on top, a fading wisp-tail behind and three crimson motes circling it. It bobs while it floats.
 */
public class MarshWispModel extends EntityModel<LivingEntityRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Gear3Content.id("marsh_wisp"), "main");
	private static final float TAU = (float) (Math.PI * 2);

	private final ModelPart body, shell, flame, tail;
	private final ModelPart[] motes = new ModelPart[3];

	public MarshWispModel(ModelPart root) {
		super(root, RenderTypes::entityTranslucent);
		this.body = root.getChild("body");
		this.shell = body.getChild("shell");
		this.flame = body.getChild("flame");
		this.tail = body.getChild("tail");
		for (int i = 0; i < motes.length; i++) motes[i] = body.getChild("mote" + i);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2F, -2F, -2F, 4F, 4F, 4F), PartPose.offset(0F, 20F, 0F));
		body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 8).addBox(-3F, -3F, -3F, 6F, 6F, 6F), PartPose.ZERO);
		body.addOrReplaceChild("flame", CubeListBuilder.create().texOffs(16, 0).addBox(-1F, -3F, -1F, 2F, 3F, 2F), PartPose.offset(0F, -2F, 0F));
		body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 20).addBox(-1F, -1F, 0F, 2F, 2F, 4F), PartPose.offset(0F, 0.5F, 2.5F));
		for (int i = 0; i < 3; i++)
			body.addOrReplaceChild("mote" + i, CubeListBuilder.create().texOffs(24, 0).addBox(-0.5F, -0.5F, -0.5F, 1F, 1F, 1F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 32, 32);
	}

	@Override
	public void setupAnim(LivingEntityRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		body.y += Mth.sin(t * 0.1F) * 1.2F;
		shell.yRot = t * 0.05F;
		shell.xRot = t * 0.031F;
		float f = Mth.sin(t * 0.6F) + 0.5F * Mth.sin(t * 1.7F);
		flame.yScale = 1F + 0.22F * f;
		flame.xScale = flame.zScale = 1F - 0.08F * f;
		tail.yRot = Mth.sin(t * 0.2F) * 0.35F;
		tail.xRot = 0.15F + Mth.sin(t * 0.13F) * 0.1F;
		for (int i = 0; i < motes.length; i++) {
			float a = t * 0.15F + i * TAU / 3F;
			motes[i].x = Mth.cos(a) * 4.5F;
			motes[i].z = Mth.sin(a) * 4.5F;
			motes[i].y = Mth.sin(t * 0.2F + i * 2.1F) * 1.5F;
		}
	}
}
