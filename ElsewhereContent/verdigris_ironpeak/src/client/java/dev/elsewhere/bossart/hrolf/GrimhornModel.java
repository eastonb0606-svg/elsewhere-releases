package dev.elsewhere.bossart.hrolf;

import dev.elsewhere.boss.hrolf.GrimhornEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Grimhorn: a shaggy white war goat with huge iron-capped ram horns, an iron brow plate and a clan saddle. */
@Environment(EnvType.CLIENT)
public class GrimhornModel extends EntityModel<GrimhornRenderState> {
	private final ModelPart head;
	private final KeyframeAnimation idle, walk, paw, charge, stunned;

	public GrimhornModel(ModelPart parts) {
		super(parts);
		this.head = parts.getChild("root").getChild("body").getChild("neck").getChild("head");
		this.idle = HrolfAnimations.GOAT_IDLE.bake(parts);
		this.walk = HrolfAnimations.GOAT_WALK.bake(parts);
		this.paw = HrolfAnimations.GOAT_PAW.bake(parts);
		this.charge = HrolfAnimations.GOAT_CHARGE.bake(parts);
		this.stunned = HrolfAnimations.GOAT_STUNNED.bake(parts);
	}

	@Override
	public void setupAnim(GrimhornRenderState s) {
		super.setupAnim(s);
		long ms = (long) (s.ageInTicks * 50);
		switch (s.goatState) {
			case GrimhornEntity.WINDUP -> paw.apply(ms, 1.0F);
			case GrimhornEntity.CHARGE -> charge.apply(ms, 1.0F);
			case GrimhornEntity.STUNNED -> stunned.apply(ms, 1.0F);
			default -> {
				head.yRot += Mth.clamp(s.yRot, -30, 30) * Mth.DEG_TO_RAD;
				idle.apply(ms, 1.0F);
				walk.applyWalk(s.walkAnimationPos, s.walkAnimationSpeed, 1.0F, 2.0F);
			}
		}
	}
}
