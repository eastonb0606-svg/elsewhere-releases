package dev.elsewhere.boss.solaxis.client;

import net.minecraft.client.animation.AnimationChannel;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.Keyframe;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Solaxis: the rods orbit in three rings (speed shows the charge), eject per phase, the core pulses during Supernova. */
public class SolaxisModel extends EntityModel<SolaxisRenderState> {
	/** Ring radius / height in model pixels; matches {@code Solaxis.rodPosition} at render scale 2. */
	private static final float[] RING_RADIUS = {16.0F, 12.8F, 10.0F};
	private static final float[] RING_Y = {-11.2F, 1.6F, 13.6F};

	/** Phase-change roar: the head throws back, the core flares. */
	public static final AnimationDefinition ROAR = AnimationDefinition.Builder.withLength(2.0F)
		.addAnimation("head", new AnimationChannel(AnimationChannel.Targets.ROTATION,
			new Keyframe(0.0F, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(0.4F, KeyframeAnimations.degreeVec(-28, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(1.5F, KeyframeAnimations.degreeVec(-22, 0, 0), AnimationChannel.Interpolations.CATMULLROM),
			new Keyframe(2.0F, KeyframeAnimations.degreeVec(0, 0, 0), AnimationChannel.Interpolations.CATMULLROM)))
		.addAnimation("core", new AnimationChannel(AnimationChannel.Targets.SCALE,
			new Keyframe(0.0F, KeyframeAnimations.scaleVec(1, 1, 1), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(0.5F, KeyframeAnimations.scaleVec(1.35, 1.35, 1.35), AnimationChannel.Interpolations.LINEAR),
			new Keyframe(2.0F, KeyframeAnimations.scaleVec(1, 1, 1), AnimationChannel.Interpolations.LINEAR)))
		.build();

	private final ModelPart head;
	private final ModelPart core;
	private final ModelPart tail1;
	private final ModelPart tail2;
	private final ModelPart[] rods = new ModelPart[9];
	private final KeyframeAnimation roar;

	public SolaxisModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.core = root.getChild("core");
		this.tail1 = root.getChild("tail1");
		this.tail2 = tail1.getChild("tail2");
		for (int i = 0; i < 9; i++) rods[i] = root.getChild("rod" + i);
		this.roar = ROAR.bake(root);
	}

	@Override
	public void setupAnim(SolaxisRenderState state) {
		super.setupAnim(state);
		float age = state.ageInTicks;
		head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5F;
		head.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
		head.y += Mth.sin(age * 0.08F) * 0.6F;
		tail1.zRot = Mth.sin(age * 0.21F) * 0.12F;
		tail1.xRot = Mth.cos(age * 0.17F) * 0.10F;
		tail2.zRot = Mth.sin(age * 0.29F + 1) * 0.18F;
		for (int i = 0; i < 9; i++) {
			ModelPart rod = rods[i];
			rod.visible = i < state.rodsLeft;
			int ring = i / 3;
			float speed = (ring == 1 ? -1 : 1) * (0.06F + ring * 0.02F);
			float a = age * speed + i * (Mth.TWO_PI / 3) + ring * 0.6F;
			rod.x = Mth.cos(a) * RING_RADIUS[ring];
			rod.z = Mth.sin(a) * RING_RADIUS[ring];
			rod.y = RING_Y[ring] + Mth.sin(age * 0.15F + i) * 1.2F;
			rod.zRot = Mth.sin(a) * 0.18F;
			rod.xRot = Mth.cos(a) * 0.18F;
			if (state.rodsGlow) {
				rod.xScale = rod.zScale = 1.25F;
				rod.y -= 1.5F;
			}
		}
		if (state.corePulse) {
			float pulse = 1.0F + 0.18F * Mth.sin(age * 0.7F);
			core.xScale = core.yScale = core.zScale = pulse;
			head.xScale = head.yScale = head.zScale = 1.0F + 0.05F * Mth.sin(age * 0.7F);
		}
		if ("eclipse_orb".equals(state.attack)) {
			float k = Math.min(1.0F, state.attackAge / 25.0F);
			core.xScale = core.yScale = core.zScale = 1.0F + 0.3F * k;
		}
		if (state.deathTime > 0) {
			float d = Math.min(1.0F, state.deathTime / 20.0F);
			for (ModelPart rod : rods) rod.y += d * 30.0F;
			core.xScale = core.yScale = core.zScale = 1.0F - 0.8F * d;
		}
		roar.apply(state.transitionAnim, age);
	}
}
