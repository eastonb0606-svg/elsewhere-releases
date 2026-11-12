package dev.elsewhere.greenhaze_hush.art2;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.hush.HushMobs;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.monster.enderman.EndermanModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.LivingEntityEmissiveLayer;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Echo Stalker (art2): the vanilla enderman rig with a blind, eyeless face, sculk veins and two warden-style tendril
 * planes on the head (2 extra zero-depth cubes, cutout). Idle: the tendrils sway; walking swings them with the
 * stride; while the mob is LISTENING they perk up and the jaw drops (vanilla "creepy" pose). The veins glow through
 * one emissive layer (same cost as the vanilla enderman eyes).
 */
@Environment(EnvType.CLIENT)
public final class EchoStalkerArt {
	private EchoStalkerArt() {}

	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "art2/echo_stalker"), "main");
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/echo_stalker.png");
	static final Identifier EMISSIVE = Identifier.fromNamespaceAndPath("elsewhere", "textures/entity/echo_stalker_emissive.png");
	public static final float SCALE = 1.25F;

	public static LayerDefinition createBodyLayer() {
		return EndermanModel.createBodyLayer().apply(mesh -> {
			PartDefinition head = mesh.getRoot().getChild("head");
			head.addOrReplaceChild("right_tendril", CubeListBuilder.create()
					.texOffs(32, 0).addBox(-6.0F, -6.0F, 0.0F, 6.0F, 8.0F, 0.0F), PartPose.offset(-4.0F, -5.0F, 0.0F));
			head.addOrReplaceChild("left_tendril", CubeListBuilder.create().mirror()
					.texOffs(32, 0).addBox(0.0F, -6.0F, 0.0F, 6.0F, 8.0F, 0.0F), PartPose.offset(4.0F, -5.0F, 0.0F));
			return mesh;
		});
	}

	@Environment(EnvType.CLIENT)
	public static class Model extends EndermanModel<EndermanRenderState> {
		private final ModelPart rightTendril, leftTendril;

		public Model(ModelPart root) {
			super(root);
			this.rightTendril = head.getChild("right_tendril");
			this.leftTendril = head.getChild("left_tendril");
		}

		@Override
		public void setupAnim(EndermanRenderState s) {
			super.setupAnim(s);
			float sway = Mth.sin(s.ageInTicks * 0.1F) * 0.08F + Mth.cos(s.walkAnimationPos * 0.6662F) * s.walkAnimationSpeed * 0.2F;
			float perk = s.isCreepy ? 0.4F + Mth.sin(s.ageInTicks * 0.9F) * 0.06F : 0.0F;
			rightTendril.zRot = perk + sway;
			leftTendril.zRot = -(perk + sway);
		}
	}

	@Environment(EnvType.CLIENT)
	public static class Renderer extends MobRenderer<HushMobs.EchoStalker, EndermanRenderState, Model> {
		public Renderer(EntityRendererProvider.Context ctx) {
			super(ctx, new Model(ctx.bakeLayer(LAYER)), 0.5F * SCALE);
			this.addLayer(new LivingEntityEmissiveLayer<>(this, s -> EMISSIVE, (s, age) -> 1.0F, new Model(ctx.bakeLayer(LAYER)), RenderTypes::eyes, false));
		}

		@Override
		public EndermanRenderState createRenderState() {
			return new EndermanRenderState();
		}

		@Override
		public void extractRenderState(HushMobs.EchoStalker e, EndermanRenderState s, float partial) {
			super.extractRenderState(e, s, partial);
			s.isCreepy = e.getEntityData().get(HushMobs.EchoStalker.LISTENING) > 0;
		}

		@Override
		public Identifier getTextureLocation(EndermanRenderState state) {
			return TEXTURE;
		}

		@Override
		protected void scale(EndermanRenderState state, PoseStack pose) {
			pose.scale(SCALE, SCALE, SCALE);
		}
	}
}
