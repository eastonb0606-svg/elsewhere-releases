package dev.elsewhere.bossart.galvanox;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * Galvanox, the Lighthouse Engine: a hulking oxidized-copper automaton with a blazing lens in its chest, a visor
 * slit, knuckle-walking copper fists and a lightning-rod spire with three spinning collar rings and a lamp on top.
 * Eight crusted patina plates (chest x2, back, pauldrons x2, shins x2, helm) disappear as players scrape them off.
 * Part tree generated from the hero boxes of {@code galvanox()} in Ideas/art/src/bosses_art.py.
 */
public class GalvanoxArtModel extends EntityModel<GalvanoxArtState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "bossart_galvanox"), "main");
	private static final float DEG = (float) (Math.PI / 180.0);

	private final ModelPart head;
	private final ModelPart ring0, ring1, ring2;
	private final ModelPart[] plates = new ModelPart[8];
	private final KeyframeAnimation idle, walk, hammer, bolt, wax, roar, death;

	public GalvanoxArtModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		ModelPart bone = root.getChild("bone");
		ModelPart body = bone.getChild("body");
		this.head = body.getChild("head");
		ModelPart spire = head.getChild("spire");
		this.ring0 = spire.getChild("ring0");
		this.ring1 = spire.getChild("ring1");
		this.ring2 = spire.getChild("ring2");
		this.plates[0] = body.getChild("plate0");
		this.plates[1] = body.getChild("plate1");
		this.plates[2] = body.getChild("plate2");
		this.plates[3] = body.getChild("right_arm").getChild("plate3");
		this.plates[4] = body.getChild("left_arm").getChild("plate4");
		this.plates[5] = bone.getChild("right_leg").getChild("plate5");
		this.plates[6] = bone.getChild("left_leg").getChild("plate6");
		this.plates[7] = head.getChild("plate7");
		this.idle = GalvanoxAnimations.IDLE.bake(root);
		this.walk = GalvanoxAnimations.WALK.bake(root);
		this.hammer = GalvanoxAnimations.HAMMER.bake(root);
		this.bolt = GalvanoxAnimations.BOLT.bake(root);
		this.wax = GalvanoxAnimations.WAX.bake(root);
		this.roar = GalvanoxAnimations.ROAR.bake(root);
		this.death = GalvanoxAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_bone = root.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));
		PartDefinition p_right_leg = p_bone.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(160, 0).addBox(-4F, 0F, -4.5F, 8F, 15F, 9F), PartPose.offset(-8F, -15F, 0F));
		PartDefinition p_plate5 = p_right_leg.addOrReplaceChild("plate5", CubeListBuilder.create().texOffs(188, 58).addBox(-3.5F, 2F, -5.5F, 7F, 8F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_left_leg = p_bone.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(194, 0).addBox(-4F, 0F, -4.5F, 8F, 15F, 9F), PartPose.offset(8F, -15F, 0F));
		PartDefinition p_plate6 = p_left_leg.addOrReplaceChild("plate6", CubeListBuilder.create().texOffs(204, 58).addBox(-3.5F, 2F, -5.5F, 7F, 8F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_body = p_bone.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-14F, -20.5F, -8F, 28F, 21F, 16F), PartPose.offset(0F, -14F, 0F));
		PartDefinition p_core = p_body.addOrReplaceChild("core", CubeListBuilder.create().texOffs(94, 58).addBox(-5F, -15.5F, -8.5F, 10F, 10F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_plate0 = p_body.addOrReplaceChild("plate0", CubeListBuilder.create().texOffs(156, 58).addBox(-13.5F, -20F, -9F, 7F, 9F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_plate1 = p_body.addOrReplaceChild("plate1", CubeListBuilder.create().texOffs(172, 58).addBox(6.5F, -20F, -9F, 7F, 9F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_plate2 = p_body.addOrReplaceChild("plate2", CubeListBuilder.create().texOffs(0, 58).addBox(-10F, -18F, 8F, 20F, 14F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 37).addBox(-6F, -8F, -6F, 12F, 9F, 12F), PartPose.offset(0F, -20F, -1.5F));
		PartDefinition p_visor = p_head.addOrReplaceChild("visor", CubeListBuilder.create().texOffs(32, 73).addBox(-4.5F, -5F, -6.5F, 9F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_plate7 = p_head.addOrReplaceChild("plate7", CubeListBuilder.create().texOffs(42, 58).addBox(-6.5F, -9F, -6.5F, 13F, 2F, 13F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_spire = p_head.addOrReplaceChild("spire", CubeListBuilder.create().texOffs(48, 37).addBox(-1F, -17F, -1F, 2F, 18F, 2F), PartPose.offset(0F, -8F, 0F));
		PartDefinition p_ring0 = p_spire.addOrReplaceChild("ring0", CubeListBuilder.create().texOffs(116, 58).addBox(-5F, -0.5F, -5F, 10F, 1F, 10F), PartPose.offset(0F, -3.5F, 0F));
		PartDefinition p_ring1 = p_spire.addOrReplaceChild("ring1", CubeListBuilder.create().texOffs(220, 58).addBox(-3.5F, -0.5F, -3.5F, 7F, 1F, 7F), PartPose.offset(0F, -8F, 0F));
		PartDefinition p_ring2 = p_spire.addOrReplaceChild("ring2", CubeListBuilder.create().texOffs(0, 73).addBox(-2.5F, -0.5F, -2.5F, 5F, 1F, 5F), PartPose.offset(0F, -12.5F, 0F));
		PartDefinition p_lamp = p_spire.addOrReplaceChild("lamp", CubeListBuilder.create().texOffs(20, 73).addBox(-1.5F, -1.5F, -1.5F, 3F, 3F, 3F), PartPose.offset(0F, -17.5F, 0F));
		PartDefinition p_right_arm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(88, 0).addBox(-4.5F, -1F, -4.5F, 9F, 24F, 9F).texOffs(56, 37).addBox(-5.5F, 22.5F, -5.5F, 11F, 9F, 11F), PartPose.offset(-18.5F, -17F, 0F));
		PartDefinition p_plate3 = p_right_arm.addOrReplaceChild("plate3", CubeListBuilder.create().texOffs(144, 37).addBox(-5.5F, -2.5F, -5.5F, 11F, 6F, 11F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_left_arm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(124, 0).addBox(-4.5F, -1F, -4.5F, 9F, 24F, 9F).texOffs(100, 37).addBox(-5.5F, 22.5F, -5.5F, 11F, 9F, 11F), PartPose.offset(18.5F, -17F, 0F));
		PartDefinition p_plate4 = p_left_arm.addOrReplaceChild("plate4", CubeListBuilder.create().texOffs(188, 37).addBox(-5.5F, -2.5F, -5.5F, 11F, 6F, 11F), PartPose.offset(0F, 0F, 0F));
		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(GalvanoxArtState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		if (s.deathTime <= 0) {
			this.head.yRot += s.yRot * DEG * 0.8F;
			this.head.xRot += s.xRot * DEG * 0.5F;
		}
		// The spire's collar rings turn like a lighthouse lens; faster with every phase and while charging a bolt.
		float spin = s.deathTime > 0 ? 0.02F : (1 + s.phase) * (s.attack == 5 ? 3 : 1);
		this.ring0.yRot += age * 0.05F * spin;
		this.ring1.yRot -= age * 0.08F * spin;
		this.ring2.yRot += age * 0.12F * spin;
		this.idle.apply((long) (age * 50.0F), 1.0F);
		this.walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.4F, 2.5F);
		this.hammer.apply(s.hammer, age);
		this.bolt.apply(s.bolt, age);
		this.wax.apply(s.wax, age);
		this.roar.apply(s.roar, age);
		for (int i = 0; i < this.plates.length; i++) this.plates[i].visible = i < s.plates;
		if (s.stunned) {
			this.head.xRot += 0.5F;
			this.head.zRot += 0.15F * (float) Math.sin(age * 0.4F);
		}
		if (s.deathTime > 0) this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
	}
}
