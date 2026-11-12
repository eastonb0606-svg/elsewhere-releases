package dev.elsewhere.bossart.hrolf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * Hrolf Grimhorn: an illager champion (vindicator nose) in clan tartan, mail and a brown fur cloak, ram-horned
 * steel helm, braided ginger beard, iron axe and the war horn on his belt.
 */
@Environment(EnvType.CLIENT)
public class HrolfModel extends EntityModel<HrolfRenderState> {
	private final ModelPart head, rightLeg, leftLeg, rightArm, leftArm, beltHorn, handHorn;
	private final KeyframeAnimation idle, walk, swing, horn, overhead, kick, warcry, rally, death;

	public HrolfModel(ModelPart parts) {
		super(parts);
		ModelPart root = parts.getChild("root");
		ModelPart body = root.getChild("body");
		this.head = body.getChild("head");
		this.rightLeg = root.getChild("right_leg");
		this.leftLeg = root.getChild("left_leg");
		this.rightArm = body.getChild("right_arm");
		this.leftArm = body.getChild("left_arm");
		this.beltHorn = body.getChild("belt_horn");
		this.handHorn = leftArm.getChild("hand_horn");
		this.idle = HrolfAnimations.IDLE.bake(parts);
		this.walk = HrolfAnimations.WALK.bake(parts);
		this.swing = HrolfAnimations.SWING.bake(parts);
		this.horn = HrolfAnimations.HORN.bake(parts);
		this.overhead = HrolfAnimations.OVERHEAD.bake(parts);
		this.kick = HrolfAnimations.KICK.bake(parts);
		this.warcry = HrolfAnimations.WARCRY.bake(parts);
		this.rally = HrolfAnimations.RALLY.bake(parts);
		this.death = HrolfAnimations.DEATH.bake(parts);
	}

	@Override
	public void setupAnim(HrolfRenderState s) {
		super.setupAnim(s);
		boolean blowing = s.hornMillis >= 0 && s.hornMillis < 1500;
		handHorn.visible = blowing;
		beltHorn.visible = !blowing;
		if (s.deathTime > 0) {
			death.apply((long) (s.deathTime * 50), 1.0F);
			return;
		}
		head.yRot = Mth.clamp(s.yRot, -45, 45) * Mth.DEG_TO_RAD;
		head.xRot = Mth.clamp(s.xRot, -30, 30) * Mth.DEG_TO_RAD;
		if (s.riding) {
			// vanilla riding pose, legs astride the war goat
			rightLeg.xRot = -1.4137167F;
			rightLeg.yRot = (float) (Math.PI / 10);
			rightLeg.zRot = 0.07853982F;
			leftLeg.xRot = -1.4137167F;
			leftLeg.yRot = (float) (-Math.PI / 10);
			leftLeg.zRot = -0.07853982F;
			rightArm.xRot -= 0.3F;
			leftArm.xRot -= 0.5F;
		}
		boolean busy = s.attack != 0 || s.transition;
		if (!busy) {
			idle.apply((long) (s.ageInTicks * 50), 1.0F);
			if (!s.riding) walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.5F, 2.0F);
		}
		swing.apply(s.swing, s.ageInTicks);
		overhead.apply(s.overhead, s.ageInTicks);
		kick.apply(s.kick, s.ageInTicks);
		warcry.apply(s.warcry, s.ageInTicks);
		rally.apply(s.rally, s.ageInTicks);
		if (blowing) horn.apply(s.hornMillis, 1.0F);
	}
}
