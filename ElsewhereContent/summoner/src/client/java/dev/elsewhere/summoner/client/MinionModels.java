package dev.elsewhere.summoner.client;

import dev.elsewhere.summoner.SummonerContent;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;

/** The three minion models (vanilla-style cuboids; UV layouts match tools/gen_assets.py). */
public final class MinionModels {
	private MinionModels() {}

	public static final ModelLayerLocation ROOTLING = new ModelLayerLocation(SummonerContent.id("rootling"), "main");
	public static final ModelLayerLocation PAPER_KITE = new ModelLayerLocation(SummonerContent.id("paper_kite"), "main");
	public static final ModelLayerLocation BRASSBACK = new ModelLayerLocation(SummonerContent.id("brassback"), "main");

	/** Render state shared by the minions: the attack swing (0..1). */
	public static class State extends LivingEntityRenderState {
		public float attack;
		public boolean charging;
	}

	// ------------------------------------------------------------------ Rootling: knee-high bark sprite, moss tuft

	public static LayerDefinition rootling() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3F, -7F, -2.5F, 6F, 7F, 5F), PartPose.offset(0F, 21F, 0F));
		PartDefinition moss = body.addOrReplaceChild("moss", CubeListBuilder.create().texOffs(0, 20).addBox(-3.5F, -2F, -3F, 7F, 2F, 6F), PartPose.offset(0F, -7F, 0F));
		moss.addOrReplaceChild("sprig", CubeListBuilder.create().texOffs(26, 20).addBox(-0.5F, -3F, -0.5F, 1F, 3F, 1F), PartPose.offsetAndRotation(1F, -2F, 0F, 0F, 0F, 0.3F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 12).addBox(-1F, 0F, -1F, 2F, 3F, 2F), PartPose.offset(-1.5F, 21F, 0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(8, 12).addBox(-1F, 0F, -1F, 2F, 3F, 2F), PartPose.offset(1.5F, 21F, 0F));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(16, 12).addBox(-2F, 0F, -1F, 2F, 6F, 2F), PartPose.offset(-3F, -6F, 0F));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(24, 12).addBox(0F, 0F, -1F, 2F, 6F, 2F), PartPose.offset(3F, -6F, 0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	public static class RootlingModel extends EntityModel<State> {
		private final ModelPart body, rightLeg, leftLeg, rightArm, leftArm, moss;

		public RootlingModel(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			this.moss = body.getChild("moss");
			this.rightLeg = root.getChild("right_leg");
			this.leftLeg = root.getChild("left_leg");
			this.rightArm = body.getChild("right_arm");
			this.leftArm = body.getChild("left_arm");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float w = s.walkAnimationPos, ws = Math.min(1F, s.walkAnimationSpeed);
			rightLeg.xRot = Mth.cos(w * 0.9F) * 1.2F * ws;
			leftLeg.xRot = Mth.cos(w * 0.9F + Mth.PI) * 1.2F * ws;
			body.zRot = Mth.sin(w * 0.9F) * 0.12F * ws;
			float idle = Mth.sin(s.ageInTicks * 0.1F) * 0.06F;
			rightArm.zRot = 0.15F + idle;
			leftArm.zRot = -0.15F - idle;
			rightArm.xRot = Mth.cos(w * 0.9F + Mth.PI) * 0.8F * ws;
			leftArm.xRot = Mth.cos(w * 0.9F) * 0.8F * ws;
			if (s.attack > 0) {
				float a = Mth.sin(s.attack * Mth.PI);
				rightArm.xRot = -2.2F * a;
				leftArm.xRot = -2.2F * a;
				body.xRot = 0.3F * a;
			}
			moss.yRot = Mth.sin(s.ageInTicks * 0.05F) * 0.08F;
		}
	}

	// ------------------------------------------------------------------ Paper Kite: silk diamond with a ribbon tail

	public static LayerDefinition paperKite() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition kite = root.addOrReplaceChild("kite", CubeListBuilder.create(), PartPose.offset(0F, 12F, 0F));
		kite.addOrReplaceChild("sail", CubeListBuilder.create().texOffs(0, 0).addBox(-5F, -5F, -0.5F, 10F, 10F, 1F), PartPose.rotation(0F, 0F, Mth.PI / 4F));
		kite.addOrReplaceChild("spar", CubeListBuilder.create().texOffs(22, 11).addBox(-0.5F, -7F, -1F, 1F, 14F, 1F), PartPose.ZERO);
		PartDefinition t1 = kite.addOrReplaceChild("tail1", CubeListBuilder.create().texOffs(0, 12).addBox(-1F, 0F, 0F, 2F, 5F, 0F), PartPose.offset(0F, 7F, 0.2F));
		PartDefinition t2 = t1.addOrReplaceChild("tail2", CubeListBuilder.create().texOffs(4, 12).addBox(-1F, 0F, 0F, 2F, 5F, 0F), PartPose.offset(0F, 5F, 0F));
		t2.addOrReplaceChild("tail3", CubeListBuilder.create().texOffs(8, 12).addBox(-1F, 0F, 0F, 2F, 5F, 0F), PartPose.offset(0F, 5F, 0F));
		return LayerDefinition.create(mesh, 32, 32);
	}

	public static class PaperKiteModel extends EntityModel<State> {
		private final ModelPart kite, t1, t2, t3;

		public PaperKiteModel(ModelPart root) {
			super(root);
			this.kite = root.getChild("kite");
			this.t1 = kite.getChild("tail1");
			this.t2 = t1.getChild("tail2");
			this.t3 = t2.getChild("tail3");
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float t = s.ageInTicks;
			kite.xRot = -0.35F + Mth.sin(t * 0.08F) * 0.08F - (s.attack > 0 ? 0.4F * Mth.sin(s.attack * Mth.PI) : 0F);
			kite.zRot = Mth.sin(t * 0.06F) * 0.12F;
			kite.y = 12F + Mth.sin(t * 0.1F) * 0.8F;
			t1.xRot = 0.35F + Mth.sin(t * 0.25F) * 0.25F;
			t1.zRot = Mth.sin(t * 0.2F) * 0.3F;
			t2.zRot = Mth.sin(t * 0.2F - 0.8F) * 0.4F;
			t3.zRot = Mth.sin(t * 0.2F - 1.6F) * 0.5F;
		}
	}

	// ------------------------------------------------------------------ Brassback: riveted brass crab with a lamp eye

	public static LayerDefinition brassback() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition shell = root.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 0).addBox(-6F, -5F, -5F, 12F, 5F, 10F), PartPose.offset(0F, 20F, 0F));
		shell.addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(44, 0).addBox(-1F, -2F, -1F, 2F, 2F, 2F), PartPose.offset(0F, -5F, -3F));
		for (int i = 0; i < 6; i++) {
			boolean right = i < 3;
			float z = -3F + (i % 3) * 3F;
			root.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(i * 4, 16).addBox(-0.5F, 0F, -0.5F, 1F, 5F, 1F),
				PartPose.offsetAndRotation(right ? -5.5F : 5.5F, 19F, z, 0F, 0F, right ? 0.75F : -0.75F));
		}
		root.addOrReplaceChild("right_claw", CubeListBuilder.create().texOffs(24, 16).addBox(-1.5F, -1.5F, -4F, 3F, 3F, 4F), PartPose.offsetAndRotation(-4F, 18F, -5F, 0F, 0.3F, 0F));
		root.addOrReplaceChild("left_claw", CubeListBuilder.create().texOffs(38, 16).addBox(-1.5F, -1.5F, -4F, 3F, 3F, 4F), PartPose.offsetAndRotation(4F, 18F, -5F, 0F, -0.3F, 0F));
		return LayerDefinition.create(mesh, 64, 32);
	}

	public static class BrassbackModel extends EntityModel<State> {
		private final ModelPart shell, rightClaw, leftClaw;
		private final ModelPart[] legs = new ModelPart[6];

		public BrassbackModel(ModelPart root) {
			super(root);
			this.shell = root.getChild("shell");
			this.rightClaw = root.getChild("right_claw");
			this.leftClaw = root.getChild("left_claw");
			for (int i = 0; i < 6; i++) legs[i] = root.getChild("leg" + i);
		}

		@Override
		public void setupAnim(State s) {
			super.setupAnim(s);
			float w = s.walkAnimationPos, ws = Math.min(1F, s.walkAnimationSpeed);
			for (int i = 0; i < 6; i++) {
				boolean right = i < 3;
				float ph = (i % 2 == 0) ? 0F : Mth.PI;
				legs[i].zRot = (right ? 0.75F : -0.75F) + Mth.cos(w * 1.3F + ph) * 0.35F * ws;
				legs[i].yRot = Mth.sin(w * 1.3F + ph) * 0.3F * ws;
			}
			shell.y = 20F + Mth.sin(s.ageInTicks * 0.15F) * 0.15F;
			float pinch = s.attack > 0 ? Mth.sin(s.attack * Mth.PI) : 0F;
			rightClaw.xRot = -0.6F * pinch + Mth.sin(s.ageInTicks * 0.1F) * 0.05F;
			leftClaw.xRot = -0.6F * pinch - Mth.sin(s.ageInTicks * 0.1F) * 0.05F;
			rightClaw.yRot = 0.3F - 0.4F * pinch;
			leftClaw.yRot = -0.3F + 0.4F * pinch;
		}
	}
}
