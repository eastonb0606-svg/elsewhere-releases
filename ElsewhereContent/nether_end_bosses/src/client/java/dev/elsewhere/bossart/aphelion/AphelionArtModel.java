package dev.elsewhere.bossart.aphelion;

import static dev.elsewhere.bossart.nether.ArtAnim.def;
import static dev.elsewhere.bossart.nether.ArtAnim.pos;
import static dev.elsewhere.bossart.nether.ArtAnim.rot;
import static dev.elsewhere.bossart.nether.ArtAnim.scale;

import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Aphelion's head from the concept art (aphelion() in Ideas/art/src/bosses_art.py): an obsidian dragon skull with a long
 * snout, an unhinging jaw lined with teeth, an amethyst crest, two swept-back amethyst horns and glowing magenta eyes.
 * Keyframe animations: idle (jaw breathing, crest sway), bite / void breath / devour dive / collapse / crystal feast /
 * voidborn / coil wind-ups timed to the AttackSpec wind-ups, the phase roar and a slack-jawed death.
 */
public class AphelionArtModel extends EntityModel<AphelionArtModel.State> {
	public static class State extends dev.elsewhere.bossart.nether.HurtAnim.State {
		public final float[] seg = new float[(dev.elsewhere.boss.aphelion.Aphelion.SEGMENTS - 1) * 6];
		public boolean coiling;
		public boolean diving;
	}

	static final AnimationDefinition IDLE = def(3.0F, true)
		.addAnimation("jaw", rot(0, 6, 0, 0, 1.5F, 12, 0, 0, 3.0F, 6, 0, 0))
		.addAnimation("head", rot(0, 0, -4, 0, 1.5F, 0, 4, 0, 3.0F, 0, -4, 0))
		.addAnimation("crest", rot(0, 0, 0, -4, 1.5F, 0, 0, 4, 3.0F, 0, 0, -4)).build();
	/** Bite: 1 s wind-up (rear back, jaw wide), snap at 1.0 s. */
	static final AnimationDefinition BITE = def(1.7F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.9F, -28, 0, 0, 1.05F, 18, 0, 0, 1.7F, 0, 0, 0))
		.addAnimation("head", pos(0, 0, 0, 0, 0.9F, 0, -2, 6, 1.05F, 0, 1, -8, 1.7F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.9F, 55, 0, 0, 1.0F, 0, 0, 0, 1.7F, 6, 0, 0)).build();
	/** Void Breath: 1.5 s wind-up (head rears, jaw slowly opens), then the jaw holds open while it breathes. */
	static final AnimationDefinition VOID_BREATH = def(5.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 1.4F, -38, 0, 0, 1.7F, 12, 0, 0, 4.4F, 16, 0, 0, 5.0F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 1.4F, 35, 0, 0, 1.6F, 60, 0, 0, 4.4F, 58, 0, 0, 5.0F, 6, 0, 0))
		.addAnimation("crest", scale(0, 1, 1, 1, 1.5, 1, 1.4, 1.2, 4.4, 1, 1.4, 1.2, 5.0, 1, 1, 1)).build();
	/** Devour Dive: 2 s wind-up (head tucks, jaw yawns open), then the dive with the mouth gaping. */
	static final AnimationDefinition DEVOUR = def(5.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 1.0F, -20, 0, 0, 2.0F, 30, 0, 0, 4.5F, 30, 0, 0, 5.0F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 1.8F, 70, 0, 0, 4.5F, 70, 0, 0, 5.0F, 0, 0, 0)).build();
	/** Collapse: 3 s wind-up, the head shudders and the horns/crest flare, then the jaw cracks open as the halo falls. */
	static final AnimationDefinition COLLAPSE = def(4.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.5F, -10, 3, 0, 0.7F, -10, -3, 0, 0.9F, -14, 3, 0, 1.1F, -14, -3, 0, 1.3F, -18, 3, 0, 1.5F, -18, -3, 0,
			1.7F, -22, 3, 0, 1.9F, -22, -3, 0, 2.1F, -26, 3, 0, 2.3F, -26, -3, 0, 2.5F, -30, 3, 0, 2.7F, -30, -3, 0, 3.0F, 20, 0, 0, 4.0F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 2.8F, 20, 0, 0, 3.0F, 75, 0, 0, 3.6F, 60, 0, 0, 4.0F, 0, 0, 0))
		.addAnimation("horn_l", rot(0, 0, 0, 0, 2.8F, -25, 0, -10, 4.0F, 0, 0, 0))
		.addAnimation("horn_r", rot(0, 0, 0, 0, 2.8F, -25, 0, 10, 4.0F, 0, 0, 0))
		.addAnimation("crest", scale(0, 1, 1, 1, 2.8, 1.2, 1.7, 1.3, 4.0, 1, 1, 1)).build();
	/** Crystal Feast: a quick lunge, then chewing. */
	static final AnimationDefinition FEAST = def(3.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.5F, 20, 0, 0, 3.0F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.45F, 60, 0, 0, 0.6F, 5, 0, 0, 0.9F, 40, 0, 0, 1.2F, 5, 0, 0, 1.5F, 40, 0, 0, 1.8F, 5, 0, 0,
			2.1F, 40, 0, 0, 2.4F, 5, 0, 0, 3.0F, 6, 0, 0)).build();
	/** Voidborn and Coil: a short hiss with the head cocked. */
	static final AnimationDefinition HISS = def(1.6F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.8F, -15, 0, 18, 1.6F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.6F, 40, 0, 0, 1.0F, 40, 0, 0, 1.6F, 6, 0, 0)).build();
	/** Phase roar: rears back, jaw fully unhinged, head shakes. */
	static final AnimationDefinition ROAR = def(2.5F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -45, 0, 0, 0.6F, -45, 8, 0, 0.8F, -45, -8, 0, 1.0F, -45, 8, 0, 1.2F, -45, -8, 0,
			1.6F, -40, 0, 0, 2.5F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.4F, 80, 0, 0, 1.8F, 75, 0, 0, 2.5F, 6, 0, 0))
		.addAnimation("horn_l", rot(0, 0, 0, 0, 0.4F, -20, 0, -12, 2.0F, -20, 0, -12, 2.5F, 0, 0, 0))
		.addAnimation("horn_r", rot(0, 0, 0, 0, 0.4F, -20, 0, 12, 2.0F, -20, 0, 12, 2.5F, 0, 0, 0)).build();
	/** Death: a last silent scream, then the jaw goes slack and the head droops. */
	static final AnimationDefinition DEATH = def(1.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.3F, -35, 0, 0, 1.0F, 55, 0, 25))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.3F, 85, 0, 0, 1.0F, 50, 0, 0)).build();

	private final ModelPart head;
	private final ModelPart jaw;
	private final KeyframeAnimation idle, bite, breath, devour, collapse, feast, hiss, roar, death;

	/** 3.0 (P8): hurt flinch, the head rears and the jaw gapes. */
	static final AnimationDefinition HURT = def(0.5F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.08F, -20, 0, 0, 0.3F, 5, 0, 0, 0.5F, 0, 0, 0))
		.addAnimation("jaw", rot(0, 0, 0, 0, 0.08F, 35, 0, 0, 0.4F, 0, 0, 0)).build();
	private final net.minecraft.client.animation.KeyframeAnimation hurt;

	public AphelionArtModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.jaw = head.getChild("jaw");
		this.idle = IDLE.bake(root);
		this.bite = BITE.bake(root);
		this.breath = VOID_BREATH.bake(root);
		this.devour = DEVOUR.bake(root);
		this.collapse = COLLAPSE.bake(root);
		this.feast = FEAST.bake(root);
		this.hiss = HISS.bake(root);
		this.roar = ROAR.bake(root);
		this.hurt = HURT.bake(root);
		this.death = DEATH.bake(root);
	}

	/** Head layer (128x64, aphelion_head.png). Model space: up is -Y, the snout points to -Z. */
	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-7.0F, -4.5F, -6.5F, 14, 9, 13)           // skull
			.texOffs(0, 24).addBox(-5.0F, -2.5F, -13.5F, 10, 6, 7),          // snout
			PartPose.offset(0.0F, 18.0F, 0.0F));
		head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(60, 0).addBox(-1.0F, -4.0F, -5.0F, 2, 4, 10), PartPose.offset(0.0F, -4.5F, 0.5F));
		head.addOrReplaceChild("horn_l", CubeListBuilder.create().texOffs(60, 16).addBox(-1.0F, -1.0F, 0.0F, 2, 2, 12),
			PartPose.offsetAndRotation(4.5F, -3.5F, 4.0F, 0.45F, 0.25F, 0.0F));
		head.addOrReplaceChild("horn_r", CubeListBuilder.create().texOffs(60, 16).mirror().addBox(-1.0F, -1.0F, 0.0F, 2, 2, 12),
			PartPose.offsetAndRotation(-4.5F, -3.5F, 4.0F, 0.45F, -0.25F, 0.0F));
		head.addOrReplaceChild("eye_l", CubeListBuilder.create().texOffs(90, 0).addBox(-0.5F, -1.0F, -1.5F, 2, 2, 3), PartPose.offset(6.5F, -1.5F, -4.0F));
		head.addOrReplaceChild("eye_r", CubeListBuilder.create().texOffs(90, 0).mirror().addBox(-1.5F, -1.0F, -1.5F, 2, 2, 3), PartPose.offset(-6.5F, -1.5F, -4.0F));
		CubeListBuilder upper = CubeListBuilder.create();
		for (float x : new float[] {-4.0F, -1.5F, 0.5F, 3.0F}) upper.texOffs(90, 8).addBox(x, 3.5F, -12.5F, 1, 2, 1);
		head.addOrReplaceChild("teeth_upper", upper, PartPose.ZERO);
		// jaw hinged under the back of the skull; positive xRot drops the front
		PartDefinition jaw = head.addOrReplaceChild("jaw", CubeListBuilder.create().texOffs(0, 38).addBox(-6.0F, 0.0F, -17.0F, 12, 3, 17),
			PartPose.offset(0.0F, 4.0F, 3.5F));
		CubeListBuilder lower = CubeListBuilder.create();
		for (float x : new float[] {-5.0F, -2.5F, 1.5F, 4.0F}) lower.texOffs(90, 8).addBox(x, -2.0F, -16.0F, 1, 2, 1);
		for (float z : new float[] {-12.0F, -8.0F}) {
			lower.texOffs(90, 8).addBox(-5.5F, -2.0F, z, 1, 2, 1);
			lower.texOffs(90, 8).addBox(4.5F, -2.0F, z, 1, 2, 1);
		}
		jaw.addOrReplaceChild("teeth_lower", lower, PartPose.ZERO);
		return LayerDefinition.create(mesh, 128, 64);
	}

	@Override
	public void setupAnim(State s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		idle.apply((long) (age * 50.0F), 1.0F);
		if (s.deathTime > 0) {
			death.apply((long) (s.deathTime * 50.0F), 1.0F);
		} else {
			if (s.transition) roar.apply(s.transitionAnim, age);
			dev.elsewhere.bossart.nether.HurtAnim.apply(hurt, s);
			if (s.attack != null) {
				long ms = (long) (s.attackAge * 50.0F);
				switch (s.attack) {
					case "bite" -> bite.apply(ms, 1.0F);
					case "void_breath" -> breath.apply(ms, 1.0F);
					case "devour_dive" -> devour.apply(ms, 1.0F);
					case "collapse" -> collapse.apply(ms, 1.0F);
					case "crystal_feast" -> feast.apply(ms, 1.0F);
					case "voidborn", "coil" -> hiss.apply(ms, 1.0F);
					default -> { }
				}
			}
		}
		head.xRot += Mth.clamp(s.xRot, -60.0F, 60.0F) * Mth.DEG_TO_RAD;
		if (s.diving) jaw.xRot = Math.max(jaw.xRot, 1.1F);
	}
}
