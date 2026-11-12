package dev.elsewhere.verdigris_ironpeak.client.art2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.ironpeak.mob.IronclanRaider;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/**
 * Ironclan Raider (art2): the vanilla humanoid rig plus a horned iron helm (hat layer + 4 horn cubes), a braided
 * beard, a fur collar and fur pauldrons. 8 extra cubes; vanilla walk, idle arm bob, item pose and swing carry over.
 * Texture: 64x64, top half in the zombie layout, extras in the bottom half.
 */
@Environment(EnvType.CLIENT)
public final class IronclanRaiderArt {
	private IronclanRaiderArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "art2/ironclan_raider"), "main");
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/ironclan_raider.png");
	public static final float SCALE = 1.4F;

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.getChild("head");
		head.addOrReplaceChild("right_horn", CubeListBuilder.create()
				.texOffs(0, 32).addBox(-7.5F, -7.0F, -1.0F, 3.0F, 2.0F, 2.0F)
				.texOffs(10, 32).addBox(-7.0F, -10.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("left_horn", CubeListBuilder.create().mirror()
				.texOffs(0, 32).addBox(4.5F, -7.0F, -1.0F, 3.0F, 2.0F, 2.0F)
				.texOffs(10, 32).addBox(6.0F, -10.0F, -0.5F, 1.0F, 3.0F, 1.0F), PartPose.ZERO);
		head.addOrReplaceChild("beard", CubeListBuilder.create()
				.texOffs(16, 32).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 5.0F, 1.0F), PartPose.ZERO);
		root.getChild("body").addOrReplaceChild("collar", CubeListBuilder.create()
				.texOffs(0, 40).addBox(-4.5F, -1.0F, -2.5F, 9.0F, 3.0F, 5.0F), PartPose.ZERO);
		root.getChild("right_arm").addOrReplaceChild("pauldron", CubeListBuilder.create()
				.texOffs(32, 40).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 4.0F, 5.0F), PartPose.ZERO);
		root.getChild("left_arm").addOrReplaceChild("pauldron", CubeListBuilder.create().mirror()
				.texOffs(32, 40).addBox(-1.5F, -2.5F, -2.5F, 5.0F, 4.0F, 5.0F), PartPose.ZERO);
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Environment(EnvType.CLIENT)
	public static class Model extends HumanoidModel<HumanoidRenderState> {
		private final ModelPart beard;

		public Model(ModelPart root) {
			super(root);
			this.beard = head.getChild("beard");
		}

		@Override
		public void setupAnim(HumanoidRenderState s) {
			super.setupAnim(s);
			// idle: the beard sways a little; walking makes it bounce with the stride
			beard.xRot = -0.04F + (float) Math.sin(s.ageInTicks * 0.08F) * 0.03F
					+ Math.abs((float) Math.cos(s.walkAnimationPos * 0.6662F)) * s.walkAnimationSpeed * 0.12F;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Renderer extends HumanoidMobRenderer<IronclanRaider, HumanoidRenderState, Model> {
		public Renderer(EntityRendererProvider.Context ctx) {
			super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5F * SCALE);
		}

		@Override
		public HumanoidRenderState createRenderState() {
			return new HumanoidRenderState();
		}

		@Override
		public Identifier getTextureLocation(HumanoidRenderState state) {
			return TEXTURE;
		}

		@Override
		protected void scale(HumanoidRenderState state, PoseStack pose) {
			pose.scale(SCALE, SCALE, SCALE);
		}
	}
}
