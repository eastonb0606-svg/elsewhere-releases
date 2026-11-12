package dev.elsewhere.verdigris_ironpeak.client.art2;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.zombie.BabyDrownedModel;
import net.minecraft.client.model.monster.zombie.DrownedModel;
import net.minecraft.client.renderer.entity.AbstractZombieRenderer;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Clockwork Drowned (art2): the vanilla drowned rig plus a riveted boiler on its back (the weak spot the mob's hit
 * logic already uses), a chimney and a turning wind-up key. 4 extra cubes. The renderer is vanilla DrownedRenderer
 * behaviour (trident throw pose, swim tilt) with this model; the seaweed outer layer is left off.
 */
@Environment(EnvType.CLIENT)
public final class ClockworkDrownedArt {
	private ClockworkDrownedArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "art2/clockwork_drowned"), "main");
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/clockwork_drowned.png");
	public static final float SCALE = 1.1F;

	public static LayerDefinition createBodyLayer() {
		return DrownedModel.createBodyLayer(CubeDeformation.NONE).apply(mesh -> {
			PartDefinition body = mesh.getRoot().getChild("body");
			PartDefinition boiler = body.addOrReplaceChild("boiler", CubeListBuilder.create()
					.texOffs(0, 32).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 7.0F, 4.0F)
					.texOffs(20, 32).addBox(0.5F, -3.0F, 1.0F, 2.0F, 3.0F, 2.0F), PartPose.offset(0.0F, 1.0F, 2.0F));
			boiler.addOrReplaceChild("key", CubeListBuilder.create()
					.texOffs(20, 38).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 2.0F)
					.texOffs(28, 32).addBox(0.0F, -2.0F, 1.5F, 0.0F, 4.0F, 3.0F), PartPose.offset(-1.0F, 3.5F, 4.0F));
			return mesh;
		});
	}

	@Environment(EnvType.CLIENT)
	public static class Model extends DrownedModel {
		private final ModelPart boiler, key;

		public Model(ModelPart root) {
			super(root);
			this.boiler = body.getChild("boiler");
			this.key = boiler.getChild("key");
		}

		@Override
		public void setupAnim(ZombieRenderState s) {
			super.setupAnim(s);
			// idle: the key winds down steadily and the boiler chugs; both speed up while walking
			key.zRot = s.ageInTicks * 0.12F + s.walkAnimationPos * 0.25F;
			boiler.y = 1.0F + Math.max(0.0F, (float) Math.sin(s.ageInTicks * (0.35F + s.walkAnimationSpeed * 0.4F))) * 0.25F;
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Renderer extends AbstractZombieRenderer<Drowned, ZombieRenderState, DrownedModel> {
		public Renderer(EntityRendererProvider.Context ctx) {
			super(ctx, new Model(ctx.bakeLayer(LAYER)), new BabyDrownedModel(ctx.bakeLayer(ModelLayers.DROWNED_BABY)),
					ArmorModelSet.bake(ModelLayers.DROWNED_ARMOR, ctx.getModelSet(), DrownedModel::new),
					ArmorModelSet.bake(ModelLayers.DROWNED_BABY_ARMOR, ctx.getModelSet(), BabyDrownedModel::new));
		}

		@Override
		public ZombieRenderState createRenderState() {
			return new ZombieRenderState();
		}

		@Override
		public Identifier getTextureLocation(ZombieRenderState state) {
			return TEXTURE;
		}

		@Override
		protected HumanoidModel.ArmPose getArmPose(Drowned mob, HumanoidArm arm) {
			ItemStack item = mob.getItemHeldByArm(arm);
			return mob.getMainArm() == arm && mob.isAggressive() && item.is(Items.TRIDENT) ? HumanoidModel.ArmPose.THROW_TRIDENT : super.getArmPose(mob, arm);
		}

		@Override
		protected void setupRotations(ZombieRenderState state, PoseStack pose, float bodyRot, float scale) {
			super.setupRotations(state, pose, bodyRot, scale);
			float swim = state.swimAmount;
			if (swim > 0.0F) {
				float rot = Mth.lerp(swim, 0.0F, -10.0F - state.xRot);
				pose.rotateAround(Axis.XP.rotationDegrees(rot), 0.0F, state.boundingBoxHeight / 2.0F / scale, 0.0F);
			}
		}

		@Override
		protected void scale(ZombieRenderState state, PoseStack pose) {
			pose.scale(SCALE, SCALE, SCALE);
		}
	}
}
