package dev.elsewhere.bossart.auris;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;

/**
 * Auris, the Listening Engine: the concept art's humanoid (body 14x12x7, head 12x10x8, arms 5x13x5, legs) in deep
 * sculk teal, with two tall tympanum ear-fins (glowing inner membranes), a glowing chest core and three bone studs.
 * Built in the art's pixel units; the renderer scales it x2 to the 2.6 x 5.0 hitbox.
 */
public class AurisModel extends EntityModel<AurisRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/auris"), "main");
	private static final float D = (float) (Math.PI / 180.0);

	private final ModelPart head;
	private final KeyframeAnimation idle, walk, listen, charge, smash, deaf, roar, death;

	public AurisModel(ModelPart root) {
		super(root);
		this.head = root.getChild("bone").getChild("body").getChild("head");
		this.idle = AurisAnimations.IDLE.bake(root);
		this.walk = AurisAnimations.WALK.bake(root);
		this.listen = AurisAnimations.LISTEN.bake(root);
		this.charge = AurisAnimations.SONIC_CHARGE.bake(root);
		this.smash = AurisAnimations.SMASH.bake(root);
		this.deaf = AurisAnimations.DEAF.bake(root);
		this.roar = AurisAnimations.ROAR.bake(root);
		this.death = AurisAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));
		bone.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 40).addBox(-2.5F, 0F, -2.5F, 5F, 12F, 5F), PartPose.offset(-3F, -12F, 0F));
		bone.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(20, 40).addBox(-2.5F, 0F, -2.5F, 5F, 12F, 5F), PartPose.offset(3F, -12F, 0F));
		PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7F, -12F, -3.5F, 14F, 12F, 7F)
			// three bone studs low on the chest
			.texOffs(16, 60).addBox(-5F, -3F, -4.2F, 2F, 2F, 1F)
			.texOffs(16, 60).addBox(-1F, -3F, -4.2F, 2F, 2F, 1F)
			.texOffs(16, 60).addBox(3F, -3F, -4.2F, 2F, 2F, 1F), PartPose.offset(0F, -12F, 0F));
		// glowing chest core (scaled by the animations around its centre)
		body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(0, 60).addBox(-3F, -2.5F, -0.5F, 6F, 5F, 1F), PartPose.offset(0F, -7F, -3.7F));
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 20).addBox(-6F, -10F, -4F, 12F, 10F, 8F),
			PartPose.offset(0F, -12F, 0F));
		PartDefinition rEar = head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(44, 0).addBox(-1F, -10F, -3F, 2F, 10F, 6F)
			.texOffs(76, 0).addBox(-1.6F, -8.5F, -2F, 1F, 7F, 4F), PartPose.offsetAndRotation(-5.5F, -8F, 0F, 0F, 0F, -22F * D));
		PartDefinition lEar = head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(60, 0).addBox(-1F, -10F, -3F, 2F, 10F, 6F)
			.texOffs(88, 0).addBox(0.6F, -8.5F, -2F, 1F, 7F, 4F), PartPose.offsetAndRotation(5.5F, -8F, 0F, 0F, 0F, 22F * D));
		body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 40).addBox(-2.5F, -2F, -2.5F, 5F, 13F, 5F),
			PartPose.offsetAndRotation(-9.5F, -10F, 0F, -20F * D, 0F, 35F * D));
		body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(60, 40).addBox(-2.5F, -2F, -2.5F, 5F, 13F, 5F),
			PartPose.offsetAndRotation(9.5F, -10F, 0F, -20F * D, 0F, -35F * D));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(AurisRenderState s) {
		super.setupAnim(s);
		if (s.deathTime > 0) {
			this.death.apply((long) (s.deathTime * 50F), 1F);
			return;
		}
		this.head.yRot += s.yRot * D * 0.6F;
		this.head.xRot += s.xRot * D * 0.5F;
		this.idle.apply(s.idleState, s.ageInTicks);
		if (!s.deaf) this.walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 2.0F, 2.5F);
		this.listen.apply(s.listenState, s.ageInTicks);
		this.charge.apply(s.chargeState, s.ageInTicks);
		this.smash.apply(s.smashState, s.ageInTicks);
		this.deaf.apply(s.deafState, s.ageInTicks);
		this.roar.apply(s.roarState, s.ageInTicks);
	}
}
