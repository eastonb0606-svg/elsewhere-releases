package dev.elsewhere.bossart.verdigrim;

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
 * Archdeacon Verdigrim, the Drowned Organist: a towering drowned priest in navy-and-gold vestments, a verdigris
 * copper mitre with a glowing seam, glowing eyes, and a copper trident-crozier. Part tree generated from the hero
 * boxes of {@code verdigrim()} in Ideas/art/src/bosses_art.py (see scratch art/gen_verdigris.py).
 */
public class VerdigrimArtModel extends EntityModel<VerdigrimArtState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("elsewhere", "bossart_verdigrim"), "main");
	private static final float DEG = (float) (Math.PI / 180.0);

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final KeyframeAnimation idle, walk, slam, note, flood, arpeggio, chord, roar, death;

	public VerdigrimArtModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		ModelPart bone = root.getChild("bone");
		this.body = bone.getChild("body");
		this.head = body.getChild("head");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.idle = VerdigrimAnimations.IDLE.bake(root);
		this.walk = VerdigrimAnimations.WALK.bake(root);
		this.slam = VerdigrimAnimations.SLAM.bake(root);
		this.note = VerdigrimAnimations.NOTE.bake(root);
		this.flood = VerdigrimAnimations.FLOOD.bake(root);
		this.arpeggio = VerdigrimAnimations.ARPEGGIO.bake(root);
		this.chord = VerdigrimAnimations.CHORD.bake(root);
		this.roar = VerdigrimAnimations.ROAR.bake(root);
		this.death = VerdigrimAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition p_bone = root.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(0F, 24F, 0F));
		PartDefinition p_right_leg = p_bone.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(4, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offset(-2F, -12F, 0F));
		PartDefinition p_left_leg = p_bone.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(20, 0).addBox(-2F, 0F, -2F, 4F, 12F, 4F), PartPose.offset(2F, -12F, 0F));
		PartDefinition p_body = p_bone.addOrReplaceChild("body", CubeListBuilder.create().texOffs(36, 0).addBox(-4F, -12F, -2F, 8F, 12F, 4F), PartPose.offset(0F, -12F, 0F));
		PartDefinition p_robe = p_body.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(60, 0).addBox(-6F, -3F, -3F, 12F, 10F, 6F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_stole = p_body.addOrReplaceChild("stole", CubeListBuilder.create().texOffs(58, 31).addBox(-1F, -11F, -3.5F, 2F, 10F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_head = p_body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(96, 0).addBox(-4F, -8F, -4F, 8F, 8F, 8F), PartPose.offset(0F, -12F, 0F));
		PartDefinition p_mitre = p_head.addOrReplaceChild("mitre", CubeListBuilder.create().texOffs(32, 31).addBox(-3.5F, -16.5F, -2.5F, 7F, 9F, 6F).texOffs(64, 31).addBox(-1.5F, -18.5F, -2F, 3F, 3F, 4F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_mitre_glow = p_head.addOrReplaceChild("mitre_glow", CubeListBuilder.create().texOffs(78, 31).addBox(-1F, -15F, -3F, 2F, 6F, 1F), PartPose.offset(0F, 0F, 0F));
		PartDefinition p_right_arm = p_body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(0, 31).addBox(-2F, -2F, -2F, 4F, 12F, 4F), PartPose.offsetAndRotation(-6F, -10F, 0F, -0.35F, 0F, 0F));
		PartDefinition p_staff = p_right_arm.addOrReplaceChild("staff", CubeListBuilder.create().texOffs(0, 0).addBox(-0.5F, -19F, -0.5F, 1F, 30F, 1F).texOffs(96, 31).addBox(-3.5F, -17F, -0.5F, 7F, 1F, 1F).texOffs(84, 31).addBox(-3.5F, -21.5F, -0.5F, 1F, 5F, 1F).texOffs(92, 31).addBox(-0.5F, -22.5F, -0.5F, 1F, 4F, 1F).texOffs(88, 31).addBox(2.5F, -21.5F, -0.5F, 1F, 5F, 1F), PartPose.offsetAndRotation(0F, 9F, -0.5F, 0.35F, 0F, 0F));
		PartDefinition p_left_arm = p_body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(16, 31).addBox(-2F, -2F, -2F, 4F, 12F, 4F), PartPose.offset(6F, -10F, 0F));
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(VerdigrimArtState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		if (s.deathTime <= 0) {
			this.head.yRot += s.yRot * DEG;
			this.head.xRot += s.xRot * DEG;
		}
		this.idle.apply((long) (age * 50.0F), 1.0F);
		this.walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.6F, 2.5F);
		this.slam.apply(s.slam, age);
		this.note.apply(s.note, age);
		this.flood.apply(s.flood, age);
		this.arpeggio.apply(s.arpeggio, age);
		this.chord.apply(s.chord, age);
		this.roar.apply(s.roar, age);
		if (s.stunned) {
			// Stunned by the chord: head bowed, arms hanging limp, swaying.
			this.head.xRot += 0.7F;
			this.body.xRot += 0.25F + 0.05F * (float) Math.sin(age * 0.3F);
			this.rightArm.xRot += 0.35F;
			this.leftArm.xRot = 0.1F;
		}
		if (s.deathTime > 0) this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
	}
}
