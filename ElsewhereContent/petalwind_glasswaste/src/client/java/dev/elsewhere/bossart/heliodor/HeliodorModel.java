package dev.elsewhere.bossart.heliodor;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Heliodor, the Sunwright Engine: a gold-plated sun golem from the concept boxes (bronze legs, gold torso with a sky-glass
 * chest lens, a sun-plate face with a burning core, an 8-ray halo, long gold arms ending in heavy fists). 1 art unit = 8 px,
 * so the head top sits at the 6-block hitbox height.
 */
public class HeliodorModel extends EntityModel<HeliodorRenderState> {
	private final ModelPart body, head, halo, rightArm, leftArm;
	private final KeyframeAnimation idle, walk, lance, slam, crack, vent, sweep, roar, death;

	public HeliodorModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		this.body = root.getChild("body");
		this.head = body.getChild("head");
		this.halo = head.getChild("halo");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.idle = HeliodorAnimations.IDLE.bake(root);
		this.walk = HeliodorAnimations.WALK.bake(root);
		this.lance = HeliodorAnimations.LANCE.bake(root);
		this.slam = HeliodorAnimations.SLAM.bake(root);
		this.crack = HeliodorAnimations.CRACK.bake(root);
		this.vent = HeliodorAnimations.VENT.bake(root);
		this.sweep = HeliodorAnimations.SWEEP.bake(root);
		this.roar = HeliodorAnimations.ROAR.bake(root);
		this.death = HeliodorAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-26F, -36F, -14F, 52F, 36F, 28F),
			PartPose.offset(0F, -4F, 0F));
		body.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(84, 116).addBox(-13F, -30F, -16F, 26F, 26F, 2F), PartPose.ZERO);
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 64).addBox(-18F, -28F, -12F, 36F, 28F, 24F),
			PartPose.offset(0F, -36F, -2F));
		PartDefinition face = head.addOrReplaceChild("face", CubeListBuilder.create().texOffs(140, 116).addBox(-13F, -13F, -2F, 26F, 26F, 2F),
			PartPose.offset(0F, -14F, -12F));
		face.addOrReplaceChild("pupil", CubeListBuilder.create().texOffs(224, 20).addBox(-5F, -5F, -1F, 10F, 10F, 1F), PartPose.offset(0F, 0F, -2.4F));
		PartDefinition halo = head.addOrReplaceChild("halo", CubeListBuilder.create(), PartPose.offset(0F, -14F, 4F));
		for (int i = 0; i < 8; i++) {
			halo.addOrReplaceChild("ray" + i, CubeListBuilder.create().texOffs(224, 0).addBox(-2F, -34F, -2F, 4F, 13F, 4F),
				PartPose.rotation(0F, 0F, i * Mth.PI / 4F));
		}
		PartDefinition rightArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(160, 0).addBox(-8F, -3F, -8F, 16F, 46F, 16F),
			PartPose.offset(-34F, -31F, 0F));
		rightArm.addOrReplaceChild("right_fist", CubeListBuilder.create().texOffs(0, 116).addBox(-10.5F, -4F, -10.5F, 21F, 16F, 21F),
			PartPose.offset(0F, 44F, 0F));
		PartDefinition leftArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(160, 0).mirror().addBox(-8F, -3F, -8F, 16F, 46F, 16F),
			PartPose.offset(34F, -31F, 0F));
		leftArm.addOrReplaceChild("left_fist", CubeListBuilder.create().texOffs(0, 116).mirror().addBox(-10.5F, -4F, -10.5F, 21F, 16F, 21F),
			PartPose.offset(0F, 44F, 0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(120, 64).addBox(-7F, 0F, -7F, 14F, 28F, 14F), PartPose.offset(-14F, -4F, 0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(120, 64).mirror().addBox(-7F, 0F, -7F, 14F, 28F, 14F),
			PartPose.offset(14F, -4F, 0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(HeliodorRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		if (s.deathTime > 0) {
			death.apply((long) (s.deathTime * 50F), 1.0F);
			halo.zRot = t * 0.01F;
			return;
		}
		head.yRot = Mth.clamp(s.yRot, -45F, 45F) * Mth.DEG_TO_RAD;
		head.xRot = Mth.clamp(s.xRot, -30F, 30F) * Mth.DEG_TO_RAD;
		// the halo turns like a sun-engine flywheel, faster as the fight heats up and while the lance charges
		float spin = s.stunned ? 0.004F : 0.025F + 0.015F * (s.phase - 1);
		halo.zRot = t * spin + (s.lance.isStarted() ? lanceSpin(s, t) : 0F);
		idle.apply(1L + (long) (t * 50F), 1.0F);
		walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 2.0F, 2.5F);
		lance.apply(s.lance, t);
		slam.apply(s.slam, t);
		crack.apply(s.crack, t);
		vent.apply(s.vent, t);
		sweep.apply(s.sweep, t);
		if (s.roarTime >= 0) roar.apply((long) (s.roarTime * 1000F), 1.0F);
	}

	private static float lanceSpin(HeliodorRenderState s, float t) {
		// spins up over the 2 s charge; ends on a whole number of turns so the halo never snaps
		float x = Math.min(1F, s.lance.getTimeInMillis(t) / 2200F);
		return x * x * 4F * Mth.PI;
	}
}
