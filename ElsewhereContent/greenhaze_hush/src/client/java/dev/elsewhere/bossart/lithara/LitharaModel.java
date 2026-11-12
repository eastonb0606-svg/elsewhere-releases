package dev.elsewhere.bossart.lithara;

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
 * Lithara, the Geode Cantor: a robed humanoid from the concept art (robe 11x12x6 over the legs, body 8x12x4, stone-grey
 * head with glowing eyes and a face plate), five amethyst spires fanning out of her back, glowing crystals held in both
 * hands, and an amethyst mantle (chest plate + pauldrons) that is her crystal armor and vanishes when it shatters.
 */
public class LitharaModel extends EntityModel<LitharaRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "boss/lithara"), "main");
	private static final float D = (float) (Math.PI / 180.0);
	/** Back spires from the art: x, fan angle, height. */
	static final float[][] SPIRES = {{-6, -35, 12}, {-3, -15, 15}, {3, 15, 15}, {6, 35, 12}, {0, 0, 18}};

	private final ModelPart head, chestPlate, rightPauldron, leftPauldron;
	private final ModelPart[] spires = new ModelPart[5];
	private final KeyframeAnimation idle, sing, conduct, shattered, psalm, rain, shards, vex, roar, death;

	public LitharaModel(ModelPart root) {
		super(root);
		ModelPart body = root.getChild("bone").getChild("body");
		this.head = body.getChild("head");
		this.chestPlate = body.getChild("chest_plate");
		this.rightPauldron = body.getChild("right_arm").getChild("right_pauldron");
		this.leftPauldron = body.getChild("left_arm").getChild("left_pauldron");
		for (int i = 0; i < 5; i++) spires[i] = body.getChild("crystal_" + i);
		this.idle = LitharaAnimations.IDLE.bake(root);
		this.sing = LitharaAnimations.SING.bake(root);
		this.conduct = LitharaAnimations.CONDUCT.bake(root);
		this.shattered = LitharaAnimations.SHATTERED.bake(root);
		this.psalm = LitharaAnimations.PSALM.bake(root);
		this.rain = LitharaAnimations.RAIN.bake(root);
		this.shards = LitharaAnimations.SHARDS.bake(root);
		this.vex = LitharaAnimations.VEX.bake(root);
		this.roar = LitharaAnimations.ROAR.bake(root);
		this.death = LitharaAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition bone = root.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));
		bone.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 0).addBox(-5.5F, 0F, -3F, 11F, 12F, 6F), PartPose.offset(0F, -12F, 0F));
		PartDefinition body = bone.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 20).addBox(-4F, -12F, -2F, 8F, 12F, 4F),
			PartPose.offset(0F, -12F, 0F));
		body.addOrReplaceChild("chest_plate", CubeListBuilder.create().texOffs(40, 20).addBox(-4.5F, -11.5F, -2.8F, 9F, 6F, 1F), PartPose.ZERO);
		PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 38).addBox(-4F, -8F, -4F, 8F, 8F, 8F)
			.texOffs(32, 38).addBox(-1.5F, -5.5F, -4.4F, 3F, 2F, 1F), PartPose.offset(0F, -12F, 0F));
		PartDefinition rArm = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(48, 0).addBox(-2F, -2F, -2F, 4F, 12F, 4F),
			PartPose.offsetAndRotation(-6F, -10F, 0F, -20F * D, 0F, 15F * D));
		PartDefinition lArm = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(64, 0).addBox(-2F, -2F, -2F, 4F, 12F, 4F),
			PartPose.offsetAndRotation(6F, -10F, 0F, -20F * D, 0F, -15F * D));
		rArm.addOrReplaceChild("right_pauldron", CubeListBuilder.create().texOffs(60, 20).addBox(-2.5F, -3F, -2.5F, 5F, 3F, 5F), PartPose.ZERO);
		lArm.addOrReplaceChild("left_pauldron", CubeListBuilder.create().texOffs(60, 30).addBox(-2.5F, -3F, -2.5F, 5F, 3F, 5F), PartPose.ZERO);
		rArm.addOrReplaceChild("right_hand", CubeListBuilder.create().texOffs(80, 0).addBox(-1.5F, -1.5F, -1.5F, 3F, 3F, 3F),
			PartPose.offsetAndRotation(0F, 11.5F, 0F, 45F * D, 45F * D, 0F));
		lArm.addOrReplaceChild("left_hand", CubeListBuilder.create().texOffs(80, 8).addBox(-1.5F, -1.5F, -1.5F, 3F, 3F, 3F),
			PartPose.offsetAndRotation(0F, 11.5F, 0F, 45F * D, 45F * D, 0F));
		for (int i = 0; i < 5; i++) {
			float[] sp = SPIRES[i];
			body.addOrReplaceChild("crystal_" + i, CubeListBuilder.create().texOffs(i * 12, 60).addBox(-1.5F, -sp[2], -1.5F, 3F, sp[2], 3F),
				PartPose.offsetAndRotation(sp[0], -11F, 2.4F, -25F * D, 0F, sp[1] * D));
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(LitharaRenderState s) {
		super.setupAnim(s);
		chestPlate.visible = s.armored;
		rightPauldron.visible = s.armored;
		leftPauldron.visible = s.armored;
		if (s.deathTime > 0) {
			this.death.apply((long) (s.deathTime * 50F), 1F);
			return;
		}
		this.head.yRot += s.yRot * (float) (Math.PI / 180.0) * 0.6F;
		this.head.xRot += s.xRot * (float) (Math.PI / 180.0) * 0.5F;
		this.idle.apply(s.idleState, s.ageInTicks);
		this.sing.apply(s.singState, s.ageInTicks);
		this.conduct.apply(s.conductState, s.ageInTicks);
		this.shattered.apply(s.shatteredState, s.ageInTicks);
		this.psalm.apply(s.psalmState, s.ageInTicks);
		this.rain.apply(s.rainState, s.ageInTicks);
		this.shards.apply(s.shardsState, s.ageInTicks);
		this.vex.apply(s.vexState, s.ageInTicks);
		this.roar.apply(s.roarState, s.ageInTicks);
		if (!s.armored) for (ModelPart p : spires) p.yScale *= 0.55F; // the crown is broken while her armor is down
	}
}
