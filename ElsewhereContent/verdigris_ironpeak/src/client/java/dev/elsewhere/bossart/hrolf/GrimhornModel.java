package dev.elsewhere.bossart.hrolf;

import dev.elsewhere.boss.hrolf.GrimhornEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import static dev.elsewhere.bossart.ironpeak.Anim.ch;
import static dev.elsewhere.bossart.ironpeak.Anim.k;
import static dev.elsewhere.bossart.ironpeak.Anim.pos;
import static dev.elsewhere.bossart.ironpeak.Anim.rot;

import net.minecraft.client.animation.AnimationChannel.Targets;
import net.minecraft.client.animation.KeyframeAnimation;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/** Grimhorn: a shaggy white war goat with huge iron-capped ram horns, an iron brow plate and a clan saddle. */
@Environment(EnvType.CLIENT)
public class GrimhornModel extends EntityModel<GrimhornRenderState> {
	private final ModelPart head;
	private final KeyframeAnimation idle, walk, paw, charge, stunned, death;

	/**
	 * 3.0 (P8) death: the war goat's legs buckle under it and the neck sags while the vanilla death tilt lays it on
	 * its side (1 s, the 20-tick death time). Off with the {@code bossAnimations3} switch (plain vanilla tilt).
	 */
	static final net.minecraft.client.animation.AnimationDefinition GOAT_DEATH = net.minecraft.client.animation.AnimationDefinition.Builder.withLength(1.0F)
		.addAnimation("leg_fr", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.35F, rot(-70, 0, 6)), k(1.0F, rot(-85, 0, 10))))
		.addAnimation("leg_fl", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-65, 0, -6)), k(1.0F, rot(-85, 0, -10))))
		.addAnimation("leg_br", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.45F, rot(60, 0, 8)), k(1.0F, rot(75, 0, 12))))
		.addAnimation("leg_bl", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(60, 0, -8)), k(1.0F, rot(75, 0, -12))))
		.addAnimation("root", ch(Targets.POSITION, k(0, pos(0, 0, 0)), k(0.4F, pos(0, -4, 0)), k(1.0F, pos(0, -7, 0))))
		.addAnimation("neck", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.3F, rot(-12, 0, 0)), k(1.0F, rot(48, 0, 10))))
		.addAnimation("head", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(0.5F, rot(10, 0, 8)), k(1.0F, rot(22, 0, 18))))
		.addAnimation("tail", ch(Targets.ROTATION, k(0, rot(0, 0, 0)), k(1.0F, rot(30, 0, 0))))
		.build();

	public GrimhornModel(ModelPart parts) {
		super(parts);
		this.head = parts.getChild("root").getChild("body").getChild("neck").getChild("head");
		this.idle = HrolfAnimations.GOAT_IDLE.bake(parts);
		this.walk = HrolfAnimations.GOAT_WALK.bake(parts);
		this.paw = HrolfAnimations.GOAT_PAW.bake(parts);
		this.charge = HrolfAnimations.GOAT_CHARGE.bake(parts);
		this.stunned = HrolfAnimations.GOAT_STUNNED.bake(parts);
		this.death = GOAT_DEATH.bake(parts);
	}

	@Override
	public void setupAnim(GrimhornRenderState s) {
		super.setupAnim(s);
		if (s.deathTime > 0 && dev.elsewhere.verdigris_ironpeak.anim3.Flinch3.enabled()) {
			death.apply((long) (s.deathTime * 50), 1.0F);
			return;
		}
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
