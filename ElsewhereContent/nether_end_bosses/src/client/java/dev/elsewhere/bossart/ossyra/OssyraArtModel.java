package dev.elsewhere.bossart.ossyra;

import dev.elsewhere.boss.ossyra.Ossyra;
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
 * Ossyra, Mother of Shells: a queen shulker. Purpur shell base (24 px = 6 blocks at render scale 4) with a hinged lid that
 * lifts from the back edge, four end-rod crown spikes on the lid, the glowing yellow pearl core with a face, and nine
 * orbiting shell fragments. Built from the hero boxes of {@code ossyra()} in Ideas/art/src/bosses_art.py (1 art unit = 2.7 px).
 */
public class OssyraArtModel extends EntityModel<OssyraArtRenderState> {
	public static final int FRAGS = 9;
	private static final float LID_OPEN = -0.95F; // about 55 degrees

	private final ModelPart body;
	private final ModelPart base;
	private final ModelPart lid;
	private final ModelPart pearl;
	private final ModelPart[] rods = new ModelPart[4];
	private final ModelPart[] frags = new ModelPart[FRAGS];
	private final KeyframeAnimation idle;
	private final KeyframeAnimation bulletStorm;
	private final KeyframeAnimation shellSlam;
	private final KeyframeAnimation gravityFlip;
	private final KeyframeAnimation shulklings;
	private final KeyframeAnimation voidBloom;
	private final KeyframeAnimation surfaceSwap;
	private final KeyframeAnimation roar;
	private final KeyframeAnimation death;

	/** 3.0 (P8): hurt flinch, the shell rattles and the pearl winces. */
	static final net.minecraft.client.animation.AnimationDefinition HURT = dev.elsewhere.bossart.nether.ArtAnim.def(0.5F, false)
		.addAnimation("body", dev.elsewhere.bossart.nether.ArtAnim.rot(0, 0, 0, 0, 0.06F, 0, 0, -7, 0.18F, 0, 0, 6, 0.32F, 0, 0, -3, 0.5F, 0, 0, 0))
		.addAnimation("pearl", dev.elsewhere.bossart.nether.ArtAnim.scale(0, 1, 1, 1, 0.1, 0.8, 0.8, 0.8, 0.5, 1, 1, 1)).build();
	private final net.minecraft.client.animation.KeyframeAnimation hurt;

	public OssyraArtModel(ModelPart root) {
		super(root);
		this.body = root.getChild("body");
		this.base = body.getChild("base");
		this.lid = body.getChild("lid");
		this.pearl = body.getChild("pearl");
		for (int i = 0; i < 4; i++) rods[i] = lid.getChild("rod" + i);
		for (int i = 0; i < FRAGS; i++) frags[i] = body.getChild("frag" + i);
		this.idle = OssyraAnimations.IDLE.bake(root);
		this.hurt = HURT.bake(root);
		this.bulletStorm = OssyraAnimations.BULLET_STORM.bake(root);
		this.shellSlam = OssyraAnimations.SHELL_SLAM.bake(root);
		this.gravityFlip = OssyraAnimations.GRAVITY_FLIP.bake(root);
		this.shulklings = OssyraAnimations.SHULKLINGS.bake(root);
		this.voidBloom = OssyraAnimations.VOID_BLOOM.bake(root);
		this.surfaceSwap = OssyraAnimations.SURFACE_SWAP.bake(root);
		this.roar = OssyraAnimations.ROAR.bake(root);
		this.death = OssyraAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.offset(0.0F, 24.0F, 0.0F));
		body.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0F, -14.0F, -12.0F, 24, 14, 24), PartPose.ZERO);
		// lid hinged at the top back edge of the base; it extends forward (-Z) and upward
		PartDefinition lid = body.addOrReplaceChild("lid", CubeListBuilder.create().texOffs(0, 40).addBox(-12.5F, -10.0F, -25.0F, 25, 10, 25),
			PartPose.offset(0.0F, -14.0F, 12.0F));
		// end-rod crown (art: x = -3, -1, 1, 3 art units; the middle two are taller)
		float[] rx = {-8.0F, -3.0F, 3.0F, 8.0F};
		for (int i = 0; i < 4; i++) {
			boolean tall = i == 1 || i == 2;
			lid.addOrReplaceChild("rod" + i, CubeListBuilder.create().texOffs(tall ? 110 : 100, 0).addBox(-1.0F, tall ? -12.0F : -8.0F, -1.0F, 2, tall ? 12 : 8, 2),
				PartPose.offset(rx[i], -10.0F, -13.0F));
		}
		body.addOrReplaceChild("pearl", CubeListBuilder.create().texOffs(0, 80).addBox(-7.0F, -7.0F, -7.0F, 14, 14, 14), PartPose.offset(0.0F, -11.0F, -1.0F));
		for (int i = 0; i < FRAGS; i++) {
			body.addOrReplaceChild("frag" + i, CubeListBuilder.create().texOffs(60, 80).addBox(-2.5F, -1.0F, -2.5F, 5, 2, 5), PartPose.offset(0.0F, -20.0F, 0.0F));
		}
		return LayerDefinition.create(mesh, 128, 128);
	}

	@Override
	public void setupAnim(OssyraArtRenderState s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		// shell open/close (smoothed from the synced open flag)
		lid.xRot = LID_OPEN * s.openAmount;
		pearl.y -= 6.0F * s.openAmount + Mth.sin(age * 0.2F) * 0.6F * s.openAmount;
		float pulse = 1.0F + 0.05F * Mth.sin(age * 0.3F);
		pearl.xScale = pearl.yScale = pearl.zScale = pulse;
		pearl.yRot = Mth.sin(age * 0.04F) * 0.25F;
		// shell fragments orbit on a tilted ring; they fly wider while she attacks, scatter on death
		float spread = 1.0F + (s.attack != null ? 0.25F : 0.0F) + s.deathTime * 0.12F;
		for (int i = 0; i < FRAGS; i++) {
			float a = age * (0.035F + (i % 3) * 0.006F) + i * (Mth.TWO_PI / FRAGS);
			float r = (17.0F + (i % 3) * 3.0F) * spread;
			ModelPart f = frags[i];
			f.x = Mth.cos(a) * r;
			f.z = Mth.sin(a) * r;
			f.y = -16.0F - (i % 4) * 5.0F + Mth.sin(age * 0.09F + i * 1.7F) * 2.5F - s.deathTime * 0.8F;
			f.yRot = -a + i;
			f.xRot = 0.4F * Mth.sin(i * 2.1F);
			f.zRot = 0.5F * Mth.cos(i * 1.3F + age * 0.02F);
		}
		// gravity flip: the crown rods flicker during the 3 s chime
		boolean flicker = "gravity_flip".equals(s.attack) && s.attackAge < 60;
		for (int i = 0; i < 4; i++) rods[i].visible = !flicker || ((int) (age * 0.7F) + i * 3) % 4 != 0;

		this.idle.apply((long) (age * 50.0F), 1.0F);
		dev.elsewhere.bossart.nether.HurtAnim.apply(hurt, s);
		if (s.deathTime > 0) {
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		if (s.transition) this.roar.apply(s.transitionAnim, age);
		if (s.attack == null) return;
		long ms = (long) (s.attackAge * 50.0F);
		switch (s.attack) {
			case "bullet_storm" -> bulletStorm.apply(ms, 1.0F);
			case "shell_slam" -> shellSlam.apply(ms, 1.0F);
			case "gravity_flip" -> gravityFlip.apply(ms, 1.0F);
			case "shulklings" -> shulklings.apply(ms, 1.0F);
			case "void_bloom" -> voidBloom.apply(ms, 1.0F);
			case "surface_swap" -> surfaceSwap.apply(ms, 1.0F);
			default -> { }
		}
	}

	public boolean isOpenFlag(int flags) {
		return (flags & Ossyra.FLAG_OPEN) != 0;
	}
}
