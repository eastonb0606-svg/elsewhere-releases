package dev.elsewhere.bossart.ankhara;

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
 * Ankhara's head from the concept boxes: a sand-scaled neck rising out of the pit, a broad blunt head whose front is a
 * round black gullet with a furnace core, ringed by six amber sunglass fangs (they flower open to bite), and a glass crest.
 */
public class AnkharaModel extends EntityModel<AnkharaRenderState> {
	private final KeyframeAnimation idle, swim, erupt, windup, lunge, stun, roar, death;

	public AnkharaModel(ModelPart root) {
		super(root, RenderTypes::entityCutout);
		this.idle = AnkharaAnimations.IDLE.bake(root);
		this.swim = AnkharaAnimations.SWIM.bake(root);
		this.erupt = AnkharaAnimations.ERUPT.bake(root);
		this.windup = AnkharaAnimations.WINDUP.bake(root);
		this.lunge = AnkharaAnimations.LUNGE.bake(root);
		this.stun = AnkharaAnimations.STUN.bake(root);
		this.roar = AnkharaAnimations.ROAR.bake(root);
		this.death = AnkharaAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition neck = root.addOrReplaceChild("neck", CubeListBuilder.create().texOffs(0, 76).addBox(-18F, -34F, -15F, 36F, 40F, 30F),
			PartPose.offset(0F, 24F, 10F));
		PartDefinition head = neck.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-24F, -16F, -30F, 48F, 32F, 44F)
			.texOffs(132, 76).addBox(-15F, -15F, -31F, 30F, 30F, 1F)
			.texOffs(132, 107).addBox(-6F, -6F, -31.6F, 12F, 12F, 1F), PartPose.offset(0F, -30F, -6F));
		PartDefinition crest = head.addOrReplaceChild("crest", CubeListBuilder.create().texOffs(212, 0).addBox(-3F, -14F, -12F, 6F, 16F, 6F)
			.texOffs(212, 0).addBox(-3F, -12F, 2F, 6F, 16F, 6F), PartPose.offsetAndRotation(0F, -16F, 0F, -0.35F, 0F, 0F));
		PartDefinition maw = head.addOrReplaceChild("maw", CubeListBuilder.create(), PartPose.offset(0F, 0F, -30F));
		for (int i = 0; i < 6; i++) {
			maw.addOrReplaceChild("fang" + i, CubeListBuilder.create().texOffs(184, 0).addBox(-4.5F, -40F, -2.5F, 9F, 30F, 5F),
				PartPose.rotation(70F * Mth.DEG_TO_RAD, 0F, (i * 60F + 30F) * Mth.DEG_TO_RAD));
		}
		return LayerDefinition.create(mesh, 256, 256);
	}

	@Override
	public void setupAnim(AnkharaRenderState s) {
		super.setupAnim(s);
		float t = s.ageInTicks;
		if (s.deathTime > 0) {
			death.apply((long) (s.deathTime * 50F), 1.0F);
			return;
		}
		if (s.burrowed) swim.apply((long) (t * 50F), 1.0F);
		else idle.apply((long) (t * 50F), 1.0F);
		erupt.apply(s.erupt, t);
		// the strike starts from the coiled pose, so the wind-up stops as soon as the lunge begins
		boolean struck = s.lunge.isStarted() && s.windup.isStarted() && s.lunge.getTimeInMillis(t) <= s.windup.getTimeInMillis(t);
		if (!struck) windup.apply(s.windup, t);
		lunge.apply(s.lunge, t);
		stun.apply(s.stun, t);
		if (s.roarTime >= 0) roar.apply((long) (s.roarTime * 1000F), 1.0F);
	}
}
