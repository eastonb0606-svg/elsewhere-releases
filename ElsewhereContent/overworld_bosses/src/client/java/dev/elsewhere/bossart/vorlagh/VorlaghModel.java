package dev.elsewhere.bossart.vorlagh;

import dev.elsewhere.boss.vorlagh.VorlaghEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Vorlagh's body, the Tidewrack Kraken (concept art boxes from bosses_art.py vorlagh()): a broad purple head with two
 * yellow lantern eyes (black slit pupils) under heavy brows, a dark hooked beak with a hinged lower jaw, a tall
 * four-stage mantle swept back with two side fins, and six short feeding arms around the beak (the five big
 * tentacles are separate entities). Rendered x3 so the eyes sit about 2 blocks above the Wreck Ring deck.
 */
public class VorlaghModel extends EntityModel<VorlaghRenderState> {
	private final ModelPart head;
	private final KeyframeAnimation idle, inkCharge, inkJet, whirl, bite, phaseRoar, death;

	public VorlaghModel(ModelPart root) {
		super(root);
		this.head = root.getChild("root").getChild("body").getChild("head");
		this.idle = VorlaghAnimations.IDLE.bake(root);
		this.inkCharge = VorlaghAnimations.INK_CHARGE.bake(root);
		this.inkJet = VorlaghAnimations.INK_JET.bake(root);
		this.whirl = VorlaghAnimations.WHIRL.bake(root);
		this.bite = VorlaghAnimations.BITE.bake(root);
		this.phaseRoar = VorlaghAnimations.PHASE_ROAR.bake(root);
		this.death = VorlaghAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0, -25, 0));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(0, 0).addBox(-13, -27, -12, 26, 27, 24)
				.texOffs(0, 52).addBox(3, -16, -12.6F, 7, 7, 1)
				.texOffs(0, 52).mirror().addBox(-10, -16, -12.6F, 7, 7, 1),
			PartPose.ZERO);
		head.addOrReplaceChild("brow_l", CubeListBuilder.create().texOffs(16, 52).addBox(-4.5F, -1.5F, -1.5F, 9, 3, 3),
			PartPose.offsetAndRotation(6.5F, -17.5F, -12.5F, 0, 0, -0.3F));
		head.addOrReplaceChild("brow_r", CubeListBuilder.create().texOffs(16, 52).mirror().addBox(-4.5F, -1.5F, -1.5F, 9, 3, 3),
			PartPose.offsetAndRotation(-6.5F, -17.5F, -12.5F, 0, 0, 0.3F));
		PartDefinition beak = head.addOrReplaceChild("beak", CubeListBuilder.create().texOffs(40, 52).addBox(-4, -3, -3, 8, 5, 6),
			PartPose.offset(0, 0, -12));
		beak.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(68, 52).addBox(-3.5F, 0, -2.5F, 7, 4, 5), PartPose.offset(0, 2, 0.5F));
		PartDefinition m1 = head.addOrReplaceChild("mantle1", CubeListBuilder.create().texOffs(100, 0).addBox(-12, -12, -10.5F, 24, 12, 21),
			PartPose.offsetAndRotation(0, -27, 1, -0.1F, 0, 0));
		PartDefinition m2 = m1.addOrReplaceChild("mantle2", CubeListBuilder.create().texOffs(100, 34).addBox(-10, -10, -8.5F, 20, 10, 17),
			PartPose.offsetAndRotation(0, -12, 0.5F, -0.15F, 0, 0));
		m2.addOrReplaceChild("fin_l", CubeListBuilder.create().texOffs(224, 22).addBox(0, -5, -1, 7, 10, 2),
			PartPose.offsetAndRotation(10, -5, 2, 0, 0, -0.35F));
		m2.addOrReplaceChild("fin_r", CubeListBuilder.create().texOffs(224, 22).mirror().addBox(-7, -5, -1, 7, 10, 2),
			PartPose.offsetAndRotation(-10, -5, 2, 0, 0, 0.35F));
		PartDefinition m3 = m2.addOrReplaceChild("mantle3", CubeListBuilder.create().texOffs(190, 0).addBox(-7, -8, -6.5F, 14, 8, 13),
			PartPose.offsetAndRotation(0, -10, 0.5F, -0.15F, 0, 0));
		m3.addOrReplaceChild("mantle4", CubeListBuilder.create().texOffs(190, 22).addBox(-4, -5, -4, 8, 5, 8),
			PartPose.offsetAndRotation(0, -8, 0.5F, -0.2F, 0, 0));
		float[][] arms = {{-8, -9}, {8, -9}, {-12, 0}, {12, 0}, {-7, 9}, {7, 9}};
		for (int i = 0; i < arms.length; i++) {
			float x = arms[i][0], z = arms[i][1];
			PartDefinition a0 = body.addOrReplaceChild("arm" + i, CubeListBuilder.create().texOffs(0, 64).addBox(-2.5F, 0, -2.5F, 5, 8, 5),
				PartPose.offsetAndRotation(x, -1, z, Math.signum(z) * 0.3F, 0, -Math.signum(x) * 0.3F));
			PartDefinition a1 = a0.addOrReplaceChild("arm" + i + "_1", CubeListBuilder.create().texOffs(20, 64).addBox(-2, 0, -2, 4, 8, 4),
				PartPose.offset(0, 7.5F, 0));
			a1.addOrReplaceChild("arm" + i + "_2", CubeListBuilder.create().texOffs(36, 64).addBox(-1.5F, 0, -1.5F, 3, 7, 3), PartPose.offset(0, 7.5F, 0));
		}
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	public void setupAnim(VorlaghRenderState s) {
		super.setupAnim(s);
		if (s.deathTime > 0) {
			this.idle.apply((long) (s.ageInTicks * 25.0F), 0.4F);
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		this.head.yRot += Math.max(-0.45F, Math.min(0.45F, s.yRot * ((float) Math.PI / 180F)));
		this.idle.apply((long) (s.ageInTicks * 50.0F), 1.0F);
		if (s.transMillis >= 0) {
			this.phaseRoar.apply(s.transMillis, 1.0F);
			return;
		}
		switch (s.action) {
			case VorlaghEntity.ACT_INK -> this.inkCharge.apply(s.actionMillis, 1.0F);
			case VorlaghEntity.ACT_ROAR -> this.whirl.apply(s.actionMillis, 1.0F);
			case VorlaghEntity.ACT_BITE -> this.bite.apply(s.actionMillis, 1.0F);
			default -> {}
		}
		if (s.action == 0 && s.followMillis >= 0 && s.follow == VorlaghEntity.ACT_INK) this.inkJet.apply(s.followMillis, 1.0F);
	}
}
