package dev.elsewhere.bossart.rimeback;

import dev.elsewhere.boss.rimeback.RimebackEntity;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * Rimeback, the Avalanche Colossus: a hunched colossus of packed ice and frosted granite (concept art boxes from
 * bosses_art.py rimeback()): ice chest and pelvis, granite legs and long knuckle-dragging arms with ice fists, a small
 * head with a frost jaw and glowing cyan eyes, a fan of five icicle spines on the back, rime armour plates (shoulders
 * and chest) that fly off in phase 3, and the packed-ice boulder it holds overhead during Glacier Hurl.
 * Rendered at x2.6 (about 7 blocks tall).
 */
public class RimebackModel extends EntityModel<RimebackRenderState> {
	private final ModelPart body, chest, head, rightArm, leftArm, boulder, rightPlate, leftPlate, chestPlate;
	private final KeyframeAnimation idle, walk, hurl, toss, slamUp, slamDown, breath, roar, stompUp, stompDown, phaseRoar, shatter, death;

	public RimebackModel(ModelPart root) {
		super(root);
		ModelPart r = root.getChild("root");
		this.body = r.getChild("body");
		this.chest = this.body.getChild("chest");
		this.head = this.chest.getChild("head");
		this.rightArm = this.chest.getChild("right_arm");
		this.leftArm = this.chest.getChild("left_arm");
		this.boulder = this.chest.getChild("boulder");
		this.rightPlate = this.rightArm.getChild("right_plate");
		this.leftPlate = this.leftArm.getChild("left_plate");
		this.chestPlate = this.chest.getChild("chest_plate");
		this.idle = RimebackAnimations.IDLE.bake(root);
		this.walk = RimebackAnimations.WALK.bake(root);
		this.hurl = RimebackAnimations.HURL.bake(root);
		this.toss = RimebackAnimations.THROW.bake(root);
		this.slamUp = RimebackAnimations.SLAM_UP.bake(root);
		this.slamDown = RimebackAnimations.SLAM_DOWN.bake(root);
		this.breath = RimebackAnimations.BREATH.bake(root);
		this.roar = RimebackAnimations.ROAR.bake(root);
		this.stompUp = RimebackAnimations.STOMP_UP.bake(root);
		this.stompDown = RimebackAnimations.STOMP_DOWN.bake(root);
		this.phaseRoar = RimebackAnimations.PHASE_ROAR.bake(root);
		this.shatter = RimebackAnimations.SHATTER.bake(root);
		this.death = RimebackAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(60, 36).addBox(-4.5F, 0, -4.5F, 9, 15, 9), PartPose.offset(-7.5F, -15, 0));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(60, 36).mirror().addBox(-4.5F, 0, -4.5F, 9, 15, 9), PartPose.offset(7.5F, -15, 0));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 36).addBox(-9, -4, -6, 18, 7, 12), PartPose.offset(0, -15, 0));
		PartDefinition chest = body.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 0).addBox(-13, -19, -8, 26, 19, 16),
			PartPose.offsetAndRotation(0, -3, 0, 0.12F, 0, 0));
		PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create()
				.texOffs(92, 0).addBox(-5.5F, -9, -6, 11, 10, 11)
				.texOffs(136, 0).addBox(-6, -6.5F, -7, 12, 2, 3)
				.texOffs(182, 0).addBox(-4.5F, -4.5F, -6.6F, 3, 2, 1)
				.texOffs(182, 0).addBox(1.5F, -4.5F, -6.6F, 3, 2, 1),
			PartPose.offsetAndRotation(0, -17, -5, -0.12F, 0, 0));
		head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(136, 6).addBox(-4.5F, 0, -6.5F, 9, 3, 7), PartPose.offset(0, 0, 0));
		float[][] spines = {{-10.5F, 11, -25}, {-5, 13, -10}, {0.5F, 14, 4}, {5.5F, 13, 17}, {10.5F, 11, 29}};
		for (int i = 0; i < spines.length; i++) {
			float[] s = spines[i];
			chest.addOrReplaceChild("spine" + i, CubeListBuilder.create().texOffs(168, 0).addBox(-1.5F, -s[1], -1.5F, 3, s[1], 3),
				PartPose.offsetAndRotation(s[0], -17, 4.5F, -0.55F, 0, (float) Math.toRadians(s[2])));
		}
		PartDefinition rightArm = chest.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(96, 36).addBox(-4, -2, -4, 8, 20, 8),
			PartPose.offsetAndRotation(-16.5F, -16, -1, -0.12F, 0, 0.08F));
		rightArm.addOrReplaceChild("right_fist", CubeListBuilder.create().texOffs(128, 36).addBox(-5, 0, -5, 10, 9, 10), PartPose.offset(0, 18, 0));
		rightArm.addOrReplaceChild("right_plate", CubeListBuilder.create().texOffs(168, 36).addBox(-6, -4, -6, 12, 5, 12), PartPose.offset(-0.5F, -1, 0));
		PartDefinition leftArm = chest.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(96, 36).mirror().addBox(-4, -2, -4, 8, 20, 8),
			PartPose.offsetAndRotation(16.5F, -16, -1, -0.12F, 0, -0.08F));
		leftArm.addOrReplaceChild("left_fist", CubeListBuilder.create().texOffs(128, 36).mirror().addBox(-5, 0, -5, 10, 9, 10), PartPose.offset(0, 18, 0));
		leftArm.addOrReplaceChild("left_plate", CubeListBuilder.create().texOffs(168, 36).mirror().addBox(-6, -4, -6, 12, 5, 12), PartPose.offset(0.5F, -1, 0));
		chest.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(168, 56).addBox(-11, -6, -1.5F, 22, 12, 2), PartPose.offset(0, -9, -8));
		chest.addOrReplaceChild("boulder", CubeListBuilder.create().texOffs(0, 72).addBox(-11, -7, -8, 22, 14, 16), PartPose.offset(0, -47, -4));
		return LayerDefinition.create(mesh, 256, 128);
	}

	@Override
	public void setupAnim(RimebackRenderState s) {
		super.setupAnim(s);
		boolean plates = !s.shattered || s.shatterMillis >= 0;
		this.rightPlate.visible = plates;
		this.leftPlate.visible = plates;
		this.chestPlate.visible = plates;
		this.boulder.visible = s.holding && s.deathTime <= 0;
		if (s.deathTime > 0) {
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		this.head.yRot += Math.max(-0.5F, Math.min(0.5F, s.yRot * ((float) Math.PI / 180F)));
		this.head.xRot += Math.max(-0.4F, Math.min(0.4F, s.xRot * ((float) Math.PI / 180F)));
		this.idle.apply((long) (s.ageInTicks * 50.0F), 1.0F);
		if (s.action == 0 && s.followMillis < 0 && s.transMillis < 0) this.walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.6F, 2.5F);
		if (s.transMillis >= 0) {
			(s.shatterMillis >= 0 ? this.shatter : this.phaseRoar).apply(s.transMillis, 1.0F);
			return;
		}
		switch (s.action) {
			case RimebackEntity.ACT_HURL -> this.hurl.apply(s.actionMillis, 1.0F);
			case RimebackEntity.ACT_THROW -> this.toss.apply(s.actionMillis, 1.0F);
			case RimebackEntity.ACT_SLAM -> this.slamUp.apply(s.actionMillis, 1.0F);
			case RimebackEntity.ACT_BREATH -> this.breath.apply(s.actionMillis, 1.0F);
			case RimebackEntity.ACT_ROAR -> this.roar.apply(s.actionMillis, 1.0F);
			case RimebackEntity.ACT_STOMP -> this.stompUp.apply(s.actionMillis, 1.0F);
			default -> {}
		}
		if (s.action == 0 && s.followMillis >= 0) {
			switch (s.follow) {
				case RimebackEntity.ACT_HURL -> this.toss.apply(s.followMillis, 1.0F);
				case RimebackEntity.ACT_SLAM -> this.slamDown.apply(s.followMillis, 1.0F);
				case RimebackEntity.ACT_STOMP -> this.stompDown.apply(s.followMillis, 1.0F);
				default -> {}
			}
		}
	}
}
