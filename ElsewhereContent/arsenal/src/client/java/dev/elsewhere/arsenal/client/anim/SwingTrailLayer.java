package dev.elsewhere.arsenal.client.anim;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.elsewhere.arsenal.anim.SwingCurves;
import dev.elsewhere.arsenal.anim.SwingCurves.Style;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/**
 * Third-person swing trail for the Ironclad Flail and Gale Rapier on players (yourself in third person, others within
 * 24 blocks, 32 on high, none on low). Runs only when the avatar is rendered (frustum-culled by the entity renderer)
 * and only during the strike part of a swing; the ribbon is rebuilt from the swing progress, nothing is stored.
 */
final class SwingTrailLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private static final SwingCurves.Scratch SCRATCH = new SwingCurves.Scratch();

	SwingTrailLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
		super(parent);
	}

	@Override
	public void submit(PoseStack pose, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		if (state.swingAnimation <= 0F || state.currentSwing == null || state.isInvisible) return;
		int level = UseAnimConfig.level();
		int n = SwingCurves.trailSamples(level);
		if (n < 2) return;
		double range = SwingCurves.trailRange(level);
		if (state.distanceToCameraSq > range * range) return;
		HumanoidArm arm = state.currentSwing.hand().asArm(state.mainArm);
		ItemStack stack = arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		Style s = UseAnims.style(stack);
		if (!s.trails()) return;
		float p = state.swingAnimation;
		if (p < SwingCurves.trailStart(s) || p > SwingCurves.trailEnd(s) + n * SwingCurves.TRAIL_STEP) return;
		TrailGeom g = TrailGeom.next();
		g.count = SwingCurves.thirdPersonTrail(s, p, arm == HumanoidArm.LEFT, state.xRot * Mth.DEG_TO_RAD, state.isCrouching, n, SCRATCH, g.pts);
		if (g.count < 2) return;
		g.color = UseAnims.trailColor(s);
		g.alpha = 0.45F;
		collector.submitCustomGeometry(pose, TrailGeom.TYPE, g);
	}
}
