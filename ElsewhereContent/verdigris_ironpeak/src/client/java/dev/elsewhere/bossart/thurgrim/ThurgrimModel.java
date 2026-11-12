package dev.elsewhere.bossart.thurgrim;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Thurgrim Ironjaw: horned, iron-helmed giant king in a fur mantle and red cape with a double-bit greataxe. */
@Environment(EnvType.CLIENT)
public class ThurgrimModel extends EntityModel<ThurgrimRenderState> {
	private final ModelPart head;
	private final ModelPart axe;
	private final KeyframeAnimation idle, walk, split, roar, combo, boomerang, command, death;

	public ThurgrimModel(ModelPart parts) {
		super(parts);
		ModelPart root = parts.getChild("root");
		this.head = root.getChild("body").getChild("head");
		this.axe = root.getChild("body").getChild("right_arm").getChild("axe");
		this.idle = ThurgrimAnimations.IDLE.bake(parts);
		this.walk = ThurgrimAnimations.WALK.bake(parts);
		this.split = ThurgrimAnimations.SPLIT.bake(parts);
		this.roar = ThurgrimAnimations.ROAR.bake(parts);
		this.combo = ThurgrimAnimations.COMBO.bake(parts);
		this.boomerang = ThurgrimAnimations.BOOMERANG.bake(parts);
		this.command = ThurgrimAnimations.COMMAND.bake(parts);
		this.death = ThurgrimAnimations.DEATH.bake(parts);
	}

	@Override
	public void setupAnim(ThurgrimRenderState s) {
		super.setupAnim(s);
		if (s.deathTime > 0) {
			death.apply((long) (s.deathTime * 50), 1.0F);
			return;
		}
		this.head.yRot = Mth.clamp(s.yRot, -40, 40) * Mth.DEG_TO_RAD;
		this.head.xRot = Mth.clamp(s.xRot, -25, 25) * Mth.DEG_TO_RAD;
		boolean busy = s.attack != 0 || s.transition;
		if (!busy) {
			idle.apply((long) (s.ageInTicks * 50), 1.0F);
			walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.0F, 1.5F);
		}
		split.apply(s.split, s.ageInTicks);
		roar.apply(s.roar, s.ageInTicks);
		combo.apply(s.combo, s.ageInTicks);
		boomerang.apply(s.boomerang, s.ageInTicks);
		command.apply(s.command, s.ageInTicks);
		// The axe is out of his hand while the Avalanche Split flies (1.5 s to 3.3 s of the throw).
		if (s.boomerang.isStarted()) {
			float t = s.boomerang.getTimeInMillis(s.ageInTicks) / 1000.0F;
			this.axe.visible = t < 1.5F || t > 3.3F;
		}
	}
}
