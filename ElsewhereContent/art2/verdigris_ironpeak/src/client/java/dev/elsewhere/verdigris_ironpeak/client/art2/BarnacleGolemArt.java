package dev.elsewhere.verdigris_ironpeak.client.art2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.verdigris_sea.mob.BarnacleGolem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.resources.Identifier;

/**
 * Barnacle Golem (art2): the vanilla iron golem rig in weathered copper, crusted with 14 small barnacle cubes on the
 * shoulders, back, head, arms and a leg (barnacle UVs at x >= 84 of the 128x128 texture). Vanilla walk, idle and
 * arm-slam animation carry over; the barnacles ride on their parts.
 */
@Environment(EnvType.CLIENT)
public final class BarnacleGolemArt {
	private BarnacleGolemArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "art2/barnacle_golem"), "main");
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/barnacle_golem.png");
	public static final float SCALE = 0.9F;

	// barnacle UVs: opening on top (big 3x2x3, small 2x2x2, flat 2x1x2), opening facing back (3x3x2, 2x2x2), opening facing -X (2x2x2)
	private static CubeListBuilder upBig(float x, float y, float z) { return CubeListBuilder.create().texOffs(84, 0).addBox(x, y, z, 3, 2, 3); }
	private static CubeListBuilder upSmall(float x, float y, float z) { return CubeListBuilder.create().texOffs(84, 8).addBox(x, y, z, 2, 2, 2); }
	private static CubeListBuilder upFlat(float x, float y, float z) { return CubeListBuilder.create().texOffs(96, 8).addBox(x, y, z, 2, 1, 2); }
	private static CubeListBuilder backBig(float x, float y, float z) { return CubeListBuilder.create().texOffs(108, 0).addBox(x, y, z, 3, 3, 2); }
	private static CubeListBuilder backSmall(float x, float y, float z) { return CubeListBuilder.create().texOffs(104, 8).addBox(x, y, z, 2, 2, 2); }
	private static CubeListBuilder side(float x, float y, float z, boolean mirror) { return CubeListBuilder.create().mirror(mirror).texOffs(112, 8).addBox(x, y, z, 2, 2, 2); }

	public static LayerDefinition createBodyLayer() {
		return IronGolemModel.createBodyLayer().apply(mesh -> {
			PartDefinition root = mesh.getRoot();
			PartDefinition body = root.getChild("body");
			body.addOrReplaceChild("barnacle_0", upBig(-7.0F, -4.0F, -2.0F), PartPose.ZERO);
			body.addOrReplaceChild("barnacle_1", upSmall(4.0F, -4.0F, 0.0F), PartPose.ZERO);
			body.addOrReplaceChild("barnacle_2", upFlat(-1.0F, -3.0F, -4.0F), PartPose.ZERO);
			body.addOrReplaceChild("barnacle_3", backBig(-6.0F, 0.0F, 5.0F), PartPose.ZERO);
			body.addOrReplaceChild("barnacle_4", backSmall(-1.0F, 3.0F, 5.0F), PartPose.ZERO);
			body.addOrReplaceChild("barnacle_5", backBig(3.0F, 5.0F, 5.0F), PartPose.ZERO);
			PartDefinition head = root.getChild("head");
			head.addOrReplaceChild("barnacle_0", upSmall(-3.0F, -14.0F, -3.0F), PartPose.ZERO);
			head.addOrReplaceChild("barnacle_1", upFlat(1.0F, -13.0F, -1.0F), PartPose.ZERO);
			PartDefinition rightArm = root.getChild("right_arm");
			rightArm.addOrReplaceChild("barnacle_0", upSmall(-12.0F, -4.5F, -1.0F), PartPose.ZERO);
			rightArm.addOrReplaceChild("barnacle_1", side(-15.0F, 6.0F, -1.0F, false), PartPose.ZERO);
			rightArm.addOrReplaceChild("barnacle_2", side(-15.0F, 14.0F, 0.0F, false), PartPose.ZERO);
			PartDefinition leftArm = root.getChild("left_arm");
			leftArm.addOrReplaceChild("barnacle_0", upSmall(10.0F, -4.5F, -1.0F), PartPose.ZERO);
			leftArm.addOrReplaceChild("barnacle_1", side(13.0F, 9.0F, -1.0F, true), PartPose.ZERO);
			root.getChild("left_leg").addOrReplaceChild("barnacle_0", side(2.5F, 4.0F, -1.0F, true), PartPose.ZERO);
			return mesh;
		});
	}

	@Environment(EnvType.CLIENT)
	public static class Model extends IronGolemModel {
		public Model(ModelPart root) {
			super(root);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Renderer extends MobRenderer<BarnacleGolem, IronGolemRenderState, Model> {
		public Renderer(EntityRendererProvider.Context ctx) {
			super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.7F * SCALE);
		}

		@Override
		public IronGolemRenderState createRenderState() {
			return new IronGolemRenderState();
		}

		@Override
		public void extractRenderState(BarnacleGolem golem, IronGolemRenderState state, float partial) {
			super.extractRenderState(golem, state, partial);
			state.attackTicksRemaining = golem.attackAnim > 0 ? golem.attackAnim - partial : 0;
		}

		@Override
		public Identifier getTextureLocation(IronGolemRenderState state) {
			return TEXTURE;
		}

		@Override
		protected void scale(IronGolemRenderState state, PoseStack pose) {
			pose.scale(SCALE, SCALE, SCALE);
		}
	}
}
