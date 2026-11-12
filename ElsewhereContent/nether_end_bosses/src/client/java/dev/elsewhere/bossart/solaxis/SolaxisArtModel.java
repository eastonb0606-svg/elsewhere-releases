package dev.elsewhere.bossart.solaxis;

import static dev.elsewhere.bossart.nether.ArtAnim.def;
import static dev.elsewhere.bossart.nether.ArtAnim.pos;
import static dev.elsewhere.bossart.nether.ArtAnim.rot;
import static dev.elsewhere.bossart.nether.ArtAnim.scale;

import dev.elsewhere.stub.nether_end_bosses.BossRenderState;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Solaxis from the concept art: molten head on a basalt column, nine rods in three tilted rings (3.6 / 4.6 / 3.8 art
 * units). Ring speed shows the charge, rods eject per phase, the head flares for the Eclipse orb and pulses in Supernova.
 */
public class SolaxisArtModel extends EntityModel<SolaxisArtModel.State> {
	public static class State extends dev.elsewhere.bossart.nether.HurtAnim.State {
		public int rodsLeft = 9;
		public boolean rodsGlow;
		public boolean corePulse;
	}

	private static final float[] RING_R = {28.8F, 36.8F, 30.4F};
	private static final float[] RING_Y = {-59.2F, -40.0F, -17.6F};
	private static final float[] RING_TILT = {10, -14, 6};

	static final AnimationDefinition IDLE = def(4.0F, true)
		.addAnimation("head", pos(0, 0, 0, 0, 2.0F, 0, 2.5F, 0, 4.0F, 0, 0, 0))
		.addAnimation("head", rot(0, 0, 0, 0, 2.0F, 0, 12, 0, 4.0F, 0, 0, 0))
		.addAnimation("column", rot(0, 0, 0, 0, 2.0F, 0, -10, 0, 4.0F, 0, 0, 0)).build();
	/** Rod Volley wind-up: 1 s, the head rears back, then snaps forward as the rods fire. */
	static final AnimationDefinition VOLLEY = def(1.6F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 1.0F, -18, 0, 0, 1.15F, 12, 0, 0, 1.6F, 0, 0, 0)).build();
	/** Eclipse Core wind-up: 1.25 s, the head swells, then recoils. */
	static final AnimationDefinition ECLIPSE = def(1.5F, false)
		.addAnimation("head", scale(0, 1, 1, 1, 1.25F, 1.25, 1.25, 1.25, 1.35F, 0.9, 0.9, 0.9, 1.5F, 1, 1, 1)).build();
	static final AnimationDefinition FLARE = def(1.5F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 1.5F, 25, 0, 0))
		.addAnimation("column", scale(0, 1, 1, 1, 1.5F, 1.3, 0.9, 1.3)).build();
	static final AnimationDefinition ROAR = def(2.0F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.4F, -30, 0, 0, 1.5F, -24, 0, 0, 2.0F, 0, 0, 0))
		.addAnimation("head", scale(0, 1, 1, 1, 0.5F, 1.2, 1.2, 1.2, 2.0F, 1, 1, 1)).build();

	private final ModelPart head;
	private final ModelPart column;
	private final ModelPart[] rods = new ModelPart[9];
	private final KeyframeAnimation idle, volley, eclipse, flare, roar;

	/** 3.0 (P8): hurt flinch, the core jolts back and the column rocks. */
	static final AnimationDefinition HURT = def(0.5F, false)
		.addAnimation("head", rot(0, 0, 0, 0, 0.08F, -16, 0, 8, 0.25F, 6, 0, -4, 0.5F, 0, 0, 0))
		.addAnimation("column", rot(0, 0, 0, 0, 0.1F, -6, 0, 0, 0.5F, 0, 0, 0)).build();
	private final net.minecraft.client.animation.KeyframeAnimation hurt;

	public SolaxisArtModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.column = root.getChild("column");
		for (int i = 0; i < 9; i++) rods[i] = root.getChild("rod" + i);
		this.idle = IDLE.bake(root);
		this.volley = VOLLEY.bake(root);
		this.eclipse = ECLIPSE.bake(root);
		this.flare = FLARE.bake(root);
		this.roar = ROAR.bake(root);
		this.hurt = HURT.bake(root);
	}

	@Override
	public void setupAnim(State s) {
		super.setupAnim(s);
		float age = s.ageInTicks;
		idle.apply((long) (age * 50), 1.0F);
		head.yRot += Mth.clamp(s.yRot, -40, 40) * Mth.DEG_TO_RAD;
		float spin = s.rodsGlow || s.corePulse ? 3.0F : 1.0F;
		for (int i = 0; i < 9; i++) {
			ModelPart rod = rods[i];
			rod.visible = i < s.rodsLeft;
			int ring = i / 3;
			float a = age * (ring == 1 ? -0.05F : 0.06F) * spin + i * (Mth.TWO_PI / 3) + ring * 0.7F;
			rod.x = Mth.cos(a) * RING_R[ring];
			rod.z = Mth.sin(a) * RING_R[ring];
			rod.y = RING_Y[ring] + Mth.sin(a) * RING_TILT[ring] * 0.5F + Mth.sin(age * 0.12F + i) * 1.5F;
			rod.xRot = Mth.cos(a + i) * 0.35F;
			rod.zRot = Mth.sin(a * 1.3F + i) * 0.35F;
			if (s.rodsGlow) rod.y -= 2.0F;
		}
		if ("rod_volley".equals(s.attack)) volley.apply(s.attackAnim, age);
		if ("eclipse_orb".equals(s.attack)) eclipse.apply(s.attackAnim, age);
		if ("solar_flare".equals(s.attack)) flare.apply(s.attackAnim, age);
		if (s.corePulse) {
			float p = 1.0F + 0.12F * Mth.sin(age * 0.8F);
			head.xScale = head.yScale = head.zScale = p;
		}
		roar.apply(s.transitionAnim, age);
		dev.elsewhere.bossart.nether.HurtAnim.apply(hurt, s);
		if (s.deathTime > 0) {
			float d = Math.min(1.0F, s.deathTime / 20.0F);
			for (ModelPart rod : rods) rod.y += d * d * 60.0F; // the rods fall
			head.xScale = head.yScale = head.zScale = 1.0F - 0.85F * d; // the core implodes
		}
	}
}
