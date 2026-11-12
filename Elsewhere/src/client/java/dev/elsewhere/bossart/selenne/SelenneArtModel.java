package dev.elsewhere.bossart.selenne;

import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.util.Mth;

/**
 * Selenne, the Unblinking: a floating white eye with an amber iris, black pupil and highlight, heavy slate lids
 * and ten eyeblossom lashes, after the concept painting. Keyframed lids, Moonbeam charge, phase shudders, lid tear
 * and death (see {@link SelenneAnimations}); the pupil tracks her focus procedurally.
 */
public class SelenneArtModel extends EntityModel<SelenneArtState> {
	private final ModelPart eye;
	private final ModelPart iris;
	private final ModelPart browTop;
	private final ModelPart browBottom;
	private final KeyframeAnimation idle;
	private final KeyframeAnimation open;
	private final KeyframeAnimation close;
	private final KeyframeAnimation beam;
	private final KeyframeAnimation petals;
	private final KeyframeAnimation tear;
	private final KeyframeAnimation fullMoon;
	private final KeyframeAnimation death;

	public SelenneArtModel(final ModelPart root) {
		super(root);
		this.eye = root.getChild("root").getChild("eye");
		this.iris = this.eye.getChild("iris");
		this.browTop = this.eye.getChild("brow_top");
		this.browBottom = this.eye.getChild("brow_bottom");
		this.idle = SelenneAnimations.IDLE.bake(root);
		this.open = SelenneAnimations.OPEN.bake(root);
		this.close = SelenneAnimations.CLOSE.bake(root);
		this.beam = SelenneAnimations.BEAM.bake(root);
		this.petals = SelenneAnimations.PETALS.bake(root);
		this.tear = SelenneAnimations.TEAR.bake(root);
		this.fullMoon = SelenneAnimations.FULL_MOON.bake(root);
		this.death = SelenneAnimations.DEATH.bake(root);
	}

	public static LayerDefinition createBodyLayer() {
		return LayerDefinition.create(SelenneArtGeometry.mesh(), SelenneArtGeometry.TEXTURE_WIDTH, SelenneArtGeometry.TEXTURE_HEIGHT);
	}

	@Override
	public void setupAnim(final SelenneArtState s) {
		super.setupAnim(s);
		this.browTop.visible = s.lidsVisible;
		this.browBottom.visible = s.lidsVisible;
		if (s.deathTime > 0.0F) {
			this.death.apply((long) (s.deathTime * 50.0F), 1.0F);
			return;
		}
		this.idle.apply((long) (s.ageInTicks * 50.0F), 1.0F);
		if (!s.awake) {
			return;
		}
		this.open.apply(s.open, s.ageInTicks);
		this.close.apply(s.close, s.ageInTicks);
		this.petals.apply(s.petals, s.ageInTicks);
		this.tear.apply(s.tear, s.ageInTicks);
		this.beam.apply(s.beam, s.ageInTicks);
		if (s.fullMoon) {
			this.fullMoon.apply((long) (s.ageInTicks * 50.0F), 1.0F);
		}
		// the eye turns toward her focus and the pupil drifts after it
		float yaw = Mth.clamp(s.yRot, -60.0F, 60.0F);
		float pitch = Mth.clamp(s.xRot, -45.0F, 45.0F);
		this.eye.yRot += yaw * Mth.DEG_TO_RAD * 0.5F;
		this.eye.xRot += pitch * Mth.DEG_TO_RAD * 0.5F;
		this.iris.x += Mth.clamp(-yaw * 0.06F, -2.5F, 2.5F) + Mth.sin(s.ageInTicks * 0.07F) * 0.4F;
		this.iris.y += Mth.clamp(pitch * 0.06F, -2.5F, 2.5F) + Mth.cos(s.ageInTicks * 0.05F) * 0.3F;
	}
}
