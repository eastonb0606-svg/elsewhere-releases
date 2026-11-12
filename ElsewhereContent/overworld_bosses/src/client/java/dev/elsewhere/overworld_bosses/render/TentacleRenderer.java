package dev.elsewhere.overworld_bosses.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.boss.vorlagh.VorlaghTentacle;
import dev.elsewhere.stub.overworld_bosses.BossRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/**
 * Vorlagh's tentacle: a chain of four tapering segments that sways when idle, rears back while a slam winds up,
 * slams down, and curls toward a grabbed player. Severed tentacles render as a short stump.
 */
public class TentacleRenderer extends MobRenderer<VorlaghTentacle, TentacleRenderer.State, TentacleRenderer.Model> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(BossRegistry.id("vorlagh_tentacle"), "main");
	private static final Identifier TEXTURE = BossRegistry.id("textures/entity/vorlagh_tentacle.png");

	public TentacleRenderer(EntityRendererProvider.Context context) {
		super(context, new Model(context.bakeLayer(LAYER)), 0.8F);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition s0 = root.addOrReplaceChild("seg0", CubeListBuilder.create().texOffs(0, 0).addBox(-6, -32, -6, 12, 32, 12), PartPose.offset(0, 24, 0));
		PartDefinition s1 = s0.addOrReplaceChild("seg1", CubeListBuilder.create().texOffs(48, 0).addBox(-5, -32, -5, 10, 32, 10), PartPose.offset(0, -32, 0));
		PartDefinition s2 = s1.addOrReplaceChild("seg2", CubeListBuilder.create().texOffs(0, 44).addBox(-4, -32, -4, 8, 32, 8), PartPose.offset(0, -32, 0));
		s2.addOrReplaceChild("seg3", CubeListBuilder.create().texOffs(32, 44).addBox(-2.5F, -28, -2.5F, 5, 28, 5), PartPose.offset(0, -32, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURE;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(VorlaghTentacle entity, State state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.tentacleState = entity.state();
		state.severed = entity.isSevered();
		state.index = entity.index();
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(1.0F, 1.0F, 1.0F);
	}

	public static class State extends LivingEntityRenderState {
		int tentacleState;
		boolean severed;
		int index;
	}

	public static class Model extends EntityModel<State> {
		private final ModelPart s0, s1, s2, s3;

		public Model(ModelPart root) {
			super(root);
			this.s0 = root.getChild("seg0");
			this.s1 = this.s0.getChild("seg1");
			this.s2 = this.s1.getChild("seg2");
			this.s3 = this.s2.getChild("seg3");
		}

		@Override
		public void setupAnim(State state) {
			super.setupAnim(state);
			float t = state.ageInTicks * 0.08F + state.index * 1.3F;
			float sway = (float) Math.sin(t) * 0.12F;
			this.s1.visible = !state.severed;
			this.s2.visible = !state.severed;
			this.s3.visible = !state.severed;
			switch (state.tentacleState) {
				case VorlaghTentacle.STATE_RAISE -> {
					this.s0.xRot = 0.35F;
					this.s1.xRot = 0.3F;
					this.s2.xRot = 0.25F;
					this.s3.xRot = 0.2F;
				}
				case VorlaghTentacle.STATE_SLAM -> {
					this.s0.xRot = -1.35F;
					this.s1.xRot = -0.15F;
					this.s2.xRot = -0.05F;
					this.s3.xRot = 0.0F;
				}
				case VorlaghTentacle.STATE_GLOW, VorlaghTentacle.STATE_GRAB -> {
					this.s0.xRot = -0.5F;
					this.s1.xRot = -0.4F;
					this.s2.xRot = -0.5F;
					this.s3.xRot = -0.9F;
				}
				default -> {
					this.s0.xRot = sway;
					this.s1.xRot = sway * 1.4F + 0.1F;
					this.s2.xRot = sway * 1.8F + 0.15F;
					this.s3.xRot = sway * 2.2F + 0.25F;
				}
			}
			this.s0.zRot = (float) Math.cos(t * 0.7F) * 0.08F;
		}
	}
}
